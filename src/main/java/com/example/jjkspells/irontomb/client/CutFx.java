package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.irontomb.IrontombCut;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** World-side visuals of the stand-alone cutscenes (intro, phase changes, death). Camera and HUD live elsewhere. */
public final class CutFx {
    private CutFx() {
    }

    public static void draw(FxCtx c) {
        switch (c.cut) {
            case IrontombCut.INTRO -> intro(c);
            case IrontombCut.PHASE2 -> phase(c, AttackFx.GOLD, 40f);
            case IrontombCut.PHASE3 -> phase(c, AttackFx.RED, 60f);
            case IrontombCut.DEATH -> death(c);
            default -> { }
        }
    }

    private static void intro(FxCtx c) {
        float t = c.ct;
        float[] R = AttackFx.RED;
        // the ground splits
        float cr = Fx.window(t, 0, 6, 110, 150);
        c.flatFrame("crack", new Vec3(0, 0.08, 0), 22f, (int) (Fx.ramp(t, 0, 60) * 15), 4, R[0], 0.5f, 0.2f, 0.95f * cr);
        c.flat("glyph", new Vec3(0, 0.1, 0), 18f * Fx.ease(Fx.ramp(t, 20, 90)), c.age * 0.03f, R[0], 0.4f, 0.15f, 0.8f * Fx.window(t, 20, 60, 150, 170));
        // dust rings as the tomb rises
        for (int i = 0; i < 3; i++) {
            float s = 24 + i * 34;
            float k = Mth.clamp((t - s) / 30f, 0, 1);
            if (t > s && k < 1) {
                c.flatFrame("ring", new Vec3(0, 0.12, 0), 16f + i * 4, (int) (k * 15), 4, 0.8f, 0.45f, 0.3f, 0.8f);
            }
        }
        // light erupts from the chest as the halo ignites
        float ig = Fx.window(t, 92, 118, 150, 170);
        c.billboard("glow", AttackFx.chest(c), 14f * ig, 0, R[0], 0.45f, 0.2f, 0.8f * ig);
        for (int i = 0; i < 10; i++) {
            float ang = i * 0.628f + 0.3f;
            Vec3 end = AttackFx.chest(c).add(Math.cos(ang) * 22, Math.sin(ang * 1.7) * 14 + 4, Math.sin(ang) * 22);
            c.beam(AttackFx.chest(c), end, 0.9f * ig, 0.3f, R[0], 0.55f, 0.25f, 0.55f * ig);
        }
        float fl = Fx.window(t, 112, 118, 122, 140);
        c.billboard("spark", new Vec3(0, 7.4, 1.4), 16f * fl, 0, 1f, 0.85f, 0.6f, fl);
        c.billboard("spark", AttackFx.chest(c), 20f * ig, c.age * 0.02f, 1f, 0.6f, 0.35f, 0.9f * ig);
    }

    private static void phase(FxCtx c, float[] col, float radius) {
        float t = c.ct;
        float charge = Fx.ramp(t, 0, 58);
        float burst = Mth.clamp((t - 58) / 40f, 0, 1);
        Vec3 ch = AttackFx.chest(c);
        float w = Fx.window(t, 0, 20, 60, 90);
        c.billboard("glow", ch, 12f * charge * (1 - burst), 0, col[0], col[1], col[2], 0.9f * (1 - burst));
        c.billboard("spark", ch, 7f * charge * (1 - burst), c.age * 0.08f, 1f, 0.95f, 0.85f, 1f - burst);
        c.flat("glyph", new Vec3(0, 0.08, 0), radius * 0.5f * Fx.ease(Fx.ramp(t, 0, 50)), c.age * 0.06f, col[0], col[1], col[2], 0.9f * w);
        for (int i = 0; i < 12; i++) {
            float ang = i * 0.5236f + c.age * 0.03f;
            Vec3 e = new Vec3(Math.cos(ang) * radius * 0.6, 0.5, Math.sin(ang) * radius * 0.6);
            c.beam(e, ch, 0.35f * charge, 0.5f, col[0], col[1], col[2], 0.45f * w);
        }
        if (t >= 58) {
            c.flatFrame("ring", new Vec3(0, 0.12, 0), radius * 1.5f * Fx.ease(burst) + 4, (int) (burst * 15), 4, col[0], col[1], col[2], 1f);
            c.flatFrame("ring", new Vec3(0, 0.14, 0), radius * 0.9f * Fx.ease(Mth.clamp(burst * 1.5f, 0, 1)) + 3, (int) (Mth.clamp(burst * 1.5f, 0, 1) * 15), 4, 1f, 0.95f, 0.85f, 0.9f);
            c.pillarAt(Vec3.ZERO, 90f, 7f * (1 - burst) + 0.5f, col, 1 - burst);
        }
    }

    private static void death(FxCtx c) {
        float t = c.ct;
        float[] R = AttackFx.RED;
        Vec3 ch = AttackFx.chest(c);
        float build = Fx.ramp(t, 0, 100);
        // fractures spread over the body
        c.billboardFrame("crack", ch.add(0, 0.2, 0), 7f, (int) (build * 15), 4, 1f, 0.55f, 0.25f, 0.95f * (1 - Fx.ramp(t, 100, 104)));
        c.billboard("glow", ch, 6f + 16f * build, 0, R[0], 0.4f, 0.18f, 0.6f + 0.4f * build);
        // rays tear out of the chest
        int n = 6 + (int) (build * 16);
        for (int i = 0; i < n; i++) {
            float ang = i * 2.3999f;
            float up = (Fx.rnd(i + 3) - 0.3f) * 1.6f;
            float len = 6 + 50 * build * (0.4f + Fx.rnd(i));
            Vec3 end = ch.add(Math.cos(ang) * len, up * len * 0.5, Math.sin(ang) * len);
            c.beam(ch, end, 0.5f + 1.2f * build, 0.3f, 1f, 0.55f + 0.3f * Fx.rnd(i + 9), 0.3f, 0.7f * (1 - Fx.ramp(t, 108, 130)));
        }
        if (t >= 100) {
            float k = Mth.clamp((t - 100) / 60f, 0, 1);
            for (int j = 0; j < 3; j++) {
                float kk = Mth.clamp(k * (1.1f + 0.4f * j), 0, 1);
                c.flatFrame("ring", new Vec3(0, 0.12 + 0.02 * j, 0), (60f - 14 * j) * Fx.ease(kk) + 4, (int) (kk * 15), 4, 1f, 0.7f + 0.1f * j, 0.4f + 0.2f * j, 1f - k * 0.7f);
            }
            c.pillarAt(Vec3.ZERO, 160f, 12f * (1 - k) + 1f, AttackFx.WHITE, 1 - k);
            c.billboard("glow", ch, 90f * (1 - k), 0, 1f, 0.7f, 0.4f, 1 - k);
            c.billboard("spark", ch, 60f * (1 - k), c.age * 0.04f, 1f, 1f, 0.95f, 1 - k);
        }
    }
}
