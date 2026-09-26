package com.example.artemisrdp.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.example.artemisrdp.model.PointerAction
import com.example.artemisrdp.model.PointerButton
import com.example.artemisrdp.model.RdpKeyEvent
import com.example.artemisrdp.model.RdpPointerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RdpDemoServer(
    private val frameBuffer: RdpFrameBuffer,
    private val sessionLabel: String = "Connected to Artemis RDP Host"
) {

    private val width = frameBuffer.width
    private val height = frameBuffer.height

    private var cursorX = width / 2f
    private var cursorY = height / 2f

    private var isStartMenuOpen = false
    private var terminalText = StringBuilder("echo \"$sessionLabel\"\nSession established. Type on keyboard...\n> ")

    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // Paints
    private val bgPaint = Paint().apply { isAntiAlias = true }
    private val taskbarPaint = Paint().apply {
        color = Color.argb(220, 28, 33, 44)
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val monoPaint = Paint().apply {
        color = Color.parseColor("#4AF626")
        textSize = 24f
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
    }
    private val windowPaint = Paint().apply {
        color = Color.argb(245, 18, 22, 28)
        isAntiAlias = true
    }
    private val windowTitlePaint = Paint().apply {
        color = Color.argb(255, 36, 44, 58)
        isAntiAlias = true
    }
    private val cursorPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val cursorStroke = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    fun start() {
        renderFullDesktop()
        loopJob = scope.launch {
            while (isActive) {
                delay(1000)
                renderFullDesktop()
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    fun handlePointerEvent(event: RdpPointerEvent) {
        cursorX = event.x.toFloat().coerceIn(0f, width.toFloat())
        cursorY = event.y.toFloat().coerceIn(0f, height.toFloat())

        if (event.action == PointerAction.BUTTON_DOWN && event.button == PointerButton.LEFT) {
            val taskbarY = height - 72f
            // Check click on Start Menu icon (center around x = 860..910 on taskbar)
            val startButtonLeft = (width / 2f) - 180f
            val startButtonRight = (width / 2f) - 130f
            if (cursorY >= taskbarY && cursorX in startButtonLeft..startButtonRight) {
                isStartMenuOpen = !isStartMenuOpen
            } else if (isStartMenuOpen && (cursorY < taskbarY - 500f || cursorX < (width / 2f) - 300f || cursorX > (width / 2f) + 300f)) {
                isStartMenuOpen = false
            }
        }
        renderFullDesktop()
    }

    fun handleKeyEvent(event: RdpKeyEvent) {
        if (event.isDown) {
            when (event.scancode) {
                RdpInputHandler.SCANCODE_RETURN -> {
                    terminalText.append("\n> ")
                }
                RdpInputHandler.SCANCODE_BACKSPACE -> {
                    if (terminalText.isNotEmpty() && !terminalText.endsWith("> ")) {
                        terminalText.deleteCharAt(terminalText.length - 1)
                    }
                }
                RdpInputHandler.SCANCODE_SPACE -> {
                    terminalText.append(" ")
                }
                RdpInputHandler.SCANCODE_WIN -> {
                    isStartMenuOpen = !isStartMenuOpen
                }
                else -> {
                    if (event.unicodeChar != null && event.unicodeChar != '\u0000') {
                        terminalText.append(event.unicodeChar)
                    }
                }
            }
            renderFullDesktop()
        }
    }

    private fun renderFullDesktop() {
        frameBuffer.drawCustom { canvas ->
            drawWallpaper(canvas)
            drawDesktopIcons(canvas)
            drawActiveWindow(canvas)
            if (isStartMenuOpen) {
                drawStartMenu(canvas)
            }
            drawTaskbar(canvas)
            drawCursor(canvas)
        }
    }

    private fun drawWallpaper(canvas: Canvas) {
        // High quality gradient background
        val shader = android.graphics.LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.parseColor("#0F2027"),
                Color.parseColor("#203A43"),
                Color.parseColor("#2C5364")
            ),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        bgPaint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        bgPaint.shader = null

        // Decorative abstract bloom circles
        val bloomPaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(45, 0, 168, 255)
        }
        canvas.drawCircle(width * 0.65f, height * 0.45f, 320f, bloomPaint)
        bloomPaint.color = Color.argb(35, 142, 68, 173)
        canvas.drawCircle(width * 0.52f, height * 0.55f, 260f, bloomPaint)
    }

    private fun drawDesktopIcons(canvas: Canvas) {
        val icons = listOf(
            "This PC",
            "Network",
            "Recycle Bin",
            "PowerShell",
            "Artemis RDP"
        )
        var startY = 60f
        val iconPaint = Paint().apply {
            color = Color.argb(190, 255, 255, 255)
            isAntiAlias = true
        }

        for (name in icons) {
            // Icon background tile
            val iconRect = RectF(50f, startY, 130f, startY + 80f)
            canvas.drawRoundRect(iconRect, 16f, 16f, iconPaint)

            // Icon inner symbol
            val symbolPaint = Paint().apply {
                color = Color.parseColor("#1565C0")
                isAntiAlias = true
            }
            canvas.drawRoundRect(RectF(65f, startY + 15f, 115f, startY + 65f), 8f, 8f, symbolPaint)

            // Text
            val labelPaint = Paint().apply {
                color = Color.WHITE
                textSize = 20f
                isAntiAlias = true
                setShadowLayer(4f, 1f, 1f, Color.BLACK)
            }
            canvas.drawText(name, 50f, startY + 110f, labelPaint)
            startY += 150f
        }
    }

    private fun drawActiveWindow(canvas: Canvas) {
        val winLeft = 320f
        val winTop = 120f
        val winRight = width - 260f
        val winBottom = height - 160f

        // Window drop shadow
        val shadowPaint = Paint().apply {
            color = Color.argb(80, 0, 0, 0)
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(winLeft + 8f, winTop + 8f, winRight + 8f, winBottom + 8f), 20f, 20f, shadowPaint)

        // Window background
        val winRect = RectF(winLeft, winTop, winRight, winBottom)
        canvas.drawRoundRect(winRect, 16f, 16f, windowPaint)

        // Title bar
        val titleBar = RectF(winLeft, winTop, winRight, winTop + 54f)
        canvas.drawRoundRect(titleBar, 16f, 16f, windowTitlePaint)
        // Square bottom of title bar
        canvas.drawRect(winLeft, winTop + 30f, winRight, winTop + 54f, windowTitlePaint)

        // Title text
        val titleText = "Command Prompt - Artemis Virtual Host [Administrator]"
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 22f
            isAntiAlias = true
        }
        canvas.drawText(titleText, winLeft + 24f, winTop + 36f, titlePaint)

        // Window controls (minimize, maximize, close)
        val closePaint = Paint().apply { color = Color.parseColor("#E81123"); isAntiAlias = true }
        val minMaxPaint = Paint().apply { color = Color.argb(120, 255, 255, 255); isAntiAlias = true }
        canvas.drawCircle(winRight - 32f, winTop + 27f, 10f, closePaint)
        canvas.drawCircle(winRight - 64f, winTop + 27f, 10f, minMaxPaint)
        canvas.drawCircle(winRight - 96f, winTop + 27f, 10f, minMaxPaint)

        // Terminal content area
        var lineY = winTop + 90f
        val lines = terminalText.toString().split("\n")
        val displayLines = if (lines.size > 14) lines.takeLast(14) else lines
        for (line in displayLines) {
            canvas.drawText(line, winLeft + 24f, lineY, monoPaint)
            lineY += 34f
        }

        // Blinking cursor in terminal
        val cursorBar = Paint().apply {
            color = Color.parseColor("#4AF626")
            strokeWidth = 3f
        }
        val lastLine = displayLines.lastOrNull() ?: ""
        val cursorOffset = monoPaint.measureText(lastLine)
        canvas.drawLine(winLeft + 24f + cursorOffset + 4f, lineY - 30f, winLeft + 24f + cursorOffset + 4f, lineY - 6f, cursorBar)
    }

    private fun drawStartMenu(canvas: Canvas) {
        val menuWidth = 600f
        val menuHeight = 520f
        val menuLeft = (width - menuWidth) / 2f
        val menuBottom = height - 76f
        val menuTop = menuBottom - menuHeight

        // Background
        val menuPaint = Paint().apply {
            color = Color.argb(248, 24, 30, 40)
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(menuLeft, menuTop, menuLeft + menuWidth, menuBottom), 24f, 24f, menuPaint)

        // Header / Search
        val searchBar = RectF(menuLeft + 30f, menuTop + 30f, menuLeft + menuWidth - 30f, menuTop + 80f)
        val searchPaint = Paint().apply { color = Color.argb(180, 40, 48, 62); isAntiAlias = true }
        canvas.drawRoundRect(searchBar, 12f, 12f, searchPaint)

        val searchTextPaint = Paint().apply { color = Color.LTGRAY; textSize = 22f; isAntiAlias = true }
        canvas.drawText("Type here to search...", menuLeft + 50f, menuTop + 62f, searchTextPaint)

        // Pinned apps grid
        val pinnedTitle = Paint().apply { color = Color.WHITE; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        canvas.drawText("Pinned Apps", menuLeft + 35f, menuTop + 130f, pinnedTitle)

        val apps = listOf("Edge", "Settings", "Word", "Excel", "Photos", "Store", "Files", "Terminal")
        var appX = menuLeft + 45f
        var appY = menuTop + 180f
        val appTilePaint = Paint().apply { color = Color.argb(100, 50, 60, 80); isAntiAlias = true }
        val appTextPaint = Paint().apply { color = Color.WHITE; textSize = 18f; isAntiAlias = true }

        for ((index, app) in apps.withIndex()) {
            canvas.drawRoundRect(RectF(appX, appY, appX + 110f, appY + 70f), 12f, 12f, appTilePaint)
            canvas.drawText(app, appX + 20f, appY + 45f, appTextPaint)
            appX += 135f
            if ((index + 1) % 4 == 0) {
                appX = menuLeft + 45f
                appY += 95f
            }
        }
    }

    private fun drawTaskbar(canvas: Canvas) {
        val taskbarY = height - 72f
        canvas.drawRect(0f, taskbarY, width.toFloat(), height.toFloat(), taskbarPaint)

        // Center app icons (Windows 11 style)
        val centerX = width / 2f
        val startBtnX = centerX - 160f
        val startRect = RectF(startBtnX, taskbarY + 12f, startBtnX + 48f, taskbarY + 60f)
        val startPaint = Paint().apply {
            color = if (isStartMenuOpen) Color.parseColor("#0078D4") else Color.argb(120, 255, 255, 255)
            isAntiAlias = true
        }
        canvas.drawRoundRect(startRect, 10f, 10f, startPaint)

        // Windows 4-pane icon
        val winTile = Paint().apply { color = Color.parseColor("#00ADEF"); isAntiAlias = true }
        canvas.drawRect(startBtnX + 12f, taskbarY + 20f, startBtnX + 22f, taskbarY + 32f, winTile)
        canvas.drawRect(startBtnX + 26f, taskbarY + 20f, startBtnX + 36f, taskbarY + 32f, winTile)
        canvas.drawRect(startBtnX + 12f, taskbarY + 36f, startBtnX + 22f, taskbarY + 48f, winTile)
        canvas.drawRect(startBtnX + 26f, taskbarY + 36f, startBtnX + 36f, taskbarY + 48f, winTile)

        // Other taskbar icons
        val otherIcons = listOf("#2196F3", "#4CAF50", "#FF9800", "#9C27B0")
        var iconOffset = startBtnX + 65f
        for (colorHex in otherIcons) {
            val p = Paint().apply { color = Color.parseColor(colorHex); isAntiAlias = true }
            canvas.drawRoundRect(RectF(iconOffset, taskbarY + 12f, iconOffset + 48f, taskbarY + 60f), 10f, 10f, p)
            iconOffset += 60f
        }

        // System Tray (Time & Date)
        val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val now = Date()
        val timeStr = timeFormat.format(now)
        val dateStr = dateFormat.format(now)

        val trayPaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            isAntiAlias = true
        }
        canvas.drawText(timeStr, width - 180f, taskbarY + 32f, trayPaint)
        canvas.drawText(dateStr, width - 180f, taskbarY + 56f, trayPaint)
    }

    private fun drawCursor(canvas: Canvas) {
        val path = Path().apply {
            moveTo(cursorX, cursorY)
            lineTo(cursorX, cursorY + 24f)
            lineTo(cursorX + 6f, cursorY + 18f)
            lineTo(cursorX + 14f, cursorY + 26f)
            lineTo(cursorX + 18f, cursorY + 22f)
            lineTo(cursorX + 10f, cursorY + 14f)
            lineTo(cursorX + 18f, cursorY + 14f)
            close()
        }
        canvas.drawPath(path, cursorPaint)
        canvas.drawPath(path, cursorStroke)
    }
}
