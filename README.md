# Home Remote — compatibility prototype

A small native Kotlin Android remote for two Chromecasts with Google TV. Built from the supplied plan for personal installation on a Galaxy S22 and S25. No account, advertising, analytics, cloud backend, or background service.

**The owner confirms 0.1.2 works with both TVs on the Galaxy S25. Galaxy S22 testing is pending.** The plan requires actual phone/TV tests before proceeding with the full app. Individual controls, recovery scenarios, and performance still need recording in [the hardware checklist](docs/device-testing.md). Hold-to-repeat, keyboard, voice, power, input switching, and later features are intentionally deferred.

## Install and try

1. Use [the updated test APK](outputs/HomeRemote-0.1.2-test.apk), or build using the instructions below.
2. Copy the APK to each phone, open it, and allow installation from the app used to open it when Android asks. Samsung Auto Blocker or a managed-device policy may restrict installation; follow the phone's instructions. This APK is debug-signed for personal testing.
3. Wake the Chromecast and connect the phone to the same home Wi-Fi. Open **Home Remote → Add a TV**. The app searches for Android TV Remote Service v2. You can also enter the TV's IP address from its network settings.
4. Name the device and pair using the six-character code on the TV, including letters A–F. Add the second Chromecast. Repeat pairing independently on the other phone.
5. Use the main remote. **Test this setup** records pass/fail results on each phone and offers volume/mute/playback test buttons. Only enable those controls on the main remote after observing that they work.
6. Use **Share test report** to export results through an app you choose. Reports include local IP addresses and phone details but no private keys, codes, or certificate pins.

## Build on Windows

Requires a JDK 17, Android SDK platform 35/build-tools 35.0.0, and Gradle 8.11.1. A helper installs them **inside this project** under ignored `.tools/`:

```powershell
# Downloads build tools. Review Android SDK terms before accepting them.
.\scripts\setup.ps1 -AcceptAndroidLicenses
.\scripts\build.ps1
```

The setup helper generates the standard Gradle wrapper. With an existing Android Studio/JDK/SDK installation, set `local.properties` to your SDK location and run `gradlew.bat testDebugUnitTest lintDebug assembleDebug`. Open the repository root in Android Studio for editing. The application supports Android 8.0+ (API 26); record the actual OS versions on both Samsung phones during testing.

Build outputs:

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Convenient copy and checksum: `outputs/HomeRemote-0.1.2-test.apk` and `.apk.sha256`
- Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Android lint report: `app/build/reports/lint-results-debug.html`

## Behavior and safety

- Saved selection appears before a network connection. Commands are disabled until the TV completes its remote handshake.
- Each selection invalidates the previous connection, closes its socket, and clears pending commands. Every command captures its original connection and expires after 250 ms. It is never transferred to another connection.
- Foreground reconnects back off from 2 to 30 seconds. Leaving the app cancels pairing, discovery, retries, and the remote socket; there is no background service.
- Discovery is limited to 20-second searches. Saved mDNS service names refresh changed IP addresses, and the TV's pinned certificate authenticates subsequent connections. Use **Devices & setup → Search again** after a network change if necessary.
- Non-exportable RSA pairing keys are generated in Android Keystore. The pairing code authenticates the self-signed TV certificate; subsequent remote TLS connections require that exact certificate. Backup is disabled. Removing a TV deletes its local keys, pin, preferences, and test record. It does not revoke the old certificate at the TV; use TV settings for that.
- Android network permissions cover local TLS, network state, and Wi-Fi multicast discovery. No location, microphone, contacts, storage, or notification permission is requested. The app targets API 35; recheck local-network permission requirements before increasing the target SDK.
- Native scrolling layouts, text scaling, large buttons, explicit control labels, selection announcements, and status updates support accessibility. Actual large-text/TalkBack verification is still required.
- Optional control verification is stored per TV **on each phone**. HDMI-CEC, soundbar settings, and infrared-only volume configurations may prevent volume control. A sent key does not prove the TV acted on it.

## Protocol dependency

The app implements a deliberately small subset of the **unofficial Android TV Remote v2 protocol**, using TLS on 6466 and Polo pairing on 6467 (or discovery port + 1). The Google Cast SDK is not used for navigation. There is no third-party runtime protocol library; the framing/parser and message handling are in `protocol/`. The implementation was checked against the original schemas and reference implementation, linked in [protocol notes](docs/protocol.md). Future Google TV / Android TV Remote Service updates may break it.

See [the hardware test checklist](docs/device-testing.md) for the compatibility gate, measurement instructions, and outstanding acceptance work. The helper keeps its debug signing key at `.tools/android-user/debug.keystore`. Keep that key when rebuilding to update an existing installation without uninstalling and losing pairings. Android Studio or a different build environment may use a different signing key.

## Remote startup correction in 0.1.2

The owner confirmed that 0.1.1 accepted the Guest Room TV's pairing code, but never showed Connected. The app incorrectly waited for an optional power-status message despite not requesting the power feature. Its own 12-second setup timer then closed the socket. Version 0.1.2 uses the TV's SetActive acknowledgement to complete remote setup, before allowing key commands. Power messages no longer control readiness. The two new protocol regression tests failed against 0.1.1 before this correction.

Install **0.1.2 over the current app**, open Home Remote, and tap **Reconnect** if needed. Existing successful pairings are retained; the Guest Room TV does not need to be paired again for this fix. Error messages now distinguish local setup timeouts, failed setup, and an interrupted active connection. The version is displayed in the app header and exported test report.

## Pairing correction in 0.1.1

The owner reported an RSA/Conscrypt failure during pairing in 0.1. The original Keystore configuration did not permit the prehashed/raw RSA operations used by Android's TLS stack. Version 0.1.1 corrects those authorizations while retaining TLS 1.2/1.3, non-exportable keys, and TV certificate pinning. See [the investigation](docs/protocol.md#pairing-failure-reported-in-01).

If upgrading from the original 0.1 build, install the latest APK over the existing app, then open **Devices & setup** and retry any unfinished pairing. There is no need to uninstall, clear data, or remove your saved TVs. New pairing attempts use a separate, corrected key. Legacy pairing records keep their original identity until a successful pairing acknowledgement saves the replacement. Removing a TV deletes both key versions. Earlier APKs should no longer be used.

## Validation completed

- `testDebugUnitTest`: 29 checks passed — 11 protocol tests, 12 app/storage cases, and 6 TLS key-policy cases. Android-specific checks run on simulated API 26 and API 35.
- `lintDebug`: no issues found, with warnings treated as errors.
- `assembleDebug`: successful; the 0.1.2 test APK is 897,770 bytes (about 0.86 MiB).
- Android APK signature verification: each update uses the same signing certificate and a higher version code for installation over the previous test build.

The app/storage tests exercise first use, disabled commands before connection, restoring selection, switching, per-device optional controls, persistence, removal, and untested-by-default reports. Protocol tests exercise framing, malformed input, handshake/capability negotiation, pings, key encoding, pairing-code hashing, and stale-command rejection. Key-policy tests verify the production key-generation settings and separate legacy/current aliases; they do not emulate hardware Keystore or a native Android TLS handshake. The new policy checks failed against the original configuration before the correction. Retesting encrypted pairing on the owner's phone/TV remains required.
