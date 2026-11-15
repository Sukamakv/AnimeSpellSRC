package com.example.jjkspells.enchant;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/jjk_spells-flame_slash.toml */
public final class FlameSlashConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue DAMAGE, RANGE, SPEED, LENGTH, WIDTH;
    private static final ForgeConfigSpec.IntValue BURN;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        DAMAGE = b.comment("Damage to everything the slash passes through").defineInRange("damage", 18.0, 0.0, 100000.0);
        BURN = b.comment("Burn time in seconds").defineInRange("burn_seconds", 5, 0, 600);
        RANGE = b.comment("How far the slash flies, in blocks").defineInRange("range", 18.0, 1.0, 100.0);
        SPEED = b.comment("Blocks per tick").defineInRange("speed", 1.5, 0.2, 5.0);
        LENGTH = b.comment("Length of the slash along its flight, in blocks").defineInRange("length", 6.0, 0.5, 30.0);
        WIDTH = b.comment("Width of the slash, in blocks").defineInRange("width", 2.0, 0.5, 20.0);
        SPEC = b.build();
    }

    private FlameSlashConfig() {}

    private static double d(ForgeConfigSpec.DoubleValue v, double def) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return def;
        }
    }

    public static float damage() { return (float) d(DAMAGE, 18); }
    public static double range() { return d(RANGE, 18); }
    public static double speed() { return d(SPEED, 1.5); }
    public static double length() { return d(LENGTH, 6); }
    public static double width() { return d(WIDTH, 2); }

    public static int burn() {
        try {
            return BURN.get();
        } catch (IllegalStateException e) {
            return 5;
        }
    }
}
