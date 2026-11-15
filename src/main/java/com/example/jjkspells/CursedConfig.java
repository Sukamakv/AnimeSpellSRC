package com.example.jjkspells;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Editable settings for both spells.
 * <p>
 * File location: <code>config/jjk_spells-common.toml</code> (created on first launch).
 * Changes take effect after restarting the game / server (or re-entering the world).
 * <p>
 * Iron's own per-spell JSON config (enabled, school, min rarity, max level, spell power multiplier, ...)
 * still works for these spells as well - this file only owns the values listed here.
 */
public final class CursedConfig {
    public static LapseBlue BLUE;
    public static ReversalRed RED;

    /** Called by JJKConfig so these settings live in the same file: config/jjk_spells-common.toml */
    static void define(ForgeConfigSpec.Builder builder) {
        BLUE = new LapseBlue(builder);
        RED = new ReversalRed(builder);
    }

    private CursedConfig() {
    }

    /**
     * Wraps a config value so reading it before the config file has loaded returns the default instead of throwing.
     */
    public static final class Val<T> {
        private final ForgeConfigSpec.ConfigValue<T> value;
        private final T fallback;

        private Val(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
            this.value = value;
            this.fallback = fallback;
        }

        public T get() {
            try {
                return value.get();
            } catch (IllegalStateException e) {
                return fallback;
            }
        }
    }

    private static Val<Integer> intVal(ForgeConfigSpec.Builder b, String name, int def, int min, int max, String comment) {
        return new Val<>(b.comment(comment + " [default: " + def + "]").defineInRange(name, def, min, max), def);
    }

    private static Val<Double> dblVal(ForgeConfigSpec.Builder b, String name, double def, double min, double max, String comment) {
        return new Val<>(b.comment(comment + " [default: " + def + "]").defineInRange(name, def, min, max), def);
    }

    private static Val<Boolean> boolVal(ForgeConfigSpec.Builder b, String name, boolean def, String comment) {
        return new Val<>(b.comment(comment + " [default: " + def + "]").define(name, def), def);
    }

    // ------------------------------------------------------------------------------------------------------------
    public static final class LapseBlue {
        public final Val<Integer> manaCostBase;
        public final Val<Integer> manaCostPerLevel;
        public final Val<Double> cooldownSeconds;
        public final Val<Integer> castTimeTicks;

        public final Val<Double> damageBase;
        public final Val<Double> damagePerLevel;
        public final Val<Integer> damageIntervalTicks;
        public final Val<Boolean> useSpellPower;

        public final Val<Double> orbRadiusBase;
        public final Val<Double> orbRadiusPerLevel;
        public final Val<Double> pullRangeBase;
        public final Val<Double> pullRangePerLevel;
        public final Val<Double> pullStrength;
        public final Val<Double> maxPullSpeed;

        public final Val<Double> durationSecondsBase;
        public final Val<Double> durationSecondsPerLevel;
        public final Val<Double> castDistance;

        public final Val<Boolean> pullPlayers;
        public final Val<Boolean> pullItems;

