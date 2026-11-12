package com.nafahat.key;

import java.util.ArrayList;
import java.util.List;

/** Only confirmed public business information; no prices, costs or internal operational data. */
public final class BrandData {
    private BrandData() {}
    public static final String NAME = "نفحات للحج والعمرة";
    public static final String GREETING = "السلام عليكم ورحمة الله وبركاته 🤍";
    public static final String ADDRESS = "شارع الحمرا – اللاميرادا مول – فوق عصير أبو الشكر – الطابق الثالث – بناء الطيلوني – نفحات للحج والعمرة";
    public static final String PHONE = "+963 994 791 692";
    /** Exactly one LEFT-TO-RIGHT MARK before "+" keeps the plus sign in place inside Arabic text. */
    public static final String PHONE_LTR = "\u200E" + PHONE;
    public static final String FACEBOOK = "https://www.facebook.com/NafahatSY";
    public static final String WEBSITE = "https://nafahatsy.com/";
    public static final String EMAIL = "info@nafahatsy.com";
    public static final String HOURS = "يومياً من 11 صباحاً إلى 5 مساءً، والجمعة عطلة";

    public static final int INFO = 1;
    public static final int REPLIES = 2;
    public static final String INFO_NAME = "معلومات نفحات";
    public static final String REPLIES_NAME = "ردود جاهزة";

    public static List<Snippet> publicSnippets() {
        List<Snippet> r = new ArrayList<>();
        r.add(Snippet.builtin("info.name", INFO, "اسم المؤسسة", NAME, "أخرى"));
        r.add(Snippet.builtin("info.address", INFO, "عنوان المكتب", ADDRESS, "عنوان/روابط"));
        r.add(Snippet.builtin("info.phone", INFO, "الاتصال والاستفسارات", PHONE_LTR, "عنوان/روابط"));
        r.add(Snippet.builtin("info.whatsapp", INFO, "واتساب نفحات", PHONE_LTR, "واتساب"));
        r.add(Snippet.builtin("info.facebook", INFO, "فيسبوك", FACEBOOK, "عنوان/روابط"));
        r.add(Snippet.builtin("info.website", INFO, "الموقع الإلكتروني", WEBSITE, "عنوان/روابط"));
        r.add(Snippet.builtin("info.email", INFO, "البريد الإلكتروني", EMAIL, "عنوان/روابط"));
        r.add(Snippet.builtin("info.hours", INFO, "أوقات الدوام", HOURS, "أخرى"));
        r.add(Snippet.builtin("reply.welcome", REPLIES, "الترحيب",
            GREETING + "\nأهلاً وسهلاً بكم في " + NAME + "، كيف يمكننا مساعدتكم؟", "رد"));
        r.add(Snippet.builtin("reply.address", REPLIES, "الرد بعنوان المكتب",
            "يسعدنا زيارتكم في مكتب " + NAME + ".\nالعنوان: " + ADDRESS + ".\nأوقات الدوام: " + HOURS + ".", "رد"));
        r.add(Snippet.builtin("reply.contact", REPLIES, "التواصل والحجوزات",
            "للحجز والاستفسار يمكنكم التواصل معنا على الرقم " + PHONE_LTR + " عبر الاتصال أو واتساب.", "رد"));
        r.add(Snippet.builtin("reply.links", REPLIES, "روابط نفحات",
            "الموقع: " + WEBSITE + "\nفيسبوك: " + FACEBOOK, "عنوان/روابط"));
        return r;
    }
}
