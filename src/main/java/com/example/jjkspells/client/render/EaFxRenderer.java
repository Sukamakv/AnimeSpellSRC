package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.EaFxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
 * Draws Ea, the Sword of Rupture, and everything that comes of it, all from the entity's age:
 *   0-VORTEX_START      Ea in the caster's hand: three cylindrical sections turn faster and faster, red light on the lines.
 *   VORTEX_START-FIRE   a crimson-black vortex opens ahead of the sword; a funnel drinks the air toward it.
 *   FIRE-               Ea is thrust forward; a huge twisting wave of crimson and black tears down the line of fire.
 *   explosion           a blinding gold-red burst at the end of the wave.
 *   after               the vortex collapses inward and everything fades.
 * The frame used for drawing has +Y pointing along the line of fire, so tubes and discs are drawn with the plain FxDraw
 * helpers (a tube grows along Y; an annulus lies flat across it).
 */
public class EaFxRenderer extends EntityRenderer<EaFxEntity> {
    private static final ResourceLocation PLATE = JJKSpells.id("textures/entity/ea_plate.png");
    private static final ResourceLocation LINES = JJKSpells.id("textures/entity/ea_lines.png");
    private static final ResourceLocation V_DARK = JJKSpells.id("textures/entity/enuma_vortex_dark.png");
    private static final ResourceLocation V_RED = JJKSpells.id("textures/entity/enuma_vortex_red.png");
    private static final ResourceLocation W_DARK = JJKSpells.id("textures/entity/enuma_wave_dark.png");
    private static final ResourceLocation W_RED = JJKSpells.id("textures/entity/enuma_wave_red.png");
    private static final ResourceLocation GLOW = JJKSpells.id("textures/entity/fx_glow.png");
    private static final ResourceLocation SHOCK = JJKSpells.id("textures/entity/fx_shock.png");
    private static final ResourceLocation RING = JJKSpells.id("textures/entity/ea_ring.png");
    private static final ResourceLocation GUARD = JJKSpells.id("textures/entity/ea_guard.png");
    private static final ResourceLocation FIRE = JJKSpells.id("textures/entity/fx_fire.png");

    private static final float VORTEX_DIST = 5.5f;
    private static final float VORTEX_R = 7.5f;
    private static final float WAVE_R = 6.0f;
    private static final float WAVE_START = 1.4f;

