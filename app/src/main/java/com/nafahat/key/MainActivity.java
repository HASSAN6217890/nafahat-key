package com.nafahat.key;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One local-only settings and snippet management screen. */
public final class MainActivity extends Activity {
    private static final int IMPORT_CHAT=501, IMPORT_LIBRARY=502, EXPORT_LIBRARY=503;
    private static final Pattern WHATSAPP_LINE=Pattern.compile(
        "^\\[?\\d{1,2}[/.]\\d{1,2}[/.]\\d{2,4},?\\s+\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[AaPp]\\.?[Mm]\\.?)?\\]?\\s*(?:[-–]\\s*)?([^:]{1,90}):\\s*(.*)$");
    private KeyboardPrefs prefs;
    private LibraryDb db;
    private LearningModel model;
    private LinearLayout body;
    private CanvasKeyboardView livePreview;
    private boolean dark;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=new KeyboardPrefs(this);db=new LibraryDb(this);model=new LearningModel(this);
        render();
    }
    private void render(){
        dark=prefs.isDark();
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setBackgroundColor(dark?Color.rgb(23,28,31):Color.WHITE);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(15),dp(16),dp(15),dp(20));body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(body);setContentView(scroll);
        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.nafahat_original_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        body.addView(logo,new LinearLayout.LayoutParams(-1,dp(110)));
        heading("NafahatKey — كيبورد نفحات");
        description("الإصدار 0.3 — يعمل محلياً دون اتصال بالإنترنت.");
        action("تفعيل لوحة المفاتيح",()->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        action("اختيار كيبورد نفحات",()->{
            InputMethodManager manager=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(manager!=null)manager.showInputMethodPicker();
        });
        heading("الإعدادات والمظهر");
        for(String name:new String[]{"نفحات","داكن","فاتح","حسب نظام الهاتف"}){
            String value=name.equals("نفحات")?"nafahat":name.equals("داكن")?"dark":
                         name.equals("فاتح")?"light":"system";
            action((value.equals(prefs.theme())?"✓  ":"")+name,()->{prefs.setTheme(value);render();});
        }
        heading("حجم لوحة المفاتيح — معاينة حية");
        slider("الارتفاع",85,125,prefs.heightPercent(),v->{
            prefs.setHeightPercent(v);if(livePreview!=null)livePreview.setSettings(prefs);
        },"%");
        slider("حجم الحروف",18,36,prefs.fontSize(),v->{
            prefs.setFontSize(v);if(livePreview!=null)livePreview.setSettings(prefs);
        },"");
        toggle("صف الأرقام 0123456789",prefs.numberRow(),prefs::setNumberRow);
        toggle("الاهتزاز عند الضغط",prefs.vibrate(),prefs::setVibrate);
        slider("قوة الاهتزاز",0,3,prefs.vibrationStrength(),prefs::setVibrationStrength,"");
        toggle("الحرف الكبير التلقائي",prefs.autoCap(),prefs::setAutoCap);
        toggle("التصحيح التلقائي",prefs.autoCorrect(),prefs::setAutoCorrect);
        livePreview=new CanvasKeyboardView(this,prefs,null);
        livePreview.setSuggestions(java.util.Arrays.asList("نفحات","السلام","مرحباً"));
        body.addView(livePreview,new LinearLayout.LayoutParams(-1,-2));
        heading("جرّب الكتابة");
        EditText test=new EditText(this);test.setHint("جرّب العربية والإنكليزية والأرقام 0123456789");
        test.setTextSize(16);test.setMinLines(2);test.setTextColor(dark?Color.WHITE:Color.BLACK);
        body.addView(test,new LinearLayout.LayoutParams(-1,dp(95)));
        heading("علّم الكيبورد من كلامي");
        description("يُحلّل الملف على هاتفك فقط، ويتعلّم من رسائلك أنت بعد اختيار اسمك. لا يحتفظ الكيبورد بالملف.");
        action("استيراد محادثة واتساب (.txt)",()->openFile(IMPORT_CHAT,"text/plain"));
        action("لصق نص للتعلّم",this::pasteLearningDialog);
        action("مسح ما تعلّمه الكيبورد",()->new AlertDialog.Builder(this)
            .setTitle("مسح التعلّم").setMessage("هل تريد حذف الكلمات والثنائيات المتعلّمة من هذا الهاتف؟")
            .setNegativeButton("إلغاء",null).setPositiveButton("مسح",(d,w)->{
                model.clearLearning();toast("تم مسح التعلّم");}).show());
        heading("مكتبة النصوص والمجموعات");
        description("المجموعات والنصوص محفوظة على الهاتف، مع نسخة احتياطية محلية قبل التغييرات.");
        action("إضافة نص إلى مجموعة",()->snippetDialog(null,null));
        action("حفظ نص منسوخ",()->{
            ClipboardManager clip=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(clip==null||!clip.hasPrimaryClip()){toast("لا يوجد نص منسوخ");return;}
            ClipData data=clip.getPrimaryClip();
            if(data==null||data.getItemCount()==0)return;
            CharSequence text=data.getItemAt(0).coerceToText(this);
            snippetDialog(null,text==null?"":text.toString());
        });
        action("تصدير المكتبة والإعدادات (JSON)",()->createFile());
        action("استيراد مكتبة (JSON)",()->openFile(IMPORT_LIBRARY,"application/json"));
        List<Snippet> personal;
        try{personal=db.getPersonal();}catch(Exception e){description("تعذرت قراءة المكتبة. لم تُحذف أي بيانات.");return;}
        String last="";
        for(Snippet s:personal){
            if(!s.category.equals(last)){
                last=s.category;heading(last);
                String group=last;
                action("أرشفة مجموعة "+group,()->new AlertDialog.Builder(this)
                    .setMessage("أرشفة المجموعة وإخفاؤها من لوحة المفاتيح؟")
                    .setNegativeButton("إلغاء",null)
                    .setPositiveButton("أرشفة",(d,w)->{db.archiveCollection(group,true);render();}).show());
            }
            action(s.title+" — "+shorten(s.text,65),()->snippetDialog(s,null));
        }
        for(String archived:db.collectionNames(true)){
            action("استعادة المجموعة المؤرشفة: "+archived,()->{
                db.archiveCollection(archived,false);render();
            });
        }
        heading("النصوص الرسمية");
        for(Snippet s:BrandData.publicSnippets())
            action(s.title,()->copy(s.title,s.text));
        description("تُخزَّن النصوص المحلية دون مزامنة أو وصول للرسائل أو الأسماء من التطبيقات الأخرى.");
    }
    private String shorten(String t,int max){return t.length()<=max?t:t.substring(0,max)+"…";}
    private void snippetDialog(Snippet old,String preset){
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(15),dp(6),dp(15),dp(6));
        EditText group=new EditText(this);group.setHint("المجموعة (مثل: رحلة 7.1)");
        EditText title=new EditText(this);title.setHint("عنوان النص");
        EditText text=new EditText(this);text.setHint("النص المحفوظ");text.setMinLines(3);
        String[] typeNames={"رد","واتساب","إنستغرام قصير","أسعار","عنوان/روابط","أخرى"};
        Spinner type=new Spinner(this);
        type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,typeNames));
        if(old!=null){int index=java.util.Arrays.asList(typeNames).indexOf(old.type);if(index>=0)type.setSelection(index);}
        if(old!=null){group.setText(old.category);title.setText(old.title);text.setText(old.text);}
        if(preset!=null)text.setText(preset);
        fields.addView(group);fields.addView(title);fields.addView(type);fields.addView(text);
        AlertDialog.Builder box=new AlertDialog.Builder(this).setTitle(old==null?"إضافة نص":"تعديل نص")
            .setView(fields).setNegativeButton("إلغاء",null)
            .setPositiveButton("حفظ",(d,w)->{
                try{
                    if(old!=null)db.update(old.id,title.getText().toString(),text.getText().toString(),group.getText().toString(),type.getSelectedItem().toString());
                    else db.add(title.getText().toString(),text.getText().toString(),group.getText().toString(),type.getSelectedItem().toString());
                    render();
                }catch(Exception e){toast("لم يُحفظ النص. بقيت النسخة الاحتياطية.");}
            });
        if(old!=null)box.setNeutralButton("حذف",(d,w)->new AlertDialog.Builder(this)
            .setMessage("هل تريد حذف هذا النص؟").setNegativeButton("إلغاء",null)
            .setPositiveButton("حذف",(dialog,which)->{try{db.delete(old.id);render();}catch(Exception e){toast("تعذر الحذف");}}).show());
        box.show();
    }
    private void pasteLearningDialog(){
        EditText text=new EditText(this);text.setMinLines(5);text.setHint("الصق هنا نصوصك فقط");
        new AlertDialog.Builder(this).setTitle("التعلّم من نص مكتوب")
            .setView(text).setNegativeButton("إلغاء",null)
            .setPositiveButton("تعلّم",(d,w)->learnText(text.getText().toString())).show();
    }
    private void learnText(String text){
        if(text.trim().isEmpty()){toast("لم يُعثر على نص للتعلّم");return;}
        new Thread(()->{
            try{
                int count=model.learnText(text);
                runOnUiThread(()->toast("تعلّمتُ "+count+" كلمة"));
            }catch(Exception e){runOnUiThread(()->toast("تعذّر التعلّم، لم يُحفَظ الملف")); }
        },"Nafahat-Learn").start();
    }
    private void openFile(int request,String type){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(type);intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,request);
    }
    private void createFile(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"NafahatKey-library.json");
        startActivityForResult(intent,EXPORT_LIBRARY);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==EXPORT_LIBRARY){
            try(OutputStream output=getContentResolver().openOutputStream(uri)){
                if(output==null)throw new IllegalStateException();
                JSONObject json=db.exportJson();
                JSONObject options=new JSONObject();
                options.put("theme",prefs.theme());options.put("height",prefs.heightPercent());
                options.put("font",prefs.fontSize());options.put("numbers",prefs.numberRow());
                options.put("vibrate",prefs.vibrate());options.put("autoCap",prefs.autoCap());
                options.put("autoCorrect",prefs.autoCorrect());
                json.put("settings",options);
                output.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                toast("تم تصدير المكتبة");
            }catch(Exception e){toast("تعذّر تصدير الملف");}
            return;
        }
        try{
            byte[] bytes=readLimit(uri,6*1024*1024);
            String input=new String(bytes,StandardCharsets.UTF_8).replace("\uFEFF","");
            if(request==IMPORT_LIBRARY){
                JSONObject json=new JSONObject(input);
                db.importJson(json);
                JSONObject settings=json.optJSONObject("settings");
                if(settings!=null){
                    prefs.setTheme(settings.optString("theme","nafahat"));
                    prefs.setHeightPercent(settings.optInt("height",100));
                    prefs.setFontSize(settings.optInt("font",26));
                    prefs.setNumberRow(settings.optBoolean("numbers",true));
                    prefs.setVibrate(settings.optBoolean("vibrate",true));
                    prefs.setAutoCap(settings.optBoolean("autoCap",true));
                    prefs.setAutoCorrect(settings.optBoolean("autoCorrect",true));
                }
                toast("تم استيراد المكتبة");render();
            }else if(request==IMPORT_CHAT)parseChat(input);
        }catch(Exception e){toast("تعذّر قراءة الملف؛ لم تُحذَف البيانات الحالية");}
    }
    private byte[] readLimit(Uri uri,int maximum) throws Exception {
        try(InputStream input=getContentResolver().openInputStream(uri);
            ByteArrayOutputStream memory=new ByteArrayOutputStream()){
            if(input==null)throw new IllegalStateException();
            byte[] block=new byte[8192];int bytes,total=0;
            while((bytes=input.read(block))!=-1){
                total+=bytes;if(total>maximum)throw new IllegalArgumentException("File too large");
                memory.write(block,0,bytes);
            }
            return memory.toByteArray();
        }
    }
    private void parseChat(String text){
        Map<String,StringBuilder> bySender=new LinkedHashMap<>();
        String current=null;
        for(String line:text.split("\\r?\\n",-1)){
            Matcher item=WHATSAPP_LINE.matcher(line);
            if(item.matches()){
                current=item.group(1).trim();
                bySender.computeIfAbsent(current,k->new StringBuilder()).append(item.group(2)).append('\n');
            }else if(current!=null){
                bySender.get(current).append(line).append('\n');
            }
        }
        if(bySender.isEmpty()){toast("لم يُعثر على مرسلين في الملف. استخدم لصق النص بدلاً من ذلك.");return;}
        String[] names=bySender.keySet().toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("أي اسم هو أنت؟")
            .setSingleChoiceItems(names,-1,(dialog,which)->{
                dialog.dismiss();
                String selected=names[which];
                String ownWords=bySender.get(selected).toString();
                // No file path or content is saved; only aggregate token/bigram counts.
                learnText(ownWords);
            }).setNegativeButton("إلغاء",null).show();
    }
    private void heading(String text){
        TextView v=new TextView(this);v.setText(text);v.setTextSize(18);
        v.setTextColor(Color.rgb(200,131,56));v.setPadding(0,dp(15),0,dp(8));
        body.addView(v);
    }
    private void description(String text){
        TextView v=new TextView(this);v.setText(text);v.setTextSize(14);
        v.setTextColor(dark?Color.WHITE:Color.BLACK);v.setPadding(0,0,0,dp(7));
        body.addView(v);
    }
    private void action(String label,Runnable listener){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);
        b.setTextSize(14);b.setOnClickListener(v->listener.run());
        body.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private interface Value {void set(int n);}
    private void slider(String name,int min,int max,int value,Value setter,String suffix){
        TextView title=new TextView(this);title.setText(name+" — "+value+suffix);
        title.setTextColor(dark?Color.WHITE:Color.BLACK);body.addView(title);
        SeekBar seek=new SeekBar(this);seek.setMax(max-min);seek.setProgress(value-min);
        body.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar v,int progress,boolean user){
                title.setText(name+" — "+(min+progress)+suffix);
                if(user)setter.set(min+progress);
            }
            @Override public void onStartTrackingTouch(SeekBar v){}
            @Override public void onStopTrackingTouch(SeekBar v){}
        });
    }
    private interface Toggle {void set(boolean enabled);}
    private void toggle(String label,boolean checked,Toggle setter){
        Switch sw=new Switch(this);sw.setText(label);sw.setTextColor(dark?Color.WHITE:Color.BLACK);
        sw.setChecked(checked);sw.setTextSize(14);body.addView(sw);
        sw.setOnCheckedChangeListener((button,enabled)->{setter.set(enabled);if(livePreview!=null)livePreview.setSettings(prefs);});
    }
    private void copy(String title,String text){
        ClipboardManager clip=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clip!=null)clip.setPrimaryClip(ClipData.newPlainText(title,text));
        toast("تم نسخ النص");
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){if(db!=null)db.close();if(model!=null)model.close();super.onDestroy();}
}
