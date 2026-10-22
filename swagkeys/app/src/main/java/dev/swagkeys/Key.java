package dev.swagkeys;

import android.graphics.RectF;

/** A key definition plus its computed bounds within the keyboard view. */
public final class Key {
    public static final int CODE_SHIFT = -1;
    public static final int CODE_MODE_SYMBOLS = -2;
    public static final int CODE_MODE_ALPHA = -3;
    public static final int CODE_MODE_SYMBOLS2 = -4;
    public static final int CODE_DELETE = -5;
    public static final int CODE_EMOJI = -6;
    public static final int CODE_ENTER = -7;
    public static final int CODE_SPACE = ' ';

    public enum Type { CHAR, FUNCTION, SPACE, ENTER, SHIFT, DELETE }

    public final int code;
    public final String label;
    public final DotIcon icon;
    public final float weight;
    /** Code point emitted on long press, or 0. */
    public final int longPressCode;
    public final Type type;

    public final RectF bounds = new RectF();

    Key(int code, String label, DotIcon icon, float weight, int longPressCode, Type type) {
        this.code = code;
        this.label = label;
        this.icon = icon;
        this.weight = weight;
        this.longPressCode = longPressCode;
        this.type = type;
    }

    static Key chr(char c, float weight) {
        return new Key(c, String.valueOf(c), null, weight, 0, Type.CHAR);
    }

    static Key chr(String s, float weight) {
        return new Key(s.codePointAt(0), s, null, weight, 0, Type.CHAR);
    }

    static Key chrLong(char c, char longPress, float weight) {
        return new Key(c, String.valueOf(c), null, weight, longPress, Type.CHAR);
    }

    static Key fn(int code, String label, DotIcon icon, float weight, Type type) {
        return new Key(code, label, icon, weight, 0, type);
    }

    public boolean isLetter() {
        return type == Type.CHAR && Character.isLetter(code);
    }

    public boolean isModifier() {
        return type == Type.SHIFT || type == Type.DELETE;
    }
}
