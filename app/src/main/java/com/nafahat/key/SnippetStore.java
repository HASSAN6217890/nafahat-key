package com.nafahat.key;

import android.content.Context;
import java.util.List;

/** Backward-compatible facade: old v0.2 callers now use versioned SQLite. */
public final class SnippetStore {
    private final LibraryDb db;
    public SnippetStore(Context context){db=new LibraryDb(context);}
    public List<Snippet> getPersonal(){return db.getPersonal();}
    public void add(String title,String body,String category){db.add(title,body,category);}
    public void delete(String id){db.delete(id);}
    public List<Snippet> getAll(){return db.getAll();}
    public LibraryDb database(){return db;}
}
