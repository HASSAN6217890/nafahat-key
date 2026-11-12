package com.nafahat.key;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;
import java.util.List;

/**
 * Persistent native Android IME. The Canvas view is not rebuilt when a key is typed,
 * Shift is toggled, or a layout is changed. No network or text logging.
 */
public final class NafahatKeyboardService extends InputMethodService
        implements CanvasKeyboardView.Listener {
    private CanvasKeyboardView keyboard;
    private KeyboardPrefs prefs;
    private KeyboardEngine engine;
    private LearningModel learned;
    private LibraryDb library;

    @Override public void onCreate(){
        super.onCreate();
        prefs=new KeyboardPrefs(this);
        learned=new LearningModel(this);
        library=new LibraryDb(this);
        engine=new KeyboardEngine(this,prefs,learned,this::renderState);
    }
    @Override public View onCreateInputView(){
        if(keyboard==null)keyboard=new CanvasKeyboardView(this,prefs,this);
        renderState();
        return keyboard;
    }
    @Override public void onStartInput(EditorInfo info,boolean restarting){
        super.onStartInput(info,restarting);
        engine.start(info);
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting){
        super.onStartInputView(info,restarting);
        renderState();
    }
    private void renderState(){
        if(keyboard==null||engine==null)return;
        keyboard.setSettings(prefs);
        keyboard.setSensitive(engine.isSensitive());
        keyboard.setLanguage(engine.arabic(),engine.upper());
        keyboard.setMode(engine.mode());
        List<String> suggestions=engine.suggestions();
        if(!engine.isSensitive()&&engine.composingText().startsWith("#")){
            String prefix=engine.composingText().substring(1);
            java.util.ArrayList<String> shortCodes=new java.util.ArrayList<>();
            for(Snippet entry:library.getAll()){
                if(entry.title.startsWith(prefix)||(!entry.shortcut.isEmpty()&&entry.shortcut.startsWith(prefix))){
                    shortCodes.add("#"+entry.title);
                    if(shortCodes.size()==3)break;
                }
            }
            suggestions=shortCodes;
        }
        keyboard.setSuggestions(suggestions);
        keyboard.setEnterLabel(engine.enterLabel());
        if(engine.mode().equals("library")&&!engine.isSensitive())keyboard.setSnippets(library.getAll());
    }
    @Override public void onUpdateSelection(int oldSelStart,int oldSelEnd,int newSelStart,
                                            int newSelEnd,int candidatesStart,int candidatesEnd){
        super.onUpdateSelection(oldSelStart,oldSelEnd,newSelStart,newSelEnd,candidatesStart,candidatesEnd);
        if(engine!=null)engine.selection(newSelStart,newSelEnd,candidatesStart,candidatesEnd);
    }
    @Override public void onComputeInsets(Insets outInsets){
        super.onComputeInsets(outInsets);
        if(keyboard==null||keyboard.getWidth()==0)return;
        int[] where=new int[2];keyboard.getLocationInWindow(where);
        outInsets.touchableInsets=Insets.TOUCHABLE_INSETS_REGION;
        outInsets.touchableRegion.set(where[0],where[1]+keyboard.previewHeight(),
             where[0]+keyboard.getWidth(),where[1]+keyboard.getHeight());
    }
    @Override public void onKey(String code){
        if(code.startsWith("menu:")){handleMenu(code);return;}
        if(code.equals("lib:back")){engine.setMode("letters");return;}
        if(code.equals("lib:search")){
            Toast.makeText(this,"البحث المتقدم متاح من التطبيق",Toast.LENGTH_SHORT).show();return;
        }
        if(code.startsWith("edit:")){editCommand(code);return;}
        engine.key(code);
    }
    private void handleMenu(String item){
        switch(item){
            case "menu:open":engine.setMode("menu");break;
            case "menu:back":engine.setMode("letters");break;
            case "menu:library":
                if(engine.isSensitive())return;
                engine.setMode(engine.mode().equals("library")?"letters":"library");break;
            case "menu:lang":engine.key("lang");break;
            case "menu:diacritics":engine.setMode("diacritics");break;
            case "menu:undo":engine.undo();engine.setMode("letters");break;
            case "menu:theme":
                prefs.setTheme(prefs.theme().equals("dark")?"light":"dark");
                renderState();break;
            case "menu:size":
                prefs.setHeightPercent(prefs.heightPercent()>=125?85:prefs.heightPercent()+5);
                renderState();break;
            case "menu:left":case "menu:right":
                if(keyboard!=null)keyboard.setOneHanded(item.endsWith("left")?1:2);
                engine.setMode("letters");break;
            case "menu:edit":engine.setMode("edit");break;
            case "menu:settings":
                Intent activity=new Intent(this,MainActivity.class);
                activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(activity);break;
        }
    }
    private void editCommand(String cmd){
        android.view.inputmethod.InputConnection ic=getCurrentInputConnection();
        if(ic==null)return;
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        switch(cmd){
            case "edit:left":sendKey(ic,KeyEvent.KEYCODE_DPAD_LEFT);break;
            case "edit:right":sendKey(ic,KeyEvent.KEYCODE_DPAD_RIGHT);break;
            case "edit:up":sendKey(ic,KeyEvent.KEYCODE_DPAD_UP);break;
            case "edit:down":sendKey(ic,KeyEvent.KEYCODE_DPAD_DOWN);break;
            case "edit:all":ic.performContextMenuAction(android.R.id.selectAll);break;
            case "edit:copy":ic.performContextMenuAction(android.R.id.copy);break;
            case "edit:cut":ic.performContextMenuAction(android.R.id.cut);break;
            case "edit:paste":
                if(clipboard!=null&&clipboard.hasPrimaryClip())
                    ic.performContextMenuAction(android.R.id.paste);
                break;
            case "edit:back":engine.setMode("letters");break;
        }
    }
    private void sendKey(android.view.inputmethod.InputConnection ic,int code){
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));
    }
    @Override public void onSuggestion(String suggestion){
        if(engine.isSensitive())return;
        if(suggestion.startsWith("#")){
            for(Snippet item:library.getAll()){
                if(suggestion.equals("#"+item.title)){engine.replaceComposingWithSnippet(item.text);library.recordUse(item.id);return;}
            }
        }
        engine.candidate(suggestion);
    }
    @Override public void onSpaceSwipe(boolean cursor,int units){engine.swipeSpace(cursor,units);}
    @Override public void onSnippet(Snippet snippet,boolean longPress){
        if(engine.isSensitive())return;
        if(longPress){
            ClipboardManager clip=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(clip!=null)clip.setPrimaryClip(ClipData.newPlainText(snippet.title,snippet.text));
            Toast.makeText(this,"تم نسخ النص",Toast.LENGTH_SHORT).show();
        }else{
            engine.insertSnippet(snippet.text);
            library.recordUse(snippet.id);
        }
    }
    @Override public void onSnippetPreviewAction(Snippet snippet,String action){
        if(engine.isSensitive()||snippet==null)return;
        if(action.equals("insert")){
            engine.insertSnippet(snippet.text);library.recordUse(snippet.id);
        }else if(action.equals("copy")){
            ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText(snippet.title,snippet.text));
            Toast.makeText(this,"تم نسخ النص",Toast.LENGTH_SHORT).show();
        }else if(action.equals("pin")){
            library.togglePinned(snippet.id);
            if(keyboard!=null)keyboard.setSnippets(library.getAll());
        }
    }
    @Override public void onFinishInputView(boolean finishingInput){
        super.onFinishInputView(finishingInput);
        if(keyboard!=null)keyboard.cancelPresses();
    }
    @Override public void onDestroy(){
        if(keyboard!=null)keyboard.cancelPresses();
        if(library!=null)library.close();
        if(learned!=null)learned.close();
        super.onDestroy();
    }
}