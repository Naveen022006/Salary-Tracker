# Salary Tracker - architecture (part 1: core + data)

Package `com.local.salarytracker`. All money = `Long` paise. Pure logic (parse/, budget/, model/) has no Android imports.

## Versions that build
Gradle 9.4.1 (wrapper) / AGP 9.2.1 (built-in Kotlin: do NOT apply `kotlin-android`) / Kotlin 2.2.21 (+ `kotlin-compose` plugin) /
KSP 2.3.12 / Room 2.8.4 / Compose BOM 2026.09.00 (needs **compileSdk 37**; targetSdk 36, minSdk 26) / WorkManager 2.10.5 /
activity-compose 1.11.0 / navigation-compose 2.9.5 / lifecycle 2.9.4 / coroutines 1.10.2 / JUnit 4.13.2. JDK 17.
`local.properties` must use forward slashes for sdk.dir. Commands: `gradlew.bat testDebugUnitTest`, `gradlew.bat assembleDebug`.
Manifest has the INTERNET permission since 1.2 (Google sign-in + Supabase sync); allowBackup=false + backup rules exclude everything.
Cloud libs: supabase-kt BOM 3.2.6 (`auth-kt`, `postgrest-kt`), `ktor-client-okhttp` 3.3.1, kotlinx-serialization-json 1.9.0 (+ serialization plugin), androidx.credentials 1.5.0 (+ `credentials-play-services-auth`), googleid 1.1.1; test: sqlite-jdbc.
WorkManager merges WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE (harmless).

## model/Enums.kt
- `TxnType {DEBIT, CREDIT}`, `TxnSource {SMS, NOTIFICATION, MANUAL}`
- `Category(displayName, emoji, color: Long ARGB, isFixed)`: FOOD GROCERIES TRAVEL BILLS(fixed) RENT_EMI(fixed) SHOPPING HEALTH ENTERTAINMENT EDUCATION PEOPLE CASH TRANSFER OTHER; `Category.fromName(String?)`.

## parse/
- `ParsedTxn(viaGateway: Boolean = false` (payee routed via RAZORPAY*/rzp/PAYU*/CASHFREE*/BHARATPE*/P2M etc: always a business; `Categorizer.categorize(..., viaGateway)` never returns PEOPLE for it). `ParsedTxn(amountPaise, type, merchant?, accountLast4?, upiRef?, excluded, isSalary, timestampHint=null)` (timestampHint is never set yet; use the SMS/notification time).
- `SmsParser.parse(sender, body): ParsedTxn?`, `SmsParser.isBankSender(sender)`. Phone-number / non DLT senders -> null.
- `NotificationParser.parse(pkg, title, text): ParsedTxn?` (SMS-app packages ignored; shopping/food apps force merchant = app name).
- `DEFAULT_ALLOWED_PACKAGES: List<String>`, `SMS_APP_PACKAGES: Set<String>`.
- `Categorizer.categorize(merchant, text, learnedRules: Map<keywordLowercase, Category>): Category`.
- `Dedupe.isDuplicate(TxnKey, TxnKey)`, `Dedupe.WINDOW_MS`, `Dedupe.bodyHash(source, sender, body, timestamp)`; `TxnEntity.toKey()`.
  Rule: MANUAL never merges; both refs present -> equal refs; else same amount (and type) within 10 min when SMS<->NOTIFICATION or NOTIFICATION<->NOTIFICATION (any package, same or different); SMS<->SMS without a shared ref stay separate. See "Payment app catalog" below.
- `ParserCore` is internal (shared engine).

