package com.example.artemisrdp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.artemisrdp.data.ConnectionRepository
import com.example.artemisrdp.engine.RdpClient
import com.example.artemisrdp.engine.RdpInputHandler
import com.example.artemisrdp.model.RdpConnection
import com.example.artemisrdp.model.RdpKeyEvent
import com.example.artemisrdp.model.SessionStatus
import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import com.example.artemisrdp.ui.components.DesktopCanvas
import com.example.artemisrdp.ui.components.FloatingSessionToolbar
import com.example.artemisrdp.ui.components.SpecialKeysBar
import com.example.artemisrdp.ui.components.VirtualTrackpadOverlay

@Composable
fun SessionScreen(
    connectionId: String,
    repository: ConnectionRepository,
    onDisconnectComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var connection by remember { mutableStateOf<RdpConnection?>(null) }
    var client by remember { mutableStateOf<RdpClient?>(null) }

    LaunchedEffect(connectionId) {
        val found = repository.getConnectionById(connectionId)
        if (found != null) {
            connection = found
            val newClient = RdpClient(found)
            client = newClient
            newClient.connect()
            repository.updateLastConnected(connectionId)
        }
    }

    DisposableEffect(connectionId) {
        onDispose {
            client?.disconnect()
        }
    }

    BackHandler {
        client?.disconnect()
        onDisconnectComplete()
    }

    if (connection == null || client == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val currentClient = client!!
    val sessionState by currentClient.sessionState.collectAsState()

    var cursorX by remember { mutableIntStateOf(currentClient.desktopWidth / 2) }
    var cursorY by remember { mutableIntStateOf(currentClient.desktopHeight / 2) }

    // Hidden input for soft keyboard interception
    val keyboardFocusRequester = remember { FocusRequester() }
    var textInputState by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        when (sessionState.status) {
            SessionStatus.CONNECTING,
            SessionStatus.NEGOTIATING_PROTOCOL,
            SessionStatus.AUTHENTICATING -> {
                // Connection progress overlay
                ConnectingOverlay(
                    connection = connection!!,
                    statusMessage = sessionState.status.message,
                    onCancel = {
                        currentClient.disconnect()
                        onDisconnectComplete()
                    }
                )
            }

            SessionStatus.ERROR -> {
                // Error card
                ErrorOverlay(
                    errorMessage = sessionState.errorMessage ?: "Unknown error occurred.",
                    onRetry = { currentClient.connect() },
                    onExit = {
                        currentClient.disconnect()
                        onDisconnectComplete()
                    }
                )
            }

            SessionStatus.DISCONNECTED -> {
                ConnectingOverlay(
                    connection = connection!!,
                    statusMessage = "Session disconnected",
                    onCancel = {
                        onDisconnectComplete()
                    }
                )
            }

            SessionStatus.CONNECTED,
            SessionStatus.RECONNECTING -> {
                val liveWebUrl = connection?.webUrl
                if (!liveWebUrl.isNullOrBlank()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(android.graphics.Color.BLACK)
                                val cookieManager = android.webkit.CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                                isVerticalScrollBarEnabled = false
                                isHorizontalScrollBarEnabled = false
                                isFocusable = true
                                isFocusableInTouchMode = true

                                webViewClient = object : WebViewClient() {
                                    override fun onReceivedHttpAuthRequest(
                                        view: WebView?,
                                        handler: android.webkit.HttpAuthHandler?,
                                        host: String?,
                                        realm: String?
                                    ) {
                                        handler?.proceed(connection?.username ?: "RDP", connection?.password ?: "")
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                webViewRef = this
                                val customHtml = buildUltraHdWebRdpHtml()
                                loadDataWithBaseURL(liveWebUrl, customHtml, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Active session view
                    DesktopCanvas(
                        frameBuffer = currentClient.frameBuffer,
                        isMouseMode = sessionState.isMouseMode,
                        onPointerEvent = { currentClient.sendPointerEvent(it) },
                        onCursorPosChanged = { x, y ->
                            cursorX = x
                            cursorY = y
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Floating Session Toolbar
                FloatingSessionToolbar(
                    stats = sessionState.stats,
                    isMouseMode = sessionState.isMouseMode,
                    isKeyboardVisible = sessionState.isKeyboardVisible,
                    onToggleMouseMode = {
                        webViewRef?.evaluateJavascript("window.togglePointerMode()", null)
                        currentClient.toggleMouseMode()
                    },
                    onToggleKeyboard = {
                        currentClient.toggleKeyboard()
                    },
                    onResetZoom = {
                        webViewRef?.evaluateJavascript("window.resetZoom()", null)
                        currentClient.updateViewport(1f, 0f, 0f)
                    },
                    onDisconnect = {
                        currentClient.disconnect()
                        onDisconnectComplete()
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Virtual Trackpad Buttons (Bottom) - Hidden for Live Web GUI / Physical Mouse
                if (connection?.webUrl.isNullOrBlank() && sessionState.isMouseMode && !sessionState.isKeyboardVisible) {
                    VirtualTrackpadOverlay(
                        cursorX = cursorX,
                        cursorY = cursorY,
                        onPointerEvent = { currentClient.sendPointerEvent(it) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // Special Keys & Virtual Keyboard Accessory Bar
                AnimatedVisibility(
                    visible = sessionState.isKeyboardVisible,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column {
                        SpecialKeysBar(
                            activeModifiers = sessionState.activeModifiers,
                            onToggleModifier = { mod ->
                                currentClient.toggleModifier(mod)
                                val sc = when (mod) {
                                    com.example.artemisrdp.model.KeyModifier.CTRL -> 29
                                    com.example.artemisrdp.model.KeyModifier.ALT -> 56
                                    com.example.artemisrdp.model.KeyModifier.SHIFT -> 42
                                    com.example.artemisrdp.model.KeyModifier.WIN -> 57435
                                }
                                val isNowDown = !sessionState.activeModifiers.contains(mod)
                                webViewRef?.evaluateJavascript("window.sendScancode($sc, $isNowDown)", null)
                            },
                            onSendKey = { k ->
                                currentClient.sendKeyEvent(k)
                                val sc = when (k.scancode) {
                                    RdpInputHandler.SCANCODE_ESCAPE -> 1
                                    RdpInputHandler.SCANCODE_TAB -> 15
                                    RdpInputHandler.SCANCODE_RETURN -> 28
                                    RdpInputHandler.SCANCODE_BACKSPACE -> 14
                                    RdpInputHandler.SCANCODE_DELETE -> 57427
                                    RdpInputHandler.SCANCODE_WIN -> 57435
                                    else -> k.scancode
                                }
                                webViewRef?.evaluateJavascript("window.sendScancode($sc, true); setTimeout(() => window.sendScancode($sc, false), 60);", null)
                            },
                            onSendSpecialCombination = { combo ->
                                currentClient.sendSpecialCombination(combo)
                                when (combo) {
                                    "CTRL_ALT_DEL" -> webViewRef?.evaluateJavascript("window.sendCtrlAltDel()", null)
                                    "WIN_D" -> webViewRef?.evaluateJavascript("window.sendKeyCombo([57435, 32])", null)
                                    "ALT_TAB" -> webViewRef?.evaluateJavascript("window.sendKeyCombo([56, 15])", null)
                                }
                            }
                        )

                        // Hidden text field requesting focus to trigger OS software keyboard
                        BasicTextField(
                            value = textInputState,
                            onValueChange = { newStr ->
                                if (newStr.length > textInputState.length) {
                                    val addedChar = newStr.last()
                                    webViewRef?.evaluateJavascript("window.typeChar('${addedChar}')", null)
                                    currentClient.sendKeyEvent(
                                        RdpKeyEvent(
                                            scancode = 0,
                                            isDown = true,
                                            unicodeChar = addedChar,
                                            modifiers = sessionState.activeModifiers
                                        )
                                    )
                                    currentClient.sendKeyEvent(
                                        RdpKeyEvent(
                                            scancode = 0,
                                            isDown = false,
                                            unicodeChar = addedChar,
                                            modifiers = sessionState.activeModifiers
                                        )
                                    )
                                } else if (newStr.length < textInputState.length) {
                                    webViewRef?.evaluateJavascript("window.sendScancode(14, true); setTimeout(() => window.sendScancode(14, false), 50);", null)
                                    currentClient.sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_BACKSPACE, true))
                                    currentClient.sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_BACKSPACE, false))
                                }
                                textInputState = newStr
                            },
                            modifier = Modifier
                                .size(1.dp)
                                .focusRequester(keyboardFocusRequester),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    webViewRef?.evaluateJavascript("window.sendScancode(28, true); setTimeout(() => window.sendScancode(28, false), 50);", null)
                                    currentClient.sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_RETURN, true))
                                    currentClient.sendKeyEvent(RdpKeyEvent(RdpInputHandler.SCANCODE_RETURN, false))
                                }
                            )
                        )
                    }
                }

                // Auto-focus soft keyboard when toggled
                LaunchedEffect(sessionState.isKeyboardVisible) {
                    if (sessionState.isKeyboardVisible) {
                        try {
                            keyboardFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectingOverlay(
    connection: RdpConnection,
    statusMessage: String,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xE60D1117)),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.width(320.dp).padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(52.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = connection.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${connection.host}:${connection.port}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = statusMessage,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(10.dp)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    errorMessage: String,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xE60D1117)),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.width(340.dp).padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Connection Failed",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = onRetry, shape = RoundedCornerShape(10.dp)) {
                        Text("Retry Connection")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onExit, shape = RoundedCornerShape(10.dp)) {
                        Text("Back to Home")
                    }
                }
            }
        }
    }
}

private fun buildUltraHdWebRdpHtml(): String {
    return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
  <title>Artemis Ultra-HD Desktop</title>
  <link href="/css/bootstrap.min.css" rel="stylesheet">
  <link href="/css/webrdp.css" rel="stylesheet">
  <script src="/socket.io/socket.io.js"></script>
  <script src="/js/rle.js"></script>
  <script type="module" src="/js/webrdp.js"></script>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; -webkit-user-select: none; }
    html, body {
      width: 100%; height: 100%;
      background: #000;
      overflow: hidden;
      touch-action: none;
    }
    #header { display: none !important; }
    #main { display: none !important; }
    #viewport {
      position: relative;
      width: 100vw;
      height: 100vh;
      overflow: hidden;
      background: #000;
    }
    #desktopContainer {
      position: absolute;
      top: 0; left: 0;
      width: 1920px;
      height: 1080px;
      transform-origin: 0 0;
    }
    #myCanvas {
      display: block;
      width: 1920px;
      height: 1080px;
      image-rendering: -webkit-optimize-contrast;
      image-rendering: crisp-edges;
      background: #0d1117;
    }
    #mouseCursor {
      position: absolute;
      top: 0; left: 0;
      width: 26px; height: 26px;
      pointer-events: none;
      z-index: 99999;
      transform: translate(960px, 540px);
      filter: drop-shadow(1px 2px 4px rgba(0,0,0,0.8));
    }
  </style>
