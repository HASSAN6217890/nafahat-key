package com.nafahat.key;

import android.graphics.RectF;
import java.util.Locale;

/** One key described as data. Geometry (rect) is filled in by KeyboardView. */
public final class Key {
    public static final int CHAR = 0;        // inserts output (single char or short text)
    public static final int SHIFT = 1;
    public static final int DELETE = 2;
    public static final int SPACE = 3;
    public static final int ENTER = 4;
    public static final int TO_SYMBOLS = 5;
    public static final int TO_LETTERS = 6;
    public static final int SYM_PAGE = 7;
    public static final int ONE_HAND_SWAP = 8;
    public static final int ONE_HAND_EXIT = 9;
    public static final int GLOBE = 10;
    /** Cursor arrow; output is "left", "right", "up" or "down". */
    public static final int ARROW = 11;

    public final int type;
    public final String label;
    public final String output;
    /** Secondary symbol drawn small in the corner; first option of the long-press menu. */
    public final String hint;
    /** Long-press options (hint first); null when the key has none. */
    public final String[] popup;
    /** Width in layout units. */
    public final float width;
    public final boolean function;
    /** Arabic TalkBack description for symbol / function keys. */
    public final String description;
    /** Letter that changes case with shift (English letters). */
    public final boolean caseable;
    /** Label drawn while shift is active. */
    public final String upper;

    public final RectF rect = new RectF();

    public Key(int type, String label, String output, String hint, String[] popup, float width,
               boolean function, String description) {
        this.type = type;
        this.label = label;
        this.output = output;
        this.hint = hint;
        this.popup = popup;
        this.width = width;
        this.function = function;
        this.description = description;
        this.caseable = type == CHAR && output != null && output.length() == 1
            && output.charAt(0) >= 'a' && output.charAt(0) <= 'z';
        this.upper = caseable ? label.toUpperCase(Locale.US) : label;
    }

    public static Key ch(String c) { return new Key(CHAR, c, c, null, null, 1f, false, null); }

    public static Key ch(String c, String hint, String... more) {
        return new Key(CHAR, c, c, hint, Layouts.merge(hint, more), 1f, false, null);
    }

    public static Key fn(int type, String label, float width, String description) {
        return new Key(type, label, null, null, null, width, true, description);
    }

    public Key withWidth(float w) {
        return new Key(type, label, output, hint, popup, w, function, description);
    }

    /** Text spoken by TalkBack (Arabic names for symbols). */
    public String spoken() {
        if (description != null) return description;
        if (label == null) return "";
        String name = SYMBOL_NAMES.get(label);
        return name != null ? name : label;
    }

    private static final java.util.Map<String, String> SYMBOL_NAMES = new java.util.HashMap<>();
    static {
        String[][] n = {
            {"@", "علامة آت"}, {"#", "علامة الشباك"}, {"$", "دولار"}, {"%", "نسبة مئوية"}, {"&", "علامة و"},
            {"*", "نجمة"}, {"-", "شرطة"}, {"+", "زائد"}, {"(", "قوس مفتوح"}, {")", "قوس مغلق"},
            {"!", "علامة تعجب"}, {"\"", "علامة تنصيص"}, {"'", "فاصلة علوية"}, {":", "نقطتان"},
            {";", "فاصلة منقوطة"}, {"/", "شرطة مائلة"}, {"؟", "علامة استفهام"}, {"?", "علامة استفهام لاتينية"},
            {"~", "مدة"}, {"^", "علامة الإقحام"}, {"|", "خط عمودي"}, {"\\", "شرطة مائلة عكسية"},
            {"<", "أصغر من"}, {">", "أكبر من"}, {"[", "قوس مربع مفتوح"}, {"]", "قوس مربع مغلق"},
            {"{", "قوس معقوف مفتوح"}, {"}", "قوس معقوف مغلق"}, {"_", "شرطة سفلية"}, {"=", "يساوي"},
            {"€", "يورو"}, {"£", "جنيه"}, {"₺", "ليرة تركية"}, {"…", "نقاط حذف"}, {"؛", "فاصلة منقوطة عربية"},
            {",", "فاصلة لاتينية"}, {"`", "علامة نبر"}, {"«", "علامة تنصيص مفتوحة"}, {"»", "علامة تنصيص مغلقة"},
            {"°", "درجة"}, {"×", "ضرب"}, {"÷", "قسمة"}, {"±", "زائد ناقص"}, {"•", "نقطة تعداد"},
            {"ـَ", "فتحة"}, {"ـً", "تنوين فتح"}, {"ـُ", "ضمة"}, {"ـٌ", "تنوين ضم"}, {"ـِ", "كسرة"},
            {"ـٍ", "تنوين كسر"}, {"ـّ", "شدة"}, {"ـْ", "سكون"}, {"الله", "لفظ الجلالة"}};
        for (String[] e : n) SYMBOL_NAMES.put(e[0], e[1]);
    }
}
