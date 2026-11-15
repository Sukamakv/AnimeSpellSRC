package com.example.jjkspells.garou.entity;

import net.minecraft.world.phys.Vec3;

/**
 * Shared timing and shape of Nuclear Fission. The server runs the gameplay from these numbers and the client draws the
 * visuals from the very same ones, so nothing but the impact point has to be sent.
 * All times are in ticks (20 per second). "ti" is the number of ticks since the impact.
 */
public final class GarouTimeline {
    /** The arm is pulled back, the cosmic sphere and its rings form (1.0 s). */
    public static final int WIND = 20;
    /** Longest dash (ticks) before the punch lands regardless. */
    public static final int MAX_DASH = 9;
    /** The explosion damage is applied this many ticks after the impact (at the white-out peak). */
    public static final int BLAST_DELAY = 2;
    /** The last shockwave that pushes enemies away. */
    public static final int FINAL_SHOCK = 100;
    /** Ticks the sequence lasts after the impact (7.5 s). */
    public static final int AFTER = 150;

    public static final double RADIUS = 29.0;
    public static final double SHOCK_RADIUS = 38.0;

    public static final double PUNCH_FLAT = 300.0, PUNCH_PERCENT = 12.0;
    public static final double BLAST_FLAT = 250.0, BLAST_PERCENT = 10.0;
    public static final int MANA = 180;
    public static final double COOLDOWN_SECONDS = 18.0;

    /** Small explosions that burst around the main blast. */
    public static final int SAT_COUNT = 18;

    private GarouTimeline() {
    }

    public static float c01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    public static float smooth(float v) {
        v = c01(v);
        return v * v * (3f - 2f * v);
    }

    /** Fast start, slow end. */
    public static float ease(float v) {
        v = c01(v);
        float k = 1f - v;
        return 1f - k * k * k;
    }

    public static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    /** Radius of the orange-white explosion core. */
    public static float fireRadius(float ti) {
        return 15f * ease(ti / 26f) + 3.5f * c01((ti - 26f) / 50f);
    }

    /** Radius of the white and pale-blue energy shell: reaches the full blast radius. */
    public static float shellRadius(float ti) {
        return (float) RADIUS * (1f - (float) Math.pow(1f - c01(ti / 34f), 2.2));
    }

    /** Height of the middle of the mushroom cap above the ground. */
    public static float capHeight(float ti) {
        return 5f + 41f * smooth((ti - 4f) / 86f);
    }

    public static float capRadius(float ti) {
        return 5f + 15.5f * smooth((ti - 4f) / 66f);
    }

    public static float stemRadius(float ti) {
        return 1.7f + 2.6f * smooth((ti - 4f) / 60f);
    }

    public static int satTick(int i) {
        return 5 + i * 3;
    }

    /** Horizontal offset of the i-th small explosion from the centre of the blast. */
    public static Vec3 satOffset(int i, int seed) {
        double a = i * 2.399963 + hash(i, seed) * 1.3;
        double r = 6.0 + 20.0 * Math.sqrt(hash(i, seed + 7));
        return new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r);
    }

    /** A stable pseudo random number in 0..1 for an index and a salt. */
    public static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
