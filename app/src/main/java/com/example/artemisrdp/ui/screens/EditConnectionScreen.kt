package com.example.artemisrdp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.artemisrdp.data.ConnectionRepository
import com.example.artemisrdp.model.ColorDepth
import com.example.artemisrdp.model.DisplayResolution
import com.example.artemisrdp.model.ExperienceMode
import com.example.artemisrdp.model.RdpConnection
import com.example.artemisrdp.model.SoundOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditConnectionScreen(
    connectionId: String?,
    repository: ConnectionRepository,
    onSaveSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("3389") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var resolution by remember { mutableStateOf(DisplayResolution.FIT_SCREEN) }
    var colorDepth by remember { mutableStateOf(ColorDepth.HIGHEST_32) }
    var soundOption by remember { mutableStateOf(SoundOption.LOCAL) }
    var experienceMode by remember { mutableStateOf(ExperienceMode.LAN) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(connectionId) {
        if (!connectionId.isNullOrBlank() && connectionId != "new") {
            val existing = repository.getConnectionById(connectionId)
            if (existing != null) {
                name = existing.name
                host = existing.host
                port = existing.port.toString()
                username = existing.username
                password = existing.password
                domain = existing.domain
                resolution = existing.resolution
                colorDepth = existing.colorDepth
                soundOption = existing.soundOption
                experienceMode = existing.experienceMode
            }
        }
        isLoaded = true
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (connectionId == null || connectionId == "new") "Add Remote PC" else "Edit Connection",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("General Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Friendly Name") },
                placeholder = { Text("My Office Desktop") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("PC / Server Name or IP *") },
                    placeholder = { Text("192.168.1.100") },
                    modifier = Modifier.weight(2.5f),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Text("User Account", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                placeholder = { Text("Administrator") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Domain (Optional)") },
                placeholder = { Text("WORKGROUP or CORP") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Text("Display & Experience", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)

            // Display Resolution Dropdown
            var resExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = resExpanded,
                onExpandedChange = { resExpanded = !resExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = resolution.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Resolution") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = resExpanded,
                    onDismissRequest = { resExpanded = false }
                ) {
                    DisplayResolution.values().forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            onClick = {
                                resolution = item
                                resExpanded = false
                            }
                        )
                    }
                }
            }

            // Sound Redirection Dropdown
            var soundExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = soundExpanded,
                onExpandedChange = { soundExpanded = !soundExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = soundOption.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sound Redirection") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = soundExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = soundExpanded,
                    onDismissRequest = { soundExpanded = false }
                ) {
                    SoundOption.values().forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            onClick = {
                                soundOption = item
                                soundExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        val finalPort = port.toIntOrNull() ?: 3389
                        val finalName = if (name.isNotBlank()) name else host
                        val conn = RdpConnection(
                            id = if (!connectionId.isNullOrBlank() && connectionId != "new") connectionId else java.util.UUID.randomUUID().toString(),
                            name = finalName,
                            host = host.trim(),
                            port = finalPort,
                            username = username.trim(),
                            password = password,
                            domain = domain.trim(),
                            resolution = resolution,
                            colorDepth = colorDepth,
                            soundOption = soundOption,
                            experienceMode = experienceMode
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            repository.saveConnection(conn)
                            CoroutineScope(Dispatchers.Main).launch {
                                onSaveSuccess()
                            }
                        }
                    },
                    enabled = host.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save")
                }
            }
        }
    }
}
