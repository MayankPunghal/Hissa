# Contributing to Hissa

Thanks for helping. Hissa is a free, open-source Splitwise alternative, and every fix, translation and idea makes it better for everyone.

## Quick ways to help

- **Star the repo** so others find it.
- **Report a bug** or **request a feature** with the [issue templates](https://github.com/MayankPunghal/Hissa/issues/new/choose).
- **Pick something from the [Roadmap](README.md#roadmap)**: translations, other currencies, import from Splitwise, offline writes, push notifications.

## Run it locally

You need JDK 17 and the Android command-line tools (Android Studio is optional).

```bash
git clone https://github.com/MayankPunghal/Hissa.git
cd Hissa
cp local.properties.example local.properties   # set sdk.dir, optionally SUPABASE_URL and SUPABASE_ANON_KEY
./gradlew testDebugUnitTest                    # Windows: gradlew.bat
./gradlew assembleDebug
```

Try the UI with no account: on a debug build run `adb shell am start -n app.hissa/.MainActivity --ez demo true`.

## Pull requests

1. Fork, then branch: `git switch -c feature/my-change`.
2. Keep the change small and focused. Money is integer cents, never floats.
3. Run `./gradlew testDebugUnitTest` and make sure it passes.
4. If you change the database, edit `supabase/setup.sql` and keep it safe to re-run.
5. Open a pull request into `main`. Start the title with `feat` for a new feature, or `fix` for a fix. Open an issue first for big changes.

By contributing you agree your work is released under the [MIT License](LICENSE).
