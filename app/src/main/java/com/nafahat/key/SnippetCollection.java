package com.nafahat.key;

/**
 * A library group such as «رحلة 7.1». builtin: 0 user, 1 «معلومات نفحات», 2 «ردود جاهزة».
 * origin "server" = a trip tab from the Nafahat library (read-only on the phone).
 */
public final class SnippetCollection {
    public long id;
    public String name = "";
    public int sort;
    public boolean archived;
    public int builtin;
    public String origin = "local";
    public String remoteId;
    public boolean current;

    public boolean isBuiltin() { return builtin != 0; }
    public boolean isServer() { return "server".equals(origin); }
    public boolean isReadOnly() { return isBuiltin() || isServer(); }
}
