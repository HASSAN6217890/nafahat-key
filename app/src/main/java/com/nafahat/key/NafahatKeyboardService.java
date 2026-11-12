package com.nafahat.key;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local-only Android IME: no logging, no network, no clipboard reads unless the user asks. */
public final class NafahatKeyboardService extends InputMethodService implements KeyboardView.Listener,
    ResizeFrame.Listener, CandidateStrip.Listener, GridPanel.Listener, LibraryPanel.Host, InputHandler.Ui {

    private static final int PANEL_KEYS = 0, PANEL_MENU = 1, PANEL_THEME = 2, PANEL_ONE_HAND = 3,
        PANEL_EDIT = 4, PANEL_LIBRARY = 5, PANEL_MORE = 6;

    private static final int C_LIBRARY = 1, C_THEME = 2, C_RESIZE = 3, C_ONE_HAND = 4, C_EDIT = 5,
        C_DIACRITICS = 6, C_LANGUAGE = 7, C_UNDO = 8, C_SETTINGS = 9, C_SWITCH_IME = 11;
    private static final int C_BACK = 10;
    private static final int C_T_NAFAHAT = 21, C_T_DARK = 22, C_T_LIGHT = 23, C_T_SYSTEM = 24;
    private static final int C_H_LEFT = 31, C_H_RIGHT = 32, C_H_OFF = 33;
    private static final int C_E_LEFT = 41, C_E_RIGHT = 42, C_E_UP = 43, C_E_DOWN = 44, C_E_SELECT = 45,
        C_E_ALL = 46, C_E_COPY = 47, C_E_CUT = 48, C_E_PASTE = 49, C_E_HOME = 50, C_E_END = 51, C_E_DEL = 52;
    private static final int C_MORE_WORD = 60;
    private static final int C_THEME_BASE = 70;

    private KeyboardPrefs prefs;
    private InputHandler handler;
    private Theme theme;
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private RootLayout root;
    private View spacer;
    private CandidateStrip strip;
    private FrameLayout content;
    private KeyboardView keyboard;
    private GridPanel grid;
    private LibraryPanel library;
    private PreviewOverlay overlay;
    private ResizeFrame resizeFrame;
    private boolean arrowCached;
    // live resize state in pixels (kept as floats while dragging)
    private float rsLeft, rsRight, rsHeightPct, rsLiftDp;

    private int panel = PANEL_KEYS;
    private int symbolsPage;      // 0 letters, 1 or 2 symbols
    private boolean diacriticsPage;
    private boolean resizing;
    private boolean selecting;
    private boolean numberRowCached;
    private boolean globeCached;
    private final Map<String, Layout> layouts = new HashMap<>();
    private final int[] loc = new int[2];

    // ------------------------------------------------------------------ lifecycle
    @Override public void onCreate() {
        super.onCreate();
        prefs = new KeyboardPrefs(this);
        handler = new InputHandler(this, this);
        handler.setArabic(prefs.arabic());
        Suggester.get(this).ensureLoaded();
        bg.execute(() -> LibraryDb.get(getApplicationContext()));
    }

    @Override public void onDestroy() {
        bg.shutdown();
        super.onDestroy();
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Belt and braces against the keyboard showing at the top of the screen: the IME window is
     * full width, wraps its content and sits at the bottom; the input area and our view wrap too.
     */
    private void pinWindowToBottom() {
        try {
            Window w = getWindow() != null ? getWindow().getWindow() : null;
            if (w == null) return;
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(android.view.Gravity.BOTTOM);
            View inputArea = w.findViewById(android.R.id.inputArea);
            wrapAtBottom(inputArea);
            if (root != null && root.getParent() instanceof View) wrapAtBottom((View) root.getParent());
            wrapAtBottom(root);
        } catch (RuntimeException ignored) { }
    }

    private static void wrapAtBottom(View v) {
        if (v == null) return;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) return;
        boolean changed = false;
        if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) { lp.height = ViewGroup.LayoutParams.WRAP_CONTENT; changed = true; }
        if (lp instanceof FrameLayout.LayoutParams && ((FrameLayout.LayoutParams) lp).gravity != android.view.Gravity.BOTTOM) {
            ((FrameLayout.LayoutParams) lp).gravity = android.view.Gravity.BOTTOM;
            changed = true;
        } else if (lp instanceof LinearLayout.LayoutParams && ((LinearLayout.LayoutParams) lp).gravity != android.view.Gravity.BOTTOM) {
            ((LinearLayout.LayoutParams) lp).gravity = android.view.Gravity.BOTTOM;
            changed = true;
        }
        if (changed) v.setLayoutParams(lp);
    }

    @Override public View onCreateInputView() {
        float d = getResources().getDisplayMetrics().density;
        root = new RootLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(android.view.Gravity.BOTTOM);
        spacer = new View(this);
        spacer.setBackgroundColor(Color.TRANSPARENT);
        column.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(28 * d)));
        strip = new CandidateStrip(this);
        strip.setListener(this);
        column.addView(strip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content = new FrameLayout(this);
        keyboard = new KeyboardView(this);
        keyboard.setListener(this);
        grid = new GridPanel(this);
        grid.setListener(this);
        library = new LibraryPanel(this);
        library.setHost(this);
        library.setExecutor(bg);
        content.addView(keyboard, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(library, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        resizeFrame = new ResizeFrame(this);
        resizeFrame.setListener(this);
        resizeFrame.setSource(keyboard);
        resizeFrame.setVisibility(View.GONE);
        content.addView(resizeFrame, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        column.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = android.view.Gravity.BOTTOM;
        root.addView(column, clp);
        root.column = column;
        overlay = new PreviewOverlay(this);
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        keyboard.setOverlay(overlay);
        root.overlay = overlay;
        // Android 15 edge-to-edge: keep the keys above the gesture / navigation bar.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = 0;
            if (android.os.Build.VERSION.SDK_INT >= 35) {
                bottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
            }
            if (content.getPaddingBottom() != bottom) content.setPadding(0, 0, 0, bottom);
            return insets;
        });
        layouts.clear();
        applyPrefs();
        showPanel(PANEL_KEYS);
        return root;
    }

    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        handler.startInput(attribute, restarting);
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        pinWindowToBottom();
        if (root == null) return;
        resizing = false;
        if (resizeFrame != null) resizeFrame.setVisibility(View.GONE);
        selecting = false;
        symbolsPage = 0;
        diacriticsPage = false;
        applyPrefs();
        handler.setPrefs(prefs);
        handler.setArabic(prefs.arabic());
        if (!restarting || panel == PANEL_LIBRARY) showPanel(PANEL_KEYS);
        applyLayout(false);
        keyboard.setPreviewEnabled(!handler.isSensitive() && prefs.keyPopups());
        keyboard.setEnterAction(handler.enterAction());
        keyboard.setShift(handler.shifted(), false);
        handler.startInputView();
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        if (keyboard != null) keyboard.cancelAllPointers();
        super.onFinishInputView(finishingInput);
    }

    @Override public void onFinishInput() {
        handler.finishInput();
        super.onFinishInput();
    }

    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        handler.onUpdateSelection(newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    }

    @Override public void onComputeInsets(Insets outInsets) {
        super.onComputeInsets(outInsets);
        if (strip == null || root == null || !root.isShown()) return;
        strip.getLocationInWindow(loc);
        int top = loc[1];
        root.getLocationInWindow(loc);
        int bottom = loc[1] + root.getHeight();
        outInsets.contentTopInsets = top;
        outInsets.visibleTopInsets = top;
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
        outInsets.touchableRegion.set(0, top, root.getWidth(), bottom);
    }

    // ------------------------------------------------------------------ preferences / layout
    private void applyPrefs() {
        theme = prefs.resolveTheme(this);
        if (numberRowCached != prefs.numberRow()) { layouts.clear(); numberRowCached = prefs.numberRow(); }
        boolean globe = true; // the owner wants the 🌐 key always (tap = next keyboard, long-press = picker)
        if (globeCached != globe) { layouts.clear(); globeCached = globe; }
        if (arrowCached != prefs.arrowRow()) { layouts.clear(); arrowCached = prefs.arrowRow(); }
        applySizes();
        keyboard.setTheme(theme);
        keyboard.setFontPercent(Math.round(prefs.fontPercent() * (prefs.largerText() ? 1.15f : 1f)));
        keyboard.setHaptics(prefs.vibrate(), prefs.vibrationLevel());
        keyboard.setTypingOptions(prefs.longPressMs(), prefs.showHints(), prefs.swipeDeleteWords(), prefs.artStrength());
        content.setBackgroundColor(theme.background);
        resizeFrame.setTheme(theme);
        strip.setTheme(theme);
        grid.setTheme(theme);
        library.setTheme(theme);
        library.setPrefs(prefs);
        overlay.setTheme(theme);
        try {
            Window w = getWindow() != null ? getWindow().getWindow() : null;
            if (w != null) w.setNavigationBarColor(theme.background);
        } catch (RuntimeException ignored) { }
    }

    /** Height, width, position and lift for the current orientation (or the live resize state). */
    private void applySizes() {
        float d = getResources().getDisplayMetrics().density;
        int heightPct = resizing ? Math.round(rsHeightPct) : prefs.heightPercent();
        int liftDp = resizing ? Math.round(rsLiftDp) : prefs.liftDp();
        int liftPx = Math.round(liftDp * d);
        int h = KeyboardView.computeHeight(this, layoutFor(arrowCached ? "ar_arrows" : "ar"), heightPct);
        // Reserve room above the strip so the tallest preview / long-press bubble never clips.
        float d = getResources().getDisplayMetrics().density;
        float pct = heightPct / 100f;
        float tallestRowDp = Layouts.H_ROW * pct;   // the top letter row is the tallest case
        float keyDp = tallestRowDp - 6f;
        float neededDp = Math.max(keyDp * 1.25f, Math.max(keyDp, 44f) + 4f) + 8f;
        int spacerPx = Math.round(Math.max(8f, neededDp - 46f) * d);
        ViewGroup.LayoutParams lp = spacer.getLayoutParams();
        if (lp != null && lp.height != spacerPx) { lp.height = spacerPx; spacer.setLayoutParams(lp); }
        keyboard.setKeyboardHeight(h + liftPx);
        grid.setPanelHeight(h + liftPx);
        library.setPanelHeight(h + liftPx);
        if (resizing) {
            float W = keyboard.getWidth() > 0 ? keyboard.getWidth() : getResources().getDisplayMetrics().widthPixels;
            float aw = Math.max(1f, rsRight - rsLeft);
            int widthPct = Math.round(aw / W * 100f);
            int offsetPct = W - aw > 1f ? Math.round(rsLeft / (W - aw) * 100f) : 50;
            keyboard.setArea(widthPct, offsetPct, liftPx);
            resizeFrame.invalidate();
        } else {
            keyboard.setArea(prefs.widthPercent(), prefs.offsetPercent(), liftPx);
        }
    }

    private Layout layoutFor(String id) {
        Layout l = layouts.get(id);
        if (l != null) return l;
        boolean num = prefs.numberRow();
        switch (id.replace("_arrows", "")) {
            case "ar": l = Layouts.arabic(num, globeCached); break;
            case "en": l = Layouts.english(num, 0, globeCached); break;
            case "en_mail": l = Layouts.english(num, 1, globeCached); break;
            case "en_url": l = Layouts.english(num, 2, globeCached); break;
            case "sym1_ar": l = Layouts.symbols(1, true); break;
            case "sym2_ar": l = Layouts.symbols(2, true); break;
            case "sym1_en": l = Layouts.symbols(1, false); break;
            case "sym2_en": l = Layouts.symbols(2, false); break;
            case "diac": l = Layouts.diacritics(); break;
            case "phone": l = Layouts.numberPad(true); break;
            default: l = Layouts.numberPad(false);
        }
        if (id.endsWith("_arrows")) l = Layouts.withArrows(l);
        layouts.put(id, l);
        return l;
    }

    private String currentLayoutId() {
        if (handler.isPhone()) return "phone";
        if (handler.isNumeric()) return "number";
        if (diacriticsPage) return "diac" + (arrowCached ? "_arrows" : "");
        boolean ar = handler.arabic();
        String arrows = arrowCached ? "_arrows" : "";
        if (symbolsPage > 0) return "sym" + symbolsPage + (ar ? "_ar" : "_en") + arrows;
        if (handler.isEmail()) return "en_mail" + arrows;
        if (handler.isUrl()) return "en_url" + arrows;
        return (ar ? "ar" : "en") + arrows;
    }

    private void applyLayout(boolean animate) {
        if (keyboard == null) return;
        if ((handler.isEmail() || handler.isUrl()) && handler.arabic()) handler.setArabic(false);
        keyboard.setLayout(layoutFor(currentLayoutId()), animate);
        keyboard.setShift(handler.shifted(), false);
    }

    // ------------------------------------------------------------------ panels
    private void showPanel(int p) {
        panel = p;
        keyboard.setVisibility(p == PANEL_KEYS ? View.VISIBLE : View.GONE);
        grid.setVisibility(p == PANEL_MENU || p == PANEL_THEME || p == PANEL_ONE_HAND || p == PANEL_EDIT
            || p == PANEL_MORE ? View.VISIBLE : View.GONE);
        library.setVisibility(p == PANEL_LIBRARY ? View.VISIBLE : View.GONE);
        strip.setMenuActive(p != PANEL_KEYS);
        if (p != PANEL_KEYS) keyboard.cancelAllPointers();
        switch (p) {
            case PANEL_MENU: buildMenu(); break;
            case PANEL_THEME: buildThemePanel(); break;
            case PANEL_ONE_HAND: buildOneHandPanel(); break;
            case PANEL_EDIT: buildEditPanel(); break;
            case PANEL_MORE: buildMorePanel(); break;
            case PANEL_LIBRARY: library.refresh(); break;
            default:
        }
    }

    private GridPanel.Cell cell(int id, String title, String glyph) { return new GridPanel.Cell(id, title, glyph); }

    private void buildMenu() {
        List<GridPanel.Cell> cells = new ArrayList<>();
        for (int id : prefs.menuOrder(MenuItems.defaults())) {
            String title = id == C_LANGUAGE ? (handler.arabic() ? "English" : "العربية") : MenuItems.title(id);
            GridPanel.Cell c = cell(id, title, MenuItems.glyph(id));
            if (id == C_LIBRARY) c.enabled = !handler.isSensitive();
            if (id == C_ONE_HAND) c.active = prefs.oneHanded() != 0;
            if (id == C_DIACRITICS) c.active = diacriticsPage;
            cells.add(c);
        }
        grid.setCells(cells, 3, "الإصدار " + versionName());
    }

    private void buildThemePanel() {
        List<GridPanel.Cell> cells = new ArrayList<>();
        String t = prefs.theme();
        for (int i = 0; i < Theme.GALLERY.length; i++) {
            GridPanel.Cell c = cell(C_THEME_BASE + i, Theme.GALLERY[i][1], null);
            c.active = t.equals(Theme.GALLERY[i][0]);
            c.data = Theme.GALLERY[i][0];
            cells.add(c);
        }
        cells.add(cell(C_BACK, "رجوع", null));
        grid.setCells(cells, 3, "المظهر");
    }

    private void buildOneHandPanel() {
        List<GridPanel.Cell> cells = new ArrayList<>();
        int m = prefs.oneHanded();
        GridPanel.Cell r = cell(C_H_RIGHT, "اليد اليمنى", null); r.active = m == 2; cells.add(r);
        GridPanel.Cell l = cell(C_H_LEFT, "اليد اليسرى", null); l.active = m == 1; cells.add(l);
        GridPanel.Cell o = cell(C_H_OFF, "اللوحة كاملة", null); o.active = m == 0; cells.add(o);
        cells.add(cell(C_BACK, "رجوع", null));
        grid.setCells(cells, 2, "بيد واحدة");
    }

    private void buildEditPanel() {
        List<GridPanel.Cell> cells = new ArrayList<>();
        GridPanel.Cell up = cell(C_E_UP, "أعلى", "↑"); up.repeat = true;
        GridPanel.Cell left = cell(C_E_LEFT, "يسار", "←"); left.repeat = true;
        GridPanel.Cell right = cell(C_E_RIGHT, "يمين", "→"); right.repeat = true;
        GridPanel.Cell down = cell(C_E_DOWN, "أسفل", "↓"); down.repeat = true;
        GridPanel.Cell sel = cell(C_E_SELECT, "تحديد", null); sel.active = selecting;
        GridPanel.Cell del = cell(C_E_DEL, "حذف", null); del.repeat = true;
        // laid out right-to-left, four per row
        cells.add(right); cells.add(up); cells.add(down); cells.add(left);
        cells.add(cell(C_E_HOME, "البداية", null)); cells.add(sel); cells.add(cell(C_E_ALL, "تحديد الكل", null));
        cells.add(cell(C_E_END, "النهاية", null));
        cells.add(cell(C_E_COPY, "نسخ", null)); cells.add(cell(C_E_CUT, "قص", null));
        cells.add(cell(C_E_PASTE, "لصق", null)); cells.add(del);
        grid.setCells(cells, 4, "تعديل النص");
    }

    private void buildMorePanel() {
        List<GridPanel.Cell> cells = new ArrayList<>();
        for (String w : handler.moreSuggestions()) {
            GridPanel.Cell c = cell(C_MORE_WORD, w, null);
            c.data = w;
            cells.add(c);
            if (cells.size() >= 15) break;
        }
        grid.setCells(cells, 3, "اقتراحات أخرى");
    }

    // ------------------------------------------------------------------ KeyboardView.Listener
    @Override public void onKey(Key key, String text) {
        switch (key.type) {
            case Key.CHAR: handler.onText(text); break;
            case Key.SPACE: handler.onSpace(); break;
            case Key.ENTER: handler.onEnter(); break;
            case Key.SHIFT:
                handler.onShift();
                break;
            case Key.TO_SYMBOLS:
                symbolsPage = 1; diacriticsPage = false; applyLayout(true); break;
            case Key.TO_LETTERS:
                symbolsPage = 0; diacriticsPage = false; applyLayout(true); break;
            case Key.SYM_PAGE:
                symbolsPage = symbolsPage == 1 ? 2 : 1; applyLayout(true); break;
            case Key.ONE_HAND_SWAP:
                prefs.setOneHanded(prefs.oneHanded() == 1 ? 2 : 1);
                applySizes();
                break;
            case Key.ARROW: {
                InputConnection ic = getCurrentInputConnection();
                int code = "left".equals(key.output) ? KeyEvent.KEYCODE_DPAD_LEFT : "right".equals(key.output)
                    ? KeyEvent.KEYCODE_DPAD_RIGHT : "up".equals(key.output) ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
                arrow(ic, code);
                break;
            }
            case Key.GLOBE:
                handler.commitWordAsIs();
                switchToNextKeyboard();
                break;
            case Key.ONE_HAND_EXIT:
                prefs.setOneHanded(0);
                applySizes();
                break;
            default:
        }
    }

    @Override public void onDelete(int repeat) { handler.onDelete(repeat); }

    @Override public void onSpaceSwipe() { switchLanguage(); }

    @Override public void onCursorMove(int steps) { handler.onCursorMove(steps); }

    @Override public void onDeleteSwipe(int words) { handler.onDeleteSwipe(words); }

    @Override public void onDeleteSwipeEnd() { handler.onDeleteSwipeEnd(); }

    @Override public void onKeyLongPress(Key key) {
        if (key.type == Key.GLOBE) showImePicker();
    }

    // ------------------------------------------------------------------ other keyboards (🌐)
    private android.os.IBinder windowToken() {
        Window w = getWindow() != null ? getWindow().getWindow() : null;
        return w == null ? null : w.getAttributes().token;
    }

    private void switchToNextKeyboard() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) { if (!switchToNextInputMethod(false)) showImePicker(); return; }
            android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            android.os.IBinder t = windowToken();
            if (imm != null && t != null && !imm.switchToNextInputMethod(t, false)) showImePicker();
        } catch (RuntimeException e) {
            showImePicker();
        }
    }

    private void showImePicker() {
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    private void switchLanguage() {
        if (handler.isEmail() || handler.isUrl() || handler.isNumeric() || handler.isPhone()) return;
        boolean ar = !handler.arabic();
        handler.setArabic(ar);
        prefs.setArabic(ar);
        symbolsPage = 0;
        diacriticsPage = false;
        applyLayout(true);
    }

    // ------------------------------------------------------------------ CandidateStrip.Listener
    @Override public void onStripItem(CandidateStrip.Item item) {
        handler.onStripItem(item);
        if (panel == PANEL_MORE) showPanel(PANEL_KEYS);
    }

    @Override public void onMenuButton() {
        if (resizing) { onResizeDone(); return; }
        if (handler.internalActive()) handler.endInternal(true);
        showPanel(panel == PANEL_KEYS ? PANEL_MENU : PANEL_KEYS);
    }

    @Override public void onCollapseButton() {
        if (resizing) { onResizeDone(); return; }
        if (panel != PANEL_KEYS) { showPanel(PANEL_KEYS); return; }
        if (handler.moreSuggestions().size() > 3) { showPanel(PANEL_MORE); return; }
        requestHideSelf(0);
    }

    // ------------------------------------------------------------------ GridPanel.Listener
    @Override public void onCell(GridPanel.Cell c) {
        InputConnection ic = getCurrentInputConnection();
        switch (c.id) {
            case C_LIBRARY: library.setQuery(""); showPanel(PANEL_LIBRARY); break;
            case C_THEME: showPanel(PANEL_THEME); break;
            case C_RESIZE: startResize(); break;
            case C_ONE_HAND: showPanel(PANEL_ONE_HAND); break;
            case C_EDIT: handler.finishComposingForEdit(); showPanel(PANEL_EDIT); break;
            case C_DIACRITICS:
                diacriticsPage = !diacriticsPage; symbolsPage = 0;
                showPanel(PANEL_KEYS); applyLayout(true); break;
            case C_LANGUAGE: showPanel(PANEL_KEYS); switchLanguage(); break;
            case C_UNDO: showPanel(PANEL_KEYS); handler.undo(); break;
            case C_SWITCH_IME: showPanel(PANEL_KEYS); showImePicker(); break;
            case C_SETTINGS: {
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                showPanel(PANEL_KEYS);
                requestHideSelf(0);
                startActivity(i);
                break;
            }
            case C_BACK: showPanel(PANEL_MENU); break;
            case C_T_NAFAHAT: setThemeId("nafahat"); break;
            case C_H_LEFT: prefs.setOneHanded(1); applySizes(); startResize(); break;
            case C_H_RIGHT: prefs.setOneHanded(2); applySizes(); startResize(); break;
            case C_H_OFF: prefs.setOneHanded(0); applySizes(); showPanel(PANEL_KEYS); break;
            case C_E_LEFT: arrow(ic, KeyEvent.KEYCODE_DPAD_LEFT); break;
            case C_E_RIGHT: arrow(ic, KeyEvent.KEYCODE_DPAD_RIGHT); break;
            case C_E_UP: arrow(ic, KeyEvent.KEYCODE_DPAD_UP); break;
            case C_E_DOWN: arrow(ic, KeyEvent.KEYCODE_DPAD_DOWN); break;
            case C_E_HOME: arrow(ic, KeyEvent.KEYCODE_MOVE_HOME); break;
            case C_E_END: arrow(ic, KeyEvent.KEYCODE_MOVE_END); break;
            case C_E_SELECT: selecting = !selecting; buildEditPanel(); break;
            case C_E_ALL: contextAction(ic, android.R.id.selectAll); break;
            case C_E_COPY: contextAction(ic, android.R.id.copy); selecting = false; buildEditPanel(); break;
            case C_E_CUT: contextAction(ic, android.R.id.cut); selecting = false; buildEditPanel(); break;
            case C_E_PASTE: contextAction(ic, android.R.id.paste); break;
            case C_E_DEL: handler.onDelete(0); break;
            case C_THEME_BASE: case C_THEME_BASE + 1: case C_THEME_BASE + 2: case C_THEME_BASE + 3:
            case C_THEME_BASE + 4: case C_THEME_BASE + 5:
                setThemeId((String) c.data);
                break;
            case C_MORE_WORD:
                showPanel(PANEL_KEYS);
                handler.pickWord((String) c.data, 1);
                break;
            default:
        }
    }

    private void setThemeId(String id) {
        prefs.setTheme(id);
        applyPrefs();
        buildThemePanel();
        handler.updateStrip();
    }

    private void arrow(InputConnection ic, int code) {
        if (ic == null) return;
        handler.finishComposingForEdit();
        long t = SystemClock.uptimeMillis();
        int meta = selecting ? KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON : 0;
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta));
    }

    private void contextAction(InputConnection ic, int id) {
        if (ic == null) return;
        handler.finishComposingForEdit();
        ic.performContextMenuAction(id);
    }

    // ------------------------------------------------------------------ resize mode (frame with handles)
    private void startResize() {
        showPanel(PANEL_KEYS);
        float W = keyboard.getWidth() > 0 ? keyboard.getWidth() : getResources().getDisplayMetrics().widthPixels;
        float aw = W * prefs.widthPercent() / 100f;
        rsLeft = (W - aw) * prefs.offsetPercent() / 100f;
        rsRight = rsLeft + aw;
        rsHeightPct = prefs.heightPercent();
        rsLiftDp = prefs.liftDp();
        resizing = true;
        keyboard.cancelAllPointers();
        resizeFrame.setVisibility(View.VISIBLE);
        List<CandidateStrip.Item> items = new ArrayList<>();
        CandidateStrip.Item info = new CandidateStrip.Item("اسحب الحواف لتغيير الحجم، والوسط لتحريك لوحة المفاتيح",
            CandidateStrip.INFO, null);
        info.weight = 1f;
        items.add(info);
        strip.setChips(items);
        applySizes();
    }

    @Override public void onResizeDrag(int handle, float dx, float dy) {
        float d = getResources().getDisplayMetrics().density;
        float W = keyboard.getWidth();
        float minW = W * 0.55f;
        float baseH = KeyboardView.computeHeight(this, layoutFor(arrowCached ? "ar_arrows" : "ar"), 100);
        switch (handle) {
            case ResizeFrame.TOP:
                rsHeightPct -= dy / baseH * 100f;
                break;
            case ResizeFrame.BOTTOM: {
                float lift = rsLiftDp - dy / d;
                if (lift < 0) { dy += lift * d; lift = 0; }
                rsHeightPct += (rsLiftDp - lift) * d / baseH * 100f;
                rsLiftDp = lift;
                break;
            }
            case ResizeFrame.LEFT:
                rsLeft = Math.max(0, Math.min(rsRight - minW, rsLeft + dx));
                break;
            case ResizeFrame.RIGHT:
                rsRight = Math.min(W, Math.max(rsLeft + minW, rsRight + dx));
                break;
            case ResizeFrame.MOVE: {
                float shift = Math.max(-rsLeft, Math.min(W - rsRight, dx));
                rsLeft += shift;
                rsRight += shift;
                rsLiftDp = Math.max(0, Math.min(160, rsLiftDp - dy / d));
                break;
            }
            default:
        }
        rsHeightPct = Math.max(70, Math.min(140, rsHeightPct));
        rsLiftDp = Math.max(0, Math.min(160, rsLiftDp));
        applySizes();
    }

    @Override public void onResizeDone() {
        float W = keyboard.getWidth();
        float aw = rsRight - rsLeft;
        prefs.setHeightPercent(Math.round(rsHeightPct));
        prefs.setLiftDp(Math.round(rsLiftDp));
        prefs.setWidthPercent(Math.round(aw / W * 100f));
        prefs.setOffsetPercent(W - aw > 1f ? Math.round(rsLeft / (W - aw) * 100f) : 50);
        endResize();
    }

    @Override public void onResizeReset() {
        float W = keyboard.getWidth();
        rsLeft = 0;
        rsRight = W;
        rsHeightPct = 100;
        rsLiftDp = 0;
        applySizes();
    }

    private void endResize() {
        resizing = false;
        resizeFrame.setVisibility(View.GONE);
        applySizes();
        handler.updateStrip();
    }

    // ------------------------------------------------------------------ LibraryPanel.Host
    @Override public void insertSnippet(Snippet s) {
        showPanel(PANEL_KEYS);
        handler.insertSnippet(s);
    }

    @Override public void copySnippet(Snippet s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(s.title, s.body));
        Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show();
    }

    @Override public void startLibrarySearch() {
        String q = library.query();
        showPanel(PANEL_KEYS);
        handler.startSearch(q);
    }

    @Override public void saveClipboardTo(long collectionId) {
        // Reads the clipboard only because the user pressed «حفظ المنسوخ».
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        CharSequence text = null;
        try {
            ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) text = clip.getItemAt(0).coerceToText(this);
        } catch (RuntimeException ignored) { }
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(this, "لا يوجد نص منسوخ", Toast.LENGTH_SHORT).show();
            return;
        }
        final String body = text.toString().trim();
        final Context app = getApplicationContext();
        runOnBackground(() -> {
            LibraryDb db = LibraryDb.get(app);
            long target = collectionId;
            if (target >= 0) {
                for (SnippetCollection c : db.collections(false)) if (c.id == target && c.isBuiltin()) target = -1;
            }
            if (target < 0) target = db.addCollection("نصوص محفوظة");
            Snippet s = new Snippet();
            s.collectionId = target;
            s.body = body;
            String firstLine = body.split("\n", 2)[0].trim();
            s.title = firstLine.length() > 30 ? firstLine.substring(0, 30) + "…" : firstLine;
            s.type = "أخرى";
            db.saveSnippet(s);
            root.post(() -> {
                Toast.makeText(this, "تم حفظ النص في المكتبة", Toast.LENGTH_SHORT).show();
                library.refresh();
            });
        });
    }

    @Override public void togglePin(Snippet s) {
        final Context app = getApplicationContext();
        runOnBackground(() -> {
            LibraryDb.get(app).setPinned(s.id, !s.pinned);
            root.post(() -> library.refresh());
        });
    }

    // ------------------------------------------------------------------ InputHandler.Ui
    @Override public InputConnection ic() { return getCurrentInputConnection(); }

    @Override public void showStrip(List<CandidateStrip.Item> items, boolean chips) {
        if (strip == null || resizing) return;
        if (!chips && items.isEmpty() && !handler.internalActive()) {
            List<CandidateStrip.Item> shortcuts = new ArrayList<>();
            for (int id : prefs.stripShortcuts()) {
                if (id == C_LIBRARY && handler.isSensitive()) continue;
                final int cid = id;
                shortcuts.add(new CandidateStrip.Item(MenuItems.glyph(id) + " " + MenuItems.title(id), CandidateStrip.ACTION,
                    (Runnable) () -> onCell(cell(cid, MenuItems.title(cid), null))));
            }
            if (!shortcuts.isEmpty()) { strip.setChips(shortcuts); return; }
        }
        if (chips) strip.setChips(items);
        else strip.setSuggestions(items, handler.arabic());
    }

    @Override public void showShift(boolean shifted, boolean locked) {
        if (keyboard != null) keyboard.setShift(shifted, locked);
    }

    @Override public void openLibraryWithQuery(String query) {
        library.setQuery(query);
        showPanel(PANEL_LIBRARY);
    }

    @Override public void runOnBackground(Runnable r) {
        try { bg.execute(r); } catch (RuntimeException ignored) { }
    }

    /**
     * Input view root: as tall as the strip + keys only (never the whole screen). The overlay is
     * measured to exactly the same height, so the bubbles use the transparent spacer at the top.
     */
    static final class RootLayout extends FrameLayout {
        View column;
        View overlay;

        RootLayout(Context c) { super(c); }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            if (column == null || overlay == null) { super.onMeasure(widthSpec, heightSpec); return; }
            int width = MeasureSpec.getSize(widthSpec);
            int w = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            column.measure(w, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int h = column.getMeasuredHeight();
            overlay.measure(w, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            setMeasuredDimension(width, h);
        }
    }
}
