package com.nafahat.key;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Native Android companion UI: setup, test field, public library and personal snippets. */
public final class MainActivity extends Activity {
    private SnippetStore store;
    private LinearLayout content;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = new SnippetStore(this);
        render();
    }
    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);
        content = UiKit.vertical(this);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        UiKit.pad(content,this,17,18);
        scroll.addView(content);
        setContentView(scroll);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.nafahat_original_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(logo,new LinearLayout.LayoutParams(-1,UiKit.dp(this,142)));
        addTitle("NafahatKey — كيبورد نفحات",21);
        addDescription("إصدار محلي أولي 0.1 • بدون إنترنت • بدون جمع بيانات الكتابة");
        addTitle("تفعيل لوحة المفاتيح",18);
        addAction("1 — تفعيل NafahatKey من إعدادات أندرويد", () -> {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        });
        addAction("2 — اختيار NafahatKey كلوحة كتابة", () -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        addTitle("جرّب الكتابة",18);
        EditText test = new EditText(this);
        test.setHint("اكتب هنا بالعربية أو English، ثم جرّب زر 123 والحافظة");
        test.setTextSize(16);
        test.setSingleLine(false);
        test.setMinLines(2);
        test.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        content.addView(test,new LinearLayout.LayoutParams(-1,UiKit.dp(this,90)));
        addTitle("معلومات نفحات العامة",19);
        addDescription("البيانات المعتمدة للنشر فقط؛ الضغط على «نسخ» ينسخ النص إلى الحافظة.");
        for(Snippet item: BrandData.publicSnippets()) {
            if (item.category.equals("معلومات نفحات")) addSnippet(item);
        }
        addTitle("الردود الجاهزة",19);
        for(Snippet item: BrandData.publicSnippets()) {
            if (item.category.equals("ردود جاهزة")) addSnippet(item);
        }
        addTitle("مكتبتي الشخصية",19);
        addDescription("نصوص تُضاف باختيارك فقط ومحفوظة على هذا الهاتف، ولا تتم مزامنتها مع الفريق.");
        addAction("+ إضافة نص محفوظ", this::addPersonalDialog);
        List<Snippet> personal = store.getPersonal();
        if(personal.isEmpty()) addDescription("لم تُضف نصوصاً شخصية بعد.");
        for(Snippet item:personal) addSnippet(item);
        addDescription("النسخة الحالية: لا مزامنة فريق، ولا صوتيات أو صور داخل الحافظة، ولا اتصال مباشر بواتساب. هذه مراحل لاحقة.");
    }
    private void addTitle(String text,int sp) {
        TextView view=UiKit.text(this,text,sp,true);view.setTextColor(UiKit.GREEN);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=UiKit.dp(this,16);lp.bottomMargin=UiKit.dp(this,8);
        content.addView(view,lp);
    }
    private void addDescription(String value) {
        TextView t=UiKit.text(this,value,13,false);t.setTextColor(Color.DKGRAY);
        t.setPadding(0,UiKit.dp(this,5),0,UiKit.dp(this,5));
        content.addView(t);
    }
    private void addAction(String text,Runnable action) {
        Button button=UiKit.button(this,text);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,UiKit.dp(this,46));
        lp.bottomMargin=UiKit.dp(this,8);
        content.addView(button,lp);button.setOnClickListener(v->action.run());
    }
    private void addSnippet(Snippet item) {
        LinearLayout card=UiKit.vertical(this);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setBackground(UiKit.rounded(UiKit.SURFACE,13,this));
        UiKit.pad(card,this,12,10);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=UiKit.dp(this,9);
        content.addView(card,p);
        TextView title=UiKit.text(this,item.title,15,true);card.addView(title);
        TextView text=UiKit.text(this,item.text,13,false);
        text.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        text.setTextIsSelectable(true);
        text.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,7));
        card.addView(text);
        LinearLayout buttons=UiKit.horizontal(this);
        buttons.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.addView(buttons);
        Button copy=UiKit.button(this,"نسخ");
        LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,UiKit.dp(this,40),1);
        b.rightMargin=UiKit.dp(this,4);buttons.addView(copy,b);
        copy.setOnClickListener(v->copyText(item.title,item.text));
        if(!item.official) {
            Button delete=UiKit.button(this,"حذف النص");
            delete.setBackground(UiKit.rounded(Color.rgb(130,46,46),10,this));
            buttons.addView(delete,new LinearLayout.LayoutParams(0,UiKit.dp(this,40),1));
            delete.setOnClickListener(v->new AlertDialog.Builder(this)
                .setMessage("حذف النص الشخصي؟")
                .setNegativeButton("إلغاء",null)
                .setPositiveButton("حذف",(d,w)->{store.delete(item.id);render();}).show());
        }
    }
    private void copyText(String title,String value) {
        ClipboardManager mgr=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(mgr!=null) mgr.setPrimaryClip(ClipData.newPlainText(title,value));
        Toast.makeText(this,"تم نسخ النص",Toast.LENGTH_SHORT).show();
    }
    private void addPersonalDialog() {
        LinearLayout fields=UiKit.vertical(this);
        fields.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        UiKit.pad(fields,this,18,8);
        EditText group=new EditText(this);group.setHint("التبويب (مثال: رحلة خاصة)");
        EditText title=new EditText(this);title.setHint("عنوان النص");
        EditText text=new EditText(this);text.setHint("النص الجاهز");text.setMinLines(3);
        fields.addView(group);fields.addView(title);fields.addView(text);
        new AlertDialog.Builder(this).setTitle("إضافة نص إلى الحافظة الشخصية")
            .setView(fields).setNegativeButton("إلغاء",null)
            .setPositiveButton("حفظ",(dialog,which)->{
                if(title.getText().toString().trim().isEmpty() || text.getText().toString().trim().isEmpty()) {
                    Toast.makeText(this,"العنوان والنص مطلوبان",Toast.LENGTH_SHORT).show();return;
                }
                store.add(title.getText().toString(),text.getText().toString(),group.getText().toString());
                render();
            }).show();
    }
}
