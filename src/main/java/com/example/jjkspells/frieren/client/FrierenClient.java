package com.example.jjkspells.frieren.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.frieren.FrierenRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only hook: registers Frieren's renderers. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FrierenClient {
    private FrierenClient() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FrierenRegistry.ZOLTRAAK_ENTITY, ZoltraakRenderer::new);
        event.registerEntityRenderer(FrierenRegistry.DEFENSIVE_ENTITY, DefensiveMagicRenderer::new);
        event.registerEntityRenderer(FrierenRegistry.FLAMME_MAGIC, FlammeRenderer::new);
        event.registerEntityRenderer(FrierenRegistry.VOLLZANBEL_ENTITY, VollzanbelRenderer::new);
        event.registerEntityRenderer(FrierenRegistry.ANCIENT_ENTITY, AncientReleaseRenderer::new);
        event.registerEntityRenderer(FrierenRegistry.HEIGHT_ENTITY, HeightOfMagicRenderer::new);
    }
}
