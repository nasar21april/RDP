package com.example.artemisrdp.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.artemisrdp.R
import com.example.artemisrdp.data.CloudServerStatus
import com.example.artemisrdp.data.CloudSession
import com.example.artemisrdp.data.ConnectionRepository
import com.example.artemisrdp.data.GitHubCloudRdpManager
import com.example.artemisrdp.data.WorkflowStep
import com.example.artemisrdp.automation.RdpAutomationService
import com.example.artemisrdp.model.RdpConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: ConnectionRepository,
    cloudManager: GitHubCloudRdpManager,
    onConnect: (String) -> Unit,
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val connections by repository.connections.collectAsState()
    val cloudStatus by cloudManager.status.collectAsState()
    val steps by cloudManager.steps.collectAsState()
    val authUrl by cloudManager.tailscaleAuthUrl.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showQuickConnectDialog by remember { mutableStateOf(false) }
    var showCloudSettingsDialog by remember { mutableStateOf(false) }
    var showManualCredsDialog by remember { mutableStateOf(false) }
    var showTailscalePromptDialog by remember { mutableStateOf(false) }
    var pendingConnectAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var connectionToDelete by remember { mutableStateOf<RdpConnection?>(null) }
    val filteredConnections = connections.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.host.contains(searchQuery, ignoreCase = true)
    }

    LaunchedEffect(Unit) {
        val active = cloudManager.checkActiveServer()
        if (active != null) {
            val targetWebUrl = active.webUrl ?: "http://${active.host}:4200/rdp/host/127.0.0.1"
            val cloudConn = RdpConnection(
                id = "cloud_rdp_github",
                name = "Cloud PC (Windows 11)",
                host = active.host,
                port = active.port,
                username = active.username,
                password = active.password,
                iconColorHex = 0xFF00E676L,
                isDemo = false,
                webUrl = targetWebUrl
            )
            repository.saveConnection(cloudConn)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "RDP",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCloudSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cloud Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showQuickConnectDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Quick Connect",
                            tint = Color(0xFFFFB300)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddConnection,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add PC")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hero Card: 5-Hour Cloud PC (GitHub + Tailscale)
            item {
                CloudRdpCard(
                    status = cloudStatus,
                    steps = steps,
                    hasToken = cloudManager.token.isNotBlank(),
                    repoName = cloudManager.repo,
                    tailscaleAuthUrl = authUrl,
                    onConfigureToken = { showCloudSettingsDialog = true },
                    onManualCreds = { showManualCredsDialog = true },
                    onStartServer = {
                        coroutineScope.launch {
                            cloudManager.startServer()
                        }
                    },
                    onConnectSession = { session ->
                        pendingConnectAction = {
                            val targetWebUrl = session.webUrl ?: "http://${session.host}:4200/rdp/host/127.0.0.1"
                            val cloudConn = RdpConnection(
                                id = "cloud_rdp_github",
                                name = "Cloud PC (Windows 11)",
                                host = session.host,
                                port = session.port,
                                username = session.username,
                                password = session.password,
                                iconColorHex = 0xFF00E676L,
                                isDemo = false,
                                webUrl = targetWebUrl
                            )
                            coroutineScope.launch {
                                repository.saveConnection(cloudConn)
                            }
                            onConnect(cloudConn.id)
                        }
                        showTailscalePromptDialog = true
                    },
                    onLaunchWindowsApp = { session ->
                        pendingConnectAction = {
                            try {
                                clipboardManager.setText(AnnotatedString(session.password))
                                val automationReady = RdpAutomationService.isRunning
                                if (automationReady) {
                                    val automationIntent = Intent(RdpAutomationService.ACTION_TRIGGER)
                                        .setPackage(context.packageName)
                                        .putExtra(RdpAutomationService.EXTRA_HOST, session.host)
                                        .putExtra(RdpAutomationService.EXTRA_USERNAME, session.username)
                                        .putExtra(RdpAutomationService.EXTRA_PASSWORD, session.password)
                                    context.sendBroadcast(automationIntent)
                                }
                                val rdpUri = Uri.parse("rdp://full%20address=s:${session.host}:${session.port}&username=s:${session.username}")
                                val intent = Intent(Intent.ACTION_VIEW, rdpUri).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                                val message = if (automationReady) {
                                    "Opening Windows App and entering the session credentials…"
                                } else {
                                    "Opening Windows App. Enable RDP Auto-Connect in Accessibility settings to fill credentials automatically."
                                }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not launch Windows App: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showTailscalePromptDialog = true
                    },
                    onResetSession = {
                        coroutineScope.launch {
                            cloudManager.cancelServer()
                        }
                    }
                )
            }

            // 2. Search & Header for Saved PCs
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Saved Remote PCs",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${filteredConnections.size} PCs",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search remote PCs or IP addresses...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // 3. Saved Connections List
            if (filteredConnections.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "No saved remote connections.\nTap + to add one!" else "No connections match '$searchQuery'",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredConnections, key = { it.id }) { conn ->
                    ConnectionCard(
                        connection = conn,
                        onConnect = { onConnect(conn.id) },
                        onEdit = { onEditConnection(conn.id) },
                        onDelete = { connectionToDelete = conn }
                    )
                }
            }
        }
    }

    // Quick Connect Dialog
    if (showQuickConnectDialog) {
        QuickConnectDialog(
            onDismiss = { showQuickConnectDialog = false },
            onConnect = { host, port ->
                showQuickConnectDialog = false
                val quickConn = RdpConnection(
                    name = "Quick Connect ($host)",
                    host = host,
                    port = port
                )
                coroutineScope.launch {
                    repository.saveConnection(quickConn)
                    onConnect(quickConn.id)
                }
            }
        )
    }

    // Manual Credentials Dialog
    if (showManualCredsDialog) {
        ManualCredentialsDialog(
            onDismiss = { showManualCredsDialog = false },
            onSave = { ip, user, pass ->
                showManualCredsDialog = false
                cloudManager.setManualSession(ip, user, pass)
                Toast.makeText(context, "Session credentials updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Cloud Settings Dialog (GitHub Token & Repo)
    if (showCloudSettingsDialog) {
        CloudSettingsDialog(
            currentPat = cloudManager.token,
            currentRepo = cloudManager.repo,
            currentWorkflow = cloudManager.workflow,
            currentBranch = cloudManager.branch,
            onDismiss = { showCloudSettingsDialog = false },
            onSave = { pat, repo, workflow, branch ->
                cloudManager.token = pat
                cloudManager.repo = repo
                cloudManager.workflow = workflow
                cloudManager.branch = branch
                showCloudSettingsDialog = false
                Toast.makeText(context, "GitHub Cloud settings saved!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Confirmation Dialog
    connectionToDelete?.let { conn ->
        AlertDialog(
            onDismissRequest = { connectionToDelete = null },
            title = { Text("Delete Connection") },
            text = { Text("Are you sure you want to remove '${conn.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = conn.id
                        connectionToDelete = null
                        coroutineScope.launch {
                            repository.deleteConnection(id)
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { connectionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Tailscale Reminder Dialog
    if (showTailscalePromptDialog) {
        AlertDialog(
            onDismissRequest = {
                showTailscalePromptDialog = false
                pendingConnectAction = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Ensure Tailscale is Active",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This Cloud PC connects via your private Tailscale network.\n\nPlease make sure Tailscale is turned ON (Connected) on this device before connecting to avoid 'Unable to connect' (0x204) errors.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTailscalePromptDialog = false
                        pendingConnectAction?.invoke()
                        pendingConnectAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                ) {
                    Text("I'm Connected, Open PC", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val pm = context.packageManager
                                val launchIntent = pm.getLaunchIntentForPackage("com.tailscale.ipn")
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                } else {
                                    val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.tailscale.ipn")).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(playStoreIntent)
                                }
                            } catch (_: Exception) {}
                        }
                    ) {
                        Text("Open Tailscale")
                    }
                    TextButton(
                        onClick = {
                            showTailscalePromptDialog = false
                            pendingConnectAction = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
fun CloudRdpCard(
    status: CloudServerStatus,
    steps: List<WorkflowStep>,
    hasToken: Boolean,
    repoName: String,
    tailscaleAuthUrl: String?,
    onConfigureToken: () -> Unit,
    onManualCreds: () -> Unit,
    onStartServer: () -> Unit,
    onConnectSession: (CloudSession) -> Unit,
    onLaunchWindowsApp: (CloudSession) -> Unit,
    onResetSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showStepsList by remember { mutableStateOf(true) }

    val cardBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0F2027),
            Color(0xFF203A43),
            Color(0xFF2C5364)
        )
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF141E30)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBrush)
                .padding(18.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF00E676).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (status) {
                                is CloudServerStatus.Ready -> Icons.Default.CloudDone
                                is CloudServerStatus.Triggering,
                                is CloudServerStatus.RunningSteps -> Icons.Default.CloudSync
                                else -> Icons.Default.Cloud
                            },
                            contentDescription = null,
                            tint = when (status) {
                                is CloudServerStatus.Ready -> Color(0xFF00E676)
                                is CloudServerStatus.Failed -> Color(0xFFFF5252)
                                else -> Color(0xFF64B5F6)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Cloud PC",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "GitHub Actions Direct Cloud Tunnel",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Status Badge
                val badgeColor = when (status) {
                    is CloudServerStatus.Ready -> Color(0xFF00E676)
                    is CloudServerStatus.Failed -> Color(0xFFFF5252)
                    is CloudServerStatus.Idle -> if (hasToken) Color(0xFF64B5F6) else Color(0xFFFFB300)
                    is CloudServerStatus.Triggering -> Color(0xFFFFD54F)
                    is CloudServerStatus.RunningSteps -> Color(0xFF29B6F6)
                    is CloudServerStatus.Cancelling -> Color(0xFFFF9800)
                }
                val badgeText = when (status) {
                    is CloudServerStatus.Ready -> "ACTIVE 5H"
                    is CloudServerStatus.Failed -> "ERROR"
                    is CloudServerStatus.Idle -> if (hasToken) "STANDBY" else "SETUP NEEDED"
                    is CloudServerStatus.Triggering -> "STARTING"
                    is CloudServerStatus.RunningSteps -> "IN PROGRESS"
                    is CloudServerStatus.Cancelling -> "CANCELLING"
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Body Content based on status
            when (status) {
                is CloudServerStatus.Idle -> {
                    if (!hasToken) {
                        Text(
                            text = "Connect your GitHub account to trigger your automated 5-hour temporary Windows RDP server on demand.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onConfigureToken,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Configure GitHub Token", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "Repository: $repoName\nTap below to dispatch the workflow, monitor steps live, and connect directly with zero extra setup.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onStartServer,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Launch Server", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onManualCreds,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Enter IP/Pass", color = Color.White)
                            }
                        }
                    }
                }

                is CloudServerStatus.Triggering,
                is CloudServerStatus.RunningSteps -> {
                    val progressMessage = when (status) {
                        is CloudServerStatus.Triggering -> status.message
                        is CloudServerStatus.RunningSteps -> status.message
                        else -> ""
                    }
                    val transition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by transition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                        label = "alpha"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF00E676),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = progressMessage,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = pulseAlpha),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Optional Tailscale Auth Button (only if Tailscale auth URL is returned)
                    if (tailscaleAuthUrl != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tailscaleAuthUrl))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🔗 Authenticate Tailscale on Mobile", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Workflow Steps Checklist
                    if (steps.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "GITHUB WORKFLOW STEPS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            steps.forEach { step ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    when (step.status) {
                                        "completed" -> {
                                            if (step.conclusion == "success") {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                            } else {
                                                Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        "in_progress" -> {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color(0xFFFFD54F), strokeWidth = 2.dp)
                                        }
                                        else -> {
                                            Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = step.name,
                                        fontSize = 12.sp,
                                        color = if (step.status == "in_progress") Color(0xFFFFD54F) else Color.White.copy(alpha = 0.85f),
                                        fontWeight = if (step.status == "in_progress") FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onManualCreds,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Enter IP/Pass", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = onResetSession,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel Server", color = Color(0xFFFF8A80), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                is CloudServerStatus.Ready -> {
                    val session = status.session
                    var remainingTimeText by remember { mutableStateOf("") }
                    var passwordVisible by remember { mutableStateOf(false) }

                    // Countdown ticker
                    LaunchedEffect(session.expiresAt) {
                        while (true) {
                            val rem = session.remainingMillis
                            if (rem <= 0) {
                                remainingTimeText = "Session Expired"
                                break
                            }
                            val hours = rem / 3600000
                            val minutes = (rem % 3600000) / 60000
                            val seconds = (rem % 60000) / 1000
                            remainingTimeText = String.format(Locale.getDefault(), "%02d:%02d:%02d remaining", hours, minutes, seconds)
                            delay(1000)
                        }
                    }

                    // Timer Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = remainingTimeText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF00E676),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(onClick = onResetSession, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "New Session", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Credentials Details
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // IP Address
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("SERVER ADDRESS", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                Text(session.ip, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(session.ip))
                                    Toast.makeText(context, "Copied Address: ${session.ip}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Address", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            }
                        }

                        // Username
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("USERNAME", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                Text(session.username, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(session.username))
                                    Toast.makeText(context, "Copied Username", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy User", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            }
                        }

                        // Password
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("PASSWORD", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (passwordVisible) session.password else "••••••••••••",
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Password",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(session.password))
                                        Toast.makeText(context, "Copied Password", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Password", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Connect In-App Desktop (1080p Ultra-HD)
                    Button(
                        onClick = { onConnectSession(session) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🚀 Launch In-App Desktop (1080p Ultra-HD)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Launch in External Windows App
                    OutlinedButton(
                        onClick = { onLaunchWindowsApp(session) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF29B6F6)
                        )
                    ) {
                        Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF29B6F6))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("⚡ Launch in Windows App", fontWeight = FontWeight.SemiBold, color = Color(0xFF29B6F6))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stop & Cancel Server Button
                    OutlinedButton(
                        onClick = onResetSession,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFFF5252)
                        )
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFFF5252))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop & Cancel Server", fontWeight = FontWeight.SemiBold, color = Color(0xFFFF5252))
                    }
                }

                is CloudServerStatus.Cancelling -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFFFF9800),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = status.message,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Please wait while GitHub stops the workflow and releases cloud resources...",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                is CloudServerStatus.Failed -> {
                    Text(
                        text = "Status: ${status.error}",
                        fontSize = 13.sp,
                        color = Color(0xFFFF8A80),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onManualCreds,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Enter IP/Pass", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onStartServer,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ManualCredentialsDialog(
    onDismiss: () -> Unit,
    onSave: (ip: String, user: String, pass: String) -> Unit
) {
    var ip by remember { mutableStateOf("100.112.157.47") }
    var user by remember { mutableStateOf("RDP") }
    var pass by remember { mutableStateOf("Uqh(A>1=W6;c0kE3") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloud PC Credentials") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Verify or paste the Tailscale IP, Username, and Password from your GitHub workflow console.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Tailscale IP") },
                    placeholder = { Text("100.x.y.z") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username") },
                    placeholder = { Text("RDP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    placeholder = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(ip.trim(), user.trim(), pass.trim()) },
                enabled = ip.isNotBlank() && pass.isNotBlank()
            ) {
                Text("Confirm & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CloudSettingsDialog(
    currentPat: String,
    currentRepo: String,
    currentWorkflow: String,
    currentBranch: String,
    onDismiss: () -> Unit,
    onSave: (pat: String, repo: String, workflow: String, branch: String) -> Unit
) {
    var pat by remember { mutableStateOf(currentPat) }
    var repo by remember { mutableStateOf(currentRepo) }
    var workflow by remember { mutableStateOf(currentWorkflow) }
    var branch by remember { mutableStateOf(currentBranch) }
    var patVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub Cloud Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your GitHub Personal Access Token (PAT) with 'workflow' or 'repo' permissions to automate triggering and log parsing.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pat,
                    onValueChange = { pat = it },
                    label = { Text("GitHub Token (PAT)") },
                    placeholder = { Text("ghp_...") },
                    visualTransformation = if (patVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { patVisible = !patVisible }) {
                            Icon(
                                imageVector = if (patVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    label = { Text("Repository (owner/repo)") },
                    placeholder = { Text("nasar21april/RDP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = workflow,
                        onValueChange = { workflow = it },
                        label = { Text("Workflow File") },
                        placeholder = { Text("main.yml") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = branch,
                        onValueChange = { branch = it },
                        label = { Text("Branch") },
                        placeholder = { Text("main") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(pat.trim(), repo.trim(), workflow.trim(), branch.trim()) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConnectionCard(
    connection: RdpConnection,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onConnect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color(connection.iconColorHex), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = connection.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (connection.isDemo) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00C853), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("DEMO", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = connection.displaySubtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (connection.lastConnectedTime > 0) {
                    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(connection.lastConnectedTime))
                    Text(
                        text = "Last connected: $dateStr",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Actions
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Connection", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Connection", tint = Color(0xFFEF5350))
            }
        }
    }
}

@Composable
fun QuickConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (String, Int) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("3389") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Connect") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the remote IP or hostname to connect immediately.")
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host / IP Address") },
                    placeholder = { Text("192.168.1.100") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portText.toIntOrNull() ?: 3389
                    if (host.isNotBlank()) {
                        onConnect(host.trim(), port)
                    }
                },
                enabled = host.isNotBlank()
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
