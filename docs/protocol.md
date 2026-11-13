# Protocol and compatibility notes

The app independently implements a narrow subset of the unofficial Android TV Remote v2 protocol. No source implementation or generated schemas are vendored. This is not a Google-supported navigation API; TV service updates may affect compatibility.

## References

- [Remote message schema](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/remotemessage.proto) and [reference implementation](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/remote.py)
- [Polo schema](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/polo.proto) and [pairing implementation](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/pairing.py)
- [Original Google Polo schema](https://android.googlesource.com/platform/external/google-tv-pairing-protocol/+/refs/heads/master/proto/polo.proto)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore) and [network discovery](https://developer.android.com/develop/connectivity/wifi/use-nsd)
- [Google's supported phone remote](https://support.google.com/chromecast/answer/11221499)

## Discovery and transport

Android NSD searches for `_androidtvremote2._tcp.` for up to 20 seconds, resolving services serially. Saved service names allow discovered IP/port changes to refresh existing devices. Manual IPv4/IPv6 entry is available.

Remote TLS uses the advertised port (normally 6466); Polo pairing uses that port + 1 (normally 6467). Only TLS 1.2/1.3 is enabled. Client private keys remain in Android Keystore. Normal connections enforce the exact SHA-256 certificate pin saved during pairing.

Frames use an unsigned varint length followed by a protobuf payload, capped at 1 MiB. Partial reads are completed, malformed lengths fail closed, and unknown supported wire types are skipped.

## Pairing and upgrade compatibility

Polo uses version 2/status 200: request/acknowledgement (10/11), hexadecimal input options (20), configuration/acknowledgement (30/31), and secret/acknowledgement (40/41). The SHA-256 secret combines unsigned client modulus/exponent, server modulus/exponent and the final two code bytes. The first code byte is checked locally before sending.

The temporary pairing connection accepts the self-signed TV certificate; the on-screen code authenticates it. Only a successful secret acknowledgement saves the pin and identity version. Invalid code syntax/checksums can be retried; transport failures close the session.

Keystore policy includes `DIGEST_NONE` and raw RSA padding because Conscrypt performs its own TLS signature hashing/encoding. See [Android's digest requirements](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setDigests(java.lang.String...)) and [Conscrypt's RSA bridge](https://github.com/google/conscrypt/blob/master/common/src/main/java/org/conscrypt/CryptoUpcalls.java). Tests check the production key policy, not a native hardware TLS handshake.

Keep the original `home_remote` preferences name and both key aliases stable. Records without an identity version use legacy alias `home_remote_<id>`; current identities use `home_remote_tls2_<id>`. Re-pairing uses the current key without replacing a legacy record until acknowledged. Removing a TV deletes both aliases. The favourites migration retains old selections and implicit defaults; new TVs start empty.

## Remote messages

Configure (1) negotiates the intersection of the TV's capabilities and ping/key/volume/app-link bits (mask 579), plus IME bit 4 when keyboard support is requested (mask 583). The existing volume capability bit is retained for negotiation compatibility even though the UI has no volume controls.

Readiness requires Configure followed by SetActive (2). The controller writes the activation reply before announcing Connected or accepting commands. Power telemetry (40, `RemoteStart` in older schemas) cannot activate a session. The app does not request power support. A 12-second watchdog distinguishes local setup timeouts from interrupted active connections.

Other messages include errors (3), ping request/response (8/9), short key injection (10), and app links (90). Holding an arrow repeats short taps after 400 ms at 150 ms intervals. Release, cancellation, segment crossing, disconnection, TV switching and leaving the screen cancel repetition. Connection generations and a 250 ms expiry prevent queued commands from crossing sessions.

## Keyboard

Automatic mode requests IME throughout the foreground connection. With automatic mode disabled, opening the manual keyboard negotiates IME until the dialog closes.

Fields 20, 22 and 23 provide editor text/cursor metadata; hide/reset events clear the target. IME_BATCH_EDIT (21) carries the field ID/counter with selection (1), replacement (2), or editor action (5). Offsets count Android UTF-16 units.

The dialog mirrors the active TV editor in memory and coalesces edits after 120 ms. Replacement verifies the session/focus, uses the latest TV counter, replaces the current full field, then restores the phone cursor. Own-change acknowledgements update counters without rewinding newer phone typing. Search/Enter waits for pending edits. Drafts are discarded on focus/session changes, closure and backgrounding; text is never persisted or logged. Apps with custom keyboards may not expose a supported editor.

## Favourite apps and limits

APP_LINK (90) carries the user-supplied VIEW URL, without a package restriction or Play Store fallback. The migration links are `nuvio://home` and `vnd.youtube.launch://youtube.com`. SmartTube's host satisfies its [root URL check](https://github.com/yuliskov/SmartTube/blob/master/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/utils/IntentExtractor.java); the older hostless link caused an intent warning.

The protocol does not provide installed-app inventory or a playback timeline. No TV helper is included. A successful socket write does not confirm that an app opened or a key visibly took effect. Unhandled app links can close the TV service connection; foreground reconnection remains available.

See [hardware results and checks](device-testing.md) for observed compatibility and remaining manual verification.
