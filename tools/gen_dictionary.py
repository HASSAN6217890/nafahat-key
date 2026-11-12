#!/usr/bin/env python3
"""Generate NafahatKey dictionary assets offline (run locally, output is committed).

Requires: pip install wordfreq
Outputs (app/src/main/assets/dict/):
  ar.txt / en.txt   one word per line: word<TAB>score   (score = round(zipf*10), sorted by score desc)
  bigrams.txt       w1<TAB>w2<TAB>count  (from the curated phrase list below)
No user data is involved; everything here is public vocabulary.
"""
import re
from collections import Counter
from pathlib import Path
from wordfreq import top_n_list, zipf_frequency

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/src/main/assets/dict"
OUT.mkdir(parents=True, exist_ok=True)

AR_WORD = re.compile(r"^[ء-غف-يٱ]+$")
EN_WORD = re.compile(r"^[a-z]+(?:'[a-z]+)?$")
TASHKEEL = re.compile(r"[ً-ْـٰ]")

NAFAHAT = """عمرة العمرة تأشيرة التأشيرة تأشيرات المدينة المنورة مكة المكرمة نسك إحرام الإحرام طواف الطواف
سعي السعي زيارة الزيارة زيارات الطيلوني اللاميرادا نفحات الحسام الروضة الشريفة البقيع قباء أحد
معتمر معتمرين المعتمرين حج الحج حجاج الحجاج رحلة الرحلة رحلات الرحلات الحرم المسجد النبوي الحرام
الميقات التلبية لبيك الكعبة الحجر الأسود زمزم الصفا المروة جدة الطائف المدينة مكة دمشق حلب حمص حماة إدلب
فندق الفندق الفنادق الباص الباصات جواز الجواز السفر صورة شخصية حجز الحجز المقعد المكتب الاستفسار
الحمرا مول عصير أبو الشكر الطابق الثالث بناء المؤسسة الدوام الجمعة عطلة واتساب فيسبوك الموقع""".split()

SHAMI = """شو هلأ هلق منيح منيحة كتير بدي بدك بدكن بدنا هيك ليش وين إمتى امتى لسا لسه يعني طيب والله
إن شاء الله ان شاء الله الله يعطيك العافية يعطيك العافية تكرم تكرمي تكرموا حبيبي حبيبتي أهلين أهلا مرحبا
يسلمو يسلموا تسلم تسلمي ماشي تمام خلص هاد هادا هاي هدول هنن هني منشان مشان كمان شوي شوية بكرا
مبارح هون هنيك فيك فيكي فينا عنا عندك عندكن معك معكن حكيت حكينا بحكي منحكي بشوفك نشوفكم ابعتلي
ابعتلك بعتلك بعتتلك اتصل اتصلي رح رح نرد نتواصل بتمنى بنتمنى الحمد لله الحمدلله ما شاء الله جزاك الله خير
الله يسلمك الله يخليك تفضل تفضلي تفضلوا أكيد طبعا ولا يهمك على راسي من عيوني""".split()

# Large list of common Shami / colloquial spellings (customer-service chat). These must be
# "known" so they are never auto-replaced. Score 52 (zipf 5.2).
SHAMI_EXTRA = """
قديش قديه أديش اديش وقتيش لوين منين شلون كيفكن كيفكم كيفك كيفو كيفها منيحين منيحة هلق هلقد
هيكي هونيك هنيك هيدا هيدي هداك هديك هدول هنن هني هوي هيي انتو انتي نحنا وينك وينكن وينو
بدكن بدكم بدو بدها بدهم بدنا عندكن عندكم عنا معكن معكم الكن إلكن إلك الك الو إلو الها النا
كتار حبتين يلا خيي خيو عمو ستي حبايب حبايبي حبيبي حبيبتي أخوي اخوي اختي أختي ولادكن ولادي
عنجد منكن منكم منا مننا كرمالك كرمالكن كرمال مشان منشان عشان لأنو لانو مبلا بلى ماعليش معلش
مبسوط مبسوطين زعلان زعلانة تعبان تعبانين نعسان جوعان بالزبط شبك شبكن ولك ياريت انشالله شالله
مرحبتين مرحبا مرحبتين أهلين اهلين يسلمو يسلموا يسلمك يسلم تسلم تسلمي تسلملي يعطيك يعطيكن تكرم
تكرمي تكرمو تقبرني منتظرينكن ناطرينكن ناطرين بنتظرك منتظرك جاهزين جاهزة بالباص عالسريع
جوازك جوازكن جوازاتكن جوازات صورتك صورتكن تأشيرتك تأشيرتكن تأشيرتو عمرتكن عمرتك عمرتنا رحلتكن
رحلتنا رحلتك حجزكن حجزك حجزنا مكانكن مكانك بالمكتب عالمكتب عالموعد عالرحلة بالرحلة عالحرم
بالحرم بالمدينة بمكة عالمدينة عمكة بيتكن عرسكن اهلكن اهلك جماعتنا جماعتكن طلعتو وصلتو رجعتو
نزلتو طلعنا وصلنا رجعنا نزلنا خلصو خلصنا خلصت بلشنا بلشو لسا لسه بعدين بكرا مبارح هلأ
شغلة شغلات شي شغلي شغلك تمام ماشي طيب أكيد اكيد طبعا والله ولله يعني يمكن بلكي مبين كمان لكان
عم رح ح بس ليش وين امتى إمتى شو مين كيف هون هيك هاد هادا هاي منيح كتير شوي شوية خلص
بنشوف منشوفك منشوفكن بتشوفي بتعرفي بتعرفو بتقدر بقدر منقدر بتقدري بتقدرو تفضلي تفضلو تعا تعي تعو تعال
""".split()

