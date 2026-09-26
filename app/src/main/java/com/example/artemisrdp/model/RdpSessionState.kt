package com.example.artemisrdp.model

enum class SessionStatus(val message: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting to host..."),
    NEGOTIATING_PROTOCOL("Negotiating RDP protocol & security..."),
    AUTHENTICATING("Authenticating credentials..."),
    CONNECTED("Connected"),
    RECONNECTING("Reconnecting..."),
    ERROR("Connection failed")
}

data class RdpSessionStats(
    val fps: Int = 0,
    val latencyMs: Long = 0L,
    val bytesReceived: Long = 0L,
    val bandwidthKbps: Double = 0.0,
    val resolutionWidth: Int = 1920,
    val resolutionHeight: Int = 1080
)

data class RdpSessionState(
    val status: SessionStatus = SessionStatus.DISCONNECTED,
    val errorMessage: String? = null,
    val stats: RdpSessionStats = RdpSessionStats(),
    val isMouseMode: Boolean = true, // true = Virtual Trackpad/Mouse pointer; false = Direct Touch
    val isKeyboardVisible: Boolean = false,
    val activeModifiers: Set<KeyModifier> = emptySet(),
    val zoomScale: Float = 1.0f,
    val panOffsetX: Float = 0.0f,
    val panOffsetY: Float = 0.0f
)
