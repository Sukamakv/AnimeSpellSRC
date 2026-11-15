package com.example.jjkspells.irontomb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Small geometry helpers: billboards, ground quads, ribbons, spikes, spheres and flipbook UVs. */
public final class Fx {
    private Fx() {
    }

    public static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /** 0 -> 1 as t goes a -> b. */
    public static float ramp(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0f, 1f);
    }

    public static float ease(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    /** Fade in over [a,b], hold, fade out over [c,d]. */
    public static float window(float t, float a, float b, float c, float d) {
        return smooth(ramp(t, a, b)) * (1 - smooth(ramp(t, c, d)));
    }

    // ------------------------------------------------------------------ quads
    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float w, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, w).endVertex();
    }

    /** Quad in the pose's XY plane, centred. */
    public static void quadXY(VertexConsumer vc, PoseStack ps, float hw, float hh, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        Matrix4f m = ps.last().pose();
        v(vc, m, -hw, -hh, 0, u0, v1, r, g, b, a);
        v(vc, m, hw, -hh, 0, u1, v1, r, g, b, a);
        v(vc, m, hw, hh, 0, u1, v0, r, g, b, a);
        v(vc, m, -hw, hh, 0, u0, v0, r, g, b, a);
    }

    public static void quadXY(VertexConsumer vc, PoseStack ps, float hw, float hh, float r, float g, float b, float a) {
        quadXY(vc, ps, hw, hh, 0, 0, 1, 1, r, g, b, a);
    }

    /** Quad lying in the pose's XZ plane at height y, centred. */
    public static void quadXZ(VertexConsumer vc, PoseStack ps, float half, float y, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        Matrix4f m = ps.last().pose();
        v(vc, m, -half, y, -half, u0, v0, r, g, b, a);
        v(vc, m, half, y, -half, u1, v0, r, g, b, a);
        v(vc, m, half, y, half, u1, v1, r, g, b, a);
        v(vc, m, -half, y, half, u0, v1, r, g, b, a);
    }

    public static void quadXZ(VertexConsumer vc, PoseStack ps, float half, float y, float r, float g, float b, float a) {
        quadXZ(vc, ps, half, y, 0, 0, 1, 1, r, g, b, a);
    }

    /** UV rectangle of a frame in a square flipbook of cols x cols frames. Returns {u0,v0,u1,v1}. */
    public static float[] frame(int index, int cols) {
        index = Mth.clamp(index, 0, cols * cols - 1);
        float s = 1f / cols;
        float u = (index % cols) * s, w = (index / cols) * s;
        return new float[]{u, w, u + s, w + s};
    }

    public static void flipXZ(VertexConsumer vc, PoseStack ps, float half, float y, int frame, int cols, float r, float g, float b, float a) {
        float[] f = frame(frame, cols);
        quadXZ(vc, ps, half, y, f[0], f[1], f[2], f[3], r, g, b, a);
    }

    public static void flipXY(VertexConsumer vc, PoseStack ps, float hw, float hh, int frame, int cols, float r, float g, float b, float a) {
        float[] f = frame(frame, cols);
        quadXY(vc, ps, hw, hh, f[0], f[1], f[2], f[3], r, g, b, a);
    }

    // ------------------------------------------------------------------ ribbons / beams
    /**
     * Camera-facing ribbon from a to b (both in the current pose space). uvScroll moves the texture along the beam.
     * camRel is the camera position in the same space.
     */
    public static void ribbon(VertexConsumer vc, PoseStack ps, Vec3 a, Vec3 b, Vec3 camRel, float width, float uvScroll, float uvRepeat, float r, float g, float bl, float al) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) {
            return;
        }
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 toCam = camRel.subtract(mid);
        Vec3 side = d.cross(toCam);
        if (side.lengthSqr() < 1e-8) {
            side = d.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1e-8) {
                side = new Vec3(1, 0, 0);
            }
        }
        side = side.normalize().scale(width * 0.5);
        Matrix4f m = ps.last().pose();
        float len = (float) d.length();
        float u0 = uvScroll, u1 = uvScroll + len * uvRepeat;
        v(vc, m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z), u0, 0, r, g, bl, al);
        v(vc, m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z), u0, 1, r, g, bl, al);
        v(vc, m, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z), u1, 1, r, g, bl, al);
        v(vc, m, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z), u1, 0, r, g, bl, al);
    }

    /** A vertical light pillar made of two crossed quads. Uses the beam texture (tileable). */
    public static void pillar(VertexConsumer vc, PoseStack ps, Vec3 base, float height, float radius, float scroll, float r, float g, float b, float a) {
        Matrix4f m = ps.last().pose();
        float x = (float) base.x, y = (float) base.y, z = (float) base.z;
        float rep = height / 6f;
        for (int k = 0; k < 2; k++) {
            float dx = k == 0 ? radius : 0, dz = k == 0 ? 0 : radius;
            v(vc, m, x - dx, y, z - dz, scroll, 0, r, g, b, a);
            v(vc, m, x + dx, y, z + dz, scroll + 1, 0, r, g, b, a);
            v(vc, m, x + dx, y + height, z + dz, scroll + 1, rep, r, g, b, 0f);
            v(vc, m, x - dx, y + height, z - dz, scroll, rep, r, g, b, 0f);
        }
    }

    // ------------------------------------------------------------------ solid shapes (POSITION_COLOR_TEX, flat shaded by colour)
    /** Four-sided pyramid. base centre, unit direction, length, half-width. Faces are shaded by orientation. */
    public static void spike(VertexConsumer vc, PoseStack ps, Vec3 base, Vec3 dir, float length, float radius, float r, float g, float b, float a) {
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = dir.cross(up).normalize().scale(radius);
        Vec3 s2 = dir.cross(s1).normalize().scale(radius);
        Vec3 tip = base.add(dir.scale(length));
        Vec3[] c = {base.add(s1).add(s2), base.subtract(s1).add(s2), base.subtract(s1).subtract(s2), base.add(s1).subtract(s2)};
        Matrix4f m = ps.last().pose();
        for (int i = 0; i < 4; i++) {
            Vec3 p0 = c[i], p1 = c[(i + 1) % 4];
            float shade = 0.55f + 0.45f * (i % 2 == 0 ? 1f : 0.55f);
            v(vc, m, (float) p0.x, (float) p0.y, (float) p0.z, 0, 1, r * shade, g * shade, b * shade, a);
            v(vc, m, (float) p1.x, (float) p1.y, (float) p1.z, 1, 1, r * shade, g * shade, b * shade, a);
            v(vc, m, (float) tip.x, (float) tip.y, (float) tip.z, 1, 0, r * shade, g * shade, b * shade, a);
            v(vc, m, (float) tip.x, (float) tip.y, (float) tip.z, 0, 0, r * shade, g * shade, b * shade, a);
        }
    }

    /** Axis-aligned box in pose space, tiled texture (uv scale = blocks per tile). */
    public static void box(VertexConsumer vc, PoseStack ps, float x0, float y0, float z0, float x1, float y1, float z1, float tile, float r, float g, float b, float a) {
        Matrix4f m = ps.last().pose();
        float dx = (x1 - x0) / tile, dy = (y1 - y0) / tile, dz = (z1 - z0) / tile;
        // top / bottom
        face(vc, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, dx, dz, r, g, b, a, 1.0f);
        face(vc, m, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, dx, dz, r, g, b, a, 0.45f);
        // sides
        face(vc, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, dx, dy, r, g, b, a, 0.8f);
        face(vc, m, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, dx, dy, r, g, b, a, 0.8f);
        face(vc, m, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, dz, dy, r, g, b, a, 0.65f);
        face(vc, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, dz, dy, r, g, b, a, 0.65f);
    }

    private static void face(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float us, float vs,
                             float r, float g, float b, float a, float shade) {
        r *= shade;
        g *= shade;
        b *= shade;
        v(vc, m, ax, ay, az, 0, vs, r, g, b, a);
        v(vc, m, bx, by, bz, us, vs, r, g, b, a);
        v(vc, m, cx, cy, cz, us, 0, r, g, b, a);
        v(vc, m, dx, dy, dz, 0, 0, r, g, b, a);
    }

    /** UV sphere (equirectangular texture). Drawn double-sided because culling is off. */
    public static void sphere(VertexConsumer vc, PoseStack ps, float radius, int stacks, int slices, float uShift, float r, float g, float b, float a) {
        Matrix4f m = ps.last().pose();
        for (int i = 0; i < stacks; i++) {
            float p0 = (float) Math.PI * (i / (float) stacks - 0.5f), p1 = (float) Math.PI * ((i + 1) / (float) stacks - 0.5f);
            float y0 = Mth.sin(p0) * radius, y1 = Mth.sin(p1) * radius, c0 = Mth.cos(p0) * radius, c1 = Mth.cos(p1) * radius;
            for (int j = 0; j < slices; j++) {
                float t0 = (float) (Math.PI * 2) * j / slices, t1 = (float) (Math.PI * 2) * (j + 1) / slices;
                float u0 = j / (float) slices + uShift, u1 = (j + 1) / (float) slices + uShift;
                float v0 = 1 - i / (float) stacks, v1 = 1 - (i + 1) / (float) stacks;
                v(vc, m, Mth.cos(t0) * c0, y0, Mth.sin(t0) * c0, u0, v0, r, g, b, a);
                v(vc, m, Mth.cos(t1) * c0, y0, Mth.sin(t1) * c0, u1, v0, r, g, b, a);
                v(vc, m, Mth.cos(t1) * c1, y1, Mth.sin(t1) * c1, u1, v1, r, g, b, a);
                v(vc, m, Mth.cos(t0) * c1, y1, Mth.sin(t0) * c1, u0, v1, r, g, b, a);
            }
        }
    }

    private static final float LX = 0.35f / 0.9618f * 1f, LY = 0.75f / 0.9618f, LZ = 0.55f / 0.9618f;
    private static final int[][] FQ = {{0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}, {4, 5, 6, 7}, {3, 2, 1, 0}};

    /**
     * Tapered rectangular prism centred on the pose origin, axis = local Y.
     * mode 0: lit by the face normal, mode 1: red rim light from below (skips faces that get none), mode 2: flat emissive.
     */
    public static void frustum(VertexConsumer vc, PoseStack ps, float len, float w0, float d0, float w1, float d1, float tile,
                               float r, float g, float b, float a, int mode) {
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        float h = len * 0.5f;
        float[][] V = {{-w0, -h, -d0}, {w0, -h, -d0}, {w0, -h, d0}, {-w0, -h, d0}, {-w1, h, -d1}, {w1, h, -d1}, {w1, h, d1}, {-w1, h, d1}};
        Vector3f n = new Vector3f();
        for (int[] q : FQ) {
            float[] A = V[q[0]], B = V[q[1]], C = V[q[2]], D = V[q[3]];
            float ux = B[0] - A[0], uy = B[1] - A[1], uz = B[2] - A[2];
            float vx = D[0] - A[0], vy = D[1] - A[1], vz = D[2] - A[2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            if (nx * nx + ny * ny + nz * nz < 1e-8f) {
                ux = C[0] - B[0];
                uy = C[1] - B[1];
                uz = C[2] - B[2];
                vx = A[0] - B[0];
                vy = A[1] - B[1];
                vz = A[2] - B[2];
                nx = uy * vz - uz * vy;
                ny = uz * vx - ux * vz;
                nz = ux * vy - uy * vx;
            }
            float cx = (A[0] + B[0] + C[0] + D[0]) * 0.25f, cy = (A[1] + B[1] + C[1] + D[1]) * 0.25f, cz = (A[2] + B[2] + C[2] + D[2]) * 0.25f;
            if (nx * cx + ny * cy + nz * cz < 0f) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            float shade = 1f;
            if (mode != 2) {
                n.set(nx, ny, nz);
                if (n.lengthSquared() < 1e-12f) {
                    continue;
                }
                n.normalize();
                nm.transform(n);
                n.normalize();
                if (mode == 0) {
                    shade = 0.30f + 0.70f * Math.max(0f, n.x * LX + n.y * LY + n.z * LZ);
                } else {
                    shade = 0.7f * Math.max(0f, -n.y) + 0.25f * Math.max(0f, n.z);
                    if (shade < 0.03f) {
                        continue;
                    }
                }
            }
            float us = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            float lx = B[0] - A[0], ly = B[1] - A[1], lz = B[2] - A[2];
            us = (float) Math.sqrt(lx * lx + ly * ly + lz * lz) / tile;
            float vs = (float) Math.sqrt((D[0] - A[0]) * (D[0] - A[0]) + (D[1] - A[1]) * (D[1] - A[1]) + (D[2] - A[2]) * (D[2] - A[2])) / tile;
            float rr = r * shade, gg = g * shade, bb = b * shade, aa = mode == 1 ? a * shade : a;
            if (mode == 1) {
                rr = r;
                gg = g;
                bb = b;
            }
            v(vc, m, A[0], A[1], A[2], 0, vs, rr, gg, bb, aa);
            v(vc, m, B[0], B[1], B[2], us, vs, rr, gg, bb, aa);
            v(vc, m, C[0], C[1], C[2], us, 0, rr, gg, bb, aa);
            v(vc, m, D[0], D[1], D[2], 0, 0, rr, gg, bb, aa);
        }
    }

    /** Deterministic pseudo-random in 0..1. */
    public static float rnd(int seed) {
        int h = seed * 374761393 + 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }
}
