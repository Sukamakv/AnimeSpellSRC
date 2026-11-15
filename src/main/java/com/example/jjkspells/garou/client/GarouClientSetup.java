package com.example.jjkspells.garou.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.client.particle.GarouParticle.Provider;
import com.example.jjkspells.garou.client.render.AllLifeRenderer;
import com.example.jjkspells.garou.client.render.NuclearFissionRenderer;
import com.example.jjkspells.garou.registry.NfEntities;
import com.example.jjkspells.garou.registry.NfParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.example.jjkspells.garou.client.particle.GarouParticle.style;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GarouClientSetup {
    private static final float[] HOT = {1.0f, 1.0f, 1.0f}, COOL = {0.55f, 0.32f, 0.2f};
    private static final float[] SMOKE_A = {1.0f, 0.72f, 0.5f}, SMOKE_B = {0.3f, 0.28f, 0.27f};

    private GarouClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(NfEntities.NUCLEAR_FISSION.get(), NuclearFissionRenderer::new);
        e.registerEntityRenderer(NfEntities.ALL_LIFE.get(), AllLifeRenderer::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(NfParticles.FIRE.get(), s -> new Provider(s, style().size(1.5f, 1.6f).life(24, 14).gravity(-0.003f, 0.93f).grow(0.9f).fade(0.08f, 0.55f).spin(0.02f).animated().tint(HOT, COOL)));
        e.registerSpriteSet(NfParticles.SMOKE.get(), s -> new Provider(s, style().size(2.2f, 2.4f).life(70, 30).gravity(-0.0008f, 0.965f).grow(1.2f).fade(0.12f, 0.6f).spin(0.01f).translucent().alpha(0.8f).tint(SMOKE_A, SMOKE_B)));
        e.registerSpriteSet(NfParticles.SPARK.get(), s -> new Provider(s, style().size(0.5f, 0.4f).life(18, 10).gravity(0.004f, 0.93f).grow(-0.7f).fade(0.05f, 0.5f)));
        e.registerSpriteSet(NfParticles.EMBER.get(), s -> new Provider(s, style().size(0.2f, 0.14f).life(50, 30).gravity(0.012f, 0.96f).grow(-0.3f).fade(0.1f, 0.5f)));
        e.registerSpriteSet(NfParticles.DEBRIS.get(), s -> new Provider(s, style().size(0.35f, 0.3f).life(30, 20).gravity(0.08f, 0.98f).fade(0f, 0.25f).spin(0.3f).translucent()));
        e.registerSpriteSet(NfParticles.GLOW.get(), s -> new Provider(s, style().size(3.0f, 0f).life(5, 2).gravity(0f, 1f).grow(0.6f).fade(0.1f, 0.6f)));
    }
}
