package com.example.artemisrdp.model

enum class KeyModifier {
    CTRL,
    ALT,
    SHIFT,
    WIN
}

enum class PointerButton {
    NONE,
    LEFT,
    RIGHT,
    MIDDLE
}

enum class PointerAction {
    MOVE,
    BUTTON_DOWN,
    BUTTON_UP,
    SCROLL
}

data class RdpPointerEvent(
    val x: Int,
    val y: Int,
    val action: PointerAction,
    val button: PointerButton = PointerButton.NONE,
    val wheelDelta: Int = 0
)

data class RdpKeyEvent(
    val scancode: Int,
    val isDown: Boolean,
    val unicodeChar: Char? = null,
    val modifiers: Set<KeyModifier> = emptySet()
)
