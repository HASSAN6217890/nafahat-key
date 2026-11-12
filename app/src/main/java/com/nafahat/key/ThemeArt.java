package com.nafahat.key;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Very faint, original vector art for the الروضة and مكة themes (drawn here, no photos).
 * strength 0..100 controls visibility; letters always stay the clearest thing on the keyboard.
 */
public final class ThemeArt {
    private ThemeArt() { }

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final RectF R = new RectF();

    public static void draw(Canvas c, float w, float h, int art, int strength) {
        if (art == 0 || strength <= 0 || w <= 0 || h <= 0) return;
        float k = strength / 100f;
        if (art == 1) rawda(c, w, h, k); else if (art == 2) makkah(c, w, h, k);
    }

    private static int alpha(int color, float a) {
        int al = Math.max(0, Math.min(255, Math.round(255 * a)));
        return (color & 0x00FFFFFF) | (al << 24);
    }

    private static void glow(Canvas c, float cx, float cy, float r, int color, float a) {
        FILL.setShader(new RadialGradient(cx, cy, r, alpha(color, a), alpha(color, 0f), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, FILL);
        FILL.setShader(null);
    }

    /** The green dome with a minaret, centred low on the right. */
    private static void rawda(Canvas c, float w, float h, float k) {
        float cx = w * 0.70f, base = h * 0.99f;
        float dw = h * 0.36f, drumH = h * 0.16f, domeH = h * 0.40f;
        float drumTop = base - drumH;
        glow(c, cx, drumTop - domeH * 0.4f, h * 0.75f, Theme.GOLD, 0.10f * k);
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(alpha(0xFF2E9E62, 0.30f * k));
        c.drawRect(cx - dw / 2f, drumTop, cx + dw / 2f, base, FILL);
        PATH.reset();
        PATH.moveTo(cx - dw * 0.52f, drumTop);
        PATH.cubicTo(cx - dw * 0.66f, drumTop - domeH * 0.55f, cx - dw * 0.20f, drumTop - domeH * 0.82f, cx, drumTop - domeH);
        PATH.cubicTo(cx + dw * 0.20f, drumTop - domeH * 0.82f, cx + dw * 0.66f, drumTop - domeH * 0.55f, cx + dw * 0.52f, drumTop);
        PATH.close();
        FILL.setColor(alpha(0xFF2E9E62, 0.38f * k));
        c.drawPath(PATH, FILL);
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeCap(Paint.Cap.ROUND);
        LINE.setStrokeWidth(Math.max(1.5f, h * 0.008f));
        LINE.setColor(alpha(Theme.GOLD, 0.40f * k));
        float apex = drumTop - domeH;
        c.drawLine(cx, apex, cx, apex - h * 0.09f, LINE);
        R.set(cx - h * 0.03f, apex - h * 0.15f, cx + h * 0.03f, apex - h * 0.09f);
        c.drawArc(R, 200, 220, false, LINE);
        // minaret
        float mx = cx + dw * 1.05f, mw = h * 0.05f, top = base - h * 0.78f;
        FILL.setColor(alpha(0xFF2E9E62, 0.26f * k));
        c.drawRect(mx - mw / 2f, top, mx + mw / 2f, base, FILL);
        c.drawRect(mx - mw, base - h * 0.52f, mx + mw, base - h * 0.49f, FILL);
        PATH.reset();
        PATH.moveTo(mx - mw / 2f, top);
        PATH.lineTo(mx, top - h * 0.12f);
        PATH.lineTo(mx + mw / 2f, top);
        PATH.close();
        c.drawPath(PATH, FILL);
    }

    /** The Kaaba with its gold band and faint circles of tawaf. */
    private static void makkah(Canvas c, float w, float h, float k) {
        float cx = w * 0.68f, base = h * 0.94f;
        float kw = h * 0.40f, kh = h * 0.46f, sx = kw * 0.34f, sy = h * 0.06f;
        glow(c, cx, base - kh / 2f, h * 0.8f, Theme.GOLD, 0.09f * k);
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeWidth(Math.max(1f, h * 0.005f));
        LINE.setColor(alpha(Theme.GOLD, 0.22f * k));
        for (int i = 1; i <= 3; i++) {
            float rx = kw * (0.9f + 0.55f * i), ry = h * (0.05f + 0.03f * i);
            R.set(cx + sx / 2f - rx, base - ry, cx + sx / 2f + rx, base + ry);
            c.drawOval(R, LINE);
        }
        float l = cx - kw / 2f, r = cx + kw / 2f, t = base - kh;
        FILL.setStyle(Paint.Style.FILL);
        // side face
        PATH.reset();
        PATH.moveTo(r, t);
        PATH.lineTo(r + sx, t - sy);
        PATH.lineTo(r + sx, base - sy);
        PATH.lineTo(r, base);
        PATH.close();
        FILL.setColor(alpha(0xFF000000, 0.55f * k));
        c.drawPath(PATH, FILL);
        // top
        PATH.reset();
        PATH.moveTo(l, t);
        PATH.lineTo(l + sx, t - sy);
        PATH.lineTo(r + sx, t - sy);
        PATH.lineTo(r, t);
        PATH.close();
        FILL.setColor(alpha(0xFF2B3634, 0.45f * k));
        c.drawPath(PATH, FILL);
        // front face
        FILL.setColor(alpha(0xFF000000, 0.65f * k));
        c.drawRect(l, t, r, base, FILL);
        // gold band (hizam) on both faces, and the door
        float by = t + kh * 0.22f, bh = kh * 0.06f;
        FILL.setColor(alpha(Theme.GOLD, 0.42f * k));
        c.drawRect(l, by, r, by + bh, FILL);
        PATH.reset();
        PATH.moveTo(r, by);
        PATH.lineTo(r + sx, by - sy);
        PATH.lineTo(r + sx, by - sy + bh);
        PATH.lineTo(r, by + bh);
        PATH.close();
        c.drawPath(PATH, FILL);
        FILL.setColor(alpha(Theme.GOLD, 0.30f * k));
        c.drawRect(r - kw * 0.30f, base - kh * 0.42f, r - kw * 0.12f, base - kh * 0.08f, FILL);
        LINE.setColor(alpha(Theme.GOLD, 0.30f * k));
        c.drawRect(l, t, r, base, LINE);
    }
}
