# Home Remote icon concepts

Three preview concepts generated with the built-in image generation tool on 13 November 2026. These do not replace the installed launcher icon.

- `01-control-pad.png` — circular directional pad inspired by the main remote screen.
- `02-minimal-remote.png` — blue upright remote silhouette.
- `03-connected-tv.png` — television with a wireless casting symbol.

## Generation prompt set

Shared brief: Create one polished square Android launcher icon for Home Remote, a home TV remote-control app. Standalone icon, no presentation board or phone mockup. Existing palette: charcoal #191C22, navy #172B49, blue #79A9F5, off-white #F1F4FA, slate #394451. Refined flat vector-like rendering, precise curves, bold simple geometry, small-size legibility. No gradients, textures, shine, 3D, shadows, text, watermark or decorations. Main symbol within the central 66% for adaptive masking. Full-bleed charcoal background.

1. Control pad: front-facing navy circular disk, subtle slate edge, four thick off-white compass chevrons, prominent blue centre, charcoal diagonal segment separations. No OK text. Spacious and recognisable as a remote D-pad.
2. Minimal remote: chunky upright soft-blue capsule, charcoal circular directional control near its upper third, blue central dot, two charcoal pill buttons beneath. Generous negative space, front-facing without perspective or tilt.
3. Connected TV: bold soft-blue television outline with rounded corners and short centred stand; navy screen interior. Lower-left charcoal cutout contains an off-white casting dot and two thick quarter-circle arcs radiating toward the upper right. One cohesive mark, no play triangle.

## Implemented launcher icon

The enlarged second concept (`02-minimal-remote-v2-larger.png`) is now recreated as a native Android vector in `app/src/main/res/drawable/ic_remote.xml`. The manifest uses `@mipmap/ic_launcher`, which combines the transparent remote with a full-bleed charcoal background. Android supplies the outer launcher mask.

All fills are solid: blue #79A9F5, charcoal #191C22 and off-white #F1F4FA. There are no gradients, lighting effects, textures or shadows. The remote keeps the enlarged concept's proportions and occupies 82% of the visible icon height, with overscan reserved for adaptive launcher effects.

`LauncherIconTest` loads the app's actual manifest icon, checks flat interior colours and visible remote ends, and renders system, circular and rounded-square previews to `app/build/outputs/design/launcher-*.png`.

Final native-rendered previews: `04-final-flat-icon.png` and `04-final-flat-icon-circle.png`. A separate monochrome vector preserves the controls when themed icons are enabled. The focused launcher test, Android lint and debug APK assembly passed; APK signature verification passed. The installable build is `outputs/HomeRemote-0.3.3-flat-icon.apk` with a sibling SHA-256 file.
