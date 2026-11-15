package com.example.jjkspells.vfx;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Composite 3D models. Each returns named parts so a renderer can give every part its own texture, tint and glow. All models are
 * built once and cached by the callers (see {@link MeshRender#cached}).
 */
public final class Models {
    private Models() {
    }

    private static double[] bez(double[] a, double[] b, double[] c, double t) {
        double u = 1 - t;
        return new double[]{u * u * a[0] + 2 * u * t * b[0] + t * t * c[0], u * u * a[1] + 2 * u * t * b[1] + t * t * c[1], u * u * a[2] + 2 * u * t * b[2] + t * t * c[2]};
    }

    private static Mesh bezTube(double[] a, double[] b, double[] c, double r0, double r1, int steps, int seg) {
        double[][] p = new double[steps + 1][];
        double[] r = new double[steps + 1];
        for (int k = 0; k <= steps; k++) {
            double t = k / (double) steps;
            p[k] = bez(a, b, c, t);
            r[k] = r0 + (r1 - r0) * t;
        }
        return Prim.tube(p, r, seg, 4, true, true).smooth();
    }

    /** Frame with Z along fwd, Y as close to up as possible, origin o. */
    private static Xf look(double[] fwd, double[] up, double[] o) {
        double fl = Math.sqrt(fwd[0] * fwd[0] + fwd[1] * fwd[1] + fwd[2] * fwd[2]);
        double[] z = {fwd[0] / fl, fwd[1] / fl, fwd[2] / fl};
        double d = up[0] * z[0] + up[1] * z[1] + up[2] * z[2];
        double[] y = {up[0] - d * z[0], up[1] - d * z[1], up[2] - d * z[2]};
        double yl = Math.sqrt(y[0] * y[0] + y[1] * y[1] + y[2] * y[2]);
        y = new double[]{y[0] / yl, y[1] / yl, y[2] / yl};
        double[] x = {y[1] * z[2] - y[2] * z[1], y[2] * z[0] - y[0] * z[2], y[0] * z[1] - y[1] * z[0]};
        return Xf.frame(x, y, z, o);
    }

    // ------------------------------------------------------------------ serpent dragon (eastern style, smooth scales)

    /**
     * An eastern serpent dragon about 1 unit long: tail at z=0, head at z=1, body undulating in x. Parts: body, head, horns, spines,
     * whiskers, eyes. Textures: scales on body/head, bone on horns, additive for whiskers/eyes.
     */
    public static Map<String, Mesh> serpent(long seed) {
        Random rnd = new Random(seed);
        int N = 72;
        double[][] path = new double[N + 1][3];
        double[] rad = new double[N + 1];
        double ph = rnd.nextDouble() * 6;
        for (int k = 0; k <= N; k++) {
            double t = k / (double) N;
            path[k][0] = 0.075 * Math.sin(t * Math.PI * 5.2 + ph) * (0.35 + 0.65 * (1 - t * 0.45));
            path[k][1] = 0.032 * Math.sin(t * Math.PI * 3.4 + ph * 0.7);
            path[k][2] = t;
            double env;
            if (t < 0.5) {
                env = 0.006 + 0.046 * Math.pow(t / 0.5, 1.25);
            } else if (t < 0.86) {
                env = 0.052 - 0.020 * ((t - 0.5) / 0.36);
            } else {
                env = 0.032 - 0.004 * ((t - 0.86) / 0.14);
            }
            rad[k] = env;
        }
        Mesh.B body = Mesh.b();
        body.add(Prim.tube(path, rad, 14, 14, false, false).scaleUv(5, 1).smooth());
        // legs
        for (int side = -1; side <= 1; side += 2) {
            for (double lt : new double[]{0.36, 0.63}) {
                int k = (int) (lt * N);
                double[] o = path[k];
                double r = rad[k];
                double[] a = {o[0] + side * r * 0.7, o[1] - r * 0.6, o[2]};
                double[] b = {o[0] + side * (r + 0.05), o[1] - r - 0.035, o[2] + 0.035};
                double[] c = {o[0] + side * (r + 0.06), o[1] - r - 0.085, o[2] - 0.005};
                body.add(bezTube(a, b, c, 0.014, 0.007, 8, 8));
                for (int cl = -1; cl <= 1; cl++) {
                    body.add(Prim.cone(0.0035, 0.02, 5).xf(Xf.rz(Math.PI + cl * 0.35 * side).then(Xf.rx(cl * 0.3)).then(Xf.t(c[0], c[1], c[2] + cl * 0.008))));
                }
            }
        }
        // dorsal spines along the whole back, bigger near the head
        Mesh.B spines = Mesh.b();
        for (int k = 6; k < N - 2; k += 3) {
            double t = k / (double) N;
            double sz = 0.012 + 0.03 * Math.pow(Math.min(1, t * 1.15), 2.2) * (t > 0.8 ? 1.35 : 1);
            double[] o = path[k];
            double[] up = {0, 1, 0};
            double[] dir = {0, 1, -0.65};
            Xf f = look(dir, new double[]{0, 0, 1}, new double[]{o[0], o[1] + rad[k] * 0.92, o[2]});
            spines.add(Prim.cone(sz * 0.32, sz * 1.6, 5).xf(Xf.rx(Math.PI / 2).then(f)).flat());
        }
        // head, built facing +Z, then moved to the end of the path
        Mesh.B head = Mesh.b(), horns = Mesh.b(), whisk = Mesh.b(), eyes = Mesh.b();
        double[] end = path[N], pre = path[N - 3];
        double[] fwd = {end[0] - pre[0], end[1] - pre[1], end[2] - pre[2]};
        Xf hf = look(fwd, new double[]{0, 1, 0}, new double[]{end[0], end[1], end[2]});
        Mesh.B h = Mesh.b();
        h.add(Prim.sphere(0.04, 12, 16).xf(Xf.s(1, 0.82, 1.35).then(Xf.t(0, 0.004, 0.025))));
        h.add(Prim.lathe(new double[][]{{0.001, 0}, {0.032, 0.002}, {0.026, 0.05}, {0.018, 0.1}, {0.012, 0.118}, {0.001, 0.124}}, 12, 0).xf(Xf.rx(Math.PI / 2).then(Xf.s(1, 0.62, 1)).then(Xf.t(0, 0.0, 0.035))).smooth());
        Mesh jaw = Prim.lathe(new double[][]{{0.001, 0}, {0.026, 0.002}, {0.02, 0.045}, {0.013, 0.09}, {0.001, 0.098}}, 10, 0).xf(Xf.rx(Math.PI / 2).then(Xf.s(1, 0.5, 1)).then(Xf.rx(0.42)).then(Xf.t(0, -0.022, 0.03))).smooth();
        h.add(jaw);
        for (int i = 0; i < 6; i++) {
            for (int side = -1; side <= 1; side += 2) {
                double z = 0.05 + i * 0.016, w = 0.017 - i * 0.0013;
                h.add(Prim.cone(0.0032, 0.013, 4).xf(Xf.rx(Math.PI).then(Xf.t(side * w, -0.012, z))).flat());
                double jz = 0.04 + i * 0.012;
                h.add(Prim.cone(0.0028, 0.011, 4).xf(Xf.t(side * (w - 0.002), -0.03 - (jz - 0.03) * 0.3, jz + 0.01)).flat());
            }
        }
        head.add(h.build().xf(hf));
        for (int side = -1; side <= 1; side += 2) {
            double[] a = {side * 0.02, 0.028, 0.0}, b = {side * 0.055, 0.075, -0.06}, c = {side * 0.07, 0.085, -0.17};
            horns.add(bezTube(a, b, c, 0.011, 0.0012, 12, 7).xf(hf));
            double[] wa = {side * 0.022, -0.004, 0.115}, wb = {side * 0.16, -0.03, 0.2}, wc = {side * 0.28, 0.07, -0.02};
            whisk.add(bezTube(wa, wb, wc, 0.0028, 0.0006, 22, 5).xf(hf));
            eyes.add(Prim.sphere(0.0085, 8, 10).xf(Xf.t(side * 0.031, 0.02, 0.055).then(hf)));
        }
        // a flowing mane down the neck
        for (int k = N - 22; k < N - 3; k += 2) {
            double t = (k - (N - 22)) / 19.0;
            double[] o = path[k];
            double sz = 0.05 + 0.04 * Math.sin(Math.PI * t);
            Xf f = look(new double[]{0, 0.6, -1}, new double[]{0, 1, 0}, new double[]{o[0], o[1] + rad[k] * 0.8, o[2]});
            spines.add(Prim.cone(sz * 0.12, sz, 4).xf(Xf.rx(Math.PI / 2).then(f)).flat());
        }
        Map<String, Mesh> out = new LinkedHashMap<>();
        out.put("body", body.build());
        out.put("head", head.build());
        out.put("horns", horns.build());
        out.put("spines", spines.build());
        out.put("whiskers", whisk.build());
        out.put("eyes", eyes.build());
        return out;
    }

    // ------------------------------------------------------------------ western dragon with wings (length ~2.4, faceted option)

    /**
     * A western dragon: tail tip at z=-1.2, body around the origin, head near (0, 0.5, 1.05) looking +Z. Wings are separate (see
     * {@link #wing}). faceted = low-poly crystal look. Parts: body, head, horns, eyes.
     */
    public static Map<String, Mesh> western(long seed, boolean faceted) {
        Random rnd = new Random(seed);
        double[][] ctrl = {{0, 0.02, -1.25}, {0.05, -0.02, -0.85}, {-0.04, 0.0, -0.45}, {0, 0.05, -0.05}, {0, 0.1, 0.35}, {0, 0.28, 0.62}, {0, 0.52, 0.76}, {0, 0.68, 0.92}};
        int N = 48;
        double[][] path = new double[N + 1][];
        double[] rad = new double[N + 1];
        for (int k = 0; k <= N; k++) {
            double t = k / (double) N * (ctrl.length - 1);
            int i = Math.min((int) t, ctrl.length - 2);
            double f = t - i;
            // catmull-rom through the control points
            double[] p0 = ctrl[Math.max(0, i - 1)], p1 = ctrl[i], p2 = ctrl[i + 1], p3 = ctrl[Math.min(ctrl.length - 1, i + 2)];
            double[] p = new double[3];
            for (int a = 0; a < 3; a++) {
                p[a] = 0.5 * ((2 * p1[a]) + (-p0[a] + p2[a]) * f + (2 * p0[a] - 5 * p1[a] + 4 * p2[a] - p3[a]) * f * f + (-p0[a] + 3 * p1[a] - 3 * p2[a] + p3[a]) * f * f * f);
            }
            path[k] = p;
            double u = k / (double) N;
            rad[k] = u < 0.55 ? 0.012 + 0.125 * Math.pow(u / 0.55, 1.3) : (u < 0.8 ? 0.137 - 0.06 * ((u - 0.55) / 0.25) : 0.077 - 0.02 * ((u - 0.8) / 0.2));
        }
        Mesh bodyM = Prim.tube(path, rad, faceted ? 7 : 14, 6, false, false).scaleUv(3, 1);
        bodyM = faceted ? bodyM.jitter(0.006, (int) seed).flat() : bodyM.smooth();
        Mesh.B body = Mesh.b();
        body.add(bodyM);
        // legs
        for (int side = -1; side <= 1; side += 2) {
            for (double lz : new double[]{0.28, -0.32}) {
                double[] a = {side * 0.1, 0.0, lz}, b = {side * 0.2, -0.12, lz + 0.06}, c = {side * 0.17, -0.3, lz - 0.02};
                Mesh leg = bezTube(a, b, c, 0.05, 0.022, 8, faceted ? 5 : 8);
                body.add(faceted ? leg.flat() : leg);
                for (int cl = -1; cl <= 1; cl++) {
                    body.add(Prim.cone(0.012, 0.07, 4).xf(Xf.rx(Math.PI / 2 + 0.3).then(Xf.t(c[0] + cl * 0.025, c[1], c[2] + 0.02))).flat());
                }
            }
        }
        // spines along the back
        for (int k = 4; k < N - 4; k += 2) {
            double t = k / (double) N;
            double sz = 0.07 + 0.05 * Math.sin(Math.PI * Math.min(1, t * 1.1));
            double[] o = path[k];
            Xf f = look(new double[]{0, 1, -0.7}, new double[]{0, 0, 1}, new double[]{o[0], o[1] + rad[k] * 0.9, o[2]});
            body.add(Prim.cone(sz * 0.3, sz * 1.7, 4).xf(Xf.rx(Math.PI / 2).then(f)).flat());
        }
        // head
        Mesh.B head = Mesh.b(), horns = Mesh.b(), eyes = Mesh.b();
        double[] end = path[N], pre = path[N - 4];
        double[] fwd = {end[0] - pre[0], end[1] - pre[1] - 0.2, end[2] - pre[2] + 0.35};
        Xf hf = look(fwd, new double[]{0, 1, 0}, new double[]{end[0], end[1], end[2]});
        Mesh.B h = Mesh.b();
        Mesh skull = Prim.sphere(0.085, 8, 10).xf(Xf.s(1, 0.85, 1.35));
        h.add(faceted ? skull.flat() : skull.smooth());
        Mesh snout = Prim.lathe(new double[][]{{0.001, 0}, {0.07, 0.0}, {0.055, 0.1}, {0.038, 0.2}, {0.001, 0.24}}, faceted ? 6 : 12, 0).xf(Xf.rx(Math.PI / 2).then(Xf.s(1, 0.62, 1)).then(Xf.t(0, -0.01, 0.06)));
        h.add(faceted ? snout.flat() : snout.smooth());
        Mesh jw = Prim.lathe(new double[][]{{0.001, 0}, {0.05, 0.0}, {0.036, 0.1}, {0.001, 0.2}}, faceted ? 6 : 10, 0).xf(Xf.rx(Math.PI / 2).then(Xf.s(1, 0.5, 1)).then(Xf.rx(0.3)).then(Xf.t(0, -0.06, 0.05)));
        h.add(faceted ? jw.flat() : jw.smooth());
        for (int i = 0; i < 5; i++) {
            for (int side = -1; side <= 1; side += 2) {
                h.add(Prim.cone(0.008, 0.035, 4).xf(Xf.rx(Math.PI).then(Xf.t(side * (0.04 - i * 0.004), -0.02, 0.13 + i * 0.03))).flat());
            }
        }
        head.add(h.build().xf(hf));
        for (int side = -1; side <= 1; side += 2) {
            double[] a = {side * 0.05, 0.07, 0.0}, b = {side * 0.12, 0.2, -0.14}, c = {side * 0.14, 0.34, -0.36};
            horns.add(bezTube(a, b, c, 0.03, 0.003, 10, faceted ? 5 : 8).flat().xf(hf));
            double[] a2 = {side * 0.07, 0.03, 0.02}, b2 = {side * 0.22, 0.04, -0.06}, c2 = {side * 0.3, -0.04, -0.2};
            horns.add(bezTube(a2, b2, c2, 0.018, 0.002, 8, 5).flat().xf(hf));
            eyes.add(Prim.sphere(0.017, 6, 8).xf(Xf.s(1.3, 0.7, 1).then(Xf.t(side * 0.075, 0.04, 0.1)).then(hf)));
        }
        Map<String, Mesh> out = new LinkedHashMap<>();
        out.put("body", body.build());
        out.put("head", head.build());
        out.put("horns", horns.build());
        out.put("eyes", eyes.build());
        return out;
    }

    /**
     * One dragon wing in local space: shoulder at the origin, spreading toward +X and up. Parts: bones, membrane. Mirror with a
     * negative X scale for the other side. Flap by rotating about the Z axis through the origin.
     */
    public static Map<String, Mesh> wing(long seed, double span) {
        Random rnd = new Random(seed);
        double[] sh = {0, 0, 0}, el = {0.3, 0.38, -0.05}, wr = {0.72, 0.5, -0.1};
        double[][] tips = {{1.5, 0.95, -0.2}, {1.5, 0.35, -0.16}, {1.2, -0.1, -0.12}, {0.8, -0.45, -0.08}, {0.35, -0.55, -0.04}};
        Mesh.B bones = Mesh.b(), mem = Mesh.b();
        bones.add(bezTube(sh, new double[]{0.12, 0.3, -0.02}, el, 0.05, 0.035, 6, 7));
        bones.add(bezTube(el, new double[]{0.5, 0.5, -0.08}, wr, 0.035, 0.022, 6, 7));
        for (double[] t : tips) {
            double[] mid = {(wr[0] + t[0]) * 0.5, (wr[1] + t[1]) * 0.5 + 0.03, (wr[2] + t[2]) * 0.5};
            bones.add(bezTube(wr, mid, t, 0.02, 0.004, 8, 5));
            bones.add(Prim.cone(0.012, 0.09, 4).xf(Xf.rz(-Math.PI / 2 + Math.atan2(t[1] - wr[1], t[0] - wr[0])).then(Xf.t(t[0], t[1], t[2]))).flat());
        }
        // membrane: fan from the wrist with scalloped edges, plus the arm panel back to the body
        Mesh.B m = Mesh.b();
        int c = m.v(wr[0], wr[1], wr[2], 0, 0, 1, wr[0] / 1.5, wr[1] / 1.5 + 0.3);
        int prev = -1;
        for (int k = 0; k < tips.length; k++) {
            double[] t = tips[k];
            int vt = m.v(t[0], t[1], t[2], 0, 0, 1, t[0] / 1.5, t[1] / 1.5 + 0.3);
            if (prev >= 0) {
                double[] a = tips[k - 1];
                double mx = (a[0] + t[0]) * 0.5, my = (a[1] + t[1]) * 0.5, mz = (a[2] + t[2]) * 0.5;
                mx += (wr[0] - mx) * 0.22;
                my += (wr[1] - my) * 0.22;
                int vm = m.v(mx, my, mz, 0, 0, 1, mx / 1.5, my / 1.5 + 0.3);
                m.tri(c, prev, vm);
                m.tri(c, vm, vt);
            }
            prev = vt;
        }
        int root = m.v(0.0, -0.16, 0.0, 0, 0, 1, 0, 0.2), elb = m.v(el[0], el[1], el[2], 0, 0, 1, el[0] / 1.5, el[1] / 1.5 + 0.3), shv = m.v(0, 0, 0, 0, 0, 1, 0, 0.3);
        m.tri(c, prev, root);
        m.tri(c, root, shv);
        m.tri(c, shv, elb);
        Mesh membrane = m.build().smooth().xf(Xf.s(span / 1.5 * 1.0));
        Mesh bone = bones.build().xf(Xf.s(span / 1.5));
        Map<String, Mesh> out = new LinkedHashMap<>();
        out.put("bones", bone);
        out.put("membrane", membrane);
        return out;
    }

    // ------------------------------------------------------------------ weapons and ornaments

    /** A long lance along +Y from 0 to 1: shaft, leaf blade, fins. */
    public static Mesh lance(long seed) {
        Mesh.B b = Mesh.b();
        b.add(Prim.lathe(new double[][]{{0.0, 0.0}, {0.013, 0.0}, {0.013, 0.78}, {0.02, 0.8}, {0.0, 0.8}}, 8, 0));
        b.add(Prim.lathe(new double[][]{{0, 0.8}, {0.05, 0.84}, {0.04, 0.92}, {0, 1.0}}, 4, 0.0).flat());
        for (int i = 0; i < 4; i++) {
            b.add(Prim.shard(seed + i, 0.12, 0.022).xf(Xf.rx(Math.PI + 0.35).then(Xf.ry(i * Math.PI / 2)).then(Xf.t(0, 0.1, 0))));
        }
        for (int i = 0; i < 4; i++) {
            b.add(Prim.shard(seed + 9 + i, 0.18, 0.03).xf(Xf.rx(-0.9).then(Xf.ry(i * Math.PI / 2 + 0.78)).then(Xf.t(0, 0.78, 0))));
        }
        return b.build();
    }

    /** A star-shaped polyhedron: icosahedron with spikes on every face. radius ~1. */
    public static Mesh spikedStar(double spike) {
        Mesh ico = Prim.poly(Prim.Poly.ICOSA);
        Mesh.B b = Mesh.b();
        b.add(ico.xf(Xf.s(0.72)));
        double[][] dirs = new double[ico.triCount()][];
        for (int t = 0; t < ico.triCount(); t++) {
            double cx = 0, cy = 0, cz = 0;
            for (int k = 0; k < 3; k++) {
                int v = ico.idx[t * 3 + k];
                cx += ico.pos[v * 3];
                cy += ico.pos[v * 3 + 1];
                cz += ico.pos[v * 3 + 2];
            }
            double l = Math.sqrt(cx * cx + cy * cy + cz * cz);
            double[] n = {cx / l, cy / l, cz / l};
            Xf f = look(n, Math.abs(n[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0}, new double[]{n[0] * 0.58, n[1] * 0.58, n[2] * 0.58});
            b.add(Prim.cone(0.22, spike, 3).xf(Xf.rx(Math.PI / 2).then(f)).flat());
        }
        return b.build();
    }
}
