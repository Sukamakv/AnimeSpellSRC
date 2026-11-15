package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.irontomb.entity.IrontombEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * The two "reality ends" specials.
 * Singularity (phase 2): every piece of the world is pulled into a sphere between the boss' hands, which then crushes it.
 * Erasure (phase 3): a giant spiral red sword, drawn in a sky of nebula and sigils, hangs over the field and is driven through it.
 * Everything is in boss space (origin = boss feet, world axes); body-frame points go through CosmicFx.w.
 */
final class SpecialFx {
    static final float[] RED = {1f, 0.2f, 0.12f};
    static final float[] PINK = {1f, 0.45f, 0.8f};
    static final float[] VIO = {0.62f, 0.34f, 1f};
    static final float[] WHITE = {1f, 0.96f, 0.92f};
    private static final float[][] MATTER = {{0.7f, 0.9f, 1f}, {0.95f, 0.8f, 0.55f}, {0.65f, 1f, 0.75f}, {1f, 0.65f, 0.95f}, {1f, 1f, 1f}};

    private SpecialFx() {
    }

    // ------------------------------------------------------------------ shared pieces
    /** How far the hands have closed (0..1) at attack tick t of the Singularity. Also drives the body pose. */
    static float grip(float t) {
        return (0.2f * Fx.ramp(t, 150, 230) + 0.45f * Fx.ramp(t, 230, 300) + 0.35f * Fx.ramp(t, 300, 328)) * (1f - Fx.smooth(Fx.ramp(t, 330, 350)));
    }

    /** The point reality is pressed into: between the hands, in front of the boss. */
    static Vec3 sphereCenter() {
        return CosmicFx.w(0, 31, 72);
    }

    private static void flare(FxCtx c, Vec3 p, float size, float[] col, float a) {
        if (a < 0.01f || size < 0.1f) {
            return;
        }
        c.billboard("glow", p, size * 1.8f, 0, col[0], col[1], col[2], 0.55f * a);
        CosmicFx.sprite(c, "star4", p, size, size, 1f, 1f, 1f, Math.min(1f, a));
        CosmicFx.sprite(c, "hflare", p, size * 7f, size * 0.5f, 1f, 0.85f, 0.9f, 0.6f * a);
    }

    /** Deep-space sky: a tinted dark dome, its stars, and a nebula shell. */
    private static void sky(FxCtx c, float a, float[] dome, float[] neb, float nebA) {
        if (a < 0.01f) {
            return;
        }
        SkyFx.dome(c, a, dome[0], dome[1], dome[2]);
        if (nebA > 0.01f) {
            Vec3 p = c.cam;
            c.ps.pushPose();
            c.ps.translate(p.x, p.y, p.z);
            Fx.sphere(c.add("nebula"), c.ps, 116f, 12, 24, c.age * 0.00025f, neb[0], neb[1], neb[2], Mth.clamp(a * nebA, 0f, 1f));
            c.ps.popPose();
        }
    }

    private static void cracks(FxCtx c, float strength, float[] col) {
        if (strength < 0.01f) {
            return;
        }
        for (int i = 0; i < 10; i++) {
            double ang = i * 0.628 + CosmicFx.yaw + 0.4;
            float dist = 95f + Fx.rnd(i * 3) * 40f;
            float h = 30f + Fx.rnd(i * 5 + 1) * 95f;
            Vec3 p = new Vec3(Math.cos(ang) * dist, h, Math.sin(ang) * dist);
            float pulse = 0.7f + 0.3f * Mth.sin(c.age * 0.12f + i);
            c.billboard("crack", p, 55f + Fx.rnd(i * 7 + 2) * 60f, Fx.rnd(i * 9) * 6.28f, col[0], col[1], col[2], strength * pulse);
            c.billboard("glow", p, 40f, 0, col[0], col[1], col[2], 0.3f * strength * pulse);
        }
    }

    private static void shock(FxCtx c, Vec3 center, float radius, float k, float[] col, float a) {
        float kk = Mth.clamp(k, 0f, 1f);
        if (a < 0.01f) {
            return;
        }
        c.flatFrame("ring", center, radius * (0.1f + 0.9f * Fx.ease(kk)), (int) (kk * 15), 4, col[0], col[1], col[2], a);
    }

    private static Vec3 sub(Vec3 a, Vec3 b) {
        return a.subtract(b);
    }

