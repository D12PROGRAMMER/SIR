package dev.swagkeys;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Typo correction for finished words. Only plain ASCII-letter words that are not in the
 * dictionary are corrected, to a dictionary word one edit away (with keyboard-aware costs).
 * The result keeps the case pattern the user typed (lowercase stays lowercase, Capitalized
 * stays Capitalized): no capitalization, apostrophes or punctuation are ever added. Left
 * alone: ALL-CAPS words (acronyms), listed chat words and abbreviations, words of four or more
 * letters without vowels ("smth"), dropped g's ("goin"), and stretched or doubled endings
 * ("yesss", "heyy"). Edits that are rarely typos are not considered: substituting a far key
 * for a consonant, and dropping a first or last letter that is not doubled and not a
 * neighbouring key. Three-letter words are only corrected for swapped letters ("teh") or a
 * neighbouring key in one of the most common words ("thw").
 *
 * <p>Thresholds were tuned on synthetic keyboard typos of common words, against the frequent
 * English tokens missing from the word list (names, brands, foreign words) as false positives.
 */
public final class Autocorrect {

    static final int MIN_LENGTH = 3;
    static final int MAX_LENGTH = 24;
    /** The best candidate must beat the runner-up by this much, or the word is left alone. */
    static final int MIN_MARGIN = 12;
    /** Minimum frequency of a correction of a three-letter word: only the most common words. */
    static final int MIN_SHORT_TARGET_FREQUENCY = 161;

    private static final int COST_TRANSPOSE = 10;
    private static final int COST_SUB_NEAR = 12;
    private static final int COST_SUB_FAR = 30;
    private static final int COST_DELETE_DOUBLE = 8;     // typed "helllo"
    private static final int COST_DELETE_NEAR = 12;      // fat finger: extra neighbouring key
    private static final int COST_DELETE = 18;
    private static final int COST_INSERT_DOUBLE = 10;    // typed "hapy"
    private static final int COST_INSERT = 20;

    /** Slang, chat words and abbreviations this keyboard should never "fix". */
    static final Set<String> CHAT_WORDS = new HashSet<>(Arrays.asList(
            "abt", "aight", "alr", "altho", "amogus", "argh", "aura", "bae", "based", "bday",
            "bdays", "bestie", "bet", "betta", "bff", "bffr", "bffs", "boi", "boutta", "boyz",
            "bro", "bruh", "bruv", "bsf", "bussin", "cap", "cappin", "chem", "chill", "chopped",
            "convo", "convos", "coulda", "crashout", "cringe", "cringy", "crypto", "cuz", "cya",
            "dat", "dawg", "dayum", "deadass", "delulu", "dem", "dey", "dis", "dm", "dmed",
            "dming", "dms", "dope", "drip", "drippy", "esports", "fam", "fanum", "fav", "fave",
            "faves", "favs", "finna", "fomo", "fr", "frfr", "fyi", "gif", "gifs", "gimme",
            "glazing", "glitchy", "gmorning", "gnight", "goated", "gonna", "gots", "gotta", "gurl",
            "gyat", "gyatt", "hbu", "hehe", "hehehe", "hella", "heyo", "highkey", "hmu", "holo",
            "homie", "homies", "hru", "hubby", "hun", "huzz", "icl", "idc", "idgaf", "idk", "ight",
            "ikr", "ily", "imma", "imo", "improv", "indi", "innit", "insta", "irl", "istg", "kek",
            "kpop", "lemme", "lit", "lmao", "lmfao", "lol", "lowkey", "lul", "luv", "luvs", "mah",
            "mald", "manga", "mewing", "mid", "musta", "nah", "naw", "ngl", "nite", "noob", "npc",
            "nub", "nvm", "nya", "ofc", "omfg", "omg", "oml", "omw", "ong", "oop", "opp", "opps",
            "owo", "periodt", "pfp", "pls", "plz", "pmo", "pog", "poggers", "pov", "purr", "pwned",
            "rizz", "rizzed", "rizzler", "robo", "rofl", "salty", "shawty", "sheesh", "shook",
            "shoulda", "sigma", "simp", "simping", "situationship", "skibidi", "slay", "smh",
            "smol", "smth", "snatched", "soz", "srry", "srsly", "sry", "stan", "stfu", "strat",
            "sus", "sussy", "tbh", "thanx", "thicc", "tho", "thot", "thx", "tiktok", "tmr", "tmrw",
            "todo", "tonite", "tryna", "ttyl", "tuff", "tysm", "tyvm", "tyy", "uber", "unc", "uwu",
            "vape", "vapes", "vaping", "venmo", "vibe", "vibes", "vibin", "vibing", "vids", "vlog",
            "wack", "wanna", "wassup", "wat", "watcha", "wbu", "welp", "whatcha", "whatevs",
            "wifey", "wot", "woulda", "wtf", "wth", "wut", "wya", "wyd", "xtreme", "yall", "yap",
            "yapping", "yas", "yass", "yea", "yee", "yeet", "yeeted", "yeh", "yer", "yolo", "yoy",
            "yuh", "yung"));

