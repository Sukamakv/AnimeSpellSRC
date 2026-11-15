package com.example.jjkspells.dante;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dante.entity.CavaliereEntity;
import com.example.jjkspells.dante.entity.DevilTriggerEntity;
import com.example.jjkspells.dante.entity.MillionStabEntity;
import com.example.jjkspells.dante.entity.RoundTripEntity;
import com.example.jjkspells.dante.entity.SinJudgmentEntity;
import com.example.jjkspells.dante.entity.RoyalGuardEntity;
import com.example.jjkspells.dante.entity.StylishRankEntity;
import com.example.jjkspells.dante.spell.CavaliereSpell;
import com.example.jjkspells.dante.spell.DevilTriggerSpell;
import com.example.jjkspells.dante.spell.MillionStabSpell;
import com.example.jjkspells.dante.spell.RoundTripSpell;
import com.example.jjkspells.dante.spell.SinJudgmentSpell;
import com.example.jjkspells.dante.spell.RoyalGuardSpell;
import com.example.jjkspells.dante.spell.StylishRankSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Self-contained registration for the Dante (DMC5) spell pack. No existing file has to be edited. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DanteRegistry {
    public static final String STAB_ID = "dante_million_stab_zone";
    public static EntityType<MillionStabEntity> STAB;
    public static final String STYLISH_ID = "dante_stylish_rank_zone";
    public static EntityType<StylishRankEntity> STYLISH;
    public static final String ROYAL_ID = "dante_royal_guard_zone";
    public static EntityType<RoyalGuardEntity> ROYAL;
    public static final String CAVALIERE_ID = "dante_cavaliere_zone";
    public static EntityType<CavaliereEntity> CAVALIERE;
    public static final String TRIGGER_ID = "dante_devil_trigger_zone";
    public static EntityType<DevilTriggerEntity> TRIGGER;
    public static final String ROUND_ID = "dante_round_trip_zone";
    public static EntityType<RoundTripEntity> ROUND;
    public static final String JUDGMENT_ID = "dante_sparda_judgment_zone";
    public static EntityType<SinJudgmentEntity> JUDGMENT;

    private DanteRegistry() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            STAB = EntityType.Builder.<MillionStabEntity>of(MillionStabEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(STAB_ID).toString());
            helper.register(JJKSpells.id(STAB_ID), STAB);
            STYLISH = EntityType.Builder.<StylishRankEntity>of(StylishRankEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(STYLISH_ID).toString());
            helper.register(JJKSpells.id(STYLISH_ID), STYLISH);
            ROYAL = EntityType.Builder.<RoyalGuardEntity>of(RoyalGuardEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(ROYAL_ID).toString());
            helper.register(JJKSpells.id(ROYAL_ID), ROYAL);
            CAVALIERE = EntityType.Builder.<CavaliereEntity>of(CavaliereEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(CAVALIERE_ID).toString());
            helper.register(JJKSpells.id(CAVALIERE_ID), CAVALIERE);
            TRIGGER = EntityType.Builder.<DevilTriggerEntity>of(DevilTriggerEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(TRIGGER_ID).toString());
            helper.register(JJKSpells.id(TRIGGER_ID), TRIGGER);
            ROUND = EntityType.Builder.<RoundTripEntity>of(RoundTripEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(ROUND_ID).toString());
            helper.register(JJKSpells.id(ROUND_ID), ROUND);
            JUDGMENT = EntityType.Builder.<SinJudgmentEntity>of(SinJudgmentEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(JUDGMENT_ID).toString());
            helper.register(JJKSpells.id(JUDGMENT_ID), JUDGMENT);
        });
        event.register(SpellRegistry.SPELL_REGISTRY_KEY, helper -> {
            helper.register(JJKSpells.id(MillionStabSpell.NAME), new MillionStabSpell());
            helper.register(JJKSpells.id(StylishRankSpell.NAME), new StylishRankSpell());
            helper.register(JJKSpells.id(RoyalGuardSpell.NAME), new RoyalGuardSpell());
            helper.register(JJKSpells.id(CavaliereSpell.NAME), new CavaliereSpell());
            helper.register(JJKSpells.id(DevilTriggerSpell.NAME), new DevilTriggerSpell());
            helper.register(JJKSpells.id(RoundTripSpell.NAME), new RoundTripSpell());
            helper.register(JJKSpells.id(SinJudgmentSpell.NAME), new SinJudgmentSpell());
        });
    }
}
