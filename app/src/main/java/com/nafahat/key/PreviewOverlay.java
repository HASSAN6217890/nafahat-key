package com.nafahat.key;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * Transparent layer above the whole input view. Draws the key preview bubble and the
 * long-press options row in the extra window space above the keys. Never takes touches.
 */
public final class PreviewOverlay extends View {
    private Theme theme = Theme.nafahat();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private boolean previewShown;
    private final RectF previewRect = new RectF();
    private String previewText;
    private float previewTextPx;
    private float previewTextY;

    private boolean popupShown;
    private final RectF popupRect = new RectF();
    private String[] popupItems;
    private int popupSelected;
    private float popupCell;
    private float popupTextPx;
    private final RectF tmp = new RectF();

    public PreviewOverlay(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    public void setTheme(Theme t) { theme = t; invalidate(); }

    @Override public boolean onTouchEvent(MotionEvent event) { return false; }

    /** keyRect is in overlay coordinates. */
    public void showPreview(RectF keyRect, String label, float textPx) {
        float w = Math.max(keyRect.width() * 1.12f, 40 * density);
        float cx = keyRect.centerX();
        float left = Math.max(2 * density, Math.min(getWidth() - w - 2 * density, cx - w / 2f));
        float extra = keyRect.height() * 1.25f;
        previewRect.set(left, keyRect.top - extra, left + w, keyRect.bottom);
        previewText = label;
        previewTextPx = textPx * 1.35f;
        previewTextY = keyRect.top - extra / 2f;
        previewShown = true;
        invalidate();
    }

    public void hidePreview() {
        if (!previewShown) return;
        previewShown = false;
        invalidate();
    }

    /** anchor is the key rect in overlay coordinates. */
    public void showPopup(RectF anchor, String[] items, float textPx) {
        popupItems = items;
        popupSelected = 0;
        popupCell = Math.max(anchor.width(), 42 * density);
        float total = popupCell * items.length;
        float left = anchor.centerX() - popupCell / 2f;
        if (left + total > getWidth() - 2 * density) left = getWidth() - 2 * density - total;
        if (left < 2 * density) left = 2 * density;
        float h = Math.max(anchor.height(), 44 * density);
        popupRect.set(left, anchor.top - h - 4 * density, left + total, anchor.top - 4 * density);
        popupTextPx = textPx;
        popupShown = true;
        previewShown = false;
        invalidate();
    }

    /** Selects the option under overlay x; returns the selected index. */
    public int selectAt(float overlayX) {
        if (!popupShown || popupItems == null) return 0;
        int idx = (int) Math.floor((overlayX - popupRect.left) / popupCell);
        idx = Math.max(0, Math.min(popupItems.length - 1, idx));
        if (idx != popupSelected) { popupSelected = idx; invalidate(); }
        return idx;
    }

    public int popupSelected() { return popupSelected; }

    public boolean popupShown() { return popupShown; }

    public void hidePopup() {
        if (!popupShown) return;
        popupShown = false;
        popupItems = null;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float r = 10 * density;
        if (previewShown && previewText != null) {
            fill.setColor(theme.bubble);
            fill.setShadowLayer(4 * density, 0, 1 * density, 0x55000000);
            canvas.drawRoundRect(previewRect, r, r, fill);
            fill.clearShadowLayer();
            text.setColor(theme.bubbleText);
            text.setTextSize(fit(previewText, previewTextPx, previewRect.width() - 6 * density));
            Paint.FontMetrics fm = text.getFontMetrics();
            canvas.drawText(previewText, previewRect.centerX(), previewTextY - (fm.ascent + fm.descent) / 2f, text);
        }
        if (popupShown && popupItems != null) {
            fill.setColor(theme.bubble);
            fill.setShadowLayer(5 * density, 0, 1 * density, 0x66000000);
            canvas.drawRoundRect(popupRect, r, r, fill);
            fill.clearShadowLayer();
            for (int i = 0; i < popupItems.length; i++) {
                tmp.set(popupRect.left + i * popupCell, popupRect.top, popupRect.left + (i + 1) * popupCell, popupRect.bottom);
                if (i == popupSelected) {
                    fill.setColor(theme.accent);
                    tmp.inset(2 * density, 2 * density);
                    canvas.drawRoundRect(tmp, r * 0.8f, r * 0.8f, fill);
                }
                text.setColor(i == popupSelected ? 0xFFFFFFFF : theme.bubbleText);
                String s = popupItems[i];
                text.setTextSize(fit(s, popupTextPx, popupCell - 6 * density));
                Paint.FontMetrics fm = text.getFontMetrics();
                canvas.drawText(s, tmp.centerX(), popupRect.centerY() - (fm.ascent + fm.descent) / 2f, text);
            }
        }
    }

    private float fit(String s, float size, float width) {
        text.setTextSize(size);
        float w = text.measureText(s);
        if (w <= width || w <= 0) return size;
        return Math.max(9 * density, size * width / w);
    }
}
