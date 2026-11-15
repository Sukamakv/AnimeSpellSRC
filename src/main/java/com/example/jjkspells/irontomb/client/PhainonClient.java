package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.irontomb.PhainonTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only registration of Phainon's renderer. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PhainonClient {
    private PhainonClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(PhainonTypes.PHAINON, PhainonRenderer::new);
    }
}
