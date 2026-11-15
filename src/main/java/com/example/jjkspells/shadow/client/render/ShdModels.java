package com.example.jjkspells.shadow.client.render;

import com.example.jjkspells.vfx.Mesh;
import com.example.jjkspells.vfx.Prim;
import com.example.jjkspells.vfx.Xf;

/**
 * The Shadow models: a katana with its sheath, hooded silhouettes in six poses, the gothic throne, the serpent's head, a spear and
 * the dome. Only the vfx toolkit is used, no Minecraft classes, so everything can be built and checked on its own.
 * All figures face +Z and stand on y = 0; one block = one unit.
 */
public final class ShdModels {
    private ShdModels() {
    }

    public static final int SWORD = 0, FOLDED = 1, KNEEL = 2, SKY = 3, STILL = 4, SEATED = 5, LEAN = 6;
    public static final double BLADE_LEN = 0.95;

    // ------------------------------------------------------------------ katana

    /** A curved single-edged blade along +Y from 0 to 0.95 (edge towards -X), diamond section, flat shaded. */
    public static Mesh blade() {
        Mesh.B b = Mesh.b();
        int n = 14;
        double w = 0.045, t = 0.011;
        int[] ring = new int[4];
        int[][] rings = new int[n + 2][4];
        for (int k = 0; k <= n; k++) {
            double f = k / (double) n, y = BLADE_LEN * f;
            double taper = f > 0.9 ? Math.max(0.0, 1.0 - (f - 0.9) / 0.1) : 1.0;
            double ww = w * (0.8 + 0.2 * (1 - f)) * (0.15 + 0.85 * taper), tt = t * (0.25 + 0.75 * taper);
            double cx = 0.07 * f * f;
            double[][] sec = {{-ww, 0}, {ww * 0.15, tt}, {ww, 0}, {ww * 0.15, -tt}};
            for (int j = 0; j < 4; j++) {
                rings[k][j] = b.v(cx + sec[j][0], y, sec[j][1], 0, 0, 1, f * 2.0, j / 4.0);
            }
        }
        for (int k = 0; k < n; k++) {
            for (int j = 0; j < 4; j++) {
                b.quad(rings[k][j], rings[k][(j + 1) % 4], rings[k + 1][(j + 1) % 4], rings[k + 1][j]);
            }
        }
        return b.build().flat();
    }

    // ------------------------------------------------------------------ Shadow's ebony sword

    public static final double EBONY_LEN = 1.5;

    /** A long, narrow, straight blade of black steel along +Y from 0 to 1.5, hexagonal section, flat shaded. */
    public static Mesh ebonyBlade() {
        Mesh.B b = Mesh.b();
        int n = 12;
        double w = 0.042, t = 0.012;
        int[][] rings = new int[n + 1][6];
        for (int k = 0; k <= n; k++) {
            double f = k / (double) n, y = EBONY_LEN * f;
            double taper = f > 0.86 ? Math.max(0.0, 1.0 - (f - 0.86) / 0.14) : 1.0;
            double ww = w * (1.0 - 0.18 * f) * (0.08 + 0.92 * taper), tt = t * (0.2 + 0.8 * taper);
            double[][] sec = {{-ww, 0}, {-ww * 0.35, tt}, {ww * 0.35, tt}, {ww, 0}, {ww * 0.35, -tt}, {-ww * 0.35, -tt}};
            for (int j = 0; j < 6; j++) {
                rings[k][j] = b.v(sec[j][0], y, sec[j][1], 0, 0, 1, f * 2.0, j / 6.0);
            }
        }
        for (int k = 0; k < n; k++) {
            for (int j = 0; j < 6; j++) {
                b.quad(rings[k][j], rings[k][(j + 1) % 6], rings[k + 1][(j + 1) % 6], rings[k + 1][j]);
            }
        }
        return b.build().flat();
    }

    /** Dark handle and a plain narrow guard; the grip is centred on the origin, the blade starts at y = 0.1. */
    public static Mesh ebonyHilt() {
        Mesh.B b = Mesh.b();
        b.add(Prim.lathe(new double[][]{{0.0, -0.26}, {0.026, -0.26}, {0.03, -0.24}, {0.026, -0.2}, {0.024, 0.07}, {0.028, 0.09}, {0.0, 0.09}}, 8, 0.0).smooth());
        b.add(Prim.lathe(new double[][]{{0.0, 0.09}, {0.095, 0.09}, {0.095, 0.108}, {0.0, 0.108}}, 8, 0.0).xf(Xf.s(1.0, 1.0, 0.3)).flat());
        return b.build();
    }

