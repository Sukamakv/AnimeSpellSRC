package com.example.jjkspells.tempest.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/jjk_spells-tempest.toml (created on first launch; restart the game/world to apply changes). */
public final class TemConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Abyss ABYSS;
    public static final Storm STORM;
    public static final Array ARRAY;
    public static final Law LAW;
    public static final Fracture FRACTURE;
    public static final Gluttony GLUTTONY;
    public static final Nothing NOTHING;
    public static final Azathoth AZATHOTH;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        ABYSS = new Abyss(b);
        STORM = new Storm(b);
        ARRAY = new Array(b);
        LAW = new Law(b);
        FRACTURE = new Fracture(b);
        GLUTTONY = new Gluttony(b);
        NOTHING = new Nothing(b);
        AZATHOTH = new Azathoth(b);
        SPEC = b.build();
    }

    private TemConfig() {
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
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by the Tempest spells");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the server sends (lower it on weak machines)");
            bossStunSlowPercent = f(b, "bossSlowPercent", 80, 0, 100, "Bosses are never frozen; they lose this percent of their speed instead");
            manaRestoreMultiplier = f(b, "manaRestoreMultiplier", 1.0, 0, 100, "Multiplier for all mana the Tempest spells give back");
            b.pop();
        }
    }

    public static final class Abyss {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, finalRadius, waveFlat, wavePercent, dps, finalFlat, finalPercent, consumeThresholdPercent, healPercent, manaPercent, restoreMaxPercent, slowPercent, outPercent, pullStrength, bossPullPercent, projectileBonus, projectileBonusMax;

        Abyss(ForgeConfigSpec.Builder b) {
            b.push("world_devouring_abyss");
            manaCost = i(b, "manaCost", 150, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 35, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 70, 4, 160, "Maximum targeting range");
            radius = f(b, "radius", 25, 2, 96, "Initial devouring radius");
            finalRadius = f(b, "finalRadius", 40, 2, 128, "Final consumption radius");
            waveFlat = f(b, "waveFlat", 180, 0, 100000, "Initial devouring wave flat damage");
            wavePercent = f(b, "wavePercent", 15, 0, 100, "Initial wave max-HP percent");
            dps = f(b, "dps", 35, 0, 100000, "Continuous consumption damage per second");
            finalFlat = f(b, "finalFlat", 300, 0, 100000, "Final abyssal consumption flat damage");
            finalPercent = f(b, "finalPercent", 25, 0, 100, "Final consumption percent");
            consumeThresholdPercent = f(b, "consumeThresholdPercent", 15, 0, 100, "Absolute Predation: health threshold for being consumed");
            healPercent = f(b, "healPercent", 5, 0, 100, "Soul Assimilation: max health restored per defeated enemy");
            manaPercent = f(b, "manaPercent", 5, 0, 100, "Soul Assimilation: max mana restored per defeated enemy");
            restoreMaxPercent = f(b, "restoreMaxPercent", 30, 0, 100, "Soul Assimilation: maximum restoration per cast");
            slowPercent = f(b, "slowPercent", 60, 0, 95, "Devouring Gravity: movement reduction");
            outPercent = f(b, "outPercent", 30, 0, 100, "Devouring Gravity: outgoing damage reduction");
            pullStrength = f(b, "pullStrength", 0.3, 0, 3, "Devouring Gravity: pull strength");
            bossPullPercent = f(b, "bossPullPercent", 35, 0, 100, "Bosses receive this percent of the pull");
            projectileBonus = f(b, "projectileBonus", 10, 0, 100000, "Magical Consumption: final flat damage per absorbed projectile");
            projectileBonusMax = f(b, "projectileBonusMax", 200, 0, 100000, "maximum bonus");
            b.pop();
        }
    }

    public static final class Storm {
        public final Val<Integer> manaCost, strikeCount, maxHitsPerTarget;
        public final Val<Double> cooldownSeconds, range, radius, finalRadius, firstRadius, firstFlat, firstPercent, strikeFlat, strikeRadius, finalFlat, finalPercent, paralysisSlowPercent, paralysisAtkPercent, paralysisSeconds, waterBonusPercent, selfReductionPercent, executeThresholdPercent, executeBonusPercent;

        Storm(ForgeConfigSpec.Builder b) {
            b.push("civilization_storm");
            manaCost = i(b, "manaCost", 180, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 30, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 85, 4, 200, "Maximum targeting range");
            radius = f(b, "radius", 40, 2, 128, "Storm radius");
            finalRadius = f(b, "finalRadius", 55, 2, 160, "Final thunderstorm radius");
            firstRadius = f(b, "firstRadius", 20, 2, 96, "First strike radius");
            firstFlat = f(b, "firstFlat", 200, 0, 100000, "First black lightning flat damage");
            firstPercent = f(b, "firstPercent", 18, 0, 100, "First strike percent");
            strikeFlat = f(b, "strikeFlat", 75, 0, 100000, "Damage of one secondary strike");
            strikeCount = i(b, "strikeCount", 44, 0, 400, "Number of secondary strikes");
            strikeRadius = f(b, "strikeRadius", 4, 1, 24, "Radius of one secondary strike");
            maxHitsPerTarget = i(b, "maxHitsPerTarget", 5, 1, 100, "Secondary strikes one target can take");
            finalFlat = f(b, "finalFlat", 320, 0, 100000, "Thunder cataclysm flat damage");
            finalPercent = f(b, "finalPercent", 25, 0, 100, "Thunder cataclysm percent");
            paralysisSlowPercent = f(b, "paralysisSlowPercent", 50, 0, 95, "Black Lightning Paralysis: movement reduction");
            paralysisAtkPercent = f(b, "paralysisAtkPercent", 40, 0, 95, "attack speed reduction");
            paralysisSeconds = f(b, "paralysisSeconds", 5, 0, 600, "duration");
            waterBonusPercent = f(b, "waterBonusPercent", 25, 0, 1000, "Conductive Catastrophe: extra lightning damage in water");
            selfReductionPercent = f(b, "selfReductionPercent", 50, 0, 100, "Storm Sovereignty: caster reduction during the barrage");
            executeThresholdPercent = f(b, "executeThresholdPercent", 30, 0, 100, "Thunder Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 30, 0, 1000, "bonus below it");
            b.pop();
        }
    }

    public static final class Array {
        public final Val<Boolean> requireSky;
        public final Val<Integer> manaCost, thinBeams, maxBeamsPerTarget, execBeams;
        public final Val<Double> cooldownSeconds, range, radius, finalRadius, beamDamage, execFlat, execPercent, finalFlat, finalPercent, armorIgnorePercent, executeThresholdPercent, executeBonusPercent;

        Array(ForgeConfigSpec.Builder b) {
            b.push("heavenly_execution_array");
            manaCost = i(b, "manaCost", 200, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 45, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 110, 4, 220, "Maximum targeting range");
            radius = f(b, "radius", 35, 2, 128, "Primary laser radius");
            finalRadius = f(b, "finalRadius", 50, 2, 160, "Final solar detonation radius");
            requireSky = t(b, "requireSky", true, "The spell needs a clear view of the sky (turn off to cast it anywhere)");
            beamDamage = f(b, "beamDamage", 120, 0, 100000, "Damage of one thin solar beam");
            thinBeams = i(b, "thinBeams", 40, 0, 400, "Number of thin beams");
            maxBeamsPerTarget = i(b, "maxBeamsPerTarget", 6, 1, 100, "Thin beams one target can take");
            execFlat = f(b, "execFlat", 300, 0, 100000, "Concentrated execution beam flat damage");
            execPercent = f(b, "execPercent", 25, 0, 100, "Concentrated beam percent");
            execBeams = i(b, "execBeams", 8, 0, 64, "Number of concentrated beams (each on a different enemy)");
            finalFlat = f(b, "finalFlat", 280, 0, 100000, "Final solar cataclysm flat damage");
            finalPercent = f(b, "finalPercent", 22, 0, 100, "Final cataclysm percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 35, 0, 100, "Solar Penetration: armor ignored by concentrated beams");
            executeThresholdPercent = f(b, "executeThresholdPercent", 25, 0, 100, "Precision Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 35, 0, 1000, "bonus below it");
            b.pop();
        }
    }

    public static final class Law {
        public final Val<Integer> manaCost, charges, reflectMax;
        public final Val<Double> cooldownSeconds, durationSeconds, reductionPercent, reflectDamage, radius, explosionFlat, explosionPercent, storedPer, storedBonus, storedMax, knockBlocks;

        Law(ForgeConfigSpec.Builder b) {
            b.push("absolute_law_of_protection");
            manaCost = i(b, "manaCost", 220, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 55, 0, 100000, "Cooldown in seconds");
            durationSeconds = f(b, "durationSeconds", 12, 1, 600, "Barrier duration");
            reductionPercent = f(b, "reductionPercent", 95, 0, 100, "Damage reduction");
            charges = i(b, "charges", 6, 0, 32, "Attacks the barrier negates completely");
            reflectDamage = f(b, "reflectDamage", 90, 0, 100000, "Projectile Rejection bolt damage");
            reflectMax = i(b, "reflectMax", 12, 0, 64, "Retaliations per cast");
            radius = f(b, "radius", 35, 1, 96, "Absolute Rejection radius");
            explosionFlat = f(b, "explosionFlat", 300, 0, 100000, "Rejection flat damage");
            explosionPercent = f(b, "explosionPercent", 25, 0, 100, "Rejection max-HP percent");
            storedPer = f(b, "storedPer", 100, 1, 100000, "Prevented damage per stored step");
            storedBonus = f(b, "storedBonus", 30, 0, 100000, "Flat damage per step");
            storedMax = f(b, "storedMax", 400, 0, 100000, "Maximum stored bonus");
            knockBlocks = f(b, "knockBlocks", 10, 0, 48, "Rejection knockback");
            b.pop();
        }
    }

    public static final class Fracture {
        public final Val<Integer> manaCost, fractureCount;
        public final Val<Double> cooldownSeconds, range, slashLength, slashWidth, collapseRadius, slashFlat, slashPercent, fractureFlat, collapseFlat, collapsePercent, armorIgnorePercent, reduceIgnorePercent, woundBonusPercent, woundHealPercent, woundSeconds, noTeleportSeconds, executeThresholdPercent, executeBonusPercent;

        Fracture(ForgeConfigSpec.Builder b) {
            b.push("fracture_of_infinite_worlds");
            manaCost = i(b, "manaCost", 250, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 45, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 85, 4, 200, "Maximum targeting range");
            slashLength = f(b, "slashLength", 75, 4, 200, "Primary slash length");
            slashWidth = f(b, "slashWidth", 8, 1, 32, "Primary slash width");
            collapseRadius = f(b, "collapseRadius", 40, 2, 128, "Final collapse radius");
            slashFlat = f(b, "slashFlat", 240, 0, 100000, "Initial dimensional slash flat damage");
            slashPercent = f(b, "slashPercent", 20, 0, 100, "Slash percent");
            fractureFlat = f(b, "fractureFlat", 85, 0, 100000, "Damage of one spatial fracture");
            fractureCount = i(b, "fractureCount", 8, 0, 64, "Number of fractures");
            collapseFlat = f(b, "collapseFlat", 350, 0, 100000, "Final collapse flat damage");
            collapsePercent = f(b, "collapsePercent", 28, 0, 100, "Final collapse percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 40, 0, 100, "Spatial Penetration: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 30, 0, 100, "damage reduction ignored");
            woundBonusPercent = f(b, "woundBonusPercent", 25, 0, 1000, "Dimensional Wounds: extra damage taken");
            woundHealPercent = f(b, "woundHealPercent", 40, 0, 100, "healing reduction");
            woundSeconds = f(b, "woundSeconds", 8, 0, 600, "duration");
            noTeleportSeconds = f(b, "noTeleportSeconds", 4, 0, 600, "Spatial Imprisonment: seconds the enemy cannot teleport");
            executeThresholdPercent = f(b, "executeThresholdPercent", 30, 0, 100, "Dimensional Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 35, 0, 1000, "bonus below it");
            b.pop();
        }
    }

    public static final class Gluttony {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, finalRadius, openFlat, openPercent, dps, finalFlat, finalPercent, consumeThresholdPercent, healPercent, manaPercent, restoreMaxPercent, slowPercent, outPercent, pullStrength, bossPullPercent, fractureVulnPercent, fractureHealPercent, fractureSeconds;

        Gluttony(ForgeConfigSpec.Builder b) {
            b.push("abyss_that_consumes_all");
            manaCost = i(b, "manaCost", 350, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 85, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 90, 4, 200, "Maximum targeting range");
            radius = f(b, "radius", 30, 2, 96, "Initial vortex radius");
            finalRadius = f(b, "finalRadius", 50, 2, 160, "Final consumption radius");
            openFlat = f(b, "openFlat", 220, 0, 100000, "Devouring rupture flat damage");
            openPercent = f(b, "openPercent", 18, 0, 100, "Rupture percent");
            dps = f(b, "dps", 50, 0, 100000, "Continuous soul consumption per second");
            finalFlat = f(b, "finalFlat", 420, 0, 100000, "Final abyssal collapse flat damage");
            finalPercent = f(b, "finalPercent", 35, 0, 100, "Final collapse percent");
            consumeThresholdPercent = f(b, "consumeThresholdPercent", 15, 0, 100, "Soul Devouring: health threshold for being consumed");
            healPercent = f(b, "healPercent", 6, 0, 100, "Absolute Hunger: max health restored per defeated enemy");
            manaPercent = f(b, "manaPercent", 6, 0, 100, "max mana restored per defeated enemy");
            restoreMaxPercent = f(b, "restoreMaxPercent", 36, 0, 100, "maximum restoration per cast");
            slowPercent = f(b, "slowPercent", 75, 0, 95, "Devouring Gravity: movement reduction");
            outPercent = f(b, "outPercent", 40, 0, 100, "outgoing damage reduction");
            pullStrength = f(b, "pullStrength", 0.35, 0, 3, "pull strength");
            bossPullPercent = f(b, "bossPullPercent", 35, 0, 100, "Bosses receive this percent of the pull");
            fractureVulnPercent = f(b, "fractureVulnPercent", 30, 0, 1000, "Soul Fracture: extra damage taken");
            fractureHealPercent = f(b, "fractureHealPercent", 50, 0, 100, "healing reduction");
            fractureSeconds = f(b, "fractureSeconds", 8, 0, 600, "duration");
            b.pop();
        }
    }

    public static final class Nothing {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, supernovaRadius, finalRadius, impactFlat, impactPercent, supernovaFlat, supernovaPercent, finalFlat, finalPercent, armorIgnorePercent, reduceIgnorePercent, fractureVulnPercent, fractureHealPercent, fractureSeconds, gravityRadius, gravityStrength, executeThresholdPercent, executeBonusPercent;

        Nothing(ForgeConfigSpec.Builder b) {
            b.push("birth_of_nothingness");
            manaCost = i(b, "manaCost", 450, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 120, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 110, 4, 220, "Maximum targeting range");
            radius = f(b, "radius", 30, 2, 96, "Void rupture radius");
            supernovaRadius = f(b, "supernovaRadius", 40, 2, 128, "Imaginary supernova radius");
            finalRadius = f(b, "finalRadius", 65, 2, 200, "Final collapse radius");
            impactFlat = f(b, "impactFlat", 250, 0, 100000, "Void rupture flat damage");
            impactPercent = f(b, "impactPercent", 20, 0, 100, "Rupture percent");
            supernovaFlat = f(b, "supernovaFlat", 420, 0, 100000, "Supernova flat damage");
            supernovaPercent = f(b, "supernovaPercent", 35, 0, 100, "Supernova percent");
            finalFlat = f(b, "finalFlat", 350, 0, 100000, "Nihility collapse flat damage");
            finalPercent = f(b, "finalPercent", 30, 0, 100, "Nihility collapse percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 45, 0, 100, "Imaginary Annihilation: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 35, 0, 100, "damage reduction ignored");
            fractureVulnPercent = f(b, "fractureVulnPercent", 30, 0, 1000, "Nihility Fracture: extra damage taken");
            fractureHealPercent = f(b, "fractureHealPercent", 50, 0, 100, "healing reduction");
            fractureSeconds = f(b, "fractureSeconds", 10, 0, 600, "duration");
            gravityRadius = f(b, "gravityRadius", 45, 1, 128, "Void Gravity radius");
            gravityStrength = f(b, "gravityStrength", 0.3, 0, 3, "Void Gravity pull");
            executeThresholdPercent = f(b, "executeThresholdPercent", 30, 0, 100, "Imaginary Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 40, 0, 1000, "bonus below it");
            b.pop();
        }
    }

    public static final class Azathoth {
        public final Val<Integer> manaCost, endKills;
        public final Val<Double> cooldownSeconds, domainRadius, radius, finalRadius, firstFlat, firstPercent, destroyFlat, destroyPercent, soulFlat, soulPercent, finalFlat, finalPercent, armorIgnorePercent, reduceIgnorePercent, bossBonusPercent, markBonusPercent, markHealPercent, consumeThresholdPercent, blessSeconds, blessDamagePercent, blessReductionPercent, blessSpeedPercent, endHealPercent, endExtraSeconds, protectReductionPercent, protectSeconds, pullStrength;

        Azathoth(ForgeConfigSpec.Builder b) {
            b.push("end_of_all_creation");
            manaCost = i(b, "manaCost", 900, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 360, 0, 100000, "Cooldown in seconds");
            domainRadius = f(b, "domainRadius", 80, 2, 200, "Void dimension radius and soul pull radius");
            radius = f(b, "radius", 55, 2, 160, "Primary annihilation radius");
            finalRadius = f(b, "finalRadius", 100, 2, 240, "Final cataclysm radius");
            firstFlat = f(b, "firstFlat", 350, 0, 100000, "First judgment flat damage");
            firstPercent = f(b, "firstPercent", 30, 0, 100, "First judgment percent");
            destroyFlat = f(b, "destroyFlat", 500, 0, 100000, "Imaginary destruction flat damage");
            destroyPercent = f(b, "destroyPercent", 40, 0, 100, "Imaginary destruction percent");
            soulFlat = f(b, "soulFlat", 400, 0, 100000, "Soul consumption flat damage");
            soulPercent = f(b, "soulPercent", 35, 0, 100, "Soul consumption percent");
            finalFlat = f(b, "finalFlat", 750, 0, 100000, "Final annihilation flat damage");
            finalPercent = f(b, "finalPercent", 60, 0, 100, "Final annihilation percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 55, 0, 100, "Absolute Authority: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 45, 0, 100, "damage reduction ignored");
            bossBonusPercent = f(b, "bossBonusPercent", 40, 0, 1000, "final annihilation bonus against bosses");
            markBonusPercent = f(b, "markBonusPercent", 30, 0, 1000, "Void Extinction Mark: extra damage taken");
            markHealPercent = f(b, "markHealPercent", 60, 0, 100, "healing reduction");
            consumeThresholdPercent = f(b, "consumeThresholdPercent", 20, 0, 100, "Absolute Soul Consumption threshold");
            blessSeconds = f(b, "blessSeconds", 25, 0, 600, "Chaos Creator's Blessing duration");
            blessDamagePercent = f(b, "blessDamagePercent", 60, 0, 1000, "damage bonus");
            blessReductionPercent = f(b, "blessReductionPercent", 65, 0, 100, "damage reduction");
            blessSpeedPercent = f(b, "blessSpeedPercent", 35, 0, 500, "movement bonus");
            endKills = i(b, "endKills", 5, 1, 64, "The End of Creation: defeated enemies needed");
            endHealPercent = f(b, "endHealPercent", 50, 0, 100, "heal as percent of max health");
            endExtraSeconds = f(b, "endExtraSeconds", 10, 0, 600, "extra blessing seconds");
            protectReductionPercent = f(b, "protectReductionPercent", 95, 0, 100, "Absolute Protection: reduction in the last seconds");
            protectSeconds = f(b, "protectSeconds", 12, 1, 22, "Absolute Protection: length");
            pullStrength = f(b, "pullStrength", 0.3, 0, 3, "Soul pull");
            b.pop();
        }
    }
}
