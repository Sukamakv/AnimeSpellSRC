package com.example.jjkspells.vfx;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * An indexed triangle mesh (positions, normals, uvs). Pure Java so the geometry can be generated and tested anywhere;
 * {@link MeshRender} turns it into Minecraft vertices. Meshes are built once and cached, never per frame.
 */
public final class Mesh {
    public final float[] pos, nrm, uv;
    public final int[] idx;

    private Mesh(float[] p, float[] n, float[] u, int[] i) {
        pos = p;
        nrm = n;
        uv = u;
        idx = i;
    }

    public int vertexCount() {
        return pos.length / 3;
    }

    public int triCount() {
        return idx.length / 3;
    }

    // ------------------------------------------------------------------ builder

    public static final class B {
        private float[] p = new float[3 * 256], n = new float[3 * 256], t = new float[2 * 256];
        private int[] i = new int[3 * 256];
        private int vc, ic;

        public int v(double x, double y, double z, double nx, double ny, double nz, double u, double v) {
            if ((vc + 1) * 3 > p.length) {
                p = Arrays.copyOf(p, p.length * 2);
                n = Arrays.copyOf(n, n.length * 2);
                t = Arrays.copyOf(t, t.length * 2);
            }
            p[vc * 3] = (float) x;
            p[vc * 3 + 1] = (float) y;
            p[vc * 3 + 2] = (float) z;
            n[vc * 3] = (float) nx;
            n[vc * 3 + 1] = (float) ny;
            n[vc * 3 + 2] = (float) nz;
            t[vc * 2] = (float) u;
            t[vc * 2 + 1] = (float) v;
            return vc++;
        }

        public void tri(int a, int b, int c) {
            if (ic + 3 > i.length) {
                i = Arrays.copyOf(i, i.length * 2);
            }
            i[ic++] = a;
            i[ic++] = b;
            i[ic++] = c;
        }

        public void quad(int a, int b, int c, int d) {
            tri(a, b, c);
            tri(a, c, d);
        }

        public B add(Mesh m) {
            int o = vc;
            for (int k = 0; k < m.vertexCount(); k++) {
                v(m.pos[k * 3], m.pos[k * 3 + 1], m.pos[k * 3 + 2], m.nrm[k * 3], m.nrm[k * 3 + 1], m.nrm[k * 3 + 2], m.uv[k * 2], m.uv[k * 2 + 1]);
            }
            for (int k = 0; k < m.idx.length; k += 3) {
                tri(o + m.idx[k], o + m.idx[k + 1], o + m.idx[k + 2]);
            }
            return this;
        }

        public B add(Mesh m, Xf xf) {
            return add(m.xf(xf));
        }

        public Mesh build() {
            return new Mesh(Arrays.copyOf(p, vc * 3), Arrays.copyOf(n, vc * 3), Arrays.copyOf(t, vc * 2), Arrays.copyOf(i, ic));
        }
    }

    public static B b() {
        return new B();
    }

    public static Mesh merge(Mesh... ms) {
        B b = b();
        for (Mesh m : ms) {
            b.add(m);
        }
        return b.build();
    }

    // ------------------------------------------------------------------ operations (all return new meshes)

    public Mesh xf(Xf f) {
        float[] p = new float[pos.length], n = new float[nrm.length];
        double[] a = new double[3], b = new double[3];
        for (int k = 0; k < vertexCount(); k++) {
            a[0] = pos[k * 3];
            a[1] = pos[k * 3 + 1];
            a[2] = pos[k * 3 + 2];
            f.pt(a);
            p[k * 3] = (float) a[0];
            p[k * 3 + 1] = (float) a[1];
            p[k * 3 + 2] = (float) a[2];
            b[0] = nrm[k * 3];
            b[1] = nrm[k * 3 + 1];
            b[2] = nrm[k * 3 + 2];
            f.nrm(b);
            n[k * 3] = (float) b[0];
            n[k * 3 + 1] = (float) b[1];
            n[k * 3 + 2] = (float) b[2];
        }
        return new Mesh(p, n, uv.clone(), idx.clone());
    }

