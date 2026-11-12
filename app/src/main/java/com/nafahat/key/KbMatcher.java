package com.nafahat.key;

import java.util.ArrayList;
import java.util.List;

/**
 * Local keyword matching for the Nafahat library (v0.5). Pure Java, no Android imports.
 * Nothing here ever leaves the phone: typed words and clipboard text are only compared
 * against the keywords that were downloaded.
 */
public final class KbMatcher {
    private KbMatcher() { }

    /** Which variant suits the app being typed in. */
    public static String platformFor(String packageName) {
        if (packageName == null) return "any";
        switch (packageName) {
            case "com.whatsapp":
            case "com.whatsapp.w4b":
                return "wa";
            case "com.instagram.android":
                return "ig";
            case "com.facebook.katana":
            case "com.facebook.orca":
                return "fb";
            default:
                return "any";
        }
    }

    /** Typed-word score for one snippet: 0 none, 2 prefix of a keyword, 3 exact keyword. */
    static int wordScore(Snippet s, String typedNorm) {
        int best = 0;
        for (String k : s.keywords) {
            if (k.isEmpty()) continue;
            if (k.equals(typedNorm)) return 3;
            if (k.startsWith(typedNorm)) best = 2;
        }
        return best;
    }

    /**
     * The best snippet for the word being typed (at least 2 letters, prefix of a keyword).
     * Order: exact keyword before prefix, current trip first, then «معلوماتنا», then library order.
     */
    public static Snippet best(List<Snippet> all, String typed) {
        String t = TextNorm.normalize(typed == null ? "" : typed.trim());
        if (t.length() < 2 || all == null) return null;
        Snippet best = null;
        int bestScore = -1;
        for (Snippet s : all) {
            if (s.keywords.isEmpty()) continue;
            int w = wordScore(s, t);
            if (w == 0) continue;
            int score = w * 100 + (s.tripCurrent ? 20 : 0) + ("info".equals(s.tripRemote) ? 10 : 0);
            if (score > bestScore) { best = s; bestScore = score; }
        }
        return best;
    }

    /** Words of a message, normalised, without punctuation. */
    static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder w = new StringBuilder();
        String n = TextNorm.normalize(text == null ? "" : text);
        for (int i = 0; i <= n.length(); i++) {
            char c = i < n.length() ? n.charAt(i) : ' ';
            if (Character.isLetterOrDigit(c)) w.append(c);
            else if (w.length() > 0) { out.add(w.toString()); w.setLength(0); }
        }
        return out;
    }

    /** How strongly a customer's message asks for this snippet. */
    static int messageScore(Snippet s, List<String> words, String joined) {
        int score = 0;
        for (String k : s.keywords) {
            if (k.isEmpty()) continue;
            if (k.indexOf(' ') >= 0) {                 // a phrase such as «كم يوم»
                if (joined.contains(" " + k + " ")) score += 4;   // a phrase is more specific than one word
                continue;
            }
            for (String w : words) {
                if (w.equals(k)) { score += 3; break; }
                // «عنوانكم» ← «عنوان», «والسعر» ← «سعر»; short keywords must match exactly
                if (k.length() >= 3 && (w.startsWith(k) || (w.endsWith(k) && w.length() <= k.length() + 3))) { score += 2; break; }
            }
        }
        return score;
    }

    /** Top replies for a customer's message (the clipboard, read only on the user's tap). */
    public static List<Snippet> replies(List<Snippet> all, String message, int max) {
        List<Snippet> out = new ArrayList<>();
        if (all == null || message == null) return out;
        List<String> words = words(message);
        if (words.isEmpty()) return out;
        String joined = " " + String.join(" ", words) + " ";
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Snippet s = all.get(i);
            int m = messageScore(s, words, joined);
            if (m == 0) continue;
            int score = m * 100 + (s.tripCurrent ? 20 : 0) + ("info".equals(s.tripRemote) ? 10 : 0);
            scored.add(new int[]{score, i});
        }
        scored.sort((a, b) -> a[0] != b[0] ? Integer.compare(b[0], a[0]) : Integer.compare(a[1], b[1]));
        for (int[] p : scored) {
            if (out.size() >= max) break;
            out.add(all.get(p[1]));
        }
        return out;
    }
}
