package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.irontomb.IrontombAttack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The visuals of every boss attack, drawn at the scale of the cosmic form (60 blocks tall, hit zones up to 200 blocks).
 * All coordinates are boss space (relative to the boss' feet, world axes). Body-frame points go through CosmicFx.w().
 */
public final class AttackFx {
    static final float[] RED = {1f, 0.22f, 0.10f};
    static final float[] GOLD = {1f, 0.78f, 0.25f};
    static final float[] VIOLET = {0.62f, 0.34f, 1f};
    static final float[] WHITE = {1f, 0.95f, 0.88f};

    private AttackFx() {
    }

    /** The red core low in the torso: most attacks start here. */
    public static Vec3 chest(FxCtx c) {
        return CosmicFx.w(0, 6.5 + Math.sin(c.age * 0.06) * 0.3, 6.8);
    }

    /** The violet diamond in the face. */
    public static Vec3 face(FxCtx c) {
        return CosmicFx.w(0, 33.6, 6.4);
    }

    public static void draw(FxCtx c) {
        switch (c.atk) {
            case IrontombAttack.CAST_DOWN -> castDown(c);
            case IrontombAttack.WEAVE -> weave(c);
            case IrontombAttack.YIELD -> yieldAtk(c);
            case IrontombAttack.MIRE -> mire(c);
            case IrontombAttack.CROWN -> crown(c);
            case IrontombAttack.PRISON -> prison(c);
            case IrontombAttack.IMMOLATE -> immolate(c);
            case IrontombAttack.GENESIS -> genesis(c);
            case IrontombAttack.SINGULARITY -> SpecialFx.singularity(c);
            case IrontombAttack.ERASURE -> SpecialFx.erasure(c);
            case IrontombAttack.RESIST -> resist(c);
            case IrontombAttack.STAGGER -> stagger(c);
            default -> { }
        }
    }

    // ================================================================== building blocks
    /** A four-point star with a long horizontal flare. */
    private static void starFlare(FxCtx c, Vec3 p, float size, float[] col, float a) {
        if (a < 0.01f || size < 0.1f) {
            return;
        }
        c.billboard("glow", p, size * 1.8f, 0, col[0], col[1], col[2], 0.55f * a);
        CosmicFx.sprite(c, "star4", p, size, size, 1f, 1f, 1f, Math.min(1f, a));
        CosmicFx.sprite(c, "hflare", p, size * 7f, size * 0.5f, 1f, 0.85f, 0.85f, 0.6f * a);
    }

    /** A falling comet. p runs 0..1 from start to end; 1..1.25 is the landing flare. */
    private static void comet(FxCtx c, Vec3 start, Vec3 end, float p, float size, float[] col, float a) {
        if (p <= 0f || p > 1.25f || a < 0.01f) {
            return;
        }
        float q = Math.min(p, 1f);
        Vec3 head = start.lerp(end, q);
        Vec3 dir = end.subtract(start).normalize();
        float fade = p > 1f ? 0f : 1f;
        if (fade > 0f) {
            Vec3 tail = head.subtract(dir.scale(16f + size * 2.5f));
            c.beam(tail, head, size * 0.34f, 0f, col[0], col[1], col[2], 0.85f * a);
            c.beam(tail.lerp(head, 0.45), head, size * 0.14f, 0f, 1f, 0.95f, 0.85f, a);
            c.billboard("glow", head, size * 1.7f, 0, col[0], col[1], col[2], 0.9f * a);
            CosmicFx.sprite(c, "star4", head, size, size, 1f, 0.92f, 0.8f, a);
        } else {
            float f = Mth.clamp((1.25f - p) * 4f, 0f, 1f);
            c.billboard("glow", end.add(0, size * 0.5, 0), size * 5f * f, 0, col[0], col[1], col[2], 0.9f * f * a);
            c.flat("halo_ring", end.add(0, 0.2, 0), size * (2f + (p - 1f) * 60f), 0, col[0], col[1], col[2], 0.8f * f * a);
        }
    }

    /** A field of comets landing in a disc. Each has its own delay inside [t0, t0+span] and flies for dur ticks. */
    private static void rain(FxCtx c, Vec3 center, float radius, int count, float t0, float span, float dur, float height, float size, float[] col, float a) {
        for (int i = 0; i < count; i++) {
            float delay = Fx.rnd(i * 7 + 1) * span;
            float p = (c.at - t0 - delay) / dur;
            if (p <= 0f || p > 1.25f) {
                continue;
            }
            double ang = Fx.rnd(i * 11 + 2) * Math.PI * 2, rr = radius * Math.sqrt(Fx.rnd(i * 13 + 3));
            Vec3 end = center.add(Math.cos(ang) * rr, 0, Math.sin(ang) * rr);
            Vec3 start = end.add(-height * 0.35, height * (0.8 + Fx.rnd(i * 17 + 4) * 0.4), -height * 0.15);
            comet(c, start, end, p, size * (0.7f + Fx.rnd(i * 19 + 5) * 0.6f), col, a);
        }
    }

    /** Endless comets streaking through the arena (for the long build-ups). */
    private static void meteorStorm(FxCtx c, Vec3 center, float radius, int count, float height, float size, float[] col, float a) {
        for (int i = 0; i < count; i++) {
            float p = (c.age * 0.026f * (0.8f + Fx.rnd(i * 5 + 1) * 0.6f) + Fx.rnd(i * 3 + 2)) % 1.3f;
            double ang = Fx.rnd(i * 11 + 2) * Math.PI * 2, rr = radius * Math.sqrt(Fx.rnd(i * 13 + 3));
            Vec3 end = center.add(Math.cos(ang) * rr, 0, Math.sin(ang) * rr);
            Vec3 start = end.add(-height * 0.4, height, -height * 0.2);
            comet(c, start, end, p, size * (0.7f + Fx.rnd(i * 19 + 5) * 0.6f), col, a);
        }
    }

    /** The sky splitting open: big cracks on a dome around the arena. */
    private static void skyCracks(FxCtx c, float strength, float[] col) {
        if (strength < 0.01f) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            double ang = i * 0.698 + CosmicFx.yaw + 0.4;
            float dist = 95f + Fx.rnd(i * 3) * 40f;
            float h = 35f + Fx.rnd(i * 5 + 1) * 90f;
            Vec3 p = new Vec3(Math.cos(ang) * dist, h, Math.sin(ang) * dist);
            float pulse = 0.7f + 0.3f * Mth.sin(c.age * 0.12f + i);
            c.billboard("crack", p, 55f + Fx.rnd(i * 7 + 2) * 55f, Fx.rnd(i * 9) * 6.28f, col[0], col[1], col[2], strength * pulse);
            c.billboard("glow", p, 40f, 0, col[0], col[1], col[2], 0.35f * strength * pulse);
        }
    }

    private static void shock(FxCtx c, Vec3 center, float radius, float k, float[] col, float a) {
        float kk = Mth.clamp(k, 0f, 1f);
        c.flatFrame("ring", center, radius * (0.15f + 0.85f * Fx.ease(kk)), (int) (kk * 15), 4, col[0], col[1], col[2], a);
    }

    private static void world(FxCtx c, Vec3 p, float radius, float[] fire, float vis, float spin) {
        PoseStack ps = c.ps;
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        Fx.sphere(c.alpha("planet"), ps, radius, 20, 40, spin, 1f, 0.85f, 0.8f, vis);
        Fx.sphere(c.add("planet"), ps, radius * 1.02f, 14, 28, spin * 1.5f, fire[0], fire[1], fire[2], 0.5f * vis);
        ps.popPose();
        c.billboard("glow", p, radius * 2.6f, 0, fire[0], fire[1] * 0.6f, 0.1f, 0.55f * vis);
        c.billboard("glow", p, radius * 1.6f, 0, 1f, 0.55f, 0.2f, 0.85f * vis);
        for (int i = 0; i < 16; i++) {
            float ang = i * 0.393f + Fx.rnd(i) * 0.2f;
            Vec3 f = p.add(Math.cos(ang) * radius * 0.85, -radius * 0.3 - Fx.rnd(i + 3) * radius * 0.45, Math.sin(ang) * radius * 0.85);
            c.billboardFrame("flame", f, radius * 0.7f, ((int) c.age + i * 3) % 16, 4, 1f, 0.45f, 0.15f, 0.8f * vis);
        }
    }

    // ================================================================== Cast Down Blood and Scar
    private static void castDown(FxCtx c) {
        float t = c.at;
        Vec3 a = c.aim;
        float r = IrontombAttack.CAST_DOWN_RADIUS;
        float warn = Fx.window(t, 0, 6, 34, 44);
        c.flat("glyph", a.add(0, 0.06, 0), r * 1.35f * Fx.ease(Fx.ramp(t, 0, 14)), c.age * 0.05f, RED[0], RED[1], RED[2], 0.9f * warn);
        c.flat("halo_ring", a.add(0, 0.08, 0), r * 1.02f, 0, 1f, 0.5f, 0.2f, 0.85f * warn);
        if (t < 34) {
            c.beam(chest(c), a.add(0, 1, 0), 0.8f * Fx.ramp(t, 0, 20), 0.15f, RED[0], RED[1], RED[2], 0.5f * warn);
        }
        // a blade of black star-matter drops out of the sky
        float fall = Fx.ease(Fx.ramp(t, 14, 34));
        float lift = Fx.smooth(Fx.ramp(t, 52, 70));
        if (t >= 8 && t < 72) {
            float tip = 230f * (1f - fall) + 160f * lift;
            Vec3 base = a.add(10, tip + 72, 4);
            Vec3 dir = new Vec3(-0.14, -1, -0.05).normalize();
            Vec3 tipPos = a.add(0, tip, 0);
            float vis = Fx.smooth(Fx.ramp(t, 8, 18));
            VertexConsumer vc = c.solid("body_iron");
            Fx.spike(vc, c.ps, base, dir, 76f, 6f, 1f, 0.85f, 0.9f, vis);
            vc = c.add("body_glow");
            Fx.spike(vc, c.ps, base, dir, 76f, 6.4f, 1f, 0.3f, 0.15f, 0.8f * vis);
            c.beam(base, base.add(0, 160, 0), 5f, 0.3f, 1f, 0.3f, 0.18f, 0.6f * vis);
            starFlare(c, tipPos.add(0, 4, 0), 18f * vis, RED, vis);
        }
        // impact
        if (t >= 34 && t < 78) {
            float k = (t - 34) / 44f, w = 1f - k;
            Vec3 g = a.add(0, 0.1, 0);
            shock(c, g, 38f, k * 1.2f, RED, w);
            shock(c, g.add(0, 0.02, 0), 22f, k * 1.6f, new float[]{1f, 0.85f, 0.65f}, w);
            c.flat("crack", a.add(0, 0.12, 0), 17f, 0.5f, 1f, 0.45f, 0.25f, 0.9f * Fx.window(t, 34, 36, 52, 78));
            c.pillarAt(a, 220f, 5f * w + 0.6f, RED, w);
            c.billboard("glow", a.add(0, 6, 0), 46f * w, 0, 1f, 0.45f, 0.2f, 0.9f * w);
            starFlare(c, a.add(0, 8, 0), 26f * w, RED, w);
            c.billboardFrame("slash", a.add(0, 12, 0), 34f, Math.min(15, (int) ((t - 34) * 1.2f)), 4, 1f, 0.75f, 0.6f, 1f * Fx.window(t, 34, 35, 44, 50));
            for (int i = 0; i < 16; i++) {
                float ang = i * 0.3927f + Fx.rnd(i) * 0.3f;
                float sp = 10f + 26f * Fx.rnd(i + 20);
                Vec3 d = a.add(Math.cos(ang) * sp * k * 1.6, 26f * k * (1f - k) * (0.4f + Fx.rnd(i + 40)) * 2f, Math.sin(ang) * sp * k * 1.6);
                c.billboardFrame("flame", d, 4f * w + 1f, ((int) c.age + i * 3) % 16, 4, 1f, 0.45f, 0.15f, 0.85f * w);
            }
        }
    }

    // ================================================================== Weave Suffering and Death
    private static void weave(FxCtx c) {
        float t = c.at;
        float hw = IrontombAttack.WEAVE_HALF_WIDTH;
        for (int i = 0; i < 3; i++) {
            float L = IrontombAttack.WEAVE_TICKS[i], r = IrontombAttack.WEAVE_RADII[i];
            float dt = t - L;
            if (dt < 0) {
                float warn = Fx.ramp(t, 8 + i * 2, L) * 0.9f;
                float pulse = 0.6f + 0.4f * Mth.sin(c.age * 0.7f);
                c.flat("halo_ring", new Vec3(0, 0.07 + i * 0.01, 0), r + hw, 0, 1f, 0.25f, 0.12f, warn * pulse);
                c.flat("halo_ring", new Vec3(0, 0.08 + i * 0.01, 0), Math.max(1f, r - hw), 0, 1f, 0.25f, 0.12f, warn * pulse * 0.7f);
                continue;
            }
            if (dt > 30) {
                continue;
            }
            float rise = Fx.smooth(Fx.ramp(dt, 0, 3)) * (1 - Fx.smooth(Fx.ramp(dt, 12, 28)));
            int n = Math.min(60, (int) (r * 1.25f) + 12);
            Vec3[] tops = new Vec3[n];
            for (int pass = 0; pass < 2; pass++) {
                VertexConsumer vc = pass == 0 ? c.solid("body_iron") : c.add("body_glow");
                for (int k = 0; k < n; k++) {
                    float ang = (float) (k * Math.PI * 2 / n) + Fx.rnd(k + i * 91) * 0.1f;
                    float h = 20f * rise * (0.6f + 0.7f * Fx.rnd(k * 7 + i));
                    Vec3 base = new Vec3(Math.cos(ang) * r, -0.5, Math.sin(ang) * r);
                    Vec3 dir = new Vec3(Math.cos(ang) * 0.16, 1, Math.sin(ang) * 0.16).normalize();
                    tops[k] = base.add(dir.scale(h));
                    if (h < 0.05f) {
                        continue;
                    }
                    if (pass == 0) {
                        Fx.spike(vc, c.ps, base, dir, h, 1.7f, 0.85f, 0.8f, 0.8f, 1f);
                    } else {
                        Fx.spike(vc, c.ps, base.add(0, 0.05, 0), dir, h * 0.98f, 1.85f, 1f, 0.3f, 0.15f, 0.55f * rise);
                    }
                }
            }
            // the "woven" threads between the thorns
            for (int k = 0; k < n; k++) {
                if (rise > 0.05f) {
                    c.beam(tops[k], tops[(k + 1) % n], 0.4f, 0.3f, 1f, 0.3f, 0.2f, 0.65f * rise);
                    c.billboard("glow", tops[k], 2.6f, 0, 1f, 0.4f, 0.25f, 0.7f * rise);
                }
            }
            float k = Mth.clamp(dt / 20f, 0, 1);
            c.flatFrame("ring", new Vec3(0, 0.1, 0), r + hw + 6f, (int) (k * 15), 4, 1f, 0.42f, 0.2f, 1f);
            c.flatFrame("ring", new Vec3(0, 0.12, 0), (r + hw + 6f) * 0.8f, (int) (Mth.clamp(k * 1.25f, 0, 1) * 15), 4, 1f, 0.85f, 0.6f, 0.8f);
            c.flat("crack", new Vec3(0, 0.14, 0), r + hw + 4f, i * 1.1f, 1f, 0.4f, 0.2f, 0.45f * rise);
        }
        float s0 = Fx.window(t, 10, 30, 34, 70);
        c.billboard("glow", chest(c), 24f * s0, 0, 1f, 0.3f, 0.15f, 0.7f * s0);
        starFlare(c, chest(c), 12f * s0, RED, s0);
    }

    // ================================================================== Yield Not to Tyranny (Quantum)
    private static void yieldAtk(FxCtx c) {
        float t = c.at;
        float R = IrontombAttack.YIELD_RADIUS;
        Vec3 a = c.aim.add(0, 1.0, 0);
        Vec3 f = face(c);
        float charge = Fx.ramp(t, 0, 44);
        float fire = Fx.window(t, 44, 48, 54, 74);
        float zone = Fx.window(t, 0, 8, 50, 70);
        c.flat("glyph", c.aim.add(0, 0.06, 0), R * 1.4f * Fx.ease(Fx.ramp(t, 0, 16)), -c.age * 0.08f, VIOLET[0], VIOLET[1], VIOLET[2], 0.9f * zone);
        c.flat("halo_ring", c.aim.add(0, 0.08, 0), R, 0, VIOLET[0], VIOLET[1], VIOLET[2], 0.8f * zone);
        // the star above the arena and the face both charge
        Vec3 sky = CosmicFx.starPos(0);
        starFlare(c, f, 14f * charge, VIOLET, 1f);
        starFlare(c, sky, 30f * charge, RED, 1f);
        if (t < 44) {
            c.beam(f, a, 0.3f * charge, 0.4f, VIOLET[0], VIOLET[1], VIOLET[2], 0.5f);
            c.beam(sky, a, 0.25f * charge, 0.3f, RED[0], RED[1], RED[2], 0.35f);
            c.billboard("spark", a, 3.2f * charge, c.age * 0.15f, VIOLET[0], VIOLET[1], VIOLET[2], 0.95f);
        }
        if (fire > 0.01f) {
            c.beam(f, a, 5.5f * fire, 1.2f, VIOLET[0], VIOLET[1], VIOLET[2], fire);
            c.beam(f, a, 2.2f * fire, 1.6f, 1f, 0.9f, 1f, fire);
            c.beam(sky, a, 3.5f * fire, 1.0f, 1f, 0.35f, 0.3f, 0.9f * fire);
            // a tunnel of rings along the beam
            for (int i = 1; i <= 9; i++) {
                Vec3 p = f.lerp(a, ((i - (c.age * 0.12f) % 1f) / 9.5f));
                c.billboard("ring_nodes", p, 5.5f - i * 0.3f, c.age * 0.05f, VIOLET[0], VIOLET[1], VIOLET[2], 0.7f * fire);
            }
            for (int i = 0; i < 6; i++) {
                Vec3 j = new Vec3((Fx.rnd((int) (c.age * 3) + i) - 0.5) * 8, (Fx.rnd((int) (c.age * 3) + i + 9) - 0.5) * 8, (Fx.rnd((int) (c.age * 3) + i + 19) - 0.5) * 8);
                c.beam(f, a.add(j), 0.5f, 2f, 0.9f, 0.7f, 1f, 0.7f * fire);
            }
            c.pillarAt(a.add(0, -1, 0), 140f, 3f * fire, VIOLET, fire);
            float k = Mth.clamp((t - 44) / 24f, 0, 1);
            shock(c, c.aim.add(0, 0.1, 0), R * 2.4f, k, VIOLET, 1f - k * 0.4f);
            c.billboard("glow", a, 26f * fire, 0, VIOLET[0], VIOLET[1], VIOLET[2], fire);
            starFlare(c, a, 18f * fire, WHITE, fire);
        }
        // energy streaming back out of the victim
        if (t > 44) {
            float w = Fx.window(t, 44, 50, 70, 84);
            for (int i = 0; i < 18; i++) {
                float fr = (c.age * 0.035f + i / 18f) % 1f;
                c.billboard("spark", a.lerp(chest(c), fr), 1.3f, c.age * 0.1f + i, 0.8f, 0.6f, 1f, 0.9f * w);
            }
        }
    }

    // ================================================================== Mire Not Within Caves (Imaginary)
    private static void mire(FxCtx c) {
        float t = c.at;
        float R = IrontombAttack.MIRE_RADIUS;
        Vec3 a = c.aim;
        float in = Fx.window(t, 0, 12, 56, 84);
        float grow = Fx.ease(Fx.ramp(t, 6, 44));
        Vec3 core = a.add(0, 4.5, 0);
        c.flat("glyph", a.add(0, 0.06, 0), R * 1.5f * grow, c.age * 0.08f, GOLD[0], GOLD[1], GOLD[2], 0.9f * in);
        c.flat("halo_ring", a.add(0, 0.08, 0), R, 0, GOLD[0], GOLD[1], GOLD[2], 0.8f * in);
        if (t < 46) {
            c.beam(chest(c), core, 0.5f * Fx.ramp(t, 0, 24), 0.2f, GOLD[0], GOLD[1], GOLD[2], 0.5f);
        }
        if (t >= 6 && t < 84) {
            // the well: a black sphere ringed by an accretion disc
            PoseStack ps = c.ps;
            ps.pushPose();
            ps.translate(core.x, core.y, core.z);
            Fx.sphere(c.alpha("white"), ps, 3.8f * grow, 10, 20, 0f, 0.01f, 0f, 0.03f, 0.97f * in);
            ps.popPose();
            c.flat("ring_nodes", core, 14f * grow, c.age * 0.07f, GOLD[0], GOLD[1], GOLD[2], 0.95f * in);
            c.flat("halo_ring", core.add(0, 0.2, 0), 9f * grow, -c.age * 0.1f, 1f, 0.9f, 0.6f, 0.9f * in);
            c.flat("glyph", core.add(0, -0.2, 0), 11f * grow, c.age * 0.05f, GOLD[0], GOLD[1], GOLD[2], 0.6f * in);
            c.billboard("glow", core, 11f * grow, 0, GOLD[0], GOLD[1], GOLD[2], 0.5f * in);
            for (int i = 0; i < 14; i++) {
                float ang = (float) (i * Math.PI * 2 / 14) + c.age * 0.06f;
                float fr = (c.age * 0.03f + i / 14f) % 1f;
                float rr = (1f - fr) * 18f * grow + 3f;
                Vec3 from = core.add(Math.cos(ang + fr * 3f) * rr, Math.sin(ang * 2f) * 3f, Math.sin(ang + fr * 3f) * rr);
                c.beam(from, core.lerp(from, 0.8), 0.28f, 0.4f, GOLD[0], GOLD[1], GOLD[2], 0.6f * in);
                c.billboard("spark", from, 0.9f, 0, 1f, 0.9f, 0.6f, 0.85f * in);
            }
        }
        if (t >= 44) {
            float k = Mth.clamp((t - 46) / 24f, 0, 1);
            shock(c, a.add(0, 0.1, 0), R * 3f, k, GOLD, 1f - k * 0.5f);
            c.billboard("glow", core, 28f * (1f - k), 0, GOLD[0], GOLD[1], GOLD[2], 1f - k);
            starFlare(c, core, 16f * (1f - k), GOLD, 1f - k);
        }
    }

    /** Cage of light bars + chains around a locked player (Action Lock), shown for as long as the lock lasts. */
    public static void lockCage(FxCtx c, Entity victim, float intensity) {
        Vec3 v = victim.getPosition(c.pt).subtract(c.e.getPosition(c.pt));
        float h = victim.getBbHeight() + 0.6f;
        float open = Fx.smooth(Fx.ramp(intensity, 0f, 0.15f));
        int bars = 8;
        for (int i = 0; i < bars; i++) {
            float ang = (float) (i * Math.PI * 2 / bars) + c.age * 0.03f;
            Vec3 b = v.add(Math.cos(ang) * 0.95, -0.1, Math.sin(ang) * 0.95);
            c.beam(b, b.add(0, h * open, 0), 0.22f, 0.2f, GOLD[0], GOLD[1], GOLD[2], 0.95f);
        }
        c.flat("halo_ring", v.add(0, 0.05, 0), 1.5f, c.age * 0.1f, GOLD[0], GOLD[1], GOLD[2], 0.9f);
        c.flat("halo_ring", v.add(0, h, 0), 1.3f, -c.age * 0.1f, GOLD[0], GOLD[1], GOLD[2], 0.8f);
        c.flat("glyph", v.add(0, 0.07, 0), 2.2f, c.age * 0.05f, GOLD[0], GOLD[1], GOLD[2], 0.7f);
        // chains of light up to the boss' core
        Vec3 from = chest(c);
        for (int k = 0; k < 2; k++) {
            Vec3 mid = from.add(v.add(0, h * 0.5, 0)).scale(0.5).add(0, Math.sin(c.age * 0.1 + k * 3) * 2.4, 0);
            c.beam(from, mid, 0.5f, 0.2f, GOLD[0], GOLD[1], GOLD[2], 0.5f);
            c.beam(mid, v.add(0, h * 0.5, 0), 0.5f, 0.2f, GOLD[0], GOLD[1], GOLD[2], 0.5f);
        }
    }

    // ================================================================== The Crown of Divinity Shall Shatter (Quantum)
    private static void crown(FxCtx c) {
        float t = c.at;
        float R = IrontombAttack.CROWN_RADIUS;
        float in = Fx.window(t, 0, 24, 84, 100);
        Vec3 cp = new Vec3(0, 100, 0);
        skyCracks(c, Fx.ramp(t, 8, 60) * (1f - Fx.ramp(t, 96, 140)), VIOLET);
        // danger zone on the ground
        c.flat("glyph", new Vec3(0, 0.07, 0), R * 0.7f * Fx.ease(Fx.ramp(t, 0, 30)), c.age * 0.03f, VIOLET[0], VIOLET[1], VIOLET[2], 0.8f * Fx.window(t, 0, 20, 84, 100));
        c.flat("halo_ring", new Vec3(0, 0.09, 0), R * Fx.ease(Fx.ramp(t, 10, 50)), 0, VIOLET[0], VIOLET[1], VIOLET[2], 0.7f * Fx.window(t, 10, 40, 84, 100));
        if (t < 90) {
            // a crown of colossal spikes forms in the sky, with a halo and a violet sun in its heart
            float s = 1f + 0.25f * Fx.ramp(t, 0, 84);
            int n = 16;
            for (int pass = 0; pass < 2; pass++) {
                VertexConsumer vc = pass == 0 ? c.solid("body_iron") : c.add("body_glow");
                for (int i = 0; i < n; i++) {
                    float ang = (float) (i * Math.PI * 2 / n) + c.age * 0.02f;
                    float h = (i % 2 == 0 ? 44f : 28f) * s * Fx.smooth(Fx.ramp(t, 0, 30));
                    Vec3 base = cp.add(Math.cos(ang) * 44 * s, 0, Math.sin(ang) * 44 * s);
                    Vec3 dir = new Vec3(Math.cos(ang) * 0.2, 1, Math.sin(ang) * 0.2).normalize();
                    if (pass == 0) {
                        Fx.spike(vc, c.ps, base, dir, h, 3.4f, 0.7f, 0.6f, 0.95f, 1f);
                    } else {
                        Fx.spike(vc, c.ps, base.add(0, 0.1, 0), dir, h, 3.7f, VIOLET[0], VIOLET[1], VIOLET[2], 0.75f * in);
                    }
                }
            }
            c.flat("ring_nodes", cp.add(0, 0.3, 0), 62f * s, c.age * 0.02f, VIOLET[0], VIOLET[1], VIOLET[2], in);
            c.flat("glyph", cp.add(0, 0.5, 0), 54f * s, -c.age * 0.03f, 0.8f, 0.7f, 1f, 0.85f * in);
            c.billboard("glow", cp.add(0, 6, 0), 70f * in, 0, VIOLET[0], VIOLET[1], VIOLET[2], 0.9f);
            starFlare(c, cp.add(0, 6, 0), 46f * in, VIOLET, in);
        }
        // a light column on every player in range
        if (t > 14 && t < 96) {
            float grow = Fx.ramp(t, 14, 84);
            float w = Fx.window(t, 14, 30, 84, 96);
            for (Player p : c.level.players()) {
                if (p.isSpectator() || p.distanceToSqr(c.e) > (R + 6) * (R + 6)) {
                    continue;
                }
                Vec3 pp = p.getPosition(c.pt).subtract(c.e.getPosition(c.pt));
                c.flat("glyph", pp.add(0, 0.06, 0), 3.0f + 2.4f * grow, c.age * 0.12f, VIOLET[0], VIOLET[1], VIOLET[2], 0.9f * w);
                c.pillarAt(pp, 100f, 0.35f + 2.2f * grow, VIOLET, w * (0.3f + 0.7f * grow));
                c.beam(cp.add(0, 4, 0), pp.add(0, 1, 0), 0.2f + 0.5f * grow, 0.5f, VIOLET[0], VIOLET[1], VIOLET[2], 0.35f * w);
            }
        }
        // the shatter: the crown breaks apart and rains down over the whole battlefield
        if (t >= 80) {
            float k = Mth.clamp((t - 84) / 44f, 0, 1);
            rain(c, new Vec3(0, 0, 0), R * 0.85f, 46, 80f, 28f, 14f, 150f, 5f, VIOLET, 1f);
            shock(c, new Vec3(0, 0.12, 0), R * 1.15f, k * 1.1f, VIOLET, 1f - k * 0.5f);
            shock(c, new Vec3(0, 0.14, 0), R * 0.7f, k * 1.5f, WHITE, 0.9f * (1f - k));
            c.billboard("glow", cp, 140f * (1 - k), 0, VIOLET[0], VIOLET[1], VIOLET[2], 0.9f * (1 - k));
            for (Player p : c.level.players()) {
                if (p.isSpectator() || p.distanceToSqr(c.e) > (R + 6) * (R + 6)) {
                    continue;
                }
                Vec3 pp = p.getPosition(c.pt).subtract(c.e.getPosition(c.pt));
                c.pillarAt(pp, 160f, 5f * (1f - k) + 0.4f, WHITE, 1f - k);
                shock(c, pp.add(0, 0.1, 0), 22f, k * 1.6f, VIOLET, 1f - k);
            }
        }
    }

    // ================================================================== The Spirit's Prison Shall Burn (Imaginary)
    private static void prison(FxCtx c) {
        float t = c.at;
        float R = IrontombAttack.PRISON_RADIUS;
        Vec3 a = c.aim;
        float in = Fx.window(t, 0, 12, 76, 100);
        c.flat("glyph", a.add(0, 0.06, 0), R * 1.6f * Fx.ease(Fx.ramp(t, 0, 24)), c.age * 0.07f, GOLD[0], GOLD[1], GOLD[2], 0.95f * in);
        c.flat("halo_ring", a.add(0, 0.08, 0), R, -c.age * 0.04f, GOLD[0], GOLD[1], GOLD[2], 0.8f * in);
        c.beam(chest(c), a.add(0, 1.2, 0), 0.6f * Fx.ramp(t, 0, 20), 0.2f, GOLD[0], GOLD[1], GOLD[2], 0.55f * in);
        // six stars come down and bind the target in a hexagram of light
        float descend = Fx.ease(Fx.ramp(t, 0, 34));
        Vec3[] st = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            float ang = (float) (i * Math.PI / 3) + c.age * 0.01f;
            st[i] = a.add(Math.cos(ang) * (R + 2f), 70f * (1f - descend) + 12f, Math.sin(ang) * (R + 2f));
        }
        for (int i = 0; i < 6; i++) {
            starFlare(c, st[i], 9f * in, GOLD, in);
            c.beam(st[i], st[(i + 2) % 6], 0.45f, 0.3f, GOLD[0], GOLD[1], GOLD[2], 0.85f * in * descend);
            c.beam(st[i], a.add(st[i].x - a.x, 0, st[i].z - a.z), 0.3f, 0.3f, GOLD[0], GOLD[1], GOLD[2], 0.5f * in);
        }
        if (t >= 30) {
            float g = Fx.ease(Fx.ramp(t, 30, 54));
            Vec3 sun = a.add(0, 8f * g, 0);
            c.billboard("glow", sun, 16f * g, 0, GOLD[0], GOLD[1], GOLD[2], 0.8f * in);
            starFlare(c, sun, 14f * g, GOLD, in);
            for (int i = 0; i < 12; i++) {
                float ang = (float) (i * Math.PI * 2 / 12) + c.age * 0.05f;
                Vec3 b = a.add(Math.cos(ang) * (R - 1.5f), -0.2, Math.sin(ang) * (R - 1.5f));
                c.beam(b, b.add(0, 9 * g, 0), 0.4f, 0.3f, GOLD[0], GOLD[1], GOLD[2], 0.95f * in);
                c.billboardFrame("flame", b.add(0, 2.0 * g, 0), 2.6f, ((int) c.age + i * 5) % 16, 4, 1f, 0.8f, 0.3f, 0.85f * in);
            }
            c.flat("halo_ring", a.add(0, 9 * g, 0), R - 1.2f, c.age * 0.12f, GOLD[0], GOLD[1], GOLD[2], 0.9f * in);
        }
        if (t >= 60) {
            float k = Mth.clamp((t - 60) / 28f, 0, 1);
            float w = 1 - k;
            c.pillarAt(a, 300f, 7f * Fx.ease(Mth.clamp(k * 3f, 0, 1)) * w + 0.3f, GOLD, w);
            shock(c, a.add(0, 0.12, 0), 34f, k, GOLD, 1f - k * 0.4f);
            shock(c, a.add(0, 0.14, 0), 20f, k * 1.4f, WHITE, w);
            c.billboard("glow", a.add(0, 3, 0), 36f * w, 0, GOLD[0], GOLD[1], GOLD[2], w);
            starFlare(c, a.add(0, 5, 0), 26f * w, WHITE, w);
            for (int i = 0; i < 12; i++) {
                float ang = i * 0.5236f + Fx.rnd(i) * 0.3f;
                c.billboardFrame("flame", a.add(Math.cos(ang) * 9 * k * 2, 0, Math.sin(ang) * 9 * k * 2), 7f, ((int) c.age + i * 2) % 16, 4, 1f, 0.7f, 0.25f, 0.9f * w);
            }
        }
    }

    // ================================================================== Resist and Destroy
    private static void resist(FxCtx c) {
        float t = c.at;
        float a = Fx.smooth(Fx.ramp(t, 0, 14)) * (c.e.isResist() ? 1f : 0.2f);
        PoseStack ps = c.ps;
        ps.pushPose();
        ps.translate(0, 30, 0);
        Fx.sphere(c.add("beam"), ps, 46f, 16, 28, c.age * 0.01f, 1f, 0.72f, 0.3f, 0.26f * a);
        Fx.sphere(c.add("glyph"), ps, 47.5f, 12, 24, -c.age * 0.006f, 1f, 0.9f, 0.6f, 0.5f * a);
        ps.popPose();
        c.flat("glyph", new Vec3(0, 0.07, 0), 52f, c.age * 0.1f, GOLD[0], GOLD[1], GOLD[2], 0.8f * a);
        c.flat("ring_nodes", new Vec3(0, 0.1, 0), 56f, -c.age * 0.04f, GOLD[0], GOLD[1], GOLD[2], 0.7f * a);
        c.billboard("glow", chest(c), 36f, 0, GOLD[0], GOLD[1], GOLD[2], 0.45f * a);
        starFlare(c, face(c), 16f * a, GOLD, a);
    }

    // ================================================================== Grave Wound: the boss is exposed
    private static void stagger(FxCtx c) {
        float t = c.at;
        float a = Fx.window(t, 0, 6, 70, 90);
        Vec3 core = chest(c);
        for (int i = 0; i < 20; i++) {
            float seed = i * 7.3f;
            float life = ((c.age * 0.04f + Fx.rnd(i)) % 1f);
            Vec3 p = core.add(Math.cos(seed) * 5, life * 18, Math.sin(seed) * 4);
            c.billboard("spark", p, 1.3f * (1 - life), c.age * 0.2f + i, 1f, 0.45f, 0.2f, 0.9f * a * (1 - life));
        }
        c.billboard("glow", core, 26f, 0, 1f, 0.25f, 0.1f, 0.6f * a);
        starFlare(c, core, 10f * a, RED, a);
        shock(c, new Vec3(0, 0.1, 0), 40f, Mth.clamp(t / 20f, 0, 1), RED, 0.8f * (1 - Fx.ramp(t, 14, 26)));
        c.flat("crack", new Vec3(0, 0.12, 0), 24f, 0.3f, 1f, 0.35f, 0.2f, 0.7f * a);
    }

    // ================================================================== Immolate the Heavens, Abandon the World
    private static void immolate(FxCtx c) {
        float t = c.at;
        float R = IrontombAttack.IMMOLATE_RADIUS;
        float space = Fx.window(t, 6, 55, 160, 205);
        SkyFx.dome(c, space * 0.85f, 0.35f, 0.08f, 0.04f);
        skyCracks(c, Fx.ramp(t, 30, 110) * (1f - Fx.ramp(t, 160, 200)), RED);
        // the burning world falls out of the sky
        float fall = Fx.ease(Fx.ramp(t, 30, 142));
        float plunge = Fx.ramp(t, 142, 150);
        double y = 300 - 235 * fall - 90 * plunge * plunge;
        float vis = Fx.smooth(Fx.ramp(t, 24, 50)) * (1 - Fx.ramp(t, 150, 154));
        if (vis > 0.01f) {
            Vec3 p = new Vec3(0, y, -10);
            world(c, p, 70f, RED, vis, c.age * 0.002f);
            // the extensions haul it down
            for (int s = -1; s <= 1; s += 2) {
                Vec3 hand = CosmicFx.w(s * 70, 36, 0);
                c.beam(hand, p.add(s * 40, -40, 0), 4f * vis, 0.3f, RED[0], 0.4f, 0.15f, 0.6f * vis);
                c.billboard("glow", hand, 12f * vis, 0, RED[0], 0.4f, 0.15f, 0.8f * vis);
            }
        }
        meteorStorm(c, new Vec3(0, 0, 0), R * 0.9f, 26, 220f, 4.5f, RED, Fx.window(t, 30, 70, 140, 156));
        // ground: a world-sized array and burning waste
        float ga = Fx.window(t, 20, 60, 150, 170);
        c.flat("glyph", new Vec3(0, 0.07, 0), R * Fx.ease(Fx.ramp(t, 20, 100)), c.age * 0.02f, RED[0], 0.35f, 0.12f, 0.8f * ga);
        c.flat("halo_ring", new Vec3(0, 0.09, 0), R * 1.05f * Fx.ease(Fx.ramp(t, 20, 100)), 0, RED[0], 0.3f, 0.1f, 0.7f * ga);
        if (t > 60 && t < 152) {
            float w = Fx.ramp(t, 60, 120);
            for (int i = 0; i < 30; i++) {
                float ang = i * 0.2094f + Fx.rnd(i) * 0.4f;
                float rr = 14 + Fx.rnd(i + 11) * (R - 10) * w;
                c.billboardFrame("flame", new Vec3(Math.cos(ang) * rr, 0, Math.sin(ang) * rr), 8f + 10f * Fx.rnd(i + 5), ((int) c.age + i * 5) % 16, 4, 1f, 0.45f, 0.15f, 0.7f * w);
            }
        }
        // impact: the world-star hits and a nova rolls out to the edge of the danger zone
        if (t >= 150) {
            float k = Mth.clamp((t - 150) / 50f, 0, 1);
            float w = 1 - k;
            Vec3 o = new Vec3(0, 0, 0);
            shock(c, o.add(0, 0.15, 0), R * 1.15f, k * 1.15f, RED, w);
            shock(c, o.add(0, 0.17, 0), R * 0.75f, k * 1.5f, new float[]{1f, 0.9f, 0.7f}, w);
            shock(c, o.add(0, 0.19, 0), R * 0.4f, k * 1.9f, WHITE, w);
            PoseStack ps = c.ps;
            ps.pushPose();
            ps.translate(0, 10, 0);
            Fx.sphere(c.add("beam"), ps, R * 1.1f * Fx.ease(Mth.clamp(k * 1.3f, 0, 1)) + 5f, 14, 28, c.age * 0.01f, 1f, 0.45f, 0.18f, 0.5f * w);
            ps.popPose();
            c.pillarAt(o, 400f, 26f * w + 2f, RED, w);
            c.billboard("glow", new Vec3(0, 12, 0), 220f * w, 0, 1f, 0.5f, 0.2f, w);
            starFlare(c, new Vec3(0, 14, 0), 130f * w, WHITE, w);
            c.flat("crack", new Vec3(0, 0.2, 0), R * 0.9f * Fx.ease(Mth.clamp(k * 1.4f, 0, 1)) + 10f, 0.2f, 1f, 0.35f, 0.15f, 0.8f * w);
            for (int i = 0; i < 24; i++) {
                float ang = i * 0.2618f + Fx.rnd(i) * 0.2f;
                float rr = 10 + R * 0.9f * Fx.ease(k) * (0.4f + Fx.rnd(i + 3) * 0.6f);
                c.billboardFrame("flame", new Vec3(Math.cos(ang) * rr, 0, Math.sin(ang) * rr), 18f * w + 4, ((int) c.age + i * 2) % 16, 4, 1f, 0.45f, 0.15f, 0.9f * w);
            }
        }
    }

    // ================================================================== Ashes of All Begets Genesis
    private static void genesis(FxCtx c) {
        float t = c.at;
        float R = IrontombAttack.GENESIS_RADIUS;
        float space = Fx.smooth(Fx.ramp(t, 10, 120));
        float white = Fx.smooth(Fx.ramp(t, 232, 252));
        SkyFx.dome(c, space, 0.05f + 0.9f * white, 0.05f + 0.9f * white, 0.09f + 0.8f * white);
        skyCracks(c, Fx.ramp(t, 60, 180) * (1f - Fx.ramp(t, 250, 296)), GOLD);
        // a second universe rises out of the ground and swallows the sky
        float rise = Fx.ease(Fx.ramp(t, 40, 225));
        double y = -80 + 270 * rise;
        float vis = Fx.smooth(Fx.ramp(t, 40, 80)) * (1 - Fx.ramp(t, 236, 244));
        if (vis > 0.01f) {
            Vec3 p = new Vec3(0, y, -30);
            float rad = 60f + 70f * rise;
            PoseStack ps = c.ps;
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            Fx.sphere(c.alpha("planet"), ps, rad, 24, 48, c.age * 0.0015f, 0.95f, 0.9f, 0.9f, vis);
            Fx.sphere(c.add("planet"), ps, rad * 1.02f, 16, 32, c.age * 0.0025f, 1f, 0.8f, 0.5f, 0.45f * vis);
            ps.popPose();
            c.billboard("glow", p, rad * 3f, 0, 1f, 0.7f, 0.35f, 0.45f * vis);
            c.flat("ring_nodes", p, rad * 1.8f, c.age * 0.006f, GOLD[0], GOLD[1], GOLD[2], 0.8f * vis);
            c.flat("glyph", p.add(0, -rad, 0), rad * 1.1f, c.age * 0.015f, GOLD[0], GOLD[1], GOLD[2], 0.8f * vis);
            for (int i = 0; i < 24; i++) {
                float ang = i * 0.2618f + c.age * 0.004f;
                Vec3 sp = new Vec3(Math.cos(ang) * 190, 50 + Fx.rnd(i * 3) * 160, Math.sin(ang) * 190);
                starFlare(c, sp, (12f + 22f * Fx.rnd(i)) * vis * (0.7f + 0.3f * Mth.sin(c.age * 0.1f + i)), i % 2 == 0 ? GOLD : RED, vis);
            }
        }
        // the arena
        float a = Fx.window(t, 10, 60, 236, 262);
        c.flat("glyph", new Vec3(0, 0.07, 0), R * 0.85f * Fx.ease(Fx.ramp(t, 10, 120)), c.age * 0.012f, GOLD[0], GOLD[1], GOLD[2], 0.8f * a);
        c.flat("halo_ring", new Vec3(0, 0.09, 0), R * Fx.ease(Fx.ramp(t, 10, 120)), -c.age * 0.01f, 1f, 0.9f, 0.7f, 0.7f * a);
        meteorStorm(c, new Vec3(0, 0, 0), R * 0.9f, 22, 260f, 5f, GOLD, Fx.window(t, 40, 90, 225, 240));
        for (int i = 0; i < 14; i++) {
            float ang = i * 0.449f + c.age * 0.01f;
            float rr = 40 + (R - 30) * Fx.ease(Fx.ramp(t, 20, 150));
            c.pillarAt(new Vec3(Math.cos(ang) * rr, 0, Math.sin(ang) * rr), 300f, 2f + 3f * Fx.rnd(i), GOLD, 0.6f * a);
        }
        // the end: everything turns to ash
        if (t >= 232) {
            float k = Mth.clamp((t - 232) / 64f, 0, 1);
            float w = 1 - k;
            for (int j = 0; j < 4; j++) {
                float kk = Mth.clamp(k * (1.1f + j * 0.35f), 0, 1);
                shock(c, new Vec3(0, 0.15 + 0.02 * j, 0), R * (1.2f - j * 0.22f), kk, j % 2 == 0 ? GOLD : WHITE, w);
            }
            PoseStack ps = c.ps;
            ps.pushPose();
            ps.translate(0, 20, 0);
            Fx.sphere(c.add("beam"), ps, R * 1.2f * Fx.ease(Mth.clamp(k * 1.2f, 0, 1)) + 8f, 16, 32, c.age * 0.01f, 1f, 0.9f, 0.7f, 0.6f * w);
            ps.popPose();
            c.pillarAt(Vec3.ZERO, 420f, 40f * w + 3f, WHITE, w);
            c.billboard("glow", new Vec3(0, 12, 0), 300f * w, 0, 1f, 0.9f, 0.6f, w);
            starFlare(c, new Vec3(0, 16, 0), 200f * w, WHITE, w);
            for (int i = 0; i < 40; i++) {
                float fr = (c.age * 0.015f * (0.5f + Fx.rnd(i)) + Fx.rnd(i + 9)) % 1f;
                double ang = Fx.rnd(i * 3) * 6.283;
                float rr = 20 + Fx.rnd(i * 5) * (R - 20);
                c.billboard("spark", new Vec3(Math.cos(ang) * rr, 70 * (1f - fr), Math.sin(ang) * rr), 1.6f, 0, 0.7f, 0.65f, 0.6f, 0.7f * Fx.ramp(t, 245, 262) * (1f - Fx.ramp(t, 280, 298)));
            }
        }
    }
}
