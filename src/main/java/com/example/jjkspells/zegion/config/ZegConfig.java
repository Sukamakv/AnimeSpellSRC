package com.example.jjkspells.zegion.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/jjk_spells-zegion.toml. Damage lines are "flat + percent of the target's MAX HP" (percent values are plain percents: 20 = 20%). */
public final class ZegConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Impact IMPACT;
    public static final Domain DOMAIN;
    public static final Obsidian OBSIDIAN;
    public static final Phantom PHANTOM;
    public static final Labyrinth LABYRINTH;
    public static final Sing SING;
    public static final Form FORM;
    public static final Mephisto MEPHISTO;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        IMPACT = new Impact(b);
        DOMAIN = new Domain(b);
        OBSIDIAN = new Obsidian(b);
        PHANTOM = new Phantom(b);
        LABYRINTH = new Labyrinth(b);
        SING = new Sing(b);
        FORM = new Form(b);
        MEPHISTO = new Mephisto(b);
        SPEC = b.build();
    }

    private ZegConfig() {
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

    public static final class General {
        public final Val<Boolean> hitPlayers;
        public final Val<Double> damageMultiplier, particleDensity, bossPercentFactor, bossStunSlowPercent, bossPullFactor;
        public final Val<Integer> staggerTicks;

        General(ForgeConfigSpec.Builder b) {
            b.push("general");
            hitPlayers = t(b, "hitPlayers", true, "Can these spells hurt other players (still obeys Iron's friendly-fire checks)");
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by the Zegion spells");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the server sends");
            bossPercentFactor = f(b, "bossPercentFactor", 1.0, 0, 1, "Bosses take this fraction of the max-HP percent part of every hit (1 = full, as designed)");
            bossStunSlowPercent = f(b, "bossStunSlowPercent", 80, 0, 100, "Bosses are never frozen; they lose this percent of their speed instead");
            bossPullFactor = f(b, "bossPullFactor", 0.3, 0, 1, "Pull strength on bosses (Void Carapace, Labyrinth)");
            staggerTicks = i(b, "staggerTicks", 12, 0, 200, "How long a brief stagger holds an enemy");
            b.pop();
        }
    }

    public static final class Impact {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, shockRadius, ruptureRadius, punchFlat, punchPercent, shockFlat, shockPercent, ruptureFlat, rupturePercent,
                armorIgnorePercent, fractureSeconds, fractureDamagePercent, fractureHealPercent, fractureKbPercent, reductionPercent, bossShockBonusPercent, bossRuptureBonusBlocks;

        Impact(ForgeConfigSpec.Builder b) {
            b.push("kaisers_impact");
            manaCost = i(b, "manaCost", 120, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 28, 0, 100000, "Cooldown");
            range = f(b, "range", 45, 4, 128, "Maximum targeting range");
            radius = f(b, "radius", 16, 1, 64, "Primary impact radius (visual / stagger)");
            shockRadius = f(b, "shockRadius", 35, 1, 96, "Dimensional shockwave radius");
            ruptureRadius = f(b, "ruptureRadius", 25, 1, 96, "Final rupture radius");
            punchFlat = f(b, "punchFlat", 240, 0, 1000000, "Direct punch flat damage");
            punchPercent = f(b, "punchPercent", 20, 0, 100, "Direct punch max-HP percent");
            shockFlat = f(b, "shockFlat", 180, 0, 1000000, "Shockwave flat damage");
            shockPercent = f(b, "shockPercent", 15, 0, 100, "Shockwave max-HP percent");
            ruptureFlat = f(b, "ruptureFlat", 220, 0, 1000000, "Rupture flat damage");
            rupturePercent = f(b, "rupturePercent", 18, 0, 100, "Rupture max-HP percent");
            armorIgnorePercent = f(b, "armorIgnorePercent", 40, 0, 100, "Share of the punch that ignores armor and resistances");
            fractureSeconds = f(b, "fractureSeconds", 6, 0, 600, "Kaiser Fracture duration");
            fractureDamagePercent = f(b, "fractureDamagePercent", 25, 0, 500, "Kaiser Fracture: extra physical damage taken");
            fractureHealPercent = f(b, "fractureHealPercent", 35, 0, 100, "Kaiser Fracture: reduced healing");
            fractureKbPercent = f(b, "fractureKbPercent", 30, 0, 100, "Kaiser Fracture: knockback resistance removed");
            reductionPercent = f(b, "reductionPercent", 85, 0, 100, "Damage reduction for Zegion during the teleport and punch");
            bossShockBonusPercent = f(b, "bossShockBonusPercent", 25, 0, 1000, "Extra shockwave damage if the punch hit a boss");
            bossRuptureBonusBlocks = f(b, "bossRuptureBonusBlocks", 5, 0, 32, "Extra rupture radius if the punch hit a boss");
            b.pop();
        }
    }

    public static final class Domain {
        public final Val<Integer> manaCost, phantoms, fractureStacks;
        public final Val<Double> cooldownSeconds, range, radius, openFlat, openPercent, strikeDamage, finalFlat, finalPercent, soulDamage, slowPercent,
                outDownPercent, healDownPercent, vulnPercent, selfReductionPercent, selfSpeedPercent, selfDamagePercent, strikeInterval;

        Domain(ForgeConfigSpec.Builder b) {
            b.push("mist_lords_domain");
            manaCost = i(b, "manaCost", 150, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 65, 0, 100000, "Cooldown");
            range = f(b, "range", 65, 4, 160, "Maximum targeting range (aimed at nothing the domain opens around you)");
            radius = f(b, "radius", 40, 4, 100, "Domain radius");
            phantoms = i(b, "phantoms", 12, 1, 24, "Spectral Kaisers");
            openFlat = f(b, "openFlat", 180, 0, 1000000, "Initial manifestation flat damage");
            openPercent = f(b, "openPercent", 15, 0, 100, "Initial manifestation max-HP percent");
            strikeDamage = f(b, "strikeDamage", 70, 0, 1000000, "Damage of one phantom strike");
            strikeInterval = f(b, "strikeIntervalTicks", 14, 2, 100, "Ticks between phantom strike volleys");
            fractureStacks = i(b, "fractureStacks", 5, 1, 50, "Strikes needed for the soul burst");
            soulDamage = f(b, "soulDamage", 100, 0, 1000000, "Soul burst damage");
            finalFlat = f(b, "finalFlat", 320, 0, 1000000, "Final collapse flat damage");
            finalPercent = f(b, "finalPercent", 25, 0, 100, "Final collapse max-HP percent");
            slowPercent = f(b, "slowPercent", 50, 0, 95, "Movement speed reduction inside");
            outDownPercent = f(b, "outDownPercent", 35, 0, 95, "Reduced outgoing damage inside");
            healDownPercent = f(b, "healDownPercent", 30, 0, 100, "Reduced healing inside");
            vulnPercent = f(b, "vulnPercent", 25, 0, 500, "Extra damage enemies take from Zegion inside");
            selfReductionPercent = f(b, "selfReductionPercent", 50, 0, 100, "Mist Sovereignty: damage reduction");
            selfSpeedPercent = f(b, "selfSpeedPercent", 30, 0, 500, "Mist Sovereignty: speed");
            selfDamagePercent = f(b, "selfDamagePercent", 30, 0, 500, "Mist Sovereignty: damage");
            b.pop();
        }
    }

    public static final class Obsidian {
        public final Val<Integer> manaCost, charges, maxReflections;
        public final Val<Double> cooldownSeconds, durationSeconds, radius, reductionPercent, finalFlat, finalPercent, reflectDamage, perPrevented, perPreventedBonus, maxBonus;

        Obsidian(ForgeConfigSpec.Builder b) {
            b.push("obsidian_kaiser");
            manaCost = i(b, "manaCost", 110, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 50, 0, 100000, "Cooldown");
            durationSeconds = f(b, "durationSeconds", 10, 1, 120, "Duration");
            radius = f(b, "radius", 28, 1, 96, "Counterattack radius");
            reductionPercent = f(b, "reductionPercent", 95, 0, 100, "Damage reduction after the negation charges are used");
            charges = i(b, "charges", 6, 0, 100, "Attacks that are completely negated");
            finalFlat = f(b, "finalFlat", 280, 0, 1000000, "Detonation flat damage");
            finalPercent = f(b, "finalPercent", 22, 0, 100, "Detonation max-HP percent");
            maxReflections = i(b, "maxReflections", 10, 0, 100, "Energy spears thrown back");
            reflectDamage = f(b, "reflectDamage", 80, 0, 1000000, "Damage of one spear");
            perPrevented = f(b, "perPrevented", 100, 1, 1000000, "Prevented damage per bonus step");
            perPreventedBonus = f(b, "perPreventedBonus", 30, 0, 1000000, "Detonation bonus per step");
            maxBonus = f(b, "maxBonus", 400, 0, 1000000, "Maximum stored-damage bonus");
            b.pop();
        }
    }

    public static final class Phantom {
        public final Val<Integer> manaCost, strikes, afterimages, maxHitsPerTarget, perfectStrikes;
        public final Val<Double> cooldownSeconds, range, radius, strikeDamage, finisherFlat, finisherPercent, detonationFlat, detonationPercent, perfectFinisherBonusPercent,
                perfectRadiusBonus, reductionPercent, executeBelowPercent, executeBonusPercent;

        Phantom(ForgeConfigSpec.Builder b) {
            b.push("phantom_kaiser");
            manaCost = i(b, "manaCost", 130, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 38, 0, 100000, "Cooldown");
            range = f(b, "range", 55, 4, 128, "Maximum targeting range");
            radius = f(b, "radius", 20, 1, 96, "Attack / detonation radius");
            strikes = i(b, "strikes", 24, 1, 200, "Individual strikes");
            afterimages = i(b, "afterimages", 12, 1, 32, "Afterimages");
            strikeDamage = f(b, "strikeDamage", 45, 0, 1000000, "Damage of one strike");
            maxHitsPerTarget = i(b, "maxHitsPerTarget", 14, 1, 200, "Cap on strikes landing on one target");
            finisherFlat = f(b, "finisherFlat", 250, 0, 1000000, "Dimensional finisher flat damage");
            finisherPercent = f(b, "finisherPercent", 20, 0, 100, "Dimensional finisher max-HP percent");
            detonationFlat = f(b, "detonationFlat", 180, 0, 1000000, "Phantom collapse flat damage");
            detonationPercent = f(b, "detonationPercent", 15, 0, 100, "Phantom collapse max-HP percent");
            perfectStrikes = i(b, "perfectStrikes", 18, 1, 200, "Connected strikes for Perfect Assault");
            perfectFinisherBonusPercent = f(b, "perfectFinisherBonusPercent", 30, 0, 1000, "Perfect Assault: finisher bonus");
            perfectRadiusBonus = f(b, "perfectRadiusBonus", 5, 0, 32, "Perfect Assault: detonation radius bonus");
            reductionPercent = f(b, "reductionPercent", 80, 0, 100, "Damage reduction during the assault");
            executeBelowPercent = f(b, "executeBelowPercent", 25, 0, 100, "Kaiser Execution threshold");
            executeBonusPercent = f(b, "executeBonusPercent", 35, 0, 1000, "Kaiser Execution finisher bonus");
            b.pop();
        }
    }

    public static final class Labyrinth {
        public final Val<Integer> manaCost, waves, fractureStacks;
        public final Val<Double> cooldownSeconds, range, radius, openFlat, openPercent, waveDamage, soulDamage, finalFlat, finalPercent, slowPercent, selfReductionPercent,
                selfDamagePercent, ignoreArmorPercent, ignoreMagicPercent, bossSlowPercent;

        Labyrinth(ForgeConfigSpec.Builder b) {
            b.push("mephistos_labyrinth");
            manaCost = i(b, "manaCost", 200, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 85, 0, 100000, "Cooldown");
            range = f(b, "range", 70, 4, 160, "Maximum targeting range");
            radius = f(b, "radius", 35, 4, 100, "Labyrinth radius");
            openFlat = f(b, "openFlat", 200, 0, 1000000, "Capture flat damage");
            openPercent = f(b, "openPercent", 18, 0, 100, "Capture max-HP percent");
            waves = i(b, "waves", 8, 1, 40, "Cutting waves");
            waveDamage = f(b, "waveDamage", 100, 0, 1000000, "Damage of one wave hit");
            fractureStacks = i(b, "fractureStacks", 4, 1, 50, "Hits for the soul burst");
            soulDamage = f(b, "soulDamage", 150, 0, 1000000, "Soul burst damage");
            finalFlat = f(b, "finalFlat", 400, 0, 1000000, "Collapse flat damage");
            finalPercent = f(b, "finalPercent", 32, 0, 100, "Collapse max-HP percent");
            slowPercent = f(b, "slowPercent", 50, 0, 95, "Movement speed reduction inside");
            bossSlowPercent = f(b, "bossSlowPercent", 25, 0, 95, "Movement speed reduction for bosses");
            selfReductionPercent = f(b, "selfReductionPercent", 50, 0, 100, "Mephisto's Authority: damage reduction");
            selfDamagePercent = f(b, "selfDamagePercent", 35, 0, 500, "Mephisto's Authority: spell damage");
            ignoreArmorPercent = f(b, "ignoreArmorPercent", 35, 0, 100, "Final Judgment: ignored armor");
            ignoreMagicPercent = f(b, "ignoreMagicPercent", 25, 0, 100, "Final Judgment: ignored magic reduction");
            b.pop();
        }
    }

    public static final class Sing {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, finalRadius, openFlat, openPercent, dps, finalFlat, finalPercent, slowPercent, outDownPercent, pullStrength,
                ignoreArmorPercent, perEnemyBonusPercent, maxBonusPercent;

        Sing(ForgeConfigSpec.Builder b) {
            b.push("void_carapace");
            manaCost = i(b, "manaCost", 220, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 95, 0, 100000, "Cooldown");
            range = f(b, "range", 85, 4, 160, "Maximum targeting range");
            radius = f(b, "radius", 25, 1, 96, "Singularity radius");
            finalRadius = f(b, "finalRadius", 45, 1, 128, "Final collapse radius");
            openFlat = f(b, "openFlat", 200, 0, 1000000, "Gravitational rupture flat damage");
            openPercent = f(b, "openPercent", 18, 0, 100, "Gravitational rupture max-HP percent");
            dps = f(b, "damagePerSecond", 45, 0, 1000000, "Continuous damage per second");
            finalFlat = f(b, "finalFlat", 420, 0, 1000000, "Collapse flat damage");
            finalPercent = f(b, "finalPercent", 35, 0, 100, "Collapse max-HP percent");
            slowPercent = f(b, "slowPercent", 70, 0, 95, "Movement speed reduction inside");
            outDownPercent = f(b, "outDownPercent", 35, 0, 95, "Reduced outgoing damage inside");
            pullStrength = f(b, "pullStrength", 0.22, 0, 3, "Pull speed (blocks per tick) at the edge");
            ignoreArmorPercent = f(b, "ignoreArmorPercent", 40, 0, 100, "Collapse: ignored armor");
            perEnemyBonusPercent = f(b, "perEnemyBonusPercent", 5, 0, 500, "Collapse bonus per trapped enemy");
            maxBonusPercent = f(b, "maxBonusPercent", 40, 0, 1000, "Maximum collapse bonus");
            b.pop();
        }
    }

    public static final class Form {
        public final Val<Integer> manaCost, strikesPerBlast, overdriveKills;
        public final Val<Double> cooldownSeconds, durationSeconds, radius, finalRadius, openFlat, openPercent, finalFlat, finalPercent, physicalPercent, speedPercent, reductionPercent,
                meleeFlat, blastRadius, blastFlat, blastPercent, projectileReductionPercent, overdriveSeconds, overdriveMeleePercent;

        Form(ForgeConfigSpec.Builder b) {
            b.push("kaisers_final_form");
            manaCost = i(b, "manaCost", 250, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 130, 0, 100000, "Cooldown");
            durationSeconds = f(b, "durationSeconds", 18, 1, 600, "Transformation duration");
            radius = f(b, "radius", 30, 1, 96, "Transformation shockwave radius");
            finalRadius = f(b, "finalRadius", 45, 1, 128, "Final detonation radius");
            openFlat = f(b, "openFlat", 220, 0, 1000000, "Transformation shockwave flat damage");
            openPercent = f(b, "openPercent", 18, 0, 100, "Transformation shockwave max-HP percent");
            finalFlat = f(b, "finalFlat", 450, 0, 1000000, "Final detonation flat damage");
            finalPercent = f(b, "finalPercent", 35, 0, 100, "Final detonation max-HP percent");
            physicalPercent = f(b, "physicalPercent", 50, 0, 1000, "Physical damage bonus");
            speedPercent = f(b, "speedPercent", 35, 0, 500, "Movement speed bonus");
            reductionPercent = f(b, "reductionPercent", 65, 0, 100, "Incoming damage reduction");
            meleeFlat = f(b, "meleeFlat", 100, 0, 1000000, "Extra damage on every melee attack");
            strikesPerBlast = i(b, "strikesPerBlast", 3, 1, 50, "Melee hits per spatial explosion");
            blastRadius = f(b, "blastRadius", 8, 1, 32, "Sovereign's Strike radius");
            blastFlat = f(b, "blastFlat", 90, 0, 1000000, "Sovereign's Strike flat damage");
            blastPercent = f(b, "blastPercent", 8, 0, 100, "Sovereign's Strike max-HP percent");
            projectileReductionPercent = f(b, "projectileReductionPercent", 80, 0, 100, "Projectile damage reduction");
            overdriveKills = i(b, "overdriveKills", 3, 1, 100, "Kills for Kaiser Overdrive");
            overdriveSeconds = f(b, "overdriveSeconds", 5, 0, 600, "Overdrive extra duration");
            overdriveMeleePercent = f(b, "overdriveMeleePercent", 15, 0, 1000, "Overdrive extra melee damage");
            b.pop();
        }
    }

    public static final class Mephisto {
        public final Val<Integer> manaCost, phantoms, resetKills;
        public final Val<Double> cooldownSeconds, domainRadius, firstRadius, execRadius, collapseRadius, finalRadius, firstFlat, firstPercent, execFlat, execPercent, collapseFlat,
                collapsePercent, finalFlat, finalPercent, ignoreArmorPercent, ignoreMagicPercent, bossBonusPercent, markVulnPercent, markHealPercent, markSeconds, slowPercent,
                outDownPercent, atkDownPercent, bossSlowPercent, afterSeconds, afterDamagePercent, afterReductionPercent, afterSpeedPercent, healPercent, extraSeconds,
                protectionPercent, protectionSeconds;

        Mephisto(ForgeConfigSpec.Builder b) {
            b.push("illusion_king_mephisto");
            manaCost = i(b, "manaCost", 400, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 360, 0, 100000, "Cooldown");
            domainRadius = f(b, "domainRadius", 75, 8, 160, "Domain radius");
            firstRadius = f(b, "firstRadius", 50, 1, 160, "First Judgment radius");
            execRadius = f(b, "execRadius", 50, 1, 160, "Dimensional Execution radius");
            collapseRadius = f(b, "collapseRadius", 60, 1, 160, "Reality Collapse radius");
            finalRadius = f(b, "finalRadius", 90, 1, 200, "Final Annihilation radius");
            phantoms = i(b, "phantoms", 12, 1, 24, "Giant spectral Kaisers");
            firstFlat = f(b, "firstFlat", 350, 0, 1000000, "First Judgment flat damage");
            firstPercent = f(b, "firstPercent", 28, 0, 100, "First Judgment max-HP percent");
            execFlat = f(b, "execFlat", 450, 0, 1000000, "Dimensional Execution flat damage");
            execPercent = f(b, "execPercent", 35, 0, 100, "Dimensional Execution max-HP percent");
            collapseFlat = f(b, "collapseFlat", 500, 0, 1000000, "Reality Collapse flat damage");
            collapsePercent = f(b, "collapsePercent", 40, 0, 100, "Reality Collapse max-HP percent");
            finalFlat = f(b, "finalFlat", 650, 0, 1000000, "Final Annihilation flat damage");
            finalPercent = f(b, "finalPercent", 55, 0, 100, "Final Annihilation max-HP percent");
            ignoreArmorPercent = f(b, "ignoreArmorPercent", 50, 0, 100, "Ignored armor");
            ignoreMagicPercent = f(b, "ignoreMagicPercent", 40, 0, 100, "Ignored magic reduction");
            bossBonusPercent = f(b, "bossBonusPercent", 35, 0, 1000, "Extra final damage against bosses");
            markVulnPercent = f(b, "markVulnPercent", 30, 0, 1000, "Illusion Extinction Mark: extra damage from later phases");
            markHealPercent = f(b, "markHealPercent", 60, 0, 100, "Illusion Extinction Mark: reduced healing");
            markSeconds = f(b, "markSeconds", 20, 1, 600, "Mark duration");
            slowPercent = f(b, "slowPercent", 70, 0, 95, "Dimensional Imprisonment: speed reduction");
            bossSlowPercent = f(b, "bossSlowPercent", 30, 0, 95, "Dimensional Imprisonment: speed reduction for bosses");
            outDownPercent = f(b, "outDownPercent", 50, 0, 95, "Dimensional Imprisonment: reduced outgoing damage");
            atkDownPercent = f(b, "atkDownPercent", 40, 0, 95, "Dimensional Imprisonment: reduced attack speed");
            afterSeconds = f(b, "afterSeconds", 20, 0, 600, "Sovereign of the Void duration");
            afterDamagePercent = f(b, "afterDamagePercent", 50, 0, 1000, "Sovereign of the Void damage");
            afterReductionPercent = f(b, "afterReductionPercent", 60, 0, 100, "Sovereign of the Void damage reduction");
            afterSpeedPercent = f(b, "afterSpeedPercent", 30, 0, 500, "Sovereign of the Void speed");
            resetKills = i(b, "resetKills", 5, 1, 100, "Kills for Absolute Cataclysm");
            healPercent = f(b, "healPercent", 40, 0, 100, "Absolute Cataclysm: max health restored");
            extraSeconds = f(b, "extraSeconds", 10, 0, 600, "Absolute Cataclysm: extra Sovereign of the Void");
            protectionPercent = f(b, "protectionPercent", 95, 0, 100, "Primordial protection reduction");
            protectionSeconds = f(b, "protectionSeconds", 10, 0, 60, "Primordial protection window (before the end)");
            b.pop();
        }
    }
}
