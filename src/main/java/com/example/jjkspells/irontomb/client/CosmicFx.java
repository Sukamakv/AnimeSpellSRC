package com.example.jjkspells.irontomb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Irontomb's cosmic manifestation, rendered entirely client-side: the part-table body, the ring, the red constellation, haze and debris.
 * The entity's hitbox stays small and separate; none of this uses the bounding box.
 */
public final class CosmicFx {
    private CosmicFx() {
    }

    /** Pose-driven inputs from the renderer (arm lift 0..1, spread degrees, head dip degrees, glow). */
    public static final class Pose {
        public float lL, lR, spread, headDip, glow, grip;
    }

    // ------------------------------------------------------------------ body
    /** Draws the dark shell, its red rim light and the violet/red emissive parts. The pose stack must be at the body origin, facing +Z. */
    public static void body(FxCtx c, Pose p) {
        float[] P = IrontombModel.PARTS;
        int S = IrontombModel.STRIDE;
        int n = P.length / S;
        float amp = 1f + 0.3f * (c.phase - 1) + 0.4f * p.glow;
        float glow = Mth.clamp(0.55f + 0.5f * p.glow, 0f, 1.4f);
        float rimA = 0.5f * Mth.clamp(0.6f + 0.4f * p.glow, 0f, 1.3f);
        boolean p3 = c.phase >= 3;
        for (int pass = 0; pass < 3; pass++) {
            VertexConsumer vc = pass == 0 ? c.solid("shell") : pass == 1 ? c.add("white") : c.add("violet");
            for (int i = 0; i < n; i++) {
                int o = i * S;
                int mat = (int) P[o + 1];
                if ((pass == 2) != (mat >= 2)) {
                    continue;
                }
                PoseStack ps = c.ps;
                ps.pushPose();
                group(c, p, (int) P[o], (int) P[o + 2], amp);
                ps.translate(P[o + 3], P[o + 4], P[o + 5]);
                ps.mulPose(Axis.YP.rotationDegrees(P[o + 6]));
                ps.mulPose(Axis.XP.rotationDegrees(P[o + 7]));
                ps.mulPose(Axis.ZP.rotationDegrees(P[o + 8]));
                if ((int) P[o] == 8) {
                    ps.mulPose(Axis.YP.rotationDegrees(c.age * (Fx.rnd((int) P[o + 2] * 3) - 0.5f) * 0.9f));
                }
                float len = P[o + 9], w0 = P[o + 10], d0 = P[o + 11], w1 = P[o + 12], d1 = P[o + 13];
                if (pass == 0) {
                    float r = mat == 1 ? 1f : 0.95f, g = mat == 1 ? 0.8f : 0.9f, b = mat == 1 ? 0.82f : 1f;
                    if (p3) {
                        r = 1f;
                        g *= 0.85f;
                    }
                    Fx.frustum(vc, ps, len, w0, d0, w1, d1, 9f, r, g, b, 1f, 0);
                } else if (pass == 1) {
                    Fx.frustum(vc, ps, len, w0, d0, w1, d1, 9f, 1f, 0.2f, 0.16f, rimA, 1);
                } else {
                    float pulse = 0.85f + 0.15f * Mth.sin(c.age * 0.12f + i * 0.7f);
                    if (mat == 2) {
                        Fx.frustum(vc, ps, len, w0, d0, w1, d1, 10f, p3 ? 1f : 0.95f, p3 ? 0.55f : 0.9f, 1f, Mth.clamp(glow * pulse, 0f, 1f), 2);
                    } else {
                        Fx.frustum(vc, ps, len, w0, d0, w1, d1, 10f, 1f, 0.55f, 0.4f, Mth.clamp(glow * pulse, 0f, 1f), 2);
                    }
                }
                ps.popPose();
            }
        }
    }

