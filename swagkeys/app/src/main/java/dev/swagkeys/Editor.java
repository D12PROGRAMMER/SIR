package dev.swagkeys;

/**
 * The subset of {@link android.view.inputmethod.InputConnection} operations that
 * {@link InputLogic} uses. Abstracted so that the logic runs against a simulated editor in
 * JVM unit tests. All text-inserting calls use a new cursor position of 1 (cursor placed after
 * the inserted text).
 */
public interface Editor {
    /** Returns up to {@code n} chars before the cursor, or {@code null} if unavailable. */
    CharSequence getTextBeforeCursor(int n);

    /** Returns up to {@code n} chars after the cursor, or {@code null} if unavailable. */
    CharSequence getTextAfterCursor(int n);

    /** Returns the selected text, or {@code null} if none or unavailable. */
    CharSequence getSelectedText();

    /**
     * The editor's current selection as {start, end}, read synchronously, so it reflects every
     * call made so far (unlike onUpdateSelection reports, which can lag); {@code null} if the
     * editor cannot tell.
     */
    int[] getSelection();

    /**
     * Whether the editor's styled text marks the {@code length} chars right before the cursor
     * as its composing region: 1 if so, 0 if the styled text marks no such region, -1 if the
     * editor gives no styled text (or no spans that show composing).
     */
    int composingSpanBeforeCursor(int length);

    void setComposingText(CharSequence text);

    void commitText(CharSequence text);

    void finishComposingText();

    void deleteSurroundingText(int beforeLength, int afterLength);

    /** Sends a KEYCODE_DEL down/up pair. */
    void sendDeleteKey();

    /** Sends a down/up pair for {@code keyCode} (e.g. KEYCODE_DPAD_LEFT). */
    void sendKey(int keyCode);

    /** Sends a character as key events where possible (for TYPE_NULL editors). */
    void sendKeyChar(char c);

    /** See {@link android.view.inputmethod.InputConnection#getCursorCapsMode(int)}. */
    int getCursorCapsMode(int reqModes);

    /** {@link android.text.TextUtils#getCapsMode} evaluated at the end of {@code text}. */
    int capsModeOf(CharSequence text, int reqModes);

    void performEditorAction(int actionId);

    void beginBatchEdit();

    void endBatchEdit();
}
