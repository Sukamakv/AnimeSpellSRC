package com.example.jjkspells.diablo.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.diablo.client.particle.DiaParticle.Provider;
import com.example.jjkspells.diablo.client.render.NegationRenderer;
import com.example.jjkspells.diablo.client.render.SeqRenderers;
import com.example.jjkspells.diablo.registry.ModEntities;
import com.example.jjkspells.diablo.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.example.jjkspells.diablo.client.particle.DiaParticle.style;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.NETHER.get(), SeqRenderers.Nether::new);
        e.registerEntityRenderer(ModEntities.DOMINION.get(), SeqRenderers.Dominion::new);
        e.registerEntityRenderer(ModEntities.FUNERAL.get(), SeqRenderers.Funeral::new);
        e.registerEntityRenderer(ModEntities.NEGATION.get(), NegationRenderer::new);
        e.registerEntityRenderer(ModEntities.DESPAIR.get(), SeqRenderers.Despair::new);
        e.registerEntityRenderer(ModEntities.WORLD.get(), SeqRenderers.World::new);
        e.registerEntityRenderer(ModEntities.GENESIS.get(), SeqRenderers.Genesis::new);
        e.registerEntityRenderer(ModEntities.AZAZEL.get(), SeqRenderers.Azazel::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.FLAME.get(), s -> new Provider(s, style().size(0.5f, 0.25f).life(26, 12).gravity(-0.01f, 0.94f).grow(-0.5f).fade(0.05f, 0.6f).spin(0.03f)));
        e.registerSpriteSet(ModParticles.MOTE.get(), s -> new Provider(s, style().size(0.16f, 0.08f).life(22, 10).gravity(-0.004f, 0.95f).grow(-0.4f).fade(0.1f, 0.6f)));
        e.registerSpriteSet(ModParticles.EMBER.get(), s -> new Provider(s, style().size(0.14f, 0.07f).life(30, 14).gravity(0.012f, 0.92f).grow(-0.5f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.RUNE.get(), s -> new Provider(s, style().size(0.3f, 0.12f).life(26, 12).gravity(-0.006f, 0.94f).fade(0.15f, 0.6f).spin(0.1f)));
        e.registerSpriteSet(ModParticles.CRACK.get(), s -> new Provider(s, style().size(0.9f, 0.4f).life(18, 8).gravity(0f, 1f).grow(0.3f).fade(0.1f, 0.6f).spin(0.02f)));
        e.registerSpriteSet(ModParticles.ASH.get(), s -> new Provider(s, style().size(0.28f, 0.14f).life(40, 16).gravity(0.01f, 0.95f).fade(0.05f, 0.6f).translucent().spin(0.12f)));
        e.registerSpriteSet(ModParticles.SOUL.get(), s -> new Provider(s, style().size(0.4f, 0.16f).life(30, 14).gravity(-0.01f, 0.95f).fade(0.15f, 0.6f).spin(0.04f)));
        e.registerSpriteSet(ModParticles.FLASH.get(), s -> new Provider(s, style().size(2.0f, 0.6f).life(5, 1).gravity(0f, 1f).grow(1.0f).fade(0.05f, 0.8f)));
        e.registerSpriteSet(ModParticles.RING.get(), s -> new Provider(s, style().size(1.2f, 0f).life(10, 0).gravity(0f, 1f).grow(5.0f).fade(0.05f, 0.7f)));
        e.registerSpriteSet(ModParticles.GLOW.get(), s -> new Provider(s, style().size(0.9f, 0.3f).life(12, 6).gravity(0f, 1f).grow(0.5f).fade(0.1f, 0.7f)));
        e.registerSpriteSet(ModParticles.STREAK.get(), s -> new Provider(s, style().size(0.5f, 0.2f).life(8, 4).gravity(0f, 0.9f).grow(-0.8f).fade(0.05f, 0.7f)));
    }
}
