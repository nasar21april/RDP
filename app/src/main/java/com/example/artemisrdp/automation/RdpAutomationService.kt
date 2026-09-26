package com.example.artemisrdp.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RdpAutomationService : AccessibilityService() {

    companion object {
        private const val TAG = "RDP_AUTO"
        const val WINDOWS_APP_PKG = "com.microsoft.rdc.androidx"
        const val ACTION_TRIGGER = "com.example.artemisrdp.TRIGGER_RDP_WORKFLOW"
        const val EXTRA_HOST = "host"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"

        @Volatile
        var isRunning = false
    }

    private val handler = Handler(Looper.getMainLooper())

    private var pendingHost = ""
    private var pendingUsername = ""
    private var pendingPassword = ""
    private var workflowActive = false
    private var stepRetryCount = 0

    private enum class Step {
        IDLE,
        WAIT_FOR_WINDOWS_APP,
        CHECK_EXISTING_RDP,
        LONG_PRESS_EXISTING,
        TAP_DELETE,
        CONFIRM_DELETE,
        WAIT_AFTER_DELETE,
        TAP_ADD_BUTTON,
        TAP_PC_CONNECTION,
        ENTER_IP,
        OPEN_USER_ACCOUNT,
        TAP_ADD_USER,
        ENTER_USERNAME,
        ENTER_PASSWORD,
        SAVE_USER,
        SAVE_PC,
        WAIT_MAIN_SCREEN,
        TAP_CONNECTION_CARD,
        HANDLE_NOTIFICATION,
        HANDLE_CERTIFICATE,
        DONE
    }

    private var currentStep = Step.IDLE

    private val triggerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_TRIGGER) return
            pendingHost = intent.getStringExtra(EXTRA_HOST) ?: return
            pendingUsername = intent.getStringExtra(EXTRA_USERNAME) ?: ""
            pendingPassword = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
            workflowActive = true
            currentStep = Step.WAIT_FOR_WINDOWS_APP
            stepRetryCount = 0
            Log.i(TAG, "=== NEW WORKFLOW TRIGGERED ===")
            Log.i(TAG, "Host: $pendingHost, User: $pendingUsername, Password length: ${pendingPassword.length}")

            handler.removeCallbacksAndMessages(null)
            schedule(Step.WAIT_FOR_WINDOWS_APP, 1500)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "RdpAutomationService connected")
        val filter = IntentFilter(ACTION_TRIGGER)
        registerReceiver(triggerReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.i(TAG, "RdpAutomationService destroyed")
        try {
            unregisterReceiver(triggerReceiver)
        } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
    }

    override fun onInterrupt() {
        Log.w(TAG, "RdpAutomationService interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (pendingPassword.isBlank()) return
        val root = rootInActiveWindow ?: return
        try {
            val pkg = root.packageName?.toString() ?: ""
            if (pkg != WINDOWS_APP_PKG) return

            // Check if this is the "Enter Your User Account" dialog prompt
            val hasUserAccountPrompt = hasText(root, "Enter Your User Account") ||
                    (hasText(root, "PASSWORD") && (hasText(root, "CONTINUE") || hasText(root, "Continue")))

            if (hasUserAccountPrompt) {
                // Find password EditText
                val passField = findEditTextByLabel(root, listOf("PASSWORD", "Password"))
                    ?: nthEditText(root, 1)
                    ?: firstEditText(root)

                if (passField != null) {
                    val currentText = passField.text?.toString() ?: ""
                    if (currentText.isBlank()) {
                        Log.i(TAG, "Auto-filling password into 'Enter Your User Account' dialog")
                        setText(passField, pendingPassword)
                        handler.postDelayed({
                            val r2 = rootInActiveWindow ?: return@postDelayed
                            try {
                                val continueBtn = findByText(r2, "CONTINUE")
                                    ?: findByText(r2, "Continue")
                                    ?: findByText(r2, "CONNECT")
                                    ?: findByText(r2, "Connect")
                                continueBtn?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            } finally {
                                r2.recycle()
                            }
                        }, 300)
                    }
                }
            }

            // Also auto-confirm certificate prompt if it appears
            if (hasText(root, "verified") || hasText(root, "certificate") || hasText(root, "Certificate")) {
                findByText(root, "Never ask again")?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                (findByText(root, "CONNECT") ?: findByText(root, "Connect"))?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling accessibility event: ${e.message}")
        } finally {
            root.recycle()
        }
    }

    private fun schedule(nextStep: Step, delayMs: Long) {
        currentStep = nextStep
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ executeStep() }, delayMs)
    }

    private fun executeStep() {
        if (!workflowActive) return
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "[$currentStep] rootInActiveWindow is null, retrying in 500ms...")
            schedule(currentStep, 500)
            return
        }

        val pkg = root.packageName?.toString() ?: ""
        if (pkg != WINDOWS_APP_PKG) {
            Log.d(TAG, "[$currentStep] Current app is $pkg, waiting for $WINDOWS_APP_PKG...")
            schedule(currentStep, 800)
            root.recycle()
            return
        }

        Log.i(TAG, ">>> Executing: $currentStep")

        when (currentStep) {

            Step.WAIT_FOR_WINDOWS_APP -> {
                // Ensure main screen is loaded
                if (hasText(root, "Devices") || hasText(root, "PC Connections") || hasText(root, "No Devices")) {
                    Log.i(TAG, "Windows App main screen ready")
                    schedule(Step.CHECK_EXISTING_RDP, 600)
                } else {
                    schedule(Step.WAIT_FOR_WINDOWS_APP, 600)
                }
            }

            Step.CHECK_EXISTING_RDP -> {
                val card = findConnectionCard(root, pendingHost)
                if (card != null) {
                    val rect = Rect()
                    card.getBoundsInScreen(rect)
                    Log.i(TAG, "Found matching PC card at bounds $rect; reusing it")
                    schedule(Step.TAP_CONNECTION_CARD, 400)
                } else {
                    Log.i(TAG, "No matching PC card found; adding one without deleting saved connections")
                    schedule(Step.TAP_ADD_BUTTON, 400)
                }
            }

            Step.LONG_PRESS_EXISTING -> {
                val card = findConnectionCard(root)
                if (card != null) {
                    val rect = Rect()
                    card.getBoundsInScreen(rect)
                    val cx = rect.centerX().toFloat()
                    val cy = rect.centerY().toFloat()
                    Log.i(TAG, "Long pressing existing card at ($cx, $cy)")
                    longPress(cx, cy) {
                        schedule(Step.TAP_DELETE, 1200)
                    }
                } else {
                    Log.i(TAG, "Card vanished or not found, falling back to gesture at (500, 750)")
                    longPress(500f, 750f) {
                        schedule(Step.TAP_DELETE, 1200)
                    }
                }
            }

            Step.TAP_DELETE -> {
                // Look for "Delete" menu item
                val deleteNode = findByText(root, "Delete")
                if (deleteNode != null) {
                    Log.i(TAG, "Found Delete button in context menu, clicking")
                    deleteNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    schedule(Step.CONFIRM_DELETE, 800)
                } else {
                    Log.i(TAG, "Delete node not in tree, tapping context menu position (250, 1485)")
                    tap(250f, 1485f) {
                        schedule(Step.CONFIRM_DELETE, 800)
                    }
                }
            }

            Step.CONFIRM_DELETE -> {
                // Check if confirmation dialog appeared ("DELETE" or "OK")
                val confirm = findByText(root, "DELETE") ?: findByText(root, "Delete") ?: findByText(root, "OK")
                if (confirm != null && confirm.isClickable) {
                    Log.i(TAG, "Clicking Delete confirmation")
                    confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                schedule(Step.WAIT_AFTER_DELETE, 1200)
            }

            Step.WAIT_AFTER_DELETE -> {
                Log.i(TAG, "Old connection deleted. Proceeding to add new connection.")
                schedule(Step.TAP_ADD_BUTTON, 800)
            }

            Step.TAP_ADD_BUTTON -> {
                val addBtn = findByContentDesc(root, "Add") ?: findByText(root, "Add")
                if (addBtn != null) {
                    val rect = Rect()
                    addBtn.getBoundsInScreen(rect)
                    Log.i(TAG, "Tapping Add button at (${rect.centerX()}, ${rect.centerY()})")
                    if (!addBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        tap(rect.centerX().toFloat(), rect.centerY().toFloat())
                    }
                } else {
                    Log.i(TAG, "Add button fallback tap at (892, 157)")
                    tap(892f, 157f)
                }
                schedule(Step.TAP_PC_CONNECTION, 1200)
            }

            Step.TAP_PC_CONNECTION -> {
                val pcConn = findByText(root, "PC Connection") ?: findByText(root, "Desktop")
                if (pcConn != null) {
                    Log.i(TAG, "Tapping 'PC Connection' menu option")
                    pcConn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    Log.i(TAG, "'PC Connection' fallback tap at (540, 300)")
                    tap(540f, 300f)
                }
                schedule(Step.ENTER_IP, 1500)
            }

            Step.ENTER_IP -> {
                val field = firstEditText(root)
                if (field != null) {
                    Log.i(TAG, "Typing IP address into PC Name field: $pendingHost")
                    field.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    setText(field, pendingHost)
                } else {
                    Log.i(TAG, "Fallback tap into IP field at (300, 360)")
                    tap(300f, 360f)
                }
                schedule(Step.OPEN_USER_ACCOUNT, 1000)
            }

            Step.OPEN_USER_ACCOUNT -> {
                // Tap "User account" dropdown row
                val dropdown = findByText(root, "User account")
                    ?: findByText(root, "User Account")
                    ?: findByText(root, "Ask when needed")
                if (dropdown != null) {
                    val rect = Rect()
                    dropdown.getBoundsInScreen(rect)
                    Log.i(TAG, "Tapping User Account row at (${rect.centerX()}, ${rect.centerY()})")
                    if (!dropdown.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        tap(rect.centerX().toFloat(), rect.centerY().toFloat())
                    }
                } else {
                    Log.i(TAG, "User account fallback tap at (300, 560)")
                    tap(300f, 560f)
                }
                schedule(Step.TAP_ADD_USER, 1200)
            }

            Step.TAP_ADD_USER -> {
                val addUser = findByText(root, "Add user account") ?: findByText(root, "Add User")
                if (addUser != null) {
                    Log.i(TAG, "Tapping 'Add user account'")
                    addUser.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    Log.i(TAG, "Add user account fallback tap at (300, 800)")
                    tap(300f, 800f)
                }
                schedule(Step.ENTER_USERNAME, 1200)
            }

            Step.ENTER_USERNAME -> {
                val field = firstEditText(root)
                if (field != null) {
                    Log.i(TAG, "Entering username: $pendingUsername")
                    field.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    setText(field, pendingUsername)
                } else {
                    Log.i(TAG, "Username field fallback tap at (300, 360)")
                    tap(300f, 360f)
                }
                schedule(Step.ENTER_PASSWORD, 800)
            }

            Step.ENTER_PASSWORD -> {
                val field = nthEditText(root, 1) ?: findEditTextByLabel(root, listOf("Password", "PASSWORD"))
                if (field != null) {
                    Log.i(TAG, "Entering password (length: ${pendingPassword.length})")
                    field.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    setText(field, pendingPassword)
                } else {
                    Log.i(TAG, "Password field fallback tap at (300, 500)")
                    tap(300f, 500f)
                }
                schedule(Step.SAVE_USER, 800)
            }

            Step.SAVE_USER -> {
                val save = findByText(root, "SAVE") ?: findByText(root, "Save") ?: findByContentDesc(root, "Save")
                if (save != null) {
                    val rect = Rect()
                    save.getBoundsInScreen(rect)
                    Log.i(TAG, "Saving user account at (${rect.centerX()}, ${rect.centerY()})")
                    if (!save.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        tap(rect.centerX().toFloat(), rect.centerY().toFloat())
                    }
                } else {
                    Log.i(TAG, "Save user fallback tap at (950, 150)")
                    tap(950f, 150f)
                }
                schedule(Step.SAVE_PC, 1500)
            }

            Step.SAVE_PC -> {
                val save = findByText(root, "SAVE") ?: findByText(root, "Save") ?: findByContentDesc(root, "Save")
                if (save != null) {
                    val rect = Rect()
                    save.getBoundsInScreen(rect)
                    Log.i(TAG, "Saving PC connection at (${rect.centerX()}, ${rect.centerY()})")
                    if (!save.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        tap(rect.centerX().toFloat(), rect.centerY().toFloat())
                    }
                } else {
                    Log.i(TAG, "Save PC fallback tap at (950, 150)")
                    tap(950f, 150f)
                }
                schedule(Step.WAIT_MAIN_SCREEN, 1500)
            }

            Step.WAIT_MAIN_SCREEN -> {
                schedule(Step.TAP_CONNECTION_CARD, 800)
            }

            Step.TAP_CONNECTION_CARD -> {
                val card = findConnectionCard(root, pendingHost)
                if (card != null) {
                    val rect = Rect()
                    card.getBoundsInScreen(rect)
                    Log.i(TAG, "Tapping new connection card at (${rect.centerX()}, ${rect.centerY()}) to connect")
                    if (!card.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        tap(rect.centerX().toFloat(), rect.centerY().toFloat())
                    }
                } else {
                    Log.w(TAG, "Could not find a PC card for $pendingHost; leaving saved connections untouched")
                    workflowActive = false
                    currentStep = Step.DONE
                }
                if (workflowActive) schedule(Step.HANDLE_NOTIFICATION, 1500)
            }

            Step.HANDLE_NOTIFICATION -> {
                if (hasText(root, "notifications") || hasText(root, "Notifications") || hasText(root, "ALLOW")) {
                    Log.i(TAG, "Handling notification permission dialog")
                    (findByText(root, "ALLOW") ?: findByText(root, "Allow"))?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                schedule(Step.HANDLE_CERTIFICATE, 1200)
            }

            Step.HANDLE_CERTIFICATE -> {
                if (hasText(root, "verified") || hasText(root, "certificate") || hasText(root, "Certificate")) {
                    Log.i(TAG, "Handling certificate warning popup")
                    findByText(root, "Never ask again")?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    handler.postDelayed({
                        val r2 = rootInActiveWindow
                        if (r2 != null) {
                            (findByText(r2, "CONNECT") ?: findByText(r2, "Connect"))?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            r2.recycle()
                        }
                    }, 400)
                }
                Log.i(TAG, "=== WORKFLOW 1 FINISHED! ===")
                workflowActive = false
                currentStep = Step.DONE
            }

            Step.DONE, Step.IDLE -> { /* idle */ }
        }

        root.recycle()
    }

    // ---- Gesture helpers -----------------------------------------------

    private fun tap(x: Float, y: Float, onComplete: (() -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete?.invoke()
            }
        }, null)
    }

    private fun longPress(x: Float, y: Float, onComplete: (() -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1200)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete?.invoke()
            }
        }, null)
    }

    // ---- Node finders & accurate card detector -------------------------

    private fun findConnectionCard(root: AccessibilityNodeInfo, requiredHost: String? = null): AccessibilityNodeInfo? {
        return walk(root) { node ->
            val rect = Rect()
            node.getBoundsInScreen(rect)
            // Filter out top title bar (y < 300) and bottom tabs (y > 2050)
            if (rect.top < 300 || rect.bottom > 2050 || rect.height() < 120) return@walk false

            // Check if this node or any child has the card marker
            val hasCardMarker = containsCardMarker(node)
            val hasHost = requiredHost.isNullOrBlank() || containsText(node, requiredHost)
            hasCardMarker && hasHost && (node.isClickable || node.isLongClickable)
        }
    }

    private fun containsText(node: AccessibilityNodeInfo, query: String): Boolean {
        if (node.text?.toString()?.contains(query, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(query, ignoreCase = true) == true
        ) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsText(child, query)) return true
        }
        return false
    }

    private fun containsCardMarker(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains("double tap to activate", ignoreCase = true)) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val cDesc = child.contentDescription?.toString() ?: ""
            val cText = child.text?.toString() ?: ""
            if (cDesc.contains("double tap to activate", ignoreCase = true) ||
                cDesc.contains("button", ignoreCase = true) ||
                (cText.contains(".") && !cText.contains("PC Connections"))
            ) {
                return true
            }
        }
        return false
    }

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun hasTextOrDesc(root: AccessibilityNodeInfo, query: String): Boolean {
        return walk(root) { node ->
            node.text?.toString()?.contains(query, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(query, ignoreCase = true) == true
        } != null
    }

    private fun findByTextOrDesc(root: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        return walk(root) { node ->
            node.text?.toString()?.contains(query, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(query, ignoreCase = true) == true
        }
    }

    private fun hasText(root: AccessibilityNodeInfo, t: String): Boolean = hasTextOrDesc(root, t)

    private fun findByText(root: AccessibilityNodeInfo, t: String): AccessibilityNodeInfo? = findByTextOrDesc(root, t)

    private fun findByContentDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? =
        walk(root) { it.contentDescription?.contains(desc, true) == true }

    private fun firstEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        walk(root) { it.className?.contains("EditText") == true }

    private fun nthEditText(root: AccessibilityNodeInfo, n: Int): AccessibilityNodeInfo? {
        val list = mutableListOf<AccessibilityNodeInfo>()
        collect(root, list) { it.className?.contains("EditText") == true }
        return list.getOrNull(n)
    }

    private fun findEditTextByLabel(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        for (label in labels) {
            val node = findByText(root, label)
            if (node != null) {
                val parent = node.parent ?: continue
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i) ?: continue
                    if (sibling.className?.contains("EditText") == true) return sibling
                }
            }
        }
        return null
    }

    private fun walk(root: AccessibilityNodeInfo, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (pred(root)) return root
        for (i in 0 until root.childCount) {
            val c = root.getChild(i) ?: continue
            val r = walk(c, pred)
            if (r != null) return r
        }
        return null
    }

    private fun collect(
        root: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        pred: (AccessibilityNodeInfo) -> Boolean
    ) {
        if (pred(root)) out.add(root)
        for (i in 0 until root.childCount) {
            val c = root.getChild(i) ?: continue
            collect(c, out, pred)
        }
    }
}
