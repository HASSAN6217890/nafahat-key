package com.nafahat.key;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local SQLite library (schema version 1): collections + snippets.
 * Rules: a backup copy is taken before every write; a failed read or a corrupt file never
 * deletes anything (the corrupt file is kept aside and the last backup is restored).
 */
public final class LibraryDb extends SQLiteOpenHelper {
    public static final String NAME = "nafahat_library.db";
    private static final int SCHEMA = 1;
    /** Bumped on every change so in-memory caches can refresh. */
    public static final AtomicInteger CHANGES = new AtomicInteger();
    private static LibraryDb instance;
    private final Context app;

    public static synchronized LibraryDb get(Context context) {
        if (instance == null) {
            instance = new LibraryDb(context.getApplicationContext());
            instance.prepare();
        }
        return instance;
    }

    private LibraryDb(Context context) {
        super(context, NAME, null, SCHEMA, new SafeErrorHandler(context));
        app = context;
        setWriteAheadLoggingEnabled(false);
    }

    /** Keeps corrupt files instead of deleting them, and restores the last backup. */
    private static final class SafeErrorHandler implements DatabaseErrorHandler {
        private final Context context;
        SafeErrorHandler(Context c) { context = c; }
        @Override public void onCorruption(SQLiteDatabase db) {
            String path = db.getPath();
            try { db.close(); } catch (RuntimeException ignored) { }
            if (path == null || ":memory:".equals(path)) return;
            File file = new File(path);
            File kept = new File(path + ".corrupt-" + System.currentTimeMillis());
            if (file.exists() && !file.renameTo(kept)) return;
            File bak = new File(path + ".bak");
            if (bak.exists()) copy(bak, file);
        }
    }

    static boolean copy(File from, File to) {
        try (InputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.getFD().sync();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Copies the database file aside before a write. */
    private synchronized void backup() {
        File db = app.getDatabasePath(NAME);
        if (db.exists() && db.length() > 0) copy(db, new File(db.getPath() + ".bak"));
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE collections(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,"
            + " sort INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, builtin INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE snippets(id INTEGER PRIMARY KEY AUTOINCREMENT, collection_id INTEGER NOT NULL,"
            + " title TEXT NOT NULL, body TEXT NOT NULL, type TEXT, sort INTEGER NOT NULL DEFAULT 0, shortcut TEXT,"
            + " pinned INTEGER NOT NULL DEFAULT 0, last_used INTEGER NOT NULL DEFAULT 0, use_count INTEGER NOT NULL DEFAULT 0,"
            + " updated_at INTEGER NOT NULL DEFAULT 0, builtin_key TEXT)");
        db.execSQL("CREATE INDEX snippets_collection ON snippets(collection_id, sort)");
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future schema changes go here as additive migrations; never drop user tables.
    }

    private void prepare() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            syncBuiltins(db);
            migrateV02(db);
        } catch (RuntimeException ignored) {
            // Leave files untouched; the keyboard keeps working with the built-in texts.
        }
    }

    // ------------------------------------------------------------------ built-ins
    private long builtinCollection(SQLiteDatabase db, int which, String name) {
        try (Cursor c = db.rawQuery("SELECT id FROM collections WHERE builtin=?", new String[]{String.valueOf(which)})) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("sort", 1000 + which);
        v.put("builtin", which);
        return db.insert("collections", null, v);
    }

