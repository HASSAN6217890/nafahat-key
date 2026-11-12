#!/usr/bin/env python3
"""Static release checks before the Android debug APK build."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ime=(root/"app/src/main/java/com/nafahat/key/NafahatKeyboardService.java").read_text()
brand=(root/"app/src/main/java/com/nafahat/key/BrandData.java").read_text()
pref=(root/"app/src/main/java/com/nafahat/key/KeyboardPrefs.java").read_text()
gradle=(root/"app/build.gradle").read_text()
manifest=(root/"app/src/main/AndroidManifest.xml").read_text()
assert 'versionCode 2' in gradle and "versionName '0.2.0'" in gradle
assert 'android.permission.INTERNET' not in manifest
assert 'uses-permission' not in manifest
rows=[
    '"ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج"',
    '"ش","س","ي","ب","ل","ا","ت","ن","م","ك","ط"',
    '"ذ","ء","ؤ","ر","ى","ة","و","ز","ظ","د","⌫"',
]
assert all(row in ime for row in rows), "The complete Arabic baseline layout was changed"
assert all(ch in ime for ch in ["َ","ً","ُ","ٌ","ِ","ٍ","ّ","ْ","أ","إ","آ","ـ","ئ","لا"])
assert 'String[][] digits={{"1","2","3"},{"4","5","6"},{"7","8","9"},{"،","0","↵"}}' in ime
assert all(ch not in ime for ch in "٠١٢٣٤٥٦٧٨٩")
assert 'if(!sensitive) addToolbarButton' in ime
assert 'android.permission.BIND_INPUT_METHOD' in manifest
assert 'public static final String PHONE_LTR = "\\u2066" + PHONE + "\\u2069";' in brand
assert '+ PHONE_LTR + " عبر الاتصال' in brand
assert 'KeyboardPrefs' in ime and 'numberRow()' in ime
assert 'setTheme(' in pref and 'setVibrate(' in pref and 'setFontSize(' in pref
assert 'أسعار' not in brand and 'تكلفة' not in brand
print("NafahatKey 0.2 static release checks passed.")
