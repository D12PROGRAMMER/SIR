package dev.swagkeys;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Preference keys and typed accessors. */
public final class Prefs {
    public static final String FILE = "swagkeys";

    public static final String KEY_STYLE = "style";
    public static final String KEY_SLANG = "slang";
    public static final String KEY_THEME = "theme";
    public static final String KEY_FONT = "font";
    /** Incremented whenever the imported font file is replaced, so listeners reload it. */
    public static final String KEY_FONT_VERSION = "font_version";
    public static final String KEY_FONT_NAME = "font_name";
    public static final String KEY_HAPTICS = "haptics";
    public static final String KEY_SOUND = "sound";
    public static final String KEY_RECENT_EMOJI = "recent_emoji";
    public static final String KEY_ACCENT = "accent";
    public static final String KEY_NUMBER_ROW = "number_row";
    public static final String KEY_ANIMATIONS = "animations";

    private static final int MAX_RECENT_EMOJI = 24;

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";

    public static final String FONT_DOTO = "doto";
    public static final String FONT_CUSTOM = "custom";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public SharedPreferences raw() {
        return sp;
    }

    public TextStyle style() {
        return TextStyle.fromId(sp.getString(KEY_STYLE, TextStyle.NORMAL.id));
    }

    public void setStyle(TextStyle style) {
        sp.edit().putString(KEY_STYLE, style.id).apply();
    }

    public boolean slang() {
        return sp.getBoolean(KEY_SLANG, true);
    }

    public void setSlang(boolean on) {
        sp.edit().putBoolean(KEY_SLANG, on).apply();
    }

    public String theme() {
        return sp.getString(KEY_THEME, THEME_SYSTEM);
    }

    public void setTheme(String theme) {
        sp.edit().putString(KEY_THEME, theme).apply();
    }

    public String font() {
        return sp.getString(KEY_FONT, FONT_DOTO);
    }

    public void setFont(String font) {
        sp.edit().putString(KEY_FONT, font).apply();
    }

    public String fontName() {
        return sp.getString(KEY_FONT_NAME, null);
    }

    public void onCustomFontImported(String displayName) {
        sp.edit()
                .putString(KEY_FONT_NAME, displayName)
                .putInt(KEY_FONT_VERSION, sp.getInt(KEY_FONT_VERSION, 0) + 1)
                .putString(KEY_FONT, FONT_CUSTOM)
                .apply();
    }

    public int fontVersion() {
        return sp.getInt(KEY_FONT_VERSION, 0);
    }

    /** Recently inserted emoji, most recent first. */
    public List<String> recentEmojis() {
        String raw = sp.getString(KEY_RECENT_EMOJI, "");
        List<String> list = new ArrayList<>();
        for (String e : raw.split("\n")) {
            if (!e.isEmpty()) list.add(e);
        }
        return list;
    }

    public void addRecentEmoji(String emoji) {
        List<String> list = recentEmojis();
        list.remove(emoji);
        list.add(0, emoji);
        while (list.size() > MAX_RECENT_EMOJI) list.remove(list.size() - 1);
        sp.edit().putString(KEY_RECENT_EMOJI, String.join("\n", list)).apply();
    }

    public String accent() {
        return sp.getString(KEY_ACCENT, "red");
    }

    public void setAccent(String id) {
        sp.edit().putString(KEY_ACCENT, id).apply();
    }

    public boolean numberRow() {
        return sp.getBoolean(KEY_NUMBER_ROW, false);
    }

    public void setNumberRow(boolean on) {
        sp.edit().putBoolean(KEY_NUMBER_ROW, on).apply();
    }

    public boolean animations() {
        return sp.getBoolean(KEY_ANIMATIONS, true);
    }

    public void setAnimations(boolean on) {
        sp.edit().putBoolean(KEY_ANIMATIONS, on).apply();
    }

    public boolean haptics() {
        return sp.getBoolean(KEY_HAPTICS, true);
    }

    public void setHaptics(boolean on) {
        sp.edit().putBoolean(KEY_HAPTICS, on).apply();
    }

    public boolean sound() {
        return sp.getBoolean(KEY_SOUND, false);
    }

    public void setSound(boolean on) {
        sp.edit().putBoolean(KEY_SOUND, on).apply();
    }
}
