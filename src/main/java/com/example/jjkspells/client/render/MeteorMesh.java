package com.example.jjkspells.client.render;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shape of a Tengai Shinsei meteor: an icosphere whose vertices are pushed in and out by layered noise, so it
 * is a lumpy cauliflower-like boulder rather than a ball. Built once per variant (unit radius) and then scaled by
 * the renderer. Every triangle has its own three vertices, with smooth normals and a box-projected UV.
 */
final class MeteorMesh {
    private static final MeteorMesh[] CACHE = new MeteorMesh[2];

    final int triangles;
    /** per triangle corner: x,y,z */
    final float[] pos;
    /** per triangle corner: smooth normal */
    final float[] nrm;
    /** per triangle corner: u,v */
    final float[] uv;
    /** per triangle corner: a 0..1 noise used to make the glow patchy */
    final float[] patch;

    static synchronized MeteorMesh get(int variant) {
        int v = Math.floorMod(variant, CACHE.length);
        if (CACHE[v] == null) {
            CACHE[v] = new MeteorMesh(v);
        }
        return CACHE[v];
    }

    private MeteorMesh(int variant) {
        // ---- icosahedron --------------------------------------------------------------------------
        float t = (1f + (float) Math.sqrt(5.0)) / 2f;
        List<float[]> verts = new ArrayList<>();
        float[][] base = {
                {-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0},
                {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        for (float[] b : base) {
            verts.add(normalize(b));
        }
        List<int[]> faces = new ArrayList<>();
        int[][] f0 = {
                {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
                {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
                {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        for (int[] f : f0) {
            faces.add(f);
        }
        // ---- subdivide ----------------------------------------------------------------------------
        for (int level = 0; level < 4; level++) {
            Map<Long, Integer> mid = new HashMap<>();
            List<int[]> next = new ArrayList<>();
            for (int[] f : faces) {
                int a = midpoint(verts, mid, f[0], f[1]);
                int b = midpoint(verts, mid, f[1], f[2]);
                int c = midpoint(verts, mid, f[2], f[0]);
                next.add(new int[]{f[0], a, c});
                next.add(new int[]{f[1], b, a});
                next.add(new int[]{f[2], c, b});
                next.add(new int[]{a, b, c});
            }
            faces = next;
        }
        // ---- displace -------------------------------------------------------------------------------
        int n = verts.size();
        float[][] p = new float[n][3];
        float[] noise = new float[n];
        float ox = 11.3f * (variant + 1), oy = 7.7f * (variant + 2), oz = 3.1f * (variant + 3);
        for (int i = 0; i < n; i++) {
            float[] d = verts.get(i);
            float r = 1f;
            r += 0.070f * (vnoise(d[0] * 1.5f + ox, d[1] * 1.5f + oy, d[2] * 1.5f + oz, 1) * 2f - 1f);
            r += 0.040f * (vnoise(d[0] * 3.1f + oy, d[1] * 3.1f + oz, d[2] * 3.1f + ox, 2) * 2f - 1f);
            r += 0.045f * Math.abs(vnoise(d[0] * 4.6f + oz, d[1] * 4.6f + ox, d[2] * 4.6f + oy, 3) * 2f - 1f);
            r += 0.016f * (vnoise(d[0] * 9.5f + ox, d[1] * 9.5f + oz, d[2] * 9.5f + oy, 4) * 2f - 1f);
            r -= 0.02f;
            p[i][0] = d[0] * r;
            p[i][1] = d[1] * r;
            p[i][2] = d[2] * r;
            noise[i] = vnoise(d[0] * 5f + oy, d[1] * 5f + ox, d[2] * 5f + oz, 5);
        }
        // ---- smooth vertex normals (area weighted) -------------------------------------------------
        float[][] vn = new float[n][3];
        for (int[] f : faces) {
            float[] fn = faceNormal(p[f[0]], p[f[1]], p[f[2]]);
            for (int k = 0; k < 3; k++) {
                vn[f[k]][0] += fn[0];
                vn[f[k]][1] += fn[1];
                vn[f[k]][2] += fn[2];
            }
        }
        for (int i = 0; i < n; i++) {
            vn[i] = normalize(vn[i]);
        }
        // ---- expand to per-triangle vertices ---------------------------------------------------------
        this.triangles = faces.size();
        this.pos = new float[triangles * 9];
        this.nrm = new float[triangles * 9];
        this.uv = new float[triangles * 6];
        this.patch = new float[triangles * 3];
        final float tile = 2.7f;
        int ti = 0;
        for (int[] f : faces) {
            float[] fn = faceNormal(p[f[0]], p[f[1]], p[f[2]]);
            float ax = Math.abs(fn[0]), ay = Math.abs(fn[1]), az = Math.abs(fn[2]);
            for (int k = 0; k < 3; k++) {
                float[] q = p[f[k]];
                pos[ti * 9 + k * 3] = q[0];
                pos[ti * 9 + k * 3 + 1] = q[1];
                pos[ti * 9 + k * 3 + 2] = q[2];
                nrm[ti * 9 + k * 3] = vn[f[k]][0];
                nrm[ti * 9 + k * 3 + 1] = vn[f[k]][1];
                nrm[ti * 9 + k * 3 + 2] = vn[f[k]][2];
                float u, v;
                if (ay >= ax && ay >= az) {
                    u = q[0] * tile;
                    v = q[2] * tile;
                } else if (ax >= az) {
                    u = q[2] * tile;
                    v = q[1] * tile;
                } else {
                    u = q[0] * tile;
                    v = q[1] * tile;
                }
                uv[ti * 6 + k * 2] = u;
                uv[ti * 6 + k * 2 + 1] = v;
                patch[ti * 3 + k] = noise[f[k]];
            }
            ti++;
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    private static int midpoint(List<float[]> verts, Map<Long, Integer> cache, int a, int b) {
        long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
        Integer found = cache.get(key);
        if (found != null) {
            return found;
        }
        float[] va = verts.get(a), vb = verts.get(b);
        float[] m = normalize(new float[]{(va[0] + vb[0]) * 0.5f, (va[1] + vb[1]) * 0.5f, (va[2] + vb[2]) * 0.5f});
        verts.add(m);
        int idx = verts.size() - 1;
        cache.put(key, idx);
        return idx;
    }

    private static float[] normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1.0E-6f) {
            return new float[]{0, 1, 0};
        }
        return new float[]{v[0] / len, v[1] / len, v[2] / len};
    }

    /** Un-normalised cross product, so bigger triangles weigh more when summed; normalised only for the UV test. */
    private static float[] faceNormal(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        return new float[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }

    /** Smooth 3D value noise in 0..1. */
    private static float vnoise(float x, float y, float z, int seed) {
        int xi = Mth.floor(x), yi = Mth.floor(y), zi = Mth.floor(z);
        float fx = x - xi, fy = y - yi, fz = z - zi;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        fz = fz * fz * (3f - 2f * fz);
        float c000 = hash(xi, yi, zi, seed), c100 = hash(xi + 1, yi, zi, seed);
        float c010 = hash(xi, yi + 1, zi, seed), c110 = hash(xi + 1, yi + 1, zi, seed);
        float c001 = hash(xi, yi, zi + 1, seed), c101 = hash(xi + 1, yi, zi + 1, seed);
        float c011 = hash(xi, yi + 1, zi + 1, seed), c111 = hash(xi + 1, yi + 1, zi + 1, seed);
        float x00 = c000 + (c100 - c000) * fx, x10 = c010 + (c110 - c010) * fx;
        float x01 = c001 + (c101 - c001) * fx, x11 = c011 + (c111 - c011) * fx;
        float y0 = x00 + (x10 - x00) * fy, y1 = x01 + (x11 - x01) * fy;
        return y0 + (y1 - y0) * fz;
    }

    private static float hash(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 + z * 2147483647 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }
}
