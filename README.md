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
- **Ask**: a money helper you can chat with (see below).
- **Settings**: your account (Sync now, Sign out, Delete my cloud data), salary, salary day, savings goal,
  reminder time, which apps to watch, import SMS, learned categories, delete all data.

## Daily summary

Every evening (default 9:30 PM) the app sends a **Daily summary** notification. Open it to see:
- what you spent today and the top 3 categories,
- what is left from today's budget and from this month, and how many days to go,
- whether you are on track to save your goal (or how much you will save at this pace),
- money friends owe you or you owe (if you use Lending),
- one simple tip to save money.

Tap it to open the Report. The **Add cash** button opens the add-cash screen, **See report** opens the Report.
Turn it off or change the time in Settings → Reminders → **Daily summary**.

When you give notification access, the app also reads payment notifications that are already in your
notification bar. Nothing is saved twice.

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

## Ask: your money helper (version 1.4)

The **Ask** tab (next to Settings) answers questions about your money. Tap a question or type your own:

- "Can I spend today?", "Where did my money go last month?", "How much can I spend on food this week?"
- **Saving tips** made from your own spending.
- **Anything unusual?** A category, a day or a payment that is much higher than normal for you.
- **Plan a purchase**: "Can I buy a phone for ₹15,000?" shows how your daily budget changes, how many days you need
  to save for it, or what it does to next month.
- **Who owes me?** and "Does Ramesh owe me?" (from the Lending tab).
- **Bills & subscriptions**: payments that repeat every month or week, with the next expected date.
- **Fix my entries**: entries in "Other" with a suggested category. One tap fixes it and the app remembers the shop.
- **Limits**: "Keep food under ₹6,000". Ask warns you at 80% and when you go over (also as a notification right after
  a new expense). Change limits with the ⚙ button in Ask.
- **Check-ins**: every Sunday at 7 PM "Your week", and at 7 PM on the last day of your month "Your month".

**Private by default.** All these answers are made on your phone, work offline and cost nothing. The chat and your
limits are kept only on this phone (not in the cloud). "Clear chat" is in the ⋮ menu.

**AI answers (optional, off by default).** In Ask → ⚙ you can turn on AI answers. Then a question the app can't answer
goes to Claude AI (by Anthropic) through your own Supabase account. Before it turns on, the app shows exactly what is sent
(tap "See what is sent"): your question and a summary of numbers like salary, budget, totals per category, limits and
lending totals. **Never sent:** SMS or notification text, names of people, phone numbers, UPI ids, account numbers,
notes, or shop names other than big brands (like Swiggy or Amazon). Names of people you saved in Lending are replaced
with "a friend" even if you type them. Up to 30 AI questions a day. Setup (once): see step I in
[`docs/SUPABASE_GOOGLE_SETUP.md`](docs/SUPABASE_GOOGLE_SETUP.md). The API key stays in Supabase; it is never in the app.

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

Ask check-in now (also saved in the chat): `adb shell "am broadcast -n com.local.salarytracker/.capture.TestReceiver -a com.local.salarytracker.TEST_CHECKIN --es kind weekly"` (or `monthly`).
