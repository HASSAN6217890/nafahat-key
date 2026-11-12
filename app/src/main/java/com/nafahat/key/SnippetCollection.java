package com.nafahat.key;

/** A library group such as «رحلة 7.1». builtin: 0 user, 1 «معلومات نفحات», 2 «ردود جاهزة». */
public final class SnippetCollection {
    public long id;
    public String name = "";
    public int sort;
    public boolean archived;
    public int builtin;

    public boolean isBuiltin() { return builtin != 0; }
}
