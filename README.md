# NafahatKey / كيبورد نفحات — Android v0.1

> **Native Android prototype source**. This is a real `InputMethodService` keyboard project, **not an APK yet**. Android Studio + Android SDK are needed to build it.

## What is implemented

- Real Android custom input method (IME), with activation and selection screens.
- Arabic and English layouts, shift, backspace with hold-repeat, enter, punctuation, space-swipe cursor motion.
- Dedicated `123` tab with the exact **Western digits** `0123456789` in a numerical pad on the **RIGHT**, and symbols on the left. Digit row is not permanently present over letters.
- Keyboard toolbar with language switch, `123`, and public/personal snippets.
- Tapping a snippet inserts it in the active input field. Long press copies it explicitly to clipboard.
- Local native app screen with test typing area, confirmed *public* Nafahat information and reusable replies.
- Add and delete personal snippets; their custom category names become scrollable tabs inside the keyboard. Stored only on the device using SharedPreferences.
- Password field protection: hide snippets toolbar for password-like input fields.
- No INTERNET permission. No automatic clipboard collection, keylogging, cloud sync, WhatsApp API, AI, media library or team management in this version.

## Official identity

The included logo is rasterized **directly from the user-provided original PDF**, preserving its shape and colors (no redraw, no generated alternative). The original PDF is included in `/assets/` for traceability. The app name is **NafahatKey** / **كيبورد نفحات**.

## Building with Android Studio (Windows)

1. Install Android Studio (JDK 17 and Android SDK Platform **35**).
2. **Open** the `NafahatKey_Android_v0.1` project folder (the one containing `settings.gradle`).
3. Let Android Studio sync the Gradle project and download Android Gradle Plugin 8.7.3 if needed.
4. From the Build menu use **Build APK(s)**. The APK should be at `app/build/outputs/apk/debug/app-debug.apk`.
5. Transfer the APK to the Android device, install, open **كيبورد نفحات**.
6. Tap **تفعيل** and enable NafahatKey, then tap **اختيار** and select it.
7. Open a text field, press `123` to test the digits, and use **الحافظة** for saved texts.

**Security note:** Android's warning that a third-party keyboard may observe what you type is standard for all custom keyboards. This specific source does not log keystrokes, request internet access or transmit content. Never install a modified APK from an untrusted party.

## Explicit limitations / unverified

- An APK was **not produced in this environment**: Android SDK, Gradle and internet dependency downloads are unavailable here.
- Java source and XML underwent static checks, but **compilation and device tests are still pending**.
- Arabic key arrangement is inspired by the SwiftKey video, not guaranteed pixel-for-pixel identical.
- `InputConnection` behavior varies by target application; test on real Android versions and WhatsApp.
- Keyboard in sensitive password fields shows typing keys but not the clipboard toolbar.
- No team sync and no authoritative proof of WhatsApp sending are implemented; those are separate products/phases.

## Architecture / next steps

Keep the local IME independently functional. Introduce optional authenticated team library via API and a separate companion client (Android/Windows, iOS later) without granting the IME blanket network or customer-message access. Preserve personal snippets isolation, and add exports/backup and permission tests before production rollout.
