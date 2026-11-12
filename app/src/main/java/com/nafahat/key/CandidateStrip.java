package com.nafahat.key;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * Top strip: ⊞ on the left, three suggestions separated by thin lines (the middle one wide
 * and gold = most likely / the correction), ⌄ on the right. Also shows action chips for
 * modes such as library search, template filling and resizing.
 */
public final class CandidateStrip extends View {
    public static final int SUGGESTION = 0;
    public static final int LITERAL = 1;
    public static final int UNDO = 2;
    public static final int SNIPPET = 3;
    public static final int ACTION = 4;
    public static final int INFO = 5;
    public static final int PHRASE = 6;
    public static final int NEXT = 7;

    public static final class Item {
        public final String text;
        public final int kind;
        public final Object data;
        public float weight = 1f;
        public boolean strong;

        public Item(String text, int kind, Object data) {
            this.text = text;
            this.kind = kind;
            this.data = data;
        }
    }

    public interface Listener {
        void onStripItem(Item item);
        void onMenuButton();
        void onCollapseButton();
    }

    private final float density;
    private final float sp;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF menuRect = new RectF();
    private final RectF collapseRect = new RectF();
    private final List<RectF> rects = new ArrayList<>();
    private final List<Item> slots = new ArrayList<>();
    private Theme theme = Theme.nafahat();
    private Listener listener;
    private List<Item> items = new ArrayList<>();
    private boolean chipMode;
    private boolean rtl = true;
    private boolean menuActive;
    private boolean menuEnabled = true;
    private int pressed = -3; // -1 menu, -2 collapse, >=0 slot
    private int heightPx;

    public CandidateStrip(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        sp = context.getResources().getDisplayMetrics().scaledDensity;
        text.setTextAlign(Paint.Align.CENTER);
        heightPx = Math.round(46 * density);
        setContentDescription("شريط الاقتراحات");
    }

    public void setListener(Listener l) { listener = l; }
    public void setTheme(Theme t) { theme = t; invalidate(); }
    public void setMenuActive(boolean yes) { menuActive = yes; invalidate(); }
    public void setMenuEnabled(boolean yes) { menuEnabled = yes; invalidate(); }

    /** Suggestions: items[0] goes in the middle; rtl puts items[1] on the right. */
    public void setSuggestions(List<Item> list, boolean rtlOrder) {
        items = list == null ? new ArrayList<>() : list;
        chipMode = false;
        rtl = rtlOrder;
        layoutSlots();
        invalidate();
    }

    /** Chips laid out right-to-left with weights. */
    public void setChips(List<Item> list) {
        items = list == null ? new ArrayList<>() : list;
        chipMode = true;
        layoutSlots();
        invalidate();
    }

