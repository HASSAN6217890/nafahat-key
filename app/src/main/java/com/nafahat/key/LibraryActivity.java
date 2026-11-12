package com.nafahat.key;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Library management: collections (trips), texts, drag ordering, archiving and text types. */
public final class LibraryActivity extends Activity {
    private LibraryDb db;
    private KeyboardPrefs prefs;
    private LinearLayout content;
    private long selected = -1;
    private boolean showArchived;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        db = LibraryDb.get(this);
        prefs = new KeyboardPrefs(this);
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        content = UiKit.vertical(this);
        UiKit.pad(content, this, 14, 14);
        scroll.addView(content);
        setContentView(scroll);

        TextView title = UiKit.text(this, "النصوص والمجموعات", 21, true);
        title.setTextColor(UiKit.GREEN);
        content.addView(title);
        TextView hint = UiKit.text(this, "اسحب العلامة ≡ لترتيب العناصر. المجموعتان «معلومات نفحات» و«ردود جاهزة» ثابتتان "
            + "وتُحدَّثان مع التطبيق. في النصوص يمكن استعمال خانات مثل {الاسم} {التاريخ} {المبلغ} تُملأ قبل الإدراج.", 13, false);
        hint.setTextColor(Color.DKGRAY);
        content.addView(hint);

        List<SnippetCollection> cols = db.collections(true);
        SnippetCollection current = null;
        for (SnippetCollection c : cols) if (c.id == selected) current = c;
        if (current == null) {
            for (SnippetCollection c : cols) if (!c.archived) { current = c; break; }
            selected = current == null ? -1 : current.id;
        }

