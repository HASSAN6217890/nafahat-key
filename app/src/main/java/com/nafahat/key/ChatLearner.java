package com.nafahat.key;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * «علّم الكيبورد من كلامي»: learns words and word pairs from the user's own messages in a
 * WhatsApp chat export (.txt or .zip) or pasted text. Everything happens on the device and
 * the file content is only held in memory while it is processed — it is never stored.
 */
public final class ChatLearner {
    private ChatLearner() { }

    private static final int MAX_BYTES = 30 * 1024 * 1024;
    private static final Pattern HEADER = Pattern.compile(
        "^\\[?(\\d{1,4}[./-]\\d{1,2}[./-]\\d{1,4})[,،]?\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)[^\\]\\-:]{0,10}?"
            + "(?:\\]\\s*|\\s+-\\s+)([^:]{1,60}?):\\s(.*)$");
    private static final Pattern STARTS_WITH_DATE = Pattern.compile("^\\[?\\d{1,4}[./-]\\d{1,2}[./-]\\d{1,4}");
    private static final Pattern TAGS = Pattern.compile("<[^>]{1,60}>");
    private static final Pattern LINKS = Pattern.compile("(https?://\\S+|www\\.\\S+|\\S+@\\S+)");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{M}']+");

    public static final class Message {
        public final String sender;
        public final StringBuilder text;
        Message(String sender, String text) { this.sender = sender; this.text = new StringBuilder(text); }
    }

    public static final class Parsed {
        public final List<Message> messages = new ArrayList<>();
        public final LinkedHashMap<String, Integer> senders = new LinkedHashMap<>();
        public boolean whatsapp;
        public String plain;
    }

    /** Reads a .txt (or the .txt inside a WhatsApp .zip export) into memory, bounded. */
    public static String read(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream raw = resolver.openInputStream(uri)) {
            if (raw == null) throw new IOException("no stream");
            BufferedInputStream in = new BufferedInputStream(raw);
            in.mark(4);
            int b0 = in.read(), b1 = in.read();
            in.reset();
            if (b0 == 'P' && b1 == 'K') {
                try (ZipInputStream zip = new ZipInputStream(in)) {
                    ZipEntry e;
                    while ((e = zip.getNextEntry()) != null) {
                        if (!e.isDirectory() && e.getName().toLowerCase(java.util.Locale.US).endsWith(".txt"))
                            return readAll(zip);
                    }
                }
                throw new IOException("no text in zip");
            }
            return readAll(in);
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[32768];
        int n, total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_BYTES) break;
            out.write(buf, 0, n);
        }
        String s = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1);
        return s;
    }

    private static String cleanLine(String line) {
        StringBuilder b = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c >= '\u0660' && c <= '\u0669') c = (char) ('0' + (c - '\u0660'));
            else if (c >= '\u06F0' && c <= '\u06F9') c = (char) ('0' + (c - '\u06F0'));
            else if (c == '\u202F' || c == '\u00A0') c = ' ';
            else if (c == '\u200E' || c == '\u200F' || (c >= '\u202A' && c <= '\u202E') || (c >= '\u2066' && c <= '\u2069')) continue;
            b.append(c);
        }
        return b.toString();
    }

    public static Parsed parse(String text) {
        Parsed p = new Parsed();
        Message last = null;
        int headerLines = 0;
        for (String raw : text.split("\r?\n")) {
            String line = cleanLine(raw);
            Matcher m = HEADER.matcher(line);
            if (m.matches()) {
                headerLines++;
                String sender = m.group(3).trim();
                last = new Message(sender, m.group(4));
                p.messages.add(last);
                p.senders.merge(sender, 1, Integer::sum);
            } else if (STARTS_WITH_DATE.matcher(line).find()) {
                last = null; // system line (encryption notice, group events)
            } else if (last != null) {
                last.text.append('\n').append(line);
            }
        }
        p.whatsapp = headerLines >= 3 && !p.senders.isEmpty();
        if (!p.whatsapp) {
            p.messages.clear();
            p.senders.clear();
            p.plain = text;
        }
        return p;
    }

    /** Learns from the chosen sender's messages (or the whole pasted text). Returns distinct words learned. */
    public static int learn(Context context, Parsed parsed, String sender) {
        Map<String, Integer> words = new HashMap<>();
        Map<String, Integer> pairs = new HashMap<>();
        if (parsed.whatsapp) {
            for (Message m : parsed.messages) if (m.sender.equals(sender)) collect(m.text.toString(), words, pairs);
        } else if (parsed.plain != null) {
            for (String line : parsed.plain.split("\r?\n")) collect(line, words, pairs);
        }
        return UserDict.get(context).learnBulk(words, pairs);
    }

    private static void collect(String text, Map<String, Integer> words, Map<String, Integer> pairs) {
        String t = TAGS.matcher(text).replaceAll(" ");
        t = LINKS.matcher(t).replaceAll(" ");
        String lower = t.trim();
        if (lower.equals("null") || lower.contains("This message was deleted") || lower.contains("تم حذف هذه الرسالة")
            || lower.contains("Media omitted") || lower.contains("تم استبعاد الوسائط")) return;
        Matcher m = TOKEN.matcher(t);
        String prev = null;
        int prevEnd = -1;
        while (m.find()) {
            String w = TextNorm.stripMarks(m.group());
            while (w.startsWith("'")) w = w.substring(1);
            while (w.endsWith("'")) w = w.substring(0, w.length() - 1);
            if (!UserDict.learnable(w)) { prev = null; continue; }
            words.merge(w, 1, Integer::sum);
            boolean adjacent = prevEnd >= 0 && t.substring(prevEnd, m.start()).trim().isEmpty();
            if (prev != null && adjacent) pairs.merge(prev + "\t" + w, 1, Integer::sum);
            prev = w;
            prevEnd = m.end();
        }
    }
}
