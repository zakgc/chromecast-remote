# Hardware results and regression checklist

The owner confirmed basic remote use, reopening without re-pairing and phone Wi-Fi recovery with both TVs on the Galaxy S25 and Galaxy S22. The [original S25 report](reports/2026-11-13-s25.md) records detailed Guest Room TV results; the owner later confirmed the same functional results on Maria TV.

Later feature checks confirmed hold-to-repeat and Nuvio launching, followed by a general successful check of SmartTube, the live keyboard and editable favourites. Exact phone/TV combinations for those later checks were not specified. These are earlier-build observations, not a complete hardware validation of 1.0.0.

Volume does not work in either TV setup even with the official physical remotes, per the owner. Volume and mute are omitted from the app.

## Inventory

| Item | Model / software | Evidence |
| --- | --- | --- |
| Phone A | Galaxy S25 / Samsung SM-S931B; reported Android 17, API 37 | Detailed export for Guest Room TV; owner confirms same functional results on Maria TV |
| Phone B | Galaxy S22; Android version unrecorded | General owner confirmation for both TVs |
| TV A | Guest Room TV; Google Chromecast HD; Remote Service 7.00.999235495; Google TV OS version unrecorded | Working from both phones |
| TV B | Maria TV; exact Chromecast model and software versions unrecorded | Working from both phones |

## Reported baseline

Results received 13 November 2026, on 0.1.2. Not recorded means no individual result was supplied, even where general remote use was confirmed.

| Check | S25 → TV A | S25 → TV B | S22 → TV A | S22 → TV B |
| --- | --- | --- | --- | --- |
| Basic remote use | Working | Working | Working | Working |
| Discovery | Passed (export) | Passed (owner) | Not recorded | Not recorded |
| Pair with on-screen code | Passed (export) | Passed (owner) | Not recorded | Not recorded |
| Directions, Select, Back, Home | Passed (export) | Passed (owner) | Not recorded | Not recorded |
| Switch: only selected TV responds | Passed (export) | Passed (owner) | Not recorded | Not recorded |
| Close/reopen without pairing again | Working | Working | Working | Working |
| Phone Wi-Fi off/on recovery | Working | Working | Working | Working |
| Play/pause during media playback | Passed (export) | Passed (owner) | Not recorded | Not recorded |

Manual IP entry, TV sleep/restart, unavailable-TV handling, large text and TalkBack remain unrecorded across all four combinations. Physical vibration also needs checking.

## Manual regression checks

Record results outside the app for each phone/TV combination; there is no in-app test checklist or report export. Include the app version and actual phone/TV software versions. Leave unobserved cases untested.

1. Discover and pair both TVs. Test manual IP entry, a mistyped code followed by correction, and cancellation. Failed pairing must not save credentials.
2. Switch TVs while tapping and holding arrows. Only the selected TV should respond. Release or cross a pad segment, disconnect, or leave the app: scrolling must stop and never resume on another connection.
3. Reopen and force-stop/reopen the app, then restart the phone. Pairings, selected TV, preferences and favourites should persist. Removing and re-adding a TV must require pairing again.
4. Turn phone Wi-Fi off/on, restart the TV, try an incorrect IP, and re-scan after an IP change. Check automatic foreground recovery and that unavailable TVs leave the app usable. Test sleep separately; wake with the physical remote when necessary.
5. Check Play / pause in each media app. Open favourite shortcuts and confirm the intended app opens on the selected TV. Add, rename, reorder, remove and page through favourites; verify persistence and separation between TVs.
6. Focus a standard TV text field. Check automatic opening, typing, pasting, deletion, cursor movement and Search / Enter. Close with Done, change focus, and retry. Test manual Keyboard with automatic mode disabled. Change TV fields or disconnect during typing: unsent text must not replay into another field/session.
7. Check vibration and both preference switches. Increase Android text/display size, rotate the phone, and use TalkBack. Confirm controls remain reachable, correctly named and usable; TalkBack arrow activation should remain a single press.
8. Install a same-key update over an existing build. Verify saved TVs, pairing identities, preferences and favourites remain usable without re-pairing.

## Measurements

The S25 → Guest Room TV export reported a **68 ms activity-layout estimate** and **587 ms connection sample**. These single observations do not establish full cold-start time, key latency, repeated-run statistics or S22 performance. The current app no longer exposes the prototype's layout estimate/report screen.

- Startup: record at least five cold and five warm starts using video or Android startup tools.
- Connection/switching: repeat ten times; record median and worst, and whether the first key works after Connected. Connection details are included in the status accessibility description.
- Key latency: record the finger tap and TV movement together; use elapsed video frames rather than socket-write time.
- Size: record APK size separately from installed app/data size in Android Settings.
- Battery: compare controlled foreground use with an hour closed, noting Wi-Fi and phone battery settings.
