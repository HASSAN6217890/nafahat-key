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
    private final Context context;

    public KeyboardPrefs(Context context) {
        this.context = context;
        store = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        migrateLegacyOneHand();
    }

    /** v0.3 stored one-handed mode as one value; copy it into both orientations once, then drop it. */
    private void migrateLegacyOneHand() {
        if (!store.contains("one_handed")) return;
        int legacy = store.getInt("one_handed", 0);
        SharedPreferences.Editor e = store.edit();
        if (legacy == 1 || legacy == 2) {
            for (String o : new String[]{"_p", "_l"}) {
                if (!store.contains("width" + o)) e.putInt("width" + o, 84);
                if (!store.contains("offset" + o)) e.putInt("offset" + o, legacy == 1 ? 0 : 100);
            }
        }
        e.remove("one_handed").apply();
    }
    /** Sizes are stored separately for portrait and landscape. */
    public boolean landscape() {
        return context.getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }
    private String o() { return landscape() ? "_l" : "_p"; }

    public String theme() {
        String t = store.getString("theme", "nafahat");
        return isTheme(t) ? t : "nafahat";
    }
    public void setTheme(String theme) { if (isTheme(theme)) store.edit().putString("theme", theme).apply(); }
    private static boolean isTheme(String t) {
        return "nafahat".equals(t) || "dark".equals(t) || "light".equals(t) || "system".equals(t)
            || "rawda".equals(t) || "makkah".equals(t);
    }
    public Theme resolveTheme(Context c) { return Theme.forId(c, theme()); }

    /** Keyboard height 85..125 percent, per orientation. */
    public int heightPercent() {
        int legacy = store.getInt("height_v3", 100);
        return clamp(store.getInt("height" + o(), landscape() ? Math.min(legacy, 100) : legacy), 70, 140);
    }
    public void setHeightPercent(int v) { store.edit().putInt("height" + o(), clamp(v, 70, 140)).apply(); }
    public int fontPercent() { return clamp(store.getInt("font_v3", 100), 85, 125); }
    public void setFontPercent(int v) { store.edit().putInt("font_v3", clamp(v, 85, 125)).apply(); }
    /** Width of the key area 55..100 percent of the screen, per orientation. */
    public int widthPercent() { return clamp(store.getInt("width" + o(), 100), 55, 100); }
    public void setWidthPercent(int v) { store.edit().putInt("width" + o(), clamp(v, 55, 100)).apply(); }
    /** Horizontal position of a narrowed keyboard: 0 left .. 100 right, per orientation. */
    public int offsetPercent() { return clamp(store.getInt("offset" + o(), 50), 0, 100); }
    public void setOffsetPercent(int v) { store.edit().putInt("offset" + o(), clamp(v, 0, 100)).apply(); }
    /** Lift above the bottom edge in dp, per orientation. */
    public int liftDp() { return clamp(store.getInt("lift" + o(), 0), 0, 160); }
    public void setLiftDp(int v) { store.edit().putInt("lift" + o(), clamp(v, 0, 160)).apply(); }
    public void resetSize() {
        store.edit().remove("height" + o()).remove("width" + o()).remove("offset" + o()).remove("lift" + o())
            .putInt("height_v3", 100).apply();
    }

    // Typing options (spec 0.4 section 2)
    public int longPressMs() { return clamp(store.getInt("long_press_ms", 300), 150, 800); }
    public void setLongPressMs(int v) { store.edit().putInt("long_press_ms", clamp(v, 150, 800)).apply(); }
    public boolean showHints() { return store.getBoolean("show_hints", true); }
    public void setShowHints(boolean v) { store.edit().putBoolean("show_hints", v).apply(); }
    public boolean keyPopups() { return store.getBoolean("key_popups", true); }
    public void setKeyPopups(boolean v) { store.edit().putBoolean("key_popups", v).apply(); }
    public boolean largerText() { return store.getBoolean("larger_text", false); }
    public void setLargerText(boolean v) { store.edit().putBoolean("larger_text", v).apply(); }
    public boolean arrowRow() { return store.getBoolean("arrow_row", false); }
    public void setArrowRow(boolean v) { store.edit().putBoolean("arrow_row", v).apply(); }
    public boolean doubleSpacePeriod() { return store.getBoolean("double_space_period", true); }
    public void setDoubleSpacePeriod(boolean v) { store.edit().putBoolean("double_space_period", v).apply(); }
    public boolean autoSpacePunct() { return store.getBoolean("auto_space_punct", false); }
    public void setAutoSpacePunct(boolean v) { store.edit().putBoolean("auto_space_punct", v).apply(); }
    public boolean swipeDeleteWords() { return store.getBoolean("swipe_delete_words", true); }
    public void setSwipeDeleteWords(boolean v) { store.edit().putBoolean("swipe_delete_words", v).apply(); }
    public boolean spaceInsertsPrediction() { return store.getBoolean("space_inserts_prediction", false); }
    public void setSpaceInsertsPrediction(boolean v) { store.edit().putBoolean("space_inserts_prediction", v).apply(); }
    public boolean undoCorrectionOnBackspace() { return store.getBoolean("undo_on_backspace", true); }
    public void setUndoCorrectionOnBackspace(boolean v) { store.edit().putBoolean("undo_on_backspace", v).apply(); }
    /** How visible the Rawda / Makkah background art is (0..100). */
    public int artStrength() { return clamp(store.getInt("art_strength", 60), 0, 100); }
    public void setArtStrength(int v) { store.edit().putInt("art_strength", clamp(v, 0, 100)).apply(); }

    // ⊞ menu order and the strip shortcuts (spec 0.4 section 3)
    public List<Integer> menuOrder(List<Integer> defaults) {
        List<Integer> out = intList("menu_order");
        for (Integer d : defaults) if (!out.contains(d)) out.add(d);
        out.retainAll(defaults);
        return out;
    }
    public void setMenuOrder(List<Integer> ids) { putIntList("menu_order", ids); }
    public List<Integer> stripShortcuts() {
        if (!store.contains("strip_shortcuts")) return new ArrayList<>(Arrays.asList(1, 5));
        return intList("strip_shortcuts");
    }
    public void setStripShortcuts(List<Integer> ids) { putIntList("strip_shortcuts", ids); }
    private List<Integer> intList(String key) {
        List<Integer> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(store.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) if (!out.contains(a.optInt(i))) out.add(a.optInt(i));
        } catch (Exception ignored) { }
        return out;
    }
    private void putIntList(String key, List<Integer> ids) {
        JSONArray a = new JSONArray();
        for (Integer i : ids) a.put(i);
        store.edit().putString(key, a.toString()).apply();
    }

    public boolean numberRow() { return store.getBoolean("number_row_v3", true); }
    public void setNumberRow(boolean yes) { store.edit().putBoolean("number_row_v3", yes).apply(); }
    public boolean vibrate() { return store.getBoolean("vibrate_v3", true); }
    public void setVibrate(boolean yes) { store.edit().putBoolean("vibrate_v3", yes).apply(); }
    /** 1 light, 2 medium (KEYBOARD_PRESS, default), 3 strong. */
    public int vibrationLevel() { return clamp(store.getInt("vibration_level", 2), 1, 3); }
    public void setVibrationLevel(int v) { store.edit().putInt("vibration_level", clamp(v, 1, 3)).apply(); }
    public boolean autoCap() { return store.getBoolean("auto_cap", true); }
    public void setAutoCap(boolean yes) { store.edit().putBoolean("auto_cap", yes).apply(); }
    public boolean autoCorrect() { return store.getBoolean("auto_correct", true); }
    public void setAutoCorrect(boolean yes) { store.edit().putBoolean("auto_correct", yes).apply(); }
    /** 0 off, 1 left hand, 2 right hand — a preset of the resize mode. */
    public int oneHanded() {
        int w = widthPercent();
        if (w >= 95) return 0;
        float free = 100 - w;                         // empty width in % of the screen
        float gapLeft = free * offsetPercent() / 100f, gapRight = free - gapLeft;
        if (gapRight >= 8 && gapLeft < 3) return 1;   // keys on the left
        if (gapLeft >= 8 && gapRight < 3) return 2;   // keys on the right
        return 0;
    }
    public void setOneHanded(int v) {
        if (v == 0) { setWidthPercent(100); setOffsetPercent(50); }
        else { setWidthPercent(84); setOffsetPercent(v == 1 ? 0 : 100); }
    }
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
            o.put("long_press_ms", longPressMs());
            o.put("show_hints", showHints());
            o.put("key_popups", keyPopups());
            o.put("larger_text", largerText());
            o.put("arrow_row", arrowRow());
            o.put("double_space_period", doubleSpacePeriod());
            o.put("auto_space_punct", autoSpacePunct());
            o.put("swipe_delete_words", swipeDeleteWords());
            o.put("space_inserts_prediction", spaceInsertsPrediction());
            o.put("undo_on_backspace", undoCorrectionOnBackspace());
            o.put("art_strength", artStrength());
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
        if (o.has("vibration_level")) setVibrationLevel(o.optInt("vibration_level", 2));
        if (o.has("auto_cap")) setAutoCap(o.optBoolean("auto_cap", true));
        if (o.has("auto_correct")) setAutoCorrect(o.optBoolean("auto_correct", true));
        if (o.has("long_press_ms")) setLongPressMs(o.optInt("long_press_ms", 300));
        if (o.has("show_hints")) setShowHints(o.optBoolean("show_hints", true));
        if (o.has("key_popups")) setKeyPopups(o.optBoolean("key_popups", true));
        if (o.has("larger_text")) setLargerText(o.optBoolean("larger_text", false));
        if (o.has("arrow_row")) setArrowRow(o.optBoolean("arrow_row", false));
        if (o.has("double_space_period")) setDoubleSpacePeriod(o.optBoolean("double_space_period", true));
        if (o.has("auto_space_punct")) setAutoSpacePunct(o.optBoolean("auto_space_punct", false));
        if (o.has("swipe_delete_words")) setSwipeDeleteWords(o.optBoolean("swipe_delete_words", true));
        if (o.has("space_inserts_prediction")) setSpaceInsertsPrediction(o.optBoolean("space_inserts_prediction", false));
        if (o.has("undo_on_backspace")) setUndoCorrectionOnBackspace(o.optBoolean("undo_on_backspace", true));
        if (o.has("art_strength")) setArtStrength(o.optInt("art_strength", 60));
        JSONArray types = o.optJSONArray("types");
        if (types != null) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < types.length(); i++) list.add(types.optString(i, ""));
            setSnippetTypes(list);
        }
    }

    private static int clamp(int n, int lo, int hi) { return Math.max(lo, Math.min(hi, n)); }
}
