package dev.swagkeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Autocorrect against the bundled word list, and its integration with {@link InputLogic}. */
public class AutocorrectTest {

    private static final int ACTION_NONE = 1;
    private static final int CAP_SENTENCES_TYPE = 0x00000001 | 0x00004000;

    private static WordList words;

    private FakeEditor editor;
    private InputLogic logic;
    private final Set<String> kept = new HashSet<>();
    private final List<String> keptEvents = new ArrayList<>();

    @BeforeClass
    public static void loadWords() throws IOException {
        // Gradle runs unit tests with the module directory as the working directory.
        words = new WordList(Files.readAllBytes(Paths.get("src/main/assets/words.txt")));
    }

    private String fix(String typed) {
        return Autocorrect.correct(words, typed, kept);
    }

    // ---------------------------------------------------------------- word list

    @Test
    public void wordListIsSortedAndLoaded() throws IOException {
        assertTrue(words.size() > 150_000);
        List<String> lines = Files.readAllLines(Paths.get("src/main/assets/words.txt"),
                StandardCharsets.UTF_8);
        String prev = null;
        for (String line : lines) {
            String w = line.split("\t", 2)[0];
            assertFalse("blank line", w.isEmpty());
            if (prev != null) {
                // Binary search needs UTF-8 byte order; for these strings it equals char order
                // except across surrogates, which the list does not contain.
                assertTrue(prev + " >= " + w, prev.compareTo(w) < 0);
            }
            prev = w;
        }
    }

