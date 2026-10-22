package dev.swagkeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

public class InputLogicTest {

    private static final int ACTION_NONE = 1;
    private static final int ACTION_SEND = 4;
    private static final int CAP_SENTENCES_TYPE = 0x00000001 | 0x00004000;

    private FakeEditor editor;
    private InputLogic logic;

    private void start(String initial, FieldConfig.Mode mode, boolean slang, TextStyle style) {
        editor = new FakeEditor(initial);
        logic = new InputLogic(editor, null);
        editor.logic = logic;
        logic.setStyle(style);
        logic.startInput(editor.config(mode, slang, 0, ACTION_NONE));
    }

    private void type(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\n') logic.onEnter();
            else logic.onCodePoint(cp);
            editor.deliverUpdates();
        }
    }

    @Test
    public void slangReplacesCompletedWords() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("hello friend this is so cool ");
        assertEquals("wassup homie this is so lit 🔥 ", editor.value());
    }

    @Test
    public void slangPreservesCapitalization() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("Hello GOOD good, ");
        assertEquals("Wassup BUSSIN bussin, ", editor.value());
    }

    @Test
    public void leetAppliesToReplacementAndComposing() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.LEET);
        type("hel");
        assertEquals("h3l", editor.value());
        assertEquals("h3l", editor.composing());
        assertEquals("hel", logic.composingWord());
        type("lo friend ");
        assertEquals("w455up h0m13 ", editor.value());
    }

    @Test
    public void unicodeStylesRoundTripThroughComposing() {
        start("", FieldConfig.Mode.STYLED, false, TextStyle.BOLD);
        type("hey");
        assertEquals("𝐡𝐞𝐲", editor.composing());
        logic.onDelete();
        editor.deliverUpdates();
        assertEquals("𝐡𝐞", editor.value());
        type("y you");
        assertEquals("𝐡𝐞𝐲 𝐲𝐨𝐮", editor.value());
    }

    @Test
    public void changingStyleRerendersComposingWord() {
        start("", FieldConfig.Mode.STYLED, false, TextStyle.NORMAL);
        type("abc");
        logic.setStyle(TextStyle.WIDE);
        editor.deliverUpdates();
        assertEquals("ａｂｃ", editor.value());
        type(" d");
        assertEquals("ａｂｃ　ｄ", editor.value());
    }

    @Test
    public void deleteRightAfterSlangRevertsIt() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("so cool ");
        assertEquals("so lit 🔥 ", editor.value());
        assertEquals("cool", logic.undoableWord());
        logic.onDelete();
        editor.deliverUpdates();
        assertEquals("so cool ", editor.value());
        assertNull(logic.undoableWord());
        logic.onDelete();
        editor.deliverUpdates();
        assertEquals("so cool", editor.value());
    }

    @Test
    public void undoNotOfferedAfterCursorMoves() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("cool ");
        editor.userMoveCursor(0);
        editor.deliverUpdates();
        assertNull(logic.undoableWord());
    }

    @Test
    public void pickSlangCommitsReplacementWithOwedSpace() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("hello");
        assertEquals("wassup", logic.pendingSlang());
        logic.pickSlang();
        editor.deliverUpdates();
        assertEquals("wassup", editor.value());
        type(" ");
        assertEquals("wassup ", editor.value());
    }

    @Test
    public void slangDisabledLeavesWords() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        logic.setSlangEnabled(false);
        type("hello cool ");
        assertEquals("hello cool ", editor.value());
        assertNull(logic.pendingSlang());
    }

    @Test
    public void plainFieldsIgnoreStyleAndSlang() {
        start("", FieldConfig.Mode.PLAIN, false, TextStyle.LEET);
        type("hello cool ");
        assertEquals("hello cool ", editor.value());
        assertNull(editor.composing());
        assertFalse(logic.stylesActive());
    }

    @Test
    public void belatedSelectionUpdatesDoNotSplitTheWord() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        // Type without delivering updates, then deliver them all late and interleaved.
        logic.onCodePoint('h');
        logic.onCodePoint('e');
        editor.deliverOne();
        logic.onCodePoint('l');
        editor.deliverOne();
        logic.onCodePoint('l');
        logic.onCodePoint('o');
        editor.deliverUpdates();
        assertEquals("hello", logic.composingWord());
        logic.onCodePoint(' ');
        editor.deliverUpdates();
        assertEquals("wassup ", editor.value());
    }

    @Test
    public void userCursorMoveFinishesComposingWithoutDuplication() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("hel");
        editor.userMoveCursor(0);
        editor.deliverUpdates();
        assertEquals("", logic.composingWord());
        assertNull(editor.composing());
        type("x ");
        assertEquals("x hel", editor.value());
    }

    @Test
    public void appClearingFieldResetsState() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("hi");
        editor.appSetText("");
        editor.deliverUpdates();
        type("yo ");
        assertEquals("yo ", editor.value());
    }

    @Test
    public void typingReplacesSelection() {
        start("abc def", FieldConfig.Mode.STYLED, false, TextStyle.NORMAL);
        editor.userSelect(0, 3);
        editor.deliverUpdates();
        type("z ");
        assertEquals("z  def", editor.value());
    }

    @Test
    public void deleteRemovesWholeGraphemeClusters() {
        start("a👍🏽", FieldConfig.Mode.STYLED, false, TextStyle.NORMAL);
        logic.onDelete();
        editor.deliverUpdates();
        assertEquals("a", editor.value());

        start("x", FieldConfig.Mode.STYLED, false, TextStyle.STRIKE);
        type("ab ");
        assertEquals("xa̶b̶ ", editor.value());
        logic.onDelete();
        editor.deliverUpdates();
        logic.onDelete();
        editor.deliverUpdates();
        assertEquals("xa̶", editor.value());
    }

    @Test
    public void deleteWithSelectionDeletesSelection() {
        start("hello world", FieldConfig.Mode.STYLED, false, TextStyle.NORMAL);
        editor.userSelect(5, 11);
        editor.deliverUpdates();
        logic.onDelete();
        editor.deliverUpdates();
        assertEquals("hello", editor.value());
    }

    @Test
    public void enterPerformsActionAfterCommittingSlang() {
        editor = new FakeEditor("");
        logic = new InputLogic(editor, null);
        editor.logic = logic;
        logic.startInput(editor.config(FieldConfig.Mode.STYLED, true, 0, ACTION_SEND));
        type("hello");
        logic.onEnter();
        editor.deliverUpdates();
        assertEquals("wassup", editor.value());
        assertEquals(Collections.singletonList(ACTION_SEND), editor.actions);
    }

    @Test
    public void enterWithoutActionInsertsNewline() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("hello\nok\n");
        assertEquals("wassup\naight\n", editor.value());
    }

    @Test
    public void apostrophesStayInsideWords() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("don't 'cool' ");
        assertEquals("don't 'lit 🔥' ", editor.value());
    }

    @Test
    public void mockingAlternatesPerWord() {
        start("", FieldConfig.Mode.STYLED, false, TextStyle.MOCKING);
        type("hello world ");
        assertEquals("hElLo wOrLd ", editor.value());
    }

    @Test
    public void autoCapitalizationFollowsEditorCapsMode() {
        editor = new FakeEditor("");
        logic = new InputLogic(editor, null);
        editor.logic = logic;
        logic.startInput(editor.config(FieldConfig.Mode.STYLED, false, CAP_SENTENCES_TYPE, ACTION_NONE));
        assertTrue(logic.shouldAutoCapitalize());
        type("H");
        assertFalse(logic.shouldAutoCapitalize());
        type("i. ");
        assertTrue(logic.shouldAutoCapitalize());
    }

    @Test
    public void rawModeSendsKeyChars() {
        start("", FieldConfig.Mode.RAW, false, TextStyle.LEET);
        type("ls -la");
        assertEquals("ls -la", editor.value());
        logic.onDelete();
        editor.deliverUpdates();
        assertEquals("ls -l", editor.value());
    }

    @Test
    public void finishInputLeavesComposingTextAsTyped() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.LEET);
        type("hello");
        logic.finishInput();
        assertEquals("h3ll0", editor.value());
        assertNull(editor.composing());
    }

    private void startWith(FakeEditor e, FieldConfig config, TextStyle style) {
        editor = e;
        logic = new InputLogic(editor, null);
        editor.logic = logic;
        logic.setStyle(style);
        logic.startInput(config);
    }

    /** Types each character, delivering selection updates one keystroke late. */
    private void typeLagged(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') logic.onDelete();
            else logic.onCodePoint(c);
            editor.deliverAllButLast();
        }
        editor.deliverUpdates();
    }

    @Test
    public void imeRestartWithSameFieldKeepsComposedText() {
        start("abc", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type(" hel");
        // Configuration-change reset path: startInput again on the live connection.
        logic.startInput(editor.config(FieldConfig.Mode.STYLED, true, 0, ACTION_NONE));
        type("x");
        assertEquals("abc helx", editor.value());
    }

    @Test
    public void capCharactersCapitalizesEveryLetter() {
        startWith(new FakeEditor(""), FieldConfig.text(FieldConfig.Mode.STYLED, false,
                0x00000001 | 0x00001000, ACTION_NONE, 0, 0), TextStyle.NORMAL);
        assertTrue(logic.shouldAutoCapitalize());
        type("A");
        assertTrue(logic.shouldAutoCapitalize());
    }

    @Test
    public void laggingUpdatesWithDeleteKeepTheWordTogether() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        typeLagged("helo<lo ");
        assertEquals("wassup ", editor.value());
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        typeLagged("hi<i ");
        assertEquals("yo ", editor.value());
    }

    @Test
    public void unknownInitialSelectionWithLaggingUpdates() {
        startWith(new FakeEditor(""), FieldConfig.text(FieldConfig.Mode.STYLED, true, 0,
                ACTION_NONE, -1, -1), TextStyle.NORMAL);
        logic.onCodePoint('h');
        logic.onCodePoint('e');
        editor.deliverOne();
        logic.onCodePoint('l');
        editor.deliverOne();
        logic.onCodePoint('l');
        editor.deliverOne();
        logic.onCodePoint('o');
        editor.deliverUpdates();
        type(" ");
        assertEquals("wassup ", editor.value());
    }

    @Test
    public void customActionIdIsSentForActionLabel() {
        startWith(new FakeEditor(""), new FieldConfig(FieldConfig.Mode.STYLED,
                FieldConfig.Layout.TEXT, true, true, 0, true, 1234, 6, "Login", 0, 0),
                TextStyle.NORMAL);
        type("hi");
        logic.onEnter();
        assertEquals(Collections.singletonList(1234), editor.actions);
        assertEquals("yo", editor.value());
    }

    @Test
    public void wideAndStrikeKeepSentenceCaps() {
        for (TextStyle s : new TextStyle[] {TextStyle.WIDE, TextStyle.STRIKE}) {
            startWith(new FakeEditor(""), FieldConfig.text(FieldConfig.Mode.STYLED, false,
                    CAP_SENTENCES_TYPE, ACTION_NONE, 0, 0), s);
            type("hi. ");
            assertTrue(s.name(), logic.shouldAutoCapitalize());
            type("x");
            assertFalse(s.name(), logic.shouldAutoCapitalize());
        }
    }

    @Test
    public void wordsAttachedToTextAreNotReplaced() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("#fun #cool @nice_guy ");
        assertEquals("#fun #cool @nice_guy ", editor.value());

        // Correcting a typo: the remainder typed after Delete continues the earlier word.
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("this lokk ");
        logic.onDelete();
        editor.deliverUpdates();
        logic.onDelete();
        editor.deliverUpdates();
        logic.onDelete();
        editor.deliverUpdates();
        type("ok ");
        assertEquals("this look ", editor.value());
    }

    @Test
    public void joiningSeparatorsKeepTokensIntact() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("mail good.cop@gmail.com cool_kid no-show home/user ");
        assertEquals("mail good.cop@gmail.com cool_kid no-show home/user ", editor.value());

        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("Hi. Cool, ");
        assertEquals("Yo. Lit 🔥, ", editor.value());
    }

    @Test
    public void suggestOnlyFieldsOfferButDoNotReplace() {
        startWith(new FakeEditor(""), new FieldConfig(FieldConfig.Mode.STYLED,
                FieldConfig.Layout.TEXT, true, false, 0, false, 1, 1, null, 0, 0),
                TextStyle.NORMAL);
        type("best car ");
        assertEquals("best car ", editor.value());
        type("cool");
        assertEquals("lit 🔥", logic.pendingSlang());
        logic.pickSlang();
        editor.deliverUpdates();
        assertEquals("best car lit 🔥", editor.value());
    }

    @Test
    public void pickedSlangOwesASpaceUntilNeeded() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("hello");
        logic.pickSlang();
        editor.deliverUpdates();
        assertEquals("wassup", editor.value());
        type("?! ok ");
        assertEquals("wassup?! aight ", editor.value());

        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("hello");
        logic.pickSlang();
        editor.deliverUpdates();
        type("x");
        assertEquals("wassup x", editor.value());

        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("hello");
        logic.pickSlang();
        editor.deliverUpdates();
        logic.onDelete();
        editor.deliverUpdates();
        assertEquals("hello", editor.value());
    }

    @Test
    public void graphemeDeletionJoinsEmojiZwjSequences() {
        assertEquals(6, InputLogic.lastGraphemeLength("x🏳️‍🌈"));
        assertEquals(1, InputLogic.lastGraphemeLength("ab"));
        // Devanagari conjunct with ZWJ: the platform deletes the last letter only.
        assertEquals(1, InputLogic.lastGraphemeLength("क्‍ष"));
    }

    @Test
    public void unknownInitialSelectionIsAdopted() {
        editor = new FakeEditor("abc");
        logic = new InputLogic(editor, null);
        editor.logic = logic;
        logic.startInput(FieldConfig.text(FieldConfig.Mode.STYLED, true, 0, ACTION_NONE, -1, -1));
        type(" hello ");
        assertEquals("abc wassup ", editor.value());
    }
}
