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
    public void pickSlangCommitsWithSpace() {
        start("", FieldConfig.Mode.STYLED, true, TextStyle.NORMAL);
        type("hello");
        assertEquals("wassup", logic.pendingSlang());
        logic.pickSlang();
        editor.deliverUpdates();
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
        assertEquals("wassup\nbet\n", editor.value());
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

    @Test
    public void unknownInitialSelectionIsAdopted() {
        editor = new FakeEditor("abc");
        logic = new InputLogic(editor, null);
        editor.logic = logic;
        logic.startInput(new FieldConfig(FieldConfig.Mode.STYLED, FieldConfig.Layout.TEXT, true, 0,
                ACTION_NONE, -1, -1));
        type(" hello ");
        assertEquals("abc wassup ", editor.value());
    }
}
