package com.example.jjkspells.vergil;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.vergil.entity.CrescentWaveEntity;
import com.example.jjkspells.vergil.entity.DevilTriggerEntity;
import com.example.jjkspells.vergil.entity.DimensionRushEntity;
import com.example.jjkspells.vergil.entity.DimensionSeverEntity;
import com.example.jjkspells.vergil.entity.HelmBreakerEntity;
import com.example.jjkspells.vergil.entity.JudgementCutEntity;
import com.example.jjkspells.vergil.entity.SpiralSwordsEntity;
import com.example.jjkspells.vergil.spell.CrescentWaveSpell;
import com.example.jjkspells.vergil.spell.DevilTriggerSpell;
import com.example.jjkspells.vergil.spell.DimensionRushSpell;
import com.example.jjkspells.vergil.spell.DimensionSeverSpell;
import com.example.jjkspells.vergil.spell.HelmBreakerSpell;
import com.example.jjkspells.vergil.spell.JudgementCutSpell;
import com.example.jjkspells.vergil.spell.SpiralSwordsSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Self-contained registration for the Vergil (DMC5) spell pack. No existing file has to be edited. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VergilRegistry {
    public static final String CUT_ID = "vergil_judgement_cut_zone";
    public static EntityType<JudgementCutEntity> CUT;

    public static final String SWORDS_ID = "vergil_spiral_swords_zone";
    public static EntityType<SpiralSwordsEntity> SWORDS;

    public static final String RUSH_ID = "vergil_dimension_rush_path";
    public static EntityType<DimensionRushEntity> RUSH;

    public static final String TRIGGER_ID = "vergil_devil_trigger_form";
    public static EntityType<DevilTriggerEntity> TRIGGER;

    public static final String HELM_ID = "vergil_helm_breaker_impact";
    public static EntityType<HelmBreakerEntity> HELM;

    public static final String WAVE_ID = "vergil_crescent_wave_zone";
    public static EntityType<CrescentWaveEntity> WAVE_E;

    public static final String SEVER_ID = "vergil_dimension_sever_zone";
    public static EntityType<DimensionSeverEntity> SEVER;

    private VergilRegistry() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            CUT = EntityType.Builder.<JudgementCutEntity>of(JudgementCutEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(CUT_ID).toString());
            helper.register(JJKSpells.id(CUT_ID), CUT);
        });
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            SWORDS = EntityType.Builder.<SpiralSwordsEntity>of(SpiralSwordsEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(SWORDS_ID).toString());
            helper.register(JJKSpells.id(SWORDS_ID), SWORDS);
        });
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            RUSH = EntityType.Builder.<DimensionRushEntity>of(DimensionRushEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(RUSH_ID).toString());
            helper.register(JJKSpells.id(RUSH_ID), RUSH);
        });
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            TRIGGER = EntityType.Builder.<DevilTriggerEntity>of(DevilTriggerEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(TRIGGER_ID).toString());
            helper.register(JJKSpells.id(TRIGGER_ID), TRIGGER);
        });
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            HELM = EntityType.Builder.<HelmBreakerEntity>of(HelmBreakerEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(HELM_ID).toString());
            helper.register(JJKSpells.id(HELM_ID), HELM);
        });
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            WAVE_E = EntityType.Builder.<CrescentWaveEntity>of(CrescentWaveEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(WAVE_ID).toString());
            helper.register(JJKSpells.id(WAVE_ID), WAVE_E);
        });
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            SEVER = EntityType.Builder.<DimensionSeverEntity>of(DimensionSeverEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(SEVER_ID).toString());
            helper.register(JJKSpells.id(SEVER_ID), SEVER);
        });
        event.register(SpellRegistry.SPELL_REGISTRY_KEY, helper -> {
            helper.register(JJKSpells.id(JudgementCutSpell.NAME), new JudgementCutSpell());
            helper.register(JJKSpells.id(SpiralSwordsSpell.NAME), new SpiralSwordsSpell());
            helper.register(JJKSpells.id(DimensionRushSpell.NAME), new DimensionRushSpell());
            helper.register(JJKSpells.id(DevilTriggerSpell.NAME), new DevilTriggerSpell());
            helper.register(JJKSpells.id(HelmBreakerSpell.NAME), new HelmBreakerSpell());
            helper.register(JJKSpells.id(CrescentWaveSpell.NAME), new CrescentWaveSpell());
            helper.register(JJKSpells.id(DimensionSeverSpell.NAME), new DimensionSeverSpell());
        });
    }
}
