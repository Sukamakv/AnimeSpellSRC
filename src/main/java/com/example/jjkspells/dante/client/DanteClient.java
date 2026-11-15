package com.example.jjkspells.dante.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dante.DanteRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-side hook binding the Dante entities to their renderers. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DanteClient {
    private DanteClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DanteRegistry.STAB, MillionStabRenderer::new);
        event.registerEntityRenderer(DanteRegistry.STYLISH, StylishRankRenderer::new);
        event.registerEntityRenderer(DanteRegistry.ROYAL, RoyalGuardRenderer::new);
        event.registerEntityRenderer(DanteRegistry.CAVALIERE, CavaliereRenderer::new);
        event.registerEntityRenderer(DanteRegistry.TRIGGER, DevilTriggerRenderer::new);
        event.registerEntityRenderer(DanteRegistry.ROUND, RoundTripRenderer::new);
        event.registerEntityRenderer(DanteRegistry.JUDGMENT, SinJudgmentRenderer::new);
    }
}
