package com.nafahat.key;

import java.util.List;

/**
 * Arabic auto-replace policy (pure logic, no Android types, so CI tests the real code).
 * Conservative by design: Shami speech is full of words a standard dictionary does not know,
 * so a word is replaced only for a pure spelling-form difference (أ/إ/آ/ا، ة/ه، ى/ي) or a single
 * neighbouring-key slip towards a common word. Anything else is only suggested in the strip.
 */
public final class AutoCorrect {
    private AutoCorrect() { }

    public interface UserWords { boolean known(String word); }

    /** Minimum frequency (zipf) of a word we are allowed to replace with. */
    public static final float MIN_TARGET_ZIPF = 4.0f;
    /** One neighbouring-key substitution costs 0.6 in Proximity.distance. */
    public static final float MAX_COST = 0.6f;

    // Normalised clitics (ة→ه, ى→ي already applied by TextNorm.normalize).
    static final String[] PREFIXES = {"وال", "فال", "بال", "عال", "لل", "ال", "بت", "بي", "بن", "من", "عم", "رح",
        "و", "ف", "ب", "ل", "ع"};
    static final String[] SUFFIXES = {"تكن", "تكم", "تنا", "تها", "تهم", "كن", "كم", "كو", "نا", "ها", "هم", "هن",
        "ني", "لي", "لك", "لو", "ين", "ون", "ات", "وا", "تي", "تك", "ته", "تو", "ه", "ك", "ي", "و", "ت"};

    /** Replacement for an Arabic word, or null to keep what the user typed. */
    public static String arabic(Dictionary dict, String typed, UserWords user) {
        if (dict == null || typed == null) return null;
        String norm = TextNorm.normalize(typed);
        if (norm.length() < 2) return null;
        if (dict.exactIndex(typed) >= 0) return null;
        if (user != null && user.known(typed)) return null;
        int variant = dict.findNorm(norm);
        if (variant >= 0) {
            // Same letters, different hamza / ta marbuta / alef maqsura form.
            return dict.zipf(variant) >= MIN_TARGET_ZIPF && !dict.word(variant).equals(typed) ? dict.word(variant) : null;
        }
        if (cliticKnown(dict, norm)) return null;
        if (norm.length() < 3) return null;
        List<Dictionary.Candidate> c = dict.correct(norm, MAX_COST, 4);
        Dictionary.Candidate best = null;
        for (Dictionary.Candidate x : c) {
            if (x.cost <= MAX_COST && x.score + 2.2f * x.cost >= MIN_TARGET_ZIPF) {
                if (best == null) best = x;
                else if (best.score - x.score < 0.4f) return null; // two plausible words: only suggest
            }
        }
        return best == null ? null : best.word;
    }

    /** True when the word is a known stem with common Shami / Arabic prefixes or suffixes around it. */
    public static boolean cliticKnown(Dictionary dict, String norm) {
        for (int pi = -1; pi < PREFIXES.length; pi++) {
            String p = pi < 0 ? "" : PREFIXES[pi];
            if (!norm.startsWith(p)) continue;
            String rest = norm.substring(p.length());
            for (int si = -1; si < SUFFIXES.length; si++) {
                String s = si < 0 ? "" : SUFFIXES[si];
                if (p.isEmpty() && s.isEmpty()) continue;
                if (!rest.endsWith(s) || rest.length() - s.length() < 2) continue;
                String stem = rest.substring(0, rest.length() - s.length());
                if (stemOk(dict, stem)) return true;
                if (!s.isEmpty() && stem.endsWith("ت") && stemOk(dict, stem.substring(0, stem.length() - 1) + "ه"))
                    return true;
            }
        }
        return false;
    }

    private static boolean stemOk(Dictionary dict, String stem) {
        if (stem.length() < 2) return false;
        int idx = dict.findNorm(stem);
        return idx >= 0 && dict.zipf(idx) >= (stem.length() == 2 ? 4.5f : 3.0f);
    }
}
