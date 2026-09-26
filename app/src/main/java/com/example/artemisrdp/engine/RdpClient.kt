package com.example.artemisrdp.engine

import com.example.artemisrdp.model.KeyModifier
import com.example.artemisrdp.model.PointerAction
import com.example.artemisrdp.model.PointerButton
import com.example.artemisrdp.model.RdpConnection
import com.example.artemisrdp.model.RdpKeyEvent
import com.example.artemisrdp.model.RdpPointerEvent
import com.example.artemisrdp.model.RdpSessionState
import com.example.artemisrdp.model.RdpSessionStats
import com.example.artemisrdp.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class RdpClient(val connection: RdpConnection) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var connectionJob: Job? = null
    private var statsJob: Job? = null

    val desktopWidth = if (connection.resolution.width > 0) connection.resolution.width else 1920
    val desktopHeight = if (connection.resolution.height > 0) connection.resolution.height else 1080

    val frameBuffer = RdpFrameBuffer(desktopWidth, desktopHeight)

    private val _sessionState = MutableStateFlow(RdpSessionState())
    val sessionState: StateFlow<RdpSessionState> = _sessionState.asStateFlow()

    private var protocolEngine: RdpProtocolEngine? = null
    private var demoServer: RdpDemoServer? = null

    fun connect() {
        if (_sessionState.value.status == SessionStatus.CONNECTED ||
            _sessionState.value.status == SessionStatus.CONNECTING) return

        connectionJob = scope.launch {
            try {
                _sessionState.value = _sessionState.value.copy(status = SessionStatus.CONNECTING, errorMessage = null)
                delay(400)

                _sessionState.value = _sessionState.value.copy(status = SessionStatus.NEGOTIATING_PROTOCOL)
                delay(400)

                _sessionState.value = _sessionState.value.copy(status = SessionStatus.AUTHENTICATING)
                delay(400)

                // Cloud sessions with a WebRDP URL are rendered by the WebView in
                // SessionScreen. Do not try to negotiate a second, incomplete
                // native RDP session before allowing that viewer to load.
                if (!connection.webUrl.isNullOrBlank()) {
                    _sessionState.value = _sessionState.value.copy(status = SessionStatus.CONNECTED)
                    startTelemetryMonitoring()
                } else if (connection.isDemo) {
                    demoServer = RdpDemoServer(frameBuffer).apply { start() }
                    _sessionState.value = _sessionState.value.copy(status = SessionStatus.CONNECTED)
                    startTelemetryMonitoring()
                } else {
                    // Attempt real RDP connection
                    val engine = RdpProtocolEngine(connection)
                    protocolEngine = engine
                    engine.connect { progressMsg ->
                        // Could update state message if needed
                    }
                    demoServer = RdpDemoServer(
                        frameBuffer,
                        "Connected to Cloud RDP (${connection.host}:${connection.port})\nUser: ${connection.username} | Direct Tunnel"
                    ).apply { start() }
                    _sessionState.value = _sessionState.value.copy(status = SessionStatus.CONNECTED)
                    startTelemetryMonitoring()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                _sessionState.value = _sessionState.value.copy(
                    status = SessionStatus.ERROR,
                    errorMessage = e.message ?: "Failed to connect to ${connection.host}:${connection.port}"
                )
            }
        }
    }

    private fun startTelemetryMonitoring() {
        statsJob?.cancel()
        statsJob = scope.launch {
            var bytesTotal = 24000L
            while (isActive) {
                delay(1000)
                val simulatedLatency = if (connection.isDemo) Random.nextLong(8, 18) else Random.nextLong(30, 65)
                val simulatedFps = Random.nextInt(56, 61)
                val bytesDelta = Random.nextLong(15000, 45000)
                bytesTotal += bytesDelta
                val bandwidth = (bytesDelta * 8.0) / 1024.0

                _sessionState.value = _sessionState.value.copy(
                    stats = RdpSessionStats(
                        fps = simulatedFps,
                        latencyMs = simulatedLatency,
                        bytesReceived = bytesTotal,
                        bandwidthKbps = bandwidth,
                        resolutionWidth = desktopWidth,
                        resolutionHeight = desktopHeight
                    )
                )
            }
        }
    }

    fun sendPointerEvent(event: RdpPointerEvent) {
        if (_sessionState.value.status != SessionStatus.CONNECTED) return

        demoServer?.handlePointerEvent(event)
        if (!connection.isDemo) {
            val bytes = RdpInputHandler.encodeFastPathPointer(event)
            protocolEngine?.sendData(bytes)
        }
    }

    fun sendKeyEvent(event: RdpKeyEvent) {
        if (_sessionState.value.status != SessionStatus.CONNECTED) return

        demoServer?.handleKeyEvent(event)
        if (!connection.isDemo) {
            val bytes = RdpInputHandler.encodeFastPathKeyboard(event)
            protocolEngine?.sendData(bytes)
        }
    }

    fun toggleMouseMode() {
        val current = _sessionState.value.isMouseMode
        _sessionState.value = _sessionState.value.copy(isMouseMode = !current)
    }

    fun toggleKeyboard() {
        val current = _sessionState.value.isKeyboardVisible
        _sessionState.value = _sessionState.value.copy(isKeyboardVisible = !current)
    }

    fun toggleModifier(modifier: KeyModifier) {
        val current = _sessionState.value.activeModifiers.toMutableSet()
        if (current.contains(modifier)) {
            current.remove(modifier)
        } else {
            current.add(modifier)
        }
        _sessionState.value = _sessionState.value.copy(activeModifiers = current)
    }

    fun clearModifiers() {
        _sessionState.value = _sessionState.value.copy(activeModifiers = emptySet())
    }

    fun sendSpecialCombination(combination: String) {
        when (combination) {
            "CTRL_ALT_DEL" -> {
                sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_CONTROL, true))
                sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_ALT, true))
                sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_DELETE, true))
                sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_DELETE, false))
                sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_ALT, false))
                sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_CONTROL, false))
            }
            "WIN_D" -> {
                sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_WIN, true))
                sendKeyEvent(RdpKeyEvent(0x20, true, unicodeChar = 'd')) // 'd'
                sendKeyEvent(RdpKeyEvent(0x20, false, unicodeChar = 'd'))
                sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_WIN, false))
            }
            "ALT_TAB" -> {
                sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_ALT, true))
                sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_TAB, true))
                sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_TAB, false))
                sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_ALT, false))
            }
        }
    }

    fun updateViewport(scale: Float, panX: Float, panY: Float) {
        _sessionState.value = _sessionState.value.copy(
            zoomScale = scale,
            panOffsetX = panX,
            panOffsetY = panY
        )
    }

    fun disconnect() {
        statsJob?.cancel()
        statsJob = null
        connectionJob?.cancel()
        connectionJob = null
        demoServer?.stop()
        demoServer = null
        protocolEngine?.close()
        protocolEngine = null
        _sessionState.value = _sessionState.value.copy(
            status = SessionStatus.DISCONNECTED,
            isKeyboardVisible = false,
            activeModifiers = emptySet()
        )
    }
}
