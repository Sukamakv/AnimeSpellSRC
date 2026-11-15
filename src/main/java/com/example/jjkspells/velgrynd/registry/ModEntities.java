package com.example.jjkspells.velgrynd.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.velgrynd.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<BreathEntity>> BREATH = E.register("vgr_breath",
            () -> EntityType.Builder.<BreathEntity>of(BreathEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vgr_breath").toString()));
    public static final RegistryObject<EntityType<InfernoEntity>> INFERNO = E.register("vgr_inferno",
            () -> EntityType.Builder.<InfernoEntity>of(InfernoEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vgr_inferno").toString()));
    public static final RegistryObject<EntityType<AccelEntity>> ACCEL = E.register("vgr_accel",
            () -> EntityType.Builder.<AccelEntity>of(AccelEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vgr_accel").toString()));
    public static final RegistryObject<EntityType<DominionEntity>> DOMINION = E.register("vgr_dominion",
            () -> EntityType.Builder.<DominionEntity>of(DominionEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vgr_dominion").toString()));
    public static final RegistryObject<EntityType<ParallelEntity>> PARALLEL = E.register("vgr_parallel",
            () -> EntityType.Builder.<ParallelEntity>of(ParallelEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vgr_parallel").toString()));
    public static final RegistryObject<EntityType<PursuitEntity>> PURSUIT = E.register("vgr_pursuit",
            () -> EntityType.Builder.<PursuitEntity>of(PursuitEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vgr_pursuit").toString()));
    public static final RegistryObject<EntityType<StarEntity>> STAR = E.register("vgr_star",
            () -> EntityType.Builder.<StarEntity>of(StarEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vgr_star").toString()));
    public static final RegistryObject<EntityType<CthughaEntity>> CTHUGHA = E.register("vgr_cthugha",
            () -> EntityType.Builder.<CthughaEntity>of(CthughaEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(160).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vgr_cthugha").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