    /** Guard, grip and pommel with the grip centred on the origin, pointing +Y; the blade starts at y = 0.1. */
    public static Mesh hilt() {
        Mesh.B b = Mesh.b();
        b.add(Prim.lathe(new double[][]{{0.0, -0.3}, {0.024, -0.3}, {0.03, -0.27}, {0.027, -0.2}, {0.027, 0.06}, {0.03, 0.08}, {0.0, 0.08}}, 8, 0.0).smooth());
        b.add(Prim.lathe(new double[][]{{0.0, 0.085}, {0.075, 0.085}, {0.075, 0.105}, {0.0, 0.105}}, 8, 0.0).xf(Xf.s(1.0, 1.0, 0.75)).flat());
        return b.build();
    }

    /** The scabbard along +Y from 0 (the kojiri) to 1.0 (the mouth), a little wider than the blade, slightly bent like it. */
    public static Mesh sheath() {
        Mesh.B b = Mesh.b();
        int n = 12;
        double w = 0.058, t = 0.027;
        int seg = 8;
        int[][] rings = new int[n + 1][seg];
        for (int k = 0; k <= n; k++) {
            double f = k / (double) n, y = 1.0 * f, cx = 0.07 * f * f;
            double s = f < 0.04 ? 0.7 + 7.5 * f : 1.0;
            for (int j = 0; j < seg; j++) {
                double a = Math.PI * 2 * j / seg;
                rings[k][j] = b.v(cx + Math.cos(a) * w * s, y, Math.sin(a) * t * s, Math.cos(a), 0, Math.sin(a), j / (double) seg, f * 3.0);
            }
        }
        for (int k = 0; k < n; k++) {
            for (int j = 0; j < seg; j++) {
                b.quad(rings[k][j], rings[k][(j + 1) % seg], rings[k + 1][(j + 1) % seg], rings[k + 1][j]);
            }
        }
        b.add(Prim.lathe(new double[][]{{0.0, 0.0}, {0.045, 0.0}, {0.06, 0.03}, {0.0, 0.03}}, 8, 0.0).xf(Xf.s(1.0, 1.0, 0.5)).flat());
        // the collar at the mouth
        b.add(Prim.lathe(new double[][]{{0.07, 0.94}, {0.075, 0.94}, {0.075, 1.0}, {0.07, 1.0}}, 8, 0.0).xf(Xf.s(1.0, 1.0, 0.5).then(Xf.t(0.07, 0, 0))).flat());
        return b.build().smooth();
    }

    // ------------------------------------------------------------------ silhouettes

    private static double[][] arm(double sx, double[] mid, double[] hand) {
        return new double[][]{{sx, 1.38, 0.0}, {(sx + mid[0]) * 0.5, (1.38 + mid[1]) * 0.5, mid[2] * 0.5}, mid, {(mid[0] + hand[0]) * 0.5, (mid[1] + hand[1]) * 0.5, (mid[2] + hand[2]) * 0.5}, hand};
    }

    private static Mesh sleeve(double[][] path) {
        double[] r = {0.09, 0.085, 0.07, 0.06, 0.05};
        return Prim.tube(path, r, 7, 0.5, false, true).smooth();
    }

