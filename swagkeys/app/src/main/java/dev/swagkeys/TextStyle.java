package dev.swagkeys;

/**
 * Output styles. Each style maps a plain (unstyled) string to its styled form. Transforms are
 * pure and operate on Unicode code points, so a word can be re-styled from its raw form at any
 * time; this is what lets the composing word stay editable while it is displayed styled.
 */
public enum TextStyle {
    NORMAL("NORMAL", "normal"),
    LEET("L33T", "l33t"),
    MOCKING("mOcKiNg", "mocking"),
    BOLD("BOLD", "bold"),
    SCRIPT("SCRIPT", "script"),
    WIDE("WIDE", "wide"),
    BUBBLE("BUBBLE", "bubble"),
    SMALL_CAPS("SMALL CAPS", "small_caps"),
    STRIKE("STRIKE", "strike");

    /** Label shown on the keyboard style chip and in settings. */
    public final String label;
    /** Stable identifier persisted in preferences. */
    public final String id;

    TextStyle(String label, String id) {
        this.label = label;
        this.id = id;
    }

    public static TextStyle fromId(String id) {
        for (TextStyle s : values()) {
            if (s.id.equals(id)) return s;
        }
        return NORMAL;
    }

    public TextStyle next() {
        TextStyle[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    private static final String SMALL_CAPS_MAP = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final char COMBINING_LONG_STROKE = '̶';

    public String apply(CharSequence plain) {
        if (this == NORMAL) return plain.toString();
        StringBuilder out = new StringBuilder(plain.length() * 2);
        int letterIndex = 0;
        for (int i = 0; i < plain.length(); ) {
            int cp = Character.codePointAt(plain, i);
            i += Character.charCount(cp);
            switch (this) {
                case LEET:
                    out.appendCodePoint(leet(cp));
                    break;
                case MOCKING:
                    if (Character.isLetter(cp)) {
                        out.appendCodePoint(letterIndex % 2 == 0
                                ? Character.toLowerCase(cp) : Character.toUpperCase(cp));
                        letterIndex++;
                    } else {
                        out.appendCodePoint(cp);
                    }
                    break;
                case BOLD:
                    out.appendCodePoint(offsetAlnum(cp, 0x1D400, 0x1D41A, 0x1D7CE));
                    break;
                case SCRIPT:
                    // Mathematical Bold Script has no reserved holes, unlike plain Script.
                    out.appendCodePoint(offsetAlnum(cp, 0x1D4D0, 0x1D4EA, -1));
                    break;
                case WIDE:
                    out.appendCodePoint(wide(cp));
                    break;
                case BUBBLE:
                    out.appendCodePoint(bubble(cp));
                    break;
                case SMALL_CAPS:
                    out.appendCodePoint(cp >= 'a' && cp <= 'z' ? SMALL_CAPS_MAP.charAt(cp - 'a') : cp);
                    break;
                case STRIKE:
                    out.appendCodePoint(cp);
                    // One stroke after each cluster: never between a base and its variation
                    // selector, skin tone, ZWJ or combining mark (that breaks emoji sequences).
                    int next = i < plain.length() ? Character.codePointAt(plain, i) : -1;
                    if (!Character.isWhitespace(cp) && cp != 0x200D && !joinsPrevious(next)) {
                        out.append(COMBINING_LONG_STROKE);
                    }
                    break;
                default:
                    out.appendCodePoint(cp);
            }
        }
        return out.toString();
    }

    private static boolean joinsPrevious(int cp) {
        if (cp < 0) return false;
        if (cp == 0x200D || (cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0x1F3FB && cp <= 0x1F3FF)) {
            return true;
        }
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }

    private static int leet(int cp) {
        switch (cp) {
            case 'a': case 'A': return '4';
            case 'e': case 'E': return '3';
            case 'i': case 'I': return '1';
            case 'o': case 'O': return '0';
            case 's': case 'S': return '5';
            case 't': case 'T': return '7';
            default: return cp;
        }
    }

    private static int offsetAlnum(int cp, int upperBase, int lowerBase, int digitBase) {
        if (cp >= 'A' && cp <= 'Z') return upperBase + (cp - 'A');
        if (cp >= 'a' && cp <= 'z') return lowerBase + (cp - 'a');
        if (digitBase >= 0 && cp >= '0' && cp <= '9') return digitBase + (cp - '0');
        return cp;
    }

    private static int wide(int cp) {
        if (cp == ' ') return 0x3000;
        if (cp >= 0x21 && cp <= 0x7E) return cp + 0xFEE0;
        return cp;
    }

    private static int bubble(int cp) {
        if (cp >= 'A' && cp <= 'Z') return 0x24B6 + (cp - 'A');
        if (cp >= 'a' && cp <= 'z') return 0x24D0 + (cp - 'a');
        if (cp == '0') return 0x24EA;
        if (cp >= '1' && cp <= '9') return 0x2460 + (cp - '1');
        return cp;
    }
}
