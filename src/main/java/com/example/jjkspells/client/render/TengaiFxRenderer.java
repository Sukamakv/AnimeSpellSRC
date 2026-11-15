package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.TengaiFxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import static com.example.jjkspells.client.render.FxDraw.c;
import static com.example.jjkspells.client.render.FxDraw.ease;
import static com.example.jjkspells.client.render.FxDraw.lerp;

/**
 * Draws the two big visuals of Tengai Shinsei, all from procedural geometry (no particles, so it stays smooth and
 * cheap even though it is huge):
 *
 * IMPACT - the first meteor's landing: white flash, shock dome, two rolling rings of dust and a billow of dust.
 * NUKE   - the final explosion: blinding flash, a shock dome racing outward, a dust wall along the ground, a white-hot
 *          fireball that climbs and cools into a rolling mushroom head, a stem that rises with it, a condensation
 *          ring, and a dust skirt. Everything fades out over the last few seconds.
 */
public class TengaiFxRenderer extends EntityRenderer<TengaiFxEntity> {
    private static final ResourceLocation FIRE = JJKSpells.id("textures/entity/fx_fire.png");
    private static final ResourceLocation SMOKE = JJKSpells.id("textures/entity/fx_smoke.png");
    private static final ResourceLocation GLOW = JJKSpells.id("textures/entity/fx_glow.png");
    private static final ResourceLocation SHOCK = JJKSpells.id("textures/entity/fx_shock.png");

    public TengaiFxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(TengaiFxEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(TengaiFxEntity entity) {
        return SMOKE;
    }

    @Override
    public void render(TengaiFxEntity fx, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffer, int packedLight) {
        float t = fx.tickCount + partialTick;
        // smoke is lit by the world, but always by full sky light so it is never pitch black inside a crater
        int smokeLight = LightTexture.pack(Math.max(LightTexture.block(packedLight), 6), 15);

        ps.pushPose();
        float s = fx.getScale();
        ps.scale(s, s, s);
        if (fx.getKind() == TengaiFxEntity.KIND_NUKE) {
            nuke(fx, t, ps, buffer, smokeLight);
        } else {
            impact(fx, t, ps, buffer, smokeLight);
        }
        ps.popPose();
        super.render(fx, yaw, partialTick, ps, buffer, packedLight);
    }

    // =================================================================================================================
    //  IMPACT (first meteor lands)
    // =================================================================================================================
    private void impact(TengaiFxEntity fx, float t, PoseStack ps, MultiBufferSource buf, int smokeLight) {
        float life = fx.getLife();
        float fade = Mth.clamp((life - t) / 25f, 0f, 1f);
        PoseStack.Pose pose = ps.last();

        // dust: a billow that swells and rises, and two rolling rings racing outwards
        float swell = ease(t / 42f);
        float bw = 22f + 24f * swell;
        FxDraw.ellipsoid(pose, buf.getBuffer(com.example.jjkspells.client.SoftRender.entity(SMOKE)), bw, 9f + 10f * swell, bw, 18, 32,
                3f, 2f, t * 0.002f, -t * 0.004f, 150, 130, 112, c(190f * (1f - ease(t / 72f)) * fade * ease(t / 5f)), smokeLight);

        for (int ring = 0; ring < 2; ring++) {
            float rt = t - ring * 5f;
            if (rt <= 0f) {
                continue;
            }
            float R = 16f + (36f + 14f * ring) * ease(rt / 28f);
            float h = lerp(ease(rt / 40f), 11f, 3.5f);
            float a = 205f * (1f - ease(rt / 62f)) * fade;
            final float rr = R;
            FxDraw.tube(pose, buf.getBuffer(com.example.jjkspells.client.SoftRender.entity(SMOKE)), -0.5f, h, k -> rr * (0.97f + 0.05f * k), 40, 3,
                    Math.max(4f, R * 0.17f), 1f, rt * 0.003f, -rt * 0.01f, 150, 132, 114, a, a * 0.15f, smokeLight);
        }

        // the shock: flash, dome, glowing edge of the first ring
        if (t < 14f) {
            FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(),
                    lerp(ease(t / 5f), 10f, 78f), t * 6f, 255, 235, 200, c(255f * (1f - t / 14f)), FxDraw.FULL_BRIGHT);
        }
        if (t < 30f) {
            float d = 78f * ease(t / 26f);
            FxDraw.ellipsoid(pose, buf.getBuffer(RenderType.eyes(SHOCK)), d, d * 0.8f, d, 14, 36, 6f, 1f, 0f, 0f,
                    255, 235, 205, c(120f * (1f - ease(t / 28f))), FxDraw.FULL_BRIGHT);
            float R = 16f + 36f * ease(t / 28f);
            FxDraw.annulus(pose, buf.getBuffer(RenderType.eyes(SHOCK)), R - 9f, R + 2f, 0.4f, 48, R * 0.3f, 0f,
                    255, 225, 180, c(170f * (1f - ease(t / 28f))), FxDraw.FULL_BRIGHT);
        }
    }

