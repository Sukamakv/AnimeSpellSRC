package com.example.jjkspells;

import com.example.jjkspells.entity.AzureColossus;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus setup that is needed on both client and server. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CommonSetup {
    private CommonSetup() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.AZURE_COLOSSUS.get(), AzureColossus.createAttributes().build());
    }
}