    /** The ladders of small cross glyphs hanging in the gaps of the extensions and around the waist. */
    public static void chains(FxCtx c, Pose p) {
        PoseStack ps = c.ps;
        float amp = 1f + 0.3f * (c.phase - 1) + 0.4f * p.glow;
        for (int g = 4; g <= 5; g++) {
            float s = g == 4 ? -1f : 1f, k = g == 4 ? 0.82f : 1f;
            for (int plane = 0; plane < 2; plane++) {
                ps.pushPose();
                group(c, p, g, 2, amp);
                ps.translate(s * (15f + 32f * k), 34f, 0f);
                ps.mulPose(Axis.YP.rotationDegrees(plane * 90f));
                ps.mulPose(Axis.ZP.rotationDegrees(90f));
                Fx.quadXY(c.alpha("crosschain"), ps, 14f * k, 1.75f * k, 1f, 1f, 1f, 1f);
                ps.popPose();
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            ps.pushPose();
            ps.translate(0, 10.5f, side * 7.4f);
            Fx.quadXY(c.alpha("crosschain"), ps, 22f, 2.75f, 1f, 1f, 1f, 1f);
            ps.popPose();
        }
    }

    private static void group(FxCtx c, Pose p, int g, int seg, float amp) {
        PoseStack ps = c.ps;
        float age = c.age;
        switch (g) {
            case 1 -> {
                ps.translate(0, 30, 0);
                ps.mulPose(Axis.XP.rotationDegrees(p.headDip * 0.6f + Mth.sin(age * 0.02f) * 0.8f));
                ps.translate(0, -30, 0);
            }
            case 2, 3 -> {
                float s = g == 2 ? -1 : 1;
                ps.translate(s * Mth.sin(age * 0.02f) * 0.5f * amp, Mth.sin(age * 0.03f + s) * 0.9f * amp, 0);
            }
            case 4, 5 -> {
                float s = g == 4 ? -1 : 1, lift = s > 0 ? p.lR : p.lL;
                ps.translate(s * 16, 34, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(s * (lift * 26f + Mth.sin(age * 0.017f + s) * 1.4f * amp + (p.spread - 6f) * 0.15f)));
                ps.translate(-s * 16, -34, 0);
                ps.translate(0, Mth.sin(age * 0.021f + seg * 1.3f + s) * 0.35f * seg * amp, Mth.cos(age * 0.017f + seg * 0.9f) * 0.5f * seg * amp);
            }
            case 6, 7 -> {
                float s = g == 6 ? -1 : 1, lift = s > 0 ? p.lR : p.lL;
                ps.translate(s * 14, 26, 0);
                ps.mulPose(Axis.YP.rotationDegrees(-s * (lift * 70f + p.grip * 28f)));
                ps.mulPose(Axis.ZP.rotationDegrees(s * (lift * 35f + (p.spread - 6f) * 0.3f + Mth.sin(age * 0.025f + s) * 1.2f)));
                ps.translate(-s * 14, -26, 0);
            }
            case 8 -> {
                float h = Fx.rnd(seg * 7 + 1), k = 0.7f + h * 0.6f;
                ps.translate(Mth.sin(age * 0.011f * k + seg) * (2.5f + 3f * amp), Mth.sin(age * 0.015f * k + seg * 2f) * (1.5f + 2f * amp), Mth.cos(age * 0.009f * k + seg) * (2.5f + 3f * amp));
            }
            case 9 -> {
                ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(age * 0.03f) * 1.6f));
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ environment
    /** The boss' current facing, set by the renderer each frame so attack effects can use the body frame. */
    public static float yaw;

    /** A body-frame offset (x right, y up, z forward) as a boss-space vector. */
    public static Vec3 w(double x, double y, double z) {
        return yawed(new Vec3(x, y, z), yaw);
    }

    /** Position of constellation star i in boss space. */
    public static Vec3 starPos(int i) {
        float[] S = IrontombModel.STARS;
        int n = S.length / 5;
        int k = Math.floorMod(i, n);
        return w(S[k * 5], S[k * 5 + 1], S[k * 5 + 2]);
    }

    private static Vec3 yawed(Vec3 p, float yaw) {
        double s = Math.sin(yaw), co = Math.cos(yaw);
        return new Vec3(p.x * co + p.z * s, p.y, -p.x * s + p.z * co);
    }

    static void sprite(FxCtx c, String tex, Vec3 pos, float hw, float hh, float r, float g, float b, float a) {
        PoseStack ps = c.ps;
        ps.pushPose();
        ps.translate(pos.x, pos.y, pos.z);
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        Fx.quadXY(c.add(tex), ps, hw, hh, r, g, b, a);
        ps.popPose();
    }

    /** The big node-ring behind the head (pose stack at the boss origin, no yaw). */
    public static void ring(FxCtx c, float yaw, float vis, float glow) {
        if (vis < 0.01f) {
            return;
        }
        PoseStack ps = c.ps;
        float R = 44f * (1f + 0.12f * (c.phase - 1)) * (0.6f + 0.4f * vis);
        float a = Mth.clamp((0.7f + glow * 0.3f) * vis, 0f, 1f);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotation(yaw));
        ps.translate(0, 34, -8);
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotation(c.age * 0.004f));
        Fx.quadXY(c.add("ring_nodes"), ps, R, R, 1f, 1f, 1f, a);
        ps.popPose();
        ps.translate(0, 0, -0.1);
        ps.mulPose(Axis.ZP.rotation(-c.age * 0.007f + 0.6f));
        Fx.quadXY(c.add("ring_nodes"), ps, R * 0.8f, R * 0.8f, 1f, 0.75f, 0.7f, a * 0.55f);
        ps.popPose();
    }