## data/
- `TxnEntity`, `CategoryRuleEntity(keyword PK lowercase, category)`, `SettingsEntity` (id=0; `allowedPackageSet`), `Converters`.
- `TxnDao` (insert IGNORE -> -1 on bodyHash clash, update, delete, getById, observeRange/getRange [from,to), observeDay, observeDebitSum, observeAll, earliest/latestTimestamp, findCandidates, findByMerchant, deleteAll), `SettingsDao`, `RuleDao`.
- `AppDatabase.get(context)` singleton (db name `salary_tracker.db`, schema export in `app/schemas`). Part 2 should bump version + add migrations on change.
- `Repository(AppDatabase.get(ctx))`:
  - settings: `settings: Flow<SettingsEntity>`, `getSettings()`, `saveSettings(s)`, `updateSettings { it.copy(...) }`
  - queries: `observeTxns(fromMs,toMs)`, `getTxns`, `observeDay`, `observeDebitSum`, `observeAll`, `getTxn(id)`, `earliestTimestamp()`, `latestTimestamp()`, `learnedRules()`, `observeRules()`, `deleteRule(keyword)`
  - writes: `addCaptured(parsed, source, sender, timestamp, rawText): TxnEntity?` (categorize + dedupe/merge + insert; null = duplicate/already imported; excluded -> category TRANSFER), `addManual(amountPaise, category, note, timestamp)`, `updateTxn`, `deleteTxn`, `deleteAllTxns`, `setCategory(txn, category, rememberForMerchant)`

## budget/BudgetEngine.kt (java.time, zone passed by caller)
- `Period(start, endInclusive)` with `days`, `contains`, `startMillis(zone)`, `endExclusiveMillis(zone)` (use for DAO range queries).
- `BudgetEngine.currentPeriod(today, payDay)`, `periodContaining(date, payDay)`, `previousPeriod(period, payDay)`, `nextPeriod(period, payDay)`, `importStartMillis(today, payDay, zone, periodsBack=3)`.
- `SpendItem(date, amountPaise, category, excluded, type, merchant=null)` - map TxnEntity -> SpendItem using `Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()`.
- `compute(period, today, salary, savingsGoal, items): BudgetSnapshot` -> daysTotal, daysLeft, spendablePaise, baseDailyPaise, days: List<DayBudget(date, allowancePaise, dailySpendPaise, fixedSpendPaise, overUnderPaise)>, todayAllowancePaise, leftTodayPaise, leftMonthPaise, spentTotalPaise.
  Note: with no spending, allowance rises each day (pool / remaining days) - that is the specified rollover formula. `overUnderPaise = allowance - dailySpend` (positive = under).
- `report(...)`: `PeriodReport` (finished, salary, spent, saved, byCategory, daysUnder/Over/Exact, topMerchants(5), biggest(5), daily points, snapshot). Works for past periods (today after end).

