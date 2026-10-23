# Salary Tracker

A personal Android app that splits your monthly salary into a **daily spending allowance**, tracks your
spending **automatically** from bank SMS and payment-app notifications, and shows a **month-end report**.

**Privacy:** everything stays on your phone. The app has **no internet permission**, so Android itself
stops it from sending any data. Your data is kept in a small database (SQLite) inside the app, and it is
not included in phone backups.

## Install

The ready-made app file is `SalaryTracker.apk` in this folder.

1. Connect the phone with USB (USB debugging on).
2. Run:

   ```bash
   adb install -r E:\TV\salary-tracker\SalaryTracker.apk
   ```

3. Open **Salary Tracker** on the phone.

## First start (setup)

The app asks you, step by step:

1. **Monthly salary** (the money you get each month).
2. **Salary day** (1–30, or "Last day of month"). Your "month" runs from that day to the day before the
   next salary day. Example: salary on the 7th → your month is 7 Oct – 6 Nov.
3. **Savings goal** (optional). This money is kept aside and is not part of your daily allowance.
4. **Permissions:**
   - **SMS**: reads bank and UPI SMS like "debited by 250.00 … trf to SWIGGY".
   - **Notification access**: reads payment alerts from apps like Swiggy, Amazon, Samsung Wallet,
     GPay or PhonePe. If Android says it is *restricted*, open **Settings → Apps → Salary Tracker →
     ⋮ (top right) → Allow restricted settings**, then try again.
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
- **Report**: salary, spent, saved, a category chart, a daily chart (bars = spending, line = allowance),
  days under/over budget, top places and biggest expenses. Use ◀ ▶ to see older months.
- **Settings**: salary, salary day, savings goal, reminder time, which apps to watch, import SMS,
  learned categories, delete all data.

Every evening (default 9:30 PM) the app reminds you: *"Did you spend any cash today?"*.
Tap it to add cash spending.

## Build from source (PC)

Needs JDK 17 and the Android SDK (already on this PC).

```bash
cd E:\TV\salary-tracker
gradlew.bat testDebugUnitTest assembleRelease
```

The signed app is created at `app\build\outputs\apk\release\app-release.apk`. Copy it to
`SalaryTracker.apk`.

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
