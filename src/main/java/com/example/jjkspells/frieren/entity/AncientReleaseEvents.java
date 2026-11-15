package com.example.jjkspells.frieren.entity;

import com.example.jjkspells.JJKSpells;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Safety net for Ancient Release's root: if a caster still carries the movement lock but no live controller wants it (the
 * controller was removed some unusual way), the lock is lifted so nobody stays frozen.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AncientReleaseEvents {
    private AncientReleaseEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        AttributeInstance a = event.player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null && a.getModifier(AncientReleaseEntity.ROOT_UUID) != null && !AncientReleaseEntity.isRooting(event.player)) {
            a.removeModifier(AncientReleaseEntity.ROOT_UUID);
        }
    }
}
