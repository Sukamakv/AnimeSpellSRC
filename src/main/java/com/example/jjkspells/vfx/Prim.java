package com.example.jjkspells.vfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

/** Primitive solids. Y is up unless stated. All are unit sized and meant to be scaled/rotated with {@link Xf}. */
public final class Prim {
    private Prim() {
    }

    private static double[] norm(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        if (l < 1e-12) {
            return new double[]{0, 1, 0};
        }
        return new double[]{x / l, y / l, z / l};
    }

    /** UV sphere, radius r, poles on the Y axis. */
    public static Mesh sphere(double r, int lat, int lon) {
        Mesh.B b = Mesh.b();
        for (int i = 0; i <= lat; i++) {
            double ph = Math.PI * i / lat, y = Math.cos(ph), rr = Math.sin(ph);
            for (int j = 0; j <= lon; j++) {
                double th = Math.PI * 2 * j / lon, x = rr * Math.cos(th), z = rr * Math.sin(th);
                b.v(x * r, y * r, z * r, x, y, z, j / (double) lon, i / (double) lat);
            }
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                int a = i * (lon + 1) + j, c = a + lon + 1;
                b.quad(a, c, c + 1, a + 1);
            }
        }
        return b.build();
    }

    /** A lumpy planet/star/boulder: the sphere displaced by fractal noise. */
    public static Mesh lumpy(double r, int lat, int lon, double amp, double freq, int seed) {
        return sphere(1, lat, lon).radial(d -> r * (1 + amp * (Noise.fbm(d[0] * freq + 11, d[1] * freq + 5, d[2] * freq + 3, seed, 4) - 0.5) * 2)).smooth();
    }

    /** Surface of revolution around Y. profile = {radius, y} from bottom to top. twist = total twist in radians. */
    public static Mesh lathe(double[][] pr, int seg, double twist) {
        Mesh.B b = Mesh.b();
        int n = pr.length;
        double total = 0;
        double[] cum = new double[n];
        for (int k = 1; k < n; k++) {
            total += Math.hypot(pr[k][0] - pr[k - 1][0], pr[k][1] - pr[k - 1][1]);
            cum[k] = total;
        }
        if (total < 1e-9) {
            total = 1;
        }
        for (int k = 0; k < n; k++) {
            int a = Math.max(0, k - 1), c = Math.min(n - 1, k + 1);
            double dr = pr[c][0] - pr[a][0], dy = pr[c][1] - pr[a][1];
            double nl = Math.hypot(dr, dy);
            if (nl < 1e-12) {
                nl = 1;
            }
            double nr = dy / nl, ny = -dr / nl;
            double tw = n > 1 ? twist * k / (n - 1) : 0;
            for (int j = 0; j <= seg; j++) {
                double th = Math.PI * 2 * j / seg + tw, cs = Math.cos(th), sn = Math.sin(th);
                b.v(pr[k][0] * cs, pr[k][1], pr[k][0] * sn, nr * cs, ny, nr * sn, j / (double) seg, cum[k] / total);
            }
        }
        for (int k = 0; k < n - 1; k++) {
            for (int j = 0; j < seg; j++) {
                int a = k * (seg + 1) + j, c = a + seg + 1;
                b.quad(a, c, c + 1, a + 1);
            }
        }
        return b.build();
    }

    /** A swept tube along a path with a radius per point (parallel transport frames). vPerUnit repeats the texture along the length. */
    public static Mesh tube(double[][] path, double[] radius, int seg, double vPerUnit, boolean capStart, boolean capEnd) {
        int n = path.length;
        double[][] t = new double[n][];
        for (int k = 0; k < n; k++) {
            int a = Math.max(0, k - 1), c = Math.min(n - 1, k + 1);
            t[k] = norm(path[c][0] - path[a][0], path[c][1] - path[a][1], path[c][2] - path[a][2]);
        }
        double[] nv = Math.abs(t[0][1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double d = nv[0] * t[0][0] + nv[1] * t[0][1] + nv[2] * t[0][2];
        nv = norm(nv[0] - d * t[0][0], nv[1] - d * t[0][1], nv[2] - d * t[0][2]);
        Mesh.B b = Mesh.b();
        double len = 0;
        double[][] ns = new double[n][], bs = new double[n][];
        for (int k = 0; k < n; k++) {
            if (k > 0) {
                double dd = nv[0] * t[k][0] + nv[1] * t[k][1] + nv[2] * t[k][2];
                nv = norm(nv[0] - dd * t[k][0], nv[1] - dd * t[k][1], nv[2] - dd * t[k][2]);
                len += Math.sqrt(sq(path[k][0] - path[k - 1][0]) + sq(path[k][1] - path[k - 1][1]) + sq(path[k][2] - path[k - 1][2]));
            }
            double[] bv = {t[k][1] * nv[2] - t[k][2] * nv[1], t[k][2] * nv[0] - t[k][0] * nv[2], t[k][0] * nv[1] - t[k][1] * nv[0]};
            ns[k] = nv.clone();
            bs[k] = bv;
            for (int j = 0; j <= seg; j++) {
                double th = Math.PI * 2 * j / seg, c = Math.cos(th), s = Math.sin(th);
                double x = c * nv[0] + s * bv[0], y = c * nv[1] + s * bv[1], z = c * nv[2] + s * bv[2];
                b.v(path[k][0] + x * radius[k], path[k][1] + y * radius[k], path[k][2] + z * radius[k], x, y, z, j / (double) seg, len * vPerUnit);
            }
        }
        for (int k = 0; k < n - 1; k++) {
            for (int j = 0; j < seg; j++) {
                int a = k * (seg + 1) + j, c = a + seg + 1;
                b.quad(a, c, c + 1, a + 1);
            }
        }
        for (int e = 0; e < 2; e++) {
            if (e == 0 ? !capStart : !capEnd) {
                continue;
            }
            int k = e == 0 ? 0 : n - 1;
            double sgn = e == 0 ? -1 : 1;
            int c0 = b.v(path[k][0], path[k][1], path[k][2], t[k][0] * sgn, t[k][1] * sgn, t[k][2] * sgn, 0.5, 0.5);
            int first = -1, prev = -1;
            for (int j = 0; j <= seg; j++) {
                double th = Math.PI * 2 * j / seg, c = Math.cos(th), s = Math.sin(th);
                double x = c * ns[k][0] + s * bs[k][0], y = c * ns[k][1] + s * bs[k][1], z = c * ns[k][2] + s * bs[k][2];
                int v = b.v(path[k][0] + x * radius[k], path[k][1] + y * radius[k], path[k][2] + z * radius[k], t[k][0] * sgn, t[k][1] * sgn, t[k][2] * sgn, 0.5 + c * 0.5, 0.5 + s * 0.5);
                if (prev >= 0) {
                    b.tri(c0, prev, v);
                }
                prev = v;
                if (first < 0) {
                    first = v;
                }
            }
        }
        return b.build();
    }

    private static double sq(double v) {
        return v * v;
    }

    public static Mesh torus(double R, double r, int segR, int segr) {
        Mesh.B b = Mesh.b();
        for (int i = 0; i <= segR; i++) {
            double a = Math.PI * 2 * i / segR, ca = Math.cos(a), sa = Math.sin(a);
            for (int j = 0; j <= segr; j++) {
                double c = Math.PI * 2 * j / segr, cc = Math.cos(c), sc = Math.sin(c);
                b.v((R + r * cc) * ca, r * sc, (R + r * cc) * sa, cc * ca, sc, cc * sa, i / (double) segR, j / (double) segr);
            }
        }
        for (int i = 0; i < segR; i++) {
            for (int j = 0; j < segr; j++) {
                int a = i * (segr + 1) + j, c = a + segr + 1;
                b.quad(a, c, c + 1, a + 1);
            }
        }
        return b.build();
    }

    /** Flat ring in the XZ plane facing +Y. u = angle, v = 0 at the inner edge to 1 at the outer. */
    public static Mesh disc(double rIn, double rOut, int seg, int rings) {
        Mesh.B b = Mesh.b();
        for (int i = 0; i <= rings; i++) {
            double f = i / (double) rings, r = rIn + (rOut - rIn) * f;
            for (int j = 0; j <= seg; j++) {
                double th = Math.PI * 2 * j / seg;
                b.v(r * Math.cos(th), 0, r * Math.sin(th), 0, 1, 0, j / (double) seg, f);
            }
        }
        for (int i = 0; i < rings; i++) {
            for (int j = 0; j < seg; j++) {
                int a = i * (seg + 1) + j, c = a + seg + 1;
                b.quad(a, c, c + 1, a + 1);
            }
        }
        return b.build();
    }

    public static Mesh cone(double r, double h, int seg) {
        return lathe(new double[][]{{0, 0}, {r, 0}, {0, h}}, seg, 0);
    }

    public static Mesh cylinder(double r, double h, int seg) {
        return lathe(new double[][]{{0, 0}, {r, 0}, {r, h}, {0, h}}, seg, 0);
    }

    public static Mesh box(double hx, double hy, double hz) {
        Mesh.B b = Mesh.b();
        double[][] ns = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (double[] n : ns) {
            double[] u = n[1] != 0 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
            double[] v = {n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0]};
            double[] h = {hx, hy, hz};
            int[] q = new int[4];
            double[][] c = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int k = 0; k < 4; k++) {
                double x = (n[0] + u[0] * c[k][0] + v[0] * c[k][1]) * h[0], y = (n[1] + u[1] * c[k][0] + v[1] * c[k][1]) * h[1], z = (n[2] + u[2] * c[k][0] + v[2] * c[k][1]) * h[2];
                q[k] = b.v(x, y, z, n[0], n[1], n[2], (c[k][0] + 1) / 2, (c[k][1] + 1) / 2);
            }
            b.quad(q[0], q[1], q[2], q[3]);
        }
        return b.build();
    }

    /** A crystal: pointed pillar with the given number of sides, flat shaded. */
    public static Mesh crystal(double r, double h, int sides, double tipTop, double tipBot) {
        return lathe(new double[][]{{0, -tipBot}, {r, 0}, {r, h}, {0, h + tipTop}}, sides, 0).flat();
    }

    /** A jagged shard from y=0 to y=len, flat shaded, deterministic by seed. */
    public static Mesh shard(long seed, double len, double w) {
        Random rnd = new Random(seed);
        double[][] pr = {{0, 0}, {w, 0.02 * len}, {w * (0.7 + 0.4 * rnd.nextDouble()), len * 0.45}, {w * 0.35, len * 0.8}, {0, len}};
        return lathe(pr, 4 + rnd.nextInt(2), 0).jitter(w * 0.18, (int) seed).flat();
    }

    // ------------------------------------------------------------------ regular polyhedra (flat shaded, circumradius 1)

    public enum Poly {TETRA, CUBE, OCTA, ICOSA, DODECA}

    public static Mesh poly(Poly kind) {
        List<double[]> v = new ArrayList<>();
        List<int[]> f = new ArrayList<>();
        double phi = (1 + Math.sqrt(5)) / 2;
        switch (kind) {
            case TETRA -> {
                v.add(norm(1, 1, 1));
                v.add(norm(1, -1, -1));
                v.add(norm(-1, 1, -1));
                v.add(norm(-1, -1, 1));
                f.add(new int[]{0, 1, 2});
                f.add(new int[]{0, 3, 1});
                f.add(new int[]{0, 2, 3});
                f.add(new int[]{1, 3, 2});
            }
            case CUBE -> {
                for (int i = 0; i < 8; i++) {
                    v.add(norm((i & 1) == 0 ? -1 : 1, (i & 2) == 0 ? -1 : 1, (i & 4) == 0 ? -1 : 1));
                }
                int[][] q = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
                f.addAll(List.of(q));
            }
            case OCTA -> {
                v.add(new double[]{1, 0, 0});
                v.add(new double[]{-1, 0, 0});
                v.add(new double[]{0, 1, 0});
                v.add(new double[]{0, -1, 0});
                v.add(new double[]{0, 0, 1});
                v.add(new double[]{0, 0, -1});
                int[][] q = {{0, 2, 4}, {2, 1, 4}, {1, 3, 4}, {3, 0, 4}, {2, 0, 5}, {1, 2, 5}, {3, 1, 5}, {0, 3, 5}};
                f.addAll(List.of(q));
            }
            default -> {
                double[][] ic = {{0, 1, phi}, {0, -1, phi}, {0, 1, -phi}, {0, -1, -phi}, {1, phi, 0}, {-1, phi, 0}, {1, -phi, 0}, {-1, -phi, 0}, {phi, 0, 1}, {phi, 0, -1}, {-phi, 0, 1}, {-phi, 0, -1}};
                List<double[]> iv = new ArrayList<>();
                for (double[] p : ic) {
                    iv.add(norm(p[0], p[1], p[2]));
                }
                List<int[]> ifaces = new ArrayList<>();
                double edge = 2.0 / Math.sqrt(1 + phi * phi);
                for (int a = 0; a < 12; a++) {
                    for (int b2 = a + 1; b2 < 12; b2++) {
                        for (int c = b2 + 1; c < 12; c++) {
                            if (Math.abs(dist(iv.get(a), iv.get(b2)) - edge) < 1e-6 && Math.abs(dist(iv.get(b2), iv.get(c)) - edge) < 1e-6 && Math.abs(dist(iv.get(a), iv.get(c)) - edge) < 1e-6) {
                                ifaces.add(new int[]{a, b2, c});
                            }
                        }
                    }
                }
                if (kind == Poly.ICOSA) {
                    v.addAll(iv);
                    for (int[] t : ifaces) {
                        double[] c = {0, 0, 0};
                        for (int k : t) {
                            for (int a = 0; a < 3; a++) {
                                c[a] += iv.get(k)[a];
                            }
                        }
                        double[] a0 = iv.get(t[0]), a1 = iv.get(t[1]), a2 = iv.get(t[2]);
                        double[] nn = cross(sub(a1, a0), sub(a2, a0));
                        f.add(dot(nn, c) >= 0 ? t : new int[]{t[0], t[2], t[1]});
                    }
                } else {
                    // dodecahedron: vertices are the face centres of the icosahedron, faces are around each icosahedron vertex
                    List<double[]> cs = new ArrayList<>();
                    for (int[] t : ifaces) {
                        cs.add(norm(iv.get(t[0])[0] + iv.get(t[1])[0] + iv.get(t[2])[0], iv.get(t[0])[1] + iv.get(t[1])[1] + iv.get(t[2])[1], iv.get(t[0])[2] + iv.get(t[1])[2] + iv.get(t[2])[2]));
                    }
                    v.addAll(cs);
                    for (int k = 0; k < 12; k++) {
                        List<Integer> around = new ArrayList<>();
                        for (int q = 0; q < ifaces.size(); q++) {
                            for (int x : ifaces.get(q)) {
                                if (x == k) {
                                    around.add(q);
                                }
                            }
                        }
                        double[] axis = iv.get(k);
                        double[] u = norm(sub(cs.get(around.get(0)), mul(axis, dot(cs.get(around.get(0)), axis))));
                        double[] w = cross(axis, u);
                        around.sort((p, q2) -> Double.compare(Math.atan2(dot(cs.get(p), w), dot(cs.get(p), u)), Math.atan2(dot(cs.get(q2), w), dot(cs.get(q2), u))));
                        int[] face = new int[around.size()];
                        for (int q = 0; q < face.length; q++) {
                            face[q] = around.get(q);
                        }
                        f.add(face);
                    }
                }
            }
        }
        return polyMesh(v, f);
    }

    private static double[] norm(double[] a) {
        return norm(a[0], a[1], a[2]);
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2]));
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] mul(double[] a, double k) {
        return new double[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static Mesh polyMesh(List<double[]> v, List<int[]> faces) {
        Mesh.B b = Mesh.b();
        for (int[] f : faces) {
            double[] c = {0, 0, 0};
            for (int k : f) {
                for (int a = 0; a < 3; a++) {
                    c[a] += v.get(k)[a] / f.length;
                }
            }
            double[] n = norm(c);
            double[] u = norm(sub(v.get(f[0]), mul(n, dot(v.get(f[0]), n))));
            double[] w = cross(n, u);
            double scale = 0.0;
            for (int k : f) {
                scale = Math.max(scale, dist(v.get(k), c));
            }
            int[] ix = new int[f.length];
            for (int q = 0; q < f.length; q++) {
                double[] p = sub(v.get(f[q]), c);
                ix[q] = b.v(v.get(f[q])[0], v.get(f[q])[1], v.get(f[q])[2], n[0], n[1], n[2], 0.5 + dot(p, u) / scale * 0.5, 0.5 + dot(p, w) / scale * 0.5);
            }
            for (int q = 1; q < f.length - 1; q++) {
                // wind so the face normal points outwards
                double[] a0 = v.get(f[0]), a1 = v.get(f[q]), a2 = v.get(f[q + 1]);
                if (dot(cross(sub(a1, a0), sub(a2, a0)), n) >= 0) {
                    b.tri(ix[0], ix[q], ix[q + 1]);
                } else {
                    b.tri(ix[0], ix[q + 1], ix[q]);
                }
            }
        }
        return b.build();
    }

    // ------------------------------------------------------------------ organic shapes

    /** A curling tentacle / horn / tendril: starts at the origin heading +Y, curls by 'curl' radians in total, with noise. */
    public static Mesh tentacle(long seed, double len, double r0, double curl, int steps, int seg) {
        Random rnd = new Random(seed);
        double[][] path = new double[steps + 1][3];
        double[] rad = new double[steps + 1];
        double x = 0, y = 0, z = 0, ang = 0, tw = rnd.nextDouble() * 6.28, wob = rnd.nextDouble() * 6.28;
        double step = len / steps;
        double dirx = 0, diry = 1, dirz = 0;
        for (int k = 0; k <= steps; k++) {
            path[k][0] = x;
            path[k][1] = y;
            path[k][2] = z;
            double u = k / (double) steps;
            rad[k] = r0 * Math.pow(1 - u, 0.8) + r0 * 0.04;
            ang = curl * u * u;
            double sw = Math.sin(ang), cw = Math.cos(ang);
            double wobble = 0.35 * Math.sin(wob + u * 9) * (0.3 + u);
            dirx = sw * Math.cos(tw + wobble);
            diry = cw;
            dirz = sw * Math.sin(tw + wobble);
            x += dirx * step;
            y += diry * step;
            z += dirz * step;
        }
        return tube(path, rad, seg, 1.0 / Math.max(0.1, len) * 3, false, false).smooth();
    }

    /** A jagged bolt from a to b (midpoint displacement) with radius r and a few forks. */
    public static Mesh lightning(long seed, double[] a, double[] b, int levels, double jag, double r, int forks) {
        Random rnd = new Random(seed);
        List<double[]> pts = new ArrayList<>();
        pts.add(a);
        pts.add(b);
        double j = jag;
        for (int l = 0; l < levels; l++) {
            List<double[]> nx = new ArrayList<>();
            for (int k = 0; k < pts.size() - 1; k++) {
                double[] p = pts.get(k), q = pts.get(k + 1);
                nx.add(p);
                nx.add(new double[]{(p[0] + q[0]) / 2 + (rnd.nextDouble() - 0.5) * j, (p[1] + q[1]) / 2 + (rnd.nextDouble() - 0.5) * j, (p[2] + q[2]) / 2 + (rnd.nextDouble() - 0.5) * j});
            }
            nx.add(pts.get(pts.size() - 1));
            pts = nx;
            j *= 0.5;
        }
        int n = pts.size();
        double[][] path = pts.toArray(new double[0][]);
        double[] rad = new double[n];
        for (int k = 0; k < n; k++) {
            rad[k] = r * (0.35 + 0.65 * Math.sin(Math.PI * (k + 0.5) / n)) ;
        }
        Mesh.B mb = Mesh.b();
        mb.add(tube(path, rad, 5, 0.5, false, false).smooth());
        for (int f = 0; f < forks; f++) {
            int s = 2 + rnd.nextInt(Math.max(1, n - 6));
            double[] o = path[s], d = new double[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]};
            double L = Math.sqrt(dist2(d)) * (0.15 + 0.2 * rnd.nextDouble());
            double[] e = {o[0] + d[0] * 0.15 + (rnd.nextDouble() - 0.5) * L * 2, o[1] + d[1] * 0.2 + (rnd.nextDouble() - 0.5) * L, o[2] + d[2] * 0.15 + (rnd.nextDouble() - 0.5) * L * 2};
            mb.add(lightning(seed * 31 + f, o, e, Math.max(2, levels - 2), jag * 0.35, r * 0.5, 0));
        }
        return mb.build();
    }

    private static double dist2(double[] d) {
        return d[0] * d[0] + d[1] * d[1] + d[2] * d[2];
    }

    /** A cumulus-like mass: overlapping lumpy balls, flat underside. Radius about r. */
    public static Mesh cloud(long seed, double r, int blobs) {
        Random rnd = new Random(seed);
        Mesh.B b = Mesh.b();
        for (int i = 0; i < blobs; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r * 0.65;
            double br = r * (0.3 + 0.35 * rnd.nextDouble()) * (1 - 0.5 * d / r);
            double y = br * 0.35 * rnd.nextDouble() + (1 - d / r) * r * 0.25;
            Mesh s = lumpy(br, 10, 14, 0.28, 2.2, (int) seed + i * 13).xf(Xf.s(1, 0.72, 1).then(Xf.t(Math.cos(a) * d, y, Math.sin(a) * d)));
            b.add(s);
        }
        return b.build();
    }

    /** A funnel / vortex wall: radius r(y) over height h (y 0..1 -> radius), with twist and noise. */
    public static Mesh funnel(double h, DoubleUnaryOperator radius, int seg, int rings, double twist, double noise, int seed) {
        double[][] pr = new double[rings + 1][2];
        for (int k = 0; k <= rings; k++) {
            double u = k / (double) rings;
            double nz = noise * (Noise.fbm(u * 5, seed, 0, seed, 3) - 0.5) * 2;
            pr[k][0] = Math.max(0.001, radius.applyAsDouble(u) * (1 + nz));
            pr[k][1] = u * h;
        }
        return lathe(pr, seg, twist).smooth();
    }

    /** n stacked, spaced rings (a banded column), handy for gates and cages. */
    public static Mesh rings(int n, double R, double r, double spacing) {
        Mesh.B b = Mesh.b();
        for (int i = 0; i < n; i++) {
            b.add(torus(R, r, 40, 8), Xf.t(0, i * spacing, 0));
        }
        return b.build();
    }
}
