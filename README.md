# Amp for Android Auto (prototype)

A single-Activity WebView wrapper around [ampcode.com](https://ampcode.com) that
shows up as an app on the Android Auto screen. **Use only while parked.**

## Install

1. On your phone, download the APK:
   https://github.com/brianevanmiller/amp-android-auto/releases/latest/download/amp-auto.apk
   and allow your browser to install unknown apps.
2. Open **Amp** on the phone once and sign in. The session (cookies) is shared with the car.
3. Enable Android Auto developer mode: Settings → Connected devices → Connection
   preferences → Android Auto → tap **Version** ~10 times → ⋮ → **Developer settings**
   → turn on **Unknown sources**.
4. Android Auto settings → **Customize launcher** → make sure **Amp** is checked.
5. Connect to the car; tap **Amp** in the launcher.

Requires Android 15+ on the phone for Android Auto to host the Activity.

## How it works

Android Auto (on Android 15+) can host an ordinary Activity on the car display when
the app declares `CAR_LAUNCHER`, `appCategory="game"`, and an `automotive_app_desc`.
This is the same mechanism [AABrowser](https://github.com/kododake/AABrowser) uses.
Play Store–distributed apps can't do this; it relies on Android Auto's
"Unknown sources" developer setting.

## Build

CI (`.github/workflows/build.yml`) builds a release APK on every push to `main` and
publishes it as the latest GitHub Release. It's signed with the CI runner's throwaway
debug key, so installing a newer build may require uninstalling the old one first.
Add a real signing key via repo secrets before relying on in-place updates.
