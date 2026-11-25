# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack
Existing: Kotlin, Jetpack Compose (Material 3), supabase-kt; built with command-line Gradle, no Android Studio. Distributed as an APK; open source and forkable.

## Users
Friends, flatmates and travel groups in India who share costs and settle up: a dinner out, a trip, a shared flat. Users open the app in two moments of equal weight: right after paying, to log a bill in seconds, and later, to see who owes whom and settle. The owner hosts the backend on a free Supabase project for a small circle of people they know.

## Product Purpose
Hissa is a free, open-source Splitwise alternative with the paid features included: unlimited expenses, equal / exact / percent / share-based splits, simplified debts, one-tap settle-up and live updates, on a backend the owner controls. Success: a group stops arguing about money, and nobody pays for a subscription.

## Positioning
Self-hosted and free with no paywall, easy for a non-technical person to deploy (one SQL paste, a fork, an APK), and made for how Indian groups actually split bills (INR default, light Hinglish voice).

## Operating Context
Used one-handed on a phone, often at a table or on the move, in any light. Data lives in the owner's Supabase project; sign-in is email + password with an emailed confirmation code and emailed reset code. Money is stored as integer paise.

## Capabilities and Constraints
- Groups joined by an 8-character invite code; every member sees the same expenses live.
- Add expense: description, amount, payer, split Equally / Exact / Percent / Shares among chosen members.
- Balances simplified to the fewest payments; Settle records a payment.
- Delete expense. Editing expenses, offline mode, receipt photos and push notifications are not built yet.
- Android only; Material 3 is the rulebook. No iOS.
- Open undecided: group currency choice in the UI (default INR), app icon and store listing.

## Brand Commitments
Name: Hissa (Hindi for "share, portion"). Voice: English with light Hinglish (for example "Hisaab barabar!" when a group is settled). Independent project, not affiliated with Splitwise; should not resemble it.

## Evidence on Hand
No real users, testimonials, customer counts or benchmarks exist; none may be invented. Real material: the working app, the owner's own Supabase project, the README.

## Product Principles
1. Logging a bill takes seconds; the amount and the payer come first.
2. A balance is always one glance: who owes whom, how much, in rupees, no arithmetic for the user.
3. Money screens are exact and calm: tabular figures, no ambiguity about direction (owe vs owed).
4. Settled is a moment worth a little warmth, not a celebration.
5. Self-hosting must stay simple: the app asks for nothing a layperson cannot find.

## Accessibility & Inclusion
Readable in bright sunlight and at larger system font sizes; 48 dp touch targets; direction of money never conveyed by colour alone; Indian digit grouping where it applies.
