package com.example.jjkspells.malenia.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/jjk_spells-malenia.toml (created on first launch; restart the game/world to apply changes). */
public final class MelConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Waterfowl WATERFOWL;
    public static final Phantom PHANTOM;
    public static final Bloom BLOOM;
    public static final Blade BLADE;
    public static final Goddess GODDESS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        WATERFOWL = new Waterfowl(b);
        PHANTOM = new Phantom(b);
        BLOOM = new Bloom(b);
        BLADE = new Blade(b);
        GODDESS = new Goddess(b);
        SPEC = b.build();
    }

    private MelConfig() {
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
        public final Val<Boolean> hitPlayers;
        public final Val<Double> damageMultiplier, particleDensity, bossStunSlowPercent, lifestealMultiplier;

        General(ForgeConfigSpec.Builder b) {
            b.push("general");
            hitPlayers = t(b, "hitPlayers", true, "Can these spells hurt other players (still obeys Iron friendly-fire checks)");
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by the Malenia spells");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the server sends (lower it on weak machines)");
            bossStunSlowPercent = f(b, "bossSlowPercent", 80, 0, 100, "Bosses are never frozen; they lose this percent of their speed instead");
            lifestealMultiplier = f(b, "lifestealMultiplier", 1.0, 0, 100, "Multiplier for all health stolen by the Malenia spells");
            b.pop();
        }
    }

    public static final class Waterfowl {
        public final Val<Integer> manaCost, danceSlashes;
        public final Val<Double> cooldownSeconds, range, leapBlocks, slashFlat, slashReach, areaRadius, crescentRadius, crescentFlat, crescentPercent, crescentKnock, launchBlocks, lifestealSlash, lifestealCrescent, lifestealCap, trackRange, danceBonusPercent, danceSpeedPercent, danceSpeedSeconds, danceReductionPercent, danceReductionSeconds, flowReductionPercent;

        Waterfowl(ForgeConfigSpec.Builder b) {
            b.push("waterfowl_dance");
            manaCost = i(b, "manaCost", 90, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 25, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 16, 4, 64, "Maximum targeting range");
            leapBlocks = f(b, "leapBlocks", 4, 0, 16, "Height of the opening leap");
            slashFlat = f(b, "slashFlat", 12, 0, 100000, "Damage of one slash");
            slashReach = f(b, "slashReach", 3.2, 0.5, 12, "How far around the dancing caster a slash cuts");
            areaRadius = f(b, "areaRadius", 5, 1, 24, "Radius of the second flurry");
            crescentRadius = f(b, "crescentRadius", 7, 1, 32, "Radius of the final crescent");
            crescentFlat = f(b, "crescentFlat", 65, 0, 100000, "Crescent flat damage");
            crescentPercent = f(b, "crescentPercent", 6, 0, 100, "Crescent max-HP percent");
            crescentKnock = f(b, "crescentKnock", 5, 0, 32, "Crescent knockback in blocks");
            launchBlocks = f(b, "launchBlocks", 3, 0, 16, "How high the third flurry launches enemies");
            lifestealSlash = f(b, "lifestealSlash", 2, 0, 100000, "Health stolen per slash that connects");
            lifestealCrescent = f(b, "lifestealCrescent", 5, 0, 100000, "Health stolen per enemy hit by the crescent");
            lifestealCap = f(b, "lifestealCap", 50, 0, 100000, "Maximum health stolen per cast");
            trackRange = f(b, "trackRange", 12, 1, 64, "Relentless Pursuit: how far the dance follows a new target");
            danceSlashes = i(b, "danceSlashes", 15, 1, 64, "Dance of Death: slashes that must connect");
            danceBonusPercent = f(b, "danceBonusPercent", 30, 0, 1000, "Crescent damage bonus");
            danceSpeedPercent = f(b, "danceSpeedPercent", 20, 0, 500, "Caster movement bonus");
            danceSpeedSeconds = f(b, "danceSpeedSeconds", 4, 0, 600, "duration");
            danceReductionPercent = f(b, "danceReductionPercent", 20, 0, 100, "Caster damage reduction");
            danceReductionSeconds = f(b, "danceReductionSeconds", 3, 0, 600, "duration");
            flowReductionPercent = f(b, "flowReductionPercent", 50, 0, 100, "Flowing Blade: damage reduction during the flurries");
            b.pop();
        }
    }

    public static final class Phantom {
        public final Val<Integer> manaCost, stackThreshold;
        public final Val<Double> cooldownSeconds, range, radius, riseBlocks, phantomFlat, cloudRadius, cloudSeconds, cloudDps, stackSeconds, rotSeconds, rotDps, diveFlat, divePercent, perfectBonusPercent, perfectHeal, perfectSlowPercent, perfectSlowSeconds, projReducePercent;

        Phantom(ForgeConfigSpec.Builder b) {
            b.push("phantom_blossom");
            manaCost = i(b, "manaCost", 100, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 30, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 25, 4, 64, "Maximum targeting range and phantom reach");
            radius = f(b, "radius", 12, 2, 48, "Attack radius around the target");
            riseBlocks = f(b, "riseBlocks", 6, 0, 16, "How high the caster rises");
            phantomFlat = f(b, "phantomFlat", 35, 0, 100000, "Damage of one phantom attack");
            cloudRadius = f(b, "cloudRadius", 2.5, 0.5, 12, "Scarlet Rot cloud radius at phantom impacts");
            cloudSeconds = f(b, "cloudSeconds", 3, 0, 60, "Cloud duration");
            cloudDps = f(b, "cloudDps", 6, 0, 100000, "Cloud rot damage per second");
            stackSeconds = f(b, "stackSeconds", 6, 0.5, 600, "Scarlet Wounds: duration of one stack");
            stackThreshold = i(b, "stackThreshold", 4, 1, 32, "Stacks that burst into Scarlet Rot");
            rotSeconds = f(b, "rotSeconds", 6, 0, 600, "Scarlet Rot duration");
            rotDps = f(b, "rotDps", 12, 0, 100000, "Scarlet Rot damage per second");
            diveFlat = f(b, "diveFlat", 90, 0, 100000, "Final dive flat damage");
            divePercent = f(b, "divePercent", 8, 0, 100, "Final dive max-HP percent");
            perfectBonusPercent = f(b, "perfectBonusPercent", 25, 0, 1000, "Perfect Illusion: final thrust bonus");
            perfectHeal = f(b, "perfectHeal", 20, 0, 100000, "Perfect Illusion: caster heal");
            perfectSlowPercent = f(b, "perfectSlowPercent", 30, 0, 95, "Perfect Illusion: slow on the primary target");
            perfectSlowSeconds = f(b, "perfectSlowSeconds", 3, 0, 600, "duration");
            projReducePercent = f(b, "projReducePercent", 40, 0, 100, "Aerial Grace: projectile damage reduction");
            b.pop();
        }
    }

    public static final class Bloom {
        public final Val<Integer> manaCost, catastropheCount;
        public final Val<Double> cooldownSeconds, range, riseBlocks, impactRadius, impactFlat, impactPercent, bloomRadius, bloomFlat, bloomPercent, rotSeconds, rotDps, rotHealCutPercent, gardenSeconds, gardenDps, gardenSlowPercent, gardenExposureSeconds, regenPerEnemy, regenMax, catastropheBonusPercent, catastropheGardenRadius, catastropheRotSeconds;

        Bloom(ForgeConfigSpec.Builder b) {
            b.push("scarlet_bloom");
            manaCost = i(b, "manaCost", 110, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 38, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 22, 4, 64, "Maximum targeting range");
            riseBlocks = f(b, "riseBlocks", 8, 0, 24, "How high the caster ascends");
            impactRadius = f(b, "impactRadius", 9, 1, 32, "Radius of the dive impact");
            impactFlat = f(b, "impactFlat", 110, 0, 100000, "Impact flat damage");
            impactPercent = f(b, "impactPercent", 10, 0, 100, "Impact max-HP percent");
            bloomRadius = f(b, "bloomRadius", 12, 1, 40, "Radius of the flower and the garden");
            bloomFlat = f(b, "bloomFlat", 60, 0, 100000, "Second explosion flat damage");
            bloomPercent = f(b, "bloomPercent", 5, 0, 100, "Second explosion percent");
            rotSeconds = f(b, "rotSeconds", 8, 0, 600, "Scarlet Rot duration");
            rotDps = f(b, "rotDps", 15, 0, 100000, "Scarlet Rot damage per second");
            rotHealCutPercent = f(b, "rotHealCutPercent", 30, 0, 100, "Healing reduction while rotting");
            gardenSeconds = f(b, "gardenSeconds", 10, 0, 600, "Rotting Garden duration");
            gardenDps = f(b, "gardenDps", 8, 0, 100000, "Garden rot damage per second");
            gardenSlowPercent = f(b, "gardenSlowPercent", 20, 0, 95, "Garden slow");
            gardenExposureSeconds = f(b, "gardenExposureSeconds", 3, 0.5, 60, "Seconds in the garden before the full Scarlet Rot sets in");
            regenPerEnemy = f(b, "regenPerEnemy", 5, 0, 100000, "Aeonian Regeneration: heal per enemy hit by the impact");
            regenMax = f(b, "regenMax", 30, 0, 100000, "Aeonian Regeneration: maximum heal");
            catastropheCount = i(b, "catastropheCount", 5, 1, 64, "Blooming Catastrophe: enemies the impact must hit");
            catastropheBonusPercent = f(b, "catastropheBonusPercent", 25, 0, 1000, "Second explosion bonus");
            catastropheGardenRadius = f(b, "catastropheGardenRadius", 15, 1, 48, "Garden radius with it");
            catastropheRotSeconds = f(b, "catastropheRotSeconds", 3, 0, 600, "Extra rot seconds");
            b.pop();
        }
    }

    public static final class Blade {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, dashFlat, slashFlat, thrustFlat, thrustPercent, thrustKnock, executeFlat, executePercent, lifestealDash, lifestealSlash, lifestealThrust, lifestealExecute, lifestealCap, perfectBonusPercent, perfectSpeedPercent, perfectSpeedSeconds, perfectHeal, evasiveReductionPercent, evasiveKbResistPercent, armorIgnorePercent, weakThresholdPercent, weakBonusPercent;

        Blade(ForgeConfigSpec.Builder b) {
            b.push("miquellas_blade");
            manaCost = i(b, "manaCost", 80, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 20, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 18, 4, 64, "Maximum targeting range (a target is required)");
            radius = f(b, "radius", 6, 1, 24, "Execution radius");
            dashFlat = f(b, "dashFlat", 45, 0, 100000, "Opening dash damage");
            slashFlat = f(b, "slashFlat", 20, 0, 100000, "Damage of one of the four slashes");
            thrustFlat = f(b, "thrustFlat", 75, 0, 100000, "Thrust flat damage");
            thrustPercent = f(b, "thrustPercent", 7, 0, 100, "Thrust max-HP percent");
            thrustKnock = f(b, "thrustKnock", 4, 0, 32, "Thrust pushback in blocks");
            executeFlat = f(b, "executeFlat", 95, 0, 100000, "Execution flat damage");
            executePercent = f(b, "executePercent", 8, 0, 100, "Execution max-HP percent");
            lifestealDash = f(b, "lifestealDash", 4, 0, 100000, "Health stolen by the dash");
            lifestealSlash = f(b, "lifestealSlash", 2, 0, 100000, "per slash");
            lifestealThrust = f(b, "lifestealThrust", 6, 0, 100000, "thrust");
            lifestealExecute = f(b, "lifestealExecute", 8, 0, 100000, "per enemy hit by the execution");
            lifestealCap = f(b, "lifestealCap", 40, 0, 100000, "Maximum health stolen per cast");
            perfectBonusPercent = f(b, "perfectBonusPercent", 30, 0, 1000, "Perfect Swordsmanship: execution bonus");
            perfectSpeedPercent = f(b, "perfectSpeedPercent", 25, 0, 500, "movement bonus");
            perfectSpeedSeconds = f(b, "perfectSpeedSeconds", 4, 0, 600, "duration");
            perfectHeal = f(b, "perfectHeal", 10, 0, 100000, "extra heal");
            evasiveReductionPercent = f(b, "evasiveReductionPercent", 35, 0, 100, "Evasive Footwork: damage reduction");
            evasiveKbResistPercent = f(b, "evasiveKbResistPercent", 80, 0, 100, "knockback resistance");
            armorIgnorePercent = f(b, "armorIgnorePercent", 20, 0, 100, "Unalloyed Precision: armor ignored by the thrust");
            weakThresholdPercent = f(b, "weakThresholdPercent", 30, 0, 100, "Health percent below which the thrust is stronger");
            weakBonusPercent = f(b, "weakBonusPercent", 15, 0, 1000, "thrust bonus against weak targets");
            b.pop();
        }
    }

    public static final class Goddess {
        public final Val<Integer> manaCost, killCount;
        public final Val<Double> cooldownSeconds, range, impactRadius, impactFlat, impactPercent, bloomRadius, bloomFlat, bloomPercent, swordFlat, swordRadius, judgmentRadius, judgmentFlat, judgmentPercent, finalFlat, finalPercent, bossPercentFactor, rotSeconds, rotDps, rotHealCutPercent, rotSlowPercent, battlefieldSeconds, battlefieldDps, battlefieldSlowPercent, buffSeconds, buffMeleePercent, buffSpeedPercent, buffLifestealPercent, buffReductionPercent, protectReductionPercent, killHealPercent, killExtraSeconds;

        Goddess(ForgeConfigSpec.Builder b) {
            b.push("goddess_of_rot");
            manaCost = i(b, "manaCost", 300, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 300, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 45, 4, 128, "Maximum targeting range");
            impactRadius = f(b, "impactRadius", 16, 1, 48, "Dive impact radius");
            impactFlat = f(b, "impactFlat", 180, 0, 100000, "Impact flat damage");
            impactPercent = f(b, "impactPercent", 15, 0, 100, "Impact max-HP percent");
            bloomRadius = f(b, "bloomRadius", 25, 1, 64, "Radius of the colossal flower and the rot");
            bloomFlat = f(b, "bloomFlat", 160, 0, 100000, "Bloom explosion flat damage");
            bloomPercent = f(b, "bloomPercent", 12, 0, 100, "Bloom explosion percent");
            swordFlat = f(b, "swordFlat", 40, 0, 100000, "Damage of one sword projection slam");
            swordRadius = f(b, "swordRadius", 5, 1, 16, "Radius of one slam");
            judgmentRadius = f(b, "judgmentRadius", 20, 1, 48, "Judgment wave radius");
            judgmentFlat = f(b, "judgmentFlat", 140, 0, 100000, "Judgment wave flat damage");
            judgmentPercent = f(b, "judgmentPercent", 10, 0, 100, "Judgment wave percent");
            finalFlat = f(b, "finalFlat", 200, 0, 100000, "Final detonation flat damage");
            finalPercent = f(b, "finalPercent", 18, 0, 100, "Final detonation percent");
            bossPercentFactor = f(b, "bossPercentFactor", 0.5, 0, 1, "Bosses take this fraction of the percent damage");
            rotSeconds = f(b, "rotSeconds", 12, 0, 600, "Scarlet Rot Catastrophe duration");
            rotDps = f(b, "rotDps", 20, 0, 100000, "rot damage per second");
            rotHealCutPercent = f(b, "rotHealCutPercent", 40, 0, 100, "healing reduction");
            rotSlowPercent = f(b, "rotSlowPercent", 25, 0, 95, "movement reduction (bosses take half)");
            battlefieldSeconds = f(b, "battlefieldSeconds", 12, 0, 600, "Aeonian Battlefield duration");
            battlefieldDps = f(b, "battlefieldDps", 12, 0, 100000, "battlefield rot damage per second");
            battlefieldSlowPercent = f(b, "battlefieldSlowPercent", 20, 0, 95, "battlefield slow");
            buffSeconds = f(b, "buffSeconds", 15, 0, 600, "Goddess of Rot state duration");
            buffMeleePercent = f(b, "buffMeleePercent", 30, 0, 1000, "melee bonus");
            buffSpeedPercent = f(b, "buffSpeedPercent", 20, 0, 500, "movement bonus");
            buffLifestealPercent = f(b, "buffLifestealPercent", 50, 0, 1000, "extra lifesteal");
            buffReductionPercent = f(b, "buffReductionPercent", 40, 0, 100, "damage reduction");
            protectReductionPercent = f(b, "protectReductionPercent", 90, 0, 100, "Divine Scarlet Protection: reduction while transforming and descending");
            killCount = i(b, "killCount", 5, 1, 64, "Undefeated Goddess: kills needed");
            killHealPercent = f(b, "killHealPercent", 30, 0, 100, "heal as a percent of max health");
            killExtraSeconds = f(b, "killExtraSeconds", 5, 0, 600, "extra Goddess state seconds");
            b.pop();
        }
    }
}
