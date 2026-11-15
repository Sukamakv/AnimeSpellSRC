package com.example.jjkspells.velgrynd.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Small immediate-mode geometry helpers (all double sided, full-bright, tinted by r,g,b in 0-1). */
final class Draw {
    private Draw() {
    }

    static int a8(float a) {
        return (int) (Mth.clamp(a, 0f, 1f) * 255f);
    }

    static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float r, float g, float b, int a, int light) {
        vc.vertex(m, x, y, z).color((int) (r * 255), (int) (g * 255), (int) (b * 255), a).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0f, 1f, 0f).endVertex();
    }

    static void quad(VertexConsumer vc, PoseStack ps, float[][] p, float[][] uv, float r, float g, float b, float alpha, int light) {
        int a = a8(alpha);
        if (a <= 2) {
            return;
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int k = 0; k < 4; k++) {
            v(vc, m, n, p[k][0], p[k][1], p[k][2], uv[k][0], uv[k][1], r, g, b, a, light);
        }
        for (int k = 3; k >= 0; k--) {
            v(vc, m, n, p[k][0], p[k][1], p[k][2], uv[k][0], uv[k][1], r, g, b, a, light);
        }
    }

    static final float[][] UV = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};

    /** Quad in the local XY plane, centred. */
    static void xy(VertexConsumer vc, PoseStack ps, float hw, float hh, float[] c, float alpha) {
        quad(vc, ps, new float[][]{{-hw, -hh, 0}, {hw, -hh, 0}, {hw, hh, 0}, {-hw, hh, 0}}, UV, c[0], c[1], c[2], alpha, LightTexture.FULL_BRIGHT);
    }

    /** Flat quad in the XZ plane at height y, rotated by rotDeg around Y. */
    static void disc(VertexConsumer vc, PoseStack ps, float radius, float rotDeg, float y, float[] c, float alpha) {
        float s = Mth.sin(rotDeg * Mth.DEG_TO_RAD), co = Mth.cos(rotDeg * Mth.DEG_TO_RAD);
        float[][] p = new float[4][];
        float[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            float x = corner[i][0] * radius, z = corner[i][1] * radius;
            p[i] = new float[]{x * co - z * s, y, x * s + z * co};
        }
        quad(vc, ps, p, UV, c[0], c[1], c[2], alpha, LightTexture.FULL_BRIGHT);
    }

    /** Upright crossed pair of quads (beams, hands, flames), base at y0, half-width w, height h. */
    static void cross(VertexConsumer vc, PoseStack ps, float w, float y0, float h, float rotDeg, float[] c, float alpha, int light) {
        for (int i = 0; i < 2; i++) {
            float ang = (rotDeg + i * 90f) * Mth.DEG_TO_RAD;
            float dx = Mth.cos(ang) * w, dz = Mth.sin(ang) * w;
            float[][] p = {{-dx, y0, -dz}, {dx, y0, dz}, {dx, y0 + h, dz}, {-dx, y0 + h, -dz}};
            quad(vc, ps, p, UV, c[0], c[1], c[2], alpha, light);
        }
    }

    /** Open vertical band (cylinder wall) around Y. u repeats {@code repeat} times, scrolls by scroll. */
    static void band(VertexConsumer vc, PoseStack ps, float radius, float y0, float y1, int seg, float repeat, float scroll, float[] c, float alphaBottom, float alphaTop) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int ab = a8(alphaBottom), at = a8(alphaTop);
        if (ab <= 2 && at <= 2) {
            return;
        }
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float x0 = Mth.cos(a0) * radius, z0 = Mth.sin(a0) * radius, x1 = Mth.cos(a1) * radius, z1 = Mth.sin(a1) * radius;
            float u0 = i / (float) seg * repeat + scroll, u1 = (i + 1) / (float) seg * repeat + scroll;
            for (int pass = 0; pass < 2; pass++) {
                float[][] q = {{x0, y0, z0, u0, 1, ab}, {x1, y0, z1, u1, 1, ab}, {x1, y1, z1, u1, 0, at}, {x0, y1, z0, u0, 0, at}};
                for (int k = 0; k < 4; k++) {
                    int kk = pass == 0 ? k : 3 - k;
                    v(vc, m, n, q[kk][0], q[kk][1], q[kk][2], q[kk][3], q[kk][4], c[0], c[1], c[2], (int) q[kk][5], LightTexture.FULL_BRIGHT);
                }
            }
        }
    }

    /** Flat annulus in XZ at height y (u around, v across). */
    static void ring(VertexConsumer vc, PoseStack ps, float rIn, float rOut, float y, int seg, float repeat, float scroll, float[] c, float alpha) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int a = a8(alpha);
        if (a <= 2) {
            return;
        }
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float u0 = i / (float) seg * repeat + scroll, u1 = (i + 1) / (float) seg * repeat + scroll;
            float[][] q = {{Mth.cos(a0) * rIn, y, Mth.sin(a0) * rIn, u0, 1}, {Mth.cos(a1) * rIn, y, Mth.sin(a1) * rIn, u1, 1},
                    {Mth.cos(a1) * rOut, y, Mth.sin(a1) * rOut, u1, 0}, {Mth.cos(a0) * rOut, y, Mth.sin(a0) * rOut, u0, 0}};
            for (int k = 0; k < 4; k++) {
                v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], a, LightTexture.FULL_BRIGHT);
            }
            for (int k = 3; k >= 0; k--) {
                v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], a, LightTexture.FULL_BRIGHT);
            }
        }
    }

    /** UV sphere around the origin. */
    static void sphere(VertexConsumer vc, PoseStack ps, float radius, int lat, int lon, float uScroll, float[] c, float alpha) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int a = a8(alpha);
        if (a <= 2 || radius <= 0.001f) {
            return;
        }
        for (int i = 0; i < lat; i++) {
            float p0 = Mth.PI * i / lat - Mth.HALF_PI, p1 = Mth.PI * (i + 1) / lat - Mth.HALF_PI;
            for (int j = 0; j < lon; j++) {
                float t0 = Mth.TWO_PI * j / lon, t1 = Mth.TWO_PI * (j + 1) / lon;
                float[][] q = {
                        pt(radius, p0, t0, j / (float) lon + uScroll, i / (float) lat), pt(radius, p0, t1, (j + 1) / (float) lon + uScroll, i / (float) lat),
                        pt(radius, p1, t1, (j + 1) / (float) lon + uScroll, (i + 1) / (float) lat), pt(radius, p1, t0, j / (float) lon + uScroll, (i + 1) / (float) lat)};
                for (int k = 0; k < 4; k++) {
                    v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], a, LightTexture.FULL_BRIGHT);
                }
                for (int k = 3; k >= 0; k--) {
                    v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], a, LightTexture.FULL_BRIGHT);
                }
            }
        }
    }

    private static float[] pt(float r, float phi, float theta, float u, float v) {
        return new float[]{Mth.cos(phi) * Mth.cos(theta) * r, Mth.sin(phi) * r, Mth.cos(phi) * Mth.sin(theta) * r, u, v};
    }

    /** Camera-facing ribbon from a to b (positions in the current local space); cam is the camera in that same space. */
    static void ribbon(VertexConsumer vc, PoseStack ps, Vec3 a, Vec3 b, Vec3 cam, float w0, float w1, float uA, float uB, float[] c, float alphaA, float alphaB) {
        Vec3 dir = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = dir.cross(cam.subtract(mid));
        if (side.lengthSqr() < 1.0e-8) {
            return;
        }
        side = side.normalize();
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int aa = a8(alphaA), ab = a8(alphaB);
        if (aa <= 2 && ab <= 2) {
            return;
        }
        Vec3 a0 = a.add(side.scale(-w0)), a1 = a.add(side.scale(w0)), b1 = b.add(side.scale(w1)), b0 = b.add(side.scale(-w1));
        float[][] q = {{(float) a0.x, (float) a0.y, (float) a0.z, uA, 1, aa}, {(float) a1.x, (float) a1.y, (float) a1.z, uA, 0, aa},
                {(float) b1.x, (float) b1.y, (float) b1.z, uB, 0, ab}, {(float) b0.x, (float) b0.y, (float) b0.z, uB, 1, ab}};
        for (int k = 0; k < 4; k++) {
            v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], (int) q[k][5], LightTexture.FULL_BRIGHT);
        }
    }

    /** Half-height of the lens at x in [-1, 1] (pointed at both ends, fullest in the middle). */
    static float lensH(float x, float maxH, float open) {
        float k = Mth.clamp(1f - x * x, 0f, 1f);
        return maxH * open * (float) Math.pow(k, 0.7);
    }

    /** A vertical lens (a wound in space) in the local XY plane: u runs along x (repeat times, scrolling), v bottom to top. */
    static void lens(VertexConsumer vc, PoseStack ps, float halfLen, float maxH, float open, int seg, float repeat, float scroll, float[] c, float alpha) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int a = a8(alpha);
        if (a <= 2 || open <= 0.001f) {
            return;
        }
        for (int i = 0; i < seg; i++) {
            float x0 = -1f + 2f * i / seg, x1 = -1f + 2f * (i + 1) / seg;
            float h0 = lensH(x0, maxH, open), h1 = lensH(x1, maxH, open);
            float u0 = i / (float) seg * repeat + scroll, u1 = (i + 1) / (float) seg * repeat + scroll;
            float[][] q = {{x0 * halfLen, -h0, 0, u0, 1}, {x1 * halfLen, -h1, 0, u1, 1}, {x1 * halfLen, h1, 0, u1, 0}, {x0 * halfLen, h0, 0, u0, 0}};
            for (int k = 0; k < 4; k++) {
                v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], a, LightTexture.FULL_BRIGHT);
            }
            for (int k = 3; k >= 0; k--) {
                v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], a, LightTexture.FULL_BRIGHT);
            }
        }
    }

    /** The jagged rim of the lens: a band of the given thickness along the top and bottom edge. */
    static void lensEdge(VertexConsumer vc, PoseStack ps, float halfLen, float maxH, float open, int seg, float thick, float repeat, float scroll, float[] c, float alpha) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int a = a8(alpha);
        if (a <= 2 || open <= 0.001f) {
            return;
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < seg; i++) {
                float x0 = -1f + 2f * i / seg, x1 = -1f + 2f * (i + 1) / seg;
                float h0 = lensH(x0, maxH, open) * side, h1 = lensH(x1, maxH, open) * side;
                float u0 = i / (float) seg * repeat + scroll, u1 = (i + 1) / (float) seg * repeat + scroll;
                float[][] q = {{x0 * halfLen, h0 - thick, 0, u0, 1}, {x1 * halfLen, h1 - thick, 0, u1, 1}, {x1 * halfLen, h1 + thick, 0, u1, 0}, {x0 * halfLen, h0 + thick, 0, u0, 0}};
                for (int k = 0; k < 4; k++) {
                    v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], a, LightTexture.FULL_BRIGHT);
                }
                for (int k = 3; k >= 0; k--) {
                    v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], a, LightTexture.FULL_BRIGHT);
                }
            }
        }
    }

    /** Quad in the local XY plane with different half sizes and a vertical offset (for crescents and slashes). */
    static void xyAt(VertexConsumer vc, PoseStack ps, float hw, float hh, float cy, float[] c, float alpha) {
        quad(vc, ps, new float[][]{{-hw, cy - hh, 0}, {hw, cy - hh, 0}, {hw, cy + hh, 0}, {-hw, cy + hh, 0}}, UV, c[0], c[1], c[2], alpha, LightTexture.FULL_BRIGHT);
    }
}
