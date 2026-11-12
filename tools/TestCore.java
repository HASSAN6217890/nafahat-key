package com.nafahat.key;

import java.util.List;
import java.util.Map;
public final class TestCore {
    private static void check(boolean value,String name){
        if(!value)throw new AssertionError(name);
    }
    private static String labels(KeyboardLayout.Row row){
        StringBuilder out=new StringBuilder();
        for(KeyboardLayout.Key k:row.keys)out.append(k.label).append(' ');
        return out.toString().trim();
    }
    public static void main(String[] args){
        List<KeyboardLayout.Row> ar=KeyboardLayout.make(true,"letters",false,true);
        check(labels(ar.get(0)).equals("1 2 3 4 5 6 7 8 9 0"),"western number row");
        check(labels(ar.get(1)).equals("ض ص ث ق ف غ ع ه خ ح ج د"),"Arabic first row");
        check(labels(ar.get(2)).equals("ش س ي ب ل ا ت ن م ك ذ"),"Arabic second row");
        check(labels(ar.get(3)).equals("ئ ء ؤ ر ى ة و ز ط ظ ⌫"),"Arabic third row");
        check(ar.get(3).keys.get(10).weight==1.5f,"backspace width");
        check(ar.get(2).keys.get(5).longOptions().get(0).equals("أ"),"alef variants");
        check(ar.get(1).keys.get(0).longOptions().get(0).equals("1"),"western secondary");
        List<KeyboardLayout.Row> en=KeyboardLayout.make(false,"letters",true,false);
        check(labels(en.get(0)).equals("Q W E R T Y U I O P"),"English shift visual");
        check(en.get(2).keys.get(0).code.equals("⇧"),"English shift key");
        List<KeyboardLayout.Row> symbols=KeyboardLayout.make(true,"symbols2",false,false);
        check(labels(symbols.get(0)).contains("€"),"second symbol page");
        Map<String,StringBuilder> messages=WhatsAppParser.bySender(
          "12/11/2026, 18:51 - حسن: مرحبا\n"+
          "12/11/2026, 18:52 - زيد: أهلاً\n"+
          "12/11/2026, 18:53 - حسن: شكراً\n");
        check(messages.size()==2,"Android sender count");
        check(messages.get("حسن").toString().contains("شكراً"),"sender-only selection");
        check(!messages.get("حسن").toString().contains("أهلاً"),"prevent other speaker learning");
        Map<String,StringBuilder> ios=WhatsAppParser.bySender(
          "[12/11/26, 18:51:22] Hanna: Hello\n[12/11/26, 18:52:22] Alex: Welcome\n");
        check(ios.size()==2&&ios.containsKey("Hanna"),"iOS export format");
        System.out.println("Pure Java layout/parser unit tests passed.");
    }
}
