package dev.swagkeys;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.List;

/**
 * Canvas-drawn keyboard: a suggestion/style strip above four key rows, or the style picker in
 * place of the rows. All drawing uses the dot-matrix typeface and {@link DotIcon} glyphs.
 */
public final class KeyboardView extends View {

    public interface Listener {
        /** A code point, or {@link Key#CODE_DELETE} or {@link Key#CODE_ENTER}. */
        void onKey(int code);

        /** An emoji (one or more code points) picked in the emoji panel. */
        void onEmoji(String emoji);

        void onSpaceLongPress();

        void onStyleSelected(TextStyle style);

        void onSlangToggle();

        void onSuggestionTap();

        void onOpenSettings();
    }

    public enum Shift { OFF, ON, LOCKED }

    /** Slang indicator states for {@link #setStripState}. */
    public static final int SLANG_OFF = 0;
    public static final int SLANG_SUGGEST = 1;
    public static final int SLANG_AUTO = 2;

    private static final long LONG_PRESS_MS = 350;
    private static final long LONG_PRESS_FUNCTION_MS = 500;
    private static final long REPEAT_START_MS = 400;
    private static final long REPEAT_INTERVAL_MS = 50;
    private static final long DOUBLE_TAP_MS = 350;
    private static final long NO_TAP = -1;

    private static final int REGION_NONE = 0;
    private static final int REGION_KEY = 1;
    private static final int REGION_CHIP = 2;
    private static final int REGION_SUGGESTION = 3;
    private static final int REGION_SWAG = 4;
    private static final int REGION_GEAR = 5;
    private static final int REGION_TILE = 6;
    private static final int REGION_EMOJI = 7;
    private static final int REGION_EMOJI_TAB = 8;
    private static final int REGION_EMOJI_ABC = 9;
    private static final int REGION_EMOJI_DEL = 10;

    private static final String RECENT_EMOJI_ICON = "\uD83D\uDD58"; // U+1F558 clock face nine

    private static final String PANEL_SAMPLE = "swag";

    private final class Pointer {
        final int id;
        int region;
        Key key;
        int tile = -1;
        boolean consumed;
        /** Long-press code point shown in the preview after a long press fired, else 0. */
        int longPressShown;
        Runnable longPress;

        Pointer(int id) {
            this.id = id;
        }
    }

    private Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager audio;

    private Palette palette = Palette.DARK;
    private Typeface keyFont = Typeface.MONOSPACE;
    private Typeface uiFont = Typeface.MONOSPACE;
    private boolean haptics = true;
    private boolean sound = false;

    // Strip state
    private TextStyle style = TextStyle.NORMAL;
    private boolean stylesActive = true;
    private boolean slangOn = true;
    /** SLANG_OFF: not applicable here; SLANG_SUGGEST: strip only; SLANG_AUTO: automatic. */
    private int slangMode = SLANG_AUTO;
    private String pendingSlang;
    private String undoWord;
    private boolean stylePanelOpen;

    // Layout state
    private KeyboardLayouts.Mode mode = KeyboardLayouts.Mode.LETTERS;
    private FieldConfig.Layout fieldLayout = FieldConfig.Layout.TEXT;
    private int enterAction = EditorInfo.IME_ACTION_NONE;
    private String enterLabel;
    private List<KeyboardLayouts.Row> rows;
    private boolean typedInSymbols;

    // Shift state
    private Shift shift = Shift.OFF;
    private boolean shiftAuto;
    private boolean autoCapsWanted;
    /** Time of the last shift tap that may start a double tap, or NO_TAP. */
    private long lastShiftTapTime = NO_TAP;
    private int shiftPointerId = -1;
    private boolean shiftChordTyped;

    // Geometry (px)
    private final float density;
    private float stripHeight;
    private float rowHeight;
    private float padTop;
    private float padBottom;
    private float padH;
    private float keyInsetH;
    private float keyInsetV;
    private float radius;
    private int insetBottom;
    private float keysTop;
    private final RectF chipRect = new RectF();
    private final RectF suggestionRect = new RectF();
    private final RectF swagRect = new RectF();
    private final RectF gearRect = new RectF();
    private final RectF[] tileRects = new RectF[TextStyle.values().length];

    // Emoji panel: sections laid out as one vertically scrolling grid, a tab per section.
    private boolean emojiPanelOpen;
    private List<String> recentEmojis = new ArrayList<>();
    /** Grid cells; each section is padded with nulls to whole rows. */
    private final List<String> emojiCells = new ArrayList<>();
    private final List<String> emojiTabIcons = new ArrayList<>();
    private final List<Integer> emojiTabNames = new ArrayList<>();
    private final List<Integer> emojiTabRows = new ArrayList<>();
    private final List<RectF> emojiTabRects = new ArrayList<>();
    private final RectF emojiGridRect = new RectF();
    private final Key emojiAbcKey = Key.fn(Key.CODE_MODE_ALPHA, "ABC", null, 1.5f, Key.Type.FUNCTION);
    private final Key emojiDelKey = Key.fn(Key.CODE_DELETE, null, DotIcon.DELETE, 1.5f, Key.Type.DELETE);
    private int emojiCols = 8;
    private float emojiCellW = 1;
    private float emojiCellH = 1;
    private float emojiScrollY;
    private final OverScroller emojiScroller;
    private VelocityTracker emojiVelocity;
    private boolean emojiDragging;
    private float emojiDownY;
    private float emojiLastY;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;
    private final ColorMatrixColorFilter greyscale;

    // Touch
    private final SparseArray<Pointer> pointers = new SparseArray<>();
    private int stripPointerId = -1;
    private Runnable repeatRunnable;

    // Paint
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();

