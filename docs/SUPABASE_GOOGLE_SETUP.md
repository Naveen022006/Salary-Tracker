# Supabase + Google Sign-In Setup

Follow the steps in order. Do not skip steps. Takes about 30 minutes.

> WARNING: NEVER put the `service_role` / `secret` key in the app or on GitHub.
> The app only needs the **anon / publishable** key. This GitHub repository is public.

## A. Create the Supabase project

1. Go to https://supabase.com and sign in. Click **New project**.
2. Choose a name and a strong database password. Save the password in your password manager.
3. Region: **Mumbai (ap-south-1)**.
4. Wait until the project is ready.
5. Open **Settings -> API**. Copy two values:
   - **Project URL** (looks like `https://xxxx.supabase.co`)
   - **anon / publishable key**
6. Do NOT copy the `service_role` / `secret` key anywhere.

## B. Create the database tables

1. Open **SQL Editor -> New query**.
2. Paste the full content of `supabase/migrations/0001_init.sql` and click **Run**. You should see "Success".
   If you ran `0001_init.sql` before 24 Oct 2026, also run `supabase/migrations/0002_fix_rate_limit_bucket.sql` once
   (it fixes the error "column reference "bucket" is ambiguous" that stops all uploads).
3. Run `supabase/migrations/0003_lending.sql` once (for the Lending feature: tables `people` and `loan_entries`, with the same
   protection as the other tables). Without it the app still syncs everything else and Settings shows
   "Lending sync needs the 0003 database update". Run it before or after you start using Lending; nothing is lost either way.
4. Open a new query, paste `supabase/tests/rls_check.sql`, run it block by block.
   - Check 1: all tables show `true` and `true`.
   - Check 2: each table (5 after 0003) has 4 policies.
   - Check 3 and 4: must return no rows.
   - Check 6: the first three columns must be `false`, the last one `true`.
   - Checks 7 to 10 (after 0003): lending triggers, counters, owner check and anonymous access.

## C. Google Cloud Console

1. Go to https://console.cloud.google.com and create a **new project** (for example "Salary Tracker").
2. **OAuth consent screen** (Google Auth platform):
   - User type: **External**.
   - App name, support email, developer email.
   - Scopes: `openid`, `email`, `profile`.
   - While the app is in **Testing** mode, add your Gmail address (and family) under **Test users**. Or click **Publish app** to allow everyone.
3. **Credentials -> Create credentials -> OAuth client ID**. Create three clients:
   1. **Web application** (name: "Salary Tracker Web").
      - Copy the **Client ID** and **Client secret**.
      - The Client ID goes to Supabase (step D) AND into the app as `GOOGLE_WEB_CLIENT_ID`.
      - The secret goes ONLY to Supabase. Never in the app, never in GitHub.
      - You can leave "Authorized redirect URIs" empty for ID-token sign-in. If Google asks for one, use the callback URL shown in Supabase (Authentication -> Providers -> Google).
   2. **Android** (release app):
      - Package name: `com.local.salarytracker`
      - SHA-1: `97:92:EF:82:41:74:12:C0:97:8A:DC:D1:8F:B5:9E:45:80:4D:4D:42`
   3. **Android** (debug app):
      - Package name: `com.local.salarytracker.debug`
      - SHA-1: `91:9A:B6:47:8F:C5:DA:02:3B:16:63:33:4F:6B:9E:D7:76:E2:26:6E`
4. To read the SHA-1 again, open a terminal in the project folder and run:

   ```
   keytool -list -v -keystore salarytracker.keystore -alias salarytracker
   ```

   Enter the keystore password and copy the line `SHA1:`. For the debug key, use the file `%USERPROFILE%\.android\debug.keystore`, alias `androiddebugkey`, password `android`.
   If you change the keystore, you must update the SHA-1 in Google Cloud.

## D. Turn on Google in Supabase

1. Supabase -> **Authentication -> Sign In / Providers -> Google**.
2. Enable it.
3. Paste the **Web** Client ID and Client secret from step C.
4. In **Authorized Client IDs** add the same **Web** Client ID. (Required for ID-token sign-in from the app.)
5. Keep **Skip nonce check** turned **OFF**.
6. Save.

## E. Rate limits (protect your free quota)

In Supabase -> **Authentication -> Rate Limits**, suggested values:

| Setting | Suggested | What it protects |
|---|---|---|
| Sign-ups and sign-ins (per IP per 5 min) | 30 | Stops password/token guessing and bots |
| Token refreshes (per IP per 5 min) | 150 | Stops refresh loops from burning quota |
| Token verifications (per IP per 5 min) | 30 | Stops brute force on tokens |
| Anonymous sign-ins | keep OFF | The app does not use them |
| Email sending | not used | The app uses Google only |

Names can differ a little in the dashboard; pick the closest ones.

Database limits (from `0001_init.sql`, per user). When exceeded, the API answers HTTP **429** (or 413 for the row cap):

| Limit | Value |
|---|---|
| Transaction writes per minute | 300 |
| Transaction writes per day | 5,000 |
| Profile writes per minute | 30 |
| Category rule writes per minute | 120 |
| Delete-all-data calls per hour | 3 |
| Max transaction rows per user | 200,000 (HTTP 413) |
| Max rows per sync batch (app side) | 100 |
| Max `allowed_packages` | 300 |
| People writes per minute (0003) | 60 |
| Lending entry writes per minute (0003) | 300 |
| Lending entry writes per day (0003) | 5,000 |
| Max people per user (0003) | 2,000 (HTTP 413) |
| Max lending entries per user (0003) | 50,000 (HTTP 413) |

