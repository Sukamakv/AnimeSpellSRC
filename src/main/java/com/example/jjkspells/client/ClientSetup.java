package com.example.jjkspells.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEntities;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.client.particle.MoonRibbonParticle;
import com.example.jjkspells.client.particle.GlowParticle;
import com.example.jjkspells.client.particle.RedImpactProvider;
import com.example.jjkspells.client.particle.RingParticle;
import com.example.jjkspells.client.particle.VortexParticle;
import com.example.jjkspells.client.render.AzureColossusModel;
import com.example.jjkspells.client.render.AzureColossusRenderer;
import com.example.jjkspells.client.render.AzureOrbRenderer;
import com.example.jjkspells.client.render.LapseBlueRenderer;
import com.example.jjkspells.client.render.ReversalRedRenderer;
import com.example.jjkspells.client.render.UnlimitedVoidDomeRenderer;
import com.example.jjkspells.client.render.MeteorRenderer;
import com.example.jjkspells.client.render.TengaiFxRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.LAPSE_BLUE_ORB.get(), LapseBlueRenderer::new);
        event.registerEntityRenderer(ModEntities.LAEVATAIN_FX.get(), com.example.jjkspells.client.render.LaevatainFxRenderer::new);
        event.registerEntityRenderer(ModEntities.REVERSAL_RED.get(), ReversalRedRenderer::new);
        event.registerEntityRenderer(ModEntities.UNLIMITED_VOID_DOME.get(), UnlimitedVoidDomeRenderer::new);
        event.registerEntityRenderer(ModEntities.AZURE_COLOSSUS.get(), AzureColossusRenderer::new);
        event.registerEntityRenderer(ModEntities.AZURE_ORB.get(), AzureOrbRenderer::new);
        event.registerEntityRenderer(ModEntities.STORM_TORNADO.get(), StormTornadoRenderer::new);
        event.registerEntityRenderer(ModEntities.BARRAGE.get(), GaleBarrageRenderer::new);
        event.registerEntityRenderer(ModEntities.METEOR.get(), MeteorRenderer::new);
        event.registerEntityRenderer(ModEntities.TENGAI_FX.get(), TengaiFxRenderer::new);
        event.registerEntityRenderer(ModEntities.GATE_WEAPON.get(), com.example.jjkspells.client.render.GateWeaponRenderer::new);
        event.registerEntityRenderer(com.example.jjkspells.gil.GilEntities.FX.get(), com.example.jjkspells.client.render.GilFxRenderer::new);
        event.registerEntityRenderer(com.example.jjkspells.gil.GilEntities.VIMANA.get(), com.example.jjkspells.client.render.VimanaRenderer::new);
        event.registerEntityRenderer(com.example.jjkspells.enchant.FlameSlashRegistry.SLASH.get(), com.example.jjkspells.client.render.FlameSlashRenderer::new);
        event.registerEntityRenderer(ModEntities.EA_FX.get(), com.example.jjkspells.client.render.EaFxRenderer::new);
        event.registerEntityRenderer(ModEntities.JUSTICE_SWORD.get(), com.example.jjkspells.client.render.JusticeSwordRenderer::new);
        event.registerEntityRenderer(ModEntities.DEATH_WRAITH.get(), com.example.jjkspells.client.render.DeathWraithRenderer::new);
        event.registerEntityRenderer(ModEntities.THE_WORLD.get(), com.example.jjkspells.client.render.TheWorldRenderer::new);
        event.registerEntityRenderer(ModEntities.ROAD_ROLLER.get(), com.example.jjkspells.client.render.RoadRollerRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AzureColossusModel.LAYER, AzureColossusModel::createBodyLayer);
        event.registerLayerDefinition(com.example.jjkspells.client.render.TheWorldModel.LAYER, com.example.jjkspells.client.render.TheWorldModel::createBodyLayer);
        event.registerLayerDefinition(com.example.jjkspells.client.render.TheWorldModel.LAYER_STAR, com.example.jjkspells.client.render.TheWorldModel::createStarLayer);
        event.registerLayerDefinition(com.example.jjkspells.client.render.RoadRollerModel.LAYER, com.example.jjkspells.client.render.RoadRollerModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        // Malevolent Shrine
        event.registerSpriteSet(ModParticles.INK_SMOKE.get(), InkSmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BILLOW_SMOKE.get(), BillowSmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.SLASH_LINE.get(), SlashLineParticle.Provider::new);

        // Fuga
        event.registerSpriteSet(ModParticles.FUGA_FLAME.get(), s -> new FireParticle.Provider(s, FireParticle.FLAME));
        event.registerSpriteSet(ModParticles.FUGA_BLAST.get(), s -> new FireParticle.Provider(s, FireParticle.BLAST));
        event.registerSpriteSet(ModParticles.FUGA_EMBER.get(), s -> new FireParticle.Provider(s, FireParticle.EMBER));
        event.registerSpriteSet(ModParticles.FUGA_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.GLOW));
        event.registerSpriteSet(ModParticles.FUGA_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.RING_FLAT));
        event.registerSpriteSet(ModParticles.FUGA_RING.get(), s -> new FireParticle.Provider(s, FireParticle.RING));
        event.registerSpriteSet(ModParticles.FUGA_SMOKE.get(), s -> new FireParticle.Provider(s, FireParticle.SMOKE));
        event.registerSpriteSet(ModParticles.FUGA_LINE.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.LINE));
        event.registerSpriteSet(ModParticles.FUGA_BEAM.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.BEAM));
        // Hollow Purple
        event.registerSpriteSet(ModParticles.HP_CORE.get(), s -> new FireParticle.Provider(s, FireParticle.HP_CORE));
        event.registerSpriteSet(ModParticles.HP_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.HP_GLOW));
        event.registerSpriteSet(ModParticles.HP_GLOW_BLUE.get(), s -> new FireParticle.Provider(s, FireParticle.HP_GLOW_BLUE));
        event.registerSpriteSet(ModParticles.HP_GLOW_RED.get(), s -> new FireParticle.Provider(s, FireParticle.HP_GLOW_RED));
        event.registerSpriteSet(ModParticles.HP_WISP.get(), s -> new FireParticle.Provider(s, FireParticle.HP_WISP));
        event.registerSpriteSet(ModParticles.HP_WISP_BLUE.get(), s -> new FireParticle.Provider(s, FireParticle.HP_WISP_BLUE));
        event.registerSpriteSet(ModParticles.HP_WISP_RED.get(), s -> new FireParticle.Provider(s, FireParticle.HP_WISP_RED));
        event.registerSpriteSet(ModParticles.HP_RING.get(), s -> new FireParticle.Provider(s, FireParticle.HP_RING));
        event.registerSpriteSet(ModParticles.HP_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.HP_RING_FLAT));
        event.registerSpriteSet(ModParticles.HP_VOID.get(), s -> new FireParticle.Provider(s, FireParticle.HP_VOID));
        event.registerSpriteSet(ModParticles.HP_LINE.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.HP_LINE));

        // Unlimited Void / Simple Domain
        event.registerSpriteSet(ModParticles.UV_STAR.get(), s -> new FireParticle.Provider(s, FireParticle.UV_STAR));
        event.registerSpriteSet(ModParticles.UV_STAR_IN.get(), s -> new FireParticle.Provider(s, FireParticle.UV_STAR_IN));
        event.registerSpriteSet(ModParticles.UV_FLOOR.get(), s -> new FireParticle.Provider(s, FireParticle.UV_FLOOR));
        event.registerSpriteSet(ModParticles.UV_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.UV_GLOW));
        event.registerSpriteSet(ModParticles.UV_CORE.get(), s -> new FireParticle.Provider(s, FireParticle.UV_CORE));
        event.registerSpriteSet(ModParticles.UV_RING.get(), s -> new FireParticle.Provider(s, FireParticle.UV_RING));
        event.registerSpriteSet(ModParticles.UV_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.UV_RING_FLAT));
        event.registerSpriteSet(ModParticles.SD_STAR.get(), s -> new FireParticle.Provider(s, FireParticle.SD_STAR));
        event.registerSpriteSet(ModParticles.SD_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.SD_RING_FLAT));
        event.registerSpriteSet(ModParticles.UV_LINE.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.UV_LINE));

        // World Cutting Slash
        event.registerSpriteSet(ModParticles.WC_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.WC_GLOW));
        event.registerSpriteSet(ModParticles.WC_RING.get(), s -> new FireParticle.Provider(s, FireParticle.WC_RING));
        event.registerSpriteSet(ModParticles.WC_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.WC_RING_FLAT));
        event.registerSpriteSet(ModParticles.WC_INK.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.WC_INK));

        // The Ruler of Death's Crimson Wrath
        event.registerSpriteSet(ModParticles.CW_EYE.get(), CrimsonEyeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CW_GLINT.get(), s -> new FireParticle.Provider(s, FireParticle.CW_GLINT));
        event.registerSpriteSet(ModParticles.CW_LASER.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.CW_LASER));
        event.registerSpriteSet(ModParticles.GATE_PORTAL.get(), s -> new GatePortalParticle.Provider(s, 70, 10));
        event.registerSpriteSet(ModParticles.GATE_PORTAL_SMALL.get(), s -> new GatePortalParticle.Provider(s, 30, 6));
        event.registerSpriteSet(ModParticles.GOLD_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.GOLD_RING_FLAT));
        event.registerSpriteSet(ModParticles.GOLD_RING.get(), s -> new FireParticle.Provider(s, FireParticle.GOLD_RING));
        event.registerSpriteSet(ModParticles.GOLD_GLINT.get(), s -> new FireParticle.Provider(s, FireParticle.GOLD_GLINT));
        event.registerSpriteSet(ModParticles.GOLD_SPARK.get(), s -> new FireParticle.Provider(s, FireParticle.GOLD_SPARK));
        event.registerSpriteSet(ModParticles.GOLD_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.GOLD_GLOW));
        event.registerSpriteSet(ModParticles.GOLD_SHARD.get(), s -> new FireParticle.Provider(s, FireParticle.GOLD_SHARD));
        event.registerSpriteSet(ModParticles.GOLD_RUNE.get(), s -> new FireParticle.Provider(s, FireParticle.GOLD_RUNE));
        event.registerSpriteSet(ModParticles.EA_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.EA_GLOW));
        event.registerSpriteSet(ModParticles.GOLD_CHAIN.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.GOLD_CHAIN));
        event.registerSpriteSet(ModParticles.GOLD_LINE.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.GOLD_LINE));
        event.registerSpriteSet(ModParticles.EA_CRACK.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.EA_CRACK));
        event.registerSpriteSet(ModParticles.SUN_SPARK.get(), s -> new FireParticle.Provider(s, FireParticle.SUN_SPARK));
        event.registerSpriteSet(ModParticles.SUN_FLARE.get(), s -> new FireParticle.Provider(s, FireParticle.SUN_FLARE));
        event.registerSpriteSet(ModParticles.SUN_RING.get(), s -> new FireParticle.Provider(s, FireParticle.SUN_RING));
        event.registerSpriteSet(ModParticles.SUN_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.SUN_RING_FLAT));
        event.registerSpriteSet(ModParticles.SUN_EMBER.get(), s -> new FireParticle.Provider(s, FireParticle.SUN_EMBER));
        event.registerSpriteSet(ModParticles.SUN_CIRCLE.get(), s -> new FireParticle.Provider(s, FireParticle.SUN_CIRCLE));
        event.registerSpriteSet(ModParticles.SUN_FIGURE.get(), s -> new FireParticle.Provider(s, FireParticle.SUN_FIGURE));
        event.registerSpriteSet(ModParticles.SUN_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.SUN_GLOW));
        event.registerSpriteSet(ModParticles.SUN_BEAM.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.SUN_BEAM));
        event.registerSpriteSet(ModParticles.SUN_LINE.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.SUN_LINE));
        event.registerSpriteSet(ModParticles.SUN_BEAM_RED.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.SUN_BEAM_RED));
        // Darius + Garen
        event.registerSpriteSet(ModParticles.DR_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.DR_RING_FLAT));
        event.registerSpriteSet(ModParticles.DR_SHOCK.get(), s -> new FireParticle.Provider(s, FireParticle.DR_SHOCK));
        event.registerSpriteSet(ModParticles.DR_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.DR_GLOW));
        event.registerSpriteSet(ModParticles.DR_HIT.get(), s -> new FireParticle.Provider(s, FireParticle.DR_HIT));
        event.registerSpriteSet(ModParticles.DR_SPARK.get(), s -> new FireParticle.Provider(s, FireParticle.DR_SPARK));
        event.registerSpriteSet(ModParticles.DR_GATHER.get(), s -> new FireParticle.Provider(s, FireParticle.DR_GATHER));
        event.registerSpriteSet(ModParticles.DR_METAL.get(), s -> new FireParticle.Provider(s, FireParticle.DR_METAL));
        event.registerSpriteSet(ModParticles.DR_MARK.get(), s -> new FireParticle.Provider(s, FireParticle.DR_MARK));
        event.registerSpriteSet(ModParticles.DR_DUST.get(), s -> new FireParticle.Provider(s, FireParticle.DR_DUST));
        event.registerSpriteSet(ModParticles.DR_LINE.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.DR_LINE));
        event.registerSpriteSet(ModParticles.DR_CHAIN.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.DR_CHAIN));
        event.registerSpriteSet(ModParticles.GR_SPARK.get(), s -> new FireParticle.Provider(s, FireParticle.GR_SPARK));
        event.registerSpriteSet(ModParticles.GR_MOTE.get(), s -> new FireParticle.Provider(s, FireParticle.GR_MOTE));
        event.registerSpriteSet(ModParticles.GR_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.GR_GLOW));
        event.registerSpriteSet(ModParticles.GR_RING.get(), s -> new FireParticle.Provider(s, FireParticle.GR_RING));
        event.registerSpriteSet(ModParticles.GR_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.GR_RING_FLAT));
        event.registerSpriteSet(ModParticles.GG_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.GG_GLOW));
        event.registerSpriteSet(ModParticles.GG_RING.get(), s -> new FireParticle.Provider(s, FireParticle.GG_RING));
        event.registerSpriteSet(ModParticles.GG_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.GG_RING_FLAT));
        event.registerSpriteSet(ModParticles.GOLD_MOTE.get(), s -> new FireParticle.Provider(s, FireParticle.GOLD_MOTE));
        event.registerSpriteSet(ModParticles.GG_SHARD.get(), s -> new FireParticle.Provider(s, FireParticle.GG_SHARD));
        event.registerSpriteSet(ModParticles.GOLD_CREST.get(), s -> new FireParticle.Provider(s, FireParticle.GOLD_CREST));
        event.registerSpriteSet(ModParticles.GR_LINE.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.GR_LINE));
        event.registerSpriteSet(ModParticles.GOLD_BEAM.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.GOLD_BEAM));
        event.registerSpriteSet(ModParticles.CR_BEAM.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.CR_BEAM));
        event.registerSpriteSet(ModParticles.CR_CORE.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.CR_CORE));
        event.registerSpriteSet(ModParticles.GHOST_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.GHOST_GLOW));
        event.registerSpriteSet(ModParticles.CW_CIRCLE_OUTER.get(), CrimsonCircleParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CW_CIRCLE_INNER.get(), CrimsonCircleParticle.Provider::new);
        event.registerSpecial(ModParticles.CW_SKY.get(), (type, level, x, y, z, xd, yd, zd) -> new CrimsonAmbience.SignalParticle(level, x, y, z, (int) yd));

        // Gale Barrage
        event.registerSpriteSet(ModParticles.SLASH_SPARK.get(), SlashSparkParticle.Provider::new);
        event.registerSpriteSet(ModParticles.WIND_WISP.get(), WindWispParticle.Provider::new);

        // Storm Lord's Wrath
        event.registerSpriteSet(ModParticles.STORM_SPARK.get(), StormSparkParticle.Provider::new);
        event.registerSpriteSet(ModParticles.STORM_CLOUD.get(), StormCloudParticle.Provider::new);

        // Za Warudo: Time Stop
        event.registerSpriteSet(ModParticles.TS_FACE.get(), s -> new ClockCircleParticle.Provider(s, 0));
        event.registerSpriteSet(ModParticles.TS_RUNES.get(), s -> new ClockCircleParticle.Provider(s, 0));
        event.registerSpriteSet(ModParticles.TS_GEARS.get(), s -> new ClockCircleParticle.Provider(s, 0));
        event.registerSpriteSet(ModParticles.TS_RAYS.get(), s -> new ClockCircleParticle.Provider(s, 0));
        event.registerSpriteSet(ModParticles.TS_HAND_MIN.get(), s -> new ClockCircleParticle.Provider(s, 88));
        event.registerSpriteSet(ModParticles.TS_HAND_HR.get(), s -> new ClockCircleParticle.Provider(s, 88));
        event.registerSpriteSet(ModParticles.TS_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.TS_GLOW));
        event.registerSpriteSet(ModParticles.TS_MOTE.get(), s -> new FireParticle.Provider(s, FireParticle.TS_MOTE));
        event.registerSpriteSet(ModParticles.TS_STAR.get(), s -> new FireParticle.Provider(s, FireParticle.TS_STAR));
        event.registerSpriteSet(ModParticles.TS_GEAR.get(), s -> new FireParticle.Provider(s, FireParticle.TS_GEAR));
        event.registerSpriteSet(ModParticles.TS_RING.get(), s -> new FireParticle.Provider(s, FireParticle.TS_RING));
        event.registerSpriteSet(ModParticles.TS_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.TS_RING_FLAT));
        event.registerSpriteSet(ModParticles.TS_NUMERAL.get(), s -> new FireParticle.Provider(s, FireParticle.TS_NUMERAL));

        // Azure Colossus / Azure Orb Barrage
        event.registerSpriteSet(ModParticles.AZURE_FLAME.get(), s -> new FireParticle.Provider(s, FireParticle.AZURE_FLAME));
        event.registerSpriteSet(ModParticles.AZURE_BLAST.get(), s -> new FireParticle.Provider(s, FireParticle.AZURE_BLAST));
        event.registerSpriteSet(ModParticles.AZURE_AURA.get(), s -> new FireParticle.Provider(s, FireParticle.AZURE_AURA));
        event.registerSpriteSet(ModParticles.AZURE_EMBER.get(), s -> new FireParticle.Provider(s, FireParticle.AZURE_EMBER));
        event.registerSpriteSet(ModParticles.AZURE_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.AZURE_GLOW));
        event.registerSpriteSet(ModParticles.AZURE_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.AZURE_RING_FLAT));
        event.registerSpriteSet(ModParticles.AZURE_RING.get(), s -> new FireParticle.Provider(s, FireParticle.AZURE_RING));

        // Martial arts
        event.registerSpriteSet(ModParticles.SP_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.SP_GLOW));
        event.registerSpriteSet(ModParticles.SP_HIT.get(), s -> new FireParticle.Provider(s, FireParticle.SP_HIT));
        event.registerSpriteSet(ModParticles.SP_SPARK.get(), s -> new FireParticle.Provider(s, FireParticle.SP_SPARK));
        event.registerSpriteSet(ModParticles.SP_RING.get(), s -> new FireParticle.Provider(s, FireParticle.SP_RING));
        event.registerSpriteSet(ModParticles.SP_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.SP_RING_FLAT));
        event.registerSpriteSet(ModParticles.SP_FIST.get(), s -> new FireParticle.Provider(s, FireParticle.SP_FIST));
        event.registerSpriteSet(ModParticles.SP_STAR.get(), s -> new FireParticle.Provider(s, FireParticle.SP_STAR));
        event.registerSpriteSet(ModParticles.SP_LINE.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.SP_LINE));
        event.registerSpriteSet(ModParticles.SP_BEAM.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.SP_BEAM));
        event.registerSpriteSet(ModParticles.AT_BLOOD.get(), s -> new FireParticle.Provider(s, FireParticle.AT_BLOOD));
        event.registerSpriteSet(ModParticles.AT_EMBER.get(), s -> new FireParticle.Provider(s, FireParticle.AT_EMBER));
        event.registerSpriteSet(ModParticles.AT_SMOKE.get(), s -> new FireParticle.Provider(s, FireParticle.AT_SMOKE));
        event.registerSpriteSet(ModParticles.AT_GHOST.get(), s -> new FireParticle.Provider(s, FireParticle.AT_GHOST));
        event.registerSpriteSet(ModParticles.AT_CIRCLE.get(), s -> new FireParticle.Provider(s, FireParticle.AT_CIRCLE));
        event.registerSpriteSet(ModParticles.MA_IMPACT.get(), s -> new FireParticle.Provider(s, FireParticle.MA_IMPACT));
        event.registerSpriteSet(ModParticles.MA_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.MA_GLOW));
        event.registerSpriteSet(ModParticles.MA_FIST.get(), s -> new FireParticle.Provider(s, FireParticle.MA_FIST));
        event.registerSpriteSet(ModParticles.MA_SHOCK_RING.get(), s -> new FireParticle.Provider(s, FireParticle.MA_SHOCK_RING));
        event.registerSpriteSet(ModParticles.MA_SHOCK_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.MA_SHOCK_RING_FLAT));
        event.registerSpriteSet(ModParticles.MA_CRACKS.get(), s -> new FireParticle.Provider(s, FireParticle.MA_CRACKS));
        event.registerSpriteSet(ModParticles.MA_DUST.get(), s -> new FireParticle.Provider(s, FireParticle.MA_DUST));
        event.registerSpriteSet(ModParticles.MA_STAR.get(), s -> new FireParticle.Provider(s, FireParticle.MA_STAR));
        event.registerSpriteSet(ModParticles.MA_SPARK.get(), s -> new FireParticle.Provider(s, FireParticle.MA_SPARK));
        event.registerSpriteSet(ModParticles.MA_LINE.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.MA_LINE));
        event.registerSpriteSet(ModParticles.MA_LINE_HEAVY.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.MA_LINE_HEAVY));

        // Moon Breathing
        event.registerSpriteSet(ModParticles.MOON_RIBBON.get(), MoonRibbonParticle.Provider::new);
        event.registerSpriteSet(ModParticles.MOON_BURST.get(), s -> new FireParticle.Provider(s, FireParticle.MOON_BURST));
        event.registerSpriteSet(ModParticles.MOON_FLASH.get(), s -> new FireParticle.Provider(s, FireParticle.MOON_FLASH));
        event.registerSpriteSet(ModParticles.MOON_HIT.get(), s -> new FireParticle.Provider(s, FireParticle.MOON_HIT));
        event.registerSpriteSet(ModParticles.MOON_SPARK.get(), s -> new FireParticle.Provider(s, FireParticle.MOON_SPARK));

        // Lapse: Blue / Reversal: Red
        event.registerSpriteSet(ModParticles.BLUE_VORTEX.get(), VortexParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLUE_SPARK.get(), GlowParticle.SparkProvider::new);
        event.registerSpriteSet(ModParticles.RED_SPARK.get(), GlowParticle.SparkProvider::new);
        event.registerSpriteSet(ModParticles.RED_RING.get(), RingParticle.Provider::new);
        event.registerSpriteSet(ModParticles.RED_IMPACT.get(), RedImpactProvider::new);

        // Viego
        event.registerSpriteSet(ModParticles.VG_RING_FLAT.get(), s -> new FireParticle.Provider(s, FireParticle.VG_RING_FLAT));
        event.registerSpriteSet(ModParticles.VG_SHOCK.get(), s -> new FireParticle.Provider(s, FireParticle.VG_SHOCK));
        event.registerSpriteSet(ModParticles.VG_GLOW.get(), s -> new FireParticle.Provider(s, FireParticle.VG_GLOW));
        event.registerSpriteSet(ModParticles.VG_HIT.get(), s -> new FireParticle.Provider(s, FireParticle.VG_HIT));
        event.registerSpriteSet(ModParticles.VG_SPARK.get(), s -> new FireParticle.Provider(s, FireParticle.VG_SPARK));
        event.registerSpriteSet(ModParticles.VG_GATHER.get(), s -> new FireParticle.Provider(s, FireParticle.VG_GATHER));
        event.registerSpriteSet(ModParticles.VG_FLAME.get(), s -> new FireParticle.Provider(s, FireParticle.VG_FLAME));
        event.registerSpriteSet(ModParticles.VG_MIST.get(), s -> new FireParticle.Provider(s, FireParticle.VG_MIST));
        event.registerSpriteSet(ModParticles.VG_CIRCLE.get(), s -> new FireParticle.Provider(s, FireParticle.VG_CIRCLE));
        event.registerSpriteSet(ModParticles.VG_SOUL.get(), s -> new FireParticle.Provider(s, FireParticle.VG_SOUL));
        event.registerSpriteSet(ModParticles.VG_SHARD.get(), s -> new FireParticle.Provider(s, FireParticle.VG_SHARD));
        event.registerSpriteSet(ModParticles.VG_LINE.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.VG_LINE));
        event.registerSpriteSet(ModParticles.VG_TENDRIL.get(), s -> new FugaLineParticle.Provider(s, FugaLineParticle.VG_TENDRIL));
    }
}
