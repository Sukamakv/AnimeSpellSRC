#!/usr/bin/env python3
"""Procedural texture + PNG-animation generator for the Irontomb boss module.

Every flipbook is a square grid of frames (row-major, frame 0 = top-left).
Textures meant to be tinted at runtime are white/grey with alpha; the renderer
multiplies them by a vertex colour and draws them additively.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)


# ---------------------------------------------------------------- helpers
def save(name, arr):
    arr = np.clip(arr, 0, 1)
    Image.fromarray((arr * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name), optimize=True)
    print("wrote", name, arr.shape)


def save_rgb(name, arr):
    arr = np.clip(arr, 0, 1)
    Image.fromarray((arr * 255).astype(np.uint8), "RGB").save(os.path.join(OUT, name), optimize=True)
    print("wrote", name, arr.shape)


def fbm(h, w, beta=2.4, seed=0):
    """Periodic fractal noise in 0..1 (tileable in both axes)."""
    rng = np.random.default_rng(seed)
    f = np.fft.fft2(rng.standard_normal((h, w)))
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.fftfreq(w)[None, :]
    r = np.sqrt(fx ** 2 + fy ** 2)
    r[0, 0] = 1.0
    f = f / (r ** (beta / 2.0))
    f[0, 0] = 0
    n = np.fft.ifft2(f).real
    n = (n - n.min()) / (n.max() - n.min() + 1e-9)
    return n.astype(np.float32)


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0 + 1e-9), 0, 1)
    return t * t * (3 - 2 * t)


def grid(h, w):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return (x + 0.5) / w, (y + 0.5) / h


def blur(arr2d, radius):
    img = Image.fromarray((np.clip(arr2d, 0, 1) * 255).astype(np.uint8), "L").filter(ImageFilter.GaussianBlur(radius))
    return np.asarray(img).astype(np.float32) / 255.0


def white_alpha(a):
    out = np.ones(a.shape + (4,), np.float32)
    out[..., 3] = a
    return out


def tile_into(frames, cols):
    n = len(frames)
    rows = int(math.ceil(n / cols))
    fh, fw = frames[0].shape[:2]
    sheet = np.zeros((rows * fh, cols * fw, 4), np.float32)
    for i, fr in enumerate(frames):
        r, c = divmod(i, cols)
        sheet[r * fh:(r + 1) * fh, c * fw:(c + 1) * fw] = fr
    return sheet


# ---------------------------------------------------------------- white / glow / spark
save("white.png", np.ones((4, 4, 4), np.float32))

u, v = grid(128, 128)
d = np.hypot(u - 0.5, v - 0.5) * 2
glow = np.exp(-(d * 2.6) ** 2) * 0.85 + np.exp(-(d * 7.0) ** 2) * 0.6
glow *= smooth(1.0, 0.7, d)
save("glow.png", white_alpha(glow))

# 4-point flare / star
u, v = grid(256, 256)
x, y = (u - 0.5) * 2, (v - 0.5) * 2
d = np.hypot(x, y)
core = np.exp(-(d * 6.0) ** 2)
cross = (np.exp(-(np.abs(x) * 28) ** 2) * np.exp(-(np.abs(y) * 2.2) ** 2) +
         np.exp(-(np.abs(y) * 28) ** 2) * np.exp(-(np.abs(x) * 2.2) ** 2))
diag = 0.35 * (np.exp(-(np.abs(x - y) * 22) ** 2) + np.exp(-(np.abs(x + y) * 22) ** 2)) * np.exp(-(d * 2.0) ** 2)
halo = 0.35 * np.exp(-(d * 2.4) ** 2)
save("spark.png", white_alpha(np.clip(core + cross + diag + halo, 0, 1) * smooth(1.0, 0.85, d)))

# horizontal light streak (title flare / lens streak)
u, v = grid(64, 512)
streak = np.exp(-(np.abs(v - 0.5) * 70) ** 2) * (np.exp(-(np.abs(u - 0.5) * 2.6) ** 2)) \
    + 0.35 * np.exp(-(np.abs(v - 0.5) * 8) ** 2) * np.exp(-(np.abs(u - 0.5) * 5) ** 2)
streak = np.zeros((64, 512), np.float32) + np.exp(-(np.abs(grid(64, 512)[1] - 0.5) * 38) ** 2) * np.exp(
    -(np.abs(grid(64, 512)[0] - 0.5) * 3.2) ** 2)
streak += 0.4 * np.exp(-(np.abs(grid(64, 512)[1] - 0.5) * 6) ** 2) * np.exp(-(np.abs(grid(64, 512)[0] - 0.5) * 6) ** 2)
save("streak.png", white_alpha(np.clip(streak, 0, 1)))

# vignette (alpha only, black)
u, v = grid(256, 256)
d = np.hypot(u - 0.5, v - 0.5) * 2 / 1.1
vig = smooth(0.35, 1.0, d) * 0.95
vg = np.zeros((256, 256, 4), np.float32)
vg[..., 3] = vig
save("vignette.png", vg)

# ---------------------------------------------------------------- body plating (tileable)
H = W = 256
n1 = fbm(H, W, 2.8, 1)
n2 = fbm(H, W, 1.6, 2)
n3 = fbm(H, W, 3.2, 3)
base = 0.10 + 0.10 * n1 + 0.035 * n2
body = np.stack([base * 0.95, base * 1.0, base * 1.12], -1)
yy, xx = np.mgrid[0:H, 0:W]
panel = 64
seam = ((xx % panel) < 2) | ((yy % panel) < 2)
seam2 = ((xx % panel) == 2) | ((yy % panel) == 2)
body[seam] *= 0.35
body[seam2] *= 1.55
# rivets
for py in range(panel // 2, H, panel):
    for px in range(panel // 2, W, panel):
        for dx, dy in ((-22, -22), (22, -22), (-22, 22), (22, 22)):
            r = np.hypot(xx - (px + dx), yy - (py + dy))
            body[..., :] += (np.exp(-(r / 2.4) ** 2) * 0.20)[..., None]
            body[..., :] *= (1 - np.exp(-(np.hypot(xx - (px + dx) - 1.5, yy - (py + dy) - 1.5) / 3.0) ** 2) * 0.35)[..., None]
# scratches / rust
sc = np.clip((n3 - 0.62) * 5, 0, 1)
body += sc[..., None] * np.array([0.12, 0.05, 0.02])
body *= (0.8 + 0.4 * n2)[..., None]
# faint ember glow in seams
ember = (seam & (n1 > 0.55)).astype(np.float32)
body += blur(ember, 1.6)[..., None] * np.array([0.55, 0.12, 0.04])
rgba = np.concatenate([np.clip(body, 0, 1), np.ones((H, W, 1), np.float32)], -1)
save("body_iron.png", rgba)

# glowing cracks (tileable emissive)
ridge = 1 - np.abs(fbm(256, 256, 2.2, 7) - 0.5) * 2
cracks = smooth(0.90, 0.975, ridge)
cracks2 = smooth(0.955, 0.995, 1 - np.abs(fbm(256, 256, 1.8, 11) - 0.5) * 2)
crk = np.clip(cracks + cracks2, 0, 1)
crk = np.clip(blur(crk, 0.8) * 1.5 + blur(crk, 3.5) * 0.7, 0, 1)
save("body_glow.png", white_alpha(crk))

# ---------------------------------------------------------------- halo ring (512)
S = 1024
img = Image.new("L", (S, S), 0)
dr = ImageDraw.Draw(img)
c = S / 2


def ring(rad0, rad1, val=255):
    dr.ellipse([c - rad1 * c, c - rad1 * c, c + rad1 * c, c + rad1 * c], outline=val, width=max(1, int((rad1 - rad0) * c)))


ring(0.945, 0.985)
ring(0.905, 0.915, 200)
ring(0.80, 0.815, 170)
ring(0.60, 0.608, 120)
for i in range(120):
    a = i / 120 * 2 * math.pi
    r0, r1 = (0.82, 0.90) if i % 5 else (0.78, 0.93)
    dr.line([c + math.cos(a) * r0 * c, c + math.sin(a) * r0 * c, c + math.cos(a) * r1 * c, c + math.sin(a) * r1 * c],
            fill=220 if i % 5 == 0 else 130, width=3 if i % 5 == 0 else 2)
rng = np.random.default_rng(5)
for i in range(36):  # rune-like glyph blocks
    a = (i + 0.5) / 36 * 2 * math.pi
    rr = 0.865
    cx, cy = c + math.cos(a) * rr * c, c + math.sin(a) * rr * c
    for k in range(3):
        ox, oy = rng.uniform(-9, 9, 2)
        w, h = rng.uniform(3, 11, 2)
        dr.rectangle([cx + ox, cy + oy, cx + ox + w, cy + oy + h], fill=int(rng.uniform(120, 230)))
halo = np.asarray(img).astype(np.float32) / 255.0
halo = np.clip(halo + blur(halo, 6) * 0.9 + blur(halo, 22) * 0.35, 0, 1)
halo = np.asarray(Image.fromarray((halo * 255).astype(np.uint8)).resize((512, 512), Image.LANCZOS)).astype(np.float32) / 255
save("halo_ring.png", white_alpha(halo))

# ---------------------------------------------------------------- glyph circle (512)
S = 1536
img = Image.new("L", (S, S), 0)
dr = ImageDraw.Draw(img)
c = S / 2


def circ(r, w, val=255):
    R = r * c
    dr.ellipse([c - R, c - R, c + R, c + R], outline=val, width=max(1, int(w)))


def poly(n, r, rot, w=5, val=255, skip=1):
    pts = [(c + math.cos(rot + i * 2 * math.pi / n) * r * c, c + math.sin(rot + i * 2 * math.pi / n) * r * c) for i in range(n)]
    for i in range(n):
        dr.line([pts[i], pts[(i + skip) % n]], fill=val, width=w)
    return pts


circ(0.985, 10)
circ(0.955, 4, 200)
circ(0.80, 7)
circ(0.775, 3, 180)
circ(0.52, 6)
circ(0.30, 5, 220)
circ(0.075, 6)
pts = poly(7, 0.775, -math.pi / 2, 6, 255, 3)          # heptagram
poly(7, 0.775, -math.pi / 2, 3, 150, 2)
poly(6, 0.52, -math.pi / 2, 5, 220, 1)
poly(3, 0.30, -math.pi / 2, 5, 220)
poly(3, 0.30, math.pi / 2, 5, 220)
for p in pts:  # node circles
    dr.ellipse([p[0] - 34, p[1] - 34, p[0] + 34, p[1] + 34], outline=255, width=6)
    dr.ellipse([p[0] - 12, p[1] - 12, p[0] + 12, p[1] + 12], fill=255)
for i in range(96):  # outer rune ticks
    a = i / 96 * 2 * math.pi
    r0, r1 = (0.86, 0.945) if i % 4 else (0.84, 0.95)
    dr.line([c + math.cos(a) * r0 * c, c + math.sin(a) * r0 * c, c + math.cos(a) * r1 * c, c + math.sin(a) * r1 * c],
            fill=230 if i % 4 == 0 else 110, width=4)
rng = np.random.default_rng(9)
for i in range(48):  # runic marks between rings 0.80 and 0.955
    a = (i + 0.5) / 48 * 2 * math.pi
    rr = 0.878
    cx, cy = c + math.cos(a) * rr * c, c + math.sin(a) * rr * c
    for k in range(4):
        ox, oy = rng.uniform(-22, 22, 2)
        w, h = rng.uniform(5, 26, 2)
        if rng.random() < 0.5:
            dr.rectangle([cx + ox, cy + oy, cx + ox + w, cy + oy + 4], fill=200)
        else:
            dr.rectangle([cx + ox, cy + oy, cx + ox + 4, cy + oy + h], fill=200)
for i in range(14):  # spokes
    a = i / 14 * 2 * math.pi
    dr.line([c + math.cos(a) * 0.08 * c, c + math.sin(a) * 0.08 * c, c + math.cos(a) * 0.30 * c, c + math.sin(a) * 0.30 * c], fill=150, width=3)
gl = np.asarray(img).astype(np.float32) / 255.0
gl = np.clip(gl + blur(gl, 7) * 0.9 + blur(gl, 26) * 0.35, 0, 1)
gl = np.asarray(Image.fromarray((gl * 255).astype(np.uint8)).resize((512, 512), Image.LANCZOS)).astype(np.float32) / 255
save("glyph.png", white_alpha(gl))

# ---------------------------------------------------------------- beam (tileable, wraps around cylinders)
H = W = 128
u, v = grid(H, W)
band = np.zeros((H, W), np.float32)
nz = fbm(H, W, 1.3, 21)
nz2 = fbm(H, W, 2.0, 22)
stripes = 0.55 + 0.45 * np.cos(u * 2 * math.pi * 3 + nz2 * 5)
band = np.clip(0.45 + 0.7 * (nz - 0.5) * 2 + 0.25 * stripes, 0, 1)
band = np.clip(band * (0.7 + 0.5 * np.sin(v * 2 * math.pi * 4 + u * 6) ** 2), 0, 1)
save("beam.png", white_alpha(np.clip(band * 1.1, 0, 1)))

# ---------------------------------------------------------------- planet (equirect, tileable in u)
H, W = 256, 512
n1 = fbm(H, W, 2.6, 31)
n2 = fbm(H, W, 1.7, 32)
ridge = 1 - np.abs(fbm(H, W, 2.2, 33) - 0.5) * 2
lava = smooth(0.955, 0.99, ridge) + 0.6 * smooth(0.975, 0.998, 1 - np.abs(fbm(H, W, 1.8, 34) - 0.5) * 2)
lava = np.clip(blur(lava, 0.7) * 1.5 + blur(lava, 3.5) * 0.45, 0, 1.3)
rock = 0.06 + 0.16 * n1 + 0.05 * n2
rgb = np.stack([rock * 1.15, rock * 0.95, rock * 0.9], -1)
rng = np.random.default_rng(35)
yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
for i in range(70):  # craters
    cx, cy, r = rng.uniform(0, W), rng.uniform(25, H - 25), rng.uniform(5, 22)
    dx = np.minimum(np.abs(xx - cx), W - np.abs(xx - cx))
    dd = np.hypot(dx, yy - cy) / r
    rim = np.exp(-((dd - 1.0) * 5) ** 2) * 0.25
    pit = smooth(1.0, 0.2, dd) * 0.18
    rgb += rim[..., None] - pit[..., None]
rgb = np.clip(rgb, 0, 1)
emis = np.stack([lava * 1.0, lava * 0.42, lava * 0.12], -1)
rgb = np.clip(rgb + emis, 0, 1)
save_rgb("planet.png", rgb)

# ---------------------------------------------------------------- stars (equirect nebula + stars)
H, W = 512, 1024
nb1 = fbm(H, W, 2.9, 41)
nb2 = fbm(H, W, 2.4, 42)
nb3 = fbm(H, W, 3.2, 43)
neb = np.stack([
    smooth(0.45, 0.95, nb1) * 0.55 + smooth(0.5, 1.0, nb3) * 0.25,
    smooth(0.55, 1.0, nb2) * 0.18 + smooth(0.6, 1.0, nb3) * 0.05,
    smooth(0.4, 0.95, nb2) * 0.60 + smooth(0.5, 1.0, nb1) * 0.2], -1)
neb *= 0.55
stars = np.zeros((H, W), np.float32)
rng = np.random.default_rng(44)
for i in range(2600):
    x, y = rng.integers(0, W), rng.integers(0, H)
    stars[y, x] = max(stars[y, x], rng.uniform(0.25, 1.0) ** 2)
stars = np.clip(stars + blur(stars, 0.9) * 2.2, 0, 1)
bright = np.zeros((H, W), np.float32)
for i in range(110):
    x, y = rng.integers(8, W - 8), rng.integers(8, H - 8)
    bright[y, x] = 1.0
bright = np.clip(blur(bright, 1.3) * 28 + blur(bright, 4) * 10, 0, 1)
sky = np.clip(neb + stars[..., None] * np.array([0.9, 0.9, 1.0]) + bright[..., None] * np.array([1.0, 0.95, 0.85]), 0, 1)
save_rgb("stars.png", sky)

# ---------------------------------------------------------------- flipbook: expanding ring (16 frames, 4x4 of 128)
FR = 128
frames = []
u, v = grid(FR, FR)
x, y = (u - 0.5) * 2, (v - 0.5) * 2
d = np.hypot(x, y)
ang = np.arctan2(y, x)
an = fbm(FR, FR, 1.6, 51)
for k in range(16):
    p = k / 15
    e = 1 - (1 - p) ** 2.2
    r = 0.12 + 0.86 * e
    w = 0.13 * (1 - p) + 0.018
    wob = 1 + 0.28 * (an - 0.5) * 2 * (1 - 0.5 * p)
    prof = np.exp(-(((d - r) / (w * wob)) ** 2))
    inner = np.exp(-(d / (r * 0.8 + 1e-3)) ** 6) * 0.30 * (1 - p) ** 1.5
    a = np.clip((prof * 1.15 + inner) * (1 - p) ** 0.8, 0, 1) * smooth(1.0, 0.9, d)
    frames.append(white_alpha(a))
save("ring.png", tile_into(frames, 4))

# ---------------------------------------------------------------- flipbook: slash crescent (16 frames)
frames = []
cx, cy, R = 0.5, 0.82, 0.62
for k in range(16):
    p = k / 15
    head = 1.18 * (1 - (1 - min(1, p / 0.55)) ** 2.4)
    tail = max(0.0, head - 0.62) if p < 0.55 else 0.55 * ((p - 0.55) / 0.45) + 0.0
    xk, yk = (u - cx), -(v - cy)
    dist = np.hypot(xk, yk)
    phi = np.clip(np.arctan2(yk, xk) / math.pi, -0.2, 1.2)      # 0..1 over upper half
    s = 1 - phi                                                  # left->right sweep
    along = smooth(tail, tail + 0.18, s) * smooth(head, head - 0.07, s)
    width = 0.012 + 0.075 * np.clip(np.sin(np.clip(s, 0, 1) * math.pi), 0, 1) ** 0.8
    prof = np.exp(-(((dist - R) / width) ** 2))
    prof2 = np.exp(-(((dist - R * 0.97) / (width * 0.45)) ** 2)) * 0.8
    a = np.clip((prof + prof2) * along, 0, 1)
    a = np.clip(a + blur(a, 3) * 0.5, 0, 1)
    fade = 1 - smooth(0.62, 1.0, p)
    frames.append(white_alpha(a * fade))
save("slash.png", tile_into(frames, 4))

# ---------------------------------------------------------------- flipbook: flame (16 frames, loopable-ish)
frames = []
big = fbm(FR * 2, FR, 1.9, 61)
big2 = fbm(FR * 2, FR, 2.6, 62)
for k in range(16):
    sh = int(k * (FR * 2) / 16)
    nA = np.roll(big, sh, axis=0)[:FR]
    nB = np.roll(big2, sh // 2, axis=0)[:FR]
    u, v = grid(FR, FR)
    x, y = (u - 0.5) * 2, 1 - v                               # y: 0 bottom..1 top
    width = (1 - y) ** 0.65 * 0.60 + 0.06
    turb = (nA - 0.5) * 0.7 * y
    body_ = np.clip(1 - np.abs(x + turb) / width, 0, 1)
    height = smooth(1.0, 0.55, y + (nB - 0.5) * 0.55)
    inten = np.clip(body_ ** 1.2 * height * (0.55 + 0.9 * nA), 0, 1.3)
    inten = np.clip(inten * smooth(0.0, 0.07, y), 0, 1)
    core = np.clip(inten * 1.5 - 0.35, 0, 1)
    frames.append(white_alpha(np.clip(inten + core * 0.5, 0, 1)))
save("flame.png", tile_into(frames, 4))

# ---------------------------------------------------------------- flipbook: screen cracks (16 frames, 4x4 of 256)
CR = 256
rng = np.random.default_rng(71)
segs = []  # (x0,y0,x1,y1,birth,width)


def crack(x, y, ang, birth, wid, depth):
    length = rng.uniform(10, 26)
    for _ in range(rng.integers(6, 14)):
        ang += rng.uniform(-0.55, 0.55)
        x1, y1 = x + math.cos(ang) * length, y + math.sin(ang) * length
        b1 = birth + length / 150.0
        segs.append((x, y, x1, y1, birth, max(0.7, wid)))
        if depth < 3 and rng.random() < 0.28:
            crack(x1, y1, ang + rng.choice([-1, 1]) * rng.uniform(0.5, 1.2), b1, wid * 0.62, depth + 1)
        x, y, birth, wid = x1, y1, b1, wid * 0.93
        if x < -20 or y < -20 or x > CR + 20 or y > CR + 20:
            break


for i in range(26):
    a0 = i / 26 * 2 * math.pi + rng.uniform(-0.1, 0.1)
    crack(CR / 2 + math.cos(a0) * 4, CR / 2 + math.sin(a0) * 4, a0, 0.0, 3.2, 0)
frames = []
for k in range(16):
    p = k / 15
    reveal = min(1.0, p / 0.7) * 1.05
    img = Image.new("L", (CR * 2, CR * 2), 0)
    dr = ImageDraw.Draw(img)
    for (x0, y0, x1, y1, b, w) in segs:
        if b <= reveal:
            dr.line([x0 * 2, y0 * 2, x1 * 2, y1 * 2], fill=255, width=int(max(2, w * 2)))
    arr = np.asarray(img.resize((CR, CR), Image.LANCZOS)).astype(np.float32) / 255
    arr = np.clip(arr + blur(arr, 2.2) * 1.1 + blur(arr, 8) * 0.6, 0, 1)
    flash = 0.0 if p < 0.7 else (p - 0.7) / 0.3
    uu, vv = grid(CR, CR)
    dd = np.hypot(uu - 0.5, vv - 0.5) * 2
    a = np.clip(arr + flash * (0.35 * np.exp(-(dd * 1.6) ** 2)), 0, 1)
    frames.append(white_alpha(a))
save("crack.png", tile_into(frames, 4))

# ---------------------------------------------------------------- item model json lives elsewhere; done
print("all textures generated")
