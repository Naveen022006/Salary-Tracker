# Salary Tracker - architecture (part 1: core + data)

Package `com.local.salarytracker`. All money = `Long` paise. Pure logic (parse/, budget/, model/) has no Android imports.

## Versions that build
Gradle 9.4.1 (wrapper) / AGP 9.2.1 (built-in Kotlin: do NOT apply `kotlin-android`) / Kotlin 2.2.21 (+ `kotlin-compose` plugin) /
KSP 2.3.12 / Room 2.8.4 / Compose BOM 2026.09.00 (needs **compileSdk 37**; targetSdk 36, minSdk 26) / WorkManager 2.10.5 /
activity-compose 1.11.0 / navigation-compose 2.9.5 / lifecycle 2.9.4 / coroutines 1.10.2 / JUnit 4.13.2. JDK 17.
`local.properties` must use forward slashes for sdk.dir. Commands: `gradlew.bat testDebugUnitTest`, `gradlew.bat assembleDebug`.
Manifest has no INTERNET permission (verified in merged manifest); allowBackup=false + backup rules exclude everything.
WorkManager merges WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE (harmless).

## model/Enums.kt
- `TxnType {DEBIT, CREDIT}`, `TxnSource {SMS, NOTIFICATION, MANUAL}`
- `Category(displayName, emoji, color: Long ARGB, isFixed)`: FOOD GROCERIES TRAVEL BILLS(fixed) RENT_EMI(fixed) SHOPPING HEALTH ENTERTAINMENT EDUCATION PEOPLE CASH TRANSFER OTHER; `Category.fromName(String?)`.

## parse/
- `ParsedTxn(amountPaise, type, merchant?, accountLast4?, upiRef?, excluded, isSalary, timestampHint=null)` (timestampHint is never set yet; use the SMS/notification time).
- `SmsParser.parse(sender, body): ParsedTxn?`, `SmsParser.isBankSender(sender)`. Phone-number / non DLT senders -> null.
- `NotificationParser.parse(pkg, title, text): ParsedTxn?` (SMS-app packages ignored; shopping/food apps force merchant = app name).
- `DEFAULT_ALLOWED_PACKAGES: List<String>`, `SMS_APP_PACKAGES: Set<String>`.
- `Categorizer.categorize(merchant, text, learnedRules: Map<keywordLowercase, Category>): Category`.
- `Dedupe.isDuplicate(TxnKey, TxnKey)`, `Dedupe.WINDOW_MS`, `Dedupe.bodyHash(source, sender, body, timestamp)`; `TxnEntity.toKey()`.
  Rule: MANUAL never merges; both refs present -> equal refs; else only different sources (SMS vs NOTIFICATION), same amount, within 10 min.
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
- Build: `gradlew.bat testDebugUnitTest assembleRelease`; output `app/build/outputs/apk/release/app-release.apk`, copied to `E:\TV\salary-tracker\SalaryTracker.apk`. APK is v2-signed (minSdk 26 is fine with v2).
- Permissions in the APK: READ_SMS, RECEIVE_SMS, POST_NOTIFICATIONS + WorkManager's WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE. **No INTERNET.** Check: `aapt2 dump permissions SalaryTracker.apk`.

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
adb logcat -s SalaryTracker
```
(`--el time` = epoch millis, optional; default now.) In Git Bash prefix with `MSYS_NO_PATHCONV=1`. The notification test only works for packages in the "Apps to watch" list. `TEST_REMINDER` runs the real `ReminderWorker` once and posts the reminder notification. A successful save logs `saved #id DEBIT ₹250 merchant=... category=...`; otherwise the log says why it was ignored.

---

# Part 3: tracking start date, money display, UI fixes

- **trackingStart (LocalDate)** is stored in SharedPreferences `prefs`, key `tracking_start_epoch_day` (`Prefs.trackingStart/setTrackingStart`). **No Room schema change, DB version stays 1, no migration needed** (existing data survives `adb install -r`). Set to today when onboarding finishes; on the first run of this version for an already-onboarded install, `AppViewModel` init / `BudgetService.trackingStart` stores today. Settings > Money > "Tracking since" opens a date picker (future dates are clamped to today). `AppViewModel.trackingStart` is a StateFlow combined into `todayState` and `reportState`.
- `BudgetEngine.compute/report(..., trackingStart: LocalDate? = null)`. Days before trackingStart in the period that contains it are "not tracked": pool = spendable - baseDaily x untrackedDays; entries before it are ignored; `days` only lists tracked days. Snapshot gets `trackingStart` (non-null only when it cuts the period), `trackedDays`, `budgetPaise` (= pool). Example: 80,000 / 31 days / start 23 Oct -> today's allowance Rs 2,580.65, budget Rs 23,226.
- A period that ends before trackingStart gives `PeriodReport.beforeTracking = true`: only Spent, categories, top places, biggest and daily spend bars (no salary / saved / under-over / allowance line). Report UI shows "Before you started the app - only spending found in SMS is shown."
- Start period report: "Budget for 23-31 Oct" row replaces Salary; "Left"/"Left so far" is relative to that budget.
- `Money.formatWhole(paise)` = whole rupees rounded (used for Today, Report totals, capture alert, reminder); `Money.format` keeps paise when non-zero (individual entries). `Money.cleanInput` strips non-digits and leading zeros for all amount fields.
- `DailyBarChart(period, daily, baseDailyPaise, showAllowance)`: y-axis capped at max(2 x baseDaily, 1.1 x highest daily spend); allowance line clipped there with a small triangle marker.
- Entry editor: Save / Delete live in a bottom bar outside the scrolling form. Today progress bar uses `gapSize = 0.dp, drawStopIndicator = {}`.
