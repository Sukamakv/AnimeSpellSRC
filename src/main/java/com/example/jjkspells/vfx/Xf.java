package com.example.jjkspells.vfx;

/** A 3x4 affine transform (row major). Pure maths, no Minecraft classes. */
public final class Xf {
    final double[] m = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};

    private Xf() {
    }

    public static Xf id() {
        return new Xf();
    }

    public static Xf t(double x, double y, double z) {
        Xf f = new Xf();
        f.m[3] = x;
        f.m[7] = y;
        f.m[11] = z;
        return f;
    }

    public static Xf s(double x, double y, double z) {
        Xf f = new Xf();
        f.m[0] = x;
        f.m[5] = y;
        f.m[10] = z;
        return f;
    }

    public static Xf s(double k) {
        return s(k, k, k);
    }

    public static Xf rx(double a) {
        Xf f = new Xf();
        double c = Math.cos(a), s = Math.sin(a);
        f.m[5] = c;
        f.m[6] = -s;
        f.m[9] = s;
        f.m[10] = c;
        return f;
    }

    public static Xf ry(double a) {
        Xf f = new Xf();
        double c = Math.cos(a), s = Math.sin(a);
        f.m[0] = c;
        f.m[2] = s;
        f.m[8] = -s;
        f.m[10] = c;
        return f;
    }

    public static Xf rz(double a) {
        Xf f = new Xf();
        double c = Math.cos(a), s = Math.sin(a);
        f.m[0] = c;
        f.m[1] = -s;
        f.m[4] = s;
        f.m[5] = c;
        return f;
    }

    /** A frame with the columns x, y, z axes and origin o. */
    public static Xf frame(double[] x, double[] y, double[] z, double[] o) {
        Xf f = new Xf();
        f.m[0] = x[0]; f.m[1] = y[0]; f.m[2] = z[0]; f.m[3] = o[0];
        f.m[4] = x[1]; f.m[5] = y[1]; f.m[6] = z[1]; f.m[7] = o[1];
        f.m[8] = x[2]; f.m[9] = y[2]; f.m[10] = z[2]; f.m[11] = o[2];
        return f;
    }

    /** Apply this first, then o. */
    public Xf then(Xf o) {
        Xf r = new Xf();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 4; j++) {
                double s = 0;
                for (int k = 0; k < 3; k++) {
                    s += o.m[i * 4 + k] * m[k * 4 + j];
                }
                if (j == 3) {
                    s += o.m[i * 4 + 3];
                }
                r.m[i * 4 + j] = s;
            }
        }
        return r;
    }

    public void pt(double[] p) {
        double x = p[0], y = p[1], z = p[2];
        p[0] = m[0] * x + m[1] * y + m[2] * z + m[3];
        p[1] = m[4] * x + m[5] * y + m[6] * z + m[7];
        p[2] = m[8] * x + m[9] * y + m[10] * z + m[11];
    }

    /** Normals use the cofactor matrix so non-uniform scales stay correct. */
    public void nrm(double[] n) {
        double a = m[0], b = m[1], c = m[2], d = m[4], e = m[5], f = m[6], g = m[8], h = m[9], i = m[10];
        double c00 = e * i - f * h, c01 = -(d * i - f * g), c02 = d * h - e * g;
        double c10 = -(b * i - c * h), c11 = a * i - c * g, c12 = -(a * h - b * g);
        double c20 = b * f - c * e, c21 = -(a * f - c * d), c22 = a * e - b * d;
        double x = n[0], y = n[1], z = n[2];
        double nx = c00 * x + c10 * y + c20 * z, ny = c01 * x + c11 * y + c21 * z, nz = c02 * x + c12 * y + c22 * z;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-12) {
            l = 1;
        }
        n[0] = nx / l;
        n[1] = ny / l;
        n[2] = nz / l;
    }
}
