package com.nafahat.key;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM for the team section of the Nafahat library (card/server/kb.md). Pure Java.
 * The additional data binds the ciphertext to the library revision: nafahatkey-team:<rev>.
 */
public final class KbCipher {
    private KbCipher() { }

    /** A 32-byte key from base64url (padding optional; standard base64 accepted), or null. */
    public static byte[] parseKey(String text) {
        if (text == null) return null;
        String t = text.trim().replace('+', '-').replace('/', '_').replace("=", "");
        if (t.isEmpty() || !t.matches("[A-Za-z0-9_-]+")) return null;
        try {
            byte[] k = Base64.getUrlDecoder().decode(t);
            return k.length == 32 ? k : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Short key id published next to the ciphertext, so a wrong key is recognised. */
    public static String kid(byte[] key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update("nafahatkey-kid:".getBytes(StandardCharsets.US_ASCII));
            byte[] h = md.digest(key);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 4; i++) b.append(String.format(java.util.Locale.US, "%02x", h[i] & 0xff));
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Decrypts the team section; throws on a wrong key, another revision or any tampering. */
    public static String decrypt(byte[] key, int rev, String ivB64, String ctB64) throws Exception {
        Base64.Decoder d = Base64.getUrlDecoder();
        byte[] iv = d.decode(ivB64.replace("=", ""));
        byte[] ct = d.decode(ctB64.replace("=", ""));
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        c.updateAAD(("nafahatkey-team:" + rev).getBytes(StandardCharsets.US_ASCII));
        return new String(c.doFinal(ct), StandardCharsets.UTF_8);
    }

    public static String sha256Hex(byte[] data) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder b = new StringBuilder();
        for (byte x : h) b.append(String.format(java.util.Locale.US, "%02x", x & 0xff));
        return b.toString();
    }
}
