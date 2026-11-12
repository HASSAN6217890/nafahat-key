package com.nafahat.key;

import java.util.ArrayList;
import java.util.List;

/** Only user-approved public details. No internal costs or prices. */
public final class BrandData {
    private BrandData(){}
    public static final String NAME="نفحات للحج والعمرة";
    public static final String ADDRESS="دمشق – شارع الحمرا – اللاميرادا مول – فوق عصير أبو الشكر – بناء الطيلوني – الطابق الثالث";
    public static final String PHONE="\u200E+963 994 791 692";
    public static final String FACEBOOK="https://www.facebook.com/NafahatSY";
    public static final String WEBSITE="https://nafahatsy.com/";
    public static final String EMAIL="info@nafahatsy.com";
    public static List<Snippet> publicSnippets(){
        List<Snippet> list=new ArrayList<>();
        list.add(Snippet.official("اسم المؤسسة",NAME,"معلومات نفحات"));
        list.add(Snippet.official("عنوان المكتب",ADDRESS,"معلومات نفحات"));
        list.add(Snippet.official("الاتصال والاستفسارات",PHONE,"معلومات نفحات"));
        list.add(Snippet.official("واتساب نفحات",PHONE,"معلومات نفحات"));
        list.add(Snippet.official("فيسبوك",FACEBOOK,"معلومات نفحات"));
        list.add(Snippet.official("الموقع الإلكتروني",WEBSITE,"معلومات نفحات"));
        list.add(Snippet.official("البريد الإلكتروني",EMAIL,"معلومات نفحات"));
        list.add(Snippet.official("الترحيب","السلام عليكم ورحمة الله وبركاته 🤍\nأهلاً وسهلاً بكم في نفحات للحج والعمرة، كيف يمكننا مساعدتكم؟","ردود جاهزة"));
        list.add(Snippet.official("عنوان المكتب","يسعدنا زيارتكم في مكتب "+NAME+".\nالعنوان: "+ADDRESS,"ردود جاهزة"));
        list.add(Snippet.official("التواصل والحجوزات","يمكنكم التواصل معنا عبر الهاتف أو واتساب: "+PHONE,"ردود جاهزة"));
        return list;
    }
}
