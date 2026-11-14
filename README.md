# Chromecast Remote

A small native Kotlin Android remote for two Chromecasts with Google TV, made for personal use on a Galaxy S22 and S25. No account, advertising, analytics, cloud backend, or background service. Supports Android 8.0+ (API 26).

## Features

- Save and pair up to two TVs, discover them on local Wi-Fi, or add them by IP address.
- Navigate with a circular directional pad, hold arrows to repeat, hold OK for app options, and use Back, Home and Play / pause.
- Type in supported TV text fields with the automatic live phone keyboard, or open it manually.
- Save up to 12 editable, ordered favourite app shortcuts per TV. Additional favourites page in place.
- Adjust button vibration and automatic keyboard opening in Settings.

Volume, mute, power, voice, input switching, installed-app browsing and a playback timeline are not supported. Favourite apps need a launch link supported by the installed TV app; examples are `nuvio://home` and `vnd.youtube.launch://youtube.com`.

## Install and pair

1. Download `ChromecastRemote-1.0.0.apk` from [the 1.0.0 release](https://github.com/zakgc/chromecast-remote/releases/tag/v1.0.0) once published, or build it below.
2. Open the APK on the phone and allow installation from that source when Android asks. Install over the existing app to retain saved TVs, pairings, preferences and favourites. Updates must use the same signing key.
3. Wake the Chromecast and connect the phone to the same home Wi-Fi. Tap **Add a TV**, or **Settings → Manage TVs** for an existing setup. If discovery fails, enter the TV's IP address from its network settings.
4. Name the TV and pair using its six-character on-screen code, including letters A–F. Pair each phone independently.

Samsung Auto Blocker or a managed-device policy may restrict APK installation; follow the phone's instructions. Connection help is available in Settings.

## Keyboard and favourites

Selecting a supported text field on the TV opens the phone keyboard automatically. Edits are sent after a short pause; Search / Enter waits for pending edits. **Done** suppresses automatic opening for that field until focus changes. Disable automatic opening in **Settings → Automatic keyboard**; the manual **Keyboard** button remains available.

Use **Favourite apps → Edit** to add, rename, reorder or remove shortcuts. New TVs start with an empty list. The connection cannot list installed TV apps, and a package name alone is not a launch link. Confirm on the TV that the requested app opens.

## Build on Windows

Requires JDK 17, Android SDK platform 35 / build-tools 35.0.0, and Gradle 8.11.1. The setup helper installs these inside the project under ignored `.tools/`:

```powershell
# Review Android SDK terms before accepting them.
.\scripts\setup.ps1 -AcceptAndroidLicenses
.\scripts\build.ps1
```

With an existing JDK/Android SDK installation, set `local.properties` to your SDK location and run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Open the repository root in Android Studio for editing.

Build outputs:

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Versioned copy and checksum from the helper: `outputs/ChromecastRemote-1.0.0.apk` and `.apk.sha256`
- Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Android lint report: `app/build/reports/lint-results-debug.html`
- Native screen and icon renders from tests: `app/build/outputs/design/`

Keep `.tools/android-user/debug.keystore` backed up privately. Local builds and GitHub releases use this existing debug signing key to support updates to installed versions. Android Studio or another build environment may use a different key.

## Releases

The [release workflow](.github/workflows/release.yml) tests, lints, builds and verifies the APK before publishing the APK and checksum for a matching version tag. See [release setup and instructions](docs/releasing.md).

## Connection and privacy

Commands remain disabled until the remote handshake completes. Switching TVs closes the previous socket and clears pending commands; queued commands expire after 250 ms and are never transferred to another connection. Foreground reconnects back off from 2 to 30 seconds. Leaving the app stops pairing, discovery, retries and the remote connection.

Discovery searches last up to 20 seconds. Saved service names refresh changed IP addresses. Pairing keys are non-exportable Android Keystore keys; the TV code authenticates its self-signed certificate, which is pinned for later connections. Backup and device transfer are disabled. Removing a TV deletes its local credentials and preferences; revoke old phone access in TV settings if needed.

Editor text exists only in memory for the current keyboard session and is discarded on closure, TV switching or backgrounding. The app requests local-network, network-state, Wi-Fi multicast and vibration permissions. It requests no location, microphone, contacts, storage or notification permission.

The app uses the unofficial Android TV Remote v2 protocol over local TLS, with no runtime protocol dependency. Future TV service updates may affect compatibility. See [protocol and compatibility notes](docs/protocol.md).

## Validation

Automated tests cover protocol parsing and negotiation, pairing-key policy, connection isolation, storage upgrades, keyboard editing, hold cancellation, preferences, layout and launcher icons. Android-specific tests cover API 26 and 35; native design rendering uses API 35. Android lint treats warnings as errors.

The owner has confirmed basic remote use, reopening and Wi-Fi recovery on both phones with both TVs, plus later general checks of keyboard and favourites. These reports are not a fresh hardware test of every 1.0.0 case. See [hardware results and regression checklist](docs/device-testing.md) for the evidence and remaining manual checks.
