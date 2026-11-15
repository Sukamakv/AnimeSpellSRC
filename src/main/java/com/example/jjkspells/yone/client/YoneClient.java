package com.example.jjkspells.yone.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.yone.YoneRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-side hook binding the Yone entities to their renderers. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class YoneClient {
    private YoneClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(YoneRegistry.CRESCENTS, TwinCrescentsRenderer::new);
        event.registerEntityRenderer(YoneRegistry.CLEAVE, SpiritCleaveRenderer::new);
        event.registerEntityRenderer(YoneRegistry.GHOST, GhostWalkRenderer::new);
        event.registerEntityRenderer(YoneRegistry.FATE, FateSealedRenderer::new);
        event.registerEntityRenderer(YoneRegistry.VOLLEY, HornedVolleyRenderer::new);
        event.registerEntityRenderer(YoneRegistry.ECLIPSE, TwinMoonEclipseRenderer::new);
        event.registerEntityRenderer(YoneRegistry.DESTINY, DestinyCleavedRenderer::new);
    }
}
