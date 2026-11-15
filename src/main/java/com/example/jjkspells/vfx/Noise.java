package com.example.jjkspells.vfx;

/** Small deterministic value noise. */
public final class Noise {
    private Noise() {
    }

    private static double h(int x, int y, int z, int seed) {
        long n = x * 374761393L + y * 668265263L + z * 2147483647L + seed * 1274126177L;
        n = (n ^ (n >>> 13)) * 1274126177L;
        n ^= n >>> 16;
        return (n & 0xFFFFFFL) / (double) 0xFFFFFFL;
    }

    private static double sm(double t) {
        return t * t * (3 - 2 * t);
    }

    public static double value(double x, double y, double z, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double fx = sm(x - xi), fy = sm(y - yi), fz = sm(z - zi);
        double c000 = h(xi, yi, zi, seed), c100 = h(xi + 1, yi, zi, seed), c010 = h(xi, yi + 1, zi, seed), c110 = h(xi + 1, yi + 1, zi, seed);
        double c001 = h(xi, yi, zi + 1, seed), c101 = h(xi + 1, yi, zi + 1, seed), c011 = h(xi, yi + 1, zi + 1, seed), c111 = h(xi + 1, yi + 1, zi + 1, seed);
        double x00 = c000 + (c100 - c000) * fx, x10 = c010 + (c110 - c010) * fx, x01 = c001 + (c101 - c001) * fx, x11 = c011 + (c111 - c011) * fx;
        double y0 = x00 + (x10 - x00) * fy, y1 = x01 + (x11 - x01) * fy;
        return y0 + (y1 - y0) * fz;
    }

    /** Fractal noise in 0..1. */
    public static double fbm(double x, double y, double z, int seed, int oct) {
        double s = 0, a = 0.5, n = 0;
        for (int i = 0; i < oct; i++) {
            s += a * value(x, y, z, seed + i * 31);
            n += a;
            a *= 0.5;
            x *= 2.03;
            y *= 2.03;
            z *= 2.03;
        }
        return s / n;
    }

    public static double hash(int a, int b) {
        return h(a, b, 7, 1);
    }
}