# Imperfect verb stems (3rd person); Shami present forms are generated from them
# (بـ / بتـ / بيـ / منـ / تـ / نـ / اـ and the plural -و). Score 35 (known, never a replacement target).
VERB_STEMS = """يحكي يشوف يروح يجي يعرف يحجز يبعت يتصل يقدر يحب يسأل يرد يكتب يقرا يدفع ينزل يطلع يوصل
يرجع يسافر يجهز يستنى يضل يصير يعمل يشتغل ينام ياكل يشرب يلاقي يفهم يقول يحط ياخد يعطي يجيب يسجل
يبلش يخلص يتأخر يتواصل يزور يعتمر يصلي يدعي يحضر يرتاح يتصور يدور يسمع يشتري يبيع يغير يلغي
يأكد يأجل يقعد يمشي يتذكر ينسى يخاف يزعل يفرح يحس يتمنى يستلم يسلم يوقف يفتح يسكر يحمل يرسل
يتفضل يتكلم يسامح يساعد يخبر يبشر يطمن يحكيلك يبعتلك""".split()

def verb_forms(stem):
    base = stem[1:]
    plural = (base[:-1] if base.endswith("ي") else base) + "و"
    out = set()
    for p in ("ب", "بت", "بي", "من", "ت", "ن", "ا", "ي"):
        out.add(p + base)
    for p in ("بت", "بي", "ت", "ي"):
        out.add(p + plural)
    return out

PHRASES = """
السلام عليكم ورحمة الله وبركاته
وعليكم السلام ورحمة الله وبركاته
أهلا وسهلا ومرحبا
أهلا وسهلا فيك
أهلين فيك
مرحبا كيفك
كيف حالك
كيفك إن شاء الله منيح
الحمد لله على كل حال
الله يعطيك العافية
يعطيك العافية
الله يسلمك
الله يخليك
جزاك الله خير
جزاكم الله خيرا
بارك الله فيك
بارك الله فيكم
إن شاء الله خير
إن شاء الله بتكون رحلة موفقة
ما شاء الله
تقبل الله منا ومنكم
عمرة مقبولة إن شاء الله
حج مبرور وسعي مشكور
شوف كيف
شوف إذا
شو بدك
شو رأيك
شو الأخبار
شو في
بدي احجز
بدي اسأل عن العمرة
بدي اسأل عن الرحلة
بدي اعرف السعر
بدي اعرف موعد الرحلة
بدك تحجز
بدكن تحجزوا
ليش لا
وين المكتب
وين مكان الانطلاق
وين بالضبط
إمتى الرحلة
إمتى موعد الانطلاق
إمتى بتطلع الرحلة
لسا ما وصل
لسا ما في
يعني إن شاء الله
طيب تمام
طيب ماشي
تمام إن شاء الله
والله ما بعرف
والله العظيم
حياكم الله في نفحات للحج والعمرة
نفحات للحج والعمرة
أهلا وسهلا بكم في نفحات للحج والعمرة
كيف يمكننا مساعدتكم
يسعدنا تواصلكم معنا
يسعدنا زيارتكم في المكتب
نتشرف بزيارتكم
للحجز والاستفسار يمكنكم التواصل معنا
يمكنكم التواصل معنا على الرقم
يمكنكم زيارة المكتب
أوقات الدوام من الساعة
الجمعة عطلة
العنوان شارع الحمرا
شارع الحمرا اللاميرادا مول
فوق عصير أبو الشكر
الطابق الثالث بناء الطيلوني
رحلة العمرة
رحلة عمرة
موعد الرحلة القادمة
الرحلة القادمة
الرحلة المميزة
برنامج الرحلة
تفاصيل الرحلة
مدة الرحلة
عدد الأيام
المدينة المنورة
مكة المكرمة
المسجد النبوي الشريف
المسجد الحرام
الروضة الشريفة
مسجد قباء
جبل أحد
بقيع الغرقد
زيارة المدينة المنورة
زيارة الروضة الشريفة
تصريح الروضة عن طريق نسك
تطبيق نسك
التأشيرة جاهزة
تأشيرة العمرة
صورة جواز السفر
صورة شخصية بخلفية بيضاء
جواز السفر ساري المفعول
يرجى إرسال صورة الجواز
يرجى إرسال صورة شخصية
الرجاء الانتظار قليلا
نعتذر عن التأخير
شكرا لتواصلكم
شكرا جزيلا
شكرا لك
عفوا
بأقرب وقت
بأسرع وقت ممكن
رح نرد عليك
رح نتواصل معك
رح نبعتلك التفاصيل
ابعتلي رقمك
ابعتلي الصورة
بعتتلك التفاصيل
خلص تمام
خلص ماشي
على راسي
من عيوني
تكرم عينك
ولا يهمك
الله يوفقك
الله يتقبل منك
الله يتقبل منا ومنكم
في أمان الله
مع السلامة
الله معك
تصبح على خير
صباح الخير
صباح النور
مساء الخير
مساء النور
رمضان كريم
عيد مبارك
كل عام وأنتم بخير
لا إله إلا الله
سبحان الله
صلى الله عليه وسلم
اللهم صل على سيدنا محمد
hello how are you
thank you very much
thanks a lot
see you soon
good morning
good night
please send me
let me know
as soon as possible
""".strip().splitlines()

