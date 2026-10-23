# Salary Tracker — UI/UX spec (v1.1)

Goal: a calm, professional personal-finance app. Simple English (the user is not a native speaker), big
clear numbers, one main action per screen, nothing hidden below the fold that the user must find.
Works offline; no network fonts or images (bundle nothing that needs a download).

## 1. Design system (`ui/theme/`, `ui/components/`)

**Colour** (no dynamic colour — keep the brand stable; the user's wallpaper is purple):
- Brand primary: emerald. Light `#0B8A5A` on white, dark `#5FD4A0` on dark.
- Light: background `#F5F7F6`, cards (surfaceContainerLow) `#FFFFFF`, surfaceContainer `#EEF2F0`,
  outlineVariant `#DCE3DF`, text `#17201B` / secondary `#5B6660`.
- Dark: background `#0D1210`, cards `#161C19`, surfaceContainer `#1E2521`, outlineVariant `#2C3530`,
  text `#E6ECE8` / secondary `#9AA69F`.
- Semantic: positive = primary, warning amber (`#B26A00` light / `#FFB74D` dark), danger red
  (`#C62828` / `#FF8A80`), credit green. Use warning when ≥ 80% of today's allowance is used, danger when over.
- Category colours: keep `Category.color`, used as icon tint on a 12%-alpha circle of the same colour.

**Type:** Roboto (system). Money uses tabular figures (`fontFeatureSettings = "tnum"`) everywhere.
Scale: hero money 44sp bold; card money 24sp semibold; titles 20sp semibold; body 16sp; labels 13sp
medium; captions 12sp. Never fixed heights on text containers (must survive font scale 1.3).

**Shape & spacing:** 4-pt grid (4/8/12/16/24/32). Screen gutter 16dp. Cards: 20dp radius, 16dp inner
padding, no elevation (tonal surfaces + 1dp outlineVariant border in light mode only). Chips 12dp radius.
Touch targets ≥ 48dp.

**Icons:** Material Symbols via `material-icons-extended` (R8 strips unused). Replace ALL category emojis with
icons: Food=Restaurant, Groceries=ShoppingCart, Travel=DirectionsCar, Bills=ReceiptLong, Rent/EMI=Home,
Shopping=ShoppingBag, Health=LocalHospital, Entertainment=Movie, Education=School, People=Person,
Cash/ATM=Payments, Transfer/Not spending=SwapHoriz, Other=Category. One `CategoryIcon(category, size)`
composable used everywhere (40dp circle in lists, 48dp in pickers).

**Shared components** (`ui/components/`): `ScreenScaffold` (top app bar + content with insets),
`SectionCard`, `SectionHeader`, `MoneyText`, `StatTile`, `CategoryIcon`, `EntryRow`, `PeriodSwitcher`
(pill: ◀ "1 Oct – 31 Oct 2026" ▶, next disabled for the current period), `EmptyState(icon, title, text,
action)`, `InfoBanner(icon, text)`, `StatusChip(ok/needed)`, `SettingRow(icon, title, value, trailing)`.

**Motion:** `animateContentSize` on expanding cards, animated ring/progress (`animateFloatAsState`,
600ms), `Crossfade` when switching periods, `AnimatedVisibility` for banners. Respect reduced motion.

**Feedback:** Snackbar after save ("Saved ₹50 · Food") and after delete with **Undo**. Light haptic on save.

**Theme setting:** Settings → Appearance: System / Light / Dark (stored in Prefs). Edge-to-edge with
correct status/navigation bar icon colours in both themes.

## 2. Navigation shell
Bottom NavigationBar: Today, History, Report, Settings (outlined icons, filled when selected).
Each tab has a TopAppBar with a title. Today's title area: greeting ("Good morning/afternoon/evening")
+ date line "Fri, 23 Oct". Content scrolls under a flat bar that tints on scroll.

## 3. Today
1. **Hero card** (primaryContainer tone): ring gauge (Canvas, 160dp) = today's spend ÷ allowance;
   centre: "₹2,581" (hero) + "left today". Below: "Spent ₹0 of ₹2,581". Ring/text colour follows the
   semantic state. When over: centre shows "₹420" + "over today" in danger colour.
2. **Two stat tiles** side by side: "Left this month ₹23,226" with a thin progress bar (month budget used)
   and caption "of ₹23,226"; "9 days left" with caption "Daily budget ₹2,581". Below the tiles a small
   caption "Your month: 1 Oct – 31 Oct · Tracking since 23 Oct".
3. **Tip line** (one sentence, computed): under budget → "You're ₹X under budget this month. Nice!";
   over → "Spend about ₹Y a day to get back on track."; first day → "Add cash spending with the + button."
4. **Last 7 days** mini bar chart (7 bars, today highlighted, allowance dashed line), card titled "Last 7 days".
5. **Salary received** card (existing feature) restyled as InfoBanner with "Use as my salary" button.
6. **Today's spending** card: `EntryRow`s divided by insets dividers; header shows count + total. Empty
   state: icon + "No spending yet today" + "Bank SMS and payment alerts show up here by themselves."
7. ExtendedFloatingActionButton "Add cash" (icon Add). List bottom padding 96dp so nothing hides under it;
   FAB collapses to icon-only while scrolling down.

`EntryRow`: CategoryIcon · merchant (1 line, ellipsis) over "8:21 PM · SMS" (source as small text, not a
chip) · amount right-aligned (tabular). Credits: "+₹500" in credit green. Excluded: amount struck-through
in secondary colour with caption "Not spending".

## 4. Add / Edit entry (ModalBottomSheet, skipPartiallyExpanded)
- Header row: title ("Add cash spending" / "Edit entry"), Delete icon button (danger) on the right when editing.
- **Amount**: large centred field "₹ 0" (40sp, tabular), autofocus + numeric keyboard when adding.
  Validation: Save disabled until amount > 0; inline error for bad input.
- **Category**: grid of 4 columns (icon 48dp + label 12sp, 2 lines max). Selected = primaryContainer
  background + primary border. "Not spending" is the last tile.
- **Shop or note**: one outlined field with leading icon.
- **When**: one row of two assist chips: "Today" / date and "8:21 PM", each opens the picker.
- **Not my spending** switch row with helper "Transfer to myself, credit card bill, investment".
- Captured entries: "Original message" expandable row (collapsed by default).
- **Sticky bottom bar**: full-width primary "Save" button (56dp). Bar sits above the keyboard (imePadding).
- Category change on a captured entry keeps the existing "Always use X for <shop>?" dialog.

## 5. History
- TopAppBar "History" with a search action → search field filters by shop/note within the period.
- PeriodSwitcher under the bar, then a summary line "Spent ₹X · N entries".
- Filters in ONE horizontally scrolling row: source segmented control is replaced by a "Source: All ▾"
  filter chip with a dropdown (All / Bank SMS / App alert / Cash), followed by category filter chips with icons.
- List grouped by day with **sticky headers** ("Today", "Yesterday", "Wed, 21 Oct") and the day total
  on the right. Rows = `EntryRow`. Tap → edit sheet.
- Empty state per filter: "No entries for these filters" + "Clear filters" button.

## 6. Report
- TopAppBar "Report", PeriodSwitcher.
- **Summary card**: 3 KPI columns: Budget · Spent · Left (or Saved for finished periods). A progress bar of
  spent vs budget. Two small chips: "✓ 18 days on budget" (positive) and "21 days over" (danger).
  Start period caption: "Tracking since 23 Oct. Earlier days are not counted."
  Before-tracking periods: InfoBanner "Before you started the app — only spending found in SMS is shown."
  and only the Spent KPI.
- **Compared with last month** (only when the previous period has data and is tracked): "₹1,240 less than
  last month (−12%)" with arrow icon, positive/danger colour.
- **Where the money went**: donut (stroke 28dp, rounded gaps between segments, centre total) + legend rows:
  CategoryIcon · name · amount · % with a thin horizontal bar in the category colour.
- **Day by day** chart: bars per day, y-axis labels (0, half, max as "₹1.2k"), x labels every 5 days, dashed
  allowance line, today outlined. **Tap a bar** → small tooltip "Wed 21 Oct · ₹340 (budget ₹2,581)".
  Keep the existing y-axis cap.
- **Top places** and **Biggest expenses**: cards with numbered rows (1–5), CategoryIcon, name, amount.
- All sections use SectionCard with SectionHeader.

## 7. Settings
Grouped SectionCards of `SettingRow`s (leading icon in tonal circle, title, value as supporting text,
trailing chevron / switch):
- **Salary & budget**: Monthly salary · Pay day ("1st of every month · 1 Oct – 31 Oct") · Savings goal ·
  Tracking since.
- **Reminders**: Evening reminder (switch) · Reminder time.
- **Automatic tracking**: Bank SMS (switch + StatusChip "Allowed" / "Needs permission" → button) ·
  Payment app alerts (switch + StatusChip; button opens notification access) · Apps to watch (→ full-screen
  list with search and checkboxes, app icons) · Alert for each new expense (switch).
- **Data**: Import old SMS (→ bottom sheet: range as selectable cards, "Import" button, progress, result
  "Found N new expenses") · Learned categories (→ list with delete).
- **Appearance**: Theme (System / Light / Dark).
- **Privacy**: card with Lock icon + the existing text.
- **Danger zone**: "Delete all data" as a danger-coloured row (not a big pink button), keeps the 2-step confirm.
- Footer: "Salary Tracker 1.1 · Works fully offline".
Edit dialogs: amount dialogs with large ₹ field + Save/Cancel; pay day dialog with the 1–30 grid + "Last day".

## 8. Onboarding
- Top: segmented progress bar (7 segments) + back arrow icon (no "Step 5 of 7" text). Bottom: sticky
  primary button (and text "Skip" where optional). Content is centred, max width 480dp.
- **Welcome**: app mark (rupee in a 96dp emerald circle), "Know what you can spend today", and 3 benefit
  rows with icons: Automatic tracking · Daily budget · Private (stays on this phone, no internet).
- **Salary**: "What is your monthly salary?", helper "Take-home pay, after tax", big ₹ field, live
  "₹1,00,000" preview. Next disabled until > 0.
- **Pay day**: grid + "Last day of month"; preview card "Your month: 1 Oct – 31 Oct".
- **Savings**: quick chips "None · 10% · 20% · 30%" (of salary) + custom field; live
  "You can spend ₹80,000 a month · about ₹2,581 a day".
- **Permissions**: 3 cards (icon, title, why, StatusChip or "Allow" button); reminder time inside the
  reminder card.
- **Import**: range as selectable cards; LinearProgressIndicator while importing; result card.
- **Done**: summary card "Your daily budget: ₹2,581", "Open my budget".

## 9. Quality bar (check on the phone)
Both themes; font scale 1.0 and 1.3; no text clipped; nothing hidden behind the FAB/nav bar/keyboard;
every icon button has a contentDescription; all copy short and plain.
