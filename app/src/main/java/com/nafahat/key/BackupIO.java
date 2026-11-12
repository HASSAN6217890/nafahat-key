package com.nafahat.key;

import android.content.Context;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Local JSON export / import: collections + texts + settings, and the personal dictionary
 * only when the user explicitly ticks it. Importing merges and never deletes anything.
 */
public final class BackupIO {
    private BackupIO() { }

    public static final String FORMAT = "nafahatkey-backup";

    public static String export(Context context, boolean includeDictionary) throws Exception {
        JSONObject o = new JSONObject();
        o.put("format", FORMAT);
        o.put("version", 1);
        o.put("exported_at", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()));
        o.put("settings", new KeyboardPrefs(context).toJson());
        o.put("collections", LibraryDb.get(context).exportJson());
        if (includeDictionary) o.put("dictionary", UserDict.get(context).toJson());
        return o.toString(2);
    }

    /** Returns a short Arabic summary for the user. */
    public static String importJson(Context context, String json, boolean includeDictionary) throws Exception {
        JSONObject o = new JSONObject(json);
        if (!FORMAT.equals(o.optString("format"))) throw new IllegalArgumentException("not a NafahatKey backup");
        new KeyboardPrefs(context).applyJson(o.optJSONObject("settings"));
        int texts = LibraryDb.get(context).importJson(o.optJSONArray("collections"));
        int words = 0;
        if (includeDictionary && o.has("dictionary")) {
            UserDict.get(context).loadBlocking();
            words = UserDict.get(context).importJson(o.optJSONObject("dictionary"));
        }
        String msg = "تم الاستيراد: أُضيف " + texts + " نصاً، وطُبّقت الإعدادات.";
        if (words > 0) msg += " وأُضيفت " + words + " كلمة إلى القاموس الشخصي.";
        return msg;
    }
}