def ar_list(n):
    seen, out = set(), []
    for w in top_n_list("ar", n * 2):
        w = TASHKEEL.sub("", w)
        if not AR_WORD.match(w) or w in seen:
            continue
        if len(w) < 2 and w not in ("و", "ب", "ل", "ف"):
            continue
        seen.add(w)
        out.append((w, max(1, round(zipf_frequency(w, "ar") * 10))))
        if len(out) >= n:
            break
    return out, seen

def en_list(n):
    seen, out = set(), []
    for w in top_n_list("en", n * 2):
        if not EN_WORD.match(w) or w in seen:
            continue
        if len(w) < 2 and w not in ("a", "i"):
            continue
        seen.add(w)
        out.append((w, max(1, round(zipf_frequency(w, "en") * 10))))
        if len(out) >= n:
            break
    return out, seen

def words_of(text):
    return [TASHKEEL.sub("", t) for t in re.findall(r"[ء-يٱ]+|[A-Za-z']+", text)]

ar, ar_seen = ar_list(50000)
en, en_seen = en_list(30000)
extra = {}
for w in words_of(" ".join(NAFAHAT + SHAMI) + " " + " ".join(PHRASES)):
    if AR_WORD.match(w):
        extra[w] = max(extra.get(w, 0), 55)
ar_map = dict(ar)
for w, s in extra.items():
    ar_map[w] = max(ar_map.get(w, 0), s)
for w in SHAMI_EXTRA:
    w = TASHKEEL.sub("", w)
    if AR_WORD.match(w):
        ar_map[w] = max(ar_map.get(w, 0), 52)
for stem in VERB_STEMS:
    for w in verb_forms(stem) | {stem}:
        if AR_WORD.match(w):
            ar_map[w] = max(ar_map.get(w, 0), 35)
ar_sorted = sorted(ar_map.items(), key=lambda kv: (-kv[1], kv[0]))
en_map = dict(en)
en_map["i"] = max(en_map.get("i", 0), 70)
en_sorted = sorted(en_map.items(), key=lambda kv: (-kv[1], kv[0]))

bigrams = Counter()
for line in PHRASES:
    ws = [w.lower() if re.match(r"[A-Za-z]", w) else w for w in words_of(line)]
    for a, b in zip(ws, ws[1:]):
        bigrams[(a, b)] += 1

def write(name, rows):
    with open(OUT / name, "w", encoding="utf-8", newline="\n") as f:
        for r in rows:
            f.write("\t".join(str(x) for x in r) + "\n")

write("ar.txt", ar_sorted)
write("en.txt", en_sorted)
write("bigrams.txt", sorted(((a, b, c) for (a, b), c in bigrams.items())))
print(f"ar={len(ar_sorted)} en={len(en_sorted)} bigrams={len(bigrams)}")
