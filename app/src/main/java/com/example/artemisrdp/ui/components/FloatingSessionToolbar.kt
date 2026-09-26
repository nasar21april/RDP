package com.example.artemisrdp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.artemisrdp.model.RdpSessionStats

@Composable
fun FloatingSessionToolbar(
    stats: RdpSessionStats,
    isMouseMode: Boolean,
    isKeyboardVisible: Boolean,
    onToggleMouseMode: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onResetZoom: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier.padding(top = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xDD181E28),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Stats indicator chip
            Box(
                modifier = Modifier
                    .background(Color(0xFF263238), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${stats.fps} FPS • ${stats.latencyMs}ms",
                    color = Color(0xFF81C784),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Touch vs Mouse mode
                    IconButton(
                        onClick = onToggleMouseMode,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (isMouseMode) Color(0xFF64B5F6) else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isMouseMode) Icons.Default.Mouse else Icons.Default.TouchApp,
                            contentDescription = if (isMouseMode) "Mouse Trackpad Mode" else "Direct Touch Mode"
                        )
                    }

                    // Keyboard toggle
                    IconButton(
                        onClick = onToggleKeyboard,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = if (isKeyboardVisible) MaterialTheme.colorScheme.primary else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isKeyboardVisible) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                            contentDescription = "Toggle Keyboard"
                        )
                    }

                    // Reset Zoom
                    IconButton(
                        onClick = onResetZoom,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitScreen,
                            contentDescription = "Reset Zoom to Fit"
                        )
                    }

                    // Disconnect
                    IconButton(
                        onClick = onDisconnect,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = Color(0xFFEF5350)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Disconnect Session"
                        )
                    }
                }
            }

            // Expand/Collapse toggle button
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.LightGray)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.MoreVert,
                    contentDescription = "Toggle Toolbar",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
