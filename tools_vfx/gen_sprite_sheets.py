#!/usr/bin/env python3
"""
Procedural sprite-sheet VFX generator for jjk_spells.

Every effect is computed once as a 'heat' field (0..1) per frame, then pushed through colour palettes, so all
spells share one consistent look. Output:

  src/main/resources/assets/jjk_spells/textures/sprite_vfx/<kind>_<palette>.png   (8 x 4 frames of 128 px)
  src/main/resources/assets/jjk_spells/vfx_sprites/<kind>_<palette>.json          (effect definition, see SpriteEffectDef.java)

Usage:  python3 tools_vfx/gen_sprite_sheets.py [--preview] [--kinds a,b] [--palettes x,y]
Needs numpy + Pillow. Loops are seamless (noise advection is wrapped to the lattice period).
"""
import argparse
import json
import math
import os

import numpy as np
from PIL import Image

FRAME = 128
COLS, ROWS = 8, 4
FRAMES = COLS * ROWS

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jjk_spells")
TEX_DIR = os.path.join(ASSETS, "textures", "sprite_vfx")
DEF_DIR = os.path.join(ASSETS, "vfx_sprites")

# --------------------------------------------------------------------------------------------------
# value noise (vectorised, optionally periodic in x / y so animations can loop)
# --------------------------------------------------------------------------------------------------

def _hash(ix, iy, iz, seed):
    with np.errstate(over="ignore"):
        h = (ix.astype(np.int64) * 374761393 + iy.astype(np.int64) * 668265263 + iz.astype(np.int64) * 2147483629
             + seed * 1013904223) & 0xFFFFFFFF
        h = h.astype(np.uint32)
        h = (h ^ (h >> np.uint32(13))) * np.uint32(1274126177)
        h = h ^ (h >> np.uint32(16))
    return (h & np.uint32(0xFFFFFF)).astype(np.float32) / float(0xFFFFFF)


def _fade(t):
    return t * t * t * (t * (t * 6 - 15) + 10)


def vnoise(x, y, z=0.0, seed=0, px=0, py=0):
    """3D value noise in 0..1. px / py (ints) wrap the lattice so the field tiles in x / y."""
    x = np.asarray(x, np.float32)
    y = np.asarray(y, np.float32)
    z = np.broadcast_to(np.asarray(z, np.float32), x.shape)
    xi, yi, zi = np.floor(x), np.floor(y), np.floor(z)
    xf, yf, zf = _fade(x - xi), _fade(y - yi), _fade(z - zi)
    xi, yi, zi = xi.astype(np.int64), yi.astype(np.int64), zi.astype(np.int64)

    def corner(dx, dy, dz):
        cx, cy = xi + dx, yi + dy
        if px:
            cx = cx % px
        if py:
            cy = cy % py
        return _hash(cx, cy, zi + dz, seed)

    c000, c100 = corner(0, 0, 0), corner(1, 0, 0)
    c010, c110 = corner(0, 1, 0), corner(1, 1, 0)
    c001, c101 = corner(0, 0, 1), corner(1, 0, 1)
    c011, c111 = corner(0, 1, 1), corner(1, 1, 1)
    x00 = c000 + (c100 - c000) * xf
    x10 = c010 + (c110 - c010) * xf
    x01 = c001 + (c101 - c001) * xf
    x11 = c011 + (c111 - c011) * xf
    y0 = x00 + (x10 - x00) * yf
    y1 = x01 + (x11 - x01) * yf
    return y0 + (y1 - y0) * zf


def fbm(u, v, base=3, octaves=4, z=0.0, seed=0, loop_y=False, adv=0.0, loop_x=False):
    """Fractal noise. u, v in 0..1. adv = how many full lattice periods the field scrolls along +v (needs loop_y)."""
    total = np.zeros_like(u, dtype=np.float32)
    amp, norm = 1.0, 0.0
    for k in range(octaves):
        f = base * (2 ** k)
        yy = v * f + adv * f
        total += amp * vnoise(u * f, yy, z, seed + k * 17, px=f if loop_x else 0, py=f if loop_y else 0)
        norm += amp
        amp *= 0.5
    return total / norm


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a + 1e-9), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def ease_out(t):
    return 1 - (1 - t) ** 3


def grid():
    lin = (np.arange(FRAME, dtype=np.float32) + 0.5) / FRAME
    u, v = np.meshgrid(lin, lin)  # u = x, v = y (down)
    return u, v


U, V = grid()
CX, CY = (U - 0.5) * 2.0, (V - 0.5) * 2.0       # -1..1, y down
R = np.sqrt(CX ** 2 + CY ** 2)
TH = np.arctan2(CY, CX)

# --------------------------------------------------------------------------------------------------
# effect kinds: f(t, i) -> heat field 0..1   (t in 0..1 over the animation, i = frame index)
# --------------------------------------------------------------------------------------------------

