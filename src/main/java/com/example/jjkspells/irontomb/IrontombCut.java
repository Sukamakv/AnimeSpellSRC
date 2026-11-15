package com.example.jjkspells.irontomb;

/** Cutscene ids. INTRO / PHASE2 / PHASE3 / DEATH are stand-alone, IMMOLATE / GENESIS run alongside the attack of the same name. */
public final class IrontombCut {
    public static final int NONE = 0, INTRO = 1, PHASE2 = 2, PHASE3 = 3, DEATH = 4, IMMOLATE = 5, GENESIS = 6, SINGULARITY = 7, ERASURE = 8;
    /** Authored lengths (what the camera paths and effects are timed in); LENGTH is how long they really last. */
    public static final int[] AUTH_LENGTH = {0, 170, 120, 140, 160, 210, 300, 400, 540};

    public static double scale(int id) {
        return id <= 0 || id >= LENGTH.length ? 1.0 : AUTH_LENGTH[id] / (double) LENGTH[id];
    }

    public static final int[] LENGTH = {0, 170, 120, 140, 160, 210, 200, 220, 260};
    public static final String[] NAMES = {"none", "intro", "phase2", "phase3", "death", "immolate", "genesis", "singularity", "erasure"};

    private IrontombCut() {
    }

    /** True for the cutscenes that take the boss out of the fight loop (no attacks run during them). */
    public static boolean standalone(int id) {
        return id >= INTRO && id <= DEATH;
    }

    public static int byName(String n) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(n)) {
                return i;
            }
        }
        return -1;
    }
}