    private void syncBuiltins(SQLiteDatabase db) {
        List<Snippet> wanted = BrandData.publicSnippets();
        boolean dirty = false;
        List<String> keys = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT builtin_key,title,body,type FROM snippets WHERE builtin_key IS NOT NULL", null)) {
            List<String> rows = new ArrayList<>();
            while (c.moveToNext()) rows.add(c.getString(0) + "\u0001" + c.getString(1) + "\u0001" + c.getString(2) + "\u0001" + c.getString(3));
            for (Snippet s : wanted) {
                keys.add(s.builtinKey);
                if (!rows.contains(s.builtinKey + "\u0001" + s.title + "\u0001" + s.body + "\u0001" + s.type)) dirty = true;
            }
            if (rows.size() != wanted.size()) dirty = true;
        }
        boolean haveCollections;
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM collections WHERE builtin>0", null)) {
            haveCollections = c.moveToFirst() && c.getInt(0) >= 2;
        }
        if (!dirty && haveCollections) return;
        backup();
        db.beginTransaction();
        try {
            long info = builtinCollection(db, BrandData.INFO, BrandData.INFO_NAME);
            long replies = builtinCollection(db, BrandData.REPLIES, BrandData.REPLIES_NAME);
            int sort = 0;
            for (Snippet s : wanted) {
                ContentValues v = new ContentValues();
                v.put("collection_id", s.builtinCollection == BrandData.INFO ? info : replies);
                v.put("title", s.title);
                v.put("body", s.body);
                v.put("type", s.type);
                v.put("sort", sort++);
                v.put("updated_at", System.currentTimeMillis());
                int n = db.update("snippets", v, "builtin_key=?", new String[]{s.builtinKey});
                if (n == 0) {
                    v.put("builtin_key", s.builtinKey);
                    db.insert("snippets", null, v);
                }
            }
            try (Cursor c = db.rawQuery("SELECT id,builtin_key FROM snippets WHERE builtin_key IS NOT NULL", null)) {
                List<Long> stale = new ArrayList<>();
                while (c.moveToNext()) if (!keys.contains(c.getString(1))) stale.add(c.getLong(0));
                for (long id : stale) db.delete("snippets", "id=?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        CHANGES.incrementAndGet();
    }

    // ------------------------------------------------------------------ v0.2 migration
    private String meta(SQLiteDatabase db, String key) {
        try (Cursor c = db.rawQuery("SELECT value FROM meta WHERE key=?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    private void migrateV02(SQLiteDatabase db) {
        if ("1".equals(meta(db, "migrated_v02"))) return;
        SharedPreferences old = app.getSharedPreferences("nafahatkey_personal_v1", Context.MODE_PRIVATE);
        String raw = old.getString("items", null);
        JSONArray items;
        try {
            items = raw == null ? new JSONArray() : new JSONArray(raw);
        } catch (Exception e) {
            return; // unreadable: keep the old data untouched and try again next time
        }
        backup();
        db.beginTransaction();
        try {
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.optJSONObject(i);
                if (o == null) continue;
                String title = o.optString("title", "").trim();
                String text = o.optString("text", "").trim();
                if (title.isEmpty() || text.isEmpty()) continue;
                String category = o.optString("category", "شخصية").trim();
                if (category.isEmpty() || category.equals(BrandData.INFO_NAME) || category.equals(BrandData.REPLIES_NAME))
                    category = "شخصية";
                long cid = findOrCreateCollection(db, category);
                ContentValues v = new ContentValues();
                v.put("collection_id", cid);
                v.put("title", title);
                v.put("body", text);
                v.put("type", "أخرى");
                v.put("sort", i);
                v.put("updated_at", System.currentTimeMillis());
                db.insert("snippets", null, v);
            }
            ContentValues m = new ContentValues();
            m.put("key", "migrated_v02");
            m.put("value", "1");
            db.insertWithOnConflict("meta", null, m, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        CHANGES.incrementAndGet();
    }

    private long findOrCreateCollection(SQLiteDatabase db, String name) {
        try (Cursor c = db.rawQuery("SELECT id FROM collections WHERE name=? AND builtin=0", new String[]{name})) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        int sort = 0;
        try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(sort),-1)+1 FROM collections WHERE builtin=0", null)) {
            if (c.moveToFirst()) sort = c.getInt(0);
        }
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("sort", sort);
        return db.insert("collections", null, v);
    }

    // ------------------------------------------------------------------ reads (never write)
    public List<SnippetCollection> collections(boolean includeArchived) {
        List<SnippetCollection> out = new ArrayList<>();
        try {
            String where = includeArchived ? "" : " WHERE archived=0";
            try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,name,sort,archived,builtin FROM collections" + where
                    + " ORDER BY CASE WHEN builtin=0 THEN 0 ELSE 1 END, sort, id", null)) {
                while (c.moveToNext()) {
                    SnippetCollection s = new SnippetCollection();
                    s.id = c.getLong(0);
                    s.name = c.getString(1);
                    s.sort = c.getInt(2);
                    s.archived = c.getInt(3) != 0;
                    s.builtin = c.getInt(4);
                    out.add(s);
                }
            }
        } catch (RuntimeException ignored) { }
        return out;
    }

    private static final String SNIPPET_COLS = "s.id,s.collection_id,s.title,s.body,s.type,s.sort,s.shortcut,"
        + "s.pinned,s.last_used,s.use_count,s.updated_at,s.builtin_key";

    private static Snippet read(Cursor c) {
        Snippet s = new Snippet();
        s.id = c.getLong(0);
        s.collectionId = c.getLong(1);
        s.title = c.getString(2);
        s.body = c.getString(3);
        s.type = c.isNull(4) ? "أخرى" : c.getString(4);
        s.sort = c.getInt(5);
        s.shortcut = c.isNull(6) ? "" : c.getString(6);
        s.pinned = c.getInt(7) != 0;
        s.lastUsed = c.getLong(8);
        s.useCount = c.getInt(9);
        s.updatedAt = c.getLong(10);
        s.builtinKey = c.isNull(11) ? null : c.getString(11);
        return s;
    }

    /** Snippets of active (not archived) collections; collectionId -1 = all. */
    public List<Snippet> snippets(long collectionId) {
        List<Snippet> out = new ArrayList<>();
        try {
            String sql = "SELECT " + SNIPPET_COLS + " FROM snippets s JOIN collections c ON c.id=s.collection_id"
                + " WHERE c.archived=0" + (collectionId >= 0 ? " AND s.collection_id=" + collectionId : "")
                + " ORDER BY CASE WHEN c.builtin=0 THEN 0 ELSE 1 END, c.sort, s.sort, s.id";
            try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
                while (c.moveToNext()) out.add(read(c));
            }
        } catch (RuntimeException ignored) { }
        return out;
    }

    /** Snippets of one collection including archived ones (management screen). */
    public List<Snippet> snippetsOf(long collectionId) {
        List<Snippet> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + SNIPPET_COLS
            + " FROM snippets s WHERE s.collection_id=? ORDER BY s.sort, s.id", new String[]{String.valueOf(collectionId)})) {
            while (c.moveToNext()) out.add(read(c));
        } catch (RuntimeException ignored) { }
        return out;
    }

    public List<Snippet> mostUsed(int n) {
        List<Snippet> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + SNIPPET_COLS
            + " FROM snippets s JOIN collections c ON c.id=s.collection_id WHERE c.archived=0 AND s.use_count>0"
            + " ORDER BY s.use_count DESC, s.last_used DESC LIMIT " + n, null)) {
            while (c.moveToNext()) out.add(read(c));
        } catch (RuntimeException ignored) { }
        return out;
    }

    // ------------------------------------------------------------------ writes (backup first)
    public synchronized long addCollection(String name) {
        name = name == null ? "" : name.trim();
        if (name.isEmpty()) return -1;
        backup();
        long id = findOrCreateCollection(getWritableDatabase(), name);
        CHANGES.incrementAndGet();
        return id;
    }

    public synchronized void renameCollection(long id, String name) {
        if (name == null || name.trim().isEmpty()) return;
        backup();
        ContentValues v = new ContentValues();
        v.put("name", name.trim());
        getWritableDatabase().update("collections", v, "id=? AND builtin=0", new String[]{String.valueOf(id)});
        CHANGES.incrementAndGet();
    }

    public synchronized void setArchived(long id, boolean archived) {
        backup();
        ContentValues v = new ContentValues();
        v.put("archived", archived ? 1 : 0);
        getWritableDatabase().update("collections", v, "id=? AND builtin=0", new String[]{String.valueOf(id)});
        CHANGES.incrementAndGet();
    }

    /** Deletes a user collection only when it is empty. */
    public synchronized boolean deleteEmptyCollection(long id) {
        if (!snippetsOf(id).isEmpty()) return false;
        backup();
        int n = getWritableDatabase().delete("collections", "id=? AND builtin=0", new String[]{String.valueOf(id)});
        CHANGES.incrementAndGet();
        return n > 0;
    }

    public synchronized void reorderCollections(List<Long> ids) {
        backup();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < ids.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("sort", i);
                db.update("collections", v, "id=? AND builtin=0", new String[]{String.valueOf(ids.get(i))});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        CHANGES.incrementAndGet();
    }

    public synchronized long saveSnippet(Snippet s) {
        if (s.isBuiltin()) return s.id;
        if (s.title.trim().isEmpty() || s.body.trim().isEmpty()) return -1;
        backup();
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("collection_id", s.collectionId);
        v.put("title", s.title.trim());
        v.put("body", s.body);
        v.put("type", s.type);
        v.put("shortcut", s.shortcut == null ? "" : s.shortcut.trim());
        v.put("pinned", s.pinned ? 1 : 0);
        v.put("updated_at", System.currentTimeMillis());
        long id;
        if (s.id > 0) {
            db.update("snippets", v, "id=? AND builtin_key IS NULL", new String[]{String.valueOf(s.id)});
            id = s.id;
        } else {
            int sort = 0;
            try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(sort),-1)+1 FROM snippets WHERE collection_id=?",
                new String[]{String.valueOf(s.collectionId)})) {
                if (c.moveToFirst()) sort = c.getInt(0);
            }
            v.put("sort", sort);
            id = db.insert("snippets", null, v);
        }
        CHANGES.incrementAndGet();
        return id;
    }

    public synchronized void deleteSnippet(long id) {
        backup();
        getWritableDatabase().delete("snippets", "id=? AND builtin_key IS NULL", new String[]{String.valueOf(id)});
        CHANGES.incrementAndGet();
    }

    public synchronized void reorderSnippets(List<Long> ids) {
        backup();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < ids.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("sort", i);
                db.update("snippets", v, "id=?", new String[]{String.valueOf(ids.get(i))});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        CHANGES.incrementAndGet();
    }

    /** Usage counters only; no backup here so the .bak keeps the last real content change. */
    public synchronized void markUsed(long id) {
        try {
            getWritableDatabase().execSQL("UPDATE snippets SET use_count=use_count+1, last_used=? WHERE id=?",
                new Object[]{System.currentTimeMillis(), id});
            CHANGES.incrementAndGet();
        } catch (RuntimeException ignored) { }
    }

    public synchronized void setPinned(long id, boolean pinned) {
        backup();
        ContentValues v = new ContentValues();
        v.put("pinned", pinned ? 1 : 0);
        getWritableDatabase().update("snippets", v, "id=?", new String[]{String.valueOf(id)});
        CHANGES.incrementAndGet();
    }

    // ------------------------------------------------------------------ export / import
    public JSONArray exportJson() {
        JSONArray out = new JSONArray();
        try {
            for (SnippetCollection c : collections(true)) {
                if (c.isBuiltin()) continue;
                JSONObject o = new JSONObject();
                o.put("name", c.name);
                o.put("sort", c.sort);
                o.put("archived", c.archived);
                JSONArray items = new JSONArray();
                for (Snippet s : snippetsOf(c.id)) {
                    JSONObject j = new JSONObject();
                    j.put("title", s.title);
                    j.put("body", s.body);
                    j.put("type", s.type);
                    j.put("sort", s.sort);
                    j.put("shortcut", s.shortcut);
                    j.put("pinned", s.pinned);
                    items.put(j);
                }
                o.put("snippets", items);
                out.put(o);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Merges collections from a backup; nothing existing is replaced or deleted. Returns added texts. */
    public synchronized int importJson(JSONArray collections) {
        if (collections == null) return 0;
        backup();
        int added = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < collections.length(); i++) {
                JSONObject o = collections.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "").trim();
                if (name.isEmpty()) continue;
                boolean existed = false;
                try (Cursor c = db.rawQuery("SELECT id FROM collections WHERE name=? AND builtin=0", new String[]{name})) {
                    existed = c.moveToFirst();
                }
                long cid = findOrCreateCollection(db, name);
                // Existing collections are never archived or otherwise changed by an import.
                if (!existed && o.optBoolean("archived", false)) {
                    ContentValues a = new ContentValues();
                    a.put("archived", 1);
                    db.update("collections", a, "id=?", new String[]{String.valueOf(cid)});
                }
                JSONArray items = o.optJSONArray("snippets");
                if (items == null) continue;
                for (int k = 0; k < items.length(); k++) {
                    JSONObject j = items.optJSONObject(k);
                    if (j == null) continue;
                    String title = j.optString("title", "").trim();
                    String body = j.optString("body", "");
                    if (title.isEmpty() || body.trim().isEmpty()) continue;
                    try (Cursor c = db.rawQuery("SELECT 1 FROM snippets WHERE collection_id=? AND title=? AND body=?",
                        new String[]{String.valueOf(cid), title, body})) {
                        if (c.moveToFirst()) continue;
                    }
                    ContentValues v = new ContentValues();
                    v.put("collection_id", cid);
                    v.put("title", title);
                    v.put("body", body);
                    v.put("type", j.optString("type", "أخرى"));
                    v.put("sort", j.optInt("sort", k));
                    v.put("shortcut", j.optString("shortcut", ""));
                    v.put("pinned", j.optBoolean("pinned", false) ? 1 : 0);
                    v.put("updated_at", System.currentTimeMillis());
                    db.insert("snippets", null, v);
                    added++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        CHANGES.incrementAndGet();
        return added;
    }
}
