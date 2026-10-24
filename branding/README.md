# Salary Tracker - branding

Three launcher-icon concepts for the "Liquid Glass" (iOS 26-style) version of the app, plus
drop-in Android assets for the recommended one. Open `preview.html` to see every concept at
320 / 192 / 96 / 48 px inside the circle, squircle and rounded-square masks on light and dark
home screens.

Every SVG is the full 1024 x 1024 adaptive-icon canvas (= 108 dp). Launchers keep only the
central 72 dp, and the mark always sits inside the 66 dp safe zone. Coordinates in the SVGs are
written in the 108 dp space and scaled by 1024 / 108, so an SVG path and its VectorDrawable
path are the same numbers.

| File | Concept | Idea |
|---|---|---|
| `concept-1.svg` | **Allowance Ring** (recommended) | White rupee inside the app's Today ring (80 % arc, open at the top) on system-blue glass. |
| `concept-2.svg` | Thirty Slices | Ink rupee ringed by 30 ticks, 19 of them blue: the month cut into daily slices, on white glass. |
| `concept-3.svg` | Pay Day | A blue calendar page holding a white rupee with a green allowance bar, on graphite. |

## Recommendation: Concept 1, "Allowance Ring"

- It is the product in one picture: the Today screen's ring with the rupee in it. The ring is also
  what the collapsed tab bar shows ("30dp ring + ₹X left today"), so the icon and the UI rhyme.
- It is the only concept that still reads at 36 px and in the circle mask without losing anything;
  concept 2's ticks turn into a dotted ring below 48 px and concept 3 needs the page, pins and
  bar to be big enough to survive.
- System blue is already the app's accent (`#007AFF` / `#0A84FF`), so the icon matches the login
  screen's "blue app mark" and the selected tab, and it sits well next to Apple-style icons.
- It flattens cleanly: solid `#007AFF` behind the same white mark looks nearly identical, which is
  what the Play Store listing, the monochrome layer and the notification icon rely on.
- It is 6 paths and one colour in the foreground, so it works as an adaptive icon, a themed icon,
  a notification icon and an in-app mark without redrawing.

### Colours (Concept 1)

| Role | Value | Notes |
|---|---|---|
| Background gradient | `#2E9BFF` -> `#0057D6` (top-left to bottom-right) | flattens to `#007AFF` |
| Sheen | white at 22 % -> 0 %, radial from top-left | optional; skip it for a flat version |
| Ring track | `#FFFFFF` at 28 % | 6 dp stroke, r = 28 dp |
| Ring arc | `#FFFFFF` | 6 dp stroke, 288 deg clockwise from 12 o'clock, round caps |
| Rupee | `#FFFFFF` | 5 dp stroke, round caps and joins |
| Flat background colour | `#FF007AFF` | for `@color/ic_launcher_background` |
| Notification `setColor` | `#FF007AFF` | light theme; `#FF0A84FF` in dark |

Concept 2 uses `#007AFF` ticks, `#C7C7CC` ticks and a `#1D1D1F` rupee on `#FFFFFF -> #ECECF1`.
Concept 3 uses a `#1E8EFF -> #0066E6` page, `#0A3F9C` at 55 % header, `#34C759` bar on
`#2C2C30 -> #0A0A0C`.

## Wiring the `android/` files into the app

The app already has `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`
pointing at `@drawable/ic_launcher_foreground` and `@color/ic_launcher_background`, and the
old foreground is a VectorDrawable at `res/drawable/ic_launcher_foreground.xml`.

1. **Foreground**: copy `android/ic_launcher_foreground.xml` over
   `app/src/main/res/drawable/ic_launcher_foreground.xml`.
2. **Background**, pick one:
   - Flat: change the value in `app/src/main/res/values/ic_launcher_background.xml` from
     `#FF4338CA` to `#FF007AFF`. Nothing else changes.
   - Gradient: copy `android/ic_launcher_background.xml` to `app/src/main/res/drawable/` and
     change both mipmap XMLs to `<background android:drawable="@drawable/ic_launcher_background" />`.
     (Keep the `@color` resource too if anything else references it.) The gradient uses
     `aapt:attr`, which needs AGP's resource inlining and minSdk >= 24; adaptive icons are v26+ so
     this is fine.
3. **Monochrome (Android 13+ themed icons)**: copy `android/ic_launcher_monochrome.xml` to
   `app/src/main/res/drawable/` and change the `<monochrome>` line in both mipmap XMLs from
   `@drawable/ic_launcher_foreground` to `@drawable/ic_launcher_monochrome`. (The foreground would
   also work, but the track at 28 % alpha turns into a grey halo when tinted.)
4. **Notification icon**: copy `android/ic_notification.xml` to `app/src/main/res/drawable/`.
   `Notifier.kt` and `AssistantWorker.kt` call `setSmallIcon(R.drawable.ic_stat)`; either replace
   the contents of `ic_stat.xml` with this file or switch those calls to `R.drawable.ic_notification`.
   Pair it with `setColor(0xFF007AFF.toInt())`.
5. Clean build; delete any leftover legacy `mipmap-*/ic_launcher*.png` or `.webp` so pre-26
   devices do not keep the old bitmap. If the Play listing needs a 512 px PNG, export
   `concept-1.svg` at 512 px and crop to the central 66.7 % (the 72 dp area), or render it full and
   let Play's rounding take care of the corners.

The in-app "blue app mark" on Login / Onboarding can reuse the same six paths from
`ic_launcher_foreground.xml` on a `#007AFF` circle so the mark matches the launcher.

## Checks done

- Rendered `preview.html` with headless Edge and inspected 320 / 192 / 96 / 48 / 36 px rasters on
  light and dark backgrounds; thickened concept 2's ticks (2.6 -> 3 dp) and enlarged concept 3's
  page after the first pass.
- All four VectorDrawables parse as well-formed XML and use only `path` with `pathData`,
  `strokeColor`, `strokeWidth`, `strokeAlpha`, `strokeLineCap`, `strokeLineJoin`, plus a
  `gradient` under `aapt:attr` in the background. No text, clips or filters.
