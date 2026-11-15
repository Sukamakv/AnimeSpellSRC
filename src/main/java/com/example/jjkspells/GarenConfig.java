package com.example.jjkspells;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/jjk_spells-garen.toml (created on first launch; restart to apply). Settings for the four Garen spells. */
public final class GarenConfig {
    public static final ForgeConfigSpec SPEC;

    public static final class Val<T> {
        private final ForgeConfigSpec.ConfigValue<T> v;
        private final T fallback;

        Val(ForgeConfigSpec.ConfigValue<T> v, T fallback) { this.v = v; this.fallback = fallback; }

        public T get() {
            try { return v.get(); } catch (IllegalStateException e) { return fallback; }
        }
    }

    private static Val<Double> d(ForgeConfigSpec.Builder b, String n, double def, double lo, double hi, String c) {
        return new Val<>(b.comment(c + " [default: " + def + "]").defineInRange(n, def, lo, hi), def);
    }

    private static Val<Integer> i(ForgeConfigSpec.Builder b, String n, int def, int lo, int hi, String c) {
        return new Val<>(b.comment(c + " [default: " + def + "]").defineInRange(n, def, lo, hi), def);
    }

    // Decisive Strike
    public static final Val<Double> DS_DAMAGE_PERCENT, DS_LUNGE_BLOCKS, DS_STUN_SECONDS, DS_COOLDOWN, DS_EMPOWER_PERCENT;
    public static final Val<Integer> DS_MANA, DS_CAST_TICKS;
    // Judgment
    public static final Val<Double> JU_DURATION, JU_RADIUS, JU_DAMAGE_PERCENT, JU_INTERVAL_SECONDS, JU_SPEED_BONUS, JU_COOLDOWN;
    public static final Val<Integer> JU_MANA;
    // Courage
    public static final Val<Double> CO_DURATION, CO_REDUCTION, CO_EARLY_REDUCTION, CO_EARLY_SECONDS, CO_COOLDOWN, CO_KNOCKBACK_RESIST;
    public static final Val<Integer> CO_MANA;
    // Demacian Justice
    public static final Val<Double> DJ_RANGE, DJ_CAST_SECONDS, DJ_COOLDOWN, DJ_BASE_DAMAGE, DJ_DAMAGE_50, DJ_DAMAGE_20, DJ_DAMAGE_10, DJ_DAMAGE_0, DJ_EXECUTE_BELOW, DJ_EXECUTE_PERCENT;
    public static final Val<Integer> DJ_MANA;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("decisive_strike");
        DS_DAMAGE_PERCENT = d(b, "damage_percent_of_weapon", 5.0, 0, 1000, "Damage as a multiple of the caster's weapon damage (5.0 = 500%).");
        DS_LUNGE_BLOCKS = d(b, "lunge_blocks", 6.0, 0, 30, "How far the caster lunges forward.");
        DS_STUN_SECONDS = d(b, "stun_seconds", 1.0, 0, 30, "Slowness (immobilising) applied to everything hit.");
        DS_EMPOWER_PERCENT = d(b, "next_attack_bonus_percent_of_weapon", 1.0, 0, 100, "Extra damage on the caster's next melee attack within 5 seconds, as a multiple of weapon damage. 0 disables.");
        DS_COOLDOWN = d(b, "cooldown_seconds", 7.0, 0, 3600, "Cooldown.");
        DS_MANA = i(b, "mana_cost", 60, 0, 100000, "Mana cost.");
        DS_CAST_TICKS = i(b, "cast_ticks", 8, 1, 200, "Wind-up before the lunge, in ticks.");
        b.pop();
        b.push("judgment");
        JU_DURATION = d(b, "duration_seconds", 5.0, 0.5, 60, "How long the spin lasts.");
        JU_RADIUS = d(b, "radius", 4.0, 1, 30, "Radius of the spin in blocks.");
        JU_DAMAGE_PERCENT = d(b, "damage_percent_of_weapon", 1.5, 0, 1000, "Damage of every hit as a multiple of weapon damage (1.5 = 150%).");
        JU_INTERVAL_SECONDS = d(b, "hit_interval_seconds", 0.5, 0.1, 10, "Time between hits; each target is hit at most once per interval.");
        JU_SPEED_BONUS = d(b, "movement_speed_bonus", 0.12, 0, 2, "Movement speed bonus while spinning (0.12 = +12%).");
        JU_COOLDOWN = d(b, "cooldown_seconds", 10.0, 0, 3600, "Cooldown.");
        JU_MANA = i(b, "mana_cost", 90, 0, 100000, "Mana cost.");
        b.pop();
        b.push("courage");
        CO_DURATION = d(b, "duration_seconds", 7.0, 0.5, 600, "How long Courage lasts.");
        CO_REDUCTION = d(b, "damage_reduction", 0.60, 0, 1, "Fraction of incoming damage removed.");
        CO_EARLY_REDUCTION = d(b, "early_damage_reduction", 0.75, 0, 1, "Stronger reduction during the first moments (precise timing reward).");
        CO_EARLY_SECONDS = d(b, "early_window_seconds", 0.5, 0, 10, "Length of the stronger-reduction window.");
        CO_KNOCKBACK_RESIST = d(b, "knockback_resistance", 1.0, 0, 1, "Knockback resistance while Courage lasts (1.0 = immune).");
        CO_COOLDOWN = d(b, "cooldown_seconds", 18.0, 0, 3600, "Cooldown.");
        CO_MANA = i(b, "mana_cost", 70, 0, 100000, "Mana cost.");
        b.pop();
        b.push("demacian_justice");
        DJ_RANGE = d(b, "target_range", 12.0, 1, 100, "How far away a target can be.");
        DJ_CAST_SECONDS = d(b, "cast_seconds", 1.2, 0.05, 30, "Cast time.");
        DJ_BASE_DAMAGE = d(b, "base_damage", 30.0, 0, 100000, "Damage against a target above 50% health.");
        DJ_DAMAGE_50 = d(b, "damage_at_50_percent", 100.0, 0, 100000, "Total damage when the target drops below 50% health.");
        DJ_DAMAGE_20 = d(b, "damage_at_20_percent", 200.0, 0, 100000, "Total damage at 20% health (damage is interpolated between these points).");
        DJ_DAMAGE_10 = d(b, "damage_at_10_percent", 325.0, 0, 100000, "Total damage at 10% health.");
        DJ_DAMAGE_0 = d(b, "damage_cap", 375.0, 0, 100000, "Maximum damage (reached at 0% health).");
        DJ_EXECUTE_BELOW = d(b, "execute_below_health_fraction", 0.06, 0, 1, "If the target is below this fraction of its max health, the sword deals bonus damage.");
        DJ_EXECUTE_PERCENT = d(b, "execute_bonus_max_health_fraction", 0.07, 0, 1, "The bonus: this fraction of the target's max health (ignores damage caps).");
        DJ_COOLDOWN = d(b, "cooldown_seconds", 60.0, 0, 3600, "Cooldown.");
        DJ_MANA = i(b, "mana_cost", 200, 0, 100000, "Mana cost.");
        b.pop();
        SPEC = b.build();
    }

    private GarenConfig() {
    }
}
