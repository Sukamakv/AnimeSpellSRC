package com.example.jjkspells.malenia.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.example.jjkspells.malenia.client.render.Gfx.*;

/** Composite effects shared by the Malenia renderers. Positions are relative to the entity base (the current pose). */
final class Fx {
    private Fx() {
    }

    /** A camera-facing glow. */
    static void glow(MultiBufferSource buf, PoseStack ps, Vec3 p, float size, float[] c, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        billboard(ps);
        Draw.xy(add(buf, GLOW), ps, size, size, c, alpha);
        ps.popPose();
    }

    /** A bright star flare facing the camera. */
    static void star(MultiBufferSource buf, PoseStack ps, Vec3 p, float size, float rot, float[] c, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        billboard(ps);
        ps.mulPose(Axis.ZP.rotationDegrees(rot));
        Draw.xy(add(buf, STAR), ps, size, size, c, alpha);
        ps.popPose();
    }

    /** A sword slash: a crescent standing in the plane across 'yaw' (the direction it travels), rolled by 'roll' degrees. */
    static void slash(MultiBufferSource buf, PoseStack ps, Vec3 p, float yaw, float pitch, float roll, float size, float[] c, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        Draw.xyAt(add(buf, CRESCENT), ps, size, size * 0.5f, 0f, c, alpha);
        Draw.xyAt(add(buf, SLASH), ps, size * 1.15f, size * 0.14f, 0f, WHITE, alpha * 0.9f);
        ps.popPose();
    }

    /** A ribbon trail through the given points (newest last). */
    static void trail(MultiBufferSource buf, PoseStack ps, List<Vec3> pts, Vec3 cam, float width, float[] c, float alpha) {
        int n = pts.size();
        if (n < 2) {
            return;
        }
        VertexConsumer vc = add(buf, TRAIL);
        for (int i = 0; i < n - 1; i++) {
            float f0 = i / (float) (n - 1), f1 = (i + 1) / (float) (n - 1);
            Draw.ribbon(vc, ps, pts.get(i), pts.get(i + 1), cam, width * f0, width * f1, f0, f1, c, alpha * f0 * f0, alpha * f1 * f1);
        }
    }

    /** Standing crescents at ground level expanding outward (the crescent of a sword dance). */
    static void crescentRing(MultiBufferSource buf, PoseStack ps, Vec3 c, float radius, float height, int n, float spin, float[] col, float alpha) {
        for (int i = 0; i < n; i++) {
            float ang = spin + i * 360f / n;
            float rad = ang * Mth.DEG_TO_RAD;
            ps.pushPose();
            ps.translate(c.x + Mth.cos(rad) * radius, c.y + height * 0.5f, c.z + Mth.sin(rad) * radius);
            ps.mulPose(Axis.YP.rotationDegrees(-ang - 90f));
            Draw.xyAt(add(buf, CRESCENT), ps, radius * 0.5f + 0.6f, height * 0.5f, 0f, col, alpha);
            ps.popPose();
        }
    }

    /** A pair of wings behind the caster. spread 0..1 opens them. yaw is the body yaw. */
    static void wings(MultiBufferSource buf, PoseStack ps, Vec3 base, float yaw, float spread, float size, float[] c, float alpha) {
        if (alpha <= 0.01f || spread <= 0.01f) {
            return;
        }
        for (int side = -1; side <= 1; side += 2) {
            ps.pushPose();
            ps.translate(base.x, base.y, base.z);
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.translate(side * 0.12, 0, -0.18);
            ps.mulPose(Axis.YP.rotationDegrees(side * (25f - 30f * spread)));
            ps.mulPose(Axis.ZP.rotationDegrees(side * (10f * spread)));
            float s = size * (0.4f + 0.6f * spread);
            float x0 = side > 0 ? 0f : -s, x1 = side > 0 ? s : 0f;
            float[][] p = {{x0, -0.1f * s, 0}, {x1, -0.1f * s, 0}, {x1, 0.9f * s, 0}, {x0, 0.9f * s, 0}};
            float[][] uv = side > 0 ? new float[][]{{0, 1}, {1, 1}, {1, 0}, {0, 0}} : new float[][]{{0, 1}, {1, 1}, {1, 0}, {0, 0}};
            if (side < 0) {
                uv = new float[][]{{1, 1}, {0, 1}, {0, 0}, {1, 0}};
            }
            Draw.quad(add(buf, WING), ps, p, uv, c[0], c[1], c[2], alpha, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
    }

    /**
     * A giant flower lying over the ground: a flat bloom disc plus upright petals around it that lean outward as it opens.
     * open 0..1, radius in blocks (the full flower radius), rot in degrees.
     */
    static void flower(MultiBufferSource buf, PoseStack ps, Vec3 c, float radius, float open, float rot, int petals, float[] col, float alpha) {
        if (open <= 0.01f || alpha <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        // ground bloom
        Draw.disc(add(buf, FLOWER), ps, radius * (0.2f + 0.8f * Gfx.smooth(open)), rot, 0.08f, col, alpha * 0.9f);
        // standing petals: lean from upright (open 0) to flat-ish (open 1)
        float lean = 8f + 60f * open;
        float len = radius * 0.62f * (0.3f + 0.7f * open);
        float wid = radius * 0.16f * (0.5f + 0.5f * open);
        VertexConsumer vc = add(buf, PETAL);
        for (int i = 0; i < petals; i++) {
            float a = rot + i * 360f / petals;
            ps.pushPose();
            ps.translate(Mth.cos(a * Mth.DEG_TO_RAD) * radius * 0.12f * open, 0, Mth.sin(a * Mth.DEG_TO_RAD) * radius * 0.12f * open);
            ps.mulPose(Axis.YP.rotationDegrees(-a));
            ps.mulPose(Axis.ZP.rotationDegrees(-lean));
            Draw.xyAt(vc, ps, wid, len * 0.5f, len * 0.5f, col, alpha);
            ps.popPose();
        }
        ps.popPose();
    }

    /** A butterfly: flapping wings seen from any side (two crossed flaps). */
    static void butterfly(MultiBufferSource buf, PoseStack ps, Vec3 p, float size, float t, float[] c, float alpha) {
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        billboard(ps);
        float flap = 0.55f + 0.45f * Mth.cos(t * 0.9f);
        ps.scale(Math.max(0.25f, flap), 1f, 1f);
        Draw.xy(add(buf, BUTTERFLY), ps, size, size, c, alpha);
        ps.popPose();
    }

    /** A gold sword (tip towards 'to') drawn as a camera-facing ribbon; length in blocks. */
    static void sword(MultiBufferSource buf, PoseStack ps, Vec3 from, Vec3 to, Vec3 cam, float width, float[] c, float alpha) {
        Draw.ribbon(trans(buf, SWORD), ps, from, to, cam, width, width, 0f, 1f, c, alpha, alpha);
    }

    /** A red phantom figure standing at p, facing a yaw, with a lean towards a direction (thrusts). */
    static void phantom(MultiBufferSource buf, PoseStack ps, Vec3 p, float yaw, float height, float alpha, float[] c) {
        if (alpha <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        billboard(ps);
        Draw.xyAt(trans(buf, PHANTOM), ps, height * 0.25f, height * 0.5f, height * 0.5f, c, alpha);
        ps.popPose();
        // a soft red glow behind it
        glow(buf, ps, p.add(0, height * 0.55, 0), height * 0.45f, CRIMSON, alpha * 0.4f);
    }
}
