package dev.swagkeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
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
            @Override public void onSpaceLongPress() { keys.add(-100); }
            @Override public void onStyleSelected(TextStyle style) { stylesPicked.add(style); }
            @Override public void onSlangToggle() { slangToggles++; }
            @Override public void onSuggestionTap() { keys.add(-101); }
            @Override public void onOpenSettings() { keys.add(-102); }
        });
        view.setField(FieldConfig.Layout.TEXT, EditorInfo.IME_ACTION_NONE, true);
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
        view.setStripState(TextStyle.LEET, true, true, true, "wassup", null);
        assertTrue(shot("01_letters_dark").length() > 0);

        view.setPalette(Palette.LIGHT);
        view.setAutoCaps(true);
        view.setStripState(TextStyle.NORMAL, true, true, true, null, "cool");
        shot("02_letters_light_shift");

        view.setPalette(Palette.DARK);
        tap(find(Key.CODE_MODE_SYMBOLS));
        assertEquals(KeyboardLayouts.Mode.SYMBOLS, view.mode());
        shot("03_symbols");
        tap(find(Key.CODE_MODE_SYMBOLS2));
        shot("04_symbols2");

        view.setField(FieldConfig.Layout.PHONE, EditorInfo.IME_ACTION_DONE, false);
        shot("05_phone");

        view.setField(FieldConfig.Layout.TEXT, EditorInfo.IME_ACTION_SEARCH, false);
        view.setStripState(TextStyle.BOLD, true, false, true, null, null);
        Key e = find('e');
        touch(MotionEvent.ACTION_DOWN, 0, new float[][] {{e.bounds.centerX(), e.bounds.centerY()}});
        shot("06_preview_pressed");
        touch(MotionEvent.ACTION_UP, 0, new float[][] {{e.bounds.centerX(), e.bounds.centerY()}});

        view.setStripState(TextStyle.SCRIPT, true, true, false, null, null);
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
    public void swagPillToggles() {
        int w = view.getWidth();
        float[][] p = {{w - 44 * 3f - 60f, 40f}};
        touch(MotionEvent.ACTION_DOWN, 0, p);
        touch(MotionEvent.ACTION_UP, 0, p);
        assertEquals(1, slangToggles);
    }
}