    // ================================================================== SINGULARITY
    static void singularity(FxCtx c) {
        float t = c.at;
        Vec3 S = sphereCenter();
        float up = Fx.smooth(Fx.ramp(t, 0, 45)) * (1f - Fx.smooth(Fx.ramp(t, 336, 368)));

        // the sky is drawn shut and goes violet-black
        float vis = Fx.window(t, 6, 60, 338, 410);
        sky(c, vis * 0.92f, new float[]{0.02f, 0.015f, 0.09f}, VIO, 0.55f);
        cracks(c, Fx.ramp(t, 30, 130) * (1f - Fx.ramp(t, 336, 385)), VIO);

        // contracting rings on the ground: space folding in towards the boss
        float ringA = Fx.ramp(t, 40, 80) * (1f - Fx.ramp(t, 316, 330));
        if (ringA > 0.01f) {
            for (int i = 0; i < 5; i++) {
                float k = (t - 40f - i * 14f) / 70f;
                if (k < 0f) {
                    continue;
                }
                k = k - (float) Math.floor(k);
                float rad = 8f + 240f * (1f - Fx.ease(k));
                float a = (float) Math.sin(k * Math.PI) * 0.75f * ringA;
                c.flat("halo_ring", new Vec3(0, 0.25, 0), rad, 0f, 0.7f, 0.5f, 1f, a);
                c.billboard("halo_ring", S, 10f + 150f * (1f - Fx.ease(k)), c.age * 0.01f, 0.8f, 0.6f, 1f, a * 0.6f);
            }
            c.flat("glyph", new Vec3(0, 0.1, 0), 62f * Fx.ease(Fx.ramp(t, 20, 90)), c.age * 0.02f, 0.6f, 0.4f, 1f, 0.7f * ringA);
        }

        // sphere radius: grows while reality is gathered, then the hands squeeze it
        float form = Fx.ease(Fx.ramp(t, 40, 200));
        float crush = Fx.smooth(Fx.ramp(t, 290, 328));
        float R = (1.5f + 15.5f * form) * (1f - 0.8f * crush) + 0.6f * Mth.sin(c.age * 0.4f) * crush;
        float alive = Fx.smooth(Fx.ramp(t, 36, 64)) * (t < 330f ? 1f : 0f);

        // matter streaming in from every direction
        float inA = Fx.ramp(t, 40, 90) * (1f - Fx.ramp(t, 296, 328));
        if (inA > 0.01f) {
            for (int i = 0; i < 150; i++) {
                float sp = 0.009f + 0.012f * Fx.rnd(i * 3 + 1);
                float p = (c.age * sp + Fx.rnd(i * 5 + 2)) % 1f;
                float d0 = 50f + 230f * Fx.rnd(i * 7 + 3);
                double az = Fx.rnd(i * 11 + 4) * 6.2832 + p * 3.3;
                double el = (Fx.rnd(i * 13 + 5) - 0.5) * 2.2;
                float dist = d0 * (float) Math.pow(1f - p, 1.7);
                Vec3 pos = S.add(Math.cos(az) * Math.cos(el) * dist, Math.sin(el) * dist * 0.7, Math.sin(az) * Math.cos(el) * dist);
                float fade = Fx.smooth(Fx.ramp(p, 0f, 0.12f)) * (1f - Fx.ramp(p, 0.92f, 1f)) * inA;
                float size = (1.2f + 3.2f * Fx.rnd(i * 17 + 6)) * (0.4f + 0.6f * (float) Math.sqrt(1f - p));
                float[] col = MATTER[i % MATTER.length];
                Vec3 out = pos.subtract(S);
                Vec3 tail = out.lengthSqr() < 1e-3 ? pos : pos.add(out.normalize().scale(Math.min(34f, dist * 0.5f + 5f)));
                c.beam(tail, pos, size * 0.2f, 0f, col[0], col[1], col[2], 0.7f * fade);
                c.billboard("spark", pos, size, 0, col[0], col[1], col[2], 0.9f * fade);
            }
            // slabs of ground torn up and hauled in
            for (int i = 0; i < 46; i++) {
                float p = (t - 40f - Fx.rnd(i * 3) * 120f) / 150f;
                if (p <= 0f || p >= 1f) {
                    continue;
                }
                double az = Fx.rnd(i * 5 + 1) * 6.2832;
                float r0 = 8f + Fx.rnd(i * 7 + 2) * 130f;
                Vec3 start = new Vec3(Math.cos(az) * r0, 0.5, Math.sin(az) * r0);
                float e = p * p;
                Vec3 pos = start.lerp(S, e).add(0, Math.sin(Math.PI * p) * (20 + Fx.rnd(i * 9 + 3) * 40), 0);
                float size = 3f + Fx.rnd(i * 11 + 4) * 9f;
                float a = (float) Math.sin(Math.PI * p) * 0.95f * inA;
                c.billboard("shard", pos, size, p * 6f + i, 0.78f, 0.72f, 0.88f, a);
                c.billboard("smoke", pos, size * 1.6f, 0f, 0.45f, 0.38f, 0.55f, 0.35f * a);
            }
        }

        // the sphere itself
        if (alive > 0.01f) {
            PoseStack ps = c.ps;
            ps.pushPose();
            ps.translate(S.x, S.y, S.z);
            Fx.sphere(c.alpha("planet"), ps, R, 18, 36, c.age * 0.002f, 0.32f, 0.28f, 0.55f, alive);
            Fx.sphere(c.add("violet"), ps, R * 1.04f, 14, 28, -c.age * 0.004f, 1f, 1f, 1f, 0.75f * alive);
            Fx.sphere(c.add("nebula"), ps, R * 1.1f, 12, 24, c.age * 0.003f, 1f, 0.8f, 1f, 0.55f * alive);
            ps.popPose();
            float heat = 0.35f + 0.65f * crush;
            c.billboard("glow", S, R * 5.5f, 0, 0.55f, 0.3f, 1f, 0.55f * alive);
            c.billboard("glow", S, R * 2.8f, 0, 1f, 0.7f, 1f, (0.6f + 0.4f * crush) * alive);
            c.billboard("glow", S, R * 1.5f, 0, 1f, 1f, 1f, heat * alive);
            // accretion discs and the lensing ring
            for (int k = 0; k < 3; k++) {
                ps.pushPose();
                ps.translate(S.x, S.y, S.z);
                ps.mulPose(Axis.YP.rotation(c.age * 0.012f * (k + 1) + k * 2f));
                ps.mulPose(Axis.XP.rotationDegrees(70f - 55f * k));
                Fx.quadXZ(c.add("ring_nodes"), ps, R * (2.3f + 0.55f * k), 0f, 0.8f, 0.55f + 0.2f * k, 1f, 0.8f * alive);
                ps.popPose();
            }
            c.billboard("halo_ring", S, R * 6.5f, c.age * 0.008f, 0.8f, 0.6f, 1f, 0.7f * alive);
            if (crush > 0.01f) {
                for (int i = 0; i < 12; i++) {
                    double az = Fx.rnd(i * 3 + (int) (c.age * 2f)) * 6.2832, el = (Fx.rnd(i * 5 + (int) (c.age * 2f) + 1) - 0.5) * 3.0;
                    Vec3 end = S.add(Math.cos(az) * Math.cos(el) * R * 3.2, Math.sin(el) * R * 3.2, Math.sin(az) * Math.cos(el) * R * 3.2);
                    c.beam(S, end, 0.9f + R * 0.06f, 0f, 1f, 0.85f, 1f, 0.8f * crush * alive);
                }
            }
            flare(c, S, R * 1.1f * (0.3f + 0.7f * crush), WHITE, 0.8f * alive);
        }

        // the hands: threads of light from their tips to the sphere
        if (alive > 0.01f) {
            float th = (float) Math.toRadians(70f + 28f * grip(t));
            for (int s = -1; s <= 1; s += 2) {
                Vec3 tip = CosmicFx.w(s * (14.0 + 72.0 * Math.cos(th)), 28, 72.0 * Math.sin(th));
                c.beam(tip, S, 1.4f + R * 0.12f, 0.35f, 0.85f, 0.6f, 1f, 0.8f * alive * up);
                c.billboard("glow", tip, 9f + 7f * crush, 0, 0.8f, 0.5f, 1f, 0.9f * alive * up);
            }
        }

        // the crush: everything goes white and the world flies apart
        if (t >= 330f) {
            float k = Mth.clamp((t - 330f) / 90f, 0f, 1f);
            float w = 1f - k;
            PoseStack ps = c.ps;
            ps.pushPose();
            ps.translate(S.x, S.y, S.z);
            Fx.sphere(c.add("white"), ps, 8f + 950f * Fx.ease(k), 16, 32, 0f, 1f, 0.94f, 1f, 0.85f * w * w);
            Fx.sphere(c.add("violet"), ps, 10f + 900f * Fx.ease(Mth.clamp(k * 1.1f, 0f, 1f)), 14, 28, c.age * 0.003f, 1f, 1f, 1f, 0.6f * w);
            ps.popPose();
            c.billboard("halo_ring", S, 20f + 800f * Fx.ease(k), 0f, 1f, 0.8f, 1f, w);
            c.billboard("glow", S, 600f * w, 0, 0.8f, 0.5f, 1f, w);
            flare(c, S, 260f * w, WHITE, w);
            for (int j = 0; j < 4; j++) {
                shock(c, new Vec3(0, 0.2 + 0.02 * j, 0), 520f - j * 90f, k * (1.1f + 0.25f * j), j % 2 == 0 ? VIO : WHITE, w);
            }
            cracks(c, Fx.ramp(t, 330, 345) * (1f - Fx.ramp(t, 360, 405)), WHITE);
            for (int i = 0; i < 90; i++) {
                double az = Fx.rnd(i * 3 + 1) * 6.2832, el = (Fx.rnd(i * 5 + 2) - 0.5) * 2.6;
                float d = 15f + 650f * Fx.ease(k) * (0.25f + 0.75f * Fx.rnd(i * 7 + 3));
                Vec3 pos = S.add(Math.cos(az) * Math.cos(el) * d, Math.sin(el) * d, Math.sin(az) * Math.cos(el) * d);
                float[] col = MATTER[i % MATTER.length];
                c.billboard("shard", pos, 3f + 8f * Fx.rnd(i * 11 + 4), i + k * 8f, col[0], col[1], col[2], 0.9f * w);
            }
            // what is left: a tiny black hole
            float rem = Fx.ramp(t, 332, 350) * (1f - Fx.ramp(t, 380, 410));
            if (rem > 0.01f) {
                c.billboard("halo_ring", S, 30f, c.age * 0.05f, 1f, 0.7f, 1f, 0.8f * rem);
                c.billboard("glow", S, 14f, 0, 0.6f, 0.3f, 1f, 0.7f * rem);
            }
        }
    }

