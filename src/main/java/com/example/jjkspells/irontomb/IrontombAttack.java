package com.example.jjkspells.irontomb;

/** Attack ids shared by the server logic and the client renderer. Lengths are in ticks. */
public final class IrontombAttack {
    public static final int NONE = 0;
    public static final int CAST_DOWN = 1;   // Cast Down Blood and Scar    - one target, Physical
    public static final int WEAVE = 2;       // Weave Suffering and Death   - all targets, Physical (3 expanding rings)
    public static final int YIELD = 3;       // Yield Not to Tyranny        - locked target, Quantum + Energy drain
    public static final int MIRE = 4;        // Mire Not Within Caves       - locked target, Imaginary + Action Lock
    public static final int CROWN = 5;       // The Crown of Divinity Shall Shatter - all targets, massive Quantum
    public static final int PRISON = 6;      // The Spirit's Prison Shall Burn      - locked target, massive Imaginary + Action Lock
    public static final int IMMOLATE = 7;    // Immolate the Heavens, Abandon the World (cinematic)
    public static final int GENESIS = 8;     // Ashes of All Begets Genesis (Amphorean Hatred maxed, cinematic)
    public static final int STAGGER = 9;     // both satellites in Grave Wound: the boss is exposed
    public static final int RESIST = 10;     // Resist and Destroy (phase 3 prelude to Immolate)
    public static final int SINGULARITY = 11; // phase 2 special at full hatred: reality is pressed into a sphere and crushed
    public static final int ERASURE = 12;    // phase 3 special: the spiral sword that erases everything (needs the countdown to run out)

    /** The three big specials were authored at 300 / 400 / 540 ticks and are played compressed: all their timings below stay in authored ticks. */
    public static final int[] AUTH_LENGTH = {0, 64, 84, 74, 84, 140, 120, 210, 300, 90, 240, 400, 540};

    /** Authored ticks per real tick (1 for normal attacks). */
    public static double scale(int id) {
        return id <= 0 || id >= LENGTH.length ? 1.0 : AUTH_LENGTH[id] / (double) LENGTH[id];
    }

    public static final int[] LENGTH = {0, 64, 84, 74, 84, 140, 120, 210, 200, 90, 240, 220, 260};
    public static final String[] NAMES = {"none", "cast_down", "weave", "yield", "mire", "crown", "prison", "immolate", "genesis", "stagger", "resist", "singularity", "erasure"};
    public static final String[] TITLES = {"", "Cast Down Blood and Scar", "Weave Suffering and Death", "Yield Not to Tyranny",
            "Mire Not Within Caves", "The Crown of Divinity Shall Shatter", "The Spirit's Prison Shall Burn",
            "Immolate the Heavens, Abandon the World", "Ashes of All Begets Genesis", "Grave Wound", "Resist and Destroy", "Heaven Pressed Into a Single Point", "Erase the Existence of All"};

    /** Weave Suffering and Death: the three expanding spike rings (tick the ring lands, ring radius). */
    public static final int[] WEAVE_TICKS = {36, 46, 56};
    public static final float[] WEAVE_RADII = {12f, 26f, 42f};
    public static final float WEAVE_HALF_WIDTH = 5.5f;
    /** Danger-zone radii of the single-target attacks (blocks). */
    public static final float CAST_DOWN_RADIUS = 9f, YIELD_RADIUS = 7f, MIRE_RADIUS = 6f, PRISON_RADIUS = 7f;

    public static final int CAST_DOWN_HIT = 34, CAST_DOWN_FREEZE = 22;
    public static final int YIELD_HIT = 44, MIRE_HIT = 46, MIRE_LOCK = 120;
    public static final int CROWN_HIT = 84, CROWN_RADIUS = 110;
    public static final int PRISON_FREEZE = 30, PRISON_HIT = 60, PRISON_LOCK = 100;
    public static final int IMMOLATE_HIT = 150, IMMOLATE_RADIUS = 140;
    public static final int GENESIS_HIT = 232, GENESIS_RADIUS = 200;
    /** Singularity: the pull starts, the sphere is crushed (everything in the level is hurt). */
    public static final int SINGULARITY_PULL = 40, SINGULARITY_HIT = 330;
    /** Erasure: the sword lands; the kill commands run on this tick and every 2 ticks after. */
    public static final int ERASURE_HIT = 396;

    private IrontombAttack() {
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
