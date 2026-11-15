package com.example.jjkspells.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.GaleConfig;
import com.example.jjkspells.entity.GaleBarrageEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws, in the caster's view frame (local +Z = where the caster looks): a rushing wind cone, many thin speed
 * streaks, and for every strike a diagonal light crescent plus an expanding shock ring.
 */
public class GaleBarrageRenderer extends EntityRenderer<GaleBarrageEntity> {
    private static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/entity/" + n + ".png");
    }

    private static final ResourceLocation CRESCENT = tex("slash_crescent");
    private static final ResourceLocation RING = tex("wind_ring");
    private static final ResourceLocation SPIRAL = tex("wind_spiral");
    private static final ResourceLocation STREAK = tex("wind_streak");

    private static final int STRIKE_VISIBLE_TICKS = 7;

    public GaleBarrageRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(GaleBarrageEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(GaleBarrageEntity e) {
        return CRESCENT;
    }

    @Override
    public void render(GaleBarrageEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Entity caster = e.caster();
        if (caster == null) {
            return;
        }
        int life = e.getLife();
        int hits = e.getHits();
        float t = e.tickCount + pt;
        float env = Mth.clamp(t / 5f, 0f, 1f) * Mth.clamp((life + 8f - t) / 8f, 0f, 1f);
        if (env <= 0.01f) {
            return;
        }
        float reach = GaleConfig.SPELL.reach.get().floatValue();

        float cyaw = Mth.rotLerp(pt, caster.yRotO, caster.getYRot());
        float cpitch = Mth.lerp(pt, caster.xRotO, caster.getXRot());

        ps.pushPose();
        ps.translate(0.0, caster.getEyeHeight() - 0.15, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(-cyaw));
        ps.mulPose(Axis.XP.rotationDegrees(cpitch));

        // ---- wind cone
        VertexConsumer cone = buf.getBuffer(RenderType.eyes(SPIRAL));
        cone(cone, ps, reach + 1f, t, 0.55f * env, 1.0f, 0);
        cone(cone, ps, reach * 0.8f, t * 1.3f + 11f, 0.40f * env, 0.65f, 1);

        // ---- speed streaks
        VertexConsumer streak = buf.getBuffer(RenderType.eyes(STREAK));
        for (int i = 0; i < 18; i++) {
            RandomSource r = RandomSource.create(i * 977L + 13);
            float ang = r.nextFloat() * Mth.TWO_PI;
            float rad = 0.5f + r.nextFloat() * 2.2f;
            float len = 2.5f + r.nextFloat() * 4.0f;
            float speed = 0.06f + r.nextFloat() * 0.05f;
            float z0 = ((t * speed * 6f + r.nextFloat() * 10f) % (reach + len)) - len + 0.6f;
            float a = env * (0.5f + 0.5f * r.nextFloat()) * Mth.clamp(1f - z0 / (reach + 1f), 0f, 1f);
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(ang)));
            ps.translate(rad, 0, 0);
            // thin quad along Z, facing sideways (double sided)
            quadZ(streak, ps, z0, z0 + len, 0.05f + r.nextFloat() * 0.05f, a);
            ps.popPose();
        }

        // ---- strikes
        int now = e.tickCount;
        for (int pass = 0; pass < 2; pass++) {
        VertexConsumer vcp = buf.getBuffer(RenderType.eyes(pass == 0 ? CRESCENT : RING));
        for (int a = Math.max(1, now - STRIKE_VISIBLE_TICKS); a <= Math.min(now, life); a++) {
            int n = GaleBarrageEntity.strikesAt(a, life, hits);
            if (n <= 0) {
                continue;
            }
            float p = ((now - a) + pt) / STRIKE_VISIBLE_TICKS;
            if (p < 0f || p > 1f) {
                continue;
            }
            RandomSource r = RandomSource.create(a * 7919L + e.getId() * 31L);
            boolean flip = (a & 1) == 0;
            float roll = (flip ? 1 : -1) * (25f + r.nextFloat() * 55f);
            float dist = 1.7f + r.nextFloat() * 1.8f;
            float size = 2.4f + r.nextFloat() * 1.8f;
            float ox = (r.nextFloat() - 0.5f) * 0.9f, oy = (r.nextFloat() - 0.5f) * 0.7f;
            float grow = 0.65f + 0.55f * (1f - (1f - p) * (1f - p));
            float alpha = (float) Math.pow(1f - p, 1.4) * env;

            if (pass == 0) {
            ps.pushPose();
            ps.translate(ox, oy, dist + p * 1.6f);
            ps.mulPose(Axis.ZP.rotationDegrees(roll));
            quadXY(vcp, ps, size * grow, size * 0.55f * grow, alpha, flip);
            ps.popPose();
            // thin afterimage a little further out
            ps.pushPose();
            ps.translate(ox * 1.3f, oy * 1.3f, dist + 1.2f + p * 2.4f);
            ps.mulPose(Axis.ZP.rotationDegrees(roll * 0.8f));
            quadXY(vcp, ps, size * 1.35f * grow, size * 0.3f * grow, alpha * 0.6f, flip);
            ps.popPose();
            } else {

            // shock ring racing forward
            ps.pushPose();
            ps.translate(0, 0, 0.8f + p * (reach * 0.8f));
            ps.mulPose(Axis.ZP.rotationDegrees(r.nextFloat() * 360f));
            float rr = 0.6f + p * 3.0f;
            quadXY(vcp, ps, rr, rr, alpha * 0.8f, false);
            ps.popPose();
            }
        }
        }
        ps.popPose();
    }

    // ------------------------------------------------------------------------------------------------ geometry

    /** Cone opening forward along +Z, scrolling swirl texture, drawn double-sided. */
    private void cone(VertexConsumer vc, PoseStack ps, float length, float t, float alpha, float widthScale, int s) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        if (a <= 2) {
            return;
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int seg = 20, rings = 8;
        float spin = t * 0.35f * (s == 0 ? 1 : -1);
        for (int j = 0; j < rings; j++) {
            float z0 = 0.4f + length * j / rings, z1 = 0.4f + length * (j + 1) / rings;
            float r0 = (0.25f + z0 * 0.30f) * widthScale, r1 = (0.25f + z1 * 0.30f) * widthScale;
            float f0 = j / (float) rings, f1 = (j + 1) / (float) rings;
            int al0 = (int) (a * Mth.clamp(f0 * 4f, 0.15f, 1f) * (1f - f0 * 0.7f));
            int al1 = (int) (a * Mth.clamp(f1 * 4f, 0.15f, 1f) * (1f - f1 * 0.7f));
            float v0 = f0 * 2f - t * 0.12f, v1 = f1 * 2f - t * 0.12f;
            for (int i = 0; i < seg; i++) {
                float p0 = (float) (i * Math.PI * 2 / seg) + spin + z0 * 0.4f;
                float p1 = (float) ((i + 1) * Math.PI * 2 / seg) + spin + z0 * 0.4f;
                float q0 = (float) (i * Math.PI * 2 / seg) + spin + z1 * 0.4f;
                float q1 = (float) ((i + 1) * Math.PI * 2 / seg) + spin + z1 * 0.4f;
                float u0 = i / (float) seg * 2f, u1 = (i + 1) / (float) seg * 2f;
                float[][] c = {
                        {Mth.cos(p0) * r0, Mth.sin(p0) * r0, z0, u0, v0, al0},
                        {Mth.cos(p1) * r0, Mth.sin(p1) * r0, z0, u1, v0, al0},
                        {Mth.cos(q1) * r1, Mth.sin(q1) * r1, z1, u1, v1, al1},
                        {Mth.cos(q0) * r1, Mth.sin(q0) * r1, z1, u0, v1, al1}};
                for (int k = 0; k < 4; k++) {
                    vert(vc, m, n, c[k][0], c[k][1], c[k][2], c[k][3], c[k][4], (int) c[k][5]);
                }
                for (int k = 3; k >= 0; k--) {
                    vert(vc, m, n, c[k][0], c[k][1], c[k][2], c[k][3], c[k][4], (int) c[k][5]);
                }
            }
        }
    }

    /** Flat quad in the local XY plane centred on the origin, double sided. flip mirrors the texture horizontally. */
    private void quadXY(VertexConsumer vc, PoseStack ps, float halfW, float halfH, float alpha, boolean flip) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        if (a <= 2) {
            return;
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float ul = flip ? 1f : 0f, ur = flip ? 0f : 1f;
        float[][] q = {{-halfW, -halfH, ul, 1f}, {halfW, -halfH, ur, 1f}, {halfW, halfH, ur, 0f}, {-halfW, halfH, ul, 0f}};
        for (int k = 0; k < 4; k++) {
            vert(vc, m, n, q[k][0], q[k][1], 0f, q[k][2], q[k][3], a);
        }
        for (int k = 3; k >= 0; k--) {
            vert(vc, m, n, q[k][0], q[k][1], 0f, q[k][2], q[k][3], a);
        }
    }

    /** Thin quad stretching along +Z in the local XZ plane (a speed line), double sided. */
    private void quadZ(VertexConsumer vc, PoseStack ps, float z0, float z1, float halfW, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        if (a <= 2) {
            return;
        }
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float[][] q = {{-halfW, z0, 0f, 1f}, {halfW, z0, 1f, 1f}, {halfW, z1, 1f, 0f}, {-halfW, z1, 0f, 0f}};
        for (int k = 0; k < 4; k++) {
            vert(vc, m, n, q[k][0], 0f, q[k][1], q[k][2], q[k][3], a);
        }
        for (int k = 3; k >= 0; k--) {
            vert(vc, m, n, q[k][0], 0f, q[k][1], q[k][2], q[k][3], a);
        }
        // second plane so the line is visible from every side
        for (int k = 0; k < 4; k++) {
            vert(vc, m, n, 0f, q[k][0], q[k][1], q[k][2], q[k][3], a);
        }
        for (int k = 3; k >= 0; k--) {
            vert(vc, m, n, 0f, q[k][0], q[k][1], q[k][2], q[k][3], a);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int a) {
        vc.vertex(m, x, y, z).color(255, 255, 255, a).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0f, 1f, 0f).endVertex();
    }
}