def k_fire_burst(t, i):
    radius = 0.18 + 0.5 * ease_out(min(t * 1.25, 1.0))
    turb = fbm(U + t * 0.4, V - t * 0.6, base=3, octaves=5, z=t * 2.5, seed=3)
    body = (radius - R * (0.78 + 0.5 * (turb - 0.5))) / 0.42
    heat = np.clip(body, 0, 1) ** 0.9
    life = 1.0 - smoothstep(0.38, 1.0, t)
    core = np.clip(1.0 - R / (0.55 * radius + 0.02), 0, 1) ** 1.4 * (1.0 - smoothstep(0.0, 0.55, t))
    out = (heat * (0.55 + 0.6 * turb) * life) * 1.1 + core * 0.9
    # embers
    emb = vnoise(U * 40, V * 40, t * 6, seed=9)
    out += smoothstep(0.92, 0.99, emb) * (R < radius * 1.25) * life * 0.45
    return np.clip(out * smoothstep(0.98, 0.8, R), 0, 1.2)


def k_flame_loop(t, i):
    y = V
    width = 0.36 * y ** 0.7 + 0.02
    x = np.abs(U - 0.5)
    shape = np.clip(1.0 - x / width, 0, 1) ** 1.2
    n = fbm(U + 0.0 * t, y, base=3, octaves=5, z=0.0, seed=5, loop_y=True, adv=t)
    n2 = fbm(U * 1.0, y, base=3, octaves=4, z=0.0, seed=11, loop_y=True, adv=t)
    sway = (n2 - 0.5) * 0.4 * (1.0 - y)
    x2 = np.abs(U - 0.5 + sway)
    shape = np.clip(1.0 - x2 / width, 0, 1) ** 1.1
    heat = shape * (0.45 + 0.9 * n)
    heat *= smoothstep(0.0, 0.22, y) * smoothstep(1.0, 0.9, y)
    heat = np.clip(heat * 1.25, 0, 1)
    base_glow = np.exp(-(((U - 0.5) / 0.28) ** 2 + ((y - 0.9) / 0.18) ** 2)) * 0.5
    return np.clip(heat + base_glow, 0, 1.1)


def k_energy_orb(t, i):
    a = t * 2 * math.pi
    core = np.exp(-(R / 0.13) ** 2) * 0.9
    halo = np.exp(-(R / 0.32) ** 2) * 0.12
    n = fbm(CX * 0.5 + 0.5 + 0.35 * math.cos(a), CY * 0.5 + 0.5 + 0.35 * math.sin(a), base=4, octaves=4,
            z=0.0, seed=21)
    rim = np.exp(-((R - 0.40 - 0.05 * (n - 0.5)) / 0.07) ** 2) * (0.55 + 0.9 * n)
    # swirling filaments
    fil = fbm(np.cos(TH + a) * 0.5 + 0.5, R * 1.5, base=3, octaves=3, z=0.0, seed=33)
    fil = np.clip((fil - 0.45) * 3.0, 0, 1) * np.exp(-((R - 0.3) / 0.22) ** 2) * 0.7
    return np.clip(core + halo + rim + fil, 0, 1.2) * (R < 0.98)


def k_slash_arc(t, i):
    # big crescent: bright head sweeps across the top, thick near the head, thin tail that catches up and fades
    ang = np.arctan2(np.cos(TH + math.pi / 2) * 0 + (-CX) * 0 + CX, -CY)   # 0 = straight up, + = clockwise
    head = -1.25 + 2.5 * ease_out(min(t / 0.42, 1.0))
    trail = 1.5
    tail = head - trail if t < 0.42 else (-1.25 + 2.5) - trail + (t - 0.42) / 0.58 * (trail + 0.1)
    s_ = np.clip((ang - tail) / (max(head, tail + 0.05) - tail), 0, 1)
    inside = ((ang >= tail) & (ang <= head + 0.02)).astype(np.float32)
    ring_r = 0.78
    thick = 0.03 + 0.2 * s_ ** 1.2
    d = np.abs(R - ring_r) / thick
    prof = np.clip(1.0 - d, 0, 1) ** 1.5
    streak = 0.7 + 0.6 * fbm(ang * 0.4 + 0.5, R * 2.2, base=4, octaves=3, z=t * 3, seed=44)
    tip = np.exp(-((ang - head) / 0.12) ** 2) * np.exp(-((R - ring_r) / 0.08) ** 2) * 0.9
    fade = 1.0 - smoothstep(0.62, 1.0, t)
    body = prof * (s_ ** 1.3) * inside * streak
    return np.clip((body * 1.4 + tip) * fade, 0, 1.2) * (R < 1.0)


def k_shockwave_ring(t, i):
    rr = 0.12 + 0.82 * ease_out(t)
    th = 0.16 * (1.0 - t) ** 0.8 + 0.025
    n = fbm(np.cos(TH) * 0.5 + 0.5, np.sin(TH) * 0.5 + 0.5, base=5, octaves=3, z=t * 2, seed=55)
    d = np.abs(R - rr * (0.97 + 0.06 * n)) / th
    ring = np.clip(1.0 - d, 0, 1) ** 1.4
    inner = 0.0 * R
    fade = (1.0 - t) ** 0.7
    return np.clip((ring * (0.7 + 0.6 * n) + inner) * fade * 1.15, 0, 1.2) * (R < 1.0)


