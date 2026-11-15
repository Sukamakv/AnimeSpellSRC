package com.example.jjkspells.vergil.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.vergil.VergilRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-side hook binding the Vergil entities to their renderers. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VergilClient {
    private VergilClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VergilRegistry.CUT, JudgementCutRenderer::new);
        event.registerEntityRenderer(VergilRegistry.SWORDS, SpiralSwordsRenderer::new);
        event.registerEntityRenderer(VergilRegistry.RUSH, DimensionRushRenderer::new);
        event.registerEntityRenderer(VergilRegistry.TRIGGER, DevilTriggerRenderer::new);
        event.registerEntityRenderer(VergilRegistry.HELM, HelmBreakerRenderer::new);
        event.registerEntityRenderer(VergilRegistry.WAVE_E, CrescentWaveRenderer::new);
        event.registerEntityRenderer(VergilRegistry.SEVER, DimensionSeverRenderer::new);
    }
}
