package com.example.jjkspells.garou.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Immediate-mode drawing helper (own copy for Garou). Works in the local space of the current pose, vertex format
 * POSITION_TEX_COLOR. Camera right/up vectors (world space) drive the billboards.
 */
public final class RadPen {
    private static final float TAU = (float) (Math.PI * 2.0);
    private final VertexConsumer vc;
    private final Matrix4f m;
    private final Vector3f right, up;

    public RadPen(VertexConsumer vc, PoseStack ps, Vector3f right, Vector3f up) {
        this.vc = vc;
        this.m = ps.last().pose();
        this.right = right;
        this.up = up;
    }

    public void vtx(float x, float y, float z, float u, float v, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).uv(u, v).color(r, g, b, Mth.clamp(a, 0f, 1f)).endVertex();
    }

    /** Quad from 4 corners (12 floats) with its own uv rect and per-vertex alpha. */
    public void quad(float[] p, float u0, float v0, float u1, float v1, float r, float g, float b, float a0, float a1, float a2, float a3) {
        vtx(p[0], p[1], p[2], u0, v1, r, g, b, a0);
        vtx(p[3], p[4], p[5], u0, v0, r, g, b, a1);
        vtx(p[6], p[7], p[8], u1, v0, r, g, b, a2);
        vtx(p[9], p[10], p[11], u1, v1, r, g, b, a3);
    }

    /** Camera-facing quad, half extents hw/hh, rotated by rot (radians). */
    public void billboard(float cx, float cy, float cz, float hw, float hh, float rot,
                          float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        float c = Mth.cos(rot), s = Mth.sin(rot);
        float rx = (right.x * c + up.x * s) * hw, ry = (right.y * c + up.y * s) * hw, rz = (right.z * c + up.z * s) * hw;
        float ux = (up.x * c - right.x * s) * hh, uy = (up.y * c - right.y * s) * hh, uz = (up.z * c - right.z * s) * hh;
        vtx(cx - rx - ux, cy - ry - uy, cz - rz - uz, u0, v1, r, g, b, a);
        vtx(cx - rx + ux, cy - ry + uy, cz - rz + uz, u0, v0, r, g, b, a);
        vtx(cx + rx + ux, cy + ry + uy, cz + rz + uz, u1, v0, r, g, b, a);
        vtx(cx + rx - ux, cy + ry - uy, cz + rz - uz, u1, v1, r, g, b, a);
    }

    /** One frame of a 4x4 flip-book (frame 0..15). */
    public void flipbook(float cx, float cy, float cz, float size, float rot, int frame, float r, float g, float b, float a) {
        int f = Mth.clamp(frame, 0, 15);
        float u0 = (f % 4) * 0.25f, v0 = (f / 4) * 0.25f;
        billboard(cx, cy, cz, size, size, rot, u0, v0, u0 + 0.25f, v0 + 0.25f, r, g, b, a);
    }

    /**
     * Lat/long ellipsoid shell. Alpha fades towards both poles; wobble (0..~0.1) warps the surface with time,
     * which is what sells the "space is distorted" look.
     */
    public void sphereShell(float cx, float cy, float cz, float rx, float ry, float rz, int lat, int lon,
                            float uRep, float vRep, float uOff, float vOff,
                            float r, float g, float b, float alpha, float wobble, float time) {
        for (int i = 0; i < lat; i++) {
            float v0 = (float) i / lat, v1 = (float) (i + 1) / lat;
            float a0 = (float) Math.PI * (v0 - 0.5f), a1 = (float) Math.PI * (v1 - 0.5f);
            float f0 = polar(v0), f1 = polar(v1);
            for (int j = 0; j < lon; j++) {
                float u0 = (float) j / lon, u1 = (float) (j + 1) / lon;
                float t0 = u0 * TAU, t1 = u1 * TAU;
                sph(cx, cy, cz, rx, ry, rz, a0, t0, wobble, time, uRep * u0 + uOff, vRep * v0 + vOff, r, g, b, alpha * f0);
                sph(cx, cy, cz, rx, ry, rz, a0, t1, wobble, time, uRep * u1 + uOff, vRep * v0 + vOff, r, g, b, alpha * f0);
                sph(cx, cy, cz, rx, ry, rz, a1, t1, wobble, time, uRep * u1 + uOff, vRep * v1 + vOff, r, g, b, alpha * f1);
                sph(cx, cy, cz, rx, ry, rz, a1, t0, wobble, time, uRep * u0 + uOff, vRep * v1 + vOff, r, g, b, alpha * f1);
            }
        }
    }

    private static float polar(float v) {
        return (float) Math.pow(Math.sin(Math.PI * v), 0.6);
    }

    private void sph(float cx, float cy, float cz, float rx, float ry, float rz, float a, float t, float wobble, float time,
                     float u, float v, float r, float g, float b, float al) {
        float w = 1f + wobble * Mth.sin(t * 3f + a * 4f + time);
        float ca = Mth.cos(a);
        vtx(cx + rx * w * ca * Mth.cos(t), cy + ry * w * Mth.sin(a), cz + rz * w * ca * Mth.sin(t), u, v, r, g, b, al);
    }

    /**
     * Open upright cylinder with per-vertex alpha (fades in over the bottom share, out over the top share).
     * Used for the flowing aura streaks around the caster.
     */
    public void cylinder(float cx, float y0, float cz, float radius, float height, int seg, int rows,
                         float uRep, float vRep, float uOff, float vOff,
                         float r, float g, float b, float alpha, float bottomFade, float topFade, float sway) {
        for (int k = 0; k < rows; k++) {
            float h0 = (float) k / rows, h1 = (float) (k + 1) / rows;
            float al0 = alpha * fadeH(h0, bottomFade, topFade), al1 = alpha * fadeH(h1, bottomFade, topFade);
            float r0 = radius * (1f + 0.07f * Mth.sin(h0 * 6f + sway)), r1 = radius * (1f + 0.07f * Mth.sin(h1 * 6f + sway));
            for (int j = 0; j < seg; j++) {
                float u0 = (float) j / seg, u1 = (float) (j + 1) / seg;
                float t0 = u0 * TAU, t1 = u1 * TAU;
                vtx(cx + r0 * Mth.cos(t0), y0 + h0 * height, cz + r0 * Mth.sin(t0), uRep * u0 + uOff, vRep * (1f - h0) + vOff, r, g, b, al0);
                vtx(cx + r0 * Mth.cos(t1), y0 + h0 * height, cz + r0 * Mth.sin(t1), uRep * u1 + uOff, vRep * (1f - h0) + vOff, r, g, b, al0);
                vtx(cx + r1 * Mth.cos(t1), y0 + h1 * height, cz + r1 * Mth.sin(t1), uRep * u1 + uOff, vRep * (1f - h1) + vOff, r, g, b, al1);
                vtx(cx + r1 * Mth.cos(t0), y0 + h1 * height, cz + r1 * Mth.sin(t0), uRep * u0 + uOff, vRep * (1f - h1) + vOff, r, g, b, al1);
            }
        }
    }

    private static float fadeH(float h, float bottom, float top) {
        float lo = bottom <= 0f ? 1f : smooth(h / bottom);
        float hi = top <= 0f ? 1f : 1f - smooth((h - (1f - top)) / top);
        return lo * hi;
    }

    public static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
