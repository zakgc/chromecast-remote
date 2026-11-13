# Protocol research and implementation notes

Research date: 13 November 2026. Initial owner testing reported a pairing failure in 0.1. The owner subsequently confirmed that 0.1.1 accepted the TV code, but remote startup failed. Version 0.1.2 corrects remote activation; navigation compatibility remains unproven on hardware.

## Primary references

- [Google's supported phone remote](https://support.google.com/chromecast/answer/11221499)
- [Google Cast SDK overview](https://developers.google.com/cast/docs/overview): casting/media support does not establish full Google TV navigation.
- [androidtvremote2 remote schema](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/remotemessage.proto)
- [androidtvremote2 remote implementation](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/remote.py)
- [androidtvremote2 Polo schema](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/polo.proto)
- [androidtvremote2 pairing implementation](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/pairing.py)
- [Original Google Polo schema](https://android.googlesource.com/platform/external/google-tv-pairing-protocol/+/refs/heads/master/proto/polo.proto)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android network discovery](https://developer.android.com/develop/connectivity/wifi/use-nsd)

The schemas and protocol behavior informed an independent, narrow Kotlin implementation. Source code and generated schemas were not vendored. These are research sources, not a guarantee of hardware compatibility or Google's endorsement.

## Implemented subset

Discovery: `_androidtvremote2._tcp.` through Android NSD, bounded searches, serialized service resolution. Manual IP entry is available. TLS 1.2/1.3 only, with client certificate authentication. Client private keys remain in Android Keystore.

Framing: unsigned varint length followed by a protobuf payload. Messages are capped at 1 MiB, partial reads are completed, malformed lengths fail closed, and unknown supported wire types are skipped.

Polo: request (10), request acknowledgement (11), hexadecimal input encoding/options (20), configuration (30), acknowledgement (31), secret (40), secret acknowledgement (41). Version 2, status 200. The SHA-256 secret uses unsigned client modulus/exponent, server modulus/exponent, and the final two code bytes. The first code byte is checked locally before sending. Only a valid acknowledgement saves the TV certificate pin.

Remote: configure (1), set active (2), error (3), ping request/response (8/9), short key injection (10), and optional power telemetry (40, named `RemoteStart` in the older schema). Feature negotiation intersects ping/key/volume metadata with the server bitmask. Readiness requires configuration followed by SetActive; the controller writes the activation reply before publishing Connected or accepting keys. The app does not request the power feature and must not wait for its telemetry. There is no automatic power toggle. Remote service metadata is displayed with connection timing when available.

Normal remote sessions enforce the exact SHA-256 pin learned during pairing. The temporary pairing connection accepts a self-signed certificate because the visible code is the out-of-band authentication. Incorrect code syntax/checksum can be retried in the same pairing session. Transport/protocol errors close the session. Pairing can be cancelled by leaving the app or the dialog.

## Limits to establish on hardware

Protocol compatibility; Keystore-backed TLS client auth on both Samsung phones; behavior after TV service updates/reset; discovery after DHCP changes; deep sleep; HDMI-CEC vs infrared volume; mute; app-specific play/pause behavior. Connection timings measure transport setup, not visible key response latency. The phone UI does not infer success from socket writes.

No hold-to-repeat until key repetition behavior is checked on all hardware combinations. No Cast SDK fallback because it cannot be assumed to supply navigation. The supported Google TV/Home phone remote remains an alternative if this prototype fails the compatibility gate.

## Pairing failure reported in 0.1

Both supplied screenshots show an RSA internal error in Conscrypt while pairing. This is consistent with a local TLS signing failure, before a TV code can complete pairing. The exact underlying Java exception is unavailable from the screenshots.

The original key policy omitted `DIGEST_NONE`. [Android's key-generation documentation](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setDigests(java.lang.String...)) specifically requires this for typical TLS authentication because TLS hashes the data itself. [Conscrypt's RSA bridge](https://github.com/google/conscrypt/blob/master/common/src/main/java/org/conscrypt/CryptoUpcalls.java) delegates signing through private-key RSA cipher operations; [Android Keystore's RSA implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/keystore/java/android/security/keystore2/AndroidKeyStoreRSACipherSpi.java) requests a `NONE` digest for these operations. For already encoded PSS signatures, the bridge also requests `RSA/ECB/NoPadding`, which the original policy did not permit.

0.1.1 adds the required digest and raw-padding authorizations, keeping signing/verification purposes only. It does not lower TLS versions or disable remote certificate pins. Four regression cases (two on each simulated Android version) failed against the original policy before the fix. These check the actual production `KeyGenParameterSpec`, not a native Keystore handshake.

Keystore authorizations cannot be changed on an existing key. Corrected keys therefore use a new alias and saved records now include their identity version. Records from 0.1 default to the legacy alias. New pairing always uses the corrected alias, and only an acknowledged successful pairing updates the saved pin/version. Old keys remain available for legacy records until the device is removed, which deletes both aliases. TV names, selected device, other pairings, and test records are preserved by the app update.

## Remote startup failure reported in 0.1.1

Owner evidence: Guest Room TV is awake and on the same Wi-Fi; the code was accepted, but Connected never appeared. The screen showed Device unavailable / Socket closed. That text by itself is not evidence of a network fault.

The implementation waited for field 40 (`RemoteStart`) to set readiness, while negotiating only ping/key/volume features (mask 67). Field 40 is power telemetry for the unrequested power feature. A valid Configure → SetActive exchange therefore left readiness false, allowing the app's 12-second watchdog to close its own socket. The older reference client always requests power, masking this dependency when copying its readiness behavior. [The service interoperability analysis](https://github.com/sonalder-darlene/libremote-android-tv/blob/main/PROTOCOL.md#remote-port-6466) documents SetActive as feature readiness and field 40 as power telemetry.

0.1.2 marks the protocol ready on SetActive after Configure, and the controller announces connection only after writing the SetActive reply. Unconfigured activation is rejected; power updates cannot activate a session. Two regression cases verify activation and pings with no power telemetry, and that power telemetry alone does not activate. Both failed against the old implementation before the fix. The watchdog now records its own timeout so the UI does not report that case as a generic Wi-Fi problem. Successful pairings and TLS pins are unchanged.
