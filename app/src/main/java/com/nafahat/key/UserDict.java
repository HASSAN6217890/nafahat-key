package com.nafahat.key;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Personal dictionary learned on this device only (words + word pairs).
 * Never used in sensitive fields; cleared by "مسح ما تعلّمه الكيبورد". Nothing is logged.
 */
public final class UserDict {
    private static UserDict instance;

    public static synchronized UserDict get(Context context) {
        if (instance == null) instance = new UserDict(context.getApplicationContext());
        return instance;
    }

    private final Helper helper;
    private final ConcurrentHashMap<String, Integer> words = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> bigrams = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean loaded;

    private UserDict(Context context) {
        helper = new Helper(context);
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context c) {
            super(c, "nafahat_learn.db", null, 1);
            setWriteAheadLoggingEnabled(false);
        }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE words(word TEXT PRIMARY KEY, count INTEGER NOT NULL, updated INTEGER)");
            db.execSQL("CREATE TABLE bigrams(w1 TEXT NOT NULL, w2 TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(w1,w2))");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) { }
    }

    /** Loads the stored dictionary (call from a background thread). */
    public void loadBlocking() {
        if (loaded) return;
        synchronized (this) {
            if (loaded) return;
            try {
                SQLiteDatabase db = helper.getReadableDatabase();
                try (Cursor c = db.rawQuery("SELECT word,count FROM words", null)) {
                    while (c.moveToNext()) words.put(c.getString(0), c.getInt(1));
                }
                try (Cursor c = db.rawQuery("SELECT w1,w2,count FROM bigrams", null)) {
                    while (c.moveToNext()) pairs(c.getString(0)).put(c.getString(1), c.getInt(2));
                }
            } catch (RuntimeException ignored) {
                // A failed read leaves the stored data untouched.
            }
            loaded = true;
        }
    }

    private ConcurrentHashMap<String, Integer> pairs(String w1) {
        ConcurrentHashMap<String, Integer> m = bigrams.get(w1);
        if (m == null) {
            m = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, Integer> old = bigrams.putIfAbsent(w1, m);
            if (old != null) m = old;
        }
        return m;
    }

    public static boolean learnable(String w) {
        if (w == null) return false;
        int len = w.length();
        if (len < 2 || len > 24) return false;
        return TextNorm.isArabicWord(w) || TextNorm.isLatinWord(w);
    }

    private static String clean(String w) {
        String s = TextNorm.stripMarks(w);
        if (TextNorm.isLatinWord(s)) s = s.toLowerCase(java.util.Locale.US);
        return s;
    }

    /** Learn one typed word (and the pair with the previous word). */
    public void learn(String word, String prev, int weight) {
        if (!learnable(word)) return;
        final String w = clean(word);
        final String p = prev != null && learnable(prev) ? TextNorm.normalize(clean(prev)) : null;
        words.merge(w, weight, Integer::sum);
        if (p != null) pairs(p).merge(w, weight, Integer::sum);
        final int add = weight;
        io.execute(() -> persist(w, p, add));
    }

    private void persist(String w, String p, int add) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                upsertWord(db, w, add);
                if (p != null) upsertPair(db, p, w, add);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (RuntimeException ignored) { }
    }

    private static void upsertWord(SQLiteDatabase db, String w, int add) {
        long now = System.currentTimeMillis();
        db.execSQL("INSERT OR IGNORE INTO words(word,count,updated) VALUES(?,0,?)", new Object[]{w, now});
        db.execSQL("UPDATE words SET count=count+?, updated=? WHERE word=?", new Object[]{add, now, w});
    }

    private static void upsertPair(SQLiteDatabase db, String p, String w, int add) {
        db.execSQL("INSERT OR IGNORE INTO bigrams(w1,w2,count) VALUES(?,?,0)", new Object[]{p, w});
        db.execSQL("UPDATE bigrams SET count=count+? WHERE w1=? AND w2=?", new Object[]{add, p, w});
    }

    /** Bulk learning (chat import / backup import). Runs synchronously; call off the main thread. */
    public int learnBulk(Map<String, Integer> wordCounts, Map<String, Integer> pairCounts) {
        int learned = 0;
        Map<String, Integer> cleanWords = new HashMap<>();
        for (Map.Entry<String, Integer> e : wordCounts.entrySet()) {
            if (!learnable(e.getKey())) continue;
            cleanWords.merge(clean(e.getKey()), e.getValue(), Integer::sum);
        }
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                for (Map.Entry<String, Integer> e : cleanWords.entrySet()) {
                    upsertWord(db, e.getKey(), e.getValue());
                    words.merge(e.getKey(), e.getValue(), Integer::sum);
                    learned++;
                }
                for (Map.Entry<String, Integer> e : pairCounts.entrySet()) {
                    String[] ab = e.getKey().split("\t", 2);
                    if (ab.length != 2 || !learnable(ab[0]) || !learnable(ab[1])) continue;
                    String p = TextNorm.normalize(clean(ab[0]));
                    String w = clean(ab[1]);
                    upsertPair(db, p, w, e.getValue());
                    pairs(p).merge(w, e.getValue(), Integer::sum);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (RuntimeException ignored) { }
        return learned;
    }

    public int count(String word) {
        Integer c = words.get(clean(word));
        return c == null ? 0 : c;
    }

    /** A word typed at least twice (or explicitly kept) is never autocorrected. */
    public boolean isKnown(String word) { return count(word) >= 2; }

    public int size() { return words.size(); }

    /** Completions from learned words (count >= 2). */
    public List<Dictionary.Candidate> complete(String normPrefix, boolean arabic, int max) {
        List<Dictionary.Candidate> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : words.entrySet()) {
            int c = e.getValue();
            if (c < 2) continue;
            String w = e.getKey();
            if (arabic != TextNorm.isArabicWord(w)) continue;
            if (!TextNorm.normalize(w).startsWith(normPrefix)) continue;
            Dictionary.insert(out, new Dictionary.Candidate(w, 0f, zipfFor(c)), max);
        }
        return out;
    }

    static float zipfFor(int count) {
        return Math.min(7.5f, 3.6f + (float) (Math.log(count) / Math.log(2)) * 0.6f);
    }

    /** Learned next words after prev (best first). */
    public List<String> next(String prev, int max) {
        List<String> out = new ArrayList<>();
        if (prev == null) return out;
        ConcurrentHashMap<String, Integer> m = bigrams.get(TextNorm.normalize(clean(prev)));
        if (m == null) return out;
        List<Map.Entry<String, Integer>> list = new ArrayList<>(m.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Integer> e : list) {
            out.add(e.getKey());
            if (out.size() >= max) break;
        }
        return out;
    }

    /** Deletes everything learned (explicit user action only). */
    public void clearAll() {
        words.clear();
        bigrams.clear();
        io.execute(() -> {
            try {
                SQLiteDatabase db = helper.getWritableDatabase();
                db.delete("words", null, null);
                db.delete("bigrams", null, null);
            } catch (RuntimeException ignored) { }
        });
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            JSONArray w = new JSONArray();
            for (Map.Entry<String, Integer> e : words.entrySet())
                w.put(new JSONArray().put(e.getKey()).put(e.getValue()));
            JSONArray b = new JSONArray();
            for (Map.Entry<String, ConcurrentHashMap<String, Integer>> e : bigrams.entrySet())
                for (Map.Entry<String, Integer> f : e.getValue().entrySet())
                    b.put(new JSONArray().put(e.getKey()).put(f.getKey()).put(f.getValue()));
            o.put("words", w);
            o.put("bigrams", b);
        } catch (Exception ignored) { }
        return o;
    }

    public int importJson(JSONObject o) {
        if (o == null) return 0;
        Map<String, Integer> w = new HashMap<>();
        Map<String, Integer> p = new HashMap<>();
        JSONArray wa = o.optJSONArray("words");
        if (wa != null) for (int i = 0; i < wa.length(); i++) {
            JSONArray e = wa.optJSONArray(i);
            if (e != null && e.length() >= 2) w.merge(e.optString(0), Math.max(1, e.optInt(1, 1)), Integer::sum);
        }
        JSONArray ba = o.optJSONArray("bigrams");
        if (ba != null) for (int i = 0; i < ba.length(); i++) {
            JSONArray e = ba.optJSONArray(i);
            if (e != null && e.length() >= 3)
                p.merge(e.optString(0) + "\t" + e.optString(1), Math.max(1, e.optInt(2, 1)), Integer::sum);
        }
        return learnBulk(w, p);
    }
}