    // ================================================================== ERASURE
    private static final Map<Integer, Integer> ERASE_MAX = new HashMap<>();

    /** Longest countdown seen for this boss (the client is not told the configured value), so a progress bar can be drawn. */
    static float eraseProgress(IrontombEntity e) {
        int left = e.getErase();
        if (left <= 0) {
            ERASE_MAX.remove(e.getId());
            return 0f;
        }
        int max = ERASE_MAX.merge(e.getId(), left, Math::max);
        return 1f - left / (float) Math.max(1, max);
    }

    /** The spiral red sword: tip at the bottom, blade widening upward into a stack of diamonds. tip is in boss space. */
    private static void sword(FxCtx c, Vec3 tip, float L, float a, float spin, float pulse) {
        if (a < 0.01f || L < 2f) {
            return;
        }
        float W = 0.034f * L, gs = 0.80f;
        Vec3 ch = new Vec3(c.cam.x - tip.x, 0, c.cam.z - tip.z);
        ch = ch.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : ch.normalize();
        Vec3 right = new Vec3(-ch.z, 0, ch.x);
        // the blade, as stacked tapering ribbons: red haze, red body, white-hot core
        int n = 30;
        for (int i = 0; i < n; i++) {
            float s0 = i / (float) n * gs, s1 = (i + 1) / (float) n * gs;
            float w = W * (float) Math.pow((s0 + s1) * 0.5f / gs, 0.7);
            Vec3 a0 = tip.add(0, s0 * L, 0), a1 = tip.add(0, s1 * L, 0);
            c.beam(a0, a1, w * 3.6f, 0f, 1f, 0.16f, 0.12f, 0.33f * a);
            c.beam(a0, a1, w * 1.8f, 0.12f, 1f, 0.3f, 0.22f, 0.85f * a);
            c.beam(a0, a1, w * 0.7f, 0.2f, 1f, 0.92f, 0.88f, a);
        }
        // guard and the long cross-bar of light
        Vec3 gp = tip.add(0, gs * L, 0);
        c.beam(gp.add(right.scale(-0.16 * L)), gp.add(right.scale(0.16 * L)), W * 0.8f, 0f, 1f, 0.35f, 0.3f, 0.9f * a);
        c.beam(gp.add(right.scale(-0.09 * L)), gp.add(right.scale(0.09 * L)), W * 0.35f, 0f, 1f, 0.95f, 0.9f, a);
        // the spiral rope winding around the upper blade
        int m = 110;
        float turns = 7.5f;
        for (int strand = 0; strand < 2; strand++) {
            Vec3 prev = null;
            for (int j = 0; j <= m; j++) {
                float s = 0.08f + 0.9f * j / (float) m;
                float ang = turns * 6.2832f * s - spin + strand * 3.1416f;
                float rho = W * (0.9f + 1.5f * Fx.smooth(Fx.ramp(s, 0.45f, 1f))) * (0.35f + 0.65f * Fx.ramp(s, 0.08f, 0.35f));
                Vec3 p = tip.add(Math.cos(ang) * rho, s * L, Math.sin(ang) * rho);
                if (prev != null) {
                    float front = 0.55f + 0.45f * Mth.sin(ang);
                    float fade = Fx.ramp(s, 0.08f, 0.3f);
                    float[] col = strand == 0 ? new float[]{1f, 0.3f, 0.22f} : new float[]{1f, 0.55f, 0.65f};
                    c.beam(prev, p, L * 0.0075f * (0.6f + front), 0f, col[0], col[1], col[2], 0.95f * a * fade * front);
                    c.beam(prev, p, L * 0.0028f, 0f, 1f, 0.95f, 0.9f, a * fade * front);
                }
                prev = p;
            }
        }
        // the diamonds of the hilt, lighting one after another
        float[] ds = {0.84f, 0.895f, 0.945f, 0.985f, 1.03f, 1.09f};
        for (int i = 0; i < ds.length; i++) {
            float k = 1f - i * 0.12f;
            Vec3 p = tip.add(0, ds[i] * L, 0);
            float lit = 0.65f + 0.35f * Mth.sin(c.age * 0.12f - i * 0.9f);
            CosmicFx.sprite(c, "diamond", p, 0.024f * L * k, 0.05f * L * k, 1f, 1f, 1f, a * lit);
            c.billboard("glow", p, 0.06f * L * k, 0, 1f, 0.3f, 0.3f, 0.5f * a * lit);
        }
        CosmicFx.sprite(c, "diamond", tip.add(0, 1.0f * L, 0), 0.05f * L, 0.1f * L, 1f, 0.95f, 0.95f, 0.9f * a);
        c.flat("halo_ring", tip.add(0, 0.92f * L, 0), 0.085f * L, spin, 1f, 0.6f, 0.6f, 0.9f * a);
        c.flat("ring_nodes", tip.add(0, 0.78f * L, 0), 0.07f * L, -spin * 0.6f, 1f, 0.5f, 0.5f, 0.8f * a);
        // the beam above the hilt and the star at the tip
        c.pillarAt(tip.add(0, 1.1f * L, 0), 900f, 0.025f * L, RED, 0.45f * a);
        flare(c, tip, 0.1f * L * (0.85f + 0.15f * pulse), WHITE, a);
        // horizontal lens-flare ribbons crossing the tip
        for (int b = 0; b < 3; b++) {
            Vec3 prevp = null;
            int seg = 40;
            float ph = c.age * (0.03f + 0.012f * b) + b * 2.1f;
            for (int j = 0; j <= seg; j++) {
                float u = j / (float) seg * 2f - 1f;
                float env = (1f - Math.abs(u)) * (1f - Math.abs(u) * 0.3f);
                Vec3 p = tip.add(right.scale(u * 1.05 * L)).add(0, Math.sin(u * 7f + ph) * 0.03f * L * env * (b + 1), 0).add(ch.scale(Math.sin(u * 5f - ph) * 0.02f * L * env));
                if (prevp != null) {
                    float[] col = b == 0 ? new float[]{0.75f, 0.45f, 1f} : b == 1 ? new float[]{1f, 0.4f, 0.8f} : new float[]{0.9f, 0.85f, 1f};
                    c.beam(prevp, p, (0.03f - 0.007f * b) * L * env + 0.5f, 0f, col[0], col[1], col[2], 0.8f * a * Math.min(1f, env * 2f));
                }
                prevp = p;
            }
        }
    }