    public Mesh scaleUv(double su, double sv) {
        float[] t = uv.clone();
        for (int k = 0; k < t.length; k += 2) {
            t[k] *= (float) su;
            t[k + 1] *= (float) sv;
        }
        return new Mesh(pos.clone(), nrm.clone(), t, idx.clone());
    }

    /** One vertex per triangle corner with the face normal: crisp facets. */
    public Mesh flat() {
        B b = b();
        for (int k = 0; k < idx.length; k += 3) {
            int a = idx[k], c = idx[k + 1], d = idx[k + 2];
            double[] fn = faceNormal(a, c, d);
            int i0 = b.v(pos[a * 3], pos[a * 3 + 1], pos[a * 3 + 2], fn[0], fn[1], fn[2], uv[a * 2], uv[a * 2 + 1]);
            int i1 = b.v(pos[c * 3], pos[c * 3 + 1], pos[c * 3 + 2], fn[0], fn[1], fn[2], uv[c * 2], uv[c * 2 + 1]);
            int i2 = b.v(pos[d * 3], pos[d * 3 + 1], pos[d * 3 + 2], fn[0], fn[1], fn[2], uv[d * 2], uv[d * 2 + 1]);
            b.tri(i0, i1, i2);
        }
        return b.build();
    }

    private double[] faceNormal(int a, int b, int c) {
        double ux = pos[b * 3] - pos[a * 3], uy = pos[b * 3 + 1] - pos[a * 3 + 1], uz = pos[b * 3 + 2] - pos[a * 3 + 2];
        double vx = pos[c * 3] - pos[a * 3], vy = pos[c * 3 + 1] - pos[a * 3 + 1], vz = pos[c * 3 + 2] - pos[a * 3 + 2];
        double x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
        double l = Math.sqrt(x * x + y * y + z * z);
        if (l < 1e-12) {
            return new double[]{0, 1, 0};
        }
        return new double[]{x / l, y / l, z / l};
    }

    /** Recomputes normals as the area weighted average over every vertex sharing a position (closes seams). */
    public Mesh smooth() {
        Map<Long, double[]> acc = new HashMap<>();
        for (int k = 0; k < idx.length; k += 3) {
            int a = idx[k], b = idx[k + 1], c = idx[k + 2];
            double ux = pos[b * 3] - pos[a * 3], uy = pos[b * 3 + 1] - pos[a * 3 + 1], uz = pos[b * 3 + 2] - pos[a * 3 + 2];
            double vx = pos[c * 3] - pos[a * 3], vy = pos[c * 3 + 1] - pos[a * 3 + 1], vz = pos[c * 3 + 2] - pos[a * 3 + 2];
            double x = uy * vz - uz * vy, y = uz * vx - ux * vz, z = ux * vy - uy * vx;
            for (int v : new int[]{a, b, c}) {
                double[] s = acc.computeIfAbsent(key(v), q -> new double[3]);
                s[0] += x;
                s[1] += y;
                s[2] += z;
            }
        }
        float[] n = new float[nrm.length];
        for (int k = 0; k < vertexCount(); k++) {
            double[] s = acc.get(key(k));
            double l = s == null ? 0 : Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
            if (l < 1e-12) {
                n[k * 3] = nrm[k * 3];
                n[k * 3 + 1] = nrm[k * 3 + 1];
                n[k * 3 + 2] = nrm[k * 3 + 2];
            } else {
                n[k * 3] = (float) (s[0] / l);
                n[k * 3 + 1] = (float) (s[1] / l);
                n[k * 3 + 2] = (float) (s[2] / l);
            }
        }
        return new Mesh(pos.clone(), n, uv.clone(), idx.clone());
    }

    private long key(int v) {
        long x = Math.round(pos[v * 3] * 2000.0), y = Math.round(pos[v * 3 + 1] * 2000.0), z = Math.round(pos[v * 3 + 2] * 2000.0);
        return (x & 0x1FFFFFL) | ((y & 0x1FFFFFL) << 21) | ((z & 0x1FFFFFL) << 42);
    }

