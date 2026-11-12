package com.nafahat.key;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/** Keyboard colour sets. Only the Nafahat palette (dark ink, green, one gold) plus neutrals. */
public final class Theme {
    public static final int GOLD = 0xFFC88338;
    public static final int GREEN = 0xFF0F6133;
    public static final int INK = 0xFF171C1F;

    public final String id;
    public final boolean dark;
    public final int background;
    public final int key;
    public final int keyPressed;
    public final int function;
    public final int functionPressed;
    public final int functionText;
    public final int text;
    public final int hint;
    public final int accent = GOLD;
    public final int strip;
    public final int stripText;
    public final int stripDim;
    public final int divider;
    public final int card;
    public final int cardText;
    public final int bubble;
    public final int bubbleText;
    /** Background art: 0 none, 1 الروضة (green dome), 2 مكة (the Kaaba). Drawn by ThemeArt. */
    public int art;
    /** Display name in the theme gallery. */
    public String title = "";

    private Theme(String id, boolean dark, int background, int key, int keyPressed, int function,
                  int functionPressed, int functionText, int text, int hint, int strip, int stripText,
                  int stripDim, int divider, int card, int cardText, int bubble, int bubbleText) {
        this.id = id; this.dark = dark; this.background = background; this.key = key;
        this.keyPressed = keyPressed; this.function = function; this.functionPressed = functionPressed;
        this.functionText = functionText; this.text = text; this.hint = hint; this.strip = strip;
        this.stripText = stripText; this.stripDim = stripDim; this.divider = divider; this.card = card;
        this.cardText = cardText; this.bubble = bubble; this.bubbleText = bubbleText;
    }

    public static Theme nafahat() {
        return new Theme("nafahat", true, INK, 0xFF2B3634, 0xFF3D4B48, GREEN, 0xFF16804A, Color.WHITE,
            Color.WHITE, 0x99FFFFFF, INK, Color.WHITE, 0xB3FFFFFF, 0x33FFFFFF, 0xFF2B3634, Color.WHITE,
            0xFF3D4B48, Color.WHITE);
    }

    public static Theme darkTheme() {
        return new Theme("dark", true, 0xFF0F1214, 0xFF272C2F, 0xFF3A4044, 0xFF353B3F, 0xFF4A5156,
            Color.WHITE, Color.WHITE, 0x99FFFFFF, 0xFF0F1214, Color.WHITE, 0xB3FFFFFF, 0x33FFFFFF,
            0xFF272C2F, Color.WHITE, 0xFF3A4044, Color.WHITE);
    }

    public static Theme lightTheme() {
        return new Theme("light", false, 0xFFE6ECE9, Color.WHITE, 0xFFD3DDD8, GREEN, 0xFF16804A,
            Color.WHITE, INK, 0x99171C1F, 0xFFE6ECE9, INK, 0xB3171C1F, 0x33171C1F, Color.WHITE, INK,
            Color.WHITE, INK);
    }

    /** الروضة: deep ink-green gradient with a very faint green dome; translucent keys, letters first. */
    public static Theme rawda() {
        Theme t = new Theme("rawda", true, 0xFF0F1F19, 0xB82B3634, 0xD03D4B48, 0xD00F6133, 0xE016804A, Color.WHITE,
            Color.WHITE, 0x99FFFFFF, 0xFF0F1F19, Color.WHITE, 0xB3FFFFFF, 0x33FFFFFF, 0xFF22302B, Color.WHITE,
            0xFF3D4B48, Color.WHITE);
        t.art = 1;
        t.title = "الروضة";
        return t;
    }

    /** مكة: ink background with a very faint Kaaba and gold band; translucent keys. */
    public static Theme makkah() {
        Theme t = new Theme("makkah", true, 0xFF121517, 0xB82B3634, 0xD03D4B48, 0xD00F6133, 0xE016804A, Color.WHITE,
            Color.WHITE, 0x99FFFFFF, 0xFF121517, Color.WHITE, 0xB3FFFFFF, 0x33FFFFFF, 0xFF262B2E, Color.WHITE,
            0xFF3D4B48, Color.WHITE);
        t.art = 2;
        t.title = "مكة";
        return t;
    }

    /** Gallery order: id, title. */
    public static final String[][] GALLERY = {
        {"nafahat", "نفحات داكن"}, {"light", "نفحات فاتح"}, {"rawda", "الروضة"}, {"makkah", "مكة"},
        {"dark", "داكن محايد"}, {"system", "حسب نظام الهاتف"}};

    /** Resolves a stored theme id ("nafahat", "dark", "light", "system", "rawda", "makkah"). */
    public static Theme forId(Context context, String id) {
        if ("dark".equals(id)) return darkTheme();
        if ("light".equals(id)) return lightTheme();
        if ("rawda".equals(id)) return rawda();
        if ("makkah".equals(id)) return makkah();
        if ("system".equals(id)) {
            int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return night == Configuration.UI_MODE_NIGHT_YES ? darkTheme() : lightTheme();
        }
        return nafahat();
    }
}
