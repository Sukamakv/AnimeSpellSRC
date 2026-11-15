package com.example.jjkspells.garou.client.render;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Immediate-mode drawing helpers: every shape is double sided and full bright, tinted by r,g,b (0-1). */
final class Gx {
    static final int FULL = LightTexture.FULL_BRIGHT;
    /** The whole texture: u0, v0, u1, v1. */
    static final float[] ALL = {0f, 0f, 1f, 1f};

    private Gx() {
    }

    static ResourceLocation tex(String n) {
        return new ResourceLocation(JJKSpells.MODID, "textures/garou/" + n + ".png");
    }

    /** Glowing (additive) layer. */
    static VertexConsumer add(MultiBufferSource b, ResourceLocation t) {
        return b.getBuffer(LinTypes.additive(t));
    }

    /** Ordinary blended layer (smoke, scorch marks). */
    static VertexConsumer trans(MultiBufferSource b, ResourceLocation t) {
        return b.getBuffer(LinTypes.translucent(t));
    }

    static VertexConsumer layer(MultiBufferSource b, ResourceLocation t, boolean additive) {
        return additive ? add(b, t) : trans(b, t);
    }

    static int a8(float a) {
        return (int) (Mth.clamp(a, 0f, 1f) * 255f);
    }

    static int c8(float c) {
        return (int) (Mth.clamp(c, 0f, 1f) * 255f);
    }