    /**
     * A hooded figure: a flared cloak, a hood with no face (the eyes are drawn separately), two sleeves and ragged hem tails.
     * Height about 1.95 blocks. Pose SEATED is built for sitting on a seat 0.55 high with the legs in front.
     */
    public static Mesh figure(int pose) {
        Mesh.B b = Mesh.b();
        double top = 1.5;
        boolean kneel = pose == KNEEL, seated = pose == SEATED;
        double k = kneel ? 0.62 : seated ? 0.78 : 1.0;
        double[][] cloak = {
                {0.0, 0.0}, {0.62, 0.0}, {0.58, 0.12 * k}, {0.46, 0.45 * k}, {0.34, 0.9 * k}, {0.27, 1.12 * k}, {0.31, 1.3 * k}, {0.34, top * k}, {0.22, top * k + 0.06}, {0.0, top * k + 0.08}};
        Mesh c = Prim.lathe(cloak, 14, 0.0).jitter(0.025, 5).smooth();
        Mesh hood = Prim.lathe(new double[][]{{0.0, 0.0}, {0.2, 0.02}, {0.255, 0.16}, {0.22, 0.34}, {0.12, 0.46}, {0.0, 0.58}}, 12, 0.0).xf(Xf.s(1.0, 1.0, 1.1)).smooth();
        Xf head = Xf.t(0, top * k + 0.02, 0.0);
        switch (pose) {
            case SKY -> head = Xf.rx(-0.65).then(Xf.t(0, top * k + 0.02, 0.0));
            case KNEEL -> head = Xf.rx(0.4).then(Xf.t(0, top * k + 0.02, 0.0));
            case LEAN -> head = Xf.rx(0.3).then(Xf.t(0, top * k + 0.02, 0.0));
            default -> {
            }
        }
        b.add(c);
        b.add(hood.xf(head));
        double[][] left, right;
        switch (pose) {
            case SWORD -> {
                right = arm(0.3, new double[]{0.34, 1.12, 0.22}, new double[]{0.14, 0.95, 0.36});
                left = arm(-0.3, new double[]{-0.3, 1.1, 0.2}, new double[]{0.1, 0.95, 0.36});
            }
            case FOLDED -> {
                right = arm(0.3, new double[]{0.3, 1.2, 0.24}, new double[]{-0.12, 1.26, 0.3});
                left = arm(-0.3, new double[]{-0.3, 1.14, 0.26}, new double[]{0.12, 1.2, 0.34});
            }
            case KNEEL -> {
                right = arm(0.3 * 0.9, new double[]{0.32, 0.95, 0.2}, new double[]{0.14, 0.72, 0.4});
                left = arm(-0.3 * 0.9, new double[]{-0.3, 0.95, 0.2}, new double[]{0.1, 0.72, 0.4});
            }
            case SKY -> {
                right = arm(0.3, new double[]{0.62, 1.5, 0.1}, new double[]{0.95, 1.8, 0.0});
                left = arm(-0.3, new double[]{-0.62, 1.5, 0.1}, new double[]{-0.95, 1.8, 0.0});
            }
            case SEATED -> {
                right = arm(0.3, new double[]{0.4, 1.0, 0.18}, new double[]{0.46, 0.78, 0.38});
                left = arm(-0.3, new double[]{-0.4, 1.0, 0.18}, new double[]{-0.46, 0.78, 0.38});
            }
            case LEAN -> {
                right = arm(0.3, new double[]{0.34, 1.34, 0.45}, new double[]{0.3, 1.2, 0.95});
                left = arm(-0.3, new double[]{-0.4, 1.1, -0.1}, new double[]{-0.5, 0.85, -0.3});
            }
            default -> {
                right = arm(0.3, new double[]{0.36, 1.1, 0.04}, new double[]{0.38, 0.8, 0.08});
                left = arm(-0.3, new double[]{-0.36, 1.1, 0.04}, new double[]{-0.38, 0.8, 0.08});
            }
        }
        b.add(sleeve(right));
        b.add(sleeve(left));
        // ragged hem
        for (int i = 0; i < 5; i++) {
            double a = Math.PI * 2 * i / 5 + 0.3;
            b.add(Prim.shard(40 + i, 0.45 + 0.12 * (i % 3), 0.08).xf(Xf.rx(Math.PI / 2 * 0.92).then(Xf.ry(-a + Math.PI / 2)).then(Xf.t(Math.cos(a) * 0.5, 0.04, Math.sin(a) * 0.5))));
        }
        if (seated) {
            // knees and shins in front of the seat
            b.add(Prim.tube(new double[][]{{-0.16, 0.5, 0.0}, {-0.16, 0.5, 0.45}, {-0.16, 0.12, 0.62}}, new double[]{0.12, 0.1, 0.08}, 7, 0.5, false, true).smooth());
            b.add(Prim.tube(new double[][]{{0.16, 0.5, 0.0}, {0.16, 0.5, 0.45}, {0.16, 0.12, 0.62}}, new double[]{0.12, 0.1, 0.08}, 7, 0.5, false, true).smooth());
        }
        Mesh m = b.build();
        if (pose == LEAN) {
            m = m.xf(Xf.rx(0.32));
        }
        return m;
    }

