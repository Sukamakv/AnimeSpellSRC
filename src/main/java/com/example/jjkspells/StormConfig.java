package com.example.jjkspells;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/storm_lords_wrath-common.toml (created on first launch; restart to apply changes). */
public final class StormConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Spell SPELL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        SPELL = new Spell(b);
        SPEC = b.build();
    }

    private StormConfig() {
    }

    /** Returns the default if read before the config file has loaded. */
    public static final class Val<T> implements Supplier<T> {
        private final ForgeConfigSpec.ConfigValue<T> v;
        private final T fallback;

        Val(ForgeConfigSpec.ConfigValue<T> v, T fallback) {
            this.v = v;
            this.fallback = fallback;
        }

        @Override
        public T get() {
            try {
                return v.get();
            } catch (IllegalStateException e) {
                return fallback;
            }
        }
    }

    private static Val<Integer> i(ForgeConfigSpec.Builder b, String n, int d, int lo, int hi, String c) {
        return new Val<>(b.comment(c + " [default: " + d + "]").defineInRange(n, d, lo, hi), d);
    }

    private static Val<Double> f(ForgeConfigSpec.Builder b, String n, double d, double lo, double hi, String c) {
        return new Val<>(b.comment(c + " [default: " + d + "]").defineInRange(n, d, lo, hi), d);
    }

    private static Val<Boolean> t(ForgeConfigSpec.Builder b, String n, boolean d, String c) {
        return new Val<>(b.comment(c + " [default: " + d + "]").define(n, d), d);
    }

    public static final class Spell {
        public final Val<Integer> manaCostBase, manaCostPerLevel, castTimeTicks, hitIntervalTicks;
        public final Val<Double> cooldownSeconds, durationSeconds, damagePerSecond, damagePerSecondPerLevel;
        public final Val<Double> tornadoHeight, orbitRadius, orbitDegreesPerTick, baseRadius, topRadius, casterAuraRadius;
        public final Val<Double> liftStrength, spinStrength;
        public final Val<Boolean> useSpellPower, hitPlayers, lightningStrikes;

        Spell(ForgeConfigSpec.Builder b) {
            b.push("spell");
            manaCostBase = i(b, "manaCostBase", 200, 0, 100000, "Mana cost at level 1");
            manaCostPerLevel = i(b, "manaCostPerLevel", 60, 0, 100000, "Extra mana per level above 1");
            cooldownSeconds = f(b, "cooldownSeconds", 60.0, 0, 100000, "Cooldown");
            castTimeTicks = i(b, "castTimeTicks", 30, 1, 2000, "Cast time before the storm appears (20 ticks = 1 s)");
            b.pop();

            b.push("storm");
            durationSeconds = f(b, "durationSeconds", 12.0, 1, 600, "How long the two tornados last");
            damagePerSecond = f(b, "damagePerSecond", 80.0, 0, 100000, "Damage per second to every enemy touching the storm");
            damagePerSecondPerLevel = f(b, "damagePerSecondPerLevel", 0.0, 0, 100000, "Extra damage per second for each level above 1 (only matters if you raise the spell's max level in Iron's spell config)");
            hitIntervalTicks = i(b, "hitIntervalTicks", 5, 1, 100, "Ticks between damage ticks (5 = 4 hits per second of dps/4 each)");
            useSpellPower = t(b, "useSpellPower", false, "Multiply damage by the caster's spell power");
            hitPlayers = t(b, "hitPlayers", true, "Can hurt other players (Iron's friendly-fire / team rules still apply)");
            lightningStrikes = t(b, "lightningStrikes", true, "Harmless visual lightning bolts now and then strike inside the storm");
            b.pop();

            b.push("shape");
            tornadoHeight = f(b, "tornadoHeight", 25.0, 4, 128, "Height of each tornado in blocks");
            orbitRadius = f(b, "orbitRadius", 5.0, 0, 64, "Distance of each tornado from the caster");
            orbitDegreesPerTick = f(b, "orbitDegreesPerTick", 4.5, -45, 45, "How fast they circle the caster (360 / this = ticks per lap)");
            baseRadius = f(b, "baseRadius", 1.4, 0.2, 32, "Funnel radius at the ground");
            topRadius = f(b, "topRadius", 5.0, 0.2, 64, "Funnel radius at the top (also the damage reach there)");
            casterAuraRadius = f(b, "casterAuraRadius", 3.5, 0, 32, "Enemies this close to the caster are also hit");
            liftStrength = f(b, "liftStrength", 0.07, 0, 2, "Upward push on enemies inside a tornado");
            spinStrength = f(b, "spinStrength", 0.10, 0, 2, "Sideways whirl applied to enemies inside a tornado");
            b.pop();
        }
    }
}
