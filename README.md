<div align="center">

# Hissa

**Split bills with friends. Free, open source, and yours to host.**

An Android app for sharing expenses in groups, tracking who owes whom, and settling up. It has no ads, no paywall and no limits on expenses. It runs on your own free [Supabase](https://supabase.com) account, so your data stays with you.

</div>

---

## What you get

- **Groups** for trips, flatmates, dinners or anything else, joined with a tappable invite link or a short code.
- **Split any way.** One person or **several people can pay** (equally, exact amounts, percentages or shares), and the cost can be split **equally**, by **exact amounts**, **percentages**, **shares**, or **item by item** with tax and tip shared in proportion. The odd paise are handled exactly.
- **Scan a receipt.** Take or pick a photo and Hissa reads the total and the items on the phone (no upload).
- **Categories** with icons, guessed from what you type ("Uber to airport" becomes Transport).
- **Repeating expenses** (weekly or monthly), **edit** any expense, **notes** and a **date** picker, and a **default split** you can save per group.
- **Search** expenses and activity.
- **Simplify group debts** (on by default, per group): A owes B and B owes C becomes A pays C.
- **Settle up** in one tap, with an optional **UPI** button (friends add their UPI ID in Account).
- **Insights** per group: category donut, running total, by month, by weekday, paid vs share, biggest expenses, with a date range filter, and **CSV export**.
- **Import** a Splitwise export or any CSV.
- **Friends tab** with what you owe each person across all your groups.
- **Activity feed**, **dark mode**, **offline reading** (the last data is kept on the phone) and **live updates**.
- **Group settings**: name, picture, members, leave (once settled up) or delete.
- **No ads, no limits, your own server.** Everyone in a group sees the same data, and the data lives in your database.

Not built yet: push notifications and translations. Everything is in rupees (INR) for now. See [Roadmap](#roadmap).

---

## How it works

```
 Friend's phone ─┐
                 ├──►  Supabase (your free project)  ──►  Postgres database
 Your phone ─────┘      login + data + security rules
```

There is no server for you to run. The app talks directly to Supabase. A single SQL script, [`supabase/setup.sql`](supabase/setup.sql), creates every table, security rule and function in one go.

Setup takes about 10 minutes and costs nothing:

1. **[Set up the backend](#step-1--set-up-the-backend-5-min)** on Supabase (free).
2. **[Get the app](#step-2--get-the-app)** by downloading it, or building it in the cloud.
3. **[Connect and use it](#step-3--connect-and-use-it)**, then invite friends.

---

## Step 1 · Set up the backend (5 min)

1. Go to [supabase.com](https://supabase.com) and sign up (free). Click **New project**, give it a name and a database password (save it somewhere, you won't need it again), pick the region nearest you (for India, **Mumbai** or **Singapore**) and click **Create**. Wait a minute or two until it finishes.
2. In the left menu open **SQL Editor** → **New query**.
3. Open [`supabase/setup.sql`](supabase/setup.sql) in this repo, click **Raw**, select all and copy it. Paste it into the SQL Editor and click **Run**. You should see "Success". It is safe to run again if you are unsure.
4. Go to **Project Settings → API** (or **Data API**). Keep this page open. You need two values:
   - **Project URL** (looks like `https://abcdxyz.supabase.co`)
   - **anon public** key (a long text starting with `eyJ...` or `sb_publishable_...`)
5. Set up **email sending** (next section, about 10 minutes). Hissa verifies new accounts with an emailed code and uses the same kind of code for password reset. Supabase's built-in email service is only for testing, so you need your own sender.

### Email setup (needed for sign-up and password reset)

Supabase has to send these emails, so give it a real email account to send from. Both options below are free.

**A. Gmail** (simplest, no domain needed)
1. Turn on 2-step verification for your Google account, then create an **App password** at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords).
2. In Supabase open **Project Settings → Authentication → SMTP Settings** (the exact menu may be named *Authentication → Emails → SMTP Settings*), turn on **Enable custom SMTP** and fill in:
   - Sender email: your Gmail address, sender name: `Hissa`
   - Host `smtp.gmail.com`, port `465`
   - Username: your Gmail address, password: the **app password** (not your Gmail password)

**B. Brevo** (300 emails a day free, no Google needed)
1. Create a free account at [brevo.com](https://www.brevo.com).
2. Go to **Settings → Senders, domains, IPs → Senders → Add a sender**. Use an email address **you can receive mail on** (any personal address). Do **not** use an address on a domain you don't own, such as `noreply@somesite.com`, because it can never be verified. Brevo emails you a code. Enter it to verify.
3. Go to **SMTP & API → SMTP** and click **Generate a new SMTP key**. Copy it right away.
4. In Supabase (**Authentication → SMTP Settings**, enable custom SMTP) fill in:
   - Sender email: the address you verified in step 2, sender name: `Hissa`
   - Host `smtp-relay.brevo.com`, port `587`
   - Username: the SMTP login shown on Brevo's SMTP page (it ends in `@smtp-brevo.com`)
   - Password: the **SMTP key** from step 3 (not your Brevo account password)

Emails from a free address like Gmail can land in spam. If that happens, ask friends to check spam once, or use your own domain as the sender.

**Then make both emails show a code.** The app asks for the emailed code instead of a link, which avoids broken redirects on phones. In Supabase open **Authentication → Emails → Templates** and edit two templates. Keep **Confirm email** turned on under **Authentication → Sign In / Providers → Email**.

**Confirm sign up** template body:

```html
<h2>Welcome to Hissa</h2>
<p>Your confirmation code is: <b>{{ .Token }}</b></p>
<p>Enter it in the app to finish creating your account.</p>
```

**Reset Password** template body:

```html
<h2>Reset your Hissa password</h2>
<p>Your code is: <b>{{ .Token }}</b></p>
<p>Enter it in the app. If you didn't ask for this, ignore this email.</p>
```

In the app: after **Sign up** you type the emailed code. For a forgotten password: **Forgot password?** → enter your email → type the code → choose a new password.

> **Don't want to set up email?** Turn **off** "Confirm email" (Authentication → Sign In / Providers → Email). Sign-up then works instantly. Password reset by email won't work, but you can reset a password yourself in **SQL Editor**:
> `update auth.users set encrypted_password = crypt('NEW-PASSWORD', gen_salt('bf')) where email = 'friend@example.com';`
> then tell your friend the new password.

> **Is the anon key safe to share?** Yes. It is designed to be public and ships inside every app. The security rules in `setup.sql` make sure people can only see groups they belong to. **Never** share the `service_role` key or your database password.

---

## Step 2 · Get the app

Pick **one** option.

### Option A · Download a ready-made APK (easiest)

1. Open the **[Releases](../../releases)** page of this repo and download `Hissa.apk` from the latest release.
2. Send it to your phone (USB, Google Drive, WhatsApp, email) and open it.
3. Android will ask you to allow installing from this source. Allow it just for this install.

### Option B · Build it in the cloud (no tools needed)

1. Click **Fork** at the top of this page to copy the repo to your own GitHub.
2. In your fork, open the **Actions** tab and enable workflows if asked.
3. Choose **Build APK** → **Run workflow**.
4. After a few minutes open the finished run and download `Hissa-apk` from **Artifacts**. Unzip it to get `Hissa.apk`, then install it as above.

### Option C · Build it on your computer

You need [JDK 17](https://adoptium.net) and the Android command-line tools. **Android Studio is not required.**

```bash
git clone https://github.com/YOUR-USERNAME/hissa.git
cd hissa
cp local.properties.example local.properties   # fill in sdk.dir (+ optional URL/key)
./gradlew assembleDebug                        # Windows: gradlew.bat assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`. To run the unit tests, use `./gradlew testDebugUnitTest`.

**Optional:** put `SUPABASE_URL` and `SUPABASE_ANON_KEY` in `local.properties` before building, and your server is baked into the APK. Your friends then never have to type it.

---

## Step 3 · Connect and use it

1. Open Hissa. On first launch it asks for the **Project URL** and the **anon public key** from Step 1. Paste them and tap **Connect**. (Skipped if you baked them in.)
2. Tap **New here? Sign up**, create your account, then type the code we email you.
3. Tap **New group**, name it, and open it. You will see an **Invite code**.
4. Send the code to friends. They install the app, connect to the **same** server, sign up, tap **Join with code**, and they're in.
5. Tap **Add expense**: write what it was for, the amount and who paid. Pick **Equally**, **Exact**, **Percent** or **Shares**, tick who is in, and fill in their numbers if needed. The app shows each person's amount and tells you if the numbers don't add up. Balances update straight away.
6. When someone pays back, tap **Settle** next to the debt.

Changes from friends appear on their own. If live updates ever stop (weak network), tap **Refresh**.

---

## Sharing the app (without the Play Store)

You do not need the Play Store. Pick whichever suits your friends:

| Way | How | Notes |
|---|---|---|
| **GitHub Releases** (best) | Push a tag like `v0.3`. The workflow attaches `Hissa.apk`. Share the release link. | One stable link that always has the newest build. Free. |
| **WhatsApp, Telegram, email** | Send `Hissa.apk` as a document. If WhatsApp refuses, rename it to `Hissa.zip` and tell friends to rename it back. | Quick for a small circle. |
| **Google Drive or any cloud link** | Upload the APK and share the link. | Works for anyone. |
| **Obtainium** | Friends add your GitHub repo in the [Obtainium](https://github.com/ImranR98/Obtainium) app. | Gives them automatic update notices, no store needed. |
| **QR code** | Make a QR for the release link and put it in the group chat or on a poster. | Handy in person. |

**Why Android warns:** apps installed from outside the Play Store always show "install unknown apps" and sometimes a Play Protect notice for apps from a developer it doesn't know. This is normal and not a sign of a problem. To keep it as smooth as possible:

1. **Sign every release with the same key** (see below). A consistent signature means updates install over the old version and Play Protect learns the app.
2. Tell friends what to expect: allow the install once, and if Play Protect asks, choose **More details → Install anyway**.
3. Publish from GitHub Releases rather than a random file host, so people can see the source.
4. Only the Play Store removes the warning completely (a one-time $25 developer fee and a review).

### Signing your release APK

```bash
keytool -genkeypair -v -keystore keystore/hissa-release.jks -alias hissa -keyalg RSA -keysize 2048 -validity 36500
```

Add the passwords to `local.properties` (never commit this file):

```
RELEASE_STORE_FILE=keystore/hissa-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=hissa
RELEASE_KEY_PASSWORD=...
```

Then run `./gradlew assembleRelease`. The APK is in `app/build/outputs/apk/release/`. **Back up the `.jks` file and its passwords.** If you lose them you can no longer publish updates that install over the old app.

### Make invite links tappable

Messaging apps do not turn `hissa://` into a link. Hissa shares a normal web link that opens a small page, and the page opens the app. This needs the free GitHub Pages:

1. In your GitHub repo open **Settings → Pages**, set **Source** to **Deploy from a branch**, branch `main`, folder `/docs`, and save.
2. Put this in `local.properties` (replace `YOU` and `REPO`), then rebuild:

```
INVITE_BASE=https://YOU.github.io/REPO/join/?code=
INVITE_HOST=YOU.github.io
```

Shared invites then look like `https://YOU.github.io/REPO/join/?code=abc12345`, followed by the code on its own line. If the person has Hissa, the page opens it with the code filled in. If not, the page offers the APK download. Pasting a whole invite message into **Join with a code** also works: Hissa finds the code in it.

## Good to know

| Topic | Details |
|---|---|
| **Cost** | Free. Supabase's free plan has 500 MB of storage, which is plenty for years of group expenses. |
| **Inactive projects** | Free Supabase projects pause after about a week with no use. Open your Supabase dashboard and click **Restore**. Nothing is lost. |
| **Who can use it** | Android only. Everyone must connect to the same Supabase project. |
| **Updating** | Install a newer APK over the old one. Your data stays in Supabase. |
| **Your data** | Lives in your Supabase project. You can export it from the Table Editor at any time. |
| **Upgrading from an earlier version** | Run the latest `supabase/setup.sql` again (safe to repeat). It adds multiple payers, categories, repeating expenses, group editing, leave and delete. |
| **Group pictures** | Resized to about 256 px and stored in the database itself, so no extra storage setup. |
| **Web invite links** | See [Make invite links tappable](#make-invite-links-tappable). Without them, links use `hissa://join/CODE`, which only works for people who already have the app. |
| **Currency** | Rupees (INR) only for now. |

## Troubleshooting

- **"invalid invite code"**: copy the code again exactly. It is 8 letters and numbers.
- **"Invalid API key" or the app can't connect**: re-check the URL and key in **Server settings** on the sign-in screen. The URL must start with `https://`.
- **The sign-up code never arrives**: check spam, tap **Resend code**, and check that custom SMTP is saved and switched on (Step 1, email setup). Also check that the **Confirm sign up** template contains `{{ .Token }}`.
- **"Forgot password" email never arrives**: check spam, and check that custom SMTP is switched on (Step 1, password reset). The code is valid for about an hour.
- **"permission denied" or "function not found"**: run `supabase/setup.sql` again.
- **Nothing loads after a long break**: your project is probably paused. Restore it in the Supabase dashboard.

---

## For developers

```
app/src/main/java/app/hissa/
  MainActivity.kt   app entry; Auth.kt, Home.kt, GroupScreen.kt are the screens (Jetpack Compose)
  Theme.kt          colours, Manrope type, shapes; Components.kt shared UI pieces
  Data.kt           Supabase client, models, API calls, realtime channel
  Demo.kt           sample data for debug builds (no account needed)
  Debts.kt          split modes + debt-simplification + money helpers (pure Kotlin, unit-tested)
app/src/test/       unit tests
supabase/setup.sql  complete, re-runnable backend (tables, RLS, functions, realtime)
.github/workflows/  builds the APK on GitHub
```

- **Stack:** Kotlin, Jetpack Compose, [supabase-kt](https://github.com/supabase-community/supabase-kt), Gradle.
- **Money** is stored as integer cents, never floating point. Splits must add up exactly to the amount, enforced in the database.
- **Security:** Row Level Security limits every read to groups you belong to. Writes go through validated SQL functions (`create_group`, `join_group`, `add_expense2`, `edit_expense`, ...), not direct inserts.
- **Try the UI without an account:** on a debug build, run `adb shell am start -n app.hissa/.MainActivity --ez demo true` to load sample groups. It works on the Android Emulator too, so no phone is needed.
- **Font:** [Manrope](https://github.com/sharanda/manrope) under the SIL Open Font License (`docs/fonts/Manrope-OFL.txt`).
- **Releases:** push a tag like `v0.2` and the workflow attaches `Hissa.apk` to a GitHub Release.

## Roadmap

- [x] Unequal splits (exact amounts, percentages, shares)
- [x] Live updates
- [x] Edit expenses, receipts, categories, repeating expenses, search, charts, CSV import and export
- [ ] Adding expenses while offline (reading offline works)
- [ ] Push notifications
- [x] Signed release APK
- [ ] Languages other than English, other currencies, Play Store listing

Contributions and issues are welcome. Open an issue first for bigger changes.

## License

[MIT](LICENSE). Use it, change it, share it.

> Hissa is an independent open-source project and is not affiliated with Splitwise.
