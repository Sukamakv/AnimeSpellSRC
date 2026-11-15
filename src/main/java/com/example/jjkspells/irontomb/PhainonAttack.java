package com.example.jjkspells.irontomb;

/** Attack and event ids, lengths (ticks) and shared timing constants for Phainon. Server and client both read these. */
public final class PhainonAttack {
    public static final int NONE = 0, LANCE = 1, DASH = 2, FEATHER = 3, SUNFALL = 4, HALO = 5, SWEEP = 6, JUDGMENT = 7, STAGGER = 8;
    public static final int[] LENGTH = {0, 64, 90, 104, 124, 108, 112, 170, 80};
    public static final String[] NAMES = {"", "lance", "dash", "feather", "sunfall", "halo", "sweep", "judgment", "stagger"};
    public static final String[] TITLES = {"", "Dawn Lance", "Daybreak Rush", "Aureate Feather Rain", "Sunfall", "Ringing Halo", "Aureate Sweep", "Dawnbreaker's Judgment", "Exposed"};

    /** Events: not attacks, these are the scripted moments (waking, phase changes, death). */
    public static final int EV_NONE = 0, EV_INTRO = 1, EV_PHASE2 = 2, EV_PHASE3 = 3, EV_DEATH = 4;
    public static final int[] EV_LENGTH = {0, 96, 72, 92, 160};

    // --- authored tick marks (the client draws exactly the same timeline as the server runs)
    public static final int LANCE_FIRE = 24;
    public static final int DASH_FIRST = 14, DASH_STEP = 16, DASH_HIT_DELAY = 5;
    public static final int FEATHER_START = 20, FEATHER_STEP = 4, FEATHER_VOLLEYS = 14, FEATHER_FALL = 16;
    public static final int SUN_LOCK = 50, SUN_THROW = 64, SUN_HIT = 78;
    public static final double SUN_RADIUS = 16.0;
    public static final int HALO_START = 20, HALO_STEP = 14;
    public static final double HALO_SPEED = 1.4, HALO_WIDTH = 1.4;
    public static final int SWEEP_START = 30, SWEEP_END = 96;
    public static final double SWEEP_LENGTH = 44.0;
    public static final float SWEEP_DEG_PER_TICK = 5.6f;
    public static final int JUDG_LOCK = 70, JUDG_THROW = 100, JUDG_HIT = 112;
    public static final double JUDG_RADIUS = 28.0;

    private PhainonAttack() {
    }

    /** Where spoke s of volley v of Feather Rain lands (same maths on the server and in the renderer). c = centre on the ground. */
    public static net.minecraft.world.phys.Vec3 featherPoint(int seed, int v, int s, int spokes, net.minecraft.world.phys.Vec3 c) {
        double a = ((seed & 0x7fffffff) % 628) / 100.0 + v * 0.55 + s * Math.PI * 2 / spokes;
        double r = 2.5 + v * 1.0;
        return new net.minecraft.world.phys.Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
    }

    /** Sweep: angle (radians) of beam 0 at tick t. */
    public static double sweepAngle(int seed, int t) {
        double a0 = Math.toRadians((seed & 0x7fffffff) % 360);
        double dir = (seed & 1) == 0 ? 1.0 : -1.0;
        int k = Math.max(0, Math.min(t, SWEEP_END) - SWEEP_START);
        return a0 + dir * Math.toRadians(SWEEP_DEG_PER_TICK) * k;
    }

    public static int byName(String n) {
        for (int i = 1; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(n)) {
                return i;
            }
        }
        return -1;
    }

    /** Number of dash slashes in a phase. */
    public static int dashes(int phase) {
        return phase >= 3 ? 4 : phase == 2 ? 3 : 2;
    }

    public static int spokes(int phase) {
        return phase >= 3 ? 4 : phase == 2 ? 3 : 2;
    }

    public static int rings(int phase) {
        return phase >= 3 ? 5 : phase == 2 ? 4 : 3;
    }

    public static int beams(int phase) {
        return phase >= 3 ? 4 : phase == 2 ? 3 : 2;
    }
}
