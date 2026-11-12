package com.nafahat.key;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One settings screen: activation, look with a live preview, typing options, learning, backup. */
public final class MainActivity extends Activity {
    private static final int REQ_CHAT = 11, REQ_EXPORT = 12, REQ_IMPORT = 13;

    private KeyboardPrefs prefs;
    private LinearLayout content;
    private KeyboardView preview;
    private CandidateStrip previewStrip;
    private TextView statusText;
    private TextView learnedText;
    private CheckBox includeDictionary;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean includeDictForImport;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = new KeyboardPrefs(this);
        render();
        worker.execute(() -> {
            LibraryDb.get(getApplicationContext());
            UserDict.get(getApplicationContext()).loadBlocking();
            main.post(this::updateLearnedCount);
        });
    }

    @Override protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);
        content = UiKit.vertical(this);
        UiKit.pad(content, this, 16, 16);
        scroll.addView(content);
        setContentView(scroll);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.nafahat_original_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("شعار نفحات للحج والعمرة");
        content.addView(logo, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 96)));
        TextView title = UiKit.text(this, "كيبورد نفحات", 22, true);
        title.setTextColor(UiKit.GREEN);
        title.setGravity(android.view.Gravity.CENTER);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = UiKit.text(this, "الإصدار " + versionName()
            + " • يعمل على هذا الجهاز فقط، بلا إنترنت، ولا يسجّل ما تكتبه.", 13, false);
        sub.setGravity(android.view.Gravity.CENTER);
        sub.setTextColor(Color.DKGRAY);
        content.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        // Activation
        section("التفعيل");
        statusText = UiKit.text(this, "", 13, false);
        content.addView(statusText);
        LinearLayout act = row();
        act.addView(UiKit.button(this, "تفعيل الكيبورد"), weight());
        act.addView(UiKit.secondary(this, "اختيار الكيبورد"), weight());
        act.getChildAt(0).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        act.getChildAt(1).setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        content.addView(act, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 46)));

        // Look + live preview
        section("المظهر والحجم");
        FrameLayout previewBox = new FrameLayout(this);
        previewBox.setBackground(UiKit.rounded(UiKit.SURFACE, 14, this));
        LinearLayout pcol = new LinearLayout(this);
        pcol.setOrientation(LinearLayout.VERTICAL);
        previewStrip = new CandidateStrip(this);
        List<CandidateStrip.Item> sample = new ArrayList<>();
        sample.add(new CandidateStrip.Item("مرحبا", CandidateStrip.SUGGESTION, null));
        sample.add(new CandidateStrip.Item("أهلاً", CandidateStrip.SUGGESTION, null));
        sample.add(new CandidateStrip.Item("السلام", CandidateStrip.SUGGESTION, null));
        previewStrip.setSuggestions(sample, true);
        pcol.addView(previewStrip, new LinearLayout.LayoutParams(-1, -2));
        preview = new KeyboardView(this);
        preview.setPreviewOnly(true);
        pcol.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        previewBox.addView(pcol, new FrameLayout.LayoutParams(-1, -2));
        content.addView(previewBox, new LinearLayout.LayoutParams(-1, -2));
        refreshPreview();

        LinearLayout themes = row();
        String[][] ids = {{"nafahat", "نفحات"}, {"dark", "داكن"}, {"light", "فاتح"}, {"system", "حسب الهاتف"}};
        for (String[] t : ids) {
            TextView chip = UiKit.chip(this, t[1], t[0].equals(prefs.theme()));
            chip.setOnClickListener(v -> { prefs.setTheme(t[0]); rebuild(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, UiKit.dp(this, 38), 1f);
            lp.setMargins(UiKit.dp(this, 3), UiKit.dp(this, 8), UiKit.dp(this, 3), 0);
            themes.addView(chip, lp);
        }
        content.addView(themes);
        slider("ارتفاع لوحة المفاتيح", 85, 125, prefs.heightPercent(), "%", v -> {
            prefs.setHeightPercent(v);
            refreshPreview();
        });
        slider("حجم خط الحروف", 85, 125, prefs.fontPercent(), "%", v -> {
            prefs.setFontPercent(v);
            refreshPreview();
        });
        toggle("صف الأرقام 1 2 3 فوق الحروف", prefs.numberRow(), v -> { prefs.setNumberRow(v); refreshPreview(); });

        // Typing
        section("الكتابة");
        toggle("الاهتزاز عند الضغط", prefs.vibrate(), prefs::setVibrate);
        slider("قوة الاهتزاز (1 خفيف، 3 قوي)", 1, 3, prefs.vibrationLevel(), "", prefs::setVibrationLevel);
        toggle("الحرف الكبير التلقائي في بداية الجملة (الإنكليزية)", prefs.autoCap(), prefs::setAutoCap);
        toggle("التصحيح التلقائي للكلمات", prefs.autoCorrect(), prefs::setAutoCorrect);
        EditText test = new EditText(this);
        test.setHint("جرّب الكتابة هنا: طواف، زيارة، إن شاء الله، 0123456789");
        test.setTextSize(16);
        test.setMinLines(2);
        test.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        content.addView(test, new LinearLayout.LayoutParams(-1, -2));

        // Learning
        section("علّم الكيبورد من كلامي");
        note("اختر ملف تصدير محادثة واتساب (ملف نصي أو مضغوط)، أو الصق نصاً. يتعلّم الكيبورد من رسائلك أنت فقط، "
            + "وتجري المعالجة على هذا الجهاز، ولا يُحفظ الملف بعد المعالجة.");
        learnedText = UiKit.text(this, "", 13, false);
        learnedText.setTextColor(UiKit.GREEN);
        content.addView(learnedText);
        LinearLayout learn = row();
        learn.addView(UiKit.button(this, "اختيار ملف محادثة"), weight());
        learn.addView(UiKit.secondary(this, "لصق نص"), weight());
        learn.getChildAt(0).setOnClickListener(v -> pickChatFile());
        learn.getChildAt(1).setOnClickListener(v -> pasteTextDialog());
        content.addView(learn, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 46)));
        Button clear = UiKit.secondary(this, "مسح ما تعلّمه الكيبورد");
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setMessage("سيُحذف كل ما تعلّمه الكيبورد من كتابتك على هذا الجهاز. هل تريد المتابعة؟")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("مسح", (d, w) -> {
                UserDict.get(this).clearAll();
                updateLearnedCount();
                Toast.makeText(this, "تم مسح التعلّم", Toast.LENGTH_SHORT).show();
            }).show());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, UiKit.dp(this, 44));
        clp.topMargin = UiKit.dp(this, 8);
        content.addView(clear, clp);

        // Library
        section("المكتبة");
        note("النصوص الجاهزة ومجموعات الرحلات: إضافة وتعديل وترتيب بالسحب وأرشفة الرحلات المنتهية.");
        Button lib = UiKit.button(this, "إدارة النصوص والمجموعات");
        lib.setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        content.addView(lib, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 46)));

        // Backup
        section("التصدير والاستيراد");
        note("ملف محلي يحوي المجموعات والنصوص والإعدادات، لنقلها إلى هاتف آخر أو لموظف جديد.");
        includeDictionary = new CheckBox(this);
        includeDictionary.setText("تضمين القاموس الشخصي (ما تعلّمه الكيبورد)");
        includeDictionary.setTextSize(14);
        content.addView(includeDictionary);
        LinearLayout backup = row();
        backup.addView(UiKit.button(this, "تصدير إلى ملف"), weight());
        backup.addView(UiKit.secondary(this, "استيراد من ملف"), weight());
        backup.getChildAt(0).setOnClickListener(v -> startExport());
        backup.getChildAt(1).setOnClickListener(v -> startImport());
        content.addView(backup, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 46)));

        section("الخصوصية");
        note("لا يملك الكيبورد صلاحية الإنترنت، ولا يرسل شيئاً خارج الجهاز. في حقول كلمات المرور والحقول الحساسة "
            + "لا تظهر معاينة ولا اقتراحات ولا مكتبة، ولا يتعلّم شيئاً. لا يقرأ الحافظة إلا عند ضغط «حفظ المنسوخ».");
        updateStatus();
        updateLearnedCount();
    }

    private void rebuild() { render(); }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0.3";
        }
    }

    private void refreshPreview() {
        if (preview == null) return;
        Theme t = prefs.resolveTheme(this);
        Layout l = Layouts.arabic(prefs.numberRow());
        preview.setTheme(t);
        preview.setLayout(l, false);
        preview.setFontPercent(prefs.fontPercent());
        preview.setKeyboardHeight(KeyboardView.computeHeight(this, l, prefs.heightPercent()));
        previewStrip.setTheme(t);
    }

    private void updateStatus() {
        if (statusText == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        boolean enabled = false;
        if (imm != null) {
            for (InputMethodInfo i : imm.getEnabledInputMethodList())
                if (i.getPackageName().equals(getPackageName())) enabled = true;
        }
        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean selected = current != null && current.startsWith(getPackageName() + "/");
        statusText.setText(selected ? "الحالة: كيبورد نفحات مفعّل ومختار للكتابة."
            : enabled ? "الحالة: مفعّل، لكنه غير مختار. اضغط «اختيار الكيبورد»."
            : "الحالة: غير مفعّل بعد. اضغط «تفعيل الكيبورد» ثم فعّل «كيبورد نفحات».");
        statusText.setTextColor(selected ? UiKit.GREEN : UiKit.GOLD);
    }

    private void updateLearnedCount() {
        if (learnedText == null) return;
        int n = UserDict.get(this).size();
        learnedText.setText(n == 0 ? "لم يتعلّم الكيبورد كلمات خاصة بك بعد." : "القاموس الشخصي: " + n + " كلمة.");
    }

    // ------------------------------------------------------------------ learning
    private void pickChatFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "application/zip", "application/x-zip-compressed", "text/*"});
        startActivityForResult(i, REQ_CHAT);
    }

    private void pasteTextDialog() {
        EditText input = new EditText(this);
        input.setMinLines(5);
        input.setHint("الصق هنا نصاً كتبته أنت");
        input.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        new AlertDialog.Builder(this).setTitle("لصق نص للتعلّم").setView(input)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تعلّم", (d, w) -> {
                String text = input.getText().toString();
                if (text.trim().isEmpty()) return;
                worker.execute(() -> {
                    ChatLearner.Parsed p = ChatLearner.parse(text);
                    main.post(() -> afterParse(p));
                });
            }).show();
    }

    private void afterParse(ChatLearner.Parsed p) {
        if (p.whatsapp && p.senders.size() > 1) {
            List<String> names = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (Map.Entry<String, Integer> e : p.senders.entrySet()) {
                names.add(e.getKey());
                labels.add(e.getKey() + "  (" + e.getValue() + " رسالة)");
            }
            new AlertDialog.Builder(this).setTitle("أي اسم هو أنت؟")
                .setItems(labels.toArray(new String[0]), (d, which) -> runLearn(p, names.get(which)))
                .setNegativeButton("إلغاء", null).show();
        } else if (p.whatsapp && p.senders.size() == 1) {
            runLearn(p, p.senders.keySet().iterator().next());
        } else {
            runLearn(p, null);
        }
    }

    private void runLearn(ChatLearner.Parsed p, String sender) {
        Toast.makeText(this, "جارٍ التعلّم…", Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            UserDict.get(getApplicationContext()).loadBlocking();
            int n = ChatLearner.learn(getApplicationContext(), p, sender);
            main.post(() -> {
                updateLearnedCount();
                new AlertDialog.Builder(this).setMessage("تعلّمتُ " + n + " كلمة من كلامك.")
                    .setPositiveButton("حسناً", null).show();
            });
        });
    }

    // ------------------------------------------------------------------ backup
    private void startExport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "nafahatkey-backup.json");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void startImport() {
        includeDictForImport = includeDictionary != null && includeDictionary.isChecked();
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_CHAT) {
            Toast.makeText(this, "جارٍ قراءة المحادثة…", Toast.LENGTH_SHORT).show();
            worker.execute(() -> {
                try {
                    String text = ChatLearner.read(getContentResolver(), uri);
                    ChatLearner.Parsed p = ChatLearner.parse(text);
                    main.post(() -> afterParse(p));
                } catch (Exception e) {
                    main.post(() -> Toast.makeText(this, "تعذّرت قراءة الملف", Toast.LENGTH_LONG).show());
                }
            });
        } else if (requestCode == REQ_EXPORT) {
            boolean dict = includeDictionary != null && includeDictionary.isChecked();
            worker.execute(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IllegalStateException();
                    out.write(BackupIO.export(getApplicationContext(), dict).getBytes(StandardCharsets.UTF_8));
                    main.post(() -> Toast.makeText(this, "تم التصدير", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    main.post(() -> Toast.makeText(this, "تعذّر التصدير", Toast.LENGTH_LONG).show());
                }
            });
        } else if (requestCode == REQ_IMPORT) {
            boolean dict = includeDictForImport;
            worker.execute(() -> {
                try {
                    String json = ChatLearner.read(getContentResolver(), uri);
                    String msg = BackupIO.importJson(getApplicationContext(), json, dict);
                    main.post(() -> {
                        prefs = new KeyboardPrefs(this);
                        rebuild();
                        new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("حسناً", null).show();
                    });
                } catch (Exception e) {
                    main.post(() -> Toast.makeText(this, "الملف ليس نسخة احتياطية صالحة لكيبورد نفحات", Toast.LENGTH_LONG).show());
                }
            });
        }
    }

    // ------------------------------------------------------------------ small builders
    private void section(String title) {
        TextView t = UiKit.text(this, title, 17, true);
        t.setTextColor(UiKit.GREEN);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = UiKit.dp(this, 20);
        lp.bottomMargin = UiKit.dp(this, 6);
        content.addView(t, lp);
    }

    private void note(String text) {
        TextView t = UiKit.text(this, text, 13, false);
        t.setTextColor(Color.DKGRAY);
        t.setPadding(0, 0, 0, UiKit.dp(this, 6));
        content.addView(t);
    }

    private LinearLayout row() { return UiKit.horizontal(this); }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(UiKit.dp(this, 3), 0, UiKit.dp(this, 3), 0);
        return lp;
    }

    private interface IntSaver { void save(int v); }
    private interface BoolSaver { void save(boolean v); }

    private void slider(String label, int min, int max, int value, String suffix, IntSaver saver) {
        TextView t = UiKit.text(this, label + ": " + value + suffix, 14, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = UiKit.dp(this, 10);
        content.addView(t, tl);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(value - min);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        content.addView(bar, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 36)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                t.setText(label + ": " + (min + p) + suffix);
                if (fromUser) saver.save(min + p);
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { saver.save(min + b.getProgress()); }
        });
    }

    private void toggle(String label, boolean checked, BoolSaver saver) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(14);
        s.setChecked(checked);
        s.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        s.setOnCheckedChangeListener((b, v) -> saver.save(v));
        content.addView(s, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 46)));
    }
}
