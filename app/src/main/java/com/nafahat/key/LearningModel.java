package com.nafahat.key;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local aggregate word and bigram counts; no raw conversation or keystroke log. */
public final class LearningModel extends SQLiteOpenHelper {
    private static final Pattern TOKEN=Pattern.compile("[\\p{L}]{2,40}");
    public LearningModel(Context context){super(context,"nafahat_learning_v03.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE words(word TEXT PRIMARY KEY, frequency INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE pairs(previous TEXT NOT NULL, next TEXT NOT NULL, frequency INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(previous,next))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){/* Keep data until explicit migration is designed. */}
    public void observe(String previous,String word){
        if(word==null||word.length()<2||word.length()>40)return;
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            increment(db,"words",word,null);
            if(previous!=null&&!previous.isEmpty())increment(db,"pairs",word,previous);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private void increment(SQLiteDatabase db,String table,String word,String before){
        if(table.equals("words")){
            db.execSQL("INSERT OR IGNORE INTO words(word,frequency) VALUES(?,0)",new Object[]{word});
            db.execSQL("UPDATE words SET frequency=frequency+1 WHERE word=?",new Object[]{word});
        }else{
            db.execSQL("INSERT OR IGNORE INTO pairs(previous,next,frequency) VALUES(?,?,0)",new Object[]{before,word});
            db.execSQL("UPDATE pairs SET frequency=frequency+1 WHERE previous=? AND next=?",new Object[]{before,word});
        }
    }
    public int learnText(String text){
        if(text==null||text.isEmpty())return 0;
        Matcher matcher=TOKEN.matcher(text);
        String previous="";
        HashSet<String> seen=new HashSet<>();
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            int limit=0;
            while(matcher.find()&&limit++<150000){
                String word=matcher.group().toLowerCase(Locale.ROOT);
                seen.add(word);
                increment(db,"words",word,null);
                if(!previous.isEmpty())increment(db,"pairs",word,previous);
                previous=word;
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return seen.size();
    }
    public List<String> suggestPrefix(String prefix,int limit){
        ArrayList<String> out=new ArrayList<>();
        if(prefix==null||prefix.isEmpty())return out;
        String escaped=prefix.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
        try(Cursor cur=getReadableDatabase().rawQuery("SELECT word FROM words WHERE word LIKE ? ESCAPE '\\' ORDER BY frequency DESC LIMIT ?",
                new String[]{escaped+"%",String.valueOf(limit)})){
            while(cur.moveToNext())out.add(cur.getString(0));
        }
        return out;
    }
    public List<String> nextWords(String previous,int limit){
        ArrayList<String> out=new ArrayList<>();
        if(previous==null||previous.isEmpty())return out;
        try(Cursor cur=getReadableDatabase().rawQuery(
            "SELECT next FROM pairs WHERE previous=? ORDER BY frequency DESC LIMIT ?",
            new String[]{previous,String.valueOf(limit)})){
            while(cur.moveToNext())out.add(cur.getString(0));
        }
        return out;
    }
    public void clearLearning(){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{db.delete("pairs",null,null);db.delete("words",null,null);db.setTransactionSuccessful();}
        finally{db.endTransaction();}
    }
}
