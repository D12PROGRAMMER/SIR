package dev.swagkeys;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Setup and preferences screen, also the launcher activity. Built in code to apply the dot font. */
public final class SettingsActivity extends Activity {

    private static final int REQUEST_FONT = 1;
    private static final long MAX_FONT_BYTES = 20L * 1024 * 1024;
    private static final String SAMPLE = "this keyboard is so cool";

    private Prefs prefs;
    private Palette palette;
    private Typeface headFont;
    private Typeface bodyFont;

    private TextView enabledStatus;
    private TextView selectedStatus;
    private TextView fontStatus;
    private RadioGroup fontGroup;
    private RadioButton fontDoto;
    private RadioButton fontCustom;
    /** Set while refreshFontControls updates the group, so its listener ignores the change. */
    private boolean updatingFonts;
    private final EnumMap<TextStyle, RadioButton> styleButtons = new EnumMap<>(TextStyle.class);
    private Switch slangSwitch;
    private TextView keptWordsButton;

    /** Background thread for font import I/O (content providers may block while downloading). */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    // Held in a field: SharedPreferences keeps listeners in a WeakHashMap.
    private final SharedPreferences.OnSharedPreferenceChangeListener prefSync = (sp, key) -> {
        if (key == null || Prefs.KEY_STYLE.equals(key) || Prefs.KEY_SLANG.equals(key)
                || Prefs.KEY_KEPT_WORDS.equals(key)) {
            syncFromPrefs();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        syncFromPrefs();
        prefs.raw().registerOnSharedPreferenceChangeListener(prefSync);
    }

    @Override
    protected void onPause() {
        prefs.raw().unregisterOnSharedPreferenceChangeListener(prefSync);
        super.onPause();
    }

    /** Reflects changes made from the keyboard (style chip, SWAG pill, undone corrections). */
    private void syncFromPrefs() {
        if (slangSwitch == null) return;
        keptWordsButton.setText(keptWordsLabel());
        RadioButton rb = styleButtons.get(prefs.style());
        if (rb != null && !rb.isChecked()) rb.setChecked(true);
        boolean slang = prefs.slang();
        if (slangSwitch.isChecked() != slang) slangSwitch.setChecked(slang);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // The input method picker is a system dialog; refresh after it closes.
        if (hasFocus) refreshStatus();
    }

    // ---------------------------------------------------------------- UI construction

    private void build() {
        palette = Palette.resolve(this, prefs.theme()).withAccent(Palette.accentColor(prefs.accent()));
        headFont = Fonts.dotoBlack(this);
        bodyFont = Typeface.create("sans-serif", Typeface.NORMAL);

        ScrollView scroll = new ScrollView(this);
        // Ids let ScrollView and the test field keep their state across recreate().
        scroll.setId(R.id.settings_scroll);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(palette.background);
        getWindow().getDecorView().setBackgroundColor(palette.background);
        applySystemBars();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // Insets are applied to a wrapper so the ScrollView itself shrinks when the IME opens
        // (API 35+ edge-to-edge); ScrollView then keeps the focused field in view.
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(palette.background);
        frame.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.setOnApplyWindowInsetsListener(SettingsActivity::applyInsets);

        TextView title = text(getString(R.string.app_name).toUpperCase(java.util.Locale.ROOT), 44, palette.text);
        title.setTypeface(headFont);
        root.addView(title);
        TextView subtitle = text(getString(R.string.tagline), 15, palette.textSecondary);
        subtitle.setTypeface(Fonts.dotoBold(this));
        root.addView(subtitle);

        // Setup
        root.addView(header("01  SETUP"));
        enabledStatus = text("", 15, palette.text);
        root.addView(enabledStatus);
        root.addView(button(getString(R.string.enable_keyboard),
                v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
        selectedStatus = text("", 15, palette.text);
        root.addView(selectedStatus);
        root.addView(button(getString(R.string.select_keyboard), v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        }));

        // Style
        root.addView(header("02  STYLE"));
        root.addView(note(getString(R.string.style_note)));
        RadioGroup styles = new RadioGroup(this);
        TextStyle current = prefs.style();
        for (TextStyle s : TextStyle.values()) {
            RadioButton rb = radio(s.label + "   " + s.apply(SAMPLE));
            rb.setId(View.generateViewId());
            rb.setTag(s);
            styleButtons.put(s, rb);
            styles.addView(rb);
            if (s == current) rb.setChecked(true);
        }
        styles.setOnCheckedChangeListener((group, id) -> {
            View v = group.findViewById(id);
            if (v != null && v.getTag() instanceof TextStyle) prefs.setStyle((TextStyle) v.getTag());
        });
        root.addView(styles);

        // Slang
        root.addView(header("03  SWAG SLANG"));
        slangSwitch = toggle(getString(R.string.slang_toggle), prefs.slang(),
                (b, on) -> prefs.setSlang(on));
        root.addView(slangSwitch);
        root.addView(note(getString(R.string.slang_note, SlangDictionary.entries().size())));
        TextView dict = text(dictionaryText(), 14, palette.textSecondary);
        dict.setTypeface(Typeface.MONOSPACE);
        dict.setVisibility(View.GONE);
        root.addView(button(getString(R.string.show_dictionary), v -> {
            boolean show = dict.getVisibility() != View.VISIBLE;
            dict.setVisibility(show ? View.VISIBLE : View.GONE);
            ((TextView) v).setText(show ? R.string.hide_dictionary : R.string.show_dictionary);
        }));
        root.addView(dict);

        // Autocorrect
        root.addView(header("04  AUTOCORRECT"));
        root.addView(toggle(getString(R.string.autocorrect_toggle), prefs.autocorrect(),
                (b, on) -> prefs.setAutocorrect(on)));
        root.addView(note(getString(R.string.autocorrect_note)));
        keptWordsButton = button(keptWordsLabel(), v -> {
            prefs.clearKeptWords();
            keptWordsButton.setText(keptWordsLabel());
        });
        root.addView(keptWordsButton);
        root.addView(toggle(getString(R.string.auto_caps), prefs.autoCaps(),
                (b, on) -> prefs.setAutoCaps(on)));
        root.addView(note(getString(R.string.auto_caps_note)));

        // Theme
        root.addView(header("05  THEME"));
        RadioGroup themes = new RadioGroup(this);
        String[][] themeOptions = {
                {Prefs.THEME_SYSTEM, getString(R.string.theme_system)},
                {Prefs.THEME_DARK, getString(R.string.theme_dark)},
                {Prefs.THEME_LIGHT, getString(R.string.theme_light)},
        };
        for (String[] opt : themeOptions) {
            RadioButton rb = radio(opt[1]);
            rb.setId(View.generateViewId());
            rb.setTag(opt[0]);
            themes.addView(rb);
            if (opt[0].equals(prefs.theme())) rb.setChecked(true);
        }
        themes.setOnCheckedChangeListener((group, id) -> {
            View v = group.findViewById(id);
            if (v == null || !(v.getTag() instanceof String)) return;
            String theme = (String) v.getTag();
            if (theme.equals(prefs.theme())) return;
            prefs.setTheme(theme);
            recreate();
        });
        root.addView(themes);

        // Font
        root.addView(header("06  ACCENT"));
        RadioGroup accents = new RadioGroup(this);
        for (String[] a : Palette.ACCENTS) {
            RadioButton rb = radio(a[1]);
            rb.setId(View.generateViewId());
            rb.setTag(a[0]);
            GradientDrawable swatch = new GradientDrawable();
            swatch.setShape(GradientDrawable.OVAL);
            swatch.setColor(Palette.accentColor(a[0]));
            swatch.setSize(dp(18), dp(18));
            swatch.setStroke(Math.max(1, dp(1)), palette.outline);
            rb.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, swatch, null);
            rb.setCompoundDrawablePadding(dp(12));
            accents.addView(rb);
            if (a[0].equals(prefs.accent())) rb.setChecked(true);
        }
        accents.setOnCheckedChangeListener((group, id) -> {
            View v = group.findViewById(id);
            if (v == null || !(v.getTag() instanceof String)) return;
            String accent = (String) v.getTag();
            if (accent.equals(prefs.accent())) return;
            prefs.setAccent(accent);
            recreate();
        });
        root.addView(accents);

        root.addView(header("07  FONT"));
        root.addView(note(getString(R.string.font_note)));
        RadioGroup fonts = new RadioGroup(this);
        fontGroup = fonts;
        fontDoto = radio(getString(R.string.font_doto));
        fontDoto.setId(View.generateViewId());
        fontCustom = radio(getString(R.string.font_custom));
        fontCustom.setId(View.generateViewId());
        fonts.addView(fontDoto);
        fonts.addView(fontCustom);
        root.addView(fonts);
        fontStatus = text("", 14, palette.textSecondary);
        root.addView(fontStatus);
        root.addView(button(getString(R.string.import_font), v -> pickFont()));
        refreshFontControls();
        fonts.setOnCheckedChangeListener((group, id) -> {
            if (updatingFonts) return;
            if (id == fontDoto.getId()) {
                prefs.setFont(Prefs.FONT_DOTO);
            } else if (id == fontCustom.getId()) {
                if (Fonts.load(Fonts.customFontFile(this)) == null) {
                    Toast.makeText(this, R.string.font_none_imported, Toast.LENGTH_SHORT).show();
                    refreshFontControls();
                    return;
                }
                prefs.setFont(Prefs.FONT_CUSTOM);
            }
            refreshFontControls();
        });

        // Feedback
        root.addView(header("08  KEYBOARD"));
        root.addView(toggle(getString(R.string.number_row), prefs.numberRow(),
                (b, on) -> prefs.setNumberRow(on)));
        root.addView(toggle(getString(R.string.animations), prefs.animations(),
                (b, on) -> prefs.setAnimations(on)));
        root.addView(note(getString(R.string.animations_note)));
        root.addView(header("09  GESTURES"));
        root.addView(note(getString(R.string.gestures_note)));

        root.addView(header("10  FEEDBACK"));
        root.addView(toggle(getString(R.string.haptics), prefs.haptics(), (b, on) -> prefs.setHaptics(on)));
        root.addView(toggle(getString(R.string.sound), prefs.sound(), (b, on) -> prefs.setSound(on)));

        // Try it
        root.addView(header("11  TRY IT"));
        EditText field = new EditText(this);
        field.setId(R.id.try_field);
        field.setHint(R.string.try_hint);
        field.setTextColor(palette.text);
        field.setHintTextColor(palette.textSecondary);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setMinLines(3);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setBackground(outline(palette.key));
        field.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(field, matchWrap(dp(8)));

        // Licenses
        root.addView(header("12  CREDITS"));
        root.addView(note(getString(R.string.credits)));
        TextView ofl = text(readAsset("fonts/OFL.txt"), 11, palette.textSecondary);
        ofl.setTypeface(Typeface.MONOSPACE);
        ofl.setVisibility(View.GONE);
        root.addView(button(getString(R.string.show_license), v -> {
            boolean show = ofl.getVisibility() != View.VISIBLE;
            ofl.setVisibility(show ? View.VISIBLE : View.GONE);
        }));
        root.addView(ofl);
        TextView unicode = text(readAsset("UNICODE-LICENSE.txt"), 11, palette.textSecondary);
        unicode.setTypeface(Typeface.MONOSPACE);
        unicode.setVisibility(View.GONE);
        root.addView(button(getString(R.string.show_unicode_license), v ->
                unicode.setVisibility(unicode.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)));
        root.addView(unicode);
        TextView apache = text(readAsset("APACHE-LICENSE-2.0.txt"), 11, palette.textSecondary);
        apache.setTypeface(Typeface.MONOSPACE);
        apache.setVisibility(View.GONE);
        root.addView(button(getString(R.string.show_apache_license), v ->
                apache.setVisibility(apache.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)));
        root.addView(apache);

        setContentView(frame);
    }

    @SuppressWarnings("deprecation")
    private static WindowInsets applyInsets(View v, WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsets.CONSUMED;
        }
        v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
        return insets.consumeSystemWindowInsets();
    }