    private enum Case { LOWER, CAPITALIZED }

    private Autocorrect() {}

    /**
     * Returns the correction for {@code typed}, or null to leave it as typed. {@code never}
     * holds lowercased words the user kept (undid a correction of).
     */
    public static String correct(WordList words, String typed, Set<String> never) {
        int n = typed.length();
        if (words == null || n < MIN_LENGTH || n > MAX_LENGTH) return null;
        Case pattern = casePattern(typed);
        if (pattern == null) return null; // ALL CAPS (acronyms), mixed case, non-letters
        String w = typed.toLowerCase(Locale.ROOT);
        if (words.lookup(w) >= 0 || isChatWord(w) || (never != null && never.contains(w))
                || hasStretch(w) || (n >= 4 && !hasVowel(w)) || isDroppedG(words, w)
                || isDoubledEnding(words, w)) {
            return null;
        }
        Map<String, Integer> scores = new HashMap<>();
        char[] c = w.toCharArray();
        // Typed three-letter non-words are mostly abbreviations ("cuz", "abt").
        boolean shortWord = n <= 3;
        int minFrequency = shortWord ? MIN_SHORT_TARGET_FREQUENCY : 1;
        // Transposition of neighbours ("teh").
        for (int i = 0; i + 1 < n; i++) {
            if (c[i] == c[i + 1]) continue;
            char[] t = c.clone();
            t[i] = c[i + 1];
            t[i + 1] = c[i];
            score(words, new String(t), COST_TRANSPOSE, minFrequency, scores);
        }
        // Substitution: a neighbouring key ("hwllo"), or a vowel for a vowel ("seperate").
        for (int i = 0; i < n; i++) {
            for (char x = 'a'; x <= 'z'; x++) {
                if (x == c[i]) continue;
                boolean near = adjacent(c[i], x);
                if (!near && (shortWord || !isVowel(c[i]) || !isVowel(x))) continue;
                char[] t = c.clone();
                t[i] = x;
                score(words, new String(t), near ? COST_SUB_NEAR : COST_SUB_FAR, minFrequency, scores);
            }
        }
        // Deletion: the typed word has an extra letter ("heello", "heklo"). An extra first or
        // last letter is a slip only next to its double or a neighbouring key ("bday" stays).
        for (int i = 0; i < n && !shortWord; i++) {
            boolean doubled = (i > 0 && c[i - 1] == c[i]) || (i + 1 < n && c[i + 1] == c[i]);
            boolean near = (i > 0 && adjacent(c[i - 1], c[i])) || (i + 1 < n && adjacent(c[i + 1], c[i]));
            if ((i == 0 || i == n - 1) && !doubled && !near) continue;
            String t = w.substring(0, i) + w.substring(i + 1);
            score(words, t, doubled ? COST_DELETE_DOUBLE : near ? COST_DELETE_NEAR : COST_DELETE, 1, scores);
        }
        // Insertion: the typed word is missing a letter ("hapy", "helo").
        for (int i = 0; i <= n && !shortWord; i++) {
            for (char x = 'a'; x <= 'z'; x++) {
                boolean doubled = (i > 0 && c[i - 1] == x) || (i < n && c[i] == x);
                String t = w.substring(0, i) + x + w.substring(i);
                score(words, t, doubled ? COST_INSERT_DOUBLE : COST_INSERT, 1, scores);
            }
        }
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        int second = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            int s = e.getValue();
            if (s > bestScore || (s == bestScore && e.getKey().compareTo(best) < 0)) {
                second = bestScore;
                bestScore = s;
                best = e.getKey();
            } else if (s > second) {
                second = s;
            }
        }
        if (best == null || bestScore < minScore(n)) return null;
        if (second != Integer.MIN_VALUE && bestScore - second < MIN_MARGIN) return null;
        return applyCase(best, pattern);
    }

    /**
     * Minimum frequency minus edit cost (frequencies are 1-255) for a correction of a word of
     * {@code length} letters: short unknown words are more often names or abbreviations.
     */
    static int minScore(int length) {
        return length <= 4 ? 110 : length == 5 ? 90 : length == 6 ? 75 : 60;
    }

    private static void score(WordList words, String candidate, int cost, int minFrequency,
            Map<String, Integer> scores) {
        if (candidate.length() < 2) return;
        int f = words.lookup(candidate);
        if (f <= 0 || f < minFrequency) return; // unknown, known but never suggested, or too rare
        int s = f - cost;
        Integer prev = scores.get(candidate);
        if (prev == null || s > prev) scores.put(candidate, s);
    }

    private static boolean isChatWord(String w) {
        return CHAT_WORDS.contains(w) || SlangDictionary.entries().containsKey(w);
    }

    private static boolean isVowel(char ch) {
        return ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u';
    }

    /** Words without a vowel ("smth", "thx", "ppl") are abbreviations. */
    static boolean hasVowel(String w) {
        for (int i = 0; i < w.length(); i++) {
            if ("aeiouy".indexOf(w.charAt(i)) >= 0) return true;
        }
        return false;
    }

    /** "goin", "nothin": a dropped g, written on purpose. */
    private static boolean isDroppedG(WordList words, String w) {
        return w.endsWith("in") && words.lookup(w + "g") >= 0;
    }

    /** "heyy", "hii", "bruhh": a short word with its last letter doubled for emphasis. */
    private static boolean isDoubledEnding(WordList words, String w) {
        int n = w.length();
        if (w.charAt(n - 1) != w.charAt(n - 2)) return false;
        String base = w.substring(0, n - 1);
        return isChatWord(base) || (base.length() <= 4 && words.lookup(base) >= 0);
    }

    /** "yesss", "nooo", "lmaooo": stretched for emphasis, not a typo. */
    static boolean hasStretch(String w) {
        for (int i = 2; i < w.length(); i++) {
            if (w.charAt(i) == w.charAt(i - 1) && w.charAt(i) == w.charAt(i - 2)) return true;
        }
        return false;
    }

    private static Case casePattern(String typed) {
        boolean allUpper = true;
        boolean restLower = true;
        for (int i = 0; i < typed.length(); i++) {
            char ch = typed.charAt(i);
            boolean lower = ch >= 'a' && ch <= 'z';
            boolean upper = ch >= 'A' && ch <= 'Z';
            if (!lower && !upper) return null;
            if (lower) allUpper = false;
            if (i > 0 && upper) restLower = false;
        }
        if (allUpper || !restLower) return null; // "OTP", "iPhone": leave it
        return Character.isUpperCase(typed.charAt(0)) ? Case.CAPITALIZED : Case.LOWER;
    }

    private static String applyCase(String word, Case pattern) {
        return pattern == Case.CAPITALIZED ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word;
    }

    private static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    private static final float[] ROW_OFFSETS = {0f, 0.5f, 1.5f};

    /** Whether two letters are neighbours on a QWERTY layout. */
    static boolean adjacent(char a, char b) {
        if (a == b) return false;
        int ra = -1;
        int rb = -1;
        float xa = 0;
        float xb = 0;
        for (int r = 0; r < ROWS.length; r++) {
            int ia = ROWS[r].indexOf(a);
            int ib = ROWS[r].indexOf(b);
            if (ia >= 0) {
                ra = r;
                xa = ia + ROW_OFFSETS[r];
            }
            if (ib >= 0) {
                rb = r;
                xb = ib + ROW_OFFSETS[r];
            }
        }
        if (ra < 0 || rb < 0 || Math.abs(ra - rb) > 1) return false;
        float dx = Math.abs(xa - xb);
        return dx <= 1f;
    }
}