    /** Where the eyes of a figure are (relative to the feet) and how far apart. */
    public static double[] eyes(int pose) {
        return switch (pose) {
            case KNEEL -> new double[]{0.0, 0.85, 0.2};
            case SEATED -> new double[]{0.0, 1.62, 0.2};
            case SKY -> new double[]{0.0, 1.78, 0.15};
            case LEAN -> new double[]{0.0, 1.62, 0.5};
            default -> new double[]{0.0, 1.78, 0.19};
        };
    }

    // ------------------------------------------------------------------ the throne

    /** A gothic obsidian throne about 3.6 blocks tall, seat top at y = 0.55, facing +Z. */
    public static Mesh throne() {
        Mesh.B b = Mesh.b();
        // stepped octagonal dais
        b.add(Prim.cylinder(2.3, 0.18, 8).flat());
        b.add(Prim.cylinder(1.9, 0.2, 8).xf(Xf.t(0, 0.18, 0)).flat());
        b.add(Prim.cylinder(1.5, 0.19, 8).xf(Xf.t(0, 0.38, 0)).flat());
        // seat block and armrests
        b.add(Prim.box(0.62, 0.0 + 0.0 + 0.1, 0.5).xf(Xf.t(0, 0.47, -0.1)).flat());
        for (int s = -1; s <= 1; s += 2) {
            b.add(Prim.box(0.14, 0.27, 0.48).xf(Xf.t(s * 0.78, 0.72, -0.1)).flat());
            b.add(Prim.crystal(0.11, 0.0, 4, 0.3, 0.0).xf(Xf.t(s * 0.78, 0.99, 0.32)).flat());
        }
        // tall back: a slab with a pointed arch
        double bz = -0.65;
        b.add(Prim.box(0.78, 1.35, 0.14).xf(Xf.t(0, 1.9, bz)).flat());
        b.add(Prim.lathe(new double[][]{{0.0, 0.0}, {0.78, 0.0}, {0.5, 0.55}, {0.18, 1.05}, {0.0, 1.3}}, 4, 0.0).xf(Xf.rx(0.0).then(Xf.ry(Math.PI / 4)).then(Xf.s(1, 1, 0.2)).then(Xf.t(0, 3.2, bz))).flat());
        // flanking pillars with spires and fins
        for (int s = -1; s <= 1; s += 2) {
            double px = s * 1.12;
            b.add(Prim.box(0.2, 1.05, 0.2).xf(Xf.t(px, 1.28, bz + 0.05)).flat());
            b.add(Prim.crystal(0.24, 0.9, 4, 0.7, 0.0).xf(Xf.t(px, 2.32, bz + 0.05)).flat());
            for (int i = 0; i < 3; i++) {
                b.add(Prim.shard(70 + i + (s > 0 ? 5 : 0), 0.9 - i * 0.2, 0.07).xf(Xf.rz(-s * (0.5 + 0.35 * i)).then(Xf.t(px + s * 0.16, 0.9 + i * 0.5, bz + 0.05))));
            }
            // buttress rising from the dais
            b.add(Prim.shard(90 + (s > 0 ? 1 : 0), 1.7, 0.2).xf(Xf.rz(-s * 0.42).then(Xf.t(s * 1.5, 0.35, bz - 0.2))));
        }
        // crown of spikes along the arch
        for (int i = -3; i <= 3; i++) {
            double h = 0.8 - Math.abs(i) * 0.12;
            b.add(Prim.shard(120 + i + 3, h, 0.06).xf(Xf.rz(-i * 0.16).then(Xf.t(i * 0.22, 3.0 + (3 - Math.abs(i)) * 0.18, bz))));
        }
        // broken stones around the base
        for (int i = 0; i < 9; i++) {
            double a = Math.PI * 2 * i / 9 + 0.2, r = 2.0 + 0.2 * (i % 3);
            b.add(Prim.shard(150 + i, 0.55 + 0.25 * (i % 4), 0.1).xf(Xf.rz(0.15 * Math.sin(i * 2)).then(Xf.ry(a)).then(Xf.t(Math.cos(a) * r, 0.18, Math.sin(a) * r))));
        }
        return b.build();
    }

    // ------------------------------------------------------------------ the serpent

