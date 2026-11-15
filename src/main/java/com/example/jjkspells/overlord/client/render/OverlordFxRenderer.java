package com.example.jjkspells.overlord.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.overlord.config.OverlordConfig;
import com.example.jjkspells.overlord.entity.OverlordFxEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Draws every visual of the seven controller-driven spells (see OverlordFxEntity.getKind()). */
public class OverlordFxRenderer extends EntityRenderer<OverlordFxEntity> {
    private static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/environment/" + n + ".png");
    }

    private static final ResourceLocation CIRCLE_SUMMON = tex("circle_summon"), CIRCLE_DEATH = tex("circle_death"), CIRCLE_GOAT = tex("circle_goat"),
            RUNE_RING = tex("rune_ring"), RING_WAVE = tex("ring_wave"), WAVE_BAND = tex("wave_band"), BEAM = tex("beam"),
            SPHERE_DARK = tex("sphere_dark"), SPHERE_GLOW = tex("sphere_glow"), SPHERE_GOLD = tex("sphere_gold"), ACCRETION = tex("accretion"),
            FACE = tex("ghost_face"), HAND = tex("bone_hand"), RIBBON = tex("ribbon"), GLOW = tex("glow"), SCORCH = tex("scorch"),
            HAND_OPEN = tex("claw_open"), HAND_CLENCH = tex("claw_clench"), HEART = tex("heart");

    private static final float[] PURPLE = {0.72f, 0.35f, 1.0f}, DEEP_PURPLE = {0.5f, 0.15f, 0.9f}, CRIMSON = {1.0f, 0.12f, 0.2f},
            GOLD = {1.0f, 0.82f, 0.38f}, WHITE_GOLD = {1.0f, 0.95f, 0.75f}, BLACK = {0.02f, 0.0f, 0.04f}, WHITE = {1f, 1f, 1f}, GREEN = {0.35f, 0.9f, 0.5f};

    public OverlordFxRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(OverlordFxEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(OverlordFxEntity e) {
        return RING_WAVE;
    }

    private static float fade(float t, float in, float dur, float out) {
        return Mth.clamp(t / in, 0f, 1f) * Mth.clamp((dur - t) / out, 0f, 1f);
    }

    private static float c01(float v) {
        return Mth.clamp(v, 0f, 1f);
    }

    private static VertexConsumer add(MultiBufferSource b, ResourceLocation t) {
        return b.getBuffer(RenderType.eyes(t));
    }

    private static VertexConsumer solid(MultiBufferSource b, ResourceLocation t) {
        return b.getBuffer(RenderType.entityTranslucent(t));
    }

    private static void billboard(PoseStack ps) {
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
    }

    @Override
    public void render(OverlordFxEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        float dur = e.getDuration();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(e.getPosition(pt));
        ps.pushPose();
        switch (e.getKind()) {
            case OverlordFxEntity.SUMMON -> summon(e, t, dur, ps, buf, light);
            case OverlordFxEntity.BANSHEE -> banshee(e, t, dur, ps, buf);
            case OverlordFxEntity.HOLE -> hole(e, t, dur, ps, buf);
            case OverlordFxEntity.GOAL -> goal(e, t, dur, ps, buf, light);
            case OverlordFxEntity.FALLEN -> fallen(e, t, dur, ps, buf);
            case OverlordFxEntity.SLASH -> slash(e, t, ps, buf, cam);
            case OverlordFxEntity.GOAT -> goat(e, t, dur, ps, buf);
            default -> grasp(e, t, pt, ps, buf);
        }
        ps.popPose();
    }

    // ------------------------------------------------------------------------------------------ 1

    private void summon(OverlordFxEntity e, float t, float dur, PoseStack ps, MultiBufferSource buf, int light) {
        float R = e.getRadius() * 1.3f;
        float env = fade(t, 6f, dur, 14f);
        Draw.disc(add(buf, CIRCLE_SUMMON), ps, R, t * 1.5f, 0.03f, PURPLE, env * 0.95f);
        Draw.disc(add(buf, RUNE_RING), ps, R * 0.62f, -t * 2.5f, 0.05f, DEEP_PURPLE, env * 0.85f);
        VertexConsumer hands = solid(buf, HAND);
        for (int i = 0; i < 14; i++) {
            RandomSource r = RandomSource.create(i * 7727L + e.getId());
            float a = r.nextFloat() * Mth.TWO_PI, d = 0.3f + r.nextFloat() * R * 0.75f;
            float start = 2f + r.nextInt(10);
            float p = c01((t - start) / 9f) * (1f - c01((t - 30f) / 10f));
            if (p <= 0.01f) {
                continue;
            }
            ps.pushPose();
            ps.translate(Mth.cos(a) * d, 0, Mth.sin(a) * d);
            Draw.cross(hands, ps, 0.42f, -1.3f + p * 1.3f, 1.3f, r.nextFloat() * 180f, WHITE, 1f, light);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------------------------------ 2

    private void banshee(OverlordFxEntity e, float t, float dur, PoseStack ps, MultiBufferSource buf) {
        float R = e.getRadius();
        float form = c01(t / 10f);
        float wave = c01((t - 10f) / 14f);
        // aura around the caster's skull
        if (t < 14f) {
            ps.pushPose();
            ps.translate(0, 1.95f, 0);
            billboard(ps);
            Draw.xy(add(buf, GLOW), ps, 1.1f + 0.15f * Mth.sin(t), 1.1f + 0.15f * Mth.sin(t), DEEP_PURPLE, 0.8f * form * (1f - c01((t - 8f) / 6f)));
            ps.popPose();
        }
        // ring of spectral faces, rising, then flying out with the wave
        VertexConsumer faces = add(buf, FACE);
        int n = 12;
        for (int i = 0; i < n; i++) {
            float ang = Mth.TWO_PI * i / n + t * 0.02f;
            float rad = Mth.lerp(wave, 1.9f, R * 0.95f);
            float y = 1.0f + 0.4f * Mth.sin(t * 0.15f + i) + wave * 0.6f;
            float alpha = form * (1f - c01((wave - 0.35f) / 0.65f)) * 0.95f;
            ps.pushPose();
            ps.translate(Mth.cos(ang) * rad, y, Mth.sin(ang) * rad);
            ps.mulPose(Axis.YP.rotationDegrees(90f - ang * Mth.RAD_TO_DEG));
            float s = 0.55f + 0.5f * wave;
            Draw.xy(faces, ps, s, s * 1.2f, PURPLE, alpha);
            ps.popPose();
        }
        if (wave > 0f) {
            float wr = R * wave;
            float a = 1f - wave * wave * wave;
            float[] scrollCol = PURPLE;
            ps.pushPose();
            Draw.band(solid(buf, WAVE_BAND), ps, wr, 0f, 2.8f, 40, 6f, t * 0.05f, BLACK, a * 0.75f, a * 0.2f);
            Draw.band(add(buf, WAVE_BAND), ps, wr, 0f, 2.8f, 40, 6f, -t * 0.08f, scrollCol, a * 0.9f, a * 0.1f);
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, wr - 1.4f), wr + 0.4f, 0.08f, 40, 1f, 0f, PURPLE, a);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------------------------------ 3

    private void hole(OverlordFxEntity e, float t, float dur, PoseStack ps, MultiBufferSource buf) {
        int collapse = e.getMark();
        float warn = (float) (OverlordConfig.BLACK_HOLE.warningSeconds.get() * 20.0);
        float size = c01(t / 8f);
        float after = c01((t - collapse) / 5f);
        float r = 1.25f * size * (1f - after);
        float shake = (t > collapse - warn && t < collapse) ? 0.07f : 0f;
        ps.pushPose();
        ps.translate((float) Math.sin(t * 7.1f) * shake, (float) Math.sin(t * 9.3f) * shake, (float) Math.cos(t * 8.2f) * shake);
        if (r > 0.01f) {
            Draw.sphere(solid(buf, SPHERE_DARK), ps, r, 14, 24, t * 0.004f, WHITE, 0.99f);
            Draw.sphere(add(buf, SPHERE_GLOW), ps, r * 1.13f, 14, 24, t * 0.02f, PURPLE, 0.55f);
            float[] tilts = {0f, 62f, -48f};
            float[] radii = {2.6f, 3.4f, 4.2f};
            float[] speeds = {7f, -5f, 4f};
            for (int k = 0; k < 3; k++) {
                ps.pushPose();
                ps.mulPose(Axis.XP.rotationDegrees(tilts[k]));
                ps.mulPose(Axis.ZP.rotationDegrees(k * 25f));
                Draw.disc(add(buf, ACCRETION), ps, radii[k] * size * (1f - after), t * speeds[k], 0f, k == 1 ? DEEP_PURPLE : PURPLE, 0.9f * (1f - after));
                ps.popPose();
            }
            ps.pushPose();
            billboard(ps);
            Draw.xy(add(buf, GLOW), ps, r * 2.6f, r * 2.6f, DEEP_PURPLE, 0.35f * size * (1f - after));
            ps.popPose();
        }
        ps.popPose();
        if (t > collapse) {
            float p = c01((t - collapse) / 12f);
            float dr = OverlordConfig.BLACK_HOLE.damageRadius.get().floatValue();
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, dr * p - 1.4f), dr * p + 0.4f, 0f, 40, 1f, 0f, PURPLE, 1f - p);
            ps.pushPose();
            Draw.sphere(add(buf, SPHERE_GLOW), ps, dr * p * 0.7f, 12, 20, p, DEEP_PURPLE, (1f - p) * 0.45f);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------------------------------ 4

    private void goal(OverlordFxEntity e, float t, float dur, PoseStack ps, MultiBufferSource buf, int light) {
        float R = e.getRadius();
        int charge = e.getMark();
        float a = fade(t, 24f, dur, 40f);
        Draw.disc(solid(buf, CIRCLE_DEATH), ps, R, t * 0.6f, 0.02f, BLACK, a * 0.55f);
        Draw.disc(add(buf, CIRCLE_DEATH), ps, R, t * 0.6f, 0.04f, CRIMSON, a);
        Draw.disc(add(buf, RUNE_RING), ps, R * 0.74f, -t * 0.9f, 0.06f, PURPLE, a * 0.85f);
        Draw.disc(add(buf, RUNE_RING), ps, R * 0.38f, t * 1.6f, 0.08f, CRIMSON, a * 0.9f);
        // countdown pulses: one dark shock ring per second of charging
        for (int s = 1; s * 20 <= charge; s++) {
            float age = t - s * 20;
            if (age < 0f || age > 18f) {
                continue;
            }
            float p = age / 18f;
            Draw.ring(solid(buf, RING_WAVE), ps, Math.max(0f, R * p - 1.6f), R * p + 1.0f, 0.1f, 48, 1f, 0f, BLACK, (1f - p) * 0.95f);
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, R * p - 0.8f), R * p + 0.4f, 0.12f, 48, 1f, 0f, CRIMSON, (1f - p) * 0.7f);
        }
        // spectral hands clawing out of the ground
        VertexConsumer hands = solid(buf, HAND);
        for (int i = 0; i < 28; i++) {
            RandomSource r = RandomSource.create(i * 4513L + e.getId());
            float ang = r.nextFloat() * Mth.TWO_PI, d = R * (0.3f + 0.65f * r.nextFloat());
            float start = 12f + r.nextFloat() * charge * 0.7f;
            float p = c01((t - start) / 18f) * (1f - c01((t - (charge + 8f)) / 14f));
            if (p <= 0.01f) {
                continue;
            }
            float sway = Mth.sin(t * 0.2f + i) * 8f;
            ps.pushPose();
            ps.translate(Mth.cos(ang) * d, 0, Mth.sin(ang) * d);
            Draw.cross(hands, ps, 0.5f, -1.9f + p * 1.9f, 1.9f, r.nextFloat() * 180f + sway, WHITE, 0.95f, light);
            ps.popPose();
        }
        // the death wave
        float wave = c01((t - charge) / 20f);
        if (t >= charge && wave < 1f || t >= charge && t < charge + 40f) {
            float wr = R * wave;
            float fo = 1f - c01((t - charge - 20f) / 20f);
            Draw.band(solid(buf, WAVE_BAND), ps, Math.max(0.1f, wr), 0f, 6f, 56, 8f, t * 0.04f, BLACK, 0.9f * fo, 0.3f * fo);
            Draw.band(add(buf, WAVE_BAND), ps, Math.max(0.1f, wr), 0f, 6f, 56, 8f, -t * 0.06f, CRIMSON, 0.7f * fo, 0.1f * fo);
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, wr - 2f), wr + 0.6f, 0.15f, 56, 1f, 0f, PURPLE, 0.9f * fo);
        }
    }

    // ------------------------------------------------------------------------------------------ 5

    private void fallen(OverlordFxEntity e, float t, float dur, PoseStack ps, MultiBufferSource buf) {
        float R = e.getRadius();
        int charge = e.getMark();
        int impact = charge + 10;
        float H0 = 16f;
        float form = c01(t / (charge * 0.55f));
        float ease = 1f - (1f - form) * (1f - form);
        float rs = 3.2f * ease * (1f + 0.03f * Mth.sin(t * 0.5f));
        float d = c01((t - charge) / 10f);
        float sy = Mth.lerp(d * d, H0 + 0.4f * Mth.sin(t * 0.1f), 1.8f);
        float lit = c01(t / charge);
        // ground illumination
        Draw.disc(add(buf, GLOW), ps, R * 0.95f, 0f, 0.05f, GOLD, 0.6f * lit * (1f - c01((t - impact - 30f) / 60f)));
        Draw.disc(add(buf, RUNE_RING), ps, R * 0.55f * lit, t * 0.8f, 0.07f, GOLD, 0.6f * lit * (1f - c01((t - impact) / 10f)));
        // pre-explosion light beams
        VertexConsumer beams = add(buf, BEAM);
        for (int i = 0; i < 16; i++) {
            RandomSource r = RandomSource.create(i * 3571L + e.getId());
            float start = charge - 40f + i * 2.2f;
            float p = c01((t - start) / 8f) * (1f - c01((t - impact - 4f) / 10f));
            if (p <= 0.01f) {
                continue;
            }
            float ang = r.nextFloat() * Mth.TWO_PI, dd = R * 0.9f * Mth.sqrt(r.nextFloat());
            ps.pushPose();
            ps.translate(Mth.cos(ang) * dd, 0, Mth.sin(ang) * dd);
            Draw.cross(beams, ps, 0.5f + r.nextFloat() * 0.5f, 0f, 34f * p, t * 3f, GOLD, 0.85f, 15728880);
            ps.popPose();
        }
        if (t < impact + 2f) {
            ps.pushPose();
            ps.translate(0, sy, 0);
            Draw.sphere(add(buf, SPHERE_GOLD), ps, rs, 14, 24, t * 0.01f, WHITE_GOLD, 1f);
            Draw.sphere(add(buf, SPHERE_GLOW), ps, rs * 1.25f, 12, 20, -t * 0.015f, GOLD, 0.4f);
            float[] tilt = {0f, 60f, -60f};
            for (int k = 0; k < 3; k++) {
                ps.pushPose();
                ps.mulPose(Axis.XP.rotationDegrees(tilt[k]));
                ps.mulPose(Axis.YP.rotationDegrees(t * (3f + k)));
                Draw.disc(add(buf, RUNE_RING), ps, rs * (1.9f + 0.35f * k), 0f, 0f, GOLD, 0.9f * ease);
                ps.popPose();
            }
            ps.pushPose();
            billboard(ps);
            Draw.xy(add(buf, GLOW), ps, rs * 3.4f, rs * 3.4f, GOLD, 0.55f * ease);
            ps.popPose();
            ps.popPose();
            if (d > 0f) { // light pillar trailing the falling sphere
                Draw.cross(add(buf, BEAM), ps, rs * 0.75f, 0f, sy, t * 4f, WHITE_GOLD, 0.9f, 15728880);
            }
        }
        if (t >= impact) {
            float p = c01((t - impact) / 16f);
            float q = c01((t - impact) / 6f);
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, R * p - 2.4f), R * p + 0.8f, 0.15f, 56, 1f, 0f, WHITE_GOLD, 1f - p);
            Draw.band(add(buf, WAVE_BAND), ps, Math.max(0.1f, R * p), 0f, 4f * (1f - p) + 0.6f, 56, 8f, t * 0.05f, GOLD, 0.85f * (1f - p), 0.1f);
            ps.pushPose();
            ps.translate(0, 1.5f, 0);
            Draw.sphere(add(buf, SPHERE_GOLD), ps, R * 0.45f * q, 14, 24, 0f, WHITE_GOLD, (1f - c01((t - impact - 4f) / 12f)));
            ps.popPose();
            Draw.cross(beams, ps, R * 0.12f * (1f - p * 0.5f), 0f, 50f, t, WHITE_GOLD, 0.95f * (1f - c01((t - impact) / 26f)), 15728880);
            float sc = c01((dur - t) / 50f) * c01((t - impact) / 3f);
            Draw.disc(solid(buf, SCORCH), ps, R * 0.6f, 0f, 0.03f, WHITE, 0.9f * sc);
            Draw.disc(add(buf, GLOW), ps, R * 0.5f, 0f, 0.06f, GOLD, 0.35f * sc);
        }
    }

    // ------------------------------------------------------------------------------------------ 6

    private static Vec3[] crackPoints(long seed, Vec3 dir, float len, int n) {
        RandomSource r = RandomSource.create(seed);
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = dir.cross(up).normalize(), s2 = dir.cross(s1).normalize();
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float f = i / (float) n;
            double jag = Mth.sin(f * Mth.PI) * 0.55 + 0.05;
            double j1 = (r.nextDouble() - 0.5) * 2 * jag, j2 = (r.nextDouble() - 0.5) * 2 * jag;
            pts[i] = dir.scale(len * f).add(s1.scale(j1)).add(s2.scale(j2));
        }
        return pts;
    }

    private void slash(OverlordFxEntity e, float t, PoseStack ps, MultiBufferSource buf, Vec3 cam) {
        float w = e.getMark();
        Vec3 dir = e.getDir().normalize();
        float len = e.getLength();
        Vec3 end = dir.scale(len);
        if (t < w) { // thin targeting line
            float a = 0.5f + 0.5f * Mth.sin(t * 2.5f);
            Draw.ribbon(add(buf, RIBBON), ps, Vec3.ZERO, end, cam, 0.025f, 0.025f, 0f, 1f, PURPLE, a, a * 0.4f);
            return;
        }
        float s = t - w;
        float life = 14f;
        if (s > life) {
            return;
        }
        float fadeOut = 1f - c01((s - 6f) / 8f);
        float snap = c01((s - 6f) / 6f);
        snap = snap * snap;
        int n = 26;
        Vec3[] pts = crackPoints(e.getId() * 131L + 7, dir, len, n);
        for (int i = 0; i < pts.length; i++) {
            pts[i] = pts[i].lerp(end, snap);
        }
        VertexConsumer core = add(buf, RIBBON);
        for (int i = 0; i < n; i++) {
            float f = i / (float) n;
            float wid = 0.11f * (1f - snap * 0.8f);
            Draw.ribbon(core, ps, pts[i], pts[i + 1], cam, wid, wid, 0f, 1f, PURPLE, fadeOut, fadeOut);
            Draw.ribbon(core, ps, pts[i], pts[i + 1], cam, wid * 0.35f, wid * 0.35f, 0f, 1f, WHITE, fadeOut, fadeOut);
        }
        VertexConsumer dark = solid(buf, RIBBON);
        for (int i = 0; i < n; i++) {
            float wid = 0.24f * (1f - snap * 0.85f);
            Draw.ribbon(dark, ps, pts[i], pts[i + 1], cam, wid, wid, 0f, 1f, BLACK, fadeOut * 0.9f, fadeOut * 0.9f);
        }
        // glass-like side cracks
        RandomSource r = RandomSource.create(e.getId() * 977L + 3);
        VertexConsumer branch = add(buf, RIBBON);
        for (int i = 2; i < n - 1; i += 2) {
            for (int b = 0; b < 2; b++) {
                Vec3 off = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize().scale(0.6 + r.nextDouble() * 1.1);
                Vec3 a = pts[i], c = a.add(off.scale(1.0 - snap));
                Draw.ribbon(branch, ps, a, c, cam, 0.045f, 0.01f, 0f, 1f, PURPLE, fadeOut * 0.8f, 0f);
            }
        }
        // initial flash, then the black point the rift collapses to
        if (s < 3f) {
            Draw.ribbon(core, ps, Vec3.ZERO, end, cam, 0.5f * (1f - s / 3f), 0.5f * (1f - s / 3f), 0f, 1f, DEEP_PURPLE, 0.55f, 0.55f);
        }
        float dot = c01((s - 7f) / 3f) * (1f - c01((s - 11f) / 3f));
        if (dot > 0f) {
            ps.pushPose();
            ps.translate(end.x, end.y, end.z);
            billboard(ps);
            Draw.xy(solid(buf, GLOW), ps, 0.45f * dot, 0.45f * dot, BLACK, 1f);
            Draw.xy(add(buf, GLOW), ps, 0.9f * dot, 0.9f * dot, PURPLE, 0.6f);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------------------------------ 7

    private void goat(OverlordFxEntity e, float t, float dur, PoseStack ps, MultiBufferSource buf) {
        float R = e.getRadius();
        int charge = e.getMark();
        float a = fade(t, 24f, dur, 40f);
        Draw.disc(solid(buf, CIRCLE_GOAT), ps, R, -t * 0.5f, 0.02f, BLACK, a * 0.65f);
        Draw.disc(add(buf, CIRCLE_GOAT), ps, R, -t * 0.5f, 0.04f, CRIMSON, a);
        Draw.disc(add(buf, RUNE_RING), ps, R * 0.62f, t * 0.9f, 0.06f, new float[]{0.75f, 0.1f, 0.45f}, a * 0.85f);
        float p = c01((t - (charge - 14f)) / 26f);
        if (p > 0f && p < 1f || (t >= charge - 14f && t < charge + 30f)) {
            float fo = 1f - c01((t - charge) / 30f);
            for (int k = 0; k < 3; k++) {
                float rr = R * (0.18f + 0.1f * k) * (0.6f + p);
                Draw.band(solid(buf, WAVE_BAND), ps, rr, 0f, 9f * p + 1f, 24, 4f, t * (0.03f + 0.01f * k), BLACK, 0.85f * fo, 0.15f * fo);
                Draw.band(add(buf, WAVE_BAND), ps, rr * 1.05f, 0f, 9f * p + 1f, 24, 4f, -t * 0.05f, CRIMSON, 0.55f * fo, 0.05f * fo);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ 8

    private void grasp(OverlordFxEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf) {
        Entity tgt = e.getTargetEntity();
        if (tgt == null) {
            return;
        }
        Vec3 rel = tgt.getPosition(pt).add(0, tgt.getBbHeight() * 0.65, 0).subtract(e.getPosition(pt));
        float crush = e.getMark();
        ps.pushPose();
        ps.translate(rel.x, rel.y, rel.z);
        billboard(ps);
        float appear = c01(t / 8f);
        float heart = t < crush ? 0.32f * (1f + 0.18f * Mth.sin(t * 1.3f)) * (1f - 0.35f * c01((t - 12f) / 12f)) : 0.32f * (1f - c01((t - crush) / 4f));
        Draw.xy(add(buf, HEART), ps, heart, heart, CRIMSON, appear * (t < crush + 4f ? 1f : 0f));
        Draw.xy(add(buf, GLOW), ps, heart * 2.8f, heart * 2.8f, CRIMSON, 0.5f * appear * (1f - c01((t - crush) / 8f)));
        float hand = 0.95f * appear * (1f - c01((t - crush - 3f) / 8f));
        ps.pushPose();
        ps.translate(0, 0, -0.05f);
        Draw.xy(add(buf, t < 14f ? HAND_OPEN : HAND_CLENCH), ps, hand, hand, new float[]{0.85f, 0.15f, 0.3f}, 0.95f);
        ps.popPose();
        ps.popPose();
    }
}
