package dev.swagkeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.Locale;
import java.util.Map;

public class TextTransformTest {

    @Test
    public void leet() {
        assertEquals("h3ll0 w0rld 7357 4b1", TextStyle.LEET.apply("hello world TEST abi"));
    }

    @Test
    public void bold() {
        assertEquals("𝐀𝐳𝟎𝟗!", TextStyle.BOLD.apply("Az09!"));
    }

    @Test
    public void script() {
        assertEquals("𝓐𝔃09", TextStyle.SCRIPT.apply("Az09"));
    }

    @Test
    public void wide() {
        assertEquals("ｈｉ！　～", TextStyle.WIDE.apply("hi! ~"));
    }

    @Test
    public void bubble() {
        assertEquals("ⓐⓩⒶⓏ⓪①⑨", TextStyle.BUBBLE.apply("azAZ019"));
    }

    @Test
    public void smallCaps() {
        assertEquals("ꜱᴍᴀʟʟ Cᴀᴘꜱ ǫx", TextStyle.SMALL_CAPS.apply("small Caps qx"));
    }

    @Test
    public void strike() {
        assertEquals("a̶b̶ !̶", TextStyle.STRIKE.apply("ab !"));
    }

    @Test
    public void mocking() {
        assertEquals("sPoNgE-bOb", TextStyle.MOCKING.apply("SPONGE-BOB"));
    }

    @Test
    public void normalIsIdentity() {
        assertEquals("Héllo 👍", TextStyle.NORMAL.apply("Héllo 👍"));
    }

    @Test
    public void stylesPassThroughSupplementaryCodePoints() {
        for (TextStyle s : TextStyle.values()) {
            String out = s.apply("👍");
            assertEquals(s.name(), "👍", s == TextStyle.STRIKE ? out.substring(0, 2) : out);
        }
    }

    @Test
    public void idsRoundTrip() {
        for (TextStyle s : TextStyle.values()) assertSame(s, TextStyle.fromId(s.id));
        assertSame(TextStyle.NORMAL, TextStyle.fromId("missing"));
    }

    @Test
    public void slangLookup() {
        assertEquals("wassup", SlangDictionary.lookup("hello"));
        assertEquals("Wassup", SlangDictionary.lookup("Hello"));
        assertEquals("WASSUP", SlangDictionary.lookup("HELLO"));
        assertEquals("Bet", SlangDictionary.lookup("Ok"));
        assertEquals("BET", SlangDictionary.lookup("OK"));
        assertEquals("'lit 🔥'", SlangDictionary.lookup("'cool'"));
        assertEquals("💀", SlangDictionary.lookup("LOL"));
        assertEquals("Chef's kiss 🤌", SlangDictionary.lookup("Perfect"));
        assertNull(SlangDictionary.lookup("keyboard"));
        assertNull(SlangDictionary.lookup("''"));
        assertNull(SlangDictionary.lookup(""));
    }

    @Test
    public void dictionaryKeysAreNormalizedSingleWords() {
        for (Map.Entry<String, String> e : SlangDictionary.entries().entrySet()) {
            String k = e.getKey();
            assertEquals(k, k.toLowerCase(Locale.ROOT), k);
            for (int i = 0; i < k.length(); i++) {
                assertEquals(k, true, InputLogic.isWordCodePoint(k.charAt(i)));
            }
        }
    }
}
