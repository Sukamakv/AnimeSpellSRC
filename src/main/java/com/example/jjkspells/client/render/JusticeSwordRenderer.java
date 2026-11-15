package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.JusticeSwordEntity;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Demacian Justice visuals: a dark disc on the ground, a spinning golden rune, the translucent spectral sword
 * (two crossed vertical sheets) and the pillar of light after impact. Everything glowing is additive.
 */
public class JusticeSwordRenderer extends EntityRenderer<JusticeSwordEntity> {
    private static final ResourceLocation SWORD = JJKSpells.id("textures/entity/justice_sword.png");
    private static final ResourceLocation RUNE = JJKSpells.id("textures/entity/justice_rune.png");
    private static final ResourceLocation BEAM = JJKSpells.id("textures/entity/justice_beam.png");
    private static final ResourceLocation DARK = JJKSpells.id("textures/entity/justice_dark.png");

    public JusticeSwordRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(JusticeSwordEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(JusticeSwordEntity e) {
        return SWORD;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int a255(float v) {
        return Math.max(0, Math.min(255, (int) (v * 255f)));
    }

    @Override
    public void render(JusticeSwordEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float age = e.tickCount + pt;
        int crash = JusticeSwordEntity.CRASH;
        int end = JusticeSwordEntity.END;

        // ---- timeline ---------------------------------------------------------------------------------
        float runeIn = clamp01(age / 14f);
        float postFade = clamp01((age - crash) / (float) (end - crash));          // 0 before impact, 1 at the end
        float runeAlpha = runeIn * (1f - clamp01((age - crash) / 16f));
        float darkAlpha = clamp01(age / 12f) * (1f - clamp01((age - crash) / 20f));
        float swordIn = clamp01((age - 6f) / 16f);
        float swordAlpha = swordIn * (1f - postFade);
        float fall = clamp01((age - JusticeSwordEntity.FALL_START) / (float) (crash - JusticeSwordEntity.FALL_START));
        float tipHeight = Mth.lerp(fall * fall, JusticeSwordEntity.HANG_HEIGHT, -0.4f);
        float beamAlpha = age >= crash ? 1f - clamp01((age - crash) / 16f) : 0f;
        float beamWidth = 4.5f * (1f - 0.6f * clamp01((age - crash) / 16f));

        // ---- dark disc: the battlefield dims around the target -------------------------------------------
        if (darkAlpha > 0.01f) {
            ps.pushPose();
            ps.translate(0, 0.04, 0);
            flatQuad(buf.getBuffer(RenderType.entityTranslucent(DARK)), ps, 13f, a255(darkAlpha * 0.75f), light);
            ps.popPose();
        }

        // ---- rune on the ground ---------------------------------------------------------------------------
        if (runeAlpha > 0.01f) {
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(RUNE));
            ps.pushPose();
            ps.translate(0, 0.07, 0);
            ps.mulPose(Axis.YP.rotationDegrees(age * 4f));
            flatQuad(vc, ps, 4.6f * (0.7f + 0.3f * runeIn), a255(runeAlpha), LightTexture.FULL_BRIGHT);
            ps.popPose();
            ps.pushPose();
            ps.translate(0, 0.09, 0);
            ps.mulPose(Axis.YP.rotationDegrees(-age * 7f));
            flatQuad(vc, ps, 3.0f * (0.7f + 0.3f * runeIn), a255(runeAlpha * 0.8f), LightTexture.FULL_BRIGHT);
            ps.popPose();
        }

        // ---- the sword -----------------------------------------------------------------------------------
        if (swordAlpha > 0.01f) {
            float h = 18f * (0.9f + 0.1f * swordIn);
            float w = 3.6f * (1f - 0.35f * postFade);
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(SWORD));
            for (int i = 0; i < 2; i++) {
                ps.pushPose();
                ps.translate(0, tipHeight, 0);
                ps.mulPose(Axis.YP.rotationDegrees(age * 2.5f + i * 90f));
                verticalQuad(vc, ps, w, h, a255(swordAlpha * 0.85f));
                ps.popPose();
            }
        }

        // ---- the pillar of light on impact ------------------------------------------------------------------
        if (beamAlpha > 0.01f) {
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(BEAM));
            for (int i = 0; i < 3; i++) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(i * 60f + age * 3f));
                verticalQuad(vc, ps, beamWidth, 42f, a255(beamAlpha * 0.9f));
                ps.popPose();
            }
        }
    }

    /** Horizontal square of half-size r centred on the origin, visible from above and below. */
    private void flatQuad(VertexConsumer vc, PoseStack ps, float r, int alpha, int light) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        // counter-clockwise seen from above
        v(vc, m, n, -r, 0, r, 0, 1, alpha, light);
        v(vc, m, n, r, 0, r, 1, 1, alpha, light);
        v(vc, m, n, r, 0, -r, 1, 0, alpha, light);
        v(vc, m, n, -r, 0, -r, 0, 0, alpha, light);
        // and the other way round, so it also shows from below / when the pipeline culls back faces
        v(vc, m, n, -r, 0, -r, 0, 0, alpha, light);
        v(vc, m, n, r, 0, -r, 1, 0, alpha, light);
        v(vc, m, n, r, 0, r, 1, 1, alpha, light);
        v(vc, m, n, -r, 0, r, 0, 1, alpha, light);
    }

    /** Vertical sheet in the local XY plane, bottom edge on y=0, drawn from both sides. */
    private void verticalQuad(VertexConsumer vc, PoseStack ps, float halfWidth, float height, int alpha) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int l = LightTexture.FULL_BRIGHT;
        v(vc, m, n, -halfWidth, 0, 0, 0, 1, alpha, l);
        v(vc, m, n, halfWidth, 0, 0, 1, 1, alpha, l);
        v(vc, m, n, halfWidth, height, 0, 1, 0, alpha, l);
        v(vc, m, n, -halfWidth, height, 0, 0, 0, alpha, l);
        v(vc, m, n, -halfWidth, height, 0, 0, 0, alpha, l);
        v(vc, m, n, halfWidth, height, 0, 1, 0, alpha, l);
        v(vc, m, n, halfWidth, 0, 0, 1, 1, alpha, l);
        v(vc, m, n, -halfWidth, 0, 0, 0, 1, alpha, l);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float vv, int alpha, int light) {
        vc.vertex(m, x, y, z)
                .color(255, 255, 255, alpha)
                .uv(u, vv)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(n, 0f, 1f, 0f)
                .endVertex();
    }
}
