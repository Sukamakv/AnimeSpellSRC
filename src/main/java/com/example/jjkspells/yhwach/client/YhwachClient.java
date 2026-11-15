package com.example.jjkspells.yhwach.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.yhwach.YhwachRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-side hook: binds the Yhwach entity to its renderer without touching the existing ClientSetup classes. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class YhwachClient {
    private YhwachClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(YhwachRegistry.PFEIL, PfeilRenderer::new);
        event.registerEntityRenderer(YhwachRegistry.QUAL, QualKreisRenderer::new);
        event.registerEntityRenderer(YhwachRegistry.SANKT, SanktRenderer::new);
        event.registerEntityRenderer(YhwachRegistry.VOLL, VollstreckungRenderer::new);
        event.registerEntityRenderer(YhwachRegistry.AUSW, AuswaehlenRenderer::new);
        event.registerEntityRenderer(YhwachRegistry.ALMIGHTY, AlmightyRenderer::new);
    }
}
