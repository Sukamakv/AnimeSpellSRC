"""
Irontomb cosmic form: single source of truth.
Defines the part list + constellation, writes IrontombModel.java (data table) and renders preview stills from the SAME data.

Part row: group, mat, seg, cx,cy,cz (centre of the frustum axis), yaw,pitch,roll (deg, applied Ry*Rx*Rz), len, w0,d0 (half extents at -len/2), w1,d1 (at +len/2)
Frustum axis is local +Y. Groups: 0 body, 1 head, 2/3 shoulder L/R, 4/5 upper extension L/R, 6/7 lower arm L/R, 8 fragments, 9 hip wings.
Materials: 0 dark shell, 1 charcoal/crimson plate, 2 violet emissive, 3 red-orange emissive.
Side s=+1 is +X (the boss's left when facing +Z, whatever: right on screen for a viewer in front).
"""
import math, os, random
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', 'build', 'src', 'main')
JAVA = os.path.join(SRC, 'java', 'com', 'example', 'jjkspells', 'irontomb', 'client', 'IrontombModel.java')
TEX = os.path.join(SRC, 'resources', 'assets', 'jjk_spells', 'textures', 'irontomb')

parts = []


def rot(yaw, pitch, roll):
    y, x, z = map(math.radians, (yaw, pitch, roll))
    Ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    Rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    Rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return Ry @ Rx @ Rz


def Pc(g, m, c, r, L, base, top=None, seg=0):
    top = top or base
    parts.append([g, m, seg, *c, *r, L, base[0], base[1], top[0], top[1]])


def Pb(g, m, b, r, L, base, top=None, seg=0):
    """Same but position is the BASE centre of the frustum; returns the tip position."""
    R = rot(*r)
    c = np.array(b) + R @ np.array([0, L / 2, 0])
    Pc(g, m, tuple(c), r, L, base, top, seg)
    return tuple(np.array(b) + R @ np.array([0, L, 0]))


# ------------------------------------------------------------------ lower torso, hips
Pb(0, 0, (0, -8, 0), (0, 0, 0), 26, (10.5, 6.6), (8.0, 5.2))
Pb(0, 1, (0, -4, 6.0), (0, 0, 0), 17, (7.5, 1.5), (4.2, 0.9))
Pc(0, 3, (0, 6.5, 7.0), (0, 0, 45), 4.2, (2.1, 0.45))                      # red core diamond (attacks fire from here)
Pc(0, 1, (0, 6.5, 6.4), (0, 0, 45), 7.0, (3.5, 0.5))
Pc(0, 0, (0, 13.3, 0), (0, 0, 0), 3.2, (9.2, 6.2), (8.2, 5.4))              # belt
for k in range(-4, 5):
    Pb(0, 1, (k * 2.6, 12.2, 6.4), (0, 0, k * 2), 4.2, (0.9, 0.5), (0.1, 0.1))
for s in (-1, 1):
    Pb(9, 0, (s * 5, 5, -1), (0, 0, -s * 36), 20, (4.3, 1.7), (0.3, 0.4))
    Pb(9, 1, (s * 4.5, 1, -2), (0, 0, -s * 72), 15, (3.2, 1.2), (0.3, 0.3))
    Pb(9, 0, (s * 5, -1, -1), (0, 0, -s * 126), 13, (3.0, 1.2), (0.3, 0.3))
    Pb(9, 1, (s * 6.5, 9, 1), (0, 0, -s * 52), 12, (2.4, 1.0), (0.2, 0.3))
for k in range(7):
    Pb(0, 0, (-10 + k * 3.3, -2, (k % 2) * 2.5 - 1), (0, 0, 180 + (k - 3) * 9), 11 + (k % 3) * 3, (1.8, 1.6), (0.2, 0.2))
for k in range(3):
    Pb(0, 2, (-3 + k * 3, -6, 0), (0, 0, (k - 1) * 16), 9 + k % 2 * 3, (1.9, 1.5), (0.2, 0.2))

# ------------------------------------------------------------------ chest: spine + ribs with violet in the gaps
Pb(0, 0, (0, 12, -2), (0, 0, 0), 21, (7.0, 4.2), (5.4, 3.4))
for i in range(9):
    y = 14.5 + i * 1.8
    w = 13.2 - 0.85 * i
    Pc(0, 2, (0, y - 0.95, -0.3), (0, 0, 0), 0.55, (w * 0.94, 3.0))               # violet seam between plates
    Pc(0, 0 if i % 2 == 0 else 1, (0, y, 0.2), (0, 16, 0), 0.95, (w, 4.6), (w * 0.84, 3.2))
    if i % 2 == 0:
        for s in (-1, 1):
            Pb(0, 0, (s * w, y, 0.2), (0, 0, -s * 102), 3.6, (0.55, 0.8), (0.1, 0.1))
for k in range(-4, 5):
    Pb(0, 0, (k * 1.55, 29.2, 1.2 - abs(k) * 0.15), (0, 0, -k * 17), 7.2 - abs(k) * 0.55, (1.25, 0.95), (0.1, 0.1))