def k_smoke_puff(t, i):
    radius = 0.28 + 0.55 * ease_out(t)
    n = fbm(U + t * 0.15, V - t * 0.3, base=3, octaves=5, z=t * 1.2, seed=66)
    body = np.clip((radius - R * (0.8 + 0.55 * (n - 0.5))) / 0.4, 0, 1)
    fade = smoothstep(0.0, 0.12, t) * (1.0 - smoothstep(0.45, 1.0, t))
    shade = 0.55 + 0.6 * fbm(U, V, base=6, octaves=3, z=t, seed=67)
    return np.clip(body * fade * 0.95, 0, 1), shade


def _bolt(seed, jag):
    rng = np.random.default_rng(seed)
    path = np.zeros(FRAME, np.float32)
    # jagged centre line built from random walk + noise
    pos = 0.0
    for r in range(FRAME):
        pos += (rng.random() - 0.5) * jag
        pos *= 0.96
        path[r] = pos
    return path


def k_lightning(t, i):
    out = np.zeros((FRAME, FRAME), np.float32)
    flick = (1.0 - smoothstep(0.35, 1.0, t)) * (0.75 + 0.25 * ((i * 7919) % 5) / 4.0)
    for b in range(3):
        main = (b == 0)
        path = _bolt(1000 + i * 13 + b * 101, 0.09 if main else 0.07)
        path = np.convolve(np.pad(path, 4, mode="edge"), np.ones(5) / 5, mode="valid")[:FRAME]
        centre = 0.5 + (path - path.mean()) * (0.9 if main else 0.7)
        if not main:
            centre = 0.5 + (centre - 0.5) * 0.7 + (-0.12 if b == 1 else 0.14) * (V[:, 0]) * 0
        dx = np.abs(U - centre[:, None])
        w = 0.018 if main else 0.01
        core = np.exp(-(dx / w) ** 2)
        glow = np.exp(-(dx / (w * 6.5)) ** 2) * 0.35
        # branches start part-way down
        start = 0 if main else int(FRAME * (0.18 + 0.25 * b))
        mask = (np.arange(FRAME)[:, None] >= start) & (np.arange(FRAME)[:, None] < (FRAME - 3))
        vfade = smoothstep(0.0, 0.05, V) * smoothstep(1.0, 0.93, V)
        out += (core * (1.0 if main else 0.55) + glow * (1.0 if main else 0.5)) * mask * vfade
    return np.clip(out * flick * 1.2, 0, 1.2)


def k_impact_star(t, i):
    flash = np.exp(-(R / (0.18 + 0.2 * t)) ** 2) * (1.0 - smoothstep(0.0, 0.5, t)) * 1.4
    spikes = 8
    spike = 0.25 + 0.75 * np.abs(np.cos(TH * spikes / 2.0)) ** 6
    rad = (0.2 + 0.78 * ease_out(min(t * 1.3, 1.0))) * spike
    body = np.clip(1.0 - R / (rad + 1e-3), 0, 1) ** 1.5 * (1.0 - smoothstep(0.25, 0.85, t))
    ring = np.exp(-((R - (0.15 + 0.8 * ease_out(t))) / 0.05) ** 2) * (1.0 - t) * 0.45
    rng_ = vnoise(U * 30, V * 30, t * 5, seed=77)
    sparks = (rng_ > 0.9) * (R > 0.2) * (R < (0.25 + 0.7 * ease_out(t))) * (1.0 - t) * 0.8
    return np.clip(flash + body + ring + sparks, 0, 1.2) * (R < 1.0)


