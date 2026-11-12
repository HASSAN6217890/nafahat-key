package com.nafahat.key;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import java.util.ArrayList;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Functional native input method. Typing never leaves this process. */
public final class NafahatKeyboardService extends InputMethodService {
    private enum Mode { LETTERS, SYMBOLS, LIBRARY }
    private Mode mode = Mode.LETTERS;
    private String category = "معلومات نفحات";
    private boolean arabic = true;
    private boolean shift = false;
    private boolean sensitive = false;
    private LinearLayout root;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable deletionTask;
    private boolean didRepeat;
    private final int KEY_BG = Color.rgb(255,255,255);
    private final int KEY_SPECIAL = Color.rgb(221,233,232);
    private final int BACK_BG = Color.rgb(206,221,219);

    @Override public View onCreateInputView() {
        root = UiKit.vertical(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.setBackgroundColor(BACK_BG);
        UiKit.pad(root,this,3,4);
        rebuild();
        return root;
    }
    @Override public void onStartInput(EditorInfo info,boolean restarting) {
        super.onStartInput(info,restarting);
        mode=Mode.LETTERS;shift=false;
        sensitive=isSensitive(info);
        if(root!=null) rebuild();
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting) {
        super.onStartInputView(info,restarting);
        sensitive=isSensitive(info);
        if(root!=null) rebuild();
    }
    @Override public void onFinishInputView(boolean finishingInput) {
        stopDelete();
        super.onFinishInputView(finishingInput);
    }
    @Override public void onDestroy() { stopDelete();super.onDestroy(); }

    private boolean isSensitive(EditorInfo editor) {
        if(editor==null) return false;
        int cls=editor.inputType & InputType.TYPE_MASK_CLASS;
        int variation=editor.inputType & InputType.TYPE_MASK_VARIATION;
        if(cls==InputType.TYPE_CLASS_TEXT) {
            return variation==InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        }
        return cls==InputType.TYPE_CLASS_NUMBER && variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    private void rebuild() {
        if(root==null) return;
        root.removeAllViews();
        addToolbar();
        if(mode==Mode.LIBRARY && !sensitive) addLibrary();
        else if(mode==Mode.SYMBOLS) addSymbols();
        else addLetters();
    }
    private void addToolbar() {
        LinearLayout row=UiKit.horizontal(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(row,new LinearLayout.LayoutParams(-1,UiKit.dp(this,46)));
        addToolbarKey(row,"N",1,()->{});
        addToolbarKey(row,arabic?"AR":"EN",1,()->{arabic=!arabic;mode=Mode.LETTERS;rebuild();});
        addToolbarKey(row,mode==Mode.SYMBOLS?"أبت / ABC":"123",1.25f,()->{mode=mode==Mode.SYMBOLS?Mode.LETTERS:Mode.SYMBOLS;rebuild();});
        if(!sensitive) addToolbarKey(row,"▤ الحافظة",2.1f,()->{mode=mode==Mode.LIBRARY?Mode.LETTERS:Mode.LIBRARY;rebuild();});
        addToolbarKey(row,"⚙",0.8f,()->{
            android.content.Intent intent=new android.content.Intent(this,MainActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
    }
    private void addToolbarKey(LinearLayout row,String label,float weight,Runnable run) {
        Button btn=makeKey(label,true);
        btn.setTextSize(13);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,UiKit.dp(this,40),weight);
        lp.setMargins(UiKit.dp(this,2),0,UiKit.dp(this,2),0);
        row.addView(btn,lp);
        btn.setOnClickListener(v->run.run());
    }
    private void addLetters() {
        if(arabic) {
            addRow(new String[]{"ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج"},47);
            addRow(new String[]{"ش","س","ي","ب","ل","ا","ت","ن","م","ك"},47);
            addRow(new String[]{"⇧","ئ","ء","ؤ","ر","لا","ى","ة","و","ز","ظ","⌫"},47);
            addSpaceRow(new String[]{"123","EN","،","المسافة",".","↵"},53);
        } else {
            addRow(new String[]{"q","w","e","r","t","y","u","i","o","p"},47);
            addRow(new String[]{"a","s","d","f","g","h","j","k","l"},47);
            addRow(new String[]{"⇧","z","x","c","v","b","n","m","⌫"},47);
            addSpaceRow(new String[]{"123","عربي",",","space",".","↵"},53);
        }
    }
    private void addRow(String[] keys,int height) {
        LinearLayout row=UiKit.horizontal(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(row,new LinearLayout.LayoutParams(-1,UiKit.dp(this,height)));
        for(String key:keys) addKeyToRow(row,key,1f,height-4);
    }
    private void addSpaceRow(String[] keys,int height) {
        LinearLayout row=UiKit.horizontal(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(row,new LinearLayout.LayoutParams(-1,UiKit.dp(this,height)));
        for(String key:keys) addKeyToRow(row,key,key.equals("المسافة")||key.equals("space")?4f:key.equals("↵")?1.25f:1f,height-4);
    }
    private void addKeyToRow(LinearLayout row,String key,float weight,int height) {
        boolean special=key.equals("⇧")||key.equals("⌫")||key.equals("123")||key.equals("↵")
            || key.equals("عربي")||key.equals("EN")||key.equals("ABC")||key.equals("أبت");
        Button btn=makeKey(key,special);
        btn.setTextSize(key.length()>3?13:19);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,UiKit.dp(this,height),weight);
        lp.setMargins(UiKit.dp(this,1.5f),UiKit.dp(this,2),UiKit.dp(this,1.5f),0);
        row.addView(btn,lp);
        if(key.equals("⌫")) installBackspace(btn);
        else if(key.equals("المسافة") || key.equals("space")) installSpaceGestures(btn);
        else btn.setOnClickListener(v->keyPressed(key));
    }
    private Button makeKey(String key,boolean special) {
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setText(key);
        b.setTextColor(UiKit.INK);
        b.setTypeface(Typeface.DEFAULT,special?Typeface.BOLD:Typeface.NORMAL);
        b.setBackground(UiKit.rounded(special?KEY_SPECIAL:KEY_BG,6,this));
        b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(0,0,0,0);
        return b;
    }
    private void keyPressed(String key) {
        switch(key) {
            case "123": mode=Mode.SYMBOLS;rebuild();return;
            case "ABC":case "أبت": mode=Mode.LETTERS;rebuild();return;
            case "EN": arabic=false;mode=Mode.LETTERS;rebuild();return;
            case "عربي":arabic=true;mode=Mode.LETTERS;rebuild();return;
            case "⇧":shift=!shift;rebuild();return;
            case "↵":enter();return;
            case "⌫":deleteOne();return;
            case "المسافة":case "space":commit(" ");return;
            default:
                String out=key;
                if(!arabic && mode==Mode.LETTERS && shift) out=out.toUpperCase(java.util.Locale.US);
                commit(out);
                if(shift && !arabic) {shift=false;rebuild();}
        }
    }
    private void commit(String text) {
        InputConnection ic=getCurrentInputConnection();
        if(ic!=null) ic.commitText(text,1);
    }
    private void deleteOne() {
        InputConnection ic=getCurrentInputConnection();
        if(ic!=null) ic.deleteSurroundingText(1,0);
    }
    private void enter() {
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;
        EditorInfo info=getCurrentInputEditorInfo();
        int action=info==null?EditorInfo.IME_ACTION_NONE:info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if(action!=EditorInfo.IME_ACTION_NONE && action!=EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action);
        else sendKeyPair(ic,KeyEvent.KEYCODE_ENTER);
    }
    private void sendKeyPair(InputConnection ic,int keyCode) {
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,keyCode));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,keyCode));
    }
    private void stopDelete() {if(deletionTask!=null)handler.removeCallbacks(deletionTask);deletionTask=null;}
    private void installBackspace(Button btn) {
        btn.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    stopDelete();didRepeat=false;
                    deletionTask=new Runnable() { @Override public void run() {
                        didRepeat=true;deleteOne();handler.postDelayed(this,70);
                    }};
                    handler.postDelayed(deletionTask,430);return true;
                case MotionEvent.ACTION_UP:
                    stopDelete();if(!didRepeat)deleteOne();return true;
                case MotionEvent.ACTION_CANCEL:stopDelete();return true;
                default:return true;
            }
        });
    }
    private void installSpaceGestures(Button btn) {
        btn.setOnTouchListener(new View.OnTouchListener() {
            float downX;int steps=0;boolean hasSwiped=false;
            @Override public boolean onTouch(View v,MotionEvent event) {
                int action=event.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN) {downX=event.getX();steps=0;hasSwiped=false;return true;}
                if(action==MotionEvent.ACTION_MOVE) {
                    int desired=(int)((event.getX()-downX)/UiKit.dp(NafahatKeyboardService.this,18));
                    if(desired>30)desired=30;if(desired< -30)desired=-30;
                    if(desired!=0)hasSwiped=true;
                    InputConnection ic=getCurrentInputConnection();
                    if(ic!=null) {
                        while(steps<desired){sendKeyPair(ic,KeyEvent.KEYCODE_DPAD_RIGHT);steps++;}
                        while(steps>desired){sendKeyPair(ic,KeyEvent.KEYCODE_DPAD_LEFT);steps--;}
                    }
                    return true;
                }
                if(action==MotionEvent.ACTION_UP) {if(!hasSwiped)commit(" ");return true;}
                return true;
            }
        });
    }
    private void addSymbols() {
        // SwiftKey-inspired layout: symbols on the LEFT, Western numerals on the RIGHT.
        LinearLayout split=UiKit.horizontal(this);
        split.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(split,new LinearLayout.LayoutParams(-1,UiKit.dp(this,200)));
        LinearLayout symbolCol=UiKit.vertical(this);
        LinearLayout numCol=UiKit.vertical(this);
        split.addView(symbolCol,new LinearLayout.LayoutParams(0,-1,5.2f));
        split.addView(numCol,new LinearLayout.LayoutParams(0,-1,3.1f));
        String[][] left={{"#","&","-","_","?"},{"@","(",")","=","+"},{"{","}","/","%","⌫"},{"ABC","\"",":","*","!"}};
        String[][] right={{"1","2","3"},{"4","5","6"},{"7","8","9"},{"،","0","↵"}};
        for(String[] s:left)addGridRow(symbolCol,s);
        for(String[] s:right)addGridRow(numCol,s);
        addSpaceRow(new String[]{"ABC","EN","،","المسافة",".","↵"},53);
    }
    private void addGridRow(LinearLayout parent,String[] rowKeys) {
        LinearLayout row=UiKit.horizontal(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        parent.addView(row,new LinearLayout.LayoutParams(-1,0,1f));
        for(String k:rowKeys) addKeyToRow(row,k,1f,46);
    }
    private void addLibrary() {
        HorizontalScrollView tabs=new HorizontalScrollView(this);
        tabs.setHorizontalScrollBarEnabled(false);
        root.addView(tabs,new LinearLayout.LayoutParams(-1,UiKit.dp(this,46)));
        LinearLayout categories=UiKit.horizontal(this);
        categories.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        tabs.addView(categories);
        List<Snippet> personal=new SnippetStore(this).getPersonal();
        List<String> names=new ArrayList<>();
        names.add("معلومات نفحات");names.add("ردود جاهزة");names.add("شخصية");
        for(Snippet item:personal) {
            if(!names.contains(item.category)) names.add(item.category);
        }
        for(String name:names) addCategory(categories,name);
        ScrollView scroll=new ScrollView(this);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,UiKit.dp(this,197)));
        LinearLayout list=UiKit.vertical(this);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(list);
        boolean officialGroup=category.equals("معلومات نفحات") || category.equals("ردود جاهزة");
        List<Snippet> items=officialGroup ? BrandData.publicSnippets() : personal;
        int count=0;
        for(Snippet item:items) {
            if((officialGroup && item.category.equals(category)) ||
               (!officialGroup && (category.equals("شخصية") || item.category.equals(category)))) {
                count++;
                Button btn=makeKey(item.title+"  •  "+ellipsize(item.text,43),false);
                btn.setTextSize(13);
                btn.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
                btn.setPadding(UiKit.dp(this,11),0,UiKit.dp(this,11),0);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,UiKit.dp(this,49));
                p.setMargins(UiKit.dp(this,4),UiKit.dp(this,3),UiKit.dp(this,4),0);
                list.addView(btn,p);
                btn.setOnClickListener(v->{commit(item.text);mode=Mode.LETTERS;rebuild();});
                btn.setOnLongClickListener(v->{
                    ClipboardManager clip=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                    if(clip!=null)clip.setPrimaryClip(ClipData.newPlainText(item.title,item.text));
                    Toast.makeText(this,"تم النسخ",Toast.LENGTH_SHORT).show();return true;
                });
            }
        }
        if(count==0) {
            TextView empty=UiKit.text(this,"لا يوجد نصوص بعد. أضف النصوص من تطبيق كيبورد نفحات.",13,false);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty,new LinearLayout.LayoutParams(-1,UiKit.dp(this,70)));
        }
        Button back=makeKey("← العودة للكيبورد",true);
        root.addView(back,new LinearLayout.LayoutParams(-1,UiKit.dp(this,45)));
        back.setOnClickListener(v->{mode=Mode.LETTERS;rebuild();});
    }
    private static String ellipsize(String text,int max) {
        String value=text.replace('\n',' ');
        return value.length()<=max?value:value.substring(0,max)+"…";
    }
    private void addCategory(LinearLayout row,String name) {
        Button b=makeKey(name,true);
        b.setTextSize(12);
        if(category.equals(name)) b.setBackground(UiKit.rounded(UiKit.GOLD,8,this));
        int width=name.length()>10?118:89;
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(UiKit.dp(this,width),UiKit.dp(this,40));
        p.setMargins(UiKit.dp(this,2),0,UiKit.dp(this,2),0);
        row.addView(b,p);b.setOnClickListener(v->{category=name;rebuild();});
    }
}
