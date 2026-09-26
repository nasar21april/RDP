package com.example.artemisrdp.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.artemisrdp.engine.RdpFrameBuffer
import com.example.artemisrdp.model.PointerAction
import com.example.artemisrdp.model.PointerButton
import com.example.artemisrdp.model.RdpPointerEvent
import kotlin.math.roundToInt

@Composable
fun DesktopCanvas(
    frameBuffer: RdpFrameBuffer,
    isMouseMode: Boolean,
    onPointerEvent: (RdpPointerEvent) -> Unit,
    onCursorPosChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Observe buffer revision for invalidation
    val revision by frameBuffer.frameRevision.collectAsState()

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var virtualCursorX by remember { mutableIntStateOf(frameBuffer.width / 2) }
    var virtualCursorY by remember { mutableIntStateOf(frameBuffer.height / 2) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Gesture detector for pinch-to-zoom and pan / trackpad movement
            .pointerInput(isMouseMode, scale, offsetX, offsetY, virtualCursorX, virtualCursorY) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) {
                        scale = (scale * zoom).coerceIn(0.5f, 5.0f)
                    } else if (isMouseMode) {
                        // Trackpad relative cursor movement
                        val sensitivity = 1.35f / scale
                        val newX = (virtualCursorX + pan.x * sensitivity).roundToInt().coerceIn(0, frameBuffer.width)
                        val newY = (virtualCursorY + pan.y * sensitivity).roundToInt().coerceIn(0, frameBuffer.height)
                        virtualCursorX = newX
                        virtualCursorY = newY
                        onCursorPosChanged(newX, newY)
                        onPointerEvent(RdpPointerEvent(newX, newY, PointerAction.MOVE))
                    } else {
                        // In direct touch mode, two-finger or dragging pans the viewport
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
            }
            .pointerInput(isMouseMode, scale, offsetX, offsetY, virtualCursorX, virtualCursorY) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        if (isMouseMode) {
                            // Single tap = Left Click at virtual cursor
                            onPointerEvent(RdpPointerEvent(virtualCursorX, virtualCursorY, PointerAction.BUTTON_DOWN, PointerButton.LEFT))
                            onPointerEvent(RdpPointerEvent(virtualCursorX, virtualCursorY, PointerAction.BUTTON_UP, PointerButton.LEFT))
                        } else {
                            // Direct touch: map tapOffset to desktop coordinates
                            val mapped = mapScreenToDesktop(tapOffset, size, frameBuffer.width, frameBuffer.height, scale, offsetX, offsetY)
                            virtualCursorX = mapped.x
                            virtualCursorY = mapped.y
                            onCursorPosChanged(mapped.x, mapped.y)
                            onPointerEvent(RdpPointerEvent(mapped.x, mapped.y, PointerAction.MOVE))
                            onPointerEvent(RdpPointerEvent(mapped.x, mapped.y, PointerAction.BUTTON_DOWN, PointerButton.LEFT))
                            onPointerEvent(RdpPointerEvent(mapped.x, mapped.y, PointerAction.BUTTON_UP, PointerButton.LEFT))
                        }
                    },
                    onLongPress = { tapOffset ->
                        if (isMouseMode) {
                            // Long press = Right Click at virtual cursor
                            onPointerEvent(RdpPointerEvent(virtualCursorX, virtualCursorY, PointerAction.BUTTON_DOWN, PointerButton.RIGHT))
                            onPointerEvent(RdpPointerEvent(virtualCursorX, virtualCursorY, PointerAction.BUTTON_UP, PointerButton.RIGHT))
                        } else {
                            val mapped = mapScreenToDesktop(tapOffset, size, frameBuffer.width, frameBuffer.height, scale, offsetX, offsetY)
                            virtualCursorX = mapped.x
                            virtualCursorY = mapped.y
                            onCursorPosChanged(mapped.x, mapped.y)
                            onPointerEvent(RdpPointerEvent(mapped.x, mapped.y, PointerAction.MOVE))
                            onPointerEvent(RdpPointerEvent(mapped.x, mapped.y, PointerAction.BUTTON_DOWN, PointerButton.RIGHT))
                            onPointerEvent(RdpPointerEvent(mapped.x, mapped.y, PointerAction.BUTTON_UP, PointerButton.RIGHT))
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Reference revision to re-draw when server emits frames
            val currentRev = revision
            if (currentRev < 0) return@Canvas

            val viewWidth = size.width
            val viewHeight = size.height

            // Calculate fit-to-screen baseline
            val scaleFactorX = viewWidth / frameBuffer.width.toFloat()
            val scaleFactorY = viewHeight / frameBuffer.height.toFloat()
            val baseScale = minOf(scaleFactorX, scaleFactorY)

            val effectiveScale = baseScale * scale
            val destWidth = (frameBuffer.width * effectiveScale).roundToInt()
            val destHeight = (frameBuffer.height * effectiveScale).roundToInt()

            val baseX = (viewWidth - destWidth) / 2f + offsetX
            val baseY = (viewHeight - destHeight) / 2f + offsetY

            val srcRect = android.graphics.Rect(0, 0, frameBuffer.width, frameBuffer.height)
            val destRect = android.graphics.Rect(
                baseX.roundToInt(),
                baseY.roundToInt(),
                (baseX + destWidth).roundToInt(),
                (baseY + destHeight).roundToInt()
            )

            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                frameBuffer.withBuffer { bmp ->
                    nativeCanvas.drawBitmap(bmp, srcRect, destRect, null)
                }
            }
        }
    }
}

private fun mapScreenToDesktop(
    screenPos: Offset,
    screenSize: androidx.compose.ui.unit.IntSize,
    desktopWidth: Int,
    desktopHeight: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): IntOffset {
    val scaleFactorX = screenSize.width / desktopWidth.toFloat()
    val scaleFactorY = screenSize.height / desktopHeight.toFloat()
    val baseScale = minOf(scaleFactorX, scaleFactorY)
    val effectiveScale = baseScale * scale

    val destWidth = desktopWidth * effectiveScale
    val destHeight = desktopHeight * effectiveScale

    val baseX = (screenSize.width - destWidth) / 2f + offsetX
    val baseY = (screenSize.height - destHeight) / 2f + offsetY

    val desktopX = ((screenPos.x - baseX) / effectiveScale).roundToInt().coerceIn(0, desktopWidth)
    val desktopY = ((screenPos.y - baseY) / effectiveScale).roundToInt().coerceIn(0, desktopHeight)

    return IntOffset(desktopX, desktopY)
}
