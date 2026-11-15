package com.example.jjkspells.pantheon.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/jjk_spells-pantheon.toml (created on first launch; restart the game/world to apply changes). */
public final class PanConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Comet COMET;
    public static final Vault VAULT;
    public static final Aegis AEGIS;
    public static final Star STAR;
    public static final Grand GRAND;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        COMET = new Comet(b);
        VAULT = new Vault(b);
        AEGIS = new Aegis(b);
        STAR = new Star(b);
        GRAND = new Grand(b);
        SPEC = b.build();
    }

    private PanConfig() {
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
        public final Val<Double> damageMultiplier, particleDensity, bossStunSlowPercent;

        General(ForgeConfigSpec.Builder b) {
            b.push("general");
            hitPlayers = t(b, "hitPlayers", true, "Can these spells hurt other players (still obeys Iron's friendly-fire checks)");
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by the Pantheon spells");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the server sends (lower it on weak machines)");
            bossStunSlowPercent = f(b, "bossStunSlowPercent", 80, 0, 100, "Bosses are never frozen by a stun; they lose this percent of their speed instead");
            b.pop();
        }
    }

    public static final class Comet {
        public final Val<Integer> manaCost, minChargeTicks, fullChargeTicks, maxChannelTicks, quickPierce, slowAmp;
        public final Val<Double> cooldownSeconds, range, speed, quickFlat, quickPercent, fullFlat, fullPercent, quickWidth, fullWidth,
                executeBelowPercent, executeBonusPercent, slowSeconds, shockFlat, shockRadius;

        Comet(ForgeConfigSpec.Builder b) {
            b.push("comet_spear");
            manaCost = i(b, "manaCost", 30, 0, 100000, "Mana paid for every half second of channelling (a tap costs one payment, a full charge about four)");
            cooldownSeconds = f(b, "cooldownSeconds", 12, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 36, 4, 128, "Maximum range of the spear");
            speed = f(b, "blocksPerTick", 4, 0.5, 8, "Spear speed");
            minChargeTicks = i(b, "minChargeTicks", 10, 0, 200, "Below this many ticks of channelling the throw is the quick spear");
            fullChargeTicks = i(b, "fullChargeTicks", 40, 1, 200, "At this many ticks of channelling the spear is fully charged (2 s)");
            maxChannelTicks = i(b, "maxChannelTicks", 50, 1, 400, "The channel ends by itself after this many ticks (2.5 s) and throws");
            quickFlat = f(b, "quickFlat", 45, 0, 100000, "Quick spear flat damage");
            quickPercent = f(b, "quickPercent", 5, 0, 100, "Quick spear max-HP percent damage");
            fullFlat = f(b, "fullFlat", 75, 0, 100000, "Fully charged flat damage (partial charges blend between the two)");
            fullPercent = f(b, "fullPercent", 8, 0, 100, "Fully charged max-HP percent damage");
            quickWidth = f(b, "quickWidth", 0.6, 0.1, 8, "Quick spear width");
            fullWidth = f(b, "fullWidth", 1.5, 0.1, 8, "Fully charged spear width");
            quickPierce = i(b, "quickPierce", 3, 1, 64, "Enemies the quick spear pierces (a fully charged spear pierces everything)");
            executeBelowPercent = f(b, "executeBelowPercent", 20, 0, 100, "Targets below this max-HP percent take bonus damage");
            executeBonusPercent = f(b, "executeBonusPercent", 40, 0, 1000, "Bonus damage percent on those targets");
            slowSeconds = f(b, "chargedSlowSeconds", 2, 0, 600, "Slowness of a fully charged hit");
            slowAmp = i(b, "chargedSlowAmplifier", 1, 0, 10, "Slowness amplifier (1 = Slowness II = 30%)");
            shockFlat = f(b, "shockwaveFlat", 20, 0, 100000, "Flat damage of the shockwave where a fully charged spear ends");
            shockRadius = f(b, "shockwaveRadius", 3, 0.5, 16, "Radius of that shockwave");
            b.pop();
        }
    }

    public static final class Vault {
        public final Val<Integer> manaCost, lockTicks, leapMaxTicks, strikes, strikeGap, perfectStrikeGap, finalWindup;
        public final Val<Double> cooldownSeconds, range, impactFlat, impactRadius, stunSeconds, perfectStunSeconds, strikeFlat, finalFlat,
                finalPercent, arcRadius, arcDegrees, arcFactor, knockbackBlocks, leapReduction, shieldbreakBonus, shieldDisableSeconds,
                perfectAngle, perfectRadius, perfectFlat;

        Vault(ForgeConfigSpec.Builder b) {
            b.push("shield_vault");
            manaCost = i(b, "manaCost", 110, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 16, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 15, 2, 64, "How far away the target can be");
            lockTicks = i(b, "lockTicks", 5, 1, 40, "Target lock time before the leap");
            leapMaxTicks = i(b, "leapTicks", 8, 3, 40, "Duration of the leap (longer leaps are capped by horizontal speed)");
            impactFlat = f(b, "impactFlat", 35, 0, 100000, "Shield collision damage to the main target");
            impactRadius = f(b, "impactRadius", 3, 0.5, 16, "Radius of the collision shockwave (nearby enemies are pushed away)");
            stunSeconds = f(b, "stunSeconds", 1.2, 0, 60, "Stun of the main target");
            strikes = i(b, "strikes", 3, 0, 12, "Spear strikes of the combo");
            strikeFlat = f(b, "strikeFlat", 18, 0, 100000, "Flat damage per spear strike");
            strikeGap = i(b, "strikeGapTicks", 6, 1, 40, "Ticks between combo strikes");
            perfectStrikeGap = i(b, "perfectStrikeGapTicks", 5, 1, 40, "Ticks between combo strikes after a Perfect Engagement (20% faster)");
            finalWindup = i(b, "finalWindupTicks", 4, 0, 40, "Ticks between the last spear strike and the shield bash");
            finalFlat = f(b, "finalFlat", 50, 0, 100000, "Final shield bash flat damage");
            finalPercent = f(b, "finalPercent", 4, 0, 100, "Final shield bash max-HP percent damage");
            arcRadius = f(b, "arcRadius", 4, 0.5, 16, "Reach of the final bash arc");
            arcDegrees = f(b, "arcDegrees", 140, 10, 360, "Width of the final bash arc");
            arcFactor = f(b, "arcFactor", 0.6, 0, 10, "Damage of everyone else in the arc, as a share of the main hit");
            knockbackBlocks = f(b, "knockbackBlocks", 4, 0, 32, "How far the final bash pushes enemies");
            leapReduction = f(b, "leapDamageReductionPercent", 60, 0, 100, "Damage reduction for the caster during the leap");
            shieldbreakBonus = f(b, "shieldbreakerBonusPercent", 30, 0, 1000, "Extra final bash damage on enemies that are blocking with a shield");
            shieldDisableSeconds = f(b, "shieldDisableSeconds", 2, 0, 60, "How long a blocking player's shield is disabled");
            perfectAngle = f(b, "perfectAngleDegrees", 10, 0, 90, "Perfect Engagement: the target was within this angle of your crosshair when you cast");
            perfectStunSeconds = f(b, "perfectStunSeconds", 1.8, 0, 60, "Stun on a Perfect Engagement");
            perfectRadius = f(b, "perfectShockwaveRadius", 5, 0.5, 32, "Extra shockwave radius of the final bash on a Perfect Engagement");
            perfectFlat = f(b, "perfectShockwaveFlat", 20, 0, 100000, "Damage of that extra shockwave");
            b.pop();
        }
    }

    public static final class Aegis {
        public final Val<Integer> manaCost, durationTicks, formTicks, overloadTicks, thrustGap, lastStandSpeedTicks;
        public final Val<Double> cooldownSeconds, range, defenseDegrees, attackDegrees, speedPercent, hitFlat, finalFlat, finalPercent,
                finalRange, lastStandRange, frontBlockPercent, perStep, perStepBonus, maxBonus, lastStandBelowPercent, lastStandSpeedPercent;

        Aegis(ForgeConfigSpec.Builder b) {
            b.push("aegis_storm");
            manaCost = i(b, "manaCost", 130, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 24, 0, 100000, "Cooldown in seconds");
            durationTicks = i(b, "durationTicks", 80, 20, 400, "Total length of the channel (4 s)");
            formTicks = i(b, "formTicks", 8, 0, 100, "Shield formation before the advance begins");
            overloadTicks = i(b, "overloadTicks", 10, 0, 100, "Overload time at the end, when the spear attacks stop");
            range = f(b, "range", 6, 1, 32, "Reach of the spear thrusts");
            defenseDegrees = f(b, "defenseDegrees", 140, 10, 360, "Width of the protected frontal arc");
            attackDegrees = f(b, "attackDegrees", 110, 10, 360, "Width of the thrust cone");
            speedPercent = f(b, "speedPercent", 45, 5, 100, "Movement speed while channelling, in percent of normal");
            thrustGap = i(b, "thrustGapTicks", 5, 1, 40, "Ticks between thrusts (5 = every 0.25 s)");
            hitFlat = f(b, "hitFlat", 12, 0, 100000, "Flat damage of each thrust");
            finalFlat = f(b, "finalFlat", 65, 0, 100000, "Aegis Detonation flat damage");
            finalPercent = f(b, "finalPercent", 6, 0, 100, "Aegis Detonation max-HP percent damage");
            finalRange = f(b, "finalRange", 7, 1, 48, "Reach of the detonation cone");
            lastStandRange = f(b, "lastStandRange", 9, 1, 48, "Reach of the detonation after Last Stand");
            frontBlockPercent = f(b, "frontBlockPercent", 95, 0, 100, "Share of frontal damage the shield absorbs");
            perStep = f(b, "retaliationPerStep", 50, 1, 100000, "Prevented damage needed for each retaliation step");
            perStepBonus = f(b, "retaliationStepBonus", 10, 0, 100000, "Flat detonation damage gained per step");
            maxBonus = f(b, "retaliationMaxBonus", 100, 0, 100000, "Cap of that bonus");
            lastStandBelowPercent = f(b, "lastStandBelowPercent", 30, 1, 100, "Last Stand triggers below this share of your max health");
            lastStandSpeedPercent = f(b, "lastStandSpeedPercent", 20, 0, 300, "Speed bonus after a Last Stand channel");
            lastStandSpeedTicks = i(b, "lastStandSpeedTicks", 60, 0, 1200, "How long that bonus lasts");
            b.pop();
        }
    }

    public static final class Star {
        public final Val<Integer> manaCost, ascendTicks, suspendTicks, diveTicks, aftershocks, burnSeconds, stunTicks, flameTicks;
        public final Val<Double> cooldownSeconds, range, riseBlocks, radius, coreRadius, spearpointRadius, coreFlat, corePercent, outerFlat,
                outerPercent, spearpointBonusPercent, burnDamage, afterFlat, afterRadius, diveReduction, launchSpeed;

        Star(ForgeConfigSpec.Builder b) {
            b.push("starfall_execution");
            manaCost = i(b, "manaCost", 220, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 38, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 24, 2, 96, "Maximum distance of the landing point");
            riseBlocks = f(b, "riseBlocks", 15, 2, 64, "How high the leap goes");
            ascendTicks = i(b, "ascendTicks", 16, 4, 100, "Ascension time (0.8 s)");
            suspendTicks = i(b, "suspendTicks", 14, 0, 100, "Celestial suspension time (0.7 s)");
            diveTicks = i(b, "diveTicks", 16, 4, 100, "Falling Star dive time (0.8 s)");
            radius = f(b, "radius", 9, 1, 48, "Outer radius of the impact");
            coreRadius = f(b, "coreRadius", 3, 0.5, 32, "Radius of the central impact");
            spearpointRadius = f(b, "spearpointRadius", 1.5, 0.1, 16, "Spearpoint Execution radius");
            coreFlat = f(b, "coreFlat", 100, 0, 100000, "Central impact flat damage");
            corePercent = f(b, "corePercent", 10, 0, 100, "Central impact max-HP percent damage");
            outerFlat = f(b, "outerFlat", 55, 0, 100000, "Outer shockwave flat damage");
            outerPercent = f(b, "outerPercent", 5, 0, 100, "Outer shockwave max-HP percent damage");
            spearpointBonusPercent = f(b, "spearpointBonusPercent", 30, 0, 1000, "Extra damage percent at the spearpoint");
            stunTicks = i(b, "spearpointStunTicks", 40, 0, 600, "Stun at the spearpoint (2 s)");
            burnSeconds = i(b, "burnSeconds", 5, 0, 120, "Celestial Burning duration");
            burnDamage = f(b, "burnDamagePerSecond", 8, 0, 100000, "Celestial Burning damage per second");
            aftershocks = i(b, "aftershocks", 5, 0, 32, "Number of small explosions after the impact");
            afterFlat = f(b, "aftershockFlat", 15, 0, 100000, "Damage of each small explosion");
            afterRadius = f(b, "aftershockRadius", 2, 0.5, 16, "Radius of each small explosion");
            flameTicks = i(b, "flameTicks", 80, 0, 1200, "How long the celestial flames stay on the ground (visual)");
            diveReduction = f(b, "diveDamageReductionPercent", 80, 0, 100, "Damage reduction during the dive");
            launchSpeed = f(b, "launchSpeed", 0.9, 0, 4, "Upward speed given to enemies in the central impact");
            b.pop();
        }
    }

    public static final class Grand {
        public final Val<Integer> manaCost, bossSlowTicks, killsForRefund, spearStrikes, refundSeconds, buffTicks, battlefieldTicks;
        public final Val<Double> cooldownSeconds, range, impactRadius, coreRadius, outerFactor, impactFlat, impactPercent, waveRadius, waveFlat,
                wavePercent, finalRadius, finalFlat, finalPercent, spearBlastFlat, spearBlastRadius, bossBonusPercent, bossSlowPercent,
                zoneDamage, zoneHealReduction, zoneSlowPercent, buffReduction, buffMelee, launchSpeed;

        Grand(ForgeConfigSpec.Builder b) {
            b.push("grand_starfall");
            manaCost = i(b, "manaCost", 600, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 300, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 100, 8, 256, "Maximum distance of the landing point");
            coreRadius = f(b, "coreRadius", 6, 1, 64, "Radius of the full-damage impact");
            impactRadius = f(b, "impactRadius", 18, 2, 96, "Radius of the shockwave");
            outerFactor = f(b, "outerFactor", 0.65, 0, 10, "Damage of the outer shockwave as a share of the primary impact");
            impactFlat = f(b, "impactFlat", 200, 0, 100000, "Primary impact flat damage");
            impactPercent = f(b, "impactPercent", 18, 0, 100, "Primary impact max-HP percent damage");
            spearStrikes = i(b, "spearStrikes", 6, 1, 24, "Giant spear projections");
            spearBlastFlat = f(b, "spearBlastFlat", 25, 0, 100000, "Damage of each spear projection's explosion");
            spearBlastRadius = f(b, "spearBlastRadius", 4, 0.5, 32, "Radius of each spear projection's explosion");
            waveRadius = f(b, "waveRadius", 25, 2, 128, "Radius of the Celestial Wave and of the battlefield");
            waveFlat = f(b, "waveFlat", 90, 0, 100000, "Celestial Wave flat damage");
            wavePercent = f(b, "wavePercent", 8, 0, 100, "Celestial Wave max-HP percent damage");
            finalRadius = f(b, "finalRadius", 15, 1, 96, "Radius of the Divine Annihilation");
            finalFlat = f(b, "finalFlat", 120, 0, 100000, "Divine Annihilation flat damage");
            finalPercent = f(b, "finalPercent", 10, 0, 100, "Divine Annihilation max-HP percent damage");
            bossBonusPercent = f(b, "godslayerBonusPercent", 25, 0, 1000, "Extra damage on bosses");
            bossSlowPercent = f(b, "bossSlowPercent", 40, 0, 100, "Movement reduction of bosses");
            bossSlowTicks = i(b, "bossSlowTicks", 80, 0, 1200, "How long bosses are slowed (4 s)");
            battlefieldTicks = i(b, "battlefieldTicks", 160, 0, 2400, "Celestial Battlefield duration (8 s)");
            zoneDamage = f(b, "battlefieldDamagePerSecond", 12, 0, 100000, "Magic damage per second inside the battlefield");
            zoneHealReduction = f(b, "battlefieldHealReductionPercent", 20, 0, 100, "Healing reduction inside the battlefield");
            zoneSlowPercent = f(b, "battlefieldSlowPercent", 15, 0, 100, "Movement reduction inside the battlefield");
            buffTicks = i(b, "mortalWillTicks", 200, 0, 6000, "Mortal Will duration after the ultimate (10 s)");
            buffReduction = f(b, "mortalWillReductionPercent", 30, 0, 100, "Damage reduction during Mortal Will");
            buffMelee = f(b, "mortalWillMeleePercent", 25, 0, 1000, "Melee damage bonus during Mortal Will");
            killsForRefund = i(b, "killsForRefund", 5, 1, 1000, "Kills needed to cut the cooldown");
            refundSeconds = i(b, "refundSeconds", 60, 0, 100000, "Seconds removed from the cooldown");
            launchSpeed = f(b, "launchSpeed", 1.0, 0, 4, "Upward speed given to enemies by the impact");
            b.pop();
        }
    }
}
