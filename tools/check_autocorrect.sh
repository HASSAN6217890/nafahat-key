#!/usr/bin/env bash
# Autocorrect regression: compiles the real Java policy with two tiny stand-ins and runs the cases.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/com/nafahat/key"
OUT="$(mktemp -d)"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/tools/actest/android/graphics/RectF.java" "$ROOT/tools/actest/android/content/res/AssetManager.java" \
  "$SRC/Key.java" "$SRC/Layout.java" "$SRC/Layouts.java" "$SRC/Proximity.java" "$SRC/TextNorm.java" \
  "$SRC/Dictionary.java" "$SRC/AutoCorrect.java" "$ROOT/tools/actest/AutoCorrectTest.java"
java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -Dassets="$ROOT/app/src/main/assets" -cp "$OUT" AutoCorrectTest "$ROOT/tools/autocorrect_cases.txt"
python3 "$ROOT/tools/sim_autocorrect.py" --cases "$ROOT/tools/autocorrect_cases.txt"