    // =================================================================================================================
    //  NUKE (the two meteors collide)
    // =================================================================================================================
    private void nuke(TengaiFxEntity fx, float t, PoseStack ps, MultiBufferSource buf, int smokeLight) {
        final float life = fx.getLife();
        final float fade = Mth.clamp((life - t) / 100f, 0f, 1f);
        final float contact = fx.getAux();
        PoseStack.Pose pose = ps.last();

        // ---- the numbers that drive everything ------------------------------------------------------------
        float rise = 118f * (1f - (float) Math.exp(-t / 72f));      // how far the head has climbed from the blast point
        float cy = contact + rise;                                  // height of the head's centre
        float rf = 27f * ease(t / 24f) + 17f * ease(t / 170f);      // fireball radius
        float flatten = ease((t - 18f) / 90f);
        float capRx = rf * (1.0f + 0.38f * flatten);                // head: wide and squat once it has settled
        float capRy = rf * (1.0f - 0.42f * flatten);
        float heat = 1f - ease((t - 8f) / 160f);                    // 1 = white-hot, 0 = cooled to smoke
        float hotter = (float) Math.pow(heat, 0.8);

        int fireG = c(80f + 170f * heat);
        int fireB = c(25f + 190f * heat * heat);

        // smoke colour: lit orange at first, then dark grey-brown
        float cool = ease((t - 12f) / 90f);
        int smR = c(lerp(cool, 255f, 78f)), smG = c(lerp(cool, 175f, 68f)), smB = c(lerp(cool, 110f, 62f));

        float stemTop = Math.max(2f, cy - capRy * 0.6f);
        float rs = 8.5f + 5f * ease(t / 100f);                      // stem radius
        float stemGrow = ease((t - 4f) / 20f);

        // =========================== smoke layers (drawn first, fire glows on top) ===========================
        // dust skirt hugging the ground
        float skirt = 20f + 62f * ease(t / 55f);
        FxDraw.ellipsoid(pose, buf.getBuffer(com.example.jjkspells.client.SoftRender.entity(SMOKE)), skirt, 11f + 9f * ease(t / 70f), skirt, 16, 36,
                4f, 2f, t * 0.001f, -t * 0.004f, 125, 108, 90, c(205f * (1f - ease((t - 30f) / 250f)) * fade * ease(t / 6f)), smokeLight);

        // stem
        final float rsF = rs;
        FxDraw.Profile stemProfile = k -> rsF * (1f + 1.35f * (float) Math.pow(1f - k, 4)) + rsF * 0.9f * (float) Math.pow(k, 5)
                + 0.9f * Mth.sin(k * 14f + t * 0.12f);
        FxDraw.tube(pose, buf.getBuffer(com.example.jjkspells.client.SoftRender.entity(SMOKE)), 0f, stemTop * stemGrow, stemProfile, 28, 14,
                4f, Math.max(1f, stemTop / 18f), 0f, -t * 0.012f, smR, smG, smB, 235f * fade * stemGrow, 235f * fade * stemGrow, smokeLight);

        // the rolling rim of the head
        float rimA = 235f * ease((t - 26f) / 36f) * fade;
        ps.pushPose();
        ps.translate(0f, cy - capRy * 0.18f, 0f);
        FxDraw.torus(ps.last(), buf.getBuffer(com.example.jjkspells.client.SoftRender.entity(SMOKE)), capRx * 0.80f, capRy * 0.58f, 0.85f, 36, 14,
                6f, 2f, 0f, -t * 0.025f, c(smR * 1.15f), c(smG * 1.12f), c(smB * 1.1f), c(rimA), smokeLight);
        ps.popPose();

        // the head
        ps.pushPose();
        ps.translate(0f, cy, 0f);
        FxDraw.ellipsoid(ps.last(), buf.getBuffer(com.example.jjkspells.client.SoftRender.entity(SMOKE)), capRx * 1.03f, capRy * 1.05f, capRx * 1.03f, 22, 40,
                4f, 3f, t * 0.002f, -t * 0.005f, smR, smG, smB, c(240f * ease((t - 14f) / 40f) * fade), smokeLight);
        ps.popPose();

        // =========================== fire layers (additive) ===========================
        // fire inside the head
        ps.pushPose();
        ps.translate(0f, cy, 0f);
        FxDraw.ellipsoid(ps.last(), buf.getBuffer(RenderType.eyes(FIRE)), capRx * 0.96f, capRy * 0.98f, capRx * 0.96f, 22, 40,
                3f, 2f, t * 0.004f, -t * 0.008f, 255, fireG, fireB, c(255f * hotter * fade), FxDraw.FULL_BRIGHT);
        ps.popPose();

        // hot stem
        float stemHot = 1f - ease((t - 14f) / 120f);
        FxDraw.Profile hotProfile = k -> stemProfile.radius(k) * 0.9f;
        FxDraw.tube(pose, buf.getBuffer(RenderType.eyes(FIRE)), 0f, stemTop * stemGrow, hotProfile, 24, 12,
                3f, Math.max(1f, stemTop / 22f), 0f, -t * 0.02f, 255, fireG, fireB, 230f * stemHot * fade, 120f * stemHot * fade, FxDraw.FULL_BRIGHT);

        // glow blooms: one at the blast point, one at the head
        ps.pushPose();
        ps.translate(0f, contact, 0f);
        float flashA = 255f * (1f - ease(t / 20f));
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(),
                lerp(ease(t / 6f), 20f, 190f), t * 3f, 255, 245, 225, c(flashA), FxDraw.FULL_BRIGHT);
        ps.popPose();
        ps.pushPose();
        ps.translate(0f, cy, 0f);
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(),
                capRx * 2.4f, -t * 0.6f, 255, 150, 70, c(170f * hotter * hotter * fade), FxDraw.FULL_BRIGHT);
        ps.popPose();

        // =========================== the shock ===========================
        if (t < 48f) {
            float k = ease(t / 44f);
            float d = 200f * k;
            ps.pushPose();
            ps.translate(0f, contact * 0.4f, 0f);
            FxDraw.ellipsoid(ps.last(), buf.getBuffer(RenderType.eyes(SHOCK)), d, d * 0.85f, d, 16, 48, 8f, 1f, 0f, 0f,
                    255, 242, 222, c(135f * (1f - k)), FxDraw.FULL_BRIGHT);
            float d2 = 200f * ease((t - 4f) / 44f);
            FxDraw.ellipsoid(ps.last(), buf.getBuffer(RenderType.eyes(SHOCK)), d2, d2 * 0.85f, d2, 16, 48, 6f, 1f, 0.3f, 0f,
                    255, 230, 200, c(80f * (1f - ease((t - 4f) / 44f))), FxDraw.FULL_BRIGHT);
            ps.popPose();
        }

        // wall of dust racing along the ground, with a glowing edge
        if (t < 95f) {
            float k = ease(t / 70f);
            final float R = 22f + 150f * k;
            float h = lerp(k, 22f, 7f);
            float a = 215f * (1f - ease(t / 95f)) * fade;
            FxDraw.tube(pose, buf.getBuffer(com.example.jjkspells.client.SoftRender.entity(SMOKE)), -1f, h, q -> R * (0.96f + 0.06f * q), 56, 3,
                    R * 0.14f, 1f, t * 0.002f, -t * 0.012f, 148, 130, 112, a, a * 0.1f, smokeLight);
        }
        if (t < 60f) {
            float k = ease(t / 56f);
            float R = 22f + 150f * k;
            FxDraw.annulus(pose, buf.getBuffer(RenderType.eyes(SHOCK)), R - 14f, R + 3f, 0.5f, 64, R * 0.2f, 0f,
                    255, 220, 170, c(190f * (1f - k)), FxDraw.FULL_BRIGHT);
        }

        // condensation ring around the head
        if (t > 36f && t < 190f) {
            float ph = (t - 36f) / 154f;
            float a = 150f * Mth.sin((float) Math.PI * ph) * fade;
            float ringR = capRx * 1.12f + 26f * ph;
            ps.pushPose();
            ps.translate(0f, cy - capRy * 0.55f, 0f);
            FxDraw.annulus(ps.last(), buf.getBuffer(RenderType.entityTranslucentEmissive(SHOCK)), ringR - 8f, ringR + 8f, 0f, 48,
                    ringR * 0.12f, t * 0.002f, 235, 232, 225, c(a), FxDraw.FULL_BRIGHT);
            ps.popPose();
        }
    }
}
