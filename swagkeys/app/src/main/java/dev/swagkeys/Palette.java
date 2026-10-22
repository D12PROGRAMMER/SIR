package dev.swagkeys;

import android.content.Context;
import android.content.res.Configuration;

/** Monochrome palette with a single red accent, after Nothing OS. Colors are ARGB ints. */
public final class Palette {
    public final boolean dark;
    public final int background;
    public final int dotGrid;
    public final int key;
    public final int keyFunction;
    public final int keyPressed;
    public final int text;
    public final int textSecondary;
    public final int accent;
    public final int onAccent;
    public final int outline;

    private Palette(boolean dark, int background, int dotGrid, int key, int keyFunction,
            int keyPressed, int text, int textSecondary, int accent, int onAccent, int outline) {
        this.dark = dark;
        this.background = background;
        this.dotGrid = dotGrid;
        this.key = key;
        this.keyFunction = keyFunction;
        this.keyPressed = keyPressed;
        this.text = text;
        this.textSecondary = textSecondary;
        this.accent = accent;
        this.onAccent = onAccent;
        this.outline = outline;
    }

    private static final int NOTHING_RED = 0xFFD71921;

    public static final Palette DARK = new Palette(true,
            0xFF000000, 0xFF1A1A1A, 0xFF1B1B1D, 0xFF2A2A2D, 0xFF4A4A4F,
            0xFFFFFFFF, 0xFF8E8E93, NOTHING_RED, 0xFFFFFFFF, 0xFF3A3A3C);

    public static final Palette LIGHT = new Palette(false,
            0xFFEDEDED, 0xFFD2D2D2, 0xFFFFFFFF, 0xFFD9D9DB, 0xFFB8B8BC,
            0xFF000000, 0xFF6B6B70, NOTHING_RED, 0xFFFFFFFF, 0xFFC4C4C7);

    /** Accent choices: id, label, ARGB color. */
    public static final String[][] ACCENTS = {
            {"red", "Nothing red", "#D71921"},
            {"volt", "Volt", "#B6F400"},
            {"cyan", "Cyan", "#00C2FF"},
            {"purple", "Purple", "#9B5CFF"},
            {"orange", "Orange", "#FF6A00"},
    };

    public static int accentColor(String id) {
        for (String[] a : ACCENTS) {
            if (a[0].equals(id)) return android.graphics.Color.parseColor(a[2]);
        }
        return NOTHING_RED;
    }

    /** This palette with a different accent; text on the accent stays readable. */
    public Palette withAccent(int accentColor) {
        if (accentColor == accent) return this;
        double luminance = android.graphics.Color.luminance(accentColor);
        int on = luminance > 0.4 ? 0xFF000000 : 0xFFFFFFFF;
        return new Palette(dark, background, dotGrid, key, keyFunction, keyPressed, text,
                textSecondary, accentColor, on, outline);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Palette)) return false;
        Palette p = (Palette) o;
        return dark == p.dark && background == p.background && dotGrid == p.dotGrid
                && key == p.key && keyFunction == p.keyFunction && keyPressed == p.keyPressed
                && text == p.text && textSecondary == p.textSecondary && accent == p.accent
                && onAccent == p.onAccent && outline == p.outline;
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(new int[] {dark ? 1 : 0, background, dotGrid, key,
                keyFunction, keyPressed, text, textSecondary, accent, onAccent, outline});
    }

    /** Resolves the palette for the theme preference ("system", "dark" or "light"). */
    public static Palette resolve(Context context, String themePref) {
        if (Prefs.THEME_DARK.equals(themePref)) return DARK;
        if (Prefs.THEME_LIGHT.equals(themePref)) return LIGHT;
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_NO ? LIGHT : DARK;
    }
}
