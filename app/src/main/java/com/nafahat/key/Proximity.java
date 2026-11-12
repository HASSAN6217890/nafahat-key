package com.nafahat.key;

/** Keyboard-neighbour table and weighted edit distance used for autocorrection. */
final class Proximity {
    private Proximity() { }

    private static final int SIZE = 26 + 42;
    private static final boolean[][] ADJ = new boolean[SIZE][SIZE];

    static {
        mark(Layouts.arabic(false));
        mark(Layouts.english(false, 0));
    }

    private static int code(char c) {
        if (c >= 'a' && c <= 'z') return c - 'a';
        if (c >= 0x0621 && c <= 0x064A) return 26 + (c - 0x0621);
        return -1;
    }

    private static void mark(Layout layout) {
        int n = 0;
        for (Layout.Row r : layout.rows) n += r.keys.length;
        char[] chars = new char[n];
        float[] xs = new float[n], ys = new float[n];
        int count = 0;
        for (int ri = 0; ri < layout.rows.length; ri++) {
            Layout.Row row = layout.rows[ri];
            float x = row.offset;
            for (Key k : row.keys) {
                if (k.type == Key.CHAR && k.output != null && k.output.length() == 1) {
                    char c = TextNorm.normalize(k.output).isEmpty() ? k.output.charAt(0)
                        : TextNorm.normalize(k.output).charAt(0);
                    chars[count] = c;
                    xs[count] = x + k.width / 2f;
                    ys[count] = ri;
                    count++;
                }
                x += k.width;
            }
        }
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < count; j++) {
                if (i == j) continue;
                if (Math.abs(xs[i] - xs[j]) <= 1.05f && Math.abs(ys[i] - ys[j]) <= 1.05f) {
                    int a = code(chars[i]), b = code(chars[j]);
                    if (a >= 0 && b >= 0 && a != b) { ADJ[a][b] = true; ADJ[b][a] = true; }
                }
            }
        }
    }

    static boolean adjacent(char a, char b) {
        int x = code(a), y = code(b);
        return x >= 0 && y >= 0 && ADJ[x][y];
    }

    /**
     * Weighted Damerau-Levenshtein: neighbouring-key substitution 0.6, other substitution 1,
     * insertion / deletion 1, transposition 0.8. Returns a value > max when it exceeds max.
     * Arrays must be at least b.length()+1 long.
     */
    static float distance(String a, String b, float max, float[] r0, float[] r1, float[] r2) {
        int n = a.length(), m = b.length();
        if (Math.abs(n - m) > max) return max + 1f;
        if (m + 1 > r0.length) return max + 1f;
        float[] prev2 = r0, prev = r1, cur = r2;
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            float rowMin = cur[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char cb = b.charAt(j - 1);
                float sub = ca == cb ? 0f : (adjacent(ca, cb) ? 0.6f : 1f);
                float v = Math.min(Math.min(prev[j] + 1f, cur[j - 1] + 1f), prev[j - 1] + sub);
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb && ca != cb)
                    v = Math.min(v, prev2[j - 2] + 0.8f);
                cur[j] = v;
                if (v < rowMin) rowMin = v;
            }
            if (rowMin > max) return max + 1f;
            float[] t = prev2; prev2 = prev; prev = cur; cur = t;
        }
        return prev[m];
    }
}
