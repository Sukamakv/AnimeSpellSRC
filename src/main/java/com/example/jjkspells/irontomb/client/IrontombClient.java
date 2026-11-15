package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.irontomb.IrontombTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only registration of the two renderers. Registered through @EventBusSubscriber, so ClientSetup stays untouched. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class IrontombClient {
    private IrontombClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(IrontombTypes.IRONTOMB, IrontombRenderer::new);
        e.registerEntityRenderer(IrontombTypes.SATELLITE, SatelliteRenderer::new);
    }
}