        // collection chips
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = UiKit.horizontal(this);
        for (SnippetCollection c : cols) {
            if (c.archived && !showArchived && c.id != selected) continue;
            TextView chip = UiKit.chip(this, c.archived ? c.name + " (مؤرشفة)" : c.name, c.id == selected);
            chip.setOnClickListener(v -> { selected = c.id; render(); });
            chips.addView(chip, chipParams());
        }
        TextView add = UiKit.chip(this, "+ مجموعة جديدة", false);
        add.setTextColor(UiKit.GREEN);
        add.setOnClickListener(v -> askText("مجموعة جديدة", "مثال: رحلة 7.1", "", name -> {
            long id = db.addCollection(name);
            if (id > 0) selected = id;
            render();
        }));
        chips.addView(add, chipParams());
        hs.addView(chips);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, UiKit.dp(this, 48));
        hlp.topMargin = UiKit.dp(this, 10);
        content.addView(hs, hlp);

        LinearLayout tools = UiKit.horizontal(this);
        Button order = UiKit.secondary(this, "ترتيب المجموعات");
        order.setOnClickListener(v -> orderCollectionsDialog());
        tools.addView(order, weight());
        Button arch = UiKit.secondary(this, showArchived ? "إخفاء المؤرشفة" : "إظهار المؤرشفة");
        arch.setOnClickListener(v -> { showArchived = !showArchived; render(); });
        tools.addView(arch, weight());
        content.addView(tools, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 40)));

        if (current != null) renderCollection(current);
        renderTypes();
    }

    private void renderCollection(SnippetCollection c) {
        TextView name = UiKit.text(this, c.name, 18, true);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = UiKit.dp(this, 16);
        content.addView(name, nlp);
        if (!c.isReadOnly()) {
            LinearLayout actions = UiKit.horizontal(this);
            Button rename = UiKit.secondary(this, "إعادة التسمية");
            rename.setOnClickListener(v -> askText("إعادة تسمية المجموعة", "", c.name, n -> { db.renameCollection(c.id, n); render(); }));
            actions.addView(rename, weight());
            Button archive = UiKit.secondary(this, c.archived ? "إلغاء الأرشفة" : "أرشفة (رحلة انتهت)");
            archive.setOnClickListener(v -> { db.setArchived(c.id, !c.archived); render(); });
            actions.addView(archive, weight());
            Button del = UiKit.secondary(this, "حذف");
            del.setOnClickListener(v -> {
                if (db.deleteEmptyCollection(c.id)) { selected = -1; render(); }
                else Toast.makeText(this, "لا تُحذف إلا المجموعة الفارغة. استعمل الأرشفة للرحلات المنتهية.", Toast.LENGTH_LONG).show();
            });
            actions.addView(del, weight());
            content.addView(actions, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 40)));
        }
        DragList list = new DragList(this);
        list.setOnReorder(ids -> db.reorderSnippets(ids));
        List<Snippet> items = db.snippetsOf(c.id);
        for (Snippet s : items) {
            LinearLayout row = UiKit.horizontal(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UiKit.rounded(UiKit.SURFACE, 12, this));
            UiKit.pad(row, this, 10, 8);
            TextView handle = UiKit.text(this, "≡", 22, true);
            handle.setTextColor(c.isReadOnly() ? Color.TRANSPARENT : Color.GRAY);
            handle.setGravity(Gravity.CENTER);
            handle.setContentDescription("اسحب للترتيب");
            row.addView(handle, new LinearLayout.LayoutParams(UiKit.dp(this, 32), UiKit.dp(this, 40)));
            LinearLayout texts = UiKit.vertical(this);
            TextView t = UiKit.text(this, (s.pinned ? "★ " : "") + s.title + "  ·  " + s.type + (s.isServer() ? "  ·  من الإدارة" : ""), 15, true);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(t);
            TextView b = UiKit.text(this, s.body, 13, false);
            b.setMaxLines(2);
            b.setEllipsize(TextUtils.TruncateAt.END);
            b.setTextColor(Color.DKGRAY);
            texts.addView(b);
            if (!s.shortcut.isEmpty()) {
                TextView sc = UiKit.text(this, "اختصار: #" + s.shortcut.replace("#", ""), 12, false);
                sc.setTextColor(UiKit.GOLD);
                texts.addView(sc);
            }
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
            row.setOnClickListener(v -> editDialog(s, c));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.setMargins(0, UiKit.dp(this, 4), 0, UiKit.dp(this, 4));
            row.setLayoutParams(rlp);
            list.addRow(row, c.isReadOnly() ? null : handle, s.id);
        }
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));
        if (items.isEmpty()) {
            TextView empty = UiKit.text(this, "لا توجد نصوص في هذه المجموعة بعد.", 13, false);
            empty.setTextColor(Color.GRAY);
            content.addView(empty);
        }
        if (!c.isReadOnly()) {
            Button add = UiKit.button(this, "+ نص جديد");
            add.setOnClickListener(v -> {
                Snippet s = new Snippet();
                s.collectionId = c.id;
                editDialog(s, c);
            });
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, UiKit.dp(this, 46));
            alp.topMargin = UiKit.dp(this, 8);
            content.addView(add, alp);
        }
    }

    private void renderTypes() {
        TextView h = UiKit.text(this, "أنواع النصوص", 17, true);
        h.setTextColor(UiKit.GREEN);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = UiKit.dp(this, 22);
        content.addView(h, lp);
        TextView note = UiKit.text(this, "اضغط النوع لحذفه، أو أضف نوعاً جديداً.", 13, false);
        note.setTextColor(Color.DKGRAY);
        content.addView(note);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = UiKit.horizontal(this);
        List<String> types = prefs.snippetTypes();
        for (String t : types) {
            TextView chip = UiKit.chip(this, t + "  ×", false);
            chip.setOnClickListener(v -> {
                if (types.size() <= 1) return;
                new AlertDialog.Builder(this).setMessage("حذف النوع «" + t + "»؟ النصوص الحالية تبقى كما هي.")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("حذف", (d, w) -> {
                        List<String> n = new ArrayList<>(types);
                        n.remove(t);
                        prefs.setSnippetTypes(n);
                        render();
                    }).show();
            });
            row.addView(chip, chipParams());
        }
        TextView add = UiKit.chip(this, "+ نوع", false);
        add.setTextColor(UiKit.GREEN);
        add.setOnClickListener(v -> askText("نوع جديد", "مثال: تذكير", "", name -> {
            List<String> n = new ArrayList<>(types);
            if (!n.contains(name)) n.add(name);
            prefs.setSnippetTypes(n);
            render();
        }));
        row.addView(add, chipParams());
        hs.addView(row);
        content.addView(hs, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 48)));
    }

    // ------------------------------------------------------------------ dialogs
    private interface TextResult { void done(String text); }

    private void askText(String title, String hint, String value, TextResult result) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        LinearLayout box = UiKit.vertical(this);
        UiKit.pad(box, this, 18, 6);
        box.addView(input);
        new AlertDialog.Builder(this).setTitle(title).setView(box)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ", (d, w) -> {
                String t = input.getText().toString().trim();
                if (!t.isEmpty()) result.done(t);
            }).show();
    }

    private void editDialog(Snippet s, SnippetCollection c) {
        if (s.isReadOnly()) {
            new AlertDialog.Builder(this).setTitle(s.title)
                .setMessage((s.isServer() ? "من الإدارة — لا يُعدَّل على الهاتف.\n\n" : "") + s.body
                    + (s.note != null && !s.note.isEmpty() ? "\n\nتذكير: " + s.note : ""))
                .setNegativeButton("إغلاق", null)
                .setPositiveButton(s.pinned ? "إلغاء التثبيت" : "تثبيت", (d, w) -> { db.setPinned(s.id, !s.pinned); render(); })
                .show();
            return;
        }
        LinearLayout box = UiKit.vertical(this);
        UiKit.pad(box, this, 18, 6);
        EditText title = new EditText(this);
        title.setHint("العنوان");
        title.setText(s.title);
        title.setSingleLine(true);
        box.addView(title);
        EditText body = new EditText(this);
        body.setHint("النص (يمكن استعمال {الاسم} {التاريخ} {المبلغ})");
        body.setText(s.body);
        body.setMinLines(4);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        box.addView(body);
        EditText shortcut = new EditText(this);
        shortcut.setHint("اختصار يُكتب بعد # (اختياري)");
        shortcut.setText(s.shortcut);
        shortcut.setSingleLine(true);
        box.addView(shortcut);
        TextView tl = UiKit.text(this, "النوع", 13, true);
        box.addView(tl);
        List<String> types = new ArrayList<>(prefs.snippetTypes());
        if (!types.contains(s.type)) types.add(s.type);
        Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));
        type.setSelection(Math.max(0, types.indexOf(s.type)));
        box.addView(type);
        TextView cl = UiKit.text(this, "المجموعة", 13, true);
        box.addView(cl);
        List<SnippetCollection> userCols = new ArrayList<>();
        List<String> colNames = new ArrayList<>();
        for (SnippetCollection x : db.collections(true)) {
            if (x.isReadOnly()) continue;
            userCols.add(x);
            colNames.add(x.name);
        }
        Spinner col = new Spinner(this);
        col.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, colNames));
        int ci = 0;
        for (int i = 0; i < userCols.size(); i++) if (userCols.get(i).id == s.collectionId) ci = i;
        col.setSelection(ci);
        box.addView(col);
        CheckBox pin = new CheckBox(this);
        pin.setText("مثبّت");
        pin.setChecked(s.pinned);
        box.addView(pin);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(s.id > 0 ? "تعديل النص" : "نص جديد").setView(sv)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ", (d, w) -> {
                s.title = title.getText().toString().trim();
                s.body = body.getText().toString();
                s.shortcut = shortcut.getText().toString().replace("#", "").trim();
                s.type = types.get(Math.max(0, type.getSelectedItemPosition()));
                if (!userCols.isEmpty()) s.collectionId = userCols.get(Math.max(0, col.getSelectedItemPosition())).id;
                s.pinned = pin.isChecked();
                if (s.title.isEmpty() || s.body.trim().isEmpty()) {
                    Toast.makeText(this, "العنوان والنص مطلوبان", Toast.LENGTH_SHORT).show();
                    return;
                }
                db.saveSnippet(s);
                selected = s.collectionId;
                render();
            });
        if (s.id > 0) b.setNeutralButton("حذف", (d, w) -> new AlertDialog.Builder(this)
            .setMessage("هل تريد حذف هذا النص؟")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حذف", (d2, w2) -> { db.deleteSnippet(s.id); render(); }).show());
        b.show();
    }

    private void orderCollectionsDialog() {
        DragList list = new DragList(this);
        for (SnippetCollection c : db.collections(true)) {
            if (c.isReadOnly()) continue;
            LinearLayout row = UiKit.horizontal(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UiKit.rounded(UiKit.SURFACE, 10, this));
            UiKit.pad(row, this, 8, 6);
            TextView handle = UiKit.text(this, "≡", 22, true);
            handle.setGravity(Gravity.CENTER);
            handle.setTextColor(Color.GRAY);
            row.addView(handle, new LinearLayout.LayoutParams(UiKit.dp(this, 34), UiKit.dp(this, 38)));
            TextView n = UiKit.text(this, c.archived ? c.name + " (مؤرشفة)" : c.name, 15, false);
            n.setTypeface(Typeface.DEFAULT);
            row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.setMargins(0, UiKit.dp(this, 3), 0, UiKit.dp(this, 3));
            row.setLayoutParams(rlp);
            list.addRow(row, handle, c.id);
        }
        UiKit.pad(list, this, 16, 8);
        ScrollView sv = new ScrollView(this);
        sv.addView(list);
        new AlertDialog.Builder(this).setTitle("ترتيب المجموعات").setView(sv)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ", (d, w) -> { db.reorderCollections(list.ids()); render(); })
            .show();
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, UiKit.dp(this, 36));
        lp.setMargins(UiKit.dp(this, 3), UiKit.dp(this, 4), UiKit.dp(this, 3), UiKit.dp(this, 4));
        return lp;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(UiKit.dp(this, 3), 0, UiKit.dp(this, 3), 0);
        return lp;
    }
}
