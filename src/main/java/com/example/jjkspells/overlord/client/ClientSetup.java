package com.example.jjkspells.overlord.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.overlord.client.model.BlackGoatModel;
import com.example.jjkspells.overlord.client.particle.OverlordParticle;
import com.example.jjkspells.overlord.client.render.GoatRenderer;
import com.example.jjkspells.overlord.client.render.OverlordFxRenderer;
import com.example.jjkspells.overlord.client.render.UndeadSoldierRenderer;
import com.example.jjkspells.overlord.registry.ModEntities;
import com.example.jjkspells.overlord.registry.ModParticles;
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
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(BlackGoatModel.LAYER, BlackGoatModel::createLayer);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.FX.get(), OverlordFxRenderer::new);
        e.registerEntityRenderer(ModEntities.UNDEAD_SOLDIER.get(), UndeadSoldierRenderer::new);
        e.registerEntityRenderer(ModEntities.BLACK_GOAT.get(), ctx -> new GoatRenderer<>(ctx, 1.0f));
        e.registerEntityRenderer(ModEntities.GOAT_SPAWN.get(), ctx -> new GoatRenderer<>(ctx, 0.38f));
    }

    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModParticles.SOUL.get(), s -> new OverlordParticle.Provider(s, OverlordParticle.SOUL));
        e.registerSpriteSet(ModParticles.BONE.get(), s -> new OverlordParticle.Provider(s, OverlordParticle.BONE));
        e.registerSpriteSet(ModParticles.RUNE.get(), s -> new OverlordParticle.Provider(s, OverlordParticle.RUNE));
        e.registerSpriteSet(ModParticles.SMOKE.get(), s -> new OverlordParticle.Provider(s, OverlordParticle.SMOKE));
        e.registerSpriteSet(ModParticles.GOLD.get(), s -> new OverlordParticle.Provider(s, OverlordParticle.GOLD));
        e.registerSpriteSet(ModParticles.VOID.get(), s -> new OverlordParticle.Provider(s, OverlordParticle.VOID));
    }
}
