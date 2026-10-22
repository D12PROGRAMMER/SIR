package dev.swagkeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAccessibilityManager;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Renders the keyboard to PNG files (build/screenshots) and exercises touch handling. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 36, qualifiers = "w411dp-h891dp-xxhdpi")
public class KeyboardRenderTest {

    private Context context;
    private KeyboardView view;
    private final List<Integer> keys = new ArrayList<>();
    private final List<TextStyle> stylesPicked = new ArrayList<>();
    private final List<String> emojis = new ArrayList<>();
    private final List<Integer> cursorSteps = new ArrayList<>();
    private final List<Integer> deletedWords = new ArrayList<>();
    private int slangToggles;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        view = new KeyboardView(context);
        Typeface[] faces = Fonts.resolve(context, new Prefs(context));
        view.setFonts(faces[0], faces[1]);
        view.setFeedback(false, false);
        view.setListener(new KeyboardView.Listener() {
            @Override public void onKey(int code) { keys.add(code); }
            @Override public void onEmoji(String emoji) { emojis.add(emoji); }
            @Override public void onSpaceLongPress() { keys.add(-100); }
            @Override public void onSwitchKeyboard() { keys.add(-103); }
            @Override public void onCursorMove(int steps) { cursorSteps.add(steps); }
            @Override public void onDeleteWords(int words) { deletedWords.add(words); }
            @Override public void onStyleSelected(TextStyle style) { stylesPicked.add(style); }
            @Override public void onSlangToggle() { slangToggles++; }
            @Override public void onSuggestionTap() { keys.add(-101); }
            @Override public void onOpenSettings() { keys.add(-102); }
        });
        view.setField(FieldConfig.Layout.TEXT, EditorInfo.IME_ACTION_NONE, null);
        layout();
    }

    private void layout() {
        int width = context.getResources().getDisplayMetrics().widthPixels;
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private File shot(String name) throws IOException {
        layout();
        Bitmap bmp = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bmp));
        File dir = new File(System.getProperty("swagkeys.screenshotDir", "build/screenshots"));
        assertTrue(dir.isDirectory() || dir.mkdirs());
        File f = new File(dir, name + ".png");
        try (OutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return f;
    }

    private Key find(int code) {
        for (KeyboardLayouts.Row row : view.rows()) {
            for (Key k : row.keys) if (k.code == code) return k;
        }
        return null;
    }

    private void touch(int action, int pointerIndex, float[][] points) {
        int[] ids = new int[points.length];
        for (int i = 0; i < ids.length; i++) ids[i] = i;
        touch(action, pointerIndex, ids, points);
    }

    private void touch(int action, int pointerIndex, int[] ids, float[][] points) {
        int count = points.length;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = ids[i];
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = points[i][0];
            coords[i].y = points[i][1];
        }
        int fullAction = action | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(now, now, fullAction, count, props, coords,
                0, 0, 1f, 1f, 0, 0, 0, 0);
        view.onTouchEvent(ev);
        ev.recycle();
    }

    private void tap(Key k) {
        float[][] p = {{k.bounds.centerX(), k.bounds.centerY()}};
        touch(MotionEvent.ACTION_DOWN, 0, p);
        touch(MotionEvent.ACTION_UP, 0, p);
    }

    @Test
    public void rendersAllModes() throws IOException {
        view.setPalette(Palette.DARK);
        view.setStripState(TextStyle.LEET, true, true, KeyboardView.SLANG_AUTO, "wassup", null);
        assertTrue(shot("01_letters_dark").length() > 0);

        view.setPalette(Palette.LIGHT);
        view.setAutoCaps(true);
        view.setStripState(TextStyle.NORMAL, true, true, KeyboardView.SLANG_SUGGEST, null, "cool");
        shot("02_letters_light_shift");

        view.setPalette(Palette.DARK);
        tap(find(Key.CODE_MODE_SYMBOLS));
        assertEquals(KeyboardLayouts.Mode.SYMBOLS, view.mode());
        shot("03_symbols");
        tap(find(Key.CODE_MODE_SYMBOLS2));
        shot("04_symbols2");

        view.setField(FieldConfig.Layout.PHONE, EditorInfo.IME_ACTION_DONE, null);
        shot("05_phone");

        view.setField(FieldConfig.Layout.TEXT, EditorInfo.IME_ACTION_SEARCH, null);
        view.setStripState(TextStyle.BOLD, true, false, KeyboardView.SLANG_AUTO, null, null);
        Key e = find('e');
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {{e.bounds.centerX(), e.bounds.centerY()}});
        shot("06_preview_pressed");
        touch(MotionEvent.ACTION_UP, 0, new float[][] {{e.bounds.centerX(), e.bounds.centerY()}});

        view.setStripState(TextStyle.SCRIPT, true, true, KeyboardView.SLANG_OFF, null, null);
        // Open the style panel via the chip (strip, left edge).
        float[][] chip = {{40f, 40f}};
        touch(MotionEvent.ACTION_DOWN, 0, chip);
        touch(MotionEvent.ACTION_UP, 0, chip);
        assertTrue(view.isStylePanelOpen());
        shot("07_style_panel");
    }

    @Test
    public void tapsEmitCodesAndShiftIsOneShot() {
        view.setAutoCaps(false);
        tap(find(Key.CODE_SHIFT));
        assertEquals(KeyboardView.Shift.ON, view.shift());
        tap(find('h'));
        tap(find('i'));
        assertEquals(List.of((int) 'H', (int) 'i'), keys);
        assertEquals(KeyboardView.Shift.OFF, view.shift());
    }

    @Test
    public void doubleTapShiftLocks() {
        view.setAutoCaps(false);
        tap(find(Key.CODE_SHIFT));
        tap(find(Key.CODE_SHIFT));
        assertEquals(KeyboardView.Shift.LOCKED, view.shift());
        tap(find('a'));
        tap(find('b'));
        assertEquals(List.of((int) 'A', (int) 'B'), keys);
        tap(find(Key.CODE_SHIFT));
        assertEquals(KeyboardView.Shift.OFF, view.shift());
    }

    @Test
    public void longPressTopRowEmitsDigit() {
        Key q = find('q');
        float[][] p = {{q.bounds.centerX(), q.bounds.centerY()}};
        touch(MotionEvent.ACTION_DOWN, 0, p);
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        touch(MotionEvent.ACTION_UP, 0, p);
        assertEquals(List.of((int) '1'), keys);
    }

    @Test
    public void deleteRepeatsWhileHeld() {
        Key del = find(Key.CODE_DELETE);
        float[][] p = {{del.bounds.centerX(), del.bounds.centerY()}};
        touch(MotionEvent.ACTION_DOWN, 0, p);
        ShadowLooper.idleMainLooper(400 + 50 * 4, java.util.concurrent.TimeUnit.MILLISECONDS);
        touch(MotionEvent.ACTION_UP, 0, p);
        ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertTrue("repeats: " + keys.size(), keys.size() >= 5);
        for (int code : keys) assertEquals(Key.CODE_DELETE, code);
        int count = keys.size();
        ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(count, keys.size());
    }

    @Test
    public void rollOverCommitsFirstKeyOnSecondDown() {
        Key a = find('a');
        Key s = find('s');
        float[] pa = {a.bounds.centerX(), a.bounds.centerY()};
        float[] ps = {s.bounds.centerX(), s.bounds.centerY()};
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {pa});
        touch(MotionEvent.ACTION_POINTER_DOWN, 1, new float[][] {pa, ps});
        assertEquals(List.of((int) 'a'), keys);
        touch(MotionEvent.ACTION_POINTER_UP, 0, new float[][] {pa, ps});
        touch(MotionEvent.ACTION_UP, 0, new int[] {1}, new float[][] {ps});
        assertEquals(List.of((int) 'a', (int) 's'), keys);
    }

    @Test
    public void edgeTouchesMapToNearestKey() {
        Key a = find('a');
        assertEquals(a, view.keyAt(1f, a.bounds.centerY()));
        Key l = find('l');
        assertEquals(l, view.keyAt(view.getWidth() - 1f, l.bounds.centerY()));
    }

    @Test
    public void stylePanelSelectsStyle() {
        float[][] chip = {{40f, 40f}};
        touch(MotionEvent.ACTION_DOWN, 0, chip);
        touch(MotionEvent.ACTION_UP, 0, chip);
        assertTrue(view.isStylePanelOpen());
        // Second tile (L33T): first panel row, second of five columns.
        Key q = find('q');
        float[][] tile = {{view.getWidth() * 0.3f, q.bounds.centerY()}};
        touch(MotionEvent.ACTION_DOWN, 0, tile);
        touch(MotionEvent.ACTION_UP, 0, tile);
        assertEquals(List.of(TextStyle.LEET), stylesPicked);
        assertTrue(!view.isStylePanelOpen());
        assertNotNull(find('q'));
    }

    @Test
    public void rollOverEmitsHeldSpaceBeforeNextLetter() {
        Key space = find(Key.CODE_SPACE);
        Key c = find('c');
        float[] ps = {space.bounds.centerX(), space.bounds.centerY()};
        float[] pc = {c.bounds.centerX(), c.bounds.centerY()};
        touch(MotionEvent.ACTION_DOWN, 0, new int[] {0}, new float[][] {ps});
        touch(MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1}, new float[][] {ps, pc});
        touch(MotionEvent.ACTION_POINTER_UP, 1, new int[] {0, 1}, new float[][] {ps, pc});
        touch(MotionEvent.ACTION_UP, 0, new int[] {0}, new float[][] {ps});
        assertEquals(List.of((int) ' ', (int) 'c'), keys);
    }

    @Test
    public void shiftChordDoesNotLockCaps() {
        view.setAutoCaps(false);
        Key shift = find(Key.CODE_SHIFT);
        Key h = find('h');
        float[] psh = {shift.bounds.centerX(), shift.bounds.centerY()};
        float[] ph = {h.bounds.centerX(), h.bounds.centerY()};
        touch(MotionEvent.ACTION_DOWN, 0, new int[] {0}, new float[][] {psh});
        touch(MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1}, new float[][] {psh, ph});
        touch(MotionEvent.ACTION_POINTER_UP, 1, new int[] {0, 1}, new float[][] {psh, ph});
        ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS);
        touch(MotionEvent.ACTION_UP, 0, new int[] {0}, new float[][] {psh});
        assertEquals(List.of((int) 'H'), keys);
        assertEquals(KeyboardView.Shift.OFF, view.shift());
    }

    @Test
    public void doubleTapShiftLocksFromAutoCaps() {
        view.setAutoCaps(true);
        assertEquals(KeyboardView.Shift.ON, view.shift());
        tap(find(Key.CODE_SHIFT));
        tap(find(Key.CODE_SHIFT));
        assertEquals(KeyboardView.Shift.LOCKED, view.shift());
        tap(find('o'));
        tap(find('k'));
        assertEquals(List.of((int) 'O', (int) 'K'), keys);
    }

    @Test
    public void shiftLetterShiftDoesNotLock() {
        view.setAutoCaps(false);
        tap(find(Key.CODE_SHIFT));
        tap(find('a'));
        tap(find(Key.CODE_SHIFT));
        assertEquals(KeyboardView.Shift.ON, view.shift());
    }

    @Test
    public void bottomInsetCoversImeNavigationBarFrame() {
        float density = context.getResources().getDisplayMetrics().density;
        int nav = Math.round(24 * density);
        int caption = Math.round(48 * density);
        int before = view.getMeasuredHeight();
        view.dispatchApplyWindowInsets(new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, nav))
                .setInsets(WindowInsets.Type.captionBar(), Insets.of(0, 0, 0, caption))
                .build());
        layout();
        assertEquals(before + caption, view.getMeasuredHeight());
        // Touches in the inset band do not press bottom-row keys.
        float[][] p = {{view.getWidth() / 2f, view.getHeight() - caption / 2f}};
        touch(MotionEvent.ACTION_DOWN, 0, p);
        touch(MotionEvent.ACTION_UP, 0, p);
        assertTrue(keys.isEmpty());
    }

    @Test
    public void accessibilityNodesAndClicks() {
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        AccessibilityNodeInfo host = provider.createAccessibilityNodeInfo(
                AccessibilityNodeProvider.HOST_VIEW_ID);
        // 10 + 9 + 9 + 6 keys (with the emoji key) plus chip, SWAG and settings.
        assertEquals(34 + 3, host.getChildCount());
        AccessibilityNodeInfo q = provider.createAccessibilityNodeInfo(1000);
        assertEquals("q", q.getContentDescription().toString());
        AccessibilityNodeInfo shiftNode = provider.createAccessibilityNodeInfo(1000 + 2 * 100);
        assertEquals("Shift", shiftNode.getContentDescription().toString());
        assertTrue(provider.performAction(1000, AccessibilityNodeInfo.ACTION_CLICK, null));
        assertTrue(provider.performAction(1000, AccessibilityNodeInfo.ACTION_LONG_CLICK, null));
        assertEquals(List.of((int) 'q', (int) '1'), keys);
    }

    @Test
    public void touchExplorationLiftToType() {
        AccessibilityManager am = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        ShadowAccessibilityManager sam = Shadow.extract(am);
        sam.setEnabled(true);
        sam.setTouchExplorationEnabled(true);
        Key w = find('w');
        hover(MotionEvent.ACTION_HOVER_ENTER, w.bounds.centerX(), w.bounds.centerY());
        hover(MotionEvent.ACTION_HOVER_MOVE, w.bounds.centerX() + 2, w.bounds.centerY());
        assertTrue(keys.isEmpty());
        hover(MotionEvent.ACTION_HOVER_EXIT, w.bounds.centerX() + 2, w.bounds.centerY());
        assertEquals(List.of((int) 'w'), keys);
    }

    private void hover(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(now, now, action, x, y, 0);
        ev.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        view.dispatchHoverEvent(ev);
        ev.recycle();
    }

    private void drag(float x, float fromY, float toY) {
        int steps = 8;
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {{x, fromY}});
        for (int i = 1; i <= steps; i++) {
            float y = fromY + (toY - fromY) * i / steps;
            touch(MotionEvent.ACTION_MOVE, 0, new float[][] {{x, y}});
        }
        touch(MotionEvent.ACTION_UP, 0, new float[][] {{x, toY}});
    }

    @Test
    public void emojiKeyOpensPanelAndInsertsEmoji() throws IOException {
        view.setRecentEmojis(List.of("😂", "🔥"));
        Key emojiKey = find(Key.CODE_EMOJI);
        assertNotNull(emojiKey);
        tap(emojiKey);
        assertTrue(view.isEmojiPanelOpen());
        view.setPalette(Palette.DARK);
        shot("08_emoji_panel");

        // First cell of the grid is the most recent emoji.
        Key q = find('q');
        float[][] cell = {{q.bounds.left + 20, q.bounds.top + 20}};
        touch(MotionEvent.ACTION_DOWN, 0, cell);
        touch(MotionEvent.ACTION_UP, 0, cell);
        assertEquals(List.of("😂"), emojis);

        // Dragging scrolls instead of inserting.
        float x = view.getWidth() / 2f;
        drag(x, q.bounds.bottom + q.bounds.height(), q.bounds.top + 4);
        assertEquals(1, emojis.size());
        shot("09_emoji_scrolled");

        // Delete in the panel emits delete; ABC returns to the letters.
        float rowY = find(Key.CODE_SPACE).bounds.centerY();
        float[][] del = {{view.getWidth() - 20f, rowY}};
        touch(MotionEvent.ACTION_DOWN, 0, del);
        touch(MotionEvent.ACTION_UP, 0, del);
        assertEquals(List.of(Key.CODE_DELETE), keys);
        float[][] abc = {{20f, rowY}};
        touch(MotionEvent.ACTION_DOWN, 0, abc);
        touch(MotionEvent.ACTION_UP, 0, abc);
        assertTrue(!view.isEmojiPanelOpen());
        assertNotNull(find('q'));
    }

    @Test
    public void emojiTabsAndAccessibility() {
        tap(find(Key.CODE_EMOJI));
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        AccessibilityNodeInfo host = provider.createAccessibilityNodeInfo(
                AccessibilityNodeProvider.HOST_VIEW_ID);
        assertTrue(host.isScrollable());
        AccessibilityNodeInfo first = provider.createAccessibilityNodeInfo(100000);
        assertEquals("😀", first.getContentDescription().toString());
        assertTrue(provider.performAction(100000, AccessibilityNodeInfo.ACTION_CLICK, null));
        assertEquals(List.of("😀"), emojis);
        assertTrue(provider.performAction(AccessibilityNodeProvider.HOST_VIEW_ID,
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
        // The first cell scrolled out of view, so its node is gone.
        assertTrue(provider.createAccessibilityNodeInfo(100000) == null);
        // Tab "Flags" (last) scrolls to its section.
        AccessibilityNodeInfo flagsTab = provider.createAccessibilityNodeInfo(500 + 8);
        assertEquals("Flags", flagsTab.getContentDescription().toString());
        assertTrue(provider.performAction(500 + 8, AccessibilityNodeInfo.ACTION_CLICK, null));
        ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        view.computeScroll();
        ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        view.computeScroll();
        assertTrue(provider.createAccessibilityNodeInfo(100000) == null);
    }

    /** Scrolls the open emoji panel to tab {@code tab} and returns the virtual id of {@code emoji}. */
    private int emojiVid(int tab, String emoji, String description) {
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        provider.performAction(500 + tab, AccessibilityNodeInfo.ACTION_CLICK, null);
        for (int i = 0; i < 20; i++) {
            ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            view.computeScroll();
        }
        AccessibilityNodeInfo host = provider.createAccessibilityNodeInfo(
                AccessibilityNodeProvider.HOST_VIEW_ID);
        for (int vid = 100000; vid < 100000 + 4000; vid++) {
            AccessibilityNodeInfo n = provider.createAccessibilityNodeInfo(vid);
            if (n != null && description.equals(String.valueOf(n.getContentDescription()))) return vid;
        }
        throw new AssertionError(emoji + " not visible; children=" + host.getChildCount());
    }

    @Test
    public void longPressShowsSkinTonesAndSlideSelects() throws IOException {
        tap(find(Key.CODE_EMOJI));
        int vid = emojiVid(1, "👋", "👋, more skin tones");
        android.graphics.Rect r = new android.graphics.Rect();
        view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(vid).getBoundsInParent(r);
        float[][] p = {{r.exactCenterX(), r.exactCenterY()}};
        touch(MotionEvent.ACTION_DOWN, 0, p);
        ShadowLooper.idleMainLooper(450, java.util.concurrent.TimeUnit.MILLISECONDS);
        // Popup is open: base + 5 tones, each a virtual node.
        AccessibilityNodeInfo tone2 = view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(702);
        assertNotNull(tone2);
        assertEquals("👋🏼", tone2.getContentDescription().toString());
        shot("10_skin_tones");
        android.graphics.Rect t = new android.graphics.Rect();
        tone2.getBoundsInParent(t);
        float[][] q = {{t.exactCenterX(), t.exactCenterY()}};
        touch(MotionEvent.ACTION_MOVE, 0, q);
        touch(MotionEvent.ACTION_UP, 0, q);
        assertEquals(List.of("👋🏼"), emojis);
        assertTrue(view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(702) == null);
    }

    @Test
    public void shortTapOnToneableEmojiInsertsBase() {
        tap(find(Key.CODE_EMOJI));
        int vid = emojiVid(1, "👍", "👍, more skin tones");
        android.graphics.Rect r = new android.graphics.Rect();
        view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(vid).getBoundsInParent(r);
        float[][] p = {{r.exactCenterX(), r.exactCenterY()}};
        touch(MotionEvent.ACTION_DOWN, 0, p);
        touch(MotionEvent.ACTION_UP, 0, p);
        ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(List.of("👍"), emojis);
    }

    @Test
    public void accessibilityLongClickOpensStickyTonePicker() {
        tap(find(Key.CODE_EMOJI));
        int vid = emojiVid(1, "👍", "👍, more skin tones");
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        assertTrue(provider.performAction(vid, AccessibilityNodeInfo.ACTION_LONG_CLICK, null));
        assertEquals("👍🏿", provider.createAccessibilityNodeInfo(705).getContentDescription().toString());
        assertTrue(provider.performAction(705, AccessibilityNodeInfo.ACTION_CLICK, null));
        assertEquals(List.of("👍🏿"), emojis);
    }

    @Test
    public void longPressEmojiKeySwitchesKeyboard() {
        Key emojiKey = find(Key.CODE_EMOJI);
        float[][] p = {{emojiKey.bounds.centerX(), emojiKey.bounds.centerY()}};
        touch(MotionEvent.ACTION_DOWN, 0, p);
        ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS);
        touch(MotionEvent.ACTION_UP, 0, p);
        assertEquals(List.of(-103), keys);
        assertTrue(!view.isEmojiPanelOpen());
        AccessibilityNodeInfo node = view.getAccessibilityNodeProvider()
                .createAccessibilityNodeInfo(1000 + 3 * 100 + 1);
        boolean labelled = false;
        for (AccessibilityNodeInfo.AccessibilityAction a : node.getActionList()) {
            if (a.getId() == AccessibilityNodeInfo.ACTION_LONG_CLICK
                    && "Switch keyboard".contentEquals(a.getLabel())) labelled = true;
        }
        assertTrue(labelled);
    }

    @Test
    public void tapThatStopsTabScrollDoesNotInsert() {
        tap(find(Key.CODE_EMOJI));
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        provider.performAction(500 + 3, AccessibilityNodeInfo.ACTION_CLICK, null); // start scrolling
        float[][] cell = {{view.getWidth() / 2f, view.getHeight() / 3f}};
        touch(MotionEvent.ACTION_DOWN, 0, cell);
        touch(MotionEvent.ACTION_UP, 0, cell);
        assertTrue(emojis.isEmpty());
        // Once stopped, the next tap types.
        touch(MotionEvent.ACTION_DOWN, 0, cell);
        touch(MotionEvent.ACTION_UP, 0, cell);
        assertEquals(1, emojis.size());
    }

    @Test
    public void panelAbcFromSymbolsReturnsToLetters() {
        tap(find(Key.CODE_MODE_SYMBOLS));
        tap(find(Key.CODE_EMOJI));
        assertTrue(view.isEmojiPanelOpen());
        // A tap low in the bottom padding band still hits ABC.
        float density = context.getResources().getDisplayMetrics().density;
        float[][] abc = {{2f, view.getHeight() - 2 * density}};
        touch(MotionEvent.ACTION_DOWN, 0, abc);
        touch(MotionEvent.ACTION_UP, 0, abc);
        assertTrue(!view.isEmojiPanelOpen());
        assertEquals(KeyboardLayouts.Mode.LETTERS, view.mode());
    }

    /** Finds the visible grid node described as {@code description}, scrolling forward as needed. */
    private int findEmojiVid(int tab, String description) {
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        provider.performAction(500 + tab, AccessibilityNodeInfo.ACTION_CLICK, null);
        for (int i = 0; i < 20; i++) {
            ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            view.computeScroll();
        }
        for (int page = 0; page < 40; page++) {
            for (int vid = 100000; vid < 100000 + 4000; vid++) {
                AccessibilityNodeInfo n = provider.createAccessibilityNodeInfo(vid);
                if (n == null || !description.equals(String.valueOf(n.getContentDescription()))) continue;
                android.graphics.Rect r = new android.graphics.Rect();
                n.getBoundsInParent(r);
                if (r.height() >= 20) return vid; // fully enough on screen to touch
            }
            provider.performAction(AccessibilityNodeProvider.HOST_VIEW_ID,
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null);
        }
        throw new AssertionError(description + " not found");
    }

    private float[] center(int vid) {
        android.graphics.Rect r = new android.graphics.Rect();
        view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(vid).getBoundsInParent(r);
        return new float[] {r.exactCenterX(), r.exactCenterY()};
    }

    @Test
    public void jitterDuringLongPressKeepsBase() {
        tap(find(Key.CODE_EMOJI));
        float[] c = center(findEmojiVid(1, "👋, more skin tones"));
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {c});
        ShadowLooper.idleMainLooper(450, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertNotNull(view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(700));
        float[][] jitter = {{c[0] + 3, c[1] - 2}};
        touch(MotionEvent.ACTION_MOVE, 0, jitter);
        touch(MotionEvent.ACTION_UP, 0, jitter);
        assertEquals(List.of("👋"), emojis);
    }

    /** Scrolls the emoji grid by exactly {@code dy} pixels with a slow drag (no fling). */
    private void scrollGridBy(float dy) {
        int slop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
        float x = view.getWidth() / 2f;
        float y0 = find('a').bounds.centerY();
        float dir = Math.signum(dy);
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {{x, y0}});
        touch(MotionEvent.ACTION_MOVE, 0, new float[][] {{x, y0 - dir * (slop + 1)}});
        touch(MotionEvent.ACTION_MOVE, 0, new float[][] {{x, y0 - dir * (slop + 1) - dy}});
        touch(MotionEvent.ACTION_UP, 0, new float[][] {{x, y0 - dir * (slop + 1) - dy}});
        for (int k = 0; k < 20; k++) {
            ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            view.computeScroll();
        }
    }

    @Test
    public void tallPickerOnUpperRowIsTapToChooseOtherwiseSlide() throws IOException {
        tap(find(Key.CODE_EMOJI));
        int vid = findEmojiVid(1, "🤝, more skin tones");
        float density = context.getResources().getDisplayMetrics().density;
        float target = find('q').bounds.top + 30 * density; // first grid row
        for (int i = 0; i < 5 && Math.abs(center(vid)[1] - target) > 2; i++) {
            scrollGridBy(center(vid)[1] - target);
        }
        float[] c = center(vid);
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {c});
        ShadowLooper.idleMainLooper(450, java.util.concurrent.TimeUnit.MILLISECONDS);
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        int n = 0;
        while (provider.createAccessibilityNodeInfo(700 + n) != null) n++;
        touch(MotionEvent.ACTION_UP, 0, new float[][] {c});
        if (n > 6) {
            // Pair picker (base + 25) is taller than the space above: it covers the finger.
            assertTrue("emitted " + emojis, emojis.isEmpty());
            shot("11_pair_tones");
            AccessibilityNodeInfo pick = provider.createAccessibilityNodeInfo(700 + n - 1);
            float[] t = center(700 + n - 1);
            touch(MotionEvent.ACTION_DOWN, 0, new float[][] {t});
            touch(MotionEvent.ACTION_UP, 0, new float[][] {t});
            assertEquals(List.of(pick.getContentDescription().toString()), emojis);
        } else {
            // This font has only single-tone variants: the row fits above, release keeps base.
            assertEquals(6, n);
            assertEquals(List.of("🤝"), emojis);
        }
    }

    @Test
    public void screenReaderPassThroughLongPressIsTapToChoose() throws IOException {
        tap(find(Key.CODE_EMOJI));
        float[] c = center(findEmojiVid(1, "👍, more skin tones"));
        AccessibilityManager am = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        ShadowAccessibilityManager sam = Shadow.extract(am);
        sam.setEnabled(true);
        sam.setTouchExplorationEnabled(true);
        // Double-tap-and-hold is delivered as a real touch long press.
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {c});
        ShadowLooper.idleMainLooper(450, java.util.concurrent.TimeUnit.MILLISECONDS);
        touch(MotionEvent.ACTION_MOVE, 0, new float[][] {{c[0] + 80, c[1] - 80}});
        touch(MotionEvent.ACTION_UP, 0, new float[][] {{c[0] + 80, c[1] - 80}});
        assertTrue("emitted " + emojis, emojis.isEmpty());
        AccessibilityNodeInfo tone = view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(703);
        assertNotNull(tone);
        shot("11_tap_to_choose");
        float[] t = center(703);
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {t});
        touch(MotionEvent.ACTION_UP, 0, new float[][] {t});
        assertEquals(List.of(tone.getContentDescription().toString()), emojis);
        assertTrue(view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(700) == null);
    }

    @Test
    public void slidingToNeighbourCancelsLongPress() {
        tap(find(Key.CODE_EMOJI));
        float[] c = center(findEmojiVid(1, "👋, more skin tones"));
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {c});
        float[][] next = {{c[0] + 60 * context.getResources().getDisplayMetrics().density, c[1]}};
        touch(MotionEvent.ACTION_MOVE, 0, next);
        ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertTrue(view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(700) == null);
        touch(MotionEvent.ACTION_UP, 0, next);
        assertTrue(emojis.isEmpty());
    }

    @Test
    public void otherAccessibilityActionsCloseStickyPicker() {
        tap(find(Key.CODE_EMOJI));
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        int vid = findEmojiVid(1, "👍, more skin tones");
        assertTrue(provider.performAction(vid, AccessibilityNodeInfo.ACTION_LONG_CLICK, null));
        assertNotNull(provider.createAccessibilityNodeInfo(700));
        assertTrue(provider.performAction(700, AccessibilityNodeInfo.ACTION_DISMISS, null));
        assertTrue(provider.createAccessibilityNodeInfo(700) == null);
        assertTrue(provider.performAction(vid, AccessibilityNodeInfo.ACTION_LONG_CLICK, null));
        provider.performAction(AccessibilityNodeProvider.HOST_VIEW_ID,
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null);
        assertTrue(provider.createAccessibilityNodeInfo(700) == null);
        assertTrue(emojis.isEmpty());
    }

    @Test
    public void hoverDwellOpensPickerWithoutTyping() {
        AccessibilityManager am = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        ShadowAccessibilityManager sam = Shadow.extract(am);
        tap(find(Key.CODE_EMOJI));
        float[] c = center(findEmojiVid(1, "👋, more skin tones"));
        sam.setEnabled(true);
        sam.setTouchExplorationEnabled(true);
        hover(MotionEvent.ACTION_HOVER_ENTER, c[0], c[1]);
        ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertNotNull(view.getAccessibilityNodeProvider().createAccessibilityNodeInfo(700));
        hover(MotionEvent.ACTION_HOVER_EXIT, c[0], c[1]);
        assertTrue(emojis.isEmpty());
        // The picker stays; exploring a tone and lifting types it.
        float[] t = center(701);
        hover(MotionEvent.ACTION_HOVER_ENTER, t[0], t[1]);
        hover(MotionEvent.ACTION_HOVER_EXIT, t[0], t[1]);
        assertEquals(List.of("👋🏻"), emojis);
    }

    @Test
    public void spaceSwipeMovesCursorWithoutTypingSpace() throws IOException {
        Key space = find(Key.CODE_SPACE);
        float density = context.getResources().getDisplayMetrics().density;
        float x = space.bounds.centerX();
        float y = space.bounds.centerY();
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {{x, y}});
        for (int i = 1; i <= 10; i++) {
            touch(MotionEvent.ACTION_MOVE, 0, new float[][] {{x - i * 8 * density, y}});
        }
        shot("12_cursor_mode");
        touch(MotionEvent.ACTION_UP, 0, new float[][] {{x - 80 * density, y}});
        int total = 0;
        for (int s : cursorSteps) total += s;
        assertTrue("steps " + cursorSteps, total <= -5 && total >= -8);
        assertTrue(keys.isEmpty());
    }

    @Test
    public void deleteSwipeLeftDeletesWords() throws IOException {
        Key del = find(Key.CODE_DELETE);
        float density = context.getResources().getDisplayMetrics().density;
        float x = del.bounds.centerX();
        float y = del.bounds.centerY();
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {{x, y}});
        assertTrue(keys.isEmpty()); // the first delete waits for release or repeat
        for (int i = 1; i <= 12; i++) {
            touch(MotionEvent.ACTION_MOVE, 0, new float[][] {{x - i * 9 * density, y}});
        }
        ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS);
        shot("13_delete_words");
        // No key repeat and no character delete while selecting words.
        ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertTrue(keys.isEmpty());
        touch(MotionEvent.ACTION_UP, 0, new float[][] {{x - 108 * density, y}});
        assertEquals(List.of(3), deletedWords);
        assertTrue(keys.isEmpty());

        // Sliding back to the key cancels.
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {{x, y}});
        touch(MotionEvent.ACTION_MOVE, 0, new float[][] {{x - 60 * density, y}});
        touch(MotionEvent.ACTION_MOVE, 0, new float[][] {{x - 2 * density, y}});
        touch(MotionEvent.ACTION_UP, 0, new float[][] {{x - 2 * density, y}});
        assertEquals(List.of(3), deletedWords);
        assertTrue(keys.isEmpty());
    }

    @Test
    public void deleteTapAndRollOverKeepOrder() {
        tap(find(Key.CODE_DELETE));
        assertEquals(List.of(Key.CODE_DELETE), keys);
        keys.clear();
        Key del = find(Key.CODE_DELETE);
        Key a = find('a');
        float[] pd = {del.bounds.centerX(), del.bounds.centerY()};
        float[] pa = {a.bounds.centerX(), a.bounds.centerY()};
        touch(MotionEvent.ACTION_DOWN, 0, new int[] {0}, new float[][] {pd});
        touch(MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1}, new float[][] {pd, pa});
        touch(MotionEvent.ACTION_POINTER_UP, 1, new int[] {0, 1}, new float[][] {pd, pa});
        touch(MotionEvent.ACTION_UP, 0, new int[] {0}, new float[][] {pd});
        assertEquals(List.of(Key.CODE_DELETE, (int) 'a'), keys);
    }

    @Test
    public void spaceSwipeIgnoredAfterRollOver() {
        Key space = find(Key.CODE_SPACE);
        Key b = find('b');
        float density = context.getResources().getDisplayMetrics().density;
        float[] ps = {space.bounds.centerX(), space.bounds.centerY()};
        float[] pb = {b.bounds.centerX(), b.bounds.centerY()};
        touch(MotionEvent.ACTION_DOWN, 0, new int[] {0}, new float[][] {ps});
        touch(MotionEvent.ACTION_POINTER_DOWN, 1, new int[] {0, 1}, new float[][] {ps, pb});
        float[] moved = {ps[0] - 60 * density, ps[1]};
        touch(MotionEvent.ACTION_MOVE, 0, new int[] {0, 1}, new float[][] {moved, pb});
        assertTrue(cursorSteps.isEmpty());
        touch(MotionEvent.ACTION_POINTER_UP, 1, new int[] {0, 1}, new float[][] {moved, pb});
        touch(MotionEvent.ACTION_UP, 0, new int[] {0}, new float[][] {moved});
        assertEquals(List.of((int) ' ', (int) 'b'), keys);
    }

    @Test
    public void numberRowKeepsHeightAcrossModes() {
        view.setNumberRow(true);
        layout();
        int letters = view.getMeasuredHeight();
        tap(find(Key.CODE_MODE_SYMBOLS));
        layout();
        assertEquals(letters, view.getMeasuredHeight());
        assertEquals(4, view.rows().size());
        // Four symbol rows fill the five-row area: each is taller than a letter row.
        float density = context.getResources().getDisplayMetrics().density;
        assertTrue(find('1').bounds.height() > 60 * density);
    }

    @Test
    public void wordDeleteDisabledKeepsRepeating() {
        view.setWordDeleteEnabled(false);
        Key del = find(Key.CODE_DELETE);
        float density = context.getResources().getDisplayMetrics().density;
        float x = del.bounds.centerX();
        float y = del.bounds.centerY();
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {{x, y}});
        touch(MotionEvent.ACTION_MOVE, 0, new float[][] {{x - 80 * density, y}});
        ShadowLooper.idleMainLooper(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        touch(MotionEvent.ACTION_UP, 0, new float[][] {{x - 80 * density, y}});
        assertTrue(deletedWords.isEmpty());
        assertTrue(keys.size() >= 2);
    }

    @Test
    public void decodeResolvesStyledTextWithinItsDuration() {
        for (TextStyle s : new TextStyle[] {TextStyle.CURSED, TextStyle.STRIKE, TextStyle.SPACED}) {
            String target = s.apply("straight trash");
            assertEquals(s.name(), target, KeyboardView.decodeFrame(target, 899));
        }
    }

    @Test
    public void animationsRenderMidFlight() throws IOException {
        view.setPalette(Palette.DARK.withAccent(Palette.accentColor("cyan")));
        Key g = find('g');
        float[][] p = {{g.bounds.centerX(), g.bounds.centerY()}};
        touch(MotionEvent.ACTION_DOWN, 0, p);
        ShadowLooper.idleMainLooper(60, java.util.concurrent.TimeUnit.MILLISECONDS);
        touch(MotionEvent.ACTION_UP, 0, p);
        Key j = find('j');
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {{j.bounds.centerX(), j.bounds.centerY()}});
        ShadowLooper.idleMainLooper(170, java.util.concurrent.TimeUnit.MILLISECONDS);
        view.playSlangAnimation("goated");
        ShadowLooper.idleMainLooper(150, java.util.concurrent.TimeUnit.MILLISECONDS);
        shot("14_ripple_decode_cyan");
        touch(MotionEvent.ACTION_UP, 0, new float[][] {{j.bounds.centerX(), j.bounds.centerY()}});
        assertEquals(List.of((int) 'g', (int) 'j'), keys);
        // Everything settles.
        ShadowLooper.idleMainLooper(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        shot("15_settled");
    }

    @Test
    public void decodeFrameResolvesToTarget() {
        assertEquals("goated", KeyboardView.decodeFrame("goated", 10_000));
        String early = KeyboardView.decodeFrame("goated", 0);
        assertEquals(6, early.length());
        assertTrue(!early.equals("goated"));
        assertEquals(early, KeyboardView.decodeFrame("goated", 0));
        assertEquals("a b", KeyboardView.decodeFrame("a b", 1000));
    }

    @Test
    public void numberRowAndAllStyles() throws IOException {
        view.setNumberRow(true);
        layout();
        assertEquals(5, view.rows().size());
        assertEquals('1', view.rows().get(0).keys.get(0).code);
        shot("16_number_row");
        view.setNumberRow(false);
        float[][] chip = {{40f, 40f}};
        touch(MotionEvent.ACTION_DOWN, 0, chip);
        touch(MotionEvent.ACTION_UP, 0, chip);
        ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertTrue(view.isStylePanelOpen());
        shot("17_all_styles");
    }

    @Test
    public void animationsOffDrawsStatically() throws IOException {
        view.setAnimations(false);
        Key g = find('g');
        float[][] p = {{g.bounds.centerX(), g.bounds.centerY()}};
        touch(MotionEvent.ACTION_DOWN, 0, p);
        touch(MotionEvent.ACTION_UP, 0, p);
        view.playSlangAnimation("lit");
        shot("18_animations_off");
        assertEquals(List.of((int) 'g'), keys);
    }

    @Test
    public void swagPillToggles() {
        int w = view.getWidth();
        float[][] p = {{w - 44 * 3f - 60f, 40f}};
        touch(MotionEvent.ACTION_DOWN, 0, p);
        touch(MotionEvent.ACTION_UP, 0, p);
        assertEquals(1, slangToggles);
    }
}
