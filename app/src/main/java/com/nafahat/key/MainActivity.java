package com.nafahat.key;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Local settings, public business snippets and explicitly saved personal snippets. */
public final class MainActivity extends Activity {
    private SnippetStore store;
    private KeyboardPrefs preferences;
    private LinearLayout content;
    private boolean dark;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        store=new SnippetStore(this);
        preferences=new KeyboardPrefs(this);
        render();
    }
    private void render() {
        dark=preferences.isDark();
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(dark?UiKit.INK:Color.WHITE);
        content=UiKit.vertical(this);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        UiKit.pad(content,this,16,17);
        scroll.addView(content);
        setContentView(scroll);

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.nafahat_original_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(logo,new LinearLayout.LayoutParams(-1,UiKit.dp(this,120)));
        addTitle("NafahatKey — كيبورد نفحات",21);
        addDescription("الإصدار 0.2 • يعمل محلياً دون إنترنت ولا يسجّل ما تكتبه.");
        addTitle("تفعيل لوحة المفاتيح",17);
        addAction("تفعيل كيبورد نفحات من إعدادات أندرويد",()->{
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        });
        addAction("اختيار كيبورد نفحات للكتابة",()->{
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null)imm.showInputMethodPicker();
        });
        addTitle("تخصيص لوحة المفاتيح",18);
        addDescription("اختر المظهر وارتفاع المفاتيح وحجم الحروف. تُحفظ الإعدادات على هذا الجهاز.");
        addThemeButtons();
        addSlider("ارتفاع لوحة المفاتيح",80,140,preferences.heightPercent(),v->preferences.setHeightPercent(v),"%");
        addSlider("حجم خط المفاتيح",17,30,preferences.fontSize(),v->preferences.setFontSize(v),"");
        addToggle("إظهار صف الأرقام فوق الحروف",preferences.numberRow(),v->preferences.setNumberRow(v));
        addToggle("اهتزاز خفيف عند الضغط",preferences.vibrate(),v->preferences.setVibrate(v));
        addDescription("تظل الأرقام 0123456789 في جميع الأوضاع. ويمكن الوصول إليها دائماً من تبويب 123.");
        addTitle("جرّب الكتابة",18);
        EditText test=new EditText(this);
        test.setHint("جرّب: دمشق، طواف، ذِكر، أإآ، 0123456789");
        test.setHintTextColor(dark?Color.LTGRAY:Color.GRAY);
        test.setTextColor(dark?Color.WHITE:UiKit.INK);
        test.setTextSize(16);
        test.setSingleLine(false);
        test.setMinLines(2);
        test.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        content.addView(test,new LinearLayout.LayoutParams(-1,UiKit.dp(this,90)));

        addTitle("معلومات نفحات العامة",19);
        addDescription("معلومات عامة معتمدة للنشر فقط. اضغط البطاقة للنسخ.");
        for(Snippet item:BrandData.publicSnippets())
            if(item.category.equals("معلومات نفحات"))addSnippet(item);
        addTitle("الردود الجاهزة",19);
        for(Snippet item:BrandData.publicSnippets())
            if(item.category.equals("ردود جاهزة"))addSnippet(item);
        addTitle("مكتبتي الشخصية",19);
        addDescription("نصوصك محفوظة محلياً فقط؛ لا تُشارك مع الفريق تلقائياً.");
        addAction("إضافة نص شخصي",this::addPersonalDialog);
        List<Snippet> saved=store.getPersonal();
        if(saved.isEmpty())addDescription("لم تُضف نصوصاً شخصية بعد.");
        for(Snippet item:saved)addSnippet(item);
        addDescription("لا توجد في هذا الإصدار مزامنة فريق أو تسجيلات صوتية أو إرسال تلقائي عبر واتساب.");
    }
    private void addTitle(String title,int size) {
        TextView t=UiKit.text(this,title,size,true);
        t.setTextColor(dark?UiKit.GOLD:UiKit.GREEN);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=UiKit.dp(this,15);lp.bottomMargin=UiKit.dp(this,8);
        content.addView(t,lp);
    }
    private void addDescription(String value) {
        TextView t=UiKit.text(this,value,13,false);
        t.setTextColor(dark?Color.WHITE:Color.DKGRAY);
        t.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,5));
        content.addView(t);
    }
    private void addAction(String label,Runnable action) {
        Button b=UiKit.button(this,label);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,UiKit.dp(this,44));
        lp.bottomMargin=UiKit.dp(this,7);
        content.addView(b,lp);b.setOnClickListener(v->action.run());
    }
    private void addThemeButtons() {
        LinearLayout row=UiKit.horizontal(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.addView(row,new LinearLayout.LayoutParams(-1,UiKit.dp(this,48)));
        addThemeButton(row,"نفحات","nafahat");
        addThemeButton(row,"فاتح","light");
        addThemeButton(row,"داكن","dark");
    }
    private void addThemeButton(LinearLayout row,String label,String value) {
        Button b=UiKit.button(this,label);
        int background=value.equals(preferences.theme())?UiKit.GOLD:UiKit.GREEN;
        b.setBackground(UiKit.rounded(background,9,this));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,UiKit.dp(this,43),1f);
        lp.setMargins(UiKit.dp(this,2),0,UiKit.dp(this,2),0);
        row.addView(b,lp);
        b.setOnClickListener(v->{preferences.setTheme(value);render();});
    }
    private interface ValueSaver {void save(int value);}
    private void addSlider(String label,int min,int max,int current,ValueSaver saver,String suffix) {
        TextView value=UiKit.text(this,label+" — "+current+suffix,14,true);
        value.setTextColor(dark?Color.WHITE:UiKit.INK);
        content.addView(value,new LinearLayout.LayoutParams(-1,-2));
        SeekBar bar=new SeekBar(this);
        bar.setMax(max-min);bar.setProgress(current-min);
        content.addView(bar,new LinearLayout.LayoutParams(-1,UiKit.dp(this,42)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b,int p,boolean fromUser) {
                value.setText(label+" — "+(min+p)+suffix);
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){saver.save(min+b.getProgress());}
        });
    }
    private interface ToggleSaver {void save(boolean enabled);}
    private void addToggle(String label,boolean checked,ToggleSaver saver) {
        Switch toggle=new Switch(this);
        toggle.setText(label);toggle.setTextSize(14);
        toggle.setTextColor(dark?Color.WHITE:UiKit.INK);
        toggle.setChecked(checked);
        toggle.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,UiKit.dp(this,44));
        content.addView(toggle,lp);
        toggle.setOnCheckedChangeListener((button,enabled)->saver.save(enabled));
    }
    private void addSnippet(Snippet item) {
        LinearLayout card=UiKit.horizontal(this);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setBackground(UiKit.rounded(dark?Color.rgb(47,53,53):UiKit.SURFACE,11,this));
        UiKit.pad(card,this,11,9);
        LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);
        outer.bottomMargin=UiKit.dp(this,8);
        content.addView(card,outer);
        LinearLayout info=UiKit.vertical(this);
        info.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=UiKit.text(this,item.title,15,true);
        title.setTextColor(dark?Color.WHITE:UiKit.INK);
        info.addView(title);
        TextView body=UiKit.text(this,item.text,13,false);
        body.setTextColor(dark?Color.WHITE:UiKit.INK);
        body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        if(item.title.contains("اتصال")||item.title.contains("واتساب"))
            body.setTextDirection(View.TEXT_DIRECTION_LTR);
        body.setMaxLines(3);body.setPadding(0,UiKit.dp(this,4),0,0);
        info.addView(body);
        card.setOnClickListener(v->copyText(item.title,item.text));
        Button copy=UiKit.button(this,"⧉");
        copy.setTextSize(20);
        copy.setContentDescription("نسخ "+item.title);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(UiKit.dp(this,40),UiKit.dp(this,40));
        cp.setMargins(UiKit.dp(this,7),0,0,0);
        card.addView(copy,cp);
        copy.setOnClickListener(v->copyText(item.title,item.text));
        if(!item.official) {
            Button delete=UiKit.button(this,"×");
            delete.setContentDescription("حذف "+item.title);
            delete.setBackground(UiKit.rounded(UiKit.INK,9,this));
            LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(UiKit.dp(this,35),UiKit.dp(this,40));
            dp.setMargins(UiKit.dp(this,6),0,0,0);
            card.addView(delete,dp);
            delete.setOnClickListener(v->new AlertDialog.Builder(this)
                .setMessage("هل تريد حذف هذا النص الشخصي؟")
                .setNegativeButton("إلغاء",null)
                .setPositiveButton("حذف",(d,w)->{store.delete(item.id);render();}).show());
        }
    }
    private void copyText(String title,String value) {
        ClipboardManager mgr=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(mgr!=null)mgr.setPrimaryClip(ClipData.newPlainText(title,value));
        Toast.makeText(this,"تم النسخ",Toast.LENGTH_SHORT).show();
    }
    private void addPersonalDialog() {
        LinearLayout fields=UiKit.vertical(this);
        fields.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        UiKit.pad(fields,this,18,8);
        EditText group=new EditText(this);group.setHint("التبويب (مثل: رحلة خاصة)");
        EditText title=new EditText(this);title.setHint("عنوان النص");
        EditText text=new EditText(this);text.setHint("النص الجاهز");text.setMinLines(3);
        fields.addView(group);fields.addView(title);fields.addView(text);
        new AlertDialog.Builder(this).setTitle("إضافة نص إلى الحافظة الشخصية")
            .setView(fields).setNegativeButton("إلغاء",null)
            .setPositiveButton("حفظ",(d,w)->{
                if(title.getText().toString().trim().isEmpty()||
                    text.getText().toString().trim().isEmpty()){
                    Toast.makeText(this,"العنوان والنص مطلوبان",Toast.LENGTH_SHORT).show();return;
                }
                store.add(title.getText().toString(),text.getText().toString(),group.getText().toString());
                render();
            }).show();
    }
}
