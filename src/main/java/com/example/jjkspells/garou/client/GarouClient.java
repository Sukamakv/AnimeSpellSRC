package com.example.jjkspells.garou.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.GarouRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only MOD-bus subscriber: attaches Garou's renderers. */
@SuppressWarnings("removal")
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GarouClient {
    private GarouClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GarouRegistry.GAMMA_RAY_BURST, GammaRayBurstRenderer::new);
        event.registerEntityRenderer(GarouRegistry.GRAVITY_KNUCKLE, GravityKnuckleRenderer::new);
        event.registerEntityRenderer(GarouRegistry.COSMIC_PORTAL, CosmicPortalRenderer::new);
        event.registerEntityRenderer(GarouRegistry.COSMIC_RADIATION_AURA, CosmicRadiationRenderer::new);
    }
}
