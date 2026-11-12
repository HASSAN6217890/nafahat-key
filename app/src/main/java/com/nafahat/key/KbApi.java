package com.nafahat.key;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

/**
 * The only network code in NafahatKey (v0.5): two GET requests to card.nafahatsy.com/kb/.
 * Download only. No typed text, no identifiers, no cookies and no custom headers are sent;
 * redirects are not followed; nothing is logged.
 */
public final class KbApi {
    private KbApi() { }

    public static final String HOST = "card.nafahatsy.com";
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_VERSION = 4 * 1024;
    private static final int MAX_LIBRARY = 2 * 1024 * 1024;

    /** The library is not on the server yet (404, or something that is not our JSON). */
    public static final class NotPublished extends Exception { }

    /** GET https://card.nafahatsy.com/kb/v.json?ts=<now> */
    public static byte[] version() throws Exception {
        return get("/kb/v.json?ts=" + System.currentTimeMillis(), MAX_VERSION);
    }

    /** GET https://card.nafahatsy.com/kb/library.<rev>.json */
    public static byte[] library(int rev) throws Exception {
        if (rev <= 0) throw new IllegalArgumentException("rev");
        return get("/kb/library." + rev + ".json", MAX_LIBRARY);
    }

    private static byte[] get(String path, int max) throws Exception {
        URL url = new URL("https", HOST, 443, path);
        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setUseCaches(false);
            c.setDoOutput(false);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "NafahatKey");
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code == 404 || code == 410) throw new NotPublished();
            if (code != 200) throw new java.io.IOException("HTTP " + code);
            String type = c.getContentType();
            if (type != null && type.toLowerCase(java.util.Locale.US).startsWith("text/html")) throw new NotPublished();
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > max) throw new java.io.IOException("too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
