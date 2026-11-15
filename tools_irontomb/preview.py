"""Software preview of the cosmic form from the same part table the game uses (rest pose, approximate shading and glow)."""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
import irontomb_model as M

W, H = 1280, 720
MAT = {0: (40, 31, 44), 1: (66, 44, 52), 2: (200, 100, 255), 3: (255, 120, 80)}
L = np.array([0.35, 0.75, 0.55]); L /= np.linalg.norm(L)


def tex(n):
    return np.asarray(Image.open(os.path.join(M.TEX, n + '.png')).convert('RGBA')).astype(np.float32) / 255


def frustum_faces(p):
    g, m, seg, cx, cy, cz, yaw, pitch, roll, ln, w0, d0, w1, d1 = p
    R = M.rot(yaw, pitch, roll)
    h = ln / 2
    bot = [(-w0, -h, -d0), (w0, -h, -d0), (w0, -h, d0), (-w0, -h, d0)]
    top = [(-w1, h, -d1), (w1, h, -d1), (w1, h, d1), (-w1, h, d1)]
    V = [R @ np.array(v) + np.array([cx, cy, cz]) for v in bot + top]
    quads = [(0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7), (4, 5, 6, 7), (3, 2, 1, 0)]
    out = []
    for q in quads:
        pts = [V[i] for i in q]
        n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
        if np.linalg.norm(n) < 1e-6:
            n = np.cross(pts[2] - pts[1], pts[0] - pts[1])
        nn = np.linalg.norm(n)
        out.append((pts, n / nn if nn > 1e-9 else np.array([0, 1, 0])))
    return out


def camera(eye, at, fov):
    f = np.array(at, float) - np.array(eye, float); f /= np.linalg.norm(f)
    r = np.cross(f, [0, 1, 0]); r /= np.linalg.norm(r)
    u = np.cross(r, f)
    t = math.tan(math.radians(fov) / 2)

    def proj(p):
        d = np.array(p) - eye
        z = d @ f
        return (W / 2 + (d @ r) / (z * t) * (H / 2), H / 2 - (d @ u) / (z * t) * (H / 2), z)
    return proj


def bg():
    y = np.linspace(0, 1, H)[:, None, None]
    x = np.linspace(-1, 1, W)[None, :, None]
    base = np.array([10, 4, 10]) * (1 - y) + np.array([85, 22, 34]) * y ** 1.6
    base = base + np.array([40, 8, 14]) * np.exp(-((x * 1.1) ** 2 + (y - .85) ** 2 * 3))
    return base.astype(np.float32)


def sprite(canvas, tex_, cx, cy, hw, hh, tint, k=1.0):
    x0, x1, y0, y1 = int(cx - hw), int(cx + hw), int(cy - hh), int(cy + hh)
    if x1 <= 0 or y1 <= 0 or x0 >= W or y0 >= H or hw < 1 or hh < 1:
        return
    im = Image.fromarray((tex_ * 255).astype(np.uint8), 'RGBA').resize((max(1, x1 - x0), max(1, y1 - y0)), Image.BILINEAR)
    a = np.asarray(im).astype(np.float32) / 255
    sx0, sy0 = max(0, -x0), max(0, -y0)
    dx0, dy0 = max(0, x0), max(0, y0)
    w = min(a.shape[1] - sx0, W - dx0); h = min(a.shape[0] - sy0, H - dy0)
    if w <= 0 or h <= 0:
        return
    s = a[sy0:sy0 + h, sx0:sx0 + w]
    canvas[dy0:dy0 + h, dx0:dx0 + w] += s[..., :3] * 255 * np.array(tint) * s[..., 3:4] * k


