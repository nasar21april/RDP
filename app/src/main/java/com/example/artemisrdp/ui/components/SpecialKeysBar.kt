package com.example.artemisrdp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.artemisrdp.engine.RdpInputHandler
import com.example.artemisrdp.model.KeyModifier
import com.example.artemisrdp.model.RdpKeyEvent

@Composable
fun SpecialKeysBar(
    activeModifiers: Set<KeyModifier>,
    onToggleModifier: (KeyModifier) -> Unit,
    onSendKey: (RdpKeyEvent) -> Unit,
    onSendSpecialCombination: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color(0xE6141820))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Modifier keys with toggled active state
        ModifierKeyChip(
            label = "Ctrl",
            isActive = activeModifiers.contains(KeyModifier.CTRL),
            onClick = { onToggleModifier(KeyModifier.CTRL) }
        )
        ModifierKeyChip(
            label = "Alt",
            isActive = activeModifiers.contains(KeyModifier.ALT),
            onClick = { onToggleModifier(KeyModifier.ALT) }
        )
        ModifierKeyChip(
            label = "Shift",
            isActive = activeModifiers.contains(KeyModifier.SHIFT),
            onClick = { onToggleModifier(KeyModifier.SHIFT) }
        )
        ModifierKeyChip(
            label = "Win",
            isActive = activeModifiers.contains(KeyModifier.WIN),
            onClick = { onToggleModifier(KeyModifier.WIN) }
        )

        // Common navigation & action keys
        SimpleKeyButton(label = "Esc") {
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_ESCAPE, true))
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_ESCAPE, false))
        }
        SimpleKeyButton(label = "Tab") {
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_TAB, true))
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_TAB, false))
        }
        SimpleKeyButton(label = "Del") {
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_DELETE, true))
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_DELETE, false))
        }
        SimpleKeyButton(label = "Enter") {
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_RETURN, true))
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_RETURN, false))
        }

        // Directional arrows
        SimpleKeyButton(label = "▲") {
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_UP, true))
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_UP, false))
        }
        SimpleKeyButton(label = "▼") {
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_DOWN, true))
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_DOWN, false))
        }
        SimpleKeyButton(label = "◀") {
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_LEFT, true))
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_LEFT, false))
        }
        SimpleKeyButton(label = "▶") {
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_RIGHT, true))
            onSendKey(RdpKeyEvent(RdpInputHandler.SCANCODE_RIGHT, false))
        }

        // Function shortcuts
        SpecialComboButton(label = "Ctrl+Alt+Del") {
            onSendSpecialCombination("CTRL_ALT_DEL")
        }
        SpecialComboButton(label = "Win+D") {
            onSendSpecialCombination("WIN_D")
        }
        SpecialComboButton(label = "Alt+Tab") {
            onSendSpecialCombination("ALT_TAB")
        }
    }
}

@Composable
private fun ModifierKeyChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    if (isActive) {
        ElevatedButton(
            onClick = onClick,
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Text(label, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SimpleKeyButton(
    label: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF2C3240),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
private fun SpecialComboButton(
    label: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF1E3A5F),
            contentColor = Color(0xFF90CAF9)
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}
