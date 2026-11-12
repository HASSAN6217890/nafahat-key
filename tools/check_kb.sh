#!/usr/bin/env bash
# v0.5 library: matcher, variant choice and team decryption on a plain JVM (no Android SDK).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/com/nafahat/key"
OUT="$(mktemp -d)"
javac -encoding UTF-8 -d "$OUT" "$SRC/TextNorm.java" "$SRC/Snippet.java" "$SRC/KbMatcher.java" "$SRC/KbCipher.java" "$ROOT/tools/kbtest/KbTest.java"
java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "$OUT" KbTest
