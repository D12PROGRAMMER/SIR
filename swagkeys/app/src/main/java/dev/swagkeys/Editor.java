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

    /** Returns the selected text, or {@code null} if none or unavailable. */
    CharSequence getSelectedText();

    void setComposingText(CharSequence text);

    void commitText(CharSequence text);

    void finishComposingText();

    void deleteSurroundingText(int beforeLength, int afterLength);

    /** Sends a KEYCODE_DEL down/up pair. */
    void sendDeleteKey();

    /** Sends a character as key events where possible (for TYPE_NULL editors). */
    void sendKeyChar(char c);

    /** See {@link android.view.inputmethod.InputConnection#getCursorCapsMode(int)}. */
    int getCursorCapsMode(int reqModes);

    void performEditorAction(int actionId);

    void beginBatchEdit();

    void endBatchEdit();
}
