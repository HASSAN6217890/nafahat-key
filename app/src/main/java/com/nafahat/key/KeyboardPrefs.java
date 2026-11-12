package com.nafahat.key;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.content.res.Configuration;

/** No typing history is retained by this settings class. */
public final class KeyboardPrefs {
    private final SharedPreferences store;
    private final Context context;
    public KeyboardPrefs(Context context) {
        this.context=context.getApplicationContext();
        store=context.getApplicationContext().getSharedPreferences("nafahatkey_keyboard_settings_v2",Context.MODE_PRIVATE);
    }
    public String theme(){return store.getString("theme","nafahat");}
    public void setTheme(String name){
        if(name.equals("nafahat")||name.equals("dark")||name.equals("light")||name.equals("system"))
            store.edit().putString("theme",name).apply();
    }
    public boolean isDark(){
        if(theme().equals("light"))return false;
        if(theme().equals("system"))
            return (context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)
                ==Configuration.UI_MODE_NIGHT_YES;
        return true;
    }
    public int heightPercent(){return clamp(store.getInt("height",100),85,125);}
    public void setHeightPercent(int n){store.edit().putInt("height",clamp(n,85,125)).apply();}
    public int fontSize(){return clamp(store.getInt("font",26),18,36);}
    public void setFontSize(int n){store.edit().putInt("font",clamp(n,18,36)).apply();}
    public boolean numberRow(){return store.getBoolean("number_row",true);}
    public void setNumberRow(boolean b){store.edit().putBoolean("number_row",b).apply();}
    public boolean vibrate(){return store.getBoolean("vibrate",true);}
    public void setVibrate(boolean b){store.edit().putBoolean("vibrate",b).apply();}
    public int vibrationStrength(){return clamp(store.getInt("vibration_strength",1),0,3);}
    public void setVibrationStrength(int n){store.edit().putInt("vibration_strength",clamp(n,0,3)).apply();}
    public boolean autoCap(){return store.getBoolean("auto_cap",true);}
    public void setAutoCap(boolean b){store.edit().putBoolean("auto_cap",b).apply();}
    public boolean autoCorrect(){return store.getBoolean("auto_correct",true);}
    public void setAutoCorrect(boolean b){store.edit().putBoolean("auto_correct",b).apply();}
    public int background(){return isDark()?Color.rgb(23,28,31):Color.rgb(245,248,245);}
    public int keyColor(){return isDark()?Color.rgb(49,56,54):Color.rgb(228,235,230);}
    public int specialColor(){return Color.rgb(15,97,51);}
    public int textColor(){return isDark()?Color.WHITE:Color.rgb(23,28,31);}
    public int highlightColor(){return Color.rgb(200,131,56);}
    public int scaled(int dp){return Math.round(dp*heightPercent()/100f);}
    private static int clamp(int n,int lo,int hi){return Math.min(hi,Math.max(lo,n));}
}