    public EaFxRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(EaFxEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(EaFxEntity entity) {
        return PLATE;
    }

    @Override
    public void render(EaFxEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        float len = e.getLen();
        float explode = e.explosionTick();
        float waveSpeed = len > 0 ? len / EaFxEntity.TRAVEL : 0f;
        boolean fired = len > 0f && t >= EaFxEntity.FIRE;
        float sinceExp = explode >= 0 ? t - explode : -1f;

        ps.pushPose();
        float yRot = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float xRot = Mth.lerp(pt, e.xRotO, e.getXRot());
        ps.mulPose(Axis.YP.rotationDegrees(yRot));
        ps.mulPose(Axis.XP.rotationDegrees(90f - xRot));

        // ---- vortex size ---------------------------------------------------------------------------------
        float open = ease((t - EaFxEntity.VORTEX_START) / 55f);
        float collapse = sinceExp >= 40f ? ease((sinceExp - 40f) / EaFxEntity.COLLAPSE) : 0f;
        float vr = VORTEX_R * open * (1f - collapse) * (1f + 0.04f * Mth.sin(t * 0.7f));
        float vAlpha = 1f - 0.0f;

        // ---- the sword ------------------------------------------------------------------------------------
        if (collapse < 0.9f) {
            float thrust = fired ? lerp(ease((t - EaFxEntity.FIRE) / 5f), 0f, 1.1f) * (1f - ease((t - EaFxEntity.FIRE - 20f) / 20f)) : 0f;
            sword(ps, buf, t, thrust, 1f - collapse);
        }

        // ---- the vortex -----------------------------------------------------------------------------------
        if (vr > 0.1f) {
            vortex(ps, buf, t, vr, VORTEX_DIST, collapse);
        }

        // ---- the wave -------------------------------------------------------------------------------------
        if (fired) {
            float front = Math.min(len, (t - EaFxEntity.FIRE) * waveSpeed);
            float fade = sinceExp >= 0f ? 1f - Mth.clamp(sinceExp / 22f, 0f, 1f) : 1f;
            if (fade > 0.01f) {
                wave(ps, buf, t, front, len, fade);
            }
        }

        // ---- the explosion --------------------------------------------------------------------------------
        if (sinceExp >= 0f && sinceExp < 70f) {
            ps.pushPose();
            ps.translate(0, len, 0);
            explosion(ps, buf, sinceExp);
            ps.popPose();
        }
        // the reality collapse: rings fold inward, then a smaller second burst
        if (sinceExp >= 40f && sinceExp < 150f) {
            ps.pushPose();
            ps.translate(0, len, 0);
            for (int i = 0; i < 3; i++) {
                float k = ease((sinceExp - 40f) / 40f);
                float r = (30f - 24f * k) * (1f + 0.35f * i);
                float dir = i % 2 == 0 ? 1f : -1f;
                ps.pushPose();
                ps.mulPose(Axis.XP.rotationDegrees(i * 60f));
                ps.mulPose(Axis.ZP.rotationDegrees(i * 35f));
                FxDraw.annulus(ps.last(), buf.getBuffer(RenderType.eyes(RING)), r * 0.9f, r, 0f, 64, 6f, dir * sinceExp * 0.01f,
                        255, 130, 100, c(230f * (1f - Mth.clamp((sinceExp - 80f) / 14f, 0f, 1f))), FxDraw.FULL_BRIGHT);
                ps.popPose();
            }
            ps.popPose();
        }
        if (sinceExp >= 80f && sinceExp < 150f) {
            ps.pushPose();
            ps.translate(0, len, 0);
            ps.scale(0.45f, 0.45f, 0.45f);
            explosion(ps, buf, sinceExp - 80f);
            ps.popPose();
        }
        ps.popPose();
    }

    // =================================================================================================================
    //  EA
    // =================================================================================================================
    private void sword(PoseStack ps, MultiBufferSource buf, float t, float thrust, float vis) {
        float spin = Math.min(t, 80f);
        float a = spin * spin * 0.32f + Math.max(0f, t - 80f) * 52f;
        float heat = Mth.clamp(t / 60f, 0f, 1f);
        float pulse = 0.75f + 0.25f * Mth.sin(t * 0.9f);
        ps.pushPose();
        ps.translate(-0.55f, 0.35f + thrust, 0.5f);

        // plates (one buffer, all three sections + hilt + point)
        com.mojang.blaze3d.vertex.VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(PLATE));
        section(ps, vc, -0.55f, -0.18f, 0.055f, 0f, 120, 95, 60, 255, a * 0.3f, 4, false);
        section(ps, vc, -0.18f, 0.42f, 0.14f, 1f, 255, 205, 90, 255, a, 6, true);
        section(ps, vc, 0.47f, 1.07f, 0.14f, 2f, 235, 70, 50, 255, -a * 1.6f, 6, true);
        section(ps, vc, 1.12f, 1.72f, 0.14f, 3f, 255, 205, 90, 255, a * 2.2f, 6, true);
        // the point
        ps.pushPose();
        FxDraw.tube(ps.last(), vc, 1.74f, 2.2f, k -> 0.12f * (1f - k) * (1f - 0.2f * k), 10, 1, 3f, 1f, 0f, 0f, 255, 205, 90, 255f, 255f, FxDraw.FULL_BRIGHT);
        ps.popPose();

        // glowing lines over the top (additive)
        com.mojang.blaze3d.vertex.VertexConsumer lines = buf.getBuffer(RenderType.eyes(LINES));
        int la = c(255f * heat * pulse * vis);
        section(ps, lines, -0.18f, 0.42f, 0.147f, 1f, 255, 255, 255, la, a, 6, true);
        section(ps, lines, 0.47f, 1.07f, 0.147f, 2f, 255, 255, 255, la, -a * 1.6f, 6, true);
        section(ps, lines, 1.12f, 1.72f, 0.147f, 3f, 255, 255, 255, la, a * 2.2f, 6, true);

        // three circuit rings turning around the blade, and the gold winged guard
        for (int i = 0; i < 3; i++) {
            float ry = 0.35f + i * 0.62f;
            float rr = 0.42f + 0.12f * i + 0.05f * Mth.sin(t * 0.2f + i);
            ps.pushPose();
            ps.translate(0, ry, 0);
            ps.mulPose(Axis.XP.rotationDegrees(14f * (i - 1)));
            FxDraw.annulus(ps.last(), buf.getBuffer(RenderType.eyes(RING)), rr * 0.8f, rr, 0f, 40, 4f, (i % 2 == 0 ? 1 : -1) * t * 0.02f * (1 + i),
                    255, 140, 110, c(230f * heat * vis), FxDraw.FULL_BRIGHT);
            ps.popPose();
        }
        VertexConsumer gv = buf.getBuffer(RenderType.entityCutoutNoCull(GUARD));
        for (int sd = -1; sd <= 1; sd += 2) {
            GilDraw.quad(ps, gv, new net.minecraft.world.phys.Vec3(sd * 0.6, -0.12, 0), new net.minecraft.world.phys.Vec3(sd * 0.6, 0, 0),
                    new net.minecraft.world.phys.Vec3(0, 0.3 * (1.0 + 0.08 * Mth.sin(t * 0.3f)), 0), 255, 255, 255, c(255f * vis), FxDraw.FULL_BRIGHT);
        }

        // red glow around the blade
        ps.pushPose();
        ps.translate(0, 1.0f, 0);
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(),
                lerp(heat, 0.6f, 2.2f) * (0.9f + 0.1f * pulse), 0f, 255, 60, 40, c(200f * heat * vis), FxDraw.FULL_BRIGHT);
        ps.popPose();
        ps.popPose();
    }

    /** One turning cylindrical section (a short tube with a slight bulge in the middle). */
    private static void section(PoseStack ps, com.mojang.blaze3d.vertex.VertexConsumer vc, float y0, float y1, float r, float seed,
                                int cr, int cg, int cb, int ca, float angle, int segs, boolean bulge) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(angle));
        final float rr = r;
        final boolean b = bulge;
        FxDraw.tube(ps.last(), vc, y0, y1, k -> rr * (b ? 1f + 0.14f * Mth.sin(k * Mth.PI) : 1f), segs + 6, 2, 3f, 1f, seed * 0.25f, 0f,
                cr, cg, cb, ca, ca, FxDraw.FULL_BRIGHT);
        ps.popPose();
    }

    // =================================================================================================================
    //  VORTEX
    // =================================================================================================================
    private void vortex(PoseStack ps, MultiBufferSource buf, float t, float vr, float dist, float collapse) {
        float fade = 1f - collapse * 0.6f;
        ps.pushPose();
        ps.translate(0, dist, 0);
        ps.mulPose(Axis.YP.rotationDegrees(t * 3f * (1f + 3f * collapse)));
        PoseStack.Pose pose = ps.last();

        // the funnel drinking the air toward the vortex (open tube from the vortex back towards the caster)
        final float fr = vr;
        FxDraw.tube(pose, buf.getBuffer(RenderType.eyes(W_RED)), -dist * 0.55f, 0f, k -> fr * (0.12f + 0.88f * k * k), 28, 6,
                2f, 1.5f, t * 0.01f, t * 0.06f, 255, 120, 70, 0f, 200f * fade, FxDraw.FULL_BRIGHT);
        // dark body of the vortex: black spiral arms
        FxDraw.annulus(pose, buf.getBuffer(RenderType.entityTranslucent(V_DARK)), vr * 0.05f, vr, 0.0f, 48, 1f, -t * 0.008f,
                255, 255, 255, c(250f * fade), FxDraw.FULL_BRIGHT);
        FxDraw.annulus(pose, buf.getBuffer(RenderType.entityTranslucent(V_DARK)), vr * 0.05f, vr * 0.8f, 0.03f, 48, 2f, t * 0.012f,
                255, 255, 255, c(230f * fade), FxDraw.FULL_BRIGHT);
        // red arms on top, turning the other way
        FxDraw.annulus(pose, buf.getBuffer(RenderType.eyes(V_RED)), vr * 0.05f, vr * 1.04f, 0.07f, 48, 1f, t * 0.02f,
                255, 255, 255, c(255f * fade), FxDraw.FULL_BRIGHT);
        FxDraw.annulus(pose, buf.getBuffer(RenderType.eyes(V_RED)), vr * 0.05f, vr * 0.7f, 0.11f, 48, 2f, -t * 0.03f,
                255, 190, 150, c(210f * fade), FxDraw.FULL_BRIGHT);
        // singularity: a black heart ringed with fire
        FxDraw.ellipsoid(pose, buf.getBuffer(RenderType.entityTranslucent(V_DARK)), vr * 0.2f, vr * 0.2f, vr * 0.2f, 12, 20,
                2f, 1f, 0f, 0f, 255, 255, 255, c(255f * fade), FxDraw.FULL_BRIGHT);
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(),
                vr * 0.5f, 0f, 255, 70, 40, c(120f * fade), FxDraw.FULL_BRIGHT);
        ps.popPose();
    }

    // =================================================================================================================
    //  WAVE
    // =================================================================================================================
    private void wave(PoseStack ps, MultiBufferSource buf, float t, float front, float len, float fade) {
        float since = t - EaFxEntity.FIRE;
        float widen = ease(since / 6f);
        final float R = WAVE_R * widen;
        final float f = front;
        PoseStack.Pose pose = ps.last();
        float vRep = Math.max(1f, front / 9f);
        float scroll = since * 0.09f;

        // radius along the beam: narrow at the sword, full in the middle, a rounded head at the front
        com.mojang.blaze3d.vertex.VertexConsumer dark = buf.getBuffer(RenderType.entityTranslucent(W_DARK));
        FxDraw.tube(pose, dark, WAVE_START, front, k -> {
            float body = 0.30f + 0.70f * smooth(k * 6f);
            float head = 1f - 0.85f * (float) Math.pow(Math.max(0f, (k - 0.93f) / 0.07f), 2.0);
            return R * 1.08f * body * head;
        }, 32, Math.max(8, (int) (front / 4f)), 3f, vRep, since * 0.004f, -scroll * 0.4f, 255, 255, 255, 210f * fade, 235f * fade, FxDraw.FULL_BRIGHT);

        com.mojang.blaze3d.vertex.VertexConsumer red = buf.getBuffer(RenderType.eyes(W_RED));
        FxDraw.tube(pose, red, WAVE_START, front, k -> {
            float body = 0.30f + 0.70f * smooth(k * 6f);
            float head = 1f - 0.85f * (float) Math.pow(Math.max(0f, (k - 0.93f) / 0.07f), 2.0);
            return R * 0.92f * body * head;
        }, 32, Math.max(8, (int) (front / 4f)), 3f, vRep, -since * 0.006f, -scroll, 255, 255, 255, 200f * fade, 255f * fade, FxDraw.FULL_BRIGHT);

        com.mojang.blaze3d.vertex.VertexConsumer core = buf.getBuffer(RenderType.eyes(W_RED));
        FxDraw.tube(pose, core, WAVE_START, front, k -> {
            float body = 0.30f + 0.70f * smooth(k * 6f);
            float head = 1f - 0.9f * (float) Math.pow(Math.max(0f, (k - 0.95f) / 0.05f), 2.0);
            return R * 0.46f * body * head;
        }, 20, Math.max(8, (int) (front / 4f)), 2f, vRep * 1.6f, since * 0.01f, -scroll * 2.2f, 255, 235, 200, 220f * fade, 255f * fade, FxDraw.FULL_BRIGHT);

        // shock rings racing along the beam
        for (int i = 0; i < 4; i++) {
            float y = ((since * 3.2f + i * 23f) % Math.max(10f, front));
            if (y < WAVE_START || y > front) continue;
            float k = y / Math.max(1f, front);
            float rr = R * (0.30f + 0.70f * smooth(k * 6f)) * 1.25f;
            ps.pushPose();
            ps.translate(0, y, 0);
            FxDraw.annulus(ps.last(), buf.getBuffer(RenderType.eyes(SHOCK)), rr * 0.82f, rr, 0f, 40, rr * 0.4f, since * 0.01f,
                    255, 120, 80, c(150f * fade), FxDraw.FULL_BRIGHT);
            ps.popPose();
        }

        // bright head
        ps.pushPose();
        ps.translate(0, front, 0);
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(),
                R * 1.5f, since * 7f, 255, 120, 80, c(210f * fade), FxDraw.FULL_BRIGHT);
        ps.popPose();
        // lightning-like glow at the source
        ps.pushPose();
        ps.translate(0, WAVE_START, 0);
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(),
                lerp(ease(since / 8f), 1.5f, 5.5f), 0f, 255, 220, 170, c(240f * (1f - ease(since / 40f)) * fade), FxDraw.FULL_BRIGHT);
        ps.popPose();
    }

    private static float smooth(float x) {
        return FxDraw.smooth(x);
    }

    // =================================================================================================================
    //  EXPLOSION (drawn with the origin at the end of the wave)
    // =================================================================================================================
    private void explosion(PoseStack ps, MultiBufferSource buf, float e) {
        PoseStack.Pose pose = ps.last();
        float a = 1f - Mth.clamp(e / 62f, 0f, 1f);
        // the blinding flash
        if (e < 14f) {
            FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(),
                    lerp(ease(e / 5f), 12f, 90f), e * 4f, 255, 240, 215, c(255f * (1f - e / 14f)), FxDraw.FULL_BRIGHT);
        }
        // gold-white shell
        float d1 = 38f * ease(e / 16f);
        FxDraw.ellipsoid(pose, buf.getBuffer(RenderType.eyes(FIRE)), d1, d1 * 0.85f, d1, 16, 36, 3f, 2f, e * 0.004f, -e * 0.01f,
                255, 215, 120, c(235f * a * ease(e / 3f)), FxDraw.FULL_BRIGHT);
        // crimson shell
        float d2 = 52f * ease(e / 20f);
        FxDraw.ellipsoid(pose, buf.getBuffer(RenderType.eyes(SHOCK)), d2, d2 * 0.85f, d2, 14, 36, 6f, 1f, 0f, 0f,
                255, 70, 50, c(150f * a), FxDraw.FULL_BRIGHT);
        // black smoke core inside
        float d3 = 24f * ease(e / 30f);
        FxDraw.ellipsoid(pose, buf.getBuffer(com.example.jjkspells.client.SoftRender.entity(V_DARK)), d3, d3 * 0.8f, d3, 12, 24, 3f, 1f, e * 0.002f, 0f,
                255, 255, 255, c(200f * a * ease(e / 8f)), FxDraw.FULL_BRIGHT);
        // flat shock ring perpendicular to the beam
        float R = 14f + 60f * ease(e / 26f);
        FxDraw.annulus(pose, buf.getBuffer(RenderType.eyes(SHOCK)), R - 10f, R, 0f, 56, R * 0.3f, 0f,
                255, 150, 100, c(210f * (1f - ease(e / 30f))), FxDraw.FULL_BRIGHT);
    }
}