        private LapseBlue(ForgeConfigSpec.Builder b) {
            b.comment(
                    "Cursed Technique Lapse: Blue",
                    "Pulls entities toward a point in front of the caster and deals small damage over time.",
                    "'Level' values grow with spell level:  value = base + perLevel * (level - 1)"
            ).push("lapse_blue");

            b.push("cost");
            manaCostBase = intVal(b, "manaCostBase", 60, 0, 100000, "Mana cost at level 1");
            manaCostPerLevel = intVal(b, "manaCostPerLevel", 15, 0, 100000, "Extra mana per additional level");
            cooldownSeconds = dblVal(b, "cooldownSeconds", 18.0, 0.0, 3600.0, "Cooldown in seconds");
            castTimeTicks = intVal(b, "castTimeTicks", 25, 1, 1200, "Cast (charge) time in ticks (20 ticks = 1 second)");
            b.pop();

            b.push("damage");
            damageBase = dblVal(b, "damageBase", 2.0, 0.0, 10000.0, "Damage dealt per hit at level 1 (2.0 = 1 heart)");
            damagePerLevel = dblVal(b, "damagePerLevel", 1.0, 0.0, 10000.0, "Extra damage per hit per additional level");
            damageIntervalTicks = intVal(b, "damageIntervalTicks", 10, 1, 200, "Ticks between damage hits to entities inside the orb");
            useSpellPower = boolVal(b, "useSpellPower", true, "Multiply damage by the caster's spell power (attributes + Iron's power multiplier)");
            b.pop();

            b.push("size_and_pull");
            orbRadiusBase = dblVal(b, "orbRadiusBase", 0.9, 0.1, 32.0, "Visual/damage radius of the orb in blocks at level 1");
            orbRadiusPerLevel = dblVal(b, "orbRadiusPerLevel", 0.3, 0.0, 32.0, "Extra orb radius per additional level");
            pullRangeBase = dblVal(b, "pullRangeBase", 6.0, 1.0, 128.0, "Radius in blocks within which entities are sucked in, at level 1");
            pullRangePerLevel = dblVal(b, "pullRangePerLevel", 2.5, 0.0, 128.0, "Extra pull radius per additional level");
            pullStrength = dblVal(b, "pullStrength", 0.14, 0.0, 5.0, "Pull acceleration per tick at the orb's edge-to-center gradient (higher = stronger suction)");
            maxPullSpeed = dblVal(b, "maxPullSpeed", 0.9, 0.05, 10.0, "Maximum speed (blocks/tick) an entity can be pulled at");
            b.pop();

            b.push("behaviour");
            durationSecondsBase = dblVal(b, "durationSecondsBase", 4.0, 0.5, 600.0, "How long the orb lasts at level 1 (seconds)");
            durationSecondsPerLevel = dblVal(b, "durationSecondsPerLevel", 0.5, 0.0, 600.0, "Extra duration per additional level (seconds)");
            castDistance = dblVal(b, "castDistance", 5.0, 1.0, 64.0, "How far in front of the caster the orb appears (blocks)");
            pullPlayers = boolVal(b, "pullPlayers", true, "Whether other players can be pulled");
            pullItems = boolVal(b, "pullItems", true, "Whether dropped items are pulled");
            b.pop();

            b.pop();
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    public static final class ReversalRed {
        public final Val<Integer> manaCostBase;
        public final Val<Integer> manaCostPerLevel;
        public final Val<Double> cooldownSeconds;
        public final Val<Integer> castTimeTicks;

        public final Val<Double> damageBase;
        public final Val<Double> damagePerLevel;
        public final Val<Boolean> useSpellPower;

        public final Val<Double> explosionRadiusBase;
        public final Val<Double> explosionRadiusPerLevel;
        public final Val<Double> knockbackBase;
        public final Val<Double> knockbackPerLevel;
        public final Val<Double> knockbackUpward;

        public final Val<Double> blastRangeMultiplier;
        public final Val<Double> coneAngleDegrees;
        public final Val<Double> recoil;
        public final Val<Double> edgeDamageFraction;

        private ReversalRed(ForgeConfigSpec.Builder b) {
            b.comment(
                    "Cursed Technique Reversal: Red",
                    "A shotgun-style blast of repulsion from the caster: damages and throws back everything in a cone.",
                    "'Level' values grow with spell level:  value = base + perLevel * (level - 1)"
            ).push("reversal_red");

            b.push("cost");
            manaCostBase = intVal(b, "manaCostBase", 50, 0, 100000, "Mana cost at level 1");
            manaCostPerLevel = intVal(b, "manaCostPerLevel", 15, 0, 100000, "Extra mana per additional level");
            cooldownSeconds = dblVal(b, "cooldownSeconds", 12.0, 0.0, 3600.0, "Cooldown in seconds");
            castTimeTicks = intVal(b, "castTimeTicks", 20, 1, 1200, "Cast (charge) time in ticks (20 ticks = 1 second)");
            b.pop();

            b.push("damage");
            damageBase = dblVal(b, "damageBase", 12.0, 0.0, 10000.0, "Explosion damage at the centre at level 1 (2.0 = 1 heart)");
            damagePerLevel = dblVal(b, "damagePerLevel", 6.0, 0.0, 10000.0, "Extra damage per additional level");
            edgeDamageFraction = dblVal(b, "edgeDamageFraction", 0.3, 0.0, 1.0, "Fraction of damage dealt at the very edge of the explosion (1.0 = no falloff)");
            useSpellPower = boolVal(b, "useSpellPower", true, "Multiply damage by the caster's spell power (attributes + Iron's power multiplier)");
            b.pop();

            b.push("explosion");
            explosionRadiusBase = dblVal(b, "explosionRadiusBase", 3.0, 0.5, 64.0, "Explosion radius in blocks at level 1");
            explosionRadiusPerLevel = dblVal(b, "explosionRadiusPerLevel", 1.5, 0.0, 64.0, "Extra radius per additional level");
            knockbackBase = dblVal(b, "knockbackBase", 2.0, 0.0, 20.0, "Repulsion velocity (blocks/tick) at the centre at level 1");
            knockbackPerLevel = dblVal(b, "knockbackPerLevel", 0.5, 0.0, 20.0, "Extra repulsion per additional level");
            knockbackUpward = dblVal(b, "knockbackUpward", 0.6, 0.0, 10.0, "Additional upward velocity applied with the repulsion");
            b.pop();

            b.push("blast");
            blastRangeMultiplier = dblVal(b, "blastRangeMultiplier", 3.0, 1.0, 10.0, "Blast length in blocks = explosion radius * this");
            coneAngleDegrees = dblVal(b, "coneAngleDegrees", 35.0, 5.0, 80.0, "Half-angle of the blast cone in degrees (wider = more spread)");
            recoil = dblVal(b, "recoil", 0.35, 0.0, 3.0, "How hard the blast pushes the caster backwards");
            b.pop();

            b.pop();
        }
    }
}
