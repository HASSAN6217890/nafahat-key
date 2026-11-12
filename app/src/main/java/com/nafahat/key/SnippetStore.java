package com.nafahat.key;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stores only explicitly added user snippets. Never collects keystrokes or clipboard history. */
public final class SnippetStore {
    private static final String PREF = "nafahatkey_personal_v1";
    private static final String KEY = "items";
    private final SharedPreferences prefs;

    public SnippetStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    public List<Snippet> getPersonal() {
        List<Snippet> list = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject obj = data.getJSONObject(i);
                list.add(new Snippet(obj.optString("id"), obj.optString("title"),
                    obj.optString("text"), obj.optString("category", "شخصية"), false));
            }
        } catch (Exception ignored) { /* corrupted local data must never crash typing */ }
        return list;
    }
    public void add(String title, String text, String category) {
        if (title.trim().isEmpty() || text.trim().isEmpty()) return;
        List<Snippet> items = getPersonal();
        items.add(new Snippet(UUID.randomUUID().toString(), title.trim(), text.trim(),
                category.trim().isEmpty() ? "شخصية" : category.trim(), false));
        save(items);
    }
    public void delete(String id) {
        List<Snippet> items = getPersonal();
        items.removeIf(item -> item.id.equals(id));
        save(items);
    }
    private void save(List<Snippet> items) {
        JSONArray array = new JSONArray();
        for (Snippet item : items) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", item.id);
                obj.put("title", item.title);
                obj.put("text", item.text);
                obj.put("category", item.category);
                array.put(obj);
            } catch (Exception ignored) { }
        }
        prefs.edit().putString(KEY, array.toString()).apply();
    }
}
