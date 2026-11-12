package android.graphics;
/** Minimal stand-in so the pure keyboard logic compiles on a plain JDK for tests. */
public class RectF {
    public float left, top, right, bottom;
    public void set(float l, float t, float r, float b) { left = l; top = t; right = r; bottom = b; }
}
