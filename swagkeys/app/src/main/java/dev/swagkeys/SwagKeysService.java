package dev.swagkeys;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.IBinder;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

/** The input method. Bridges {@link KeyboardView} events to {@link InputLogic}. */
public final class SwagKeysService extends InputMethodService
        implements KeyboardView.Listener, InputLogic.Listener {

    private Prefs prefs;
    private InputLogic logic;
    private KeyboardView keyboard;
    private boolean fontsDirty = true;
    /** EditorInfo of the input currently started, to recognize IME-internal restarts. */
    private EditorInfo startedInfo;
    /** Undo state at the previous update, to detect a slang substitution as it happens. */
    private String lastUndoWord;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sp, key) -> onPreferenceChanged(key);

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        logic = new InputLogic(new ConnectionEditor(), this);
        logic.setStyle(prefs.style());
        logic.setSlangEnabled(prefs.slang());
        prefs.raw().registerOnSharedPreferenceChangeListener(prefListener);
        EmojiData.preload(this);
    }

    @Override
    public void onDestroy() {
        prefs.raw().unregisterOnSharedPreferenceChangeListener(prefListener);
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        keyboard = new KeyboardView(this);
        keyboard.setListener(this);
        fontsDirty = true;
        applyAppearance();
        return keyboard;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // Keep the app's own field visible so styled composing text is shown in place.
        return false;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        // InputMethodService restarts the same EditorInfo instance after a configuration change
        // (resetStateForNewConfiguration). The connection, composing span and tracked selection
        // are still valid then; resetting would let the next keystroke replace the composing word.
        if (restarting && attribute != null && attribute == startedInfo) return;
        startedInfo = attribute;
        logic.startInput(FieldConfig.from(attribute));
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboard == null) return;
        applyAppearance();
        FieldConfig config = logic.config();
        keyboard.setRecentEmojis(prefs.recentEmojis());
        keyboard.setField(config.layout, config.enterKeyAction, config.enterLabel);
        onInputStateChanged();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        logic.finishInput();
        if (keyboard != null) keyboard.reset();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onFinishInput() {
        startedInfo = null;
        logic.finishInput();
        super.onFinishInput();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        logic.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        applySystemBarColors();
    }

    // ---------------------------------------------------------------- appearance

    private void applyAppearance() {
        if (keyboard == null) return;
        keyboard.setPalette(Palette.resolve(this, prefs.theme())
                .withAccent(Palette.accentColor(prefs.accent())));
        keyboard.setFeedback(prefs.haptics(), prefs.sound());
        // Also off when the system disables animations (Remove animations / animator scale 0).
        keyboard.setAnimations(prefs.animations() && ValueAnimator.areAnimatorsEnabled());
        keyboard.setNumberRow(prefs.numberRow());
        if (fontsDirty) {
            Typeface[] faces = Fonts.resolve(this, prefs);
            keyboard.setFonts(faces[0], faces[1]);
            fontsDirty = false;
        }
        applySystemBarColors();
    }

    @SuppressWarnings("deprecation")
    private void applySystemBarColors() {
        Window window = getWindow() == null ? null : getWindow().getWindow();
        if (window == null || keyboard == null) return;
        Palette p = keyboard.palette();
        // On API 35+ the IME window is edge-to-edge and KeyboardView draws behind the navigation
        // bar itself; the color still matters for the area DecorView paints, without a scrim.
        window.setNavigationBarColor(p.background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = window.getInsetsController();
            if (c != null) {
                c.setSystemBarsAppearance(p.dark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            flags = p.dark ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    : flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(flags);
        }
    }

    private void onPreferenceChanged(String key) {
        if (key == null) return;
        switch (key) {
            case Prefs.KEY_STYLE:
                logic.setStyle(prefs.style());
                break;
            case Prefs.KEY_SLANG:
                logic.setSlangEnabled(prefs.slang());
                break;
            case Prefs.KEY_FONT:
            case Prefs.KEY_FONT_VERSION:
                fontsDirty = true;
                applyAppearance();
                break;
            case Prefs.KEY_THEME:
            case Prefs.KEY_HAPTICS:
            case Prefs.KEY_SOUND:
            case Prefs.KEY_ACCENT:
            case Prefs.KEY_ANIMATIONS:
            case Prefs.KEY_NUMBER_ROW:
                applyAppearance();
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------- InputLogic.Listener

    @Override
    public void onInputStateChanged() {
        if (keyboard == null) return;
        int slangMode = logic.slangAutoActive() ? KeyboardView.SLANG_AUTO
                : logic.slangSuggestActive() ? KeyboardView.SLANG_SUGGEST : KeyboardView.SLANG_OFF;
        String undo = logic.undoableWord();
        keyboard.setStripState(logic.style(), logic.stylesActive(), logic.isSlangEnabled(),
                slangMode, logic.pendingSlang(), undo);
        if (undo != null && lastUndoWord == null) keyboard.playSlangAnimation(logic.undoReplacement());
        lastUndoWord = undo;
        keyboard.setAutoCaps(logic.shouldAutoCapitalize());
    }

    // ---------------------------------------------------------------- KeyboardView.Listener

    @Override
    public void onKey(int code) {
        switch (code) {
            case Key.CODE_DELETE:
                logic.onDelete();
                break;
            case Key.CODE_ENTER:
                logic.onEnter();
                break;
            default:
                if (code > 0) logic.onCodePoint(code);
                break;
        }
    }

    @Override
    public void onEmoji(String emoji) {
        logic.onEmoji(emoji);
        // Recents are typing history: not kept for passwords or when the app opts out.
        if (!allowsLearning(getCurrentInputEditorInfo())) return;
        prefs.addRecentEmoji(emoji);
        if (keyboard != null) keyboard.setRecentEmojis(prefs.recentEmojis());
    }

    static boolean allowsLearning(EditorInfo info) {
        if (info == null) return true;
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return false;
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        return !(cls == InputType.TYPE_CLASS_TEXT
                && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onSwitchKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (shouldOfferSwitchingToNextInputMethod() && switchToNextInputMethod(false)) return;
        } else {
            Window window = getWindow() == null ? null : getWindow().getWindow();
            IBinder token = window == null ? null : window.getAttributes().token;
            if (token != null && imm != null && imm.shouldOfferSwitchingToNextInputMethod(token)
                    && imm.switchToNextInputMethod(token, false)) {
                return;
            }
        }
        // No other switching-aware keyboard to rotate to: let the user pick one.
        if (imm != null) imm.showInputMethodPicker();
    }

    @Override
    public void onCursorMove(int steps) {
        logic.moveCursor(steps);
    }

    @Override
    public void onDeleteWords(int words) {
        logic.deleteWords(words);
    }

    @Override
    public void onSpaceLongPress() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    @Override
    public void onStyleSelected(TextStyle style) {
        logic.setStyle(style);
        prefs.setStyle(style);
    }

    @Override
    public void onSlangToggle() {
        boolean on = !logic.isSlangEnabled();
        logic.setSlangEnabled(on);
        prefs.setSlang(on);
    }

    @Override
    public void onSuggestionTap() {
        if (logic.undoableWord() != null) {
            logic.undoSlang();
        } else {
            logic.pickSlang();
        }
    }

    @Override
    public void onOpenSettings() {
        Intent intent = new Intent(this, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        requestHideSelf(0);
        startActivity(intent);
    }

    // ---------------------------------------------------------------- editor bridge

    /** {@link Editor} over the current InputConnection; every call tolerates a null connection. */
    private final class ConnectionEditor implements Editor {
        @Override
        public CharSequence getTextBeforeCursor(int n) {
            InputConnection ic = getCurrentInputConnection();
            return ic == null ? null : ic.getTextBeforeCursor(n, 0);
        }

        @Override
        public CharSequence getSelectedText() {
            InputConnection ic = getCurrentInputConnection();
            return ic == null ? null : ic.getSelectedText(0);
        }

        @Override
        public void setComposingText(CharSequence text) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.setComposingText(text, 1);
        }

        @Override
        public void commitText(CharSequence text) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(text, 1);
        }

        @Override
        public void finishComposingText() {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
        }

        @Override
        public void deleteSurroundingText(int beforeLength, int afterLength) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.deleteSurroundingText(beforeLength, afterLength);
        }

        @Override
        public void sendDeleteKey() {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }

        @Override
        public void sendKey(int keyCode) {
            sendDownUpKeyEvents(keyCode);
        }

        @Override
        public void sendKeyChar(char c) {
            SwagKeysService.this.sendKeyChar(c);
        }

        @Override
        public int getCursorCapsMode(int reqModes) {
            InputConnection ic = getCurrentInputConnection();
            return ic == null ? 0 : ic.getCursorCapsMode(reqModes);
        }

        @Override
        public int capsModeOf(CharSequence text, int reqModes) {
            return TextUtils.getCapsMode(text, text.length(), reqModes);
        }

        @Override
        public void performEditorAction(int actionId) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.performEditorAction(actionId);
        }

        @Override
        public void beginBatchEdit() {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.beginBatchEdit();
        }

        @Override
        public void endBatchEdit() {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.endBatchEdit();
        }
    }
}
