package dev.swagkeys;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

/** Per-field input behavior derived from {@link EditorInfo}. Plain data; no Android calls. */
public final class FieldConfig {

    public enum Mode {
        /** Styles and slang apply; the current word is held as composing text. */
        STYLED,
        /** Text is committed verbatim (passwords, URIs, e-mail, numbers). */
        PLAIN,
        /** The editor has no text model (TYPE_NULL); input is sent as key events. */
        RAW
    }

    public enum Layout { TEXT, NUMBER, PHONE, DATETIME }

    public final Mode mode;
    public final Layout layout;
    public final boolean slangAllowed;
    /** inputType passed to getCursorCapsMode; 0 disables auto-capitalization. */
    public final int capsInputType;
    /** IME_ACTION_* to perform on Enter, or {@link EditorInfo#IME_ACTION_NONE} to insert '\n'. */
    public final int enterAction;
    public final int initialSelStart;
    public final int initialSelEnd;

    public FieldConfig(Mode mode, Layout layout, boolean slangAllowed, int capsInputType,
            int enterAction, int initialSelStart, int initialSelEnd) {
        this.mode = mode;
        this.layout = layout;
        this.slangAllowed = slangAllowed;
        this.capsInputType = capsInputType;
        this.enterAction = enterAction;
        this.initialSelStart = initialSelStart;
        this.initialSelEnd = initialSelEnd;
    }

    public static FieldConfig defaults() {
        return new FieldConfig(Mode.STYLED, Layout.TEXT, true, 0, EditorInfo.IME_ACTION_NONE, -1, -1);
    }

    public static FieldConfig from(EditorInfo info) {
        if (info == null) return defaults();
        int type = info.inputType;
        int cls = type & InputType.TYPE_MASK_CLASS;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        int flags = type & InputType.TYPE_MASK_FLAGS;

        Mode mode;
        Layout layout = Layout.TEXT;
        boolean slang = true;
        int caps = 0;

        if (cls == InputType.TYPE_NULL) {
            mode = Mode.RAW;
            slang = false;
        } else if (cls == InputType.TYPE_CLASS_NUMBER) {
            mode = Mode.PLAIN;
            layout = Layout.NUMBER;
            slang = false;
        } else if (cls == InputType.TYPE_CLASS_PHONE) {
            mode = Mode.PLAIN;
            layout = Layout.PHONE;
            slang = false;
        } else if (cls == InputType.TYPE_CLASS_DATETIME) {
            mode = Mode.PLAIN;
            layout = Layout.DATETIME;
            slang = false;
        } else {
            boolean literal = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_URI
                    || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    || variation == InputType.TYPE_TEXT_VARIATION_FILTER;
            if (literal) {
                mode = Mode.PLAIN;
                slang = false;
            } else {
                mode = Mode.STYLED;
                slang = (flags & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) == 0;
                if ((flags & (InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)) != 0) {
                    caps = type;
                }
            }
        }

        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean noEnterAction = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        // TextView sets IME_FLAG_NO_ENTER_ACTION on multi-line fields itself.
        int enter = (noEnterAction || action == EditorInfo.IME_ACTION_UNSPECIFIED)
                ? EditorInfo.IME_ACTION_NONE : action;
        return new FieldConfig(mode, layout, slang, caps, enter,
                info.initialSelStart, info.initialSelEnd);
    }
}
