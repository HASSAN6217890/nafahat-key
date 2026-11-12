package com.nafahat.key;

import java.util.ArrayList;
import java.util.List;

/**
 * One saved text in the library. Built-in Nafahat texts carry a builtinKey and are read-only;
 * texts from the Nafahat library server (origin "server") are read-only too («من الإدارة»).
 * Pure Java (no Android imports) so the matcher can be tested on a plain JVM.
 */
public final class Snippet {
    public long id;
    public long collectionId;
    public String title = "";
    public String body = "";
    public String type = "أخرى";
    public int sort;
    public String shortcut = "";
    public boolean pinned;
    public long lastUsed;
    public int useCount;
    public long updatedAt;
    public String builtinKey;
    /** Only used by BrandData: 1 = «معلومات نفحات», 2 = «ردود جاهزة». */
    public int builtinCollection;

    // v0.5 central library
    public String origin = "local";
    public String remoteId;
    public String tripRemote;
    public boolean tripCurrent;
    public String level = "public";
    /** Small hint for the staff member shown under the text (never inserted). */
    public String note = "";
    /** Normalised keywords (TextNorm.normalize). */
    public List<String> keywords = new ArrayList<>();
    public List<Variant> variants = new ArrayList<>();

    /** One platform / length version of a server text. */
    public static final class Variant {
        public final String platform;   // wa | ig | fb | any
        public final String length;     // short | long
        public final String body;
        public Variant(String platform, String length, String body) {
            this.platform = platform;
            this.length = length;
            this.body = body;
        }
    }

    public boolean isBuiltin() { return builtinKey != null; }
    public boolean isServer() { return "server".equals(origin); }
    /** Built-in or from the Nafahat administration: cannot be edited or deleted on the phone. */
    public boolean isReadOnly() { return isBuiltin() || isServer(); }

    public static Snippet builtin(String key, int collection, String title, String body, String type) {
        Snippet s = new Snippet();
        s.builtinKey = key;
        s.builtinCollection = collection;
        s.title = title;
        s.body = body;
        s.type = type;
        return s;
    }

    public boolean hasPlaceholders() { return hasPlaceholders(body); }

    public static boolean hasPlaceholders(String text) {
        return text != null && text.matches("(?s).*\\{[^{}\\n]{1,20}\\}.*");
    }

    public boolean hasLong() {
        for (Variant v : variants) if ("long".equals(v.length)) return true;
        return false;
    }

    /**
     * The text for an app: exact platform + length, then the platform's other length,
     * then «any» with the length, then «any», then the first variant, then the stored body.
     */
    public String bodyFor(String platform, boolean longText) {
        if (variants.isEmpty()) return body;
        String len = longText ? "long" : "short";
        String other = longText ? "short" : "long";
        String[][] order = {{platform, len}, {"any", len}, {platform, other}, {"any", other}};
        for (String[] o : order) {
            for (Variant v : variants) if (v.platform.equals(o[0]) && v.length.equals(o[1])) return v.body;
        }
        return variants.get(0).body;
    }

    /** A copy carrying another body (the chosen variant), for inserting / filling templates. */
    public Snippet withBody(String text) {
        Snippet s = new Snippet();
        s.id = id;
        s.collectionId = collectionId;
        s.title = title;
        s.body = text;
        s.type = type;
        s.origin = origin;
        s.remoteId = remoteId;
        s.builtinKey = builtinKey;
        s.note = note;
        return s;
    }
}
