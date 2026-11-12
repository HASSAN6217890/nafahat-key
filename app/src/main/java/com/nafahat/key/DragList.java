package com.nafahat.key;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

/** Vertical list whose rows can be reordered by dragging their handle (no AndroidX needed). */
public final class DragList extends LinearLayout {
    public interface OnReorder { void onReorder(List<Long> ids); }

    private OnReorder onReorder;
    private View dragging;
    private float startY;
    private boolean moved;

    public DragList(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }

    public void setOnReorder(OnReorder r) { onReorder = r; }

    /** Adds a row; handle may be null for rows that cannot move. */
    @SuppressLint("ClickableViewAccessibility")
    public void addRow(View row, View handle, long id) {
        row.setTag(id);
        addView(row);
        if (handle == null) return;
        handle.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = row;
                    startY = e.getRawY();
                    moved = false;
                    row.setElevation(8 * getResources().getDisplayMetrics().density);
                    row.setAlpha(0.92f);
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (dragging != row) return true;
                    float dy = e.getRawY() - startY;
                    int idx = indexOfChild(row);
                    if (dy > 0 && idx < getChildCount() - 1) {
                        View next = getChildAt(idx + 1);
                        if (dy > next.getHeight() / 2f) {
                            removeViewAt(idx + 1);
                            addView(next, idx);
                            startY += next.getHeight();
                            dy -= next.getHeight();
                            moved = true;
                        }
                    } else if (dy < 0 && idx > 0) {
                        View prev = getChildAt(idx - 1);
                        if (-dy > prev.getHeight() / 2f) {
                            removeViewAt(idx - 1);
                            addView(prev, idx);
                            startY -= prev.getHeight();
                            dy += prev.getHeight();
                            moved = true;
                        }
                    }
                    row.setTranslationY(dy);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    row.setTranslationY(0);
                    row.setElevation(0);
                    row.setAlpha(1f);
                    dragging = null;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if (moved && onReorder != null) onReorder.onReorder(ids());
                    return true;
                default:
                    return true;
            }
        });
    }

    public List<Long> ids() {
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            Object t = getChildAt(i).getTag();
            if (t instanceof Long) out.add((Long) t);
        }
        return out;
    }
}
