package com.example.jjkspells.dragon.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dragon.entity.AscensionFxEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Golden ground circle, circles orbiting the caster, light column and the colossal beam. */
public class AscensionRenderer extends EntityRenderer<AscensionFxEntity> {
    private static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/environment/dragon_" + n + ".png");
    }

    private static final ResourceLocation CIRCLE = tex("circle_gold"), RUNE_RING = tex("rune_ring"), RING_WAVE = tex("ring_wave"),
            WAVE_BAND = tex("wave_band"), BEAM = tex("beam"), GLOW = tex("glow");
    private static final float[] GOLD = {1.0f, 0.82f, 0.36f}, PALE = {1.0f, 0.95f, 0.78f}, WHITE = {1f, 1f, 1f};

    public AscensionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(AscensionFxEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(AscensionFxEntity e) {
        return RING_WAVE;
    }

    private static float c01(float v) {
        return Mth.clamp(v, 0f, 1f);
    }

    private static VertexConsumer add(MultiBufferSource b, ResourceLocation t) {
        return b.getBuffer(RenderType.eyes(t));
    }

    @Override
    public void render(AscensionFxEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        float R = e.getRadius();
        int rise = e.getRise();
        int lockEnd = e.lockEnd();
        float dur = e.getDuration();
        float env = c01(t / 10f) * c01((dur - t) / 24f);
        Entity owner = e.getOwnerEntity();

        // ---- ground circle (10x10 by default)
        Draw.disc(add(buf, CIRCLE), ps, R, t * 1.2f, 0.03f, GOLD, env);
        Draw.disc(add(buf, RUNE_RING), ps, R * 0.72f, -t * 1.8f, 0.05f, PALE, env * 0.9f);
        Draw.disc(add(buf, GLOW), ps, R * 1.7f, 0f, 0.02f, GOLD, env * 0.45f);

        // ---- circles around the caster, plus a halo behind them
        if (owner != null) {
            Vec3 rel = owner.getPosition(pt).subtract(e.getPosition(pt));
            float ring = c01(t / 14f) * c01((lockEnd + 12f - t) / 12f);
            if (ring > 0.01f) {
                ps.pushPose();
                ps.translate(rel.x, rel.y, rel.z);
                float[] hy = {-0.9f, 0.55f, 1.95f};
                float[] rr = {2.4f, 1.9f, 1.4f};
                for (int k = 0; k < 3; k++) {
                    Draw.disc(add(buf, k == 1 ? CIRCLE : RUNE_RING), ps, rr[k], t * (3f + k * 1.5f) * (k % 2 == 0 ? 1 : -1), hy[k], k == 1 ? GOLD : PALE, 0.9f * ring);
                }
                for (int k = 0; k < 2; k++) {
                    ps.pushPose();
                    ps.translate(0, 0.6f, 0);
                    ps.mulPose(Axis.XP.rotationDegrees(k == 0 ? 68f : -68f));
                    ps.mulPose(Axis.YP.rotationDegrees(t * 2f * (k == 0 ? 1 : -1)));
                    Draw.disc(add(buf, CIRCLE), ps, 2.9f, 0f, 0f, GOLD, 0.7f * ring);
                    ps.popPose();
                }
                ps.pushPose();
                ps.translate(0, 0.9f, 0);
                ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
                ps.mulPose(Axis.ZP.rotationDegrees(t * 2.5f));
                Draw.xy(add(buf, CIRCLE), ps, 3.2f, 3.2f, PALE, 0.55f * ring);
                ps.popPose();
                ps.popPose();
            }
            // faint column of light linking the ground circle to the caster while rising
            if (t < rise + 6) {
                float h = Math.max(0.1f, (float) rel.y);
                Draw.band(add(buf, WAVE_BAND), ps, R * 0.92f, 0f, h, 40, 6f, t * 0.04f, GOLD, 0.35f * env, 0.05f * env);
            }
        }

        // ---- the beam of light, as wide as the circle
        float bt = t - rise;
        if (bt >= 0f) {
            float f = 1f - c01((t - (rise + e.getBeam())) / 24f);
            float g = c01(bt / 5f);
            float H = 320f;
            float a = f * g;
            Draw.band(add(buf, WAVE_BAND), ps, R, 0f, H, 48, 8f, -t * 0.05f, GOLD, 0.9f * a, 0.55f * a);
            Draw.band(add(buf, WAVE_BAND), ps, R * 0.72f, 0f, H, 40, 6f, t * 0.07f, PALE, 0.9f * a, 0.6f * a);
            Draw.band(add(buf, GLOW), ps, R * 0.4f, 0f, H, 24, 1f, 0f, WHITE, 0.95f * a, 0.8f * a);
            Draw.cross(add(buf, BEAM), ps, R * 0.95f, 0f, H, t * 2f, PALE, 0.8f * a, 15728880);
            Draw.disc(add(buf, GLOW), ps, R * 1.8f, 0f, 0.06f, PALE, 0.8f * a);
            float p = c01(bt / 22f);
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, R * 3.2f * p - 2f), R * 3.2f * p + 0.8f, 0.12f, 56, 1f, 0f, PALE, (1f - p) * f);
        }
    }
}
