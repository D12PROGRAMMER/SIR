package dev.swagkeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates the generated emoji asset against the counts in its header (Unicode emoji-test.txt). */
public class EmojiDataTest {

    private static boolean hasTone(String s) {
        return s.codePoints().anyMatch(cp -> cp >= 0x1F3FB && cp <= 0x1F3FF);
    }

    @Test
    public void assetContainsEveryFullyQualifiedEmojiOnce() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("src/main/assets/emoji.txt"),
                StandardCharsets.UTF_8);
        String header = lines.get(0);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+) emoji, (\\d+) skin-tone variants").matcher(header);
        assertTrue(header, m.find());
        int expectedBase = Integer.parseInt(m.group(1));
        int expectedVariants = Integer.parseInt(m.group(2));

        Set<String> seen = new HashSet<>();
        int base = 0;
        int variants = 0;
        int groups = 0;
        for (String line : lines) {
            if (line.isEmpty() || line.startsWith("//")) continue;
            if (line.startsWith("@")) {
                groups++;
                continue;
            }
            String[] parts = line.split(" ");
            assertFalse(line, hasTone(parts[0]));
            base++;
            for (int i = 1; i < parts.length; i++) {
                assertTrue(line, hasTone(parts[i]));
                variants++;
            }
            for (String p : parts) assertTrue("duplicate " + p, seen.add(p));
        }
        assertEquals(9, groups);
        assertEquals(expectedBase, base);
        assertEquals(expectedVariants, variants);
        assertEquals(3963, base + variants); // fully-qualified count in Emoji 18.0
    }
}
