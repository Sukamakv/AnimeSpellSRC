package com.example.jjkspells.afterlife.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/afterlife-common.toml (created on first launch; restart the game/world to apply changes). */
public final class AfterlifeConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Guide GUIDE;
    public static final Blossom BLOSSOM;
    public static final Thrust THRUST;
    public static final Soother SOOTHER;
    public static final Funeral FUNERAL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        GUIDE = new Guide(b);
        BLOSSOM = new Blossom(b);
        THRUST = new Thrust(b);
        SOOTHER = new Soother(b);
        FUNERAL = new Funeral(b);
        SPEC = b.build();
    }

    private AfterlifeConfig() {
    }

    public static double BLOSSOM_SECONDS_GUIDE() {
        return GUIDE.blossomSeconds.get();
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

    /** Damage lines are "flat + percent of the target's MAX HP" (percent values are plain percents: 1.5 = 1.5%). */
    public static final class General {
        public final Val<Boolean> hitPlayers;
        public final Val<Double> damageMultiplier, particleDensity, flatScale, percentScale, cooldownScale, budgetPercent, budgetSeconds;

        General(ForgeConfigSpec.Builder b) {
            b.push("general");
            hitPlayers = t(b, "hitPlayers", true, "Can these spells hurt other players (still obeys the server PvP rule and Iron's friendly-fire checks)");
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by these spells");
            flatScale = f(b, "flatDamageScale", 0.6, 0, 100, "Balance: multiplier on every FLAT damage number of these spells (applied on top of damageMultiplier)");
            percentScale = f(b, "percentDamageScale", 0.3, 0, 100, "Balance: multiplier on every MAX-HP-PERCENT damage number of these spells");
            cooldownScale = f(b, "cooldownScale", 1.5, 0, 100, "Balance: multiplier on every cooldown of these spells");
            budgetPercent = f(b, "maxHpPercentBudget", 18, 0, 10000, "Balance: the most max-HP-percent damage these spells can deal to ONE target per budget window, in percent of its max HP (0 = no limit)");
            budgetSeconds = f(b, "maxHpPercentBudgetSeconds", 6, 0.5, 600, "Balance: length of that window");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the server sends (lower it if the ultimate lags)");
            b.pop();
        }
    }

    public static final class Guide {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, durationSeconds, hpCostPercent, bonusFlat, bonusPercent;
        public final Val<Integer> speedAmplifier, strengthAmplifier;
        public final Val<Double> blossomSeconds;

        Guide(ForgeConfigSpec.Builder b) {
            b.push("guide_to_afterlife");
            manaCost = i(b, "manaCost", 120, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 18, 0, 100000, "Cooldown");
            durationSeconds = f(b, "durationSeconds", 9, 1, 1000, "Transformation length");
            hpCostPercent = f(b, "hpCostPercent", 15, 0, 99, "Percent of CURRENT HP sacrificed on cast (never below 1 HP)");
            speedAmplifier = i(b, "speedAmplifier", 1, 0, 10, "Speed level minus one (1 = Speed II)");
            strengthAmplifier = i(b, "strengthAmplifier", 2, 0, 10, "Strength level minus one (2 = Strength III)");
            bonusFlat = f(b, "bonusFlatDamage", 15, 0, 10000, "Extra fire damage per melee hit");
            bonusPercent = f(b, "bonusMaxHpPercent", 1.5, 0, 100, "Extra melee damage as percent of target max HP");
            blossomSeconds = f(b, "blossomSeconds", 6, 0, 600, "Blood Blossom length planted by each melee hit");
            b.pop();
        }
    }

    public static final class Blossom {
        public final Val<Double> flat, percent, intervalSeconds;

        Blossom(ForgeConfigSpec.Builder b) {
            b.push("blood_blossom");
            flat = f(b, "damageFlat", 8, 0, 10000, "Damage per tick");
            percent = f(b, "damageMaxHpPercent", 0.75, 0, 100, "Percent of target max HP per tick");
            intervalSeconds = f(b, "intervalSeconds", 2.0, 0.25, 60, "Seconds between damage ticks");
            b.pop();
        }
    }

    public static final class Thrust {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, flat, percent, centerBonus, blossomSeconds, eruptDelaySeconds, eruptFlat, eruptPercent,
                eruptRadius, detFlat, detPercent, detRadius, fallImmunitySeconds;

        Thrust(ForgeConfigSpec.Builder b) {
            b.push("secret_spear_of_wangsheng");
            manaCost = i(b, "manaCost", 100, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 12, 0, 100000, "Cooldown");
            range = f(b, "range", 14, 2, 64, "Dash length in blocks");
            flat = f(b, "damageFlat", 45, 0, 100000, "Dash damage");
            percent = f(b, "damageMaxHpPercent", 5, 0, 100, "Dash damage, percent of max HP");
            centerBonus = f(b, "centerHitBonus", 25, 0, 100000, "Extra damage for the central spear hitbox");
            blossomSeconds = f(b, "blossomSeconds", 6, 0, 600, "Blood Blossom duration on enemies struck");
            eruptDelaySeconds = f(b, "eruptionDelaySeconds", 0.35, 0.05, 10, "Delay before the flame trail starts erupting");
            eruptFlat = f(b, "eruptionDamageFlat", 20, 0, 100000, "Flame trail damage");
            eruptPercent = f(b, "eruptionDamageMaxHpPercent", 2, 0, 100, "Flame trail damage, percent of max HP");
            eruptRadius = f(b, "eruptionRadius", 1.5, 0.5, 10, "Distance from the trail that is hit");
            detFlat = f(b, "detonationDamageFlat", 30, 0, 100000, "Blood Blossom detonation damage");
            detPercent = f(b, "detonationDamageMaxHpPercent", 3, 0, 100, "Blood Blossom detonation, percent of max HP");
            detRadius = f(b, "detonationRadius", 3, 0.5, 20, "Blood Blossom detonation radius");
            fallImmunitySeconds = f(b, "fallImmunitySeconds", 1, 0, 30, "Fall damage immunity after the dash");
            b.pop();
        }
    }

    public static final class Soother {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, contactFlat, contactPercent, explFlat, explPercent, coreRadius, coreBonus, fireSeconds,
                healPercent, healCapPercent, lowHpThreshold, lowHpBonusPercent, farewellRegenSeconds;

        Soother(ForgeConfigSpec.Builder b) {
            b.push("spirit_soother");
            manaCost = i(b, "manaCost", 150, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 25, 0, 100000, "Cooldown");
            range = f(b, "range", 12, 2, 64, "Max distance the ghost travels");
            radius = f(b, "explosionRadius", 7, 1, 32, "Explosion radius");
            contactFlat = f(b, "contactDamageFlat", 40, 0, 100000, "Damage when the ghost passes through an enemy");
            contactPercent = f(b, "contactDamageMaxHpPercent", 4, 0, 100, "Contact damage, percent of max HP");
            explFlat = f(b, "explosionDamageFlat", 85, 0, 100000, "Explosion damage");
            explPercent = f(b, "explosionDamageMaxHpPercent", 8, 0, 100, "Explosion damage, percent of max HP");
            coreRadius = f(b, "coreRadius", 3, 0.5, 32, "Radius of the extra-damage core");
            coreBonus = f(b, "coreBonusDamage", 25, 0, 100000, "Extra damage inside the core");
            fireSeconds = f(b, "fireSeconds", 5, 0, 600, "Burn time of everything hit");
            healPercent = f(b, "healPercentPerEnemy", 8, 0, 100, "Caster heal (percent of max HP) per enemy hit");
            healCapPercent = f(b, "healCapPercent", 32, 0, 100, "Heal cap per cast (percent of max HP)");
            lowHpThreshold = f(b, "lowHpThresholdPercent", 50, 0, 100, "Below this HP percent the explosion is stronger");
            lowHpBonusPercent = f(b, "lowHpBonusPercent", 30, 0, 1000, "Explosion damage bonus when under the threshold");
            farewellRegenSeconds = f(b, "finalFarewellRegenSeconds", 5, 0, 600, "Regeneration II length when the explosion kills something");
            b.pop();
        }
    }

    public static final class Funeral {
        public final Val<Integer> manaCost, strikes;
        public final Val<Double> cooldownSeconds, range, strikeRadius, boomRadius, strikeFlat, strikePercent, strikeBlossomSeconds, portalFlat, portalPercent,
                portalBlossomBonusPercent, portalSlowPercent, boomFlat, boomPercent, coreRadius, coreBonus, blossomDetFlat, blossomDetPercent, launchSpeed,
                bossKnockbackFactor, healPercent, healPerKillPercent, healCapPercent, regenSeconds, finalBlossomSeconds, fireImmuneSeconds, hoverHeight,
                crimsonThresholdPercent, crimsonStrikeBonusPercent, crimsonBoomFlat, crimsonBoomPercent, crimsonExtraHealPercent;

        Funeral(ForgeConfigSpec.Builder b) {
            b.push("funeral_of_a_thousand_butterflies");
            manaCost = i(b, "manaCost", 400, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 180, 0, 100000, "Cooldown");
            range = f(b, "targetRange", 20, 0, 64, "How far away the funeral can be aimed (0 = always on the caster)");
            strikeRadius = f(b, "strikeRadius", 10, 2, 64, "Radius around the centre where enemies are struck/pulled");
            boomRadius = f(b, "finalExplosionRadius", 14, 2, 64, "Final explosion radius");
            strikes = i(b, "spearStrikes", 12, 1, 60, "Number of spear strikes in the dance (spread over 2 seconds)");
            strikeFlat = f(b, "strikeDamageFlat", 18, 0, 100000, "Damage per strike");
            strikePercent = f(b, "strikeDamageMaxHpPercent", 1, 0, 100, "Strike damage, percent of max HP");
            strikeBlossomSeconds = f(b, "strikeBlossomSeconds", 6, 0, 600, "Blood Blossom length applied by strikes");
            portalFlat = f(b, "portalDamageFlat", 10, 0, 100000, "Gate damage every 0.5 s");
            portalPercent = f(b, "portalDamageMaxHpPercent", 0.5, 0, 100, "Gate damage, percent of max HP");
            portalBlossomBonusPercent = f(b, "portalBlossomBonusPercent", 50, 0, 1000, "Extra gate damage on Blood Blossom targets");
            portalSlowPercent = f(b, "portalSlowPercent", 80, 0, 99, "Slow applied to enemies inside the gate");
            boomFlat = f(b, "explosionDamageFlat", 150, 0, 100000, "Final explosion damage");
            boomPercent = f(b, "explosionDamageMaxHpPercent", 15, 0, 100, "Final explosion, percent of max HP");
            coreRadius = f(b, "coreRadius", 5, 0.5, 64, "Core radius of the final explosion");
            coreBonus = f(b, "coreBonusDamage", 75, 0, 100000, "Extra damage inside the core");
            blossomDetFlat = f(b, "blossomDetonationFlat", 35, 0, 100000, "Per Blood Blossom detonated by the explosion");
            blossomDetPercent = f(b, "blossomDetonationMaxHpPercent", 3, 0, 100, "Same, percent of max HP");
            launchSpeed = f(b, "launchSpeed", 0.65, 0, 3, "Upward velocity of launched enemies (0.65 is roughly 3 blocks)");
            bossKnockbackFactor = f(b, "bossKnockbackFactor", 0.35, 0, 1, "Knockback multiplier for bosses");
            healPercent = f(b, "healPercent", 25, 0, 100, "Heal at the end (percent of max HP)");
            healPerKillPercent = f(b, "healPerKillPercent", 5, 0, 100, "Extra heal per enemy killed");
            healCapPercent = f(b, "healCapPercent", 50, 0, 100, "Cap for the heal above");
            regenSeconds = f(b, "regenerationSeconds", 6, 0, 600, "Regeneration III length at the end");
            finalBlossomSeconds = f(b, "finalBlossomSeconds", 8, 0, 600, "Blood Blossom on survivors at the end");
            fireImmuneSeconds = f(b, "fireImmunitySeconds", 3, 0, 600, "Fire immunity after the animation");
            hoverHeight = f(b, "hoverHeight", 8, 2, 30, "How high the caster hovers above the centre during the gate");
            crimsonThresholdPercent = f(b, "crimsonFuneralBelowHpPercent", 35, 0, 100, "Casting below this HP percent enables Crimson Funeral");
            crimsonStrikeBonusPercent = f(b, "crimsonStrikeBonusPercent", 25, 0, 1000, "Strike damage bonus in Crimson Funeral");
            crimsonBoomFlat = f(b, "crimsonExplosionFlat", 50, 0, 100000, "Extra explosion damage in Crimson Funeral");
            crimsonBoomPercent = f(b, "crimsonExplosionMaxHpPercent", 5, 0, 100, "Same, percent of max HP");
            crimsonExtraHealPercent = f(b, "crimsonExtraHealPercent", 10, 0, 100, "Extra heal in Crimson Funeral");
            b.pop();
        }
    }
}
