# Amp & Puck for Android Auto (prototypes)

Two sideloaded Android Auto apps in one repo:

| App | What it is | Works while driving? |
| --- | --- | --- |
| **Amp** (`app/`) | Full [ampcode.com](https://ampcode.com) in a browser on the car screen | No, Park only |
| **Puck** (`puck-android-auto/`) | A button that calls Puck, Amp's voice agent, via [ampcode.com/car](https://ampcode.com/car) | Yes |

## Installing an APK on your phone

Both apps install the same way. What works:

1. **Copy** the APK link and **paste it into Chrome's address bar**. Don't tap the link in
   another app (GitHub, Claude, email): those open a mini-browser whose download tends to
   hang at the last percent.
2. When the download finishes, open the **Files** app → **Downloads** and tap the `.apk`.
3. If Android blocks it:
   - *"Your phone is not allowed to install unknown apps"*: tap **Settings** in the popup
     and turn on **Allow from this source** (or Settings → Apps → Special app access →
     **Install unknown apps** → Files / Chrome).
   - *Play Protect "Blocked" or "Unsafe app"*: tap **More details** → **Install anyway**.
     The builds are signed with a throwaway key, not a Play Store one.
   - *"App not installed" / "package conflicts"*: an older build is installed. Uninstall it
     first (each build is signed with a different key; see [Build](#build)).

## Amp

A single-Activity WebView wrapper around [ampcode.com](https://ampcode.com) that
shows up as an app on the Android Auto screen. If you find yourself often parked and waiting for more than three minutes at a time,
this app is for you. I use this while parked and waiting for kids to finish school or
wrap up after school events.

### Install Amp

1. Install the APK (see [Installing an APK](#installing-an-apk-on-your-phone)):
   https://github.com/brianevanmiller/amp-android-auto/releases/latest/download/amp-auto.apk
2. Open **Amp** on the phone once and sign in. The session (cookies) is shared with the car.
3. Enable Android Auto developer mode: Settings → Connected devices → Connection
   preferences → Android Auto → tap **Version** ~10 times → ⋮ → **Developer settings**
   → turn on **Unknown sources**.
4. Android Auto settings → **Customize launcher** → make sure **Amp** is checked.
5. Connect to the car; tap **Amp** in the launcher. Amp only works in Park, by design:
   Android Auto enforces this for apps like it, and Amp stops working as soon as the car
   shifts out of Park.

Requires Android 15+ on the phone for Android Auto to host the Activity.

### How Amp works

Android Auto (on Android 15+) can host an ordinary Activity on the car display when
the app declares `CAR_LAUNCHER`, `appCategory="game"`, and an `automotive_app_desc`.
This is the same mechanism [AABrowser](https://github.com/kododake/AABrowser) uses.
Play Store–distributed apps can't do this; it relies on Android Auto's
"Unknown sources" developer setting.

## Puck

Puck is Amp's meta agent: you talk to it and it kicks off and manages your Amp threads.
The Puck app puts a **Call Puck** button on the car screen. Tap it, talk, and pause or
stop when you're done to hang up. Android Auto lists it as a media app, which means it
keeps working when the car is in Drive, unlike Amp.

### Install Puck

1. Install the APK (see [Installing an APK](#installing-an-apk-on-your-phone)):
   https://github.com/brianevanmiller/amp-android-auto/releases/latest/download/puck-auto.apk
   Puck is a separate app from Amp, so you can have both.
2. Open **Puck** on the phone. Allow **Microphone**, **Nearby devices** (for car audio
   over Bluetooth) and **Notifications** when asked.
3. Sign in to Amp on the page that opens. Puck has its own login, separate from the Amp
   app. When you see the ampcode.com/car page you're set.
4. Optional: tap the call button on the phone once to check that Puck answers.
5. Android Auto developer mode and **Unknown sources** must be on (see Amp step 3).
6. Android Auto settings → **Customize launcher** → make sure **Puck** is checked.

### Use Puck

Connect to the car and open **Puck** from the launcher. Tap **Call Puck**, or say
"Hey Google, play Puck". The voice comes through the car speakers and you talk through
the car's microphone. To hang up, tap pause or stop on the car screen, or **Hang up** in
the phone notification. Do the one-time setup above before you drive.

### How Puck works

- **Car screen:** a standard media app (a `MediaBrowserService` plus a `MediaSession`).
  Play starts the call; pause or stop ends it; the status shows where the artist name
  would be. Android Auto draws media screens itself, which is why they stay usable while
  driving.
- **Why media and not a Car App Library template app:** recent Android Auto versions
  hide sideloaded template apps even with Unknown sources on. Media apps are still
  allowed ([details](https://github.com/AndreyPavlenko/Fermata/issues/846)).
- **Phone:** the call runs in an off-screen WebView that loads ampcode.com/car and presses
  its call button. A microphone foreground service keeps the mic open while the phone is
  in your pocket, and sends the call audio to the car's Bluetooth hands-free link.
- **If something's wrong:** the car screen shows the error, for example when you're not
  signed in or the mic isn't allowed.

Puck finds the page's call button by its label ("Call", "Talk", "Start"…). If
ampcode.com/car changes its layout and Puck can't find the button, the car screen will
say so.

## Build

CI (`.github/workflows/build.yml`) builds both release APKs (`amp-auto.apk`, `puck-auto.apk`)
on every push to `main` and publishes them as the latest GitHub Release. It's signed with the CI runner's throwaway
debug key, so installing a newer build may require uninstalling the old one first.
Add a real signing key via repo secrets before relying on in-place updates.
