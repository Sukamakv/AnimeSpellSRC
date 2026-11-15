package com.example.jjkspells.acheron.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.acheron.client.particle.AchParticle.Provider;
import com.example.jjkspells.acheron.client.render.SeqRenderers;
import com.example.jjkspells.acheron.registry.ModEntities;
import com.example.jjkspells.acheron.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.example.jjkspells.acheron.client.particle.AchParticle.style;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.WILTCROSS.get(), SeqRenderers.Wilt::new);
        e.registerEntityRenderer(ModEntities.OCTOBOLT.get(), SeqRenderers.Octo::new);
        e.registerEntityRenderer(ModEntities.QUAD.get(), SeqRenderers.Quad::new);
        e.registerEntityRenderer(ModEntities.RAINLEAF.get(), SeqRenderers.Rainleaf::new);
        e.registerEntityRenderer(ModEntities.RAINBLADE.get(), SeqRenderers.Blade::new);
        e.registerEntityRenderer(ModEntities.STYGIAN.get(), SeqRenderers.Stygian::new);
        e.registerEntityRenderer(ModEntities.ULTIMATE.get(), SeqRenderers.Ultimate::new);
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.PETAL.get(), s -> new Provider(s, style().size(0.22f, 0.1f).life(60, 30).gravity(0.03f, 0.96f).fade(0.08f, 0.4f).translucent().spin(0.12f)));
        e.registerSpriteSet(ModParticles.SPARK.get(), s -> new Provider(s, style().size(0.14f, 0.1f).life(10, 8).gravity(0.1f, 0.85f).grow(-0.6f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.ARC_V.get(), s -> new Provider(s, style().size(0.45f, 0.3f).life(5, 3).gravity(0f, 1f).fade(0.1f, 0.7f)));
        e.registerSpriteSet(ModParticles.ARC_R.get(), s -> new Provider(s, style().size(0.45f, 0.3f).life(5, 3).gravity(0f, 1f).fade(0.1f, 0.7f)));
        e.registerSpriteSet(ModParticles.MIST.get(), s -> new Provider(s, style().size(1.2f, 0.8f).life(40, 20).gravity(-0.004f, 0.96f).grow(0.6f).fade(0.3f, 0.6f).translucent().alpha(0.55f)));
        e.registerSpriteSet(ModParticles.SLASH_V.get(), s -> new Provider(s, style().size(1.9f, 0.5f).life(6, 2).gravity(0f, 1f).grow(0.25f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.SLASH_R.get(), s -> new Provider(s, style().size(1.9f, 0.5f).life(6, 2).gravity(0f, 1f).grow(0.25f).fade(0.05f, 0.6f)));
        e.registerSpriteSet(ModParticles.CUT.get(), s -> new Provider(s, style().size(2.4f, 1.2f).life(6, 3).gravity(0f, 1f).grow(0.2f).fade(0.05f, 0.7f)));
        e.registerSpriteSet(ModParticles.SHARD.get(), s -> new Provider(s, style().size(0.18f, 0.12f).life(24, 14).gravity(0.05f, 0.95f).fade(0.05f, 0.5f).translucent().spin(0.25f)));
        e.registerSpriteSet(ModParticles.AFTER_V.get(), s -> new Provider(s, style().size(2.3f, 0f).life(12, 0).gravity(0f, 1f).grow(0.05f).fade(0.2f, 0.7f).fixedRoll()));
        e.registerSpriteSet(ModParticles.AFTER_R.get(), s -> new Provider(s, style().size(2.3f, 0f).life(12, 0).gravity(0f, 1f).grow(0.05f).fade(0.2f, 0.7f).fixedRoll()));
        e.registerSpriteSet(ModParticles.MARK_V.get(), s -> new Provider(s, style().size(0.95f, 0f).life(6, 0).gravity(0f, 1f).fade(0.2f, 0.4f).fixedRoll()));
        e.registerSpriteSet(ModParticles.MARK_R.get(), s -> new Provider(s, style().size(1.0f, 0f).life(5, 0).gravity(0f, 1f).fade(0.2f, 0.4f).fixedRoll()));
        e.registerSpriteSet(ModParticles.MARK_F.get(), s -> new Provider(s, style().size(0.95f, 0f).life(6, 0).gravity(0f, 1f).fade(0.2f, 0.4f).fixedRoll()));
        e.registerSpriteSet(ModParticles.FLASH_V.get(), s -> new Provider(s, style().size(1.4f, 0.4f).life(5, 1).gravity(0f, 1f).grow(1.0f).fade(0.05f, 0.8f)));
        e.registerSpriteSet(ModParticles.FLASH_R.get(), s -> new Provider(s, style().size(1.4f, 0.4f).life(5, 1).gravity(0f, 1f).grow(1.0f).fade(0.05f, 0.8f)));
        e.registerSpriteSet(ModParticles.EMBER.get(), s -> new Provider(s, style().size(0.12f, 0.08f).life(20, 14).gravity(-0.02f, 0.94f).grow(-0.5f).fade(0.05f, 0.6f)));
    }
}
