package com.example.jjkspells.velgrynd.client.render;

import com.example.jjkspells.vfx.Mesh;
import com.example.jjkspells.vfx.Prim;
import com.example.jjkspells.vfx.Xf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure geometry for the Velgrynd set (no Minecraft classes, so it can be built and previewed anywhere). */
public final class VgrModels {
    private VgrModels() {
    }

    /** The edges of a regular polyhedron (circumradius 1) as thin gold bars with the given radius. */
    public static Mesh wire(Prim.Poly kind, double bar) {
        Mesh m = Prim.poly(kind);
        List<double[]> vs = new ArrayList<>();
        for (int k = 0; k < m.vertexCount(); k++) {
            double[] p = {m.pos[k * 3], m.pos[k * 3 + 1], m.pos[k * 3 + 2]};
            boolean dup = false;
            for (double[] q : vs) {
                if (Math.abs(q[0] - p[0]) + Math.abs(q[1] - p[1]) + Math.abs(q[2] - p[2]) < 1e-4) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                vs.add(p);
            }
        }
        double min = 1e9;
        for (int a = 0; a < vs.size(); a++) {
            for (int b = a + 1; b < vs.size(); b++) {
                min = Math.min(min, d(vs.get(a), vs.get(b)));
            }
        }
        Mesh.B out = Mesh.b();
        for (int a = 0; a < vs.size(); a++) {
            for (int b = a + 1; b < vs.size(); b++) {
                if (d(vs.get(a), vs.get(b)) < min * 1.02) {
                    out.add(Prim.tube(new double[][]{vs.get(a), vs.get(b)}, new double[]{bar, bar}, 6, 1, true, true));
                }
            }
            out.add(Prim.sphere(bar * 1.9, 6, 8).xf(Xf.t(vs.get(a)[0], vs.get(a)[1], vs.get(a)[2])).flat());
        }
        return out.build();
    }

    private static double d(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    /** A solar prominence: an arch of fire from (-span/2,0,0) to (span/2,0,0) rising to 'height'. */
    public static Mesh prominence(long seed, double span, double height, double r0) {
        Random rnd = new Random(seed);
        int n = 20;
        double[][] p = new double[n + 1][3];
        double[] r = new double[n + 1];
        double wob = rnd.nextDouble() * 6.28;
        for (int k = 0; k <= n; k++) {
            double u = k / (double) n;
            p[k][0] = (u - 0.5) * span;
            p[k][1] = height * Math.sin(Math.PI * u) * (1 + 0.12 * Math.sin(wob + u * 9));
            p[k][2] = 0.12 * span * Math.sin(u * 7 + wob) * Math.sin(Math.PI * u);
            r[k] = r0 * (0.35 + 0.65 * Math.sin(Math.PI * u));
        }
        return Prim.tube(p, r, 8, 2, false, false).smooth();
    }

    /** n blades radiating from a centre in the XZ plane, tilted up, base at radius r0, length len. */
    public static Mesh crown(int n, double r0, double len, double w, long seed) {
        Mesh.B b = Mesh.b();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double l = len * (i % 2 == 0 ? 1.0 : 0.62);
            b.add(Prim.shard(seed + i, l, w).xf(Xf.rz(-Math.PI / 2 + 0.25).then(Xf.t(r0, 0, 0)).then(Xf.ry(-a))));
        }
        return b.build();
    }

    /** An open cylinder along Y from 0 to h (sides only); u wraps uRep times, v repeats vRep times. */
    public static Mesh pipe(double r, double h, int seg, double uRep, double vRep) {
        return Prim.lathe(new double[][]{{r, 0}, {r, h}}, seg, 0).scaleUv(uRep, vRep);
    }

    /** An open cone from radius r0 at y=0 to r1 at y=h. */
    public static Mesh pipe(double r0, double r1, double h, int seg, double uRep, double vRep) {
        return Prim.lathe(new double[][]{{r0, 0}, {r1, h}}, seg, 0).scaleUv(uRep, vRep);
    }

    /** A flat engraved sigil lying on the ground (y about 0): two rings, a hexagram and tick marks. Radius 1. */
    public static Mesh sigil() {
        Mesh.B b = Mesh.b();
        b.add(Prim.disc(0.93, 1.0, 72, 1).xf(Xf.t(0, 0.012, 0)));
        b.add(Prim.disc(0.60, 0.64, 72, 1).xf(Xf.t(0, 0.012, 0)));
        b.add(Prim.disc(0.30, 0.33, 48, 1).xf(Xf.t(0, 0.012, 0)));
        for (int i = 0; i < 36; i++) {
            double a = Math.PI * 2 * i / 36;
            double len = i % 3 == 0 ? 0.2 : 0.1;
            b.add(Prim.box(0.01, 0.006, len * 0.5).xf(Xf.t(0, 0.012, 0.8).then(Xf.ry(-a))));
        }
        for (int tri = 0; tri < 2; tri++) {
            double[][] v = new double[3][];
            for (int k = 0; k < 3; k++) {
                double a = Math.PI * (2.0 * k / 3 + tri / 3.0) + Math.PI / 2;
                v[k] = new double[]{Math.cos(a) * 0.62, 0.0, Math.sin(a) * 0.62};
            }
            for (int k = 0; k < 3; k++) {
                double[] p = v[k], q = v[(k + 1) % 3];
                double mx = (p[0] + q[0]) / 2, mz = (p[2] + q[2]) / 2, len = Math.hypot(q[0] - p[0], q[2] - p[2]);
                double ang = Math.atan2(q[0] - p[0], q[2] - p[2]);
                b.add(Prim.box(0.008, 0.006, len / 2).xf(Xf.ry(ang).then(Xf.t(mx, 0.012, mz))));
            }
        }
        return b.build();
    }

    /** A gold ring with notches: torus plus small blocks around it. Major radius 1. */
    public static Mesh notchedRing(int notches, double thick) {
        Mesh.B b = Mesh.b();
        b.add(Prim.torus(1.0, thick, 64, 8));
        for (int i = 0; i < notches; i++) {
            double a = Math.PI * 2 * i / notches;
            b.add(Prim.box(thick * 1.7, thick * 1.7, thick * 2.2).xf(Xf.t(1.0, 0, 0).then(Xf.ry(-a))).flat());
        }
        return b.build();
    }
}
