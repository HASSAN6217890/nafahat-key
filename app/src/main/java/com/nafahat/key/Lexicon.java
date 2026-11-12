package com.nafahat.key;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** APK-bundled lexicon, plus frequently used phrases; no network requests. */
public final class Lexicon {
    private final Map<String,Integer> weights=new HashMap<>();
    private final Map<String,List<String>> prefixes=new HashMap<>();
    private final Map<String,List<String>> firstLetter=new HashMap<>();
    private final Map<String,List<String>> commonNext=new HashMap<>();
    private final LearningModel learned;
    public Lexicon(Context context,LearningModel learned){
        this.learned=learned;
        putSeeds("عمرة تأشيرة المدينة المنورة مكة المكرمة نسك إحرام طواف سعي زيارة الطيلوني اللاميرادا نفحات الحسام الروضة البقيع قباء السلام عليكم ورحمة الله وبركاته الرجاء يرجى شكرا شكرًا أهلاً وسهلاً موعد رحلتنا تفاصيل المكتب حجز حلب دمشق جدة السعر التواريخ المعلومات الفندق الباص المغادرة المعتمرين",110);
        putSeeds("شو هلأ منيح كتير بدي هيك ليش وين إمتى لسا يعني طيب والله إن شاء الله يعطيك العافية كيف فيك شلونك لساتك تمام بتقدر",95);
        putSeeds("hello welcome thanks thank you please booking trip airport hotel message good morning evening available information",95);
        load(context,"lexicon_ar.tsv");
        load(context,"lexicon_en.tsv");
        addNext("السلام","عليكم","عليكم","ورحمة");
        addNext("ورحمة","الله");addNext("إن","شاء");addNext("شاء","الله");
        addNext("مكة","المكرمة");addNext("المدينة","المنورة");addNext("شوف","كيف");
    }
    private void putSeeds(String list,int rank){
        for(String s:list.split("\\s+"))add(s,rank);
    }
    private void load(Context context,String name){
        try(InputStream file=context.getAssets().open(name);
            BufferedReader in=new BufferedReader(new InputStreamReader(file,StandardCharsets.UTF_8))){
            String line;
            while((line=in.readLine())!=null){
                String[] parts=line.split("\t",2);
                int score=parts.length>1?Integer.parseInt(parts[1]):45;
                add(parts[0],score);
            }
        }catch(Exception ignored){/* Development builds without generated assets use seed list. */}
    }
    private void add(String word,int value){
        if(word.isEmpty()||word.length()>40)return;
        String lower=word.toLowerCase(Locale.ROOT);
        if(value<=weights.getOrDefault(lower,-1))return;
        weights.put(lower,value);
        String normalized=normalize(lower);
        if(normalized.isEmpty())return;
        firstLetter.computeIfAbsent(normalized.substring(0,1),k->new ArrayList<>()).add(lower);
        for(int n=1;n<=Math.min(6,normalized.length());n++){
            String part=normalized.substring(0,n);
            List<String> best=prefixes.computeIfAbsent(part,k->new ArrayList<>());
            if(!best.contains(lower))best.add(lower);
            best.sort((a,b)->Integer.compare(weights.getOrDefault(b,0),weights.getOrDefault(a,0)));
            while(best.size()>6)best.remove(best.size()-1);
        }
    }
    private void addNext(String previous,String... words){commonNext.put(previous.toLowerCase(Locale.ROOT),java.util.Arrays.asList(words));}
    public static String normalize(String src){
        String s=Normalizer.normalize(src.toLowerCase(Locale.ROOT),Normalizer.Form.NFKC)
            .replace("ـ","").replace('أ','ا').replace('إ','ا').replace('آ','ا')
            .replace('ٱ','ا').replace('ة','ه').replace('ى','ي');
        return s.replaceAll("[\\u064B-\\u065F\\u0670]","");
    }
    public boolean known(String word){return weights.containsKey(word.toLowerCase(Locale.ROOT));}
    public List<String> complete(String typed,int count){
        String norm=normalize(typed);if(norm.isEmpty())return Collections.emptyList();
        String prefix=norm.substring(0,Math.min(norm.length(),6));
        LinkedHashMap<String,Boolean> out=new LinkedHashMap<>();
        for(String v:learned.suggestPrefix(typed.toLowerCase(Locale.ROOT),count))out.put(v,true);
        for(String v:prefixes.getOrDefault(prefix,Collections.emptyList()))if(normalize(v).startsWith(norm))out.put(v,true);
        ArrayList<String> list=new ArrayList<>(out.keySet());
        return list.subList(0,Math.min(list.size(),count));
    }
    public List<String> predict(String previous,int count){
        LinkedHashMap<String,Boolean> result=new LinkedHashMap<>();
        for(String v:learned.nextWords(previous.toLowerCase(Locale.ROOT),count))result.put(v,true);
        for(String v:commonNext.getOrDefault(previous.toLowerCase(Locale.ROOT),Collections.emptyList()))result.put(v,true);
        for(String v:java.util.Arrays.asList("السلام","أهلاً","شكراً"))result.put(v,true);
        ArrayList<String> out=new ArrayList<>(result.keySet());
        return out.subList(0,Math.min(count,out.size()));
    }
    /** Deliberately conservative: no changes to short words or mixed digit/URL/e-mail text. */
    public String corrected(String word){
        if(word==null||word.length()<4||word.matches(".*[0-9@:/\\\\.].*")||
            word.matches(".*[A-Za-z].*[\\u0600-\\u06FF].*")||known(word))return word;
        String target=normalize(word);
        if(target.length()<4)return word;
        List<String> pool=firstLetter.getOrDefault(target.substring(0,1),Collections.emptyList());
        String candidate=word;int best=0;
        for(String other:pool){
            if(other.equalsIgnoreCase(word))return word;
            String cmp=normalize(other);
            if(Math.abs(cmp.length()-target.length())>1)continue;
            if(distanceAtMostOne(cmp,target)){
                int score=weights.getOrDefault(other,0);
                if(score>best){best=score;candidate=other;}
            }
        }
        // A high corpus frequency is required: avoid speculative edits of proper names.
        return best>=76?candidate:word;
    }
    private static boolean distanceAtMostOne(String a,String b){
        if(a.equals(b))return true;
        int i=0,j=0,edits=0;
        while(i<a.length()&&j<b.length()){
            if(a.charAt(i)==b.charAt(j)){i++;j++;continue;}
            if(++edits>1)return false;
            if(a.length()>b.length())i++;
            else if(b.length()>a.length())j++;
            else{i++;j++;}
        }
        return edits+(a.length()-i)+(b.length()-j)<=1;
    }
}