for k in range(-3, 4):
    Pb(0, 1, (k * 1.9, 28.6, -2.4), (-25, 0, -k * 16), 6.5 - abs(k) * 0.4, (1.1, 0.8), (0.1, 0.1))
Pb(0, 1, (0, 26, 3.7), (0, 0, 0), 4.2, (4.6, 0.9), (1.0, 0.5))
for s in (-1, 1):
    Pb(0, 1, (s * 5.2, 24, 3.2), (0, 0, -s * 24), 6.2, (2.2, 0.9), (0.3, 0.3))

# ------------------------------------------------------------------ head: the tall hooded spire and the face
Pb(1, 0, (0, 28.5, 0), (0, 0, 0), 3.6, (2.8, 2.4), (2.4, 2.0))
Pb(1, 0, (0, 30, -1.2), (-4, 0, 0), 31, (9.0, 5.8), (0.25, 0.25))                # the crest
Pb(1, 1, (0, 31, 3.4), (2, 0, 0), 23, (6.4, 1.5), (0.3, 0.3))
for s in (-1, 1):
    Pb(1, 0, (s * 5.6, 30, 0.8), (-3, 0, -s * 15), 25, (3.4, 4.6), (0.3, 0.5))
    Pb(1, 1, (s * 6.0, 29, 0.2), (0, 0, -s * 52), 15, (2.5, 2.1), (0.2, 0.3))
    Pb(1, 0, (s * 6.5, 33, -1.5), (-12, 0, -s * 68), 13, (2.0, 1.8), (0.2, 0.3))
    Pc(1, 2, (s * 2.9, 30.2, 5.3), (0, 0, -s * 20), 7.5, (0.34, 0.3))               # violet slits under the diamond
    Pb(1, 1, (s * 2.9, 36.4, 3.6), (14, 0, -s * 42), 5.4, (0.9, 0.9), (0.15, 0.15))
Pc(1, 2, (0, 33.6, 5.6), (0, 0, 45), 6.0, (2.4, 0.6))                              # the face diamond
Pc(1, 0, (0, 38.4, 4.4), (22, 0, 0), 1.4, (6.6, 2.6), (4.6, 1.9))
for s in (-1, 1):
    Pb(1, 0, (s * 4.0, 28.8, 3.4), (0, 0, -s * 8), 4.2, (0.9, 1.0), (0.5, 0.6))

# ------------------------------------------------------------------ floating shoulder slabs
for s, g in ((-1, 2), (1, 3)):
    Pb(g, 0, (s * 13, 17.5, 0.6), (0, 0, -s * 30), 19, (5.2, 3.6), (2.4, 1.8))
    Pb(g, 1, (s * 16, 21.5, -1.2), (-8, 0, -s * 54), 15, (3.4, 2.6), (0.3, 0.4))
    Pb(g, 0, (s * 9, 24, 2.4), (6, 0, -s * 14), 8, (1.8, 1.2), (0.2, 0.3))
    Pc(g, 2, (s * 22.2, 29.6, 0.6), (0, 0, -s * 38), 3.2, (0.9, 0.7), (0.1, 0.1))

# ------------------------------------------------------------------ the colossal upper extensions
for s, g, k, tilt in ((-1, 4, 0.82, 5), (1, 5, 1.0, -2)):
    rl = -s * (90 + tilt)
    y0 = 34.0
    Pb(g, 0, (s * 15, y0, 0), (0, 0, rl), 24 * k, (2.5, 1.9), (7.2, 3.3), seg=1)                    # flared trumpet
    Pc(g, 2, (s * (15 + 24 * k), y0, 0), (0, 0, rl), 0.4, (7.0, 3.1))                      # violet rim of the trumpet
    Pb(g, 1, (s * (15 + 26 * k), y0, 0), (0, 0, rl), 3.0 * k, (3.0, 1.5), (3.0, 1.5), seg=2)         # collar joint
    Pb(g, 0, (s * (15 + 36 * k), y0, 0), (0, 0, rl), 34 * k, (6.8, 3.2), (3.4, 2.0), seg=3)           # second section
    Pc(g, 2, (s * (15 + 36 * k), y0, 0), (0, 0, rl), 0.4, (6.6, 3.0))
    Pb(g, 0, (s * (15 + 74 * k), y0, 0), (0, 0, rl), 62 * k, (3.2, 1.7), (0.2, 0.3), seg=4)           # the long blade
    for j in range(5):
        x = 15 + (82 + j * 11) * k
        Pb(g, 1, (s * x, y0 + (2.0 - j * 0.3), 0), (0, 0, -s * (6 + j * 3)), 5.5 - j * 0.6, (0.6, 0.5), (0.1, 0.1), seg=4)
    for j in range(3):
        Pb(g, 1, (s * (15 + (28 + j * 3) * k), y0 + (j - 1) * 5.0, 0.5), (0, j * 25, -s * (86 + j * 7)), 3.0, (0.6, 0.5), (0.1, 0.1), seg=2)

