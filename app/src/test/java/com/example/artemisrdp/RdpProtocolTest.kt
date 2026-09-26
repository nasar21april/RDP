package com.example.artemisrdp

import com.example.artemisrdp.engine.RdpInputHandler
import com.example.artemisrdp.model.ColorDepth
import com.example.artemisrdp.model.DisplayResolution
import com.example.artemisrdp.model.ExperienceMode
import com.example.artemisrdp.model.PointerAction
import com.example.artemisrdp.model.PointerButton
import com.example.artemisrdp.model.RdpConnection
import com.example.artemisrdp.model.RdpKeyEvent
import com.example.artemisrdp.model.RdpPointerEvent
import com.example.artemisrdp.model.SoundOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testRdpConnectionJsonSerialization() {
        val original = RdpConnection(
            id = "test-id-123",
            name = "Office Host",
            host = "192.168.1.150",
            port = 3389,
            username = "admin",
            domain = "CORP",
            resolution = DisplayResolution.FULL_HD,
            colorDepth = ColorDepth.HIGHEST_32,
            soundOption = SoundOption.LOCAL,
            experienceMode = ExperienceMode.LAN,
            isDemo = false
        )

        val encoded = json.encodeToString(original)
        assertTrue(encoded.contains("192.168.1.150"))
        assertTrue(encoded.contains("FULL_HD"))

        val decoded = json.decodeFromString<RdpConnection>(encoded)
        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.host, decoded.host)
        assertEquals(original.port, decoded.port)
        assertEquals(original.resolution, decoded.resolution)
    }

    @Test
    fun testPointerEventEncoding() {
        val moveEvent = RdpPointerEvent(
            x = 800,
            y = 600,
            action = PointerAction.MOVE
        )
        val moveBytes = RdpInputHandler.encodeFastPathPointer(moveEvent)
        assertEquals(6, moveBytes.size)

        // Verify coordinates (Little Endian)
        val x = (moveBytes[2].toInt() and 0xFF) or ((moveBytes[3].toInt() and 0xFF) shl 8)
        val y = (moveBytes[4].toInt() and 0xFF) or ((moveBytes[5].toInt() and 0xFF) shl 8)
        assertEquals(800, x)
        assertEquals(600, y)

        // Verify button down flags
        val clickEvent = RdpPointerEvent(
            x = 100,
            y = 200,
            action = PointerAction.BUTTON_DOWN,
            button = PointerButton.LEFT
        )
        val clickBytes = RdpInputHandler.encodeFastPathPointer(clickEvent)
        val flags = (clickBytes[0].toInt() and 0xFF) or ((clickBytes[1].toInt() and 0xFF) shl 8)
        assertTrue((flags and RdpInputHandler.PTRFLAGS_DOWN) != 0)
        assertTrue((flags and RdpInputHandler.PTRFLAGS_BUTTON1) != 0)
    }

    @Test
    fun testKeyboardEventEncoding() {
        val keyDown = RdpKeyEvent(
            scancode = RdpInputHandler.SCANCODE_RETURN,
            isDown = true
        )
        val downBytes = RdpInputHandler.encodeFastPathKeyboard(keyDown)
        assertEquals(2, downBytes.size)
        assertEquals(0, downBytes[0].toInt()) // flags (no release flag)
        assertEquals(RdpInputHandler.SCANCODE_RETURN, downBytes[1].toInt())

        val keyUp = RdpKeyEvent(
            scancode = RdpInputHandler.SCANCODE_ESCAPE,
            isDown = false
        )
        val upBytes = RdpInputHandler.encodeFastPathKeyboard(keyUp)
        assertEquals(2, upBytes.size)
        assertEquals(0x01, upBytes[0].toInt()) // KBDFLAGS_RELEASE
        assertEquals(RdpInputHandler.SCANCODE_ESCAPE, upBytes[1].toInt())
    }

    @Test
    fun testCredentialsParsingFromLog() {
        val sampleLog = """
            2026-09-23T14:30:10.1234567Z ##[section]Starting: Setup Tailscale and RDP
            2026-09-23T14:30:15.7891234Z Connecting to Tailscale mesh network...
            2026-09-23T14:30:20.4567890Z Success! Device online.
            2026-09-23T14:30:21.1111111Z Tailscale IP: 100.85.120.44
            2026-09-23T14:30:22.2222222Z Username: runneradmin
            2026-09-23T14:30:23.3333333Z Password: SecretPass123!@#
            2026-09-23T14:30:24.4444444Z Windows RDP Service active on port 3389.
        """.trimIndent()

        val session = com.example.artemisrdp.data.GitHubCloudRdpManager.parseCredentialsFromLog(sampleLog, 12345L)
        assertNotNull(session)
        assertEquals("100.85.120.44", session?.ip)
        assertEquals("runneradmin", session?.username)
        assertEquals("SecretPass123!@#", session?.password)
        assertEquals(12345L, session?.runId)
        assertTrue(session?.remainingMillis ?: 0L > 0)
    }
}
