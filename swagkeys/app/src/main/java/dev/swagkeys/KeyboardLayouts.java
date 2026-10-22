package dev.swagkeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Static key layouts. Every row spans 10 width units (leading offset + key weights + trailing). */
public final class KeyboardLayouts {

    public enum Mode { LETTERS, SYMBOLS, SYMBOLS2, NUMPAD }

    public static final float ROW_UNITS = 10f;

    public static final class Row {
        public final float offset;
        public final List<Key> keys;

        Row(float offset, Key... keys) {
            this.offset = offset;
            this.keys = new ArrayList<>(Arrays.asList(keys));
        }
    }

    private KeyboardLayouts() {}

    public static List<Row> build(Mode mode, FieldConfig.Layout numpadVariant, Key enterKey) {
        switch (mode) {
            case SYMBOLS:
                return symbols(enterKey);
            case SYMBOLS2:
                return symbols2(enterKey);
            case NUMPAD:
                return numpad(numpadVariant, enterKey);
            case LETTERS:
            default:
                return letters(enterKey);
        }
    }

    private static List<Row> letters(Key enter) {
        String top = "qwertyuiop";
        String digits = "1234567890";
        Key[] r1 = new Key[10];
        for (int i = 0; i < 10; i++) r1[i] = Key.chrLong(top.charAt(i), digits.charAt(i), 1f);
        return Arrays.asList(
                new Row(0f, r1),
                new Row(0.5f, chars("asdfghjkl")),
                new Row(0f, concat(
                        new Key[] {Key.fn(Key.CODE_SHIFT, null, DotIcon.SHIFT, 1.5f, Key.Type.SHIFT)},
                        chars("zxcvbnm"),
                        new Key[] {Key.fn(Key.CODE_DELETE, null, DotIcon.DELETE, 1.5f, Key.Type.DELETE)})),
                bottomRow("?123", Key.CODE_MODE_SYMBOLS, ',', '.', enter));
    }

    private static List<Row> symbols(Key enter) {
        return Arrays.asList(
                new Row(0f, chars("1234567890")),
                new Row(0f, chars("@#$_&-+()/")),
                new Row(0f, concat(
                        new Key[] {Key.fn(Key.CODE_MODE_SYMBOLS2, "=\\<", null, 1.5f, Key.Type.FUNCTION)},
                        chars("*\"':;!?"),
                        new Key[] {Key.fn(Key.CODE_DELETE, null, DotIcon.DELETE, 1.5f, Key.Type.DELETE)})),
                bottomRow("ABC", Key.CODE_MODE_ALPHA, ',', '.', enter));
    }

    private static List<Row> symbols2(Key enter) {
        return Arrays.asList(
                new Row(0f, chars("~`|•√π÷×¶∆")),
                new Row(0f, chars("£¢€¥^°={}\\")),
                new Row(0f, concat(
                        new Key[] {Key.fn(Key.CODE_MODE_SYMBOLS, "?123", null, 1.5f, Key.Type.FUNCTION)},
                        chars("%©®™✓[]"),
                        new Key[] {Key.fn(Key.CODE_DELETE, null, DotIcon.DELETE, 1.5f, Key.Type.DELETE)})),
                bottomRow("ABC", Key.CODE_MODE_ALPHA, '<', '>', enter));
    }

    private static List<Row> numpad(FieldConfig.Layout variant, Key enter) {
        float w = 2.5f;
        Key del = Key.fn(Key.CODE_DELETE, null, DotIcon.DELETE, w, Key.Type.DELETE);
        Key enterWide = Key.fn(enter.code, enter.label, enter.icon, w, Key.Type.ENTER);
        switch (variant) {
            case PHONE:
                return Arrays.asList(
                        new Row(0f, Key.chr('1', w), Key.chr('2', w), Key.chr('3', w), Key.chr('+', w)),
                        new Row(0f, Key.chr('4', w), Key.chr('5', w), Key.chr('6', w), Key.chr('-', w)),
                        new Row(0f, Key.chr('7', w), Key.chr('8', w), Key.chr('9', w), del),
                        new Row(0f, Key.chr('*', w), Key.chr('0', w), Key.chr('#', w), enterWide));
            case DATETIME:
                return Arrays.asList(
                        new Row(0f, Key.chr('1', w), Key.chr('2', w), Key.chr('3', w), Key.chr('/', w)),
                        new Row(0f, Key.chr('4', w), Key.chr('5', w), Key.chr('6', w), Key.chr(':', w)),
                        new Row(0f, Key.chr('7', w), Key.chr('8', w), Key.chr('9', w), del),
                        new Row(0f, Key.chr('-', w), Key.chr('0', w),
                                Key.fn(Key.CODE_SPACE, null, null, w, Key.Type.SPACE), enterWide));
            case NUMBER:
            default:
                return Arrays.asList(
                        new Row(0f, Key.chr('1', w), Key.chr('2', w), Key.chr('3', w), Key.chr('-', w)),
                        new Row(0f, Key.chr('4', w), Key.chr('5', w), Key.chr('6', w), Key.chr('.', w)),
                        new Row(0f, Key.chr('7', w), Key.chr('8', w), Key.chr('9', w), del),
                        new Row(0f, Key.chr(',', w), Key.chr('0', w),
                                Key.fn(Key.CODE_SPACE, null, null, w, Key.Type.SPACE), enterWide));
        }
    }

    private static Row bottomRow(String modeLabel, int modeCode, char left, char right,
            Key enter) {
        List<Key> keys = new ArrayList<>();
        keys.add(Key.fn(modeCode, modeLabel, null, 1.5f, Key.Type.FUNCTION));
        keys.add(Key.fn(Key.CODE_EMOJI, null, DotIcon.EMOJI, 1f, Key.Type.FUNCTION));
        keys.add(Key.chr(left, 1f));
        keys.add(Key.fn(Key.CODE_SPACE, null, null, 4f, Key.Type.SPACE));
        keys.add(Key.chr(right, 1f));
        keys.add(Key.fn(enter.code, enter.label, enter.icon, 1.5f, Key.Type.ENTER));
        return new Row(0f, keys.toArray(new Key[0]));
    }

    private static Key[] chars(String s) {
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            keys.add(Key.chr(new String(Character.toChars(cp)), 1f));
            i += Character.charCount(cp);
        }
        return keys.toArray(new Key[0]);
    }

    private static Key[] concat(Key[]... parts) {
        List<Key> all = new ArrayList<>();
        for (Key[] p : parts) all.addAll(Arrays.asList(p));
        return all.toArray(new Key[0]);
    }
}
