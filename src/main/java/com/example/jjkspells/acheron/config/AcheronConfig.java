package com.example.jjkspells.acheron.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/jjk_spells-acheron.toml (created on first launch; restart the game/world to apply changes). */
public final class AcheronConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Wilt WILT;
    public static final Octo OCTO;
    public static final Quad QUAD;
    public static final Rain RAIN;
    public static final Blade BLADE;
    public static final Styx STYX;
    public static final Ult ULT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        WILT = new Wilt(b);
        OCTO = new Octo(b);
        QUAD = new Quad(b);
        RAIN = new Rain(b);
        BLADE = new Blade(b);
        STYX = new Styx(b);
        ULT = new Ult(b);
        SPEC = b.build();
    }

    private AcheronConfig() {
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
        public final Val<Boolean> hitPlayers, monochromeShader, dreamNeedsHit;
        public final Val<Integer> cameraMode, eliteHealth, knotSeconds;
        public final Val<Double> damageMultiplier, particleDensity, bossStunSlowPercent, manaRestoreMultiplier;

        General(ForgeConfigSpec.Builder b) {
            b.push("general");
            hitPlayers = t(b, "hitPlayers", true, "Can these spells hurt other players (still obeys Iron friendly-fire checks)");
            dreamNeedsHit = t(b, "dreamNeedsHit", true, "A spell only gives its Slashed Dream when it hit at least one enemy");
            monochromeShader = t(b, "monochromeShader", true, "Use the monochrome post-process (colour drains to grey, red and violet stay) during Stygian Resurge and the Ultimate; false = a plain grey tint");
            cameraMode = i(b, "cameraMode", 0, 0, 2, "Camera safety, client side only: 0 = Full (shake, flashes, zoom, monochrome), 1 = Reduced (weaker and no full-screen flashes), 2 = Minimal (no camera effects at all). The server never depends on it");
            eliteHealth = i(b, "eliteHealth", 100, 1, 100000, "Enemies with at least this much max health count as elite for Quadrivalent Ascendance (bosses always do)");
            knotSeconds = i(b, "knotSeconds", 30, 5, 600, "How long a Crimson Knot lasts before it fades");
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by the Acheron spells");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the server sends (lower it on weak machines)");
            bossStunSlowPercent = f(b, "bossSlowPercent", 80, 0, 100, "Bosses are never frozen; they lose this percent of their speed instead");
            manaRestoreMultiplier = f(b, "manaRestoreMultiplier", 1.0, 0, 100, "Multiplier for all mana the Acheron spells give back");
            b.pop();
        }
    }

    public static final class Wilt {
        public final Val<Integer> manaCost, dreams;
        public final Val<Double> cooldownSeconds, range, radius, cutFlat, cutPercent, crossFlat, crossPercent;

        Wilt(ForgeConfigSpec.Builder b) {
            b.push("trilateral_wiltcross");
            manaCost = i(b, "manaCost", 120, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 8, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 20, 2, 60, "Maximum target range (the nearest enemy is chosen when you do not look straight at one)");
            dreams = i(b, "dreams", 1, 0, 9, "Slashed Dream gained when the spell hits something");
            radius = f(b, "radius", 7, 1, 24, "Radius of the three violet cuts and the cross");
            cutFlat = f(b, "cutFlat", 14, 0, 100000, "Flat damage of each of the three cuts");
            cutPercent = f(b, "cutPercent", 1, 0, 100, "Percent of max HP of each cut");
            crossFlat = f(b, "crossFlat", 28, 0, 100000, "Flat damage of the closing cross");
            crossPercent = f(b, "crossPercent", 2, 0, 100, "Percent of max HP of the cross (3 cuts + cross = 70 + 5% in all by default)");
            b.pop();
        }
    }

    public static final class Octo {
        public final Val<Integer> manaCost, dreams, knots;
        public final Val<Double> cooldownSeconds, range, primaryRadius, adjacentRadius, hitFlat, hitPercent, nearbyDamage;

        Octo(ForgeConfigSpec.Builder b) {
            b.push("octobolt_flash");
            manaCost = i(b, "manaCost", 220, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 16, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 35, 4, 80, "Maximum target range");
            primaryRadius = f(b, "primaryRadius", 6, 1, 24, "Radius around the target that is cut");
            adjacentRadius = f(b, "adjacentRadius", 14, 2, 40, "Radius around the target in which other enemies are struck by lightning");
            hitFlat = f(b, "hitFlat", 34, 0, 100000, "Flat damage of each of the five cuts on the target");
            hitPercent = f(b, "hitPercent", 2, 0, 100, "Percent of max HP of each cut on the target (5 cuts = 170 + 10% by default)");
            nearbyDamage = f(b, "nearbyDamage", 30, 0, 100000, "Damage of each of the three lightning waves on the others (90 in all)");
            dreams = i(b, "dreams", 1, 0, 9, "Slashed Dream gained");
            knots = i(b, "knots", 1, 0, 9, "Crimson Knots put on the target");
            b.pop();
        }
    }

    public static final class Quad {
        public final Val<Integer> manaCost, dreams;
        public final Val<Double> cooldownSeconds, range, radius, dashFlat, dashPercent, executeThresholdPercent, ordinaryFlat, ordinaryPercent, eliteFlat, elitePercent;

        Quad(ForgeConfigSpec.Builder b) {
            b.push("quadrivalent_ascendance");
            manaCost = i(b, "manaCost", 320, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 35, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 45, 4, 100, "Maximum range of the centre point (it snaps to the biggest group of enemies near your aim)");
            radius = f(b, "radius", 16, 2, 48, "Radius of the area");
            dashFlat = f(b, "dashFlat", 40, 0, 100000, "Flat damage of each of the four dashes");
            dashPercent = f(b, "dashPercent", 3, 0, 100, "Percent of max HP of each dash");
            executeThresholdPercent = f(b, "executeThresholdPercent", 20, 0, 100, "Ordinary enemies below this health are executed");
            ordinaryFlat = f(b, "ordinaryFlat", 100, 0, 100000, "Final damage on ordinary enemies above the threshold");
            ordinaryPercent = f(b, "ordinaryPercent", 6, 0, 100, "Percent of max HP of it");
            eliteFlat = f(b, "eliteFlat", 250, 0, 100000, "Final damage on elite enemies and bosses");
            elitePercent = f(b, "elitePercent", 15, 0, 100, "Percent of max HP of it");
            dreams = i(b, "dreams", 1, 0, 9, "Slashed Dream gained when the spell hits something");
            b.pop();
        }
    }

    public static final class Rain {
        public final Val<Integer> manaCost, maxKnots, dreams;
        public final Val<Double> cooldownSeconds, range, markRadius, durationSeconds, knotInterval, tickFlat, tickPercent, slowPercent;

        Rain(ForgeConfigSpec.Builder b) {
            b.push("rainleaf_hangs_oneness");
            manaCost = i(b, "manaCost", 300, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 28, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 40, 4, 100, "Maximum range of the centre point (it snaps to the biggest group of enemies near your aim)");
            markRadius = f(b, "markRadius", 20, 2, 60, "Radius in which enemies are marked");
            durationSeconds = f(b, "durationSeconds", 12, 2, 60, "How long the mark lasts");
            maxKnots = i(b, "maxKnots", 9, 1, 9, "Most Crimson Knots one enemy can carry");
            knotInterval = f(b, "knotInterval", 1.5, 0.2, 10, "Seconds between new knots on a marked enemy");
            tickFlat = f(b, "tickFlat", 3, 0, 100000, "Damage per knot per second (flat)");
            tickPercent = f(b, "tickPercent", 0.2, 0, 100, "Damage per knot per second (percent of max HP)");
            slowPercent = f(b, "slowPercent", 15, 0, 95, "Movement speed taken from marked enemies");
            dreams = i(b, "dreams", 1, 0, 9, "Slashed Dream gained when the marks land");
            b.pop();
        }
    }

    public static final class Blade {
        public final Val<Integer> manaCost, dreams, knotsPerCut;
        public final Val<Double> cooldownSeconds, range, radius, cut1, cut2, cut3, percent, knotBonusPercent;

        Blade(ForgeConfigSpec.Builder b) {
            b.push("rainblade");
            manaCost = i(b, "manaCost", 420, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 65, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 55, 4, 120, "Maximum range of the centre point (it snaps to the biggest group of enemies near your aim)");
            radius = f(b, "radius", 24, 2, 60, "Radius of the cuts");
            dreams = i(b, "dreams", 1, 0, 9, "Slashed Dream gained when the spell hits something");
            cut1 = f(b, "cut1", 150, 0, 100000, "Damage of the first cut wave");
            cut2 = f(b, "cut2", 180, 0, 100000, "Damage of the second cut wave");
            cut3 = f(b, "cut3", 220, 0, 100000, "Damage of the last cut wave");
            percent = f(b, "percent", 3, 0, 100, "Percent of max HP added to every wave");
            knotsPerCut = i(b, "knotsPerCut", 3, 0, 9, "Crimson Knots consumed from each enemy by one wave");
            knotBonusPercent = f(b, "knotBonusPercent", 15, 0, 500, "Extra damage per knot consumed");
            b.pop();
        }
    }

    public static final class Styx {
        public final Val<Integer> manaCost, dreams;
        public final Val<Double> cooldownSeconds, range, radius, shockRadius, flat, percent, shockFlat, shockPercent, knotBonusPercent;

        Styx(ForgeConfigSpec.Builder b) {
            b.push("stygian_resurge");
            manaCost = i(b, "manaCost", 600, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 100, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 70, 4, 150, "Maximum range of the centre point (it snaps to the biggest group of enemies near your aim)");
            dreams = i(b, "dreams", 1, 0, 9, "Slashed Dream gained when the spell hits something");
            radius = f(b, "radius", 35, 4, 100, "Radius of the main strike");
            shockRadius = f(b, "shockRadius", 45, 4, 120, "Radius of the shockwave");
            flat = f(b, "flat", 480, 0, 100000, "Main strike flat damage");
            percent = f(b, "percent", 30, 0, 100, "Main strike percent of max HP (halved on bosses)");
            shockFlat = f(b, "shockFlat", 200, 0, 100000, "Shockwave flat damage");
            shockPercent = f(b, "shockPercent", 10, 0, 100, "Shockwave percent of max HP");
            knotBonusPercent = f(b, "knotBonusPercent", 5, 0, 500, "Extra damage per Crimson Knot cleared");
            b.pop();
        }
    }

    public static final class Ult {
        public final Val<Integer> manaCost, dreamsNeeded;
        public final Val<Double> cooldownSeconds, range, radius, suckRadius, finalRadius, slashFlat, slashPercent, finalFlat, finalPercent, armorIgnorePercent, reduceIgnorePercent, slowPercent, outDamagePercent, focusPercent;

        Ult(ForgeConfigSpec.Builder b) {
            b.push("slashed_dream_cries_in_red");
            manaCost = i(b, "manaCost", 1200, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 240, 0, 100000, "Cooldown in seconds");
            dreamsNeeded = i(b, "dreamsNeeded", 9, 1, 9, "Slashed Dream needed (all of it is consumed)");
            range = f(b, "range", 80, 4, 160, "Maximum range of the black hole");
            radius = f(b, "radius", 45, 4, 100, "Radius of the slashes");
            suckRadius = f(b, "suckRadius", 80, 4, 160, "Everything within this radius of the black hole is pulled into it");
            finalRadius = f(b, "finalRadius", 60, 4, 130, "Radius of the last slash and the explosion");
            slashFlat = f(b, "slashFlat", 80, 0, 100000, "Flat damage of each of the 13 slashes");
            slashPercent = f(b, "slashPercent", 4, 0, 100, "Percent of max HP of each of the 13 slashes (halved on bosses)");
            finalFlat = f(b, "finalFlat", 800, 0, 100000, "Flat damage of the final slash");
            finalPercent = f(b, "finalPercent", 55, 0, 100, "Percent of max HP of the final slash (halved on bosses)");
            armorIgnorePercent = f(b, "armorIgnorePercent", 50, 0, 100, "Armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 40, 0, 100, "Magical damage reduction ignored");
            slowPercent = f(b, "slowPercent", 50, 0, 95, "Suppression: movement speed taken from enemies (halved on bosses)");
            outDamagePercent = f(b, "outDamagePercent", 25, 0, 95, "Suppression: damage enemies deal (halved on bosses)");
            focusPercent = f(b, "focusPercent", 90, 0, 100, "Damage reduction of the caster during the sequence");
            b.pop();
        }
    }
}
