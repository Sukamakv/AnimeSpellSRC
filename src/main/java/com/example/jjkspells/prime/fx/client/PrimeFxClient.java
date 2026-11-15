package com.example.jjkspells.prime.fx.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.prime.fx.PrimeFxRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PrimeFxClient {
    private PrimeFxClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PrimeFxRegistry.TYPE, PrimeFxRenderer::new);
    }
}
