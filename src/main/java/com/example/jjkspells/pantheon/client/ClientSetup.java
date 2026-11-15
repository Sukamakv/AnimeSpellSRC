package com.example.jjkspells.pantheon.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.pantheon.client.particle.PanParticle.Provider;
import com.example.jjkspells.pantheon.client.render.AegisRenderer;
import com.example.jjkspells.pantheon.client.render.GrandRenderer;
import com.example.jjkspells.pantheon.client.render.SpearRenderer;
import com.example.jjkspells.pantheon.client.render.StarfallRenderer;
import com.example.jjkspells.pantheon.client.render.VaultRenderer;
import com.example.jjkspells.pantheon.registry.ModEntities;
import com.example.jjkspells.pantheon.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.example.jjkspells.pantheon.client.particle.PanParticle.style;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.SPEAR.get(), SpearRenderer::new);
        e.registerEntityRenderer(ModEntities.VAULT.get(), VaultRenderer::new);
        e.registerEntityRenderer(ModEntities.AEGIS.get(), AegisRenderer::new);
        e.registerEntityRenderer(ModEntities.STARFALL.get(), StarfallRenderer::new);
        e.registerEntityRenderer(ModEntities.GRAND.get(), GrandRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.SPARK.get(), s -> new Provider(s, style().size(0.16f, 0.1f).life(14, 8).gravity(0.05f, 0.88f).grow(-0.6f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.FLAME.get(), s -> new Provider(s, style().size(0.5f, 0.28f).life(18, 10).gravity(-0.012f, 0.9f).grow(-0.4f).fade(0.1f, 0.6f).spin(0.05f)));
        e.registerSpriteSet(ModParticles.EMBER.get(), s -> new Provider(s, style().size(0.12f, 0.08f).life(30, 16).gravity(-0.015f, 0.94f).grow(-0.5f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.STREAK.get(), s -> new Provider(s, style().size(0.9f, 0.4f).life(6, 2).gravity(0f, 1f).fade(0.05f, 0.7f).fixedRoll()));
        e.registerSpriteSet(ModParticles.STAR.get(), s -> new Provider(s, style().size(0.4f, 0.3f).life(24, 12).gravity(0f, 0.96f).grow(-0.4f).fade(0.1f, 0.5f).spin(0.1f)));
        e.registerSpriteSet(ModParticles.FLASH.get(), s -> new Provider(s, style().size(1.8f, 0.6f).life(5, 1).gravity(0f, 1f).grow(1.0f).fade(0.05f, 0.8f)));
        e.registerSpriteSet(ModParticles.SHARD.get(), s -> new Provider(s, style().size(0.2f, 0.14f).life(28, 14).gravity(0.06f, 0.96f).fade(0.05f, 0.5f).translucent().spin(0.25f)));
        e.registerSpriteSet(ModParticles.DUST.get(), s -> new Provider(s, style().size(1.6f, 0.8f).life(40, 20).gravity(-0.003f, 0.95f).grow(0.5f).fade(0.25f, 0.6f).translucent().alpha(0.55f)));
        e.registerSpriteSet(ModParticles.RING.get(), s -> new Provider(s, style().size(1.2f, 0f).life(10, 0).gravity(0f, 1f).grow(4.0f).fade(0.05f, 0.7f)));
        e.registerSpriteSet(ModParticles.METAL.get(), s -> new Provider(s, style().size(0.18f, 0.1f).life(8, 5).gravity(0.1f, 0.85f).grow(-0.5f).fade(0.05f, 0.5f)));
        e.registerSpriteSet(ModParticles.GLYPH.get(), s -> new Provider(s, style().size(1.0f, 0f).life(30, 0).gravity(0f, 1f).fade(0.2f, 0.3f).fixedRoll()));
    }
}
