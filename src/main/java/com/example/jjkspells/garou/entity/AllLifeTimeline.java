package com.example.jjkspells.garou.entity;

import net.minecraft.world.phys.Vec3;

/**
 * Shared timing and numbers of All Life Eradication Fist. The server runs the gameplay from them and the client draws
 * from the very same ones. All times are in ticks (20 per second).
 * <pre>
 *   age 0-40          stance (camera slowly closes in, the body's starfield brightens)
 *   age 40-~50        dash to the target
 *   A (assault)       five strikes at A + STRIKE[i], each one hits HIT_DELAY ticks later
 *   N = A + NUKE      the final double fist: nuclear explosions. From here on "ti" = age - N
 *   ti GRAV..COLLAPSE the gravity sphere forms, is fed by the explosion, compresses
 *   ti COLLAPSE       white flash, gravitational damage
 *   ti SHOCK0..       the cosmic shockwave crosses the battlefield
 * </pre>
 */
public final class AllLifeTimeline {
    public static final int STANCE = 40, DASH_MAX = 14;
    public static final int[] STRIKE = {0, 6, 12, 18, 26};
    public static final int HIT_DELAY = 2;
    public static final int NUKE = STRIKE[4] + HIT_DELAY;

    /** Relative to the nuclear impact. */
    public static final int BLAST_DELAY = 2;
    public static final int GRAV = 64, GRAV_FORM = 24, GRAV_SPAN = 70;
    public static final int COLLAPSE = GRAV + GRAV_SPAN;
    public static final int SHOCK0 = COLLAPSE + 4, SHOCK_LEN = 36;
    public static final int FREE = COLLAPSE + 46;
    public static final int AFTER = COLLAPSE + 112;

    public static final double RADIUS = 40.0;
    /** Reach of one martial arts strike around the strike point. */
    public static final double MELEE = 5.0;
    public static final double GRAV_HEIGHT = 9.0;

    public static final double STRIKE_FLAT = 100.0, STRIKE_PERCENT = 3.0;
    public static final double NUKE_FLAT = 400.0, NUKE_PERCENT = 20.0;
    public static final double COLLAPSE_FLAT = 350.0, COLLAPSE_PERCENT = 15.0;
    public static final double SHOCK_FLAT = 200.0, SHOCK_PERCENT = 20.0;
    public static final int MANA = 700;
    public static final double COOLDOWN_SECONDS = 90.0;

    /** Medium nuclear explosions around the target (each with a small mushroom cloud) and small bright blasts. */
    public static final int MED_COUNT = 6, SAT_COUNT = 14;

    private AllLifeTimeline() {
    }

    public static int medTick(int i) {
        return 5 + i * 4;
    }

    public static int satTick(int i) {
        return 3 + i * 3;
    }

    public static Vec3 medOffset(int i, int seed) {
        double a = i * 1.0471976 + GarouTimeline.hash(i, seed) * 0.7;
        double r = 15.0 + 9.0 * GarouTimeline.hash(i, seed + 5);
        return new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r);
    }

    public static Vec3 satOffset(int i, int seed) {
        double a = i * 2.399963 + GarouTimeline.hash(i, seed + 11) * 1.1;
        double r = 5.0 + 30.0 * Math.sqrt(GarouTimeline.hash(i, seed + 3));
        return new Vec3(Math.cos(a) * r, 0, Math.sin(a) * r);
    }

    /** Radius of the gravitational sphere g ticks after it began to form. */
    public static float gravRadius(float g) {
        if (g <= 0f) {
            return 0f;
        }
        float form = 15f * GarouTimeline.ease(g / GRAV_FORM);
        if (g < GRAV_FORM) {
            return form;
        }
        float k = GarouTimeline.c01((g - GRAV_FORM) / (float) (GRAV_SPAN - GRAV_FORM));
        return GarouTimeline.lerp(15f, 2.2f, (float) Math.pow(k, 1.7));
    }

    // main nuclear cloud: the same shapes as Nuclear Fission, bigger
    public static final float BIG = 1.45f;

    public static float fireRadius(float ti, float s) {
        return GarouTimeline.fireRadius(ti) * s;
    }

    public static float shellRadius(float ti) {
        return (float) RADIUS * (1f - (float) Math.pow(1f - GarouTimeline.c01(ti / 40f), 2.2));
    }
}
