# Settings design

[Settings screen](home-remote-settings.png)

Visual concept generated with the built-in image generation tool, matching the v3 remote's dark grey, navy and blue palette, typography, surfaces and restrained icon use. One view shows saved TVs, device management, button vibration, automatic keyboard, connection help and setup testing. Back to remote is anchored at the bottom for thumb reach. No app branding or decorative row icons. Application code is unchanged.

Device row chevrons lead to device-specific management; Manage TVs opens the existing device management and discovery workflow. Preferences use the existing haptics and automatic-keyboard settings. Connected is a sample live state; Pairing saved describes stored credentials without implying an active connection. Settings switches should save immediately. Size and touch-target intentions require validation during implementation.

## Generation prompt

Use case: ui-mockup. Create a high fidelity SETTINGS PAGE for the existing Android Chromecast remote app shown in the reference. The reference is a STYLE REFERENCE, not the target page contents. Design one fresh standalone settings screen, cohesive with the exact same product family. One complete single view with no scrolling, no content cut off. Portrait 1024x1536, straight-on front view slim Samsung Galaxy-style black phone frame, equal calm outer margins, neutral charcoal backdrop. Same device style and scale as reference. UI content is primary, crisp legible text, Android OS status bar and bottom gesture indicator.

Design bible: dark neutral grey #191C22 background; matte charcoal #252A33 row surfaces; deep navy #172B49 emphasis surfaces; cool white #F1F4FA text; muted slate #A4AFBF supporting text; soft clear blue #79A9F5 active toggles and accents; hairline #394451 borders. Match reference's gently rounded rectangles, understated subtle tactile finish, refined sans typography, concise copy and spacing. No green/mint/teal/purple, no glowing borders, no glass. Settings should feel clean and functional, restrained depth, not loud. No app logo or "Home Remote" branding anywhere. No favourite app icons or logos. No random decorative row icons. Use plain text-first rows, right chevrons for drilldown, actual switches for boolean settings. Headline 26sp, section headers 16-18sp, row labels16sp, helper text14sp. Touch targets minimum48dp. Generous but controlled rhythm, 20-24dp page margins.

EXACT CONTENT top to bottom:
1. Android status bar with time, Wi-Fi and battery.
2. Clean left-aligned page title "Settings". No marketing, app name, profile avatar or remote branding.
3. Section title "Your TVs". One grouped charcoal surface containing two rows separated by fine divider:
   "Guest Room TV" in white; under it a BLUE dot and "Connected"; right facing chevron.
   "Maria TV" in white; under it "Pairing saved" in muted slate; right facing chevron.
   A modest blue text action "Manage TVs" immediately beneath this group aligned left. No Add TV button because two TVs are already saved. Device editing, pairing, discovery and removal happen inside Manage TVs, not on this screen.
4. Section title "Preferences". One grouped charcoal surface with two generous rows and fine divider:
   "Button vibration" white with helper "Subtle feedback when you tap" muted; blue enabled Android switch at right.
   "Automatic keyboard" white with helper "Opens for text boxes on your TV" muted, may wrap to two short lines; blue enabled Android switch at right.
   These are the only toggles.
5. Section title "Help & testing". One grouped charcoal surface with two simple rows:
   "Connection help" and a right chevron.
   "Test this setup" and a right chevron.
6. Modest flexible whitespace then muted centered short reassurance "Local Wi-Fi · No account · No cloud".
7. Bottom-anchored full width rounded navy button with restrained blue border, white back arrow and readable white label "Back to remote". This is the most used navigation action on this page; place it comfortably near the bottom within thumb reach, above gesture safe area.
8. Android gesture indicator.

Everything fits a normal phone logical viewport around 390x800dp. No D-pad on settings. No bottom tab navigation. No volume, power, voice or theme setting. No account, notifications or cloud settings. Do not add unsupported functionality, extra toggle rows, report tables, lengthy protocol disclaimer, red destructive buttons, decorative logos, icons for every row, or nested box stacks. Produce exactly one complete premium readable settings screen.
