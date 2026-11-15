package com.example.jjkspells.overlord.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/**
 * config/jjk_spells-overlord.toml  (created on first launch; restart the game/server to apply changes).
 * Every spell has its own section with mana cost, cooldown, damage and (for summon spells) summon health.
 */
public final class OverlordConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General GENERAL;
    public static final SummonUndead UNDEAD;
    public static final Banshee BANSHEE;
    public static final BlackHole BLACK_HOLE;
    public static final GoalOfDeath GOAL;
    public static final FallenDown FALLEN;
    public static final RealitySlash SLASH;
    public static final BlackGoat GOAT;
    public static final GraspHeart GRASP;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        GENERAL = new General(b);
        UNDEAD = new SummonUndead(b);
        BANSHEE = new Banshee(b);
        BLACK_HOLE = new BlackHole(b);
        GOAL = new GoalOfDeath(b);
        FALLEN = new FallenDown(b);
        SLASH = new RealitySlash(b);
        GOAT = new BlackGoat(b);
        GRASP = new GraspHeart(b);
        SPEC = b.build();
    }

    private OverlordConfig() {
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

    static Val<Integer> i(ForgeConfigSpec.Builder b, String n, int d, int lo, int hi, String c) {
        return new Val<>(b.comment(c + " [default: " + d + "]").defineInRange(n, d, lo, hi), d);
    }

    static Val<Double> f(ForgeConfigSpec.Builder b, String n, double d, double lo, double hi, String c) {
        return new Val<>(b.comment(c + " [default: " + d + "]").defineInRange(n, d, lo, hi), d);
    }

    static Val<Boolean> t(ForgeConfigSpec.Builder b, String n, boolean d, String c) {
        return new Val<>(b.comment(c + " [default: " + d + "]").define(n, d), d);
    }

    /** Settings shared by every spell. */
    public abstract static class Base {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds;

        Base(ForgeConfigSpec.Builder b, int mana, double cooldown) {
            manaCost = i(b, "manaCost", mana, 0, 100000, "Mana cost at spell level 1");
            cooldownSeconds = f(b, "cooldownSeconds", cooldown, 0, 100000, "Cooldown in seconds");
        }
    }

    public static final class General {
        public final Val<Double> levelScaling;
        public final Val<Boolean> hitPlayers;

        General(ForgeConfigSpec.Builder b) {
            b.push("general");
            levelScaling = f(b, "levelScaling", 0.10, 0, 10, "Damage/summon-health bonus per spell level above 1 (0.10 = +10% per level)");
            hitPlayers = t(b, "hitPlayers", true, "Can these spells hurt other players (Iron's friendly-fire / team rules still apply)");
            b.pop();
        }
    }

    // 1 -------------------------------------------------------------------------------------------------------
    public static final class SummonUndead extends Base {
        public final Val<Integer> minMinions, maxMinions, durationSeconds;
        public final Val<Double> minionHealth, minionAttackDamage, minionSpeed, summonRadius, targetRange;

        SummonUndead(ForgeConfigSpec.Builder b) {
            super(startSection(b, "summon_undead"), 150, 25.0);
            minMinions = i(b, "minMinions", 7, 1, 100, "Minimum number of skeleton soldiers");
            maxMinions = i(b, "maxMinions", 10, 1, 100, "Maximum number of skeleton soldiers");
            durationSeconds = i(b, "durationSeconds", 40, 1, 3600, "How long the minions last");
            minionHealth = f(b, "minionHealth", 45.0, 1, 100000, "HP of EACH skeleton soldier");
            minionAttackDamage = f(b, "minionAttackDamage", 2.0, 0, 100000, "Base attack damage (the netherite sword adds ~8 on top)");
            minionSpeed = f(b, "minionSpeed", 0.30, 0.05, 2, "Movement speed attribute");
            summonRadius = f(b, "summonRadius", 4.0, 1, 32, "Radius around the caster where the minions rise");
            targetRange = f(b, "targetRange", 30.0, 1, 128, "Range of the 'attack what I'm looking at' command given on cast");
            b.pop();
        }
    }

    // 2 -------------------------------------------------------------------------------------------------------
    public static final class Banshee extends Base {
        public final Val<Double> radius, damage, weaknessSeconds, slownessSeconds, fearSeconds;
        public final Val<Integer> weaknessAmplifier, slownessAmplifier;

        Banshee(ForgeConfigSpec.Builder b) {
            super(startSection(b, "cry_of_the_banshee"), 100, 18.0);
            radius = f(b, "radius", 10.0, 1, 64, "Radius of the scream");
            damage = f(b, "damage", 20.0, 0, 100000, "Damage dealt to each enemy");
            weaknessSeconds = f(b, "weaknessSeconds", 4.0, 0, 600, "Weakness duration");
            slownessSeconds = f(b, "slownessSeconds", 4.0, 0, 600, "Slowness duration");
            fearSeconds = f(b, "fearSeconds", 1.0, 0, 600, "Fear duration: frightened enemies cannot attack / target / cast for this long");
            weaknessAmplifier = i(b, "weaknessAmplifier", 1, 0, 10, "Weakness amplifier (0 = I)");
            slownessAmplifier = i(b, "slownessAmplifier", 1, 0, 10, "Slowness amplifier (0 = I)");
            b.pop();
        }
    }

    // 3 -------------------------------------------------------------------------------------------------------
    public static final class BlackHole extends Base {
        public final Val<Double> range, pullRadius, pullSeconds, damage, damageRadius, slownessSeconds, pullStrength, warningSeconds;
        public final Val<Integer> slownessAmplifier;

        BlackHole(ForgeConfigSpec.Builder b) {
            super(startSection(b, "black_hole"), 220, 30.0);
            range = f(b, "range", 10.0, 1, 128, "Max distance the black hole can be placed");
            pullRadius = f(b, "pullRadius", 10.0, 1, 64, "Enemies inside this radius are dragged toward the centre");
            pullSeconds = f(b, "pullSeconds", 2.0, 0.2, 60, "How long the pull lasts");
            warningSeconds = f(b, "warningSeconds", 0.6, 0, 10, "Extra time after the pull where the sphere shakes before it collapses (your escape window)");
            pullStrength = f(b, "pullStrength", 0.16, 0.01, 2, "Pull speed per tick at the edge (stronger closer to the centre)");
            damage = f(b, "damage", 150.0, 0, 100000, "Collapse damage");
            damageRadius = f(b, "damageRadius", 6.0, 0.5, 64, "Radius of the collapse explosion that deals the damage");
            slownessSeconds = f(b, "slownessSeconds", 3.0, 0, 600, "Slowness applied to everything inside the pull radius when it collapses");
            slownessAmplifier = i(b, "slownessAmplifier", 3, 0, 10, "Slowness amplifier (3 = IV)");
            b.pop();
        }
    }

    // 4 -------------------------------------------------------------------------------------------------------
    public static final class GoalOfDeath extends Base {
        public final Val<Double> castTimeSeconds, chargeSeconds, radius, maxHealthLoss, curseSeconds, instantDeathHealthThreshold, instantDeathChance, ascendanceSeconds;

        GoalOfDeath(ForgeConfigSpec.Builder b) {
            super(startSection(b, "goal_of_all_life_is_death"), 400, 90.0);
            castTimeSeconds = f(b, "castTimeSeconds", 1.5, 0.25, 5, "Normal Iron's cast time (1-2 s) BEFORE the stand-still charge starts");
            chargeSeconds = f(b, "chargeSeconds", 7.0, 0.5, 60, "Stand-still charging time");
            radius = f(b, "radius", 18.0, 2, 64, "Radius of the death wave");
            maxHealthLoss = f(b, "maxHealthLoss", 0.5, 0.01, 0.99, "Fraction of max HP every enemy loses (0.5 = 50%)");
            curseSeconds = f(b, "curseSeconds", 80.0, 1, 3600, "How long the max-HP loss lasts");
            instantDeathHealthThreshold = f(b, "instantDeathHealthThreshold", 0.15, 0, 1, "Enemies at or below this HP fraction can be killed outright");
            instantDeathChance = f(b, "instantDeathChance", 0.5, 0, 1, "Chance of instant death for those enemies (failure: they only get the max-HP curse)");
            ascendanceSeconds = f(b, "ascendanceSeconds", 60.0, 0, 3600, "How long the caster's 'Death Ascendance' lasts (lets Grasp Heart kill boss-type creatures)");
            b.pop();
        }
    }

    // 5 -------------------------------------------------------------------------------------------------------
    public static final class FallenDown extends Base {
        public final Val<Double> castTimeSeconds, chargeSeconds, range, radius, damageFractionOfMaxHealth;

        FallenDown(ForgeConfigSpec.Builder b) {
            super(startSection(b, "fallen_down"), 350, 55.0);
            castTimeSeconds = f(b, "castTimeSeconds", 1.5, 0.25, 5, "Normal Iron's cast time (1-2 s) BEFORE the stand-still charge starts");
            chargeSeconds = f(b, "chargeSeconds", 6.0, 0.5, 60, "Stand-still charging time");
            range = f(b, "range", 30.0, 1, 128, "How far away the target point can be");
            radius = f(b, "radius", 25.0, 1, 64, "Radius of the holy explosion");
            damageFractionOfMaxHealth = f(b, "damageFractionOfMaxHealth", 0.40, 0, 1, "Damage as a fraction of each enemy's max HP (ignores damage caps)");
            b.pop();
        }
    }

    // 6 -------------------------------------------------------------------------------------------------------
    public static final class RealitySlash extends Base {
        public final Val<Double> range, width, baseDamage, maxHealthFraction, armorIgnore;
        public final Val<Integer> windupTicks;
        public final Val<Boolean> ignoreBlocks;

        RealitySlash(ForgeConfigSpec.Builder b) {
            super(startSection(b, "reality_slash"), 90, 20.0);
            range = f(b, "range", 25.0, 1, 128, "Length of the slash");
            width = f(b, "width", 1.5, 0.1, 16, "Width of the slash");
            baseDamage = f(b, "baseDamage", 30.0, 0, 100000, "Flat damage");
            maxHealthFraction = f(b, "maxHealthFraction", 0.10, 0, 1, "Extra damage as a fraction of the target's max HP (ignores damage caps)");
            armorIgnore = f(b, "armorIgnore", 0.30, 0, 1, "Fraction of the target's armour damage-reduction that is ignored");
            windupTicks = i(b, "windupTicks", 4, 0, 40, "Ticks the targeting line is shown before the slash lands");
            ignoreBlocks = t(b, "ignoreBlocks", true, "The slash cuts through walls");
            b.pop();
        }
    }

    // 7 -------------------------------------------------------------------------------------------------------
    public static final class BlackGoat extends Base {
        public final Val<Double> castTimeSeconds, chargeSeconds, range, goatHealth, goatAttackDamage, goatKnockback, goatSpeed;
        public final Val<Double> offspringHealth, offspringAttackDamage, offspringSpeed, minDurationSeconds, maxDurationSeconds, sweepRadius;
        public final Val<Integer> minOffspring, maxOffspring;

        BlackGoat(ForgeConfigSpec.Builder b) {
            super(startSection(b, "black_goat_sacrifice"), 450, 220.0);
            castTimeSeconds = f(b, "castTimeSeconds", 1.5, 0.25, 5, "Normal Iron's cast time (1-2 s) BEFORE the stand-still ritual starts");
            chargeSeconds = f(b, "chargeSeconds", 4.0, 0.5, 60, "Stand-still ritual time");
            range = f(b, "range", 20.0, 1, 128, "How far away the ritual circle can be placed");
            minDurationSeconds = f(b, "minDurationSeconds", 60.0, 1, 3600, "Minimum time the summons last");
            maxDurationSeconds = f(b, "maxDurationSeconds", 70.0, 1, 3600, "Maximum time the summons last");
            goatHealth = f(b, "goatHealth", 400.0, 1, 1000000, "HP of the big Black Goat");
            goatAttackDamage = f(b, "goatAttackDamage", 24.0, 0, 100000, "Melee damage of the big Black Goat");
            goatKnockback = f(b, "goatKnockback", 2.5, 0, 20, "Knockback of the big Black Goat");
            goatSpeed = f(b, "goatSpeed", 0.26, 0.05, 2, "Movement speed attribute of the Black Goat");
            minOffspring = i(b, "minOffspring", 3, 0, 50, "Minimum number of Young");
            maxOffspring = i(b, "maxOffspring", 5, 0, 50, "Maximum number of Young");
            offspringHealth = f(b, "offspringHealth", 40.0, 1, 1000000, "HP of EACH Young of the Black Goat");
            offspringAttackDamage = f(b, "offspringAttackDamage", 6.0, 0, 100000, "Damage of the Young (they hit everything around them)");
            offspringSpeed = f(b, "offspringSpeed", 0.40, 0.05, 2, "Movement speed attribute of the Young");
            sweepRadius = f(b, "offspringSweepRadius", 1.8, 0, 16, "The Young's attacks hit every enemy within this radius of the target");
            b.pop();
        }
    }

    // 8 -------------------------------------------------------------------------------------------------------
    public static final class GraspHeart extends Base {
        public final Val<Double> range, instantDeathChance, instantDeathHealthThreshold, failDamageFractionOfMaxHealth, bossFailDamageFraction, stunSeconds;

        GraspHeart(ForgeConfigSpec.Builder b) {
            super(startSection(b, "grasp_heart"), 160, 40.0);
            range = f(b, "range", 25.0, 1, 128, "Targeting range");
            instantDeathChance = f(b, "instantDeathChance", 0.5, 0, 1, "Chance to crush the heart outright");
            instantDeathHealthThreshold = f(b, "instantDeathHealthThreshold", 0.10, 0, 1, "The heart can only be crushed outright if the target is BELOW this fraction of its max health (0.10 = 10%)");
            failDamageFractionOfMaxHealth = f(b, "failDamageFractionOfMaxHealth", 0.30, 0, 1, "On failure: damage as a fraction of max HP (ignores damage caps)");
            bossFailDamageFraction = f(b, "bossFailDamageFraction", 0.10, 0, 1, "Same, but against boss-type creatures (which cannot be killed outright unless the caster has Death Ascendance)");
            stunSeconds = f(b, "stunSeconds", 2.0, 0, 60, "Stun (heavy slowness + weakness) applied on failure");
            b.pop();
        }
    }

    private static ForgeConfigSpec.Builder startSection(ForgeConfigSpec.Builder b, String name) {
        b.push(name);
        return b;
    }
}
