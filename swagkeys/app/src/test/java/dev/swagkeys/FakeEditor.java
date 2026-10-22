package dev.swagkeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulates an editor with BaseInputConnection replace semantics. Selection updates are queued
 * (as the framework delivers them asynchronously) and delivered with {@link #deliverUpdates()}.
 */
final class FakeEditor implements Editor {
    final StringBuilder text = new StringBuilder();
    int selStart;
    int selEnd;
    int compStart = -1;
    int compEnd = -1;
    final List<Integer> actions = new ArrayList<>();
    InputLogic logic;
    boolean capsAlways;

    private int batchDepth;
    private int reportedSelStart;
    private int reportedSelEnd;
    private final List<int[]> pending = new ArrayList<>();

    FakeEditor(String initial) {
        text.append(initial);
        selStart = selEnd = initial.length();
        reportedSelStart = reportedSelEnd = selStart;
    }

    FieldConfig config(FieldConfig.Mode mode, boolean slang, int capsInputType, int enterAction) {
        return FieldConfig.text(mode, slang, capsInputType, enterAction, selStart, selEnd);
    }

    String value() {
        return text.toString();
    }

    String composing() {
        return compStart < 0 ? null : text.substring(compStart, compEnd);
    }

    void deliverUpdates() {
        List<int[]> copy = new ArrayList<>(pending);
        pending.clear();
        for (int[] u : copy) logic.onUpdateSelection(u[0], u[1], u[2], u[3], u[4], u[5]);
    }

    /** Delivers only the oldest queued update. */
    void deliverOne() {
        if (pending.isEmpty()) return;
        int[] u = pending.remove(0);
        logic.onUpdateSelection(u[0], u[1], u[2], u[3], u[4], u[5]);
    }

    /** Delivers every queued update except the most recent (an editor lagging one keystroke). */
    void deliverAllButLast() {
        while (pending.size() > 1) deliverOne();
    }

    /** User taps to place the cursor (composing span is left in place, as TextView does). */
    void userMoveCursor(int pos) {
        selStart = selEnd = pos;
        changed();
    }

    void userSelect(int start, int end) {
        selStart = start;
        selEnd = end;
        changed();
    }

    /** The application replaces the whole text (e.g. clears the field after sending). */
    void appSetText(String s) {
        text.setLength(0);
        text.append(s);
        compStart = compEnd = -1;
        selStart = selEnd = s.length();
        changed();
    }

    private void changed() {
        if (batchDepth > 0) return;
        pending.add(new int[] {reportedSelStart, reportedSelEnd, selStart, selEnd, compStart, compEnd});
        reportedSelStart = selStart;
        reportedSelEnd = selEnd;
    }

    private void replace(CharSequence t, boolean keepComposing) {
        int a;
        int b;
        if (compStart >= 0) {
            a = compStart;
            b = compEnd;
        } else {
            a = selStart;
            b = selEnd;
        }
        text.replace(a, b, t.toString());
        selStart = selEnd = a + t.length();
        if (keepComposing && t.length() > 0) {
            compStart = a;
            compEnd = a + t.length();
        } else {
            compStart = compEnd = -1;
        }
        changed();
    }

    @Override
    public CharSequence getTextBeforeCursor(int n) {
        return text.substring(Math.max(0, selStart - n), selStart);
    }

    @Override
    public CharSequence getSelectedText() {
        return selStart == selEnd ? null : text.substring(selStart, selEnd);
    }

    @Override
    public void setComposingText(CharSequence t) {
        replace(t, true);
    }

    @Override
    public void commitText(CharSequence t) {
        replace(t, false);
    }

    @Override
    public void finishComposingText() {
        compStart = compEnd = -1;
        changed();
    }

    @Override
    public void deleteSurroundingText(int before, int after) {
        int a = Math.max(0, selStart - before);
        int e = Math.min(text.length(), selEnd + after);
        text.delete(selEnd, e);
        text.delete(a, selStart);
        int removed = selStart - a;
        selStart -= removed;
        selEnd -= removed;
        changed();
    }

    @Override
    public void sendDeleteKey() {
        if (selStart != selEnd) {
            text.delete(selStart, selEnd);
            selEnd = selStart;
        } else if (selStart > 0) {
            text.deleteCharAt(selStart - 1);
            selStart = selEnd = selStart - 1;
        }
        compStart = compEnd = -1;
        changed();
    }

    @Override
    public void sendKeyChar(char c) {
        compStart = compEnd = -1;
        replace(String.valueOf(c), false);
    }

    @Override
    public int getCursorCapsMode(int reqModes) {
        return capsModeOf(text.substring(0, selStart), reqModes);
    }

    /** Simplified TextUtils.getCapsMode: sentence start after ". ", "! ", "? " or at the start. */
    @Override
    public int capsModeOf(CharSequence textBefore, int reqModes) {
        if (capsAlways) return reqModes;
        String all = textBefore.toString();
        String before = all.replaceAll("[ \\t]+$", "");
        boolean sentenceStart = before.isEmpty() || before.endsWith(".") || before.endsWith("!")
                || before.endsWith("?") || before.endsWith("\n");
        boolean afterSpace = all.isEmpty() || all.endsWith(" ") || all.endsWith("\t")
                || all.endsWith("\n");
        return sentenceStart && afterSpace ? 0x4000 : 0;
    }

    @Override
    public void performEditorAction(int actionId) {
        actions.add(actionId);
    }

    @Override
    public void beginBatchEdit() {
        batchDepth++;
    }

    @Override
    public void endBatchEdit() {
        if (--batchDepth == 0) changed();
    }
}
