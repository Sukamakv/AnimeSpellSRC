package com.example.jjkspells.zegion.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.zegion.client.particle.ZegParticle.Provider;
import com.example.jjkspells.zegion.client.render.*;
import com.example.jjkspells.zegion.registry.ModEntities;
import com.example.jjkspells.zegion.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.example.jjkspells.zegion.client.particle.ZegParticle.style;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.IMPACT.get(), ImpactRenderer::new);
        e.registerEntityRenderer(ModEntities.DOMAIN.get(), DomainRenderer::new);
        e.registerEntityRenderer(ModEntities.OBSIDIAN.get(), ObsidianRenderer::new);
        e.registerEntityRenderer(ModEntities.ASSAULT.get(), AssaultRenderer::new);
        e.registerEntityRenderer(ModEntities.LABYRINTH.get(), LabyrinthRenderer::new);
        e.registerEntityRenderer(ModEntities.SINGULARITY.get(), SingularityRenderer::new);
        e.registerEntityRenderer(ModEntities.SOVEREIGN.get(), SovereignRenderer::new);
        e.registerEntityRenderer(ModEntities.MEPHISTO.get(), MephistoRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.MIST.get(), s -> new Provider(s, style().size(2.4f, 1.0f).life(48, 22).gravity(-0.004f, 0.95f).grow(0.8f).fade(0.2f, 0.7f).translucent().alpha(0.55f).spin(0.02f)));
        e.registerSpriteSet(ModParticles.VOID.get(), s -> new Provider(s, style().size(0.6f, 0.3f).life(28, 12).gravity(-0.002f, 0.94f).grow(-0.4f).fade(0.1f, 0.6f).translucent().alpha(0.9f).spin(0.05f)));
        e.registerSpriteSet(ModParticles.SPARK.get(), s -> new Provider(s, style().size(0.16f, 0.09f).life(16, 9).gravity(0.02f, 0.9f).grow(-0.6f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.GLOW.get(), s -> new Provider(s, style().size(1.0f, 0.3f).life(14, 6).gravity(0f, 1f).grow(0.5f).fade(0.1f, 0.7f)));
        e.registerSpriteSet(ModParticles.FLASH.get(), s -> new Provider(s, style().size(2.6f, 0.8f).life(6, 1).gravity(0f, 1f).grow(1.2f).fade(0.05f, 0.8f)));
        e.registerSpriteSet(ModParticles.RING.get(), s -> new Provider(s, style().size(1.2f, 0f).life(12, 0).gravity(0f, 1f).grow(6.0f).fade(0.05f, 0.7f)));
        e.registerSpriteSet(ModParticles.SHARD.get(), s -> new Provider(s, style().size(0.26f, 0.14f).life(30, 14).gravity(0.035f, 0.96f).fade(0.02f, 0.5f).translucent().spin(0.3f)));
        e.registerSpriteSet(ModParticles.STAR.get(), s -> new Provider(s, style().size(0.36f, 0.2f).life(18, 8).gravity(-0.002f, 0.93f).grow(-0.5f).fade(0.05f, 0.6f).spin(0.1f)));
        e.registerSpriteSet(ModParticles.RUNE.get(), s -> new Provider(s, style().size(0.5f, 0.2f).life(34, 14).gravity(-0.01f, 0.95f).grow(0.3f).fade(0.1f, 0.6f).spin(0.04f)));
        e.registerSpriteSet(ModParticles.EMBER.get(), s -> new Provider(s, style().size(0.12f, 0.08f).life(32, 16).gravity(-0.012f, 0.94f).grow(-0.5f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.MIRROR.get(), s -> new Provider(s, style().size(0.34f, 0.2f).life(34, 16).gravity(0.02f, 0.96f).fade(0.02f, 0.5f).translucent().spin(0.25f)));
        e.registerSpriteSet(ModParticles.CRACK.get(), s -> new Provider(s, style().size(0.9f, 0.5f).life(18, 8).gravity(0f, 1f).grow(0.4f).fade(0.05f, 0.6f).spin(0.02f)));
    }
}
