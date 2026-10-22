package dev.swagkeys;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Monochrome palette with a single accent (Nothing red by default), after Nothing OS. Colors are
 * ARGB ints. {@link #accent} is for solid fills (Enter, SWAG pill, shift lock) with
 * {@link #onAccent} on top; {@link #accentInk} is the accent as text, outlines and indicators,
 * darkened on light backgrounds until it reaches 4.5:1 contrast.
 */
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
    public final int accentInk;
    public final int outline;

    private Palette(boolean dark, int background, int dotGrid, int key, int keyFunction,
            int keyPressed, int text, int textSecondary, int accent, int outline) {
        this.dark = dark;
        this.background = background;
        this.dotGrid = dotGrid;
        this.key = key;
        this.keyFunction = keyFunction;
        this.keyPressed = keyPressed;
        this.text = text;
        this.textSecondary = textSecondary;
        this.accent = accent;
        this.onAccent = onAccentFor(accent);
        this.accentInk = dark ? accent : inkFor(accent, background);
        this.outline = outline;
    }

    /** WCAG contrast ratio of two opaque colors. */
    static double contrast(int a, int b) {
        double la = android.graphics.Color.luminance(a);
        double lb = android.graphics.Color.luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** Black or white, whichever contrasts more with the accent. */
    static int onAccentFor(int accent) {
        double l = android.graphics.Color.luminance(accent);
        // Equal contrast against black and white at (l + 0.05)^2 = 0.0525 (l ~ 0.179).
        return (l + 0.05) * (l + 0.05) > 0.0525 ? 0xFF000000 : 0xFFFFFFFF;
    }

    /** The accent darkened (keeping its hue) until it reads at 4.5:1 on {@code background}. */
    static int inkFor(int accent, int background) {
        for (float f = 1f; f > 0f; f -= 0.02f) {
            int c = android.graphics.Color.rgb(
                    Math.round(android.graphics.Color.red(accent) * f),
                    Math.round(android.graphics.Color.green(accent) * f),
                    Math.round(android.graphics.Color.blue(accent) * f));
            if (contrast(c, background) >= 4.5) return c;
        }
        return 0xFF000000;
    }

    private static final int NOTHING_RED = 0xFFD71921;

    public static final Palette DARK = new Palette(true,
            0xFF000000, 0xFF1A1A1A, 0xFF1B1B1D, 0xFF2A2A2D, 0xFF4A4A4F,
            0xFFFFFFFF, 0xFF8E8E93, NOTHING_RED, 0xFF3A3A3C);

    public static final Palette LIGHT = new Palette(false,
            0xFFEDEDED, 0xFFD2D2D2, 0xFFFFFFFF, 0xFFD9D9DB, 0xFFB8B8BC,
            0xFF000000, 0xFF6B6B70, NOTHING_RED, 0xFFC4C4C7);

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

    /** This palette with a different accent; onAccent and accentInk are derived from it. */
    public Palette withAccent(int accentColor) {
        if (accentColor == accent) return this;
        return new Palette(dark, background, dotGrid, key, keyFunction, keyPressed, text,
                textSecondary, accentColor, outline);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Palette)) return false;
        Palette p = (Palette) o;
        return dark == p.dark && background == p.background && dotGrid == p.dotGrid
                && key == p.key && keyFunction == p.keyFunction && keyPressed == p.keyPressed
                && text == p.text && textSecondary == p.textSecondary && accent == p.accent
                && onAccent == p.onAccent && accentInk == p.accentInk && outline == p.outline;
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(new int[] {dark ? 1 : 0, background, dotGrid, key,
                keyFunction, keyPressed, text, textSecondary, accent, onAccent, accentInk, outline});
    }

    /** Resolves the palette for the theme preference ("system", "dark" or "light"). */
    public static Palette resolve(Context context, String themePref) {
        if (Prefs.THEME_DARK.equals(themePref)) return DARK;
        if (Prefs.THEME_LIGHT.equals(themePref)) return LIGHT;
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_NO ? LIGHT : DARK;
    }
}
