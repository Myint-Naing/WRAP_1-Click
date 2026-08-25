# 1-Click WARP Android Application

A native Android application providing 1-Click Cloudflare WARP WireGuard VPN tunnel with automated silent 48-hour key renewal, secure credential storage, and automated CI/CD for building and releasing signed APKs.

---

## Features

- **1-Click Activation:** Generates a WireGuard keypair and registers directly with the Cloudflare WARP API without requiring external dependencies or Go toolchains.
- **Automated Silent Key Renewal:** WorkManager background tasks automatically renew WireGuard keys 48 hours before expiration without interrupting active VPN connectivity.
- **Secure Encrypted Storage:** Uses Android Jetpack EncryptedSharedPreferences (AES-256 GCM) to store private keys and registration credentials securely on the device.
- **Modern Jetpack Compose UI:** Reactive user interface displaying tunnel connection state, target endpoint, and real-time countdown to the next automated background renewal.
- **Automated CI/CD APK Build & Release:** GitHub Actions pipeline automatically compiles, signs, and attaches release APKs to GitHub Releases whenever a version tag is pushed.

---

## Architecture & Project Structure

- **`app/src/main/java/com/warp/android/`**:
  - `MainActivity.kt`: Jetpack Compose UI (`WarpScreen`) and ViewModel (`MainViewModel`).
  - `data/WarpApi.kt`: Retrofit client for Cloudflare WARP API (`/v0a737/reg`) and EncryptedSharedPreferences persistence.
  - `vpn/VpnManager.kt`: Integration with WireGuard Android SDK (`GoBackend`, WireGuard configuration builder).
  - `worker/WarpRenewalWorker.kt`: Android WorkManager task for scheduling background silent renewals.
- **`.github/workflows/release.yml`**: GitHub Actions workflow for building and signing release APKs on tag push.

---

## How to Build & Push the Android APK File

### Method 1: Push a Tag to Automatically Trigger GitHub Release (Recommended)

The project includes a GitHub Actions workflow (`.github/workflows/release.yml`) that builds, signs, and releases the Android APK automatically when a version tag is pushed.

#### Step 1: Commit Your Changes
Ensure all your changes are committed:
```bash
git add .
git commit -m "Prepare version 1.0.0 release"
```

#### Step 2: Create a Version Tag
Create a git tag starting with `v` (e.g., `v1.0.0` or `v1.0.1`):
```bash
git tag v1.0.0
```

#### Step 3: Push the Tag to Remote Repository
Push both your branch and the tag to GitHub:
```bash
git push origin main
git push origin v1.0.0
```

#### Step 4: GitHub Actions Automated Build & Release
1. Pushing the tag automatically triggers the `Build & Release APK` workflow on GitHub Actions.
2. The workflow compiles the release APK using `./gradlew assembleRelease`.
3. The APK is signed using repository secrets (`SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`).
4. A new GitHub Release is automatically created containing the signed APK asset.

---

### Method 2: Manually Trigger GitHub Actions Workflow

1. Navigate to your repository on **GitHub**.
2. Click on the **Actions** tab.
3. Select **Build and Release Android APK** from the left sidebar.
4. Click **Run workflow**, select the branch (e.g., `main`), and click **Run workflow**.

---

### Method 3: Local Manual Build via Gradle

To build the APK locally on your machine:

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
Sign the generated APK using `apksigner`:
```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner sign \
    --ks /path/to/keystore.jks \
    --ks-key-alias your_alias \
    app/build/outputs/apk/release/app-release.apk
```

#### Step 3: Install or Share the APK
Install directly to a connected Android device via `adb`:
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

---

## Required GitHub Secrets for Signed Releases

To enable automatic APK signing in GitHub Actions, configure the following secrets under **Repository Settings -> Secrets and variables -> Actions**:

| Secret Name | Description |
| :--- | :--- |
| `SIGNING_KEY` | Base64-encoded string of your `.jks` or `.keystore` file |
| `ALIAS` | Key store alias name |
| `KEY_STORE_PASSWORD` | Password for the key store |
| `KEY_PASSWORD` | Password for the key entry |

---

## Testing

To run unit tests locally:
```bash
./gradlew test
```
