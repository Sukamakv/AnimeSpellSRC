#!/usr/bin/env python3
"""
Checks every use of the sprite VFX API in the Java sources without needing Minecraft or Forge on the classpath:
  * SpriteFx.method(...)      -> the method exists and the argument count matches one of its overloads
  * SpriteVfxApi.play(...)    -> literal effect names exist as vfx_sprites/NAME.json
  * Builder chains            -> only known builder methods are called, and the chain ends in .send()
Exit code 1 on any problem. Usage: python3 tools_vfx/check_sprite_calls.py
"""
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SRC = os.path.join(ROOT, "src", "main", "java")
DEFS = os.path.join(ROOT, "src", "main", "resources", "assets", "jjk_spells", "vfx_sprites")
SPRITE_PKG = os.path.join(SRC, "com", "example", "jjkspells", "vfx", "sprite")


def split_args(s):
    """Top level comma split that respects (), <>, [] and string literals."""
    out, depth, cur, i, in_str = [], 0, "", 0, False
    while i < len(s):
        ch = s[i]
        if in_str:
            cur += ch
            if ch == "\\":
                cur += s[i + 1]
                i += 1
            elif ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True
            cur += ch
        elif ch in "([{":
            depth += 1
            cur += ch
        elif ch in ")]}":
            depth -= 1
            cur += ch
        elif ch == "," and depth == 0:
            out.append(cur.strip())
            cur = ""
        else:
            cur += ch
        i += 1
    if cur.strip():
        out.append(cur.strip())
    return out


def find_call(src, start):
    """src[start] is just after '('. Returns (inner, end_index_after_close)."""
    depth, i, in_str = 1, start, False
    while i < len(src) and depth:
        ch = src[i]
        if in_str:
            if ch == "\\":
                i += 1
            elif ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True
        elif ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        i += 1
    return src[start:i - 1], i


def load_signatures():
    text = open(os.path.join(SPRITE_PKG, "SpriteFx.java"), encoding="utf-8").read()
    sigs = {}
    for m in re.finditer(r"public static (?:void|List<String>|String) (\w+)\(([^)]*)\)", text):
        name, params = m.group(1), m.group(2).strip()
        n = len(split_args(params)) if params else 0
        sigs.setdefault(name, set()).add(n)
    text = open(os.path.join(SPRITE_PKG, "SpriteVfxApi.java"), encoding="utf-8").read()
    builder = set(re.findall(r"public Builder (\w+)\(", text))
    builder |= {"send"}
    return sigs, builder


def main():
    sigs, builder_methods = load_signatures()
    names = {f[:-5] for f in os.listdir(DEFS)} if os.path.isdir(DEFS) else set()
    problems, uses = [], 0
    for dp, _, fns in os.walk(SRC):
        if dp.startswith(SPRITE_PKG):
            continue
        for fn in fns:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(dp, fn)
            src = open(path, encoding="utf-8", errors="ignore").read()
            if "SpriteFx" not in src and "SpriteVfxApi" not in src:
                continue
            rel = os.path.relpath(path, SRC)

            def line(idx):
                return src.count("\n", 0, idx) + 1

            for m in re.finditer(r"\bSpriteFx\.(\w+)\s*\(", src):
                name = m.group(1)
                inner, _ = find_call(src, m.end())
                if name in ("Palette", "Smoke"):
                    continue
                uses += 1
                if name not in sigs:
                    problems.append(f"{rel}:{line(m.start())}: SpriteFx.{name}() does not exist")
                    continue
                n = len(split_args(inner)) if inner.strip() else 0
                if n not in sigs[name]:
                    problems.append(f"{rel}:{line(m.start())}: SpriteFx.{name} takes {sorted(sigs[name])} args, got {n}")
            for m in re.finditer(r"\bSpriteVfxApi\.play\s*\(", src):
                uses += 1
                inner, end = find_call(src, m.end())
                args = split_args(inner)
                if len(args) < 3:
                    problems.append(f"{rel}:{line(m.start())}: SpriteVfxApi.play needs (level, name, pos) or (level, name, x, y, z)")
                lit = re.fullmatch(r'"([a-z0-9_]+)"', args[1]) if len(args) > 1 else None
                if lit and lit.group(1) not in names:
                    problems.append(f"{rel}:{line(m.start())}: unknown effect '{lit.group(1)}' (no vfx_sprites/{lit.group(1)}.json)")
                # walk the builder chain
                i, ended = end, False
                while True:
                    cm = re.match(r"\s*\.\s*(\w+)\s*\(", src[i:])
                    if not cm:
                        break
                    meth = cm.group(1)
                    if meth not in builder_methods:
                        problems.append(f"{rel}:{line(i)}: unknown builder method .{meth}()")
                    _, i2 = find_call(src, i + cm.end())
                    i = i2
                    if meth == "send":
                        ended = True
                        break
                if not ended and ".send()" not in src[end:end + 400]:
                    # allowed if the Builder is stored in a variable and sent later
                    if not re.search(r"Builder\s+\w+\s*=\s*[^;]*$", src[max(0, m.start() - 80):m.start()]):
                        problems.append(f"{rel}:{line(m.start())}: builder chain never calls .send()")
    for p in problems:
        print("PROBLEM", p)
    print(f"checked {uses} sprite API uses, {len(problems)} problem(s)")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