    /** Constellation, haze, debris and meteors, all in the boss frame (yawed so they stay behind it as seen from its target). */
    public static void environment(FxCtx c, float yaw, float vis, float glow) {
        if (vis < 0.01f) {
            return;
        }
        float age = c.age;
        float bright = vis * (0.8f + 0.25f * (c.phase - 1) + 0.25f * Mth.clamp(glow, 0f, 1.5f));
        // atmosphere
        sprite(c, "glow", yawed(new Vec3(0, 34, -30), yaw), 110f, 110f, 1f, 0.22f, 0.16f, 0.30f * vis);
        sprite(c, "glow", yawed(new Vec3(0, 33, 9), yaw), 26f, 26f, 0.75f, 0.35f, 1f, Mth.clamp(0.25f + 0.2f * glow, 0f, 0.7f) * vis);
        for (int i = 0; i < 16; i++) {
            double ang = i * 2.4 + age * 0.0006;
            float rad = 12 + (i * 7) % 52;
            Vec3 pos = yawed(new Vec3(Math.cos(ang) * rad, 2 + (i % 4) * 3 + Math.sin(age * 0.01 + i) * 1.2, Math.sin(ang) * rad * 0.8), yaw);
            float size = 22 + (i % 5) * 6;
            sprite(c, "smoke", pos, size, size * 0.75f, 1f, 0.38f, 0.32f, 0.45f * vis);
        }
        for (int i = 0; i < 6; i++) {
            Vec3 pos = yawed(new Vec3(-70 + i * 28, 18 + (i % 3) * 14, -55 - (i % 2) * 20), yaw);
            sprite(c, "smoke", pos, 48f, 36f, 0.9f, 0.25f, 0.28f, 0.22f * vis);
        }

        // star nodes and the lines between them
        float[] S = IrontombModel.STARS;
        int ns = S.length / 5;
        Vec3[] sp = new Vec3[ns];
        float[] tw = new float[ns];
        for (int i = 0; i < ns; i++) {
            sp[i] = yawed(new Vec3(S[i * 5], S[i * 5 + 1], S[i * 5 + 2]), yaw);
            tw[i] = 0.78f + 0.22f * Mth.sin(age * (0.09f + 0.06f * Fx.rnd(i * 5)) + S[i * 5 + 4] * 3.1f);
        }
        int[] L = IrontombModel.LINKS;
        for (int i = 0; i < L.length; i += 2) {
            Vec3 a = sp[L[i]], b = sp[L[i + 1]];
            float pulse = 0.55f + 0.45f * Mth.sin(age * 0.05f + i);
            c.beam(a, b, 0.35f, 0f, 1f, 0.2f, 0.16f, 0.30f * pulse * bright);
            float t = (age * 0.012f + i * 0.37f) % 1f;
            Vec3 q = a.add(b.subtract(a).scale(t));
            sprite(c, "spark", q, 1.8f, 1.8f, 1f, 0.7f, 0.5f, 0.9f * bright);
        }
        for (int i = 0; i < ns; i++) {
            float sz = S[i * 5 + 3] * tw[i];
            sprite(c, "star4", sp[i], sz * 2.1f, sz * 2.1f, 1f, 1f, 1f, Mth.clamp(bright * tw[i], 0f, 1f));
            sprite(c, "hflare", sp[i], sz * 17f, sz * 1.1f, 1f, 0.8f, 0.8f, 0.55f * bright * tw[i]);
            if (i < 7) {   // dotted comet trail up and to the right, like the reference
                for (int d = 1; d <= 7; d++) {
                    Vec3 q = sp[i].add(yawed(new Vec3(d * sz * 0.42, d * sz * 0.14, 0), yaw));
                    sprite(c, "spark", q, 0.55f, 0.55f, 1f, 0.7f, 0.4f, (1f - d / 8f) * 0.9f * bright);
                }
            }
        }

        // gold meteor streaks drifting down and to the left of the screen
        for (int i = 0; i < 24; i++) {
            float h = Fx.rnd(i * 11 + 3);
            Vec3 base = new Vec3(-150 + Fx.rnd(i * 13) * 300, 20 + Fx.rnd(i * 17 + 1) * 120, -30 - Fx.rnd(i * 19 + 2) * 95);
            float run = 70f + 60f * h, t = (age * (0.5f + h) * 0.9f + h * run) % run;
            Vec3 dir = new Vec3(1.0, -0.22, 0).normalize();
            Vec3 head = yawed(base.add(dir.scale(t - run * 0.5f)), yaw);
            Vec3 tail = yawed(base.add(dir.scale(t - run * 0.5f - 7 - 5 * h)), yaw);
            float fade = Fx.window(t, 0, run * 0.15f, run * 0.8f, run);
            c.beam(tail, head, 0.22f, 0f, 1f, 0.82f, 0.4f, 0.75f * fade * vis);
        }

        // debris chips and embers
        int cnt = 40 + 16 * c.phase;
        PoseStack ps = c.ps;
        for (int i = 0; i < cnt; i++) {
            float h = Fx.rnd(i * 23 + 5);
            double ang = Fx.rnd(i * 29) * Math.PI * 2 + age * 0.0035 * (h - 0.5);
            float rad = 16 + Fx.rnd(i * 31 + 7) * 70;
            float y = ((Fx.rnd(i * 37 + 9) * 90f + age * (0.02f + 0.05f * h)) % 90f) - 4f;
            Vec3 pos = yawed(new Vec3(Math.cos(ang) * rad, y, Math.sin(ang) * rad * 0.9), yaw);
            if ((i & 3) == 0) {
                sprite(c, "spark", pos, 0.35f + 0.25f * h, 0.35f + 0.25f * h, 1f, 0.45f, 0.25f, 0.8f * vis);
            } else {
                ps.pushPose();
                ps.translate(pos.x, pos.y, pos.z);
                ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
                ps.mulPose(Axis.ZP.rotation(age * 0.05f * (h - 0.5f) * 4f + i));
                float s = 0.3f + 0.45f * h;
                Fx.quadXY(c.alpha("shard"), ps, s, s, 1f, 0.85f, 0.85f, 0.95f * vis);
                ps.popPose();
            }
        }
    }
}
