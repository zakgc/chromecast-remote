# Phase 1: test on the actual devices

Status: **owner confirms basic remote use, reopening without re-pairing, and phone Wi-Fi off/on recovery with both TVs on the Galaxy S25 and Galaxy S22**. Remaining individual checks and measurements are listed below.

Latest update, 13 November 2026: the owner reports **0.2.1 works well** after the corrected SmartTube launch, automatic live keyboard, and editable/reorderable favourites. This is a successful general feature check; the exact combinations used for the latest check were not specified. The baseline matrix below remains the record of individually reported results.

The experimental **Apps** button in 0.2.2 sent `KEYCODE_ALL_APPS` (284). The owner reported that it did not work and requested removal. Version **0.2.3** removes the button and its checklist entry. No fallback navigation macro or TV helper was added.

Initial owner testing on 13 November reported an RSA/Conscrypt pairing failure in 0.1. With 0.1.1, the owner confirmed the Guest Room TV's code was accepted, but Connected never appeared despite the TV being awake on the same Wi-Fi. After the 0.1.2 startup correction, the owner confirmed both TVs work on the S25, then confirmed the S22 works in response to the request to test it with each TV. The owner subsequently confirmed that closing/reopening the app and turning phone Wi-Fi off/back on reconnect without pairing again. Basic remote use and these reconnection checks are owner-confirmed across all four combinations. TV sleep/restart/unavailability, detailed controls, optional controls, accessibility, and performance measurements remain to be recorded. Saved names remain in place.

The [exported S25 report](reports/2026-11-13-s25.md) additionally records discovery, pairing, all navigation controls, isolated switching, reopening, Wi-Fi recovery, and play/pause as Passed for Guest Room TV. The owner subsequently confirmed the same functional results on Maria TV. Its timing, model, and service metadata are not inferred from Guest Room TV's report. The owner also reported that volume does not work with either TV even using the official Chromecast physical remotes. Treat volume as unavailable in the current TV setups and keep the app's volume/mute group disabled; volume support is not required for this personal version.

## Inventory

| Item | Model / Android or Google TV version | Network / notes |
| --- | --- | --- |
| Phone A | Galaxy S25 / Samsung SM-S931B — reported Android 17, API 37 | Exported Guest Room TV checklist; owner confirms same functional results on Maria TV |
| Phone B | Galaxy S22 — record Android version | Owner reports working on 0.1.2 |
| TV A | Guest Room TV — Google Chromecast HD; Remote Service 7.00.999235495; Google TV OS version unrecorded | Working from both phones; detailed S25 report received |
| TV B | Maria TV — record exact Chromecast model / Google TV version / Remote Service version | Working from both phones; owner confirms same S25 functional checks as Guest Room TV |

Use the app's per-TV checklist on **each phone**, and share reports after completing it. The reports are local and can be sent manually. Leave individual checks marked Not tested until specifically observed; the owner's general confirmation below does not establish every control, recovery scenario, or measurement.

## Four-combination matrix

The table records baseline 0.1.2 testing. The owner subsequently confirmed hold-to-repeat and Nuvio launching in 0.2.0, then reported that **0.2.1 works well** after the SmartTube and keyboard corrections and editable favourites. Exact phone/TV combinations for these later feature checks were not specified; see [the current feature notes](features-0.2.1.md#install-and-check).

| Check | S25 → TV A | S25 → TV B | S22 → TV A | S22 → TV B |
| --- | --- | --- | --- | --- |
| Basic remote use (owner report, 13 November) | Working | Working | Working | Working |
| Discovery | Passed (export) | Passed (owner) | Not tested | Not tested |
| Manual-IP fallback | Not tested | Not tested | Not tested | Not tested |
| Pair with on-screen code | Passed (export) | Passed (owner) | Not tested | Not tested |
| Up, Down, Left, Right | Passed (export) | Passed (owner) | Not tested | Not tested |
| Select, Back, Home | Passed (export) | Passed (owner) | Not tested | Not tested |
| Switch: only selected TV responds | Passed (export) | Passed (owner) | Not tested | Not tested |
| Close/reopen without pairing again | Working | Working | Working | Working |
| Sleep/wake; restart TV | Not tested | Not tested | Not tested | Not tested |
| Phone Wi-Fi off/on recovery without re-pairing | Working | Working | Working | Working |
| Unavailable TV handling | Not tested | Not tested | Not tested | Not tested |
| Volume/mute group | Disabled: TV setup limitation | Disabled: TV setup limitation | Disabled: TV setup limitation | Disabled: TV setup limitation |
| Play/pause in a playing media app | Passed (export); enabled | Passed (owner); setting not recorded | Not tested | Not tested |
| Large text / TalkBack | Not tested | Not tested | Not tested | Not tested |

## Important exercises

1. Run discovery and pairing for both TVs. Deliberately mistype a code, correct it, then cancel a separate pairing attempt. Ensure no credentials are saved for a failed pairing.
2. Put both TVs on different home-screen positions. Select TV A, press one direction, and verify only A moves. Switch quickly while tapping. Confirm old presses never appear on B; repeat B → A.
3. Leave the app, reopen, force-stop/reopen, then restart the phone. Saved pairings should reconnect without new codes. Removing a device should require pairing again when re-added.
4. Turn phone Wi-Fi off, wait for the unavailable state, turn it back on, and verify automatic foreground recovery. Disconnect TV power, reconnect, and use Reconnect if needed. Re-scan to refresh changed IPs. Try incorrect/old IPs and ensure the app remains usable.
5. Check TV sleep separately from complete power loss. Deep sleep may stop the remote service; wake using the physical remote. Do not assume phone power control exists.
6. Play/pause is confirmed on both TVs from the S25; enable it per TV/phone after checking. Leave volume/mute disabled for these TV setups. Revisit volume only if the TV or soundbar configuration changes and the owner wants to test it again.
7. Increase Android display/text size, use landscape, and enable TalkBack. Verify all controls are reachable and correctly named, device selection is clear, and statuses are understandable. Hold-to-repeat is intentionally absent.

## Measure on both phones (S22 is the baseline)

Reported sample for S25 → Guest Room TV on 0.1.2: first-layout activity estimate **68 ms**; connection **587 ms**. These are single app-reported observations. They do not establish full cold-start time, key response latency, repeated-run statistics, or S22 performance.

- Startup: record opening from cold and warm states, at least five trials each. The app's first-layout estimate is only an activity timing aid; use a recording or Android startup tools for the actual first visible frame. Target: usable remote displayed within 1 second, before connection finishes.
- Connection/switching: read the connection time in the status detail, repeat ten times, and record median and worst. Observe whether the first key is accepted after Connected.
- Key latency: use a video showing the finger tap and TV movement for ten presses; calculate elapsed frames divided by frame rate. Do not substitute socket-write time for TV response time.
- Size: record APK size separately from installed app/data size in Android Settings → Apps → Home Remote → Storage.
- Battery: compare a controlled period in foreground and an hour with the app closed, noting phone battery settings and Wi-Fi conditions. Confirm no background service or persistent notification.
- Final budgets for size, latency, connection time, and battery should be chosen after collecting the baseline.

## Decision gate

Proceed with the full app only after pairing, navigation, switching, and reconnection pass reliably in all four combinations and the owner accepts the documented unofficial-protocol risk. Investigate failures first. If navigation is unreliable, stop and consider Google's supported remote. Full UI polish, hold-to-repeat, release packaging, and broader features remain beyond this gate.
