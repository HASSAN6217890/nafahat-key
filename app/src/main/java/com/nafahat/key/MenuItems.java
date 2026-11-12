package com.nafahat.key;

import java.util.ArrayList;
import java.util.List;

/** The ⊞ menu entries (id, title, icon). The order and the strip shortcuts are user settings. */
public final class MenuItems {
    private MenuItems() { }

    public static final int LIBRARY = 1, THEME = 2, RESIZE = 3, ONE_HAND = 4, EDIT = 5, DIACRITICS = 6,
        LANGUAGE = 7, UNDO = 8, SETTINGS = 9, SWITCH_IME = 11, REPLY = 12;

    public static final int[] IDS = {LIBRARY, REPLY, THEME, RESIZE, ONE_HAND, EDIT, DIACRITICS, LANGUAGE, UNDO, SETTINGS, SWITCH_IME};

    public static List<Integer> defaults() {
        List<Integer> out = new ArrayList<>();
        for (int id : IDS) out.add(id);
        return out;
    }

    public static String title(int id) {
        switch (id) {
            case LIBRARY: return "النصوص";
            case REPLY: return "رد على رسالة";
            case THEME: return "المظهر";
            case RESIZE: return "تغيير الحجم";
            case ONE_HAND: return "بيد واحدة";
            case EDIT: return "تعديل النص";
            case DIACRITICS: return "الحركات";
            case LANGUAGE: return "عربي / English";
            case UNDO: return "تراجع";
            case SETTINGS: return "الإعدادات";
            case SWITCH_IME: return "تبديل لوحة المفاتيح";
            default: return "";
        }
    }

    public static String glyph(int id) {
        switch (id) {
            case LIBRARY: return "📋";
            case REPLY: return "💬";
            case THEME: return "🎨";
            case RESIZE: return "↕";
            case ONE_HAND: return "✋";
            case EDIT: return "✂";
            case DIACRITICS: return "ـَ";
            case LANGUAGE: return "🌐";
            case UNDO: return "↶";
            case SETTINGS: return "⚙";
            case SWITCH_IME: return "⌨";
            default: return "";
        }
    }
}
