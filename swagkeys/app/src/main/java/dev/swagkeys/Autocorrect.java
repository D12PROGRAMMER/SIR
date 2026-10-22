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
 * stays Capitalized, ALL CAPS stays ALL CAPS): no capitalization, apostrophes or punctuation
 * are ever added. Chat spellings are left alone: listed chat words and abbreviations, words
 * without vowels ("smth"), dropped g's ("goin"), and stretched or doubled endings ("yesss",
 * "heyy"). Three-letter words are only corrected for swapped letters ("teh").
 */
public final class Autocorrect {

    static final int MIN_LENGTH = 3;
    static final int MAX_LENGTH = 24;
    /** Minimum frequency minus edit cost (frequencies are 1-255) for a correction. */
    static final int MIN_SCORE = 60;
    /** The best candidate must beat the runner-up by this much, or the word is left alone. */
    static final int MIN_MARGIN = 8;

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
            "abt", "aight", "amogus", "aura", "bae", "based", "bestie", "bet", "bff", "bffr",
            "bffs", "boutta", "bro", "bruh", "bruv", "bsf", "bussin", "cap", "cappin", "chill",
            "chopped", "crashout", "cringe", "cringy", "cuz", "deadass", "delulu", "dope", "drip",
            "drippy", "fam", "fanum", "finna", "fomo", "fr", "frfr", "fyi", "gimme", "glazing",
            "goated", "gonna", "gotta", "gyat", "gyatt", "hbu", "highkey", "hmu", "homie",
            "homies", "hru", "huzz", "icl", "idc", "idgaf", "idk", "ight", "ikr", "ily", "imo",
            "innit", "irl", "istg", "kek", "kpop", "lemme", "lit", "lmao", "lmfao", "lol",
            "lowkey", "luv", "lul", "mewing", "mid", "ngl", "noob", "npc", "nub", "nvm", "ofc",
            "omfg", "omg", "oml", "omw", "ong", "opp", "opps", "owo", "periodt", "pfp", "pls",
            "plz", "pmo", "pog", "poggers", "pov", "purr", "rizz", "rizzed", "rizzler", "rofl",
            "salty", "shawty", "sheesh", "shook", "sigma", "simp", "simping", "situationship",
            "skibidi", "slay", "smh", "smth", "snatched", "srsly", "stan", "stfu", "sus", "sussy",
            "tbh", "thicc", "tho", "thx", "tmr", "tmrw", "tuff", "tryna", "ttyl", "tysm", "unc",
            "uwu", "vibe", "vibes", "vibin", "vibing", "wanna", "wassup", "wbu", "whatevs",
            "wot", "wth", "wtf", "wut", "wyd", "wya", "yall", "yap", "yapping", "yass", "yee",
            "yeeted", "yeet", "yeh", "yolo", "yuh"));

    private enum Case { LOWER, CAPITALIZED, UPPER }

    private Autocorrect() {}

    /**
     * Returns the correction for {@code typed}, or null to leave it as typed. {@code never}
     * holds lowercased words the user kept (undid a correction of).
     */
    public static String correct(WordList words, String typed, Set<String> never) {
        int n = typed.length();
        if (words == null || n < MIN_LENGTH || n > MAX_LENGTH) return null;
        Case pattern = casePattern(typed);
        if (pattern == null) return null;
        String w = typed.toLowerCase(Locale.ROOT);
        if (words.lookup(w) >= 0 || isChatWord(w) || (never != null && never.contains(w))
                || hasStretch(w) || !hasVowel(w) || isDroppedG(words, w) || isDoubledEnding(words, w)) {
            return null;
        }
        Map<String, Integer> scores = new HashMap<>();
        char[] c = w.toCharArray();
        // Typed three-letter non-words are mostly abbreviations ("cuz", "abt"); fix swaps only.
        boolean swapsOnly = n <= 3;
        // Transposition of neighbours ("teh").
        for (int i = 0; i + 1 < n; i++) {
            if (c[i] == c[i + 1]) continue;
            char[] t = c.clone();
            t[i] = c[i + 1];
            t[i + 1] = c[i];
            score(words, new String(t), COST_TRANSPOSE, scores);
        }
        // Substitution ("hwllo").
        for (int i = 0; i < n && !swapsOnly; i++) {
            for (char x = 'a'; x <= 'z'; x++) {
                if (x == c[i]) continue;
                char[] t = c.clone();
                t[i] = x;
                score(words, new String(t), adjacent(c[i], x) ? COST_SUB_NEAR : COST_SUB_FAR, scores);
            }
        }
        // Deletion: the typed word has an extra letter ("heello", "heklo").
        for (int i = 0; i < n && !swapsOnly; i++) {
            boolean doubled = (i > 0 && c[i - 1] == c[i]) || (i + 1 < n && c[i + 1] == c[i]);
            boolean near = (i > 0 && adjacent(c[i - 1], c[i])) || (i + 1 < n && adjacent(c[i + 1], c[i]));
            String t = w.substring(0, i) + w.substring(i + 1);
            score(words, t, doubled ? COST_DELETE_DOUBLE : near ? COST_DELETE_NEAR : COST_DELETE, scores);
        }
        // Insertion: the typed word is missing a letter ("hapy", "helo").
        for (int i = 0; i <= n && !swapsOnly; i++) {
            for (char x = 'a'; x <= 'z'; x++) {
                boolean doubled = (i > 0 && c[i - 1] == x) || (i < n && c[i] == x);
                String t = w.substring(0, i) + x + w.substring(i);
                score(words, t, doubled ? COST_INSERT_DOUBLE : COST_INSERT, scores);
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
        if (best == null || bestScore < MIN_SCORE) return null;
        if (second != Integer.MIN_VALUE && bestScore - second < MIN_MARGIN) return null;
        return applyCase(best, pattern);
    }

    private static void score(WordList words, String candidate, int cost, Map<String, Integer> scores) {
        if (candidate.length() < 2) return;
        int f = words.lookup(candidate);
        if (f <= 0) return; // unknown, or known but never suggested
        int s = f - cost;
        Integer prev = scores.get(candidate);
        if (prev == null || s > prev) scores.put(candidate, s);
    }

    private static boolean isChatWord(String w) {
        return CHAT_WORDS.contains(w) || SlangDictionary.entries().containsKey(w);
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
        if (allUpper) return Case.UPPER;
        if (!restLower) return null; // mixed case such as "iPhone": leave it
        return Character.isUpperCase(typed.charAt(0)) ? Case.CAPITALIZED : Case.LOWER;
    }

    private static String applyCase(String word, Case pattern) {
        switch (pattern) {
            case UPPER:
                return word.toUpperCase(Locale.ROOT);
            case CAPITALIZED:
                return Character.toUpperCase(word.charAt(0)) + word.substring(1);
            default:
                return word;
        }
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
        return ra == rb ? dx <= 1f : dx <= 1f;
    }
}
