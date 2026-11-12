#!/usr/bin/env python3
"""Static release checks run in CI before building NafahatKey (0.3 rules + the 0.5 network rules)."""
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/com/nafahat/key"
java = {p.name: p.read_text(encoding="utf-8") for p in src.glob("*.java")}
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
layouts = java["Layouts.java"]
brand = java["BrandData.java"]
errors = []

def check(cond, msg):
    if not cond:
        errors.append(msg)

# Permissions / privacy
# v0.5: exactly one permission, INTERNET (download of the Nafahat library); every other one stays forbidden.
perms = re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', manifest)
check(perms == ["android.permission.INTERNET"], f"manifest must request exactly android.permission.INTERNET, found {perms}")
check(manifest.count("uses-permission") == 1, "only one <uses-permission> element is allowed")
check("uses-permission-sdk" not in manifest, "no uses-permission-sdk-23 either")
check('android:networkSecurityConfig="@xml/network_security_config"' in manifest, "network security config missing")
check('android:usesCleartextTraffic="false"' in manifest, "cleartext must stay off")
nsc = (root / "app/src/main/res/xml/network_security_config.xml").read_text(encoding="utf-8")
check('cleartextTrafficPermitted="false"' in nsc and 'src="system"' in nsc and 'src="user"' not in nsc,
      "network security config: no cleartext, system CAs only")
check("CAMERA" not in manifest and "RECEIVE_BOOT_COMPLETED" not in manifest, "no camera / boot permission")
check('android:allowBackup="false"' in manifest, "allowBackup must be false")
check("@xml/data_extraction_rules" in manifest, "dataExtractionRules missing")
rules = (root / "app/src/main/res/xml/data_extraction_rules.xml").read_text(encoding="utf-8")
check(rules.count("<exclude") >= 10, "data extraction rules must exclude every domain")
for name, text in java.items():
    check("android.util.Log" not in text and "Log." not in text, f"{name}: no logging allowed")
    check(not re.search(r"[٠-٩۰-۹]", text), f"{name}: Arabic-Indic digits are not allowed")
    if name != "KbApi.java":
        check("java.net" not in text and "URLConnection" not in text and "javax.net" not in text and "Socket" not in text,
              f"{name}: networking is allowed only in KbApi.java")
check("keystore" not in gradle.lower() or "NAFAHATKEY_KEYSTORE_PATH" in gradle, "signing must come from env only")
for p in root.rglob("*"):
    if p.suffix in (".jks", ".keystore", ".p12"):
        errors.append(f"keystore file must never be committed: {p}")

# v0.5 KbApi: GET only, fixed host, no redirects, 15 s timeouts, nothing uploaded
api = java.get("KbApi.java", "")
check('HOST = "card.nafahatsy.com"' in api, "KbApi host must be hard-coded to card.nafahatsy.com")
check("setInstanceFollowRedirects(false)" in api, "KbApi must not follow redirects")
check(api.count("TIMEOUT_MS = 15000") == 1 and "setConnectTimeout(TIMEOUT_MS)" in api and "setReadTimeout(TIMEOUT_MS)" in api, "15 s timeouts")
check('setRequestMethod("GET")' in api and "POST" not in api and "PUT" not in api and "getOutputStream" not in api
      and "setDoOutput(true)" not in api, "KbApi is GET only and never writes a body")
check(set(re.findall(r'setRequestProperty\("([^"]+)"', api)) <= {"User-Agent", "Accept"}, "no custom headers beyond User-Agent / Accept")
check(re.findall(r'"(/kb/[^"]*)"', api) == ["/kb/v.json?ts=", "/kb/library."], "only /kb/v.json and /kb/library.<rev>.json")
check("http://" not in api, "https only")
sync = java.get("KbSync.java", "")
check("sha256Hex(data).equals(sha)" in sync, "KbSync must verify sha256 from v.json")
for name, text in java.items():
    if name != "KbApi.java":
        check("KbApi." not in text or name == "KbSync.java", f"{name}: only KbSync may call KbApi")

