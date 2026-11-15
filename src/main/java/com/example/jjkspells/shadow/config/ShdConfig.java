package com.example.jjkspells.shadow.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/jjk_spells-shadow.toml (created on first launch; restart the game/world to apply changes). */
public final class ShdConfig {
    public static final ForgeConfigSpec SPEC;
    public static final General G;
    public static final Garden GARDEN;
    public static final Throne THRONE;
    public static final Serpent SERPENT;
    public static final Counter COUNTER;
    public static final Atomic ATOMIC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        G = new General(b);
        GARDEN = new Garden(b);
        THRONE = new Throne(b);
        SERPENT = new Serpent(b);
        COUNTER = new Counter(b);
        ATOMIC = new Atomic(b);
        SPEC = b.build();
    }

    private ShdConfig() {
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
            damageMultiplier = f(b, "damageMultiplier", 1.0, 0, 1000, "Multiplier for ALL damage dealt by the Shadow spells");
            particleDensity = f(b, "particleDensity", 1.0, 0.05, 4.0, "Multiplier for how many particles the server sends (lower it on weak machines)");
            bossStunSlowPercent = f(b, "bossSlowPercent", 80, 0, 100, "Bosses are never frozen; they lose this percent of their speed instead");
            manaRestoreMultiplier = f(b, "manaRestoreMultiplier", 1.0, 0, 100, "Multiplier for all mana the Shadow spells give back");
            b.pop();
        }
    }

    public static final class Garden {
        public final Val<Integer> manaCost, maxTargets;
        public final Val<Double> cooldownSeconds, range, initialDamage, secondaryDamage, finalFlat, finalPercent, knockback;

        Garden(ForgeConfigSpec.Builder b) {
            b.push("shadows_garden");
            manaCost = i(b, "manaCost", 260, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 24, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 35, 4, 120, "Maximum target range");
            maxTargets = i(b, "maxTargets", 6, 1, 16, "Maximum number of targets");
            initialDamage = f(b, "initialDamage", 85, 0, 100000, "Initial strike damage (stored on the primary target)");
            secondaryDamage = f(b, "secondaryDamage", 55, 0, 100000, "Secondary strike damage per target (stored)");
            finalFlat = f(b, "finalFlat", 160, 0, 100000, "Final execution flat damage (stored)");
            finalPercent = f(b, "finalPercent", 12, 0, 100, "Final execution percent of max health");
            knockback = f(b, "knockback", 4, 0, 30, "Knockback blocks when the delayed damage lands");
            b.pop();
        }
    }

    public static final class Throne {
        public final Val<Integer> manaCost, maxCounters;
        public final Val<Double> cooldownSeconds, radius, shockDamage, counterDamage, finalFlat, finalPercent, finalRadius, touchRadius;

        Throne(ForgeConfigSpec.Builder b) {
            b.push("nameless_king");
            manaCost = i(b, "manaCost", 380, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 42, 0, 100000, "Cooldown in seconds");
            radius = f(b, "radius", 22, 4, 64, "Radius of the illusion stage");
            shockDamage = f(b, "shockDamage", 60, 0, 100000, "Initial psychological shock damage");
            counterDamage = f(b, "counterDamage", 130, 0, 100000, "Damage of one counterattack");
            maxCounters = i(b, "maxCounters", 6, 1, 16, "Maximum counterattacks");
            finalFlat = f(b, "finalFlat", 220, 0, 100000, "King's Judgment flat damage");
            finalPercent = f(b, "finalPercent", 15, 0, 100, "Judgment percent of max health");
            finalRadius = f(b, "finalRadius", 15, 2, 64, "Judgment radius around the primary target");
            touchRadius = f(b, "touchRadius", 2.4, 0.5, 8, "An enemy this close to a false Shadow counts as attacking it");
            b.pop();
        }
    }

    public static final class Serpent {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, pierceDamage, pierceWidth, strikeDamage, finalFlat, finalPercent, bindSlowPercent;

        Serpent(ForgeConfigSpec.Builder b) {
            b.push("slime_armament");
            manaCost = i(b, "manaCost", 340, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 30, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 45, 4, 120, "Maximum target range");
            radius = f(b, "radius", 18, 2, 64, "Predator form attack radius");
            pierceDamage = f(b, "pierceDamage", 110, 0, 100000, "Piercing Form damage");
            pierceWidth = f(b, "pierceWidth", 3.5, 0.5, 16, "Piercing Form width");
            strikeDamage = f(b, "strikeDamage", 55, 0, 100000, "Damage of one tendril strike");
            finalFlat = f(b, "finalFlat", 200, 0, 100000, "Final constriction flat damage");
            finalPercent = f(b, "finalPercent", 15, 0, 100, "Constriction percent of max health");
            bindSlowPercent = f(b, "bindSlowPercent", 90, 0, 100, "Movement reduction of the constricted target (bosses are only slowed)");
            b.pop();
        }
    }

    public static final class Counter {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, windowSeconds, perfectSeconds, range, counterFlat, counterPercent, perfectBonus, interruptSeconds, refundPercent;

        Counter(ForgeConfigSpec.Builder b) {
            b.push("perfect_counter");
            manaCost = i(b, "manaCost", 300, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 36, 0, 100000, "Cooldown in seconds");
            windowSeconds = f(b, "windowSeconds", 3, 0.5, 10, "Counter window length");
            perfectSeconds = f(b, "perfectSeconds", 1, 0.1, 5, "The last part of the window that counts as a Perfect Counter");
            range = f(b, "range", 25, 2, 80, "Maximum counter range");
            counterFlat = f(b, "counterFlat", 180, 0, 100000, "Counterattack flat damage");
            counterPercent = f(b, "counterPercent", 20, 0, 100, "Counterattack percent of max health");
            perfectBonus = f(b, "perfectBonus", 100, 0, 100000, "Perfect Counter bonus damage");
            interruptSeconds = f(b, "interruptSeconds", 1.5, 0, 30, "Perfect Counter: attacker is interrupted and held this long (bosses only slowed)");
            refundPercent = f(b, "refundPercent", 50, 0, 100, "Part of the cooldown refunded when nobody attacked during the window");
            b.pop();
        }
    }

    public static final class Atomic {
        public final Val<Integer> manaCost;
        public final Val<Double> cooldownSeconds, range, radius, shockRadius, mainFlat, mainPercent, armorIgnorePercent, reduceIgnorePercent, channelReductionPercent, craterRadius, craterDepth;
        public final Val<Boolean> breakBlocks, craterIllusion, cinematicCamera;
        private final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> protNames, protTypes;

        Atomic(ForgeConfigSpec.Builder b) {
            b.push("i_am_atomic");
            manaCost = i(b, "manaCost", 900, 0, 100000, "Mana cost");
            cooldownSeconds = f(b, "cooldownSeconds", 180, 0, 100000, "Cooldown in seconds");
            range = f(b, "range", 100, 4, 200, "Maximum target range");
            radius = f(b, "radius", 55, 2, 128, "Radius of the decisive blast");
            shockRadius = f(b, "shockRadius", 75, 2, 160, "Radius the delayed shockwave reaches (knockback only, no damage)");
            mainFlat = f(b, "mainFlat", 650, 0, 100000, "Release damage: flat part");
            mainPercent = f(b, "mainPercent", 45, 0, 100, "Release damage: percent of the target's maximum health");
            armorIgnorePercent = f(b, "armorIgnorePercent", 45, 0, 100, "Armor ignored by the release");
            reduceIgnorePercent = f(b, "reduceIgnorePercent", 35, 0, 100, "Magic damage reduction ignored by the release");
            channelReductionPercent = f(b, "channelReductionPercent", 85, 0, 100, "Damage reduction of the caster while the spell is channelled (he also cannot be pushed)");
            breakBlocks = new Val<>(b.comment("Destructive mode: the blast carves a crater into the land (respects bedrock and claim mods through the block break event) [default: false]").define("breakBlocks", false), false);
            craterRadius = f(b, "craterRadius", 28, 2, 64, "Destructive mode: crater radius");
            craterDepth = f(b, "craterDepth", 10, 1, 40, "Destructive mode: crater depth");
            craterIllusion = new Val<>(b.comment("Draw a temporary crater (visual only) after the blast [default: true]").define("craterIllusion", true), true);
            cinematicCamera = new Val<>(b.comment("The caster's own view follows the cinematic camera shots [default: true]").define("cinematicCamera", true), true);
            protNames = b.comment("Entities whose custom name contains one of these are never harmed (case-insensitive)").defineList("protectedNames", java.util.List.of("Alexia"), o -> o instanceof String);
            protTypes = b.comment("Entity type ids that are never harmed, e.g. minecraft:villager").defineList("protectedTypes", java.util.List.of(), o -> o instanceof String);
            b.pop();
        }

        public java.util.List<? extends String> names() {
            try {
                return protNames.get();
            } catch (IllegalStateException e) {
                return java.util.List.of("Alexia");
            }
        }

        public java.util.List<? extends String> types() {
            try {
                return protTypes.get();
            } catch (IllegalStateException e) {
                return java.util.List.of();
            }
        }
    }
}
