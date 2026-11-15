package com.example.jjkspells.effect;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.spells.FX;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The rules of the Stunned effect. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class StunEvents {

    public static boolean isStunned(LivingEntity e) {
        return e != null && e.hasEffect(ModEffects.STUN.get());
    }

    /** No attacking: melee, arrows in flight and spells all deal damage through hurt(). */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && isStunned(attacker)) {
            event.setCanceled(true);
        }
    }

    /** No using items (bows, food, shields, ...). */
    @SubscribeEvent
    public static void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (isStunned(event.getEntity())) event.setCanceled(true);
    }

    /** No clicking blocks or entities. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent event) {
        if (event.isCancelable() && isStunned(event.getEntity())) event.setCanceled(true);
    }

    /** No casting Iron's spells. */
    @SubscribeEvent
    public static void onSpellPreCast(SpellPreCastEvent event) {
        if (isStunned(event.getEntity())) event.setCanceled(true);
    }

    /** No jumping. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity e = event.getEntity();
        if (isStunned(e)) {
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x, Math.min(0, v.y), v.z);
        }
    }

    /** Mobs stop pathing; a ring of stars circles the head of anything stunned. */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (!isStunned(e) || !(e.level() instanceof ServerLevel level)) return;
        if (e instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setJumping(false);
        }
        if (e.tickCount % 3 != 0) return;
        double base = e.tickCount * 0.55;
        double rad = Math.max(0.35, e.getBbWidth() * 0.6);
        double y = e.getY() + e.getBbHeight() + 0.12;
        for (int i = 0; i < 2; i++) {
            double ang = base + i * Math.PI;
            Vec3 p = new Vec3(e.getX() + Math.cos(ang) * rad, y, e.getZ() + Math.sin(ang) * rad);
            FX.one(level, ModParticles.MA_STAR.get(), p, new Vec3(-Math.sin(ang) * 0.05, 0.0, Math.cos(ang) * 0.05));
        }
    }
}