# Arabic layout exactly as SwiftKey (spec section 2)
rows = [
    'Key.ch("ض", "1"), Key.ch("ص", "2"), Key.ch("ث", "3"), Key.ch("ق", "4")',
    'Key.ch("ف", "5"), Key.ch("غ", "6"), Key.ch("ع", "7"), Key.ch("ه", "8", "ة")',
    'Key.ch("خ", "9"), Key.ch("ح", "0"), Key.ch("ج", "چ"), Key.ch("د", "الله")',
    'Key.ch("ش", "@"), Key.ch("س", "#"), Key.ch("ي", "&", "ئ", "ى"), Key.ch("ب", "پ")',
    'Key.ch("ل", "لا", "لأ", "لإ", "لآ"), Key.ch("ا", "أ", "إ", "آ", "ٱ"), Key.ch("ت", "=")',
    'Key.ch("ن", "("), Key.ch("م", ")"), Key.ch("ك", "گ"), Key.ch("ذ", "*")',
    'Key.ch("ئ", "_"), Key.ch("ء", "~"), Key.ch("ؤ", "-"), Key.ch("ر", "+")',
    'Key.ch("ى", ":"), Key.ch("ة", "؛"), Key.ch("و", "/", "ؤ"), Key.ch("ز", "ژ")',
    'Key.ch("ط", "\\""), Key.ch("ظ", "ـِ"), del(1.5f)',
]
for r in rows:
    check(r in layouts, f"Arabic row changed: {r}")
for d in ["ـَ", "ـً", "ـُ", "ـٌ", "ـِ", "ـٍ", "ـّ", "ـْ"]:
    check(d in layouts, f"diacritic {d} missing")

# Symbols: no duplicates across the two pages
symbols = []
for m in re.finditer(r'\bsym\(', layouts):
    i = m.end()
    while True:
        while layouts[i] in ' ,\n':
            i += 1
        if layouts[i] != '"':
            break
        j = i + 1
        while layouts[j] != '"':
            j += 2 if layouts[j] == '\\' else 1
        symbols.append(layouts[i + 1:j])
        i = j + 1
check(len(symbols) == len(set(symbols)), "duplicate key in the symbol pages")
for s in ["؟", "؛", "!", "@", "#", "$", "%", "&", "*", "(", ")", "-", "_", "+", "=", "/", "\\\\", ":", ";",
          "<", ">", "[", "]", "{", "}", "~", "^", "|", "€", "£", "₺", "…"]:
    check(s in symbols, f"symbol {s} missing from the symbol pages")

# Public info
check('PHONE_LTR = "\\u200E" + PHONE' in brand, "phone must have exactly one LRM before +")
check("\\u2066" not in brand and "\\u2069" not in brand, "no isolate marks around the phone")
check('GREETING = "السلام عليكم ورحمة الله وبركاته 🤍"' in brand, "greeting text")
check("GREETING + " in brand and '"reply.welcome"' in brand, "welcome reply must start with the greeting")
for word in ["سعر", "أسعار", "تكلفة", "ريال", "دولار", "$", "ليرة"]:
    check(word not in brand, f"BrandData must not contain financial info ({word})")

# Dictionary assets
dict_dir = root / "app/src/main/assets/dict"
for name, minimum in (("ar.txt", 45000), ("en.txt", 25000), ("bigrams.txt", 100)):
    p = dict_dir / name
    check(p.exists(), f"missing asset {name}")
    if p.exists():
        text = p.read_text(encoding="utf-8")
        check(text.count("\n") >= minimum, f"{name} too small")
        check(not re.search(r"[٠-٩۰-۹]", text), f"{name}: Arabic-Indic digits")

if errors:
    print("FAILED:")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print("NafahatKey static checks passed (0.3 rules + 0.5 network rules).")
