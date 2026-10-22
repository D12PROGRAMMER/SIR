# SwagKeys

Android input method with a Nothing OS look: dot-matrix labels (Doto typeface), black/white
surfaces, a red accent. Text styles are applied as you type and finished words can be swapped for slang automatically.

Prebuilt APK: [`dist/SwagKeys-1.0.0.apk`](dist/SwagKeys-1.0.0.apk) (Android 8.0 / API 26 or later,
signed with a debug key).

## Install

1. Install the APK (allow installs from unknown sources when prompted).
2. Open **SwagKeys** → **Enable SwagKeys** → turn it on in system settings.
3. **Switch to SwagKeys**, or long-press the space bar in any keyboard to pick it.

## Features

| Feature | Behavior |
|---|---|
| Styles | NORMAL, L33T (a→4 e→3 i→1 o→0 s→5 t→7), mOcKiNg, 𝐁𝐎𝐋𝐃, 𝓢𝓒𝓡𝓘𝓟𝓣, ＷＩＤＥ, ⓑⓤⓑⓑⓛⓔ, ꜱᴍᴀʟʟ ᴄᴀᴘꜱ, s̶t̶r̶i̶k̶e̶. The current word stays editable (composing text) and is restyled from what you typed. Choose from the style chip on the keyboard or in settings. |
| Swag slang | 91 single-word substitutions (hello → wassup, cool → lit 🔥, friend → homie, …), applied when a word is finished. Delete right after a substitution, or tap **UNDO**, to restore the word. |
| Where slang applies | Automatically in chat-style fields (multi-line or auto-correct). In single-line search, name and address fields it is only offered in the strip (tap to apply). Never in passwords, URLs, e-mail, number fields, or in words glued to other text (`#tags`, `@names`, `a.b`, `a_b`). |
| Keyboard | Multi-touch roll-over, one-shot / double-tap / long-press shift, long-press top row for digits, key repeat on delete, symbol and number pads, editor-action Enter key, globe key when the system asks for one, TalkBack support (explore by touch, lift to type). |
| Font | Bundled: Doto (SIL OFL 1.1). Any `.ttf`/`.otf` can be imported in settings. |

Nothing's own NDot typefaces are licensed for Nothing brand use only, so they are not bundled.

## Build

Requires JDK 17+ and an Android SDK with platform 36 (`local.properties`: `sdk.dir=...`).

```
./gradlew testDebugUnitTest lintRelease assembleRelease
```

The release APK is signed with `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`) if present, otherwise with the local debug key. Robolectric tests render the
keyboard to `app/build/screenshots/`.
