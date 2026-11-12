package com.nafahat.key;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Word completion, next-word prediction and autocorrection.
 * Dictionaries load lazily on a background thread; results are delivered on the main thread.
 */
public final class Suggester {
    private static Suggester instance;

    public static synchronized Suggester get(Context context) {
        if (instance == null) instance = new Suggester(context.getApplicationContext());
        return instance;
    }

    public interface Callback { void onResult(Result result); }

    /** Suggestions for the word being typed. */
    public static final class Result {
        public final String typed;
        public final boolean arabic;
        public final List<String> words = new ArrayList<>();
        /** Word that space / punctuation will commit instead of the typed one, or null. */
        public String autoCorrect;
        public boolean typedKnown;
        Result(String typed, boolean arabic) { this.typed = typed; this.arabic = arabic; }
    }

    private final AssetManager assets;
    private final UserDict user;
    private final Handler worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger seq = new AtomicInteger();
    private volatile Dictionary ar, en;
    private volatile Map<String, List<String>> bigrams;
    private volatile Result last;
    private volatile boolean loading;

    private Suggester(Context context) {
        assets = context.getAssets();
        user = UserDict.get(context);
        HandlerThread t = new HandlerThread("nafahat-suggest");
        t.start();
        worker = new Handler(t.getLooper());
    }

    /** Starts loading dictionaries in the background (idempotent). */
    public void ensureLoaded() {
        if (loading) return;
        loading = true;
        worker.post(() -> {
            user.loadBlocking();
            try { if (ar == null) ar = Dictionary.load(assets, "dict/ar.txt", true); } catch (Exception ignored) { }
            try { if (bigrams == null) bigrams = loadBigrams(); } catch (Exception ignored) { }
            try { if (en == null) en = Dictionary.load(assets, "dict/en.txt", false); } catch (Exception ignored) { }
        });
    }

    public boolean ready(boolean arabic) { return (arabic ? ar : en) != null; }