    @SuppressWarnings("deprecation")
    private void applySystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(palette.dark ? 0 : light, light);
            }
        } else {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(palette.dark ? flags & ~light : flags | light);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            getWindow().setStatusBarColor(palette.background);
            getWindow().setNavigationBarColor(palette.background);
        }
    }

    private String keptWordsLabel() {
        int n = prefs.keptWords().size();
        // Upper case like button() labels, since it is also set after the button is built.
        return getResources().getQuantityString(R.plurals.forget_kept_words, n, n)
                .toUpperCase(java.util.Locale.ROOT);
    }

    private String dictionaryText() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : SlangDictionary.entries().entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.getKey()).append("  →  ").append(e.getValue());
        }
        return sb.toString();
    }

    private String readAsset(String path) {
        try (InputStream in = getAssets().open(path);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private TextView header(String s) {
        TextView t = text(s, 20, palette.accentInk);
        t.setTypeface(headFont);
        LinearLayout.LayoutParams lp = matchWrap(dp(28));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView note(String s) {
        TextView t = text(s, 14, palette.textSecondary);
        t.setLayoutParams(matchWrap(dp(4)));
        return t;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(bodyFont);
        t.setLayoutParams(matchWrap(dp(4)));
        return t;
    }

    private TextView button(String label, View.OnClickListener onClick) {
        TextView b = text(label.toUpperCase(java.util.Locale.ROOT), 16, palette.text);
        b.setTypeface(Fonts.dotoBold(this));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setBackground(outline(palette.key));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        b.setLayoutParams(matchWrap(dp(10)));
        return b;
    }

    private RadioButton radio(String label) {
        RadioButton rb = new RadioButton(this);
        rb.setText(label);
        rb.setTextColor(palette.text);
        rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        rb.setButtonTintList(tint());
        rb.setPadding(dp(8), dp(8), 0, dp(8));
        return rb;
    }

    private Switch toggle(String label, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(palette.text);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        s.setChecked(checked);
        s.setThumbTintList(tint());
        s.setTrackTintList(tint());
        s.setPadding(0, dp(10), 0, dp(10));
        s.setOnCheckedChangeListener(l);
        s.setLayoutParams(matchWrap(dp(4)));
        return s;
    }

    private ColorStateList tint() {
        return new ColorStateList(
                new int[][] {{android.R.attr.state_checked}, {}},
                new int[] {palette.accentInk, palette.textSecondary});
    }

    private GradientDrawable outline(int fillColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setCornerRadius(dp(14));
        d.setStroke(Math.max(1, dp(1)), palette.outline);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- status

    private void refreshStatus() {
        if (enabledStatus == null) return;
        boolean enabled = isEnabled();
        boolean selected = isSelected();
        enabledStatus.setText(enabled ? R.string.status_enabled : R.string.status_not_enabled);
        enabledStatus.setTextColor(enabled ? palette.text : palette.accentInk);
        selectedStatus.setText(selected ? R.string.status_selected : R.string.status_not_selected);
        selectedStatus.setTextColor(selected ? palette.text : palette.accentInk);
    }

    private String imeId() {
        return new ComponentName(this, SwagKeysService.class).flattenToShortString();
    }

    private boolean isEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        ComponentName self = new ComponentName(this, SwagKeysService.class);
        for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
            if (self.equals(info.getComponent())) return true;
        }
        return false;
    }

    private boolean isSelected() {
        String current = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        if (current == null) return false;
        ComponentName cn = ComponentName.unflattenFromString(current);
        return cn != null && cn.equals(new ComponentName(this, SwagKeysService.class))
                || current.equals(imeId());
    }

    // ---------------------------------------------------------------- font import

    private void refreshFontControls() {
        boolean haveCustom = Fonts.load(Fonts.customFontFile(this)) != null;
        boolean useCustom = haveCustom && Prefs.FONT_CUSTOM.equals(prefs.font());
        updatingFonts = true;
        try {
            fontGroup.check(useCustom ? fontCustom.getId() : fontDoto.getId());
        } finally {
            updatingFonts = false;
        }
        String name = prefs.fontName();
        fontStatus.setText(haveCustom
                ? getString(R.string.font_imported, name == null ? Fonts.CUSTOM_FONT_FILE : name)
                : getString(R.string.font_none_imported));
    }

    @SuppressWarnings("deprecation")
    private void pickFont() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                        "font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf",
                        "application/x-font-otf", "application/font-sfnt", "application/vnd.ms-opentype",
                        "application/octet-stream"});
        try {
            startActivityForResult(intent, REQUEST_FONT);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.font_picker_missing, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FONT || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        File target = Fonts.customFontFile(this);
        File temp = new File(getFilesDir(), Fonts.CUSTOM_FONT_FILE + ".tmp");
        IO.execute(() -> {
            String message;
            String importedName = null;
            try {
                copyLimited(uri, temp);
                if (Fonts.load(temp) == null) {
                    message = getString(R.string.font_invalid);
                } else if (!temp.renameTo(target)) {
                    throw new IOException("rename failed");
                } else {
                    importedName = displayName(uri);
                    message = getString(R.string.font_import_ok);
                }
            } catch (IOException | SecurityException e) {
                message = getString(R.string.font_import_failed, e.getMessage());
            } finally {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
            String toast = message;
            String name = importedName;
            runOnUiThread(() -> {
                if (name != null) prefs.onCustomFontImported(name);
                if (isDestroyed()) return;
                Toast.makeText(this, toast, Toast.LENGTH_LONG).show();
                refreshFontControls();
            });
        });
    }

    private void copyLimited(Uri uri, File dest) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IOException("cannot open");
            byte[] buf = new byte[64 * 1024];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_FONT_BYTES) throw new IOException("file larger than 20 MB");
                out.write(buf, 0, n);
            }
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null) return name;
            }
        } catch (RuntimeException ignored) {
            // Fall through to the URI's last segment.
        }
        String seg = uri.getLastPathSegment();
        return seg == null ? Fonts.CUSTOM_FONT_FILE : seg;
    }
}
