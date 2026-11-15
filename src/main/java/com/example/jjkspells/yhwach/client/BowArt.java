package com.example.jjkspells.yhwach.client;

import com.example.jjkspells.yhwach.entity.BowFrame;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** The Reishi bow look shared by the multi-bow spells (same crescent, string, arrow and sparkle styling as Heiliger Pfeil). */
final class BowArt {
    static final double MAX_DRAW = 1.9;
    static final double ARROW_LEN = 4.2;

    private static final float[][] LAYER_COL = {Pen.BLUE, Pen.CYAN, Pen.CORE, Pen.WHITE};
    private static final double[] LAYER_MUL = {3.4, 1.9, 0.85, 0.34};
    private static final float[] LAYER_ALPHA = {0.30f, 0.55f, 0.90f, 1.0f};

    private BowArt() {
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    static double ease(float x) {
        double k = 1.0 - clamp01(x);
        return 1.0 - k * k * k;
    }

    static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    static Vec3 nock(BowFrame fr, double draw, double scale) {
        return fr.center.add(fr.f.scale((-0.5 - draw) * scale));
    }

    /** Halo behind the bow and at its tips (GLOW texture). */
    static void glow(Pen p, BowFrame fr, double reveal, float a, float t, double scale) {
        if (a <= 0.01f || reveal <= 0.02) {
            return;
        }
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.55f);
        p.billboard(fr.center, 5.2 * reveal * scale, 0, Pen.BLUE, (0.20f + 0.06f * pulse) * a, 0, 0, 1, 1);
        p.billboard(fr.center, 2.8 * reveal * scale, 0, Pen.CYAN, (0.20f + 0.06f * pulse) * a, 0, 0, 1, 1);
        p.billboard(fr.bowPoint(1, reveal * scale), 1.1 * reveal * scale, 0, Pen.CYAN, 0.55f * a, 0, 0, 1, 1);
        p.billboard(fr.bowPoint(-1, reveal * scale), 1.1 * reveal * scale, 0, Pen.CYAN, 0.55f * a, 0, 0, 1, 1);
    }

    /** Crescent limbs, tip blades and the glowing string (STREAK texture). */
    static void bow(Pen p, BowFrame fr, double reveal, float a, double draw, float t, double scale) {
        if (a <= 0.01f || reveal <= 0.02) {
            return;
        }
        int n = 40;
        for (int layer = 0; layer < 4; layer++) {
            Vec3 prev = null;
            double prevW = 0;
            for (int i = 0; i <= n; i++) {
                double tt = (-1.0 + 2.0 * i / n) * reveal;
                Vec3 pt = fr.bowPoint(tt, scale);
                double at = Math.abs(tt);
                double w = (0.06 + 0.46 * Math.pow(1.0 - Math.pow(at, 1.8), 1.4)) * (1.0 + 0.07 * Math.sin(t * 0.7 + tt * 3.0)) * LAYER_MUL[layer] * scale;
                if (prev != null) {
                    float u0 = (i - 1) * 0.35f - t * 0.05f;
                    p.ribbon(prev, pt, prevW, w, LAYER_ALPHA[layer] * a, LAYER_ALPHA[layer] * a, u0, u0 + 0.35f, LAYER_COL[layer]);
                }
                prev = pt;
                prevW = w;
            }
        }
        if (reveal > 0.9) {
            for (int sign = -1; sign <= 1; sign += 2) {
                Vec3 tip = fr.bowPoint(sign, scale);
                Vec3 dir = tip.subtract(fr.bowPoint(sign * 0.93, scale)).normalize();
                Vec3 ext = tip.add(dir.scale((0.9 + 0.1 * Math.sin(t * 0.8)) * scale));
                p.ribbon(tip, ext, 0.22 * scale, 0.0, 0.9f * a, 0.0f, 0f, 1f, Pen.CYAN);
                p.ribbon(tip, ext, 0.10 * scale, 0.0, 1.0f * a, 0.0f, 0f, 1f, Pen.WHITE);
            }
        }
        float sa = clamp01((float) (reveal - 0.6) / 0.4f) * a;
        if (sa > 0.01f) {
            Vec3 nk = nock(fr, draw, scale);
            Vec3 lo = fr.bowPoint(-1, scale);
            Vec3 hi = fr.bowPoint(1, scale);
            float[][] sc = {Pen.BLUE, Pen.CYAN, Pen.WHITE};
            double[] sw = {0.34, 0.17, 0.06};
            float[] sal = {0.30f, 0.6f, 0.95f};
            for (int l = 0; l < 3; l++) {
                p.ribbon(lo, nk, sw[l] * scale, sal[l] * sa, 0f, 2.5f, sc[l]);
                p.ribbon(nk, hi, sw[l] * scale, sal[l] * sa, 0f, 2.5f, sc[l]);
            }
        }
    }

