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
    /** Slang may be offered in the strip for this field. */
    public final boolean slangSuggest;
    /** Slang is applied automatically when a word is completed (implies slangSuggest). */
    public final boolean slangAuto;
    /** inputType passed to getCursorCapsMode; 0 disables auto-capitalization. */
    public final int capsInputType;
    /** Whether Enter performs an editor action instead of inserting '\n'. */
    public final boolean enterIsAction;
    /** Value passed to performEditorAction: actionId if the field has an actionLabel, else IME_ACTION_*. */
    public final int enterActionId;
    /** IME_ACTION_* used to choose the Enter key's label or icon; IME_ACTION_NONE for newline. */
    public final int enterKeyAction;
    /** Custom Enter key label from EditorInfo.actionLabel, or null. */
    public final String enterLabel;
    public final int initialSelStart;
    public final int initialSelEnd;

    public FieldConfig(Mode mode, Layout layout, boolean slangSuggest, boolean slangAuto,
            int capsInputType, boolean enterIsAction, int enterActionId, int enterKeyAction,
            String enterLabel, int initialSelStart, int initialSelEnd) {
        this.mode = mode;
        this.layout = layout;
        this.slangSuggest = slangSuggest;
        this.slangAuto = slangSuggest && slangAuto;
        this.capsInputType = capsInputType;
        this.enterIsAction = enterIsAction;
        this.enterActionId = enterActionId;
        this.enterKeyAction = enterKeyAction;
        this.enterLabel = enterLabel;
        this.initialSelStart = initialSelStart;
        this.initialSelEnd = initialSelEnd;
    }

    /**
     * A text-layout field. {@code slang} enables both suggested and automatic slang;
     * {@code enterAction} of IME_ACTION_NONE means Enter inserts a newline.
     */
    public static FieldConfig text(Mode mode, boolean slang, int capsInputType, int enterAction,
            int initialSelStart, int initialSelEnd) {
        boolean isAction = enterAction != EditorInfo.IME_ACTION_NONE;
        return new FieldConfig(mode, Layout.TEXT, slang, slang, capsInputType, isAction,
                enterAction, enterAction, null, initialSelStart, initialSelEnd);
    }

    public static FieldConfig defaults() {
        return text(Mode.STYLED, true, 0, EditorInfo.IME_ACTION_NONE, -1, -1);
    }

    public static FieldConfig from(EditorInfo info) {
        if (info == null) return defaults();
        int type = info.inputType;
        int cls = type & InputType.TYPE_MASK_CLASS;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        int flags = type & InputType.TYPE_MASK_FLAGS;
        boolean multiLine = cls == InputType.TYPE_CLASS_TEXT
                && (flags & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;

        Mode mode;
        Layout layout = Layout.TEXT;
        boolean suggest = false;
        boolean auto = false;
        int caps = 0;

        if (cls == InputType.TYPE_NULL) {
            mode = Mode.RAW;
        } else if (cls == InputType.TYPE_CLASS_NUMBER) {
            mode = Mode.PLAIN;
            layout = Layout.NUMBER;
        } else if (cls == InputType.TYPE_CLASS_PHONE) {
            mode = Mode.PLAIN;
            layout = Layout.PHONE;
        } else if (cls == InputType.TYPE_CLASS_DATETIME) {
            mode = Mode.PLAIN;
            layout = Layout.DATETIME;
        } else {
            boolean literal = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_URI
                    || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    || variation == InputType.TYPE_TEXT_VARIATION_FILTER;
            mode = literal ? Mode.PLAIN : Mode.STYLED;
            if (!literal) {
                boolean autoCorrect = (flags & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0;
                suggest = (flags & (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)) == 0;
                // As in AOSP LatinIME, single-line fields are only auto-corrected when they ask
                // for it; names, addresses and searches would otherwise be rewritten silently.
                auto = (autoCorrect || multiLine)
                        && !(variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT && !autoCorrect)
                        && variation != InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                        && variation != InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS;
            }
            if ((flags & (InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                    | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)) != 0
                    && variation != InputType.TYPE_TEXT_VARIATION_PASSWORD
                    && variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
                caps = type;
            }
        }

        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean noEnterAction = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        boolean hasLabel = info.actionLabel != null;
        // Matches InputMethodService.sendDefaultEditorAction and LatinIME: any action except
        // NONE is performed, a custom label sends its actionId. Multi-line editors that leave
        // the action unspecified (TextView itself sets NO_ENTER_ACTION) get a newline.
        boolean isAction = !noEnterAction && (hasLabel || (action != EditorInfo.IME_ACTION_NONE
                && !(action == EditorInfo.IME_ACTION_UNSPECIFIED && multiLine)));
        int actionId = !isAction ? EditorInfo.IME_ACTION_NONE : hasLabel ? info.actionId : action;
        int keyAction = isAction ? action : EditorInfo.IME_ACTION_NONE;
        String label = isAction && hasLabel ? info.actionLabel.toString() : null;
        return new FieldConfig(mode, layout, suggest, auto, caps, isAction, actionId, keyAction,
                label, info.initialSelStart, info.initialSelEnd);
    }
}
