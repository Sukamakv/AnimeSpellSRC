package com.example.jjkspells.irontomb;

import com.example.jjkspells.spells.SpellDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * One place where Irontomb's {flat, percent-of-max-health} attacks hurt someone.
 * The flat part is a normal hit. The percent part goes through the mod's own SpellDamage.percent, so it obeys the
 * Max Health Damage stat of the boss, the Max Health Resistance stat of the victim, wards, Time Stop and so on.
 */
public final class IrontombDamage {
    private IrontombDamage() {
    }

    public static void deal(LivingEntity source, LivingEntity attacker, LivingEntity victim, float[] f, float mult) {
        float scale = IrontombBalance.DAMAGE_SCALE * mult;
        DamageSource src = source.level().damageSources().indirectMagic(source, attacker);
        float flat = f[0] * scale;
        float pct = f[1] * victim.getMaxHealth() * scale;
        if (flat > 0f) {
            victim.invulnerableTime = 0;
            victim.hurt(src, flat);
        }
        if (pct > 0f && victim.isAlive()) {
            SpellDamage.percent(victim, pct, attacker, src);
        }
    }
}
