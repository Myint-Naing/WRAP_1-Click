# 1-Click Cloudflare WARP Solution (OpenWrt & Android)

A complete solution for running Cloudflare WARP WireGuard tunnel on OpenWrt routers and Android devices, featuring 1-click activation, automated key renewal (48 hours prior to expiration), and automated CI/CD for building and releasing signed Android APK files.

---

## Repository Overview

- **`luci-app-warp/`**: OpenWrt LuCI web interface package and backend shell scripts (`warp-script`, `warp-renew`) for 1-click WARP setup on OpenWrt routers.
- **`app/`**: Native Android application (`com.warp.android`) built with Jetpack Compose, Kotlin, WireGuard Android SDK, WorkManager for silent background renewals, and Retrofit.
- **`.github/workflows/release.yml`**: GitHub Actions workflow that automatically compiles, signs, and creates GitHub Releases with the APK whenever a version tag is pushed.

---

## Features

- **1-Click Activation:** Generates WireGuard keypairs and registers directly with the Cloudflare WARP API without external dependencies or heavy Go toolchains.
- **Automated Silent 48-Hour Key Renewal:**
  - On **OpenWrt**: Daily cron job (`warp-renew`) checks expiration and re-registers 48 hours prior to expiration, reloading the interface seamlessly.
  - On **Android**: WorkManager schedules background task 48 hours prior to expiration to renew keys silently without interrupting connectivity.
- **Encrypted Credential Storage:** Android credentials are saved securely using Android Jetpack EncryptedSharedPreferences (AES-256 GCM).
- **Automated CI/CD APK Build & Release:** Fully automated GitHub Actions pipeline for building, signing, and attaching APK releases.

---

## OpenWrt LuCI Setup

### Quick Direct Installation
1. Install dependencies on your OpenWrt router:
   ```bash
   opkg update
   opkg install wireguard-tools curl jsonfilter uci luci-base
   ```
2. Copy package files to the router:
   ```bash
   scp -r luci-app-warp/root/* root@192.168.1.1:/
   scp -r luci-app-warp/htdocs/* root@192.168.1.1:/www/
   ```
3. Set permissions & enable services:
   ```bash
   chmod +x /usr/bin/warp-script /usr/bin/warp-renew /etc/init.d/warp
   /etc/init.d/warp enable
   /etc/init.d/warp start
   /etc/init.d/rpcd restart
   ```

---

## How to Build & Push the Android APK File

### Method 1: Push a Tag to Automatically Trigger GitHub Release (Recommended)

The project includes a GitHub Actions workflow (`.github/workflows/release.yml`) that builds, signs, and releases the Android APK automatically when you push a version tag.

#### Step 1: Commit Your Changes
Ensure all your changes are committed:
```bash
git add .
git commit -m "Prepare version 1.0.0 release"
```

#### Step 2: Create a Version Tag
Create a git tag starting with `v` (e.g. `v1.0.0` or `v1.0.1`):
```bash
git tag v1.0.0
```

#### Step 3: Push the Tag to Remote Repository
Push both your branch and the new tag to GitHub:
```bash
git push origin main
git push origin v1.0.0
```

#### Step 4: GitHub Actions Automated Build & Release
1. Once the tag is pushed, GitHub Actions triggers the `Build & Release APK` workflow.
2. The workflow compiles the release APK using Gradle.
3. The APK is signed using repository secrets (`SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`).
4. A new GitHub Release is automatically created containing the signed APK asset.

---

### Method 2: Manually Trigger GitHub Actions Workflow

1. Navigate to your repository on **GitHub**.
2. Click on the **Actions** tab.
3. Select **Build and Release Android APK** from the left sidebar.
4. Click **Run workflow**, choose the branch (e.g., `main`), and click **Run workflow**.

---

### Method 3: Local Manual Build via Gradle

If you want to build the APK locally on your machine:

#### Step 1: Build Release APK
Run the Gradle assemble command:
```bash
./gradlew assembleRelease
```
The generated APK will be placed at:
```text
app/build/outputs/apk/release/app-release.apk
```

#### Step 2: Sign the APK (Optional for distribution)
You can sign the generated APK using `apksigner`:
```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner sign \
    --ks /path/to/keystore.jks \
    --ks-key-alias your_alias \
    app/build/outputs/apk/release/app-release.apk
```

#### Step 3: Upload or Share the APK
You can directly upload the signed `app-release.apk` to GitHub Releases, Google Drive, or transfer it to an Android device via `adb`:
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

---

## Required GitHub Secrets for Signed APK Releases

To enable automatic APK signing in GitHub Actions, configure the following secrets under **Repository Settings -> Secrets and variables -> Actions**:

| Secret Name | Description |
| :--- | :--- |
| `SIGNING_KEY` | Base64-encoded string of your `.jks` or `.keystore` file |
| `ALIAS` | Key store alias name |
| `KEY_STORE_PASSWORD` | Password for the key store |
| `KEY_PASSWORD` | Password for the key entry |

---

## Verification & Testing

To run Android unit tests locally:
```bash
./gradlew test
```
