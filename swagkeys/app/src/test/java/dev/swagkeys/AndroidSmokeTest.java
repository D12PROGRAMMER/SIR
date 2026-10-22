package dev.swagkeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = {26, 29, 36})
public class AndroidSmokeTest {

    private static EditorInfo info(int inputType, int imeOptions) {
        EditorInfo ei = new EditorInfo();
        ei.inputType = inputType;
        ei.imeOptions = imeOptions;
        ei.initialSelStart = 0;
        ei.initialSelEnd = 0;
        return ei;
    }

    @Test
    public void fieldConfigClassifiesFields() {
        FieldConfig text = FieldConfig.from(info(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_ACTION_SEND));
        assertEquals(FieldConfig.Mode.STYLED, text.mode);
        assertTrue(text.slangSuggest);
        assertFalse(text.slangAuto); // single-line without AUTO_CORRECT: strip only
        assertTrue(text.capsInputType != 0);
        assertTrue(text.enterIsAction);
        assertEquals(EditorInfo.IME_ACTION_SEND, text.enterActionId);

        FieldConfig pw = FieldConfig.from(info(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE));
        assertEquals(FieldConfig.Mode.PLAIN, pw.mode);
        assertFalse(pw.slangSuggest);

        FieldConfig mail = FieldConfig.from(info(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 0));
        assertEquals(FieldConfig.Mode.PLAIN, mail.mode);
        assertTrue(mail.enterIsAction); // UNSPECIFIED on a single-line field is an action

        FieldConfig noSuggest = FieldConfig.from(info(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 0));
        assertEquals(FieldConfig.Mode.STYLED, noSuggest.mode);
        assertFalse(noSuggest.slangSuggest);

        FieldConfig multi = FieldConfig.from(info(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_ENTER_ACTION));
        assertFalse(multi.enterIsAction);
        assertTrue(multi.slangAuto);

        FieldConfig chat = FieldConfig.from(info(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT, EditorInfo.IME_ACTION_SEND));
        assertTrue(chat.slangAuto);
        FieldConfig name = FieldConfig.from(info(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_MULTI_LINE, 0));
        assertFalse(name.slangAuto);
        assertTrue(name.slangSuggest);
        FieldConfig unspecifiedMulti = FieldConfig.from(info(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE, EditorInfo.IME_ACTION_UNSPECIFIED));
        assertFalse(unspecifiedMulti.enterIsAction);

        EditorInfo labelled = info(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_DONE);
        labelled.actionLabel = "Login";
        labelled.actionId = 4242;
        FieldConfig login = FieldConfig.from(labelled);
        assertTrue(login.enterIsAction);
        assertEquals(4242, login.enterActionId);
        assertEquals("Login", login.enterLabel);

        FieldConfig code = FieldConfig.from(info(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, 0));
        assertEquals(FieldConfig.Mode.PLAIN, code.mode);
        assertTrue(code.capsInputType != 0);

        assertEquals(FieldConfig.Layout.NUMBER,
                FieldConfig.from(info(InputType.TYPE_CLASS_NUMBER, 0)).layout);
        assertEquals(FieldConfig.Layout.PHONE,
                FieldConfig.from(info(InputType.TYPE_CLASS_PHONE, 0)).layout);
        assertEquals(FieldConfig.Mode.RAW, FieldConfig.from(info(InputType.TYPE_NULL, 0)).mode);
    }

    @Test
    public void serviceLifecycleWithoutConnection() {
        SwagKeysService service = Robolectric.setupService(SwagKeysService.class);
        View input = service.onCreateInputView();
        assertNotNull(input);
        EditorInfo ei = info(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_GO);
        service.onStartInput(ei, false);
        service.onStartInputView(ei, false);
        service.onKey('h');
        service.onKey(Key.CODE_DELETE);
        service.onKey(Key.CODE_ENTER);
        service.onStyleSelected(TextStyle.BUBBLE);
        assertEquals(TextStyle.BUBBLE, new Prefs(service).style());
        service.onSlangToggle();
        assertFalse(new Prefs(service).slang());
        service.onEmoji("😂");
        service.onEmoji("🔥");
        service.onEmoji("😂");
        assertEquals(java.util.List.of("😂", "🔥"), new Prefs(service).recentEmojis());
        service.onFinishInputView(true);
        service.onFinishInput();
        service.onDestroy();
    }

    @Test
    public void recentEmojiNotLearnedFromPrivateFields() {
        assertTrue(SwagKeysService.allowsLearning(info(InputType.TYPE_CLASS_TEXT, 0)));
        assertFalse(SwagKeysService.allowsLearning(info(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD, 0)));
        assertFalse(SwagKeysService.allowsLearning(info(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, 0)));
        assertFalse(SwagKeysService.allowsLearning(info(InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)));
    }

    @Test
    public void settingsActivityBuilds() {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class).setup().get();
        assertNotNull(activity.findViewById(android.R.id.content));
        new Prefs(activity).setTheme(Prefs.THEME_LIGHT);
        Robolectric.buildActivity(SettingsActivity.class).setup();
    }

    @Test
    public void fontLoadingRejectsGarbageAndAcceptsDoto() throws IOException {
        Context ctx = RuntimeEnvironment.getApplication();
        File garbage = new File(ctx.getFilesDir(), "garbage.ttf");
        try (FileOutputStream out = new FileOutputStream(garbage)) {
            out.write("not a font".getBytes());
        }
        assertNull(Fonts.load(garbage));
        assertNull(Fonts.load(new File(ctx.getFilesDir(), "missing.ttf")));

        Prefs prefs = new Prefs(ctx);
        prefs.setFont(Prefs.FONT_CUSTOM);
        Typeface[] faces = Fonts.resolve(ctx, prefs);
        assertEquals(Fonts.dotoBlack(ctx), faces[0]);
    }
}
