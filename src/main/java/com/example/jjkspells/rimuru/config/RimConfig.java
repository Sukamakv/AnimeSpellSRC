package com.example.jjkspells.rimuru.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/jjk_spells-rimuru.toml (created on first launch; restart the game/world to apply changes). */
public final class RimConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Predator PREDATOR;
    public static final Lightning LIGHTNING;
    public static final Megiddo MEGIDDO;
    public static final Defense DEFENSE;
    public static final Spatial SPATIAL;
    public static final Beelzebuth BEELZEBUTH;
    public static final Collapse COLLAPSE;
    public static final Azathoth AZATHOTH;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        PREDATOR = new Predator(b);
        LIGHTNING = new Lightning(b);
        MEGIDDO = new Megiddo(b);
        DEFENSE = new Defense(b);
        SPATIAL = new Spatial(b);
        BEELZEBUTH = new Beelzebuth(b);
        COLLAPSE = new Collapse(b);
        AZATHOTH = new Azathoth(b);
        SPEC = b.build();
    }

    private RimConfig() {
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
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by the Rimuru spells");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the server sends (lower it on weak machines)");
            bossStunSlowPercent = f(b, "bossSlowPercent", 80, 0, 100, "Bosses are never frozen; they lose this percent of their speed instead");
            manaRestoreMultiplier = f(b, "manaRestoreMultiplier", 1.0, 0, 100, "Multiplier for all mana the Rimuru spells give back");
            b.pop();
        }
    }

    public static final class Predator {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, initialFlat, initialPercent, dps, finalFlat, finalPercent, executeThresholdPercent, bossBonus, healPerEnemy, manaPerEnemy, healCap, adaptReductionPercent, adaptBonusPercent, adaptSeconds, bossSlowPercent, pullStrength;

        Predator(ForgeConfigSpec.Builder b) {
            b.push("predator");
            manaCost = i(b, "manaCost", 70, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 18, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 12, 2, 64, "Maximum targeting range");
            radius = f(b, "radius", 5, 1, 24, "Absorption radius around the target");
            initialFlat = f(b, "initialFlat", 55, 0, 100000, "Capture flat damage");
            initialPercent = f(b, "initialPercent", 6, 0, 100, "Capture max-HP percent");
            dps = f(b, "dps", 20, 0, 100000, "Continuous devouring damage per second");
            finalFlat = f(b, "finalFlat", 80, 0, 100000, "Final consumption flat damage");
            finalPercent = f(b, "finalPercent", 8, 0, 100, "Final consumption percent");
            executeThresholdPercent = f(b, "executeThresholdPercent", 15, 0, 100, "Predatory Execution: health percent below which enemies are consumed");
            bossBonus = f(b, "bossBonus", 60, 0, 100000, "Extra damage dealt to bosses instead of an execution");
            healPerEnemy = f(b, "healPerEnemy", 5, 0, 100000, "Energy Absorption: health per enemy");
            manaPerEnemy = f(b, "manaPerEnemy", 10, 0, 100000, "Energy Absorption: mana per enemy");
            healCap = f(b, "healCap", 40, 0, 100000, "Maximum health restored per cast");
            adaptReductionPercent = f(b, "adaptReductionPercent", 15, 0, 100, "Adaptive Consumption: damage reduction after a kill");
            adaptBonusPercent = f(b, "adaptBonusPercent", 10, 0, 1000, "spell damage bonus");
            adaptSeconds = f(b, "adaptSeconds", 5, 0, 600, "duration");
            bossSlowPercent = f(b, "bossSlowPercent", 60, 0, 95, "Devouring Resistance: movement reduction of bosses");
            pullStrength = f(b, "pullStrength", 0.35, 0, 3, "How hard captured enemies are pulled in");
            b.pop();
        }
    }

    public static final class Lightning {
        public final Val<Integer> manaCost, chainMax, waterChainMax, overloadCount;
        public final Val<Double> cooldownSeconds, range, chainRadius, primaryFlat, primaryPercent, chainFlat, blastRadius, blastFlat, blastPercent, paralysisSlowPercent, paralysisAtkPercent, paralysisSeconds, waterChainRadius, waterBonusPercent, overloadBonusPercent, overloadSpeedPercent, overloadSpeedSeconds;

        Lightning(ForgeConfigSpec.Builder b) {
            b.push("black_lightning");
            manaCost = i(b, "manaCost", 60, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 16, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 35, 4, 128, "Maximum targeting range");
            chainRadius = f(b, "chainRadius", 10, 1, 48, "Chain radius");
            primaryFlat = f(b, "primaryFlat", 85, 0, 100000, "Primary bolt flat damage");
            primaryPercent = f(b, "primaryPercent", 8, 0, 100, "Primary bolt max-HP percent");
            chainFlat = f(b, "chainFlat", 45, 0, 100000, "Chain damage");
            chainMax = i(b, "chainMax", 6, 0, 64, "Maximum chained enemies");
            blastRadius = f(b, "blastRadius", 6, 1, 24, "Storm detonation radius");
            blastFlat = f(b, "blastFlat", 65, 0, 100000, "Detonation flat damage");
            blastPercent = f(b, "blastPercent", 6, 0, 100, "Detonation percent");
            paralysisSlowPercent = f(b, "paralysisSlowPercent", 40, 0, 95, "Electrical Paralysis: movement reduction");
            paralysisAtkPercent = f(b, "paralysisAtkPercent", 25, 0, 95, "attack speed reduction");
            paralysisSeconds = f(b, "paralysisSeconds", 3, 0, 600, "duration");
            waterChainRadius = f(b, "waterChainRadius", 15, 1, 64, "Storm Conduction: chain radius when the target stands in water");
            waterBonusPercent = f(b, "waterBonusPercent", 25, 0, 1000, "chain damage bonus");
            waterChainMax = i(b, "waterChainMax", 10, 0, 64, "maximum chained enemies");
            overloadCount = i(b, "overloadCount", 5, 1, 64, "Lightning Overload: enemies that must be struck");
            overloadBonusPercent = f(b, "overloadBonusPercent", 30, 0, 1000, "detonation bonus");
            overloadSpeedPercent = f(b, "overloadSpeedPercent", 20, 0, 500, "caster movement bonus");
            overloadSpeedSeconds = f(b, "overloadSpeedSeconds", 4, 0, 600, "duration");
            b.pop();
        }
    }

    public static final class Megiddo {
        public final Val<Integer> manaCost, beamCount;
        public final Val<Double> cooldownSeconds, range, daylightRange, radius, beamFlat, focusFlat, focusPercent, finalFlat, finalPercent, executeThresholdPercent, executeBonusPercent, daylightBonusPercent;

        Megiddo(ForgeConfigSpec.Builder b) {
            b.push("megiddo");
            manaCost = i(b, "manaCost", 90, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 35, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 50, 4, 128, "Maximum targeting range");
            daylightRange = f(b, "daylightRange", 60, 4, 128, "Range in clear daylight outdoors");
            radius = f(b, "radius", 20, 2, 64, "Attack radius");
            beamCount = i(b, "beamCount", 12, 1, 64, "Number of precise beams of the first barrage");
            beamFlat = f(b, "beamFlat", 55, 0, 100000, "Damage of one beam");
            focusFlat = f(b, "focusFlat", 110, 0, 100000, "Focused laser flat damage");
            focusPercent = f(b, "focusPercent", 10, 0, 100, "Focused laser percent");
            finalFlat = f(b, "finalFlat", 90, 0, 100000, "Final rain flat damage");
            finalPercent = f(b, "finalPercent", 8, 0, 100, "Final rain percent");
            executeThresholdPercent = f(b, "executeThresholdPercent", 20, 0, 100, "Precision Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 35, 0, 1000, "focused laser bonus below it");
            daylightBonusPercent = f(b, "daylightBonusPercent", 20, 0, 1000, "Atmospheric Requirement: damage bonus in clear daylight");
            b.pop();
        }
    }

    public static final class Defense {
        public final Val<Integer> manaCost, charges, reflectMax;
        public final Val<Double> cooldownSeconds, durationSeconds, reductionPercent, reflectDamage, radius, explosionFlat, explosionPercent, storedPer, storedBonus, storedMax, knockBlocks;

        Defense(ForgeConfigSpec.Builder b) {
            b.push("absolute_defense");
            manaCost = i(b, "manaCost", 100, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 40, 0, 100000, "Cooldown in seconds");
            durationSeconds = f(b, "durationSeconds", 8, 1, 600, "Barrier duration");
            reductionPercent = f(b, "reductionPercent", 90, 0, 100, "Damage reduction");
            charges = i(b, "charges", 3, 0, 32, "Attacks the barrier negates completely");
            reflectDamage = f(b, "reflectDamage", 30, 0, 100000, "Spatial Reflection bolt damage");
            reflectMax = i(b, "reflectMax", 8, 0, 64, "Reflections per cast");
            radius = f(b, "radius", 12, 1, 48, "Covenant Release radius");
            explosionFlat = f(b, "explosionFlat", 100, 0, 100000, "Release flat damage");
            explosionPercent = f(b, "explosionPercent", 8, 0, 100, "Release max-HP percent");
            storedPer = f(b, "storedPer", 50, 1, 100000, "Prevented damage per stored step");
            storedBonus = f(b, "storedBonus", 10, 0, 100000, "Flat damage per step");
            storedMax = f(b, "storedMax", 150, 0, 100000, "Maximum stored bonus");
            knockBlocks = f(b, "knockBlocks", 6, 0, 32, "Release knockback");
            b.pop();
        }
    }

    public static final class Spatial {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, slashRange, slashFlat, slashPercent, cutFlat, cutRadius, ruptureRadius, perfectRuptureRadius, ruptureFlat, rupturePercent, armorIgnorePercent, lockSlowPercent, lockSeconds, perfectBonusPercent;

        Spatial(ForgeConfigSpec.Builder b) {
            b.push("spatial_domination");
            manaCost = i(b, "manaCost", 90, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 25, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 35, 4, 128, "Maximum targeting range");
            slashRange = f(b, "slashRange", 18, 2, 64, "Reach of the severance slash");
            slashFlat = f(b, "slashFlat", 80, 0, 100000, "Severance slash flat damage");
            slashPercent = f(b, "slashPercent", 8, 0, 100, "Severance slash percent");
            cutFlat = f(b, "cutFlat", 45, 0, 100000, "Damage of one dimensional cut");
            cutRadius = f(b, "cutRadius", 3, 0.5, 12, "Reach of one cut around the target");
            ruptureRadius = f(b, "ruptureRadius", 8, 1, 32, "Dimensional rupture radius");
            perfectRuptureRadius = f(b, "perfectRuptureRadius", 10, 1, 32, "Rupture radius with Perfect Positioning");
            ruptureFlat = f(b, "ruptureFlat", 100, 0, 100000, "Rupture flat damage");
            rupturePercent = f(b, "rupturePercent", 10, 0, 100, "Rupture percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 25, 0, 100, "Dimensional Bypass: armor ignored");
            lockSlowPercent = f(b, "lockSlowPercent", 40, 0, 95, "Spatial Lock: movement reduction");
            lockSeconds = f(b, "lockSeconds", 3, 0, 600, "duration");
            perfectBonusPercent = f(b, "perfectBonusPercent", 25, 0, 1000, "Perfect Positioning: slash bonus");
            b.pop();
        }
    }

    public static final class Beelzebuth {
        public final Val<Integer> manaCost, harvestKills;
        public final Val<Double> cooldownSeconds, range, radius, initialFlat, initialPercent, dps, finalFlat, finalPercent, executeThresholdPercent, bossBonus, healPerEnemy, manaPerEnemy, healCap, suppressSlowPercent, suppressOutPercent, pullStrength, harvestBonusPercent, harvestSeconds, harvestHeal;

        Beelzebuth(ForgeConfigSpec.Builder b) {
            b.push("beelzebuth");
            manaCost = i(b, "manaCost", 150, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 55, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 35, 4, 128, "Maximum targeting range");
            radius = f(b, "radius", 15, 2, 48, "Devouring radius");
            initialFlat = f(b, "initialFlat", 90, 0, 100000, "Soul capture flat damage");
            initialPercent = f(b, "initialPercent", 8, 0, 100, "Soul capture percent");
            dps = f(b, "dps", 25, 0, 100000, "Continuous soul damage per second");
            finalFlat = f(b, "finalFlat", 130, 0, 100000, "Soul collapse flat damage");
            finalPercent = f(b, "finalPercent", 12, 0, 100, "Soul collapse percent");
            executeThresholdPercent = f(b, "executeThresholdPercent", 15, 0, 100, "Soul Devouring threshold");
            bossBonus = f(b, "bossBonus", 80, 0, 100000, "Extra soul damage to bosses");
            healPerEnemy = f(b, "healPerEnemy", 5, 0, 100000, "Magical Consumption: health per enemy");
            manaPerEnemy = f(b, "manaPerEnemy", 10, 0, 100000, "mana per enemy");
            healCap = f(b, "healCap", 50, 0, 100000, "maximum health restored");
            suppressSlowPercent = f(b, "suppressSlowPercent", 50, 0, 95, "Gluttonous Suppression: movement reduction");
            suppressOutPercent = f(b, "suppressOutPercent", 25, 0, 100, "outgoing spell damage reduction");
            pullStrength = f(b, "pullStrength", 0.3, 0, 3, "Inward pulling force");
            harvestKills = i(b, "harvestKills", 5, 1, 64, "Soul Harvest: kills needed");
            harvestBonusPercent = f(b, "harvestBonusPercent", 25, 0, 1000, "spell damage bonus");
            harvestSeconds = f(b, "harvestSeconds", 10, 0, 600, "duration");
            harvestHeal = f(b, "harvestHeal", 20, 0, 100000, "extra heal");
            b.pop();
        }
    }

    public static final class Collapse {
        public final Val<Integer> manaCost, overloadCount;
        public final Val<Double> cooldownSeconds, range, radius, ruptureRadius, overloadRuptureRadius, impactFlat, impactPercent, blastFlat, blastPercent, ruptureFlat, rupturePercent, armorIgnorePercent, reduceIgnorePercent, bossBonusPercent, slowPercent, slowSeconds, outPercent, outSeconds, overloadBonusPercent, overloadSpellPercent, overloadSpellSeconds, pullStrength;

        Collapse(ForgeConfigSpec.Builder b) {
            b.push("imaginary_collapse");
            manaCost = i(b, "manaCost", 200, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 75, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 45, 4, 128, "Maximum targeting range");
            radius = f(b, "radius", 14, 2, 48, "Primary explosion radius");
            ruptureRadius = f(b, "ruptureRadius", 22, 2, 64, "Rupture and gravity radius");
            overloadRuptureRadius = f(b, "overloadRuptureRadius", 26, 2, 64, "Rupture radius with Nihility Overload");
            impactFlat = f(b, "impactFlat", 140, 0, 100000, "Initial impact flat damage");
            impactPercent = f(b, "impactPercent", 12, 0, 100, "Initial impact percent");
            blastFlat = f(b, "blastFlat", 180, 0, 100000, "Imaginary explosion flat damage");
            blastPercent = f(b, "blastPercent", 15, 0, 100, "Imaginary explosion percent");
            ruptureFlat = f(b, "ruptureFlat", 120, 0, 100000, "Nihility rupture flat damage");
            rupturePercent = f(b, "rupturePercent", 10, 0, 100, "Nihility rupture percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 30, 0, 100, "Imaginary Energy: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 20, 0, 100, "damage reduction ignored");
            bossBonusPercent = f(b, "bossBonusPercent", 20, 0, 1000, "final rupture bonus against bosses");
            slowPercent = f(b, "slowPercent", 40, 0, 95, "Void Instability: movement reduction");
            slowSeconds = f(b, "slowSeconds", 4, 0, 600, "duration");
            outPercent = f(b, "outPercent", 20, 0, 100, "outgoing damage reduction");
            outSeconds = f(b, "outSeconds", 4, 0, 600, "duration");
            overloadCount = i(b, "overloadCount", 5, 1, 64, "Nihility Overload: enemies the first explosion must hit");
            overloadBonusPercent = f(b, "overloadBonusPercent", 25, 0, 1000, "second explosion bonus");
            overloadSpellPercent = f(b, "overloadSpellPercent", 20, 0, 1000, "caster spell damage bonus");
            overloadSpellSeconds = f(b, "overloadSpellSeconds", 8, 0, 600, "duration");
            pullStrength = f(b, "pullStrength", 0.25, 0, 3, "Gravitational pull");
            b.pop();
        }
    }

    public static final class Azathoth {
        public final Val<Integer> manaCost, supremacyKills;
        public final Val<Double> cooldownSeconds, range, radius, voidRadius, finalRadius, centralRadius, centralBonusPercent, beamFlat, beamPercent, beamRadius, collapseFlat, collapsePercent, soulFlat, soulPercent, finalFlat, finalPercent, armorIgnorePercent, reduceIgnorePercent, bossBonusPercent, spaceSlowPercent, spaceOutPercent, pullStrength, executeThresholdPercent, bossSoul, blessSeconds, blessReductionPercent, blessSpellPercent, blessSpeedPercent, supremacyHealPercent, supremacyExtraSeconds, protectReductionPercent;

        Azathoth(ForgeConfigSpec.Builder b) {
            b.push("void_god_azathoth");
            manaCost = i(b, "manaCost", 400, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 300, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 80, 4, 160, "Maximum targeting range");
            radius = f(b, "radius", 25, 2, 64, "Collapse radius");
            voidRadius = f(b, "voidRadius", 40, 2, 96, "Soul devouring radius");
            finalRadius = f(b, "finalRadius", 30, 2, 96, "End of Creation radius");
            centralRadius = f(b, "centralRadius", 8, 1, 32, "Central void radius");
            centralBonusPercent = f(b, "centralBonusPercent", 25, 0, 1000, "collapse bonus in the centre");
            beamFlat = f(b, "beamFlat", 180, 0, 100000, "Reality rupture flat damage");
            beamPercent = f(b, "beamPercent", 15, 0, 100, "Reality rupture percent");
            beamRadius = f(b, "beamRadius", 3, 0.5, 16, "Width of the rupture beam");
            collapseFlat = f(b, "collapseFlat", 300, 0, 100000, "Imaginary collapse flat damage");
            collapsePercent = f(b, "collapsePercent", 25, 0, 100, "Imaginary collapse percent");
            soulFlat = f(b, "soulFlat", 180, 0, 100000, "Soul wave flat damage");
            soulPercent = f(b, "soulPercent", 15, 0, 100, "Soul wave percent");
            finalFlat = f(b, "finalFlat", 350, 0, 100000, "Void annihilation flat damage");
            finalPercent = f(b, "finalPercent", 30, 0, 100, "Void annihilation percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 35, 0, 100, "Void God Authority: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 25, 0, 100, "damage reduction ignored");
            bossBonusPercent = f(b, "bossBonusPercent", 25, 0, 1000, "bonus damage against bosses");
            spaceSlowPercent = f(b, "spaceSlowPercent", 50, 0, 95, "Imaginary Space: movement reduction");
            spaceOutPercent = f(b, "spaceOutPercent", 30, 0, 100, "outgoing damage reduction");
            pullStrength = f(b, "pullStrength", 0.3, 0, 3, "Inward pulling force");
            executeThresholdPercent = f(b, "executeThresholdPercent", 10, 0, 100, "Soul Consumption threshold");
            bossSoul = f(b, "bossSoul", 100, 0, 100000, "extra soul damage to bosses");
            blessSeconds = f(b, "blessSeconds", 15, 0, 600, "Chaos Creator Blessing duration");
            blessReductionPercent = f(b, "blessReductionPercent", 40, 0, 100, "damage reduction");
            blessSpellPercent = f(b, "blessSpellPercent", 30, 0, 1000, "spell damage bonus");
            blessSpeedPercent = f(b, "blessSpeedPercent", 20, 0, 500, "movement bonus");
            supremacyKills = i(b, "supremacyKills", 5, 1, 64, "Absolute Supremacy: kills needed");
            supremacyHealPercent = f(b, "supremacyHealPercent", 30, 0, 100, "heal as percent of max health");
            supremacyExtraSeconds = f(b, "supremacyExtraSeconds", 5, 0, 600, "extra blessing seconds");
            protectReductionPercent = f(b, "protectReductionPercent", 90, 0, 100, "Void Protection: damage reduction in the last 7 seconds");
            b.pop();
        }
    }
}
