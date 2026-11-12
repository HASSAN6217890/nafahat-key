package com.nafahat.key;

import java.util.ArrayList;
import java.util.List;

/** Only confirmed public business information; no prices or internal operational data. */
public final class BrandData {
    private BrandData() {}
    public static final String NAME = "نفحات للحج والعمرة";
    public static final String ADDRESS = "شارع الحمرا – اللاميرادا مول – فوق عصير أبو الشكر – الطابق الثالث – بناء الطيلوني – نفحات للحج والعمرة";
    public static final String PHONE = "+963 994 791 692";
    // LTR isolate prevents the leading plus sign moving in RTL messaging apps.
    public static final String PHONE_LTR = "\u2066" + PHONE + "\u2069";
    public static final String FACEBOOK = "https://www.facebook.com/NafahatSY";
    public static final String WEBSITE = "https://nafahatsy.com/";
    public static final String EMAIL = "info@nafahatsy.com";
    public static final String HOURS = "يومياً من 11 صباحاً إلى 5 مساءً، والجمعة عطلة";

    public static List<Snippet> publicSnippets() {
        List<Snippet> result = new ArrayList<>();
        result.add(Snippet.official("اسم المؤسسة", NAME, "معلومات نفحات"));
        result.add(Snippet.official("عنوان المكتب", ADDRESS, "معلومات نفحات"));
        result.add(Snippet.official("الاتصال والاستفسارات", PHONE_LTR, "معلومات نفحات"));
        result.add(Snippet.official("واتساب نفحات", PHONE_LTR, "معلومات نفحات"));
        result.add(Snippet.official("فيسبوك", FACEBOOK, "معلومات نفحات"));
        result.add(Snippet.official("الموقع الإلكتروني", WEBSITE, "معلومات نفحات"));
        result.add(Snippet.official("البريد الإلكتروني", EMAIL, "معلومات نفحات"));
        result.add(Snippet.official("أوقات الدوام", HOURS, "معلومات نفحات"));
        result.add(Snippet.official("الترحيب", "أهلاً وسهلاً ومرحباً، حياكم الله في نفحات للحج والعمرة. كيف يمكننا مساعدتكم؟", "ردود جاهزة"));
        result.add(Snippet.official("الرد بعنوان المكتب", "يسعدنا زيارتكم في مكتب " + NAME + ".\nالعنوان: " + ADDRESS + ".", "ردود جاهزة"));
        result.add(Snippet.official("التواصل والحجوزات", "للحجز والاستفسار يمكنكم التواصل معنا على الرقم " + PHONE_LTR + " عبر الاتصال أو واتساب.", "ردود جاهزة"));
        result.add(Snippet.official("روابط نفحات", "الموقع: " + WEBSITE + "\nفيسبوك: " + FACEBOOK, "ردود جاهزة"));
        return result;
    }
}