    public List<Item> currentItems() { return items; }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), heightPx);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { layoutSlots(); }

    private void layoutSlots() {
        rects.clear();
        slots.clear();
        float W = getWidth(), H = getHeight();
        if (W == 0) return;
        float btn = 46 * density;
        menuRect.set(0, 0, btn, H);
        collapseRect.set(W - btn, 0, W, H);
        float left = btn, right = W - btn;
        if (!chipMode) {
            float total = right - left;
            float side = total / 3.4f;
            float centre = total - 2 * side;
            RectF leftR = new RectF(left, 0, left + side, H);
            RectF midR = new RectF(left + side, 0, left + side + centre, H);
            RectF rightR = new RectF(right - side, 0, right, H);
            Item a = items.size() > 0 ? items.get(0) : null;
            Item b = items.size() > 1 ? items.get(1) : null;
            Item c = items.size() > 2 ? items.get(2) : null;
            Item l = rtl ? c : b;
            Item r = rtl ? b : c;
            if (l != null) { rects.add(leftR); slots.add(l); }
            if (a != null) { rects.add(midR); slots.add(a); }
            if (r != null) { rects.add(rightR); slots.add(r); }
        } else {
            float sum = 0;
            for (Item it : items) sum += it.weight;
            if (sum <= 0) return;
            float x = right;
            float unit = (right - left) / sum;
            for (Item it : items) {
                float w = it.weight * unit;
                rects.add(new RectF(x - w, 0, x, H));
                slots.add(it);
                x -= w;
            }
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (rects.isEmpty() && !items.isEmpty()) layoutSlots();
        canvas.drawColor(theme.strip);
        float H = getHeight();
        // ⊞ menu button
        if (menuEnabled) {
            paint.setStyle(Paint.Style.FILL);
            if (menuActive || pressed == -1) {
                paint.setColor(menuActive ? theme.accent : theme.keyPressed);
                canvas.drawCircle(menuRect.centerX(), menuRect.centerY(), 17 * density, paint);
            }
            paint.setColor(menuActive ? 0xFFFFFFFF : theme.stripText);
            float s = 5.5f * density, g = 1.5f * density, cx = menuRect.centerX(), cy = menuRect.centerY();
            float rr = 1.5f * density;
            canvas.drawRoundRect(cx - s - g, cy - s - g, cx - g, cy - g, rr, rr, paint);
            canvas.drawRoundRect(cx + g, cy - s - g, cx + s + g, cy - g, rr, rr, paint);
            canvas.drawRoundRect(cx - s - g, cy + g, cx - g, cy + s + g, rr, rr, paint);
            canvas.drawRoundRect(cx + g, cy + g, cx + s + g, cy + s + g, rr, rr, paint);
        }
        // ⌄ button
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(theme.stripDim);
        float ccx = collapseRect.centerX(), ccy = collapseRect.centerY();
        if (pressed == -2) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(theme.keyPressed);
            canvas.drawCircle(ccx, ccy, 15 * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(theme.stripDim);
        }
        canvas.drawCircle(ccx, ccy, 13 * density, paint);
        path.reset();
        path.moveTo(ccx - 5 * density, ccy - 2 * density);
        path.lineTo(ccx, ccy + 3 * density);
        path.lineTo(ccx + 5 * density, ccy - 2 * density);
        paint.setStrokeWidth(2f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(path, paint);
        // slots
        for (int i = 0; i < rects.size(); i++) {
            RectF r = rects.get(i);
            Item it = slots.get(i);
            if (pressed == i && it.kind != INFO) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(theme.keyPressed);
                canvas.drawRoundRect(r.left + 2 * density, 5 * density, r.right - 2 * density, H - 5 * density,
                    8 * density, 8 * density, paint);
            }
            boolean centre = !chipMode && it == (items.isEmpty() ? null : items.get(0));
            boolean strong = it.strong || centre;
            if (chipMode && it.kind == ACTION) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1f * density);
                paint.setColor(it.strong ? theme.accent : theme.divider);
                canvas.drawRoundRect(r.left + 4 * density, 8 * density, r.right - 4 * density, H - 8 * density,
                    14 * density, 14 * density, paint);
            }
            text.setTypeface(strong ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            text.setTextSize((chipMode ? 14 : 17) * sp);
            text.setColor(strong ? theme.accent : (it.kind == INFO ? theme.stripText : theme.stripDim));
            if (!chipMode && !centre) text.setColor(theme.stripText);
            if (centre) text.setColor(theme.accent);
            CharSequence s = TextUtils.ellipsize(it.text, text, r.width() - 12 * density, TextUtils.TruncateAt.END);
            Paint.FontMetrics fm = text.getFontMetrics();
            canvas.drawText(s, 0, s.length(), r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, text);
            if (!chipMode && i < rects.size() - 1) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(theme.divider);
                canvas.drawRect(r.right - 0.5f * density, 13 * density, r.right + 0.5f * density, H - 13 * density, paint);
            }
        }
    }

    private int hit(float x) {
        if (menuEnabled && menuRect.contains(x, getHeight() / 2f)) return -1;
        if (collapseRect.contains(x, getHeight() / 2f)) return -2;
        for (int i = 0; i < rects.size(); i++) {
            RectF r = rects.get(i);
            if (x >= r.left && x < r.right) return slots.get(i).kind == INFO ? -3 : i;
        }
        return -3;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = hit(e.getX());
                if (pressed != -3) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (hit(e.getX()) != pressed) { pressed = -3; invalidate(); }
                return true;
            case MotionEvent.ACTION_UP: {
                int p = pressed;
                pressed = -3;
                invalidate();
                if (listener == null) return true;
                if (p == -1) listener.onMenuButton();
                else if (p == -2) listener.onCollapseButton();
                else if (p >= 0 && p < slots.size()) listener.onStripItem(slots.get(p));
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressed = -3;
                invalidate();
                return true;
            default:
                return true;
        }
    }
}
