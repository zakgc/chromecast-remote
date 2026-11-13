# Phone-only update 0.2.1

Owner feedback on 0.2.0: Nuvio and hold-to-repeat work; SmartTube opens but shows "Can't process intent"; the keyboard is difficult to use. The owner requested editable favourites and an installed-app picker, then explicitly declined a TV helper APK. This update includes no TV helper, TV service, new network endpoint, or new permission.

## Automatic live keyboard

While Home Remote is visible, selecting an editable TV text box now opens the phone keyboard automatically. The phone field shows the TV field's current text. Type, paste, move the phone cursor, or use the phone keyboard's Backspace; changes are sent after a short 120 ms pause without a Send button. The phone keyboard's Search/Enter action and the dialog's Search/Enter button wait for the latest edit before submitting the TV action.

Closing with Done dismisses the phone keyboard for that TV field; it opens again when a different field is selected. **Devices & setup → Open phone keyboard automatically for TV text boxes** turns automatic opening off. The manual Keyboard button remains available. With automatic mode enabled, the input capability stays active for the foreground connection, avoiding the repeated reconnects required by 0.2.0. Disabling it restores the normal connection when the dialog closes.

Switching fields or reconnecting rebinds to the TV's new/current text and discards unsent edits from the old field/session. Going to another TV, closing the panel, or backgrounding the app cancels pending edits. Text is kept only in memory for the current editor and is not stored or included in test reports. Delayed acknowledgements of earlier phone edits update counters without rewinding newer typing. Only TV apps exposing an Android editable field are supported.

## Editable favourite buttons

**Manage favourite apps** now supports:

- Add an app with a chosen button name and a supported app launch link.
- Rename a button or change its link.
- Move a button earlier or later.
- Remove a button.
- Keep a separate ordered list of up to 12 buttons per TV on each phone.

Selections from 0.2.0 are preserved during migration, including its implicit default buttons. New TVs start with an empty list. The migration defaults are not the selectable app catalogue; saved buttons can use any supported launch scheme.

The Android TV Remote v2 connection does not provide an installed-app inventory. Since the owner declined a TV helper, **automatic installed-app browsing is not implemented**. This update provides manual, editable app-link buttons instead. An arbitrary installed APK still needs to support a launch link; a package name alone is insufficient through this protocol. No Play Store lookup or invented app list is substituted for a device inventory.

## SmartTube correction

The former `vnd.youtube.launch://` link matched SmartTube's manifest but failed its separate root-request check, triggering the visible warning. The corrected migration/default link is `vnd.youtube.launch://youtube.com`. It retains SmartTube's dedicated scheme and matches the `.com` root suffix explicitly accepted by its [IntentExtractor.isRootUrl](https://github.com/yuliskov/SmartTube/blob/master/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/utils/IntentExtractor.java). Its video parser treats only the exact `vnd.youtube` scheme as a video ID host, so this dedicated launch scheme carries no video request. Nuvio's working link is unchanged.

## Install and check

Install `outputs/HomeRemote-0.2.1-test.apk` over the existing phone app. Pairings, names, verification flags, and previous test records remain. There is no additional TV APK.

1. Open SmartTube and Nuvio with their buttons. SmartTube should open without the intent warning.
2. Select SmartTube search. The phone keyboard should open automatically; type a phrase, delete characters, then press Search. No manual Send step is needed.
3. Close the keyboard with Done, navigate elsewhere, and select search again. Verify automatic opening resumes for the new field.
4. Type a draft, then disconnect or change TV text fields before sending settles. Confirm the old draft is not replayed.
5. Rename, reorder, add, and remove favourite buttons. Reopen the app to confirm the per-TV order and links remain.
6. Repeat existing navigation, TV switching, reopening, Wi-Fi recovery, and hold-to-repeat checks on both phones and TVs.

Owner follow-up on 13 November 2026: "All works well", in response to the 0.2.1 update and request to retry SmartTube launching and search typing. Record this as a successful general owner check of the update. Specific phone/TV combinations and every individual edge case were not reported, so this confirmation does not supply unmeasured performance or blanket per-case test results.

Build validation: all 65 automated cases pass; Android lint reports no issues with warnings treated as errors. Signature verification passes with the existing test certificate, version code 5. APK size is 1,002,051 bytes. The SHA-256 checksum is provided alongside the APK. No new permission, runtime dependency, or TV helper was added.