    @Test
    public void lookupDistinguishesTargetsKnownAndUnknown() {
        assertTrue(words.lookup("the") > 200);
        assertTrue(words.lookup("hello") > 0);
        assertEquals(0, words.lookup("dont"));
        assertEquals(0, words.lookup("monday"));
        assertEquals(-1, words.lookup("teh"));
        assertEquals(-1, words.lookup("zzzzqx"));
        assertEquals(-1, words.lookup(""));
        assertEquals(-1, words.lookup("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertEquals(-1, words.lookup("\uFFFF"));
    }

    @Test
    public void wordListToleratesMissingFinalNewline() {
        WordList w = new WordList("abc\t5\nabd\nabe\t7".getBytes(StandardCharsets.UTF_8));
        assertEquals(3, w.size());
        assertEquals(5, w.lookup("abc"));
        assertEquals(0, w.lookup("abd"));
        assertEquals(7, w.lookup("abe"));
        assertEquals(-1, w.lookup("ab"));
        assertEquals(-1, w.lookup("abcd"));
        assertEquals(0, new WordList(new byte[0]).size());
        assertEquals(-1, new WordList(new byte[0]).lookup("a"));
    }

    @Test
    public void wordListEntryKindsAndCrlf() {
        WordList w = new WordList("abc\t5\r\nabd\t=9\r\n\r\nabe\t~3\r\nabf\r\nabg\t0\n"
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(5, w.size());
        assertEquals(WordList.TARGET, WordList.kind(w.entry("abc")));
        assertEquals(5, WordList.frequency(w.entry("abc")));
        assertEquals(WordList.KNOWN, WordList.kind(w.entry("abd")));
        assertEquals(9, WordList.frequency(w.entry("abd")));
        assertEquals(WordList.WEAK, WordList.kind(w.entry("abe")));
        assertEquals(3, WordList.frequency(w.entry("abe")));
        assertEquals(WordList.KNOWN, WordList.kind(w.entry("abf")));
        // A target without a frequency is only known.
        assertEquals(WordList.KNOWN, WordList.kind(w.entry("abg")));
        assertEquals(5, w.lookup("abc"));
        assertEquals(0, w.lookup("abd"));
        assertEquals(0, w.lookup("abe"));
        assertEquals(-1, w.lookup("ab"));
        assertEquals(-1, w.lookup(""));
    }

    @Test
    public void bundledListHasKindsForContractionsNamesAndPossessives() {
        assertEquals(WordList.KNOWN, WordList.kind(words.entry("dont")));
        assertEquals(WordList.KNOWN, WordList.kind(words.entry("wasnt")));
        assertEquals(WordList.KNOWN, WordList.kind(words.entry("monday")));
        assertEquals(WordList.KNOWN, WordList.kind(words.entry("colour")));
        assertEquals(WordList.WEAK, WordList.kind(words.entry("oday")));
        assertEquals(WordList.KNOWN, WordList.kind(words.entry("kates")));
        assertTrue(WordList.frequency(words.entry("dont")) > 100);
    }

    // ---------------------------------------------------------------- corrections

    @Test
    public void fixesCommonTypos() {
        assertEquals("the", fix("teh"));
        assertEquals("hello", fix("hwllo"));
        assertEquals("hello", fix("heello"));
        assertEquals("until", fix("untill"));
        assertEquals("and", fix("adn"));
        assertEquals("not", fix("nto"));
        assertEquals("happy", fix("hapy"));
        assertEquals("because", fix("becuase"));
        assertEquals("people", fix("peopel"));
        assertEquals("would", fix("woudl"));
        assertEquals("tomorrow", fix("tomorow"));
        assertEquals("really", fix("realyl"));
        assertEquals("their", fix("thier"));
        assertEquals("something", fix("somthing"));
    }

    @Test
    public void keepsTheTypedCasePattern() {
        assertEquals("The", fix("Teh"));
        assertEquals("Hello", fix("Hwllo"));
        // Mixed case is someone's spelling on purpose; ALL CAPS words are mostly acronyms.
        assertNull(fix("tEh"));
        assertNull(fix("iPhonee"));
        for (String w : new String[] {"TEH", "HWLLO", "EST", "OTP", "OCD", "GIFS"}) {
            assertNull(w, fix(w));
        }
    }

    @Test
    public void leavesChatSpellingsNamesAndBritishSpellingAlone() {
        for (String w : new String[] {"hehe", "imma", "shoulda", "coulda", "woulda", "gurl",
                "bday", "thanx", "chem", "convo", "faves", "favs", "smol", "boi", "heyo", "welp",
                "dawg", "watcha", "crypto", "uber", "venmo", "vape", "manga", "dming", "dmed",
                "esports", "gifs", "hella", "yea", "nah", "wat", "colour", "grey", "centre",
                "cancelled", "favourite", "kent"}) {
            assertNull(w, fix(w));
        }
    }

    @Test
    public void neverCapitalizesOrAddsApostrophes() {
        for (String w : new String[] {"i", "im", "monday", "dont", "cant", "wont", "ive", "youre",
                "thats", "john", "london", "okay", "ok", "thru", "alot"}) {
            assertNull(w, fix(w));
        }
    }

    @Test
    public void leavesKnownWordsSlangAndStretchedWordsAlone() {
        for (String w : new String[] {"nut", "but", "form", "from", "bruh", "rizz", "ngl", "idk",
                "sus", "yeet", "lowkey", "yesss", "nooo", "lmaooo", "helllo", "hello", "cool",
                "goated", "wassup", "aight",
                // Abbreviations, vowel-less words, three-letter non-words (swaps only).
                "smth", "thx", "ppl", "tmrw", "cuz", "abt", "ofc", "luv", "lov", "wth", "kpop",
                // Dropped g's and doubled endings.
                "goin", "doin", "nothin", "chillin", "heyy", "hii", "okk", "youu", "bruhh"}) {
            assertNull(w, fix(w));
        }
    }

    @Test
    public void leavesShortLongAndNonLetterWordsAlone() {
        assertNull(fix("hte".substring(0, 2)));
        assertNull(fix("teh1"));
        assertNull(fix("te'h"));
        assertNull(fix("héllo"));
        assertNull(fix("abcdefghijklmnopqrstuvwxyz"));
        assertNull(Autocorrect.correct(null, "teh", kept));
    }

    @Test
    public void typosThatMayMeanAKnownWordAreLeftAlone() {
        // Days, contractions without apostrophes and UK spellings can't be suggested, but a
        // typo that fits them about as well is not turned into something else.
        for (String w : new String[] {"monay", "mondy", "Monay", "havetn", "coulnt", "wsant",
                "thetre", "theure", "colur", "honur", "jine", "dient", "kates"}) {
            assertNull(w, fix(w));
        }
    }

    @Test
    public void possessivesWithoutApostropheStayButOtherNameFormsDoNotBlock() {
        for (String w : new String[] {"megans", "Megans", "sams", "brians", "dans", "zoes",
                "billys", "legos", "atms"}) {
            assertNull(w, fix(w));
        }
        // "oday" (from "O'Day") is much less likely than "today".
        assertEquals("today", fix("oday"));
    }

    @Test
    public void keptWordsAreNotCorrected() {
        kept.add("teh");
        assertNull(fix("teh"));
        assertNull(fix("Teh"));
    }

    @Test
    public void ambiguousTyposAreLeftAlone() {
        // Gibberish with no single clear neighbour stays as typed.
        assertNull(fix("qzxv"));
        assertNull(fix("xkcdq"));
    }

    @Test
    public void adjacencyFollowsQwerty() {
        assertTrue(Autocorrect.adjacent('e', 'w'));
        assertTrue(Autocorrect.adjacent('e', 'r'));
        assertTrue(Autocorrect.adjacent('e', 'd'));
        assertTrue(Autocorrect.adjacent('g', 'b'));
        assertFalse(Autocorrect.adjacent('e', 'e'));
        assertFalse(Autocorrect.adjacent('q', 'p'));
        assertFalse(Autocorrect.adjacent('a', 'm'));
        assertFalse(Autocorrect.adjacent('1', 'q'));
    }

    // ---------------------------------------------------------------- typing

    private void start(boolean auto, boolean slangOn, TextStyle style) {
        editor = new FakeEditor("");
        logic = new InputLogic(editor, new InputLogic.Listener() {
            @Override
            public void onInputStateChanged() {}

            @Override
            public void onWordKept(String word) {
                keptEvents.add(word);
                kept.add(word.toLowerCase(java.util.Locale.ROOT));
            }
        });
        editor.logic = logic;
        logic.setStyle(style);
        logic.setSlangEnabled(slangOn);
        logic.setCorrector(this::fix);
        logic.startInput(new FieldConfig(FieldConfig.Mode.STYLED, FieldConfig.Layout.TEXT,
                true, auto, CAP_SENTENCES_TYPE, false, ACTION_NONE, ACTION_NONE, null, 0, 0));
    }

    private void type(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '<') logic.onDelete();
            else if (cp == '\n') logic.onEnter();
            else logic.onCodePoint(cp);
            editor.deliverUpdates();
        }
    }

    /** Types like {@link #type}, with the editor's selection updates one keystroke late. */
    private void typeLagged(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '<') logic.onDelete();
            else logic.onCodePoint(cp);
            editor.deliverAllButLast();
        }
        editor.deliverUpdates();
    }

    @Test
    public void lateUpdatesKeepTheUndoOfStyledCorrections() {
        for (TextStyle style : new TextStyle[] {TextStyle.STRIKE, TextStyle.BOLD, TextStyle.CURSED,
                TextStyle.NORMAL}) {
            start(true, false, style);
            typeLagged("heello ");
            assertEquals(style.name(), style.apply("hello "), editor.value());
            assertEquals(style.name(), "heello", logic.undoableWord());
            typeLagged("<");
            assertEquals(style.name(), style.apply("heello "), editor.value());
            start(true, false, style);
            typeLagged("heello wrold friend ");
            // Each word is styled on its own (CURSED marks depend on the letter's position).
            String expected = style.apply("hello") + style.apply(" ") + style.apply("world")
                    + style.apply(" ") + style.apply("friend") + style.apply(" ");
            assertEquals(style.name(), expected, editor.value());
        }
    }

    /** Types with the editor's selection updates {@code lag} keystrokes late. */
    private void typeLagged(String s, int lag) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '<') logic.onDelete();
            else logic.onCodePoint(cp);
            editor.deliverAllBut(lag);
        }
        editor.deliverUpdates();
    }

    @Test
    public void repeatedStatesUnderLagAreMatchedInOrder() {
        for (int lag = 0; lag <= 6; lag++) {
            start(true, false, TextStyle.NORMAL);
            typeLagged("hello x<hapy ", lag);
            assertEquals("lag " + lag, "hello happy ", editor.value());
            start(true, false, TextStyle.NORMAL);
            typeLagged("tehh< ", lag);
            assertEquals("lag " + lag, "the ", editor.value());
            assertEquals("lag " + lag, "teh", logic.undoableWord());
        }
    }

    @Test
    public void tapBackAfterSameLengthUndoIsAUserMove() {
        start(true, false, TextStyle.NORMAL);
        type("teh ");
        type("<");
        assertEquals("teh ", editor.value());
        // The same-length revert changes neither selection nor composing: no report follows.
        logic.moveCursor(-1);
        editor.deliverUpdates();
        editor.userMoveCursor(4);
        editor.deliverUpdates();
        type("hapy ");
        assertEquals("teh happy ", editor.value());
    }

    @Test
    public void tapAfterCoalescedBurstIsAUserMove() {
        // ',' Delete '!' reach the editor in one frame and are reported once.
        startWith("hi");
        editor.coalesce = true;
        type(",<!");
        editor.flush();
        editor.deliverUpdates();
        editor.userMoveCursor(2);
        editor.flush();
        editor.deliverUpdates();
        editor.coalesce = false;
        type(" tehx< ");
        assertEquals("hi the !", editor.value());
        assertEquals("teh", logic.undoableWord());
    }

    @Test
    public void tapAfterUnreportedBurstIsAUserMove() {
        // '.' Delete end where the editor last reported: nothing is reported at all.
        startWith("ab cd");
        editor.userMoveCursor(2);
        editor.deliverUpdates();
        editor.coalesce = true;
        type(".<");
        editor.flush();
        editor.deliverUpdates();
        editor.userMoveCursor(3);
        editor.flush();
        editor.deliverUpdates();
        editor.coalesce = false;
        type("teh ");
        assertEquals("ab the cd", editor.value());
        assertEquals("teh", logic.undoableWord());
    }

    @Test
    public void appClearAfterCoalescedBurstResetsTheWord() {
        startWith("");
        editor.coalesce = true;
        type("o<o");
        editor.flush();
        editor.deliverUpdates();
        editor.appSetText("");
        editor.flush();
        editor.deliverUpdates();
        editor.coalesce = false;
        type("k");
        assertEquals("k", editor.value());
    }

    @Test
    public void coalescedTapIntoComposingWordIsAUserMove() {
        startWith("");
        type("hi ");
        editor.coalesce = true;
        type("b");
        editor.userMoveCursor(3);
        editor.flush();
        editor.deliverUpdates();
        editor.coalesce = false;
        type("o");
        assertEquals("hi ob", editor.value());
    }

    @Test
    public void appRemovingTheComposingSpanEndsTheWord() {
        startWith("");
        type("ahi");
        editor.appRemoveComposing();
        editor.deliverUpdates();
        type("o");
        assertEquals("ahio", editor.value());
    }

    @Test
    public void typingWhileArrowReportsLag() {
        for (int lag = 0; lag <= 3; lag++) {
            startWith("so ok");
            logic.moveCursor(-2);
            editor.deliverAllBut(lag);
            typeLagged("teh ", lag);
            assertEquals("lag " + lag, "so the ok", editor.value());
            assertEquals("lag " + lag, "teh", logic.undoableWord());
        }
    }

    @Test
    public void lateReportsAfterWordDelete() {
        for (TextStyle style : new TextStyle[] {TextStyle.BOLD, TextStyle.CURSED, TextStyle.NORMAL}) {
            for (int lag = 0; lag <= 6; lag++) {
                start(true, false, style);
                type("hi x");
                logic.deleteWords(2);
                editor.deliverAllBut(lag);
                typeLagged("teh ", lag);
                assertEquals(style + " lag " + lag, style.apply("the") + style.apply(" "),
                        editor.value());
                assertEquals(style + " lag " + lag, "teh", logic.undoableWord());
            }
        }
    }

    @Test
    public void withoutSelectionReadsTheReportHeuristicStillTracksTaps() {
        startWith("ab cd");
        editor.selectionReadable = false;
        editor.userMoveCursor(2);
        editor.deliverUpdates();
        type(" teh");
        editor.userMoveCursor(0);
        editor.deliverUpdates();
        type("x");
        assertEquals("xab teh cd", editor.value());
    }

    /** Starts a chat field holding {@code text}, cursor at its end. */
    private void startWith(String text) {
        start(true, false, TextStyle.NORMAL);
        editor.appSetText(text);
        logic.startInput(new FieldConfig(FieldConfig.Mode.STYLED, FieldConfig.Layout.TEXT,
                true, true, CAP_SENTENCES_TYPE, false, ACTION_NONE, ACTION_NONE, null,
                text.length(), text.length()));
        editor.deliverUpdates();
    }

    @Test
    public void lateUpdatesStillKeepAWordAfterDelete() {
        start(true, false, TextStyle.NORMAL);
        typeLagged("hapy < ok ");
        assertEquals("hapy  ok ", editor.value());
        assertEquals(Collections.singletonList("hapy"), keptEvents);
    }

    @Test
    public void closingQuoteAfterPeriodKeepsTheCorrection() {
        start(true, false, TextStyle.NORMAL);
        type("she said 'so hapy.' ok \u2018so hapy.\u2019 ok ");
        assertEquals("she said 'so happy.' ok \u2018so happy.\u2019 ok ", editor.value());
        start(true, false, TextStyle.NORMAL);
        type("teh.com ");
        assertEquals("teh.com ", editor.value());
    }

    @Test
    public void correctsFinishedWordsAsTyped() {
        start(true, false, TextStyle.NORMAL);
        type("i think teh dog is hapy, monday Teh end. ");
        assertEquals("i think the dog is happy, monday The end. ", editor.value());
    }

    @Test
    public void correctionIsPendingWhileTyping() {
        start(true, false, TextStyle.NORMAL);
        type("teh");
        assertEquals("the", logic.pendingSlang());
        assertEquals("teh", editor.value());
        type("1");
        assertNull(logic.pendingSlang());
    }

    @Test
    public void slangAppliesToTheCorrectedWord() {
        start(true, true, TextStyle.NORMAL);
        type("hwllo ");
        assertEquals("wassup ", editor.value());
        assertTrue(logic.undoIsSlang());
        type("<");
        assertEquals("hwllo ", editor.value());
        // Slang was involved, so the typo itself is not learned.
        assertTrue(keptEvents.isEmpty());
    }

    @Test
    public void deleteUndoesACorrectionAndKeepsTheWord() {
        start(true, false, TextStyle.NORMAL);
        type("teh ");
        assertEquals("the ", editor.value());
        assertEquals("teh", logic.undoableWord());
        assertFalse(logic.undoIsSlang());
        type("<");
        assertEquals("teh ", editor.value());
        // Kept once typing goes on, before the next word is finished.
        assertTrue(keptEvents.isEmpty());
        type("teh ");
        assertEquals(Collections.singletonList("teh"), keptEvents);
        assertEquals("teh teh ", editor.value());
    }

    @Test
    public void heldDeleteErasesWithoutKeepingTheTypo() {
        start(true, false, TextStyle.NORMAL);
        type("a adn ");
        assertEquals("a and ", editor.value());
        // A held Delete: the first event restores the typed word, the rest erase it.
        type("<<<<<");
        assertEquals("a ", editor.value());
        assertTrue(keptEvents.isEmpty());
        type("adn ");
        assertEquals("a and ", editor.value());
    }

    @Test
    public void deletingAnEmojiAfterACorrectionDoesNotKeepTheTypo() {
        start(true, false, TextStyle.NORMAL);
        type("hapy");
        logic.onEmoji("\uD83D\uDE00");
        editor.deliverUpdates();
        assertEquals("happy\uD83D\uDE00", editor.value());
        type("<");
        assertEquals("hapy\uD83D\uDE00", editor.value());
        type("<");
        assertEquals("hapy", editor.value());
        assertTrue(keptEvents.isEmpty());
    }

    @Test
    public void undoTapKeepsTheWordAtOnce() {
        start(true, false, TextStyle.NORMAL);
        type("teh ");
        assertTrue(logic.undoSlang());
        editor.deliverUpdates();
        assertEquals("teh ", editor.value());
        assertEquals(Collections.singletonList("teh"), keptEvents);
    }

    @Test
    public void movingAwayAfterDeleteUndoDoesNotKeepTheWord() {
        start(true, false, TextStyle.NORMAL);
        type("teh ");
        type("<");
        assertEquals("teh ", editor.value());
        editor.userMoveCursor(0);
        editor.deliverUpdates();
        type("x");
        assertTrue(keptEvents.isEmpty());
    }

    @Test
    public void continuingTheTokenRevertsWithoutLearning() {
        start(true, false, TextStyle.NORMAL);
        type("teh.com ");
        assertEquals("teh.com ", editor.value());
        assertTrue(keptEvents.isEmpty());
    }

    @Test
    public void suggestOnlyFieldsOfferTheCorrectionInTheStrip() {
        start(false, false, TextStyle.NORMAL);
        type("teh");
        assertEquals("the", logic.pendingSlang());
        type(" ");
        assertEquals("teh ", editor.value());
        type("teh");
        logic.pickSlang();
        type("x");
        assertEquals("teh the x", editor.value());
        // An undone pick is not learned either.
        start(false, false, TextStyle.NORMAL);
        type("teh");
        logic.pickSlang();
        editor.deliverUpdates();
        logic.undoSlang();
        editor.deliverUpdates();
        assertEquals("teh", editor.value());
        assertTrue(keptEvents.isEmpty());
    }

    @Test
    public void correctionIsStyledLikeTheWord() {
        start(true, false, TextStyle.LEET);
        type("teh ");
        assertEquals("7h3 ", editor.value());
    }

    @Test
    public void noCorrectionWhenOff() {
        start(true, false, TextStyle.NORMAL);
        logic.setCorrector(null);
        type("teh ");
        assertEquals("teh ", editor.value());
        assertNull(logic.undoableWord());
    }

    @Test
    public void wordsGluedToTextAreNotCorrected() {
        start(true, false, TextStyle.NORMAL);
        type("#teh @teh teh-teh teh_ ");
        assertEquals("#teh @teh teh-teh teh_ ", editor.value());
    }

    @Test
    public void noAutoCapitalizationUnlessTurnedOn() {
        start(true, false, TextStyle.NORMAL);
        assertFalse(logic.shouldAutoCapitalize());
        type("hi. ");
        assertFalse(logic.shouldAutoCapitalize());
        logic.setAutoCapitalize(true);
        assertTrue(logic.shouldAutoCapitalize());
        // Not even fields asking for all caps.
        logic.setAutoCapitalize(false);
        logic.startInput(FieldConfig.text(FieldConfig.Mode.STYLED, false,
                0x00000001 | 0x00001000, ACTION_NONE, 0, 0));
        assertFalse(logic.shouldAutoCapitalize());
    }
}
