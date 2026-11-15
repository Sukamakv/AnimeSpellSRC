package com.example.jjkspells.tempest.client.render;

import com.example.jjkspells.vfx.Mesh;
import com.example.jjkspells.vfx.Noise;
import com.example.jjkspells.vfx.Prim;
import com.example.jjkspells.vfx.Xf;

import java.util.HashSet;
import java.util.Set;

/** Pure-geometry models of the Rimuru set (no Minecraft types, so they can be tested with plain javac). */
final class RimModels {
    private RimModels() {
    }

    /** A biconvex lens around the Y axis: radius R, half thickness t. v runs from the centre of one face over the rim to the other. */
    static Mesh lens(double R, double t) {
        double[][] pr = {{0.001, -t}, {R * 0.5, -t * 0.86}, {R * 0.8, -t * 0.5}, {R * 0.95, -t * 0.16}, {R, 0}, {R * 0.95, t * 0.16}, {R * 0.8, t * 0.5}, {R * 0.5, t * 0.86}, {0.001, t}};
        return Prim.lathe(pr, 56, 0).smooth();
    }

    /** Geodesic sphere of radius 1 with flat panels (freq 1 = 80 panels with the icosahedron split once). */
    static Mesh geodesic(int freq) {
        Mesh.B c = Mesh.b();
        for (double[][] tri : tris(freq)) {
            int a = c.v(tri[0][0], tri[0][1], tri[0][2], 0, 1, 0, 0, 0);
            int bb = c.v(tri[1][0], tri[1][1], tri[1][2], 0, 1, 0, 1, 0);
            int cc = c.v(tri[2][0], tri[2][1], tri[2][2], 0, 1, 0, 0.5, 1);
            c.tri(a, bb, cc);
        }
        return c.build().flat();
    }

    private static java.util.List<double[][]> tris(int freq) {
        Mesh ico = Prim.poly(Prim.Poly.ICOSA);
        java.util.List<double[][]> list = new java.util.ArrayList<>();
        for (int t = 0; t < ico.triCount(); t++) {
            double[][] tri = new double[3][3];
            for (int k = 0; k < 3; k++) {
                int v = ico.idx[t * 3 + k];
                tri[k] = new double[]{ico.pos[v * 3], ico.pos[v * 3 + 1], ico.pos[v * 3 + 2]};
            }
            list.add(tri);
        }
        for (int f = 0; f < freq; f++) {
            java.util.List<double[][]> next = new java.util.ArrayList<>();
            for (double[][] t : list) {
                double[] ab = unit(mid(t[0], t[1])), bc = unit(mid(t[1], t[2])), ca = unit(mid(t[2], t[0]));
                next.add(new double[][]{t[0], ab, ca});
                next.add(new double[][]{ab, t[1], bc});
                next.add(new double[][]{ca, bc, t[2]});
                next.add(new double[][]{ab, bc, ca});
            }
            list = next;
        }
        for (double[][] t : list) {
            for (int k = 0; k < 3; k++) {
                t[k] = unit(t[k]);
            }
        }
        return list;
    }

    private static double[] mid(double[] a, double[] b) {
        return new double[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2};
    }

    private static double[] unit(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return new double[]{a[0] / l, a[1] / l, a[2] / l};
    }

    /** The edges of the geodesic sphere as thin tubes of radius r. */
    static Mesh geodesicEdges(int freq, double r) {
        Mesh.B b = Mesh.b();
        Set<String> seen = new HashSet<>();
        for (double[][] t : tris(freq)) {
            for (int k = 0; k < 3; k++) {
                double[] p = t[k], q = t[(k + 1) % 3];
                String key = key(p, q);
                if (!seen.add(key)) {
                    continue;
                }
                double[] m = mid(p, q);
                b.add(Prim.tube(new double[][]{p, m, q}, new double[]{r, r, r}, 4, 1, false, false));
            }
        }
        return b.build();
    }

    private static String key(double[] p, double[] q) {
        String a = Math.round(p[0] * 1000) + "," + Math.round(p[1] * 1000) + "," + Math.round(p[2] * 1000);
        String c = Math.round(q[0] * 1000) + "," + Math.round(q[1] * 1000) + "," + Math.round(q[2] * 1000);
        return a.compareTo(c) < 0 ? a + "|" + c : c + "|" + a;
    }

    /** A jagged vertical tear in space: z from -0.5 to 0.5 (length 1), y from -1 to 1, in the plane x = 0. */
    static Mesh rift(long seed, int cols) {
        Mesh.B b = Mesh.b();
        int prevB = -1, prevT = -1;
        for (int k = 0; k <= cols; k++) {
            double u = k / (double) cols;
            double env = Math.pow(Math.sin(Math.PI * u), 0.7);
            double jag = k % 2 == 0 ? 1.0 : 0.78;
            double top = env * (0.7 + 0.3 * Noise.value(u * 9, seed, 1, (int) seed)) * jag;
            double bot = env * (0.7 + 0.3 * Noise.value(u * 9, seed, 5, (int) seed + 3)) * (k % 2 == 0 ? 0.8 : 1.0);
            int vb = b.v(0, -bot, u - 0.5, 1, 0, 0, u, 0);
            int vt = b.v(0, top, u - 0.5, 1, 0, 0, u, 1);
            if (prevB >= 0) {
                b.quad(prevB, vb, vt, prevT);
            }
            prevB = vb;
            prevT = vt;
        }
        return b.build();
    }

    /** A ring of n inward and downward pointing teeth on a circle of radius R. tilt = angle away from straight down (radians). */
    static Mesh mawRing(int n, double R, double len, double tilt) {
        Mesh.B b = Mesh.b();
        Mesh tooth = Prim.cone(0.075 * R / 1.0 * 0.5 + 0.03, len, 5);
        for (int k = 0; k < n; k++) {
            double a = Math.PI * 2 * k / n;
            b.add(tooth, Xf.rz(Math.PI - tilt).then(Xf.ry(-a)).then(Xf.t(R * Math.cos(a), 0, R * Math.sin(a))));
        }
        return b.build().flat();
    }

    /** Upper hemisphere of radius 1 (base on y = 0), meant to be seen from inside or outside. */
    static Mesh hemi(int lat, int lon) {
        double[][] pr = new double[lat + 1][2];
        for (int i = 0; i <= lat; i++) {
            double a = Math.PI / 2 * i / lat;
            pr[i][0] = Math.max(0.001, Math.cos(a));
            pr[i][1] = Math.sin(a);
        }
        return Prim.lathe(pr, lon, 0).smooth();
    }

    /** An open tube of radius 1, height 1 (v runs along the height). */
    static Mesh openTube(int seg) {
        return Prim.lathe(new double[][]{{1, 0}, {1, 1}}, seg, 0);
    }

    /** A capped cylinder, radius 1, height 1, along +Y: scaled into beams. */
    static Mesh beam() {
        return Prim.lathe(new double[][]{{0.001, 0}, {1, 0}, {1, 1}, {0.001, 1}}, 8, 0);
    }
}
