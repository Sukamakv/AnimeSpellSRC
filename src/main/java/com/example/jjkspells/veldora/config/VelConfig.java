package com.example.jjkspells.veldora.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/jjk_spells-veldora.toml (created on first launch; restart the game/world to apply changes). */
public final class VelConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Wrath WRATH;
    public static final Thunder THUNDER;
    public static final Breath BREATH;
    public static final Fist FIST;
    public static final Chaos CHAOS;
    public static final Ascend ASCEND;
    public static final Nova NOVA;
    public static final Eternal ETERNAL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        WRATH = new Wrath(b);
        THUNDER = new Thunder(b);
        BREATH = new Breath(b);
        FIST = new Fist(b);
        CHAOS = new Chaos(b);
        ASCEND = new Ascend(b);
        NOVA = new Nova(b);
        ETERNAL = new Eternal(b);
        SPEC = b.build();
    }

    private VelConfig() {
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
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by the Veldora spells");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the server sends (lower it on weak machines)");
            bossStunSlowPercent = f(b, "bossSlowPercent", 80, 0, 100, "Bosses are never frozen; they lose this percent of their speed instead");
            manaRestoreMultiplier = f(b, "manaRestoreMultiplier", 1.0, 0, 100, "Multiplier for all mana the Veldora spells give back");
            b.pop();
        }
    }

    public static final class Wrath {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, finalRadius, firstFlat, firstPercent, dps, finalFlat, finalPercent, slowPercent, outPercent, pullStrength, bossPullPercent, waterBonusPercent, selfReductionPercent, executeThresholdPercent, executeBonusPercent;

        Wrath(ForgeConfigSpec.Builder b) {
            b.push("storm_dragons_wrath");
            manaCost = i(b, "manaCost", 300, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 32, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 90, 4, 200, "Maximum targeting range");
            radius = f(b, "radius", 35, 2, 128, "Primary storm radius");
            finalRadius = f(b, "finalRadius", 55, 2, 160, "Final tempest radius");
            firstFlat = f(b, "firstFlat", 200, 0, 100000, "Initial storm shockwave flat damage");
            firstPercent = f(b, "firstPercent", 18, 0, 100, "Initial shockwave percent");
            dps = f(b, "dps", 45, 0, 100000, "Cyclone destruction damage per second");
            finalFlat = f(b, "finalFlat", 350, 0, 100000, "Final tempest detonation flat damage");
            finalPercent = f(b, "finalPercent", 28, 0, 100, "Final detonation percent");
            slowPercent = f(b, "slowPercent", 60, 0, 95, "Storm Dragon Dominion: movement reduction");
            outPercent = f(b, "outPercent", 35, 0, 100, "outgoing damage reduction");
            pullStrength = f(b, "pullStrength", 0.3, 0, 3, "inward pull strength");
            bossPullPercent = f(b, "bossPullPercent", 35, 0, 100, "Bosses receive this percent of the pull");
            waterBonusPercent = f(b, "waterBonusPercent", 25, 0, 1000, "Thunderstorm Amplification: extra lightning damage in water");
            selfReductionPercent = f(b, "selfReductionPercent", 70, 0, 100, "True Dragon Stability: caster damage reduction");
            executeThresholdPercent = f(b, "executeThresholdPercent", 30, 0, 100, "Atmospheric Devastation threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 30, 0, 1000, "bonus below it");
            b.pop();
        }
    }

    public static final class Thunder {
        public final Val<Integer> manaCost, strikeCount, maxHitsPerTarget;
        public final Val<Double> cooldownSeconds, range, radius, firstRadius, finalRadius, firstFlat, firstPercent, strikeFlat, strikeRadius, finalFlat, finalPercent, paralysisSlowPercent, paralysisAtkPercent, paralysisSeconds, waterBonusPercent, selfReductionPercent, executeThresholdPercent, executeBonusPercent;

        Thunder(ForgeConfigSpec.Builder b) {
            b.push("black_thunder_apocalypse");
            manaCost = i(b, "manaCost", 320, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 38, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 100, 4, 220, "Maximum targeting range");
            radius = f(b, "radius", 40, 2, 128, "Primary lightning radius");
            firstRadius = f(b, "firstRadius", 25, 2, 96, "First black lightning radius");
            finalRadius = f(b, "finalRadius", 60, 2, 160, "Final thunder radius");
            firstFlat = f(b, "firstFlat", 250, 0, 100000, "First black lightning flat damage");
            firstPercent = f(b, "firstPercent", 20, 0, 100, "First strike percent");
            strikeFlat = f(b, "strikeFlat", 90, 0, 100000, "Damage of one secondary strike");
            strikeCount = i(b, "strikeCount", 40, 0, 400, "Number of secondary strikes");
            strikeRadius = f(b, "strikeRadius", 4, 1, 24, "Radius of one secondary strike");
            maxHitsPerTarget = i(b, "maxHitsPerTarget", 5, 1, 100, "Secondary strikes one target can take");
            finalFlat = f(b, "finalFlat", 400, 0, 100000, "Divine thunder flat damage");
            finalPercent = f(b, "finalPercent", 32, 0, 100, "Divine thunder percent");
            paralysisSlowPercent = f(b, "paralysisSlowPercent", 60, 0, 95, "Absolute Paralysis: movement reduction");
            paralysisAtkPercent = f(b, "paralysisAtkPercent", 50, 0, 95, "attack speed reduction");
            paralysisSeconds = f(b, "paralysisSeconds", 6, 0, 600, "duration");
            waterBonusPercent = f(b, "waterBonusPercent", 30, 0, 1000, "Electrical Conduction: extra lightning damage in water");
            selfReductionPercent = f(b, "selfReductionPercent", 75, 0, 100, "Storm Dragon Authority: caster damage reduction");
            executeThresholdPercent = f(b, "executeThresholdPercent", 30, 0, 100, "Thunder Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 35, 0, 1000, "bonus below it");
            b.pop();
        }
    }

    public static final class Breath {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, width, finalRadius, firstFlat, firstPercent, dps, finalFlat, finalPercent, armorIgnorePercent, reduceIgnorePercent, overloadVulnPercent, overloadHealPercent, overloadSeconds, selfReductionPercent, executeThresholdPercent, executeBonusPercent;

        Breath(ForgeConfigSpec.Builder b) {
            b.push("dragon_breath");
            manaCost = i(b, "manaCost", 350, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 45, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 120, 4, 240, "Maximum beam range");
            width = f(b, "width", 12, 1, 48, "Beam width");
            finalRadius = f(b, "finalRadius", 45, 2, 128, "Final explosion radius");
            firstFlat = f(b, "firstFlat", 300, 0, 100000, "Initial breath impact flat damage");
            firstPercent = f(b, "firstPercent", 25, 0, 100, "Initial impact percent");
            dps = f(b, "dps", 60, 0, 100000, "Continuous breath damage per second");
            finalFlat = f(b, "finalFlat", 380, 0, 100000, "Final breath explosion flat damage");
            finalPercent = f(b, "finalPercent", 30, 0, 100, "Final explosion percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 45, 0, 100, "True Dragon Penetration: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 35, 0, 100, "magical damage reduction ignored");
            overloadVulnPercent = f(b, "overloadVulnPercent", 30, 0, 1000, "Draconic Overload: extra damage taken");
            overloadHealPercent = f(b, "overloadHealPercent", 50, 0, 100, "healing reduction");
            overloadSeconds = f(b, "overloadSeconds", 8, 0, 600, "duration");
            selfReductionPercent = f(b, "selfReductionPercent", 85, 0, 100, "Immovable Dragon: caster damage reduction");
            executeThresholdPercent = f(b, "executeThresholdPercent", 30, 0, 100, "Catastrophic Breath threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 35, 0, 1000, "bonus below it");
            b.pop();
        }
    }

    public static final class Fist {
        public final Val<Integer> manaCost, strikeCount, paralysisHits;
        public final Val<Double> cooldownSeconds, range, radius, finalRadius, firstFlat, firstPercent, strikeFlat, finalFlat, finalPercent, armorIgnorePercent, comboBonusPercent, comboMaxPercent, paralysisSlowPercent, paralysisAtkPercent, paralysisSeconds, selfReductionPercent;

        Fist(ForgeConfigSpec.Builder b) {
            b.push("storm_dragons_fist");
            manaCost = i(b, "manaCost", 330, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 40, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 60, 4, 160, "Maximum targeting range");
            radius = f(b, "radius", 20, 2, 96, "Assault radius");
            finalRadius = f(b, "finalRadius", 40, 2, 128, "Final shockwave radius");
            firstFlat = f(b, "firstFlat", 220, 0, 100000, "Initial martial strike flat damage");
            firstPercent = f(b, "firstPercent", 18, 0, 100, "Initial strike percent");
            strikeFlat = f(b, "strikeFlat", 75, 0, 100000, "Damage of one secondary strike");
            strikeCount = i(b, "strikeCount", 8, 0, 64, "Number of secondary strikes");
            finalFlat = f(b, "finalFlat", 350, 0, 100000, "Final dragon fist flat damage");
            finalPercent = f(b, "finalPercent", 28, 0, 100, "Final fist percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 40, 0, 100, "Dragon Fist Penetration: armor ignored by the final punch");
            comboBonusPercent = f(b, "comboBonusPercent", 4, 0, 100, "Storm Combo: final damage per secondary hit");
            comboMaxPercent = f(b, "comboMaxPercent", 32, 0, 1000, "maximum bonus");
            paralysisHits = i(b, "paralysisHits", 4, 1, 64, "Thunder Paralysis: secondary hits needed");
            paralysisSlowPercent = f(b, "paralysisSlowPercent", 50, 0, 95, "movement reduction");
            paralysisAtkPercent = f(b, "paralysisAtkPercent", 40, 0, 95, "attack speed reduction");
            paralysisSeconds = f(b, "paralysisSeconds", 5, 0, 600, "duration");
            selfReductionPercent = f(b, "selfReductionPercent", 80, 0, 100, "True Dragon Momentum: caster damage reduction");
            b.pop();
        }
    }

    public static final class Chaos {
        public final Val<Integer> manaCost, strikeCount;
        public final Val<Double> cooldownSeconds, range, radius, firstFlat, firstPercent, strikeFlat, finalFlat, finalPercent, negatePercent, bonusChancePercent, bonusDamagePercent, redirectPercent, slowPercent, outPercent, healPercent, selfReductionPercent, executeThresholdPercent, executeBonusPercent;

        Chaos(ForgeConfigSpec.Builder b) {
            b.push("law_of_impossible_fate");
            manaCost = i(b, "manaCost", 450, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 85, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 85, 4, 200, "Maximum targeting range");
            radius = f(b, "radius", 40, 2, 128, "Domain radius");
            firstFlat = f(b, "firstFlat", 220, 0, 100000, "Initial reality distortion flat damage");
            firstPercent = f(b, "firstPercent", 18, 0, 100, "Initial distortion percent");
            strikeFlat = f(b, "strikeFlat", 100, 0, 100000, "Damage of one probability strike");
            strikeCount = i(b, "strikeCount", 12, 0, 100, "Number of probability strikes");
            finalFlat = f(b, "finalFlat", 420, 0, 100000, "Final fate collapse flat damage");
            finalPercent = f(b, "finalPercent", 35, 0, 100, "Final collapse percent");
            negatePercent = f(b, "negatePercent", 40, 0, 100, "Probability Manipulation: chance to negate an ordinary attack");
            bonusChancePercent = f(b, "bonusChancePercent", 25, 0, 100, "chance for your attack to deal extra damage");
            bonusDamagePercent = f(b, "bonusDamagePercent", 50, 0, 1000, "extra damage of such an attack");
            redirectPercent = f(b, "redirectPercent", 35, 0, 100, "chance to redirect a hostile projectile");
            slowPercent = f(b, "slowPercent", 50, 0, 95, "Chaos Suppression: movement reduction");
            outPercent = f(b, "outPercent", 35, 0, 100, "outgoing damage reduction");
            healPercent = f(b, "healPercent", 30, 0, 100, "healing reduction");
            selfReductionPercent = f(b, "selfReductionPercent", 50, 0, 100, "True Dragon's Fortune: caster damage reduction");
            executeThresholdPercent = f(b, "executeThresholdPercent", 30, 0, 100, "Fate Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 35, 0, 1000, "bonus below it");
            b.pop();
        }
    }

    public static final class Ascend {
        public final Val<Integer> manaCost, shockEvery, overdriveKills;
        public final Val<Double> cooldownSeconds, durationSeconds, radius, finalRadius, firstFlat, firstPercent, finalFlat, finalPercent, damageBonusPercent, speedPercent, reductionPercent, meleeFlat, shockRadius, shockFlat, shockPercent, projectileReducePercent, overdriveSeconds, overdriveDamagePercent;

        Ascend(ForgeConfigSpec.Builder b) {
            b.push("storm_dragons_ascension");
            manaCost = i(b, "manaCost", 500, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 130, 0, 100000, "Cooldown in seconds");
            durationSeconds = f(b, "durationSeconds", 20, 1, 600, "Transformation duration");
            radius = f(b, "radius", 35, 2, 128, "Transformation shockwave radius");
            finalRadius = f(b, "finalRadius", 50, 2, 160, "Final dragon cataclysm radius");
            firstFlat = f(b, "firstFlat", 250, 0, 100000, "Transformation shockwave flat damage");
            firstPercent = f(b, "firstPercent", 20, 0, 100, "Shockwave percent");
            finalFlat = f(b, "finalFlat", 480, 0, 100000, "Final dragon cataclysm flat damage");
            finalPercent = f(b, "finalPercent", 38, 0, 100, "Final cataclysm percent");
            damageBonusPercent = f(b, "damageBonusPercent", 60, 0, 1000, "Physical and magical damage bonus");
            speedPercent = f(b, "speedPercent", 40, 0, 500, "Movement bonus");
            reductionPercent = f(b, "reductionPercent", 70, 0, 100, "Damage reduction");
            meleeFlat = f(b, "meleeFlat", 120, 0, 100000, "Extra damage on ordinary melee attacks");
            shockEvery = i(b, "shockEvery", 3, 1, 100, "Storm Dragon's Strikes: every Nth melee attack creates a shockwave");
            shockRadius = f(b, "shockRadius", 10, 1, 64, "shockwave radius");
            shockFlat = f(b, "shockFlat", 120, 0, 100000, "shockwave flat damage");
            shockPercent = f(b, "shockPercent", 10, 0, 100, "shockwave percent");
            projectileReducePercent = f(b, "projectileReducePercent", 85, 0, 100, "Dragon's Protection: projectile damage reduction");
            overdriveKills = i(b, "overdriveKills", 3, 1, 64, "Calamity Overdrive: defeated enemies needed");
            overdriveSeconds = f(b, "overdriveSeconds", 5, 0, 600, "extra duration");
            overdriveDamagePercent = f(b, "overdriveDamagePercent", 15, 0, 1000, "extra damage bonus");
            b.pop();
        }
    }

    public static final class Nova {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, supernovaRadius, finalRadius, impactFlat, impactPercent, supernovaFlat, supernovaPercent, finalFlat, finalPercent, armorIgnorePercent, reduceIgnorePercent, fractureVulnPercent, fractureHealPercent, fractureSeconds, gravityStrength, executeThresholdPercent, executeBonusPercent;

        Nova(ForgeConfigSpec.Builder b) {
            b.push("dragon_nova");
            manaCost = i(b, "manaCost", 700, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 125, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 110, 4, 220, "Maximum targeting range");
            radius = f(b, "radius", 35, 2, 128, "Dragon star impact radius");
            supernovaRadius = f(b, "supernovaRadius", 45, 2, 128, "Supernova radius");
            finalRadius = f(b, "finalRadius", 70, 2, 200, "Final celestial collapse radius");
            impactFlat = f(b, "impactFlat", 280, 0, 100000, "Dragon star impact flat damage");
            impactPercent = f(b, "impactPercent", 22, 0, 100, "Impact percent");
            supernovaFlat = f(b, "supernovaFlat", 450, 0, 100000, "Dragon supernova flat damage");
            supernovaPercent = f(b, "supernovaPercent", 35, 0, 100, "Supernova percent");
            finalFlat = f(b, "finalFlat", 380, 0, 100000, "Celestial collapse flat damage");
            finalPercent = f(b, "finalPercent", 30, 0, 100, "Collapse percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 45, 0, 100, "Dragon Energy Annihilation: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 35, 0, 100, "magical damage reduction ignored");
            fractureVulnPercent = f(b, "fractureVulnPercent", 30, 0, 1000, "Storm Fracture: extra damage taken");
            fractureHealPercent = f(b, "fractureHealPercent", 50, 0, 100, "healing reduction");
            fractureSeconds = f(b, "fractureSeconds", 10, 0, 600, "duration");
            gravityStrength = f(b, "gravityStrength", 0.3, 0, 3, "Thunder Gravity: pull strength");
            executeThresholdPercent = f(b, "executeThresholdPercent", 30, 0, 100, "Celestial Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 40, 0, 1000, "bonus below it");
            b.pop();
        }
    }

    public static final class Eternal {
        public final Val<Integer> manaCost, endKills;
        public final Val<Double> cooldownSeconds, range, domainRadius, radius, collapseRadius, finalRadius, firstFlat, firstPercent, annihilationFlat, annihilationPercent, collapseFlat, collapsePercent, finalFlat, finalPercent, armorIgnorePercent, reduceIgnorePercent, bossBonusPercent, markBonusPercent, markHealPercent, negatePercent, bonusChancePercent, bonusDamagePercent, redirectPercent, blessSeconds, blessDamagePercent, blessReductionPercent, blessSpeedPercent, endHealPercent, endExtraSeconds, protectReductionPercent, protectSeconds, pullStrength;

        Eternal(ForgeConfigSpec.Builder b) {
            b.push("end_of_the_eternal_storm");
            manaCost = i(b, "manaCost", 1000, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 360, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 140, 4, 260, "Maximum targeting range");
            domainRadius = f(b, "domainRadius", 85, 2, 200, "Eternal storm dimension radius");
            radius = f(b, "radius", 60, 2, 160, "Primary annihilation radius");
            collapseRadius = f(b, "collapseRadius", 75, 2, 200, "Probability collapse radius");
            finalRadius = f(b, "finalRadius", 105, 2, 260, "Final cataclysm radius");
            firstFlat = f(b, "firstFlat", 400, 0, 100000, "First judgment flat damage");
            firstPercent = f(b, "firstPercent", 32, 0, 100, "First judgment percent");
            annihilationFlat = f(b, "annihilationFlat", 550, 0, 100000, "Storm dragon annihilation flat damage");
            annihilationPercent = f(b, "annihilationPercent", 45, 0, 100, "Annihilation percent");
            collapseFlat = f(b, "collapseFlat", 450, 0, 100000, "Probability collapse flat damage");
            collapsePercent = f(b, "collapsePercent", 38, 0, 100, "Probability collapse percent");
            finalFlat = f(b, "finalFlat", 800, 0, 100000, "Final Nyarlathotep cataclysm flat damage");
            finalPercent = f(b, "finalPercent", 65, 0, 100, "Final cataclysm percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 55, 0, 100, "Nyarlathotep's Absolute Authority: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 45, 0, 100, "damage reduction ignored");
            bossBonusPercent = f(b, "bossBonusPercent", 40, 0, 1000, "final cataclysm bonus against bosses");
            markBonusPercent = f(b, "markBonusPercent", 30, 0, 1000, "Chaos Extinction Mark: extra damage taken from later phases");
            markHealPercent = f(b, "markHealPercent", 60, 0, 100, "healing reduction");
            negatePercent = f(b, "negatePercent", 50, 0, 100, "Probability Domination: chance to negate an ordinary attack");
            bonusChancePercent = f(b, "bonusChancePercent", 30, 0, 100, "chance for your attack to deal extra damage");
            bonusDamagePercent = f(b, "bonusDamagePercent", 50, 0, 1000, "extra damage of such an attack");
            redirectPercent = f(b, "redirectPercent", 40, 0, 100, "chance to redirect a hostile projectile");
            blessSeconds = f(b, "blessSeconds", 25, 0, 600, "True Dragon's Blessing duration");
            blessDamagePercent = f(b, "blessDamagePercent", 65, 0, 1000, "damage bonus");
            blessReductionPercent = f(b, "blessReductionPercent", 70, 0, 100, "damage reduction");
            blessSpeedPercent = f(b, "blessSpeedPercent", 40, 0, 500, "movement bonus");
            endKills = i(b, "endKills", 5, 1, 64, "Eternal Storm: defeated enemies needed");
            endHealPercent = f(b, "endHealPercent", 50, 0, 100, "heal as percent of max health");
            endExtraSeconds = f(b, "endExtraSeconds", 10, 0, 600, "extra blessing seconds");
            protectReductionPercent = f(b, "protectReductionPercent", 95, 0, 100, "Absolute Dragon Protection: reduction in the last seconds");
            protectSeconds = f(b, "protectSeconds", 12, 1, 22, "Absolute Dragon Protection: length");
            pullStrength = f(b, "pullStrength", 0.3, 0, 3, "pull strength");
            b.pop();
        }
    }
}
