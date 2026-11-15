package com.example.jjkspells.dante;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dante.entity.RoyalGuardEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus hooks of the Dante pack. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public final class DanteEvents {
    private DanteEvents() {
    }

    /** Royal Guard: while the guard is up every incoming hit is cancelled and stored. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled()) {
            return;
        }
        RoyalGuardEntity g = RoyalGuardEntity.guardOf(event.getEntity());
        if (g == null || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        event.setCanceled(true);
        g.block(event.getAmount(), event.getSource().getEntity());
    }
}
