#!/usr/bin/env python3
"""Independent Python simulation of the Arabic autocorrect policy (AutoCorrect.java) against ar.txt.

Originally written by the project's critic to expose over-eager corrections; kept as a second,
independent implementation. CI runs it on tools/autocorrect_cases.txt next to the Java test.
Usage: sim_autocorrect.py --cases FILE      or      sim_autocorrect.py word1 word2 ...
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(ROOT, 'app/src/main/assets/dict/ar.txt')
MIN_TARGET_ZIPF = 4.0
MAX_COST = 0.6
PREFIXES = ["وال", "فال", "بال", "عال", "لل", "ال", "بت", "بي", "بن", "من", "عم", "رح", "و", "ف", "ب", "ل", "ع"]
SUFFIXES = ["تكن", "تكم", "تنا", "تها", "تهم", "كن", "كم", "كو", "نا", "ها", "هم", "هن", "ني", "لي", "لك", "لو",
            "ين", "ون", "ات", "وا", "تي", "تك", "ته", "تو", "ه", "ك", "ي", "و", "ت"]

def is_mark(c):
    o = ord(c)
    return 0x64B <= o <= 0x65F or o == 0x670 or o == 0x640

def norm(s):
    out = []
    for c in s:
        if is_mark(c):
            continue
        c = {'أ': 'ا', 'إ': 'ا', 'آ': 'ا', 'ٱ': 'ا', 'ة': 'ه', 'ى': 'ي'}.get(c, c)
        out.append(c.lower() if ord(c) < 0x600 else c)
    return ''.join(out)

rows = [(0, "ضصثقفغعهخحجد"), (0.5, "شسيبلاتنمكذ"), (0.5, "ئءؤرىةوزطظ")]
pos = {}
for ri, (off, ch) in enumerate(rows):
    for i, c in enumerate(ch):
        pos.setdefault(norm(c), []).append((off + i + 0.5, ri))
adj = set()
for a in pos:
    for b in pos:
        if a != b and any(abs(xa - xb) <= 1.05 and abs(ya - yb) <= 1.05 for xa, ya in pos[a] for xb, yb in pos[b]):
            adj.add((a, b))

def dist(a, b, mx):
    n, m = len(a), len(b)
    if abs(n - m) > mx:
        return mx + 1
    prev2 = [0] * (m + 1)
    prev = list(range(m + 1))
    for i in range(1, n + 1):
        cur = [0] * (m + 1)
        cur[0] = i
        for j in range(1, m + 1):
            sub = 0 if a[i - 1] == b[j - 1] else (0.6 if (a[i - 1], b[j - 1]) in adj else 1)
            v = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + sub)
            if i > 1 and j > 1 and a[i - 1] == b[j - 2] and a[i - 2] == b[j - 1] and a[i - 1] != b[j - 1]:
                v = min(v, prev2[j - 2] + 0.8)
            cur[j] = v
        if min(cur) > mx:
            return mx + 1
        prev2, prev = prev, cur
    return prev[m]

words, sc = [], []
for line in open(D, encoding='utf8'):
    w, s = line.rstrip('\n').split('\t')
    words.append(w)
    sc.append(max(1, min(120, int(s))) / 10)
norms = [norm(w) for w in words]
first_by_norm = {}
for i, n in enumerate(norms):
    first_by_norm.setdefault(n, i)
exact = {}
for i, w in enumerate(words):
    exact.setdefault(w, i)
bylen = {}
for i, n in enumerate(norms):
    bylen.setdefault(min(24, len(n)), []).append(i)

def find_norm(n):
    return first_by_norm.get(n, -1)

def correct(nm, mx, k):
    best = {}
    for ln in range(max(1, len(nm) - int(mx)), min(24, len(nm) + int(mx)) + 1):
        for i in bylen.get(ln, []):
            d = dist(nm, norms[i], mx)
            if d > mx:
                continue
            s = sc[i] - 2.2 * d
            if norms[i] not in best or s > best[norms[i]][0]:
                best[norms[i]] = (s, d, words[i])
    return sorted(best.values(), key=lambda x: -x[0])[:k]

def stem_ok(stem):
    if len(stem) < 2:
        return False
    i = find_norm(stem)
    return i >= 0 and sc[i] >= (4.5 if len(stem) == 2 else 3.0)

def clitic_known(nm):
    for p in [""] + PREFIXES:
        if not nm.startswith(p):
            continue
        rest = nm[len(p):]
        for s in [""] + SUFFIXES:
            if not p and not s:
                continue
            if not rest.endswith(s) or len(rest) - len(s) < 2:
                continue
            stem = rest[:len(rest) - len(s)]
            if stem_ok(stem):
                return True
            if s and stem.endswith("ت") and stem_ok(stem[:-1] + "ه"):
                return True
    return False

def autocorrect(t):
    nm = norm(t)
    if len(nm) < 2:
        return None
    if ''.join(c for c in t if not is_mark(c)) in exact:
        return None
    v = find_norm(nm)
    if v >= 0:
        return words[v] if sc[v] >= MIN_TARGET_ZIPF and words[v] != t else None
    if clitic_known(nm) or len(nm) < 3:
        return None
    best = None
    for s, d, w in correct(nm, MAX_COST, 4):
        if d <= MAX_COST and s + 2.2 * d >= MIN_TARGET_ZIPF:
            if best is None:
                best = (s, d, w)
            elif best[0] - s < 0.4:
                return None
    return best[2] if best else None

def load_cases(path):
    keep, fix, sec = [], [], None
    for line in open(path, encoding='utf8'):
        l = line.strip()
        if not l or l.startswith('#'):
            continue
        if l in ('[keep]', '[fix]'):
            sec = l
            continue
        if sec == '[keep]':
            keep += l.split()
        else:
            fix.append(l.split('\t'))
    return list(dict.fromkeys(keep)), fix

if __name__ == '__main__':
    if len(sys.argv) > 2 and sys.argv[1] == '--cases':
        keep, fix = load_cases(sys.argv[2])
        bad_keep = [(w, autocorrect(w)) for w in keep if autocorrect(w)]
        bad_fix = [(t, autocorrect(t), e) for t, e in fix if autocorrect(t) != e]
        for w, r in bad_keep:
            print('WRONGLY CORRECTED:', w, '->', r)
        for t, r, e in bad_fix:
            print('NOT FIXED:', t, '->', r, '(expected', e + ')')
        print(f'Python sim — keep: {len(keep) - len(bad_keep)}/{len(keep)} untouched; '
              f'fix: {len(fix) - len(bad_fix)}/{len(fix)} corrected')
        sys.exit(1 if bad_keep or bad_fix else 0)
    for t in sys.argv[1:]:
        r = autocorrect(t)
        print(t, '->', r if r else '(kept)')
