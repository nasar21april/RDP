package com.example.artemisrdp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class WorkflowStep(
    val number: Int,
    val name: String,
    val status: String, // "queued", "in_progress", "completed"
    val conclusion: String? // "success", "failure", null
)

data class CloudSession(
    val host: String,
    val port: Int = 3389,
    val username: String,
    val password: String,
    val runId: Long,
    val startedAt: Long,
    val expiresAt: Long,
    val tailscaleAuthUrl: String? = null,
    val webUrl: String? = null
) {
    // Backwards-compatible alias for existing UI callers
    val ip: String
        get() = if (port != 3389) "$host:$port" else host

    val remainingMillis: Long
        get() = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)

    val isExpired: Boolean
        get() = remainingMillis <= 0L
}

sealed class CloudServerStatus {
    object Idle : CloudServerStatus()
    data class Triggering(val message: String) : CloudServerStatus()
    data class RunningSteps(val runId: Long, val message: String) : CloudServerStatus()
    data class Ready(val session: CloudSession) : CloudServerStatus()
    data class Cancelling(val message: String = "Cancelling server on GitHub, please wait...") : CloudServerStatus()
    data class Failed(val error: String) : CloudServerStatus()
}

class GitHubCloudRdpManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rdp_cloud_settings", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow<CloudServerStatus>(CloudServerStatus.Idle)
    val status: StateFlow<CloudServerStatus> = _status.asStateFlow()

    private val _steps = MutableStateFlow<List<WorkflowStep>>(emptyList())
    val steps: StateFlow<List<WorkflowStep>> = _steps.asStateFlow()

    private val _tailscaleAuthUrl = MutableStateFlow<String?>(null)
    val tailscaleAuthUrl: StateFlow<String?> = _tailscaleAuthUrl.asStateFlow()

    var token: String
        get() = prefs.getString("github_pat", "") ?: ""
        set(value) = prefs.edit().putString("github_pat", value.trim()).apply()

    var repo: String
        get() = prefs.getString("github_repo", "nasar21april/RDP") ?: "nasar21april/RDP"
        set(value) = prefs.edit().putString("github_repo", value.trim()).apply()

    var workflow: String
        get() = prefs.getString("github_workflow", "main.yml") ?: "main.yml"
        set(value) = prefs.edit().putString("github_workflow", value.trim()).apply()

    var branch: String
        get() = prefs.getString("github_branch", "main") ?: "main"
        set(value) = prefs.edit().putString("github_branch", value.trim()).apply()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val host = prefs.getString("session_host", prefs.getString("session_ip", null))
        val port = prefs.getInt("session_port", 3389)
        val user = prefs.getString("session_user", null)
        val pass = prefs.getString("session_pass", null)
        val runId = prefs.getLong("session_run_id", 0L)
        val started = prefs.getLong("session_started", 0L)
        val expires = prefs.getLong("session_expires", 0L)
        val webUrl = prefs.getString("session_web_url", null)

        if (!host.isNullOrBlank() && !user.isNullOrBlank() && expires > System.currentTimeMillis()) {
            val session = CloudSession(
                host = host,
                port = port,
                username = user,
                password = pass ?: "",
                runId = runId,
                startedAt = started,
                expiresAt = expires,
                webUrl = webUrl
            )
            _status.value = CloudServerStatus.Ready(session)
        }
    }

    fun setManualSession(hostOrIp: String, user: String, pass: String, webUrl: String? = null) {
        val trimmed = hostOrIp.trim()
        val parts = trimmed.split(":")
        val host = parts[0]
        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 3389 else 3389
        val now = System.currentTimeMillis()
        val session = CloudSession(
            host = host,
            port = port,
            username = user.trim().ifBlank { "RDP" },
            password = pass.trim(),
            runId = 0L,
            startedAt = now,
            expiresAt = now + (5L * 3600L * 1000L),
            webUrl = webUrl
        )
        persistSession(session)
        _status.value = CloudServerStatus.Ready(session)
    }

    private fun persistSession(session: CloudSession) {
        prefs.edit()
            .putString("session_host", session.host)
            .putInt("session_port", session.port)
            .putString("session_ip", session.ip)
            .putString("session_user", session.username)
            .putString("session_pass", session.password)
            .putString("session_web_url", session.webUrl)
            .putLong("session_run_id", session.runId)
            .putLong("session_started", session.startedAt)
            .putLong("session_expires", session.expiresAt)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove("session_host")
            .remove("session_port")
            .remove("session_ip")
            .remove("session_user")
            .remove("session_pass")
            .remove("session_web_url")
            .remove("session_run_id")
            .remove("session_started")
            .remove("session_expires")
            .apply()
        _status.value = CloudServerStatus.Idle
        _steps.value = emptyList()
        _tailscaleAuthUrl.value = null
    }

    suspend fun cancelServer(): Unit = withContext(Dispatchers.IO) {
        val savedRunId = prefs.getLong("session_run_id", 0L)
        var runIdToCancel = when (val s = _status.value) {
            is CloudServerStatus.RunningSteps -> if (s.runId > 0) s.runId else savedRunId
            is CloudServerStatus.Ready -> if (s.session.runId > 0) s.session.runId else savedRunId
            else -> savedRunId
        }

        _status.value = CloudServerStatus.Cancelling("Cancelling server on GitHub, please wait...")

        if (token.isNotBlank()) {
            try {
                // If runIdToCancel is 0, query GitHub for the in_progress run
                if (runIdToCancel <= 0L) {
                    val runsUrl = URL("https://api.github.com/repos/$repo/actions/workflows/$workflow/runs?status=in_progress&per_page=1")
                    val conn = (runsUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "RDP-Android-Client")
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                    if (conn.responseCode in 200..299) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(text)
                        val runs = json.optJSONArray("workflow_runs")
                        if (runs != null && runs.length() > 0) {
                            runIdToCancel = runs.getJSONObject(0).optLong("id")
                        }
                    }
                    conn.disconnect()
                }

                // 1. Cancel the run on GitHub Actions
                if (runIdToCancel > 0L) {
                    val cancelUrl = URL("https://api.github.com/repos/$repo/actions/runs/$runIdToCancel/cancel")
                    val cancelConn = (cancelUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "RDP-Android-Client")
                        connectTimeout = 10000
                        readTimeout = 10000
                    }
                    cancelConn.responseCode
                    cancelConn.disconnect()
                }

                // 2. Close open rdp-active session issues
                val issuesUrl = URL("https://api.github.com/repos/$repo/issues?state=open&labels=rdp-active")
                val issuesConn = (issuesUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "RDP-Android-Client")
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (issuesConn.responseCode in 200..299) {
                    val resp = issuesConn.inputStream.bufferedReader().use { it.readText() }
                    issuesConn.disconnect()
                    val issuesArr = JSONArray(resp)
                    for (i in 0 until issuesArr.length()) {
                        val num = issuesArr.getJSONObject(i).optInt("number")
                        if (num > 0) {
                            val patchUrl = URL("https://api.github.com/repos/$repo/issues/$num")
                            val patchConn = (patchUrl.openConnection() as HttpURLConnection).apply {
                                requestMethod = "PATCH"
                                setRequestProperty("Authorization", "Bearer $token")
                                setRequestProperty("Accept", "application/vnd.github+json")
                                setRequestProperty("User-Agent", "RDP-Android-Client")
                                setRequestProperty("Content-Type", "application/json")
                                doOutput = true
                            }
                            patchConn.outputStream.use { it.write("{\"state\":\"closed\"}".toByteArray()) }
                            patchConn.responseCode
                            patchConn.disconnect()
                        }
                    }
                } else {
                    issuesConn.disconnect()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        clearSession()
    }

    suspend fun checkActiveServer(): CloudSession? = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext null
        try {
            val session = fetchSessionFromIssues()
            if (session != null) {
                persistSession(session)
                _status.value = CloudServerStatus.Ready(session)
                return@withContext session
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun startServer(): Result<CloudSession> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            val err = "GitHub Personal Access Token is required."
            _status.value = CloudServerStatus.Failed(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        try {
            _status.value = CloudServerStatus.Triggering("Dispatching GitHub Actions workflow '$workflow'...")
            val dispatchSuccess = triggerWorkflowDispatch()
            if (!dispatchSuccess) {
                val err = "Failed to dispatch workflow. Verify your token has 'workflow' or 'repo' permissions."
                _status.value = CloudServerStatus.Failed(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            _status.value = CloudServerStatus.RunningSteps(0L, "Waiting for runner to initialize...")
            val triggerTimestamp = System.currentTimeMillis() - 15000

            // Poll for run ID
            var runId: Long? = null
            for (attempt in 1..25) {
                delay(3000)
                runId = findLatestRunId(triggerTimestamp)
                if (runId != null) break
            }

            if (runId == null) {
                val err = "Triggered workflow, but could not locate active run on GitHub."
                _status.value = CloudServerStatus.Failed(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            // Monitor live steps and search for credentials / issues
            var activeSession: CloudSession? = null
            for (attempt in 1..60) { // Up to 5 minutes
                delay(4000)
                val currentSteps = fetchWorkflowSteps(runId)
                _steps.value = currentSteps

                // 1. Try checking GitHub issues for published session data
                val issueSession = fetchSessionFromIssues()
                if (issueSession != null) {
                    activeSession = issueSession.copy(runId = runId)
                    break
                }

                // 2. Check if the "Maintain Connection" step is active
                val maintainStep = currentSteps.find { it.name.contains("Maintain", ignoreCase = true) }
                if (maintainStep != null && (maintainStep.status == "in_progress" || maintainStep.status == "completed")) {
                    _status.value = CloudServerStatus.RunningSteps(runId, "RDP Active! Connecting...")
                    // If issue wasn't found, try checking logs or artifacts
                    val credsFromLogs = tryFetchLogsCredentials(runId)
                    if (credsFromLogs != null) {
                        activeSession = credsFromLogs
                        break
                    }
                } else {
                    val activeStepName = currentSteps.find { it.status == "in_progress" }?.name
                        ?: currentSteps.lastOrNull { it.status == "completed" }?.name
                        ?: "Initializing runner"
                    _status.value = CloudServerStatus.RunningSteps(runId, "Step: $activeStepName")
                }
            }

            if (activeSession != null) {
                persistSession(activeSession)
                _status.value = CloudServerStatus.Ready(activeSession)
                Result.success(activeSession)
            } else {
                val err = "Workflow is active, but credentials need to be confirmed. View steps below or enter credentials."
                _status.value = CloudServerStatus.Failed(err)
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            val err = e.message ?: "Failed to launch cloud RDP server."
            _status.value = CloudServerStatus.Failed(err)
            Result.failure(e)
        }
    }

    suspend fun refreshLiveSteps(runId: Long) = withContext(Dispatchers.IO) {
        if (runId > 0 && token.isNotBlank()) {
            val stepList = fetchWorkflowSteps(runId)
            if (stepList.isNotEmpty()) {
                _steps.value = stepList
            }
        }
    }

    private fun triggerWorkflowDispatch(): Boolean {
        val url = URL("https://api.github.com/repos/$repo/actions/workflows/$workflow/dispatches")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "RDP-Android-Client")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
        }

        val jsonBody = JSONObject().apply {
            put("ref", branch)
        }

        conn.outputStream.use { os ->
            os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        conn.disconnect()
        return code in 200..299
    }

    private fun findLatestRunId(afterTimestamp: Long): Long? {
        val url = URL("https://api.github.com/repos/$repo/actions/workflows/$workflow/runs?per_page=5")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "RDP-Android-Client")
            connectTimeout = 15000
            readTimeout = 15000
        }

        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            return null
        }

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val json = JSONObject(response)
        val workflowRuns = json.optJSONArray("workflow_runs") ?: return null
        if (workflowRuns.length() == 0) return null

        val latest = workflowRuns.getJSONObject(0)
        return latest.optLong("id")
    }

    private fun fetchWorkflowSteps(runId: Long): List<WorkflowStep> {
        try {
            val url = URL("https://api.github.com/repos/$repo/actions/runs/$runId/jobs")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RDP-Android-Client")
                connectTimeout = 15000
                readTimeout = 15000
            }

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return emptyList()
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(text)
            val jobs = json.optJSONArray("jobs") ?: return emptyList()
            if (jobs.length() == 0) return emptyList()

            val job = jobs.getJSONObject(0)
            val stepsArray = job.optJSONArray("steps") ?: return emptyList()

            val list = mutableListOf<WorkflowStep>()
            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                list.add(
                    WorkflowStep(
                        number = stepObj.optInt("number", i + 1),
                        name = stepObj.optString("name", "Step ${i + 1}"),
                        status = stepObj.optString("status", "queued"),
                        conclusion = if (stepObj.has("conclusion") && !stepObj.isNull("conclusion")) stepObj.getString("conclusion") else null
                    )
                )
            }
            return list
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun fetchSessionFromIssues(): CloudSession? {
        try {
            val url = URL("https://api.github.com/repos/$repo/issues?state=open&labels=rdp-active&sort=created&direction=desc&per_page=10")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RDP-Android-Client")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val issues = JSONArray(text)
            for (i in 0 until issues.length()) {
                val issue = issues.getJSONObject(i)
                val body = issue.optString("body", "")
                val title = issue.optString("title", "")
                if (title.contains("RDP", ignoreCase = true) ||
                    body.contains("Host:", ignoreCase = true) ||
                    body.contains("bore.pub", ignoreCase = true) ||
                    body.contains("100.", ignoreCase = true)
                ) {
                    val parsed = parseCredentialsFromLog(body, 0L)
                    if (parsed != null) return parsed
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun tryFetchLogsCredentials(runId: Long): CloudSession? {
        // Fallback log check
        return null
    }

    companion object {
        fun parseCredentialsFromLog(log: String, runId: Long = 0L): CloudSession? {
            var host: String? = null
            var port = 3389

            // 1. Check for explicit Host: and Port: lines
            val hostHeaderPattern = Pattern.compile("(?im)^Host\\s*[:=]\\s*([a-zA-Z0-9\\.\\-]+)")
            val hostHeaderMatcher = hostHeaderPattern.matcher(log)
            if (hostHeaderMatcher.find()) {
                host = hostHeaderMatcher.group(1)?.trim()
            }

            val portHeaderPattern = Pattern.compile("(?im)^Port\\s*[:=]\\s*(\\d+)")
            val portHeaderMatcher = portHeaderPattern.matcher(log)
            if (portHeaderMatcher.find()) {
                port = portHeaderMatcher.group(1)?.trim()?.toIntOrNull() ?: 3389
            }

            // 2. Check for bore.pub:port pattern
            if (host == null) {
                val borePattern = Pattern.compile("(?i)(bore\\.pub):(\\d+)")
                val boreMatcher = borePattern.matcher(log)
                if (boreMatcher.find()) {
                    host = boreMatcher.group(1)
                    port = boreMatcher.group(2)?.toIntOrNull() ?: 3389
                }
            }

            // 3. Fallback to Tailscale or IP pattern
            if (host == null) {
                val tailscalePattern = Pattern.compile("\\b(100\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b")
                val ipMatcher = tailscalePattern.matcher(log)
                if (ipMatcher.find()) {
                    host = ipMatcher.group(1)
                } else {
                    val generalIpPattern = Pattern.compile("\\b((?:[1-9]\\d?|1\\d\\d|2[0-4]\\d|25[0-4])\\.(?:\\d{1,3}\\.){2}(?:[1-9]\\d?|1\\d\\d|2[0-4]\\d|25[0-4]))\\b")
                    val generalMatcher = generalIpPattern.matcher(log)
                    while (generalMatcher.find()) {
                        val candidate = generalMatcher.group(1) ?: continue
                        if (!candidate.startsWith("127.") && !candidate.startsWith("0.") && !candidate.startsWith("255.")) {
                            host = candidate
                            break
                        }
                    }
                }
            }

            if (host == null) return null

            // Password pattern handling both "Password: RDP / SecretPass" and "Password: SecretPass"
            val passPattern = Pattern.compile("(?i)Password\\s*[:=]\\s*(?:RDP\\s*/\\s*)?([^\r\n]+)")
            val passMatcher = passPattern.matcher(log)
            val password = if (passMatcher.find()) {
                passMatcher.group(1)?.trim() ?: ""
            } else ""

            // Username
            val userPattern = Pattern.compile("(?i)Username\\s*[:=]\\s*([a-zA-Z0-9_\\-\\.\\@]+)")
            val userMatcher = userPattern.matcher(log)
            val username = if (userMatcher.find()) {
                userMatcher.group(1)?.trim() ?: "RDP"
            } else "RDP"

            // Tailscale Auth URL (if any)
            val authPattern = Pattern.compile("(https://login\\.tailscale\\.com/a/[a-zA-Z0-9]+)")
            val authMatcher = authPattern.matcher(log)
            val authUrl = if (authMatcher.find()) authMatcher.group(1) else null

            // Web URL (if any)
            val webUrlPattern = Pattern.compile("(?im)^WebUrl\\s*[:=]\\s*(https?://[^\r\n]+)")
            val webUrlMatcher = webUrlPattern.matcher(log)
            val webUrl = if (webUrlMatcher.find()) webUrlMatcher.group(1)?.trim() else null

            val now = System.currentTimeMillis()
            val fiveHoursMillis = 5L * 60L * 60L * 1000L

            return CloudSession(
                host = host,
                port = port,
                username = username,
                password = password,
                runId = runId,
                startedAt = now,
                expiresAt = now + fiveHoursMillis,
                tailscaleAuthUrl = authUrl,
                webUrl = webUrl
            )
        }
    }
}