    public KeyboardView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        emojiScroller = new OverScroller(context);
        ColorMatrix desaturate = new ColorMatrix();
        desaturate.setSaturation(0f);
        greyscale = new ColorMatrixColorFilter(desaturate);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f * density);
        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < tileRects.length; i++) tileRects[i] = new RectF();
        setContentDescription(context.getString(R.string.ime_name));
        setHapticFeedbackEnabled(true);
        updateDimensions();
        rebuildLayout();
        rebuildDotGrid();
        setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // On API 35+ the IME window is edge-to-edge; with gesture navigation the nav bar
                // frame drawn by InputMethodService is reported as a captionBar inset that is
                // taller than navigationBars. Below 35 DecorView consumes both (inset is 0).
                bottom = insets.getInsets(WindowInsets.Type.navigationBars()
                        | WindowInsets.Type.captionBar()).bottom;
            } else {
                bottom = insets.getSystemWindowInsetBottom();
            }
            if (bottom != insetBottom) {
                insetBottom = bottom;
                requestLayout();
            }
            return insets;
        });
    }

    // ---------------------------------------------------------------- configuration

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPalette(Palette palette) {
        if (this.palette == palette) return;
        this.palette = palette;
        rebuildDotGrid();
        invalidate();
    }

    public Palette palette() {
        return palette;
    }

    public void setFonts(Typeface keyFont, Typeface uiFont) {
        this.keyFont = keyFont;
        this.uiFont = uiFont;
        computeGeometry();
        invalidate();
    }

    public void setFeedback(boolean haptics, boolean sound) {
        this.haptics = haptics;
        this.sound = sound;
    }

    /** Configures the layout for a newly focused field. */
    public void setField(FieldConfig.Layout layout, int enterAction, String enterLabel) {
        cancelAllPointers();
        this.fieldLayout = layout;
        this.enterAction = enterAction;
        this.enterLabel = enterLabel;
        this.emojiPanelOpen = false;
        this.mode = layout == FieldConfig.Layout.TEXT
                ? KeyboardLayouts.Mode.LETTERS : KeyboardLayouts.Mode.NUMPAD;
        this.stylePanelOpen = false;
        this.typedInSymbols = false;
        shift = Shift.OFF;
        shiftAuto = false;
        rebuildLayout();
        applyAutoCaps();
        invalidate();
        notifyA11yLayoutChanged();
    }

    public void setStripState(TextStyle style, boolean stylesActive, boolean slangOn,
            int slangMode, String pendingSlang, String undoWord) {
        this.style = style;
        this.stylesActive = stylesActive;
        this.slangOn = slangOn;
        this.slangMode = slangMode;
        this.pendingSlang = pendingSlang;
        this.undoWord = undoWord;
        invalidate();
    }

    /**
     * Sets the recently used emoji (most recent first). An open panel keeps its layout so the
     * grid does not shift under the finger; the list is applied when the panel is next opened.
     */
    public void setRecentEmojis(List<String> recent) {
        recentEmojis = new ArrayList<>(recent);
    }

    public boolean isEmojiPanelOpen() {
        return emojiPanelOpen;
    }

    /** Requests (or withdraws) automatic capitalization of the next letter. */
    public void setAutoCaps(boolean caps) {
        autoCapsWanted = caps;
        applyAutoCaps();
    }

    public Shift shift() {
        return shift;
    }

    public KeyboardLayouts.Mode mode() {
        return mode;
    }

    public boolean isStylePanelOpen() {
        return stylePanelOpen;
    }

    /** Closes transient UI (style panel, presses); called when the keyboard is hidden. */
    public void reset() {
        cancelAllPointers();
        stylePanelOpen = false;
        emojiPanelOpen = false;
        emojiScroller.forceFinished(true);
        invalidate();
    }

    List<KeyboardLayouts.Row> rows() {
        return rows;
    }

    // ---------------------------------------------------------------- layout

    private void updateDimensions() {
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        stripHeight = 44 * density;
        rowHeight = (landscape ? 40 : 54) * density;
        padTop = 2 * density;
        padBottom = 6 * density;
        padH = 3 * density;
        keyInsetH = 2.5f * density;
        keyInsetV = 3.5f * density;
        radius = 10 * density;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateDimensions();
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int rowsCount = rows == null ? 4 : rows.size();
        int height = Math.round(padTop + stripHeight + rowsCount * rowHeight + padBottom) + insetBottom;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeGeometry();
    }

    private void rebuildLayout() {
        rows = KeyboardLayouts.build(mode, fieldLayout, enterKeyFor(enterAction, enterLabel));
        computeGeometry();
    }

    static Key enterKeyFor(int action, String customLabel) {
        if (customLabel != null && !customLabel.trim().isEmpty()) {
            String label = customLabel.trim().toUpperCase(java.util.Locale.getDefault());
            return Key.fn(Key.CODE_ENTER, label, null, 1.5f, Key.Type.ENTER);
        }
        switch (action) {
            case EditorInfo.IME_ACTION_GO:
                return Key.fn(Key.CODE_ENTER, "GO", null, 1.5f, Key.Type.ENTER);
            case EditorInfo.IME_ACTION_SEARCH:
                return Key.fn(Key.CODE_ENTER, null, DotIcon.SEARCH, 1.5f, Key.Type.ENTER);
            case EditorInfo.IME_ACTION_SEND:
                return Key.fn(Key.CODE_ENTER, "SEND", null, 1.5f, Key.Type.ENTER);
            case EditorInfo.IME_ACTION_NEXT:
                return Key.fn(Key.CODE_ENTER, "NEXT", null, 1.5f, Key.Type.ENTER);
            case EditorInfo.IME_ACTION_DONE:
                return Key.fn(Key.CODE_ENTER, "DONE", null, 1.5f, Key.Type.ENTER);
            case EditorInfo.IME_ACTION_PREVIOUS:
                return Key.fn(Key.CODE_ENTER, "PREV", null, 1.5f, Key.Type.ENTER);
            default:
                return Key.fn(Key.CODE_ENTER, null, DotIcon.ENTER, 1.5f, Key.Type.ENTER);
        }
    }

    private void computeGeometry() {
        int width = getWidth();
        if (width == 0 || rows == null) return;
        keysTop = padTop + stripHeight;
        float unit = (width - 2 * padH) / KeyboardLayouts.ROW_UNITS;
        for (int r = 0; r < rows.size(); r++) {
            KeyboardLayouts.Row row = rows.get(r);
            float x = padH + row.offset * unit;
            float top = keysTop + r * rowHeight;
            for (Key k : row.keys) {
                float w = k.weight * unit;
                k.bounds.set(x, top, x + w, top + rowHeight);
                x += w;
            }
        }
        // Strip regions
        float stripMid = padTop + stripHeight / 2f;
        float pillH = stripHeight - 14 * density;
        gearRect.set(width - padH - stripHeight, padTop, width - padH, padTop + stripHeight);
        text.setTypeface(uiFont);
        text.setTextSize(pillH * 0.52f);
        float swagW = text.measureText("SWAG") + 22 * density;
        swagRect.set(gearRect.left - swagW - 4 * density, stripMid - pillH / 2f,
                gearRect.left - 4 * density, stripMid + pillH / 2f);
        float chipW = Math.min(width * 0.34f,
                text.measureText(longestStyleLabel()) + 34 * density);
        chipRect.set(padH + 6 * density, stripMid - pillH / 2f,
                padH + 6 * density + chipW, stripMid + pillH / 2f);
        suggestionRect.set(chipRect.right + 8 * density, padTop,
                swagRect.left - 8 * density, padTop + stripHeight);
        // Emoji panel: grid over the first rows, bottom row = ABC, category tabs, delete.
        float areaBottom = keysTop + rows.size() * rowHeight;
        float barTop = areaBottom - rowHeight;
        emojiGridRect.set(padH, keysTop, width - padH, barTop);
        emojiAbcKey.bounds.set(padH, barTop, padH + 1.5f * unit, areaBottom);
        emojiDelKey.bounds.set(width - padH - 1.5f * unit, barTop, width - padH, areaBottom);
        emojiCols = Math.max(6, (int) (emojiGridRect.width() / (46 * density)));
        emojiCellW = emojiGridRect.width() / emojiCols;
        emojiCellH = Math.min(emojiCellW, rowHeight * 0.92f);
        if (emojiPanelOpen) buildEmojiCells();
        // Style panel tiles: 3 columns, filling the key area.
        int cols = 3;
        int count = tileRects.length;
        int tileRows = (count + cols - 1) / cols;
        float areaH = (rows.size() * rowHeight);
        float tileW = (width - 2 * padH) / cols;
        float tileH = areaH / tileRows;
        for (int i = 0; i < count; i++) {
            int c = i % cols;
            int r = i / cols;
            tileRects[i].set(padH + c * tileW, keysTop + r * tileH,
                    padH + (c + 1) * tileW, keysTop + (r + 1) * tileH);
        }
    }

    private static String longestStyleLabel() {
        String best = "";
        for (TextStyle s : TextStyle.values()) if (s.label.length() > best.length()) best = s.label;
        return best;
    }

    private void rebuildDotGrid() {
        int cell = Math.max(4, Math.round(12 * density));
        Bitmap tile = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tile);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(palette.dotGrid);
        c.drawCircle(cell / 2f, cell / 2f, 0.9f * density, p);
        dotGridPaint.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(palette.background);
        canvas.drawRect(0, 0, getWidth(), getHeight(), dotGridPaint);
        drawStrip(canvas);
        if (stylePanelOpen) {
            drawStylePanel(canvas);
        } else if (emojiPanelOpen) {
            drawEmojiPanel(canvas);
        } else {
            for (KeyboardLayouts.Row row : rows) {
                for (Key k : row.keys) drawKey(canvas, k, isPressed(k));
            }
            drawPreviews(canvas);
        }
    }

    private void drawStrip(Canvas canvas) {
        float pillRadius = chipRect.height() / 2f;
        boolean chipPressed = isRegionPressed(REGION_CHIP);

        // Style chip
        fill.setColor(chipPressed || stylePanelOpen ? palette.keyPressed : palette.key);
        canvas.drawRoundRect(chipRect, pillRadius, pillRadius, fill);
        stroke.setColor(palette.outline);
        canvas.drawRoundRect(chipRect, pillRadius, pillRadius, stroke);
        fill.setColor(stylesActive && style != TextStyle.NORMAL ? palette.accent : palette.textSecondary);
        float dotR = 3 * density;
        float dotX = chipRect.left + 12 * density;
        canvas.drawCircle(dotX, chipRect.centerY(), dotR, fill);
        text.setTypeface(uiFont);
        text.setTextSize(chipRect.height() * 0.52f);
        text.setColor(stylesActive ? palette.text : palette.textSecondary);
        text.setTextAlign(Paint.Align.LEFT);
        String chipLabel = stylesActive ? style.label : "PLAIN";
        float labelLeft = dotX + 8 * density;
        CharSequence fitted = TextUtils.ellipsize(chipLabel, text,
                chipRect.right - 8 * density - labelLeft, TextUtils.TruncateAt.END);
        canvas.drawText(fitted, 0, fitted.length(), labelLeft, baseline(chipRect.centerY()), text);

        // Suggestion / undo
        String suggestion = null;
        int suggestionColor = palette.text;
        if (undoWord != null) {
            suggestion = "UNDO: " + undoWord;
            suggestionColor = palette.textSecondary;
        } else if (pendingSlang != null) {
            suggestion = "→ " + (stylesActive ? style.apply(pendingSlang) : pendingSlang);
            suggestionColor = palette.accent;
        }
        if (suggestion != null) {
            if (isRegionPressed(REGION_SUGGESTION)) {
                fill.setColor(palette.keyPressed);
                tmp.set(suggestionRect.left, chipRect.top, suggestionRect.right, chipRect.bottom);
                canvas.drawRoundRect(tmp, pillRadius, pillRadius, fill);
            }
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(stripHeight * 0.36f);
            text.setColor(suggestionColor);
            CharSequence s = TextUtils.ellipsize(suggestion, text,
                    suggestionRect.width() - 8 * density, TextUtils.TruncateAt.END);
            canvas.drawText(s, 0, s.length(), suggestionRect.centerX(),
                    baseline(suggestionRect.centerY()), text);
        }

        // SWAG toggle
        boolean swagPressed = isRegionPressed(REGION_SWAG);
        if (slangOn && slangMode == SLANG_AUTO) {
            fill.setColor(swagPressed ? palette.keyPressed : palette.accent);
            canvas.drawRoundRect(swagRect, pillRadius, pillRadius, fill);
            text.setColor(palette.onAccent);
        } else {
            // Suggest-only fields: red outline; disabled or not applicable: grey outline.
            boolean suggestOnly = slangOn && slangMode == SLANG_SUGGEST;
            fill.setColor(swagPressed ? palette.keyPressed : palette.key);
            canvas.drawRoundRect(swagRect, pillRadius, pillRadius, fill);
            stroke.setColor(suggestOnly ? palette.accent : palette.outline);
            canvas.drawRoundRect(swagRect, pillRadius, pillRadius, stroke);
            text.setColor(suggestOnly ? palette.accent : palette.textSecondary);
        }
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(swagRect.height() * 0.52f);
        canvas.drawText("SWAG", swagRect.centerX(), baseline(swagRect.centerY()), text);
        if (slangOn && slangMode == SLANG_OFF) {
            // Enabled but not applicable to this field: strike through.
            fill.setColor(palette.textSecondary);
            canvas.drawRect(swagRect.left + 8 * density, swagRect.centerY() - 0.75f * density,
                    swagRect.right - 8 * density, swagRect.centerY() + 0.75f * density, fill);
        }

        // Settings
        fill.setColor(isRegionPressed(REGION_GEAR) ? palette.text : palette.textSecondary);
        DotIcon.GEAR.draw(canvas, gearRect.centerX(), gearRect.centerY(), stripHeight * 0.42f, fill);
    }

    private void drawKey(Canvas canvas, Key k, boolean pressed) {
        tmp.set(k.bounds.left + keyInsetH, k.bounds.top + keyInsetV,
                k.bounds.right - keyInsetH, k.bounds.bottom - keyInsetV);
        int bg;
        int fg = palette.text;
        switch (k.type) {
            case ENTER:
                bg = pressed ? palette.keyPressed : palette.accent;
                fg = palette.onAccent;
                break;
            case SHIFT:
                if (shift == Shift.LOCKED) {
                    bg = pressed ? palette.keyPressed : palette.accent;
                    fg = palette.onAccent;
                } else {
                    bg = pressed ? palette.keyPressed : palette.keyFunction;
                }
                break;
            case FUNCTION:
            case DELETE:
                bg = pressed ? palette.keyPressed : palette.keyFunction;
                break;
            default:
                bg = pressed ? palette.keyPressed : palette.key;
        }
        fill.setColor(bg);
        canvas.drawRoundRect(tmp, radius, radius, fill);

        float cx = tmp.centerX();
        float cy = tmp.centerY();
        DotIcon icon = k.icon;
        if (k.type == Key.Type.SHIFT) {
            icon = shift == Shift.OFF ? DotIcon.SHIFT
                    : shift == Shift.ON ? DotIcon.SHIFT_ON : DotIcon.SHIFT_LOCK;
        }
        if (icon != null) {
            fill.setColor(fg);
            icon.draw(canvas, cx, cy, Math.min(tmp.height(), tmp.width()) * 0.46f, fill);
            return;
        }
        text.setTextAlign(Paint.Align.CENTER);
        if (k.type == Key.Type.SPACE) {
            String label = stylesActive && style != TextStyle.NORMAL ? style.label : "SWAGKEYS";
            text.setTypeface(uiFont);
            text.setColor(palette.textSecondary);
            fitTextSize(label, tmp.width() - 16 * density, rowHeight * 0.26f);
            canvas.drawText(label, cx, baseline(cy), text);
            return;
        }
        if (k.type == Key.Type.CHAR) {
            String label = k.label;
            if (k.isLetter() && shift != Shift.OFF) label = label.toUpperCase(java.util.Locale.ROOT);
            text.setTypeface(keyFont);
            text.setColor(fg);
            fitTextSize(label, tmp.width() - 6 * density, rowHeight * 0.44f);
            canvas.drawText(label, cx, baseline(cy), text);
            if (k.longPressCode != 0) {
                text.setTypeface(uiFont);
                text.setColor(palette.textSecondary);
                text.setTextSize(rowHeight * 0.2f);
                canvas.drawText(new String(Character.toChars(k.longPressCode)),
                        tmp.right - 7 * density, tmp.top + rowHeight * 0.22f, text);
            }
            return;
        }
        // Text-labelled function / enter key
        text.setTypeface(uiFont);
        text.setColor(fg);
        fitTextSize(k.label, tmp.width() - 10 * density, rowHeight * 0.3f);
        canvas.drawText(k.label, cx, baseline(cy), text);
    }

    private void drawPreviews(Canvas canvas) {
        for (int i = 0; i < pointers.size(); i++) {
            Pointer p = pointers.valueAt(i);
            if (p.region != REGION_KEY || p.key == null || p.key.type != Key.Type.CHAR) continue;
            if (p.consumed && p.longPressShown == 0) continue;
            int cp = p.longPressShown != 0 ? p.longPressShown : displayCode(p.key);
            String glyph = new String(Character.toChars(cp));
            if (stylesActive) glyph = style.apply(glyph);
            Key k = p.key;
            float w = Math.max(k.bounds.width() * 1.25f, rowHeight * 0.95f);
            float h = rowHeight * 1.3f;
            float left = Math.max(0, Math.min(getWidth() - w, k.bounds.centerX() - w / 2f));
            float top = Math.max(0, k.bounds.top - h + keyInsetV + 6 * density);
            tmp.set(left, top, left + w, top + h - 6 * density);
            fill.setColor(palette.keyPressed);
            canvas.drawRoundRect(tmp, radius, radius, fill);
            stroke.setColor(palette.accent);
            canvas.drawRoundRect(tmp, radius, radius, stroke);
            text.setTypeface(keyFont);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(palette.text);
            fitTextSize(glyph, tmp.width() - 8 * density, rowHeight * 0.62f);
            canvas.drawText(glyph, tmp.centerX(), baseline(tmp.centerY()), text);
        }
    }

    private void drawStylePanel(Canvas canvas) {
        TextStyle[] styles = TextStyle.values();
        Pointer pressedTile = stripPointerId >= 0 ? pointers.get(stripPointerId) : null;
        for (int i = 0; i < styles.length; i++) {
            RectF r = tileRects[i];
            tmp.set(r.left + keyInsetH, r.top + keyInsetV, r.right - keyInsetH, r.bottom - keyInsetV);
            boolean pressed = pressedTile != null && pressedTile.region == REGION_TILE
                    && pressedTile.tile == i;
            fill.setColor(pressed ? palette.keyPressed : palette.key);
            canvas.drawRoundRect(tmp, radius, radius, fill);
            if (styles[i] == style) {
                stroke.setColor(palette.accent);
                canvas.drawRoundRect(tmp, radius, radius, stroke);
            }
            String sample = styles[i].apply(PANEL_SAMPLE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(keyFont);
            text.setColor(palette.text);
            fitTextSize(sample, tmp.width() - 12 * density, tmp.height() * 0.38f);
            canvas.drawText(sample, tmp.centerX(), baseline(tmp.top + tmp.height() * 0.42f), text);
            text.setTypeface(uiFont);
            text.setColor(styles[i] == style ? palette.accent : palette.textSecondary);
            fitTextSize(styles[i].label, tmp.width() - 12 * density, tmp.height() * 0.2f);
            canvas.drawText(styles[i].label, tmp.centerX(), baseline(tmp.top + tmp.height() * 0.78f), text);
        }
    }

    private void drawEmojiPanel(Canvas canvas) {
        Pointer pressed = stripPointerId >= 0 ? pointers.get(stripPointerId) : null;
        int pressedRegion = pressed == null ? REGION_NONE : pressed.region;
        int pressedIndex = pressed == null ? -1 : pressed.tile;

        canvas.save();
        canvas.clipRect(emojiGridRect);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(palette.text);
        text.setTextSize(emojiCellH * 0.6f);
        int firstRow = Math.max(0, (int) (emojiScrollY / emojiCellH));
        int lastRow = (int) ((emojiScrollY + emojiGridRect.height()) / emojiCellH);
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < emojiCols; col++) {
                int idx = row * emojiCols + col;
                if (idx >= emojiCells.size()) break;
                String e = emojiCells.get(idx);
                if (e == null) continue;
                float left = emojiGridRect.left + col * emojiCellW;
                float top = emojiGridRect.top + row * emojiCellH - emojiScrollY;
                if (pressedRegion == REGION_EMOJI && pressedIndex == idx && !emojiDragging) {
                    tmp.set(left + 2 * density, top + 2 * density,
                            left + emojiCellW - 2 * density, top + emojiCellH - 2 * density);
                    fill.setColor(palette.keyPressed);
                    canvas.drawRoundRect(tmp, radius, radius, fill);
                }
                canvas.drawText(e, left + emojiCellW / 2f, baseline(top + emojiCellH / 2f), text);
            }
        }
        canvas.restore();

        drawKey(canvas, emojiAbcKey, pressedRegion == REGION_EMOJI_ABC);
        drawKey(canvas, emojiDelKey, pressedRegion == REGION_EMOJI_DEL);

        int current = currentEmojiTab();
        text.setTypeface(Typeface.DEFAULT);
        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < emojiTabRects.size(); i++) {
            RectF r = emojiTabRects.get(i);
            boolean selected = i == current;
            if (pressedRegion == REGION_EMOJI_TAB && pressedIndex == i) {
                tmp.set(r.left + keyInsetH, r.top + keyInsetV, r.right - keyInsetH, r.bottom - keyInsetV);
                fill.setColor(palette.keyPressed);
                canvas.drawRoundRect(tmp, radius, radius, fill);
            }
            // Unselected tabs are drawn in greyscale, the selected one in color with a red dot.
            text.setColorFilter(selected ? null : greyscale);
            text.setColor(palette.text);
            text.setAlpha(selected ? 255 : 150);
            text.setTextSize(Math.min(r.height() * 0.4f, r.width() * 0.62f));
            canvas.drawText(emojiTabIcons.get(i), r.centerX(), baseline(r.centerY() - 2 * density), text);
            if (selected) {
                fill.setColor(palette.accent);
                canvas.drawCircle(r.centerX(), r.bottom - keyInsetV - 4 * density, 2.5f * density, fill);
            }
        }
        text.setColorFilter(null);
        text.setAlpha(255);
    }

    private void fitTextSize(String s, float maxWidth, float preferred) {
        text.setTextSize(preferred);
        float w = text.measureText(s);
        if (w > maxWidth && w > 0) text.setTextSize(Math.max(6 * density, preferred * maxWidth / w));
    }

    private float baseline(float centerY) {
        Paint.FontMetrics fm = text.getFontMetrics();
        return centerY - (fm.ascent + fm.descent) / 2f;
    }

    private boolean isPressed(Key k) {
        for (int i = 0; i < pointers.size(); i++) {
            Pointer p = pointers.valueAt(i);
            if (p.region == REGION_KEY && p.key == k) return true;
        }
        return false;
    }

    private boolean isRegionPressed(int region) {
        if (stripPointerId < 0) return false;
        Pointer p = pointers.get(stripPointerId);
        return p != null && p.region == region;
    }

    private int displayCode(Key k) {
        return k.isLetter() && shift != Shift.OFF ? Character.toUpperCase(k.code) : k.code;
    }

    // ---------------------------------------------------------------- touch

    // Keys are not clickable views; input is delivered through Listener, not performClick().
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (emojiVelocity != null && emojiPanelOpen) emojiVelocity.addMovement(ev);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = ev.getActionIndex();
                onPointerDown(ev.getPointerId(idx), ev.getX(idx), ev.getY(idx));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    onPointerMove(ev.getPointerId(i), ev.getX(i), ev.getY(i));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = ev.getActionIndex();
                onPointerUp(ev.getPointerId(idx), ev.getX(idx), ev.getY(idx));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelAllPointers();
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    private int stripRegionAt(float x, float y) {
        if (y >= keysTop) return REGION_NONE;
        if (x >= gearRect.left) return REGION_GEAR;
        if (x >= swagRect.left - 4 * density) return REGION_SWAG;
        if (x <= chipRect.right + 4 * density) return REGION_CHIP;
        if (x >= suggestionRect.left && x <= suggestionRect.right) return REGION_SUGGESTION;
        return REGION_NONE;
    }

    private int tileAt(float x, float y) {
        for (int i = 0; i < tileRects.length; i++) {
            if (tileRects[i].contains(x, y)) return i;
        }
        return -1;
    }

    /** Bottom of the key area; below it lies padding and the navigation-bar inset band. */
    private float keysBottom() {
        return keysTop + (rows == null ? 0 : rows.size()) * rowHeight + padBottom;
    }

    /** Key in the row under y nearest to x; rows' gaps and side margins map to the nearest key. */
    Key keyAt(float x, float y) {
        if (rows == null || rows.isEmpty() || y < keysTop) return null;
        int r = (int) ((y - keysTop) / rowHeight);
        r = Math.max(0, Math.min(rows.size() - 1, r));
        Key best = null;
        float bestDist = Float.MAX_VALUE;
        for (Key k : rows.get(r).keys) {
            float d = x < k.bounds.left ? k.bounds.left - x : x > k.bounds.right ? x - k.bounds.right : 0;
            if (d < bestDist) {
                bestDist = d;
                best = k;
            }
        }
        return best;
    }

    private void onPointerDown(int id, float x, float y) {
        if (y >= keysBottom()) return;
        if (emojiPanelOpen && y >= keysTop) {
            onEmojiPointerDown(id, x, y);
            return;
        }
        if (y < keysTop || stylePanelOpen) {
            // Strip and style panel accept a single pointer at a time.
            if (pointers.size() > 0) return;
            Pointer p = new Pointer(id);
            if (y < keysTop) {
                p.region = stripRegionAt(x, y);
            } else {
                p.region = REGION_TILE;
                p.tile = tileAt(x, y);
            }
            if (p.region == REGION_NONE) return;
            pointers.put(id, p);
            stripPointerId = id;
            feedback(0);
            return;
        }
        if (stripPointerId >= 0) return;
        Key key = keyAt(x, y);
        if (key == null) return;

        if (key.type != Key.Type.DELETE) stopRepeat();
        if (key.type != Key.Type.SHIFT) {
            lastShiftTapTime = NO_TAP;
            // A chord (shift held while typing) must not turn into caps lock.
            Pointer sp = shiftPointerId >= 0 ? pointers.get(shiftPointerId) : null;
            if (sp != null) cancelLongPress(sp);
        }

        // Roll-over: a new touch commits a character or space key still held by another
        // finger, so output order follows press order.
        for (int i = 0; i < pointers.size(); i++) {
            Pointer other = pointers.valueAt(i);
            if (other.region != REGION_KEY || other.consumed) continue;
            if (other.key.type == Key.Type.CHAR) {
                cancelLongPress(other);
                other.consumed = true;
                emitChar(other.key, displayCode(other.key));
            } else if (other.key.type == Key.Type.SPACE) {
                cancelLongPress(other);
                other.consumed = true;
                emit(Key.CODE_SPACE);
            }
        }

        Pointer p = new Pointer(id);
        p.region = REGION_KEY;
        p.key = key;
        pointers.put(id, p);
        feedback(key.code);

        switch (key.type) {
            case DELETE:
                p.consumed = true;
                emit(Key.CODE_DELETE);
                startRepeat();
                break;
            case SHIFT:
                p.consumed = true;
                onShiftDown(id);
                scheduleLongPress(p, LONG_PRESS_FUNCTION_MS);
                break;
            case SPACE:
                scheduleLongPress(p, LONG_PRESS_FUNCTION_MS);
                break;
            case CHAR:
                if (key.longPressCode != 0) scheduleLongPress(p, LONG_PRESS_MS);
                break;
            default:
                break;
        }
    }

    private void onPointerMove(int id, float x, float y) {
        Pointer p = pointers.get(id);
        if (p == null) return;
        if (p.region == REGION_EMOJI) {
            if (!emojiDragging && Math.abs(y - emojiDownY) > touchSlop) {
                emojiDragging = true;
                emojiLastY = y;
            }
            if (emojiDragging) {
                emojiScrollY += emojiLastY - y;
                clampEmojiScroll();
                emojiLastY = y;
            }
            return;
        }
        if (p.region == REGION_TILE) {
            int t = tileAt(x, y);
            if (t != p.tile) p.tile = t;
            return;
        }
        if (p.region != REGION_KEY || p.consumed || p.key.type != Key.Type.CHAR) return;
        Key k = keyAt(x, y);
        if (k != null && k != p.key && k.type == Key.Type.CHAR) {
            // Sliding onto a neighbouring character key retargets the press.
            cancelLongPress(p);
            p.key = k;
            if (k.longPressCode != 0) scheduleLongPress(p, LONG_PRESS_MS);
        }
    }

    private void onPointerUp(int id, float x, float y) {
        Pointer p = pointers.get(id);
        if (p == null) return;
        pointers.remove(id);
        cancelLongPress(p);
        if (id == stripPointerId) {
            stripPointerId = -1;
            if (p.region >= REGION_EMOJI) {
                onEmojiPointerUp(p, x, y);
            } else if (p.region == REGION_TILE) {
                if (p.tile >= 0 && p.tile == tileAt(x, y)) {
                    selectStyle(p.tile);
                }
            } else if (p.region == stripRegionAt(x, y)) {
                onStripTap(p.region);
            }
            return;
        }
        if (p.key.type == Key.Type.DELETE) {
            stopRepeat();
            return;
        }
        if (p.key.type == Key.Type.SHIFT) {
            onShiftUp(id);
            return;
        }
        if (p.consumed) return;
        activateKey(p.key);
    }

    /** Performs a key's tap action, for a touch release or an accessibility click. */
    private void activateKey(Key k) {
        switch (k.type) {
            case CHAR:
                emitChar(k, displayCode(k));
                break;
            case SPACE:
                emit(Key.CODE_SPACE);
                if (typedInSymbols && (mode == KeyboardLayouts.Mode.SYMBOLS
                        || mode == KeyboardLayouts.Mode.SYMBOLS2)) {
                    switchMode(KeyboardLayouts.Mode.LETTERS);
                }
                break;
            case ENTER:
                emit(Key.CODE_ENTER);
                break;
            case FUNCTION:
                onFunctionKey(k.code);
                break;
            case DELETE:
                emit(Key.CODE_DELETE);
                break;
            case SHIFT:
                onShiftDown(-1);
                shiftPointerId = -1;
                break;
            default:
                break;
        }
        notifyA11yLayoutChanged();
    }

    private void onStripTap(int region) {
        if (listener == null) return;
        switch (region) {
            case REGION_CHIP:
                stylePanelOpen = !stylePanelOpen;
                if (stylePanelOpen) emojiPanelOpen = false;
                notifyA11yLayoutChanged();
                break;
            case REGION_SUGGESTION:
                if (undoWord != null || pendingSlang != null) listener.onSuggestionTap();
                break;
            case REGION_SWAG:
                listener.onSlangToggle();
                break;
            case REGION_GEAR:
                listener.onOpenSettings();
                break;
            default:
                break;
        }
    }

    private void selectStyle(int tile) {
        stylePanelOpen = false;
        notifyA11yLayoutChanged();
        if (listener != null) listener.onStyleSelected(TextStyle.values()[tile]);
    }

    private void onFunctionKey(int code) {
        switch (code) {
            case Key.CODE_MODE_SYMBOLS:
                switchMode(KeyboardLayouts.Mode.SYMBOLS);
                break;
            case Key.CODE_MODE_SYMBOLS2:
                switchMode(KeyboardLayouts.Mode.SYMBOLS2);
                break;
            case Key.CODE_MODE_ALPHA:
                switchMode(KeyboardLayouts.Mode.LETTERS);
                break;
            case Key.CODE_EMOJI:
                openEmojiPanel();
                break;
            default:
                break;
        }
    }

    private void switchMode(KeyboardLayouts.Mode newMode) {
        cancelAllPointers();
        mode = newMode;
        typedInSymbols = false;
        if (newMode != KeyboardLayouts.Mode.LETTERS) {
            shift = Shift.OFF;
            shiftAuto = false;
        }
        rebuildLayout();
        applyAutoCaps();
        requestLayout();
        notifyA11yLayoutChanged();
    }

    private void emitChar(Key key, int code) {
        if (mode == KeyboardLayouts.Mode.SYMBOLS || mode == KeyboardLayouts.Mode.SYMBOLS2) {
            typedInSymbols = true;
        }
        if (shift == Shift.ON) {
            if (shiftPointerId >= 0) {
                shiftChordTyped = true;
            } else {
                shift = Shift.OFF;
                shiftAuto = false;
            }
        }
        emit(code);
    }

    private void emit(int code) {
        if (listener != null) listener.onKey(code);
    }

    // ---------------------------------------------------------------- emoji panel

    private void openEmojiPanel() {
        cancelAllPointers();
        stylePanelOpen = false;
        emojiPanelOpen = true;
        emojiScroller.forceFinished(true);
        emojiScrollY = 0;
        buildEmojiCells();
        notifyA11yLayoutChanged();
        invalidate();
    }

    private void closeEmojiPanel() {
        cancelAllPointers();
        emojiPanelOpen = false;
        emojiScroller.forceFinished(true);
        applyAutoCaps();
        notifyA11yLayoutChanged();
        invalidate();
    }

    private void buildEmojiCells() {
        emojiCells.clear();
        emojiTabIcons.clear();
        emojiTabNames.clear();
        emojiTabRows.clear();
        if (!recentEmojis.isEmpty()) {
            addEmojiSection(RECENT_EMOJI_ICON, R.string.a11y_emoji_recent, recentEmojis);
        }
        for (EmojiData.Category c : EmojiData.categories()) {
            addEmojiSection(c.icon, c.nameRes, c.emojis);
        }
        emojiTabRects.clear();
        float left = emojiAbcKey.bounds.right;
        float w = (emojiDelKey.bounds.left - left) / Math.max(1, emojiTabIcons.size());
        for (int i = 0; i < emojiTabIcons.size(); i++) {
            emojiTabRects.add(new RectF(left + i * w, emojiAbcKey.bounds.top,
                    left + (i + 1) * w, emojiAbcKey.bounds.bottom));
        }
        clampEmojiScroll();
    }

    private void addEmojiSection(String icon, int nameRes, List<String> emojis) {
        emojiTabIcons.add(icon);
        emojiTabNames.add(nameRes);
        emojiTabRows.add(emojiCells.size() / emojiCols);
        emojiCells.addAll(emojis);
        while (emojiCells.size() % emojiCols != 0) emojiCells.add(null);
    }

    private float maxEmojiScroll() {
        int totalRows = emojiCells.size() / Math.max(1, emojiCols);
        return Math.max(0f, totalRows * emojiCellH - emojiGridRect.height());
    }

    private void clampEmojiScroll() {
        emojiScrollY = Math.max(0f, Math.min(maxEmojiScroll(), emojiScrollY));
    }

    private int emojiCellAt(float x, float y) {
        if (!emojiGridRect.contains(x, y)) return -1;
        int col = Math.min(emojiCols - 1, (int) ((x - emojiGridRect.left) / emojiCellW));
        int row = (int) ((y - emojiGridRect.top + emojiScrollY) / emojiCellH);
        int idx = row * emojiCols + col;
        return idx < emojiCells.size() && emojiCells.get(idx) != null ? idx : -1;
    }

    private int emojiTabAt(float x, float y) {
        for (int i = 0; i < emojiTabRects.size(); i++) {
            if (emojiTabRects.get(i).contains(x, y)) return i;
        }
        return -1;
    }

    /** The tab whose section contains the first (mostly) visible row. */
    private int currentEmojiTab() {
        if (emojiScrollY >= maxEmojiScroll() - 1 && maxEmojiScroll() > 0) {
            return emojiTabRows.size() - 1;
        }
        int row = (int) ((emojiScrollY + emojiCellH * 0.5f) / emojiCellH);
        int current = 0;
        for (int i = 0; i < emojiTabRows.size(); i++) {
            if (emojiTabRows.get(i) <= row) current = i;
        }
        return current;
    }

    private void scrollToEmojiTab(int tab) {
        float target = Math.min(maxEmojiScroll(), emojiTabRows.get(tab) * emojiCellH);
        emojiScroller.forceFinished(true);
        emojiScroller.startScroll(0, Math.round(emojiScrollY), 0,
                Math.round(target - emojiScrollY), 250);
        postInvalidateOnAnimation();
    }

    private boolean emojiCellVisible(int idx) {
        float top = emojiGridRect.top + (idx / emojiCols) * emojiCellH - emojiScrollY;
        return top + emojiCellH > emojiGridRect.top && top < emojiGridRect.bottom;
    }

    private void onEmojiPointerDown(int id, float x, float y) {
        if (pointers.size() > 0) return;
        Pointer p = new Pointer(id);
        if (emojiGridRect.contains(x, y)) {
            p.region = REGION_EMOJI;
            p.tile = emojiCellAt(x, y);
            emojiScroller.forceFinished(true);
            emojiDragging = false;
            emojiDownY = y;
            emojiLastY = y;
            if (emojiVelocity == null) {
                emojiVelocity = VelocityTracker.obtain();
            } else {
                emojiVelocity.clear();
            }
        } else if (emojiAbcKey.bounds.contains(x, y)) {
            p.region = REGION_EMOJI_ABC;
        } else if (emojiDelKey.bounds.contains(x, y)) {
            p.region = REGION_EMOJI_DEL;
        } else {
            p.tile = emojiTabAt(x, y);
            if (p.tile < 0) return;
            p.region = REGION_EMOJI_TAB;
        }
        pointers.put(id, p);
        stripPointerId = id;
        if (p.region == REGION_EMOJI_DEL) {
            feedback(Key.CODE_DELETE);
            emit(Key.CODE_DELETE);
            startRepeat();
        } else {
            feedback(0);
        }
    }

    private void onEmojiPointerUp(Pointer p, float x, float y) {
        switch (p.region) {
            case REGION_EMOJI:
                if (emojiDragging) {
                    emojiDragging = false;
                    if (emojiVelocity != null) {
                        emojiVelocity.computeCurrentVelocity(1000, maxFlingVelocity);
                        float vy = emojiVelocity.getYVelocity(p.id);
                        if (Math.abs(vy) >= minFlingVelocity) {
                            emojiScroller.fling(0, Math.round(emojiScrollY), 0, Math.round(-vy),
                                    0, 0, 0, Math.round(maxEmojiScroll()));
                            postInvalidateOnAnimation();
                        }
                    }
                    notifyA11yLayoutChanged();
                } else if (p.tile >= 0 && p.tile == emojiCellAt(x, y)) {
                    emitEmoji(emojiCells.get(p.tile));
                }
                break;
            case REGION_EMOJI_TAB:
                if (p.tile >= 0 && p.tile == emojiTabAt(x, y)) scrollToEmojiTab(p.tile);
                break;
            case REGION_EMOJI_ABC:
                if (emojiAbcKey.bounds.contains(x, y)) closeEmojiPanel();
                break;
            case REGION_EMOJI_DEL:
                stopRepeat();
                break;
            default:
                break;
        }
    }

    private void emitEmoji(String emoji) {
        if (listener != null) listener.onEmoji(emoji);
    }

    @Override
    public void computeScroll() {
        if (emojiScroller.computeScrollOffset()) {
            emojiScrollY = emojiScroller.getCurrY();
            clampEmojiScroll();
            if (emojiScroller.isFinished()) notifyA11yLayoutChanged();
            postInvalidateOnAnimation();
        }
    }

    // ---------------------------------------------------------------- shift

    private void onShiftDown(int pointerId) {
        long now = SystemClock.uptimeMillis();
        boolean doubleTap = lastShiftTapTime != NO_TAP && now - lastShiftTapTime < DOUBLE_TAP_MS;
        if (shift == Shift.LOCKED) {
            shift = Shift.OFF;
            lastShiftTapTime = NO_TAP;
        } else if (doubleTap) {
            shift = Shift.LOCKED;
            lastShiftTapTime = NO_TAP;
        } else {
            shift = shift == Shift.ON ? Shift.OFF : Shift.ON;
            lastShiftTapTime = now;
        }
        shiftAuto = false;
        shiftPointerId = pointerId;
        shiftChordTyped = false;
    }

    private void onShiftUp(int pointerId) {
        if (pointerId != shiftPointerId) return;
        shiftPointerId = -1;
        if (shiftChordTyped && shift == Shift.ON) shift = Shift.OFF;
        shiftChordTyped = false;
    }

    private void applyAutoCaps() {
        if (mode != KeyboardLayouts.Mode.LETTERS || shift == Shift.LOCKED || shiftPointerId >= 0) {
            invalidate();
            return;
        }
        if (autoCapsWanted) {
            if (shift == Shift.OFF) {
                shift = Shift.ON;
                shiftAuto = true;
            }
        } else if (shift == Shift.ON && shiftAuto) {
            shift = Shift.OFF;
            shiftAuto = false;
        }
        invalidate();
    }

    // ---------------------------------------------------------------- timers

    private void scheduleLongPress(Pointer p, long delay) {
        cancelLongPress(p);
        p.longPress = () -> {
            p.longPress = null;
            if (pointers.get(p.id) != p) return;
            onLongPress(p);
            invalidate();
        };
        handler.postDelayed(p.longPress, delay);
    }

    private void cancelLongPress(Pointer p) {
        if (p.longPress != null) {
            handler.removeCallbacks(p.longPress);
            p.longPress = null;
        }
    }

    private void onLongPress(Pointer p) {
        switch (p.key.type) {
            case CHAR:
                if (p.consumed || p.key.longPressCode == 0) return;
                p.consumed = true;
                p.longPressShown = p.key.longPressCode;
                feedback(p.key.code);
                emitChar(p.key, p.key.longPressCode);
                break;
            case SPACE:
                if (p.consumed) return;
                p.consumed = true;
                if (listener != null) listener.onSpaceLongPress();
                break;
            case SHIFT:
                if (shiftChordTyped) return;
                shift = Shift.LOCKED;
                feedback(p.key.code);
                break;
            default:
                break;
        }
    }

    private void startRepeat() {
        stopRepeat();
        repeatRunnable = new Runnable() {
            @Override
            public void run() {
                emit(Key.CODE_DELETE);
                handler.postDelayed(this, REPEAT_INTERVAL_MS);
            }
        };
        handler.postDelayed(repeatRunnable, REPEAT_START_MS);
    }

    private void stopRepeat() {
        if (repeatRunnable != null) {
            handler.removeCallbacks(repeatRunnable);
            repeatRunnable = null;
        }
    }

    private void cancelAllPointers() {
        emojiDragging = false;
        for (int i = 0; i < pointers.size(); i++) cancelLongPress(pointers.valueAt(i));
        pointers.clear();
        stripPointerId = -1;
        if (shiftPointerId >= 0) {
            shiftPointerId = -1;
            if (shiftChordTyped && shift == Shift.ON) shift = Shift.OFF;
            shiftChordTyped = false;
        }
        stopRepeat();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAllPointers();
        emojiScroller.forceFinished(true);
        if (emojiVelocity != null) {
            emojiVelocity.recycle();
            emojiVelocity = null;
        }
        super.onDetachedFromWindow();
    }

    // ---------------------------------------------------------------- accessibility

    /*
     * Touch exploration (TalkBack): every key, strip control and style tile is a virtual view.
     * Hovering announces it; lifting the finger on it types it (lift-to-type), and a
     * double tap performs ACTION_CLICK on the focused virtual view.
     */

    private static final int VID_NONE = Integer.MIN_VALUE;
    private static final int VID_STRIP = 10;
    private static final int VID_TILE = 100;
    private static final int VID_EMOJI_TAB = 500;
    private static final int VID_EMOJI_ABC = 600;
    private static final int VID_EMOJI_DEL = 601;
    private static final int VID_KEY = 1000;
    private static final int VID_ROW = 100;
    /** Emoji cells: VID_EMOJI + index into emojiCells. */
    private static final int VID_EMOJI = 100000;

    private int hoveredVid = VID_NONE;
    private int a11yFocusedVid = VID_NONE;
    private AccessibilityNodeProvider nodeProvider;

    private boolean touchExploring() {
        AccessibilityManager am = (AccessibilityManager)
                getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
    }

    private int virtualIdAt(float x, float y) {
        if (y >= keysBottom()) return VID_NONE;
        if (y < keysTop) {
            int region = stripRegionAt(x, y);
            return region == REGION_NONE ? VID_NONE : VID_STRIP + region;
        }
        if (stylePanelOpen) {
            int t = tileAt(x, y);
            return t < 0 ? VID_NONE : VID_TILE + t;
        }
        if (emojiPanelOpen) {
            if (emojiGridRect.contains(x, y)) {
                int idx = emojiCellAt(x, y);
                return idx < 0 ? VID_NONE : VID_EMOJI + idx;
            }
            if (emojiAbcKey.bounds.contains(x, y)) return VID_EMOJI_ABC;
            if (emojiDelKey.bounds.contains(x, y)) return VID_EMOJI_DEL;
            int t = emojiTabAt(x, y);
            return t < 0 ? VID_NONE : VID_EMOJI_TAB + t;
        }
        Key k = keyAt(x, y);
        return k == null ? VID_NONE : keyVid(k);
    }

    private int keyVid(Key k) {
        for (int r = 0; r < rows.size(); r++) {
            int i = rows.get(r).keys.indexOf(k);
            if (i >= 0) return VID_KEY + r * VID_ROW + i;
        }
        return VID_NONE;
    }

    private Key keyForVid(int vid) {
        if (vid < VID_KEY || vid >= VID_EMOJI || stylePanelOpen || emojiPanelOpen) return null;
        int r = (vid - VID_KEY) / VID_ROW;
        int i = (vid - VID_KEY) % VID_ROW;
        if (r >= rows.size() || i >= rows.get(r).keys.size()) return null;
        return rows.get(r).keys.get(i);
    }

    private boolean isValidVid(int vid) {
        if (vid >= VID_EMOJI) {
            int idx = vid - VID_EMOJI;
            return emojiPanelOpen && idx < emojiCells.size() && emojiCells.get(idx) != null
                    && emojiCellVisible(idx);
        }
        if (vid >= VID_KEY) return keyForVid(vid) != null;
        if (vid == VID_EMOJI_ABC || vid == VID_EMOJI_DEL) return emojiPanelOpen;
        if (vid >= VID_EMOJI_TAB) return emojiPanelOpen && vid - VID_EMOJI_TAB < emojiTabIcons.size();
        if (vid >= VID_TILE) return stylePanelOpen && vid - VID_TILE < tileRects.length;
        if (vid > VID_STRIP) return vid - VID_STRIP >= REGION_CHIP && vid - VID_STRIP <= REGION_GEAR
                && (vid - VID_STRIP != REGION_SUGGESTION || undoWord != null || pendingSlang != null);
        return false;
    }

    private void boundsForVid(int vid, Rect out) {
        RectF r;
        if (vid >= VID_EMOJI) {
            int idx = vid - VID_EMOJI;
            float left = emojiGridRect.left + (idx % emojiCols) * emojiCellW;
            float top = emojiGridRect.top + (idx / emojiCols) * emojiCellH - emojiScrollY;
            r = tmp;
            r.set(left, Math.max(top, emojiGridRect.top), left + emojiCellW,
                    Math.min(top + emojiCellH, emojiGridRect.bottom));
        } else if (vid >= VID_KEY) {
            r = keyForVid(vid).bounds;
        } else if (vid == VID_EMOJI_ABC) {
            r = emojiAbcKey.bounds;
        } else if (vid == VID_EMOJI_DEL) {
            r = emojiDelKey.bounds;
        } else if (vid >= VID_EMOJI_TAB) {
            r = emojiTabRects.get(vid - VID_EMOJI_TAB);
        } else if (vid >= VID_TILE) {
            r = tileRects[vid - VID_TILE];
        } else {
            switch (vid - VID_STRIP) {
                case REGION_CHIP: r = chipRect; break;
                case REGION_SWAG: r = swagRect; break;
                case REGION_GEAR: r = gearRect; break;
                default: r = suggestionRect; break;
            }
        }
        out.set(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));
    }

    private CharSequence describeVid(int vid) {
        Context c = getContext();
        if (vid >= VID_EMOJI) return emojiCells.get(vid - VID_EMOJI);
        if (vid == VID_EMOJI_ABC) return c.getString(R.string.a11y_letters);
        if (vid == VID_EMOJI_DEL) return c.getString(R.string.a11y_delete);
        if (vid >= VID_EMOJI_TAB && vid < VID_KEY) {
            return c.getString(emojiTabNames.get(vid - VID_EMOJI_TAB));
        }
        if (vid >= VID_KEY) {
            Key k = keyForVid(vid);
            switch (k.type) {
                case SHIFT:
                    return c.getString(shift == Shift.LOCKED ? R.string.a11y_shift_locked
                            : shift == Shift.ON ? R.string.a11y_shift_on : R.string.a11y_shift);
                case DELETE:
                    return c.getString(R.string.a11y_delete);
                case SPACE:
                    return c.getString(R.string.a11y_space);
                case ENTER:
                    return k.label != null ? k.label : c.getString(
                            enterAction == EditorInfo.IME_ACTION_SEARCH ? R.string.a11y_search
                                    : R.string.a11y_enter);
                case FUNCTION:
                    switch (k.code) {
                        case Key.CODE_MODE_SYMBOLS: return c.getString(R.string.a11y_symbols);
                        case Key.CODE_MODE_SYMBOLS2: return c.getString(R.string.a11y_more_symbols);
                        case Key.CODE_MODE_ALPHA: return c.getString(R.string.a11y_letters);
                        case Key.CODE_EMOJI: return c.getString(R.string.a11y_emoji);
                        default: return k.label;
                    }
                default:
                    return new String(Character.toChars(displayCode(k)));
            }
        }
        if (vid >= VID_TILE) {
            TextStyle t = TextStyle.values()[vid - VID_TILE];
            return t == style ? c.getString(R.string.a11y_style_selected, t.label) : t.label;
        }
        switch (vid - VID_STRIP) {
            case REGION_CHIP:
                return c.getString(R.string.a11y_style_chip, stylesActive ? style.label : "PLAIN");
            case REGION_SUGGESTION:
                return undoWord != null ? c.getString(R.string.a11y_undo, undoWord)
                        : c.getString(R.string.a11y_replace_with, String.valueOf(pendingSlang));
            case REGION_SWAG:
                return c.getString(slangOn ? R.string.a11y_slang_on : R.string.a11y_slang_off);
            default:
                return c.getString(R.string.a11y_settings);
        }
    }

    private void clickVid(int vid) {
        if (!isValidVid(vid)) return;
        if (vid >= VID_EMOJI) {
            feedback(0);
            emitEmoji(emojiCells.get(vid - VID_EMOJI));
        } else if (vid == VID_EMOJI_ABC) {
            feedback(0);
            closeEmojiPanel();
        } else if (vid == VID_EMOJI_DEL) {
            feedback(Key.CODE_DELETE);
            emit(Key.CODE_DELETE);
        } else if (vid >= VID_EMOJI_TAB && vid < VID_KEY) {
            feedback(0);
            scrollToEmojiTab(vid - VID_EMOJI_TAB);
        } else if (vid >= VID_KEY) {
            Key k = keyForVid(vid);
            feedback(k.code);
            activateKey(k);
        } else if (vid >= VID_TILE) {
            feedback(0);
            selectStyle(vid - VID_TILE);
        } else {
            feedback(0);
            onStripTap(vid - VID_STRIP);
        }
        invalidate();
    }

    private boolean longClickVid(int vid) {
        Key k = keyForVid(vid);
        if (k == null) return false;
        if (k.type == Key.Type.SPACE) {
            if (listener != null) listener.onSpaceLongPress();
            return true;
        }
        if (k.type == Key.Type.CHAR && k.longPressCode != 0) {
            feedback(k.code);
            emitChar(k, k.longPressCode);
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        if (!touchExploring()) return super.dispatchHoverEvent(event);
        int vid = virtualIdAt(event.getX(), event.getY());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                setHoveredVid(vid);
                break;
            case MotionEvent.ACTION_HOVER_EXIT:
                // Lift-to-type: releasing the finger over the explored control activates it.
                if (vid != VID_NONE && vid == hoveredVid) clickVid(vid);
                setHoveredVid(VID_NONE);
                break;
            default:
                break;
        }
        return true;
    }

    private void setHoveredVid(int vid) {
        if (vid == hoveredVid) return;
        int old = hoveredVid;
        hoveredVid = vid;
        if (old != VID_NONE) sendA11yEvent(old, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
        if (vid != VID_NONE) sendA11yEvent(vid, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
    }

    @SuppressWarnings("deprecation")
    private void sendA11yEvent(int vid, int type) {
        ViewParent parent = getParent();
        if (parent == null || !isValidVid(vid)) return;
        AccessibilityManager am = (AccessibilityManager)
                getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null || !am.isEnabled()) return;
        AccessibilityEvent e = AccessibilityEvent.obtain(type);
        e.setPackageName(getContext().getPackageName());
        e.setClassName(Key.class.getName());
        e.setSource(this, vid);
        e.setEnabled(true);
        e.getText().add(describeVid(vid));
        parent.requestSendAccessibilityEvent(this, e);
    }

    private void notifyA11yLayoutChanged() {
        hoveredVid = VID_NONE;
        a11yFocusedVid = VID_NONE;
        ViewParent parent = getParent();
        if (parent != null && touchExploring()) {
            parent.notifySubtreeAccessibilityStateChanged(this, this,
                    AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
        }
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        if (nodeProvider == null) nodeProvider = new KeyNodeProvider();
        return nodeProvider;
    }

    private final class KeyNodeProvider extends AccessibilityNodeProvider {
        private final Rect rect = new Rect();
        private final int[] location = new int[2];

        @Override
        @SuppressWarnings("deprecation")
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int vid) {
            if (vid == HOST_VIEW_ID) {
                AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(KeyboardView.this);
                onInitializeAccessibilityNodeInfo(info);
                if (stylePanelOpen) {
                    for (int i = 0; i < tileRects.length; i++) info.addChild(KeyboardView.this, VID_TILE + i);
                } else if (emojiPanelOpen) {
                    for (int i = 0; i < emojiCells.size(); i++) {
                        if (isValidVid(VID_EMOJI + i)) info.addChild(KeyboardView.this, VID_EMOJI + i);
                    }
                    for (int i = 0; i < emojiTabIcons.size(); i++) {
                        info.addChild(KeyboardView.this, VID_EMOJI_TAB + i);
                    }
                    info.addChild(KeyboardView.this, VID_EMOJI_ABC);
                    info.addChild(KeyboardView.this, VID_EMOJI_DEL);
                    info.setScrollable(true);
                    if (emojiScrollY > 0) {
                        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
                    }
                    if (emojiScrollY < maxEmojiScroll()) {
                        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                    }
                } else {
                    for (int r = 0; r < rows.size(); r++) {
                        for (int i = 0; i < rows.get(r).keys.size(); i++) {
                            info.addChild(KeyboardView.this, VID_KEY + r * VID_ROW + i);
                        }
                    }
                }
                for (int region = REGION_CHIP; region <= REGION_GEAR; region++) {
                    if (isValidVid(VID_STRIP + region)) info.addChild(KeyboardView.this, VID_STRIP + region);
                }
                return info;
            }
            if (!isValidVid(vid)) return null;
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(KeyboardView.this, vid);
            info.setPackageName(getContext().getPackageName());
            info.setClassName(Key.class.getName());
            info.setParent(KeyboardView.this);
            info.setContentDescription(describeVid(vid));
            boundsForVid(vid, rect);
            info.setBoundsInParent(rect);
            getLocationOnScreen(location);
            rect.offset(location[0], location[1]);
            info.setBoundsInScreen(rect);
            info.setEnabled(true);
            info.setVisibleToUser(true);
            info.setClickable(true);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
            Key k = keyForVid(vid);
            if (k != null && (k.type == Key.Type.SPACE || k.longPressCode != 0)) {
                info.setLongClickable(true);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK);
            }
            if (vid == a11yFocusedVid) {
                info.setAccessibilityFocused(true);
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            } else {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
            }
            return info;
        }

        @Override
        public boolean performAction(int vid, int action, Bundle arguments) {
            if (vid == HOST_VIEW_ID) {
                if (emojiPanelOpen && (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
                    float page = Math.max(emojiCellH, emojiGridRect.height() - emojiCellH);
                    float before = emojiScrollY;
                    emojiScroller.forceFinished(true);
                    emojiScrollY += action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? page : -page;
                    clampEmojiScroll();
                    invalidate();
                    notifyA11yLayoutChanged();
                    return emojiScrollY != before;
                }
                return performAccessibilityAction(action, arguments);
            }
            if (!isValidVid(vid)) return false;
            switch (action) {
                case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS:
                    if (a11yFocusedVid == vid) return false;
                    a11yFocusedVid = vid;
                    sendA11yEvent(vid, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                    return true;
                case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
                    if (a11yFocusedVid != vid) return false;
                    a11yFocusedVid = VID_NONE;
                    sendA11yEvent(vid, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                    return true;
                case AccessibilityNodeInfo.ACTION_CLICK:
                    sendA11yEvent(vid, AccessibilityEvent.TYPE_VIEW_CLICKED);
                    clickVid(vid);
                    return true;
                case AccessibilityNodeInfo.ACTION_LONG_CLICK:
                    return longClickVid(vid);
                default:
                    return false;
            }
        }
    }

    // ---------------------------------------------------------------- feedback

    private void feedback(int code) {
        if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (sound && audio != null) {
            int effect;
            switch (code) {
                case Key.CODE_DELETE:
                    effect = AudioManager.FX_KEYPRESS_DELETE;
                    break;
                case Key.CODE_ENTER:
                    effect = AudioManager.FX_KEYPRESS_RETURN;
                    break;
                case Key.CODE_SPACE:
                    effect = AudioManager.FX_KEYPRESS_SPACEBAR;
                    break;
                default:
                    effect = AudioManager.FX_KEYPRESS_STANDARD;
            }
            audio.playSoundEffect(effect, -1f);
        }
    }
}