    /** The serpent's head along +Z (snout), about 1.5 long, with a crest of horns; the neck joins at z = -0.6. */
    public static Mesh serpentHead() {
        Mesh.B b = Mesh.b();
        Mesh skull = Prim.lathe(new double[][]{{0.0, -0.7}, {0.3, -0.62}, {0.5, -0.3}, {0.46, 0.1}, {0.3, 0.55}, {0.2, 0.9}, {0.0, 1.0}}, 14, 0.0).xf(Xf.s(1.0, 1.0, 1.0).then(Xf.rx(Math.PI / 2)).then(Xf.s(1.0, 0.7, 1.0)));
        b.add(skull.smooth());
        for (int s = -1; s <= 1; s += 2) {
            b.add(Prim.shard(200 + s, 0.9, 0.09).xf(Xf.rx(-1.9).then(Xf.rz(-s * 0.35)).then(Xf.t(s * 0.3, 0.2, -0.3))));
            b.add(Prim.shard(210 + s, 0.55, 0.06).xf(Xf.rx(-1.6).then(Xf.rz(-s * 0.7)).then(Xf.t(s * 0.4, 0.05, -0.1))));
            b.add(Prim.shard(220 + s, 0.35, 0.05).xf(Xf.rx(1.3).then(Xf.rz(-s * 0.2)).then(Xf.t(s * 0.16, -0.18, 0.6))));
        }
        b.add(Prim.shard(230, 0.5, 0.07).xf(Xf.rx(-1.7).then(Xf.t(0, 0.25, -0.45))));
        return b.build();
    }

    /** A long thin spear along +Z from 0 to 1 (the serpent's head in its piercing form). */
    public static Mesh spear() {
        Mesh.B b = Mesh.b();
        b.add(Prim.lathe(new double[][]{{0.0, 0.0}, {0.34, 0.02}, {0.3, 0.35}, {0.22, 0.6}, {0.1, 0.85}, {0.0, 1.0}}, 6, 0.0).xf(Xf.rx(Math.PI / 2)).flat());
        for (int i = 0; i < 4; i++) {
            b.add(Prim.shard(300 + i, 0.4, 0.06).xf(Xf.rx(1.2).then(Xf.rz(i * Math.PI / 2)).then(Xf.t(0, 0, 0.12))));
        }
        return b.build();
    }

    // ------------------------------------------------------------------ the dome

    /** A hemisphere of radius 1 (y >= 0, open at the bottom), u around and v from the rim to the top. */
    public static Mesh dome(int lat, int lon) {
        Mesh.B b = Mesh.b();
        int[][] g = new int[lat + 1][lon + 1];
        for (int i = 0; i <= lat; i++) {
            double ph = Math.PI / 2 * i / lat;
            for (int j = 0; j <= lon; j++) {
                double th = Math.PI * 2 * j / lon;
                double x = Math.cos(ph) * Math.cos(th), y = Math.sin(ph), z = Math.cos(ph) * Math.sin(th);
                g[i][j] = b.v(x, y, z, x, y, z, j / (double) lon * 4.0, i / (double) lat);
            }
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                b.quad(g[i][j], g[i][j + 1], g[i + 1][j + 1], g[i + 1][j]);
            }
        }
        return b.build();
    }

    /** A thin standing blade of light (used for the final lines): a long diamond in the XY plane, length 1 along +X from -0.5. */
    public static Mesh needle() {
        Mesh.B b = Mesh.b();
        int a = b.v(-0.5, 0, 0, 0, 0, 1, 0, 0.5), c = b.v(0, 0.5, 0, 0, 0, 1, 0.5, 1), d = b.v(0.5, 0, 0, 0, 0, 1, 1, 0.5), e = b.v(0, -0.5, 0, 0, 0, 1, 0.5, 0);
        b.quad(a, e, d, c);
        return b.build();
    }

    /** The same surface seen from the other side (reversed winding and normals), for shells that are viewed from inside. */
    public static Mesh invert(Mesh m) {
        Mesh.B b = Mesh.b();
        for (int k = 0; k < m.vertexCount(); k++) {
            b.v(m.pos[k * 3], m.pos[k * 3 + 1], m.pos[k * 3 + 2], -m.nrm[k * 3], -m.nrm[k * 3 + 1], -m.nrm[k * 3 + 2], m.uv[k * 2], m.uv[k * 2 + 1]);
        }
        for (int k = 0; k < m.idx.length; k += 3) {
            b.tri(m.idx[k], m.idx[k + 2], m.idx[k + 1]);
        }
        return b.build();
    }
}
