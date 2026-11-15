package com.example.jjkspells.frieren.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Immediate-mode drawing helper (own copy for Frieren): billboards, flip-book frames, planes in any orientation, camera-facing ribbons, shells, cylinders. */
public final class VolPen {
    private static final float TAU = (float) (Math.PI * 2.0);
    private final VertexConsumer vc;
    private final Matrix4f m;
    private final Vector3f right, up;

    public VolPen(VertexConsumer vc, PoseStack ps, Vector3f right, Vector3f up) {
        this.vc = vc;
        this.m = ps.last().pose();
        this.right = right;
        this.up = up;
    }

    public void vtx(float x, float y, float z, float u, float v, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).uv(u, v).color(r, g, b, Mth.clamp(a, 0f, 1f)).endVertex();
    }

    public static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

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

    /** One frame (0..15) of a 4x4 flip-book. */
    public void flipbook(float cx, float cy, float cz, float size, float rot, int frame, float a) {
        int f = Mth.clamp(frame, 0, 15);
        float u0 = (f % 4) * 0.25f, v0 = (f / 4) * 0.25f;
        billboard(cx, cy, cz, size, size, rot, u0, v0, u0 + 0.25f, v0 + 0.25f, 1f, 1f, 1f, a);
    }

    /** Square plane spanned by unit axes u and v (any orientation), half size radius, rotated in-plane by rot. Full texture. */
    public void disc(float cx, float cy, float cz, float[] u, float[] v, float radius, float rot, float a) {
        float c = Mth.cos(rot), s = Mth.sin(rot);
        float ax = (u[0] * c + v[0] * s) * radius, ay = (u[1] * c + v[1] * s) * radius, az = (u[2] * c + v[2] * s) * radius;
        float bx = (v[0] * c - u[0] * s) * radius, by = (v[1] * c - u[1] * s) * radius, bz = (v[2] * c - u[2] * s) * radius;
        vtx(cx - ax - bx, cy - ay - by, cz - az - bz, 0f, 1f, 1f, 1f, 1f, a);
        vtx(cx - ax + bx, cy - ay + by, cz - az + bz, 0f, 0f, 1f, 1f, 1f, a);
        vtx(cx + ax + bx, cy + ay + by, cz + az + bz, 1f, 0f, 1f, 1f, 1f, a);
        vtx(cx + ax - bx, cy + ay - by, cz + az - bz, 1f, 1f, 1f, 1f, 1f, a);
    }

    /**
     * Ribbon from tail to head that turns around its own axis to face the camera (cam = camera position in local space).
     * Texture v runs 0 (head) .. 1 (tail), u across.
     */
    public void ribbon(float[] head, float[] tail, float wHead, float wTail, float[] cam, float a) {
        float dx = head[0] - tail[0], dy = head[1] - tail[1], dz = head[2] - tail[2];
        float mx = (head[0] + tail[0]) * 0.5f, my = (head[1] + tail[1]) * 0.5f, mz = (head[2] + tail[2]) * 0.5f;
        float vx = cam[0] - mx, vy = cam[1] - my, vz = cam[2] - mz;
        float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        float l = Mth.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1.0e-5f) return;
        sx /= l;
        sy /= l;
        sz /= l;
        float hw = wHead * 0.5f, tw = wTail * 0.5f;
        vtx(tail[0] - sx * tw, tail[1] - sy * tw, tail[2] - sz * tw, 0f, 1f, 1f, 1f, 1f, a);
        vtx(head[0] - sx * hw, head[1] - sy * hw, head[2] - sz * hw, 0f, 0f, 1f, 1f, 1f, a);
        vtx(head[0] + sx * hw, head[1] + sy * hw, head[2] + sz * hw, 1f, 0f, 1f, 1f, 1f, a);
        vtx(tail[0] + sx * tw, tail[1] + sy * tw, tail[2] + sz * tw, 1f, 1f, 1f, 1f, 1f, a);
    }

    /** Lat/long ellipsoid shell, alpha fading towards the poles, optional warping. */
    public void sphereShell(float cx, float cy, float cz, float rx, float ry, float rz, int lat, int lon,
                            float uRep, float vRep, float uOff, float vOff, float alpha, float wobble, float time) {
        for (int i = 0; i < lat; i++) {
            float v0 = (float) i / lat, v1 = (float) (i + 1) / lat;
            float a0 = (float) Math.PI * (v0 - 0.5f), a1 = (float) Math.PI * (v1 - 0.5f);
            float f0 = (float) Math.pow(Math.sin(Math.PI * v0), 0.6), f1 = (float) Math.pow(Math.sin(Math.PI * v1), 0.6);
            for (int j = 0; j < lon; j++) {
                float u0 = (float) j / lon, u1 = (float) (j + 1) / lon;
                float t0 = u0 * TAU, t1 = u1 * TAU;
                sph(cx, cy, cz, rx, ry, rz, a0, t0, wobble, time, uRep * u0 + uOff, vRep * v0 + vOff, alpha * f0);
                sph(cx, cy, cz, rx, ry, rz, a0, t1, wobble, time, uRep * u1 + uOff, vRep * v0 + vOff, alpha * f0);
                sph(cx, cy, cz, rx, ry, rz, a1, t1, wobble, time, uRep * u1 + uOff, vRep * v1 + vOff, alpha * f1);
                sph(cx, cy, cz, rx, ry, rz, a1, t0, wobble, time, uRep * u0 + uOff, vRep * v1 + vOff, alpha * f1);
            }
        }
    }

    private void sph(float cx, float cy, float cz, float rx, float ry, float rz, float a, float t, float wobble, float time,
                     float u, float v, float al) {
        float w = 1f + wobble * Mth.sin(t * 3f + a * 4f + time);
        float ca = Mth.cos(a);
        vtx(cx + rx * w * ca * Mth.cos(t), cy + ry * w * Mth.sin(a), cz + rz * w * ca * Mth.sin(t), u, v, 1f, 1f, 1f, al);
    }

    /** Open upright cylinder with per-vertex alpha (fade in over the bottom share, out over the top share). */
    public void cylinder(float cx, float y0, float cz, float radius, float height, int seg, int rows,
                         float uRep, float vRep, float uOff, float vOff, float alpha, float bottomFade, float topFade) {
        for (int k = 0; k < rows; k++) {
            float h0 = (float) k / rows, h1 = (float) (k + 1) / rows;
            float al0 = alpha * fadeH(h0, bottomFade, topFade), al1 = alpha * fadeH(h1, bottomFade, topFade);
            for (int j = 0; j < seg; j++) {
                float u0 = (float) j / seg, u1 = (float) (j + 1) / seg, t0 = u0 * TAU, t1 = u1 * TAU;
                vtx(cx + radius * Mth.cos(t0), y0 + h0 * height, cz + radius * Mth.sin(t0), uRep * u0 + uOff, vRep * (1f - h0) + vOff, 1f, 1f, 1f, al0);
                vtx(cx + radius * Mth.cos(t1), y0 + h0 * height, cz + radius * Mth.sin(t1), uRep * u1 + uOff, vRep * (1f - h0) + vOff, 1f, 1f, 1f, al0);
                vtx(cx + radius * Mth.cos(t1), y0 + h1 * height, cz + radius * Mth.sin(t1), uRep * u1 + uOff, vRep * (1f - h1) + vOff, 1f, 1f, 1f, al1);
                vtx(cx + radius * Mth.cos(t0), y0 + h1 * height, cz + radius * Mth.sin(t0), uRep * u0 + uOff, vRep * (1f - h1) + vOff, 1f, 1f, 1f, al1);
            }
        }
    }

    /** Tinted flip-book frame. */
    public void flipbookTint(float cx, float cy, float cz, float size, float rot, int frame, float r, float g, float b, float a) {
        int f = Mth.clamp(frame, 0, 15);
        float u0 = (f % 4) * 0.25f, v0 = (f / 4) * 0.25f;
        billboard(cx, cy, cz, size, size, rot, u0, v0, u0 + 0.25f, v0 + 0.25f, r, g, b, a);
    }

    /** Camera-facing ribbon through a polyline (per-point width, texture v and alpha). Used for the flame core. */
    public void strip(float[][] pts, float[] w, float[] v, float[] al, float[] cam, float r, float g, float b) {
        int n = pts.length;
        float[][] side = new float[n][3];
        for (int i = 0; i < n; i++) {
            float[] a = pts[Math.max(i - 1, 0)], c = pts[Math.min(i + 1, n - 1)];
            float tx = c[0] - a[0], ty = c[1] - a[1], tz = c[2] - a[2];
            float vx = cam[0] - pts[i][0], vy = cam[1] - pts[i][1], vz = cam[2] - pts[i][2];
            float sx = ty * vz - tz * vy, sy = tz * vx - tx * vz, sz = tx * vy - ty * vx;
            float l = Mth.sqrt(sx * sx + sy * sy + sz * sz);
            if (l < 1.0e-5f) {
                side[i] = i > 0 ? side[i - 1] : new float[]{1f, 0f, 0f};
            } else {
                side[i] = new float[]{sx / l, sy / l, sz / l};
            }
        }
        for (int i = 0; i < n - 1; i++) {
            float h0 = w[i] * 0.5f, h1 = w[i + 1] * 0.5f;
            vtx(pts[i][0] - side[i][0] * h0, pts[i][1] - side[i][1] * h0, pts[i][2] - side[i][2] * h0, 0f, v[i], r, g, b, al[i]);
            vtx(pts[i][0] + side[i][0] * h0, pts[i][1] + side[i][1] * h0, pts[i][2] + side[i][2] * h0, 1f, v[i], r, g, b, al[i]);
            vtx(pts[i + 1][0] + side[i + 1][0] * h1, pts[i + 1][1] + side[i + 1][1] * h1, pts[i + 1][2] + side[i + 1][2] * h1, 1f, v[i + 1], r, g, b, al[i + 1]);
            vtx(pts[i + 1][0] - side[i + 1][0] * h1, pts[i + 1][1] - side[i + 1][1] * h1, pts[i + 1][2] - side[i + 1][2] * h1, 0f, v[i + 1], r, g, b, al[i + 1]);
        }
    }

    /** Sphere shell with uniform alpha and flat colour (no pole fade): screen tint / flash around the camera. Use with the flat texture. */
    public void shellFlat(float cx, float cy, float cz, float radius, int lat, int lon, float r, float g, float b, float a) {
        for (int i = 0; i < lat; i++) {
            float a0 = (float) Math.PI * ((float) i / lat - 0.5f), a1 = (float) Math.PI * ((float) (i + 1) / lat - 0.5f);
            for (int j = 0; j < lon; j++) {
                float t0 = (float) j / lon * TAU, t1 = (float) (j + 1) / lon * TAU;
                vtx(cx + radius * Mth.cos(a0) * Mth.cos(t0), cy + radius * Mth.sin(a0), cz + radius * Mth.cos(a0) * Mth.sin(t0), 0.5f, 0.5f, r, g, b, a);
                vtx(cx + radius * Mth.cos(a0) * Mth.cos(t1), cy + radius * Mth.sin(a0), cz + radius * Mth.cos(a0) * Mth.sin(t1), 0.5f, 0.5f, r, g, b, a);
                vtx(cx + radius * Mth.cos(a1) * Mth.cos(t1), cy + radius * Mth.sin(a1), cz + radius * Mth.cos(a1) * Mth.sin(t1), 0.5f, 0.5f, r, g, b, a);
                vtx(cx + radius * Mth.cos(a1) * Mth.cos(t0), cy + radius * Mth.sin(a1), cz + radius * Mth.cos(a1) * Mth.sin(t0), 0.5f, 0.5f, r, g, b, a);
            }
        }
    }

    private static float fadeH(float h, float bottom, float top) {
        float lo = bottom <= 0f ? 1f : smooth(h / bottom);
        float hi = top <= 0f ? 1f : 1f - smooth((h - (1f - top)) / top);
        return lo * hi;
    }
}
