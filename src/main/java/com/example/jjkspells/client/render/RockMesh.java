package com.example.jjkspells.client.render;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A jagged, flat-shaded chunk of volcanic rock (a low-detail icosphere shoved around by noise). Cheap enough to
 * draw dozens of per frame. Triangles are stored per corner; {@code glow} marks the corners that sit on a molten
 * crack so the renderer can light them up.
 */
final class RockMesh {
    private static final Map<Integer, RockMesh> CACHE = new HashMap<>();

    final int triangles;
    final float[] pos;   // 9 per triangle
    final float[] nrm;   // 9 per triangle (flat)
    final float[] uv;    // 6 per triangle
    final float[] glow;  // 3 per triangle, 0..1

    static synchronized RockMesh get(int seed) {
        return CACHE.computeIfAbsent(Math.floorMod(seed, 6), RockMesh::new);
    }

    private RockMesh(int seed) {
        float t = (1f + (float) Math.sqrt(5.0)) / 2f;
        List<float[]> verts = new ArrayList<>();
        float[][] base = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        for (float[] b : base) verts.add(norm(b));
        int[][] f0 = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        List<int[]> faces = new ArrayList<>(List.of(f0));
        for (int level = 0; level < 2; level++) {
            Map<Long, Integer> mid = new HashMap<>();
            List<int[]> next = new ArrayList<>();
            for (int[] f : faces) {
                int a = mid(verts, mid, f[0], f[1]), b = mid(verts, mid, f[1], f[2]), c = mid(verts, mid, f[2], f[0]);
                next.add(new int[]{f[0], a, c});
                next.add(new int[]{f[1], b, a});
                next.add(new int[]{f[2], c, b});
                next.add(new int[]{a, b, c});
            }
            faces = next;
        }
        int n = verts.size();
        float[][] p = new float[n][3];
        float[] g = new float[n];
        float ox = 3.7f * (seed + 1), oy = 9.1f * (seed + 2), oz = 5.3f * (seed + 3);
        for (int i = 0; i < n; i++) {
            float[] d = verts.get(i);
            float r = 1f + 0.34f * (noise(d[0] * 1.7f + ox, d[1] * 1.7f + oy, d[2] * 1.7f + oz, 1) - 0.5f) * 2f
                    + 0.16f * (noise(d[0] * 3.9f + oy, d[1] * 3.9f + oz, d[2] * 3.9f + ox, 2) - 0.5f) * 2f;
            // squash a little so the rocks are not all balls
            p[i][0] = d[0] * r * (1.0f + 0.18f * (seed % 3));
            p[i][1] = d[1] * r * (1.0f - 0.10f * (seed % 2));
            p[i][2] = d[2] * r;
            g[i] = noise(d[0] * 2.6f + oz, d[1] * 2.6f + ox, d[2] * 2.6f + oy, 3);
        }
        triangles = faces.size();
        pos = new float[triangles * 9];
        nrm = new float[triangles * 9];
        uv = new float[triangles * 6];
        glow = new float[triangles * 3];
        int ti = 0;
        for (int[] f : faces) {
            float[] a = p[f[0]], b = p[f[1]], c = p[f[2]];
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
            float[] fn = norm(new float[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx});
            // make sure the face normal points away from the centre
            float cx = a[0] + b[0] + c[0], cy = a[1] + b[1] + c[1], cz = a[2] + b[2] + c[2];
            if (fn[0] * cx + fn[1] * cy + fn[2] * cz < 0) {
                fn[0] = -fn[0];
                fn[1] = -fn[1];
                fn[2] = -fn[2];
            }
            float ax = Math.abs(fn[0]), ay = Math.abs(fn[1]), az = Math.abs(fn[2]);
            for (int k = 0; k < 3; k++) {
                float[] q = p[f[k]];
                pos[ti * 9 + k * 3] = q[0];
                pos[ti * 9 + k * 3 + 1] = q[1];
                pos[ti * 9 + k * 3 + 2] = q[2];
                nrm[ti * 9 + k * 3] = fn[0];
                nrm[ti * 9 + k * 3 + 1] = fn[1];
                nrm[ti * 9 + k * 3 + 2] = fn[2];
                float u, v;
                if (ay >= ax && ay >= az) { u = q[0]; v = q[2]; } else if (ax >= az) { u = q[2]; v = q[1]; } else { u = q[0]; v = q[1]; }
                uv[ti * 6 + k * 2] = u * 0.9f;
                uv[ti * 6 + k * 2 + 1] = v * 0.9f;
                glow[ti * 3 + k] = g[f[k]];
            }
            ti++;
        }
    }

    private static int mid(List<float[]> verts, Map<Long, Integer> cache, int a, int b) {
        long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
        Integer found = cache.get(key);
        if (found != null) return found;
        float[] va = verts.get(a), vb = verts.get(b);
        verts.add(norm(new float[]{(va[0] + vb[0]) * 0.5f, (va[1] + vb[1]) * 0.5f, (va[2] + vb[2]) * 0.5f}));
        int idx = verts.size() - 1;
        cache.put(key, idx);
        return idx;
    }

    private static float[] norm(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return len < 1.0E-6f ? new float[]{0, 1, 0} : new float[]{v[0] / len, v[1] / len, v[2] / len};
    }

    private static float noise(float x, float y, float z, int seed) {
        int xi = Mth.floor(x), yi = Mth.floor(y), zi = Mth.floor(z);
        float fx = x - xi, fy = y - yi, fz = z - zi;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        fz = fz * fz * (3f - 2f * fz);
        float x00 = lerp(h(xi, yi, zi, seed), h(xi + 1, yi, zi, seed), fx), x10 = lerp(h(xi, yi + 1, zi, seed), h(xi + 1, yi + 1, zi, seed), fx);
        float x01 = lerp(h(xi, yi, zi + 1, seed), h(xi + 1, yi, zi + 1, seed), fx), x11 = lerp(h(xi, yi + 1, zi + 1, seed), h(xi + 1, yi + 1, zi + 1, seed), fx);
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float h(int x, int y, int z, int seed) {
        int v = x * 374761393 + y * 668265263 + z * 2147483647 + seed * 1274126177;
        v = (v ^ (v >>> 13)) * 1274126177;
        v ^= v >>> 16;
        return (v & 0xFFFF) / 65535f;
    }
}
