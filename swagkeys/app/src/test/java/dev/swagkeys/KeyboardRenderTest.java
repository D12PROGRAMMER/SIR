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
        // Second tile (L33T) lies in the first panel row, middle column.
        Key w = find('w');
        Key y = find('y');
        float[][] tile = {{y.bounds.left, w.bounds.bottom}};
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

    @Test
    public void swagPillToggles() {
        int w = view.getWidth();
        float[][] p = {{w - 44 * 3f - 60f, 40f}};
        touch(MotionEvent.ACTION_DOWN, 0, p);
        touch(MotionEvent.ACTION_UP, 0, p);
        assertEquals(1, slangToggles);
    }
}
