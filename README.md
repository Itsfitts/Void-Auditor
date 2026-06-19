<div align="center">
  <img src="android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="VOID Auditor Logo"/>
  <h1>☣ VOID AUDITOR</h1>
  <p><b>CyberHack Edition • Android Forensics & Red Team Tool</b></p>
  <p>
    <img src="https://img.shields.io/badge/Android-34DD22?style=flat&logo=android&logoColor=white" alt="Android"/>
    <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Kotlin"/>
    <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"/>
    <img src="https://img.shields.io/badge/Shizuku-8B5CF6?style=flat&logo=shizuku&logoColor=white" alt="Shizuku"/>
    <img src="https://img.shields.io/badge/minSdk-24-FF6D00?style=flat" alt="minSdk 24"/>
    <img src="https://img.shields.io/badge/targetSdk-33-018786?style=flat" alt="targetSdk 33"/>
    <img src="https://img.shields.io/badge/license-MIT-yellow?style=flat" alt="License"/>
    <img src="https://img.shields.io/badge/version-12.6-39FF14?style=flat" alt="v12.6"/>
  </p>
</div>

---

## Table of Contents

- [About](#about)
- [Features](#features)
- [Screenshots](#screenshots)
- [Tech Stack](#tech-stack)
- [Installation](#installation)
- [Build from Source](#build-from-source)
- [Architecture](#architecture)
- [Usage](#usage)
- [Development](#development)
- [License](#license)

---

## About

**VOID Auditor** — professional Android Forensics  Red Team tool for auditing device security directly from the phone. Works via **Shizuku API**, requires no root privileges or PC connection.

The main goal of the project is to provide security specialists, pentesters, and advanced users with a complete set of tools for device analysis and management: from checking permissions and Accessibility services to freezing apps and exporting reports.

The project includes an AI assistant based on **Gemini API** for automatic analysis of audit results and IOC (Indicators of Compromise) search.

---

## Features

### 🔐 Security Audit
- Full device check: SELinux, ADB, Accessibility Services
- Dangerous permissions analysis (READ_CONTACTS, RECORD_AUDIO, CAMERA, etc.)
- Checking Device Administrators and installs from unknown sources
- Banking trojan detection
- Suspicious apps search

### 📱 App Management
- View and filter installed packages
- Batch freeze/unfreeze of suspicious apps
- Full info on versions, permissions, installation paths
- Launch Activity by package name
- APK backup and restore

### 🤖 AI Assistant (Gemini)
- Automatic log analysis and IOC search
- Audit reports generation with risk assessment
- Recommendations on blocking and hardening
- Russian language support
- Model selection: Gemini 2.0 Flash Lite / Flash / 1.5 Flash

### 📂 File System  Terminal
- View files and directories on device
- Built-in shell with Shizuku privileges
- Execution of bash/python scripts
- Pre-installed audit and cleanup scripts

### 📊 Reports  Export
- Auto-saving audit results in `/sdcard/ADB_Studio_Logs/`
- Export AI Assistant chat
- Export scripts to SD card
- Log Stream with color coding

---

## Screenshots

| Dashboard | AI Assistant | App Manager |
|---|---|---|
| ![Dashboard](screenshots/Screenshot_20260519-041357.jpg) | ![AI](screenshots/Screenshot_20260519-041614.jpg) | ![Apps](screenshots/Screenshot_20260519-041422.jpg) |

| Activity Launcher | Backup | Terminal | Scripts |
|---|---|---|---|
| ![Activity](screenshots/Screenshot_20260519-041450.jpg) | ![Backup](screenshots/Screenshot_20260519-041507.jpg) | ![Terminal](screenshots/Screenshot_20260519-041532.jpg) | ![Scripts](screenshots/Screenshot_20260519-041559.jpg) |

---

## Tech Stack

| Component | Technology |
|---|---|
| **Language** | Kotlin 1.9.20 |
| **UI** | Jetpack Compose + Material 3 |
| **Privileges** | Shizuku API 13.1.5 |
| **AI** | Google Gemini API |
| **Build** | Gradle 8.2.2, AGP 8.2.2 |
| **minSdk** | 24 (Android 7.0) |
| **targetSdk** | 33 |
| **compileSdk** | 35 |

---

## Installation

### Requirements
- Android 7.0+ (API 24)
- [Shizuku](https://shizuku.rikka.app/) — app for getting privileges
- Gemini API key (for AI functions) — get at [Google AI Studio](https://aistudio.google.com/)

### Quick Start
1. Install **Shizuku** from [official source](https://github.com/RikkaApps/Shizuku/releases)
2. Activate Shizuku: **Settings → Shizuku → Start**
3. Download latest APK from [Releases](https://github.com/YOUR_USERNAME/void-auditor/releases)
4. Install and launch
5. On first login to AI Assistant, enter Gemini API key

---

## Build from Source

```bash
# Cloning
git clone https://github.com/YOUR_USERNAME/void-auditor.git
cd adbstudio

# Install Node dependencies (Capacitor web part)
npm install

# Build Android
cd android
./gradlew assembleDebug

# APK will be in: android/app/build/outputs/apk/debug/app-debug.apk
```

### Build requirements
- Android Studio Hedgehog+ (2023.1+)
- JDK 17
- Android SDK 35
- Node.js 18+ (for Capacitor)

---

## Architecture

```
android/app/src/main/java/com/kuzyamond/adbstudio/
├── core/
│   └── ShizukuExecutor.kt       # Adapter for Shizuku with CommandResult
├── AIAssistantScreen.kt         # AI Assistant (Gemini + audit)
├── AppManagerScreen.kt          # App Management (batch actions)
├── ActivityLauncherScreen.kt    # Launch Activity by package
├── BackupScreen.kt              # Backup/restore APK
├── ConnectScreen.kt             # ADB WiFi connection
├── DashboardScreen.kt           # Main dashboard with info
├── FilesScreen.kt               # File manager
├── TerminalScreen.kt            # Shell terminal
├── ScriptsScreen.kt             # Script executor
├── MainActivity.kt              # Entry point, theme, GlobalLog
└── ShizukuManager.kt            # Base manager Shizuku (legacy)
```

### Key concepts

- **ShizukuExecutor** — single interface for executing commands, wraps `ShizukuManager` with execution time metrics, splitting stdout/stderr
- **GlobalLog** — global log manager with tags for each screen
- **CommandResult** — data class with fields: `success`, `output`, `error`, `exitCode`, `executionTimeMs`
- **ChatMessage** — AI Assistant message model with risk level support (riskLevel)

---

## Usage

### Device Audit
1. Go to tab **AI**
2. Click **AUDIT** — 11 diagnostic commands will be executed
3. Result will be automatically saved and sent to Gemini for analysis
4. AI will return response with **RISK LEVEL** and recommendations

### Batch freeze apps
1. Go to tab **APPS**
2. Check the boxes next to suspicious apps
3. Click **BATCH** → select action:
   - `pm disable` — freeze selected
   - `pm enable` — unfreeze
   - `am force-stop` — force stop
   - `pm clear` — clear data

### Run scripts
1. Go to tab **SCRIPTS**
2. Select preset (SECURITY_AUDIT, LOCKDOWN, NETWORK_SCAN etc.)
3. If necessary, edit the script code
4. Click **EXECUTE**

---

## Development

### Commit Structure
The project follows [Conventional Commits](https://www.conventionalcommits.org/):
```
feat: add batch freeze/unfreeze for apps
fix: fix crash on empty audit report
refactor: migrate Shell to ShizukuExecutor
docs: update README with new features
```

### Useful commands
```bash
# Build debug version
cd android && ./gradlew assembleDebug

# Fast install to device
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat | grep -i adbstudio
```

---

## License

Distributed under **MIT**. More details in file [LICENSE](LICENSE).

---

<div align="center">
  <p>
    Developed with ❤️ for community Android Security Researchers
  </p>
  <p>
    <a href="https://github.com/M0NDsuChTiG/Void-Auditor/issues">Report an issue</a>
    ·
    <a href="https://github.com/M0NDsuChTiG/Void-Auditor/discussions">Discussions</a>
    ·
    <a href="https://t.me/+26NV_KkOTkE0ZmE6">Telegram</a>
  </p>
</div>
