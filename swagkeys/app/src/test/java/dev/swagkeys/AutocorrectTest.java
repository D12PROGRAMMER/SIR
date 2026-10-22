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
        assertEquals(-1, words.lookup("￿"));
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
