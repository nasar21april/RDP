package com.example.artemisrdp.engine

import com.example.artemisrdp.model.RdpConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate

/**
 * Handles network socket, TPKT framing, X.224 connection negotiation,
 * and SSL/TLS security upgrade for Microsoft RDP (MS-RDPBCGR).
 */
class RdpProtocolEngine(private val config: RdpConnection) {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    companion object {
        // TPKT Version
        const val TPKT_VERSION = 3

        // X.224 Codes
        const val X224_CR_TPDU = 0xE0 // Connection Request
        const val X224_CC_TPDU = 0xD0 // Connection Confirm

        // RDP Negotiation Protocols
        const val PROTOCOL_RDP = 0x00
        const val PROTOCOL_SSL = 0x01
        const val PROTOCOL_HYBRID = 0x02 // CredSSP / NLA
        const val PROTOCOL_RDSTLS = 0x04
    }

    suspend fun connect(onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress("Opening TCP socket to ${config.host}:${config.port}...")
            val rawSocket = Socket()
            rawSocket.tcpNoDelay = true
            rawSocket.soTimeout = 10000
            rawSocket.connect(InetSocketAddress(config.host, config.port), 8000)
            socket = rawSocket

            inputStream = BufferedInputStream(rawSocket.getInputStream())
            outputStream = BufferedOutputStream(rawSocket.getOutputStream())

            onProgress("Sending X.224 Connection Request with RDP negotiation...")
            sendConnectionRequest(outputStream!!)

            onProgress("Reading X.224 Connection Confirm...")
            val response = readTpktPacket(inputStream!!)
            if (response.isEmpty() || (response[0].toInt() and 0xFF) != X224_CC_TPDU) {
                throw IllegalStateException("Invalid X.224 CC-TPDU response from server")
            }

            val negotiatedProtocol = parseNegotiationResponse(response)
            onProgress("Negotiated security protocol: 0x%02X".format(negotiatedProtocol))

            if ((negotiatedProtocol and PROTOCOL_SSL) != 0 || (negotiatedProtocol and PROTOCOL_HYBRID) != 0) {
                onProgress("Upgrading connection to SSL/TLS...")
                upgradeToTls()
                onProgress("TLS handshake completed successfully.")
            }

            onProgress("RDP handshake complete. Initializing session...")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            close()
            throw e
        }
    }

    /**
     * Builds and sends TPKT + X.224 Connection Request carrying RDP_NEG_REQ
     */
    private fun sendConnectionRequest(out: OutputStream) {
        val crPayload = ByteArrayOutputStream()
        // X.224 CR-TPDU
        crPayload.write(0x00) // Destination reference (2 bytes)
        crPayload.write(0x00)
        crPayload.write(0x12) // Source reference (2 bytes)
        crPayload.write(0x34)
        crPayload.write(0x00) // Class and options

        // Cookie: mstshash=username\r\n
        val user = if (config.username.isNotBlank()) config.username else "User"
        val cookie = "Cookie: mstshash=$user\r\n"
        crPayload.write(cookie.toByteArray(Charsets.US_ASCII))

        // RDP_NEG_REQ
        crPayload.write(0x01) // type: RDP_NEG_REQ
        crPayload.write(0x00) // flags
        crPayload.write(0x08) // length (8 bytes)
        crPayload.write(0x00)

        // requestedProtocols: PROTOCOL_RDP (Standard RDP Security as configured on Windows server)
        val protocols = PROTOCOL_RDP
        crPayload.write(protocols and 0xFF)
        crPayload.write((protocols shr 8) and 0xFF)
        crPayload.write((protocols shr 16) and 0xFF)
        crPayload.write((protocols shr 24) and 0xFF)

        val payload = crPayload.toByteArray()
        val body = ByteArrayOutputStream()
        // X.224 Header: LI + CR_TPDU + Payload
        body.write((payload.size + 1) and 0xFF) // LI (Length Indicator: length of TPDU excluding LI itself)
        body.write(X224_CR_TPDU)
        body.write(payload)

        val tpduBytes = body.toByteArray()
        val totalLength = tpduBytes.size + 4 // 4 bytes TPKT header + TPDU bytes

        val packet = ByteArrayOutputStream()
        // TPKT Header (4 bytes)
        packet.write(TPKT_VERSION)
        packet.write(0x00) // Reserved
        packet.write((totalLength shr 8) and 0xFF)
        packet.write(totalLength and 0xFF)
        packet.write(tpduBytes)

        out.write(packet.toByteArray())
        out.flush()
    }

    /**
     * Reads a full TPKT packet from the input stream.
     */
    private fun readTpktPacket(input: InputStream): ByteArray {
        val header = ByteArray(4)
        var read = 0
        while (read < 4) {
            val r = input.read(header, read, 4 - read)
            if (r == -1) return ByteArray(0)
            read += r
        }

        val version = header[0].toInt() and 0xFF
        if (version != TPKT_VERSION) {
            throw IllegalStateException("Invalid TPKT version: $version")
        }

        val length = ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
        val payloadLength = length - 4
        if (payloadLength <= 0) return ByteArray(0)

        val payload = ByteArray(payloadLength)
        read = 0
        while (read < payloadLength) {
            val r = input.read(payload, read, payloadLength - read)
            if (r == -1) break
            read += r
        }

        // Return X.224 TPDU without Length Indicator byte
        if (payload.isNotEmpty()) {
            val result = ByteArray(payload.size - 1)
            System.arraycopy(payload, 1, result, 0, result.size)
            return result
        }
        return ByteArray(0)
    }

    private fun parseNegotiationResponse(x224Data: ByteArray): Int {
        // Search for RDP_NEG_RSP (0x02) or RDP_NEG_FAILURE (0x03)
        for (i in 0 until x224Data.size - 7) {
            val type = x224Data[i].toInt() and 0xFF
            val len = (x224Data[i + 2].toInt() and 0xFF) or ((x224Data[i + 3].toInt() and 0xFF) shl 8)
            if (type == 0x02 && len == 8) {
                // selectedProtocol (4 bytes)
                val p0 = x224Data[i + 4].toInt() and 0xFF
                val p1 = x224Data[i + 5].toInt() and 0xFF
                val p2 = x224Data[i + 6].toInt() and 0xFF
                val p3 = x224Data[i + 7].toInt() and 0xFF
                return p0 or (p1 shl 8) or (p2 shl 16) or (p3 shl 24)
            } else if (type == 0x03 && len == 8) {
                val code = (x224Data[i + 4].toInt() and 0xFF) or
                        ((x224Data[i + 5].toInt() and 0xFF) shl 8) or
                        ((x224Data[i + 6].toInt() and 0xFF) shl 16) or
                        ((x224Data[i + 7].toInt() and 0xFF) shl 24)
                throw IllegalStateException("RDP negotiation failed with code 0x%08X".format(code))
            }
        }
        return PROTOCOL_RDP
    }

    private fun upgradeToTls() {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        val sslSocketFactory = sslContext.socketFactory
        val sslSocket = sslSocketFactory.createSocket(
            socket,
            config.host,
            config.port,
            true
        ) as SSLSocket

        sslSocket.useClientMode = true
        sslSocket.startHandshake()

        socket = sslSocket
        inputStream = BufferedInputStream(sslSocket.inputStream)
        outputStream = BufferedOutputStream(sslSocket.outputStream)
    }

    fun sendData(data: ByteArray) {
        try {
            outputStream?.write(data)
            outputStream?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        inputStream = null
        outputStream = null
        socket = null
    }
}
