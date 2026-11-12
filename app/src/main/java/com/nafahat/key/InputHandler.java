package com.nafahat.key;

import android.content.Context;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns all text logic: shift / caps / auto-cap, field type, enter action, the composing word,
 * suggestions, autocorrect + undo, cursor tracking (onUpdateSelection) and batch edits.
 * It never reads the field text on ordinary key presses and never logs what is typed.
 */
public final class InputHandler {

    public interface Ui {
        InputConnection ic();
        void showStrip(List<CandidateStrip.Item> items, boolean chips);
        void showShift(boolean shifted, boolean locked);
        void openLibraryWithQuery(String query);
        void runOnBackground(Runnable r);
    }

    private static final int SEARCH = 1;
    private static final int TEMPLATE = 2;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}\\n]{1,20})\\}");

    private final Context context;
    private final Ui ui;
    private final Suggester suggester;
    private final UserDict userDict;
    private KeyboardPrefs prefs;

    // field
    private EditorInfo info;
    private boolean sensitive, typeNull, numeric, phone, email, url, multiLine;
    private boolean autoCorrectField, learnField;
    private int enterAction = -1;

    // language / shift
    private boolean arabic = true;
    private int shift;            // 0 off, 1 one-shot, 2 locked
    private boolean autoShifted;
    private long lastShiftTap;

    // composing + cursor belief
    private final StringBuilder word = new StringBuilder();
    private int composeStart = -1;
    private int belief = -1;      // where we believe the cursor is (-1 unknown)
    private int selStart = -1, selEnd = -1;
    private final int[] recent = new int[8];
    private int recentPos;
    private String prevWord;
    private boolean autoSpace;
    private boolean emptyField;
    private int cursorMax = Integer.MAX_VALUE;

    private static final class Correction {
        final String original, corrected, sep;
        int end;
        Correction(String o, String c, String s) { original = o; corrected = c; sep = s; }
    }
    private Correction lastCorrection;
    private Suggester.Result current;

    // internal editor (library search / template filling)
    private int internalKind;
    private String[] fieldNames;
    private StringBuilder[] fieldValues;
    private int fieldIndex;
    private Snippet templateSnippet;
    private boolean templateReplacesWord;

    // cached snippets for #shortcuts
    private List<Snippet> snippetCache;
    private int snippetCacheVersion = -1;

    public InputHandler(Context context, Ui ui) {
        this.context = context;
        this.ui = ui;
        this.suggester = Suggester.get(context);
        this.userDict = UserDict.get(context);
        this.prefs = new KeyboardPrefs(context);
        for (int i = 0; i < recent.length; i++) recent[i] = -2;
    }

    public void setPrefs(KeyboardPrefs p) { prefs = p; }

    // ================================================================== field setup
    public void startInput(EditorInfo editorInfo, boolean restarting) {
        info = editorInfo;
        word.setLength(0);
        composeStart = -1;
        lastCorrection = null;
        autoSpace = false;
        prevWord = null;
        current = null;
        endInternal(false);
        int type = info == null ? 0 : info.inputType;
        int cls = type & InputType.TYPE_MASK_CLASS;
        int var = type & InputType.TYPE_MASK_VARIATION;
        int flags = type & InputType.TYPE_MASK_FLAGS;
        int ime = info == null ? 0 : info.imeOptions;
        typeNull = type == InputType.TYPE_NULL;
        numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_DATETIME;
        phone = cls == InputType.TYPE_CLASS_PHONE;
        boolean textClass = cls == InputType.TYPE_CLASS_TEXT;
        boolean password = (textClass && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
            || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
            || (cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        boolean noSuggestions = textClass && (flags & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0;
        boolean noLearning = (ime & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
        sensitive = password || noSuggestions || noLearning;
        email = textClass && (var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
        url = textClass && var == InputType.TYPE_TEXT_VARIATION_URI;
        multiLine = textClass && (flags & (InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE)) != 0;
        autoCorrectField = textClass && !sensitive && !email && !url
            && var != InputType.TYPE_TEXT_VARIATION_PERSON_NAME && var != InputType.TYPE_TEXT_VARIATION_FILTER
            && var != InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS;
        learnField = textClass && !sensitive && !email && !url;
        int action = ime & EditorInfo.IME_MASK_ACTION;
        boolean noEnterAction = (ime & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        enterAction = (noEnterAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED)
            ? -1 : action;
        belief = info != null ? info.initialSelStart : -1;
        selStart = belief;
        selEnd = info != null ? info.initialSelEnd : -1;
        if (shift != 2) { shift = 0; autoShifted = false; }
    }

    /** Called when the keyboard becomes visible for the field (may do one read of the field). */
    public void startInputView() {
        emptyField = false;
        if (canSuggest()) {
            InputConnection ic = ui.ic();
            if (ic != null) {
                CharSequence b = ic.getTextBeforeCursor(1, 0);
                CharSequence a = ic.getTextAfterCursor(1, 0);
                emptyField = (b == null || b.length() == 0) && (a == null || a.length() == 0);
            }
        }
        updateAutoCap();
        updateStrip();
    }

    public void finishInput() {
        word.setLength(0);
        composeStart = -1;
        lastCorrection = null;
        suggester.cancel();
        endInternal(false);
    }

    public boolean isSensitive() { return sensitive; }
    public boolean isNumeric() { return numeric; }
    public boolean isPhone() { return phone; }
    public boolean isEmail() { return email; }
    public boolean isUrl() { return url; }
    public int enterAction() { return enterAction; }
    public boolean arabic() { return arabic; }
    public boolean internalActive() { return internalKind != 0; }

    public void setArabic(boolean yes) {
        if (arabic == yes) return;
        commitWordAsIs();
        arabic = yes;
        if (yes) { shift = 0; autoShifted = false; ui.showShift(false, false); }
        else updateAutoCap();
        updateStrip();
    }

    private boolean canCompose() {
        return !sensitive && !typeNull && !numeric && !phone && info != null;
    }

    private boolean canSuggest() { return canCompose(); }

    private boolean autocorrectOn() { return autoCorrectField && prefs.autoCorrect(); }

    // ================================================================== cursor tracking
    private void setBelief(int pos) {
        belief = pos;
        recent[recentPos] = pos;
        recentPos = (recentPos + 1) % recent.length;
    }

    private boolean wasRecent(int pos) {
        for (int r : recent) if (r == pos) return true;
        return false;
    }

    public void onUpdateSelection(int newSelStart, int newSelEnd, int candStart, int candEnd) {
        selStart = newSelStart;
        selEnd = newSelEnd;
        boolean collapsed = newSelStart == newSelEnd;
        boolean ours;
        if (word.length() > 0) {
            if (composeStart < 0 && candStart >= 0) composeStart = candStart;
            // Ours: the composing region we set, or a stale update from before the word started.
            ours = collapsed && ((candStart >= 0 && candStart == composeStart && candEnd == newSelEnd
                && candEnd - candStart <= word.length())
                || (candStart < 0 && composeStart >= 0 && newSelStart <= composeStart));
        } else {
            ours = collapsed && (belief < 0 || newSelStart == belief || wasRecent(newSelStart));
            if (ours && belief < 0) belief = newSelStart;
        }
        if (ours) return;
        // The user moved the cursor or selected text.
        InputConnection ic = ui.ic();
        if (word.length() > 0 && ic != null) ic.finishComposingText();
        word.setLength(0);
        composeStart = -1;
        belief = newSelStart;
        lastCorrection = null;
        autoSpace = false;
        prevWord = null;
        emptyField = false;
        current = null;
        suggester.cancel();
        if (internalKind == 0) {
            updateAutoCap();
            updateStrip();
        }
    }

    // ================================================================== keys
    public void onText(String text) {
        if (text == null || text.isEmpty()) return;
        if (internalKind != 0) { internalAppend(text); return; }
        InputConnection ic = ui.ic();
        if (ic == null) return;
        emptyField = false;
        if (canCompose() && isWordText(text)) {
            lastCorrection = null;
            autoSpace = false;
            if (word.length() == 0) composeStart = selStart != selEnd && selStart >= 0 ? Math.min(selStart, selEnd) : belief;
            word.append(text);
            ic.setComposingText(word, 1);
            if (composeStart >= 0) setBelief(composeStart + word.length()); else belief = -1;
            afterCharShift();
            requestSuggestions();
            return;
        }
        boolean punct = text.length() == 1 && ".,!?؟،؛:".indexOf(text.charAt(0)) >= 0;
        if (autoSpace && punct && word.length() == 0) {
            ic.deleteSurroundingText(1, 0);
            if (belief > 0) setBelief(belief - 1);
        }
        autoSpace = false;
        boolean correct = punct && text.charAt(0) != '.' && text.charAt(0) != ':';
        finishWord(text, correct);
        afterCharShift();
    }

    public void onSpace() {
        if (internalKind != 0) { internalAppend(" "); return; }
        if (ui.ic() == null) return;
        emptyField = false;
        autoSpace = false;
        finishWord(" ", true);
    }

    private boolean isWordText(String text) {
        if (text.length() == 1) {
            char c = text.charAt(0);
            if (TextNorm.isWordChar(c)) return true;
            if (c == '#' && word.length() == 0) return true;
            if (word.length() > 0 && word.charAt(0) == '#' && (Character.isDigit(c) || c == '_' || c == '.')) return true;
            return c == '\'' && word.length() > 0 && !arabic;
        }
        for (int i = 0; i < text.length(); i++) if (!TextNorm.isWordChar(text.charAt(i))) return false;
        return true;
    }

    /** Commits the composing word (autocorrected when allowed) followed by sep. */
    private void finishWord(String sep, boolean allowCorrection) {
        InputConnection ic = ui.ic();
        if (ic == null) return;
        String typed = word.toString();
        String committed = typed;
        Correction corr = null;
        ic.beginBatchEdit();
        if (!typed.isEmpty()) {
            if (typed.startsWith("#")) allowCorrection = false;
            if (allowCorrection && autocorrectOn() && !TextNorm.hasDigit(typed) && scriptMatches(typed)
                && suggester.ready(arabic)) {
                Suggester.Result r = suggester.now(typed, arabic, true);
                if (r.autoCorrect != null && !r.autoCorrect.equals(typed)) {
                    committed = r.autoCorrect;
                    corr = new Correction(typed, committed, sep == null ? "" : sep);
                }
            }
            ic.commitText(committed, 1);
            if (composeStart >= 0) setBelief(composeStart + committed.length()); else belief = -1;
            learn(committed, prevWord, 1);
            prevWord = UserDict.learnable(committed) ? committed : null;
            word.setLength(0);
            composeStart = -1;
        }
        if (sep != null && !sep.isEmpty()) {
            ic.commitText(sep, 1);
            if (belief >= 0) setBelief(belief + sep.length());
            if (!" ".equals(sep)) prevWord = null;
        }
        ic.endBatchEdit();
        lastCorrection = corr;
        if (corr != null) corr.end = belief;
        current = null;
        suggester.cancel();
        if (sep != null) updateAutoCap();
        updateStrip();
    }

    private boolean scriptMatches(String w) {
        return arabic ? TextNorm.isArabicWord(w) : TextNorm.isLatinWord(w);
    }

    /** Commits the composing word exactly as typed (no correction). */
    public void commitWordAsIs() {
        if (word.length() == 0) return;
        InputConnection ic = ui.ic();
        if (ic != null) ic.finishComposingText();
        String typed = word.toString();
        learn(typed, prevWord, 1);
        prevWord = UserDict.learnable(typed) ? typed : null;
        word.setLength(0);
        composeStart = -1;
        current = null;
        suggester.cancel();
    }

    private void learn(String w, String prev, int weight) {
        if (!learnField || sensitive) return;
        userDict.learn(w, prev, weight);
    }

    public void onEnter() {
        if (internalKind != 0) { internalNext(); return; }
        InputConnection ic = ui.ic();
        if (ic == null) return;
        finishWord(null, false);
        prevWord = null;
        autoSpace = false;
        int ime = info == null ? 0 : info.imeOptions;
        if (enterAction >= 0) {
            ic.performEditorAction(ime & EditorInfo.IME_MASK_ACTION);
        } else if (multiLine) {
            ic.commitText("\n", 1);
            if (belief >= 0) setBelief(belief + 1);
        } else {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            belief = -1;
        }
        updateAutoCap();
        updateStrip();
    }

    public void onDelete(int repeat) {
        if (internalKind != 0) { internalBackspace(); return; }
        InputConnection ic = ui.ic();
        if (ic == null) return;
        autoSpace = false;
        if (repeat == 0 && lastCorrection != null && word.length() == 0 && belief == lastCorrection.end) {
            revertCorrection();
            return;
        }
        lastCorrection = null;
        if (word.length() > 0) {
            if (repeat > 15) {
                word.setLength(0);
            } else {
                int cp = word.codePointBefore(word.length());
                word.setLength(word.length() - Character.charCount(cp));
            }
            if (word.length() == 0) {
                ic.setComposingText("", 1);
                ic.finishComposingText();
                if (composeStart >= 0) setBelief(composeStart); else belief = -1;
                composeStart = -1;
                current = null;
                suggester.cancel();
                updateStrip();
            } else {
                ic.setComposingText(word, 1);
                if (composeStart >= 0) setBelief(composeStart + word.length());
                requestSuggestions();
            }
            return;
        }
        if (selStart >= 0 && selEnd >= 0 && selStart != selEnd) {
            ic.commitText("", 1);
            setBelief(Math.min(selStart, selEnd));
            updateStrip();
            return;
        }
        if (repeat > 15) {
            deleteWordBefore(ic);
            return;
        }
        if (repeat > 0 || !canCompose()) {
            if (belief == 0 || typeNull) sendDel(ic);
            else ic.deleteSurroundingTextInCodePoints(1, 0);
            belief = -1;
            if (repeat == 0) updateAutoCap();
            return;
        }
        // Single tap: one small read so we can resume the previous word for suggestions.
        CharSequence before = ic.getTextBeforeCursor(48, 0);
        if (before == null || before.length() == 0) {
            sendDel(ic);
            belief = -1;
            return;
        }
        String b = before.toString();
        int cp = b.codePointBefore(b.length());
        int units = Character.charCount(cp);
        String remaining = b.substring(0, b.length() - units);
        int start = remaining.length();
        while (start > 0 && TextNorm.isWordChar(remaining.charAt(start - 1))) start--;
        String tail = remaining.substring(start);
        boolean resume = !tail.isEmpty() && tail.length() <= 24 && canSuggest()
            && (start > 0 ? Character.isWhitespace(remaining.charAt(start - 1)) : b.length() < 48);
        if (resume) {
            // Re-open the previous word for suggestions using relative edits only (no absolute positions).
            ic.beginBatchEdit();
            ic.deleteSurroundingText(units + tail.length(), 0);
            ic.setComposingText(tail, 1);
            ic.endBatchEdit();
            if (belief >= 0) {
                composeStart = belief - units - tail.length();
                setBelief(belief - units);
            } else {
                composeStart = -1;
            }
            word.setLength(0);
            word.append(tail);
            prevWord = null;
            requestSuggestions();
        } else {
            ic.deleteSurroundingText(units, 0);
            if (belief >= 0) setBelief(belief - units);
            prevWord = null;
            if (belief == 0) {
                CharSequence after = ic.getTextAfterCursor(1, 0);
                emptyField = after == null || after.length() == 0;
            }
            updateAutoCap();
            updateStrip();
        }
    }

    private void sendDel(InputConnection ic) {
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
    }

    private void deleteWordBefore(InputConnection ic) {
        CharSequence before = ic.getTextBeforeCursor(64, 0);
        if (before == null || before.length() == 0) return;
        String b = before.toString();
        int i = b.length();
        while (i > 0 && Character.isWhitespace(b.charAt(i - 1))) i--;
        if (i > 0 && TextNorm.isWordChar(b.charAt(i - 1))) {
            while (i > 0 && TextNorm.isWordChar(b.charAt(i - 1))) i--;
        } else if (i > 0) {
            i -= Character.charCount(b.codePointBefore(i));
        }
        int n = b.length() - i;
        if (n <= 0) return;
        ic.deleteSurroundingText(n, 0);
        if (belief >= 0) setBelief(belief - n);
        updateStrip();
    }

    private void revertCorrection() {
        Correction c = lastCorrection;
        lastCorrection = null;
        InputConnection ic = ui.ic();
        if (c == null || ic == null) return;
        ic.beginBatchEdit();
        ic.deleteSurroundingText(c.corrected.length() + c.sep.length(), 0);
        ic.commitText(c.original + c.sep, 1);
        ic.endBatchEdit();
        if (belief >= 0) setBelief(belief - c.corrected.length() + c.original.length());
        // Keeping the original word means it is wanted: never correct it again.
        if (learnField && !sensitive) userDict.learn(c.original, null, 2);
        prevWord = UserDict.learnable(c.original) ? c.original : null;
        updateStrip();
    }

    // ================================================================== shift
    public void onShift() {
        if (arabic) return;
        long now = SystemClock.uptimeMillis();
        if (shift == 0) { shift = 1; }
        else if (shift == 1) { shift = (now - lastShiftTap < 350 && !autoShifted) ? 2 : 0; }
        else shift = 0;
        autoShifted = false;
        lastShiftTap = now;
        ui.showShift(shift > 0, shift == 2);
    }

    public boolean shifted() { return shift > 0; }

    private void afterCharShift() {
        if (shift == 1) {
            shift = 0;
            autoShifted = false;
            ui.showShift(false, false);
        }
    }

    private void updateAutoCap() {
        if (arabic || info == null || shift == 2) return;
        boolean want = false;
        if (prefs.autoCap() && !sensitive && internalKind == 0) {
            int capsFlags = info.inputType & (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            if (capsFlags != 0 && word.length() == 0) {
                InputConnection ic = ui.ic();
                want = ic != null && ic.getCursorCapsMode(info.inputType) != 0;
            }
        }
        if (want && shift == 0) { shift = 1; autoShifted = true; ui.showShift(true, false); }
        else if (!want && shift == 1 && autoShifted) { shift = 0; autoShifted = false; ui.showShift(false, false); }
    }

    // ================================================================== cursor move (space long-press drag)
    public void beginCursorDrag() {
        commitWordAsIs();
        InputConnection ic = ui.ic();
        cursorMax = Integer.MAX_VALUE;
        if (ic != null && belief >= 0) {
            CharSequence after = ic.getTextAfterCursor(2000, 0);
            if (after != null) cursorMax = belief + after.length();
        }
    }

    private long lastCursorMove;

    public void onCursorMove(int steps) {
        InputConnection ic = ui.ic();
        if (ic == null || steps == 0) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastCursorMove > 600 || word.length() > 0) beginCursorDrag();
        lastCursorMove = now;
        lastCorrection = null;
        int logical = arabic ? -steps : steps;
        if (belief >= 0) {
            int pos = Math.max(0, Math.min(cursorMax, belief + logical));
            ic.setSelection(pos, pos);
            setBelief(pos);
        } else {
            int code = steps > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
            for (int i = 0; i < Math.abs(steps); i++) {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
            }
        }
        prevWord = null;
    }

    // ================================================================== suggestions
    private void requestSuggestions() {
        if (!canSuggest() || word.length() == 0) { updateStrip(); return; }
        final String typed = word.toString();
        if (typed.startsWith("#")) { current = null; updateStrip(); return; }
        if (!suggester.ready(arabic)) { suggester.ensureLoaded(); updateStrip(); return; }
        suggester.request(typed, arabic, autocorrectOn() && scriptMatches(typed), r -> {
            if (!typed.equals(word.toString())) return;
            current = r;
            updateStrip();
        });
    }

    /** Rebuilds the strip content for the current state. */
    public void updateStrip() {
        if (internalKind != 0) { showInternalStrip(); return; }
        List<CandidateStrip.Item> items = new ArrayList<>();
        if (!canSuggest()) { ui.showStrip(items, false); return; }
        if (word.length() > 0) {
            String typed = word.toString();
            if (typed.startsWith("#")) {
                for (Snippet s : matchShortcuts(typed.substring(1), 3))
                    items.add(new CandidateStrip.Item("#" + s.title, CandidateStrip.SNIPPET, s));
            } else if (current != null && current.typed.equals(typed)) {
                if (current.autoCorrect != null) {
                    CandidateStrip.Item a = new CandidateStrip.Item(current.autoCorrect, CandidateStrip.SUGGESTION, current.autoCorrect);
                    a.strong = true;
                    items.add(a);
                    items.add(new CandidateStrip.Item("«" + typed + "»", CandidateStrip.LITERAL, typed));
                    for (String w : current.words) {
                        if (items.size() >= 3) break;
                        if (!w.equals(current.autoCorrect) && !w.equals(typed))
                            items.add(new CandidateStrip.Item(w, CandidateStrip.SUGGESTION, w));
                    }
                } else {
                    for (String w : current.words) {
                        if (items.size() >= 3) break;
                        items.add(new CandidateStrip.Item(w, CandidateStrip.SUGGESTION, w));
                    }
                }
            }
            ui.showStrip(items, false);
            return;
        }
        if (lastCorrection != null && belief == lastCorrection.end) {
            CandidateStrip.Item u = new CandidateStrip.Item("↶ " + lastCorrection.original, CandidateStrip.UNDO, null);
            u.strong = true;
            items.add(u);
        }
        if (emptyField && items.isEmpty()) {
            items.add(new CandidateStrip.Item(BrandData.GREETING, CandidateStrip.PHRASE, BrandData.GREETING));
            for (Snippet s : LibraryDb.get(context).mostUsed(2))
                items.add(new CandidateStrip.Item(s.title, CandidateStrip.SNIPPET, s));
            ui.showStrip(items, false);
            return;
        }
        if (prevWord != null && suggester.ready(arabic)) {
            for (String w : suggester.predict(prevWord, arabic, 3)) {
                if (items.size() >= 3) break;
                items.add(new CandidateStrip.Item(w, CandidateStrip.NEXT, w));
            }
        }
        ui.showStrip(items, false);
    }

    /** Extra suggestions for the "more" panel. */
    public List<String> moreSuggestions() {
        List<String> out = new ArrayList<>();
        if (current != null && word.length() > 0 && current.typed.equals(word.toString())) out.addAll(current.words);
        else if (prevWord != null) out.addAll(suggester.predict(prevWord, arabic, 12));
        return out;
    }

    public void onStripItem(CandidateStrip.Item item) {
        switch (item.kind) {
            case CandidateStrip.UNDO:
                revertCorrection();
                break;
            case CandidateStrip.SUGGESTION:
            case CandidateStrip.LITERAL:
            case CandidateStrip.NEXT:
                pickWord((String) item.data, item.kind == CandidateStrip.LITERAL ? 2 : 1);
                break;
            case CandidateStrip.PHRASE:
                commitWordAsIs();
                commitPlain((String) item.data + " ");
                break;
            case CandidateStrip.SNIPPET:
                if (internalKind == SEARCH) endInternal(false);
                insertSnippet((Snippet) item.data);
                break;
            case CandidateStrip.ACTION:
                if (item.data instanceof Runnable) ((Runnable) item.data).run();
                break;
            default:
        }
    }

    public void pickWord(String w, int weight) {
        InputConnection ic = ui.ic();
        if (ic == null || w == null) return;
        ic.beginBatchEdit();
        ic.commitText(w, 1);
        ic.commitText(" ", 1);
        ic.endBatchEdit();
        if (word.length() > 0) {
            if (composeStart >= 0) setBelief(composeStart + w.length() + 1); else belief = -1;
        } else if (belief >= 0) setBelief(belief + w.length() + 1);
        learn(w, prevWord, weight);
        prevWord = UserDict.learnable(w) ? w : null;
        word.setLength(0);
        composeStart = -1;
        lastCorrection = null;
        autoSpace = true;
        emptyField = false;
        current = null;
        suggester.cancel();
        afterCharShift();
        updateAutoCap();
        updateStrip();
    }

    private void commitPlain(String text) {
        InputConnection ic = ui.ic();
        if (ic == null) return;
        ic.commitText(text, 1);
        if (belief >= 0) setBelief(belief + text.length());
        emptyField = false;
        prevWord = null;
        lastCorrection = null;
        updateStrip();
    }

    // ================================================================== library
    private List<Snippet> snippets() {
        int v = LibraryDb.CHANGES.get();
        if (snippetCache == null || v != snippetCacheVersion) {
            snippetCache = LibraryDb.get(context).snippets(-1);
            snippetCacheVersion = v;
        }
        return snippetCache;
    }

    private List<Snippet> matchShortcuts(String q, int max) {
        List<Snippet> out = new ArrayList<>();
        String n = TextNorm.normalize(q);
        if (n.isEmpty()) return LibraryDb.get(context).mostUsed(max);
        for (Snippet s : snippets()) {
            if (!s.shortcut.isEmpty() && TextNorm.normalize(s.shortcut.replace("#", "")).startsWith(n)) out.add(s);
            if (out.size() >= max) return out;
        }
        for (Snippet s : snippets()) {
            if (out.contains(s)) continue;
            if (TextNorm.normalize(s.title).contains(n)) out.add(s);
            if (out.size() >= max) break;
        }
        return out;
    }

    /** Inserts a library text (template fields are asked for first). */
    public void insertSnippet(Snippet s) {
        if (sensitive || s == null) return;
        boolean replaceWord = word.length() > 0 && word.charAt(0) == '#';
        if (s.hasPlaceholders()) {
            startTemplate(s, replaceWord);
            return;
        }
        doInsert(s, s.body, replaceWord);
    }

    private void doInsert(Snippet s, String text, boolean replaceWord) {
        InputConnection ic = ui.ic();
        if (ic == null) return;
        if (!replaceWord) commitWordAsIs();
        ic.beginBatchEdit();
        ic.commitText(text, 1);
        ic.endBatchEdit();
        if (replaceWord && composeStart >= 0) setBelief(composeStart + text.length());
        else if (!replaceWord && belief >= 0) setBelief(belief + text.length());
        else belief = -1;
        word.setLength(0);
        composeStart = -1;
        prevWord = null;
        lastCorrection = null;
        emptyField = false;
        current = null;
        if (s.id > 0) {
            final long id = s.id;
            ui.runOnBackground(() -> LibraryDb.get(context).markUsed(id));
        }
        updateStrip();
    }

    // ================================================================== internal editor
    public void startSearch(String initial) {
        commitWordAsIs();
        internalKind = SEARCH;
        fieldNames = new String[]{"بحث"};
        fieldValues = new StringBuilder[]{new StringBuilder(initial == null ? "" : initial)};
        fieldIndex = 0;
        showInternalStrip();
    }

    private void startTemplate(Snippet s, boolean replaceWord) {
        Matcher m = PLACEHOLDER.matcher(s.body);
        Map<String, String> names = new LinkedHashMap<>();
        while (m.find()) names.put(m.group(1), m.group(1));
        if (names.isEmpty()) { doInsert(s, s.body, replaceWord); return; }
        internalKind = TEMPLATE;
        templateSnippet = s;
        templateReplacesWord = replaceWord;
        fieldNames = names.keySet().toArray(new String[0]);
        fieldValues = new StringBuilder[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            fieldValues[i] = new StringBuilder();
            if (fieldNames[i].trim().equals("التاريخ")) {
                Calendar c = Calendar.getInstance();
                fieldValues[i].append(String.format(Locale.US, "%02d/%02d/%04d",
                    c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR)));
            }
        }
        fieldIndex = 0;
        showInternalStrip();
    }

    private void internalAppend(String text) {
        if (fieldValues == null) return;
        fieldValues[fieldIndex].append(text);
        afterCharShift();
        showInternalStrip();
    }

    private void internalBackspace() {
        if (fieldValues == null) return;
        StringBuilder b = fieldValues[fieldIndex];
        if (b.length() > 0) b.setLength(b.length() - Character.charCount(b.codePointBefore(b.length())));
        showInternalStrip();
    }

    private void internalNext() {
        if (internalKind == SEARCH) {
            String q = fieldValues[0].toString().trim();
            endInternal(false);
            ui.openLibraryWithQuery(q);
            return;
        }
        if (internalKind == TEMPLATE) {
            if (fieldIndex < fieldNames.length - 1) {
                fieldIndex++;
                showInternalStrip();
                return;
            }
            String body = templateSnippet.body;
            for (int i = 0; i < fieldNames.length; i++)
                body = body.replace("{" + fieldNames[i] + "}", fieldValues[i].toString());
            Snippet s = templateSnippet;
            boolean replace = templateReplacesWord;
            endInternal(false);
            doInsert(s, body, replace);
        }
    }

    /** Leaves search / template mode. */
    public void endInternal(boolean refresh) {
        internalKind = 0;
        fieldNames = null;
        fieldValues = null;
        templateSnippet = null;
        if (refresh) updateStrip();
    }

    private void showInternalStrip() {
        List<CandidateStrip.Item> items = new ArrayList<>();
        String value = fieldValues[fieldIndex].toString();
        CandidateStrip.Item field = new CandidateStrip.Item(fieldNames[fieldIndex] + ": " + value + "▏", CandidateStrip.INFO, null);
        field.weight = 2.4f;
        items.add(field);
        if (internalKind == SEARCH) {
            for (Snippet s : matchShortcutsByText(value, 1))
                items.add(new CandidateStrip.Item(s.title, CandidateStrip.SNIPPET, s));
            CandidateStrip.Item go = new CandidateStrip.Item("النتائج", CandidateStrip.ACTION, (Runnable) this::internalNext);
            go.strong = true;
            items.add(go);
        } else {
            boolean last = fieldIndex == fieldNames.length - 1;
            CandidateStrip.Item go = new CandidateStrip.Item(last ? "إدراج" : "التالي", CandidateStrip.ACTION, (Runnable) this::internalNext);
            go.strong = true;
            items.add(go);
        }
        items.add(new CandidateStrip.Item("إلغاء", CandidateStrip.ACTION, (Runnable) () -> endInternal(true)));
        ui.showStrip(items, true);
    }

    private List<Snippet> matchShortcutsByText(String q, int max) {
        List<Snippet> out = new ArrayList<>();
        String n = TextNorm.normalize(q.trim());
        if (n.isEmpty()) return out;
        for (Snippet s : snippets()) {
            if (TextNorm.normalize(s.title + " " + s.body + " " + s.shortcut).contains(n)) out.add(s);
            if (out.size() >= max) break;
        }
        return out;
    }

    /** ⊞ «تراجع»: reverts the last autocorrection, otherwise asks the app to undo (Ctrl+Z). */
    public void undo() {
        if (lastCorrection != null && word.length() == 0 && belief == lastCorrection.end) { revertCorrection(); return; }
        InputConnection ic = ui.ic();
        if (ic == null) return;
        commitWordAsIs();
        long t = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON));
        belief = -1;
    }

    // ================================================================== text editing panel helpers
    public void finishComposingForEdit() {
        commitWordAsIs();
        lastCorrection = null;
        belief = -1;
    }
}
