package dev.swagkeys;

import android.content.Context;
import android.graphics.Typeface;

import java.io.File;

/**
 * Typeface loading. The bundled face is Doto (SIL OFL 1.1), a dot-matrix family in the style of
 * Nothing OS. Nothing's own NDot faces are licensed for Nothing brand use only and are therefore
 * not bundled; a user-supplied font file can be imported in settings instead.
 */
public final class Fonts {
    public static final String CUSTOM_FONT_FILE = "custom_font";

    private static Typeface dotoBlack;
    private static Typeface dotoBold;

    private Fonts() {}

    /** Heavy weight, used for key labels. */
    public static synchronized Typeface dotoBlack(Context context) {
        if (dotoBlack == null) {
            dotoBlack = Typeface.createFromAsset(context.getAssets(), "fonts/doto_black.ttf");
        }
        return dotoBlack;
    }

    /** Bold weight, used for secondary text. */
    public static synchronized Typeface dotoBold(Context context) {
        if (dotoBold == null) {
            dotoBold = Typeface.createFromAsset(context.getAssets(), "fonts/doto_bold.ttf");
        }
        return dotoBold;
    }

    public static File customFontFile(Context context) {
        return new File(context.getFilesDir(), CUSTOM_FONT_FILE);
    }

    /** Loads a font file, returning null if it is missing or not a usable font. */
    public static Typeface load(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            // createFromFile returns Typeface.DEFAULT for an existing but unparsable file.
            Typeface tf = Typeface.createFromFile(file);
            return tf == null || tf == Typeface.DEFAULT ? null : tf;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Returns {key label face, secondary text face} for the font preference. An imported font is
     * used for both; if it is missing or invalid, Doto is used.
     */
    public static Typeface[] resolve(Context context, Prefs prefs) {
        if (Prefs.FONT_CUSTOM.equals(prefs.font())) {
            Typeface custom = load(customFontFile(context));
            if (custom != null) return new Typeface[] {custom, custom};
        }
        return new Typeface[] {dotoBlack(context), dotoBold(context)};
    }
}
