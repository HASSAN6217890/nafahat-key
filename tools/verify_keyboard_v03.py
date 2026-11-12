#!/usr/bin/env python3
"""Source guardrails: compilation and physical device testing are still required."""
import pathlib,re
root=pathlib.Path("app/src/main")
layout=(root/"java/com/nafahat/key/KeyboardLayout.java").read_text()
engine=(root/"java/com/nafahat/key/KeyboardEngine.java").read_text()
service=(root/"java/com/nafahat/key/NafahatKeyboardService.java").read_text()
canvas=(root/"java/com/nafahat/key/CanvasKeyboardView.java").read_text()
manifest=(root/"AndroidManifest.xml").read_text()
assert 'k("ض","1")' in layout and 'k("ذ","*")' in layout
assert 'k("ئ","_")' in layout and 'k("ء","~")' in layout and 'k("ؤ","-")' in layout
assert "android.permission.INTERNET" not in manifest
assert 'android:allowBackup="false"' in manifest and "android:dataExtractionRules" in manifest
assert "PopupWindow" not in service and "new Button" not in service
assert "removeAllViews" not in canvas and "onDraw(Canvas" in canvas
assert "IME_FLAG_NO_ENTER_ACTION" in engine
assert "IME_FLAG_NO_PERSONALIZED_LEARNING" in engine
assert "TYPE_TEXT_FLAG_NO_SUGGESTIONS" in engine
assert "setComposingText" in engine and "beginBatchEdit" in engine
assert "السلام عليكم ورحمة الله وبركاته 🤍" in engine
for p in (root/"java/com/nafahat/key").glob("*.java"):
    assert not re.search("[٠-٩۰-۹]",p.read_text()),f"Forbidden numeric glyph: {p}"
print("v0.3 static checks passed")
