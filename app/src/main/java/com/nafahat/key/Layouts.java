package com.nafahat.key;

import java.util.ArrayList;
import java.util.List;

/**
 * All keyboard pages as data. Arabic order follows SwiftKey exactly (spec section 2),
 * digits are always Western 0123456789.
 */
public final class Layouts {
    private Layouts() { }

    public static final float H_NUM = 46f;
    public static final float H_ROW = 56f;

    public static final String[] DIACRITICS = {"ـَ", "ـً", "ـُ", "ـٌ", "ـِ", "ـٍ", "ـّ", "ـْ"};

    static String[] merge(String first, String... more) {
        List<String> out = new ArrayList<>();
        if (first != null) out.add(first);
        if (more != null) for (String m : more) if (m != null && !out.contains(m)) out.add(m);
        return out.isEmpty() ? null : out.toArray(new String[0]);
    }

    private static Key[] digitsRow(float width) {
        Key[] row = new Key[10];
        for (int i = 0; i < 10; i++) {
            String d = String.valueOf((i + 1) % 10);
            row[i] = Key.ch(d).withWidth(width);
        }
        return row;
    }

    private static Key space(String label, float width) {
        return new Key(Key.SPACE, label, " ", null, null, width, false, "مسافة");
    }

    private static Key enter(float width) { return Key.fn(Key.ENTER, null, width, "إدخال"); }

    private static Key del(float width) { return Key.fn(Key.DELETE, null, width, "حذف"); }

    private static Key globe(float width) { return Key.fn(Key.GLOBE, null, width, "تبديل لوحة المفاتيح"); }

    private static Key toSymbols(float width) { return Key.fn(Key.TO_SYMBOLS, "123", width, "الرموز والأرقام"); }

    // ---------------------------------------------------------------- Arabic
    public static Layout arabic(boolean numberRow) { return arabic(numberRow, false); }

    /** globe: show the 🌐 key that switches to the phone's other keyboards. */
    public static Layout arabic(boolean numberRow, boolean globe) {
        List<Layout.Row> rows = new ArrayList<>();
        if (numberRow) rows.add(new Layout.Row(0f, H_NUM, digitsRow(1.2f)));
        rows.add(new Layout.Row(0f, H_ROW,
            Key.ch("ض", "1"), Key.ch("ص", "2"), Key.ch("ث", "3"), Key.ch("ق", "4"),
            Key.ch("ف", "5"), Key.ch("غ", "6"), Key.ch("ع", "7"), Key.ch("ه", "8", "ة"),
            Key.ch("خ", "9"), Key.ch("ح", "0"), Key.ch("ج", "چ"), Key.ch("د", "الله")));
        rows.add(new Layout.Row(0.5f, H_ROW,
            Key.ch("ش", "@"), Key.ch("س", "#"), Key.ch("ي", "&", "ئ", "ى"), Key.ch("ب", "پ"),
            Key.ch("ل", "لا", "لأ", "لإ", "لآ"), Key.ch("ا", "أ", "إ", "آ", "ٱ"), Key.ch("ت", "="),
            Key.ch("ن", "("), Key.ch("م", ")"), Key.ch("ك", "گ"), Key.ch("ذ", "*")));
        rows.add(new Layout.Row(0.5f, H_ROW,
            Key.ch("ئ", "_"), Key.ch("ء", "~"), Key.ch("ؤ", "-"), Key.ch("ر", "+"),
            Key.ch("ى", ":"), Key.ch("ة", "؛"), Key.ch("و", "/", "ؤ"), Key.ch("ز", "ژ"),
            Key.ch("ط", "\""), Key.ch("ظ", "ـِ"), del(1.5f)));
        if (globe) {
            rows.add(new Layout.Row(0f, H_ROW,
                toSymbols(1.5f), globe(1.1f),
                new Key(Key.CHAR, "،", "،", "ـَ", DIACRITICS, 1.1f, false, "فاصلة، اضغط مطولاً للحركات"),
                space("العربية", 5.3f),
                new Key(Key.CHAR, ".", ".", "؟!", new String[]{"؟", "!", "؛", "…", ":"}, 1.1f, false, "نقطة"),
                enter(1.9f)));
        } else {
            rows.add(new Layout.Row(0f, H_ROW,
                toSymbols(1.6f),
                new Key(Key.CHAR, "،", "،", "ـَ", DIACRITICS, 1.3f, false, "فاصلة، اضغط مطولاً للحركات"),
                space("العربية", 5.8f),
                new Key(Key.CHAR, ".", ".", "؟!", new String[]{"؟", "!", "؛", "…", ":"}, 1.3f, false, "نقطة"),
                enter(2.0f)));
        }
        return new Layout((numberRow ? "ar_num" : "ar") + (globe ? "_g" : ""), 12f, true, true, rows.toArray(new Layout.Row[0]));
    }

