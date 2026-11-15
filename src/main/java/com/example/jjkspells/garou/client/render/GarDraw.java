package com.example.jjkspells.garou.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Immediate-mode geometry for the Gamma Ray Burst. Everything is full-bright, double sided and tinted by r,g,b,a (0-1).
 * Animated PNG sheets are stacked vertically: frame f of n occupies v in [f/n, (f+1)/n].
 */
final class GarDraw {
    private GarDraw() {
    }

    static final float[] WHITE = {1f, 1f, 1f};

    static int a8(float a) {
        return (int) (Mth.clamp(a, 0f, 1f) * 255f);
    }

    static float fv0(int frame, int n) {
        return (frame % n) / (float) n + 0.002f;
    }

    static float fv1(int frame, int n) {
        return (frame % n + 1) / (float) n - 0.002f;
    }

    static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, double x, double y, double z, float u, float v, float r, float g, float b, int a) {
        vc.vertex(m, (float) x, (float) y, (float) z).color((int) (Mth.clamp(r, 0f, 1f) * 255), (int) (Mth.clamp(g, 0f, 1f) * 255), (int) (Mth.clamp(b, 0f, 1f) * 255), a)
                .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0f, 1f, 0f).endVertex();
    }

    /** A quad given by four corners (counter-clockwise as seen from the front) and a uv rectangle; drawn from both sides. */
    static void quad(VertexConsumer vc, PoseStack ps, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float u0, float v0, float u1, float v1, float[] c, float alpha) {
        int a = a8(alpha);
        if (a <= 2) {
            return;
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        // corners: p0 = (u0, v1) bottom-left, p1 = (u1, v1), p2 = (u1, v0), p3 = (u0, v0)
        v(vc, m, n, p0.x, p0.y, p0.z, u0, v1, c[0], c[1], c[2], a);
        v(vc, m, n, p1.x, p1.y, p1.z, u1, v1, c[0], c[1], c[2], a);
        v(vc, m, n, p2.x, p2.y, p2.z, u1, v0, c[0], c[1], c[2], a);
        v(vc, m, n, p3.x, p3.y, p3.z, u0, v0, c[0], c[1], c[2], a);
        v(vc, m, n, p3.x, p3.y, p3.z, u0, v0, c[0], c[1], c[2], a);
        v(vc, m, n, p2.x, p2.y, p2.z, u1, v0, c[0], c[1], c[2], a);
        v(vc, m, n, p1.x, p1.y, p1.z, u1, v1, c[0], c[1], c[2], a);
        v(vc, m, n, p0.x, p0.y, p0.z, u0, v1, c[0], c[1], c[2], a);
    }

    /**
     * Camera-facing square of half-size {@code hs} around {@code c}, rolled by {@code roll} radians. camR / camU are the
     * camera's right and up vectors (any length 1 vectors in the same space as c).
     */
    static void billboard(VertexConsumer vc, PoseStack ps, Vec3 c, Vec3 camR, Vec3 camU, float hs, float roll, int frame, int frames, float[] col, float alpha) {
        float cs = Mth.cos(roll), sn = Mth.sin(roll);
        Vec3 r = camR.scale(cs).add(camU.scale(sn)).scale(hs);
        Vec3 u = camU.scale(cs).subtract(camR.scale(sn)).scale(hs);
        quad(vc, ps, c.subtract(r).subtract(u), c.add(r).subtract(u), c.add(r).add(u), c.subtract(r).add(u),
                0f, frames <= 1 ? 0f : fv0(frame, frames), 1f, frames <= 1 ? 1f : fv1(frame, frames), col, alpha);
    }

    /** A flat square in the plane spanned by the unit vectors ax, ay (rotated by rot radians inside that plane). */
    static void plane(VertexConsumer vc, PoseStack ps, Vec3 c, Vec3 ax, Vec3 ay, float hs, float rot, int frame, int frames, float[] col, float alpha) {
        billboard(vc, ps, c, ax, ay, hs, rot, frame, frames, col, alpha);
    }

    /**
     * Camera-facing ribbon through the points {@code pts} (local space). halfWidth[i] and alpha[i] are per point; u runs
     * from u0 along the strip with {@code uPerBlock} per block of length; v spans one animation frame of the sheet.
     */
    static void strip(VertexConsumer vc, PoseStack ps, Vec3[] pts, float[] halfWidth, float[] alpha, Vec3 cam, float u0, float uPerBlock, int frame, int frames, float[] col) {
        int n = pts.length;
        if (n < 2) {
            return;
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        float vTop = frames <= 1 ? 0f : fv0(frame, frames), vBot = frames <= 1 ? 1f : fv1(frame, frames);
        Vec3[] l = new Vec3[n], r = new Vec3[n];
        float[] us = new float[n];
        double acc = 0;
        for (int i = 0; i < n; i++) {
            Vec3 d = i == 0 ? pts[1].subtract(pts[0]) : (i == n - 1 ? pts[i].subtract(pts[i - 1]) : pts[i + 1].subtract(pts[i - 1]));
            Vec3 side = d.cross(cam.subtract(pts[i]));
            if (side.lengthSqr() < 1.0E-10) {
                side = d.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1.0E-10) {
                    side = new Vec3(1, 0, 0);
                }
            }
            side = side.normalize().scale(halfWidth[i]);
            l[i] = pts[i].subtract(side);
            r[i] = pts[i].add(side);
            if (i > 0) {
                acc += pts[i].distanceTo(pts[i - 1]);
            }
            us[i] = u0 + (float) (acc * uPerBlock);
        }
        for (int i = 0; i < n - 1; i++) {
            int a0 = a8(alpha[i]), a1 = a8(alpha[i + 1]);
            if (a0 <= 2 && a1 <= 2) {
                continue;
            }
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 0) {
                    v(vc, m, nm, l[i].x, l[i].y, l[i].z, us[i], vBot, col[0], col[1], col[2], a0);
                    v(vc, m, nm, r[i].x, r[i].y, r[i].z, us[i], vTop, col[0], col[1], col[2], a0);
                    v(vc, m, nm, r[i + 1].x, r[i + 1].y, r[i + 1].z, us[i + 1], vTop, col[0], col[1], col[2], a1);
                    v(vc, m, nm, l[i + 1].x, l[i + 1].y, l[i + 1].z, us[i + 1], vBot, col[0], col[1], col[2], a1);
                } else {
                    v(vc, m, nm, l[i + 1].x, l[i + 1].y, l[i + 1].z, us[i + 1], vBot, col[0], col[1], col[2], a1);
                    v(vc, m, nm, r[i + 1].x, r[i + 1].y, r[i + 1].z, us[i + 1], vTop, col[0], col[1], col[2], a1);
                    v(vc, m, nm, r[i].x, r[i].y, r[i].z, us[i], vTop, col[0], col[1], col[2], a0);
                    v(vc, m, nm, l[i].x, l[i].y, l[i].z, us[i], vBot, col[0], col[1], col[2], a0);
                }
            }
        }
    }

    static float smooth(float v) {
        v = Mth.clamp(v, 0f, 1f);
        return v * v * (3f - 2f * v);
    }

    /** A stable pseudo random number in 0..1 for an index and a salt. */
    static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