# ------------------------------------------------------------------ lower bone arms
def chain(g, s, start, rolls, lens, base_w, tips):
    p = np.array(start, float)
    for i, (rl, L) in enumerate(zip(rolls, lens)):
        r = (0, 0, -s * rl)
        w0 = base_w[i]
        w1 = base_w[i + 1]
        t = Pb(g, 0 if i != 1 else 1, tuple(p), r, L, (w0, w0 * 0.8), (w1, w1 * 0.8))
        p = np.array(t)
        Pc(g, 1, tuple(p), (0, 0, 0), 3.4, (w1 * 1.6, w1 * 1.3), (w1 * 1.6, w1 * 1.3))
    return p


for s, g in ((-1, 6), (1, 7)):
    Pb(g, 0, (s * 12, 28, 0.5), (0, 0, s * 40), 7.5, (1.1, 0.9), (0.1, 0.1))            # hooked bone near the shoulder
    end = chain(g, s, (s * 14, 26, 0), (112, 118), (30, 34), (3.0, 2.0, 1.4), None)
    Pb(g, 0, tuple(end + np.array([0, 0.5, 0])), (-28, 0, -s * 18), 8, (1.3, 1.1), (0.1, 0.1))   # elbow spike
    # hand: violet crystal cluster + claws
    for j, (rr, L) in enumerate(((-s * 70, 9), (-s * 108, 7), (-s * 40, 6))):
        Pb(g, 2, tuple(end), (j * 8 - 8, 0, rr), L, (1.4, 1.0), (0.15, 0.15))
    for j in range(4):
        a = (j - 1.5) * 24
        t = Pb(g, 0, tuple(end + np.array([s * 1.2, -1.0, 0])), (0, 0, -s * (118 + a * 0.8)), 8, (0.7, 0.55), (0.35, 0.3))
        Pb(g, 0, t, (0, 0, -s * (150 + a)), 6, (0.4, 0.3), (0.1, 0.1))

# ------------------------------------------------------------------ detached fragments, near and far
rnd = random.Random(1209)
for i in range(46):
    ang = rnd.uniform(0, 2 * math.pi)
    rad = rnd.uniform(24, 100)
    pos = (math.cos(ang) * rad, rnd.uniform(-2, 88), math.sin(ang) * rad * 0.9)
    L = rnd.uniform(2.5, 9) * (1 + rad / 90)
    w = L * rnd.uniform(0.12, 0.3)
    m = 2 if i % 11 == 0 else (0 if i % 2 else 1)
    Pc(8, m, pos, (rnd.uniform(0, 360), rnd.uniform(0, 360), rnd.uniform(0, 360)), L, (w, w * rnd.uniform(.5, 1)), (w * .15, w * .15), seg=i + 1)

# ------------------------------------------------------------------ constellation (boss frame: z<0 is behind it as seen from the player)
def sky(u, v, z=-105.0):
    return ((u - 640) * 0.1531, max(14.0, 32 + (360 - v) * 0.236), z)


STARS = []
for (u, v, sz) in ((1250, 70, 13), (905, 70, 8.5), (637, 118, 7.5), (468, 200, 6.5), (337, 277, 5.5), (225, 372, 4.5), (125, 470, 4.0)):
    STARS.append((*sky(u, v), sz, len(STARS)))
overhead = ((-150, 128, -60, 7), (-60, 150, -20, 9), (40, 142, -70, 6.5), (120, 120, -40, 8), (150, 108, 30, 6), (80, 150, 50, 7),
            (-40, 140, 80, 6), (-140, 115, 40, 7), (-95, 82, -125, 5.5), (160, 86, -90, 5.5))
for (x, y, z, sz) in overhead:
    STARS.append((x, y, z, sz, len(STARS)))
LINKS = [0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7, 8, 8, 9, 10, 11]


# ------------------------------------------------------------------ java export
def arr(vals):
    return ','.join(('%.2f' % v).rstrip('0').rstrip('.') for v in vals)


def write_java():
    flat = [v for p in parts for v in p]
    sflat = [v for s in STARS for v in s]
    src = '''package com.example.jjkspells.irontomb.client;

/** GENERATED by tools/irontomb_model.py: the part table of Irontomb's cosmic form and the constellation. Do not edit by hand. */
final class IrontombModel {
    static final int STRIDE = 14;   // group, mat, seg, cx,cy,cz, yaw,pitch,roll, len, w0,d0, w1,d1
    static final float[] PARTS = parse("%s");
    static final float[] STARS = parse("%s");   // x,y,z,size,seed
    static final int[] LINKS = {%s};

    private IrontombModel() {
    }

    private static float[] parse(String s) {
        String[] t = s.split(",");
        float[] o = new float[t.length];
        for (int i = 0; i < t.length; i++) {
            o[i] = Float.parseFloat(t[i]);
        }
        return o;
    }
}
''' % (arr(flat), arr(sflat), ','.join(map(str, LINKS)))
    open(JAVA, 'w').write(src)
    print('parts', len(parts), 'chars', len(arr(flat)))


if __name__ == '__main__':
    write_java()
