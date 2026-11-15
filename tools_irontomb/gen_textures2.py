"""Textures for the Irontomb cosmic form. Everything is generated procedurally; tileable where it is a surface."""
import os
import numpy as np
from PIL import Image, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), '..', 'build', 'src', 'main', 'resources', 'assets', 'jjk_spells', 'textures', 'irontomb')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)


def save(name, rgba):
    Image.fromarray(np.clip(rgba, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(OUT, name + '.png'))


def fbm(n, beta=2.2, seed=0):
    """Periodic (tileable) noise via random-phase FFT, 0..1."""
    r = np.random.default_rng(seed)
    f = np.fft.fftfreq(n)[:, None] ** 2 + np.fft.fftfreq(n)[None, :] ** 2
    f[0, 0] = 1
    amp = 1 / f ** (beta / 2)
    amp[0, 0] = 0
    ph = np.exp(2j * np.pi * r.random((n, n)))
    a = np.real(np.fft.ifft2(amp * ph))
    a = (a - a.min()) / (a.max() - a.min())
    return a


def shell(n=512):
    base = fbm(n, 2.6, 1)
    fine = fbm(n, 1.4, 2)
    mid = fbm(n, 2.0, 3)
    # plate seams: a warped grid of dark crevices
    y, x = np.mgrid[0:n, 0:n] / n
    wx = x + (mid - .5) * .08
    wy = y + (fine - .5) * .05
    seam = np.zeros((n, n))
    for k, per in enumerate((4, 6)):
        gx = np.abs(((wx * per) % 1) - .5) * 2
        gy = np.abs(((wy * per * (1.3 if k else 1)) % 1) - .5) * 2
        seam = np.maximum(seam, np.maximum(gx, gy) ** 28)
    wear = np.clip((fine - .62) * 4, 0, 1) * (1 - seam)
    r = 13 + 26 * base + 14 * wear
    g = 10 + 20 * base + 11 * wear
    b = 16 + 28 * base + 16 * wear
    crim = np.clip((mid - .55) * 3.0, 0, 1) * (0.4 + 0.6 * fbm(n, 2.4, 4))
    r = r + 62 * crim
    g = g + 6 * crim
    b = b + 14 * crim
    # faint violet sheen in the high spots
    sheen = np.clip((base - .6) * 3, 0, 1) * 0.5
    b = b + 26 * sheen
    r = r + 10 * sheen
    dark = 1 - 0.86 * seam
    k = 1.55
    img = np.stack([r * dark * k, g * dark * k, b * dark * k, np.full((n, n), 255.)], -1)
    save('shell', img)


def violet(n=512):
    a = fbm(n, 2.3, 11)
    b = fbm(n, 1.6, 12)
    ridge = 1 - np.abs(a - .5) * 2
    vein = np.clip(ridge ** 6 * 1.4 + (b - .5) * .4, 0, 1)
    core = np.clip(a * 1.2, 0, 1) ** 2
    inten = np.clip(.38 + .38 * core + .55 * vein, 0, 1)
    # deep violet -> bright purple -> white-purple
    r = 90 + 150 * vein + 30 * core
    g = 14 + 90 * vein + 10 * core
    bl = 190 + 65 * vein
    save('violet', np.stack([r, g, bl, inten * 255], -1))


def star4(n=256):
    y, x = (np.mgrid[0:n, 0:n] - n / 2 + .5) / (n / 2)
    ax, ay = np.abs(x), np.abs(y)
    rad = np.sqrt(x * x + y * y)
    cross = np.exp(-5.2 * np.sqrt(ax * ay + 1e-5)) * np.clip(1 - rad, 0, 1) ** 0.6      # astroid / four-point body
    spikeh = np.exp(-ay * 70) * np.clip(1 - ax, 0, 1) ** 1.6
    spikev = np.exp(-ax * 70) * np.clip(1 - ay, 0, 1) ** 1.6
    core = np.exp(-rad ** 2 * 60)
    glow = np.exp(-rad * 7) * .5
    inten = np.clip(cross * 1.1 + .8 * (spikeh + spikev) + core * 1.4 + glow, 0, 1)
    t = np.clip(core * 1.4 + cross * .3, 0, 1)          # white-yellow centre, orange-red falloff
    r = 255 * np.ones_like(t)
    g = 120 + 130 * t
    b = 70 + 150 * t * t
    save('star4', np.stack([r, g, b, inten * 255], -1))


def hflare(w=1024, h=64):
    y, x = (np.mgrid[0:h, 0:w] - np.array([[h / 2], [w / 2]]).reshape(2, 1, 1) + .5)
    ux, uy = x / (w / 2), y / (h / 2)
    inten = np.exp(-np.abs(uy) * 9) * np.clip(1 - np.abs(ux), 0, 1) ** 1.8
    save('hflare', np.stack([np.full_like(inten, 255), 70 + 60 * inten, 60 + 30 * inten, inten * 255], -1))


def ring_nodes(n=1024):
    y, x = (np.mgrid[0:n, 0:n] - n / 2 + .5) / (n / 2)
    rad = np.sqrt(x * x + y * y)
    ang = np.arctan2(y, x)
    node = np.zeros_like(rad)
    for k in range(4):
        d = np.abs(np.angle(np.exp(1j * (ang - k * np.pi / 2))))
        node = np.maximum(node, np.exp(-(d * 11) ** 2))
    R = .9
    width = .006 + .045 * node
    band = np.exp(-((rad - R) / width) ** 2)
    glow = np.exp(-np.abs(rad - R) * 14) * .25 * (1 + node)
    core = np.exp(-((rad - R) / (width * .3)) ** 2)
    inten = np.clip(band * .85 + glow + core * .5, 0, 1) * np.clip((0.985 - rad) * 30, 0, 1)
    t = np.clip(core + node * .25, 0, 1)
    save('ring_nodes', np.stack([np.full_like(t, 255), 105 + 130 * t, 70 + 140 * t, inten * 255], -1))


def crosschain(w=1024, h=128):
    """Row of small cross-shaped glyphs on a thin string (the ladders in the concept art). Alpha, dark with a faint warm rim."""
    im = np.zeros((h, w, 4))
    cell = 64
    yy, xx = np.mgrid[0:h, 0:w]
    for i in range(w // cell):
        cx = i * cell + cell // 2
        cy = h // 2
        bar_h = (np.abs(yy - cy) < 3) & (np.abs(xx - cx) < 22)
        bar_v = (np.abs(xx - cx) < 3) & (np.abs(yy - cy) < 36)
        ends = ((np.abs(yy - (cy - 36)) < 3) | (np.abs(yy - (cy + 36)) < 3)) & (np.abs(xx - cx) < 12)
        m = bar_h | bar_v | ends
        im[m] = (18, 12, 16, 255)
    string = (np.abs(yy - h // 2) < 1)
    im[string] = (120, 70, 70, 210)
    save('crosschain', im)


def smoke(n=512):
    a = fbm(n, 2.8, 21)
    b = fbm(n, 2.0, 22)
    y, x = (np.mgrid[0:n, 0:n] - n / 2 + .5) / (n / 2)
    fall = np.clip(1 - np.sqrt(x * x + y * y), 0, 1) ** 1.2
    d = np.clip((a * .7 + b * .5 - .35) * 2.2, 0, 1) * fall
    r = 255
    g = 120 + 90 * b
    bl = 90 + 60 * b
    save('smoke', np.stack([np.full((n, n), r), g, bl, d * 255], -1))


def shard(n=128):
    """Small tumbling debris chip: angular dark-red fleck, used as an alpha billboard."""
    im = np.zeros((n, n, 4))
    y, x = (np.mgrid[0:n, 0:n] - n / 2 + .5) / (n / 2)
    m = (np.abs(x) * 1.0 + np.abs(y) * 1.7 < .8) & (x + y * .3 > -.6)
    im[m] = (150, 20, 28, 255)
    im[m & (x < -.2)] = (230, 70, 60, 255)
    save('shard', im)


for f in (shell, violet, star4, hflare, ring_nodes, crosschain, smoke, shard):
    f()
print(sorted(os.listdir(OUT)))