</head>
<body>
  <div id="viewport">
    <div id="desktopContainer">
      <canvas id="myCanvas" width="1920" height="1080"></canvas>
      <svg id="mouseCursor" viewBox="0 0 24 24" fill="none">
        <path d="M4 2L18 14L11.5 14.5L15 22L12 23.5L8.5 16L4 20L4 2Z" fill="white" stroke="#111" stroke-width="1.8" stroke-linejoin="round"/>
      </svg>
    </div>
  </div>

  <script type="module">
    const canvas = document.getElementById('myCanvas');
    const desktopContainer = document.getElementById('desktopContainer');
    const cursor = document.getElementById('mouseCursor');
    const viewport = document.getElementById('viewport');

    const DESKTOP_W = 1920;
    const DESKTOP_H = 1080;
    canvas.width = DESKTOP_W;
    canvas.height = DESKTOP_H;

    let baseScale = 1.0;
    let zoomLevel = 1.0;
    let panX = 0;
    let panY = 0;

    function applyTransform() {
      desktopContainer.style.transform = `translate(${'$'}{panX}px, ${'$'}{panY}px) scale(${'$'}{baseScale * zoomLevel})`;
    }

    function fitToScreen() {
      const vw = window.innerWidth;
      const vh = window.innerHeight;
      baseScale = Math.min(vw / DESKTOP_W, vh / DESKTOP_H);
      panX = (vw - DESKTOP_W * baseScale) / 2;
      panY = (vh - DESKTOP_H * baseScale) / 2;
      zoomLevel = 1.0;
      applyTransform();
    }

    window.addEventListener('resize', fitToScreen);
    fitToScreen();

    const client = window.WebRDP.createClient(canvas);
    client.connect((err) => {
      if (err) console.error("WebRDP connect err:", err);
      else console.log("WebRDP connected in 1080p Ultra-HD");
    });

    let pointerMode = 'trackpad';
    let curX = DESKTOP_W / 2;
    let curY = DESKTOP_H / 2;

    function updateCursor(x, y) {
      curX = Math.max(0, Math.min(DESKTOP_W - 1, x));
      curY = Math.max(0, Math.min(DESKTOP_H - 1, y));
      cursor.style.transform = `translate(${'$'}{curX}px, ${'$'}{curY}px)`;
    }
    updateCursor(curX, curY);

    let lastX = 0, lastY = 0;
    let touchStartTime = 0;
    let moved = false;
    let startDist = 0;
    let startZoom = 1.0;
    let touchCount = 0;

    viewport.addEventListener('touchstart', (e) => {
      touchCount = e.touches.length;
      if (touchCount === 1) {
        lastX = e.touches[0].clientX;
        lastY = e.touches[0].clientY;
        touchStartTime = Date.now();
        moved = false;
        if (pointerMode === 'touch') {
          const rect = canvas.getBoundingClientRect();
          const targetX = (lastX - rect.left) * (DESKTOP_W / rect.width);
          const targetY = (lastY - rect.top) * (DESKTOP_H / rect.height);
          updateCursor(targetX, targetY);
          if (client.socket) client.socket.emit("mouse", Math.round(targetX), Math.round(targetY), 1, true);
        }
      } else if (touchCount === 2) {
        const dx = e.touches[0].clientX - e.touches[1].clientX;
        const dy = e.touches[0].clientY - e.touches[1].clientY;
        startDist = Math.hypot(dx, dy);
        startZoom = zoomLevel;
        lastX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
        lastY = (e.touches[0].clientY + e.touches[1].clientY) / 2;
      }
    }, { passive: false });

    viewport.addEventListener('touchmove', (e) => {
      e.preventDefault();
      if (e.touches.length === 1) {
        const dx = e.touches[0].clientX - lastX;
        const dy = e.touches[0].clientY - lastY;
        lastX = e.touches[0].clientX;
        lastY = e.touches[0].clientY;
        if (Math.hypot(dx, dy) > 3) moved = true;

        if (pointerMode === 'trackpad') {
          const sens = 1.6 / (baseScale * zoomLevel);
          updateCursor(curX + dx * sens, curY + dy * sens);
          if (client.socket) client.socket.emit("mouse", Math.round(curX), Math.round(curY), 0, false);
        } else if (pointerMode === 'touch') {
          const rect = canvas.getBoundingClientRect();
          const targetX = (lastX - rect.left) * (DESKTOP_W / rect.width);
          const targetY = (lastY - rect.top) * (DESKTOP_H / rect.height);
          updateCursor(targetX, targetY);
          if (client.socket) client.socket.emit("mouse", Math.round(targetX), Math.round(targetY), 0, false);
        }
      } else if (e.touches.length === 2) {
        const dx = e.touches[0].clientX - e.touches[1].clientX;
        const dy = e.touches[0].clientY - e.touches[1].clientY;
        const dist = Math.hypot(dx, dy);
        const midX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
        const midY = (e.touches[0].clientY + e.touches[1].clientY) / 2;

        if (Math.abs(dist - startDist) > 35) {
          zoomLevel = Math.max(1.0, Math.min(4.0, startZoom * (dist / startDist)));
          applyTransform();
        } else {
          const scrollDy = midY - lastY;
          if (client.socket && Math.abs(scrollDy) > 5) {
            client.socket.emit("wheel", Math.round(curX), Math.round(curY), Math.round(Math.abs(scrollDy)), scrollDy > 0, false);
          }
        }
        lastX = midX;
        lastY = midY;
      }
    }, { passive: false });

    viewport.addEventListener('touchend', (e) => {
      if (touchCount === 2 && e.touches.length === 0 && !moved) {
        if (client.socket) {
          client.socket.emit("mouse", Math.round(curX), Math.round(curY), 2, true);
          setTimeout(() => client.socket.emit("mouse", Math.round(curX), Math.round(curY), 2, false), 60);
        }
      } else if (e.touches.length === 0) {
        const duration = Date.now() - touchStartTime;
        if (!moved && duration < 350) {
          if (client.socket) {
            client.socket.emit("mouse", Math.round(curX), Math.round(curY), 1, true);
            setTimeout(() => client.socket.emit("mouse", Math.round(curX), Math.round(curY), 1, false), 60);
          }
        } else if (pointerMode === 'touch') {
          if (client.socket) client.socket.emit("mouse", Math.round(curX), Math.round(curY), 1, false);
        }
      }
      touchCount = e.touches.length;
    });

    window.togglePointerMode = function() {
      pointerMode = (pointerMode === 'trackpad') ? 'touch' : 'trackpad';
      cursor.style.display = (pointerMode === 'trackpad') ? 'block' : 'none';
      return pointerMode;
    };
    window.toggleZoom = function() {
      if (zoomLevel > 1.2) {
        zoomLevel = 1.0;
        fitToScreen();
      } else {
        zoomLevel = 2.0;
        applyTransform();
      }
    };
    window.resetZoom = function() {
      fitToScreen();
    };
    window.sendScancode = function(code, isDown) {
      if (client.socket) client.socket.emit("scancode", code, isDown);
    };
    window.sendKeyCombo = function(codes) {
      if (!client.socket) return;
      codes.forEach(c => client.socket.emit("scancode", c, true));
      setTimeout(() => {
        codes.slice().reverse().forEach(c => client.socket.emit("scancode", c, false));
      }, 80);
    };
    window.sendWinKey = function() {
      window.sendKeyCombo([57435]);
    };
    window.sendCtrlAltDel = function() {
      window.sendKeyCombo([29, 56, 57427]);
    };
    window.typeChar = function(ch) {
      if (!client.socket) return;
      const upper = ch.toUpperCase();
      if (upper >= 'A' && upper <= 'Z') {
        const sc = 30 + (upper.charCodeAt(0) - 65);
        client.socket.emit("scancode", sc, true);
        setTimeout(() => client.socket.emit("scancode", sc, false), 30);
      } else if (ch === ' ') {
        client.socket.emit("scancode", 57, true);
        setTimeout(() => client.socket.emit("scancode", 57, false), 30);
      } else if (ch === '\n') {
        client.socket.emit("scancode", 28, true);
        setTimeout(() => client.socket.emit("scancode", 28, false), 30);
      }
    };
  </script>
</body>
</html>
    """.trimIndent()
}
