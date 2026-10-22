package dev.swagkeys;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The autocorrect dictionary ({@code assets/words.txt}, built by {@code tools/build_words.py}
 * from the AOSP LatinIME English wordlist). Lines are sorted by UTF-8 bytes; each holds a
 * lowercased word, followed by a tab and a frequency (1-255) when the word may be used as a
 * correction. Kept as one byte array searched in place, without a per-word object.
 */
public final class WordList {
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
                if (i > lineStart) lines++;
                lineStart = i + 1;
            }
        }
        starts = new int[lines];
        int n = 0;
        lineStart = 0;
        for (int i = 0; i <= data.length; i++) {
            if (i == data.length || data[i] == '\n') {
                if (i > lineStart) starts[n++] = lineStart;
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
                return frequencyAt(starts[mid] + key.length);
            }
        }
        return -1;
    }

    /** Compares the word on the line at {@code start} with {@code key} as unsigned bytes. */
    private int compare(int start, byte[] key) {
        for (int i = 0; ; i++) {
            int p = start + i;
            int b = p < data.length ? data[p] & 0xFF : '\n';
            boolean end = b == '\t' || b == '\n';
            if (i == key.length) return end ? 0 : 1;
            if (end) return -1;
            int k = key[i] & 0xFF;
            if (b != k) return b < k ? -1 : 1;
        }
    }

    private int frequencyAt(int p) {
        if (p >= data.length || data[p] != '\t') return 0;
        int f = 0;
        for (p++; p < data.length && data[p] >= '0' && data[p] <= '9'; p++) f = f * 10 + (data[p] - '0');
        return f;
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
