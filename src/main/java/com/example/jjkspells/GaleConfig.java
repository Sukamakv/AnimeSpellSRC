package com.example.jjkspells;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.Supplier;

/** config/gale_barrage-common.toml (created on first launch; restart to apply changes). */
public final class GaleConfig {
    public static final ForgeConfigSpec SPEC;
    public static final Spell SPELL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        SPELL = new Spell(b);
        SPEC = b.build();
    }

    private GaleConfig() {
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

    private static Val<Integer> i(ForgeConfigSpec.Builder b, String n, int d, int lo, int hi, String c) {
        return new Val<>(b.comment(c + " [default: " + d + "]").defineInRange(n, d, lo, hi), d);
    }

    private static Val<Double> f(ForgeConfigSpec.Builder b, String n, double d, double lo, double hi, String c) {
        return new Val<>(b.comment(c + " [default: " + d + "]").defineInRange(n, d, lo, hi), d);
    }

    private static Val<Boolean> t(ForgeConfigSpec.Builder b, String n, boolean d, String c) {
        return new Val<>(b.comment(c + " [default: " + d + "]").define(n, d), d);
    }

    public static final class Spell {
        public final Val<Integer> manaCostBase, manaCostPerLevel, hits;
        public final Val<Double> cooldownSeconds, durationSeconds, weaponDamageFraction, weaponFractionPerLevel;
        public final Val<Double> reach, widthBase, widthPerBlock, knockback;
        public final Val<Boolean> requireWeapon, includeEnchantments, hitPlayers, swingArm;

        Spell(ForgeConfigSpec.Builder b) {
            b.push("spell");
            manaCostBase = i(b, "manaCostBase", 120, 0, 100000, "Mana cost at level 1");
            manaCostPerLevel = i(b, "manaCostPerLevel", 40, 0, 100000, "Extra mana per level above 1");
            cooldownSeconds = f(b, "cooldownSeconds", 18.0, 0, 100000, "Cooldown (the spell is instant cast)");
            requireWeapon = t(b, "requireWeapon", true, "Refuse to cast with an empty main hand");
            b.pop();

            b.push("barrage");
            durationSeconds = f(b, "durationSeconds", 4.0, 0.5, 60, "How long the barrage lasts");
            hits = i(b, "hits", 27, 1, 500, "Number of strikes spread evenly over the duration");
            weaponDamageFraction = f(b, "weaponDamageFraction", 0.7, 0, 100, "Damage of each hit as a fraction of the main-hand weapon's damage");
            weaponFractionPerLevel = f(b, "weaponDamageFractionPerLevel", 0.0, 0, 100, "Extra fraction per level above 1 (only matters if you raise the max level in Iron's spell config)");
            includeEnchantments = t(b, "includeEnchantments", true, "Add the weapon's Sharpness-type enchantment bonus to its damage");
            hitPlayers = t(b, "hitPlayers", true, "Can hurt other players (Iron's friendly-fire / team rules still apply)");
            swingArm = t(b, "swingArm", true, "Swing the sword arm on every strike");
            b.pop();

            b.push("shape");
            reach = f(b, "reach", 7.0, 1, 64, "How far in front of the caster the strikes reach");
            widthBase = f(b, "widthBase", 1.3, 0.1, 32, "Half-width of the strike zone next to the caster");
            widthPerBlock = f(b, "widthPerBlock", 0.22, 0, 4, "How much the half-width grows per block of distance");
            knockback = f(b, "knockback", 0.12, 0, 5, "Forward shove per hit (keeps victims in the barrage)");
            b.pop();
        }
    }
}
