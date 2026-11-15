package com.example.jjkspells.veldora.client.render;

import com.example.jjkspells.vfx.Mesh;
import com.example.jjkspells.vfx.Prim;
import com.example.jjkspells.vfx.Xf;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/** Pure geometry for the Veldora storm dragon (no Minecraft imports): a serpent that follows any path, and a huge dragon head. */
public final class VelModels {
    private VelModels() {
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

    /** Helix path from the top (tail, index 0) to the bottom (head). radius(u) with u 0..1 from tail to head. */
    public static double[][] helix(double turns, double H, int n, DoubleUnaryOperator radius, double phase) {
        double[][] p = new double[n + 1][3];
        for (int k = 0; k <= n; k++) {
            double u = k / (double) n, a = phase + Math.PI * 2 * turns * u, r = radius.applyAsDouble(u);
            p[k][0] = Math.cos(a) * r;
            p[k][1] = H * (1 - u);
            p[k][2] = Math.sin(a) * r;
        }
        return p;
    }

    /** A flat ring path (a circle of radius R at height y), tail first; the head ends after 'sweep' radians. */
    public static double[][] arc(double R, double y, double sweep, int n, double wave) {
        double[][] p = new double[n + 1][3];
        for (int k = 0; k <= n; k++) {
            double u = k / (double) n, a = sweep * u;
            double r = R + wave * Math.sin(u * Math.PI * 6);
            p[k][0] = Math.cos(a) * r;
            p[k][1] = y + wave * 0.6 * Math.sin(u * Math.PI * 4 + 1);
            p[k][2] = Math.sin(a) * r;
        }
        return p;
    }

    /**
     * A serpent dragon that follows 'path' (tail first, head last). bodyR is the thickest body radius. The head faces the direction
     * of travel. Parts: body, head, horns, spines, whiskers, eyes.
     */
    public static Map<String, Mesh> dragonPath(double[][] path, double bodyR, long seed) {
        int N = path.length - 1;
        double f = bodyR / 0.052;
        double[] rad = new double[N + 1];
        for (int k = 0; k <= N; k++) {
            double t = k / (double) N, env;
            if (t < 0.5) {
                env = 0.006 + 0.046 * Math.pow(t / 0.5, 1.25);
            } else if (t < 0.86) {
                env = 0.052 - 0.020 * ((t - 0.5) / 0.36);
            } else {
                env = 0.032 - 0.004 * ((t - 0.86) / 0.14);
            }
            rad[k] = env * f;
        }
        Mesh body = Prim.tube(path, rad, 12, 1.0 / (bodyR * 6.0), false, false).scaleUv(5, 1).smooth();
        Mesh.B spines = Mesh.b();
        for (int k = 4; k < N - 2; k += 2) {
            double t = k / (double) N;
            double sz = (0.012 + 0.03 * Math.pow(Math.min(1, t * 1.15), 2.2)) * f;
            double[] a = path[k - 1], b = path[k + 1];
            double[] tan = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
            double[] o = {path[k][0], path[k][1] + rad[k] * 0.92, path[k][2]};
            Xf fr = look(new double[]{tan[0] * -0.65, 1.0 + tan[1] * -0.65, tan[2] * -0.65}, new double[]{0, 0, 1}, o);
            spines.add(Prim.cone(sz * 0.32, sz * 1.6, 5).xf(Xf.rx(Math.PI / 2).then(fr)).flat());
        }
        double[] end = path[N], pre = path[Math.max(0, N - 3)];
        double[] fwd = {end[0] - pre[0], end[1] - pre[1], end[2] - pre[2]};
        Xf hf = look(fwd, new double[]{0, 1, 0}, new double[]{end[0], end[1], end[2]});
        Map<String, Mesh> hp = head(seed, 0.42);
        Xf place = Xf.s(f).then(hf);
        Map<String, Mesh> out = new LinkedHashMap<>();
        out.put("body", body);
        out.put("head", hp.get("head").xf(place));
        out.put("horns", hp.get("horns").xf(place));
        out.put("spines", spines.build());
        out.put("whiskers", hp.get("whiskers").xf(place));
        out.put("eyes", hp.get("eyes").xf(place));
        return out;
    }

    /** The dragon head alone, facing +Z with the skull at the origin (about 0.16 long, scale it up). jaw = opening angle in radians. */
    public static Map<String, Mesh> head(long seed, double jaw) {
        Mesh.B h = Mesh.b(), horns = Mesh.b(), whisk = Mesh.b(), eyes = Mesh.b();
        h.add(Prim.sphere(0.04, 12, 16).xf(Xf.s(1, 0.82, 1.35).then(Xf.t(0, 0.004, 0.025))));
        h.add(Prim.lathe(new double[][]{{0.001, 0}, {0.032, 0.002}, {0.026, 0.05}, {0.018, 0.1}, {0.012, 0.118}, {0.001, 0.124}}, 12, 0).xf(Xf.rx(Math.PI / 2).then(Xf.s(1, 0.62, 1)).then(Xf.t(0, 0.0, 0.035))).smooth());
        h.add(Prim.lathe(new double[][]{{0.001, 0}, {0.026, 0.002}, {0.02, 0.045}, {0.013, 0.09}, {0.001, 0.098}}, 10, 0).xf(Xf.rx(Math.PI / 2).then(Xf.s(1, 0.5, 1)).then(Xf.rx(jaw)).then(Xf.t(0, -0.022, 0.03))).smooth());
        for (int i = 0; i < 6; i++) {
            for (int side = -1; side <= 1; side += 2) {
                double z = 0.05 + i * 0.016, w = 0.017 - i * 0.0013;
                h.add(Prim.cone(0.0032, 0.013, 4).xf(Xf.rx(Math.PI).then(Xf.t(side * w, -0.012, z))).flat());
                double jz = 0.04 + i * 0.012;
                h.add(Prim.cone(0.0028, 0.011, 4).xf(Xf.rx(jaw * 0.7).then(Xf.t(side * (w - 0.002), -0.03 - (jz - 0.03) * 0.3 - jaw * 0.02, jz + 0.01))).flat());
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            horns.add(bezTube(new double[]{side * 0.02, 0.028, 0.0}, new double[]{side * 0.055, 0.075, -0.06}, new double[]{side * 0.07, 0.085, -0.17}, 0.011, 0.0012, 12, 7));
            whisk.add(bezTube(new double[]{side * 0.022, -0.004, 0.115}, new double[]{side * 0.16, -0.03, 0.2}, new double[]{side * 0.28, 0.07, -0.02}, 0.0028, 0.0006, 22, 5));
            eyes.add(Prim.sphere(0.0085, 8, 10).xf(Xf.t(side * 0.031, 0.02, 0.055)));
        }
        Map<String, Mesh> out = new LinkedHashMap<>();
        out.put("head", h.build());
        out.put("horns", horns.build());
        out.put("whiskers", whisk.build());
        out.put("eyes", eyes.build());
        return out;
    }
}
