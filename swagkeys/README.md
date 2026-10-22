# SwagKeys

Android input method with a Nothing OS look: dot-matrix labels (Doto typeface), black/white
surfaces, a red accent. Text styles are applied as you type and finished words can be swapped for slang automatically.

Prebuilt APK: [`dist/SwagKeys-1.2.1.apk`](dist/SwagKeys-1.2.1.apk) (Android 8.0 / API 26 or later,
signed with a debug key; installs over earlier versions).

## Install

1. Install the APK (allow installs from unknown sources when prompted).
2. Open **SwagKeys** → **Enable SwagKeys** → turn it on in system settings.
3. **Switch to SwagKeys**, or long-press the space bar in any keyboard to pick it.

## Features

| Feature | Behavior |
|---|---|
| Styles | 17: NORMAL, L33T (a→4 e→3 i→1 o→0 s→5 t→7), mOcKiNg, 𝐁𝐎𝐋𝐃, 𝓢𝓒𝓡𝓘𝓟𝓣, ＷＩＤＥ, ⓑⓤⓑⓑⓛⓔ, ꜱᴍᴀʟʟ ᴄᴀᴘꜱ, s̶t̶r̶i̶k̶e̶, 𝖌𝖔𝖙𝖍𝖎𝖈, 𝚖𝚘𝚗𝚘, 𝕕𝕠𝕦𝕓𝕝𝕖, ᵗⁱⁿʸ, cursed (z̴a̷l̸g̵o̶), u̲n̲d̲e̲r̲l̲i̲n̲e̲, 🅂🅀🅄🄰🅁🄴🄳, s p a c e d. The current word stays editable (composing text) and is restyled from what you typed. Choose from the style chip on the keyboard or in settings. |
| Animations | Glyph ripple through the dot grid on every key press, key press/spring-back, pop-in previews, sliding panels, and a decode effect with a SWAG pulse when slang kicks in. Off with the setting or when Android's Remove animations is on. |
| Gestures | Swipe along the space bar to move the cursor; swipe left from delete to erase whole words (a bubble shows the count; slide back to cancel). Holding delete, cursor steps and word-count steps all vibrate (with the key-press haptic setting). |
| Look | Dark/light/system theme; accent colour Nothing red, volt, cyan, purple or orange (text uses a contrast-checked shade on light backgrounds); optional number row. |
| Swag slang | 89 single-word substitutions (hello → wassup, cool → lit, friend → homie, …), applied when a word is finished; replacements are plain text. Delete right after a substitution, or tap **UNDO**, to restore the word. |
| Emoji | The emoji key (left of the comma) opens every fully-qualified emoji of Unicode Emoji 18.0: 1923 emoji in nine categories plus recently used, and 2040 skin-tone variants on long-press (dotted cells). Emoji the phone's font cannot draw are hidden. |
| Where slang applies | Automatically in chat-style fields (multi-line or auto-correct). In single-line search, name and address fields it is only offered in the strip (tap to apply). Never in passwords, URLs, e-mail, number fields, or in words glued to other text (`#tags`, `@names`, `a.b`, `a_b`). |
| Keyboard | Multi-touch roll-over, one-shot / double-tap / long-press shift, long-press top row for digits, key repeat on delete, symbol and number pads, editor-action Enter key, TalkBack support (explore by touch, lift to type). Switch keyboards by long-pressing the emoji key (next keyboard) or the space bar (picker). |
| Font | Bundled: Doto (SIL OFL 1.1). Any `.ttf`/`.otf` can be imported in settings. |

Nothing's own NDot typefaces are licensed for Nothing brand use only, so they are not bundled.
The emoji list is regenerated with `python3 tools/build_emoji.py emoji-test.txt app/src/main/assets/emoji.txt`
from Unicode's [emoji-test.txt](https://unicode.org/Public/emoji/latest/emoji-test.txt) (Unicode License v3).

## Build

Requires JDK 17+ and an Android SDK with platform 36 (`local.properties`: `sdk.dir=...`).

```
./gradlew testDebugUnitTest lintRelease assembleRelease
```

The release APK is signed with `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`,
`keyPassword`) if present, otherwise with the local debug key. Robolectric tests render the
keyboard to `app/build/screenshots/`.
