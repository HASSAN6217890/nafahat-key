package com.nafahat.key;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;

public final class UiKit {
    public static final int GREEN = Color.rgb(15,97,51);
    public static final int GOLD = Color.rgb(200,131,56);
    public static final int INK = Color.rgb(23,28,31);
    public static final int SURFACE = Color.rgb(242,245,244);
    private UiKit() { }
    public static int dp(Context c,float value) { return (int)(value*c.getResources().getDisplayMetrics().density+0.5f); }
    public static GradientDrawable rounded(int color,float radius,Context c) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL);return l;
    }
    public static TextView text(Context c,String value,int sp,boolean bold) {
        TextView t=new TextView(c);t.setText(value);t.setTextSize(sp);t.setTextColor(INK);
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    public static Button button(Context c,String label) {
        Button b=new Button(c); b.setText(label); b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(Color.WHITE);b.setBackground(rounded(GREEN,11,c));
        b.setMinHeight(0);b.setMinimumHeight(0); b.setMinWidth(0);b.setMinimumWidth(0);return b;
    }
    public static void pad(View v,Context c,int x,int y) {
        v.setPadding(dp(c,x),dp(c,y),dp(c,x),dp(c,y));
    }
}
