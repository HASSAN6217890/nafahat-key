package com.nafahat.key;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Versioned local snippets library with non-destructive v0.2 migration. */
public final class LibraryDb extends SQLiteOpenHelper {
    private static final String NAME="nafahat_library_v03.db";
    private final Context context;
    public LibraryDb(Context context){
        super(context.getApplicationContext(),NAME,null,1);
        this.context=context.getApplicationContext();
        migrateOldPrefs();
    }
    @Override public void onConfigure(SQLiteDatabase db){
        super.onConfigure(db);db.setForeignKeyConstraintsEnabled(true);
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE collections(id INTEGER PRIMARY KEY, name TEXT UNIQUE NOT NULL, sort INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, builtin INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE snippets(id TEXT PRIMARY KEY, collection_id INTEGER NOT NULL REFERENCES collections(id), title TEXT NOT NULL, body TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'رد', sort INTEGER NOT NULL DEFAULT 0, shortcut TEXT NOT NULL DEFAULT '', pinned INTEGER NOT NULL DEFAULT 0, last_used INTEGER NOT NULL DEFAULT 0, use_count INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX snippets_col ON snippets(collection_id,sort)");
        db.execSQL("INSERT INTO collections(name,sort,builtin) VALUES('معلومات نفحات',0,1)");
        db.execSQL("INSERT INTO collections(name,sort,builtin) VALUES('ردود جاهزة',1,1)");
        for(Snippet s:BrandData.publicSnippets())insertSnippet(db,s.id,s.title,s.text,s.category,"رد",false);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        // Database upgrades must be strictly additive: NEVER DROP TABLE or overwrite user text.
    }
    private long collectionId(SQLiteDatabase db,String name,boolean create){
        String cleaned=name==null||name.trim().isEmpty()?"شخصية":name.trim();
        try(Cursor cur=db.rawQuery("SELECT id FROM collections WHERE name=?",new String[]{cleaned})){
            if(cur.moveToFirst())return cur.getLong(0);
        }
        if(!create)return -1;
        ContentValues v=new ContentValues();v.put("name",cleaned);
        return db.insertOrThrow("collections",null,v);
    }
    private void insertSnippet(SQLiteDatabase db,String id,String title,String body,String group,String type,boolean pinned){
        long col=collectionId(db,group,true);
        ContentValues v=new ContentValues();
        v.put("id",id);v.put("collection_id",col);v.put("title",title);v.put("body",body);
        v.put("type",type);v.put("pinned",pinned?1:0);
        v.put("updated_at",System.currentTimeMillis());
        db.insertOrThrow("snippets",null,v);
    }
    private void migrateOldPrefs(){
        SharedPreferences prefs=context.getSharedPreferences("nafahatkey_personal_v1",Context.MODE_PRIVATE);
        if(prefs.getBoolean("__migrated_v03",false))return;
        String raw=prefs.getString("items","[]");
        JSONArray array;
        try{array=new JSONArray(raw);}catch(JSONException e){return;} // Retain originals unchanged on failed read.
        SQLiteDatabase db;
        try{db=getWritableDatabase();backupBeforeWrite();}
        catch(Exception ex){return;}
        db.beginTransaction();
        try {
            for(int i=0;i<array.length();i++){
                JSONObject entry=array.getJSONObject(i);
                String id=entry.optString("id",UUID.randomUUID().toString());
                if(id.trim().isEmpty())id=UUID.randomUUID().toString();
                String title=entry.getString("title"),body=entry.getString("text");
                String group=entry.optString("category","شخصية");
                if(title.trim().isEmpty()||body.trim().isEmpty())throw new JSONException("Empty legacy record");
                try(Cursor cur=db.rawQuery("SELECT id FROM snippets WHERE id=?",new String[]{id})){
                    if(!cur.moveToFirst())insertSnippet(db,id,title,body,group,"رد",false);
                }
            }
            db.setTransactionSuccessful();
        }catch(Exception ex){return;}
        finally{db.endTransaction();}
        // Commit marker only after the SQLite transaction succeeded.
        prefs.edit().putBoolean("__migrated_v03",true).commit();
        // Deliberately retain legacy SharedPreferences as a recovery source.
    }
    public List<Snippet> getAll(){
        ArrayList<Snippet> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT s.id,s.title,s.body,c.name,c.builtin,s.type,s.shortcut,s.pinned,s.last_used FROM snippets s JOIN collections c ON c.id=s.collection_id WHERE c.archived=0 ORDER BY s.pinned DESC,s.last_used DESC,c.sort,s.sort,s.title",null)){
            while(c.moveToNext())out.add(new Snippet(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4)!=0,c.getString(5),c.getString(6),c.getInt(7)!=0,c.getLong(8)));
        }
        return out;
    }
    public List<Snippet> getPersonal(){
        ArrayList<Snippet> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT s.id,s.title,s.body,c.name FROM snippets s JOIN collections c ON c.id=s.collection_id WHERE c.builtin=0 ORDER BY c.sort,s.sort,s.title",null)){
            while(c.moveToNext())out.add(new Snippet(c.getString(0),c.getString(1),c.getString(2),c.getString(3),false));
        }
        return out;
    }
    /** A durable JSON copy is flushed before any modifying transaction. */
    private void backupBeforeWrite() throws Exception {
        JSONObject snapshot=exportJson();
        File destination=new File(context.getFilesDir(),"nafahat-library-backup.json");
        File tmp=new File(context.getFilesDir(),"nafahat-library-backup.json.tmp");
        try(FileOutputStream stream=new FileOutputStream(tmp)){
            stream.write(snapshot.toString().getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        }
        if(!tmp.renameTo(destination))throw new java.io.IOException("Cannot preserve library snapshot");
    }
    public void add(String title,String body,String collection){add(title,body,collection,"رد");}
    public void add(String title,String body,String collection,String type){
        if(title==null||title.trim().isEmpty()||body==null||body.trim().isEmpty())return;
        try{backupBeforeWrite();}catch(Exception e){throw new IllegalStateException("تعذّر إنشاء النسخة الاحتياطية",e);}
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            insertSnippet(db,UUID.randomUUID().toString(),title.trim(),body.trim(),collection,type,false);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void delete(String id){
        try{backupBeforeWrite();}catch(Exception e){throw new IllegalStateException("تعذّر إنشاء النسخة الاحتياطية",e);}
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            db.delete("snippets","id=? AND collection_id IN (SELECT id FROM collections WHERE builtin=0)",new String[]{id});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void recordUse(String id){
        try{backupBeforeWrite();}catch(Exception e){return;}
        SQLiteDatabase db=getWritableDatabase();
        db.execSQL("UPDATE snippets SET last_used=?,use_count=use_count+1 WHERE id=?",
            new Object[]{System.currentTimeMillis(),id});
    }
    public void update(String id,String title,String body,String collection,String type){
        if(title==null||title.trim().isEmpty()||body==null||body.trim().isEmpty())
            throw new IllegalArgumentException("Required text");
        try{backupBeforeWrite();}catch(Exception e){throw new IllegalStateException(e);}
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            long col=collectionId(db,collection,true);
            ContentValues values=new ContentValues();
            values.put("collection_id",col);values.put("title",title.trim());
            values.put("body",body.trim());values.put("type",type);
            values.put("updated_at",System.currentTimeMillis());
            if(db.update("snippets",values,"id=? AND collection_id IN (SELECT id FROM collections WHERE builtin=0)",new String[]{id})!=1)
                throw new IllegalArgumentException("Unknown personal text");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void archiveCollection(String name,boolean archive){
        try{backupBeforeWrite();}catch(Exception e){throw new IllegalStateException(e);}
        getWritableDatabase().execSQL("UPDATE collections SET archived=? WHERE name=? AND builtin=0",
            new Object[]{archive?1:0,name});
    }
    public List<String> collectionNames(boolean archived){
        ArrayList<String> names=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
          "SELECT name FROM collections WHERE builtin=0 AND archived=? ORDER BY sort,name",
          new String[]{archived?"1":"0"})){
            while(c.moveToNext())names.add(c.getString(0));
        }
        return names;
    }
    public void togglePinned(String id){
        try{backupBeforeWrite();}catch(Exception e){return;}
        getWritableDatabase().execSQL("UPDATE snippets SET pinned=1-pinned WHERE id=?",new Object[]{id});
    }
    public JSONObject exportJson() throws JSONException {
        JSONObject root=new JSONObject();root.put("schema",1);
        JSONArray cols=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,sort,archived,builtin FROM collections",null)){
            while(c.moveToNext()){
                JSONObject o=new JSONObject();
                o.put("id",c.getLong(0));o.put("name",c.getString(1));o.put("sort",c.getInt(2));
                o.put("archived",c.getInt(3));o.put("builtin",c.getInt(4));cols.put(o);
            }
        }
        JSONArray texts=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT s.id,c.name,s.title,s.body,s.type,s.sort,s.shortcut,s.pinned,s.last_used,s.use_count,s.updated_at FROM snippets s JOIN collections c ON s.collection_id=c.id WHERE c.builtin=0",null)){
            while(c.moveToNext()){
                JSONObject o=new JSONObject();
                o.put("id",c.getString(0));o.put("collection",c.getString(1));o.put("title",c.getString(2));
                o.put("body",c.getString(3));o.put("type",c.getString(4));o.put("sort",c.getInt(5));
                o.put("shortcut",c.getString(6));o.put("pinned",c.getInt(7));o.put("last_used",c.getLong(8));
                o.put("use_count",c.getInt(9));o.put("updated_at",c.getLong(10));texts.put(o);
            }
        }
        root.put("collections",cols);root.put("snippets",texts);
        return root;
    }
    public void importJson(JSONObject data) throws Exception {
        if(data.getInt("schema")!=1)throw new JSONException("Unsupported schema");
        JSONArray cols=data.getJSONArray("collections"),texts=data.getJSONArray("snippets");
        if(cols.length()>300||texts.length()>10000)throw new JSONException("Oversized library");
        // Validate everything before touching current data.
        for(int i=0;i<cols.length();i++)cols.getJSONObject(i).getString("name");
        for(int i=0;i<texts.length();i++){
            JSONObject o=texts.getJSONObject(i);
            o.getString("title");o.getString("body");o.getString("collection");
        }
        backupBeforeWrite();
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            for(int i=0;i<cols.length();i++){
                JSONObject o=cols.getJSONObject(i);
                if(o.optInt("builtin")==0)collectionId(db,o.getString("name"),true);
            }
            for(int i=0;i<texts.length();i++){
                JSONObject o=texts.getJSONObject(i);
                String id=o.optString("id",UUID.randomUUID().toString());
                try(Cursor c=db.rawQuery("SELECT id FROM snippets WHERE id=?",new String[]{id})){
                    if(c.moveToFirst())id=UUID.randomUUID().toString();
                }
                insertSnippet(db,id,o.getString("title"),o.getString("body"),
                    o.getString("collection"),o.optString("type","رد"),o.optInt("pinned")==1);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
