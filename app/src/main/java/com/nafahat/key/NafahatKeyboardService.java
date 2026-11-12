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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local-only Android IME: no logging, permissions, networking or automatic clipboard reads. */
public final class NafahatKeyboardService extends InputMethodService {
    private enum Mode { LETTERS, SYMBOLS, DIACRITICS, LIBRARY }
    private Mode mode = Mode.LETTERS;
    private boolean arabic = true, shift = false, sensitive = false;
    private String category = "معلومات نفحات";
    private LinearLayout root;
    private KeyboardPrefs preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable deletionTask;
    private boolean didRepeat;
    private PopupWindow preview;

    @Override public View onCreateInputView() {
        preferences = new KeyboardPrefs(this);
        root = UiKit.vertical(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        rebuild();
        return root;
    }
    @Override public void onStartInput(EditorInfo info,boolean restarting) {
        super.onStartInput(info,restarting);
        mode=Mode.LETTERS; shift=false; sensitive=isSensitive(info);
        if(root!=null) rebuild();
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting) {
        super.onStartInputView(info,restarting);
        sensitive=isSensitive(info);
        if(root!=null) rebuild();
    }
    @Override public void onFinishInputView(boolean finishingInput) {
        stopDelete(); hidePreview(); super.onFinishInputView(finishingInput);
    }
    @Override public void onDestroy() {
        stopDelete(); hidePreview(); super.onDestroy();
    }
    private boolean isSensitive(EditorInfo editor) {
        if(editor==null)return false;
        int cls=editor.inputType & InputType.TYPE_MASK_CLASS;
        int variation=editor.inputType & InputType.TYPE_MASK_VARIATION;
        if(cls==InputType.TYPE_CLASS_TEXT)
            return variation==InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        return cls==InputType.TYPE_CLASS_NUMBER && variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }
    private void rebuild() {
        if(root==null)return;
        preferences = new KeyboardPrefs(this);
        hidePreview();
        root.removeAllViews();
        root.setBackgroundColor(preferences.background());
        UiKit.pad(root,this,3,3);
        addToolbar();
        if(mode==Mode.LIBRARY && !sensitive) addLibrary();
        else if(mode==Mode.SYMBOLS) addSymbols();
        else if(mode==Mode.DIACRITICS && arabic) addDiacritics();
        else addLetters();
    }
    private void addToolbar() {
        LinearLayout row=UiKit.horizontal(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(row,new LinearLayout.LayoutParams(-1,UiKit.dp(this,37)));
        if(!sensitive) addToolbarButton(row,"▤  الحافظة",3f,()->{
            mode=mode==Mode.LIBRARY?Mode.LETTERS:Mode.LIBRARY;rebuild();
        });
        addToolbarButton(row,"⚙  الإعدادات",2f,()->{
            android.content.Intent intent=new android.content.Intent(this,MainActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
    }
    private void addToolbarButton(LinearLayout row,String label,float weight,Runnable action) {
        Button button=makeKey(label,true);
        button.setTextSize(13);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,UiKit.dp(this,33),weight);
        lp.setMargins(UiKit.dp(this,2),0,UiKit.dp(this,2),0);
        row.addView(button,lp);
        button.setOnClickListener(v->action.run());
    }
    private void addLetters() {
        if(preferences.numberRow()) addNumberRow();
        if(arabic) {
            addRow(new String[]{"ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج"},47);
            addRow(new String[]{"ش","س","ي","ب","ل","ا","ت","ن","م","ك","ط"},47);
            addRow(new String[]{"ذ","ء","ؤ","ر","ى","ة","و","ز","ظ","د","⌫"},47);
            addSpaceRow(new String[]{"123","EN","◌","،","المسافة",".","↵"},52);
        } else {
            addRow(new String[]{"q","w","e","r","t","y","u","i","o","p"},47);
            addRow(new String[]{"a","s","d","f","g","h","j","k","l"},47);
            addRow(new String[]{"⇧","z","x","c","v","b","n","m","⌫"},47);
            addSpaceRow(new String[]{"123","عربي",",","space",".","↵"},52);
        }
    }
    private void addNumberRow() {
        LinearLayout row=UiKit.horizontal(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(row,new LinearLayout.LayoutParams(-1,UiKit.dp(this,preferences.scaled(34))));
        for(String digit:new String[]{"1","2","3","4","5","6","7","8","9","0"})
            addKeyToRow(row,digit,1f,preferences.scaled(32));
    }
    private void addRow(String[] keys,int heightDp) {
        int height=preferences.scaled(heightDp);
        LinearLayout row=UiKit.horizontal(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(row,new LinearLayout.LayoutParams(-1,UiKit.dp(this,height)));
        for(String key:keys)addKeyToRow(row,key,1f,height-3);
    }
    private void addSpaceRow(String[] keys,int heightDp) {
        int height=preferences.scaled(heightDp);
        LinearLayout row=UiKit.horizontal(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(row,new LinearLayout.LayoutParams(-1,UiKit.dp(this,height)));
        for(String key:keys)
            addKeyToRow(row,key,(key.equals("المسافة") || key.equals("space"))?3.7f:
                key.equals("↵")?1.12f:1f,height-3);
    }
    private void addKeyToRow(LinearLayout row,String key,float weight,int height) {
        boolean special=key.equals("⇧")||key.equals("⌫")||key.equals("123")
            ||key.equals("↵")||key.equals("EN")||key.equals("عربي")
            ||key.equals("ABC")||key.equals("حروف")||key.equals("◌");
        Button b=makeKey(key,special);
        b.setTextSize(key.length()>4?13:preferences.fontSize());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,UiKit.dp(this,height),weight);
        lp.setMargins(UiKit.dp(this,1),UiKit.dp(this,2),UiKit.dp(this,1),0);
        row.addView(b,lp);
        if(key.equals("⌫")) { installBackspace(b); return; }
        if(key.equals("المسافة")||key.equals("space")) {installSpaceGestures(b);return;}
        String[] alternatives=alternatives(key);
        if(alternatives!=null) {
            b.setOnLongClickListener(v->{
                hidePreview();
                PopupMenu menu=new PopupMenu(this,b);
                for(String option:alternatives)menu.getMenu().add(option);
                menu.setOnMenuItemClickListener(item->{commit(item.getTitle().toString());return true;});
                menu.show();
                return true;
            });
        }
        if(key.length()<=2 && !special) attachPreview(b,key);
        b.setOnClickListener(v->{keyPressed(key);feedback(b);});
    }
    private String[] alternatives(String key) {
        if(!arabic || mode!=Mode.LETTERS) return null;
        switch(key) {
            case "ا": return new String[]{"أ","إ","آ","ٱ"};
            case "ي": return new String[]{"ئ","ى"};
            case "ل": return new String[]{"لا","لأ","لإ","لآ"};
            case "و": return new String[]{"ؤ"};
            case "ه": return new String[]{"ة"};
            case "ء": return new String[]{"أ","إ","ئ","ؤ"};
            case "ى": return new String[]{"ي"};
            case "ة": return new String[]{"ه"};
            default: return null;
        }
    }
    private Button makeKey(String label,boolean special) {
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setText(label);
        b.setTextColor(preferences.textColor());
        b.setTypeface(Typeface.DEFAULT,special?Typeface.BOLD:Typeface.NORMAL);
        b.setBackground(UiKit.rounded(special?preferences.specialColor():preferences.keyColor(),6,this));
        b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(0,0,0,0);
        b.setHapticFeedbackEnabled(true);
        return b;
    }
    private void attachPreview(Button btn,String label) {
        btn.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: showPreview(btn,label); return false;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    hidePreview();return false;
                default:return false;
            }
        });
    }
    private void showPreview(Button anchor,String text) {
        hidePreview();
        TextView tv=new TextView(this);
        tv.setText(text);tv.setTextSize(Math.max(24,preferences.fontSize()+6));
        tv.setTextColor(preferences.textColor());
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(UiKit.rounded(preferences.keyColor(),8,this));
        int w=UiKit.dp(this,53),h=UiKit.dp(this,56);
        preview=new PopupWindow(tv,w,h,false);
        preview.setBackgroundDrawable(UiKit.rounded(preferences.keyColor(),8,this));
        int[] xy=new int[2];anchor.getLocationOnScreen(xy);
        try {
            preview.showAtLocation(anchor,Gravity.TOP|Gravity.LEFT,
                xy[0]+(anchor.getWidth()-w)/2,Math.max(0,xy[1]-h-UiKit.dp(this,4)));
        } catch(RuntimeException e) { hidePreview(); } // IME window tokens may differ by device
    }
    private void hidePreview() {
        if(preview!=null) {if(preview.isShowing())preview.dismiss();preview=null;}
    }
    private void feedback(View view) {
        if(preferences.vibrate())view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }
    private void keyPressed(String key) {
        switch(key) {
            case "123":mode=Mode.SYMBOLS;rebuild();return;
            case "ABC":case "حروف":mode=Mode.LETTERS;rebuild();return;
            case "◌":mode=Mode.DIACRITICS;rebuild();return;
            case "EN":arabic=false;shift=false;mode=Mode.LETTERS;rebuild();return;
            case "عربي":arabic=true;shift=false;mode=Mode.LETTERS;rebuild();return;
            case "⇧":shift=!shift;rebuild();return;
            case "↵":enter();return;
            case "⌫":deleteOne();return;
            case "المسافة":case "space":commit(" ");return;
            default:
                String out=key;
                if(!arabic && mode==Mode.LETTERS && shift)out=out.toUpperCase(Locale.US);
                commit(out);
                if(shift && !arabic){shift=false;rebuild();}
        }
    }
    private void commit(String text) {
        InputConnection ic=getCurrentInputConnection();
        if(ic!=null)ic.commitText(text,1);
    }
    private void deleteOne() {
        InputConnection ic=getCurrentInputConnection();
        if(ic!=null) {
            CharSequence selected=ic.getSelectedText(0);
            if(selected!=null && selected.length()>0)ic.commitText("",1);
            else ic.deleteSurroundingTextInCodePoints(1,0);
        }
    }
    private void enter() {
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;
        EditorInfo info=getCurrentInputEditorInfo();
        int action=info==null?EditorInfo.IME_ACTION_NONE:info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if(action!=EditorInfo.IME_ACTION_NONE && action!=EditorInfo.IME_ACTION_UNSPECIFIED)
            ic.performEditorAction(action);
        else sendKeyPair(ic,KeyEvent.KEYCODE_ENTER);
    }
    private void sendKeyPair(InputConnection ic,int keyCode) {
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,keyCode));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,keyCode));
    }
    private void stopDelete() {
        if(deletionTask!=null)handler.removeCallbacks(deletionTask);deletionTask=null;
    }
    private void installBackspace(Button btn) {
        btn.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    stopDelete();didRepeat=false;feedback(btn);
                    deletionTask=new Runnable() {@Override public void run(){
                        didRepeat=true;deleteOne();handler.postDelayed(this,75);
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
            float downX;int steps;boolean swiped;
            @Override public boolean onTouch(View v,MotionEvent e) {
                int action=e.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN){
                    downX=e.getX();steps=0;swiped=false;feedback(btn);return true;
                }
                if(action==MotionEvent.ACTION_MOVE){
                    int desired=(int)((e.getX()-downX)/UiKit.dp(NafahatKeyboardService.this,18));
                    desired=Math.max(-30,Math.min(30,desired));
                    if(desired!=0)swiped=true;
                    InputConnection ic=getCurrentInputConnection();
                    if(ic!=null){
                        while(steps<desired){sendKeyPair(ic,KeyEvent.KEYCODE_DPAD_RIGHT);steps++;}
                        while(steps>desired){sendKeyPair(ic,KeyEvent.KEYCODE_DPAD_LEFT);steps--;}
                    }
                    return true;
                }
                if(action==MotionEvent.ACTION_UP){if(!swiped)commit(" ");return true;}
                return true;
            }
        });
    }
    private void addSymbols() {
        LinearLayout split=UiKit.horizontal(this);
        split.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(split,new LinearLayout.LayoutParams(-1,UiKit.dp(this,preferences.scaled(200))));
        LinearLayout left=UiKit.vertical(this),right=UiKit.vertical(this);
        split.addView(left,new LinearLayout.LayoutParams(0,-1,5.2f));
        split.addView(right,new LinearLayout.LayoutParams(0,-1,3.1f));
        String[][] symbols={{"#","&","-","_","?"},{"@","(",")","=","+"},
            {"{","}","/","%","⌫"},{"ABC","\"",":","*","!"}};
        String[][] digits={{"1","2","3"},{"4","5","6"},{"7","8","9"},{"،","0","↵"}};
        for(String[] row:symbols)addGridRow(left,row);
        for(String[] row:digits)addGridRow(right,row);
        addSpaceRow(new String[]{"ABC",arabic?"EN":"عربي","،","المسافة",".","↵"},52);
    }
    private void addDiacritics() {
        addRow(new String[]{"َ","ً","ُ","ٌ"},52);
        addRow(new String[]{"ِ","ٍ","ّ","ْ"},52);
        addRow(new String[]{"أ","إ","آ","ـ"},52);
        addSpaceRow(new String[]{"حروف","123","المسافة","↵"},52);
    }
    private void addGridRow(LinearLayout parent,String[] keys) {
        LinearLayout row=UiKit.horizontal(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        parent.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        for(String key:keys)addKeyToRow(row,key,1f,preferences.scaled(46));
    }
    private void addLibrary() {
        HorizontalScrollView tabs=new HorizontalScrollView(this);
        tabs.setHorizontalScrollBarEnabled(false);
        root.addView(tabs,new LinearLayout.LayoutParams(-1,UiKit.dp(this,43)));
        LinearLayout nav=UiKit.horizontal(this);
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        tabs.addView(nav);
        List<Snippet> personal=new SnippetStore(this).getPersonal();
        List<String> names=new ArrayList<>();
        names.add("معلومات نفحات");names.add("ردود جاهزة");names.add("شخصية");
        for(Snippet item:personal)if(!names.contains(item.category))names.add(item.category);
        for(String name:names)addCategory(nav,name);

        ScrollView scroll=new ScrollView(this);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,UiKit.dp(this,preferences.scaled(201))));
        LinearLayout list=UiKit.vertical(this);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(list);
        boolean official=category.equals("معلومات نفحات") || category.equals("ردود جاهزة");
        List<Snippet> items=official?BrandData.publicSnippets():personal;
        int count=0;
        for(Snippet item:items) {
            if(!item.category.equals(category))continue;
            count++;
            LinearLayout card=UiKit.vertical(this);
            card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            card.setBackground(UiKit.rounded(preferences.keyColor(),8,this));
            UiKit.pad(card,this,11,7);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.setMargins(UiKit.dp(this,4),UiKit.dp(this,3),UiKit.dp(this,4),UiKit.dp(this,2));
            list.addView(card,lp);
            TextView title=new TextView(this);
            title.setText(item.title);title.setTextSize(14);
            title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            title.setTextColor(preferences.textColor());title.setGravity(Gravity.RIGHT);
            card.addView(title,new LinearLayout.LayoutParams(-1,-2));
            TextView excerpt=new TextView(this);
            excerpt.setText(item.text);excerpt.setTextSize(12);
            excerpt.setTextColor(preferences.textColor());
            excerpt.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            excerpt.setMaxLines(2);excerpt.setEllipsize(TextUtils.TruncateAt.END);
            excerpt.setGravity(Gravity.RIGHT);
            card.addView(excerpt,new LinearLayout.LayoutParams(-1,-2));
            card.setContentDescription(item.title+"، "+item.text);
            card.setOnClickListener(v->{commit(item.text);mode=Mode.LETTERS;rebuild();});
            card.setOnLongClickListener(v->{
                ClipboardManager mgr=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if(mgr!=null)mgr.setPrimaryClip(ClipData.newPlainText(item.title,item.text));
                Toast.makeText(this,"تم النسخ",Toast.LENGTH_SHORT).show();return true;
            });
        }
        if(count==0) {
            TextView empty=new TextView(this);
            empty.setText("لا توجد نصوص في هذا التبويب.");empty.setTextSize(13);
            empty.setTextColor(preferences.textColor());empty.setGravity(Gravity.CENTER);
            list.addView(empty,new LinearLayout.LayoutParams(-1,UiKit.dp(this,70)));
        }
        Button back=makeKey("العودة إلى الحروف",true);
        root.addView(back,new LinearLayout.LayoutParams(-1,UiKit.dp(this,40)));
        back.setOnClickListener(v->{mode=Mode.LETTERS;rebuild();});
    }
    private void addCategory(LinearLayout nav,String name) {
        Button b=makeKey(name,true);
        b.setTextSize(12);
        if(category.equals(name)){
            b.setBackground(UiKit.rounded(preferences.highlightColor(),8,this));
            b.setTextColor(Color.WHITE);
        }
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(UiKit.dp(this,name.length()>10?124:86),UiKit.dp(this,38));
        lp.setMargins(UiKit.dp(this,2),0,UiKit.dp(this,2),0);
        nav.addView(b,lp);
        b.setOnClickListener(v->{category=name;rebuild();});
    }
}
