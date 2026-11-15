package com.example.jjkspells.shadow.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.shadow.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<GardenEntity>> GARDEN = E.register("shd_garden",
            () -> EntityType.Builder.<GardenEntity>of(GardenEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("shd_garden").toString()));
    public static final RegistryObject<EntityType<ThroneEntity>> THRONE = E.register("shd_throne",
            () -> EntityType.Builder.<ThroneEntity>of(ThroneEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("shd_throne").toString()));
    public static final RegistryObject<EntityType<SerpentEntity>> SERPENT = E.register("shd_serpent",
            () -> EntityType.Builder.<SerpentEntity>of(SerpentEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("shd_serpent").toString()));
    public static final RegistryObject<EntityType<CounterEntity>> COUNTER = E.register("shd_counter",
            () -> EntityType.Builder.<CounterEntity>of(CounterEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("shd_counter").toString()));
    public static final RegistryObject<EntityType<AtomicEntity>> ATOMIC = E.register("shd_atomic",
            () -> EntityType.Builder.<AtomicEntity>of(AtomicEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(256).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("shd_atomic").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