def render(eye, at, fov, name, glow=1.0, rimk=0.9):
    proj = camera(np.array(eye, float), at, fov)
    canvas = bg()
    S = {n: tex(n) for n in ('star4', 'hflare', 'ring_nodes', 'smoke', 'glow', 'crosschain')}
    # red atmosphere behind
    c = proj((0, 34, -30))
    if c[2] > 0:
        sc = (H / 2) / (math.tan(math.radians(fov) / 2) * c[2])
        sprite(canvas, S['glow'], c[0], c[1], 110 * sc, 110 * sc, (1, .22, .16), .35)
        r = proj((0, 34, -8))
        sprite(canvas, S['ring_nodes'], r[0], r[1], 44 * sc, 44 * sc, (1, 1, 1), .8)
    # constellation
    for i in range(0, len(M.LINKS), 2):
        a, b = M.STARS[M.LINKS[i]], M.STARS[M.LINKS[i + 1]]
        pa, pb = proj(a[:3]), proj(b[:3])
        if pa[2] > 0 and pb[2] > 0:
            tmp = Image.new('RGB', (W, H)); ImageDraw.Draw(tmp).line([pa[:2], pb[:2]], fill=(120, 24, 24), width=1)
            canvas += np.asarray(tmp.filter(ImageFilter.GaussianBlur(1))).astype(np.float32) * .45
    for (x, y, z, sz, sd) in M.STARS:
        p = proj((x, y, z))
        if p[2] <= 0:
            continue
        sc = (H / 2) / (math.tan(math.radians(fov) / 2) * p[2])
        sprite(canvas, S['star4'], p[0], p[1], sz * 2.1 * sc, sz * 2.1 * sc, (1, 1, 1), 1.0)
        sprite(canvas, S['hflare'], p[0], p[1], sz * 17 * sc, sz * 1.1 * sc, (1, .8, .8), .55)
    # smoke at the base
    for i in range(16):
        ang = i * 2.4; rad = 12 + (i * 7) % 52
        p = proj((math.cos(ang) * rad, 2 + (i % 4) * 3, math.sin(ang) * rad * .8))
        if p[2] > 0:
            sc = (H / 2) / (math.tan(math.radians(fov) / 2) * p[2])
            sprite(canvas, S['smoke'], p[0], p[1], (22 + i % 5 * 6) * sc, (16 + i % 4 * 5) * sc, (1, .38, .32), .5)
    canvas = np.clip(canvas, 0, 255)
    # solid geometry, painter's order
    polys = []
    for prt in M.parts:
        m = int(prt[1])
        for pts, n in frustum_faces(prt):
            P = [proj(p) for p in pts]
            if min(q[2] for q in P) < 1.0:
                continue
            depth = sum(q[2] for q in P) / 4
            if m >= 2:
                col = np.array(MAT[m]) * (0.75 + .25 * glow)
            else:
                sh = 0.28 + 0.72 * max(0.0, float(n @ L))
                rim = (0.7 * max(0.0, -n[1]) + 0.25 * max(0.0, n[2])) * rimk
                col = np.array(MAT[m]) * sh * 1.15 + np.array([255, 56, 50]) * rim * 0.42
            polys.append((depth, [(q[0], q[1]) for q in P], tuple(int(max(0, min(255, v))) for v in col), m))
    polys.sort(key=lambda t: -t[0])
    img = Image.fromarray(canvas.astype(np.uint8))
    d = ImageDraw.Draw(img, 'RGBA')
    glowl = Image.new('RGB', (W, H))
    gd = ImageDraw.Draw(glowl)
    for depth, pts, col, m in polys:
        d.polygon(pts, fill=col + (255,))
        if m >= 2:
            gd.polygon(pts, fill=col)
    bloom = glowl.filter(ImageFilter.GaussianBlur(9))
    out = np.clip(np.asarray(img).astype(np.float32) + np.asarray(bloom).astype(np.float32) * 0.9, 0, 255)
    # face star flare
    p = proj((0, 33.6, 5.4))
    if p[2] > 0:
        sc = (H / 2) / (math.tan(math.radians(fov) / 2) * p[2])
        sprite(out, tex('star4'), p[0], p[1], 7 * sc, 7 * sc, (.9, .5, 1), 1.0)
    Image.fromarray(np.clip(out, 0, 255).astype(np.uint8)).save(os.path.join(HERE_OUT, name))


HERE_OUT = os.path.join(M.HERE, '..')
if __name__ == '__main__':
    render((0, 40, 150), (0, 40, 0), 90, 'preview_full.png')
    render((0, 34, 78), (0, 34, 0), 56, 'preview_closeup.png')
    render((0, 3, 60), (0, 34, 0), 84, 'preview_player.png')
    render((95, 52, 120), (20, 34, 0), 72, 'preview_extension.png')
    print('ok')
