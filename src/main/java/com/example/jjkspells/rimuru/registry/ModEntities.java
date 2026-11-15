package com.example.jjkspells.rimuru.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.rimuru.entity.PredatorEntity;
import com.example.jjkspells.rimuru.entity.BlackLightningEntity;
import com.example.jjkspells.rimuru.entity.MegiddoEntity;
import com.example.jjkspells.rimuru.entity.DefenseEntity;
import com.example.jjkspells.rimuru.entity.SpatialEntity;
import com.example.jjkspells.rimuru.entity.BeelzebuthEntity;
import com.example.jjkspells.rimuru.entity.CollapseEntity;
import com.example.jjkspells.rimuru.entity.AzathothEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<PredatorEntity>> PREDATOR = E.register("rim_predator",
            () -> EntityType.Builder.<PredatorEntity>of(PredatorEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(48).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("rim_predator").toString()));
    public static final RegistryObject<EntityType<BlackLightningEntity>> LIGHTNING = E.register("rim_lightning",
            () -> EntityType.Builder.<BlackLightningEntity>of(BlackLightningEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("rim_lightning").toString()));
    public static final RegistryObject<EntityType<MegiddoEntity>> MEGIDDO = E.register("rim_megiddo",
            () -> EntityType.Builder.<MegiddoEntity>of(MegiddoEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("rim_megiddo").toString()));
    public static final RegistryObject<EntityType<DefenseEntity>> DEFENSE = E.register("rim_defense",
            () -> EntityType.Builder.<DefenseEntity>of(DefenseEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(64).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("rim_defense").toString()));
    public static final RegistryObject<EntityType<SpatialEntity>> SPATIAL = E.register("rim_spatial",
            () -> EntityType.Builder.<SpatialEntity>of(SpatialEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("rim_spatial").toString()));
    public static final RegistryObject<EntityType<BeelzebuthEntity>> BEELZEBUTH = E.register("rim_beelzebuth",
            () -> EntityType.Builder.<BeelzebuthEntity>of(BeelzebuthEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("rim_beelzebuth").toString()));
    public static final RegistryObject<EntityType<CollapseEntity>> COLLAPSE = E.register("rim_collapse",
            () -> EntityType.Builder.<CollapseEntity>of(CollapseEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("rim_collapse").toString()));
    public static final RegistryObject<EntityType<AzathothEntity>> AZATHOTH = E.register("rim_azathoth",
            () -> EntityType.Builder.<AzathothEntity>of(AzathothEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(160).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("rim_azathoth").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
