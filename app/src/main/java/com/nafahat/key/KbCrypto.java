package com.nafahat.key;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The team key (32 bytes) is stored only wrapped by a non-exportable AES key in the Android
 * Keystore. Nothing about it is ever logged or sent anywhere.
 */
public final class KbCrypto {
    private KbCrypto() { }

    private static final String ALIAS = "nafahatkey_kb_wrap";
    private static final String PREF = "nafahatkey_kb_key";

    private static SharedPreferences store(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static SecretKey wrapKey(boolean create) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        if (!create) return null;
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return g.generateKey();
    }

    /** Saves the team key; returns false if the text is not a valid 32-byte key. */
    public static synchronized boolean save(Context c, String text) {
        byte[] key = KbCipher.parseKey(text);
        if (key == null) return false;
        try {
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.ENCRYPT_MODE, wrapKey(true));
            byte[] ct = ci.doFinal(key);
            store(c).edit()
                .putString("iv", Base64.encodeToString(ci.getIV(), Base64.NO_WRAP))
                .putString("ct", Base64.encodeToString(ct, Base64.NO_WRAP))
                .putString("kid", KbCipher.kid(key))
                .commit();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            java.util.Arrays.fill(key, (byte) 0);
        }
    }

    /** The team key, or null when this phone is not linked (or the Keystore lost it). */
    public static synchronized byte[] load(Context c) {
        SharedPreferences p = store(c);
        String iv = p.getString("iv", null), ct = p.getString("ct", null);
        if (iv == null || ct == null) return null;
        try {
            SecretKey w = wrapKey(false);
            if (w == null) return null;
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.DECRYPT_MODE, w, new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            byte[] k = ci.doFinal(Base64.decode(ct, Base64.NO_WRAP));
            return k.length == 32 ? k : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean linked(Context c) { return store(c).contains("ct"); }

    /** Key id of the stored key (to tell the user when it does not match the library). */
    public static String kid(Context c) { return store(c).getString("kid", ""); }

    /** «إلغاء الربط»: forgets the key (team texts are removed by the caller). */
    public static synchronized void clear(Context c) {
        store(c).edit().clear().commit();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
        } catch (Exception ignored) { }
    }
}
