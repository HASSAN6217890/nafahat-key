package com.nafahat.key;

public final class Snippet {
    public final String id;
    public final String title;
    public final String text;
    public final String category;
    public final boolean official;
    public final String type,shortcut;
    public final boolean pinned;
    public final long lastUsed;

    public Snippet(String id, String title, String text, String category, boolean official) {
        this.id = id;
        this.title = title;
        this.text = text;
        this.category = category;
        this.official = official;
        this.type="رد";this.shortcut="";this.pinned=false;this.lastUsed=0;
    }
    public Snippet(String id,String title,String body,String category,boolean official,
                   String type,String shortcut,boolean pinned,long lastUsed) {
        this.id=id;this.title=title;this.text=body;this.category=category;this.official=official;
        this.type=type;this.shortcut=shortcut;this.pinned=pinned;this.lastUsed=lastUsed;
    }
    public static Snippet official(String title, String text, String category) {
        return new Snippet("public:" + category + ":" + title, title, text, category, true);
    }
}
