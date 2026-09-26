package com.example.artemisrdp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.artemisrdp.model.PointerAction
import com.example.artemisrdp.model.PointerButton
import com.example.artemisrdp.model.RdpPointerEvent

@Composable
fun VirtualTrackpadOverlay(
    cursorX: Int,
    cursorY: Int,
    onPointerEvent: (RdpPointerEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color(0xDD181E28))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Click button
        Button(
            onClick = {
                onPointerEvent(RdpPointerEvent(cursorX, cursorY, PointerAction.BUTTON_DOWN, PointerButton.LEFT))
                onPointerEvent(RdpPointerEvent(cursorX, cursorY, PointerAction.BUTTON_UP, PointerButton.LEFT))
            },
            modifier = Modifier.weight(1f).height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E384D),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Left Click", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        // Scroll Wheel Drag Area
        Box(
            modifier = Modifier
                .weight(0.7f)
                .height(46.dp)
                .background(Color(0xFF222834), RoundedCornerShape(10.dp))
                .pointerInput(cursorX, cursorY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = (dragAmount.y * -1).toInt()
                        if (delta != 0) {
                            onPointerEvent(RdpPointerEvent(cursorX, cursorY, PointerAction.SCROLL, wheelDelta = delta))
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("Scroll ⇅", fontSize = 12.sp, color = Color.LightGray)
        }

        // Right Click button
        FilledTonalButton(
            onClick = {
                onPointerEvent(RdpPointerEvent(cursorX, cursorY, PointerAction.BUTTON_DOWN, PointerButton.RIGHT))
                onPointerEvent(RdpPointerEvent(cursorX, cursorY, PointerAction.BUTTON_UP, PointerButton.RIGHT))
            },
            modifier = Modifier.weight(1f).height(46.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = Color(0xFF2E384D),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Right Click", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
