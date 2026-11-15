package com.example.jjkspells.overlord.event;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.overlord.registry.ModEffects;
import com.example.jjkspells.overlord.util.Magic;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Fear (no attacking / targeting / casting) and the stand-still ritual lock. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class OverlordEvents {
    private OverlordEvents() {
    }

    private static boolean afraid(LivingEntity e) {
        return e != null && e.hasEffect(ModEffects.FEAR.get());
    }

    /** While their Black Goat or its Young live, the caster takes 25% less damage. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity().level().isClientSide) {
            return;
        }
        float factor = com.example.jjkspells.overlord.entity.GoatBase.damageFactor(e.getEntity());
        if (factor < 1f) {
            e.setAmount(e.getAmount() * factor);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        com.example.jjkspells.overlord.entity.GoatBase.GUARDED.clear();
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (afraid(e.getEntity()) && e.getNewTarget() != null) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (e.getSource().getEntity() instanceof LivingEntity attacker && afraid(attacker) && e.getSource().getDirectEntity() == attacker) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent e) {
        if (afraid(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPreCast(SpellPreCastEvent e) {
        if (e.getEntity().level().isClientSide) {
            return;
        }
        if (afraid(e.getEntity()) || Magic.LOCKED.contains(e.getEntity().getUUID())) {
            e.setCanceled(true);
        }
    }
}
