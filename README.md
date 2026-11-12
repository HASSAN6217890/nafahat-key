# NafahatKey / كيبورد نفحات — Android v0.3

Custom Android keyboard (InputMethodService) for Nafahat's customer-service staff.
Plain Java, no third-party libraries, minSdk 26, targetSdk 34. Approved spec: `docs/SPEC_v0.3.md`.

## What is in v0.3

- **One Canvas-drawn keyboard view** (`KeyboardView`): layouts are data (`Layouts`), nearest-key hit
  testing, per-finger tracking with rollover, highlight + haptic on touch-down, accelerating delete
  (then word-by-word), space swipe = switch language, space long-press + drag = move cursor.
- **Preview and long-press bubbles** drawn by `PreviewOverlay` in extra window space above the keys
  (`onComputeInsets`), no PopupWindow. No preview in password / sensitive fields.
- **Arabic layout exactly as SwiftKey** with corner symbols (Western digits only), English QWERTY with
  shift / caps lock / auto-capitals, two symbol pages without duplicates, diacritics page, number and
  phone pads, email / URL variants.
- **Suggestion strip** (`CandidateStrip`): ⊞ menu, three suggestions (middle = most likely / the
  correction, in gold), ⌄ for more. Word completion, next-word prediction (bigrams), autocorrect on
  space/punctuation with Arabic normalisation, «↶» undo chip and backspace-to-undo.
- **Dictionary**: `app/src/main/assets/dict/` generated offline by `tools/gen_dictionary.py` from the
  `wordfreq` package (50k Arabic, 30k English, Nafahat and Shami vocabulary, curated phrase bigrams).
- **Personal learning** on the device only (`UserDict`, SQLite), never in sensitive fields;
  «علّم الكيبورد من كلامي» learns from the user's own lines in a WhatsApp chat export or pasted text.
- **⊞ menu**: texts library, theme, live resize, one-handed mode, text editing (arrows, select, copy,
  cut, paste), diacritics, language, undo, settings.
- **Library** (`LibraryDb`, SQLite schema v1): trip collections, types, pinned / recent sections,
  `#shortcut` suggestions, `{الاسم} {التاريخ} {المبلغ}` templates, "save copied text" (reads the
  clipboard only on that tap). v0.2 texts are migrated automatically; a backup copy is made before
  every write and a corrupt/failed read never deletes anything.
- **Settings screen** with a live keyboard preview, JSON export / import (personal dictionary only
  when ticked), and a separate library management screen with drag ordering and archiving.

## Privacy rules (enforced by `tools/verify_keyboard_v03.py` in CI)

No permissions at all (no INTERNET), no logging, `allowBackup=false` plus data-extraction rules that
exclude everything, no prices or financial data in code, Western digits only, formal Arabic UI.

## Builds and signing

Every push to `main` runs `.github/workflows/android-debug.yml` and publishes `NafahatKey.apk` and
`BUILD_INFO.txt` to the `apk-builds` branch.

- If the repository secrets `NAFAHATKEY_KEYSTORE_B64`, `NAFAHATKEY_STORE_PASSWORD`,
  `NAFAHATKEY_KEY_ALIAS` and `NAFAHATKEY_KEY_PASSWORD` all exist, CI builds a **signed release**
  (`versionCode = run_number + 100`, `versionName = 0.3.<run>`); otherwise it builds the debug APK.
  `BUILD_INFO.txt` says which one was built.
- The keystore and passwords live only in GitHub Secrets; never commit them.
- Moving from 0.2 (debug key) to the signed release needs one uninstall; later updates install over it.

To regenerate the dictionary: `pip install wordfreq && python3 tools/gen_dictionary.py`.