    static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float r, float g, float b, int a) {
        vc.vertex(m, x, y, z).color(c8(r), c8(g), c8(b), a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL).normal(n, 0f, 1f, 0f).endVertex();
    }

    /** One quad, both faces. */
    static void quad(VertexConsumer vc, PoseStack ps, float[][] p, float[][] uv, float[] c, float alpha) {
        int a = a8(alpha);
        if (a <= 2) {
            return;
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int k = 0; k < 4; k++) {
            v(vc, m, n, p[k][0], p[k][1], p[k][2], uv[k][0], uv[k][1], c[0], c[1], c[2], a);
        }
        for (int k = 3; k >= 0; k--) {
            v(vc, m, n, p[k][0], p[k][1], p[k][2], uv[k][0], uv[k][1], c[0], c[1], c[2], a);
        }
    }

    private static float[][] uvs(float[] r) {
        return new float[][]{{r[0], r[3]}, {r[2], r[3]}, {r[2], r[1]}, {r[0], r[1]}};
    }

    /** Quad in the local XY plane, centred, with half sizes hw and hh. */
    static void xy(VertexConsumer vc, PoseStack ps, float hw, float hh, float[] uv, float[] c, float alpha) {
        quad(vc, ps, new float[][]{{-hw, -hh, 0}, {hw, -hh, 0}, {hw, hh, 0}, {-hw, hh, 0}}, uvs(uv), c, alpha);
    }

    /** A camera-facing texture at p (relative to the entity base); size is the half extent. */
    static void billboard(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, boolean additive, Vec3 p, float size, float rotDeg, float[] c, float alpha, float[] uv) {
        if (alpha <= 0.01f || size <= 0.001f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        ps.mulPose(Axis.ZP.rotationDegrees(rotDeg));
        xy(layer(buf, tex, additive), ps, size, size, uv == null ? ALL : uv, c, alpha);
        ps.popPose();
    }

    /** A flat texture lying on the ground (XZ plane), turned by rotDeg. */
    static void disc(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, boolean additive, Vec3 centre, float radius, float rotDeg, float[] c, float alpha) {
        if (alpha <= 0.01f || radius <= 0.001f) {
            return;
        }
        ps.pushPose();
        ps.translate(centre.x, centre.y, centre.z);
        float s = Mth.sin(rotDeg * Mth.DEG_TO_RAD), co = Mth.cos(rotDeg * Mth.DEG_TO_RAD);
        float[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        float[][] p = new float[4][];
        for (int i = 0; i < 4; i++) {
            float x = corner[i][0] * radius, z = corner[i][1] * radius;
            p[i] = new float[]{x * co - z * s, 0f, x * s + z * co};
        }
        quad(layer(buf, tex, additive), ps, p, uvs(ALL), c, alpha);
        ps.popPose();
    }

    /** Turns the pose so that local +Z points along dir. */
    static void face(PoseStack ps, Vec3 dir) {
        Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        if (d.lengthSquared() < 1.0e-8f) {
            return;
        }
        d.normalize();
        ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), d));
    }

    /** Flat annulus in XZ at the current origin (u around, v across the band). */
    static void ring(VertexConsumer vc, PoseStack ps, float rIn, float rOut, int seg, float repeat, float scroll, float[] c, float alpha) {
        int a = a8(alpha);
        if (a <= 2 || rOut <= 0.01f) {
            return;
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float u0 = i / (float) seg * repeat + scroll, u1 = (i + 1) / (float) seg * repeat + scroll;
            float[][] q = {{Mth.cos(a0) * rIn, 0, Mth.sin(a0) * rIn, u0, 1}, {Mth.cos(a1) * rIn, 0, Mth.sin(a1) * rIn, u1, 1},
                    {Mth.cos(a1) * rOut, 0, Mth.sin(a1) * rOut, u1, 0}, {Mth.cos(a0) * rOut, 0, Mth.sin(a0) * rOut, u0, 0}};
            for (int k = 0; k < 4; k++) {
                v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], a);
            }
            for (int k = 3; k >= 0; k--) {
                v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], a);
            }
        }
    }

    /** An annulus lying at a point. */
    static void ringAt(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, Vec3 at, float rIn, float rOut, int seg, float repeat, float scroll, float[] c, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ring(add(buf, tex), ps, rIn, rOut, seg, repeat, scroll, c, alpha);
        ps.popPose();
    }

    /** UV sphere around the origin; the texture rectangle uv is repeated repU x repV times. */
    static void sphere(VertexConsumer vc, PoseStack ps, float radius, int lat, int lon, float[] uv, float repU, float repV, float uScroll, float[] c, float alpha) {
        int a = a8(alpha);
        if (a <= 2 || radius <= 0.001f) {
            return;
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float du = uv[2] - uv[0], dv = uv[3] - uv[1];
        for (int i = 0; i < lat; i++) {
            float p0 = Mth.PI * i / lat - Mth.HALF_PI, p1 = Mth.PI * (i + 1) / lat - Mth.HALF_PI;
            float v0 = uv[1] + dv * (i / (float) lat) * repV, v1 = uv[1] + dv * ((i + 1) / (float) lat) * repV;
            for (int j = 0; j < lon; j++) {
                float t0 = Mth.TWO_PI * j / lon, t1 = Mth.TWO_PI * (j + 1) / lon;
                float u0 = uv[0] + du * (j / (float) lon) * repU + uScroll, u1 = uv[0] + du * ((j + 1) / (float) lon) * repU + uScroll;
                float[][] q = {pt(radius, p0, t0, u0, v0), pt(radius, p0, t1, u1, v0), pt(radius, p1, t1, u1, v1), pt(radius, p1, t0, u0, v1)};
                for (int k = 0; k < 4; k++) {
                    v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], a);
                }
                for (int k = 3; k >= 0; k--) {
                    v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], a);
                }
            }
        }
    }

    private static float[] pt(float r, float phi, float theta, float u, float v) {
        return new float[]{Mth.cos(phi) * Mth.cos(theta) * r, Mth.sin(phi) * r, Mth.cos(phi) * Mth.sin(theta) * r, u, v};
    }

    /** A sphere at a point, optionally flattened (sy < 1 squashes it into a cap). */
    static void sphereAt(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, boolean additive, Vec3 at, float radius, float sy, int lat, int lon, float[] uv, float repU, float repV, float uScroll, float[] c, float alpha) {
        if (alpha <= 0.01f || radius <= 0.001f) {
            return;
        }
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.scale(1f, sy, 1f);
        sphere(layer(buf, tex, additive), ps, radius, lat, lon, uv, repU, repV, uScroll, c, alpha);
        ps.popPose();
    }

    /** An open (tapered) cylinder wall around Y: radius r0 at y0, r1 at y1. */
    static void cone(VertexConsumer vc, PoseStack ps, float y0, float y1, float r0, float r1, int seg, float repU, float repV, float scroll, float[] c, float alphaBottom, float alphaTop) {
        int ab = a8(alphaBottom), at = a8(alphaTop);
        if (ab <= 2 && at <= 2) {
            return;
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float u0 = i / (float) seg * repU + scroll, u1 = (i + 1) / (float) seg * repU + scroll;
            float[][] q = {{Mth.cos(a0) * r0, y0, Mth.sin(a0) * r0, u0, repV, ab}, {Mth.cos(a1) * r0, y0, Mth.sin(a1) * r0, u1, repV, ab},
                    {Mth.cos(a1) * r1, y1, Mth.sin(a1) * r1, u1, 0, at}, {Mth.cos(a0) * r1, y1, Mth.sin(a0) * r1, u0, 0, at}};
            for (int k = 0; k < 4; k++) {
                v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], (int) q[k][5]);
            }
            for (int k = 3; k >= 0; k--) {
                v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], (int) q[k][5]);
            }
        }
    }

    /** A camera-facing ribbon from a to b (positions relative to the base); cam is the camera in that same space. */
    static void ribbon(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, Vec3 a, Vec3 b, Vec3 cam, float w, float[] c, float alphaA, float alphaB) {
        Vec3 dir = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = dir.cross(cam.subtract(mid));
        if (side.lengthSqr() < 1.0e-8) {
            return;
        }
        side = side.normalize();
        int aa = a8(alphaA), ab = a8(alphaB);
        if (aa <= 2 && ab <= 2) {
            return;
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        VertexConsumer vc = add(buf, tex);
        Vec3 a0 = a.add(side.scale(-w)), a1 = a.add(side.scale(w)), b1 = b.add(side.scale(w)), b0 = b.add(side.scale(-w));
        float[][] q = {{(float) a0.x, (float) a0.y, (float) a0.z, 0, 1, aa}, {(float) a1.x, (float) a1.y, (float) a1.z, 0, 0, aa},
                {(float) b1.x, (float) b1.y, (float) b1.z, 1, 0, ab}, {(float) b0.x, (float) b0.y, (float) b0.z, 1, 1, ab}};
        for (int k = 0; k < 4; k++) {
            v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], (int) q[k][5]);
        }
        for (int k = 3; k >= 0; k--) {
            v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], (int) q[k][5]);
        }
    }

    /** A flat annulus segment in XZ at the current origin, from angle a0 to a1 (radians); alpha fades from aStart to aEnd along it. */
    static void arc(VertexConsumer vc, PoseStack ps, float rIn, float rOut, float a0, float a1, int seg, float repeat, float scroll, float[] c, float aStart, float aEnd) {
        int as = a8(aStart), ae = a8(aEnd);
        if (as <= 2 && ae <= 2) {
            return;
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int i = 0; i < seg; i++) {
            float f0 = i / (float) seg, f1 = (i + 1) / (float) seg;
            float t0 = a0 + (a1 - a0) * f0, t1 = a0 + (a1 - a0) * f1;
            int al0 = (int) (as + (ae - as) * f0), al1 = (int) (as + (ae - as) * f1);
            float u0 = f0 * repeat + scroll, u1 = f1 * repeat + scroll;
            float[][] q = {{Mth.cos(t0) * rIn, 0, Mth.sin(t0) * rIn, u0, 1, al0}, {Mth.cos(t1) * rIn, 0, Mth.sin(t1) * rIn, u1, 1, al1},
                    {Mth.cos(t1) * rOut, 0, Mth.sin(t1) * rOut, u1, 0, al1}, {Mth.cos(t0) * rOut, 0, Mth.sin(t0) * rOut, u0, 0, al0}};
            for (int k = 0; k < 4; k++) {
                v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], (int) q[k][5]);
            }
            for (int k = 3; k >= 0; k--) {
                v(vc, m, n, q[k][0], q[k][1], q[k][2], q[k][3], q[k][4], c[0], c[1], c[2], (int) q[k][5]);
            }
        }
    }

    /** Turns the pose so that local +Y (the normal of a ring drawn flat in XZ) points along dir. */
    static void faceY(PoseStack ps, Vec3 dir) {
        Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        if (d.lengthSquared() < 1.0e-8f) {
            return;
        }
        d.normalize();
        ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), d));
    }

    /** Cell k (0-15) of a 4x4 sprite sheet. */
    static float[] cell(int k) {
        k = ((k % 16) + 16) % 16;
        float x = (k % 4) * 0.25f, y = (k / 4) * 0.25f;
        return new float[]{x + 0.002f, y + 0.002f, x + 0.248f, y + 0.248f};   // tiny inset: no bleeding from the neighbouring cell
    }
}
