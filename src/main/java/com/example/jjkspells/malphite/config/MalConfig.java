package com.example.jjkspells.malphite.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/jjk_spells-malphite.toml (created on first launch; restart the game/world to apply changes). */
public final class MalConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Shard SHARD;
    public static final Clap CLAP;
    public static final Strikes STRIKES;
    public static final Granite GRANITE;
    public static final Force FORCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        SHARD = new Shard(b);
        CLAP = new Clap(b);
        STRIKES = new Strikes(b);
        GRANITE = new Granite(b);
        FORCE = new Force(b);
        SPEC = b.build();
    }

    private MalConfig() {
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

    /** Damage lines are "flat + percent of the target's MAX HP" (percent values are plain percents: 5 = 5%). */
    public static final class General {
        public final Val<Boolean> hitPlayers;
        public final Val<Double> damageMultiplier, particleDensity, bossStunSlowPercent, bossArmorFactor;
        public final Val<Integer> staggerTicks;

        General(ForgeConfigSpec.Builder b) {
            b.push("general");
            hitPlayers = t(b, "hitPlayers", true, "Can these spells hurt other players (still obeys Iron's friendly-fire checks)");
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by the Malphite spells");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the server sends (lower it on weak machines)");
            bossStunSlowPercent = f(b, "bossStunSlowPercent", 80, 0, 100, "Bosses are never frozen by a stun; they lose this percent of their speed instead");
            bossArmorFactor = f(b, "bossArmorBreakFactor", 0.5, 0, 1, "Bosses suffer this fraction of the armor reduction");
            staggerTicks = i(b, "staggerTicks", 12, 0, 200, "How long a 'brief stagger' holds an enemy");
            b.pop();
        }
    }


    public static final class Shard {
        public final Val<Integer> manaCost, castTicks, fragments, fragMaxHits;
        public final Val<Double> cooldownSeconds, range, trackBreak, blocksPerTick, turnRate, flat, percent, aoeFlat, aoeRadius, fragFlat, slowPercent, slowSeconds, hastePercent, hasteSeconds, armorPercent, armorSeconds, earthVulnPercent;

        Shard(ForgeConfigSpec.Builder b) {
            b.push("seismic_shard");
            manaCost = i(b, "manaCost", 60, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 12, 0, 100000, "Cooldown in seconds");
            castTicks = i(b, "castTicks", 30, 0, 200, "Cast time in ticks (1.5 s)");
            range = f(b, "range", 30, 4, 128, "Maximum targeting range");
            trackBreak = f(b, "trackBreak", 35, 4, 128, "Homing stops if the target is further away than this");
            blocksPerTick = f(b, "blocksPerTick", 1.8, 0.3, 6, "Shard speed");
            turnRate = f(b, "turnRate", 0.12, 0, 1, "How sharply the shard turns towards its target (0 = never, 1 = instantly)");
            flat = f(b, "flat", 50, 0, 100000, "Impact flat damage");
            percent = f(b, "percent", 5, 0, 100, "Impact max-HP percent damage");
            aoeFlat = f(b, "aoeFlat", 25, 0, 100000, "Explosion damage to other enemies");
            aoeRadius = f(b, "aoeRadius", 4, 0.5, 16, "Explosion radius");
            fragments = i(b, "fragments", 6, 0, 32, "Fragments thrown by the impact");
            fragFlat = f(b, "fragFlat", 12, 0, 100000, "Damage of one fragment");
            fragMaxHits = i(b, "fragMaxHits", 2, 1, 32, "Fragments that may hit one enemy per cast");
            slowPercent = f(b, "slowPercent", 35, 0, 95, "Movement speed stolen from the target");
            slowSeconds = f(b, "slowSeconds", 4, 0, 600, "Duration of the slow");
            hastePercent = f(b, "hastePercent", 25, 0, 500, "Movement speed gained by the caster");
            hasteSeconds = f(b, "hasteSeconds", 4, 0, 600, "Duration");
            armorPercent = f(b, "armorPercent", 15, 0, 100, "Shattered Armor: armor reduction");
            armorSeconds = f(b, "armorSeconds", 5, 0, 600, "Shattered Armor duration");
            earthVulnPercent = f(b, "earthVulnPercent", 10, 0, 500, "Extra damage the target takes from Malphite spells while Shattered");
            b.pop();
        }
    }

    public static final class Clap {
        public final Val<Integer> manaCost, zoneInterval, resonanceCount;
        public final Val<Double> cooldownSeconds, radius, outerRadius, firstFlat, firstPercent, outerFlat, finalFlat, finalPercent, atkSlowPercent, atkSlowSeconds, moveSlowPercent, moveSlowSeconds, knockUpBlocks, zoneSeconds, zoneSlowPercent, zoneFlat, zoneEruptRadius, resonanceBonusPercent, resonanceZoneRadius, resonanceReductionPercent, resonanceReductionSeconds;

        Clap(ForgeConfigSpec.Builder b) {
            b.push("thunderclap");
            manaCost = i(b, "manaCost", 90, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 18, 0, 100000, "Cooldown in seconds");
            radius = f(b, "radius", 8, 1, 32, "Radius of the first slam and the final eruption");
            outerRadius = f(b, "outerRadius", 12, 1, 48, "Radius the shockwave grows to");
            firstFlat = f(b, "firstFlat", 60, 0, 100000, "First slam flat damage");
            firstPercent = f(b, "firstPercent", 6, 0, 100, "First slam max-HP percent");
            outerFlat = f(b, "outerFlat", 40, 0, 100000, "Outer shockwave damage");
            finalFlat = f(b, "finalFlat", 75, 0, 100000, "Final eruption flat damage");
            finalPercent = f(b, "finalPercent", 7, 0, 100, "Final eruption max-HP percent");
            atkSlowPercent = f(b, "atkSlowPercent", 35, 0, 95, "Crippling Tremor: attack speed reduction");
            atkSlowSeconds = f(b, "atkSlowSeconds", 4, 0, 600, "duration");
            moveSlowPercent = f(b, "moveSlowPercent", 25, 0, 95, "Crippling Tremor: movement reduction");
            moveSlowSeconds = f(b, "moveSlowSeconds", 3, 0, 600, "duration");
            knockUpBlocks = f(b, "knockUpBlocks", 2, 0, 16, "How high the final eruption throws enemies");
            zoneSeconds = f(b, "zoneSeconds", 6, 0, 600, "Earth Fracture zone duration");
            zoneSlowPercent = f(b, "zoneSlowPercent", 20, 0, 95, "Slow inside the zone");
            zoneInterval = i(b, "zoneInterval", 30, 5, 400, "Ticks between eruptions in the zone");
            zoneFlat = f(b, "zoneFlat", 15, 0, 100000, "Eruption damage");
            zoneEruptRadius = f(b, "zoneEruptRadius", 2, 0.5, 8, "Eruption radius");
            resonanceCount = i(b, "resonanceCount", 3, 1, 64, "Enemies the first slam must hit for Resonating Impact");
            resonanceBonusPercent = f(b, "resonanceBonusPercent", 25, 0, 1000, "Extra final damage");
            resonanceZoneRadius = f(b, "resonanceZoneRadius", 10, 1, 48, "Zone radius with Resonating Impact");
            resonanceReductionPercent = f(b, "resonanceReductionPercent", 20, 0, 100, "Caster damage reduction");
            resonanceReductionSeconds = f(b, "resonanceReductionSeconds", 4, 0, 600, "duration");
            b.pop();
        }
    }

    public static final class Strikes {
        public final Val<Integer> manaCost, monolithCount;
        public final Val<Double> cooldownSeconds, strikeRadius, smashRange, coneDegrees, flat1, flat2, flat3, percent3, flat4, percent4, momentumPercent, momentumMax, kbResistPercent, reductionPercent, speedPercent, armorBreakPercent, armorBreakSeconds, smashSlowPercent, smashSlowSeconds, monolithRadius, monolithDamagePercent, monolithMeleePercent, monolithMeleeSeconds, smashLaunchBlocks;

        Strikes(ForgeConfigSpec.Builder b) {
            b.push("brutal_strikes");
            manaCost = i(b, "manaCost", 100, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 22, 0, 100000, "Cooldown in seconds");
            strikeRadius = f(b, "strikeRadius", 6, 1, 16, "Reach of the three strikes");
            smashRange = f(b, "smashRange", 7, 1, 16, "Reach of the shockwave and the smash");
            coneDegrees = f(b, "coneDegrees", 140, 10, 360, "Frontal cone");
            flat1 = f(b, "flat1", 45, 0, 100000, "First strike damage");
            flat2 = f(b, "flat2", 55, 0, 100000, "Second strike damage");
            flat3 = f(b, "flat3", 70, 0, 100000, "Third strike flat damage");
            percent3 = f(b, "percent3", 5, 0, 100, "Third strike percent");
            flat4 = f(b, "flat4", 100, 0, 100000, "Titanic Smash flat damage");
            percent4 = f(b, "percent4", 9, 0, 100, "Titanic Smash percent");
            momentumPercent = f(b, "momentumPercent", 10, 0, 100, "Crushing Momentum per previous hit");
            momentumMax = f(b, "momentumMax", 30, 0, 500, "Crushing Momentum cap");
            kbResistPercent = f(b, "kbResistPercent", 50, 0, 100, "Caster knockback resistance during the combo");
            reductionPercent = f(b, "reductionPercent", 25, 0, 100, "Caster damage reduction during the combo");
            speedPercent = f(b, "speedPercent", -40, -95, 500, "Caster movement change during the combo");
            armorBreakPercent = f(b, "armorBreakPercent", 20, 0, 100, "Armorbreaker armor reduction");
            armorBreakSeconds = f(b, "armorBreakSeconds", 5, 0, 600, "duration");
            smashSlowPercent = f(b, "smashSlowPercent", 30, 0, 95, "Armorbreaker slow");
            smashSlowSeconds = f(b, "smashSlowSeconds", 3, 0, 600, "duration");
            monolithCount = i(b, "monolithCount", 4, 1, 64, "Enemies that must be hit for Monolithic Strength");
            monolithRadius = f(b, "monolithRadius", 9, 1, 32, "Smash reach with it");
            monolithDamagePercent = f(b, "monolithDamagePercent", 20, 0, 1000, "Smash bonus");
            monolithMeleePercent = f(b, "monolithMeleePercent", 30, 0, 1000, "Caster melee bonus");
            monolithMeleeSeconds = f(b, "monolithMeleeSeconds", 5, 0, 600, "duration");
            smashLaunchBlocks = f(b, "smashLaunchBlocks", 3, 0, 16, "Smash launch height");
            b.pop();
        }
    }

    public static final class Granite {
        public final Val<Integer> manaCost, formTicks;
        public final Val<Double> cooldownSeconds, durationSeconds, reductionPercent, armorPercent, speedPercent, reflectPercent, reflectCap, perStep, perStepBonus, maxBonus, lowHealthPercent, unbrokenReductionPercent, unbrokenRadius, unbrokenMeleePercent, unbrokenMeleeSeconds, radius, flat, percent, knockBlocks, pushRadius;

        Granite(ForgeConfigSpec.Builder b) {
            b.push("granite_shield");
            manaCost = i(b, "manaCost", 100, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 32, 0, 100000, "Cooldown in seconds");
            formTicks = i(b, "formTicks", 30, 0, 200, "Activation time");
            durationSeconds = f(b, "durationSeconds", 8, 1, 600, "How long the shield lasts after forming");
            reductionPercent = f(b, "reductionPercent", 65, 0, 100, "Damage reduction");
            armorPercent = f(b, "armorPercent", 40, 0, 500, "Armor bonus");
            speedPercent = f(b, "speedPercent", -15, -95, 500, "Movement change");
            reflectPercent = f(b, "reflectPercent", 20, 0, 500, "Share of prevented damage reflected");
            reflectCap = f(b, "reflectCap", 30, 0, 100000, "Cap per reflected hit");
            perStep = f(b, "perStep", 40, 1, 100000, "Prevented damage per bonus step");
            perStepBonus = f(b, "perStepBonus", 10, 0, 100000, "Flat bonus per step");
            maxBonus = f(b, "maxBonus", 120, 0, 100000, "Maximum flat bonus");
            lowHealthPercent = f(b, "lowHealthPercent", 25, 0, 100, "Unbreakable Mountain below this health percent");
            unbrokenReductionPercent = f(b, "unbrokenReductionPercent", 85, 0, 100, "Reduction with it");
            unbrokenRadius = f(b, "unbrokenRadius", 13, 1, 48, "Detonation radius with it");
            unbrokenMeleePercent = f(b, "unbrokenMeleePercent", 20, 0, 1000, "Melee bonus");
            unbrokenMeleeSeconds = f(b, "unbrokenMeleeSeconds", 5, 0, 600, "duration");
            radius = f(b, "radius", 10, 1, 48, "Detonation radius");
            flat = f(b, "flat", 80, 0, 100000, "Detonation flat damage");
            percent = f(b, "percent", 7, 0, 100, "Detonation max-HP percent");
            knockBlocks = f(b, "knockBlocks", 5, 0, 32, "Detonation knockback");
            pushRadius = f(b, "pushRadius", 6, 0, 32, "Radius of the push when the armor forms");
            b.pop();
        }
    }

    public static final class Force {
        public final Val<Integer> manaCost, zoneInterval, mbCount;
        public final Val<Double> cooldownSeconds, range, impactRadius, seismicRadius, collisionFlat, collisionPercent, collisionRadius, impactFlat, impactPercent, centralRadius, centralBonusPercent, launchBlocks, immobileSeconds, slowPercent, slowSeconds, vulnPercent, vulnSeconds, quakeFlat, quakePercent, finalFlat, finalPercent, finalRadius, zoneSeconds, zoneSlowPercent, spikeFlat, spikeRadius, mbDamagePercent, mbZoneSeconds, mbRefundPercent, chargeReductionPercent, resReductionPercent, resMeleePercent, resSeconds;

        Force(ForgeConfigSpec.Builder b) {
            b.push("unstoppable_force");
            manaCost = i(b, "manaCost", 300, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 300, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 50, 4, 128, "Maximum charge distance");
            impactRadius = f(b, "impactRadius", 14, 1, 48, "Impact radius");
            seismicRadius = f(b, "seismicRadius", 25, 1, 64, "Radius of the seismic waves and the fractured zone");
            collisionFlat = f(b, "collisionFlat", 180, 0, 100000, "Charge collision flat damage");
            collisionPercent = f(b, "collisionPercent", 15, 0, 100, "Charge collision percent");
            collisionRadius = f(b, "collisionRadius", 2.2, 0.5, 8, "Width of the charge");
            impactFlat = f(b, "impactFlat", 150, 0, 100000, "Impact flat damage");
            impactPercent = f(b, "impactPercent", 12, 0, 100, "Impact percent");
            centralRadius = f(b, "centralRadius", 5, 0, 32, "Centre of the impact");
            centralBonusPercent = f(b, "centralBonusPercent", 25, 0, 1000, "Centre bonus");
            launchBlocks = f(b, "launchBlocks", 5, 0, 32, "Launch height");
            immobileSeconds = f(b, "immobileSeconds", 1.5, 0, 60, "How long launched enemies cannot move voluntarily");
            slowPercent = f(b, "slowPercent", 40, 0, 95, "Slow after landing");
            slowSeconds = f(b, "slowSeconds", 3, 0, 600, "duration");
            vulnPercent = f(b, "vulnPercent", 20, 0, 500, "Extra damage from Malphite spells");
            vulnSeconds = f(b, "vulnSeconds", 5, 0, 600, "duration");
            quakeFlat = f(b, "quakeFlat", 110, 0, 100000, "Seismic wave flat damage");
            quakePercent = f(b, "quakePercent", 10, 0, 100, "Seismic wave percent");
            finalFlat = f(b, "finalFlat", 100, 0, 100000, "Final explosion flat damage");
            finalPercent = f(b, "finalPercent", 8, 0, 100, "Final explosion percent");
            finalRadius = f(b, "finalRadius", 15, 1, 48, "Final explosion radius");
            zoneSeconds = f(b, "zoneSeconds", 10, 0, 600, "Fractured-earth zone duration");
            zoneSlowPercent = f(b, "zoneSlowPercent", 25, 0, 95, "Slow in the zone");
            zoneInterval = i(b, "zoneInterval", 40, 5, 400, "Ticks between stone spikes");
            spikeFlat = f(b, "spikeFlat", 25, 0, 100000, "Spike damage");
            spikeRadius = f(b, "spikeRadius", 2, 0.5, 8, "Spike radius");
            mbCount = i(b, "mbCount", 5, 1, 64, "Enemies the impact must hit for Mountainbreaker");
            mbDamagePercent = f(b, "mbDamagePercent", 30, 0, 1000, "Seismic bonus");
            mbZoneSeconds = f(b, "mbZoneSeconds", 5, 0, 600, "Extra zone time");
            mbRefundPercent = f(b, "mbRefundPercent", 50, 0, 100, "Share of Granite Shield cooldown removed");
            chargeReductionPercent = f(b, "chargeReductionPercent", 90, 0, 100, "Damage reduction during the charge");
            resReductionPercent = f(b, "resReductionPercent", 40, 0, 100, "Resilience damage reduction");
            resMeleePercent = f(b, "resMeleePercent", 25, 0, 1000, "melee bonus");
            resSeconds = f(b, "resSeconds", 10, 0, 600, "duration");
            b.pop();
        }
    }
}
