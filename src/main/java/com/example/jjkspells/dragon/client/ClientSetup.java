package com.example.jjkspells.dragon.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dragon.client.particle.GoldParticle;
import com.example.jjkspells.dragon.client.render.AscensionRenderer;
import com.example.jjkspells.dragon.registry.ModEntities;
import com.example.jjkspells.dragon.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.FX.get(), AscensionRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.GOLD.get(), s -> new GoldParticle.Provider(s, false));
        e.registerSpriteSet(ModParticles.RUNE.get(), s -> new GoldParticle.Provider(s, true));
    }
}
