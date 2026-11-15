package com.example.jjkspells.garou.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.client.particle.GarParticle.Provider;
import com.example.jjkspells.garou.client.render.GammaBeamRenderer;
import com.example.jjkspells.garou.registry.ModEntities;
import com.example.jjkspells.garou.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.example.jjkspells.garou.client.particle.GarParticle.style;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.GAMMA_BEAM.get(), GammaBeamRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.STAR.get(), s -> new Provider(s, style().size(0.34f, 0.3f).life(26, 16).gravity(0f, 0.93f).fade(0.12f, 0.45f).spin(0.04f).loop()));
        e.registerSpriteSet(ModParticles.GLOW.get(), s -> new Provider(s, style().size(0.8f, 0.4f).life(12, 6).gravity(0f, 0.9f).grow(0.5f).fade(0.1f, 0.7f)));
        e.registerSpriteSet(ModParticles.SPARK.get(), s -> new Provider(s, style().size(0.18f, 0.1f).life(14, 8).gravity(0f, 0.88f).grow(-0.6f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.BURST.get(), s -> new Provider(s, style().size(3.2f, 1.4f).life(12, 2).gravity(0f, 1f).grow(0.35f).fade(0f, 0.2f).fixedRoll()));
        e.registerSpriteSet(ModParticles.RING.get(), s -> new Provider(s, style().size(1.4f, 0.4f).life(12, 2).gravity(0f, 1f).grow(4.5f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.DUST.get(), s -> new Provider(s, style().size(0.3f, 0.25f).life(24, 12).gravity(0f, 0.9f).grow(-0.5f).fade(0.15f, 0.4f).translucent().alpha(0.85f).spin(0.03f)));
    }
}
