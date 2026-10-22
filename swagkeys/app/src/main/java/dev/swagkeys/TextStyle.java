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
    STRIKE("STRIKE", "strike"),
    GOTHIC("GOTHIC", "gothic"),
    MONO("MONO", "mono"),
    DOUBLE("DOUBLE", "double"),
    TINY("TINY", "tiny"),
    CURSED("CURSED", "cursed"),
    UNDERLINE("UNDERLINE", "underline"),
    SQUARED("SQUARED", "squared"),
    SPACED("S P A C E D", "spaced");

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
    private static final char COMBINING_LONG_STROKE = '\u0336';
    private static final char COMBINING_LOW_LINE = '\u0332';
    /** Superscript forms of a-z (q has no superscript letter; U+146B is the customary stand-in). */
    private static final String TINY_LETTERS =
            "ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐ"
            + "ⁿᵒᵖᑫʳˢᵗᵘᵛʷˣʸᶻ";
    private static final String TINY_DIGITS =
            "⁰¹²³⁴⁵⁶⁷⁸⁹";
    private static final char[] CURSED_ABOVE = {
            '\u030D', '\u030E', '\u0304', '\u0305', '\u033F', '\u0311', '\u0306', '\u0310',
            '\u0352', '\u0357', '\u0351', '\u0307', '\u0308', '\u030A', '\u0342', '\u0343',
            '\u0344', '\u034A', '\u034B', '\u034C', '\u0303', '\u0302', '\u030C', '\u0350'};
    private static final char[] CURSED_BELOW = {
            '\u0316', '\u0317', '\u0318', '\u0319', '\u031C', '\u031D', '\u031E', '\u031F',
            '\u0320', '\u0324', '\u0325', '\u0326', '\u0329', '\u032A', '\u032B', '\u032C',
            '\u032D', '\u032E', '\u032F', '\u0330', '\u0339', '\u033A', '\u033B', '\u033C'};

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
                case UNDERLINE:
                    out.appendCodePoint(cp);
                    // One mark after each cluster: never between a base and its variation
                    // selector, skin tone, ZWJ or combining mark (that breaks emoji sequences).
                    int next = i < plain.length() ? Character.codePointAt(plain, i) : -1;
                    if (!Character.isWhitespace(cp) && cp != 0x200D && !joinsPrevious(next)) {
                        out.append(this == STRIKE ? COMBINING_LONG_STROKE : COMBINING_LOW_LINE);
                    }
                    break;
                case GOTHIC:
                    // Mathematical Bold Fraktur has no reserved holes.
                    out.appendCodePoint(offsetAlnum(cp, 0x1D56C, 0x1D586, -1));
                    break;
                case MONO:
                    out.appendCodePoint(offsetAlnum(cp, 0x1D670, 0x1D68A, 0x1D7F6));
                    break;
                case DOUBLE:
                    out.appendCodePoint(doubleStruck(cp));
                    break;
                case TINY:
                    out.appendCodePoint(tiny(cp));
                    break;
                case SQUARED:
                    out.appendCodePoint(cp >= 'A' && cp <= 'Z' ? 0x1F130 + (cp - 'A')
                            : cp >= 'a' && cp <= 'z' ? 0x1F130 + (cp - 'a') : cp);
                    break;
                case CURSED:
                    out.appendCodePoint(cp);
                    if (Character.isLetterOrDigit(cp)) {
                        // Deterministic per position, so restyling the composing word is stable.
                        int h = mix(cp * 31 + letterIndex);
                        out.append(CURSED_ABOVE[(h & 0xFF) % CURSED_ABOVE.length]);
                        out.append(CURSED_BELOW[((h >>> 8) & 0xFF) % CURSED_BELOW.length]);
                        if (((h >>> 16) & 1) == 0) {
                            out.append(CURSED_ABOVE[((h >>> 17) & 0xFF) % CURSED_ABOVE.length]);
                        }
                        letterIndex++;
                    }
                    break;
                case SPACED:
                    if (cp == ' ') {
                        out.append("   ");
                    } else {
                        if (out.length() > 0 && !Character.isWhitespace(out.charAt(out.length() - 1))
                                && !joinsPrevious(cp)) {
                            out.append(' ');
                        }
                        out.appendCodePoint(cp);
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

    private static int mix(int x) {
        x ^= x >>> 16;
        x *= 0x7FEB352D;
        x ^= x >>> 15;
        x *= 0x846CA68B;
        x ^= x >>> 16;
        return x;
    }

    private static int doubleStruck(int cp) {
        switch (cp) {
            case 'C': return 0x2102;
            case 'H': return 0x210D;
            case 'N': return 0x2115;
            case 'P': return 0x2119;
            case 'Q': return 0x211A;
            case 'R': return 0x211D;
            case 'Z': return 0x2124;
            default: return offsetAlnum(cp, 0x1D538, 0x1D552, 0x1D7D8);
        }
    }

    private static int tiny(int cp) {
        if (cp >= 'a' && cp <= 'z') return TINY_LETTERS.charAt(cp - 'a');
        if (cp >= 'A' && cp <= 'Z') return TINY_LETTERS.charAt(cp - 'A');
        if (cp >= '0' && cp <= '9') return TINY_DIGITS.charAt(cp - '0');
        switch (cp) {
            case '+': return 0x207A;
            case '-': return 0x207B;
            case '=': return 0x207C;
            case '(': return 0x207D;
            case ')': return 0x207E;
            default: return cp;
        }
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