    /** Moves vertices by a position-hashed offset so shared corners stay together. */
    public Mesh jitter(double amp, int seed) {
        float[] p = pos.clone();
        for (int k = 0; k < vertexCount(); k++) {
            long key = key(k);
            int h1 = (int) (key ^ (key >>> 29)), h2 = (int) ((key >>> 11) ^ seed * 31L), h3 = (int) ((key >>> 5) * 7 + seed);
            p[k * 3] += (float) ((Noise.hash(h1, seed) - 0.5) * 2 * amp);
            p[k * 3 + 1] += (float) ((Noise.hash(h2, seed + 1) - 0.5) * 2 * amp);
            p[k * 3 + 2] += (float) ((Noise.hash(h3, seed + 2) - 0.5) * 2 * amp);
        }
        return new Mesh(p, nrm.clone(), uv.clone(), idx.clone());
    }

    /** Moves each vertex along its direction from the origin by f(direction). */
    public Mesh radial(java.util.function.ToDoubleFunction<double[]> f) {
        float[] p = pos.clone();
        double[] d = new double[3];
        for (int k = 0; k < vertexCount(); k++) {
            double x = pos[k * 3], y = pos[k * 3 + 1], z = pos[k * 3 + 2];
            double l = Math.sqrt(x * x + y * y + z * z);
            if (l < 1e-9) {
                continue;
            }
            d[0] = x / l;
            d[1] = y / l;
            d[2] = z / l;
            double s = f.applyAsDouble(d);
            p[k * 3] = (float) (x * s);
            p[k * 3 + 1] = (float) (y * s);
            p[k * 3 + 2] = (float) (z * s);
        }
        return new Mesh(p, nrm.clone(), uv.clone(), idx.clone());
    }

    /** Warps y-dependent twist / bend: rotates around the Y axis by f(y) radians. */
    public Mesh twist(DoubleUnaryOperator f) {
        float[] p = pos.clone(), n = nrm.clone();
        for (int k = 0; k < vertexCount(); k++) {
            double a = f.applyAsDouble(pos[k * 3 + 1]), c = Math.cos(a), s = Math.sin(a);
            double x = pos[k * 3], z = pos[k * 3 + 2];
            p[k * 3] = (float) (x * c - z * s);
            p[k * 3 + 2] = (float) (x * s + z * c);
            double nx = nrm[k * 3], nz = nrm[k * 3 + 2];
            n[k * 3] = (float) (nx * c - nz * s);
            n[k * 3 + 2] = (float) (nx * s + nz * c);
        }
        return new Mesh(p, n, uv.clone(), idx.clone());
    }

    public double[] bounds() {
        double[] b = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9};
        for (int k = 0; k < vertexCount(); k++) {
            for (int a = 0; a < 3; a++) {
                b[a] = Math.min(b[a], pos[k * 3 + a]);
                b[a + 3] = Math.max(b[a + 3], pos[k * 3 + a]);
            }
        }
        return b;
    }

    public String toObj() {
        StringBuilder s = new StringBuilder();
        for (int k = 0; k < vertexCount(); k++) {
            s.append(String.format(java.util.Locale.ROOT, "v %.5f %.5f %.5f\n", pos[k * 3], pos[k * 3 + 1], pos[k * 3 + 2]));
        }
        for (int k = 0; k < vertexCount(); k++) {
            s.append(String.format(java.util.Locale.ROOT, "vn %.4f %.4f %.4f\n", nrm[k * 3], nrm[k * 3 + 1], nrm[k * 3 + 2]));
        }
        for (int k = 0; k < idx.length; k += 3) {
            s.append(String.format("f %d//%d %d//%d %d//%d\n", idx[k] + 1, idx[k] + 1, idx[k + 1] + 1, idx[k + 1] + 1, idx[k + 2] + 1, idx[k + 2] + 1));
        }
        return s.toString();
    }
}
