# Salary Tracker — "Liquid Glass" design (v1.5)

Apple iOS 26 style for Android (Jetpack Compose). Replaces the colour and component parts of `UI_SPEC.md` and
`COLOR_SPEC.md` ("Ink & Saffron"); layout and content rules in `UI_SPEC.md` still apply. Spec written with a
design partner (Fable 5.1) and implemented in `ui/theme/` and `ui/components/`.

## A. Colours (light / dark) — `ui/theme/Color.kt`, `Theme.kt`, `AppColors`

| Token | Light | Dark | Use |
|---|---|---|---|
| accent (systemBlue) | #007AFF | #0A84FF | buttons, links, selected tab, chart bars |
| positive / positiveFill | text #248A3D, fill #34C759 | #30D158 | saved, money in, on-budget ring |
| warning / warningFill | text #C93400, fill #FF9500 | #FF9F0A | close to a limit, allowance line |
| danger / dangerFill | text #D70015, fill #FF3B30 | #FF453A | over budget, delete |
| label / secondaryLabel / tertiaryLabel | #000 / #6E6E73 / #3C3C43 30% | #FFF / #9D9DA3 / #EBEBF5 30% | text |
| fill1..4 | #787880 at 20/16/12/8 % | 36/32/24/18 % | chips, tracks, inputs |
| separator | #3C3C43 29 % | #545458 60 % | 0.5dp hairlines |
| grouped / card / nested | #F2F2F7 / #FFF / #F2F2F7 | #000 / #1C1C1E / #2C2C2E | page / sections / inside a card |

Text colours keep 4.5:1 on cards; the *Fill colours are for rings, bars, icon tiles and filled buttons.
Material3 slots: primary = accent, background/surface = grouped, surfaceContainerLow = card, surfaceContainer = nested,
onSurfaceVariant = secondaryLabel, outlineVariant = light separator.

**Categories** (`Category.color` light, `Category.tint()` picks the dark version): Food red #FF3B30/#FF453A ·
Groceries green #34C759/#30D158 · Travel cyan #32ADE6/#64D2FF · Bills yellow #FFCC00/#FFD60A · Rent/EMI indigo
#5856D6/#5E5CE6 · Shopping pink #FF2D55/#FF375F · Health mint #00C7BE/#63E6E2 · Movies purple #AF52DE/#BF5AF2 ·
Education blue #007AFF/#0A84FF · People orange #FF9500/#FF9F0A · Cash teal #30B0C7/#40C8E0 · Not spending gray
#8E8E93/#98989D · Other brown #A2845E/#AC8E68. Icons are white glyphs on a rounded square of the colour (dark glyph on
yellow and mint in light theme).

## B. Glass — `ui/components/Glass.kt` (Haze 2.0.1)

- **Refracting glass** (`Modifier.liquidGlass`, Haze glass shader, Android 13+ full, 12 render-effect, older blur
  material): only the floating tab bar and its shrunk pill. Header buttons and the action capsule use the Thin blur
  material (refraction over text in the header was hard to read).
- **Blur materials** (`GlassMaterial`, `Modifier.glassBlur`, `GlassSurface`):

| Material | Blur | Tint light / dark | Fallback (no blur) | Used by |
|---|---|---|---|---|
| UltraThin | 12dp | white 35 % / #1C1C1E 35 % | 72 % | — |
| Thin | 20dp | 55 % / 55 % | 86 % | collapsed top bar, onboarding bottom bar |
| Regular | 28dp | 70 % / 65 % | 93 % | hero cards (Today, Lending, Report, onboarding done), Ask input |
| Thick | 36dp | 86 % / 82 % | 97 % | dialogs (solid tint) |

  1px white highlight rim (light 45–80 %, dark 10–18 %), soft shadow on Thin+.
- **Sources**: `AppWallpaper` (grouped colour + 3 radial glows: blue/green/orange light, blue/indigo/cyan dark) is
  `LocalWallpaperHaze`; wallpaper + tab content is `LocalContentHaze` (tab bar, "+"); each `ScreenScaffold`'s list is
  `LocalScreenHaze` (its top bar and header buttons). Glass never samples the source it sits in.
- Sheets and dialogs live in their own window, so they use solid colours, not blur: sheets #F2F2F7 (light) /
  #1C1C1E (dark) with fields #FFF / #2C2C2E (`sheetBackground()`, `sheetCard()`), 12dp gap under the status bar.
- Every `ScreenScaffold` draws a wallpaper inside its blur source and the materials use no grain: blurring
  see-through pixels with grain drew dotted lines on Android.
  List rows, forms and grouped sections are always solid.
- Android 11 and older: same tints, more opaque, no blur.

## C. Shape & spacing — `ui/components/Apple.kt`

Glass cards 24 · grouped sections 20 · capsules (buttons, chips, pills, tab bar, period switcher, search) · sheets 36
(top) · dialogs 28 · fields 14 · icon tiles 25 % of size (29dp in settings, 40dp in lists). Gutter 16, section gap
24, rows 44 / 56 / 60–64, separators 0.5dp inset to the text (57dp after a 29dp tile, 68dp after a 40dp tile).

