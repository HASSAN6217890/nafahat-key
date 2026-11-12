package com.nafahat.key;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for the app screens (formal Arabic UI, Nafahat colours). */
public final class UiKit {
    public static final int GREEN = Theme.GREEN;
    public static final int GOLD = Theme.GOLD;
    public static final int INK = Theme.INK;
    public static final int SURFACE = Color.rgb(242, 245, 244);
    public static final int LINE = Color.rgb(222, 229, 226);
    private UiKit() { }

    public static int dp(Context c, float value) { return (int) (value * c.getResources().getDisplayMetrics().density + 0.5f); }

    public static GradientDrawable rounded(int color, float radius, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        return d;
    }

    public static GradientDrawable outlined(int fill, int stroke, float radius, Context c) {
        GradientDrawable d = rounded(fill, radius, c);
        d.setStroke(dp(c, 1), stroke);
        return d;
    }

    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }

    public static TextView text(Context c, String value, int sp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(INK);
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    public static Button button(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(GREEN, 12, c));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        b.setStateListAnimator(null);
        return b;
    }

    public static Button secondary(Context c, String label) {
        Button b = button(c, label);
        b.setTextColor(GREEN);
        b.setBackground(outlined(Color.WHITE, GREEN, 12, c));
        return b;
    }

    public static TextView chip(Context c, String label, boolean active) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextSize(13);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        t.setTextColor(active ? Color.WHITE : INK);
        t.setBackground(active ? rounded(GOLD, 16, c) : outlined(Color.WHITE, LINE, 16, c));
        return t;
    }

    public static void pad(View v, Context c, int x, int y) {
        v.setPadding(dp(c, x), dp(c, y), dp(c, x), dp(c, y));
    }
}
