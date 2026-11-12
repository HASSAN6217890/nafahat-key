package com.nafahat.key;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * The whole key area as one Canvas-drawn view. Layouts are data; switching shift, page or
 * theme only swaps data and calls invalidate(). Every touch goes to the nearest key, every
 * finger is tracked separately, highlight + haptic happen on touch-down.
 */
public final class KeyboardView extends View {

    public interface Listener {
        /** A key was activated; text is the character(s) to insert for CHAR / SPACE keys. */
        void onKey(Key key, String text);
        /** Backspace: repeat 0 on touch-down, then 1, 2, 3... while held. */
        void onDelete(int repeat);
        /** Quick horizontal swipe on the space bar. */
        void onSpaceSwipe();
        /** Long-press on space then drag: relative cursor steps (positive = visually right). */
        void onCursorMove(int steps);
    }

    private static final long LONG_PRESS_MS = 300;
    private static final long REPEAT_START_MS = 400;
    private static final long REPEAT_MS = 60;
    private static final long ANIM_MS = 130;

    private final float density;
    private final float sp;
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmp = new RectF();
    private final int[] loc1 = new int[2];
    private final int[] loc2 = new int[2];

    private Listener listener;
    private PreviewOverlay overlay;
    private Theme theme = Theme.nafahat();
    private Layout layout;
    private final List<Key> keys = new ArrayList<>();
    private Key swapKey;
    private Key exitKey;
    private boolean geometryDirty = true;

    private int fixedHeight;
    private int fontPercent = 100;
    private boolean shifted;
    private boolean capsLock;
    /** EditorInfo action id, or -1 for a plain new line. */
    private int enterAction = -1;
    private boolean previewEnabled = true;
    private boolean hapticEnabled = true;
    private int hapticLevel = 1;
    private int oneHanded;
    private boolean previewOnly;
    private long animStart;

    private static final class Pointer {
        int id;
        Key key;
        float downX, downY, lastX;
        boolean committed;
        boolean popupOpen;
        float popupStartX;
        boolean popupMoved;
        boolean spaceSwipe;
        boolean cursorMode;
        float cursorOriginX;
        int cursorSteps;
        int repeat;
        Runnable longPress;
        Runnable repeater;
    }

    private final SparseArray<Pointer> pointers = new SparseArray<>();
    private Key hoverKey;

