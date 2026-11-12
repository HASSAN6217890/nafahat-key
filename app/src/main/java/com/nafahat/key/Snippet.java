package com.nafahat.key;

/** One saved text in the library. Built-in Nafahat texts carry a builtinKey and are read-only. */
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

    public boolean isBuiltin() { return builtinKey != null; }

    public static Snippet builtin(String key, int collection, String title, String body, String type) {
        Snippet s = new Snippet();
        s.builtinKey = key;
        s.builtinCollection = collection;
        s.title = title;
        s.body = body;
        s.type = type;
        return s;
    }

    public boolean hasPlaceholders() { return body != null && body.matches("(?s).*\\{[^{}\\n]{1,20}\\}.*"); }
}
