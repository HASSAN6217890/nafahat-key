package com.nafahat.key;

import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Separated IME state machine: field policy, composing, corrections and selection. */
public final class KeyboardEngine {
    public interface Callback {void stateChanged();}
    private final InputMethodService service;
    private final KeyboardPrefs prefs;
    private final Lexicon dictionary;
    private final LearningModel learning;
    private final Callback callback;
    private EditorInfo editor;
    private final StringBuilder composing=new StringBuilder();
    private String previousWord="",mode="letters";
    private boolean arabic=true,sensitive=false,caps=false,shift=false;
    private long shiftTime=0;
    private int cursorEnd=-1;
    private String undoOriginal=null,undoCorrected=null,undoDelimiter=null;
    private int undoExpectedCursor=-1;
    public KeyboardEngine(InputMethodService service,KeyboardPrefs preferences,LearningModel learned,Callback cb){
        this.service=service;this.prefs=preferences;this.learning=learned;callback=cb;
        dictionary=new Lexicon(service,learned);
    }
    public void start(EditorInfo info){
        this.editor=info;
        sensitive=isSensitive(info);
        composing.setLength(0);previousWord="";clearUndo();caps=false;shift=false;cursorEnd=-1;
        int cls=info==null?InputType.TYPE_CLASS_TEXT:info.inputType&InputType.TYPE_MASK_CLASS;
        int variation=info==null?0:info.inputType&InputType.TYPE_MASK_VARIATION;
        boolean email=cls==InputType.TYPE_CLASS_TEXT && (variation==InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS||
                variation==InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS||variation==InputType.TYPE_TEXT_VARIATION_URI);
        arabic=!email;
        mode=(cls==InputType.TYPE_CLASS_PHONE||cls==InputType.TYPE_CLASS_NUMBER||
              cls==InputType.TYPE_CLASS_DATETIME)?"number":"letters";
        if(!arabic&&prefs.autoCap()&&info!=null&&(info.inputType&InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)!=0)shift=true;
        changed();
    }
    public boolean isSensitive(){return sensitive;}
    public boolean arabic(){return arabic;}
    public boolean upper(){return caps||shift;}
    public String mode(){return mode;}
    public String composingText(){return composing.toString();}
    public String enterLabel(){
        if(editor==null||(editor.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0)return "↵";
        switch(editor.imeOptions&EditorInfo.IME_MASK_ACTION){
            case EditorInfo.IME_ACTION_SEND:return "➤";
            case EditorInfo.IME_ACTION_SEARCH:return "⌕";
            case EditorInfo.IME_ACTION_NEXT:return "→";
            case EditorInfo.IME_ACTION_GO:return "➜";
            case EditorInfo.IME_ACTION_DONE:return "✓";
            case EditorInfo.IME_ACTION_PREVIOUS:return "←";
            default:return "↵";
        }
    }
    public void setMode(String value){
        if(sensitive&&("library".equals(value)||"menu".equals(value)))return;
        mode=value;changed();
    }
    public List<String> suggestions(){
        if(sensitive||!mode.equals("letters"))return Collections.emptyList();
        String current=composing.toString();
        if(current.startsWith("#"))return Collections.emptyList();
        if(!current.isEmpty()){
            ArrayList<String> values=new ArrayList<>();
            values.add(current);
            for(String word:dictionary.complete(current,3))if(!values.contains(word))values.add(word);
            String correction=dictionary.corrected(current);
            if(prefs.autoCorrect()&&!correction.equals(current)&&!values.contains(correction))
                values.add(1,correction);
            return values.subList(0,Math.min(3,values.size()));
        }
        if(undoOriginal!=null)return java.util.Arrays.asList("تراجع ↶",undoCorrected,undoOriginal);
        if(previousWord.isEmpty())return java.util.Arrays.asList("السلام عليكم ورحمة الله وبركاته 🤍","نفحات","أهلاً وسهلاً");
        return dictionary.predict(previousWord,3);
    }
    public void candidate(String value){
        if(sensitive||value==null||value.isEmpty())return;
        if(value.equals("تراجع ↶")){undo();return;}
        InputConnection ic=ic();if(ic==null)return;
        clearUndo();
        ic.beginBatchEdit();
        try{
            if(composing.length()>0){
                ic.commitText(value+" ",1);composing.setLength(0);
            }else ic.commitText(value+" ",1);
        }finally{ic.endBatchEdit();}
        previousWord=value.toLowerCase(Locale.ROOT);
        if(!sensitive)learning.observe("",previousWord);
        if(!arabic&&!caps&&prefs.autoCap())shift=false;
        changed();
    }
    public void key(String code){
        InputConnection ic=ic();if(ic==null)return;
        switch(code){
            case "lang":
                finalizeWord("");arabic=!arabic;shift=false;caps=false;mode="letters";changed();return;
            case "⇧":
                long now=android.os.SystemClock.uptimeMillis();
                if(shift&&now-shiftTime<420){caps=!caps;shift=caps;}else if(caps){caps=false;shift=false;}else shift=!shift;
                shiftTime=now;changed();return;
            case "123":mode="symbols1";changed();return;
            case "ABC":case "حروف":mode="letters";changed();return;
            case "symbols2":mode="symbols2";changed();return;
            case "symbols1":mode="symbols1";changed();return;
            case "◌":mode="diacritics";changed();return;
            case "⌫":delete(false);return;
            case "deleteWord":delete(true);return;
            case "space":finalizeWord(" ");return;
            case "↵":enter();return;
            default:break;
        }
        if(code.isEmpty())return;
        if(code.equals("،")||code.equals(".")||code.equals("!")||code.equals("?")||code.equals("؟")||
           code.equals("؛")||code.equals(":")||code.equals(";")||code.equals("\n")){
            finalizeWord(code);
            if(!arabic&&!caps&&prefs.autoCap()&&sentenceCap()&&
               (code.equals(".")||code.equals("!")||code.equals("?")||code.equals("؟")))
                {shift=true;changed();}
            return;
        }
        if((code.equals("#")&&composing.length()==0||code.matches("[a-zA-Z\\u0621-\\u064A\\u0671]+"))&&mode.equals("letters")&&!sensitive){
            clearUndo();
            String output=!arabic&&(shift||caps)?code.toUpperCase(Locale.ROOT):code;
            composing.append(output);
            ic.setComposingText(composing.toString(),1);
            if(shift&&!caps)shift=false;
            changed();
        }else{
            finalizeWord("");
            clearUndo();
            ic.commitText(code,1);
            changed();
        }
    }
    private void finalizeWord(String delimiter){
        InputConnection ic=ic();if(ic==null)return;
        String original=composing.toString();
        if(original.isEmpty()){clearUndo();if(!delimiter.isEmpty())ic.commitText(delimiter,1);changed();return;}
        String next=original;
        if(!sensitive&&prefs.autoCorrect())next=dictionary.corrected(original);
        ic.beginBatchEdit();
        try{ic.commitText(next+delimiter,1);}finally{ic.endBatchEdit();}
        composing.setLength(0);
        if(!sensitive){
            learning.observe(previousWord,original.toLowerCase(Locale.ROOT));
            previousWord=next.toLowerCase(Locale.ROOT);
        }else previousWord="";
        if(!next.equals(original)){
            undoOriginal=original;undoCorrected=next;undoDelimiter=delimiter;
            undoExpectedCursor=cursorEnd<0?-1:cursorEnd+next.length()-original.length()+delimiter.length();
        }else clearUndo();
        changed();
    }
    private void delete(boolean fullWord){
        InputConnection ic=ic();if(ic==null)return;
        if(undoOriginal!=null){undo();return;}
        if(composing.length()>0){
            composing.deleteCharAt(composing.length()-1);
            if(composing.length()==0)ic.finishComposingText();
            else ic.setComposingText(composing.toString(),1);
            changed();return;
        }
        CharSequence selection=ic.getSelectedText(0);
        if(selection!=null&&selection.length()>0){ic.commitText("",1);changed();return;}
        if(fullWord){
            CharSequence before=ic.getTextBeforeCursor(100,0);
            if(before!=null){
                String s=before.toString();int i=s.length();
                while(i>0&&Character.isWhitespace(s.charAt(i-1)))i--;
                while(i>0&&!Character.isWhitespace(s.charAt(i-1)))i--;
                ic.deleteSurroundingTextInCodePoints(s.length()-i,0);
                changed();return;
            }
        }
        ic.deleteSurroundingTextInCodePoints(1,0);
        changed();
    }
    public void undo(){
        if(undoOriginal==null)return;
        InputConnection ic=ic();if(ic==null)return;
        String suffix=undoCorrected+undoDelimiter;
        CharSequence before=ic.getTextBeforeCursor(suffix.length(),0);
        if(before==null||!suffix.contentEquals(before)||
           (undoExpectedCursor>=0&&cursorEnd>=0&&cursorEnd!=undoExpectedCursor)){
            clearUndo();changed();return;
        }
        ic.beginBatchEdit();
        try{
            ic.deleteSurroundingTextInCodePoints(
                undoCorrected.codePointCount(0,undoCorrected.length())+undoDelimiter.codePointCount(0,undoDelimiter.length()),0);
            ic.commitText(undoOriginal+undoDelimiter,1);
        }finally{ic.endBatchEdit();}
        clearUndo();changed();
    }
    public void replaceComposingWithSnippet(String value){
        if(sensitive)return;
        InputConnection ic=ic();if(ic==null)return;
        clearUndo();
        ic.beginBatchEdit();
        try{ic.commitText(value,1);composing.setLength(0);}
        finally{ic.endBatchEdit();}
        mode="letters";changed();
    }
    public void insertSnippet(String value){
        if(sensitive)return;
        InputConnection ic=ic();if(ic==null)return;
        finalizeWord("");clearUndo();ic.commitText(value,1);mode="letters";changed();
    }
    public void swipeSpace(boolean cursor,int delta){
        if(!cursor){key("lang");return;}
        InputConnection ic=ic();if(ic==null)return;
        finalizeWord("");
        // Arrow key events are more compatible with remote editors than guessed selection offsets.
        int key=delta<0?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT;
        if(cursorEnd>=0){
            cursorEnd=Math.max(0,cursorEnd+delta);
            if(ic.setSelection(cursorEnd,cursorEnd))return;
        }
        for(int i=0;i<Math.min(30,Math.abs(delta));i++)sendKey(ic,key);
    }
    private void enter(){
        InputConnection ic=ic();if(ic==null)return;
        finalizeWord("");
        int options=editor==null?0:editor.imeOptions;
        int action=options & EditorInfo.IME_MASK_ACTION;
        boolean forceNewline=(options&EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0;
        if(!forceNewline&&action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED){
            ic.performEditorAction(action);
        }else sendKey(ic,KeyEvent.KEYCODE_ENTER);
        if(!arabic&&!caps&&prefs.autoCap()&&sentenceCap())shift=true;
        changed();
    }
    private boolean sentenceCap(){
        return editor!=null&&(editor.inputType&InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)!=0;
    }
    private void sendKey(InputConnection ic,int code){
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));
    }
    public void selection(int start,int end,int candidatesStart,int candidatesEnd){
        cursorEnd=end;
        if(composing.length()>0&&candidatesEnd>=0&&end!=candidatesEnd){
            composing.setLength(0);InputConnection ic=ic();if(ic!=null)ic.finishComposingText();
            changed();
        }
        if(undoOriginal!=null&&(start!=end||(undoExpectedCursor>=0&&end!=undoExpectedCursor)))clearUndo();
    }
    private void clearUndo(){undoOriginal=null;undoCorrected=null;undoDelimiter=null;undoExpectedCursor=-1;}
    private void changed(){if(callback!=null)callback.stateChanged();}
    private InputConnection ic(){return service.getCurrentInputConnection();}
    public static boolean isSensitive(EditorInfo info){
        if(info==null)return false;
        int cls=info.inputType&InputType.TYPE_MASK_CLASS;
        int v=info.inputType&InputType.TYPE_MASK_VARIATION;
        boolean secret=(cls==InputType.TYPE_CLASS_TEXT&&(
            v==InputType.TYPE_TEXT_VARIATION_PASSWORD||v==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD||
            v==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))||
            (cls==InputType.TYPE_CLASS_NUMBER&&v==InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return secret||(info.inputType&InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)!=0||
            (info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0;
    }
}
