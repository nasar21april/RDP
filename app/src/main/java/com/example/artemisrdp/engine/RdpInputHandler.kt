package com.example.artemisrdp.engine

import com.example.artemisrdp.model.KeyModifier
import com.example.artemisrdp.model.PointerAction
import com.example.artemisrdp.model.PointerButton
import com.example.artemisrdp.model.RdpKeyEvent
import com.example.artemisrdp.model.RdpPointerEvent
import java.io.ByteArrayOutputStream

/**
 * Handles mapping of touch/mouse events and keyboard inputs to RDP FastPath and scancode packets.
 * Standard Windows Scan Codes (Set 1):
 * ESC = 0x01, ENTER = 0x1C, LCTRL = 0x1D, LSHIFT = 0x2A, LALT = 0x38, LWIN = 0x5B (ext 0xE05B),
 * DEL = 0x53 (ext 0xE053), TAB = 0x0F, BACKSPACE = 0x0E, SPACE = 0x39.
 */
object RdpInputHandler {

    // FastPath Mouse flags (TS_FP_POINTEREVENT)
    const val PTRFLAGS_DOWN = 0x8000
    const val PTRFLAGS_BUTTON1 = 0x1000 // Left
    const val PTRFLAGS_BUTTON2 = 0x2000 // Right
    const val PTRFLAGS_BUTTON3 = 0x4000 // Middle
    const val PTRFLAGS_WHEEL = 0x0200
    const val PTRFLAGS_WHEEL_NEGATIVE = 0x0100
    const val PTRFLAGS_MOVE = 0x0800

    // Common Scancodes
    const val SCANCODE_ESCAPE = 0x01
    const val SCANCODE_RETURN = 0x1C
    const val SCANCODE_TAB = 0x0F
    const val SCANCODE_BACKSPACE = 0x0E
    const val SCANCODE_SPACE = 0x39
    const val SCANCODE_CONTROL = 0x1D
    const val SCANCODE_ALT = 0x38
    const val SCANCODE_SHIFT = 0x2A
    const val SCANCODE_WIN = 0x5B
    const val SCANCODE_DELETE = 0x53
    const val SCANCODE_UP = 0x48
    const val SCANCODE_DOWN = 0x50
    const val SCANCODE_LEFT = 0x4B
    const val SCANCODE_RIGHT = 0x4D

    /**
     * Encodes a pointer event into an RDP FastPath input PDU byte array.
     */
    fun encodeFastPathPointer(event: RdpPointerEvent): ByteArray {
        var flags = 0
        when (event.action) {
            PointerAction.MOVE -> flags = flags or PTRFLAGS_MOVE
            PointerAction.BUTTON_DOWN -> {
                flags = flags or PTRFLAGS_DOWN
                when (event.button) {
                    PointerButton.LEFT -> flags = flags or PTRFLAGS_BUTTON1
                    PointerButton.RIGHT -> flags = flags or PTRFLAGS_BUTTON2
                    PointerButton.MIDDLE -> flags = flags or PTRFLAGS_BUTTON3
                    PointerButton.NONE -> {}
                }
            }
            PointerAction.BUTTON_UP -> {
                when (event.button) {
                    PointerButton.LEFT -> flags = flags or PTRFLAGS_BUTTON1
                    PointerButton.RIGHT -> flags = flags or PTRFLAGS_BUTTON2
                    PointerButton.MIDDLE -> flags = flags or PTRFLAGS_BUTTON3
                    PointerButton.NONE -> {}
                }
            }
            PointerAction.SCROLL -> {
                flags = flags or PTRFLAGS_WHEEL
                if (event.wheelDelta < 0) {
                    flags = flags or PTRFLAGS_WHEEL_NEGATIVE
                }
                flags = flags or (kotlin.math.abs(event.wheelDelta) and 0xFF)
            }
        }

        val out = ByteArrayOutputStream()
        // FastPath Input header: length + event type (FASTPATH_INPUT_EVENT_MOUSE = 0x00)
        val x = event.x.coerceIn(0, 0xFFFF)
        val y = event.y.coerceIn(0, 0xFFFF)

        out.write(flags and 0xFF)
        out.write((flags shr 8) and 0xFF)
        out.write(x and 0xFF)
        out.write((x shr 8) and 0xFF)
        out.write(y and 0xFF)
        out.write((y shr 8) and 0xFF)
        return out.toByteArray()
    }

    /**
     * Encodes a key event into an RDP FastPath keyboard event PDU.
     */
    fun encodeFastPathKeyboard(event: RdpKeyEvent): ByteArray {
        var flags = 0
        if (!event.isDown) {
            flags = flags or 0x01 // KBDFLAGS_RELEASE
        }
        val out = ByteArrayOutputStream()
        out.write(flags and 0xFF)
        out.write(event.scancode and 0xFF)
        return out.toByteArray()
    }
}
