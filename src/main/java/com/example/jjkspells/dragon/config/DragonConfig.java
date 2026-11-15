package com.example.jjkspells.dragon.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/true_dragon-common.toml (created on first launch; restart to apply changes). */
public final class DragonConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Spell SPELL;
    public static final Buff BUFF;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        SPELL = new Spell(b);
        BUFF = new Buff(b);
        SPEC = b.build();
    }

    private DragonConfig() {
    }

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
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, riseHeight, riseSeconds, beamSeconds, circleRadius;

        Spell(ForgeConfigSpec.Builder b) {
            b.push("spell");
            manaCost = i(b, "manaCost", 300, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 360.0, 0, 100000, "Cooldown in seconds");
            riseHeight = f(b, "riseHeight", 12.0, 1, 128, "How high the caster flies");
            riseSeconds = f(b, "riseSeconds", 3.0, 0.5, 30, "Seconds until the light beam fires (the rise takes this long)");
            beamSeconds = f(b, "beamSeconds", 3.0, 0.5, 30, "How long the beam of light lasts (the caster stays frozen until it ends)");
            circleRadius = f(b, "circleRadius", 5.0, 1, 64, "Radius of the ground circle and the beam (5 = a 10x10 circle)");
            b.pop();
        }
    }

    public static final class Buff {
        public final Val<Double> durationSeconds, spellPowerBonus, attackDamageBonus, meleeMaxHealthPercent;
        public final Val<Boolean> creativeFlight, immuneToNegativeEffects;

        Buff(ForgeConfigSpec.Builder b) {
            b.push("true_dragon_authority");
            durationSeconds = f(b, "durationSeconds", 300.0, 1, 100000, "Buff duration (300 = 5 minutes)");
            spellPowerBonus = f(b, "spellPowerBonus", 1.5, 0, 100, "Spell power bonus (1.5 = +150%)");
            attackDamageBonus = f(b, "attackDamageBonus", 2.0, 0, 100, "Attack damage bonus (2.0 = +200%)");
            meleeMaxHealthPercent = f(b, "meleeMaxHealthPercent", 0.05, 0, 100, "Extra melee damage as a PERCENT of the target's max HP (0.05 = 0.05%)");
            creativeFlight = t(b, "creativeFlight", true, "Grant creative flight while the buff lasts");
            immuneToNegativeEffects = t(b, "immuneToNegativeEffects", true, "Immune to harmful potion effects, fire and freezing");
            b.pop();
        }
    }
}
