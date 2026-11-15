package com.example.jjkspells.yasuo.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.yasuo.YasuoRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-side hook binding the Yasuo entities to their renderers. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class YasuoClient {
    private YasuoClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(YasuoRegistry.STEEL, SteelTempestRenderer::new);
        event.registerEntityRenderer(YasuoRegistry.WALL, WindWallRenderer::new);
        event.registerEntityRenderer(YasuoRegistry.BLADE, SweepingBladeRenderer::new);
        event.registerEntityRenderer(YasuoRegistry.DUEL, WindboundDuelRenderer::new);
        event.registerEntityRenderer(YasuoRegistry.BREATH, LastBreathRenderer::new);
        event.registerEntityRenderer(YasuoRegistry.CYCLONE, ReapingCycloneRenderer::new);
        event.registerEntityRenderer(YasuoRegistry.WINDS, ThousandWindsRenderer::new);
    }
}
