package com.example.jjkspells.afterlife.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.afterlife.client.particle.AfterParticle;
import com.example.jjkspells.afterlife.client.particle.AfterParticle.Provider;
import com.example.jjkspells.afterlife.client.render.FuneralRenderer;
import com.example.jjkspells.afterlife.client.render.GhostRenderer;
import com.example.jjkspells.afterlife.client.render.NoopRenderer;
import com.example.jjkspells.afterlife.registry.ModEntities;
import com.example.jjkspells.afterlife.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.example.jjkspells.afterlife.client.particle.AfterParticle.style;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.GHOST.get(), GhostRenderer::new);
        e.registerEntityRenderer(ModEntities.FUNERAL.get(), FuneralRenderer::new);
        e.registerEntityRenderer(ModEntities.DASH.get(), NoopRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        // butterflies: recognisable wings, four flapping frames, drifting upward with a flutter
        AfterParticle.Style bf = style().size(0.3f, 0.12f).life(50, 30).gravity(0f, 0.96f).fade(0.12f, 0.3f).translucent().flap().flutter();
        e.registerSpriteSet(ModParticles.BUTTERFLY_RED.get(), s -> new Provider(s, bf));
        e.registerSpriteSet(ModParticles.BUTTERFLY_ORANGE.get(), s -> new Provider(s, bf));
        e.registerSpriteSet(ModParticles.BUTTERFLY_GOLD.get(), s -> new Provider(s, bf));
        e.registerSpriteSet(ModParticles.EMBER.get(), s -> new Provider(s, style().size(0.12f, 0.1f).life(20, 16).gravity(-0.05f, 0.92f).grow(-0.6f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.PETAL.get(), s -> new Provider(s, style().size(0.2f, 0.08f).life(30, 16).gravity(0.02f, 0.93f).fade(0.05f, 0.5f).translucent().spin(0.16f)));
        e.registerSpriteSet(ModParticles.SPIRIT_FLAME.get(), s -> new Provider(s, style().size(0.55f, 0.25f).life(14, 8).gravity(-0.05f, 0.9f).grow(-0.5f).fade(0.1f, 0.7f).spin(0.05f)));
        e.registerSpriteSet(ModParticles.WISP.get(), s -> new Provider(s, style().size(0.5f, 0.3f).life(28, 16).gravity(-0.02f, 0.95f).grow(0.3f).fade(0.3f, 0.6f)));
        e.registerSpriteSet(ModParticles.BLOSSOM.get(), s -> new Provider(s, style().size(0.6f, 0f).life(9, 0).gravity(0f, 1f).grow(0.7f).fade(0.15f, 0.5f)));
        e.registerSpriteSet(ModParticles.SLASH.get(), s -> new Provider(s, style().size(1.9f, 0.6f).life(6, 1).gravity(0f, 1f).grow(0.25f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.FLASH.get(), s -> new Provider(s, style().size(1.3f, 0.4f).life(5, 1).gravity(0f, 1f).grow(1.2f).fade(0.05f, 0.8f)));
        e.registerSpriteSet(ModParticles.AFTERIMAGE.get(), s -> new Provider(s, style().size(2.3f, 0f).life(12, 0).gravity(0f, 1f).grow(0.05f).fade(0.2f, 0.7f).fixedRoll()));
        e.registerSpriteSet(ModParticles.LANTERN.get(), s -> new Provider(s, style().size(0.55f, 0.1f).life(70, 30).gravity(-0.004f, 0.98f).fade(0.15f, 0.3f).translucent().fixedRoll().sway()));
        e.registerSpriteSet(ModParticles.TALISMAN.get(), s -> new Provider(s, style().size(0.6f, 0.1f).life(90, 30).gravity(-0.0005f, 0.97f).fade(0.2f, 0.3f).translucent().fixedRoll().sway()));
    }
}
