package com.nafahat.key;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Immutable data-driven physical key rows. All digits are ASCII Western numerals. */
public final class KeyboardLayout {
    public static final class Key {
        public final String code, label, secondary;
        public final float weight;
        public final List<String> alternatives;
        public final boolean function;
        public Key(String code, String label, String secondary, float weight, boolean function,
                   String... alternatives) {
            this.code=code; this.label=label; this.secondary=secondary;
            this.weight=weight; this.function=function;
            this.alternatives=Collections.unmodifiableList(Arrays.asList(alternatives));
        }
        public List<String> longOptions() {
            ArrayList<String> out=new ArrayList<>();
            if(!secondary.isEmpty()) out.add(secondary);
            for(String s:alternatives) if(!out.contains(s)) out.add(s);
            return out;
        }
    }
    public static final class Row {
        public final List<Key> keys;
        public final float height;
        public final float inset;
        Row(float height,float inset,Key... keys) {
            this.height=height;this.inset=inset;this.keys=Arrays.asList(keys);
        }
    }
    private static Key k(String label,String secondary,String... alternatives) {
        return new Key(label,label,secondary,1f,false,alternatives);
    }
    private static Key f(String code,String text,float width) {
        return new Key(code,text,"",width,true);
    }
    public static List<Row> make(boolean arabic,String mode,boolean upper,boolean numberRow) {
        ArrayList<Row> rows=new ArrayList<>();
        if("number".equals(mode)) {
            rows.add(new Row(56,0,k("1",""),k("2",""),k("3","")));
            rows.add(new Row(56,0,k("4",""),k("5",""),k("6","")));
            rows.add(new Row(56,0,k("7",""),k("8",""),k("9","")));
            rows.add(new Row(56,0,k("*",""),k("0",""),k("#",""),f("⌫","⌫",1.5f)));
            rows.add(new Row(56,0,f("ABC","حروف",1.4f),k("+",""),f("↵","↵",1.5f)));
            return rows;
        }
        if("diacritics".equals(mode)) {
            rows.add(new Row(56,0,k("َ",""),k("ً",""),k("ُ",""),k("ٌ","")));
            rows.add(new Row(56,0,k("ِ",""),k("ٍ",""),k("ّ",""),k("ْ","")));
            rows.add(new Row(56,0,k("أ",""),k("إ",""),k("آ",""),k("ـ","")));
            rows.add(bottom(arabic,"حروف")); return rows;
        }
        if("symbols1".equals(mode) || "symbols2".equals(mode)) {
            boolean first="symbols1".equals(mode);
            if(first) {
                rows.add(digits());
                rows.add(new Row(56,0,k("@",""),k("#",""),k("$",""),k("%",""),k("&",""),k("*",""),k("(",""),k(")",""),k("-",""),k("+","")));
                rows.add(new Row(56,0,k("!",""),k("?",""),k("/",""),k(":",""),k(";",""),k("'",""),k("\"",""),k("=",""),k("_",""),f("⌫","⌫",1.5f)));
            } else {
                rows.add(new Row(56,0,k("[",""),k("]",""),k("{",""),k("}",""),k("<",""),k(">",""),k("€",""),k("£",""),k("₺","")));
                rows.add(new Row(56,0,k("~",""),k("^",""),k("|",""),k("\\",""),k("…",""),k("،",""),k("؛",""),k("؟",""),f("⌫","⌫",1.5f)));
                rows.add(new Row(56,0,k(".",""),k(",",""),k("0",""),k("1",""),k("2",""),k("3",""),k("4",""),k("5",""),k("6","")));
            }
            rows.add(new Row(56,0,f("ABC","حروف",1.6f),f(first?"=\\<":"?123",first?"=\\<":"?123",1.3f),
                f("،","،",1f),f("space",arabic?"العربية":"English",4f),f(".",".",1f),f("↵","↵",1.5f)));
            return rows;
        }
        if(numberRow) rows.add(digits());
        if(arabic) {
            rows.add(new Row(56,0,
                k("ض","1"),k("ص","2"),k("ث","3"),k("ق","4"),k("ف","5"),k("غ","6"),
                k("ع","7"),k("ه","8","ة"),k("خ","9"),k("ح","0"),k("ج","چ"),k("د","الله")));
            rows.add(new Row(56,.5f,
                k("ش","@"),k("س","#"),k("ي","&","ئ","ى"),k("ب","پ"),
                k("ل","لا","لأ","لإ","لآ"),k("ا","أ","إ","آ","ٱ"),
                k("ت","="),k("ن","("),k("م",")"),k("ك","گ"),k("ذ","*")));
            rows.add(new Row(56,0,
                k("ئ","_"),k("ء","~"),k("ؤ","-"),k("ر","+"),k("ى",":"),
                k("ة","؛"),k("و","/","ؤ"),k("ز","ژ"),k("ط","\""),k("ظ","ِ"),
                f("⌫","⌫",1.5f)));
        } else {
            String[] top="qwertyuiop".split("");
            String[] middle="asdfghjkl".split("");
            String[] low="zxcvbnm".split("");
            Key[] row1=new Key[10],row2=new Key[9],row3=new Key[9];
            for(int i=0;i<10;i++) row1[i]=k(upper?top[i].toUpperCase():top[i],String.valueOf((i+1)%10));
            String[] common={"@","#","$","%","&","*","-","+","="};
            for(int i=0;i<9;i++)row2[i]=k(upper?middle[i].toUpperCase():middle[i],common[i]);
            row3[0]=f("⇧",upper?"⇧●":"⇧",1.5f);
            for(int i=0;i<7;i++)row3[i+1]=k(upper?low[i].toUpperCase():low[i],"");
            row3[8]=f("⌫","⌫",1.5f);
            rows.add(new Row(56,0,row1));rows.add(new Row(56,.5f,row2));rows.add(new Row(56,0,row3));
        }
        rows.add(bottom(arabic,"123"));
        return rows;
    }
    private static Row digits() {
        Key[] keys=new Key[10];
        for(int i=0;i<10;i++) keys[i]=k(String.valueOf((i+1)%10),"");
        return new Row(46,0,keys);
    }
    private static Row bottom(boolean arabic,String start) {
        return new Row(56,0,f(start,start,1.4f),f("lang",arabic?"EN":"عربي",1.2f),
            f("،","،",.85f),f("space",arabic?"العربية ‹ ›":"English ‹ ›",4.2f),
            f(".",".",.85f),f("↵","↵",1.5f));
    }
    private KeyboardLayout(){}
}
