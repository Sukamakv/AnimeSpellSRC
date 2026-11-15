package com.example.jjkspells.garou.util;

import com.example.jjkspells.spells.SpellDamage;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Garou damage helpers shared by his spells: who counts as an enemy, and the flat + %max-HP hit. */
public final class GarouDamage {
    private GarouDamage() {}

    /** Enemy = alive living entity that is not the caster, not an ally, not friendly-fire protected, not creative/spectator. */
    public static boolean isEnemy(LivingEntity caster, LivingEntity t) {
        if (t == caster || !t.isAlive() || t.isSpectator()) return false;
        if (t instanceof Player p && (p.getAbilities().invulnerable || p.isCreative())) return false;
        return !t.isAlliedTo(caster) && !DamageSources.isFriendlyFireBetween(caster, t);
    }

    /**
     * Flat damage (not scaled by max_hp_damage) followed by percent-of-max-HP damage (scaled by the attacker's
     * max_hp_damage and reduced by the target's max_hp_resist inside SpellDamage.percent). Knockback from hurt() is cancelled
     * by saving / restoring the motion, so each spell decides its own knockback.
     */
    public static void hit(LivingEntity target, LivingEntity caster, DamageSource src, float flat, float percentOfMax) {
        Vec3 keep = target.getDeltaMovement();
        target.invulnerableTime = 0;
        DamageSources.applyDamage(target, flat, src);
        target.setDeltaMovement(keep);
        if (target.isAlive()) SpellDamage.percent(target, target.getMaxHealth() * percentOfMax, caster, src);
        target.setDeltaMovement(keep);
        if (target.isAlive()) SpellDamage.irradiate(target, 120);   // Alex's Caves Irradiated: no healing
    }
}
