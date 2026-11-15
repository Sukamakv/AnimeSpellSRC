package com.example.jjkspells;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * config/jjk_spells-azure.toml (created on first launch; restart to apply).
 * Settings for the Azure Colossus summon.
 */
public final class AzureConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Val<Double> BASE_HEALTH, SLASH_DAMAGE, SMASH_DAMAGE;
    public static final Val<Double> HEALTH_PER_LEVEL, DAMAGE_PER_LEVEL, DURATION_PER_LEVEL;
    public static final Val<Integer> BASE_DURATION_SECONDS;

    /** Config value that returns its default if read before the file has loaded. */
    public static final class Val<T> {
        private final ForgeConfigSpec.ConfigValue<T> v;
        private final T fallback;

        Val(ForgeConfigSpec.ConfigValue<T> v, T fallback) {
            this.v = v;
            this.fallback = fallback;
        }

        public T get() {
            try {
                return v.get();
            } catch (IllegalStateException e) {
                return fallback;
            }
        }
    }

    private static Val<Double> d(ForgeConfigSpec.Builder b, String name, double def, double lo, double hi, String comment) {
        return new Val<>(b.comment(comment + " [default: " + def + "]").defineInRange(name, def, lo, hi), def);
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("azure_colossus");
        BASE_HEALTH = d(b, "base_health", 600.0, 1.0, 1000000.0, "Colossus max health at spell level 1.");
        SLASH_DAMAGE = d(b, "slash_damage", 45.0, 0.0, 1000000.0, "Damage of the default sword slash at spell level 1.");
        SMASH_DAMAGE = d(b, "smash_damage", 70.0, 0.0, 1000000.0, "Damage of the ground smash shockwave at spell level 1.");
        BASE_DURATION_SECONDS = new Val<>(b.comment("How long the colossus stays at spell level 1, in seconds. [default: 60]")
                .defineInRange("base_duration_seconds", 60, 1, 100000), 60);
        b.push("per_level");
        HEALTH_PER_LEVEL = d(b, "health_multiplier", 1.25, 1.0, 10.0, "Health is multiplied by this for every spell level above 1 (level 5 = this ^ 4).");
        DAMAGE_PER_LEVEL = d(b, "damage_multiplier", 1.15, 1.0, 10.0, "Slash and smash damage are multiplied by this for every level above 1. Does not affect the orb barrage (5% max health).");
        DURATION_PER_LEVEL = d(b, "duration_multiplier", 1.75, 1.0, 10.0, "Duration is multiplied by this for every level above 1.");
        b.pop();
        b.pop();
        SPEC = b.build();
    }

    private AzureConfig() {
    }

    public static double healthMult(int level) { return Math.pow(HEALTH_PER_LEVEL.get(), Math.max(0, level - 1)); }
    public static double damageMult(int level) { return Math.pow(DAMAGE_PER_LEVEL.get(), Math.max(0, level - 1)); }
    public static int durationTicks(int level) {
        return (int) Math.round(BASE_DURATION_SECONDS.get() * Math.pow(DURATION_PER_LEVEL.get(), Math.max(0, level - 1)) * 20);
    }
}
