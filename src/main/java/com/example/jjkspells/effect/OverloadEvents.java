package com.example.jjkspells.effect;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The rules of Overload (and Simple Domain's immunity to it). */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class OverloadEvents {

    public static boolean isOverloaded(LivingEntity e) {
        return e != null && e.hasEffect(ModEffects.OVERLOAD.get());
    }

    // ---- outside forces ------------------------------------------------------------------------------
    private static final String FORCED_KEY = "jjk_forced_move_until";

    /**
     * Spells that throw or drag entities around (Lapse: Blue, Reversal: Red, blasts) call this each time they push
     * something, so an Overloaded target is still carried by the force. Overload only stops the target's OWN movement.
     */
    public static void forceMove(Entity e) {
        e.getPersistentData().putLong(FORCED_KEY, e.level().getGameTime() + 4);
    }

    private static boolean isForced(LivingEntity e) {
        return e.level().getGameTime() <= e.getPersistentData().getLong(FORCED_KEY);
    }

    // ---- Malevolent Shrine: no healing ---------------------------------------------------------------
    private static final String NOHEAL_KEY = "jjk_noheal_until";

    /** Marks an entity as unable to heal for the next {@code ticks} ticks (refreshed by Malevolent Shrine). */
    public static void blockHealing(LivingEntity e, int ticks) {
        e.getPersistentData().putLong(NOHEAL_KEY, Math.max(e.getPersistentData().getLong(NOHEAL_KEY), e.level().getGameTime() + ticks));
    }

    public static boolean isHealingBlocked(LivingEntity e) {
        return e != null && e.level().getGameTime() <= e.getPersistentData().getLong(NOHEAL_KEY);
    }

    /** Simple Domain holders cannot be overloaded. */
    @SubscribeEvent
    public static void onApplicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect() == ModEffects.OVERLOAD.get()
                && event.getEntity().hasEffect(ModEffects.SIMPLE_DOMAIN.get())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** No healing of any kind. */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (isOverloaded(event.getEntity()) || isHealingBlocked(event.getEntity())) event.setCanceled(true);
    }

    /** No attacking: melee, projectiles already in flight and spells all deal damage through hurt(). */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && isOverloaded(attacker)) {
            event.setCanceled(true);
        }
    }

    /** No using items (bows, food, shields, ...). */
    @SubscribeEvent
    public static void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (isOverloaded(event.getEntity())) event.setCanceled(true);
    }

    /** No clicking blocks or entities. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent event) {
        if (event.isCancelable() && isOverloaded(event.getEntity())) event.setCanceled(true);
    }

    /** No casting Iron's spells. */
    @SubscribeEvent
    public static void onSpellPreCast(SpellPreCastEvent event) {
        if (isOverloaded(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity e = event.getEntity();
        if (isOverloaded(e)) e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
    }

    /** Frozen in place (gravity still applies if they are in the air). */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !isOverloaded(e) || isForced(e)) return;
        var v = e.getDeltaMovement();
        if (v.x != 0 || v.z != 0 || v.y > 0) {
            e.setDeltaMovement(0, Math.min(0, v.y), 0);
            e.hurtMarked = true;
        }
        e.setSprinting(false);
    }
}
