package dev.swagkeys;

import java.text.BreakIterator;

/**
 * Editing state machine. The word being typed is kept unstyled in {@link #word} and shown in the
 * editor as composing text rendered through the current {@link TextStyle}. When a separator is
 * typed, the word is looked up in {@link SlangDictionary}, styled, and committed together with the
 * styled separator. Expected selection offsets are tracked so that belated
 * {@code onUpdateSelection} callbacks caused by this IME are distinguished from cursor movement by
 * the user or the application.
 */
public final class InputLogic {

    public interface Listener {
        /** Composing word, pending slang, undo availability or caps state may have changed. */
        void onInputStateChanged();
    }

    private static final int GRAPHEME_LOOKBEHIND = 64;
    /** TYPE_TEXT_FLAG_CAP_CHARACTERS, equal to TextUtils.CAP_MODE_CHARACTERS. */
    private static final int CAP_CHARACTERS = 0x00001000;

    private enum Slang { NONE, AUTO, FORCE }

    private final Editor editor;
    private final Listener listener;

    private FieldConfig config = FieldConfig.defaults();
    private TextStyle style = TextStyle.NORMAL;
    private boolean slangEnabled = true;

    /** Unstyled code points of the word currently held as composing text. */
    private final StringBuilder word = new StringBuilder();
    /** The word continues text before it (e.g. "#tag", "lo|ok" after a correction): no slang. */
    private boolean wordAttached;
    /** Absolute offset where the composing text starts, or -1 if unknown / not composing. */
    private int composingStart = -1;
    /** Expected selection after this IME's last edit; -1 when unknown. */
    private int selStart = -1;
    private int selEnd = -1;
    /** Cursor offset after a picked suggestion where a space is owed, or -1. */
    private int phantomSpaceAt = -1;

    /** Last slang substitution, revertible by Delete or the strip while the cursor is unmoved. */
    private String undoCommitted;
    private String undoRevert;
    private String undoOriginalWord;
    private String undoSeparator;
    private int undoCursor = -1;

    public InputLogic(Editor editor, Listener listener) {
        this.editor = editor;
        this.listener = listener;
    }

    // ---------------------------------------------------------------- configuration

    public void startInput(FieldConfig config) {
        this.config = config;
        if (word.length() > 0) {
            // A reset while composing must not leave a region that the next keystroke replaces.
            editor.finishComposingText();
        }
        word.setLength(0);
        composingStart = -1;
        phantomSpaceAt = -1;
        selStart = config.initialSelStart;
        selEnd = config.initialSelEnd;
        if (selStart > selEnd) {
            int t = selStart;
            selStart = selEnd;
            selEnd = t;
        }
        clearUndo();
        notifyChanged();
    }

    /** Leaves the composing word in the editor as typed (styled, without slang). */
    public void finishInput() {
        if (word.length() > 0) {
            editor.finishComposingText();
        }
        word.setLength(0);
        composingStart = -1;
        phantomSpaceAt = -1;
        clearUndo();
    }

    public FieldConfig config() {
        return config;
    }

    public TextStyle style() {
        return style;
    }

    public void setStyle(TextStyle style) {
        if (this.style == style) return;
        this.style = style;
        clearUndo();
        if (word.length() > 0 && config.mode == FieldConfig.Mode.STYLED) {
            showComposing();
        }
        notifyChanged();
    }

    public boolean isSlangEnabled() {
        return slangEnabled;
    }

    public void setSlangEnabled(boolean enabled) {
        if (slangEnabled == enabled) return;
        slangEnabled = enabled;
        notifyChanged();
    }

    /** True if styles apply to the current field. */
    public boolean stylesActive() {
        return config.mode == FieldConfig.Mode.STYLED;
    }

    /** True if slang may be suggested in the current field. */
    public boolean slangSuggestActive() {
        return slangEnabled && config.slangSuggest && config.mode == FieldConfig.Mode.STYLED;
    }

    /** True if slang is applied automatically in the current field. */
    public boolean slangAutoActive() {
        return slangSuggestActive() && config.slangAuto;
    }

    // ---------------------------------------------------------------- queries for the UI

    public String composingWord() {
        return word.toString();
    }

    /** Slang the current word would be replaced with, or null. */
    public String pendingSlang() {
        if (!slangSuggestActive() || word.length() == 0 || wordAttached) return null;
        return SlangDictionary.lookup(word.toString());
    }

    /** The original word restorable by {@link #undoSlang()}, or null. */
    public String undoableWord() {
        return undoAvailable() ? undoOriginalWord : null;
    }