    private Map<String, List<String>> loadBigrams() throws java.io.IOException {
        Map<String, List<String>> map = new HashMap<>();
        try (InputStream in = assets.open("dict/bigrams.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            Map<String, Map<String, Integer>> tmp = new HashMap<>();
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length < 3) continue;
                int c;
                try { c = Integer.parseInt(p[2].trim()); } catch (NumberFormatException e) { continue; }
                Map<String, Integer> m = tmp.get(TextNorm.normalize(p[0]));
                if (m == null) { m = new HashMap<>(); tmp.put(TextNorm.normalize(p[0]), m); }
                m.merge(p[1], c, Integer::sum);
            }
            for (Map.Entry<String, Map<String, Integer>> e : tmp.entrySet()) {
                List<Map.Entry<String, Integer>> l = new ArrayList<>(e.getValue().entrySet());
                l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                List<String> words = new ArrayList<>();
                for (Map.Entry<String, Integer> f : l) words.add(f.getKey());
                map.put(e.getKey(), words);
            }
        }
        return map;
    }

    /** Asynchronous suggestions for the composing word; stale results are dropped. */
    public void request(String typed, boolean arabic, boolean allowCorrection, Callback cb) {
        final int id = seq.incrementAndGet();
        worker.post(() -> {
            if (id != seq.get()) return;
            final Result r = compute(typed, arabic, allowCorrection);
            last = r;
            main.post(() -> { if (id == seq.get()) cb.onResult(r); });
        });
    }

    /** Invalidates pending asynchronous requests. */
    public void cancel() { seq.incrementAndGet(); }

    /** Synchronous result for the separator path (uses the cached result when it matches). */
    public Result now(String typed, boolean arabic, boolean allowCorrection) {
        Result r = last;
        if (r != null && r.typed.equals(typed) && r.arabic == arabic) return r;
        return compute(typed, arabic, allowCorrection);
    }

    Result compute(String typed, boolean arabic, boolean allowCorrection) {
        Result res = new Result(typed, arabic);
        Dictionary dict = arabic ? ar : en;
        String norm = TextNorm.normalize(typed);
        if (norm.isEmpty()) return res;
        List<Dictionary.Candidate> cands = new ArrayList<>();
        if (dict != null) {
            for (int idx : dict.complete(norm, 6)) {
                float sc = dict.zipf(idx);
                if (TextNorm.normalize(dict.word(idx)).equals(norm)) sc += 0.6f;
                Dictionary.insert(cands, new Dictionary.Candidate(dict.word(idx), 0f, sc), 6);
            }
        }
        for (Dictionary.Candidate c : user.complete(norm, arabic, 4)) {
            float sc = c.score + (TextNorm.normalize(c.word).equals(norm) ? 0.6f : 0f);
            Dictionary.insert(cands, new Dictionary.Candidate(c.word, 0f, sc), 6);
        }
        int exact = dict != null ? dict.exactIndex(typed) : -1;
        res.typedKnown = exact >= 0 || user.count(typed) >= 1;
        boolean userKnown = user.isKnown(typed);
        if (allowCorrection && dict != null && exact >= 0 && !userKnown && typed.length() >= 2) {
            // A listed but rare spelling next to a much more common one ("الى" → "إلى", "teh" → "the").
            float typedZipf = dict.zipf(exact);
            int variant = dict.findNorm(norm);
            if (variant >= 0 && variant != exact && dict.zipf(variant) - typedZipf >= 0.85f) {
                res.autoCorrect = fixCase(typed, dict.word(variant), arabic);
            } else if (typedZipf < 3.5f && norm.length() >= 3) {
                for (Dictionary.Candidate c : dict.correct(norm, 1.0f, 3)) {
                    float z = c.score + 2.2f * c.cost;
                    if (c.cost > 0f && z - typedZipf >= 2.5f) { res.autoCorrect = fixCase(typed, c.word, arabic); break; }
                }
            }
        }
        boolean known = exact >= 0 || userKnown;
        if (allowCorrection && dict != null && !known && typed.length() >= 2) {
            int variant = dict.findNorm(norm);
            if (variant >= 0 && !dict.word(variant).equals(typed)) {
                // Same letters, different hamza / ta marbuta / alef maqsura form.
                res.autoCorrect = dict.word(variant);
            } else if (variant < 0) {
                int L = norm.length();
                float maxCost = L <= 4 ? 1.0f : L <= 7 ? 1.6f : 2.0f;
                List<Dictionary.Candidate> corr = dict.correct(norm, maxCost, 4);
                for (Dictionary.Candidate c : corr)
                    Dictionary.insert(cands, new Dictionary.Candidate(c.word, c.cost, c.score - 0.3f), 6);
                if (!corr.isEmpty() && L >= 3) {
                    Dictionary.Candidate best = corr.get(0);
                    Dictionary.Candidate second = corr.size() > 1 ? corr.get(1) : null;
                    boolean frequent = best.score + 2.2f * best.cost >= 2.5f;
                    boolean clear = second == null || best.score - second.score >= 0.4f || best.cost < second.cost;
                    boolean allCaps = !arabic && typed.length() > 1 && typed.equals(typed.toUpperCase(Locale.US));
                    if (frequent && clear && !allCaps) res.autoCorrect = best.word;
                }
            }
            if (res.autoCorrect != null) res.autoCorrect = fixCase(typed, res.autoCorrect, arabic);
        }
        for (Dictionary.Candidate c : cands) {
            String w = fixCase(typed, c.word, arabic);
            if (!res.words.contains(w)) res.words.add(w);
        }
        if (res.autoCorrect != null) {
            res.words.remove(res.autoCorrect);
            res.words.add(0, res.autoCorrect);
        }
        return res;
    }

    private static String fixCase(String typed, String word, boolean arabic) {
        if (arabic) return word;
        if (word.equals("i")) return "I";
        return TextNorm.matchCase(typed, word);
    }

    /** Next-word predictions after prev (learned pairs first, then the built-in phrase list). */
    public List<String> predict(String prev, boolean arabic, int max) {
        List<String> out = new ArrayList<>();
        if (prev == null || prev.isEmpty()) return out;
        for (String w : user.next(prev, max)) if (!out.contains(w)) out.add(w);
        Map<String, List<String>> b = bigrams;
        if (b != null && out.size() < max) {
            List<String> l = b.get(TextNorm.normalize(prev));
            if (l != null) for (String w : l) {
                if (out.size() >= max) break;
                if (!out.contains(w)) out.add(w);
            }
        }
        List<String> filtered = new ArrayList<>();
        for (String w : out) if (arabic == TextNorm.isArabicWord(w)) filtered.add(w);
        return filtered;
    }
}
