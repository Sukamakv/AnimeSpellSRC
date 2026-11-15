package com.example.jjkspells.gil;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.function.Supplier;

/**
 * config/jjk_spells-gilgamesh.toml. Every number of the five Gilgamesh spells. Percent values are fractions
 * (0.05 = 5% of the target's MAX health) and go through SpellDamage.percent (ignores damage caps).
 */
public final class GilConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Gate GATE;
    public static final Enkidu ENKIDU;
    public static final Vimana VIMANA;
    public static final Merodach MERODACH;
    public static final Ea EA;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        GATE = new Gate(b);
        ENKIDU = new Enkidu(b);
        VIMANA = new Vimana(b);
        MERODACH = new Merodach(b);
        EA = new Ea(b);
        SPEC = b.build();
    }

    private GilConfig() {}

    /** A config value that falls back to its default if read before the config is loaded. */
    public static final class V<T> implements Supplier<T> {
        private final ForgeConfigSpec.ConfigValue<T> v;
        private final T d;

        V(ForgeConfigSpec.ConfigValue<T> v, T d) {
            this.v = v;
            this.d = d;
        }

        @Override
        public T get() {
            try {
                return v.get();
            } catch (IllegalStateException e) {
                return d;
            }
        }

        public float f() {
            return ((Number) get()).floatValue();
        }

        public int i() {
            return ((Number) get()).intValue();
        }
    }

    private static V<Double> d(ForgeConfigSpec.Builder b, String n, double def, double lo, double hi, String c) {
        return new V<>(b.comment(c + " [default: " + def + "]").defineInRange(n, def, lo, hi), def);
    }

    private static V<Integer> i(ForgeConfigSpec.Builder b, String n, int def, int lo, int hi, String c) {
        return new V<>(b.comment(c + " [default: " + def + "]").defineInRange(n, def, lo, hi), def);
    }

    private static V<Boolean> t(ForgeConfigSpec.Builder b, String n, boolean def, String c) {
        return new V<>(b.comment(c + " [default: " + def + "]").define(n, def), def);
    }

    public static final class General {
        public final V<Boolean> hitPlayers, breaksBlocks;
        public final V<Double> damageMultiplier, bossReduction;
        public final V<List<? extends String>> divine;

        General(ForgeConfigSpec.Builder b) {
            b.push("general");
            hitPlayers = t(b, "hitPlayers", true, "Can these spells hurt other players (still obeys Iron's friendly-fire checks)");
            breaksBlocks = t(b, "breaksBlocks", false, "Enuma Elish carves a crater. Only works if allow_block_breaking in jjk_spells-common.toml is also true");
            damageMultiplier = d(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage of the five spells");
            bossReduction = d(b, "bossReduction", 1.0, 0, 1, "Not used for damage; kept for packs that want to scale boss hits (1 = none)");
            divine = new V<>(b.comment("Entities counted as divine / celestial / godlike by Enkidu: entity ids, or #tag ids. The tag jjk_spells:divine is always checked too.")
                    .defineList("divine", List.of("minecraft:ender_dragon", "minecraft:wither"), o -> o instanceof String), List.of("minecraft:ender_dragon", "minecraft:wither"));
            b.pop();
        }
    }

    public static final class Gate {
        public final V<Double> weapon, heavy, finalFlat, finalPercent, arrogance, arrogantSpeed, arrogantRadius, finalRadius, speed, range;
        public final V<Integer> opening, standard, heavyCount;

        Gate(ForgeConfigSpec.Builder b) {
            b.push("gate_of_babylon");
            range = d(b, "range", 32, 8, 100, "Targeting range, in blocks");
            weapon = d(b, "weapon_damage", 18, 0, 100000, "Standard weapon damage");
            heavy = d(b, "heavy_damage", 35, 0, 100000, "Heavy weapon damage (pierces 3, 15% less each)");
            finalFlat = d(b, "final_damage", 60, 0, 100000, "Royal Judgment sword: flat damage");
            finalPercent = d(b, "final_percent", 0.05, 0, 1, "Royal Judgment sword: fraction of MAX health");
            finalRadius = d(b, "final_radius", 5, 1, 30, "Royal Judgment blast radius");
            arrogantRadius = d(b, "arrogant_radius", 7, 1, 40, "Blast radius if the caster stood still the whole time");
            arrogance = d(b, "arrogance_damage", 0.20, 0, 5, "Damage bonus if the caster stood still the whole time (King's Arrogance)");
            arrogantSpeed = d(b, "arrogance_speed", 0.15, 0, 5, "Projectile speed bonus for King's Arrogance");
            speed = d(b, "speed", 2.5, 0.5, 6, "Blocks per tick");
            opening = i(b, "opening_weapons", 6, 1, 24, "Weapons in the first volley");
            standard = i(b, "expansion_standard", 12, 1, 48, "Standard weapons in the second volley");
            heavyCount = i(b, "expansion_heavy", 4, 0, 24, "Heavy weapons in the second volley");
            b.pop();
        }
    }

    public static final class Enkidu {
        public final V<Double> range, initial, perSecond, finalFlat, finalPercent, splashFraction, splashRadius, divineMult, divineBonus, secondaryRadius,
                bossSlow, bossAttackSlow, secondaryFraction;
        public final V<Integer> seconds, divineSeconds, bossSeconds, secondaryTargets;
        public final V<Double> secondarySeconds;

        Enkidu(ForgeConfigSpec.Builder b) {
            b.push("enkidu");
            range = d(b, "range", 25, 5, 100, "Targeting range, in blocks");
            seconds = i(b, "binding_seconds", 5, 1, 60, "Total binding time");
            divineSeconds = i(b, "divine_binding_seconds", 7, 1, 60, "Binding time on divine targets");
            bossSeconds = i(b, "boss_binding_seconds", 3, 1, 60, "How long a boss is slowed (bosses are never frozen)");
            bossSlow = d(b, "boss_move_slow", 0.70, 0, 1, "Boss movement speed reduction");
            bossAttackSlow = d(b, "boss_attack_slow", 0.30, 0, 1, "Boss attack speed reduction");
            initial = d(b, "initial_damage", 30, 0, 100000, "Damage when the chains bind");
            perSecond = d(b, "constrict_damage_per_second", 12, 0, 100000, "Damage every second while bound");
            finalFlat = d(b, "final_damage", 55, 0, 100000, "Final chain explosion: flat");
            finalPercent = d(b, "final_percent", 0.06, 0, 1, "Final chain explosion: fraction of MAX health");
            splashRadius = d(b, "splash_radius", 4, 0, 30, "Final explosion splash radius");
            splashFraction = d(b, "splash_fraction", 0.5, 0, 1, "Share of the final damage the splash deals");
            divineMult = d(b, "divine_constrict_multiplier", 1.5, 0, 10, "Constricting damage multiplier on divine targets");
            divineBonus = d(b, "divine_spell_bonus", 0.25, 0, 5, "Extra damage a divine bound target takes from Gilgamesh's other spells");
            secondaryTargets = i(b, "secondary_targets", 4, 0, 20, "Chains that reach nearby enemies");
            secondaryRadius = d(b, "secondary_radius", 8, 1, 40, "Search radius for secondary chains");
            secondarySeconds = d(b, "secondary_root_seconds", 2.5, 0.1, 60, "Root time of secondary targets");
            secondaryFraction = d(b, "secondary_damage_fraction", 0.5, 0, 1, "Share of the initial damage secondary targets take");
            b.pop();
        }
    }

    public static final class Vimana {
        public final V<Double> range, radius, projectile, cannonFlat, cannonPercent, cannonRadius, finalFlat, finalPercent, ascend, projectileBlast, staggerDamage, spearRing;
        public final V<Integer> projectiles, staggerTicks;

        Vimana(ForgeConfigSpec.Builder b) {
            b.push("vimana");
            range = d(b, "range", 40, 8, 120, "Longest distance to the bombarded area");
            radius = d(b, "radius", 12, 3, 40, "Bombardment area radius");
            ascend = d(b, "ascend", 12, 4, 40, "Blocks the vessel rises");
            projectiles = i(b, "projectiles", 20, 1, 100, "Golden weapons in the bombardment");
            projectile = d(b, "projectile_damage", 28, 0, 100000, "Damage per golden weapon");
            projectileBlast = d(b, "projectile_blast_radius", 2, 0.5, 12, "Explosion radius of each weapon");
            cannonFlat = d(b, "cannon_damage", 75, 0, 100000, "Divine Cannon flat damage");
            cannonPercent = d(b, "cannon_percent", 0.07, 0, 1, "Divine Cannon fraction of MAX health");
            cannonRadius = d(b, "cannon_radius", 7, 1, 30, "Divine Cannon explosion radius");
            finalFlat = d(b, "final_damage", 90, 0, 100000, "Final bombardment flat damage");
            finalPercent = d(b, "final_percent", 0.08, 0, 1, "Final bombardment fraction of MAX health");
            spearRing = d(b, "spear_ring_radius", 8, 2, 30, "Radius of the ring of spears");
            staggerDamage = d(b, "stagger_damage", 60, 1, 100000, "Magic damage in a single hit that staggers the vessel");
            staggerTicks = i(b, "stagger_ticks", 10, 1, 100, "How long the bombardment pauses");
            b.pop();
        }
    }

    public static final class Merodach {
        public final V<Double> range, width, blade, bladePercent, wave, wavePercent, boom, boomPercent, boomRadius, execute, executeBonus, perfectRadius, perfectDamage, perfectReduction;
        public final V<Integer> swords, markSeconds;
        public final V<Double> markBonus;

        Merodach(ForgeConfigSpec.Builder b) {
            b.push("merodach");
            range = d(b, "range", 30, 8, 100, "How far the slash travels");
            width = d(b, "width", 10, 2, 40, "Slash width");
            blade = d(b, "blade_damage", 110, 0, 100000, "Direct blade hit flat damage");
            bladePercent = d(b, "blade_percent", 0.10, 0, 1, "Direct blade hit fraction of MAX health");
            wave = d(b, "wave_damage", 85, 0, 100000, "Energy slash flat damage");
            wavePercent = d(b, "wave_percent", 0.08, 0, 1, "Energy slash fraction of MAX health");
            boom = d(b, "detonation_damage", 70, 0, 100000, "Final detonation flat damage");
            boomPercent = d(b, "detonation_percent", 0.06, 0, 1, "Final detonation fraction of MAX health");
            boomRadius = d(b, "detonation_radius", 6, 1, 30, "Radius of each embedded sword's blast");
            swords = i(b, "swords", 8, 1, 24, "Swords that plunge down");
            execute = d(b, "execute_threshold", 0.25, 0, 1, "Below this health fraction the blade hit gets the bonus");
            executeBonus = d(b, "execute_bonus", 0.35, 0, 5, "Blade hit bonus below the threshold");
            markSeconds = i(b, "mark_seconds", 5, 1, 60, "Noble Phantasm Resonance duration");
            markBonus = d(b, "mark_bonus", 0.15, 0, 5, "Extra Gate of Babylon damage on marked enemies");
            perfectRadius = d(b, "perfect_radius_bonus", 0.25, 0, 5, "Detonation radius bonus when not interrupted");
            perfectDamage = d(b, "perfect_damage_bonus", 0.20, 0, 5, "Detonation damage bonus when not interrupted");
            perfectReduction = d(b, "perfect_reduction", 0.20, 0, 1, "Caster damage reduction for 3 seconds after a perfect release");
            b.pop();
        }
    }

    public static final class Ea {
        public final V<Double> range, spatial, spatialPercent, primary, primaryPercent, primaryRadius, centerRadius, centerBonus, collapse, collapsePercent, collapseRadius,
                ruptureRadius, bossPrimary, bossCollapse, distortRadius, distortSlow, ruptureDps, ruptureSlow, ruptureHeal, ruptureRadius2, supremacyBonus, guardReduction, craterRadius;
        public final V<Integer> ruptureSeconds, supremacySeconds, bossSlowSeconds;

        Ea(ForgeConfigSpec.Builder b) {
            b.push("enuma_elish");
            range = d(b, "range", 65, 15, 200, "Longest distance to the impact point");
            spatial = d(b, "spatial_rupture_damage", 120, 0, 1000000, "Vortex path flat damage");
            spatialPercent = d(b, "spatial_rupture_percent", 0.10, 0, 1, "Vortex path fraction of MAX health");
            primary = d(b, "primary_damage", 300, 0, 1000000, "Primary impact flat damage");
            primaryPercent = d(b, "primary_percent", 0.25, 0, 1, "Primary impact fraction of MAX health");
            primaryRadius = d(b, "primary_radius", 22, 4, 80, "Primary destruction radius");
            centerRadius = d(b, "center_radius", 7, 1, 40, "Central zone radius");
            centerBonus = d(b, "center_bonus", 0.30, 0, 5, "Extra damage in the central zone");
            collapse = d(b, "collapse_damage", 180, 0, 1000000, "Final reality collapse flat damage");
            collapsePercent = d(b, "collapse_percent", 0.15, 0, 1, "Final reality collapse fraction of MAX health");
            collapseRadius = d(b, "collapse_radius", 25, 4, 80, "Final reality collapse radius");
            ruptureRadius = d(b, "secondary_rupture_radius", 35, 8, 100, "Radius of the pull of the collapse");
            bossPrimary = d(b, "boss_primary_bonus", 0.25, 0, 5, "Primary impact bonus against bosses");
            bossCollapse = d(b, "boss_collapse_bonus", 0.20, 0, 5, "Collapse bonus against bosses");
            bossSlowSeconds = i(b, "boss_slow_seconds", 5, 1, 60, "Boss 40% slow duration");
            distortRadius = d(b, "distortion_radius", 15, 4, 60, "Radius around the impact point of the spatial distortion");
            distortSlow = d(b, "distortion_slow", 0.40, 0, 1, "Movement slow inside the distortion");
            ruptureSeconds = i(b, "rupture_seconds", 8, 1, 60, "How long the Rupture of Creation lasts");
            ruptureRadius2 = d(b, "rupture_zone_radius", 10, 2, 40, "Radius of the Rupture of Creation");
            ruptureDps = d(b, "rupture_damage_per_second", 15, 0, 100000, "Rupture damage per second");
            ruptureSlow = d(b, "rupture_slow", 0.20, 0, 1, "Rupture movement slow");
            ruptureHeal = d(b, "rupture_heal_cut", 0.25, 0, 1, "Rupture healing reduction");
            supremacyBonus = d(b, "supremacy_bonus", 0.30, 0, 5, "Damage bonus after killing a boss with Enuma Elish");
            supremacySeconds = i(b, "supremacy_seconds", 15, 1, 120, "Supremacy duration");
            guardReduction = d(b, "guard_reduction", 0.90, 0, 1, "Caster damage reduction during the final 5 seconds");
            craterRadius = d(b, "crater_radius", 16, 4, 64, "Crater radius if block breaking is on");
            b.pop();
        }
    }
}
