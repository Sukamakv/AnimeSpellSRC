package com.example.jjkspells.prime;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dismantled: the holder's arms have been cut off from the fight. It cannot melee attack. Spells, arrows and other damage still work. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class DismantledEffect extends MobEffect {
    public DismantledEffect() {
        super(MobEffectCategory.HARMFUL, 0x1A1A1A);
    }

    private static boolean has(LivingEntity e) {
        return e.hasEffect(ModEffects.DISMANTLED.get());
    }

    private static boolean melee(DamageSource s) {
        return s.is(DamageTypes.PLAYER_ATTACK) || s.is(DamageTypes.MOB_ATTACK) || s.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingAttackEvent e) {
        if (e.getEntity().level().isClientSide) return;
        if (e.getSource().getEntity() instanceof LivingEntity a && has(a) && melee(e.getSource())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttack(AttackEntityEvent e) {
        if (has(e.getEntity())) e.setCanceled(true);
    }

    /** A Dismantled mob stops trying to bite: it keeps its distance instead of swinging at nothing. */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent e) {
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide || !(le instanceof Mob m) || (le.tickCount & 7) != 0) return;
        if (has(le) && m.getTarget() != null) m.setAggressive(false);
    }
}
