# Salary Tracker

A personal Android app that splits your monthly salary into a **daily spending allowance**, tracks your
spending **automatically** from bank SMS and payment-app notifications, and shows a **month-end report**.

**Sign-in and cloud sync (version 1.2):** you sign in with Google (required). Your entries, settings and
learned categories are saved on the phone first and then copied to your own private cloud account
([Supabase](https://supabase.com)) in the background whenever there is internet, so you can restore them on a new
phone or after reinstalling. Only you can see your data (row level security). **The text of your SMS and
notifications never leaves the phone.** Automatic tracking works offline exactly as before; it syncs later.
The app data is not included in phone backups.

**Set up the cloud once:** see [`docs/SUPABASE_GOOGLE_SETUP.md`](docs/SUPABASE_GOOGLE_SETUP.md). Then copy
`supabase.properties.example` to `supabase.properties` (gitignored) and fill in your Supabase URL, anon key and
Google web client id. Without it the app builds, but the login screen says that cloud sign-in is not set up.

## Install

The ready-made app file is `SalaryTracker.apk` in this folder.

1. Connect the phone with USB (USB debugging on).
2. Run:

   ```bash
   adb install -r E:\salary-tracker\SalaryTracker.apk
   ```

3. Open **Salary Tracker** on the phone and tap **Continue with Google**. If this phone already had data from
   an older version, it is uploaded to your account the first time you sign in.

A debug build (`SalaryTracker-debug.apk`, app name "Salary Tracker (debug)") installs next to the real app and
keeps its own data.

## First start (setup)

After signing in, the app asks you, step by step (skipped when your account already has a saved setup):

1. **Monthly salary** (the money you get each month).
2. **Salary day** (1–30, or "Last day of month"). Your "month" runs from that day to the day before the
   next salary day. Example: salary on the 7th → your month is 7 Oct – 6 Nov.
3. **Savings goal** (optional). This money is kept aside and is not part of your daily allowance.
4. **Permissions:**
   - **SMS**: reads bank and UPI SMS like "debited by 250.00 … trf to SWIGGY".
   - **Notification access**: reads payment alerts from apps like Swiggy, Amazon, Samsung Wallet,
     GPay or PhonePe. If Android says it is *restricted*, open **Settings → Apps → Salary Tracker →
     ⋮ (top right) → Allow restricted settings**, then try again.
     "Smart mode" (on by default, switch in Settings → Apps to watch) also checks alerts from other apps, but only
     for completed payments; chat and social apps are always skipped, and nothing leaves the phone.
   - **Notifications**: for the evening reminder and the "new expense" alerts.
5. **Import old SMS**: picks up your past bank messages (3, 6 or 12 months, or all).

**Samsung tip:** to keep automatic tracking working, go to **Settings → Apps → Salary Tracker → Battery**
and choose **Unrestricted**.

## How the daily allowance works

- Money to spend = salary − savings goal.
- **Today's allowance** = (money left for the month) ÷ (days left, including today).
- So if you spend less today, the next days get a little more. If you spend more, the next days get
  a little less. The leftover or overspend is carried forward automatically.
- **Bills and Rent/EMI** reduce the month's money, but they don't make that day count as "over budget".
- **Credits** (money received) and **"Not spending"** entries (transfer to yourself, credit card bill,
  investments like Groww or mutual funds) are not counted as spending.

## Screens

- **Today**: "Left today" and "Left this month", today's spending, and the **+ Add cash** button.
- **History**: all entries by day. Tap one to change the amount, category or note, mark it "Not
  spending", or delete it. When you change a category, the app can remember it for that shop.
- **Lending**: people you lend to or borrow from, with balances and return dates.
- **Report**: salary, spent, saved, a category chart, a daily chart (bars = spending, line = allowance),
  days under/over budget, top places and biggest expenses. Use ◀ ▶ to see older months.
- **Settings**: your account (Sync now, Sign out, Delete my cloud data), salary, salary day, savings goal,
  reminder time, which apps to watch, import SMS, learned categories, delete all data.

Every evening (default 9:30 PM) the app reminds you: *"Did you spend any cash today?"*.
Tap it to add cash spending.

## Lending (version 1.3)

The **Lending** tab (in the middle of the bottom bar) is for money you give to people or borrow from them.
It does **not** change your daily budget or your report.

- Tap **Add person**. Type a name, or tap **Pick from contacts** to fill the name and phone number.
  The app does not need permission to read all your contacts; it only gets the one you choose.
- Open a person and tap **I gave**, **I got back**, **I borrowed** or **I paid back**. Choose how it was
  done (Cash, GPay, PhonePe, Paytm, Navi, Bank transfer, Other), the date, and a note.
- For money you gave or borrowed you can set a **Return by** date. At 10 AM on that day (and every day after,
  until it is paid) the app reminds you. You can turn this off in Settings → Reminders → **Money return reminders**.
- The top card shows **You will get** and **You need to pay**. Each person shows who owes whom, and an
  **Overdue** or **Due today** mark when a return date has come.
- **Call** and **Message** open your phone or messages app; you press send yourself.
- Lending is saved on the phone and in your private cloud account like everything else. Run
  `supabase/migrations/0003_lending.sql` in the Supabase SQL Editor once so it can sync (see
  `docs/SUPABASE_GOOGLE_SETUP.md`).

**Sync happens by itself when internet comes back.** If you were offline, Settings shows "N changes waiting for
internet", and they are uploaded as soon as the phone is online again.

**Muted notifications:** bank SMS are read even when notifications for your Messages app are muted or off.
Payment apps (GPay, PhonePe...) are different: if you *mute* their alerts (silent) they can still be read, but if you
turn their notifications *completely off*, no app can read them. Your bank SMS still covers those payments.

## Build from source (PC)

Needs JDK 17 and the Android SDK (already on this PC).

```bash
cd E:\salary-tracker
gradlew.bat testDebugUnitTest assembleRelease assembleDebug
```

The signed app is created at `app\build\outputs\apk\release\app-release.apk`. Copy it to
`SalaryTracker.apk`. The cloud keys are read from `supabase.properties` at build time (never commit that file).

**Signing key:** the key is not in this repository. To sign a release build, make your own key, then
copy `keystore.properties.example` to `keystore.properties` and fill it in:

```bash
keytool -genkeypair -v -keystore salarytracker.keystore -alias salarytracker -keyalg RSA -keysize 2048 -validity 10000
```

Keep the key safe: an installed app can only be updated by an APK signed with the same key. Without
`keystore.properties`, the release build is left unsigned.

Technical details (code layout, test commands) are in `docs/ARCHITECTURE.md`.

## Testing with fake messages (adb)

```bash
adb shell "am broadcast -n com.local.salarytracker/.capture.TestReceiver -a com.local.salarytracker.TEST_SMS --es sender VM-SBIUPI-S --es body 'Dear UPI user A/C X1234 debited by 120.00 on date 23Oct26 trf to TEA STALL Refno 999900001111 -SBI'"
```

Only adb can send these test messages. Other apps can't.
