package com.example.jjkspells.malenia.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.malenia.client.particle.MelParticle.Provider;
import com.example.jjkspells.malenia.client.render.WaterfowlRenderer;
import com.example.jjkspells.malenia.client.render.PhantomRenderer;
import com.example.jjkspells.malenia.client.render.BloomRenderer;
import com.example.jjkspells.malenia.client.render.BladeRenderer;
import com.example.jjkspells.malenia.client.render.GoddessRenderer;
import com.example.jjkspells.malenia.registry.ModEntities;
import com.example.jjkspells.malenia.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.example.jjkspells.malenia.client.particle.MelParticle.style;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.WATERFOWL.get(), WaterfowlRenderer::new);
        e.registerEntityRenderer(ModEntities.PHANTOM.get(), PhantomRenderer::new);
        e.registerEntityRenderer(ModEntities.BLOOM.get(), BloomRenderer::new);
        e.registerEntityRenderer(ModEntities.BLADE.get(), BladeRenderer::new);
        e.registerEntityRenderer(ModEntities.GODDESS.get(), GoddessRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.PETAL.get(), s -> new Provider(s, style().size(0.28f, 0.14f).life(40, 20).gravity(0.012f, 0.97f).fade(0.05f, 0.5f).translucent().spin(0.18f)));
        e.registerSpriteSet(ModParticles.BUTTERFLY.get(), s -> new Provider(s, style().size(0.32f, 0.12f).life(44, 20).gravity(-0.004f, 0.96f).fade(0.1f, 0.5f).spin(0.05f)));
        e.registerSpriteSet(ModParticles.GOLD.get(), s -> new Provider(s, style().size(0.14f, 0.08f).life(18, 10).gravity(0.02f, 0.9f).grow(-0.5f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.SILVER.get(), s -> new Provider(s, style().size(0.14f, 0.08f).life(16, 8).gravity(0.02f, 0.9f).grow(-0.5f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.SPORE.get(), s -> new Provider(s, style().size(0.2f, 0.1f).life(40, 20).gravity(-0.006f, 0.95f).grow(-0.3f).fade(0.15f, 0.6f)));
        e.registerSpriteSet(ModParticles.ROT.get(), s -> new Provider(s, style().size(1.0f, 0.5f).life(36, 16).gravity(-0.004f, 0.95f).grow(0.7f).fade(0.15f, 0.65f).translucent().alpha(0.6f).spin(0.03f)));
        e.registerSpriteSet(ModParticles.FLASH.get(), s -> new Provider(s, style().size(2.0f, 0.6f).life(5, 1).gravity(0f, 1f).grow(1.0f).fade(0.05f, 0.8f)));
        e.registerSpriteSet(ModParticles.RING.get(), s -> new Provider(s, style().size(1.2f, 0f).life(10, 0).gravity(0f, 1f).grow(5.0f).fade(0.05f, 0.7f)));
        e.registerSpriteSet(ModParticles.GLOW.get(), s -> new Provider(s, style().size(0.9f, 0.3f).life(12, 6).gravity(0f, 1f).grow(0.5f).fade(0.1f, 0.7f)));
        e.registerSpriteSet(ModParticles.STREAK.get(), s -> new Provider(s, style().size(0.5f, 0.2f).life(8, 4).gravity(0f, 0.9f).grow(-0.8f).fade(0.05f, 0.7f)));
    }
}
