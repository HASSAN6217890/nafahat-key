package com.nafahat.key;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Purely local keyboard preferences; never records typed text. */
public final class KeyboardPrefs {
    private static final String PREF = "nafahatkey_keyboard_settings_v2";
    public static final List<String> DEFAULT_TYPES = Arrays.asList(
        "واتساب", "إنستغرام قصير", "رد", "أسعار", "عنوان/روابط", "أخرى");
    private final SharedPreferences store;

    public KeyboardPrefs(Context context) {
        store = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public String theme() {
        String t = store.getString("theme", "nafahat");
        return isTheme(t) ? t : "nafahat";
    }
    public void setTheme(String theme) { if (isTheme(theme)) store.edit().putString("theme", theme).apply(); }
    private static boolean isTheme(String t) {
        return "nafahat".equals(t) || "dark".equals(t) || "light".equals(t) || "system".equals(t);
    }
    public Theme resolveTheme(Context c) { return Theme.forId(c, theme()); }

    /** Keyboard height 85..125 percent (v0.3 key, so the old 80..140 value does not leak in). */
    public int heightPercent() { return clamp(store.getInt("height_v3", 100), 85, 125); }
    public void setHeightPercent(int v) { store.edit().putInt("height_v3", clamp(v, 85, 125)).apply(); }
    public int fontPercent() { return clamp(store.getInt("font_v3", 100), 85, 125); }
    public void setFontPercent(int v) { store.edit().putInt("font_v3", clamp(v, 85, 125)).apply(); }

    public boolean numberRow() { return store.getBoolean("number_row_v3", true); }
    public void setNumberRow(boolean yes) { store.edit().putBoolean("number_row_v3", yes).apply(); }
    public boolean vibrate() { return store.getBoolean("vibrate_v3", true); }
    public void setVibrate(boolean yes) { store.edit().putBoolean("vibrate_v3", yes).apply(); }
    /** 1 light, 2 medium, 3 strong. */
    public int vibrationLevel() { return clamp(store.getInt("vibration_level", 1), 1, 3); }
    public void setVibrationLevel(int v) { store.edit().putInt("vibration_level", clamp(v, 1, 3)).apply(); }
    public boolean autoCap() { return store.getBoolean("auto_cap", true); }
    public void setAutoCap(boolean yes) { store.edit().putBoolean("auto_cap", yes).apply(); }
    public boolean autoCorrect() { return store.getBoolean("auto_correct", true); }
    public void setAutoCorrect(boolean yes) { store.edit().putBoolean("auto_correct", yes).apply(); }
    /** 0 off, 1 left hand, 2 right hand. */
    public int oneHanded() { return clamp(store.getInt("one_handed", 0), 0, 2); }
    public void setOneHanded(int v) { store.edit().putInt("one_handed", clamp(v, 0, 2)).apply(); }
    public boolean arabic() { return store.getBoolean("arabic", true); }
    public void setArabic(boolean yes) { store.edit().putBoolean("arabic", yes).apply(); }

    public List<String> snippetTypes() {
        List<String> out = new ArrayList<>();
        String raw = store.getString("snippet_types", null);
        if (raw != null) {
            try {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) {
                    String s = a.optString(i, "").trim();
                    if (!s.isEmpty() && !out.contains(s)) out.add(s);
                }
            } catch (Exception ignored) { /* fall back to defaults, never overwrite */ }
        }
        if (out.isEmpty()) out.addAll(DEFAULT_TYPES);
        return out;
    }
    public void setSnippetTypes(List<String> types) {
        JSONArray a = new JSONArray();
        for (String t : types) if (t != null && !t.trim().isEmpty()) a.put(t.trim());
        if (a.length() > 0) store.edit().putString("snippet_types", a.toString()).apply();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("theme", theme());
            o.put("height", heightPercent());
            o.put("font", fontPercent());
            o.put("number_row", numberRow());
            o.put("vibrate", vibrate());
            o.put("vibration_level", vibrationLevel());
            o.put("auto_cap", autoCap());
            o.put("auto_correct", autoCorrect());
            o.put("one_handed", oneHanded());
            JSONArray types = new JSONArray();
            for (String t : snippetTypes()) types.put(t);
            o.put("types", types);
        } catch (Exception ignored) { }
        return o;
    }

    public void applyJson(JSONObject o) {
        if (o == null) return;
        if (o.has("theme")) setTheme(o.optString("theme"));
        if (o.has("height")) setHeightPercent(o.optInt("height", 100));
        if (o.has("font")) setFontPercent(o.optInt("font", 100));
        if (o.has("number_row")) setNumberRow(o.optBoolean("number_row", true));
        if (o.has("vibrate")) setVibrate(o.optBoolean("vibrate", true));
        if (o.has("vibration_level")) setVibrationLevel(o.optInt("vibration_level", 1));
        if (o.has("auto_cap")) setAutoCap(o.optBoolean("auto_cap", true));
        if (o.has("auto_correct")) setAutoCorrect(o.optBoolean("auto_correct", true));
        if (o.has("one_handed")) setOneHanded(o.optInt("one_handed", 0));
        JSONArray types = o.optJSONArray("types");
        if (types != null) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < types.length(); i++) list.add(types.optString(i, ""));
            setSnippetTypes(list);
        }
    }

    private static int clamp(int n, int lo, int hi) { return Math.max(lo, Math.min(hi, n)); }
}
