package com.nafahat.key;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * The library inside the keyboard (replaces the keys, the top strip stays):
 * search · collection chips · type chips · «الأحدث» then «المثبّتة» then the rest.
 * Tap inserts and closes; long-press opens a full preview with insert / copy / pin.
 */
public final class LibraryPanel extends FrameLayout {
    public interface Host {
        void insertSnippet(Snippet s);
        void copySnippet(Snippet s);
        void startLibrarySearch();
        void saveClipboardTo(long collectionId);
        void togglePin(Snippet s);
    }

    private final Context ctx;
    private final float density;
    private Host host;
    private Theme theme = Theme.nafahat();
    private KeyboardPrefs prefs;
    private int fixedHeight;
    private long selectedCollection = -1;
    private String selectedType;
    private String query = "";
    private final LinearLayout column;
    private FrameLayout previewLayer;

    public LibraryPanel(Context context) {
        super(context);
        ctx = context;
        density = context.getResources().getDisplayMetrics().density;
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setHost(Host h) { host = h; }
    public void setPrefs(KeyboardPrefs p) { prefs = p; }
    public void setTheme(Theme t) { theme = t; }

    public void setPanelHeight(int px) {
        if (px == fixedHeight) return;
        fixedHeight = px;
        requestLayout();
    }

    @Override protected void onMeasure(int w, int h) {
        int hs = fixedHeight > 0 ? MeasureSpec.makeMeasureSpec(fixedHeight, MeasureSpec.EXACTLY) : h;
        super.onMeasure(w, hs);
    }

    public void setQuery(String q) { query = q == null ? "" : q; }
    public String query() { return query; }

    private int dp(float v) { return Math.round(v * density); }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView chip(String label, boolean active, View.OnClickListener click) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextSize(13);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), 0, dp(12), 0);
        t.setTextColor(active ? 0xFFFFFFFF : theme.cardText);
        t.setBackground(round(active ? theme.accent : theme.card, 16));
        t.setOnClickListener(click);
        return t;
    }

    private HorizontalScrollView chipRow(List<TextView> chips) {
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(4), 0, dp(4), 0);
        for (TextView t : chips) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(t, lp);
        }
        hs.addView(row);
        return hs;
    }

    private java.util.concurrent.Executor executor;
    private int loadSeq;

    public void setExecutor(java.util.concurrent.Executor e) { executor = e; }

    /** Reloads from the database on the background executor, then rebuilds on the main thread. */
    public void refresh() {
        final int seq = ++loadSeq;
        final long col = selectedCollection;
        Runnable load = () -> {
            LibraryDb db = LibraryDb.get(ctx);
            List<SnippetCollection> cols = db.collections(false);
            boolean found = col < 0;
            for (SnippetCollection c : cols) if (c.id == col) found = true;
            List<Snippet> all = db.snippets(found ? col : -1);
            final boolean ok = found;
            post(() -> {
                if (seq != loadSeq) return;
                if (!ok) selectedCollection = -1;
                build(cols, all);
            });
        };
        if (executor != null) executor.execute(load); else new Thread(load).start();
    }

    private void build(List<SnippetCollection> cols, List<Snippet> all) {
        removePreview();
        column.removeAllViews();
        setBackgroundColor(theme.background);

        // search + save clipboard
        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        top.setPadding(dp(6), dp(5), dp(6), dp(2));
        TextView search = new TextView(ctx);
        search.setText(query.isEmpty() ? "بحث في النصوص…" : "بحث: " + query);
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setEllipsize(TextUtils.TruncateAt.END);
        search.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setTextColor(query.isEmpty() ? (theme.hint | 0xFF000000) : theme.cardText);
        search.setBackground(round(theme.card, 18));
        search.setContentDescription("بحث في النصوص");
        search.setOnClickListener(v -> { if (host != null) host.startLibrarySearch(); });
        top.addView(search, new LinearLayout.LayoutParams(0, dp(36), 1f));
        if (!query.isEmpty()) {
            TextView clear = chip("مسح البحث", false, v -> { query = ""; refresh(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            lp.setMarginStart(dp(6));
            top.addView(clear, lp);
        }
        TextView save = chip("حفظ المنسوخ", false, v -> {
            if (host != null) host.saveClipboardTo(selectedCollection);
        });
        save.setContentDescription("حفظ النص المنسوخ في المكتبة");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        slp.setMarginStart(dp(6));
        top.addView(save, slp);
        column.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // collections
        List<TextView> cchips = new ArrayList<>();
        cchips.add(chip("الكل", selectedCollection < 0, v -> { selectedCollection = -1; refresh(); }));
        for (SnippetCollection c : cols) {
            final long id = c.id;
            cchips.add(chip(c.name, c.id == selectedCollection, v -> { selectedCollection = id; refresh(); }));
        }
        column.addView(chipRow(cchips));

        // types
        List<TextView> tchips = new ArrayList<>();
        tchips.add(chip("كل الأنواع", selectedType == null, v -> { selectedType = null; refresh(); }));
        List<String> types = prefs != null ? prefs.snippetTypes() : KeyboardPrefs.DEFAULT_TYPES;
        for (String t : types) {
            tchips.add(chip(t, t.equals(selectedType), v -> { selectedType = t; refresh(); }));
        }
        column.addView(chipRow(tchips));

        // list
        List<Snippet> filtered = new ArrayList<>();
        String q = TextNorm.normalize(query.trim());
        for (Snippet s : all) {
            if (selectedType != null && !selectedType.equals(s.type)) continue;
            if (!q.isEmpty() && !TextNorm.normalize(s.title + " " + s.body + " " + s.shortcut).contains(q)) continue;
            filtered.add(s);
        }
        List<Snippet> recent = new ArrayList<>();
        for (Snippet s : filtered) if (s.lastUsed > 0) recent.add(s);
        recent.sort((a, b) -> Long.compare(b.lastUsed, a.lastUsed));
        while (recent.size() > 3) recent.remove(recent.size() - 1);
        List<Snippet> pinned = new ArrayList<>();
        for (Snippet s : filtered) if (s.pinned && !recent.contains(s)) pinned.add(s);
        List<Snippet> rest = new ArrayList<>();
        for (Snippet s : filtered) if (!recent.contains(s) && !pinned.contains(s)) rest.add(s);

        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        list.setPadding(dp(6), 0, dp(6), dp(6));
        scroll.addView(list);
        if (filtered.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText(query.isEmpty() ? "لا توجد نصوص هنا بعد. أضِفها من تطبيق كيبورد نفحات." : "لا توجد نتائج لهذا البحث.");
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(theme.hint | 0xFF000000);
            list.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
        }
        section(list, "الأحدث", recent);
        section(list, "المثبّتة", pinned);
        section(list, recent.isEmpty() && pinned.isEmpty() ? null : "الباقي", rest);
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void section(LinearLayout list, String heading, List<Snippet> items) {
        if (items.isEmpty()) return;
        if (heading != null) {
            TextView h = new TextView(ctx);
            h.setText(heading);
            h.setTextSize(12);
            h.setTypeface(Typeface.DEFAULT_BOLD);
            h.setTextColor(theme.hint | 0xFF000000);
            h.setPadding(dp(6), dp(8), dp(6), dp(3));
            list.addView(h);
        }
        for (Snippet s : items) list.addView(card(s));
    }

    private View card(Snippet s) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(round(theme.card, 12));
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, dp(3));
        card.setLayoutParams(lp);

        TextView icon = new TextView(ctx);
        String t = s.type == null || s.type.isEmpty() ? "؟" : s.type.substring(0, 1);
        icon.setText(t);
        icon.setTextSize(13);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(0xFFFFFFFF);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(s.pinned ? theme.accent : Theme.GREEN);
        icon.setBackground(oval);
        icon.setContentDescription(s.type);
        card.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10), 0, 0, 0);
        TextView title = new TextView(ctx);
        title.setText(s.title);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(theme.cardText);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        TextView body = new TextView(ctx);
        body.setText(s.body);
        body.setTextSize(12);
        body.setTextColor(theme.cardText);
        body.setAlpha(0.8f);
        body.setMaxLines(2);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        texts.addView(body);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        card.setContentDescription(s.title + "، " + s.body);
        card.setOnClickListener(v -> { if (host != null) host.insertSnippet(s); });
        card.setOnLongClickListener(v -> { showPreview(s); return true; });
        return card;
    }

    private void removePreview() {
        if (previewLayer != null) { removeView(previewLayer); previewLayer = null; }
    }

    private void showPreview(Snippet s) {
        removePreview();
        previewLayer = new FrameLayout(ctx);
        previewLayer.setBackgroundColor((theme.background & 0x00FFFFFF) | 0xE6000000);
        previewLayer.setClickable(true);
        previewLayer.setOnClickListener(v -> removePreview());
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setBackground(round(theme.card, 14));
        box.setPadding(dp(14), dp(10), dp(14), dp(10));
        box.setClickable(true);
        TextView title = new TextView(ctx);
        title.setText(s.title);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(theme.cardText);
        box.addView(title);
        ScrollView sv = new ScrollView(ctx);
        TextView body = new TextView(ctx);
        body.setText(s.body);
        body.setTextSize(13);
        body.setTextColor(theme.cardText);
        body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        body.setPadding(0, dp(6), 0, dp(6));
        sv.addView(body);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        buttons.addView(button("إدراج", true, v -> { removePreview(); if (host != null) host.insertSnippet(s); }), weight());
        buttons.addView(button("نسخ", false, v -> { removePreview(); if (host != null) host.copySnippet(s); }), weight());
        buttons.addView(button(s.pinned ? "إلغاء التثبيت" : "تثبيت", false, v -> {
            removePreview();
            if (host != null) host.togglePin(s);
        }), weight());
        buttons.addView(button("إغلاق", false, v -> removePreview()), weight());
        box.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        blp.setMargins(dp(10), dp(8), dp(10), dp(8));
        previewLayer.addView(box, blp);
        addView(previewLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), 0);
        return lp;
    }

    private TextView button(String label, boolean primary, View.OnClickListener click) {
        TextView b = new TextView(ctx);
        b.setText(label);
        b.setTextSize(13);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setTextColor(primary ? 0xFFFFFFFF : theme.cardText);
        b.setBackground(round(primary ? Theme.GREEN : theme.background, 18));
        b.setOnClickListener(click);
        return b;
    }
}
