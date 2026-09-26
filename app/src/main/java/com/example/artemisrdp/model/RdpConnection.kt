package com.example.artemisrdp.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class DisplayResolution(val label: String, val width: Int, val height: Int) {
    FIT_SCREEN("Fit to Screen", 0, 0),
    FULL_HD("1080p (1920x1080)", 1920, 1080),
    HD("720p (1280x720)", 1280, 720),
    WXGA("1366x768", 1366, 768),
    CUSTOM("Custom Resolution", 0, 0)
}

@Serializable
enum class ColorDepth(val bpp: Int, val label: String) {
    HIGH_COLOR_16(16, "High Color (16-bit)"),
    TRUE_COLOR_24(24, "True Color (24-bit)"),
    HIGHEST_32(32, "Highest Quality (32-bit)")
}

@Serializable
enum class SoundOption(val label: String) {
    LOCAL("Play on this device"),
    REMOTE("Leave on remote computer"),
    MUTE("Do not play")
}

@Serializable
enum class ExperienceMode(val label: String) {
    LAN("LAN / High Speed (Full visual styles)"),
    BROADBAND("Broadband (Balanced)"),
    LOW_SPEED("Low Speed (Optimized for speed)")
}

@Serializable
data class RdpConnection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 3389,
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val resolution: DisplayResolution = DisplayResolution.FIT_SCREEN,
    val customWidth: Int = 1920,
    val customHeight: Int = 1080,
    val colorDepth: ColorDepth = ColorDepth.HIGHEST_32,
    val soundOption: SoundOption = SoundOption.LOCAL,
    val enableClipboard: Boolean = true,
    val experienceMode: ExperienceMode = ExperienceMode.LAN,
    val isDemo: Boolean = false,
    val lastConnectedTime: Long = 0L,
    val iconColorHex: Long = 0xFF2196F3,
    val webUrl: String? = null
) {
    val displaySubtitle: String
        get() = if (isDemo) "Interactive Demo Desktop (Built-in)" else if (!webUrl.isNullOrBlank()) "Live Cloud GUI Desktop" else "$host:$port"
}
