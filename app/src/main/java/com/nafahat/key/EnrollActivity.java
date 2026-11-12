package com.nafahat.key;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 * nafahatkey://enroll?k=<key> — opened by the phone camera from the QR code the owner shows.
 * Asks before linking; the key is stored only wrapped by the Android Keystore.
 */
public final class EnrollActivity extends Activity {
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Uri u = getIntent() == null ? null : getIntent().getData();
        String k = u != null && "nafahatkey".equals(u.getScheme()) && "enroll".equals(u.getHost()) ? u.getQueryParameter("k") : null;
        if (KbCipher.parseKey(k) == null) {
            Toast.makeText(this, "رمز الربط غير صالح", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        final String key = k;
        new AlertDialog.Builder(this)
            .setTitle("ربط الهاتف بنصوص فريق نفحات")
            .setMessage("سيُنزّل كيبورد نفحات نصوص الفريق الداخلية ويفكّها على هذا الهاتف فقط. لا يُرسل الكيبورد شيئاً مما تكتبه. هل تريد الربط؟")
            .setNegativeButton("إلغاء", (d, w) -> finish())
            .setOnCancelListener(d -> finish())
            .setPositiveButton("ربط", (d, w) -> link(this, key, this::finish))
            .show();
    }

    /** Saves the key, then downloads the library again in the background. */
    static void link(Activity a, String key, Runnable done) {
        if (!KbCrypto.save(a, key)) {
            Toast.makeText(a, "رمز الربط غير صالح", Toast.LENGTH_LONG).show();
            if (done != null) done.run();
            return;
        }
        final android.content.Context app = a.getApplicationContext();
        new Thread(() -> KbSync.run(app, true), "nafahatkey-kb").start();
        Toast.makeText(a, "تم الربط. تُنزَّل نصوص الفريق الآن.", Toast.LENGTH_LONG).show();
        if (done != null) done.run();
    }
}