    // ---------------------------------------------------------------- English
    /** variant: 0 normal text, 1 email, 2 url. */
    public static Layout english(boolean numberRow, int variant) { return english(numberRow, variant, false); }

    public static Layout english(boolean numberRow, int variant, boolean globe) {
        List<Layout.Row> rows = new ArrayList<>();
        if (numberRow) rows.add(new Layout.Row(0f, H_NUM, digitsRow(1f)));
        String[] r1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
        Key[] k1 = new Key[10];
        for (int i = 0; i < 10; i++) k1[i] = Key.ch(r1[i], String.valueOf((i + 1) % 10), accents(r1[i]));
        rows.add(new Layout.Row(0f, H_ROW, k1));
        String[] r2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l"};
        String[] h2 = {"@", "#", "$", "_", "&", "-", "+", "(", ")"};
        Key[] k2 = new Key[9];
        for (int i = 0; i < 9; i++) k2[i] = Key.ch(r2[i], h2[i], accents(r2[i]));
        rows.add(new Layout.Row(0.5f, H_ROW, k2));
        String[] r3 = {"z", "x", "c", "v", "b", "n", "m"};
        String[] h3 = {"*", "\"", "'", ":", ";", "!", "?"};
        Key[] k3 = new Key[9];
        k3[0] = Key.fn(Key.SHIFT, null, 1.5f, "تكبير الحرف");
        for (int i = 0; i < 7; i++) k3[i + 1] = Key.ch(r3[i], h3[i], accents(r3[i]));
        k3[8] = del(1.5f);
        rows.add(new Layout.Row(0f, H_ROW, k3));
        Key dot = new Key(Key.CHAR, ".", ".", "?!", new String[]{"?", "!", ",", ";", ":", "…"}, 1f, false, "نقطة");
        if (variant == 1 || variant == 2) {
            String extra = variant == 1 ? "@" : "/";
            Key com = new Key(Key.CHAR, ".com", ".com", null, new String[]{".com", ".net", ".org", ".sy", ".sa"}, 1.5f, false, "دوت كوم");
            if (globe) rows.add(new Layout.Row(0f, H_ROW,
                toSymbols(1.3f), globe(1f), Key.ch(extra), space("English", 2.7f), com, dot, enter(1.5f)));
            else rows.add(new Layout.Row(0f, H_ROW,
                toSymbols(1.5f), Key.ch(extra), space("English", 3.5f), com, dot, enter(1.5f)));
        } else {
            Key comma = new Key(Key.CHAR, ",", ",", null, null, 1f, false, "فاصلة");
            if (globe) rows.add(new Layout.Row(0f, H_ROW,
                toSymbols(1.3f), globe(1f), comma, space("English", 3.7f), dot, enter(2.0f)));
            else rows.add(new Layout.Row(0f, H_ROW,
                toSymbols(1.5f), comma, space("English", 4.5f), dot, enter(2.0f)));
        }
        String id = "en" + (numberRow ? "_num" : "") + (variant == 1 ? "_mail" : variant == 2 ? "_url" : "")
            + (globe ? "_g" : "");
        return new Layout(id, 10f, false, true, rows.toArray(new Layout.Row[0]));
    }

    private static String[] accents(String c) {
        switch (c) {
            case "a": return new String[]{"à", "á", "â", "ä", "ã"};
            case "e": return new String[]{"è", "é", "ê", "ë"};
            case "i": return new String[]{"ì", "í", "î", "ï"};
            case "o": return new String[]{"ò", "ó", "ô", "ö", "õ"};
            case "u": return new String[]{"ù", "ú", "û", "ü"};
            case "n": return new String[]{"ñ"};
            case "c": return new String[]{"ç"};
            case "s": return new String[]{"ß"};
            default: return null;
        }
    }

