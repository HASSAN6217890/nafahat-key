package com.nafahat.key;

import java.util.Locale;

/** Arabic / English text normalisation used only for comparing words. */
public final class TextNorm {
    private TextNorm() { }

    public static boolean isMark(char c) {
        return (c >= 0x064B && c <= 0x065F) || c == 0x0670 || c == 0x0640;
    }

    public static boolean isArabicLetter(char c) {
        return (c >= 0x0621 && c <= 0x064A) || c == 0x0671 || c == 0x067E || c == 0x0686
            || c == 0x0698 || c == 0x06A4 || c == 0x06AF || c == 0x06CC;
    }

    public static boolean isLatinLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= 0x00C0 && c <= 0x024F);
    }

    /** Characters that can be part of a composing word. */
    public static boolean isWordChar(char c) {
        return isArabicLetter(c) || isMark(c) || isLatinLetter(c) || Character.isLetter(c);
    }

    /** أ/إ/آ/ٱ→ا، ة→ه، ى→ي، remove tashkeel and tatweel, lower-case Latin. */
    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isMark(c)) continue;
            switch (c) {
                case 'أ': case 'إ': case 'آ': case 'ٱ': c = 'ا'; break;
                case 'ة': c = 'ه'; break;
                case 'ى': c = 'ي'; break;
                default:
                    if (c < 0x0600) c = Character.toLowerCase(c);
            }
            b.append(c);
        }
        return b.toString();
    }

    /** Remove tashkeel / tatweel only (keeps hamza forms) — used before learning words. */
    public static String stripMarks(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!isMark(c)) b.append(c);
        }
        return b.toString();
    }

    public static boolean hasDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) return true;
        }
        return false;
    }

    public static boolean isArabicWord(String s) {
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isMark(c)) continue;
            if (!isArabicLetter(c)) return false;
            any = true;
        }
        return any;
    }

    public static boolean isLatinWord(String s) {
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') continue;
            if (!isLatinLetter(c)) return false;
            any = true;
        }
        return any;
    }

    /** Copies the capitalisation of the typed word onto a suggestion (English only). */
    public static String matchCase(String typed, String word) {
        if (typed.isEmpty() || word.isEmpty()) return word;
        if (!isLatinLetter(typed.charAt(0))) return word;
        boolean allCaps = typed.length() > 1 && typed.equals(typed.toUpperCase(Locale.US));
        if (allCaps) return word.toUpperCase(Locale.US);
        if (Character.isUpperCase(typed.charAt(0)))
            return Character.toUpperCase(word.charAt(0)) + word.substring(1);
        return word;
    }
}
