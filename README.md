# RDP — On-Demand Cloud Windows PC & Android Client

An Android remote desktop client paired with an on-demand, free Windows Cloud PC environment provisioned entirely via **GitHub Actions** and secured over a private **Tailscale** mesh network.

---

## 🌟 Key Features

- **1-Tap Cloud PC Launcher:** Spin up a temporary, powerful Windows 11 cloud environment (up to 6 hours) on Microsoft Azure infrastructure with one tap inside the mobile app.
- **Tailscale Mesh Security:** All traffic is routed directly and securely between your phone and the cloud runner over a private Tailscale IP (`100.x.x.x`). No public ports are exposed.
- **Dual Connection Modes:**
  - **In-App Desktop:** Full-screen touch-optimized desktop viewer with on-screen trackpad, gestures, and special keys bar (Ctrl, Alt, Win, Del, Esc).
  - **Native Windows App / RD Client:** 1-tap handoff to Microsoft's official Windows App / Remote Desktop client with automatic credential copying.
- **Zero Local Building:** Gradle compiles the Android APK directly in GitHub's cloud. Pushing code automatically updates the APK on GitHub Releases for 1-click mobile download.
- **Keep-Alive Heartbeat:** Built-in loop prevents GitHub Actions from timing out, giving you the full 6-hour runtime limit.

---

## 🏗️ Architecture & How It Works

```
┌─────────────────────────┐          GitHub REST API          ┌───────────────────────────────────┐
│     Android App (RDP)   │ ───────────────────────────────── │ GitHub Actions (main.yml)        │
│  - Jetpack Compose UI   │                                   │ - Runs on windows-latest (6 hrs)  │
│  - 1-Tap Cloud Launcher │                                   │ - Creates 'RDP' Windows User      │
│  - Virtual Trackpad     │                                   │ - Sets SecurityLayer = 1 (SSL)    │
│  - Session Polling      │                                   │ - Starts Tailscale VPN Client     │
└────────────┬────────────┘                                   └─────────────────┬─────────────────┘
             │                                                                  │
             │                    Private Tailscale Mesh                        │
             └─────────────────────── (100.x.x.x:3389) ─────────────────────────┘
```

1. **Triggering:** The app calls GitHub's `/actions/workflows/main.yml/dispatches` endpoint using your Personal Access Token.
2. **Provisioning:** GitHub Actions launches a `windows-latest` VM, configures Windows RDP registry (`SecurityLayer = 1`, `fDenyTSConnections = 0`), generates a secure password, and connects to your Tailscale network using the repository secret `TAILSCALE_AUTH_KEY`.
3. **Session Discovery:** The runner posts session details (Tailscale IP, generated password, and timestamps) to a private GitHub Issue tagged `rdp-active`.
4. **Connection:** The Android app reads the issue, displays the credentials on the home screen, and opens the session.

---

## 🚀 Setup & Installation

### 1. Download the Android App
Download the latest APK directly from the Releases page:
👉 **[Download Latest RDP.apk](https://github.com/nasar21april/RDP/releases/latest)**

### 2. Configure GitHub Token (In-App)
1. Generate a GitHub Personal Access Token (classic) with `repo` and `workflow` scopes at:
   [https://github.com/settings/tokens/new](https://github.com/settings/tokens/new)
2. Open the **RDP** app on your phone, tap **Settings (⚙️)**, and paste your token.
3. Set the repository to `nasar21april/RDP`.

### 3. Add Tailscale Auth Key to GitHub Secrets
1. Generate a reusable Tailscale auth key at:
   [https://login.tailscale.com/admin/settings/keys](https://login.tailscale.com/admin/settings/keys)
2. Add it as an encrypted repository secret named **`TAILSCALE_AUTH_KEY`** at:
   [https://github.com/nasar21april/RDP/settings/secrets/actions](https://github.com/nasar21april/RDP/settings/secrets/actions)
3. Ensure the **Tailscale app** is installed and connected on your phone/client device under the same account.

---

## 🛠️ Project Structure

- `.github/workflows/main.yml`: Windows Cloud PC runner orchestration script (RDP setup, Tailscale, keep-alive loop).
- `.github/workflows/build-rdp-apk.yml`: CI/CD workflow that builds `RDP.apk` via Gradle and publishes GitHub Releases.
- `app/src/main/java/com/example/artemisrdp/`:
  - `MainActivity.kt`: Entry point with Material 3 edge-to-edge Compose theme.
  - `ui/screens/HomeScreen.kt`: Hero Cloud PC card, live runner progress tracking, and connection management.
  - `ui/screens/SessionScreen.kt`: Desktop viewport, gestures, and session toolbar.
  - `data/GitHubCloudRdpManager.kt`: GitHub REST API client for dispatching workflows and polling session issues.
  - `automation/RdpAutomationService.kt`: Android Accessibility Service helper for auto-filling RDP prompts.
  - `engine/`: Custom RDP protocol frame buffer, input handlers, and stats telemetry.

---

## 🔒 Security & Privacy

- All sensitive network keys (`TAILSCALE_AUTH_KEY`, GitHub PAT) are kept strictly private inside GitHub Encrypted Secrets or Android private SharedPreferences.
- Windows user passwords are randomly generated per session.
- No public open ports: connections are exclusively routable over your authenticated Tailscale tailnet.
