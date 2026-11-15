package com.example.jjkspells.velgrynd.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/jjk_spells-velgrynd.toml (created on first launch; restart the game/world to apply changes). */
public final class VgrConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Breath BREATH;
    public static final Inferno INFERNO;
    public static final Accel ACCEL;
    public static final Dominion DOMINION;
    public static final Parallel PARALLEL;
    public static final Pursuit PURSUIT;
    public static final Star STAR;
    public static final Cthugha CTHUGHA;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        BREATH = new Breath(b);
        INFERNO = new Inferno(b);
        ACCEL = new Accel(b);
        DOMINION = new Dominion(b);
        PARALLEL = new Parallel(b);
        PURSUIT = new Pursuit(b);
        STAR = new Star(b);
        CTHUGHA = new Cthugha(b);
        SPEC = b.build();
    }

    private VgrConfig() {
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
        public final Val<Double> damageMultiplier, particleDensity, bossStunSlowPercent, manaRestoreMultiplier;

        General(ForgeConfigSpec.Builder b) {
            b.push("general");
            hitPlayers = t(b, "hitPlayers", true, "Can these spells hurt other players (still obeys Iron friendly-fire checks)");
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by the Velgrynd spells");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the server sends (lower it on weak machines)");
            bossStunSlowPercent = f(b, "bossSlowPercent", 80, 0, 100, "Bosses are never frozen; they lose this percent of their speed instead");
            manaRestoreMultiplier = f(b, "manaRestoreMultiplier", 1.0, 0, 100, "Multiplier for all mana the Velgrynd spells give back");
            b.pop();
        }
    }

    public static final class Breath {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, width, finalRadius, firstFlat, firstPercent, dps, finalFlat, finalPercent, armorIgnorePercent, reduceIgnorePercent, scorchDps, scorchHealPercent, scorchSeconds, accelPercent, accelMaxPercent, selfReductionPercent;

        Breath(ForgeConfigSpec.Builder b) {
            b.push("scorch_dragons_breath");
            manaCost = i(b, "manaCost", 350, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 35, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 140, 4, 260, "Maximum beam range");
            width = f(b, "width", 14, 1, 48, "Beam width");
            finalRadius = f(b, "finalRadius", 45, 2, 128, "Final detonation radius");
            firstFlat = f(b, "firstFlat", 320, 0, 100000, "Initial beam flat damage");
            firstPercent = f(b, "firstPercent", 25, 0, 100, "Initial beam percent");
            dps = f(b, "dps", 70, 0, 100000, "Continuous beam damage per second");
            finalFlat = f(b, "finalFlat", 420, 0, 100000, "Solar annihilation flat damage");
            finalPercent = f(b, "finalPercent", 30, 0, 100, "Solar annihilation percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 45, 0, 100, "Thermal Penetration: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 35, 0, 100, "magical damage reduction ignored");
            scorchDps = f(b, "scorchDps", 35, 0, 100000, "Absolute Scorch: burning damage per second");
            scorchHealPercent = f(b, "scorchHealPercent", 50, 0, 100, "healing reduction");
            scorchSeconds = f(b, "scorchSeconds", 10, 0, 600, "duration");
            accelPercent = f(b, "accelPercent", 10, 0, 1000, "Accelerated Destruction: extra beam damage per second inside");
            accelMaxPercent = f(b, "accelMaxPercent", 40, 0, 1000, "maximum bonus");
            selfReductionPercent = f(b, "selfReductionPercent", 85, 0, 100, "Dragon's Stability: caster damage reduction");
            b.pop();
        }
    }

    public static final class Inferno {
        public final Val<Integer> manaCost, sunCount;
        public final Val<Double> cooldownSeconds, range, radius, finalRadius, sunFlat, sunRadius, convergeFlat, convergePercent, finalFlat, finalPercent, brandPercent, brandMaxPercent, executeThresholdPercent, executeBonusPercent, slowPercent, healPercent, selfReductionPercent;

        Inferno(ForgeConfigSpec.Builder b) {
            b.push("cardinal_inferno");
            manaCost = i(b, "manaCost", 450, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 55, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 110, 4, 220, "Maximum targeting range");
            radius = f(b, "radius", 45, 2, 128, "Solar field radius");
            finalRadius = f(b, "finalRadius", 65, 2, 160, "Extinction supernova radius");
            sunCount = i(b, "sunCount", 7, 1, 32, "Number of suns");
            sunFlat = f(b, "sunFlat", 110, 0, 100000, "Damage of one solar impact");
            sunRadius = f(b, "sunRadius", 9, 1, 48, "Radius of one solar impact");
            convergeFlat = f(b, "convergeFlat", 350, 0, 100000, "Seven Suns convergence flat damage");
            convergePercent = f(b, "convergePercent", 28, 0, 100, "Convergence percent");
            finalFlat = f(b, "finalFlat", 450, 0, 100000, "Extinction supernova flat damage");
            finalPercent = f(b, "finalPercent", 35, 0, 100, "Supernova percent");
            brandPercent = f(b, "brandPercent", 8, 0, 1000, "Solar Branding: extra solar damage per stack");
            brandMaxPercent = f(b, "brandMaxPercent", 40, 0, 1000, "maximum bonus");
            executeThresholdPercent = f(b, "executeThresholdPercent", 30, 0, 100, "Stellar Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 35, 0, 1000, "bonus below it");
            slowPercent = f(b, "slowPercent", 40, 0, 95, "Scorching Atmosphere: movement reduction");
            healPercent = f(b, "healPercent", 30, 0, 100, "healing reduction");
            selfReductionPercent = f(b, "selfReductionPercent", 75, 0, 100, "True Dragon Protection: caster damage reduction");
            b.pop();
        }
    }

    public static final class Accel {
        public final Val<Integer> manaCost, strikeCount, maxHitsPerTarget, disorientHits;
        public final Val<Double> cooldownSeconds, range, radius, firstFlat, firstPercent, strikeFlat, finalFlat, finalPercent, armorIgnorePercent, precisionPercent, precisionMaxPercent, disorientSlowPercent, disorientSeconds, selfReductionPercent;

        Accel(ForgeConfigSpec.Builder b) {
            b.push("cardinal_acceleration");
            manaCost = i(b, "manaCost", 330, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 40, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 80, 4, 200, "Maximum targeting range");
            radius = f(b, "radius", 35, 2, 128, "Assault and rupture radius");
            firstFlat = f(b, "firstFlat", 240, 0, 100000, "Initial accelerated strike flat damage");
            firstPercent = f(b, "firstPercent", 20, 0, 100, "Initial strike percent");
            strikeFlat = f(b, "strikeFlat", 65, 0, 100000, "Damage of one follow-up strike");
            strikeCount = i(b, "strikeCount", 12, 0, 64, "Number of follow-up strikes");
            maxHitsPerTarget = i(b, "maxHitsPerTarget", 8, 1, 100, "Follow-up strikes one target can take");
            finalFlat = f(b, "finalFlat", 380, 0, 100000, "Light-speed judgment flat damage");
            finalPercent = f(b, "finalPercent", 30, 0, 100, "Judgment percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 35, 0, 100, "Thermal Severance: armor ignored by strikes");
            precisionPercent = f(b, "precisionPercent", 3, 0, 100, "Accelerated Precision: final damage per strike");
            precisionMaxPercent = f(b, "precisionMaxPercent", 36, 0, 1000, "maximum bonus");
            disorientHits = i(b, "disorientHits", 6, 1, 64, "Temporal Disorientation: hits needed");
            disorientSlowPercent = f(b, "disorientSlowPercent", 65, 0, 95, "movement reduction");
            disorientSeconds = f(b, "disorientSeconds", 5, 0, 600, "duration");
            selfReductionPercent = f(b, "selfReductionPercent", 85, 0, 100, "Untouchable Acceleration: caster damage reduction");
            b.pop();
        }
    }

    public static final class Dominion {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, radius, firstFlat, firstPercent, dps, finalFlat, finalPercent, healPercent, slowPercent, outPercent, spellBonusPercent, selfReductionPercent, executeThresholdPercent, executeBonusPercent;

        Dominion(ForgeConfigSpec.Builder b) {
            b.push("scorch_dragons_dominion");
            manaCost = i(b, "manaCost", 500, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 90, 0, 100000, "Cooldown in seconds");
            radius = f(b, "radius", 50, 2, 160, "Domain radius (centred on you)");
            firstFlat = f(b, "firstFlat", 220, 0, 100000, "World ignition flat damage");
            firstPercent = f(b, "firstPercent", 18, 0, 100, "Ignition percent");
            dps = f(b, "dps", 45, 0, 100000, "Scorching pressure damage per second");
            finalFlat = f(b, "finalFlat", 450, 0, 100000, "Thermal collapse flat damage");
            finalPercent = f(b, "finalPercent", 35, 0, 100, "Collapse percent");
            healPercent = f(b, "healPercent", 60, 0, 100, "Absolute Scorching: healing reduction");
            slowPercent = f(b, "slowPercent", 45, 0, 95, "movement reduction");
            outPercent = f(b, "outPercent", 35, 0, 100, "outgoing damage reduction");
            spellBonusPercent = f(b, "spellBonusPercent", 45, 0, 1000, "Cardinal Supremacy: spell damage bonus");
            selfReductionPercent = f(b, "selfReductionPercent", 65, 0, 100, "caster damage reduction");
            executeThresholdPercent = f(b, "executeThresholdPercent", 25, 0, 100, "Thermal Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 35, 0, 1000, "bonus below it");
            b.pop();
        }
    }

    public static final class Parallel {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, distance, beamWidth, beamFlat, judgeFlat, judgePercent, finalFlat, finalPercent, slowPercent, bossSlowPercent, vulnPercent, vulnMaxPercent, selfReductionPercent;

        Parallel(ForgeConfigSpec.Builder b) {
            b.push("parallel_existence");
            manaCost = i(b, "manaCost", 550, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 95, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 90, 4, 200, "Maximum targeting range");
            radius = f(b, "radius", 45, 2, 128, "Combined attack radius");
            distance = f(b, "distance", 30, 5, 90, "Distance of the four bodies from the target");
            beamWidth = f(b, "beamWidth", 7, 1, 32, "Width of one beam");
            beamFlat = f(b, "beamFlat", 150, 0, 100000, "Damage of one parallel beam hit");
            judgeFlat = f(b, "judgeFlat", 400, 0, 100000, "Convergent dragon judgment flat damage");
            judgePercent = f(b, "judgePercent", 32, 0, 100, "Judgment percent");
            finalFlat = f(b, "finalFlat", 480, 0, 100000, "Fourfold detonation flat damage");
            finalPercent = f(b, "finalPercent", 38, 0, 100, "Detonation percent");
            slowPercent = f(b, "slowPercent", 65, 0, 95, "Unavoidable Convergence: movement reduction");
            bossSlowPercent = f(b, "bossSlowPercent", 35, 0, 100, "Bosses receive this percent of it");
            vulnPercent = f(b, "vulnPercent", 10, 0, 1000, "Fourfold Scorch: damage taken per beam that hit");
            vulnMaxPercent = f(b, "vulnMaxPercent", 30, 0, 1000, "maximum");
            selfReductionPercent = f(b, "selfReductionPercent", 75, 0, 100, "True Dragon Multiplicity: caster damage reduction");
            b.pop();
        }
    }

    public static final class Pursuit {
        public final Val<Integer> manaCost, strikeCount;
        public final Val<Double> cooldownSeconds, range, radius, finalRadius, brandFlat, brandPercent, strikeFlat, strikeRadius, finalFlat, finalPercent, markBonusPercent, accelPercent, accelMaxPercent, armorIgnorePercent, reduceIgnorePercent;

        Pursuit(ForgeConfigSpec.Builder b) {
            b.push("dimensional_scorch");
            manaCost = i(b, "manaCost", 420, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 75, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 100, 4, 220, "Maximum targeting range");
            radius = f(b, "radius", 35, 2, 128, "Portal attack radius");
            finalRadius = f(b, "finalRadius", 45, 2, 128, "Final explosion radius");
            brandFlat = f(b, "brandFlat", 200, 0, 100000, "Initial dimensional brand flat damage");
            brandPercent = f(b, "brandPercent", 15, 0, 100, "Brand percent");
            strikeFlat = f(b, "strikeFlat", 90, 0, 100000, "Damage of one dimensional flame strike");
            strikeCount = i(b, "strikeCount", 12, 1, 64, "Number of strikes");
            strikeRadius = f(b, "strikeRadius", 5, 1, 32, "Radius of one strike");
            finalFlat = f(b, "finalFlat", 480, 0, 100000, "Final pursuit execution flat damage");
            finalPercent = f(b, "finalPercent", 38, 0, 100, "Execution percent");
            markBonusPercent = f(b, "markBonusPercent", 25, 0, 1000, "Dimensional Brand: extra damage from this spell on the marked target");
            accelPercent = f(b, "accelPercent", 5, 0, 1000, "Accelerated Flames: extra damage per successful strike");
            accelMaxPercent = f(b, "accelMaxPercent", 40, 0, 1000, "maximum bonus");
            armorIgnorePercent = f(b, "armorIgnorePercent", 45, 0, 100, "Dimensional Penetration: armor ignored by the execution");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 35, 0, 100, "damage reduction ignored");
            b.pop();
        }
    }

    public static final class Star {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, supernovaRadius, finalRadius, impactFlat, impactPercent, supernovaFlat, supernovaPercent, finalFlat, finalPercent, armorIgnorePercent, reduceIgnorePercent, overheatPercent, overheatSeconds, centerBonusPercent, gravityStrength, protectSeconds, protectPercent;

        Star(ForgeConfigSpec.Builder b) {
            b.push("cardinal_cataclysm");
            manaCost = i(b, "manaCost", 750, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 135, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 120, 4, 240, "Maximum targeting range");
            radius = f(b, "radius", 40, 2, 128, "Stellar impact radius");
            supernovaRadius = f(b, "supernovaRadius", 50, 2, 160, "Cardinal supernova radius");
            finalRadius = f(b, "finalRadius", 75, 2, 200, "Final stellar collapse radius");
            impactFlat = f(b, "impactFlat", 300, 0, 100000, "Stellar impact flat damage");
            impactPercent = f(b, "impactPercent", 25, 0, 100, "Impact percent");
            supernovaFlat = f(b, "supernovaFlat", 500, 0, 100000, "Supernova flat damage");
            supernovaPercent = f(b, "supernovaPercent", 40, 0, 100, "Supernova percent");
            finalFlat = f(b, "finalFlat", 450, 0, 100000, "Final collapse flat damage");
            finalPercent = f(b, "finalPercent", 35, 0, 100, "Final collapse percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 50, 0, 100, "Stellar Annihilation: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 40, 0, 100, "magical damage reduction ignored");
            overheatPercent = f(b, "overheatPercent", 35, 0, 1000, "Cardinal Overheat: extra magical damage taken");
            overheatSeconds = f(b, "overheatSeconds", 10, 0, 600, "duration");
            centerBonusPercent = f(b, "centerBonusPercent", 25, 0, 1000, "Accelerated Collapse: maximum extra damage at the centre");
            gravityStrength = f(b, "gravityStrength", 0.3, 0, 3, "pull strength while the star contracts");
            protectSeconds = f(b, "protectSeconds", 5, 0, 30, "Scorch Dragon Protection: length at the end");
            protectPercent = f(b, "protectPercent", 90, 0, 100, "caster damage reduction");
            b.pop();
        }
    }

    public static final class Cthugha {
        public final Val<Integer> manaCost, endKills;
        public final Val<Double> cooldownSeconds, range, domainRadius, radius, stellarRadius, finalRadius, firstFlat, firstPercent, annihilationFlat, annihilationPercent, stellarFlat, stellarPercent, finalFlat, finalPercent, armorIgnorePercent, reduceIgnorePercent, bossBonusPercent, markBonusPercent, markHealPercent, selfReductionPercent, lockSeconds, blessSeconds, blessSpellPercent, blessScorchPercent, blessReductionPercent, blessSpeedPercent, endHealPercent, endExtraSeconds, pullStrength;

        Cthugha(ForgeConfigSpec.Builder b) {
            b.push("flame_god_cthugha");
            manaCost = i(b, "manaCost", 1100, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 360, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 150, 4, 260, "Maximum targeting range");
            domainRadius = f(b, "domainRadius", 90, 2, 200, "Artificial dimension radius");
            radius = f(b, "radius", 65, 2, 160, "Primary destruction radius");
            stellarRadius = f(b, "stellarRadius", 75, 2, 200, "Stellar incineration radius");
            finalRadius = f(b, "finalRadius", 110, 2, 260, "Final extinction radius");
            firstFlat = f(b, "firstFlat", 450, 0, 100000, "First judgment flat damage");
            firstPercent = f(b, "firstPercent", 35, 0, 100, "First judgment percent");
            annihilationFlat = f(b, "annihilationFlat", 550, 0, 100000, "Accelerated dragon annihilation flat damage");
            annihilationPercent = f(b, "annihilationPercent", 45, 0, 100, "Annihilation percent");
            stellarFlat = f(b, "stellarFlat", 600, 0, 100000, "Stellar incineration flat damage");
            stellarPercent = f(b, "stellarPercent", 50, 0, 100, "Stellar incineration percent");
            finalFlat = f(b, "finalFlat", 900, 0, 100000, "Final Cthugha extinction flat damage");
            finalPercent = f(b, "finalPercent", 70, 0, 100, "Final extinction percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 60, 0, 100, "Cthugha's Absolute Authority: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 50, 0, 100, "damage reduction ignored");
            bossBonusPercent = f(b, "bossBonusPercent", 40, 0, 1000, "final extinction bonus against bosses");
            markBonusPercent = f(b, "markBonusPercent", 30, 0, 1000, "Scorch Extinction Mark: extra damage taken from later phases");
            markHealPercent = f(b, "markHealPercent", 70, 0, 100, "healing reduction");
            selfReductionPercent = f(b, "selfReductionPercent", 95, 0, 100, "Absolute Acceleration: caster damage reduction");
            lockSeconds = f(b, "lockSeconds", 16, 0, 24, "Movement is locked for this long at the end");
            blessSeconds = f(b, "blessSeconds", 25, 0, 600, "Cardinal Sovereignty duration");
            blessSpellPercent = f(b, "blessSpellPercent", 65, 0, 1000, "spell damage bonus");
            blessScorchPercent = f(b, "blessScorchPercent", 50, 0, 1000, "melee / scorching damage bonus");
            blessReductionPercent = f(b, "blessReductionPercent", 70, 0, 100, "damage reduction");
            blessSpeedPercent = f(b, "blessSpeedPercent", 40, 0, 500, "movement bonus");
            endKills = i(b, "endKills", 5, 1, 64, "The End of All Flames: defeated enemies needed");
            endHealPercent = f(b, "endHealPercent", 50, 0, 100, "heal as percent of max health");
            endExtraSeconds = f(b, "endExtraSeconds", 10, 0, 600, "extra sovereignty seconds");
            pullStrength = f(b, "pullStrength", 0.3, 0, 3, "pull strength during the collapse");
            b.pop();
        }
    }
}