    // ---------------------------------------------------------------- Symbols (two pages, no duplicates)
    public static Layout symbols(int page, boolean arabic) {
        Key letters = Key.fn(Key.TO_LETTERS, arabic ? "أبج" : "ABC", 1.5f, "العودة إلى الحروف");
        Key bottomComma = new Key(Key.CHAR, "،", "،", null, null, 1f, false, "فاصلة");
        Key bottomDot = new Key(Key.CHAR, ".", ".", null, null, 1f, false, "نقطة");
        Layout.Row bottom = new Layout.Row(0f, H_ROW, letters, bottomComma, space(arabic ? "العربية" : "English", 4.5f),
            bottomDot, enter(2f));
        if (page == 1) {
            return new Layout("sym1", 10f, arabic, false,
                new Layout.Row(0f, H_NUM, digitsRow(1f)),
                new Layout.Row(0f, H_ROW, sym("@", "#", "$", "%", "&", "*", "-", "+", "(", ")")),
                new Layout.Row(0f, H_ROW, concat(Key.fn(Key.SYM_PAGE, "1/2", 1.5f, "الصفحة الثانية من الرموز"),
                    sym("!", "\"", "'", ":", ";", "/", "؟"), del(1.5f))),
                bottom);
        }
        return new Layout("sym2", 10f, arabic, false,
            new Layout.Row(0f, H_ROW, sym("~", "^", "|", "\\", "<", ">", "[", "]", "{", "}")),
            new Layout.Row(0f, H_ROW, sym("_", "=", "€", "£", "₺", "…", "؛", "?", ",", "`")),
            new Layout.Row(0f, H_ROW, concat(Key.fn(Key.SYM_PAGE, "2/2", 1.5f, "الصفحة الأولى من الرموز"),
                sym("«", "»", "°", "×", "÷", "±", "•"), del(1.5f))),
            bottom);
    }

    // ---------------------------------------------------------------- Diacritics page (from the ⊞ menu)
    public static Layout diacritics() {
        Key[] r1 = new Key[8];
        for (int i = 0; i < 8; i++) {
            String label = DIACRITICS[i];
            r1[i] = new Key(Key.CHAR, label, label.substring(1), null, null, 1f, false, null);
        }
        Key[] r2 = {
            new Key(Key.CHAR, "ـٰ", "ٰ", null, null, 1f, false, "ألف خنجرية"),
            new Key(Key.CHAR, "ـٓ", "ٓ", null, null, 1f, false, "مدة"),
            new Key(Key.CHAR, "ـٔ", "ٔ", null, null, 1f, false, "همزة فوق"),
            new Key(Key.CHAR, "ـٕ", "ٕ", null, null, 1f, false, "همزة تحت"),
            new Key(Key.CHAR, "ـ", "ـ", null, null, 1f, false, "تطويل"),
            Key.ch("ء"), Key.ch("أ"), Key.ch("إ")};
        Key[] r3 = {Key.ch("آ"), Key.ch("ٱ"), Key.ch("ؤ"), Key.ch("ئ"), Key.ch("ى"), Key.ch("ة"), del(2f)};
        return new Layout("diac", 8f, true, false,
            new Layout.Row(0f, H_ROW, r1),
            new Layout.Row(0f, H_ROW, r2),
            new Layout.Row(0f, H_ROW, r3),
            new Layout.Row(0f, H_ROW,
                Key.fn(Key.TO_LETTERS, "أبج", 1.5f, "العودة إلى الحروف"),
                new Key(Key.CHAR, "،", "،", null, null, 1f, false, "فاصلة"),
                space("العربية", 3.5f),
                new Key(Key.CHAR, ".", ".", null, null, 1f, false, "نقطة"),
                enter(1f)));
    }

    // ---------------------------------------------------------------- Number / phone pad
    public static Layout numberPad(boolean phone) {
        Key a = phone ? Key.ch("+") : Key.ch("-");
        Key b = phone ? Key.ch("*") : Key.ch(",");
        Key c = phone ? Key.ch("#") : Key.ch(".");
        return new Layout(phone ? "phone" : "number", 4f, false, false,
            new Layout.Row(0f, H_ROW, Key.ch("1"), Key.ch("2"), Key.ch("3"), a),
            new Layout.Row(0f, H_ROW, Key.ch("4"), Key.ch("5"), Key.ch("6"), space("مسافة", 1f)),
            new Layout.Row(0f, H_ROW, Key.ch("7"), Key.ch("8"), Key.ch("9"), del(1f)),
            new Layout.Row(0f, H_ROW, b, Key.ch("0"), c, enter(1f)));
    }

    private static Key[] sym(String... s) {
        Key[] out = new Key[s.length];
        for (int i = 0; i < s.length; i++) out[i] = Key.ch(s[i]);
        return out;
    }

    private static Key[] concat(Key first, Key[] middle, Key last) {
        Key[] out = new Key[middle.length + 2];
        out[0] = first;
        System.arraycopy(middle, 0, out, 1, middle.length);
        out[out.length - 1] = last;
        return out;
    }

    /** Output text for a long-press option; diacritics drawn on a tatweel insert the mark alone. */
    public static String popupOutput(String item) {
        if (item != null && item.length() == 2 && item.charAt(0) == 'ـ') {
            char m = item.charAt(1);
            if ((m >= 0x064B && m <= 0x0655) || m == 0x0670) return String.valueOf(m);
        }
        return item;
    }
}
