# Phase 1: test on the actual devices

Status: **owner confirms basic remote use on the Galaxy S25 with both TVs; Galaxy S22 testing pending**. A successful build, unit tests, and static checks do not satisfy the full hardware decision gate.

Initial owner testing on 13 November reported an RSA/Conscrypt pairing failure in 0.1. With 0.1.1, the owner confirmed the Guest Room TV's code was accepted, but Connected never appeared despite the TV being awake on the same Wi-Fi. After the 0.1.2 startup correction, the owner reported that the app works with both TVs and clarified that this was tested only on the Galaxy S25. The S22, individual control checks, reconnection/recovery scenarios, optional controls, and performance measurements remain to be recorded. Saved names remain in place.

## Inventory

| Item | Model / Android or Google TV version | Network / notes |
| --- | --- | --- |
| Phone A | Galaxy S25 — record Android version | Owner reports both TVs work on 0.1.2 |
| Phone B | Galaxy S22 — record Android version | Not yet tested |
| TV A | Guest Room TV — record exact Chromecast model / Google TV version / Remote Service version | Working from S25, per owner |
| TV B | Maria TV — record exact Chromecast model / Google TV version / Remote Service version | Working from S25, per owner |

Use the app's per-TV checklist on **each phone**, and share reports after completing it. The reports are local and can be sent manually. Leave individual checks marked Not tested until specifically observed; the owner's general confirmation below does not establish every control, recovery scenario, or measurement.

## Four-combination matrix

| Check | S25 → TV A | S25 → TV B | S22 → TV A | S22 → TV B |
| --- | --- | --- | --- | --- |
| Basic remote use (owner report, 13 November) | Working | Working | Not tested | Not tested |
| Discover / manual-IP fallback | Not tested | Not tested | Not tested | Not tested |
| Pair with on-screen code | Not tested | Not tested | Not tested | Not tested |
| Up, Down, Left, Right | Not tested | Not tested | Not tested | Not tested |
| Select, Back, Home | Not tested | Not tested | Not tested | Not tested |
| Switch: only selected TV responds | Not tested | Not tested | Not tested | Not tested |
| Close/reopen without pairing again | Not tested | Not tested | Not tested | Not tested |
| Sleep/wake; restart TV | Not tested | Not tested | Not tested | Not tested |
| Phone Wi-Fi off/on; unavailable TV | Not tested | Not tested | Not tested | Not tested |
| Volume up/down/mute | Not tested | Not tested | Not tested | Not tested |
| Play/pause in a playing media app | Not tested | Not tested | Not tested | Not tested |
| Large text / TalkBack | Not tested | Not tested | Not tested | Not tested |

## Important exercises

1. Run discovery and pairing for both TVs. Deliberately mistype a code, correct it, then cancel a separate pairing attempt. Ensure no credentials are saved for a failed pairing.
2. Put both TVs on different home-screen positions. Select TV A, press one direction, and verify only A moves. Switch quickly while tapping. Confirm old presses never appear on B; repeat B → A.
3. Leave the app, reopen, force-stop/reopen, then restart the phone. Saved pairings should reconnect without new codes. Removing a device should require pairing again when re-added.
4. Turn phone Wi-Fi off, wait for the unavailable state, turn it back on, and verify automatic foreground recovery. Disconnect TV power, reconnect, and use Reconnect if needed. Re-scan to refresh changed IPs. Try incorrect/old IPs and ensure the app remains usable.
5. Check TV sleep separately from complete power loss. Deep sleep may stop the remote service; wake using the physical remote. Do not assume phone power control exists.
6. Test volume and mute on the actual TV/soundbar and play/pause inside a video app. Only enable main-screen controls that work consistently. Document any CEC or infrared limitations.
7. Increase Android display/text size, use landscape, and enable TalkBack. Verify all controls are reachable and correctly named, device selection is clear, and statuses are understandable. Hold-to-repeat is intentionally absent.

## Measure on both phones (S22 is the baseline)

- Startup: record opening from cold and warm states, at least five trials each. The app's first-layout estimate is only an activity timing aid; use a recording or Android startup tools for the actual first visible frame. Target: usable remote displayed within 1 second, before connection finishes.
- Connection/switching: read the connection time in the status detail, repeat ten times, and record median and worst. Observe whether the first key is accepted after Connected.
- Key latency: use a video showing the finger tap and TV movement for ten presses; calculate elapsed frames divided by frame rate. Do not substitute socket-write time for TV response time.
- Size: record APK size separately from installed app/data size in Android Settings → Apps → Home Remote → Storage.
- Battery: compare a controlled period in foreground and an hour with the app closed, noting phone battery settings and Wi-Fi conditions. Confirm no background service or persistent notification.
- Final budgets for size, latency, connection time, and battery should be chosen after collecting the baseline.

## Decision gate

Proceed with the full app only after pairing, navigation, switching, and reconnection pass reliably in all four combinations and the owner accepts the documented unofficial-protocol risk. Investigate failures first. If navigation is unreliable, stop and consider Google's supported remote. Full UI polish, hold-to-repeat, release packaging, and broader features remain beyond this gate.
