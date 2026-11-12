import com.nafahat.key.KbCipher;
import com.nafahat.key.KbMatcher;
import com.nafahat.key.Snippet;
import com.nafahat.key.TextNorm;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Host test for the v0.5 library matcher, variant choice and team decryption (no Android needed). */
public final class KbTest {
    static int fails;

    static void ok(boolean c, String m) {
        System.out.println((c ? "PASS " : "FAIL ") + m);
        if (!c) fails++;
    }

    static Snippet s(String id, String trip, boolean current, String title, String... kws) {
        Snippet x = new Snippet();
        x.origin = "server";
        x.remoteId = id;
        x.tripRemote = trip;
        x.tripCurrent = current;
        x.title = title;
        for (String k : kws) x.keywords.add(TextNorm.normalize(k));
        x.variants.add(new Snippet.Variant("any", "short", title + " قصير"));
        x.body = title + " قصير";
        return x;
    }

    public static void main(String[] a) throws Exception {
        List<Snippet> lib = new ArrayList<>();
        Snippet addr = s("info.address", "info", false, "العنوان", "عنوان", "وين", "مكتب", "موقعكم");
        Snippet welcome = s("info.welcome", "info", false, "الترحيب", "سلام", "مرحبا", "اهلا");
        Snippet price = s("t1117.price", "t1117", true, "السعر", "سعر", "كم", "اسعار", "قديش", "حق الرحلة");
        price.variants.add(new Snippet.Variant("wa", "long", "السعر طويل واتساب"));
        Snippet program = s("t1117.program", "t1117", true, "البرنامج", "برنامج", "كم يوم", "مدة");
        program.variants.clear();
        program.variants.add(new Snippet.Variant("ig", "short", "برنامج إنستغرام"));
        program.variants.add(new Snippet.Variant("wa", "long", "برنامج واتساب طويل"));
        program.variants.add(new Snippet.Variant("fb", "short", "برنامج فيسبوك"));
        Snippet local = new Snippet();
        local.title = "نص شخصي";
        lib.addAll(Arrays.asList(addr, welcome, price, program, local));

        ok(KbMatcher.best(lib, "ع") == null, "حرف واحد لا يقترح شيئاً");
        ok(KbMatcher.best(lib, "عن") == addr, "«عن» ← العنوان");
        ok(KbMatcher.best(lib, "مرح") == welcome, "«مرح» ← الترحيب");
        ok(KbMatcher.best(lib, "قدي") == price, "«قدي» ← السعر");
        ok(KbMatcher.best(lib, "أهلا") == welcome, "التطبيع: «أهلا» = «اهلا»");
        ok(KbMatcher.best(lib, "زززز") == null, "كلمة بلا مفتاح لا تقترح");
        Snippet infoKm = s("info.km", "info", false, "كم من المعلومات", "كم");
        lib.add(infoKm);
        ok(KbMatcher.best(lib, "كم") == price, "الرحلة الحالية أولاً عند التساوي");
        lib.remove(infoKm);

        ok(KbMatcher.platformFor("com.whatsapp").equals("wa") && KbMatcher.platformFor("com.whatsapp.w4b").equals("wa"), "واتساب وواتساب أعمال ← wa");
        ok(KbMatcher.platformFor("com.instagram.android").equals("ig"), "إنستغرام ← ig");
        ok(KbMatcher.platformFor("com.facebook.katana").equals("fb") && KbMatcher.platformFor("com.facebook.orca").equals("fb"), "فيسبوك وماسنجر ← fb");
        ok(KbMatcher.platformFor("com.android.chrome").equals("any") && KbMatcher.platformFor(null).equals("any"), "باقي التطبيقات ← any");

        ok(price.bodyFor("wa", false).equals("السعر قصير"), "واتساب بضغطة: القصير");
        ok(price.bodyFor("wa", true).equals("السعر طويل واتساب"), "واتساب بضغطة مطوّلة: الطويل");
        ok(price.bodyFor("ig", true).equals("السعر قصير"), "لا طويل لإنستغرام ← القصير العام");
        ok(program.bodyFor("ig", false).equals("برنامج إنستغرام") && program.bodyFor("fb", false).equals("برنامج فيسبوك"), "نسخة كل منصة");
        ok(program.bodyFor("wa", false).equals("برنامج واتساب طويل"), "واتساب بلا قصير ← طويل واتساب");
        ok(program.bodyFor("any", false).equals("برنامج إنستغرام"), "بلا any ← أول نسخة");
        ok(local.bodyFor("wa", true).equals(local.body), "النص المحلي بلا نسخ ← نصه");
        ok(local.isReadOnly() == false && addr.isReadOnly(), "نصوص الإدارة للقراءة فقط");

        List<Snippet> r = KbMatcher.replies(lib, "السلام عليكم، شو عنوانكم؟ وقديش سعر الرحلة؟", 3);
        ok(r.size() == 3 && r.contains(addr) && r.contains(price) && r.contains(welcome), "رسالة زبون ← العنوان والسعر والترحيب");
        ok(KbMatcher.replies(lib, "كم يوم الرحلة؟", 3).get(0) == program, "العبارة «كم يوم» ← البرنامج أولاً");
        ok(KbMatcher.replies(lib, "تمام شكراً", 3).isEmpty(), "رسالة بلا كلمات مفتاحية ← لا ردود");
        ok(KbMatcher.replies(lib, null, 3).isEmpty() && KbMatcher.replies(lib, "", 3).isEmpty(), "حافظة فارغة ← لا ردود");

        // Test vector made by card/tools/kb_build.py's AES-GCM with an obvious TEST key (bytes 0..31).
        byte[] key = KbCipher.parseKey("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8");
        String iv = "ZGVmZ2hpamtsbW5v";
        String ct = "MzmqFBCZJbwEOQLE-BAa33iZfSjiCNFIhaXJKZaN3Wq4yzSyJWK5Pt-aFlmSYObtbyLoggJwY0eOmcmkza2H_mH4t2v0cRndJLdKm79TtT_uWjMW0aT_r76fQrdZKRUqrAH-Hx8r1wze2OTnw2PByg4J7HqvHs1ckEOxwrLberMQs7rKBlY7CfNrj4NRNXxbEh55n9GTN0u7SPxtOZB8eEiGBSHN8MOLDiHWmJ4Q0OysqpCFFd83uR1tM3Lg9Hqvpw0qyNt6BnVgRVseGEeJ_BsdnNhGqiuKqkXLpedI";
        ok(key != null && KbCipher.kid(key).equals("52928426"), "قراءة المفتاح وkid مثل kb_build.py");
        ok(KbCipher.decrypt(key, 7, iv, ct).contains("نص للفريق فقط"), "فك قسم الفريق المشفّر بـkb_build.py");
        boolean bad = false;
        try { KbCipher.decrypt(key, 8, iv, ct); } catch (Exception e) { bad = true; }
        ok(bad, "rev آخر ← لا يُفك");
        bad = false;
        byte[] other = key.clone(); other[0] ^= 1;
        try { KbCipher.decrypt(other, 7, iv, ct); } catch (Exception e) { bad = true; }
        ok(bad, "مفتاح آخر ← لا يُفك");
        ok(KbCipher.parseKey("قصير") == null && KbCipher.parseKey("AAEC") == null && KbCipher.parseKey(null) == null, "مفتاح غير صالح ← مرفوض");
        ok(KbCipher.parseKey("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=") != null, "مع حشو = ← مقبول");

        System.out.println(fails == 0 ? "ALL PASS" : "FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