## D. Type — Inter (bundled subset `res/font/inter.ttf`, weight 400–700, optical size 14–32, OFL: `docs/licenses/Inter-OFL.txt`)

Large Title 34/41 bold · Title1 28/34 bold · Title2 22/28 bold · Title3 20/25 600 · Headline 17/22 600 · Body 17/22 ·
Subhead 15/20 · Footnote 13/18 · section header 13/18 500 upper case · Caption 11/13 500. Big sizes use optical size
32 (`InterDisplay`), text sizes 14 (`InterText`). Money (tabular figures): Hero 40, Card 28, Row 17, Small 13, Input 44.

## E. Components

- `ScreenScaffold`: large title (+ subtitle) that scrolls away; a 52dp thin-glass bar with a centred title fades in;
  navigation button and actions on round glass; round settings button (account initial) on tab screens.
- `FloatingTabBar` (64dp glass capsule, 5 tabs, sliding lens behind the selected one, labels shrink to 9sp). It hides
  while the keyboard or Settings is open. The **"+"** is in the page header, top-right before the account button
  (`LocalAddAction`, iOS style): Add cash on Today / History / Report, Add person on Lending home, none on Ask or a
  person page. On History and Lending it shares the glass capsule with Search. Content gets
  `LocalBottomBarPadding` (bar + margins + navigation bar).
- `GroupedSection` + `GroupHeader`/`GroupFooter`, `Hairline`, `IconTile`, `ListRow`, `SettingRow` (tile colour from
  `LocalSettingTint`, one colour per Settings group), `CapsuleButton` (Filled / Tinted / Glass / Destructive / Plain,
  50dp or 36dp small, press scale 0.96), `AppleChip`, `SegmentedControl`, `appleSwitchColors()` (green),
  `appleFieldColors()`, `RoundGlassButton`, `TintPill`, `StatusChip`, `InfoBanner` (tinted card + icon tile),
  `PeriodSwitcher` (grey capsule, blue chevrons), `StatTile`, `EntryRow`, `PersonAvatar` (colour gradient + white initial).
- Charts: bars blue 45 % (today full blue, over red), dashed orange allowance line, rounded caps; donut 20dp with
  category colours; Today ring 14dp green → orange (< 20 % left) → red ("Over today").
- Ask: user bubbles blue with white text (tail bottom-right), bot bubbles card colour (tail bottom-left), gradient
  sparkle mark, glass capsule input with a round blue send button.

## F. Screens

Today (large title "Today", greeting + date subtitle, glass ring hero, stat tiles, tip, grouped Last 7 days and
today's entries) · History (search capsule, chips with category colours, frosted sticky day headers, one grouped
section per day) · Lending (glass hero green / red, chips, grouped people) and person page (inline title, tinted
capsule actions) · Report (glass KPI hero, grouped sections) · Ask · Settings page (slides in, back button, grouped
sections with coloured tiles, segmented theme control) · sheets (grouped background, white fields, capsule chips) ·
Login and Onboarding (wallpaper, blue app mark, capsule buttons).

## G. Motion & accessibility

Tab crossfade 180ms; tab lens spring; press scale 0.96 (off with reduced motion); title collapse follows the scroll;
tab bar slides away for the keyboard (200ms). Text sits only on Thin or stronger glass; the fallback tint is the
contrast worst case; font scale 1.3 never clips (min heights, not fixed heights); touch targets ≥ 48dp; states are
also written in words ("Over today", "Overdue").

## v1.6 changes

- **Colours**: the wallpaper glows are gone. Backgrounds are Apple's plain grouped colours (#000 dark, #F2F2F7
  light); colour comes from icons, rings, charts and the blue accent only.
- **Tab bar (Apple Music style)**: 64dp clear refracting glass capsule, filled icons for every tab (label colour),
  the selected tab in an accent-tinted glass pill with a light rim, accent icon and label. Scrolling down (> 24dp)
  shrinks it to [58dp glass circle with the current tab's icon] [glass pill: 30dp ring + "₹X left today"]
  [58dp glass "+"]; scrolling up, a tab change, Settings or tapping the circle / pill brings the full bar back.
  Content keeps the full bar's bottom padding. Reduced motion: no morph animation.
- **Spending map**: Mapbox Standard style (day / night preset by theme), circles in category colours with a white
  2dp stroke (7–16dp, square-root of the amount), glass header capsule, glass "My location" button, thick-glass card
  for the tapped entry.

### v1.6 polish
- Tab bar motion like iOS 26: the lens slides with two springs (leading edge fast, trailing edge slow, so it
  stretches); press lifts it 16% and it follows the finger with a tick per tab; shrinking fades and blurs the tabs
  first (gone by 40%), then the "left today" pill comes in and the selected icon slides into the circle.
- Tapping the current tab again scrolls its list to the top (`LocalScrollToTop`, `ScrollToTopEffect`).
- Ask chat: opens at the newest message; a new question scrolls to the end, a new answer to its start; a "newest
  message" button when scrolled up; day/time lines; long-press copies; "Try again" on failed AI answers.