    /** Whether the next letter should be capitalized according to the editor's caps mode. */
    public boolean shouldAutoCapitalize() {
        if (config.capsInputType == 0) return false;
        if ((config.capsInputType & CAP_CHARACTERS) != 0) return true;
        if (word.length() > 0) return false;
        if (editor.getCursorCapsMode(config.capsInputType) != 0) return true;
        if (style == TextStyle.WIDE || style == TextStyle.STRIKE) {
            // Fullwidth and struck separators hide spaces and sentence ends from TextUtils.
            CharSequence before = editor.getTextBeforeCursor(GRAPHEME_LOOKBEHIND);
            if (before != null) {
                return editor.capsModeOf(unstyleSeparators(before), config.capsInputType) != 0;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- input events

    public void onCodePoint(int cp) {
        switch (config.mode) {
            case RAW:
                if (Character.isBmpCodePoint(cp)) {
                    editor.sendKeyChar((char) cp);
                } else {
                    editor.commitText(new String(Character.toChars(cp)));
                }
                selStart = selEnd = -1;
                break;
            case PLAIN:
                commitPlain(new String(Character.toChars(cp)));
                break;
            case STYLED:
                onStyledCodePoint(cp);
                break;
        }
        notifyChanged();
    }

    private void onStyledCodePoint(int cp) {
        String text = new String(Character.toChars(cp));
        if (word.length() == 0 && consumePhantomSpace()) {
            if (isTrailingPunctuation(cp)) {
                // "wassup" + "?" -> "wassup?" with the space still owed.
                clearUndo();
                commitWord(Slang.NONE, text);
                phantomSpaceAt = selStart >= 0 ? selEnd : -1;
                return;
            }
            if (cp != ' ' && cp != '\n') {
                clearUndo();
                commitWord(Slang.NONE, " ");
            }
        }
        if (isWordCodePoint(cp) && word.length() == 0 && undoAvailable()
                && (".".equals(undoSeparator) || ":".equals(undoSeparator))) {
            // The token continues ("good.com", "best:"): take the replacement back.
            undoSlang();
        }
        clearUndo();
        if (isWordCodePoint(cp)) {
            if (word.length() == 0) wordAttached = isAttachedToPreviousText();
            word.appendCodePoint(cp);
            showComposing();
        } else {
            commitWord(isJoiningSeparator(cp) ? Slang.NONE : Slang.AUTO, text);
        }
    }

    /**
     * Inserts an emoji from the picker. It is committed unstyled and ends the current word the
     * way punctuation does (slang applies to that word).
     */
    public void onEmoji(String emoji) {
        switch (config.mode) {
            case RAW:
                editor.commitText(emoji);
                selStart = selEnd = -1;
                break;
            case PLAIN:
                commitPlain(emoji);
                break;
            case STYLED:
                if (word.length() == 0 && consumePhantomSpace()) {
                    clearUndo();
                    commitWord(Slang.NONE, " ");
                }
                clearUndo();
                commitWord(Slang.AUTO, emoji, false);
                break;
        }
        notifyChanged();
    }

    public void onEnter() {
        phantomSpaceAt = -1;
        if (config.enterIsAction) {
            if (config.mode == FieldConfig.Mode.STYLED && word.length() > 0) {
                commitWord(Slang.AUTO, "");
            }
            clearUndo();
            editor.performEditorAction(config.enterActionId);
            notifyChanged();
            return;
        }
        if (config.mode == FieldConfig.Mode.RAW) {
            editor.sendKeyChar('\n');
            selStart = selEnd = -1;
            notifyChanged();
            return;
        }
        onCodePoint('\n');
    }

    public void onDelete() {
        phantomSpaceAt = -1;
        if (config.mode == FieldConfig.Mode.RAW) {
            editor.sendDeleteKey();
            selStart = selEnd = -1;
            notifyChanged();
            return;
        }
        if (config.mode == FieldConfig.Mode.STYLED && word.length() > 0) {
            int last = word.codePointBefore(word.length());
            word.setLength(word.length() - Character.charCount(last));
            if (word.length() == 0) {
                editor.setComposingText("");
                if (composingStart >= 0) selStart = selEnd = composingStart;
                composingStart = -1;
            } else {
                showComposing();
            }
            notifyChanged();
            return;
        }
        if (undoSlang()) return;
        clearUndo();
        deleteBeforeCursor();
        notifyChanged();
    }

    /**
     * Applies the pending slang to the current word immediately. The following space is
     * inserted only once the next character needs it, so punctuation attaches to the word.
     */
    public void pickSlang() {
        if (pendingSlang() == null) return;
        clearUndo();
        commitWord(Slang.FORCE, "");
        if (selStart >= 0) {
            phantomSpaceAt = selEnd;
        } else {
            commitWord(Slang.NONE, " ");
        }
        notifyChanged();
    }

    /** Reverts the last slang substitution. Returns false if none is revertible. */
    public boolean undoSlang() {
        if (!undoAvailable()) return false;
        CharSequence before = editor.getTextBeforeCursor(undoCommitted.length());
        if (before == null || !undoCommitted.contentEquals(before)) {
            clearUndo();
            return false;
        }
        editor.beginBatchEdit();
        editor.deleteSurroundingText(undoCommitted.length(), 0);
        editor.commitText(undoRevert);
        editor.endBatchEdit();
        if (selStart >= 0) {
            selStart = selEnd = selStart - undoCommitted.length() + undoRevert.length();
        }
        phantomSpaceAt = -1;
        clearUndo();
        notifyChanged();
        return true;
    }

    /** Called from {@code InputMethodService.onUpdateSelection}. */
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        int ns = Math.min(newSelStart, newSelEnd);
        int ne = Math.max(newSelStart, newSelEnd);
        int os = Math.min(oldSelStart, oldSelEnd);
        int oe = Math.max(oldSelStart, oldSelEnd);
        if (config.mode == FieldConfig.Mode.RAW) {
            selStart = ns;
            selEnd = ne;
            return;
        }
        if (word.length() > 0 && composingStart >= 0 && candidatesStart == composingStart
                && ns == ne && ns == candidatesEnd) {
            // A cursor at the end of a composing region that starts at this word's start is one
            // of this IME's own setComposingText states, in whatever order updates arrive.
            return;
        }
        if (selStart < 0) {
            // Positions were unknown; adopt them if consistent with the composing state.
            boolean consistent = word.length() == 0
                    || (candidatesStart >= 0 && ns == ne && ne == candidatesEnd);
            if (consistent) {
                if (word.length() > 0) {
                    // The update may be stale; expect the state after the latest keystroke.
                    composingStart = candidatesStart;
                    selStart = selEnd = composingStart + style.apply(word).length();
                } else {
                    selStart = ns;
                    selEnd = ne;
                }
                notifyChanged();
                return;
            }
        } else if (isBelatedExpectedUpdate(os, oe, ns, ne)) {
            return;
        }
        // The selection was changed by the user or the application.
        selStart = ns;
        selEnd = ne;
        phantomSpaceAt = -1;
        clearUndo();
        if (word.length() > 0) {
            word.setLength(0);
            composingStart = -1;
            editor.finishComposingText();
        }
        notifyChanged();
    }

    // ---------------------------------------------------------------- internals

    static boolean isWordCodePoint(int cp) {
        return Character.isLetterOrDigit(cp) || SlangDictionary.isApostrophe(cp);
    }

    /** Separators that join tokens (handles, paths, e-mail, compounds): no slang before them. */
    static boolean isJoiningSeparator(int cp) {
        switch (cp) {
            case '@': case '_': case '/': case '-': case '#': case '&': case '+': case '=':
            case '\\':
                return true;
            default:
                return false;
        }
    }

    private static boolean isTrailingPunctuation(int cp) {
        switch (cp) {
            case '.': case ',': case '!': case '?': case ';': case ':': case ')': case ']':
            case '}':
                return true;
            default:
                return false;
        }
    }

    /** Whether a character ends a preceding token, so that a word starting after it is free-standing. */
    static boolean isWordBoundary(int cp) {
        if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) return true;
        int type = Character.getType(cp);
        return type == Character.START_PUNCTUATION || type == Character.INITIAL_QUOTE_PUNCTUATION
                || cp == '"' || cp == 0xFF02;
    }

    private boolean isAttachedToPreviousText() {
        CharSequence before = editor.getTextBeforeCursor(2);
        if (before == null || before.length() == 0) return false;
        return !isWordBoundary(Character.codePointBefore(before, before.length()));
    }

    /** Whether a space is owed at the cursor; clears the pending state either way. */
    private boolean consumePhantomSpace() {
        boolean here = phantomSpaceAt >= 0 && selStart == phantomSpaceAt && selEnd == phantomSpaceAt;
        phantomSpaceAt = -1;
        return here;
    }

    /** Maps WIDE and STRIKE separators back to the ASCII forms TextUtils.getCapsMode expects. */
    static String unstyleSeparators(CharSequence text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '̶') continue;
            if (c == '　') {
                c = ' ';
            } else if (c >= '！' && c <= '～') {
                c = (char) (c - 0xFEE0);
            }
            out.append(c);
        }
        return out.toString();
    }

    private boolean isBelatedExpectedUpdate(int oldStart, int oldEnd, int newStart, int newEnd) {
        if (selStart == newStart && selEnd == newEnd) return true;
        if (selStart == oldStart && selEnd == oldEnd
                && (oldStart != newStart || oldEnd != newEnd)) {
            return false;
        }
        // The editor may still be reporting intermediate states of this IME's edits: accept a
        // cursor lying between the previously reported position and the expected one.
        return newStart == newEnd
                && (long) (newStart - oldStart) * (selStart - newStart) >= 0
                && (long) (newEnd - oldEnd) * (selEnd - newEnd) >= 0;
    }

    private void showComposing() {
        if (composingStart < 0 && selStart >= 0) composingStart = selStart;
        String shown = style.apply(word);
        editor.setComposingText(shown);
        if (composingStart >= 0) {
            selStart = selEnd = composingStart + shown.length();
        } else {
            selStart = selEnd = -1;
        }
    }

    /**
     * Commits the composing word (with slang according to {@code slang}) followed by
     * {@code separator}, both styled, replacing the composing region or the selection.
     */
    private void commitWord(Slang slang, String separator) {
        commitWord(slang, separator, true);
    }

    private void commitWord(Slang slang, String separator, boolean styleSeparator) {
        String raw = word.toString();
        String replacement = null;
        if (!raw.isEmpty() && !wordAttached
                && (slang == Slang.FORCE ? slangSuggestActive()
                        : slang == Slang.AUTO && slangAutoActive())) {
            replacement = SlangDictionary.lookup(raw);
        }
        String styledSep = styleSeparator ? style.apply(separator) : separator;
        String text = style.apply(replacement != null ? replacement : raw) + styledSep;
        int base = composingStart >= 0 ? composingStart : selStart;
        editor.commitText(text);
        word.setLength(0);
        wordAttached = false;
        composingStart = -1;
        selStart = selEnd = base >= 0 ? base + text.length() : -1;
        if (replacement != null && !text.isEmpty()) {
            undoCommitted = text;
            undoRevert = style.apply(raw) + styledSep;
            undoOriginalWord = raw;
            undoSeparator = separator;
            undoCursor = selEnd;
        }
    }

    private void commitPlain(String text) {
        int base = selStart;
        editor.commitText(text);
        selStart = selEnd = base >= 0 ? base + text.length() : -1;
    }

    private void deleteBeforeCursor() {
        boolean hasSelection = selStart >= 0 ? selStart != selEnd : hasSelectedText();
        if (hasSelection) {
            editor.sendDeleteKey();
            if (selStart >= 0) selEnd = selStart;
            return;
        }
        CharSequence before = editor.getTextBeforeCursor(GRAPHEME_LOOKBEHIND);
        if (before == null) {
            editor.sendDeleteKey();
            selStart = selEnd = -1;
            return;
        }
        if (before.length() == 0) {
            // Nothing to delete in the text model; some editors act on the key event itself.
            editor.sendDeleteKey();
            return;
        }
        int n = lastGraphemeLength(before.toString());
        editor.deleteSurroundingText(n, 0);
        if (selStart >= 0) selStart = selEnd = Math.max(0, selStart - n);
    }

    private boolean hasSelectedText() {
        CharSequence sel = editor.getSelectedText();
        return sel != null && sel.length() > 0;
    }

    static int lastGraphemeLength(String text) {
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(text);
        int end = text.length();
        int start = it.preceding(end);
        if (start == BreakIterator.DONE || start >= end) {
            return Character.charCount(text.codePointBefore(end));
        }
        // ICU 58 (API 26-27) splits emoji ZWJ sequences whose first element carries VS16;
        // join clusters across a ZWJ that precedes a pictograph.
        while (start > 0 && text.charAt(start - 1) == '‍'
                && Character.getType(text.codePointAt(start)) == Character.OTHER_SYMBOL) {
            int p = it.preceding(start);
            if (p == BreakIterator.DONE) break;
            start = p;
        }
        return end - start;
    }

    private boolean undoAvailable() {
        if (undoCommitted == null || config.mode != FieldConfig.Mode.STYLED || word.length() > 0) {
            return false;
        }
        return selStart < 0 || (selStart == undoCursor && selEnd == undoCursor);
    }

    private void clearUndo() {
        undoCommitted = null;
        undoRevert = null;
        undoOriginalWord = null;
        undoSeparator = null;
        undoCursor = -1;
    }

    private void notifyChanged() {
        if (listener != null) listener.onInputStateChanged();
    }
}
