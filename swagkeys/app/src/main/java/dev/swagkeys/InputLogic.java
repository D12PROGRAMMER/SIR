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

    /** IME_ACTION_NONE; duplicated so the class has no static dependency on EditorInfo. */
    private static final int ACTION_NONE = 1;
    private static final int GRAPHEME_LOOKBEHIND = 64;

    private final Editor editor;
    private final Listener listener;

    private FieldConfig config = FieldConfig.defaults();
    private TextStyle style = TextStyle.NORMAL;
    private boolean slangEnabled = true;

    /** Unstyled code points of the word currently held as composing text. */
    private final StringBuilder word = new StringBuilder();
    /** Absolute offset where the composing text starts, or -1 if unknown / not composing. */
    private int composingStart = -1;
    /** Expected selection after this IME's last edit; -1 when unknown. */
    private int selStart = -1;
    private int selEnd = -1;

    /** Last slang substitution, revertible by Delete or the strip while the cursor is unmoved. */
    private String undoCommitted;
    private String undoRevert;
    private String undoOriginalWord;
    private int undoCursor = -1;

    public InputLogic(Editor editor, Listener listener) {
        this.editor = editor;
        this.listener = listener;
    }

    // ---------------------------------------------------------------- configuration

    public void startInput(FieldConfig config) {
        this.config = config;
        word.setLength(0);
        composingStart = -1;
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

    /** True if slang substitution applies to the current field. */
    public boolean slangActive() {
        return slangEnabled && config.slangAllowed && config.mode == FieldConfig.Mode.STYLED;
    }

    // ---------------------------------------------------------------- queries for the UI

    public String composingWord() {
        return word.toString();
    }

    /** Slang the current word would be replaced with, or null. */
    public String pendingSlang() {
        if (!slangActive() || word.length() == 0) return null;
        return SlangDictionary.lookup(word.toString());
    }

    /** The original word restorable by {@link #undoSlang()}, or null. */
    public String undoableWord() {
        return undoAvailable() ? undoOriginalWord : null;
    }

    /** Whether the next letter should be capitalized according to the editor's caps mode. */
    public boolean shouldAutoCapitalize() {
        if (config.capsInputType == 0 || word.length() > 0) return false;
        return editor.getCursorCapsMode(config.capsInputType) != 0;
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
                clearUndo();
                if (isWordCodePoint(cp)) {
                    word.appendCodePoint(cp);
                    showComposing();
                } else {
                    commitWord(true, new String(Character.toChars(cp)));
                }
                break;
        }
        notifyChanged();
    }

    public void onEnter() {
        if (config.enterAction != ACTION_NONE) {
            if (config.mode == FieldConfig.Mode.STYLED && word.length() > 0) {
                commitWord(true, "");
            }
            clearUndo();
            editor.performEditorAction(config.enterAction);
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

    /** Applies the pending slang to the current word immediately, followed by a space. */
    public void pickSlang() {
        if (pendingSlang() == null) return;
        clearUndo();
        commitWord(true, " ");
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
        if (selStart < 0) {
            // Positions were unknown; adopt the editor's values if they are consistent with
            // the composing state, otherwise treat this as an external change.
            boolean consistent = word.length() == 0
                    || (candidatesStart >= 0 && ns == ne && ne == candidatesEnd);
            selStart = ns;
            selEnd = ne;
            if (consistent) {
                if (word.length() > 0) composingStart = candidatesStart;
                notifyChanged();
                return;
            }
        } else if (isBelatedExpectedUpdate(os, oe, ns, ne)) {
            return;
        }
        // The selection was changed by the user or the application.
        selStart = ns;
        selEnd = ne;
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
     * Commits the composing word (with slang if {@code withSlang}) followed by {@code separator},
     * both styled, replacing the composing region or the selection.
     */
    private void commitWord(boolean withSlang, String separator) {
        String raw = word.toString();
        String replacement = (withSlang && slangActive() && !raw.isEmpty())
                ? SlangDictionary.lookup(raw) : null;
        String styledSep = style.apply(separator);
        String text = style.apply(replacement != null ? replacement : raw) + styledSep;
        int base = composingStart >= 0 ? composingStart : selStart;
        editor.commitText(text);
        word.setLength(0);
        composingStart = -1;
        selStart = selEnd = base >= 0 ? base + text.length() : -1;
        if (replacement != null && !text.isEmpty()) {
            undoCommitted = text;
            undoRevert = style.apply(raw) + styledSep;
            undoOriginalWord = raw;
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
        undoCursor = -1;
    }

    private void notifyChanged() {
        if (listener != null) listener.onInputStateChanged();
    }
}
