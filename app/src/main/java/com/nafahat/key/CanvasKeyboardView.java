package com.nafahat.key;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One persistent custom View. Draws all keys, chips, menus and hold bubbles on Canvas.
 * No Button children, PopupMenu, PopupWindow, or per-key View allocations.
 */
public final class CanvasKeyboardView extends View {
    public interface Listener {
        void onKey(String key);
        void onSuggestion(String word);
        void onSpaceSwipe(boolean cursor, int units);
        void onSnippet(Snippet snippet, boolean longPress);
        void onSnippetPreviewAction(Snippet snippet,String action);
    }
    private static final int WHITE=Color.WHITE, BACK=Color.rgb(23,28,31);
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF scratch=new RectF();
    private final ArrayList<Cell> cells=new ArrayList<>();
    private final SparseArray<Press> fingers=new SparseArray<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Context context;
    private KeyboardPrefs prefs;
    private Listener listener;
    private String mode="letters";
    private boolean arabic=true, upper=false, sensitive=false;
    private List<String> suggestions=Collections.emptyList();
    private List<Snippet> snippets=Collections.emptyList();
    private int libraryPage=0, selectedGroup=0, selectedType=0, oneHanded=0;
    private String enterLabel="↵";
    private Snippet previewSnippet;
    private ValueAnimator layoutAnimator;
    private float transitionAlpha=1f;
    private int cachedHeight=-1; private boolean cachedNumbers=false;
    private List<String> groups=Collections.emptyList();
    private final String[] types={"الكل","واتساب","إنستغرام قصير","رد","أسعار","عنوان/روابط","أخرى"};
    private float previewTop,toolbarHeight,keyboardStart;
    private static final class Cell {
        final KeyboardLayout.Key key;
        final RectF rect;
        final Snippet snippet;
        Cell(KeyboardLayout.Key k,RectF r){key=k;rect=r;snippet=null;}
        Cell(KeyboardLayout.Key k,RectF r,Snippet s){key=k;rect=r;snippet=s;}
    }
    private static final class Press {
        final int pointer;
        final Cell cell;
        final float x,y;
        boolean longHeld=false,dragged=false,repeat=false;
        int cursorSteps=0,optionIndex=0;
        Runnable timer;
        Press(int p,Cell c,float x,float y){pointer=p;cell=c;this.x=x;this.y=y;}
    }
    public CanvasKeyboardView(Context c,KeyboardPrefs settings,Listener callbacks){
        super(c);context=c;prefs=settings;listener=callbacks;
        setHapticFeedbackEnabled(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateGroups();
    }
    public CanvasKeyboardView(Context c,AttributeSet a){this(c,new KeyboardPrefs(c),null);}
    public void setListener(Listener l){listener=l;}
    public void setEnterLabel(String value){
        if(!enterLabel.equals(value)){enterLabel=value;refresh();}
    }
    public void setSettings(KeyboardPrefs value){
        prefs=value;
        int height=value.heightPercent();boolean numbers=value.numberRow();
        if(cachedHeight!=height||cachedNumbers!=numbers){cachedHeight=height;cachedNumbers=numbers;refresh();}
        else invalidate();
    }
    public void setMode(String value){
        if(!mode.equals(value)){mode=value;refresh();animateLayout();}
    }
    private void animateLayout(){
        if(layoutAnimator!=null)layoutAnimator.cancel();
        layoutAnimator=ValueAnimator.ofFloat(.55f,1f);
        layoutAnimator.setDuration(135);
        layoutAnimator.addUpdateListener(anim->{
            transitionAlpha=(Float)anim.getAnimatedValue();invalidate();
        });
        layoutAnimator.start();
    }
    public String getMode(){return mode;}
    public void setLanguage(boolean value,boolean shift){if(arabic!=value||upper!=shift){arabic=value;upper=shift;refresh();}}
    public void setSensitive(boolean value){if(sensitive!=value){sensitive=value;refresh();}}
    public void setSuggestions(List<String> words){
        suggestions=words==null?Collections.emptyList():new ArrayList<>(words);invalidate();
    }
    public void setSnippets(List<Snippet> list){
        snippets=list==null?Collections.emptyList():new ArrayList<>(list);
        updateGroups();refresh();
    }
    private void updateGroups(){
        ArrayList<String> names=new ArrayList<>();
        names.add("الكل");
        for(Snippet s:snippets)if(!names.contains(s.category))names.add(s.category);
        groups=names;selectedGroup=Math.min(selectedGroup,Math.max(0,names.size()-1));
    }
    public int previewHeight(){return dp(54);}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    public void setOneHanded(int side){oneHanded=side;refresh();}
    public void cancelPresses(){stopAll();invalidate();}
    private void refresh(){stopAll();layoutCells(getWidth());requestLayout();invalidate();}
    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        float height=54+46;
        if("menu".equals(mode)||"library".equals(mode)||"edit".equals(mode))height+=280;
        else for(KeyboardLayout.Row row:KeyboardLayout.make(arabic,mode,upper,prefs.numberRow()))height+=row.height*prefs.heightPercent()/100f;
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),dp(height));
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);layoutCells(w);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        c.drawColor(prefs.background());
        if(cells.isEmpty())layoutCells(getWidth());
        drawToolbar(c);
        int layer=c.saveLayerAlpha(0,keyboardStart,getWidth(),getHeight(),
            Math.round(255*transitionAlpha));
        for(Cell cell:cells){
            RectF r=cell.rect;
            boolean down=false;
            for(int i=0;i<fingers.size();i++)if(fingers.valueAt(i).cell==cell)down=true;
            boolean special=cell.key.function;
            int color=down?prefs.highlightColor():special?prefs.specialColor():prefs.keyColor();
            paint.setColor(color);paint.setStyle(Paint.Style.FILL);
            float radius=dp(cell.key.code.equals("↵")||cell.key.code.equals("123")?22:8);
            c.drawRoundRect(r,radius,radius,paint);
            float size=cell.snippet!=null?14:cell.key.label.length()>5?13:cell.key.label.length()>2?17:prefs.fontSize();
            if("menu".equals(mode))size=13;
            paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
            paint.setColor(prefs.textColor());
            paint.setTextSize(dp(size)*getResources().getDisplayMetrics().scaledDensity/getResources().getDisplayMetrics().density);
            paint.setTextAlign(Paint.Align.CENTER);
            if(cell.snippet!=null){
                drawOneLine(c,cell.key.label,r.centerX(),r.top+dp(22),r.width()-dp(12));
                paint.setTextSize(dp(11));
                paint.setColor(WHITE);
                drawOneLine(c,cell.snippet.text,r.centerX(),r.top+dp(45),r.width()-dp(18));
            } else {
                drawOneLine(c,cell.key.label,r.centerX(),r.centerY()-(paint.ascent()+paint.descent())/2,r.width()-dp(5));
                if(!cell.key.secondary.isEmpty()){
                    paint.setTextSize(dp(11));paint.setColor(prefs.isDark()?Color.argb(170,240,240,240):Color.argb(170,30,40,34));
                    paint.setTextAlign(Paint.Align.RIGHT);
                    c.drawText(cell.key.secondary,r.right-dp(5),r.top+dp(14),paint);
                }
            }
        }
        c.restoreToCount(layer);
        drawPressBubbles(c);
        if(previewSnippet!=null)drawSnippetPreview(c);
    }
    private void drawSnippetPreview(Canvas c){
        float x=dp(7),y=keyboardStart+dp(5),w=getWidth()-dp(14),h=getHeight()-y-dp(5);
        paint.setColor(prefs.background());scratch.set(x,y,x+w,y+h);
        c.drawRoundRect(scratch,dp(10),dp(10),paint);
        paint.setColor(prefs.highlightColor());paint.setTextSize(dp(17));
        paint.setTextAlign(Paint.Align.CENTER);
        drawOneLine(c,previewSnippet.title,getWidth()/2f,y+dp(30),w-dp(20));
        paint.setColor(prefs.textColor());paint.setTextSize(dp(13));
        String text=previewSnippet.text.replace('\n',' ');
        float lineWidth=w-dp(24);
        int position=0,lines=0;
        while(position<text.length()&&lines<5){
            int end=Math.min(text.length(),position+55);
            while(end>position+10&&paint.measureText(text.substring(position,end))>lineWidth)end--;
            if(end<=position)break;
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText(text.substring(position,end),getWidth()/2f,y+dp(55+lines*25),paint);
            position=end;lines++;
        }
        String[] actions={"إدراج","نسخ","تثبيت"};
        float gap=dp(5),buttonWidth=(w-dp(12)-gap*2)/3f;
        for(int i=0;i<3;i++){
            float left=x+dp(6)+i*(buttonWidth+gap),top=y+h-dp(52);
            paint.setColor(i==0?prefs.specialColor():prefs.keyColor());
            scratch.set(left,top,left+buttonWidth,top+dp(42));
            c.drawRoundRect(scratch,dp(8),dp(8),paint);
            paint.setColor(Color.WHITE);paint.setTextSize(dp(14));paint.setTextAlign(Paint.Align.CENTER);
            c.drawText(actions[i],left+buttonWidth/2,top+dp(27),paint);
        }
    }
    private void drawOneLine(Canvas c,String text,float x,float baseline,float maxWidth){
        if(text==null)return;
        String label=text.replace('\n',' ');
        if(paint.measureText(label)>maxWidth&&maxWidth>0){
            while(label.length()>1&&paint.measureText(label+"…")>maxWidth)label=label.substring(0,label.length()-1);
            label+="…";
        }
        c.drawText(label,x,baseline,paint);
    }
    private void drawToolbar(Canvas c){
        float y=dp(54),h=dp(46),w=getWidth();
        paint.setColor(prefs.background());paint.setStyle(Paint.Style.FILL);
        c.drawRect(0,y,w,y+h,paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(WHITE);paint.setTextSize(dp(23));
        c.drawText("⊞",dp(24),y+dp(30),paint);
        c.drawText("⌄",w-dp(25),y+dp(30),paint);
        if("menu".equals(mode)){drawToolbarTitle(c,"القائمة",w,y);return;}
        if("edit".equals(mode)){drawToolbarTitle(c,"تعديل النص",w,y);return;}
        if("library".equals(mode)){drawToolbarTitle(c,"النصوص المحفوظة",w,y);return;}
        List<String> labels=sensitive?Collections.emptyList():suggestions;
        float usable=w-dp(100);
        for(int i=0;i<3;i++){
            String word=i<labels.size()?labels.get(i):"";
            if(word.isEmpty())continue;
            paint.setColor(i==1?prefs.highlightColor():WHITE);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif",i==1?1:0));
            paint.setTextSize(dp(i==1?17:15));
            drawOneLine(c,word,dp(50)+(i+.5f)*usable/3,y+dp(28),usable/3-dp(10));
            if(i<2){paint.setColor(Color.DKGRAY);c.drawRect(dp(50)+(i+1)*usable/3,y+dp(12),dp(51)+(i+1)*usable/3,y+dp(36),paint);}
        }
    }
    private void drawToolbarTitle(Canvas c,String label,float w,float top){
        paint.setTextSize(dp(17));paint.setColor(WHITE);
        c.drawText(label,w/2,top+dp(29),paint);
    }
    private void layoutCells(int width){
        cells.clear();if(width<=0)return;
        previewTop=dp(54);toolbarHeight=dp(46);keyboardStart=previewTop+toolbarHeight;
        if("menu".equals(mode)){layoutMenu(width);return;}
        if("library".equals(mode)){layoutLibrary(width);return;}
        if("edit".equals(mode)){layoutEdit(width);return;}
        float y=keyboardStart,margin=dp(4),gap=dp(4);
        int actualWidth=oneHanded==0?width:Math.round(width*.79f);
        float startX=oneHanded==2?width-actualWidth:0;
        for(KeyboardLayout.Row row:KeyboardLayout.make(arabic,mode,upper,prefs.numberRow())){
            float height=dp(row.height*prefs.heightPercent()/100f);
            float inset=row.inset==0?0:row.inset*actualWidth/12;
            float weights=0;for(KeyboardLayout.Key k:row.keys)weights+=k.weight;
            float available=actualWidth-2*margin-2*inset-gap*(row.keys.size()-1);
            float x=startX+margin+inset;
            for(KeyboardLayout.Key original:row.keys){
                KeyboardLayout.Key k=original.code.equals("↵")
                    ?new KeyboardLayout.Key("↵",enterLabel,"",original.weight,true):original;
                float keyWidth=available*k.weight/weights;
                cells.add(new Cell(k,new RectF(x,y+dp(2),x+keyWidth,y+height-dp(4))));
                x+=keyWidth+gap;
            }
            y+=height;
        }
    }
    private KeyboardLayout.Key action(String code,String title){return new KeyboardLayout.Key(code,title,"",1,true);}
    private void layoutMenu(int width){
        String[][] labels={
            {"menu:library","النصوص"},{"menu:theme","المظهر"},{"menu:size","تغيير الحجم"},
            {"menu:left","بيد واحدة: يسار"},{"menu:right","بيد واحدة: يمين"},{"menu:edit","تعديل النص"},
            {"menu:diacritics","الحركات"},{"menu:lang","عربي / English"},{"menu:undo","تراجع"},
            {"menu:settings","الإعدادات"},{"menu:back","العودة"}};
        float margin=dp(6),gap=dp(6),col=(width-margin*2-gap*2)/3f;
        for(int i=0;i<labels.length;i++){
            float x=margin+(i%3)*(col+gap);
            float y=keyboardStart+dp(6)+(i/3)*dp(65);
            cells.add(new Cell(action(labels[i][0],labels[i][1]),new RectF(x,y,x+col,y+dp(58))));
        }
    }
    private void layoutEdit(int width){
        String[][] actions={{"edit:left","←"},{"edit:right","→"},{"edit:up","↑"},
            {"edit:down","↓"},{"edit:all","تحديد الكل"},{"edit:copy","نسخ"},
            {"edit:cut","قص"},{"edit:paste","لصق"},{"edit:back","عودة"}};
        float gap=dp(6),margin=dp(6),col=(width-2*margin-2*gap)/3f;
        for(int i=0;i<actions.length;i++){
            float x=margin+(i%3)*(col+gap),y=keyboardStart+dp(9)+(i/3)*dp(87);
            cells.add(new Cell(action(actions[i][0],actions[i][1]),new RectF(x,y,x+col,y+dp(76))));
        }
    }
    private void layoutLibrary(int width){
        int height=dp(280);
        float y=keyboardStart+dp(4),margin=dp(4);
        cells.add(new Cell(action("lib:back","‹ العودة"),new RectF(margin,y,dp(89),y+dp(32))));
        cells.add(new Cell(action("lib:prev","‹"),new RectF(dp(98),y,dp(140),y+dp(32))));
        cells.add(new Cell(action("lib:next","›"),new RectF(width-dp(45),y,width-margin,y+dp(32))));
        cells.add(new Cell(action("lib:search","بحث"),new RectF(dp(145),y,width-dp(50),y+dp(32))));
        y+=dp(37);
        int maxGroups=Math.min(4,groups.size());
        float groupW=(width-2*margin-(maxGroups-1)*dp(4))/(float)Math.max(maxGroups,1);
        for(int i=0;i<maxGroups;i++){
            cells.add(new Cell(action("group:"+i,groups.get(i)),new RectF(margin+i*(groupW+dp(4)),y,margin+i*(groupW+dp(4))+groupW,y+dp(29))));
        }
        y+=dp(34);
        for(int i=0;i<3;i++){
            int index=(selectedType+i)%types.length;
            float cellW=(width-dp(16))/3f;
            cells.add(new Cell(action("type:"+index,types[index]),new RectF(margin+i*(cellW+dp(4)),y,margin+i*(cellW+dp(4))+cellW,y+dp(28))));
        }
        y+=dp(34);
        ArrayList<Snippet> filtered=new ArrayList<>();
        for(Snippet s:snippets)if((selectedGroup==0||s.category.equals(groups.get(selectedGroup))))
            filtered.add(s);
        if(selectedType!=0)filtered.removeIf(s->!typeFor(s).equals(types[selectedType]));
        filtered.sort((a,b)->{
            if(a.pinned!=b.pinned)return a.pinned?-1:1;
            return Long.compare(b.lastUsed,a.lastUsed);
        });
        libraryPage=Math.min(libraryPage,Math.max(0,(filtered.size()-1)/3));
        int first=libraryPage*3;
        for(int i=0;i<3&&first+i<filtered.size();i++){
            Snippet item=filtered.get(first+i);
            cells.add(new Cell(action("snippet:"+item.id,item.title),new RectF(margin,y+i*dp(52),width-margin,y+(i+1)*dp(52)-dp(4)),item));
        }
    }
    private String typeFor(Snippet s){return s.type;}
    private Cell hit(float x,float y){
        Cell nearest=null;float best=Float.MAX_VALUE;
        for(Cell cell:cells){
            float dx=Math.max(cell.rect.left-x,Math.max(0,x-cell.rect.right));
            float dy=Math.max(cell.rect.top-y,Math.max(0,y-cell.rect.bottom));
            float distance=dx*dx+dy*dy;
            if(distance<best){nearest=cell;best=distance;}
        }
        return nearest;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(previewSnippet!=null){
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||
               action==MotionEvent.ACTION_CANCEL){
                int id=event.getPointerId(event.getActionIndex());
                Press prior=fingers.get(id);
                if(prior!=null&&prior.timer!=null)handler.removeCallbacks(prior.timer);
                fingers.remove(id);invalidate();return true;
            }
            if(action==MotionEvent.ACTION_DOWN){
                float y=event.getY(),x=event.getX();
                if(y>getHeight()-dp(66)){
                    int index=Math.max(0,Math.min(2,(int)(x/(getWidth()/3f))));
                    if(listener!=null)listener.onSnippetPreviewAction(previewSnippet,
                        index==0?"insert":index==1?"copy":"pin");
                }
                previewSnippet=null;invalidate();
            }
            return true;
        }
        int index=event.getActionIndex();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            float x=event.getX(index),y=event.getY(index);
            int id=event.getPointerId(index);
            if(y<keyboardStart&&y>=previewTop){toolbarTap(x);return true;}
            Cell selected=hit(x,y);if(selected==null)return true;
            Press p=new Press(id,selected,x,y);fingers.put(id,p);
            if(prefs.vibrate()&&prefs.vibrationStrength()>0){
                int strength=prefs.vibrationStrength();
                int pattern=strength==1?HapticFeedbackConstants.KEYBOARD_PRESS:
                    strength==2?HapticFeedbackConstants.VIRTUAL_KEY:HapticFeedbackConstants.LONG_PRESS;
                // HapticFeedback avoids requesting VIBRATE, which the specification forbids.
                performHapticFeedback(pattern);
            }
            p.timer=()->onHold(p);
            handler.postDelayed(p.timer,300);
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            for(int i=0;i<event.getPointerCount();i++){
                Press p=fingers.get(event.getPointerId(i));if(p==null)continue;
                float x=event.getX(i);
                if(Math.abs(x-p.x)>dp(18))p.dragged=true;
                if(p.longHeld&&!p.cell.key.longOptions().isEmpty()){
                    int count=p.cell.key.longOptions().size();
                    p.optionIndex=Math.max(0,Math.min(count-1,(int)((x-(p.cell.rect.centerX()-dp(22)*count/2f))/dp(22))));
                }
                if(p.cell.key.code.equals("space")&&p.longHeld){
                    int units=(int)((x-p.x)/dp(18));
                    if(units!=p.cursorSteps){if(listener!=null)listener.onSpaceSwipe(true,units-p.cursorSteps);p.cursorSteps=units;}
                }
            }
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){
            int id=event.getPointerId(index),pos=fingers.indexOfKey(id);
            if(pos<0)return true;
            Press p=fingers.valueAt(pos);
            handler.removeCallbacks(p.timer);fingers.remove(id);
            if(p.cell.snippet!=null){
                if(listener!=null&&!p.longHeld)listener.onSnippet(p.cell.snippet,false);
            } else if(!p.repeat){
                String code=p.cell.key.code;
                if(p.longHeld&&!p.cell.key.longOptions().isEmpty())
                    code=p.cell.key.longOptions().get(p.optionIndex);
                else if(code.equals("space")&&p.dragged&&!p.longHeld){
                    if(listener!=null)listener.onSpaceSwipe(false,0);
                    invalidate();return true;
                }else if(code.equals("space")&&p.longHeld){invalidate();return true;}
                dispatch(code);
            }
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){stopAll();invalidate();return true;}
        return true;
    }
    private void toolbarTap(float x){
        if(x<dp(51)){dispatch(sensitive?"menu:settings":"menu:open");return;}
        if(x>getWidth()-dp(50)){dispatch("menu:library");return;}
        if(sensitive)return;
        float region=getWidth()-dp(100);
        int i=Math.max(0,Math.min(2,(int)((x-dp(50))/(region/3f))));
        if(i<suggestions.size()&&listener!=null)listener.onSuggestion(suggestions.get(i));
    }
    private void dispatch(String code){
        if(code.startsWith("group:")){selectedGroup=Integer.parseInt(code.substring(6));libraryPage=0;refresh();return;}
        if(code.startsWith("type:")){selectedType=Integer.parseInt(code.substring(5));libraryPage=0;refresh();return;}
        if(code.equals("lib:next")){libraryPage++;refresh();return;}
        if(code.equals("lib:prev")){libraryPage=Math.max(0,libraryPage-1);refresh();return;}
        if(listener!=null)listener.onKey(code);
    }
    private void onHold(Press p){
        if(fingers.get(p.pointer)!=p)return;
        p.longHeld=true;
        if(p.cell.snippet!=null){previewSnippet=p.cell.snippet;}
        else if("⌫".equals(p.cell.key.code)){
            p.repeat=true;
            Runnable r=new Runnable(){int count=0;@Override public void run(){
                if(fingers.get(p.pointer)!=p)return;
                if(listener!=null)listener.onKey(count++>14?"deleteWord":"⌫");
                handler.postDelayed(this,Math.max(35,95-count*3));
            }};
            p.timer=r;handler.post(r);
        }
        invalidate();
    }
    private void drawPressBubbles(Canvas c){
        for(int i=0;i<fingers.size();i++){
            Press p=fingers.valueAt(i);
            if(sensitive||p.cell.snippet!=null)continue;
            RectF key=p.cell.rect;
            List<String> choices=p.longHeld?p.cell.key.longOptions():Collections.emptyList();
            if(!choices.isEmpty()){
                float cellW=dp(26),width=choices.size()*cellW+dp(8);
                float left=Math.max(dp(3),Math.min(getWidth()-width,key.centerX()-width/2));
                float top=Math.max(dp(2),key.top-dp(53));
                paint.setColor(prefs.keyColor());scratch.set(left,top,left+width,top+dp(43));
                c.drawRoundRect(scratch,dp(9),dp(9),paint);
                paint.setColor(WHITE);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(19));
                for(int j=0;j<choices.size();j++){
                    if(j==p.optionIndex){paint.setColor(prefs.highlightColor());
                        c.drawRoundRect(new RectF(left+dp(4)+j*cellW,top+dp(2),left+dp(4)+(j+1)*cellW,top+dp(41)),dp(6),dp(6),paint);}
                    paint.setColor(WHITE);String shown=choices.get(j);
                    if(shown.length()==1&&shown.charAt(0)>='\u064B'&&shown.charAt(0)<='\u065F')
                        shown="◌"+shown;
                    c.drawText(shown,left+dp(4)+(j+.5f)*cellW,top+dp(28),paint);
                }
            }else{
                float left=Math.max(dp(2),Math.min(getWidth()-dp(53),key.centerX()-dp(27)));
                scratch.set(left,key.top-dp(47),left+dp(54),key.top+dp(4));
                paint.setColor(prefs.keyColor());c.drawRoundRect(scratch,dp(9),dp(9),paint);
                paint.setColor(WHITE);paint.setTextSize(dp(29));paint.setTextAlign(Paint.Align.CENTER);
                c.drawText(p.cell.key.label,left+dp(27),key.top-dp(12),paint);
            }
        }
    }
    private void stopAll(){
        for(int i=0;i<fingers.size();i++){Press p=fingers.valueAt(i);if(p.timer!=null)handler.removeCallbacks(p.timer);}
        fingers.clear();
    }
    @Override protected void onDetachedFromWindow(){stopAll();if(layoutAnimator!=null)layoutAnimator.cancel();super.onDetachedFromWindow();}
}