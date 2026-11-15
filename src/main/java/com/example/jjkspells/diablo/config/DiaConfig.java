package com.example.jjkspells.diablo.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/jjk_spells-diablo.toml (created on first launch; restart the game/world to apply changes). */
public final class DiaConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Nether NETHER;
    public static final Dominion DOMINION;
    public static final Funeral FUNERAL;
    public static final Negation NEGATION;
    public static final Despair DESPAIR;
    public static final World WORLD;
    public static final Genesis GENESIS;
    public static final Azazel AZAZEL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        NETHER = new Nether(b);
        DOMINION = new Dominion(b);
        FUNERAL = new Funeral(b);
        NEGATION = new Negation(b);
        DESPAIR = new Despair(b);
        WORLD = new World(b);
        GENESIS = new Genesis(b);
        AZAZEL = new Azazel(b);
        SPEC = b.build();
    }

    private DiaConfig() {
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
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by the Diablo spells");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the server sends (lower it on weak machines)");
            bossStunSlowPercent = f(b, "bossSlowPercent", 80, 0, 100, "Bosses are never frozen; they lose this percent of their speed instead");
            manaRestoreMultiplier = f(b, "manaRestoreMultiplier", 1.0, 0, 100, "Multiplier for all mana the Diablo spells give back");
            b.pop();
        }
    }

    public static final class Nether {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, superRadius, impactFlat, impactPercent, supernovaFlat, supernovaPercent, finalFlat, finalPercent, hellfireDps, hellfireSeconds, hellfireHealCutPercent, ignitionBonusPercent, gravityRadius, gravityStrength, immunityReductionPercent;

        Nether(ForgeConfigSpec.Builder b) {
            b.push("nether_apocalypse");
            manaCost = i(b, "manaCost", 120, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 30, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 80, 4, 160, "Maximum targeting range");
            radius = f(b, "radius", 24, 2, 96, "Black sun impact radius");
            superRadius = f(b, "superRadius", 38, 2, 128, "Infernal supernova and final wave radius");
            impactFlat = f(b, "impactFlat", 180, 0, 100000, "Black sun impact flat damage");
            impactPercent = f(b, "impactPercent", 15, 0, 100, "Black sun impact max-HP percent");
            supernovaFlat = f(b, "supernovaFlat", 260, 0, 100000, "Infernal supernova flat damage");
            supernovaPercent = f(b, "supernovaPercent", 20, 0, 100, "Infernal supernova percent");
            finalFlat = f(b, "finalFlat", 150, 0, 100000, "Hell's Final Breath flat damage");
            finalPercent = f(b, "finalPercent", 12, 0, 100, "Hell's Final Breath percent");
            hellfireDps = f(b, "hellfireDps", 25, 0, 100000, "Eternal Hellfire damage per second");
            hellfireSeconds = f(b, "hellfireSeconds", 10, 0, 600, "Eternal Hellfire duration");
            hellfireHealCutPercent = f(b, "hellfireHealCutPercent", 50, 0, 100, "Eternal Hellfire: healing reduction");
            ignitionBonusPercent = f(b, "ignitionBonusPercent", 30, 0, 1000, "Catastrophic Ignition: final wave bonus against burning enemies");
            gravityRadius = f(b, "gravityRadius", 20, 1, 96, "Infernal Gravity radius");
            gravityStrength = f(b, "gravityStrength", 0.25, 0, 3, "Infernal Gravity pull");
            immunityReductionPercent = f(b, "immunityReductionPercent", 80, 0, 100, "Primordial Immunity: caster damage reduction during the snap and impact");
            b.pop();
        }
    }

    public static final class Dominion {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, radius, shockFlat, shockPercent, pressureDps, finalFlat, finalPercent, executeThresholdPercent, executeBonusPercent, terrorSlowPercent, terrorSeconds, suppressSlowPercent, suppressAtkPercent, suppressOutPercent, suppressHealPercent, selfReductionPercent;

        Dominion(ForgeConfigSpec.Builder b) {
            b.push("primordial_dominion");
            manaCost = i(b, "manaCost", 150, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 35, 0, 100000, "Cooldown in seconds");
            radius = f(b, "radius", 45, 2, 128, "Effect radius");
            shockFlat = f(b, "shockFlat", 140, 0, 100000, "Initial aura shockwave flat damage");
            shockPercent = f(b, "shockPercent", 12, 0, 100, "Initial shockwave percent");
            pressureDps = f(b, "pressureDps", 35, 0, 100000, "Crushing pressure damage per second");
            finalFlat = f(b, "finalFlat", 250, 0, 100000, "Final judgment flat damage");
            finalPercent = f(b, "finalPercent", 20, 0, 100, "Final judgment percent");
            executeThresholdPercent = f(b, "executeThresholdPercent", 40, 0, 100, "Crushing Authority: health threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 30, 0, 1000, "Crushing Authority: bonus below the threshold");
            terrorSlowPercent = f(b, "terrorSlowPercent", 50, 0, 95, "Primordial Terror: movement reduction");
            terrorSeconds = f(b, "terrorSeconds", 4, 0, 600, "Primordial Terror: seconds mobs cannot target the caster");
            suppressSlowPercent = f(b, "suppressSlowPercent", 65, 0, 95, "Absolute Suppression: movement reduction");
            suppressAtkPercent = f(b, "suppressAtkPercent", 50, 0, 95, "attack speed reduction");
            suppressOutPercent = f(b, "suppressOutPercent", 40, 0, 100, "outgoing damage reduction");
            suppressHealPercent = f(b, "suppressHealPercent", 50, 0, 100, "healing reduction");
            selfReductionPercent = f(b, "selfReductionPercent", 75, 0, 100, "Primordial Superiority: caster damage reduction");
            b.pop();
        }
    }

    public static final class Funeral {
        public final Val<Integer> manaCost, fractureCount;
        public final Val<Double> cooldownSeconds, range, slashLength, slashWidth, collapseRadius, slashFlat, slashPercent, fractureFlat, collapseFlat, collapsePercent, armorIgnorePercent, reduceIgnorePercent, woundBonusPercent, woundHealPercent, woundSeconds, executeThresholdPercent, executeBonusPercent;

        Funeral(ForgeConfigSpec.Builder b) {
            b.push("dimensional_funeral");
            manaCost = i(b, "manaCost", 160, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 40, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 65, 4, 160, "Maximum targeting range");
            slashLength = f(b, "slashLength", 60, 4, 160, "Primary slash length");
            slashWidth = f(b, "slashWidth", 8, 1, 32, "Primary slash width");
            collapseRadius = f(b, "collapseRadius", 30, 2, 96, "Final collapse radius");
            slashFlat = f(b, "slashFlat", 220, 0, 100000, "Reality severance flat damage");
            slashPercent = f(b, "slashPercent", 18, 0, 100, "Reality severance percent");
            fractureFlat = f(b, "fractureFlat", 75, 0, 100000, "Damage of one dimensional fracture");
            fractureCount = i(b, "fractureCount", 8, 0, 64, "Number of fractures");
            collapseFlat = f(b, "collapseFlat", 280, 0, 100000, "Final collapse flat damage");
            collapsePercent = f(b, "collapsePercent", 22, 0, 100, "Final collapse percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 40, 0, 100, "Spatial Annihilation: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 30, 0, 100, "damage reduction ignored");
            woundBonusPercent = f(b, "woundBonusPercent", 25, 0, 1000, "Dimensional Wounds: extra damage taken");
            woundHealPercent = f(b, "woundHealPercent", 40, 0, 100, "healing reduction");
            woundSeconds = f(b, "woundSeconds", 8, 0, 600, "duration");
            executeThresholdPercent = f(b, "executeThresholdPercent", 30, 0, 100, "Spatial Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 35, 0, 1000, "final collapse bonus below it");
            b.pop();
        }
    }

    public static final class Negation {
        public final Val<Integer> manaCost, charges, reflectMax;
        public final Val<Double> cooldownSeconds, durationSeconds, reductionPercent, reflectDamage, radius, explosionFlat, explosionPercent, storedPer, storedBonus, storedMax, knockBlocks;

        Negation(ForgeConfigSpec.Builder b) {
            b.push("absolute_negation");
            manaCost = i(b, "manaCost", 200, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 55, 0, 100000, "Cooldown in seconds");
            durationSeconds = f(b, "durationSeconds", 10, 1, 600, "Barrier duration");
            reductionPercent = f(b, "reductionPercent", 95, 0, 100, "Damage reduction");
            charges = i(b, "charges", 5, 0, 32, "Attacks the barrier negates completely");
            reflectDamage = f(b, "reflectDamage", 80, 0, 100000, "Magical Rejection bolt damage");
            reflectMax = i(b, "reflectMax", 10, 0, 64, "Retaliations per cast");
            radius = f(b, "radius", 30, 1, 96, "Rejection Release radius");
            explosionFlat = f(b, "explosionFlat", 250, 0, 100000, "Release flat damage");
            explosionPercent = f(b, "explosionPercent", 20, 0, 100, "Release max-HP percent");
            storedPer = f(b, "storedPer", 100, 1, 100000, "Prevented damage per stored step");
            storedBonus = f(b, "storedBonus", 25, 0, 100000, "Flat damage per step");
            storedMax = f(b, "storedMax", 300, 0, 100000, "Maximum stored bonus");
            knockBlocks = f(b, "knockBlocks", 8, 0, 48, "Release knockback");
            b.pop();
        }
    }

    public static final class Despair {
        public final Val<Integer> manaCost, stackMax;
        public final Val<Double> cooldownSeconds, range, radius, openFlat, openPercent, tormentDps, finalFlat, finalPercent, slowPercent, atkPercent, outPercent, bossFactorPercent, stackBonusPercent, fractureVulnPercent, fractureHealPercent, fractureSeconds;

        Despair(ForgeConfigSpec.Builder b) {
            b.push("despair_time");
            manaCost = i(b, "manaCost", 300, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 65, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 60, 4, 160, "Maximum targeting range");
            radius = f(b, "radius", 30, 2, 96, "Domain radius");
            openFlat = f(b, "openFlat", 150, 0, 100000, "Temporal rupture flat damage");
            openPercent = f(b, "openPercent", 12, 0, 100, "Temporal rupture percent");
            tormentDps = f(b, "tormentDps", 40, 0, 100000, "Soul Torment damage per second");
            finalFlat = f(b, "finalFlat", 320, 0, 100000, "Eternity collapse flat damage");
            finalPercent = f(b, "finalPercent", 25, 0, 100, "Eternity collapse percent");
            slowPercent = f(b, "slowPercent", 85, 0, 95, "Temporal Imprisonment: movement reduction");
            atkPercent = f(b, "atkPercent", 70, 0, 95, "attack speed reduction");
            outPercent = f(b, "outPercent", 50, 0, 100, "outgoing damage reduction");
            bossFactorPercent = f(b, "bossFactorPercent", 50, 0, 100, "Bosses receive this percent of the suppression");
            stackBonusPercent = f(b, "stackBonusPercent", 6, 0, 1000, "Despair Accumulation: final damage bonus per second inside");
            stackMax = i(b, "stackMax", 6, 0, 60, "maximum stacks");
            fractureVulnPercent = f(b, "fractureVulnPercent", 30, 0, 1000, "Soul Fracture: extra damage taken");
            fractureHealPercent = f(b, "fractureHealPercent", 50, 0, 100, "healing reduction");
            fractureSeconds = f(b, "fractureSeconds", 8, 0, 600, "duration");
            b.pop();
        }
    }

    public static final class World {
        public final Val<Integer> manaCost, spearCount;
        public final Val<Double> cooldownSeconds, range, radius, openFlat, openPercent, spearFlat, finalFlat, finalPercent, executeThresholdPercent, executeBonusPercent, slowPercent, outPercent, healCutPercent, vulnPercent, selfSpellPercent, selfReductionPercent;

        World(ForgeConfigSpec.Builder b) {
            b.push("temptation_world");
            manaCost = i(b, "manaCost", 400, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 90, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 70, 4, 160, "Maximum targeting range");
            radius = f(b, "radius", 40, 2, 128, "Domain radius");
            openFlat = f(b, "openFlat", 180, 0, 100000, "Domain manifestation flat damage");
            openPercent = f(b, "openPercent", 15, 0, 100, "Domain manifestation percent");
            spearCount = i(b, "spearCount", 12, 0, 64, "Demonic Judgment spears");
            spearFlat = f(b, "spearFlat", 90, 0, 100000, "Damage per spear");
            finalFlat = f(b, "finalFlat", 350, 0, 100000, "World collapse flat damage");
            finalPercent = f(b, "finalPercent", 28, 0, 100, "World collapse percent");
            executeThresholdPercent = f(b, "executeThresholdPercent", 25, 0, 100, "Demonic Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 40, 0, 1000, "final bonus below it");
            slowPercent = f(b, "slowPercent", 60, 0, 95, "Absolute Dominion: movement reduction");
            outPercent = f(b, "outPercent", 40, 0, 100, "outgoing damage reduction");
            healCutPercent = f(b, "healCutPercent", 50, 0, 100, "healing reduction");
            vulnPercent = f(b, "vulnPercent", 30, 0, 1000, "extra damage taken");
            selfSpellPercent = f(b, "selfSpellPercent", 40, 0, 1000, "The King's Authority: caster spell damage inside");
            selfReductionPercent = f(b, "selfReductionPercent", 60, 0, 100, "caster damage reduction inside");
            b.pop();
        }
    }

    public static final class Genesis {
        public final Val<Integer> manaCost, catastropheCount;
        public final Val<Double> cooldownSeconds, range, radius, collapseRadius, bigCollapseRadius, starFlat, starPercent, starImpactRadius, supernovaFlat, supernovaPercent, finalFlat, finalPercent, armorIgnorePercent, reduceIgnorePercent, pullRadius, pullStrength, catastropheBonusPercent, bossBonusPercent;

        Genesis(ForgeConfigSpec.Builder b) {
            b.push("abyssal_genesis");
            manaCost = i(b, "manaCost", 500, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 120, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 100, 4, 200, "Maximum targeting range");
            radius = f(b, "radius", 35, 2, 128, "Supernova radius");
            collapseRadius = f(b, "collapseRadius", 55, 2, 160, "Cosmic collapse radius");
            bigCollapseRadius = f(b, "bigCollapseRadius", 65, 2, 160, "Collapse radius with Starfall Catastrophe");
            starFlat = f(b, "starFlat", 220, 0, 100000, "Starfall impact flat damage");
            starPercent = f(b, "starPercent", 18, 0, 100, "Starfall impact percent");
            starImpactRadius = f(b, "starImpactRadius", 12, 1, 48, "Radius of one star impact");
            supernovaFlat = f(b, "supernovaFlat", 380, 0, 100000, "Abyssal supernova flat damage");
            supernovaPercent = f(b, "supernovaPercent", 30, 0, 100, "Abyssal supernova percent");
            finalFlat = f(b, "finalFlat", 300, 0, 100000, "Cosmic collapse flat damage");
            finalPercent = f(b, "finalPercent", 25, 0, 100, "Cosmic collapse percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 40, 0, 100, "Cosmic Annihilation: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 30, 0, 100, "damage reduction ignored");
            pullRadius = f(b, "pullRadius", 40, 1, 128, "Abyssal Gravity radius");
            pullStrength = f(b, "pullStrength", 0.3, 0, 3, "Abyssal Gravity pull");
            catastropheCount = i(b, "catastropheCount", 5, 1, 64, "Starfall Catastrophe: enemies that must be struck");
            catastropheBonusPercent = f(b, "catastropheBonusPercent", 25, 0, 1000, "supernova bonus");
            bossBonusPercent = f(b, "bossBonusPercent", 30, 0, 1000, "final collapse bonus against bosses");
            b.pop();
        }
    }

    public static final class Azazel {
        public final Val<Integer> manaCost, cataclysmKills;
        public final Val<Double> cooldownSeconds, domainRadius, radius, finalRadius, firstFlat, firstPercent, dimFlat, dimPercent, soulFlat, soulPercent, finalFlat, finalPercent, armorIgnorePercent, reduceIgnorePercent, bossBonusPercent, markBonusPercent, markHealPercent, executeThresholdPercent, bossSoul, authSeconds, authSpellPercent, authReductionPercent, authSpeedPercent, cataclysmHealPercent, cataclysmExtraSeconds, protectReductionPercent, pullStrength;

        Azazel(ForgeConfigSpec.Builder b) {
            b.push("temptation_king_azazel");
            manaCost = i(b, "manaCost", 800, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 360, 0, 100000, "Cooldown in seconds");
            domainRadius = f(b, "domainRadius", 70, 2, 200, "Initial domain and soul pull radius");
            radius = f(b, "radius", 45, 2, 128, "Primary annihilation radius");
            finalRadius = f(b, "finalRadius", 80, 2, 200, "Final extinction radius");
            firstFlat = f(b, "firstFlat", 300, 0, 100000, "First judgment flat damage");
            firstPercent = f(b, "firstPercent", 25, 0, 100, "First judgment percent");
            dimFlat = f(b, "dimFlat", 450, 0, 100000, "Dimensional destruction flat damage");
            dimPercent = f(b, "dimPercent", 35, 0, 100, "Dimensional destruction percent");
            soulFlat = f(b, "soulFlat", 350, 0, 100000, "Soul annihilation flat damage");
            soulPercent = f(b, "soulPercent", 30, 0, 100, "Soul annihilation percent");
            finalFlat = f(b, "finalFlat", 600, 0, 100000, "Final extinction flat damage");
            finalPercent = f(b, "finalPercent", 50, 0, 100, "Final extinction percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 50, 0, 100, "Azazel's Absolute Authority: armor ignored");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 40, 0, 100, "damage reduction ignored");
            bossBonusPercent = f(b, "bossBonusPercent", 35, 0, 1000, "final extinction bonus against bosses");
            markBonusPercent = f(b, "markBonusPercent", 25, 0, 1000, "Extinction Mark: extra damage taken");
            markHealPercent = f(b, "markHealPercent", 60, 0, 100, "healing reduction");
            executeThresholdPercent = f(b, "executeThresholdPercent", 15, 0, 100, "Soul Annihilation threshold");
            bossSoul = f(b, "bossSoul", 200, 0, 100000, "extra soul damage to bosses");
            authSeconds = f(b, "authSeconds", 20, 0, 600, "True Authority duration");
            authSpellPercent = f(b, "authSpellPercent", 50, 0, 1000, "spell damage bonus");
            authReductionPercent = f(b, "authReductionPercent", 60, 0, 100, "damage reduction");
            authSpeedPercent = f(b, "authSpeedPercent", 30, 0, 500, "movement bonus");
            cataclysmKills = i(b, "cataclysmKills", 5, 1, 64, "Absolute Cataclysm: kills needed");
            cataclysmHealPercent = f(b, "cataclysmHealPercent", 40, 0, 100, "heal as percent of max health");
            cataclysmExtraSeconds = f(b, "cataclysmExtraSeconds", 10, 0, 600, "extra True Authority seconds");
            protectReductionPercent = f(b, "protectReductionPercent", 95, 0, 100, "Primordial Invulnerability: damage reduction in the last 10 seconds");
            pullStrength = f(b, "pullStrength", 0.3, 0, 3, "Soul pull");
            b.pop();
        }
    }
}
