package dev.swagkeys;

import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Emoji picker data: categories in Unicode CLDR order. Entries that the device's fonts cannot
 * render as a single glyph (newer than the installed emoji font) are dropped at load time.
 */
public final class EmojiData {

    public static final class Category {
        /** Emoji shown on the category tab. */
        public final String icon;
        /** String resource naming the category for accessibility. */
        public final int nameRes;
        public final List<String> emojis;

        Category(String icon, int nameRes, List<String> emojis) {
            this.icon = icon;
            this.nameRes = nameRes;
            this.emojis = emojis;
        }
    }

    private static List<Category> categories;

    private EmojiData() {}
    private static final String SMILEYS = "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 ☺️ 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 ☠️ 💩 🤡 👹 👺 👻 👽 👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊";
    private static final String PEOPLE = "👋 🤚 🖐️ ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦵 🦶 👂 👃 🧠 👀 👁️ 👅 👄 💋 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🙇 🤦 🤷 👮 🕵️ 💂 👷 🤴 👸 👳 👲 🧕 🤵 👰 🤰 🤱 👼 🎅 🤶 🦸 🦹 🧙 🧚 🧛 🧜 🧝 🧞 🧟 💆 💇 🚶 🏃 💃 🕺 👯 🧖 🧘 🛀 🛌 👭 👫 👬 💏 💑 👪 🗣️ 👤 👥 👣";
    private static final String NATURE = "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🕷️ 🦂 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦏 🐪 🐫 🦒 🐄 🐎 🐖 🐑 🐐 🦌 🐕 🐈 🐓 🦃 🕊️ 🐇 🐁 🐿️ 🦔 🐾 🐉 🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍃 🍂 🍁 🍄 🐚 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌚 🌕 🌙 🌎 💫 ⭐ 🌟 ✨ ⚡ ☄️ 💥 🔥 🌪️ 🌈 ☀️ ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ ⛄ 💨 💧 💦 ☔ 🌊";
    private static final String FOOD = "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🥔 🍠 🥐 🥯 🍞 🥖 🥨 🧀 🥚 🍳 🥞 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🥙 🌮 🌯 🥗 🥘 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🥠 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 🍼 ☕ 🍵 🥤 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🍾 🍴 🍽️ 🥣 🥡 🥢 🧂";
    private static final String ACTIVITY = "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🏓 🏸 🥅 🏒 🏑 🏏 ⛳ 🏹 🎣 🥊 🥋 🎽 🛹 🛷 ⛸️ 🎿 🏆 🥇 🥈 🥉 🏅 🎖️ 🎫 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 🧩 🎯 🎳 🎮 🎰 🚗 🚕 🚙 🚌 🏎️ 🚓 🚑 🚒 🚐 🚚 🚜 🛴 🚲 🛵 🏍️ 🚨 🚂 🚆 🚇 ✈️ 🛫 🛬 🚀 🛸 🚁 ⛵ 🚤 🚢 ⚓ ⛽ 🚦 🗺️ 🗿 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ 🏖️ 🏝️ 🌋 ⛰️ 🏔️ 🗻 🏕️ ⛺ 🏠 🏡 🏢 🏥 🏦 🏨 🏪 🏫 🏛️ ⛪ 🕌 🕍 ⛩️ 🌃 🌆 🌇 🌉 🎆 🎇";
    private static final String OBJECTS = "⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 💿 📷 📸 📹 🎥 📞 ☎️ 📺 📻 🎙️ ⏰ ⌛ ⏳ 📡 🔋 🔌 💡 🔦 🕯️ 💸 💵 💰 💳 💎 ⚖️ 🔧 🔨 🛠️ ⚙️ 🧲 💣 🧨 🔪 🗡️ ⚔️ 🛡️ 🔮 📿 🧿 💈 🔭 🔬 💊 💉 🧬 🦠 🧪 🌡️ 🧹 🧻 🚽 🚿 🛁 🧼 🔑 🗝️ 🚪 🛋️ 🛏️ 🧸 🖼️ 🛍️ 🛒 🎁 🎈 🎀 🎊 🎉 🏮 🧧 ✉️ 📩 💌 📦 🏷️ 📮 📜 📄 📊 📈 📉 🗓️ 📅 📋 📁 📰 📓 📕 📗 📘 📙 📚 📖 🔖 🔗 📎 📐 📏 📌 📍 ✂️ 🖊️ 🖌️ 🖍️ 📝 ✏️ 🔍 🔒 🔓";
    private static final String SYMBOLS = "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ ✝️ ☪️ 🕉️ ☯️ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ ⚛️ ☢️ ☣️ 🆚 💯 💢 ♨️ 🚫 ⛔ 🛑 ❌ ⭕ ❗ ❓ ‼️ ⁉️ ⚠️ 🔞 ♻️ ✅ ☑️ ✔️ ❎ ➕ ➖ ➗ ✖️ 💲 💱 🔱 ⚜️ 🔰 🌀 💤 💬 💭 🗯️ ♠️ ♣️ ♥️ ♦️ 🃏 🎴 🀄 🔔 🔕 📣 📢 🎵 🎶 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟤 🔺 🔻 🔸 🔹 🔶 🔷 ⬛ ⬜ 🆒 🆕 🆓 🆗 🆙 🆘 🔝 🔜 🔙 ➡️ ⬅️ ⬆️ ⬇️ 🔄 🔀 🔁 ▶️ ⏸️ ⏩ ⏪ 🏁 🚩 🏳️ 🏴 🏳️‍🌈";

    /** Categories with unrenderable entries removed; computed once. */
    public static synchronized List<Category> categories() {
        if (categories == null) {
            Paint paint = new Paint();
            List<Category> list = new ArrayList<>();
            list.add(category("😀", R.string.a11y_emoji_smileys, SMILEYS, paint));
            list.add(category("👋", R.string.a11y_emoji_people, PEOPLE, paint));
            list.add(category("🐶", R.string.a11y_emoji_nature, NATURE, paint));
            list.add(category("🍔", R.string.a11y_emoji_food, FOOD, paint));
            list.add(category("⚽", R.string.a11y_emoji_activity, ACTIVITY, paint));
            list.add(category("💡", R.string.a11y_emoji_objects, OBJECTS, paint));
            list.add(category("❤️", R.string.a11y_emoji_symbols, SYMBOLS, paint));
            categories = Collections.unmodifiableList(list);
        }
        return categories;
    }

    private static Category category(String icon, int nameRes, String data, Paint paint) {
        List<String> all = Arrays.asList(data.split(" "));
        List<String> renderable = new ArrayList<>(all.size());
        for (String e : all) {
            if (paint.hasGlyph(e)) renderable.add(e);
        }
        // A font stack that reports no emoji glyphs at all (e.g. a stripped test runtime) would
        // leave the picker empty; show the full list rather than nothing.
        return new Category(icon, nameRes, Collections.unmodifiableList(
                renderable.isEmpty() ? all : renderable));
    }
}