def _seg(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy + 1e-9
    tt = np.clip(((px - ax) * dx + (py - ay) * dy) / l2, 0, 1)
    return np.hypot(px - (ax + tt * dx), py - (ay + tt * dy))


def k_magic_circle(t, i):
    # 6-fold symmetric; rotating by 2*pi/6 over the loop is seamless
    rot = t * (2 * math.pi / 6)
    c, s = math.cos(rot), math.sin(rot)
    x, y = CX * c + CY * s, -CX * s + CY * c
    r = np.hypot(x, y)
    th = np.arctan2(y, x)
    line = lambda d, w: np.exp(-(d / (w * 0.7)) ** 2)
    out = line(r - 0.94, 0.012) + line(r - 0.86, 0.008) * 0.8
    # tick marks
    ticks = 24
    tm = np.abs(((th / (2 * math.pi) * ticks) % 1.0) - 0.5)
    out += np.exp(-((r - 0.9) / 0.03) ** 2) * (tm > 0.44) * 0.8
    # counter-rotating inner ring with rune dots
    rot2 = -t * (2 * math.pi / 6) * 2
    c2, s2 = math.cos(rot2), math.sin(rot2)
    x2, y2 = CX * c2 + CY * s2, -CX * s2 + CY * c2
    r2, th2 = np.hypot(x2, y2), np.arctan2(y2, x2)
    out += line(r2 - 0.62, 0.01) * 0.9
    dots = np.abs(((th2 / (2 * math.pi) * 12) % 1.0) - 0.5)
    out += np.exp(-((r2 - 0.72) / 0.025) ** 2) * (dots > 0.4) * 0.9
    # hexagram
    for k in range(6):
        a0 = k * math.pi / 3
        a1 = a0 + 2 * math.pi / 3
        out += line(_seg(x, y, 0.8 * math.cos(a0), 0.8 * math.sin(a0), 0.8 * math.cos(a1), 0.8 * math.sin(a1)), 0.008) * 0.9
    return np.clip(out, 0, 1.0) * (R < 1.0)


def k_vortex(t, i):
    arms = 4
    ang = TH + t * 2 * math.pi / arms - 2.6 * R
    arm = (np.cos(ang * arms) * 0.5 + 0.5) ** 2.2
    n = fbm(np.cos(TH + t * 2 * math.pi / arms) * 0.5 + 0.5, R * 2.0, base=3, octaves=3, z=0.0, seed=88)
    radial = smoothstep(0.98, 0.45, R) * smoothstep(0.0, 0.12, R)
    core = np.exp(-(R / 0.17) ** 2) * 0.9
    return np.clip(arm * (0.6 + 0.8 * n) * radial * 1.05 + core, 0, 1.2)


def k_beam_segment(t, i):
    # tiles along u (x): scroll by exactly one lattice period per loop
    dv = np.abs(V - 0.5)
    core = np.exp(-(dv / 0.06) ** 2) * 1.1
    glow = np.exp(-(dv / 0.16) ** 2) * 0.18
    n = fbm(U, V, base=4, octaves=4, z=0.0, seed=99, loop_x=True, loop_y=False)
    # scroll in x via wrapped lattice: shift u by t * (lattice period) in noise space
    f = 4
    shifted = 0.0
    for k in range(4):
        ff = f * (2 ** k)
        shifted = shifted + (0.5 ** k) * vnoise((U + t) * ff, V * ff, 0.0, 99 + k * 17, px=ff)
    shifted = shifted / (1 + 0.5 + 0.25 + 0.125)
    streak = 0.65 + 0.75 * shifted
    return np.clip((core * streak + glow * (0.7 + 0.5 * shifted)), 0, 1.2)


def k_sparkle_burst(t, i):
    out = np.zeros((FRAME, FRAME), np.float32)
    rng = np.random.default_rng(404)
    n = 34
    for _ in range(n):
        ang = rng.random() * 2 * math.pi
        speed = 0.35 + 0.6 * rng.random()
        size = 0.02 + 0.03 * rng.random()
        delay = rng.random() * 0.2
        tt = max(0.0, t - delay) / (1.0 - delay)
        dist = speed * ease_out(tt)
        px, py = math.cos(ang) * dist, math.sin(ang) * dist + 0.25 * tt * tt
        d = np.hypot(CX - px, CY - py)
        life = (1.0 - tt) ** 1.3 * (tt > 0 or t == 0)
        star = np.exp(-(d / size) ** 2) + 0.45 * np.exp(-(np.minimum(np.abs(CX - px), np.abs(CY - py)) / (size * 0.45)) ** 2) * np.exp(-(d / (size * 7)) ** 2)
        out += star * life
    return np.clip(out, 0, 1.2)


def k_glow_pulse(t, i):
    p = 0.85 + 0.15 * math.sin(t * 2 * math.pi)
    return np.clip((np.exp(-(R / (0.22 * p)) ** 2) * 0.8 + np.exp(-(R / (0.5 * p)) ** 2) * 0.1), 0, 1.0) * (R < 1.0)



# --------------------------------------------------------------------------------------------------
# sharp, designed shapes (slashes, shards, cracks, petals, tendrils): hard edges, little halo
# --------------------------------------------------------------------------------------------------

def _wrap(a):
    return (a + math.pi) % (2 * math.pi) - math.pi


def _streak(x0, y0, ang, length, bow, width, head, tail, px=None, py=None):
    """Tapered curved blade. Local coords: u along the blade (-1..1), v across. Visible span [tail, head] in u."""
    px = CX if px is None else px
    py = CY if py is None else py
    c, s_ = math.cos(ang), math.sin(ang)
    dx, dy = px - x0, py - y0
    u = (dx * c + dy * s_) / length
    v = (-dx * s_ + dy * c)
    v = v - bow * (1.0 - u * u) * length * 0.5
    span = np.clip((u + 1.0) / 2.0, 0, 1)
    taper = np.maximum(np.sin(np.pi * np.clip(span, 0, 1)), 0.0) ** 0.7
    w = width * (0.15 + taper)
    d = np.abs(v) / (w + 1e-4)
    core = np.clip(1.0 - d, 0, 1) ** 1.2
    edge = np.clip(1.0 - d / 2.6, 0, 1) ** 2.0
    vis = (u >= tail) & (u <= head)
    rel = np.clip((u - tail) / (head - tail + 1e-4), 0, 1)
    heat = (core * 1.05 + edge * 0.5) * vis * (0.35 + 0.65 * rel ** 1.2)
    return heat


def k_slash_streak(t, i):
    head = -1.0 + 2.2 * ease_out(min(t / 0.32, 1.0))
    tail = -1.0 + 2.2 * smoothstep(0.18, 0.85, t)
    heat = _streak(0.0, 0.3, -0.42, 1.0, -0.34, 0.085, head, tail)
    fade = 1.0 - smoothstep(0.55, 1.0, t)
    rng = np.random.default_rng(31)
    out = heat * fade
    for _ in range(18):
        a = rng.random() * 0.9
        px = -0.9 + 1.8 * a
        py = 0.3 - 0.4 * px - 0.34 * 0.5 * (1 - px * px) + (rng.random() - 0.5) * 0.35
        vx, vy = (rng.random() - 0.5) * 0.5, (rng.random() - 0.2) * 0.5
        tt = max(0.0, t - 0.1 - a * 0.2)
        x, y = px + vx * tt, py + vy * tt
        d = np.hypot(CX - x, CY - y)
        out += np.exp(-(d / 0.018) ** 2) * (1.0 - smoothstep(0.1, 0.5, tt)) * (tt > 0) * 0.9
    return np.clip(out, 0, 1.15) * (R < 1.0)


def k_claw_rake(t, i):
    out = np.zeros((FRAME, FRAME), np.float32)
    for k in range(3):
        off = (k - 1) * 0.30
        delay = k * 0.08
        tt = max(0.0, t - delay) / (1.0 - delay)
        head = -1.0 + 2.2 * ease_out(min(tt / 0.3, 1.0))
        tail = -1.0 + 2.2 * smoothstep(0.2, 0.85, tt)
        out += _streak(-off * 0.55 + 0.05, off * 0.85 + 0.12, 0.92, 1.1, 0.1, 0.07, head, tail) * (1.0 - smoothstep(0.55, 1.0, tt))
    return np.clip(out, 0, 1.15) * (R < 1.0)


def _tri_mask(px, py, a, b, c):
    (ax, ay), (bx, by), (cx_, cy_) = a, b, c
    den = (by - cy_) * (ax - cx_) + (cx_ - bx) * (ay - cy_)
    w1 = ((by - cy_) * (px - cx_) + (cx_ - bx) * (py - cy_)) / den
    w2 = ((cy_ - ay) * (px - cx_) + (ax - cx_) * (py - cy_)) / den
    w3 = 1.0 - w1 - w2
    return w1, w2, w3


def k_shard_burst(t, i):
    rng = np.random.default_rng(77)
    out = np.zeros((FRAME, FRAME), np.float32)
    for _ in range(16):
        ang = rng.random() * 2 * math.pi
        speed = 0.3 + 0.62 * rng.random()
        size = 0.07 + 0.08 * rng.random()
        spin = (rng.random() - 0.5) * 6.0
        delay = rng.random() * 0.12
        tt = max(0.0, t - delay) / (1.0 - delay)
        dist = 0.08 + speed * ease_out(tt)
        cx_, cy_ = math.cos(ang) * dist, math.sin(ang) * dist + 0.18 * tt * tt
        rot = ang + spin * tt
        dr = (math.cos(rot), math.sin(rot))
        pr = (-dr[1], dr[0])
        k = size * (1.0 - 0.55 * smoothstep(0.5, 1.0, tt))
        a = (cx_ + dr[0] * k * 1.8, cy_ + dr[1] * k * 1.8)
        b = (cx_ + pr[0] * k * 0.6 - dr[0] * k * 0.5, cy_ + pr[1] * k * 0.6 - dr[1] * k * 0.5)
        c = (cx_ - pr[0] * k * 0.6 - dr[0] * k * 0.5, cy_ - pr[1] * k * 0.6 - dr[1] * k * 0.5)
        w1, w2, w3 = _tri_mask(CX, CY, a, b, c)
        m = np.minimum(np.minimum(w1, w2), w3)
        inside = (m > 0)
        life = (1.0 - smoothstep(0.55, 1.0, tt)) * (tt > 0 or t == 0)
        heat = inside * (0.5 + 0.3 * np.clip(w1, 0, 1)) + inside * np.clip(1.0 - m / 0.14, 0, 1) * 0.4
        out += heat * life
    flash = np.exp(-(R / 0.16) ** 2) * (1.0 - smoothstep(0.0, 0.22, t)) * 1.1
    return np.clip(out + flash, 0, 1.15) * (R < 1.0)


def _jag(r, k, amp, freq=7.0):
    return (vnoise(r * freq, np.full_like(r, k * 3.17), 0.0, seed=123 + k) - 0.5) * amp


def k_ground_crack(t, i):
    reach = 0.12 + 0.86 * ease_out(min(t / 0.45, 1.0))
    out = np.zeros_like(R)
    n = 9
    for k in range(n):
        th0 = k * 2 * math.pi / n + (k % 3) * 0.11
        for br, (r0, off, w) in enumerate([(0.0, 0.0, 0.017), (0.38, 0.35, 0.011), (0.55, -0.4, 0.009)]):
            if br > 0 and k % 2 == br % 2:
                continue
            th = th0 + _jag(R, k * 3 + br, 0.7) + off * np.clip((R - r0) / 0.5, 0, 1) * (br > 0)
            d = R * np.abs(_wrap(TH - th))
            vis = (R >= r0) & (R <= reach * (1.0 - 0.28 * br)) & (R > 0.04)
            taper = 1.0 - 0.7 * np.clip(R / 1.0, 0, 1)
            out += (np.exp(-(d / (w * taper)) ** 2) + 0.3 * np.exp(-(d / (w * 3.2)) ** 2)) * vis
    life = 1.0 - smoothstep(0.55, 1.0, t)
    core = np.exp(-(R / 0.1) ** 2) * (1.0 - smoothstep(0.0, 0.4, t)) * 0.9
    return np.clip(out * life * 0.95 + core, 0, 1.1) * (R < 1.0)


def k_petal_swirl(t, i):
    rng = np.random.default_rng(55)
    out = np.zeros((FRAME, FRAME), np.float32)
    for _ in range(18):
        a0 = rng.random() * 2 * math.pi
        rr = 0.25 + 0.7 * rng.random()
        spin = 0.8 + rng.random() * 0.7
        flip = rng.random() * 6.0
        size = 0.075 + 0.045 * rng.random()
        tt = t
        ang = a0 + spin * 2 * math.pi * tt * 0.5
        dist = (0.12 + rr * ease_out(min(tt * 1.2, 1.0))) * (1.0 + 0.1 * math.sin(tt * 5 + a0))
        px, py = math.cos(ang) * dist, math.sin(ang) * dist * 0.85
        rot = ang + math.pi / 2 + flip + tt * 5.0
        c, s_ = math.cos(rot), math.sin(rot)
        lx = (CX - px) * c + (CY - py) * s_
        ly = -(CX - px) * s_ + (CY - py) * c
        tw = 0.3 + 0.7 * abs(math.cos(flip + tt * 7.0))
        e = (lx / (size * 1.5)) ** 2 + (ly / (size * 0.62 * tw + 1e-4)) ** 2
        mask = e < 1.0
        shade = 0.55 + 0.25 * np.clip(-lx / (size * 1.5), 0, 1)
        vein = np.exp(-(ly / 0.006) ** 2) * (np.abs(lx) < size * 1.3)
        life = smoothstep(0.0, 0.12, tt) * (1.0 - smoothstep(0.6, 1.0, tt))
        out += (mask * shade + mask * vein * 0.35 + mask * np.clip(1.0 - e, 0, 1) * 0.2) * life
    return np.clip(out, 0, 1.1) * (R < 1.0)


def k_fracture_web(t, i):
    grow = ease_out(min(t / 0.22, 1.0))
    n = 11
    out = np.zeros_like(R)
    for k in range(n):
        th = k * 2 * math.pi / n + _jag(R, k, 0.45, 9.0) + (k % 2) * 0.05
        d = R * np.abs(_wrap(TH - th))
        reach = grow * (0.55 + 0.43 * ((k * 37) % 10) / 10.0)
        w = 0.012 * (1.0 - 0.6 * R)
        out += (np.exp(-(d / w) ** 2) + 0.25 * np.exp(-(d / (w * 3.5)) ** 2)) * (R < reach) * (R > 0.03)
    for j, rj in enumerate((0.22, 0.4, 0.6, 0.8)):
        seg = (vnoise(TH * 2.0 + 20, np.full_like(R, j), 0.0, seed=200 + j) > 0.42)
        d = np.abs(R - rj - _jag(TH * 0.5 + 5, j, 0.06, 6.0))
        out += np.exp(-(d / 0.0085) ** 2) * seg * (R < grow * 0.98) * 0.85
    life = 1.0 - smoothstep(0.62, 1.0, t)
    flash = np.exp(-(R / 0.22) ** 2) * (1.0 - smoothstep(0.0, 0.25, t)) * 1.1
    return np.clip(out * life * 0.95 + flash, 0, 1.1) * (R < 1.0)


def k_tendril_burst(t, i):
    out = np.zeros_like(R)
    n = 9
    reach = 0.15 + 0.82 * ease_out(min(t / 0.5, 1.0))
    for k in range(n):
        th = k * 2 * math.pi / n + 0.2 * math.sin(k * 3.1) + (1.3 + 0.4 * math.sin(k)) * R * (-1 if k % 2 else 1)
        th = th + _jag(R, k, 0.35, 5.0) * R
        d = R * np.abs(_wrap(TH - th))
        w = np.maximum(0.03 * (1.0 - R / (reach + 0.02)) + 0.005, 0.004)
        body = np.exp(-np.minimum(d / w, 6.0) ** 3) * (R < reach) * (R > 0.05)
        out += body * (0.65 + 0.4 * (1.0 - R))
    life = smoothstep(0.0, 0.06, t) * (1.0 - smoothstep(0.55, 1.0, t))
    core = np.exp(-(R / 0.09) ** 2) * life
    return np.clip(out * life + core * 0.8, 0, 1.1) * (R < 1.0)


# kind -> (function, default def values)
KINDS = {
    "fire_burst":     (k_fire_burst,     dict(playback="once", lifetime=26, size=3.0, orientation="billboard", scale_end=1.15, fade_out=6)),
    "flame_loop":     (k_flame_loop,     dict(playback="loop", fps=24, lifetime=40, size=2.2, orientation="billboard_y", scale_end=1.0, fade_in=3, fade_out=6)),
    "energy_orb":     (k_energy_orb,     dict(playback="loop", fps=24, lifetime=40, size=2.0, orientation="billboard", scale_end=1.0, fade_in=3, fade_out=4)),
    "slash_arc":      (k_slash_arc,      dict(playback="once", lifetime=10, size=4.0, orientation="normal", scale_end=1.1, fade_out=0)),
    "shockwave_ring": (k_shockwave_ring, dict(playback="once", lifetime=18, size=6.0, orientation="flat", scale_end=1.0, fade_out=0, alpha=0.7)),
    "smoke_puff":     (k_smoke_puff,     dict(playback="once", lifetime=50, size=3.0, orientation="billboard", scale_end=1.3, fade_out=0)),
    "lightning":      (k_lightning,      dict(playback="once", lifetime=12, size=6.0, orientation="axis", axis_uv="v", scale_end=1.0, fade_out=0)),
    "impact_star":    (k_impact_star,    dict(playback="once", lifetime=14, size=3.2, orientation="billboard", scale_end=1.0, fade_out=0)),
    "magic_circle":   (k_magic_circle,   dict(playback="loop", fps=20, lifetime=60, size=7.0, orientation="flat", scale_end=1.0, fade_in=8, fade_out=10, alpha=0.55)),
    "vortex":         (k_vortex,         dict(playback="loop", fps=24, lifetime=50, size=4.5, orientation="flat", scale_end=1.0, fade_in=6, fade_out=8)),
    "beam_segment":   (k_beam_segment,   dict(playback="loop", fps=30, lifetime=24, size=1.2, orientation="axis", scale_end=1.0, fade_in=2, fade_out=4, tile_length=4.0)),
    "sparkle_burst":  (k_sparkle_burst,  dict(playback="once", lifetime=24, size=4.0, orientation="billboard", scale_end=1.0, fade_out=0)),
    "glow_pulse":     (k_glow_pulse,     dict(playback="loop", fps=20, lifetime=40, size=3.0, orientation="billboard", scale_end=1.0, fade_in=4, fade_out=6, alpha=0.5)),
    "slash_streak":   (k_slash_streak,   dict(playback="once", lifetime=9, size=4.5, orientation="normal", scale_end=1.05, fade_out=0)),
    "claw_rake":      (k_claw_rake,      dict(playback="once", lifetime=12, size=4.5, orientation="normal", scale_end=1.05, fade_out=0)),
    "shard_burst":    (k_shard_burst,    dict(playback="once", lifetime=26, size=4.0, orientation="billboard", scale_end=1.1, fade_out=0)),
    "ground_crack":   (k_ground_crack,   dict(playback="once", lifetime=36, size=7.0, orientation="flat", scale_end=1.0, fade_out=0)),
    "petal_swirl":    (k_petal_swirl,    dict(playback="once", lifetime=44, size=4.0, orientation="billboard", scale_end=1.0, fade_out=0)),
    "fracture_web":   (k_fracture_web,   dict(playback="once", lifetime=26, size=5.0, orientation="billboard", scale_end=1.0, fade_out=0)),
    "tendril_burst":  (k_tendril_burst,  dict(playback="once", lifetime=30, size=4.0, orientation="billboard", scale_end=1.1, fade_out=0)),
}

# --------------------------------------------------------------------------------------------------
# palettes: stops of (heat, r, g, b); alpha follows heat
# --------------------------------------------------------------------------------------------------

def _ramp(edge, mid, hot):
    return [(0.0, *[c * 0.4 for c in edge]), (0.22, *edge), (0.5, *mid), (0.85, *hot), (1.0, *[min(1.0, h * 1.04) for h in hot])]


PALETTES = {
    "fire":    _ramp((0.55, 0.08, 0.0), (1.0, 0.45, 0.05), (1.0, 0.86, 0.35)),
    "crimson": _ramp((0.35, 0.0, 0.05), (0.92, 0.05, 0.14), (1.0, 0.5, 0.42)),
    "azure":   _ramp((0.02, 0.12, 0.5), (0.1, 0.5, 1.0), (0.55, 0.88, 1.0)),
    "violet":  _ramp((0.22, 0.0, 0.42), (0.62, 0.16, 0.95), (0.92, 0.62, 1.0)),
    "gold":    _ramp((0.5, 0.25, 0.0), (1.0, 0.72, 0.1), (1.0, 0.93, 0.55)),
    "toxic":   _ramp((0.0, 0.25, 0.05), (0.2, 0.85, 0.15), (0.78, 1.0, 0.5)),
    "white":   _ramp((0.5, 0.55, 0.65), (0.85, 0.9, 1.0), (1.0, 1.0, 1.0)),
    "rot":     _ramp((0.3, 0.06, 0.0), (0.86, 0.3, 0.04), (1.0, 0.74, 0.42)),
    "wind":    _ramp((0.04, 0.3, 0.3), (0.4, 0.85, 0.8), (0.92, 1.0, 1.0)),
}
# smoke is its own thing (alpha blended, dark)
SMOKE_PALETTES = {
    "ash":  ((0.16, 0.16, 0.18), (0.55, 0.55, 0.58)),
    "void": ((0.03, 0.0, 0.07), (0.32, 0.1, 0.55)),
    "ember": ((0.12, 0.05, 0.03), (0.8, 0.3, 0.08)),
}


def colourise(heat, stops):
    h = np.clip(heat, 0, 1)
    xs = [s[0] for s in stops]
    rgb = np.stack([np.interp(h, xs, [s[k] for s in stops]) for k in (1, 2, 3)], -1)
    alpha = smoothstep(0.10, 0.50, heat) * 0.92
    return rgb.astype(np.float32), alpha.astype(np.float32)


def to_sheet(frames_rgba):
    sheet = np.zeros((ROWS * FRAME, COLS * FRAME, 4), np.uint8)
    for idx, fr in enumerate(frames_rgba):
        r, c = divmod(idx, COLS)
        sheet[r * FRAME:(r + 1) * FRAME, c * FRAME:(c + 1) * FRAME] = fr
    return sheet


def render_kind(kind, palette_name, fn):
    heats = []
    for i in range(FRAMES):
        t = i / FRAMES if KINDS[kind][1]["playback"] == "loop" else i / (FRAMES - 1)
        out = fn(t, i)
        heats.append(out)
    frames = []
    for out in heats:
        if kind == "smoke_puff":
            dens, shade = out
            dark, light = SMOKE_PALETTES[palette_name]
            dark, light = np.array(dark, np.float32), np.array(light, np.float32)
            rgb = dark + (light - dark) * np.clip(shade, 0, 1)[..., None]
            a = np.clip(dens, 0, 1)
        else:
            rgb, a = colourise(out, PALETTES[palette_name])
        # premultiplied-style soften at edges, then 8 bit
        rgba = np.concatenate([np.clip(rgb, 0, 1), a[..., None]], -1)
        frames.append((np.nan_to_num(rgba) * 255).astype(np.uint8))
    return to_sheet(frames)


def def_json(kind, pal, extra):
    blend = "alpha" if kind == "smoke_puff" else "additive"
    d = {
        "texture": f"jjk_spells:textures/sprite_vfx/{kind}_{pal}.png",
        "cols": COLS, "rows": ROWS, "frames": FRAMES,
        "blend": blend,
        "fullbright": blend == "additive",
    }
    d.update(extra)
    return d


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true", help="write contact sheets into ./vfx_preview")
    ap.add_argument("--kinds", default="")
    ap.add_argument("--palettes", default="")
    args = ap.parse_args()
    kinds = [k for k in args.kinds.split(",") if k] or list(KINDS)
    pal_filter = [p for p in args.palettes.split(",") if p]
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(DEF_DIR, exist_ok=True)
    prev = os.path.join(ROOT, "vfx_preview")
    if args.preview:
        os.makedirs(prev, exist_ok=True)
    count = 0
    for kind in kinds:
        fn, extra = KINDS[kind]
        pals = list(SMOKE_PALETTES) if kind == "smoke_puff" else list(PALETTES)
        if pal_filter:
            pals = [p for p in pals if p in pal_filter]
        # compute heat once per kind, reuse across palettes
        cache = {}
        orig = fn

        def cached(t, i, _orig=orig, _cache=cache):
            if i not in _cache:
                _cache[i] = _orig(t, i)
            return _cache[i]

        for pal in pals:
            sheet = render_kind(kind, pal, cached)
            name = f"{kind}_{pal}"
            img = Image.fromarray(sheet, "RGBA")
            img.save(os.path.join(TEX_DIR, name + ".png"), optimize=True)
            with open(os.path.join(DEF_DIR, name + ".json"), "w") as f:
                json.dump(def_json(kind, pal, extra), f, indent=2)
            if args.preview:
                # preview on a dark background so additive glow reads
                bg = Image.new("RGBA", img.size, (10, 10, 16, 255))
                bg.alpha_composite(img)
                bg.convert("RGB").save(os.path.join(prev, name + ".png"))
            count += 1
            print("wrote", name)
    print(f"{count} sprite sheets written")


if __name__ == "__main__":
    main()
