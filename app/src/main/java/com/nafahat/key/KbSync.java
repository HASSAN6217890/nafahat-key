package com.nafahat.key;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Downloads the Nafahat library (card.nafahatsy.com/kb/) and applies it to the local library.
 * Rules: download only; the sha256 from v.json must match; any error keeps the last good copy
 * (a network failure never deletes anything); runs off the main thread only.
 */
public final class KbSync {
    private KbSync() { }

    private static final String PREF = "nafahatkey_kb_sync";
    public static final int NEVER = 0, OK = 1, NOT_PUBLISHED = 2, OFFLINE = 3, BAD = 4;
    /** Team section state for the settings screen. */
    public static final int TEAM_UNLINKED = 0, TEAM_ACTIVE = 1, TEAM_EMPTY = 2, TEAM_WRONG_KEY = 3;

    public static final class Status {
        public long lastOk, lastTry;
        public int rev, code, team;
    }

    private static SharedPreferences store(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static Status status(Context c) {
        SharedPreferences p = store(c);
        Status s = new Status();
        s.lastOk = p.getLong("last_ok", 0);
        s.lastTry = p.getLong("last_try", 0);
        s.rev = p.getInt("rev", 0);
        s.code = p.getInt("code", NEVER);
        s.team = p.getInt("team", TEAM_UNLINKED);
        return s;
    }

    /** Arabic one-line state for the settings screen. */
    public static String describe(int code) {
        switch (code) {
            case OK: return "محدّثة";
            case NOT_PUBLISHED: return "لم تُنشر المكتبة بعد — يعمل الكيبورد بالنصوص المدمجة";
            case OFFLINE: return "تعذّر الاتصال — يعمل الكيبورد بآخر نسخة نزلت";
            case BAD: return "النسخة المنزّلة غير سليمة — بقيت النسخة السابقة";
            default: return "لم تتم المزامنة بعد";
        }
    }

    /** Syncs when the last attempt is older than minAgeMs (call off the main thread). */
    public static void maybeSync(Context c, long minAgeMs) {
        long last = store(c).getLong("last_try", 0);
        long now = System.currentTimeMillis();
        if (now - last >= minAgeMs || last > now) run(c, false);
    }

    /** One sync; force = download even when the revision did not change (after linking). */
    public static synchronized int run(Context context, boolean force) {
        Context c = context.getApplicationContext();
        SharedPreferences p = store(c);
        p.edit().putLong("last_try", System.currentTimeMillis()).apply();
        int code;
        try {
            JSONObject v = new JSONObject(new String(KbApi.version(), StandardCharsets.UTF_8));
            int rev = v.getInt("rev");
            String file = v.getString("file");
            String sha = v.getString("sha256").toLowerCase(java.util.Locale.US);
            if (rev <= 0 || !file.equals("library." + rev + ".json") || !sha.matches("[0-9a-f]{64}")) throw new BadLibrary();
            boolean linked = KbCrypto.linked(c);
            if (!force && rev == p.getInt("rev", 0) && p.getInt("code", NEVER) == OK
                && linked == p.getBoolean("applied_linked", false)) {
                p.edit().putLong("last_ok", System.currentTimeMillis()).apply();
                return OK;
            }
            byte[] data = KbApi.library(rev);
            if (!KbCipher.sha256Hex(data).equals(sha)) throw new BadLibrary();
            JSONObject lib = new JSONObject(new String(data, StandardCharsets.UTF_8));
            if (!"nafahatkey-library".equals(lib.optString("format")) || lib.optInt("rev") != rev) throw new BadLibrary();
            List<LibraryDb.Trip> trips = new ArrayList<>();
            List<Snippet> items = new ArrayList<>();
            readSection(lib, trips, items, "public");
            boolean hasInfo = false;
            for (LibraryDb.Trip t : trips) if ("info".equals(t.id)) hasInfo = true;
            if (!hasInfo) throw new BadLibrary();
            int teamApply = LibraryDb.TEAM_NONE, teamState = TEAM_UNLINKED;
            JSONObject team = lib.optJSONObject("team");
            if (linked) {
                byte[] key = KbCrypto.load(c);
                if (team == null) {
                    teamState = TEAM_EMPTY;
                } else if (key == null || !KbCipher.kid(key).equals(team.optString("kid"))) {
                    teamApply = LibraryDb.TEAM_KEEP;
                    teamState = TEAM_WRONG_KEY;
                } else {
                    try {
                        JSONObject plain = new JSONObject(KbCipher.decrypt(key, rev, team.getString("iv"), team.getString("ct")));
                        readSection(plain, trips, items, "team");
                        teamApply = LibraryDb.TEAM_OK;
                        teamState = TEAM_ACTIVE;
                    } catch (Exception e) {
                        teamApply = LibraryDb.TEAM_KEEP;
                        teamState = TEAM_WRONG_KEY;
                    } finally {
                        java.util.Arrays.fill(key, (byte) 0);
                    }
                }
            }
            List<String> del = new ArrayList<>();
            JSONArray d = lib.optJSONArray("del");
            if (d != null) for (int i = 0; i < d.length(); i++) del.add(d.optString(i));
            LibraryDb.get(c).applyServer(trips, items, teamApply, del, lib.optBoolean("full", true));
            p.edit().putInt("rev", rev).putInt("team", teamState).putBoolean("applied_linked", linked)
                .putLong("last_ok", System.currentTimeMillis()).apply();
            code = OK;
        } catch (KbApi.NotPublished e) {
            code = NOT_PUBLISHED;
        } catch (BadLibrary | org.json.JSONException | IllegalStateException e) {
            code = BAD;
        } catch (Exception e) {
            code = OFFLINE;
        }
        p.edit().putInt("code", code).apply();
        return code;
    }

    private static final class BadLibrary extends Exception { }

    private static void readSection(JSONObject o, List<LibraryDb.Trip> trips, List<Snippet> items, String level) throws Exception {
        JSONArray ts = o.optJSONArray("trips");
        if (ts != null) {
            for (int i = 0; i < ts.length(); i++) {
                JSONObject t = ts.getJSONObject(i);
                LibraryDb.Trip x = new LibraryDb.Trip();
                x.id = t.getString("id");
                x.name = t.getString("name");
                x.date = t.optString("date", "");
                x.current = t.optBoolean("current", false);
                x.archived = t.optBoolean("archived", false);
                x.sort = t.optInt("sort", i);
                boolean dup = false;
                for (LibraryDb.Trip y : trips) if (y.id.equals(x.id)) dup = true;
                if (!dup) trips.add(x);
            }
        }
        JSONArray up = o.optJSONArray("up");
        if (up == null) return;
        for (int i = 0; i < up.length(); i++) {
            JSONObject j = up.getJSONObject(i);
            Snippet s = new Snippet();
            s.origin = "server";
            s.remoteId = j.getString("id");
            s.tripRemote = j.getString("trip");
            s.type = j.optString("type", "other");
            s.title = j.getString("title");
            s.level = level;                       // never trust a «public» label inside the team section, or vice versa
            s.note = j.optString("note", "");
            s.sort = j.optInt("sort", i);
            JSONArray kw = j.optJSONArray("keywords");
            if (kw != null) for (int k = 0; k < kw.length(); k++) {
                String n = TextNorm.normalize(kw.optString(k, "")).trim();
                if (!n.isEmpty()) s.keywords.add(n);
            }
            JSONArray vs = j.getJSONArray("variants");
            for (int k = 0; k < vs.length(); k++) {
                JSONObject v = vs.getJSONObject(k);
                String body = v.getString("body");
                if (!body.isEmpty()) s.variants.add(new Snippet.Variant(v.optString("platform", "any"), v.optString("length", "short"), body));
            }
            if (s.variants.isEmpty() || s.title.isEmpty()) continue;
            s.body = s.bodyFor("any", false);
            items.add(s);
        }
    }

    /** «إلغاء الربط»: forgets the key and removes the team texts from this phone. */
    public static void unlink(Context context) {
        Context c = context.getApplicationContext();
        KbCrypto.clear(c);
        LibraryDb.get(c).clearServerTeam();
        store(c).edit().putInt("team", TEAM_UNLINKED).putBoolean("applied_linked", false).apply();
    }
}
