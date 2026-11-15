package com.example.jjkspells.malphite.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.malphite.client.particle.MalParticle.Provider;
import com.example.jjkspells.malphite.client.render.ClapRenderer;
import com.example.jjkspells.malphite.client.render.ForceRenderer;
import com.example.jjkspells.malphite.client.render.GraniteRenderer;
import com.example.jjkspells.malphite.client.render.ShardRenderer;
import com.example.jjkspells.malphite.client.render.StrikesRenderer;
import com.example.jjkspells.malphite.registry.ModEntities;
import com.example.jjkspells.malphite.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.example.jjkspells.malphite.client.particle.MalParticle.style;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.SHARD.get(), ShardRenderer::new);
        e.registerEntityRenderer(ModEntities.CLAP.get(), ClapRenderer::new);
        e.registerEntityRenderer(ModEntities.STRIKES.get(), StrikesRenderer::new);
        e.registerEntityRenderer(ModEntities.GRANITE.get(), GraniteRenderer::new);
        e.registerEntityRenderer(ModEntities.FORCE.get(), ForceRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.DUST.get(), s -> new Provider(s, style().size(1.5f, 0.7f).life(34, 16).gravity(-0.004f, 0.94f).grow(0.7f).fade(0.15f, 0.65f).translucent().alpha(0.6f).spin(0.03f)));
        e.registerSpriteSet(ModParticles.SMOKE.get(), s -> new Provider(s, style().size(2.2f, 0.9f).life(46, 20).gravity(-0.006f, 0.95f).grow(0.8f).fade(0.2f, 0.7f).translucent().alpha(0.5f).spin(0.02f)));
        e.registerSpriteSet(ModParticles.GRAVEL.get(), s -> new Provider(s, style().size(0.14f, 0.08f).life(30, 14).gravity(0.07f, 0.96f).fade(0.02f, 0.4f).translucent().spin(0.3f)));
        e.registerSpriteSet(ModParticles.ROCK.get(), s -> new Provider(s, style().size(0.3f, 0.16f).life(36, 16).gravity(0.07f, 0.97f).fade(0.02f, 0.4f).translucent().spin(0.2f)));
        e.registerSpriteSet(ModParticles.SHARD.get(), s -> new Provider(s, style().size(0.22f, 0.12f).life(26, 12).gravity(0.06f, 0.96f).fade(0.02f, 0.5f).translucent().spin(0.3f)));
        e.registerSpriteSet(ModParticles.SPARK.get(), s -> new Provider(s, style().size(0.14f, 0.08f).life(14, 8).gravity(0.04f, 0.88f).grow(-0.6f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.EMBER.get(), s -> new Provider(s, style().size(0.12f, 0.08f).life(32, 16).gravity(-0.012f, 0.94f).grow(-0.5f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.FLASH.get(), s -> new Provider(s, style().size(2.0f, 0.6f).life(5, 1).gravity(0f, 1f).grow(1.0f).fade(0.05f, 0.8f)));
        e.registerSpriteSet(ModParticles.RING.get(), s -> new Provider(s, style().size(1.2f, 0f).life(10, 0).gravity(0f, 1f).grow(5.0f).fade(0.05f, 0.7f)));
        e.registerSpriteSet(ModParticles.GLOW.get(), s -> new Provider(s, style().size(0.9f, 0.3f).life(12, 6).gravity(0f, 1f).grow(0.5f).fade(0.1f, 0.7f)));
    }
}
