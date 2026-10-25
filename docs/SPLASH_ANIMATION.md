# Splash / opening animation - "Allowance Ring"

Asset: `app/src/main/res/drawable/avd_splash_ring.xml` (AnimatedVectorDrawable, 600 ms). It is the
launcher foreground 1:1: the ring arc draws itself clockwise from 12 o'clock (`trimPathEnd`, 600 ms,
fast-out-slow-in), the rupee fades in and scales 0.8 -> 1.0 with a slight overshoot (250 -> 600 ms).
Everything stays inside the 36dp circle the splash mask keeps on the 108dp canvas, so no scaling.

Dependency: `androidx.core:core-splashscreen:1.0.1` (or newer 1.2.x). `installSplashScreen()` must run
**before** `super.onCreate()` (and before the existing `enableEdgeToEdge()` call) in `MainActivity`.

## 1. Theme

`values/themes.xml` and `values-night/themes.xml` (same values in both; the launcher icon is the
same blue in both modes, so the splash is too). Point the activity in the manifest at
`Theme.SalaryTracker.Starting`; `postSplashScreenTheme` swaps back to the real theme.

```xml
<style name="Theme.SalaryTracker.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">#FF007AFF</item>          <!-- flat icon blue -->
    <item name="windowSplashScreenAnimatedIcon">@drawable/avd_splash_ring</item>
    <item name="windowSplashScreenAnimationDuration">600</item>          <!-- ms, = AVD length -->
    <item name="postSplashScreenTheme">@style/Theme.SalaryTracker</item>
</style>
```

- No `windowSplashScreenIconBackgroundColor`: the window itself is the icon's blue, the white mark sits
  on it directly (240dp icon area, 160dp visible circle; ring outer edge is at 69dp radius).
