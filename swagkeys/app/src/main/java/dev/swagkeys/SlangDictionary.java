package dev.swagkeys;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Single-word slang substitutions applied when a word is completed. Lookup is
 * case-insensitive; the replacement inherits the capitalization pattern of the typed word.
 */
public final class SlangDictionary {

    private static final Map<String, String> ENTRIES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        // Greetings / farewells
        m.put("hello", "wassup");
        m.put("hi", "yo");
        m.put("hey", "ayo");
        m.put("goodbye", "peace out ✌️");
        m.put("bye", "deuces ✌️");
        // People
        m.put("friend", "homie");
        m.put("friends", "homies");
        m.put("buddy", "bestie");
        m.put("dude", "bro");
        m.put("brother", "bro");
        m.put("girlfriend", "bae");
        m.put("boyfriend", "bae");
        m.put("enemy", "opp");
        m.put("enemies", "opps");
        m.put("loser", "L");
        // Praise
        m.put("good", "bussin");
        m.put("great", "fire 🔥");
        m.put("cool", "lit 🔥");
        m.put("awesome", "fire");
        m.put("amazing", "goated 🐐");
        m.put("excellent", "elite");
        m.put("best", "GOAT 🐐");
        m.put("delicious", "bussin");
        m.put("tasty", "bussin");
        m.put("nice", "clean");
        m.put("perfect", "chef's kiss 🤌");
        m.put("stylish", "drippy");
        m.put("fashionable", "drippy");
        m.put("beautiful", "stunning fr");
        m.put("gorgeous", "snatched");
        m.put("impressive", "hard");
        m.put("fun", "a vibe");
        m.put("smart", "big brain");
        m.put("rich", "ballin");
        m.put("win", "W");
        m.put("victory", "W");
        m.put("success", "dub");
        // Criticism
        m.put("bad", "trash");
        m.put("terrible", "straight trash");
        m.put("boring", "mid");
        m.put("average", "mid");
        m.put("mediocre", "mid");
        m.put("fail", "L");
        m.put("failure", "L");
        m.put("awkward", "cringe");
        m.put("embarrassing", "cringe");
        m.put("suspicious", "sus");
        m.put("weird", "sus");
        m.put("fake", "cap");
        // Truth / agreement
        m.put("lie", "cap");
        m.put("lies", "cap");
        m.put("lying", "cappin");
        m.put("seriously", "no cap");
        m.put("honestly", "ngl");
        m.put("truly", "fr fr");
        m.put("really", "deadass");
        m.put("true", "facts");
        m.put("agreed", "bet");
        m.put("okay", "bet");
        m.put("ok", "bet");
        m.put("yes", "bet");
        m.put("no", "nah");
        m.put("very", "hella");
        m.put("extremely", "mad");
        // Feelings
        m.put("angry", "pressed");
        m.put("annoyed", "tilted");
        m.put("jealous", "salty");
        m.put("bitter", "salty");
        m.put("excited", "hyped");
        m.put("tired", "dead");
        m.put("exhausted", "cooked");
        m.put("funny", "dead 💀");
        m.put("hilarious", "dead 💀");
        m.put("lol", "💀");
        m.put("crazy", "wild");
        m.put("wow", "sheesh");
        m.put("relax", "chill");
        m.put("calm", "chill");
        m.put("nervous", "shook");
        m.put("shocked", "shook");
        // Things
        m.put("money", "bands 💸");
        m.put("cash", "bread");
        m.put("house", "crib");
        m.put("home", "crib");
        m.put("car", "whip");
        m.put("shoes", "kicks");
        m.put("sneakers", "kicks");
        m.put("outfit", "fit");
        m.put("clothes", "drip");
        m.put("party", "function");
        m.put("food", "grub");
        m.put("gossip", "tea ☕");
        m.put("drama", "tea ☕");
        m.put("rumor", "tea ☕");
        // Actions / misc
        m.put("leave", "dip");
        m.put("leaving", "dipping");
        m.put("showing", "flexing");
        m.put("boast", "flex");
        m.put("brag", "flex");
        m.put("understand", "get it get it");
        m.put("whatever", "it's whatever");
        m.put("please", "pls");
        m.put("because", "cuz");
        m.put("thanks", "ty fam");
        ENTRIES = Collections.unmodifiableMap(m);
    }

    private SlangDictionary() {}

    public static Map<String, String> entries() {
        return ENTRIES;
    }

    /**
     * Returns the slang replacement for {@code word}, or {@code null} if none applies. Leading
     * and trailing apostrophes (used as quotes) are preserved around the replacement.
     */
    public static String lookup(String word) {
        if (word == null || word.isEmpty()) return null;
        int start = 0;
        int end = word.length();
        while (start < end && isApostrophe(word.charAt(start))) start++;
        while (end > start && isApostrophe(word.charAt(end - 1))) end--;
        if (start == end) return null;
        String core = word.substring(start, end);
        String replacement = ENTRIES.get(core.replace('’', '\'').toLowerCase(Locale.ROOT));
        if (replacement == null) return null;
        return word.substring(0, start) + matchCase(core, replacement) + word.substring(end);
    }

    static boolean isApostrophe(int c) {
        return c == '\'' || c == '’';
    }

    /** Applies the case pattern of {@code typed} (lower, Capitalized, or ALL CAPS) to {@code replacement}. */
    static String matchCase(String typed, String replacement) {
        int letters = 0;
        int uppers = 0;
        for (int i = 0; i < typed.length(); ) {
            int cp = typed.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp)) {
                letters++;
                if (Character.isUpperCase(cp)) uppers++;
            }
        }
        if (letters > 1 && uppers == letters) {
            return replacement.toUpperCase(Locale.ROOT);
        }
        if (letters > 0 && Character.isUpperCase(typed.codePointAt(firstLetterIndex(typed)))) {
            int idx = firstLetterIndex(replacement);
            if (idx < 0) return replacement;
            int cp = replacement.codePointAt(idx);
            return replacement.substring(0, idx)
                    + new String(Character.toChars(Character.toUpperCase(cp)))
                    + replacement.substring(idx + Character.charCount(cp));
        }
        return replacement;
    }

    private static int firstLetterIndex(String s) {
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (Character.isLetter(cp)) return i;
            i += Character.charCount(cp);
        }
        return -1;
    }
}