    /** Arrow resting on the string: shaft, tapered head, fletching (STREAK texture). {@code grow} 0..1 is how complete it is. */
    static void arrow(Pen p, BowFrame fr, double draw, double grow, float a, float t, double scale) {
        double len = ARROW_LEN * scale * grow;
        if (a <= 0.01f || len < 0.3) {
            return;
        }
        Vec3 tail = nock(fr, draw, scale);
        Vec3 head = tail.add(fr.f.scale(len));
        float[][] ac = {Pen.CYAN, Pen.CORE, Pen.WHITE};
        double[] aw = {0.50, 0.22, 0.09};
        float[] aal = {0.5f, 0.95f, 1.0f};
        for (int l = 0; l < 3; l++) {
            p.ribbon(tail, head.subtract(fr.f.scale(0.25 * scale)), aw[l] * scale, aal[l] * a, -t * 0.1f, -t * 0.1f + 2.0f, ac[l]);
        }
        float[][] hc = {Pen.BLUE, Pen.CYAN, Pen.WHITE};
        double[] hw = {1.35, 0.85, 0.40};
        float[] hal = {0.5f, 0.9f, 1.0f};
        for (int l = 0; l < 3; l++) {
            p.ribbon(head.subtract(fr.f.scale(scale)), head.add(fr.f.scale(0.75 * scale)), hw[l] * scale, 0.0, hal[l] * a, hal[l] * a * 0.4f, 0f, 1f, hc[l]);
        }
        if (len > 1.0) {
            Vec3[] offs = {fr.r.scale(0.55 * scale), fr.r.scale(-0.55 * scale), fr.u.scale(0.55 * scale), fr.u.scale(-0.55 * scale)};
            for (Vec3 off : offs) {
                p.ribbon(tail.add(fr.f.scale(0.95 * scale)), tail.add(off).add(fr.f.scale(0.1 * scale)), 0.10 * scale, 0.03, 0.85f * a, 0.5f * a, 0f, 1f, Pen.CYAN);
            }
        }
    }

    /** Twinkling stars hugging the limbs (SPARKLE texture). */
    static void sparkles(Pen p, BowFrame fr, double reveal, float a, float t, long seed, int count, double scale) {
        if (a <= 0.02f || reveal <= 0.05) {
            return;
        }
        Random rnd = new Random(seed);
        for (int i = 0; i < count; i++) {
            double tt = (rnd.nextDouble() * 2 - 1) * reveal;
            double ang = rnd.nextDouble() * Math.PI * 2 + t * 0.03 * (0.5 + rnd.nextDouble());
            double rad = (0.15 + rnd.nextDouble() * 1.1) * scale;
            double size = (0.12 + rnd.nextDouble() * 0.34) * scale;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.25 + rnd.nextDouble() * 0.3) + rnd.nextDouble() * 6.28);
            Vec3 pos = fr.bowPoint(tt, scale).add(fr.r.scale(Math.cos(ang) * rad)).add(fr.f.scale(Math.sin(ang) * rad));
            p.billboard(pos, size * (0.6 + 0.6 * tw), rnd.nextDouble() * 1.57 + t * 0.02, (i & 1) == 0 ? Pen.WHITE : Pen.CYAN, 0.95f * tw * a * (float) reveal);
        }
    }
}