## Part 2 notes
- SMS inbox scan must take a `sinceMillis` parameter (user's newest SMS may be months old); default via `importStartMillis`, but offer picking older periods. Pass inbox message date as `timestamp` and sender address as `sender`.
- Call `SmsParser.parse` then `Repository.addCaptured(parsed, TxnSource.SMS, sender, ts, body)`; for notifications use `TxnSource.NOTIFICATION` with package as sender. Credits with `isSalary` should be offered as "update salary", not auto-set.
- Notification listener should filter by `SettingsEntity.allowedPackageSet`.
- Tests: 111 JVM tests (SmsParserTest 48 methods / ~65 sample messages, NotificationParserTest 20, CategorizerTest 11, BudgetEngineTest 22, DedupeTest 10).

---

# Part 2: Android layer + UI

## Build / signing
- `keystore.properties` (root, ignored by git; copy from `keystore.properties.example`) points to `salarytracker.keystore` (alias `salarytracker`). `app/build.gradle.kts` reads it for the `release` signing config. Release has R8 minify + shrinkResources on (`app/proguard-rules.pro` keeps `data.**`, `model.**`, `ReminderWorker`).
- Build: `gradlew.bat testDebugUnitTest assembleRelease`; output `app/build/outputs/apk/release/app-release.apk`, copied to `E:\salary-tracker\SalaryTracker.apk`. APK is v2-signed (minSdk 26 is fine with v2).
- Permissions in the APK: READ_SMS, RECEIVE_SMS, POST_NOTIFICATIONS + WorkManager's WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE. INTERNET (cloud sync). Check: `aapt2 dump permissions SalaryTracker.apk`.

## Components (package `com.local.salarytracker`)
- `App` - creates notification channels ("Daily reminder", "New expense"), calls `ReminderScheduler.sync(replace=false)`.
- `capture/Capture` - shared pipeline: `sms(ctx, sender, body, ts)` and `notification(ctx, pkg, title, text, ts)` -> parse -> `Repository.addCaptured` -> optional capture alert. Returns a result string (logged to logcat tag `SalaryTracker`). Respects `smsEnabled` / `notifEnabled` / `allowedPackages`.
- `capture/SmsReceiver` - `SMS_RECEIVED` (permission BROADCAST_SMS), joins multipart PDUs per sender, `goAsync()` + coroutine.
- `capture/PaymentNotificationListener` - NotificationListenerService; skips own package, ongoing and group-summary notifications; uses EXTRA_TITLE + EXTRA_TEXT/BIG_TEXT; SMS apps and non-allowed packages are dropped in `Capture.notification`.
- `capture/InboxScanner(ctx).scan(sinceMillis)` - reads `content://sms/inbox` (`date > since`), stores via `addCaptured`, sets `lastSmsScanTime`. Used by onboarding / Settings import (ranges 3, 6, 12 months, all = since 0) and by the catch-up scan on every `MainActivity.onResume` (since `lastSmsScanTime`, only when permission granted, `smsEnabled`, onboarding done).
- `capture/TestReceiver` - exported, `android:permission="android.permission.DUMP"` (only adb shell). Actions below.
- `work/ReminderScheduler` - unique periodic work `daily_reminder` (24 h, initial delay to next reminder time; UPDATE when time changes or toggled, KEEP on app start; cancelled when reminder is off or onboarding is not done). `ReminderScheduler.runNow()` enqueues the real `ReminderWorker` once.
- `work/Notifier` - capture alert ("₹250 · Swiggy · Food — left today ₹430", tap -> edit screen via extra `txn_id`) and daily reminder ("Today you spent ₹X. Did you spend any cash? Tap to add.", tap and "Add cash" button -> extra `open_add_cash`).
- `Prefs` - SharedPreferences for the reminder on/off flag (not in Room, so no DB migration was needed). `BudgetService.snapshotNow` - current-period snapshot used by notifications.
- `util/Money` - Indian-grouping formatter (`₹1,25,000`, paise only when non-zero), input parser. Has `MoneyTest`.
- Data additions (no schema change): `TxnDao.observeEarliest()`, `RuleDao.deleteAll()`, `Repository.observeEarliest()`, `Repository.deleteAllRules()`.

## UI (Compose Material 3, light + dark; `ui/`)
`AppViewModel` (flows: `settings`, `todayState`, `historyState`, `reportState`, `rules`, `importState`, `editor`), `AppRoot` (loading / `OnboardingScreen` / `MainScreen` with bottom nav Today, History, Report, Settings), `TodayScreen`, `HistoryScreen`, `ReportScreen` + `Charts.kt` (Canvas donut and daily bars with allowance line), `SettingsScreen` (incl. "Apps to watch" dialog using `queryIntentActivities`, manifest has `<queries>` for MAIN/LAUNCHER), `EntryEditorSheet` (add/edit/delete, "Always use <category> for <merchant>?", original SMS text), `Permissions.kt`, `Pickers.kt`.
Notes: past periods use the *current* salary/savings setting; "Use as my salary" card appears for a recent credit the parser flagged as salary (re-parsed from `rawText`) whose amount differs from the saved salary.

## adb test commands
Use an explicit component (implicit broadcasts are not delivered to manifest receivers on Android 8+). From PowerShell or cmd, wrap the whole remote command in double quotes and use single quotes inside; extras with spaces must be inside the single quotes:

```
adb shell "am broadcast -n com.local.salarytracker/.capture.TestReceiver -a com.local.salarytracker.TEST_SMS --es sender VM-HDFCBK --es body 'Rs.250.00 debited from A/c XX1234 on 23-Oct-26 to VPA swiggy@icici (UPI Ref 123456789012). Not you? Call 18002586161'"
adb shell "am broadcast -n com.local.salarytracker/.capture.TestReceiver -a com.local.salarytracker.TEST_SMS --es sender VM-HDFCBK --el time 1779000000000 --es body 'Rs.99.00 debited from A/c XX1234 to VPA zomato@axis. UPI Ref 223456789012'"
adb shell "am broadcast -n com.local.salarytracker/.capture.TestReceiver -a com.local.salarytracker.TEST_NOTIF --es pkg com.phonepe.app --es title 'Paid Rs 120' --es text 'Paid Rs 120 to Chai Point'"
adb shell "am broadcast -n com.local.salarytracker/.capture.TestReceiver -a com.local.salarytracker.TEST_REMINDER"
adb shell "am broadcast -n com.local.salarytracker/.capture.TestReceiver -a com.local.salarytracker.TEST_DELETE --el id 42"
adb shell "am broadcast -n com.local.salarytracker/.capture.TestReceiver -a com.local.salarytracker.TEST_DELETE --es merchantPrefix Test --el sinceMillis 1779000000000"
adb logcat -s SalaryTracker
```
(`--el time` = epoch millis, optional; default now.) In Git Bash prefix with `MSYS_NO_PATHCONV=1`. The notification test only works for packages in the "Apps to watch" list. `TEST_REMINDER` runs the real `ReminderWorker` once and posts the reminder notification. `TEST_DELETE` deletes one txn by `--el id`, or all txns with timestamp >= `--el sinceMillis` whose merchant starts with `--es merchantPrefix` (case-insensitive); logs `TEST_DELETE: deleted N` to tag SalaryTracker. A successful save logs `saved #id DEBIT ₹250 merchant=... category=...`; otherwise the log says why it was ignored.

---

# Part 3: tracking start date, money display, UI fixes

- **trackingStart (LocalDate)** is stored in SharedPreferences `prefs`, key `tracking_start_epoch_day` (`Prefs.trackingStart/setTrackingStart`). **No Room schema change, DB version stays 1, no migration needed** (existing data survives `adb install -r`). Set to today when onboarding finishes; on the first run of this version for an already-onboarded install, `AppViewModel` init / `BudgetService.trackingStart` stores today. Settings > Money > "Tracking since" opens a date picker (future dates are clamped to today). `AppViewModel.trackingStart` is a StateFlow combined into `todayState` and `reportState`.
- `BudgetEngine.compute/report(..., trackingStart: LocalDate? = null)`. Days before trackingStart in the period that contains it are "not tracked": pool = spendable - baseDaily x untrackedDays; entries before it are ignored; `days` only lists tracked days. Snapshot gets `trackingStart` (non-null only when it cuts the period), `trackedDays`, `budgetPaise` (= pool). Example: 80,000 / 31 days / start 23 Oct -> today's allowance Rs 2,580.65, budget Rs 23,226.
- A period that ends before trackingStart gives `PeriodReport.beforeTracking = true`: only Spent, categories, top places, biggest and daily spend bars (no salary / saved / under-over / allowance line). Report UI shows "Before you started the app - only spending found in SMS is shown."
- Start period report: "Budget for 23-31 Oct" row replaces Salary; "Left"/"Left so far" is relative to that budget.
- `Money.formatWhole(paise)` = whole rupees rounded (used for Today, Report totals, capture alert, reminder); `Money.format` keeps paise when non-zero (individual entries). `Money.cleanInput` strips non-digits and leading zeros for all amount fields.
- `DailyBarChart(period, daily, baseDailyPaise, showAllowance)`: y-axis capped at max(2 x baseDaily, 1.1 x highest daily spend); allowance line clipped there with a small triangle marker.
- Entry editor: Save / Delete live in a bottom bar outside the scrolling form. Today progress bar uses `gapSize = 0.dp, drawStopIndicator = {}`.

---

# UI part 1 (design system, shell, Today, Add/Edit)

Spec: `docs/UI_SPEC.md`. History / Report / Settings / Onboarding still have the old layout (only adapted to compile); part 2 redesigns them with the components below.

## Theme (`ui/theme/`)
- `SalaryTrackerTheme(darkTheme = isSystemInDarkTheme(), content)` - emerald brand, no dynamic colour. Cards = `surfaceContainerLow`, tonal bits = `surfaceContainer`, hero = `primaryContainer`.
- `AppColors.current` (CompositionLocal `LocalAppColors`): `positive, warning, danger, credit, isDark`. `creditColor()` still exists.
- `MoneyStyle.Hero|Card|Row|Small|Input` - TextStyles with tabular figures (`tnum`). Use with `Text` / `MoneyText`.
- `Prefs.themeMode(ctx)/setThemeMode` (`ThemeMode {SYSTEM, LIGHT, DARK}` in `model/Enums.kt`); `AppViewModel.themeMode: StateFlow<ThemeMode>` + `setThemeMode(m)`. `MainActivity` applies it and calls `enableEdgeToEdge` with matching bar icon colours. **Part 2: add Settings > Appearance row that calls `vm.setThemeMode`.**

## Components (`ui/components/`, package `com.local.salarytracker.ui.components`)
- `ScreenScaffold(title, modifier, titleContent: (@Composable () -> Unit)? = null, navigationIcon = {}, actions: RowScope.() -> Unit = {}, snackbarHost = {}, floatingActionButton = {}, bottomBar = {}, contentWindowInsets = ScaffoldDefaults.contentWindowInsets, content: (PaddingValues) -> Unit)` - flat TopAppBar, tints on scroll. Put `padding.calculateTopPadding()` in the list `contentPadding`. In `MainScreen` each tab is wrapped in one with `WindowInsets(0)`; History/Report/Settings still receive `(vm, padding)`.
- `SectionCard(modifier, containerColor, contentColor, contentPadding = PaddingValues(16.dp), onClick: (() -> Unit)? = null, content: ColumnScope.() -> Unit)` - 20dp radius, border in light only, `animateContentSize`.
- `SectionHeader(title, modifier, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null)`; `ScreenGutter = 16.dp`.
- `MoneyText(paise, modifier, style = MoneyStyle.Card, color, whole = true, plus = false, strikethrough = false, textAlign, maxLines = 1)`.
- `StatTile(label, value: String, modifier, caption: String? = null, progress: Float? = null, valueColor, progressColor)`.
- `CategoryIcon(category, size = 40.dp, modifier, credit = false)`; extension props `Category.icon: ImageVector` and `Category.label` ("Not spending" for TRANSFER).
- `EntryRow(t: TxnEntity, zone: ZoneId, modifier = Modifier, onClick: () -> Unit)` - the old `ui.EntryRow`, `ui.PeriodSwitcher`, `SourceChip` were removed from `Common.kt`.
- `PeriodSwitcher(label, canGoBack, canGoForward, onBack, onForward, modifier)` - pill, label cross-fades.
- `EmptyState(icon, title, text, modifier, action: (@Composable () -> Unit)? = null)`.
- `InfoBanner(icon, text, modifier, tone: BannerTone = Info|Positive|Warning|Danger, action: (@Composable () -> Unit)? = null)`.
- `StatusChip(ok: Boolean, modifier, okLabel = "Allowed", neededLabel = "Needs permission")`.
- `SettingRow(icon, title, modifier, value: String? = null, onClick: (() -> Unit)? = null, danger = false, trailing: (@Composable () -> Unit)? = null)` - chevron when `onClick` and no trailing. For switch rows pass `trailing = { Switch(...) }` and an `onClick` that toggles.
- `rememberReducedMotion()`, `rememberAppTween<T>(millis = 600)` (instant when animations are off).
- `Fmt` (in `ui/Common.kt`) gained `greeting(hour)` and `weekdayDate(d)`.

## Shell / Today / Editor
- `MainScreen`: one Scaffold (Snackbar host, NavigationBar with outlined/filled icons, ExtendedFAB "Add cash" shown on Today; collapses while scrolling down via `LazyListState.isScrollingUpOrAtTop()`).
- Snackbar: `AppViewModel.messages: MutableSharedFlow<UiMessage(text, actionLabel?, onAction?)>`. `addEntry`/`updateEntry` post "Saved ₹50 · Food"; `deleteEntry` posts "Entry deleted" + Undo (`Repository.restoreTxn` re-inserts the entity with its old id).
- `TodayState` gained `last7: List<DayBar>` and `tip: String`. Pure helpers (no Android) in `budget/TodayInsights.kt`: `lastDays(today, items, trackingStart, count = 7)` and `tip(snapshot)`; tests in `TodayInsightsTest`. `BudgetEngine` unchanged. The Today query also loads the 6 days before the period start (only for the chart).
- `EntryEditorSheet`: amount `BasicTextField` with rupee prefix transformation, 4-column category grid, date / time assist chips, switch row, collapsible original message, sticky Save bar (`imePadding`), Delete icon in header, light haptic on save, "Always use X for shop?" dialog kept.
- Emojis are no longer shown anywhere (the `Category.emoji` field remains in the enum).

---

# UI part 2 (History, Report, Settings, Onboarding) - version 1.1 (code 2)

- **History**: `HistoryScaffold(vm)` (used by `MainScreen`) owns the app bar: search icon -> text field in the bar (state in `AppViewModel.historySearchOpen/historyQuery`, `closeHistorySearch()`). `HistoryScreen`: PeriodSwitcher, summary line, one scrolling filter row ("Source: All" dropdown chip + category chips with `CategoryIcon`), `stickyHeader` day groups with day totals, `EntryRow`, empty state with "Clear filters".
- **Report**: `ReportState` gained `comparison: Comparison?` and `comparisonPartial`. Pure helpers in `budget/ReportInsights.kt` (`compare`, `sentence`, `spentInFirstDays`, `axisLabel`) with `ReportInsightsTest`. Comparison only when both periods are fully tracked and the earlier one has data; for an unfinished period the same number of days is compared. `MerchantTotal` got an optional `category` (most spent category) for the Top places icons. `ReportScreen` cross-fades content on period change. `Charts.kt`: `DonutChart` (28dp stroke, rounded gaps, animated), `DailyBarChart(period, daily, baseDaily, today, ..)` with y-axis labels, dashed allowance line, today outline, tap-for-tooltip, same y-axis cap as before.
- **Settings**: grouped `SectionCard`s of `SettingRow`s. Full-screen pages (Dialog) for Apps to watch (search, icons, checkboxes) and Learned categories; `ImportSheet` (ModalBottomSheet) uses `ImportPanel` from `Pickers.kt` (range cards, progress, result). New shared dialogs in `Pickers.kt`: `AppDialog`, `AmountField`, `MoneyDialog`, `PayDayDialog`, `PayMonthCard`, `PayDayPicker(columns)`. Theme uses `vm.setThemeMode`.
- **Onboarding**: 7 segmented progress bar + back arrow, sticky bottom button (+ Skip on Permissions / Import), content max 480dp. `PermissionCard` replaces `PermRow` in `Permissions.kt`. Behaviour unchanged (settings saved at Savings step, reminder time at Permissions step, trackingStart + lastSmsScanTime on finish).
- Part-1 fixes done here: hero ring shows remaining share (amber < 20% left, red when over), tip wording "under budget so far", Last-7 chart today colour / label placement / untracked days, `Category.ENTERTAINMENT.displayName` = "Movies & fun", Undo snackbar stays 8 s.

---

# Payment app catalog, smart mode, cross-app dedupe

- **`parse/PaymentApps.kt`**: `PaymentApp(pkg, name, kind: AppKind, defaultCategory?, isMerchantSide, merchantName)`; `AppKind {UPI, WALLET, BANK, CARD, SHOPPING, FOOD, GROCERY, TRAVEL, ENTERTAINMENT, BILLS, GATEWAY}` (each has a `label` for Settings); `PaymentApps.ALL`, `byPackage(pkg)`. `DEFAULT_ALLOWED_PACKAGES` = every catalog package. Shopping / food / grocery / travel / entertainment / bills apps are themselves the merchant (`merchantName` is used, e.g. "Swiggy", "Airtel"); UPI / wallet / bank / gateway apps keep the parsed payee. `defaultCategory` travels in `ParsedTxn.categoryHint` and `Repository.addCaptured` uses it only when the categorizer returns OTHER. Gateway apps (`isMerchantSide`) show money RECEIVED: "paid by X" is turned into a CREDIT.
- **Catalog migration**: `App.onCreate` merges catalog apps that were not in the old 24-app default list into an existing install's watch list once (`Prefs.catalogMigrated`); apps the user had unticked stay unticked.
- **Smart mode** (`Prefs.smartDetect`, default ON; `parse/SmartDetect.kt`, `NotificationParser.parseSmart`): `Capture.notification` first checks the watch list. A package in the watch list uses the normal parser. A catalog package that is not ticked is ignored. Any other package is accepted only if smart mode is on, it is not own app / SMS app / `SmartDetect.BLOCKED_PACKAGES` (WhatsApp, Telegram, Instagram, Facebook, Snapchat, X, LinkedIn, Gmail, Outlook, Truecaller, Discord, Messages, system packages) / an app with `ApplicationInfo.CATEGORY_SOCIAL` (checked in Capture), and the text has a rupee amount (₹/Rs/INR) AND a completed-payment phrase (paid, debited, payment ... successful, sent ₹, money sent, spent, purchase of, order placed ... paid, autopay/auto-debit ... successful) AND `ParserCore` accepts it (no promo / future / failed / request / OTP) AND the result is a DEBIT. Third-person text ("Ramesh paid ₹500", "paid you", owes / split / settle) is rejected. The entry keeps `sender` = package; `EntryRow` shows "App alert · <app label>" (`ui/AppLabels`: catalog name, else the installed app label).
- **TestReceiver TEST_NOTIF** uses the same `Capture.notification` path, so any package works when smart mode is on (e.g. `--es pkg com.example.shop --es text 'You paid Rs 250 to Cafe One'`).
- **Parser additions** (`ParserCore`, `MerchantCleaner`): gateway noise is stripped from payee names and VPAs for SMS and notifications ("RAZORPAY*SWIGGY" -> Swiggy, "swiggy.rzp@icici" -> Swiggy, "BHARATPE*RAMESH TEA STALL" -> Ramesh Tea Stall; Paytm QR ids and gateway+number VPAs give no merchant; a bare gateway name such as "Razorpay Software" is kept but ranked as a gateway name). New FUTURE/skip rules: "request of", "Pay ₹X ...", "Add ₹X ...", "to complete your order", "refund ... initiated/in progress", "flat ₹ off"; notification-only: "pending/processing", "order ... confirmed" counts as a payment, "₹X added to your wallet" is an excluded CREDIT (cashback/reward wording -> ignored), "charged" is a debit word. STOP words gained successful/successfully/completed/confirmed.
- **Dedupe rules** (`Dedupe.isDuplicate`): MANUAL never merges; both upiRef present -> equal refs; else same type + amount within 10 min when SMS<->NOTIFICATION or NOTIFICATION<->NOTIFICATION (different packages, or the same package's status updates); SMS<->SMS without shared ref stay separate. On merge `MerchantPick.better` keeps the best merchant (real shop/person > VPA/phone number > gateway name > none; ties keep the longer) and the category is upgraded when the merchant improved or the old one was OTHER.
- **Settings > Apps to watch**: top switch "Find payments in all apps (smart)"; section "Payment apps on this phone" (installed catalog apps with icon, kind label, checkbox, default on); collapsed "Other apps". Settings row value: "Smart mode on · N payment apps" (N = installed and ticked catalog apps).
- Tests: `PaymentAppsTest` (94: catalog, notification phrasings, must-be-null cases, gateway cleanup, smart rule, dedupe, merchant pick) + 2 new `DedupeTest` cases.

## Cloud: sign-in + sync (1.2; package `cloud/`)
Server contract (tables `profiles`, `transactions`, `category_rules`, RPCs `server_time`, `delete_my_data`) and setup: `supabase/` and `docs/SUPABASE_GOOGLE_SETUP.md`.
Room stays the source of truth; the cloud is a background copy. `rawText` is never uploaded (no field for it in `Dtos.kt`); `bodyHash` is.

- `BuildConfig.SUPABASE_URL / SUPABASE_ANON_KEY / GOOGLE_WEB_CLIENT_ID` come from the gitignored root `supabase.properties` (template: `supabase.properties.example`); blank -> `CloudConfig.isConfigured = false`.
- `Supabase.kt`: `Cloud.client` lazy singleton (Auth with default session storage, Postgrest, OkHttp engine).
- `GoogleSignIn.kt`: Credential Manager `GetGoogleIdOption` (nonce = SHA-256 hex of a random raw nonce) -> `auth.signInWith(IDToken)`; errors mapped to short messages (`SignInException`).
- `AccountController.kt` (owned by `AppViewModel.account`): stages `Checking / ConfirmWipe / LoadError / Ready` after the session becomes active.
  `Prefs.ownerUserId` = account whose data is on the phone. Same owner -> Ready. No owner + local data (existing install) -> **adopt**: mark every row dirty + profile dirty, upload on first sync, one-time note. No owner + empty DB -> first full sync (profile decides whether onboarding is skipped). Different owner -> ask, then wipe DB + per-account prefs and download.
  Sign out: final push, warns if rows are still dirty, wipes DB (`clearAllTables`) + prefs. Delete cloud data: RPC `delete_my_data`, then wipe + sign out.
- `AppRoot` (MainScreen.kt): not configured / not signed in -> `LoginScreen`; signed in -> account stage -> onboarding or main.
- Local rows (`TxnEntity`, `CategoryRuleEntity`) carry `updatedAt`, `deleted` (soft delete, hidden by every DAO query, purged after upload) and `dirty`. `TxnEntity.remoteId` = client UUID (unique). Profile = `SettingsEntity` + Prefs (reminder on/off, smart mode, theme, tracking start); changes set `Prefs.profileDirty`. `Repository` marks rows dirty and calls `SyncScheduler.requestSoon`.
- `SyncEngine.sync()`: one run at a time (mutex), min 15 s between runs, after HTTP 429 blocked 60 s. Order: `server_time` (clock skew) -> pull profile/transactions/rules (`updated_at >= cursor`, ordered, pages of 500, cursor per account in Prefs) -> push profile, dirty txns (batches of 100, upsert on `id`), rules (upsert on `user_id,keyword`) -> purge synced soft deletes. A row the server rejects is skipped (one-by-one retry); a `body_hash` unique clash triggers a full re-pull, which keeps the cloud copy.
- `Merge.kt`: pure last-write-wins (`decide`, `decideTxn`), remote wins ties, phone times shifted by the clock skew. Tested in `MergeTest`.
- `SyncWorker.kt` / `SyncScheduler`: WorkManager, `NetworkType.CONNECTED`, exponential back-off from 30 s. Debounced one-time sync 20 s after the last local change (unique `sync_debounced`: REPLACE, or APPEND_OR_REPLACE while a sync runs), periodic every 6 h (`sync_periodic`), on app open if the last sync is older than 5 min, "Sync now" in Settings. Status for the UI: `SyncStatus.ui` / `lastSynced`.

### Room migration v1 -> v2
`data/Migrations.kt` (`MigrationSql.V1_TO_V2`, `MIGRATION_1_2`), no destructive fallback. `txn` gets `remoteId TEXT NOT NULL DEFAULT ''` (backfilled with `lower(hex(randomblob))` v4 UUIDs, then a unique index), `updatedAt` (= createdAt), `deleted` 0, `dirty` 1; `category_rule` gets `updatedAt` (= now), `deleted` 0, `dirty` 1. Everything starts dirty so existing data uploads on first sign-in. `MigrationSqlTest` runs the SQL on a real SQLite (sqlite-jdbc) and compares the columns with the exported `schemas/.../2.json`.

### Build variants
Debug: `applicationIdSuffix ".debug"`, app name "Salary Tracker (debug)" (`src/debug/res`), so it installs beside the real app. R8 keep rules for serialization/Ktor/supabase-kt are in `proguard-rules.pro`.