Note: an upsert of a row that already exists counts as 2 writes (one insert attempt, one update). Keep this in mind for the per-minute limit.

## F. Put the keys in the app

1. In `E:\salary-tracker`, copy `supabase.properties.example` to `supabase.properties`.
2. Fill in these keys:
   - `SUPABASE_URL` = Project URL from step A
   - `SUPABASE_ANON_KEY` = anon / publishable key from step A
   - `GOOGLE_WEB_CLIENT_ID` = Web Client ID from step C
3. `supabase.properties` is ignored by git. Never commit it.
4. Build: `gradlew.bat assembleRelease`
5. Install the new APK on your phone.

## G. Troubleshooting

- **"No credentials available"**: no Google account on the phone, or the SHA-1 or package name in the Android client is wrong. Add a Google account in phone Settings. Check that release uses `com.local.salarytracker` with the release SHA-1, and debug uses `com.local.salarytracker.debug` with the debug SHA-1.
- **"Developer console is not set up correctly"**: the Android OAuth client is missing or wrong. Create it (step C) and wait 5-10 minutes.
- **"invalid nonce"**: Supabase Google provider has "Skip nonce check" OFF, so the app must send the nonce. Make sure the app and the Web Client ID are the latest build and the Web Client ID is in "Authorized Client IDs".
- **HTTP 429 "Too many requests"**: you hit a rate limit (step E). Wait a few minutes and try again.
- **HTTP 413**: you reached a row limit (200,000 transactions, 2,000 people or 50,000 lending entries). Delete old data.
- **"Lending sync needs the 0003 database update"**: run `supabase/migrations/0003_lending.sql` once in the SQL Editor (step B3).
- **Google says "access blocked" / app not verified**: the consent screen is in Testing mode. Add your Gmail under **Test users**, or publish the app.
- **Sign-in works but no data syncs**: run `supabase/tests/rls_check.sql` and check the tables exist.

## I. AI answers in the Ask tab (optional)

The Ask tab works without this. Do it only if you want AI answers for questions the app can't answer itself.
The Anthropic API key goes ONLY into Supabase secrets. Never put it in the app, in `supabase.properties` or on GitHub.

1. **Anthropic console** (https://console.anthropic.com): sign in, add a little credit, and under **Limits** set a
   **monthly spend limit** (for example $5). Create an API key (Settings -> API keys). Copy it once; keep it secret.
2. **Database**: Supabase -> SQL Editor -> paste `supabase/migrations/0004_assistant.sql` -> **Run**. It adds the
   limits: 5 AI questions per minute and 30 per day for each user, 500 per day for all users together.
3. **Secret**: Supabase -> **Edge Functions -> Secrets** -> **Add new secret**: name `ANTHROPIC_API_KEY`, value = your key.
   Type or paste it yourself here, nowhere else.
4. **Function**: Supabase -> **Edge Functions -> Deploy a new function -> Via editor**. Name it exactly `assistant`.
   Replace the example code with the full content of `supabase/functions/assistant/index.ts` and click **Deploy**.
   Keep **Verify JWT** turned ON (only signed-in users can call it).
   (Or with the Supabase CLI: `npx supabase functions deploy assistant`.)
5. **Check**: run checks 11 and 12 of `supabase/tests/rls_check.sql`. Then in the app: Ask -> ⚙ -> turn on
   **AI answers**, read the note, tap **Turn on**, and ask something like "Is my spending OK this month?".

Model: Claude Haiku 5.5 (`claude-haiku-5-5`, $0.10 per million input tokens, $0.50 per million output tokens).
One question costs about ₹0.05, so 30 questions a day is at most about ₹45 a month. See the real use with
`select * from private.ai_usage order by day desc;` in the SQL Editor.

If something is wrong, the app says so in the chat:
- "AI answers are not set up yet (the "assistant" function is missing in Supabase)": step 4 not done, or the name is wrong.
- "... (the API key is missing in Supabase)": step 3 not done, or the key is wrong / out of credit.
- "You have used today's 30 AI questions": the daily limit; it resets at midnight (India time).
- Any other error: Supabase -> Edge Functions -> assistant -> **Logs**.

## H. Your data

Stored in the cloud (your own Supabase project, protected by row level security so each user sees only their own rows):

- Profile: salary, pay day, savings goal, reminder settings, tracking start date, SMS/notification/smart-detect/alert switches, allowed app packages, theme, onboarding flag.
- Transactions: amount (paise), debit/credit, date and time, merchant, category, source (SMS/NOTIFICATION/MANUAL), sender, last 4 digits of account, UPI reference, your note, excluded flag, deleted flag, and a SHA-256 hash of the message (used only to avoid duplicates).
- Category rules: keyword and category.
- Lending (needs 0003): people (name, optional phone, note) and lending entries (who, gave / got back / borrowed / paid back, amount, cash / UPI / bank / card / other, payment app name such as GPay, date, optional return-by date, note).
- Your Google account id and email (kept by Supabase Auth).

**Ask tab:** the chat and the category limits stay on the phone. With AI answers on, each AI question sends your
question and a summary of numbers to the `assistant` function, which passes them to Anthropic (not stored in your
database). Stored in the database only: a count of AI questions and tokens per day (`private.ai_usage`).

**Never uploaded:** the original SMS text and notification text. Only the hash, which cannot be turned back into text.

To delete your cloud data:

1. In the app, use the "Delete cloud data" option (it calls the `delete_my_data` function), or
2. In the SQL Editor run `select public.delete_my_data();` while signed in as the user, or
3. To also remove the login: Supabase -> **Authentication -> Users** -> delete the user. All their rows are removed automatically (cascade).
