package com.example.jjkspells.irontomb;

import com.example.jjkspells.spells.SpellDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Phainon's {flat, percent-of-max-health} hits: same pipeline as Irontomb's, with its own damage multiplier. */
public final class PhainonDamage {
    private PhainonDamage() {
    }

    public static void deal(LivingEntity source, LivingEntity victim, float[] f, float mult) {
        float scale = IrontombBalance.PH_DAMAGE_SCALE * mult;
        DamageSource src = source.level().damageSources().indirectMagic(source, source);
        float flat = f[0] * scale;
        float pct = f[1] * victim.getMaxHealth() * scale;
        if (flat > 0f) {
            victim.invulnerableTime = 0;
            victim.hurt(src, flat);
        }
        if (pct > 0f && victim.isAlive()) {
            SpellDamage.percent(victim, pct, source, src);
        }
    }
}