    public KeyboardView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        sp = context.getResources().getDisplayMetrics().scaledDensity;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hintPaint.setTextAlign(Paint.Align.LEFT);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        setHapticFeedbackEnabled(true);
        setContentDescription("لوحة مفاتيح نفحات");
    }

    // ------------------------------------------------------------------ configuration
    public void setListener(Listener l) { listener = l; }
    public void setOverlay(PreviewOverlay o) { overlay = o; }
    /** Settings preview: draws only, never sends input. */
    public void setPreviewOnly(boolean yes) { previewOnly = yes; }

    public void setTheme(Theme t) {
        theme = t;
        invalidate();
    }

    public void setLayout(Layout l, boolean animate) {
        if (l == layout) return;
        boolean changed = layout == null || !layout.id.equals(l.id);
        cancelAllPointers();
        layout = l;
        geometryDirty = true;
        if (animate && changed) animStart = SystemClock.uptimeMillis();
        invalidate();
    }

    public Layout getLayout() { return layout; }

    public void setKeyboardHeight(int px) {
        if (px == fixedHeight) return;
        fixedHeight = px;
        geometryDirty = true;
        requestLayout();
        invalidate();
    }

    public void setFontPercent(int p) { fontPercent = p; invalidate(); }

    public void setShift(boolean shift, boolean locked) {
        if (shift == shifted && locked == capsLock) return;
        shifted = shift;
        capsLock = locked;
        invalidate();
    }

    public void setEnterAction(int action) {
        if (enterAction == action) return;
        enterAction = action;
        invalidate();
    }

    public void setPreviewEnabled(boolean yes) {
        previewEnabled = yes;
        if (!yes && overlay != null) overlay.hidePreview();
    }

    public void setHaptics(boolean enabled, int level) { hapticEnabled = enabled; hapticLevel = level; }

    public void setOneHanded(int mode) {
        if (oneHanded == mode) return;
        oneHanded = mode;
        geometryDirty = true;
        invalidate();
    }

    /** Height in px for a letters layout at the given scale. */
    public static int computeHeight(Context c, Layout letters, int heightPercent) {
        float d = c.getResources().getDisplayMetrics().density;
        return Math.round((letters.totalHeightDp() * heightPercent / 100f + 6f) * d);
    }

    // ------------------------------------------------------------------ geometry
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = fixedHeight > 0 ? fixedHeight : Math.round(270 * density);
        setMeasuredDimension(w, h);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        geometryDirty = true;
    }

    private void computeGeometry() {
        geometryDirty = false;
        keys.clear();
        swapKey = null;
        exitKey = null;
        if (layout == null || getWidth() == 0) return;
        float W = getWidth();
        float H = getHeight();
        float padH = 3 * density;
        float padTop = 3 * density;
        float padBottom = 3 * density;
        float areaLeft = 0, areaRight = W;
        if (oneHanded != 0) {
            float aw = W * 0.84f;
            if (oneHanded == 1) { areaLeft = 0; areaRight = aw; } else { areaLeft = W - aw; areaRight = W; }
        }
        float unit = (areaRight - areaLeft - 2 * padH) / layout.units;
        float totalDp = layout.totalHeightDp();
        float scaleY = (H - padTop - padBottom) / (totalDp * density);
        float gapH = 4 * density;
        float gapV = Math.min(6 * density * scaleY, 8 * density);
        float y = padTop;
        for (Layout.Row row : layout.rows) {
            float rh = row.heightDp * density * scaleY;
            float rowUnits = row.offset;
            for (Key k : row.keys) rowUnits += k.width;
            // rows narrower than the page are centred (e.g. shifted rows keep their offset)
            float extra = row.offset > 0 ? 0 : Math.max(0, (layout.units - rowUnits) / 2f);
            float x = areaLeft + padH + (row.offset + extra) * unit;
            for (Key k : row.keys) {
                float w = k.width * unit;
                k.rect.set(x + gapH / 2f, y + gapV / 2f, x + w - gapH / 2f, y + rh - gapV / 2f);
                keys.add(k);
                x += w;
            }
            y += rh;
        }
        if (oneHanded != 0) {
            float colLeft = oneHanded == 1 ? areaRight : 0;
            float colRight = oneHanded == 1 ? W : areaLeft;
            float mid = (padTop + H - padBottom) / 2f;
            swapKey = Key.fn(Key.ONE_HAND_SWAP, oneHanded == 1 ? "يمين" : "يسار", 1f, "نقل اللوحة إلى الجهة الأخرى");
            exitKey = Key.fn(Key.ONE_HAND_EXIT, "كامل", 1f, "إيقاف وضع اليد الواحدة");
            swapKey.rect.set(colLeft + gapH, padTop + gapV, colRight - gapH, mid - gapV / 2f);
            exitKey.rect.set(colLeft + gapH, mid + gapV / 2f, colRight - gapH, H - padBottom - gapV);
            keys.add(swapKey);
            keys.add(exitKey);
        }
    }

    private Key nearestKey(float x, float y) {
        if (geometryDirty) computeGeometry();
        Key best = null;
        float bestD = Float.MAX_VALUE;
        for (Key k : keys) {
            RectF r = k.rect;
            float dx = x < r.left ? r.left - x : (x > r.right ? x - r.right : 0);
            float dy = y < r.top ? r.top - y : (y > r.bottom ? y - r.bottom : 0);
            float d = dx * dx + dy * dy;
            if (d < bestD) { bestD = d; best = k; }
        }
        return best;
    }

    // ------------------------------------------------------------------ drawing
    private float keyTextPx() { return 26 * sp * fontPercent / 100f; }

    @Override protected void onDraw(Canvas canvas) {
        if (geometryDirty) computeGeometry();
        canvas.drawColor(theme.background);
        float anim = 1f;
        if (animStart > 0) {
            long t = SystemClock.uptimeMillis() - animStart;
            if (t >= ANIM_MS) animStart = 0; else { anim = 0.25f + 0.75f * t / (float) ANIM_MS; postInvalidateOnAnimation(); }
        }
        float radius = 8 * density;
        for (Key k : keys) {
            boolean pressed = isPressed(k);
            int color;
            if (k.type == Key.SHIFT && (shifted || capsLock)) color = capsLock ? theme.accent : theme.functionPressed;
            else if (k.function) color = pressed ? theme.functionPressed : theme.function;
            else color = pressed ? theme.keyPressed : theme.key;
            keyPaint.setColor(color);
            boolean pill = k.type == Key.ENTER || k.type == Key.TO_SYMBOLS || k.type == Key.TO_LETTERS;
            float r = pill ? k.rect.height() / 2f : radius;
            canvas.drawRoundRect(k.rect, r, r, keyPaint);
            int fg = k.function ? theme.functionText : theme.text;
            int alpha = (int) (255 * anim);
            switch (k.type) {
                case Key.SHIFT: drawShift(canvas, k, fg); break;
                case Key.DELETE: drawDelete(canvas, k, fg); break;
                case Key.ENTER: drawEnter(canvas, k, fg); break;
                case Key.SPACE: drawSpace(canvas, k, alpha); break;
                default: drawLabel(canvas, k, fg, alpha);
            }
        }
    }

    private void drawLabel(Canvas canvas, Key k, int color, int alpha) {
        String label = shifted && k.caseable ? k.upper : k.label;
        if (label == null) return;
        float size;
        if (k.function) size = 15 * sp * fontPercent / 100f;
        else if (label.length() <= 2) size = keyTextPx();
        else size = 18 * sp * fontPercent / 100f;
        textPaint.setTextSize(size);
        float maxW = k.rect.width() - 6 * density;
        float w = textPaint.measureText(label);
        if (w > maxW && w > 0) textPaint.setTextSize(Math.max(9 * density, size * maxW / w));
        textPaint.setColor(color);
        textPaint.setAlpha(alpha);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float cy = k.rect.centerY() + (k.hint != null ? 3 * density : 0);
        canvas.drawText(label, k.rect.centerX(), cy - (fm.ascent + fm.descent) / 2f, textPaint);
        if (k.hint != null) {
            hintPaint.setTextSize(11 * sp * fontPercent / 100f);
            hintPaint.setColor(theme.hint | 0xFF000000);
            hintPaint.setAlpha((int) (alpha * 0.6f));
            float hx = k.rect.left + 5 * density;
            float hy = k.rect.top + 3 * density - hintPaint.getFontMetrics().ascent;
            canvas.drawText(k.hint, hx, hy, hintPaint);
        }
    }

    private void drawSpace(Canvas canvas, Key k, int alpha) {
        if (k.label == null) return;
        textPaint.setTextSize(14 * sp);
        textPaint.setColor(theme.hint);
        textPaint.setAlpha((int) (alpha * 0.8f));
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = k.rect.centerY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(k.label, k.rect.centerX(), baseline, textPaint);
        if (k.rect.width() > 120 * density) {
            canvas.drawText("‹", k.rect.left + 16 * density, baseline, textPaint);
            canvas.drawText("›", k.rect.right - 16 * density, baseline, textPaint);
        }
    }

    private void prepIcon(int color) {
        iconPaint.setColor(color);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(2f * density);
    }

    private void drawShift(Canvas canvas, Key k, int color) {
        prepIcon(color);
        float s = 10 * density, cx = k.rect.centerX(), cy = k.rect.centerY();
        path.reset();
        path.moveTo(cx, cy - s);
        path.lineTo(cx + s, cy);
        path.lineTo(cx + s * 0.45f, cy);
        path.lineTo(cx + s * 0.45f, cy + s * 0.8f);
        path.lineTo(cx - s * 0.45f, cy + s * 0.8f);
        path.lineTo(cx - s * 0.45f, cy);
        path.lineTo(cx - s, cy);
        path.close();
        if (shifted || capsLock) iconPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawPath(path, iconPaint);
        if (capsLock) {
            iconPaint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(cx - s * 0.6f, cy + s * 1.25f, cx + s * 0.6f, cy + s * 1.25f, iconPaint);
        }
    }

    private void drawDelete(Canvas canvas, Key k, int color) {
        prepIcon(color);
        float h = 8 * density, w = 22 * density, cx = k.rect.centerX(), cy = k.rect.centerY();
        float l = cx - w / 2f, r = cx + w / 2f;
        path.reset();
        path.moveTo(l, cy);
        path.lineTo(l + 7 * density, cy - h);
        path.lineTo(r, cy - h);
        path.lineTo(r, cy + h);
        path.lineTo(l + 7 * density, cy + h);
        path.close();
        canvas.drawPath(path, iconPaint);
        float xc = (l + 7 * density + r) / 2f, d = 3.5f * density;
        canvas.drawLine(xc - d, cy - d, xc + d, cy + d, iconPaint);
        canvas.drawLine(xc - d, cy + d, xc + d, cy - d, iconPaint);
    }

    private void drawEnter(Canvas canvas, Key k, int color) {
        prepIcon(color);
        float cx = k.rect.centerX(), cy = k.rect.centerY(), s = 9 * density;
        path.reset();
        switch (enterAction) {
            case EditorInfo.IME_ACTION_SEND:
                iconPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                path.moveTo(cx - s, cy - s);
                path.lineTo(cx + s * 1.1f, cy);
                path.lineTo(cx - s, cy + s);
                path.lineTo(cx - s * 0.55f, cy);
                path.close();
                canvas.drawPath(path, iconPaint);
                break;
            case EditorInfo.IME_ACTION_SEARCH:
                canvas.drawCircle(cx - 2 * density, cy - 2 * density, s * 0.65f, iconPaint);
                canvas.drawLine(cx + s * 0.3f, cy + s * 0.3f, cx + s, cy + s, iconPaint);
                break;
            case EditorInfo.IME_ACTION_DONE:
                path.moveTo(cx - s, cy);
                path.lineTo(cx - s * 0.3f, cy + s * 0.7f);
                path.lineTo(cx + s, cy - s * 0.7f);
                canvas.drawPath(path, iconPaint);
                break;
            case EditorInfo.IME_ACTION_GO:
            case EditorInfo.IME_ACTION_NEXT:
                canvas.drawLine(cx - s, cy, cx + s, cy, iconPaint);
                path.moveTo(cx + s * 0.3f, cy - s * 0.7f);
                path.lineTo(cx + s, cy);
                path.lineTo(cx + s * 0.3f, cy + s * 0.7f);
                canvas.drawPath(path, iconPaint);
                break;
            case EditorInfo.IME_ACTION_PREVIOUS:
                canvas.drawLine(cx - s, cy, cx + s, cy, iconPaint);
                path.moveTo(cx - s * 0.3f, cy - s * 0.7f);
                path.lineTo(cx - s, cy);
                path.lineTo(cx - s * 0.3f, cy + s * 0.7f);
                canvas.drawPath(path, iconPaint);
                break;
            default:
                path.moveTo(cx + s, cy - s * 0.8f);
                path.lineTo(cx + s, cy + s * 0.2f);
                path.lineTo(cx - s, cy + s * 0.2f);
                canvas.drawPath(path, iconPaint);
                path.reset();
                path.moveTo(cx - s * 0.4f, cy - s * 0.4f);
                path.lineTo(cx - s, cy + s * 0.2f);
                path.lineTo(cx - s * 0.4f, cy + s * 0.8f);
                canvas.drawPath(path, iconPaint);
        }
    }

    private boolean isPressed(Key k) {
        for (int i = 0; i < pointers.size(); i++) if (pointers.valueAt(i).key == k) return true;
        return k == hoverKey;
    }

    // ------------------------------------------------------------------ touch
    private void haptic() {
        if (!hapticEnabled) return;
        int c;
        if (hapticLevel <= 1) c = HapticFeedbackConstants.CLOCK_TICK;
        else if (hapticLevel == 2) c = Build.VERSION.SDK_INT >= 27 ? HapticFeedbackConstants.KEYBOARD_PRESS
            : HapticFeedbackConstants.KEYBOARD_TAP;
        else c = HapticFeedbackConstants.VIRTUAL_KEY;
        performHapticFeedback(c, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
    }

    private RectF toOverlay(RectF r) {
        getLocationInWindow(loc1);
        overlay.getLocationInWindow(loc2);
        tmp.set(r);
        tmp.offset(loc1[0] - loc2[0], loc1[1] - loc2[1]);
        return tmp;
    }

    private float overlayX(float x) {
        getLocationInWindow(loc1);
        overlay.getLocationInWindow(loc2);
        return x + loc1[0] - loc2[0];
    }

    private void showPreview(Key k) {
        if (overlay == null || !previewEnabled || previewOnly) return;
        if (k.type != Key.CHAR || k.label == null) { overlay.hidePreview(); return; }
        String label = shifted && k.caseable ? k.upper : k.label;
        overlay.showPreview(toOverlay(k.rect), label, keyTextPx());
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (previewOnly || layout == null) return true;
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                onDown(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) onMove(e.getPointerId(i), e.getX(i), e.getY(i));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex();
                onUp(e.getPointerId(idx), e.getX(idx));
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelAllPointers();
                return true;
            default:
                return true;
        }
    }

    private void onDown(int id, float x, float y) {
        // Rollover: a new finger commits any letter still held by an earlier finger, in order.
        for (int i = 0; i < pointers.size(); i++) {
            Pointer o = pointers.valueAt(i);
            if (!o.committed && !o.popupOpen && o.key.type == Key.CHAR) {
                cancelTimers(o);
                commitChar(o);
                o.committed = true;
            }
        }
        Key k = nearestKey(x, y);
        if (k == null) return;
        Pointer p = new Pointer();
        p.id = id;
        p.key = k;
        p.downX = x;
        p.downY = y;
        p.lastX = x;
        pointers.put(id, p);
        haptic();
        showPreview(k);
        if (k.type == Key.DELETE) {
            if (listener != null) listener.onDelete(0);
            p.committed = true;
            p.repeater = new Runnable() {
                @Override public void run() {
                    p.repeat++;
                    if (listener != null) listener.onDelete(p.repeat);
                    postDelayed(this, p.repeat > 15 ? REPEAT_MS * 3 : REPEAT_MS);
                }
            };
            postDelayed(p.repeater, REPEAT_START_MS);
        } else if ((k.type == Key.CHAR && k.popup != null) || k.type == Key.SPACE) {
            p.longPress = () -> onLongPress(p);
            postDelayed(p.longPress, LONG_PRESS_MS);
        }
        invalidate();
    }

    private void onLongPress(Pointer p) {
        p.longPress = null;
        if (pointers.get(p.id) != p || p.committed) return;
        if (p.key.type == Key.SPACE) {
            if (p.spaceSwipe) return;
            p.cursorMode = true;
            p.cursorOriginX = p.lastX;
            haptic();
            return;
        }
        if (overlay == null || p.key.popup == null) return;
        p.popupOpen = true;
        p.popupStartX = p.lastX;
        overlay.hidePreview();
        overlay.showPopup(toOverlay(p.key.rect), p.key.popup, keyTextPx() * 0.85f);
        haptic();
    }

    private void onMove(int id, float x, float y) {
        Pointer p = pointers.get(id);
        if (p == null) return;
        p.lastX = x;
        if (p.popupOpen && overlay != null) {
            if (!p.popupMoved && Math.abs(x - p.popupStartX) > 8 * density) p.popupMoved = true;
            if (p.popupMoved) overlay.selectAt(overlayX(x));
            return;
        }
        if (p.key.type == Key.SPACE) {
            float dx = x - p.downX;
            if (p.cursorMode) {
                int steps = (int) ((x - p.cursorOriginX) / (11 * density));
                if (steps != p.cursorSteps) {
                    if (listener != null) listener.onCursorMove(steps - p.cursorSteps);
                    p.cursorSteps = steps;
                }
            } else if (!p.spaceSwipe && Math.abs(dx) > 48 * density && Math.abs(dx) > Math.abs(y - p.downY)) {
                p.spaceSwipe = true;
                cancelTimers(p);
            }
        }
    }

    private void onUp(int id, float x) {
        Pointer p = pointers.get(id);
        if (p == null) return;
        pointers.remove(id);
        cancelTimers(p);
        if (p.popupOpen) {
            int sel = overlay != null ? overlay.popupSelected() : 0;
            if (overlay != null) overlay.hidePopup();
            String[] items = p.key.popup;
            if (items != null && sel >= 0 && sel < items.length && listener != null) {
                String out = Layouts.popupOutput(items[sel]);
                if (shifted && p.key.caseable) out = out.toUpperCase(java.util.Locale.US);
                listener.onKey(p.key, out);
            }
        } else if (!p.committed) {
            switch (p.key.type) {
                case Key.CHAR: commitChar(p); break;
                case Key.SPACE:
                    if (p.spaceSwipe) { if (listener != null) listener.onSpaceSwipe(); }
                    else if (!p.cursorMode && listener != null) listener.onKey(p.key, " ");
                    break;
                default:
                    if (listener != null) listener.onKey(p.key, null);
            }
        }
        if (pointers.size() == 0 && overlay != null) overlay.hidePreview();
        else if (pointers.size() > 0) showPreview(pointers.valueAt(pointers.size() - 1).key);
        invalidate();
    }

    private void commitChar(Pointer p) {
        if (listener == null || p.key.output == null) return;
        String out = p.key.output;
        if (shifted && p.key.caseable) out = p.key.upper;
        listener.onKey(p.key, out);
    }

    private void cancelTimers(Pointer p) {
        if (p.longPress != null) { removeCallbacks(p.longPress); p.longPress = null; }
        if (p.repeater != null) { removeCallbacks(p.repeater); p.repeater = null; }
    }

    public void cancelAllPointers() {
        for (int i = 0; i < pointers.size(); i++) cancelTimers(pointers.valueAt(i));
        pointers.clear();
        if (overlay != null) { overlay.hidePreview(); overlay.hidePopup(); }
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        cancelAllPointers();
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------ TalkBack: explore by touch, lift to type
    @Override public boolean onHoverEvent(MotionEvent e) {
        AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null || !am.isTouchExplorationEnabled() || previewOnly) return super.onHoverEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE: {
                Key k = nearestKey(e.getX(), e.getY());
                if (k != hoverKey) {
                    hoverKey = k;
                    if (k != null) announceForAccessibility(k.spoken());
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_HOVER_EXIT: {
                Key k = hoverKey;
                hoverKey = null;
                invalidate();
                boolean inside = e.getX() >= 0 && e.getY() >= 0 && e.getX() <= getWidth() && e.getY() <= getHeight();
                if (k != null && inside && listener != null) {
                    if (k.type == Key.DELETE) listener.onDelete(0);
                    else if (k.type == Key.CHAR) listener.onKey(k, shifted && k.caseable ? k.upper : k.output);
                    else if (k.type == Key.SPACE) listener.onKey(k, " ");
                    else listener.onKey(k, null);
                }
                return true;
            }
            default:
                return true;
        }
    }
}
