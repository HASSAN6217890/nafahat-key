package com.nafahat.key;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import java.util.ArrayList;
import java.util.List;

/** Card grid that replaces the keys (the ⊞ menu, its sub-panels, more suggestions). */
public final class GridPanel extends View {
    public static final class Cell {
        public final int id;
        public final String title;
        public final String glyph;
        public boolean active;
        public boolean enabled = true;
        public boolean repeat;
        public Object data;
        final RectF rect = new RectF();

        public Cell(int id, String title, String glyph) {
            this.id = id;
            this.title = title;
            this.glyph = glyph;
        }
    }

    public interface Listener { void onCell(Cell cell); }

    private final float density;
    private final float sp;
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint glyph = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final List<Cell> cells = new ArrayList<>();
    private Theme theme = Theme.nafahat();
    private Listener listener;
    private int columns = 3;
    private Cell pressed;
    private boolean dirty = true;
    private int fixedHeight;
    private String heading;
    private final Runnable repeater = new Runnable() {
        @Override public void run() {
            if (pressed != null && pressed.repeat && listener != null) {
                listener.onCell(pressed);
                postDelayed(this, 70);
            }
        }
    };
    private boolean repeated;

    public GridPanel(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        sp = context.getResources().getDisplayMetrics().scaledDensity;
        title.setTextAlign(Paint.Align.CENTER);
        glyph.setTextAlign(Paint.Align.CENTER);
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0f);
        glyph.setColorFilter(new ColorMatrixColorFilter(m));
    }

    public void setListener(Listener l) { listener = l; }
    public void setTheme(Theme t) { theme = t; invalidate(); }

    public void setPanelHeight(int px) {
        if (px == fixedHeight) return;
        fixedHeight = px;
        requestLayout();
    }

    public void setCells(List<Cell> list, int cols, String headingText) {
        cells.clear();
        cells.addAll(list);
        columns = Math.max(1, cols);
        heading = headingText;
        dirty = true;
        invalidate();
    }

    public List<Cell> cells() { return cells; }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), fixedHeight > 0 ? fixedHeight : MeasureSpec.getSize(h));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { dirty = true; }

    private void layoutCells() {
        dirty = false;
        float W = getWidth(), H = getHeight();
        float pad = 6 * density;
        float top = heading != null ? 30 * density : pad;
        int rows = Math.max(1, (cells.size() + columns - 1) / columns);
        float cw = (W - pad * 2) / columns;
        float ch = Math.min((H - top - pad) / rows, 96 * density);
        for (int i = 0; i < cells.size(); i++) {
            int r = i / columns, c = i % columns;
            float right = W - pad - c * cw; // right-to-left
            cells.get(i).rect.set(right - cw + 3 * density, top + r * ch + 3 * density, right - 3 * density,
                top + (r + 1) * ch - 3 * density);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (dirty) layoutCells();
        canvas.drawColor(theme.background);
        if (heading != null) {
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextSize(14 * sp);
            title.setColor(theme.hint | 0xFF000000);
            title.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(heading, getWidth() - 12 * density, 21 * density, title);
            title.setTextAlign(Paint.Align.CENTER);
        }
        float r = 12 * density;
        for (Cell c : cells) {
            int color = c.active ? theme.accent : (c == pressed ? theme.keyPressed : theme.card);
            card.setColor(color);
            canvas.drawRoundRect(c.rect, r, r, card);
            int fg = c.active ? 0xFFFFFFFF : theme.cardText;
            int alpha = c.enabled ? 255 : 90;
            boolean hasGlyph = c.glyph != null && !c.glyph.isEmpty();
            float cy = c.rect.centerY();
            if (hasGlyph && c.rect.height() > 52 * density) {
                glyph.setTextSize(22 * sp);
                glyph.setColor(fg);
                glyph.setAlpha(alpha);
                Paint.FontMetrics fm = glyph.getFontMetrics();
                canvas.drawText(c.glyph, c.rect.centerX(), cy - 9 * density - (fm.ascent + fm.descent) / 2f, glyph);
                cy += 15 * density;
            } else if (hasGlyph) {
                cy = c.rect.centerY();
            }
            title.setTypeface(Typeface.DEFAULT);
            title.setTextSize((hasGlyph && c.rect.height() > 52 * density ? 13 : 16) * sp);
            title.setColor(fg);
            title.setAlpha(alpha);
            String t = c.title;
            if (hasGlyph && c.rect.height() <= 52 * density) t = c.glyph + "  " + c.title;
            CharSequence s = TextUtils.ellipsize(t, title, c.rect.width() - 8 * density, TextUtils.TruncateAt.END);
            Paint.FontMetrics fm = title.getFontMetrics();
            canvas.drawText(s, 0, s.length(), c.rect.centerX(), cy - (fm.ascent + fm.descent) / 2f, title);
        }
    }

    private Cell hit(float x, float y) {
        if (dirty) layoutCells();
        for (Cell c : cells) if (c.rect.contains(x, y)) return c;
        return null;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                Cell c = hit(e.getX(), e.getY());
                pressed = c != null && c.enabled ? c : null;
                repeated = false;
                if (pressed != null) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    if (pressed.repeat) {
                        repeated = true;
                        if (listener != null) listener.onCell(pressed);
                        postDelayed(repeater, 380);
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (pressed != null && !pressed.rect.contains(e.getX(), e.getY())) {
                    removeCallbacks(repeater);
                    pressed = null;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP: {
                removeCallbacks(repeater);
                Cell c = pressed;
                pressed = null;
                invalidate();
                if (c != null && !repeated && listener != null) listener.onCell(c);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(repeater);
                pressed = null;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override public boolean onHoverEvent(MotionEvent e) {
        AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null || !am.isTouchExplorationEnabled()) return super.onHoverEvent(e);
        Cell c = hit(e.getX(), e.getY());
        if (e.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
            if (c != null && c.enabled && listener != null) listener.onCell(c);
        } else if (c != null && c != pressed) {
            pressed = c;
            announceForAccessibility(c.title);
            invalidate();
        }
        return true;
    }
}
