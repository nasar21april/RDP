# ============================================================
#  ARTEMIS — RDP Auto-Connect Module
#  Polls GitHub Issues for active RDP session, then
#  auto-configures Microsoft Windows App on Xiaomi via ADB
# ============================================================

param(
    [string]$GithubToken  = $env:GITHUB_TOKEN,
    [string]$GithubRepo   = "nasar21april/RDP",
    [Parameter(Mandatory=$true)][string]$DeviceId,
    [string]$AdbPath      = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [int]   $PollInterval = 15,
    [switch]$OneShot
)

$LogFile = "$PSScriptRoot\rdp_connect.log"
Start-Transcript -Path $LogFile -Append -Force | Out-Null

function Log($icon, $msg, $color = "White") {
    $ts = Get-Date -Format "HH:mm:ss"
    Write-Host "  [$ts] $icon  $msg" -ForegroundColor $color
}

function Get-ActiveRDPSession {
    $headers = @{
        "Accept"     = "application/vnd.github+json"
        "User-Agent" = "Artemis-RDP"
    }
    if (-not [string]::IsNullOrWhiteSpace($GithubToken)) {
        $headers["Authorization"] = "Bearer $GithubToken"
    }
    try {
        $issues = Invoke-RestMethod `
            -Uri "https://api.github.com/repos/$GithubRepo/issues?state=open&labels=rdp-active" `
            -Headers $headers -ErrorAction Stop
        if ($issues.Count -eq 0) { return $null }
        $body = $issues[0].body
        $parsed = @{}
        foreach ($line in ($body -split "`n")) {
            $parts = $line -split ": ", 2
            if ($parts.Count -eq 2) {
                $parsed[$parts[0].Trim()] = $parts[1].Trim()
            }
        }
        if ($parsed["Host"] -and $parsed["Password"]) { return $parsed }
    } catch {
        Log "⚠️" "GitHub poll error: $_" "DarkYellow"
    }
    return $null
}

function Adb($cmd) {
    & $AdbPath -s $DeviceId shell $cmd | Out-Null
}

function Adb-Tap($x, $y) { Adb "input tap $x $y" }
function Adb-LongPress($x, $y) { Adb "input swipe $x $y $x $y 1500" }

function Get-UI {
    Adb "uiautomator dump /sdcard/ui.xml"
    return (& $AdbPath -s $DeviceId shell "cat /sdcard/ui.xml" 2>$null) -join ""
}

function Wait-ForUI($text, $maxSecs = 8) {
    $start = Get-Date
    while (((Get-Date) - $start).TotalSeconds -lt $maxSecs) {
        $ui = Get-UI
        if ($ui -match [regex]::Escape($text)) { return $ui }
        Start-Sleep -Milliseconds 700
    }
    return $null
}

function Tap-UIElement($ui, $pattern, $fallbackX, $fallbackY) {
    if ($ui -match ($pattern + '[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')) {
        $cx = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
        $cy = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
        Adb-Tap $cx $cy
        return $true
    }
    Adb-Tap $fallbackX $fallbackY
    return $false
}

function Screenshot($name) {
    $path = "$PSScriptRoot\rdp_screen_$name.png"
    cmd.exe /c "`"$AdbPath`" -s $DeviceId exec-out screencap -p > `"$path`""
}

# ---- Step 1: Open Windows App --------------------------------
function Open-WindowsApp {
    Log "📱" "Opening Windows App..." "Cyan"
    & $AdbPath -s $DeviceId shell "monkey -p com.microsoft.rdc.androidx -c android.intent.category.LAUNCHER 1" | Out-Null
    Start-Sleep -Seconds 2
}

