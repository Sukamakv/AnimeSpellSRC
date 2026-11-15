package com.example.jjkspells.garou.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/jjk_spells-garou.toml (created on first launch; restart the game/world to apply changes). */
public final class GarConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Gamma GAMMA;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        GAMMA = new Gamma(b);
        SPEC = b.build();
    }

    private GarConfig() {
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

    private static Val<Double> f(ForgeConfigSpec.Builder b, String n, double d, double lo, double hi, String c) {
        return new Val<>(b.comment(c + " [default: " + d + "]").defineInRange(n, d, lo, hi), d);
    }

    private static Val<Integer> i(ForgeConfigSpec.Builder b, String n, int d, int lo, int hi, String c) {
        return new Val<>(b.comment(c + " [default: " + d + "]").defineInRange(n, d, lo, hi), d);
    }

    private static Val<Boolean> t(ForgeConfigSpec.Builder b, String n, boolean d, String c) {
        return new Val<>(b.comment(c + " [default: " + d + "]").define(n, d), d);
    }

    /** Damage lines are "flat + percent of the target max HP" (percent values are plain percents: 5 = 5%). */
    public static final class General {
        public final Val<Boolean> hitPlayers, lightBlocks, cameraEffects;
        public final Val<Double> damageMultiplier, particleDensity;

        General(ForgeConfigSpec.Builder b) {
            b.push("general");
            hitPlayers = t(b, "hitPlayers", true, "Can the Garou spells hurt other players (still obeys Iron's friendly-fire checks)");
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by the Garou spells");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the spells create (lower it on weak machines)");
            lightBlocks = t(b, "lightBlocks", true, "Place temporary invisible light blocks (removed again when the spell ends) so the beam really lights up the terrain");
            cameraEffects = t(b, "cameraEffects", true, "Camera shake, field-of-view warp and the white flash on screen near the Gamma Ray Burst");
            b.pop();
        }
    }

    public static final class Gamma {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, maxLength, beamWidth, initialFlat, initialPercent, tickFlat, tickPercent,
                intervalSeconds, durationSeconds, bossPercentFactor, knockback, aimFollowDegPerTick, explosionRadius, explosionDamage;

        Gamma(ForgeConfigSpec.Builder b) {
            b.push("gamma_ray_burst");
            manaCost = i(b, "manaCost", 400, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 45, 0, 100000, "Cooldown in seconds");
            maxLength = f(b, "maxLength", 80, 8, 200, "Maximum beam length in blocks (the beam stops at the first solid block)");
            beamWidth = f(b, "beamWidth", 9, 1, 40, "Beam width in blocks (diameter of the damaging cylinder and of the visible beam)");
            initialFlat = f(b, "initialFlat", 500, 0, 1000000, "Initial beam impact: flat damage (once per target)");
            initialPercent = f(b, "initialPercent", 20, 0, 100, "Initial beam impact: percent of the target max health");
            tickFlat = f(b, "tickFlat", 100, 0, 1000000, "Continuous beam: flat damage per damage interval");
            tickPercent = f(b, "tickPercent", 4, 0, 100, "Continuous beam: percent of the target max health per damage interval");
            intervalSeconds = f(b, "intervalSeconds", 0.5, 0.05, 10, "Damage interval of the continuous beam (a target is never hit more often than this)");
            durationSeconds = f(b, "durationSeconds", 3, 0.5, 20, "Maximum duration of the beam. A target can take at most 1 initial hit + duration / interval continuous hits per cast");
            bossPercentFactor = f(b, "bossPercentFactor", 1.0, 0, 1, "Scales the percent-of-max-health parts of the damage on bosses (1 = full)");
            knockback = f(b, "knockback", 1.1, 0, 10, "How hard targets are pushed along the beam (0 = none)");
            aimFollowDegPerTick = f(b, "aimFollowDegPerTick", 3.0, 0, 360, "How fast the beam may follow where the caster looks after it fired (degrees per tick, 0 = locked in place)");
            explosionRadius = f(b, "explosionRadius", 7, 1, 40, "Radius of the secondary explosions (impact point and every struck enemy)");
            explosionDamage = f(b, "explosionDamage", 0, 0, 1000000, "Flat damage of a secondary explosion (0 = visual and knockback only, so the damage stays exactly as listed above)");
            b.pop();
        }
    }
}
