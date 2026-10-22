package dev.swagkeys;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The autocorrect dictionary ({@code assets/words.txt}, built by {@code tools/build_words.py}
 * from the AOSP LatinIME English wordlists). Lines are sorted by UTF-8 bytes; each holds a
 * lowercased word, a tab, and a frequency (0-255) prefixed with the entry kind: none for a
 * {@link #TARGET}, {@code =} for {@link #KNOWN}, {@code ~} for {@link #WEAK}. A bare word is
 * known with frequency 0. Kept as one byte array searched in place, without a per-word object.
 */
public final class WordList {
    /** May be used as a correction. */
    public static final int TARGET = 0;
    /** Never corrected or suggested; competes with corrections as a word the user may mean. */
    public static final int KNOWN = 1;
    /** Like {@link #KNOWN}, but typed it competes with corrections instead of blocking them. */
    public static final int WEAK = 2;

    private static final String ASSET = "words.txt";
    private static volatile WordList instance;

    private final byte[] data;
    private final int[] starts;

    public WordList(byte[] data) {
        this.data = data;
        int lines = 0;
        int lineStart = 0;
        for (int i = 0; i <= data.length; i++) {
            if (i == data.length || data[i] == '\n') {
                if (i > lineStart && data[lineStart] != '\r') lines++;
                lineStart = i + 1;
            }
        }
        starts = new int[lines];
        int n = 0;
        lineStart = 0;
        for (int i = 0; i <= data.length; i++) {
            if (i == data.length || data[i] == '\n') {
                if (i > lineStart && data[lineStart] != '\r') starts[n++] = lineStart;
                lineStart = i + 1;
            }
        }
    }

    public int size() {
        return starts.length;
    }

    /**
     * Looks up a lowercased word: its correction frequency (1-255) if it may be suggested,
     * 0 if it is a known word that is never suggested, -1 if it is unknown.
     */
    public int lookup(String lowerWord) {
        int e = entry(lowerWord);
        return e < 0 ? -1 : kind(e) == TARGET ? frequency(e) : 0;
    }

    /** The entry for a lowercased word, decoded with {@link #kind} and {@link #frequency}; -1 if unknown. */
    public int entry(String lowerWord) {
        byte[] key = lowerWord.getBytes(StandardCharsets.UTF_8);
        int lo = 0;
        int hi = starts.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = compare(starts[mid], key);
            if (c < 0) {
                lo = mid + 1;
            } else if (c > 0) {
                hi = mid - 1;
            } else {
                return entryAt(starts[mid] + key.length);
            }
        }
        return -1;
    }

    /** Compares the word on the line at {@code start} with {@code key} as unsigned bytes. */
    private int compare(int start, byte[] key) {
        for (int i = 0; ; i++) {
            int p = start + i;
            int b = p < data.length ? data[p] & 0xFF : '\n';
            boolean end = b == '\t' || b == '\n' || b == '\r';
            if (i == key.length) return end ? 0 : 1;
            if (end) return -1;
            int k = key[i] & 0xFF;
            if (b != k) return b < k ? -1 : 1;
        }
    }

    public static int kind(int entry) {
        return entry >>> 8;
    }

    public static int frequency(int entry) {
        return entry & 0xFF;
    }

    private int entryAt(int p) {
        if (p >= data.length || data[p] != '\t') return KNOWN << 8;
        p++;
        int kind = TARGET;
        if (p < data.length && data[p] == '=') {
            kind = KNOWN;
            p++;
        } else if (p < data.length && data[p] == '~') {
            kind = WEAK;
            p++;
        }
        int f = 0;
        for (; p < data.length && data[p] >= '0' && data[p] <= '9'; p++) f = f * 10 + (data[p] - '0');
        f = Math.min(f, 255);
        // A target needs a frequency; without one the word is only known.
        if (kind == TARGET && f == 0) kind = KNOWN;
        return kind << 8 | f;
    }

    /** The loaded list, or null while it is still loading (callers then skip autocorrect). */
    public static WordList peek() {
        return instance;
    }

    /** Loads the list on a background thread; idempotent. */
    public static void preload(Context context) {
        if (instance != null) return;
        Context app = context.getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                WordList loaded = load(app);
                synchronized (WordList.class) {
                    if (instance == null) instance = loaded;
                }
            } catch (IOException e) {
                // No dictionary: autocorrect stays off.
            }
        }, "words-preload");
        t.setDaemon(true);
        t.start();
    }

    static WordList load(Context context) throws IOException {
        try (InputStream in = context.getAssets().open(ASSET)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(2 * 1024 * 1024);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new WordList(out.toByteArray());
        }
    }
}
