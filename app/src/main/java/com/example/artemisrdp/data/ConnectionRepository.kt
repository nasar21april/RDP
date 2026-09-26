package com.example.artemisrdp.data

import android.content.Context
import com.example.artemisrdp.model.ColorDepth
import com.example.artemisrdp.model.DisplayResolution
import com.example.artemisrdp.model.ExperienceMode
import com.example.artemisrdp.model.RdpConnection
import com.example.artemisrdp.model.SoundOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface ConnectionRepository {
    val connections: StateFlow<List<RdpConnection>>
    suspend fun loadConnections()
    suspend fun saveConnection(connection: RdpConnection)
    suspend fun deleteConnection(id: String)
    suspend fun getConnectionById(id: String): RdpConnection?
    suspend fun updateLastConnected(id: String)
}

class DefaultConnectionRepository(private val context: Context) : ConnectionRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val file: File
        get() = File(context.filesDir, "rdp_connections.json")

    private val _connections = MutableStateFlow<List<RdpConnection>>(emptyList())
    override val connections: StateFlow<List<RdpConnection>> = _connections.asStateFlow()

    init {
        // Load initial connections
        if (!file.exists()) {
            val defaults = createDefaultConnections()
            _connections.value = defaults
            writeToFile(defaults)
        } else {
            _connections.value = readFromFile()
        }
    }

    override suspend fun loadConnections() = withContext(Dispatchers.IO) {
        val list = readFromFile()
        _connections.value = list
    }

    override suspend fun saveConnection(connection: RdpConnection) = withContext(Dispatchers.IO) {
        val current = _connections.value.toMutableList()
        val index = current.indexOfFirst { it.id == connection.id }
        if (index != -1) {
            current[index] = connection
        } else {
            current.add(0, connection)
        }
        _connections.value = current
        writeToFile(current)
    }

    override suspend fun deleteConnection(id: String) = withContext(Dispatchers.IO) {
        val current = _connections.value.filterNot { it.id == id }
        _connections.value = current
        writeToFile(current)
    }

    override suspend fun getConnectionById(id: String): RdpConnection? {
        return _connections.value.find { it.id == id }
    }

    override suspend fun updateLastConnected(id: String) = withContext(Dispatchers.IO) {
        val current = _connections.value.map {
            if (it.id == id) it.copy(lastConnectedTime = System.currentTimeMillis()) else it
        }
        _connections.value = current
        writeToFile(current)
    }

    private fun readFromFile(): List<RdpConnection> {
        return try {
            if (!file.exists()) emptyList()
            else {
                val text = file.readText()
                json.decodeFromString<List<RdpConnection>>(text)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun writeToFile(list: List<RdpConnection>) {
        try {
            val text = json.encodeToString(list)
            file.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createDefaultConnections(): List<RdpConnection> {
        return listOf(
            RdpConnection(
                id = "demo-pc-01",
                name = "Windows 11 Test Rig (Interactive Demo)",
                host = "127.0.0.1",
                port = 3389,
                username = "Administrator",
                domain = "WORKGROUP",
                resolution = DisplayResolution.FULL_HD,
                colorDepth = ColorDepth.HIGHEST_32,
                soundOption = SoundOption.LOCAL,
                enableClipboard = true,
                experienceMode = ExperienceMode.LAN,
                isDemo = true,
                iconColorHex = 0xFF0D47A1
            ),
            RdpConnection(
                id = "work-station-sample",
                name = "Office Workstation",
                host = "192.168.1.100",
                port = 3389,
                username = "artemis_user",
                domain = "CORP",
                resolution = DisplayResolution.FIT_SCREEN,
                colorDepth = ColorDepth.HIGHEST_32,
                soundOption = SoundOption.LOCAL,
                enableClipboard = true,
                experienceMode = ExperienceMode.BROADBAND,
                isDemo = false,
                iconColorHex = 0xFF2E7D32
            )
        )
    }
}
