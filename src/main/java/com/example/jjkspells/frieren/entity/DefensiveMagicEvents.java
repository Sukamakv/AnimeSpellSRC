package com.example.jjkspells.frieren.entity;

import com.example.jjkspells.JJKSpells;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** FORGE-bus hook for Frieren's barrier: reduces damage to anyone it protects. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DefensiveMagicEvents {
    private DefensiveMagicEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || DefensiveMagicEntity.ACTIVE.isEmpty()) return;
        DamageSource src = event.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;            // /kill, void
        if (src.getEntity() == null && src.getDirectEntity() == null) return;   // environment is not an "attack"
        for (DefensiveMagicEntity b : DefensiveMagicEntity.ACTIVE) {
            if (b.isRemoved() || !b.isProtecting() || !b.protects(victim)) continue;
            event.setAmount(b.absorb(event.getAmount(), victim, src));
            break; // one barrier per hit
        }
    }
}
