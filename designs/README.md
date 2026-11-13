# Chromecast Remote design concepts

## Current revision: v3 — grey, navy and blue

Visual mockups generated with the built-in image generation tool. Build 0.3.0 implements the regular v3 remote and settings references as native Android screens. Automated layout checks cover a 360×684dp content area for compact phones and 390×788dp for regular phones, plus settings at 390×744dp. Native renders are generated under `app/build/outputs/design/` by the design tests. Physical-device and TalkBack checks remain to be completed.

- [Regular phone v3](home-remote-regular-v3.png)
- [Compact phone v3](home-remote-compact-v3.png)

## Layout

Keep the whole everyday remote in one view. Arrange content from top to bottom:

1. TV selector, connection status and reconnect action, with a separate settings button at the right and a 12dp gap between them (revised in 0.3.2). No app name or branding icon at the top.
2. Favourite apps heading and Edit, with text-only shortcut buttons. No favourite logos, icons or monograms.
3. Keyboard action.
4. Flexible space.
5. Circular directional pad with blue OK centre, placed in the lower half for thumb reach.
6. Back, Home and Play / pause as the bottommost row, above the Android gesture safe area.

Keep the control group anchored low across phone sizes. On shorter screens, reduce the D-pad diameter and gaps, and keep favourite buttons compact and text-only. Preserve readable labels and touch targets. The concepts show two favourites; additional favourites can use an in-place paged group. Enlarged system text may require an accessible scrolling fallback.

Following phone feedback in 0.3.1, show Play / pause for both TVs, with commands disabled until connected. Verification remains a testing record rather than a visibility gate. Keep unsupported volume, power, voice, input switching, installed-app inventory and playback timeline out of the design. Centre direction glyphs geometrically inside each segment's button, independently of font metrics.

The approved 0.3.3 preview increases the circle to a maximum of 340dp, constrained by available width and height. Settings stays square and matches the device selector height. Long device names use a continuous single-line marquee; short names stay stationary. Refresh uses two clockwise arrows. Back, Home and Play / pause keep their existing native icons.

## Palette

| Role | Colour |
| --- | --- |
| Background | #191C22 |
| Raised charcoal | #252A33 |
| Navy directional pad | #172B49 |
| Hairline edge | #394451 |
| Text | #F1F4FA |
| Secondary text | #A4AFBF |
| Blue accent | #79A9F5 |

## Settings

[Settings screen](home-remote-settings.png) follows the same v3 design language, with TV management, haptic and keyboard preferences, and help. Back to remote sits at the bottom. See [settings notes and generation prompt](settings.md).

## Earlier concepts

Earlier concepts remain available as [regular v1](home-remote-regular.png), [compact v1](home-remote-compact.png), [regular v2](home-remote-regular-v2.png) and [compact v2](home-remote-compact-v2.png).

## Generation prompts

### Regular v3

Edit the referenced regular-phone Android app design, preserving its polished dark grey, navy and blue palette, device frame, typography, circular directional pad and bottom-aligned primary controls. Make exactly these design changes:
1. Remove the remote-shaped branding icon and the words "Chromecast Remote" from the top completely. Remove their dedicated branding row and reclaim its vertical space. Keep access to settings as a small gear aligned at the right side of the device-selector area, integrated cleanly beside the TV selector, without its own empty header row. Android OS status bar remains.
2. Favourite app buttons must be TEXT ONLY. Remove the blue N monogram and the SmartTube play-logo icon from the favourites. Two short equal dark charcoal buttons with ONLY centered text "Nuvio" and "SmartTube". No logos, glyphs, initials, decorative marks or replacement icons inside these two buttons. Keep "Favourite apps" title and "Edit".
Reflow the top section neatly: OS status bar, device selector "Guest Room TV" with Connected blue dot, small reconnect arrow, dropdown chevron and settings access, then Favourite apps with text-only buttons, then Keyboard button. Do not leave a blank branding row.
Keep the directional pad low in the LOWER HALF of the screen and keep Back, Home, Play / pause as the BOTTOMMOST control row above the gesture safe area. Reclaimed top space should become calm flexible space ABOVE the D-pad; don't lift the primary remote controls toward the top.
Keep the keyboard icon and navigation/control icons elsewhere; only favourite icons and top branding are removed. No other new controls. Everything stays in ONE view without scrolling. High fidelity crisp readable screen, one regular phone, portrait 1024x1536, complete slim black Samsung-style frame on neutral dark grey background. Palette: #191C22 grey background, #252A33 charcoal surfaces, #172B49 navy D-pad, #79A9F5 blue OK centre, white labels. No green or mint.

### Compact v3

Create a fresh COMPACT phone adaptation of this reference design. Preserve the EXACT revised app content and visual language. One polished Android remote screen, dark grey/navy/blue, bottom-aligned directional pad and bottommost Back Home Play / pause. The reference is the regular phone. Make a shorter compact Android phone (logical screen 360x740dp), with slightly smaller directional pad 224dp and tighter vertical gaps while preserving 48dp minimum touch targets and readable 14-16sp labels. Full phone in a subtle slim Samsung-style frame, centered on dark grey background, portrait canvas 1024x1536. One view with no scrolling.

MANDATORY screen contents in order:
OS status bar.
TV selector "Guest Room TV" with dropdown chevron and a small separated settings gear at its right; blue dot and "Connected", small reconnect arrow. No title or branding row above it.
"Favourite apps" and "Edit".
Two low equal buttons containing ONLY the centered text "Nuvio" and "SmartTube". Absolutely NO icons, logos, initials or monograms on favourite app buttons.
Compact full-width Keyboard button with keyboard icon and trailing chevron.
Flexible space above bottom controls.
LOWER-HALF circular segmented navy D-pad with white up/down/left/right chevrons and blue round OK centre.
BOTTOMMOST control row with icons and labels "Back", "Home", "Play / pause".
Android gesture bar.

Do not show the app title "Chromecast Remote" ANYWHERE, no remote brand icon, no standalone app bar or empty reserved top header. Keep other functional icons including keyboard, gear and navigation. Colours #191C22 background, #252A33 surface, #172B49 navy pad, #79A9F5 blue centre/accent, #F1F4FA text. No green/mint. Retain matte refined tactile look and high text clarity. No extra features, footer, tabs or media card. All frequent controls remain physically close to the bottom.

