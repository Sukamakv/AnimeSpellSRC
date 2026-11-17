#!/usr/bin/env python3
"""
Removes whole SpriteFx call statements of given kinds from Java files, paren-balanced, refusing anything unsafe
(a statement that is the body of a brace-less if/else/for/while, or does not end in ';').
Usage: strip_calls.py [--apply] --kinds glowOn,orbOn --packages dante,vergil [--all-packages]
"""
import argparse, os, re, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SRC = os.path.join(ROOT, "src", "main", "java", "com", "example", "jjkspells")
ap = argparse.ArgumentParser()
ap.add_argument("--apply", action="store_true")
ap.add_argument("--kinds", required=True)
ap.add_argument("--packages", default="")
ap.add_argument("--all-packages", action="store_true")
a = ap.parse_args()
kinds = a.kinds.split(","); pk = set(x for x in a.packages.split(",") if x)
pat = re.compile(r"(?:com\.example\.jjkspells\.vfx\.sprite\.)?SpriteFx\.(" + "|".join(kinds) + r")\s*\(")
removed = skipped = 0
for dp, _, fns in os.walk(SRC):
    rel = os.path.relpath(dp, SRC).split(os.sep)[0]
    if rel == "vfx" and "sprite" in dp: continue
    if not a.all_packages and rel not in pk: continue
    for fn in fns:
        if not fn.endswith(".java"): continue
        path = os.path.join(dp, fn)
        s = open(path, encoding="utf-8").read()
        out, i, changed = [], 0, False
        while True:
            m = pat.search(s, i)
            if not m: out.append(s[i:]); break
            # statement start = beginning of the line if only whitespace precedes the call
            ls = s.rfind("\n", 0, m.start()) + 1
            if s[ls:m.start()].strip(): skipped += 1; out.append(s[i:m.end()]); i = m.end(); print("SKIP (not at line start)", fn); continue
            depth, j, in_str = 1, m.end(), False
            while j < len(s) and depth:
                c = s[j]
                if in_str:
                    if c == "\\": j += 1
                    elif c == '"': in_str = False
                elif c == '"': in_str = True
                elif c == "(": depth += 1
                elif c == ")": depth -= 1
                j += 1
            k = j
            while k < len(s) and s[k] in " \t": k += 1
            if k >= len(s) or s[k] != ";": skipped += 1; out.append(s[i:m.end()]); i = m.end(); print("SKIP (no ;)", fn); continue
            k += 1
            eol = s.find("\n", k); eol = len(s) if eol < 0 else eol + 1
            tail = s[k:eol].strip()
            if tail and not tail.startswith("//"): skipped += 1; out.append(s[i:m.end()]); i = m.end(); print("SKIP (code after)", fn); continue
            # previous significant line must end a statement/block, else this could be a brace-less body
            prev = s[:ls].rstrip()
            pl = prev[prev.rfind("\n") + 1:].strip()
            pl = re.sub(r"\s*//.*$", "", pl) if not pl.startswith("//") else pl
            if not (pl.endswith("{") or pl.endswith(";") or pl.endswith("}") or pl.startswith("//") or pl.endswith("*/") or pl == ""):
                skipped += 1; out.append(s[i:m.end()]); i = m.end(); print("SKIP (unsafe predecessor):", fn, "|", pl[-60:]); continue
            out.append(s[i:ls]); i = eol; removed += 1; changed = True
        if changed and a.apply: open(path, "w", encoding="utf-8").write("".join(out))
print(("APPLIED" if a.apply else "DRY RUN"), "removed", removed, "skipped", skipped)
