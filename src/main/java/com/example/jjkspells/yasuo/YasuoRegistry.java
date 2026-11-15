package com.example.jjkspells.yasuo;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.yasuo.entity.LastBreathEntity;
import com.example.jjkspells.yasuo.entity.ReapingCycloneEntity;
import com.example.jjkspells.yasuo.entity.SteelTempestEntity;
import com.example.jjkspells.yasuo.entity.ThousandWindsEntity;
import com.example.jjkspells.yasuo.entity.SweepingBladeEntity;
import com.example.jjkspells.yasuo.entity.WindWallEntity;
import com.example.jjkspells.yasuo.entity.WindboundDuelEntity;
import com.example.jjkspells.yasuo.spell.LastBreathSpell;
import com.example.jjkspells.yasuo.spell.ReapingCycloneSpell;
import com.example.jjkspells.yasuo.spell.SteelTempestSpell;
import com.example.jjkspells.yasuo.spell.ThousandWindsSpell;
import com.example.jjkspells.yasuo.spell.SweepingBladeSpell;
import com.example.jjkspells.yasuo.spell.WindWallSpell;
import com.example.jjkspells.yasuo.spell.WindboundDuelSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Self-contained registration for the Yasuo (League of Legends) spell pack. No existing file has to be edited. */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class YasuoRegistry {
    public static final String STEEL_ID = "yasuo_steel_tempest_zone";
    public static EntityType<SteelTempestEntity> STEEL;
    public static final String WALL_ID = "yasuo_wind_wall_zone";
    public static EntityType<WindWallEntity> WALL;
    public static final String BLADE_ID = "yasuo_sweeping_blade_zone";
    public static EntityType<SweepingBladeEntity> BLADE;
    public static final String DUEL_ID = "yasuo_windbound_duel_zone";
    public static EntityType<WindboundDuelEntity> DUEL;
    public static final String BREATH_ID = "yasuo_last_breath_zone";
    public static EntityType<LastBreathEntity> BREATH;
    public static final String CYCLONE_ID = "yasuo_reaping_cyclone_zone";
    public static EntityType<ReapingCycloneEntity> CYCLONE;
    public static final String WINDS_ID = "yasuo_thousand_winds_zone";
    public static EntityType<ThousandWindsEntity> WINDS;

    private YasuoRegistry() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            STEEL = EntityType.Builder.<SteelTempestEntity>of(SteelTempestEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(STEEL_ID).toString());
            helper.register(JJKSpells.id(STEEL_ID), STEEL);
            WALL = EntityType.Builder.<WindWallEntity>of(WindWallEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(WALL_ID).toString());
            helper.register(JJKSpells.id(WALL_ID), WALL);
            BLADE = EntityType.Builder.<SweepingBladeEntity>of(SweepingBladeEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(BLADE_ID).toString());
            helper.register(JJKSpells.id(BLADE_ID), BLADE);
            DUEL = EntityType.Builder.<WindboundDuelEntity>of(WindboundDuelEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(DUEL_ID).toString());
            helper.register(JJKSpells.id(DUEL_ID), DUEL);
            BREATH = EntityType.Builder.<LastBreathEntity>of(LastBreathEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(BREATH_ID).toString());
            helper.register(JJKSpells.id(BREATH_ID), BREATH);
            CYCLONE = EntityType.Builder.<ReapingCycloneEntity>of(ReapingCycloneEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(CYCLONE_ID).toString());
            helper.register(JJKSpells.id(CYCLONE_ID), CYCLONE);
            WINDS = EntityType.Builder.<ThousandWindsEntity>of(ThousandWindsEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(WINDS_ID).toString());
            helper.register(JJKSpells.id(WINDS_ID), WINDS);
        });
        event.register(SpellRegistry.SPELL_REGISTRY_KEY, helper -> {
            helper.register(JJKSpells.id(SteelTempestSpell.NAME), new SteelTempestSpell());
            helper.register(JJKSpells.id(WindWallSpell.NAME), new WindWallSpell());
            helper.register(JJKSpells.id(SweepingBladeSpell.NAME), new SweepingBladeSpell());
            helper.register(JJKSpells.id(WindboundDuelSpell.NAME), new WindboundDuelSpell());
            helper.register(JJKSpells.id(LastBreathSpell.NAME), new LastBreathSpell());
            helper.register(JJKSpells.id(ReapingCycloneSpell.NAME), new ReapingCycloneSpell());
            helper.register(JJKSpells.id(ThousandWindsSpell.NAME), new ThousandWindsSpell());
        });
    }
}