# ---- Step 2: Delete existing connection if any ---------------
function Delete-ExistingRDP {
    $ui = Get-UI
    if ($ui -notmatch "PC Connections") {
        Log "✅" "No existing connections — skipping delete." "Green"
        return
    }
    Log "🗑️" "Existing connection found — deleting..." "Yellow"
    Adb-LongPress 500 800
    Start-Sleep -Seconds 1
    $ui2 = Wait-ForUI "Delete"
    if ($ui2) {
        Tap-UIElement $ui2 'text="Delete"' 250 1485
        Start-Sleep -Seconds 1
        Log "✅" "Deleted." "Green"
    } else {
        Log "⚠️" "Delete menu not found — pressing back." "DarkYellow"
        Adb "input keyevent 4"
    }
    Start-Sleep -Milliseconds 500
}

# ---- Step 3: Add PC Connection -------------------------------
function Add-PCConnection($ip, $password) {
    Log "➕" "Adding PC: $ip" "Cyan"

    # Open Add menu
    $ui = Get-UI
    if ($ui -match "No Devices") {
        Adb-Tap 540 1456   # center FAB on empty screen
    } else {
        Adb-Tap 892 157    # top-right + button
    }
    Start-Sleep -Seconds 1

    # Wait for "PC Connection" option
    $ui2 = Wait-ForUI "PC Connection" 10
    if (-not $ui2) {
        Log "🔴" "Add PC popup not found!" "Red"
        Screenshot "error_addpc"
        return $false
    }
    Tap-UIElement $ui2 'text="PC Connection"' 540 1200
    Start-Sleep -Seconds 1

    # Enter IP in PC Name field
    Log "⌨️" "Entering IP: $ip" "Gray"
    $ui3 = Wait-ForUI "PC NAME" 8
    Adb-Tap 540 228
    Start-Sleep -Milliseconds 400
    Adb "input keyevent KEYCODE_CTRL_A"
    Adb "input keyevent KEYCODE_DEL"
    Adb "input text $ip"
    Start-Sleep -Milliseconds 500

    # Open User Account dropdown
    Log "👤" "Opening User Account dropdown..." "Gray"
    Adb-Tap 540 370
    Start-Sleep -Seconds 1

    # Tap "Add user account"
    $ui4 = Wait-ForUI "Add user account" 8
    if ($ui4) {
        Tap-UIElement $ui4 'text="Add user account"' 540 1200
    } else {
        Adb-Tap 540 1200
    }
    Start-Sleep -Seconds 1

    # Enter username
    Log "⌨️" "Entering username: RDP" "Gray"
    $ui5 = Wait-ForUI "USERNAME" 8
    Adb-Tap 540 370
    Start-Sleep -Milliseconds 400
    Adb "input keyevent KEYCODE_CTRL_A"
    Adb "input keyevent KEYCODE_DEL"
    Adb "input text RDP"
    Start-Sleep -Milliseconds 400

    # Enter password
    Log "🔑" "Entering password..." "Gray"
    Adb-Tap 540 490
    Start-Sleep -Milliseconds 400
    Adb "input keyevent KEYCODE_CTRL_A"
    Adb "input keyevent KEYCODE_DEL"
    # Escape special chars for shell
    $safePass = $password -replace '"', '\"'
    & $AdbPath -s $DeviceId shell "input text `"$safePass`"" | Out-Null
    Start-Sleep -Milliseconds 500

    # Save user account
    Log "💾" "Saving user account..." "Gray"
    $ui6 = Get-UI
    if (-not (Tap-UIElement $ui6 'text="SAVE"' 900 65)) {}
    Start-Sleep -Seconds 1

    # Save PC connection
    Log "💾" "Saving PC connection..." "Gray"
    $ui7 = Get-UI
    if (-not (Tap-UIElement $ui7 'text="SAVE"' 900 65)) {}
    Start-Sleep -Seconds 2

    Screenshot "after_save"
    return $true
}

# ---- Step 4: Connect + Handle Popups -------------------------
function Connect-RDP {
    Log "🔗" "Tapping PC card to connect..." "Magenta"
    Adb-Tap 500 600
    Start-Sleep -Seconds 2

    # Notification permission popup
    $ui = Get-UI
    if ($ui -match "Allow Windows App to send you notifications") {
        Log "🔔" "Tapping ALLOW notifications..." "Gray"
        Tap-UIElement $ui 'text="ALLOW"' 540 1930
        Start-Sleep -Seconds 1
    }

    # Certificate popup
    $ui2 = Get-UI
    if ($ui2 -match "can.t be verified") {
        Log "🔐" "Accepting certificate..." "Gray"
        # Check "Never ask again"
        Tap-UIElement $ui2 'text="Never ask again' 220 1230
        Start-Sleep -Milliseconds 500
        # Tap CONNECT
        Tap-UIElement $ui2 'text="CONNECT"' 760 1375
        Start-Sleep -Seconds 4
    }

    Screenshot "connected"

    $ui3 = Get-UI
    if ($ui3 -match "Configuring remote PC|The user name or password is incorrect") {
        if ($ui3 -match "The user name or password is incorrect") {
            Log "🔴" "Wrong credentials!" "Red"
            return $false
        }
        Log "🟢" "Connected — Configuring remote PC!" "Green"
        return $true
    }

    if ($ui3 -notmatch "Devices|Add PC") {
        Log "🟢" "Session appears active!" "Green"
        return $true
    }

    Log "⚠️" "Connection status unclear — check phone." "DarkYellow"
    return $false
}

# ---- Full Workflow 1 -----------------------------------------
function Run-Workflow1($ip, $password) {
    Write-Host ""
    Write-Host "  ════════════════════════════════════════════" -ForegroundColor DarkCyan
    Log "🚀" "Workflow 1 starting for $ip" "Cyan"

    $state = (& $AdbPath -s $DeviceId get-state 2>&1)
    if ($state -ne "device") {
        Log "🔴" "Phone not connected via ADB! State: $state" "Red"
        return $false
    }

    Open-WindowsApp
    Delete-ExistingRDP
    $added = Add-PCConnection $ip $password
    if (-not $added) { return $false }
    $ok = Connect-RDP
    Write-Host "  ════════════════════════════════════════════" -ForegroundColor DarkCyan
    return $ok
}

# ---- Entry -------------------------------------------------
Write-Host ""
Write-Host "  ╔══════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "  ║     ARTEMIS — RDP Auto-Connect           ║" -ForegroundColor Cyan
Write-Host "  ║  Watching GitHub for active RDP session  ║" -ForegroundColor Cyan
Write-Host "  ╚══════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host "  Log: $LogFile" -ForegroundColor DarkGray
Write-Host ""

if ($OneShot) {
    Log "🔍" "OneShot mode — checking GitHub now..." "Yellow"
    $session = Get-ActiveRDPSession
    if ($session) {
        Log "🎯" "Session found! IP: $($session['Host'])" "Green"
        Run-Workflow1 $session["Host"] $session["Password"]
    } else {
        Log "💤" "No active RDP session in GitHub Issues." "DarkYellow"
    }
} else {
    $lastIP = ""
    Log "🟡" "Polling every ${PollInterval}s... (Ctrl+C to stop)" "Yellow"
    Write-Host ""
    while ($true) {
        Log "🔍" "Polling GitHub..." "DarkGray"
        $session = Get-ActiveRDPSession
        if ($session) {
            $ip = $session["Host"]
            if ($ip -ne $lastIP) {
                Log "🎯" "New session detected! IP: $ip" "Green"
                $ok = Run-Workflow1 $ip $session["Password"]
                if ($ok) {
                    $lastIP = $ip
                    Log "✅" "Auto-connected to $ip!" "Green"
                } else {
                    Log "⚠️" "Connection attempt failed. Will retry next poll." "DarkYellow"
                }
            } else {
                Log "⏸️" "Same session ($ip) — already connected." "DarkGray"
            }
        } else {
            if ($lastIP) {
                Log "🔄" "Session ended (was $lastIP). Watching for new one..." "DarkYellow"
                $lastIP = ""
            } else {
                Log "💤" "No active session yet..." "DarkGray"
            }
        }
        Start-Sleep -Seconds $PollInterval
    }
}

Stop-Transcript | Out-Null
