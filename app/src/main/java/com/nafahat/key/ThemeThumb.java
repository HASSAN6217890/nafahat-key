package com.nafahat.key;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Small preview card of a theme for the gallery in the settings screen. */
public final class ThemeThumb extends View {
    private final Theme theme;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean selected;
    private int artStrength = 60;

    public ThemeThumb(Context c, Theme theme) {
        super(c);
        this.theme = theme;
        density = c.getResources().getDisplayMetrics().density;
    }

    public void setSelectedTheme(boolean yes) { selected = yes; invalidate(); }
    public void setArtStrength(int v) { artStrength = v; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), rad = 10 * density;
        c.save();
        android.graphics.Path clip = new android.graphics.Path();
        r.set(0, 0, w, h);
        clip.addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW);
        c.clipPath(clip);
        c.drawColor(theme.background);
        ThemeArt.draw(c, w, h, theme.art, artStrength);
        float pad = 4 * density, top = h * 0.22f;
        paint.setColor(theme.accent);
        r.set(w * 0.38f, h * 0.08f, w * 0.62f, h * 0.08f + 3 * density);
        c.drawRoundRect(r, 2 * density, 2 * density, paint);
        int[] perRow = {7, 6, 5};
        float rowH = (h - top - pad) / 4f;
        for (int row = 0; row < 3; row++) {
            int n = perRow[row];
            float kw = (w - pad * 2) / 7f;
            float start = (w - kw * n) / 2f;
            for (int i = 0; i < n; i++) {
                paint.setColor(theme.key);
                r.set(start + i * kw + 1.5f * density, top + row * rowH + 1.5f * density,
                    start + (i + 1) * kw - 1.5f * density, top + (row + 1) * rowH - 1.5f * density);
                c.drawRoundRect(r, 2.5f * density, 2.5f * density, paint);
            }
        }
        float y = top + 3 * rowH;
        float kw = (w - pad * 2) / 7f;
        paint.setColor(theme.function);
        r.set(pad + 1.5f * density, y + 1.5f * density, pad + kw * 1.4f, y + rowH - 1.5f * density);
        c.drawRoundRect(r, rowH / 2f, rowH / 2f, paint);
        paint.setColor(theme.key);
        r.set(pad + kw * 1.6f, y + 1.5f * density, w - pad - kw * 1.6f, y + rowH - 1.5f * density);
        c.drawRoundRect(r, 2.5f * density, 2.5f * density, paint);
        paint.setColor(theme.function);
        r.set(w - pad - kw * 1.4f, y + 1.5f * density, w - pad - 1.5f * density, y + rowH - 1.5f * density);
        c.drawRoundRect(r, rowH / 2f, rowH / 2f, paint);
        c.restore();
        if (selected) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * density);
            paint.setColor(Theme.GOLD);
            r.set(1.5f * density, 1.5f * density, w - 1.5f * density, h - 1.5f * density);
            c.drawRoundRect(r, rad, rad, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
