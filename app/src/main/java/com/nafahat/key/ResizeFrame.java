package com.nafahat.key;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * On-keyboard resize mode (spec 0.4 section 1): a gold frame around the keys with handles —
 * top / bottom for height and position, left / right for width, ✥ in the middle to move —
 * plus «موافق» and «إعادة تعيين». Dragging reports raw-pixel deltas to the listener.
 */
public final class ResizeFrame extends View {
    public static final int TOP = 1, BOTTOM = 2, LEFT = 3, RIGHT = 4, MOVE = 5;

    public interface Listener {
        void onResizeDrag(int handle, float dx, float dy);
        void onResizeDone();
        void onResizeReset();
    }

    private final float density;
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF area = new RectF();
    private final RectF ok = new RectF();
    private final RectF reset = new RectF();
    private final RectF tmp = new RectF();
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Listener listener;
    private Theme theme = Theme.nafahat();
    private int active;
    private int pressedButton; // 1 ok, 2 reset
    private float lastX, lastY;

    public ResizeFrame(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(3 * density);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setColor(0xFFFFFFFF);
        icon.setStrokeWidth(2.2f * density);
        icon.setStrokeCap(Paint.Cap.ROUND);
        setContentDescription("وضع تغيير حجم لوحة المفاتيح");
    }

    public void setListener(Listener l) { listener = l; }
    public void setTheme(Theme t) { theme = t; invalidate(); }

    /** Area of the keys in this view's coordinates. */
    public void setArea(RectF r) {
        area.set(r);
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        c.drawColor((theme.background & 0x00FFFFFF) | 0x8C000000);
        float r = 10 * density;
        frame.setColor(Theme.GOLD);
        c.drawRoundRect(area, r, r, frame);
        fill.setColor(Theme.GOLD);
        float hl = 44 * density, ht = 7 * density;
        // top / bottom bars, left / right bars
        bar(c, area.centerX(), area.top, hl, ht);
        bar(c, area.centerX(), area.bottom, hl, ht);
        bar(c, area.left, area.centerY(), ht, hl);
        bar(c, area.right, area.centerY(), ht, hl);
        // ✥ move handle
        float cx = area.centerX(), cy = area.centerY() - 22 * density, rr = 24 * density;
        fill.setColor(active == MOVE ? 0xFFE0A050 : Theme.GOLD);
        c.drawCircle(cx, cy, rr, fill);
        Paint ic = icon;
        float a = 12 * density, h = 4 * density;
        c.drawLine(cx - a, cy, cx + a, cy, ic);
        c.drawLine(cx, cy - a, cx, cy + a, ic);
        for (int i = 0; i < 4; i++) {
            c.save();
            c.rotate(90 * i, cx, cy);
            c.drawLine(cx + a, cy, cx + a - h, cy - h, ic);
            c.drawLine(cx + a, cy, cx + a - h, cy + h, ic);
            c.restore();
        }
        // buttons
        float bw = 104 * density, bh = 38 * density, gap = 10 * density, by = cy + rr + 14 * density;
        ok.set(cx + gap / 2f, by, cx + gap / 2f + bw, by + bh);
        reset.set(cx - gap / 2f - bw, by, cx - gap / 2f, by + bh);
        fill.setColor(pressedButton == 1 ? 0xFFE0A050 : Theme.GOLD);
        c.drawRoundRect(ok, bh / 2f, bh / 2f, fill);
        fill.setColor(pressedButton == 2 ? 0x55FFFFFF : 0x33FFFFFF);
        c.drawRoundRect(reset, bh / 2f, bh / 2f, fill);
        text.setTextSize(15 * getResources().getDisplayMetrics().scaledDensity);
        Paint.FontMetrics fm = text.getFontMetrics();
        float base = by + bh / 2f - (fm.ascent + fm.descent) / 2f;
        text.setColor(0xFFFFFFFF);
        c.drawText("موافق", ok.centerX(), base, text);
        c.drawText("إعادة تعيين", reset.centerX(), base, text);
    }

    private void bar(Canvas c, float cx, float cy, float w, float h) {
        tmp.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        c.drawRoundRect(tmp, h / 2f, h / 2f, fill);
    }

    private int handleAt(float x, float y) {
        float t = 26 * density;
        float cx = area.centerX(), cy = area.centerY() - 22 * density;
        if (Math.hypot(x - cx, y - cy) <= 34 * density) return MOVE;
        if (Math.abs(y - area.top) <= t && x > area.left && x < area.right) return TOP;
        if (Math.abs(y - area.bottom) <= t && x > area.left && x < area.right) return BOTTOM;
        if (Math.abs(x - area.left) <= t) return LEFT;
        if (Math.abs(x - area.right) <= t) return RIGHT;
        if (area.contains(x, y)) return MOVE;
        return 0;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (ok.contains(x, y)) pressedButton = 1;
                else if (reset.contains(x, y)) pressedButton = 2;
                else active = handleAt(x, y);
                lastX = e.getRawX();
                lastY = e.getRawY();
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (active != 0 && listener != null) {
                    float dx = e.getRawX() - lastX, dy = e.getRawY() - lastY;
                    lastX = e.getRawX();
                    lastY = e.getRawY();
                    listener.onResizeDrag(active, dx, dy);
                }
                return true;
            case MotionEvent.ACTION_UP: {
                int b = pressedButton;
                pressedButton = 0;
                active = 0;
                invalidate();
                if (listener != null) {
                    if (b == 1 && ok.contains(x, y)) listener.onResizeDone();
                    else if (b == 2 && reset.contains(x, y)) listener.onResizeReset();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressedButton = 0;
                active = 0;
                invalidate();
                return true;
            default:
                return true;
        }
    }
}