- Light / dark: both `#FF007AFF`. If dark-mode testing finds it too bright next to the `#000` Today
  background, use `#FF0057D6` (the gradient's dark end) in `values-night` only. Do not use `#0A84FF`;
  it is lighter than the icon and the hand-off from the home screen would visibly jump.
- Optional: `windowSplashScreenBrandingImage` is deliberately not used (Google discourages it; keeps
  the screen one mark on one colour).

## 2. Hand-off (exit) animation

In `MainActivity.onCreate`, before `super.onCreate`:

```kotlin
val splash = installSplashScreen()
val t0 = SystemClock.uptimeMillis()
splash.setKeepOnScreenCondition {
    !vm.isFirstScreenReady.value && SystemClock.uptimeMillis() - t0 < 600L   // hard cap
}
splash.setOnExitAnimationListener { provider -> exitSplash(provider) }
```

`isFirstScreenReady` = session known (Login vs Today) and Today's allowance/ring numbers loaded. The
600 ms cap guarantees start is never slowed: if data is late the content fades in and fills itself.
Content is set with `setContent` as usual; the splash sits above it until the condition clears.

`exitSplash(provider: SplashScreenViewProvider)`:

| Layer | Property | From -> To | Duration | Interpolator |
|---|---|---|---|---|
| `provider.iconView` | scaleX/Y | 1.00 -> 1.08 | 220 ms | FastOutLinearIn |
| `provider.iconView` | alpha | 1 -> 0 | 220 ms | linear |
| `provider.view` (splash) | alpha | 1 -> 0 | 220 ms | linear |
| Compose content (`AppRoot`) | alpha | 0 -> 1 | 280 ms | FastOutSlowIn |
| Compose content | translationY | 12dp -> 0 | 280 ms | FastOutSlowIn |

Run the three View animators in one `AnimatorSet`, call `provider.remove()` in `doOnEnd`. Start the
Compose entrance at the same moment: set `vm.splashExiting = true` (or a `mutableStateOf` in the
Activity) in the listener; `AppRoot` wraps its root in `graphicsLayer { alpha; translationY }` driven by
`animateFloatAsState(if (exiting) 1f else 0f, tween(280, easing = FastOutSlowInEasing))`. The content
rises while the blue fades, so there is never a blank frame. Total hand-off 280 ms; total splash
including the AVD is well under 1 s (600 + 280 ms, and the AVD may be cut short by the exit; that is fine
because the arc is already past 80 % by 400 ms).

Icon continuity on Android 12+: the system already morphs the launcher icon into the splash icon; the
exit scales the mark slightly up and out (1.08) rather than shrinking it, so it reads as "the ring
opens into the app". Do not animate `provider.view` translation; only alpha, so the status/nav bars
stay put.

## 3. Pre-Android-12 (API 26-30)

`core-splashscreen` draws `windowSplashScreenBackground` + the first frame of the AVD (static ring at
trimPathEnd 0 and the rupee at alpha 0 would be empty, so the compat path needs a static icon):
add `<item name="windowSplashScreenAnimatedIcon">@drawable/ic_splash_static</item>` in
`values/themes.xml` and keep the AVD in `values-v31/themes.xml`. `ic_splash_static` is a plain copy of
`ic_launcher_foreground.xml` (full arc, full rupee). The exit listener and animation are identical on
all API levels because the compat library provides its own `SplashScreenViewProvider` with `iconView`.

## 4. Reduced motion ("Remove animations")

The system skips the AVD by itself when animations are off (static icon). Our exit must follow:

```kotlin
private fun motionEnabled(): Boolean =
    ValueAnimator.areAnimatorsEnabled() &&   // false when animator_duration_scale == 0
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
```

If `motionEnabled()` is false: in the exit listener call `provider.remove()` immediately and set the
Compose entrance targets directly (`alpha = 1`, `translationY = 0`, no tween; use `snap()` or skip the
`graphicsLayer`). Also skip the `setKeepOnScreenCondition` wait cap entirely (ready-condition only).
Respect the same flag for any later ring animations on Today.

## 5. Verifying on device

```sh
adb shell settings put global animator_duration_scale 1        # normal motion
adb shell am force-stop com.local.salarytracker
adb shell am start -W -n com.local.salarytracker/.MainActivity  # cold start; read TotalTime
# reduced motion
adb shell settings put global animator_duration_scale 0
adb shell am force-stop com.local.salarytracker
adb shell am start -W -n com.local.salarytracker/.MainActivity  # expect static icon, instant hand-off
adb shell settings put global animator_duration_scale 1        # restore
```

Debug builds use `com.local.salarytracker.debug` as the package but the same component path. Also check:
light and dark system theme (background identical, Today page tone differs after the hand-off); signed
out (Login appears, same entrance); Android 12 and 13+ (icon morph from the launcher); an API 26-29
emulator (static icon, same exit); `TotalTime` must not grow by more than ~50 ms versus a build with
the splash theme removed, since the splash never waits beyond the 600 ms cap.

## 6. In-app intro: why and how

Measured on a Samsung (Android 16): the platform animates the splash icon only while the system draws the
starting window. Once the app has drawn its first frame (~300 ms) the splash is handed to the app and the
icon is static, so an AVD spin never shows. The spin is therefore drawn by the app.

- `ui/IntroOverlay.kt` is a full-screen Box (`#FF007AFF`) on top of the already-composed app content, so
  data loads in parallel. Two stacked `Image`s (`intro_ring.xml`, `intro_rupee.xml`, geometry copied from
  `ic_launcher_foreground.xml`): the ring rotates 0 -> 720 degrees in 900 ms
  (`CubicBezierEasing(0.65, 0, 0.35, 1)`), the rupee stays still. Then 280 ms exit: overlay alpha 1 -> 0 and
  icon scale 1.0 -> 1.08 while the content entrance (alpha + 12 dp rise) runs; then `onDone`.
- **288 dp alignment rule:** each Image is exactly 288 dp x 288 dp. The Android 12 splash icon (no icon
  background colour, none is set in the themes) is a 288 dp canvas for the 108 unit icon, so the overlay
  lines up pixel for pixel with the system splash and the hand-off (`provider.remove()` with no animation)
  is invisible. Do not add `windowSplashScreenIconBackgroundColor` (that would change it to 240 dp).
  The overlay ignores system bar insets and centres on the whole window like the splash.
- Shown only on a real cold start: `savedInstanceState == null`, motion enabled, and not launched from a
  notification intent (add cash, report, assistant, loan person, entry). The flag lives in
  `AppViewModel.showIntro`, so rotation never replays it. The spin starts when the splash exit listener
  sets `introStarted`. Otherwise the old exit animation path (or instant removal with reduced motion) runs.
- The splash is held only until `isFirstScreenReady` (cap 1.5 s); there is no fixed 900 ms hold.
  `avd_splash_ring.xml` is now just the static full icon; `windowSplashScreenAnimationDuration` is 300.
- The overlay is hidden from TalkBack (`clearAndSetSemantics {}`) and has no click handling.
