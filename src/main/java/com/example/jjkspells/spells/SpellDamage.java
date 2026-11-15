package com.example.jjkspells.spells;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import com.example.jjkspells.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/** Shared damage helpers for the spells of this mod. */
public final class SpellDamage {
    private static final ResourceLocation BLEEDING = new ResourceLocation("dungeons_and_combat", "bleeding");
    private static final ResourceLocation IRRADIATED = new ResourceLocation("alexscaves", "irradiated");

    private SpellDamage() {}

    /**
     * Max-health percentage damage. It is taken straight off the target's health, so it ignores armor, absorption,
     * invulnerability frames and every damage cap / damage reduction that works through the hurt events (other mods'
     * "max damage per hit" rules, resistance effects, enchantments, and so on). Killing blows go through die() so
     * kill credit, drops and death events still happen. Creative and spectator players are skipped, and so is the
     * caster of an active Time Stop.
     */
    public static void percent(LivingEntity target, float amount, @Nullable LivingEntity attacker, DamageSource source) {
        if (target == null || amount <= 0 || target.level().isClientSide || !target.isAlive()) return;
        if (target instanceof Player p && (p.getAbilities().invulnerable || p.isSpectator())) return;
        if (TimeStopManager.protects(target, attacker)) return;
        if (JotaroManager.absorbPercent(target, amount, attacker)) return;
        Debuffs.hit(attacker, target);

        target.setLastHurtByMob(attacker);
        if (attacker instanceof Player pl) target.setLastHurtByPlayer(pl);

        LivingEntity src = attacker;
        if (src instanceof io.redspace.ironsspellbooks.entity.mobs.IMagicSummon sm && sm.getSummoner() instanceof LivingEntity o) src = o;
        amount *= com.example.jjkspells.ModAttributes.multiplier(src);                       // the Max Health Damage stat
        amount *= 1f - com.example.jjkspells.ModAttributes.resist(target);                   // the Max Health Resistance stat
        amount *= 1f - com.example.jjkspells.item.PercentWardItem.reductionOf(target);   // curios that soften max-health damage
        amount *= com.example.jjkspells.overlord.entity.GoatBase.damageFactor(target);       // a caster guarded by the Black Goat
        amount = com.example.jjkspells.yhwach.entity.AlmightyEntity.capFor(target, amount); // THE ALMIGHTY: no hit above the cap
        if (amount <= 0) return;
        float hp = target.getHealth();
        if (amount < hp) {
            target.setHealth(hp - amount);
            target.level().broadcastEntityEvent(target, (byte) 2); // the hurt flash and sound
        } else {
            target.setHealth(0f);
            target.die(source);
        }
    }

    /**
     * Inflicts Alex's Caves' Irradiated (which really does stop healing). Does nothing if that mod is not installed.
     * Call every tick or so while the target should stay irradiated; it only refreshes when it is running low.
     */
    public static void irradiate(LivingEntity target, int ticks) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(IRRADIATED);
        if (effect == null) return;
        MobEffectInstance cur = target.getEffect(effect);
        if (cur == null || cur.getDuration() < ticks - 20) {
            target.addEffect(new MobEffectInstance(effect, ticks, 0, false, true, true));
        }
    }

    /** Seconds the Darius / Sun Breathing wound lasts. */
    public static final int WOUND_TICKS = 120;

    /** Bleeding + Alex's Caves' Irradiated (no healing) for 6 seconds. Used by every Darius and Sun Breathing spell. */
    public static void wound(LivingEntity target) {
        if (target == null || target.level().isClientSide || !target.isAlive()) return;
        irradiate(target, WOUND_TICKS);
        MobEffect bleed = ForgeRegistries.MOB_EFFECTS.getValue(BLEEDING);   // dungeons_and_combat:bleeding, if installed
        if (bleed == null) bleed = ModEffects.BLEEDING.get();               // otherwise this mod's own Bleeding
        MobEffectInstance cur = target.getEffect(bleed);
        if (cur == null || cur.getDuration() < WOUND_TICKS - 20) {
            target.addEffect(new MobEffectInstance(bleed, WOUND_TICKS, 0, false, true, true));
        }
    }
}
