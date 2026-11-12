package com.nafahat.key;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** Purely local keyboard preferences; never records typed text. */
public final class KeyboardPrefs {
    private static final String PREF = "nafahatkey_keyboard_settings_v2";
    private final SharedPreferences store;
    public KeyboardPrefs(Context context) {
        store = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    public String theme() { return store.getString("theme", "nafahat"); }
    public void setTheme(String theme) {
        if (!theme.equals("nafahat") && !theme.equals("light") && !theme.equals("dark")) return;
        store.edit().putString("theme", theme).apply();
    }
    public boolean isDark() { return "dark".equals(theme()); }
    public int heightPercent() { return clamp(store.getInt("height", 100), 80, 140); }
    public void setHeightPercent(int value) { store.edit().putInt("height", clamp(value,80,140)).apply(); }
    public int fontSize() { return clamp(store.getInt("font", 22), 17, 30); }
    public void setFontSize(int value) { store.edit().putInt("font", clamp(value,17,30)).apply(); }
    public boolean numberRow() { return store.getBoolean("number_row", false); }
    public void setNumberRow(boolean yes) { store.edit().putBoolean("number_row", yes).apply(); }
    public boolean vibrate() { return store.getBoolean("vibrate", false); }
    public void setVibrate(boolean yes) { store.edit().putBoolean("vibrate", yes).apply(); }

    public int background() {
        return isDark() ? Color.rgb(23,28,31) :
            "light".equals(theme()) ? Color.rgb(242,245,244) : Color.rgb(222,234,227);
    }
    public int keyColor() { return isDark() ? Color.rgb(47,53,53) : Color.WHITE; }
    public int specialColor() {
        return isDark() ? Color.rgb(15,97,51) :
            "light".equals(theme()) ? Color.rgb(222,231,226) : Color.rgb(201,224,208);
    }
    public int textColor() { return isDark() ? Color.WHITE : Color.rgb(23,28,31); }
    public int highlightColor() { return Color.rgb(200,131,56); }
    public int scaled(int base) { return Math.max(24, base*heightPercent()/100); }
    private static int clamp(int n,int lo,int hi) { return Math.max(lo,Math.min(hi,n)); }
}
