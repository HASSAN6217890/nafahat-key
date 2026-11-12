package com.nafahat.key;

import android.content.res.AssetManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only word list loaded from assets (generated offline from wordfreq).
 * Immutable after loading, so it can be read from any thread.
 */
public final class Dictionary {
    public final boolean arabic;
    final String[] words;   // rank order (most frequent first)
    final byte[] score;     // zipf * 10
    final String[] norms;   // normalised form of words[i]
    final int[] sorted;     // indices sorted by norms
    final int[][] byLen;    // indices grouped by normalised length
    private static final int MAX_LEN = 24;

    private Dictionary(boolean arabic, String[] words, byte[] score) {
        this.arabic = arabic;
        this.words = words;
        this.score = score;
        int n = words.length;
        norms = new String[n];
        for (int i = 0; i < n; i++) {
            String nm = TextNorm.normalize(words[i]);
            norms[i] = nm.equals(words[i]) ? words[i] : nm;
        }
        Integer[] boxed = new Integer[n];
        for (int i = 0; i < n; i++) boxed[i] = i;
        Arrays.sort(boxed, new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                int c = norms[a].compareTo(norms[b]);
                return c != 0 ? c : Integer.compare(a, b);
            }
        });
        sorted = new int[n];
        for (int i = 0; i < n; i++) sorted[i] = boxed[i];
        int[] counts = new int[MAX_LEN + 1];
        for (String s : norms) counts[Math.min(MAX_LEN, s.length())]++;
        byLen = new int[MAX_LEN + 1][];
        for (int l = 0; l <= MAX_LEN; l++) byLen[l] = new int[counts[l]];
        int[] fill = new int[MAX_LEN + 1];
        for (int i = 0; i < n; i++) {
            int l = Math.min(MAX_LEN, norms[i].length());
            byLen[l][fill[l]++] = i;
        }
    }

    public static Dictionary load(AssetManager assets, String path, boolean arabic) throws IOException {
        List<String> w = new ArrayList<>(arabic ? 51000 : 31000);
        List<Integer> s = new ArrayList<>(arabic ? 51000 : 31000);
        try (InputStream in = assets.open(path);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            while ((line = r.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                int sc;
                try { sc = Integer.parseInt(line.substring(tab + 1).trim()); }
                catch (NumberFormatException e) { continue; }
                w.add(line.substring(0, tab));
                s.add(Math.max(1, Math.min(120, sc)));
            }
        }
        byte[] sc = new byte[s.size()];
        for (int i = 0; i < sc.length; i++) sc[i] = (byte) (int) s.get(i);
        return new Dictionary(arabic, w.toArray(new String[0]), sc);
    }

    public int size() { return words.length; }

    public float zipf(int idx) { return score[idx] / 10f; }

    public String word(int idx) { return words[idx]; }

    private int lowerBound(String key) {
        int lo = 0, hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (norms[sorted[mid]].compareTo(key) < 0) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    /** Most frequent word whose normalised form equals norm, or -1. */
    public int findNorm(String norm) {
        int i = lowerBound(norm);
        int best = -1;
        while (i < sorted.length && norms[sorted[i]].equals(norm)) {
            int idx = sorted[i];
            if (best < 0 || idx < best) best = idx;
            i++;
        }
        return best;
    }

    /** True when the exact spelling is in the list (case-insensitive for English). */
    public boolean containsExact(String word) { return exactIndex(word) >= 0; }

    /** Index of the exact spelling, or -1. */
    public int exactIndex(String word) {
        String norm = TextNorm.normalize(word);
        String cmp = arabic ? TextNorm.stripMarks(word) : word.toLowerCase(java.util.Locale.US);
        int i = lowerBound(norm);
        while (i < sorted.length && norms[sorted[i]].equals(norm)) {
            if (words[sorted[i]].equals(cmp)) return sorted[i];
            i++;
        }
        return -1;
    }

    /** Top completions (word indices, best first) whose normalised form starts with prefix. */
    public int[] complete(String normPrefix, int max) {
        int[] best = new int[max];
        int count = 0;
        int i = lowerBound(normPrefix);
        int scanned = 0;
        while (i < sorted.length && scanned < 25000) {
            int idx = sorted[i];
            if (!norms[idx].startsWith(normPrefix)) break;
            scanned++;
            i++;
            // keep the `max` smallest indices (= most frequent)
            if (count < max) {
                int p = count++;
                while (p > 0 && best[p - 1] > idx) { best[p] = best[p - 1]; p--; }
                best[p] = idx;
            } else if (idx < best[max - 1]) {
                int p = max - 1;
                while (p > 0 && best[p - 1] > idx) { best[p] = best[p - 1]; p--; }
                best[p] = idx;
            }
        }
        return Arrays.copyOf(best, count);
    }

    /** A spelling-correction candidate. */
    public static final class Candidate {
        public final String word;
        public final float cost;
        public final float score;
        Candidate(String word, float cost, float score) { this.word = word; this.cost = cost; this.score = score; }
    }

    /** Candidates within maxCost (weighted Damerau-Levenshtein with keyboard proximity). */
    public List<Candidate> correct(String norm, float maxCost, int max) {
        List<Candidate> out = new ArrayList<>();
        int L = norm.length();
        if (L < 2 || L > MAX_LEN - 2) return out;
        int span = (int) Math.floor(maxCost);
        float[] a = new float[MAX_LEN + 4], b = new float[MAX_LEN + 4], c = new float[MAX_LEN + 4];
        for (int len = Math.max(1, L - span); len <= Math.min(MAX_LEN, L + span); len++) {
            for (int idx : byLen[len]) {
                float d = Proximity.distance(norm, norms[idx], maxCost, a, b, c);
                if (d > maxCost) continue;
                float sc = zipf(idx) - 2.2f * d;
                insert(out, new Candidate(words[idx], d, sc), max);
            }
        }
        return out;
    }

    static void insert(List<Candidate> list, Candidate c, int max) {
        for (int i = 0; i < list.size(); i++) {
            Candidate o = list.get(i);
            if (TextNorm.normalize(o.word).equals(TextNorm.normalize(c.word))) {
                if (c.score > o.score) list.set(i, c);
                list.sort((x, y) -> Float.compare(y.score, x.score));
                return;
            }
        }
        list.add(c);
        list.sort((x, y) -> Float.compare(y.score, x.score));
        while (list.size() > max) list.remove(list.size() - 1);
    }
}