    /** The forming sword shown to everyone while the countdown runs. */
    static void erasureCharge(FxCtx c) {
        float p = eraseProgress(c.e);
        if (p <= 0.001f && c.e.getErase() <= 0) {
            return;
        }
        Vec3 base = CosmicFx.w(0, 22, 50);
        float L = 300f * 0.45f * (0.15f + 0.85f * p);
        float a = 0.3f + 0.7f * p;
        float pulse = 0.5f + 0.5f * Mth.sin(c.age * 0.2f);
        sky(c, 0.3f * p, new float[]{0.1f, 0.015f, 0.14f}, PINK, 0.6f);
        c.flat("sigil", new Vec3(base.x, 0.12, base.z), 210f * (0.3f + 0.7f * p), c.age * 0.004f, 1f, 0.7f, 0.7f, 0.7f * a);
        sword(c, base, L, a, c.age * (0.03f + 0.06f * p), pulse);
        // a warning light on the ground
        c.flat("halo_ring", new Vec3(base.x, 0.2, base.z), 40f + 30f * pulse, 0f, 1f, 0.3f, 0.3f, 0.5f * a);
    }

    static void erasure(FxCtx c) {
        float t = c.at;
        Vec3 base = CosmicFx.w(0, 22, 50);
        float pre = Fx.smooth(Fx.ramp(t, 0, 70));
        float voidA = Fx.smooth(Fx.ramp(t, 440, 485));
        float skyA = Math.max(pre, voidA);
        float[] domeCol = {0.13f * (1f - voidA) + 0.01f * voidA, 0.02f * (1f - voidA), 0.2f * (1f - voidA) + 0.03f * voidA};
        sky(c, skyA * 0.96f, domeCol, new float[]{1f, 0.6f, 1f}, 0.85f * (1f - 0.85f * voidA));

        Vec3 h = new Vec3(c.cam.x - base.x, 0, c.cam.z - base.z);
        h = h.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : h.normalize();
        float gate = (1f - Fx.ramp(t, 400, 440));
        float geo = Fx.smooth(Fx.ramp(t, 20, 150)) * gate;

        // distant nebula blooms and stars
        for (int i = 0; i < 9; i++) {
            double az = Fx.rnd(i * 3 + 1) * 6.2832;
            Vec3 p = new Vec3(Math.cos(az) * 260, 60 + Fx.rnd(i * 5 + 2) * 260, Math.sin(az) * 260);
            float[] col = i % 2 == 0 ? PINK : VIO;
            c.billboard("glow", p, 150f + 120f * Fx.rnd(i * 7 + 3), 0, col[0], col[1], col[2], 0.35f * pre * (1f - voidA));
        }
        for (int i = 0; i < 36; i++) {
            double az = Fx.rnd(i * 3 + 9) * 6.2832, el = 0.1 + Fx.rnd(i * 5 + 7) * 1.2;
            Vec3 p = new Vec3(Math.cos(az) * Math.cos(el) * 220, Math.sin(el) * 220, Math.sin(az) * Math.cos(el) * 220);
            flare(c, p, (2f + 6f * Fx.rnd(i)) * (0.7f + 0.3f * Mth.sin(c.age * 0.1f + i)), i % 3 == 0 ? PINK : WHITE, 0.8f * pre * (1f - voidA));
        }

        // the red geometry: sigils facing the viewer behind the sword, a ring of them on the ground and one in the sky
        Vec3 centre = base.add(0, 150, 0).add(h.scale(-70));
        float grow = 0.7f + 0.3f * Fx.ease(Fx.ramp(t, 30, 200));
        c.billboard("sigil", centre, 178f * grow, c.age * 0.0016f, 1f, 1f, 1f, 0.85f * geo);
        c.billboard("sigil", centre.add(h.scale(-6)), 118f * grow, -c.age * 0.0032f, 1f, 0.9f, 0.9f, 0.75f * geo);
        c.billboard("ring_nodes", centre.add(h.scale(-12)), 215f * grow, c.age * 0.002f, 1f, 0.6f, 0.75f, 0.6f * geo);
        c.flat("sigil", new Vec3(base.x, 0.12, base.z), 270f * Fx.ease(Fx.ramp(t, 20, 200)), c.age * 0.005f, 1f, 0.85f, 0.85f, 0.7f * geo);
        c.flat("halo_ring", new Vec3(base.x, 0.14, base.z), 285f * Fx.ease(Fx.ramp(t, 20, 200)), -c.age * 0.004f, 1f, 0.5f, 0.6f, 0.6f * geo);
        c.flat("sigil", new Vec3(base.x, 430, base.z), 320f, -c.age * 0.003f, 1f, 0.7f, 0.9f, 0.4f * geo);

        // the sword: it already hangs at 45% size from the countdown, grows to full, then is driven down
        float g = 0.45f + 0.55f * Fx.ease(Fx.ramp(t, 60, 260));
        float L = 300f * g;
        float drop = 100f * (float) Math.pow(Fx.ramp(t, 376, 396), 3);
        float a = Fx.ramp(t, 0, 30) * (1f - Fx.ramp(t, 404, 452));
        float spin = c.age * (0.03f + 0.06f * Fx.ramp(t, 160, 380));
        float pulse = 0.5f + 0.5f * Mth.sin(c.age * (0.15f + 0.2f * Fx.ramp(t, 260, 390)));
        sword(c, base.add(0, -drop, 0), L, a, spin, pulse);

        // charge: light drawn up the blade, embers rising
        float charge = Fx.window(t, 150, 260, 380, 398);
        if (charge > 0.01f) {
            for (int i = 0; i < 70; i++) {
                float p = (c.age * 0.012f * (0.6f + Fx.rnd(i * 3 + 1)) + Fx.rnd(i * 5 + 2)) % 1f;
                double az = Fx.rnd(i * 7 + 3) * 6.2832 + c.age * 0.01;
                float rr = 8f + Fx.rnd(i * 11 + 4) * 55f * (1f - p * 0.5f);
                Vec3 pos = base.add(Math.cos(az) * rr, p * 330f, Math.sin(az) * rr);
                c.billboard("spark", pos, 2.2f, 0, 1f, 0.45f, 0.4f, 0.8f * charge * (1f - Fx.ramp(p, 0.8f, 1f)));
            }
            for (int i = 0; i < 12; i++) {
                double az = Fx.rnd(i * 3 + 5) * 6.2832;
                Vec3 s0 = base.add(Math.cos(az) * 150, 20 + Fx.rnd(i * 5) * 40, Math.sin(az) * 150);
                c.beam(s0, base, 1.6f, 0.4f, 1f, 0.4f, 0.4f, 0.4f * charge);
            }
        }

        // the sword lands: a white-hot nova rolls out of the tip and erases the world
        if (t >= 396f) {
            float k = Mth.clamp((t - 396f) / 74f, 0f, 1f);
            float w = 1f - Fx.ramp(t, 450, 520);
            PoseStack ps = c.ps;
            ps.pushPose();
            ps.translate(base.x, base.y, base.z);
            Fx.sphere(c.add("white"), ps, 20f + 1600f * Fx.ease(k), 18, 36, 0f, 1f, 0.95f, 0.96f, 0.95f * w);
            Fx.sphere(c.add("white"), ps, 24f + 1650f * Fx.ease(Mth.clamp(k * 0.95f, 0f, 1f)), 14, 28, c.age * 0.002f, 1f, 0.2f, 0.25f, 0.5f * w);
            ps.popPose();
            for (int j = 0; j < 4; j++) {
                shock(c, new Vec3(base.x, 0.2 + 0.02 * j, base.z), 1100f - j * 200f, k * (1.1f + 0.2f * j), j % 2 == 0 ? RED : WHITE, w);
            }
            c.pillarAt(new Vec3(base.x, 0, base.z), 900f, 45f * (1f - k) + 3f, WHITE, 1f - k);
            flare(c, base, 260f * (1f - k), WHITE, 1f - k);
        }
        // afterwards: a void with a few red embers drifting
        if (voidA > 0.01f) {
            for (int i = 0; i < 60; i++) {
                float p = (c.age * 0.004f * (0.5f + Fx.rnd(i)) + Fx.rnd(i + 9)) % 1f;
                double az = Fx.rnd(i * 3) * 6.2832;
                float rr = 15 + Fx.rnd(i * 5) * 190;
                c.billboard("spark", new Vec3(Math.cos(az) * rr, 90 * (1f - p), Math.sin(az) * rr), 2.2f, 0, 1f, 0.3f, 0.3f, 0.7f * voidA * (1f - Fx.ramp(t, 520, 540)));
            }
            c.flat("sigil", new Vec3(base.x, 0.12, base.z), 270f, c.age * 0.003f, 1f, 0.3f, 0.35f, 0.35f * voidA * (1f - Fx.ramp(t, 505, 540)));
        }
    }
}
