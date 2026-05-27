# Flux Hourglass

An ultra-minimalist particle physics hourglass timer for Android, built with
Jetpack Compose Canvas and on-device accelerometer-driven sand physics.

- Set hours / minutes / seconds with drag-or-tap pickers.
- Real sand falls and piles up in real time as the timer counts down.
- Tilt the device — the pile of sand slumps under physical gravity.
- Touch and hold to reveal exact remaining `HH : MM : SS` time.
- Endless black-and-white minimalist surface; no network calls, no analytics.

## Build

Requirements:

- Android Studio Hedgehog+ (or any IDE with Android Gradle Plugin 9.1 support)
- JDK 21
- Android SDK 36

```powershell
# Debug APK -> app/build/outputs/apk/debug/
.\gradlew.bat assembleDebug

# Run unit + screenshot tests
.\gradlew.bat test
```

The repo ships `debug.keystore.base64`; running `certutil -decode
debug.keystore.base64 debug.keystore` (already executed locally for committers)
materializes the debug keystore so the debug build is signed with a stable
SHA — useful for Firebase and Play Console debug uploads.

## Release

A signed release build needs four environment variables — `KEYSTORE_PATH`,
`STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (the CI form also accepts
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`).

From PowerShell, a typical full release run looks like:

```powershell
$env:KEYSTORE_PATH = "$HOME\keystores\flux-hourglass-upload.jks"
$env:STORE_PASSWORD = "***"
$env:KEY_ALIAS = "upload"
$env:KEY_PASSWORD = "***"

.\gradlew.bat clean bundleRelease assembleRelease `
    -PVERSION_NAME=1.0.0 -PVERSION_CODE=1

# Copy the freshly built AAB to the Windows desktop for Play Console upload
.\scripts\export-play-store-release.ps1 -Version 1.0.0
```

`scripts/build_release.ps1` wraps the same flow into a single command.

## CI

`.github/workflows/android-ci.yml` runs unit tests + assembleDebug on every
push/PR. `.github/workflows/release.yml` builds and uploads a signed APK + AAB
when a `v*` tag is pushed. Both expect the release keystore to be base64-encoded
into the `RELEASE_KEYSTORE_BASE64` secret plus the three password secrets above.
