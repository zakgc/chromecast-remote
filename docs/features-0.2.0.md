# Feature test build 0.2.0

Historical notes for 0.2.0. See [0.2.1](features-0.2.1.md) for the automatic live keyboard, editable favourites, and SmartTube launch correction that supersede the behaviours below. The owner confirmed Nuvio and hold-to-repeat working in 0.2.0; SmartTube showed an intent warning and the keyboard flow was difficult to use.

Adds the three requested controls without redesigning the existing remote. Install `outputs/HomeRemote-0.2.0-test.apk` over 0.1.2; TV names, keys, pairings, verification flags, and existing test records are preserved. The app still has no accounts, cloud backend, analytics, or background service. No new permissions or runtime dependencies are added.

## Hold to repeat

Press a directional arrow for one immediate tap. Keep holding to repeat after a 400 ms delay, at 150 ms intervals. These are repeated short key presses, not a held key left on the TV. Release, move outside the button, cancel the gesture, change TV, disconnect, navigate away, or background the app to stop. Reconnecting never resumes a previous hold. OK, Back, Home, and playback remain single presses; accessibility clicks on arrows remain single presses too.

## Keyboard

1. Use the remote to focus a search/text box on the TV.
2. Tap **Keyboard**. The app briefly reconnects with text-input capability requested.
3. Type into the phone's text field and tap **Send text**. Text is inserted at the TV cursor, replacing a selected range if present.
4. **Enter / search on TV** uses the TV field's declared action when available. **Delete one character on TV** sends Backspace to that field.
5. Tap **Done** to clear the phone draft and reconnect with normal TV input restored.

Text is sent in explicit batches, not continuously with each phone keystroke. The app requires a text field reported by the TV; it does not type blindly into unknown/custom text controls. If the TV switches fields or the connection changes while typing, close and reopen Keyboard before sending. No draft is replayed after a reconnect or switch. Drafts are not saved, put in test reports, or sent anywhere except the selected TV. The app retains only cursor/field metadata from the TV, not its field text.

Some TV apps implement their own keyboards and may not expose a standard Android text field. Test Nuvio and SmartTube search separately on both TVs.

## Favourite apps

Nuvio and SmartTube buttons are included by default. **Choose favourite apps** saves a separate selection for each TV on each phone. Buttons are disabled before the selected TV is connected or if it does not offer app-link support.

The links were checked against the apps' own manifests:

- Nuvio: `nuvio://home`; [Nuvio manifest](https://github.com/NuvioMedia/NuvioTV/blob/main/app/src/main/AndroidManifest.xml).
- SmartTube: `vnd.youtube.launch://`; [SmartTube manifest](https://github.com/yuliskov/SmartTube/blob/master/smarttubetv/src/main/AndroidManifest.xml).

These open the installed TV apps through their VIEW schemes rather than the Play Store. No package restriction is sent, avoiding Android TV Remote Service's limited package visibility. Older app versions may not support the same schemes; if both beta/stable variants handle a link, Android may ask which to open. The remote sends an open request; the TV provides no reliable success acknowledgement. Confirm that the intended app opens on the selected TV. Unhandled links can make the remote service close its connection; automatic reconnection remains available. No TV helper app is included or required for this first test of the declared schemes.

## Playback timeline investigation

The [remote protocol](https://github.com/sonalder-darlene/libremote-android-tv/blob/main/PROTOCOL.md) does not expose video position, duration, or a seek-to-time operation. A progress slider cannot be accurately populated from this connection. It is not included in 0.2.0.

The [Cast media APIs](https://developers.google.com/cast/docs/android_sender/integrate#work_with_media_status) offer media status and progress for a compatible Cast session. That would require a separate integration and would not automatically cover arbitrary videos launched directly in Nuvio or SmartTube. A future app-specific integration or TV companion could be investigated, with position/duration/seek support checked in each player. No timeline or player support is promised until that data source is proven.

## Hardware acceptance

Build verification: 53 automated checks pass; Android lint reports no issues with warnings treated as errors. APK signature verification passes with the same signing certificate as 0.1.2 and version code 4. APK size: 984,732 bytes. These checks do not establish feature compatibility on the actual TVs.

Baseline 0.1.2 navigation, switching, and reconnection have owner confirmation. These additions are new and await device testing. Use the new entries in **Test this setup** on each phone and TV:

- Hold an arrow, release, and verify scrolling stops. Repeat with the finger moved outside the arrow and with a connection loss. Switch to the other TV and confirm no old movement resumes.
- Focus search in Nuvio and SmartTube. Insert text, insert into a selected range, test Backspace and Enter/search, then close the dialog. Verify normal navigation and the TV keyboard still work.
- While typing a draft, change the TV field or disconnect. Confirm Send is disabled or rejected and nothing is replayed to the new field/connection.
- Open each favourite on each TV. Verify it opens on the selected TV only. Save different favourite selections and reopen the app to confirm persistence.
- Repeat the existing navigation, switching, app-reopen, and Wi-Fi recovery checks. Volume remains disabled for the owner's setups.
