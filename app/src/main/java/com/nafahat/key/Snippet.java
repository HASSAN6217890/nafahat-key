package com.nafahat.key;

public final class Snippet {
    public final String id;
    public final String title;
    public final String text;
    public final String category;
    public final boolean official;

    public Snippet(String id, String title, String text, String category, boolean official) {
        this.id = id;
        this.title = title;
        this.text = text;
        this.category = category;
        this.official = official;
    }
    public static Snippet official(String title, String text, String category) {
        return new Snippet("public:" + category + ":" + title, title, text, category, true);
    }
}
