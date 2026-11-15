package com.example.jjkspells.acheron.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.acheron.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<WiltEntity>> WILTCROSS = E.register("ach_wilt",
            () -> EntityType.Builder.<WiltEntity>of(WiltEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(256).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("ach_wilt").toString()));
    public static final RegistryObject<EntityType<OctoEntity>> OCTOBOLT = E.register("ach_octo",
            () -> EntityType.Builder.<OctoEntity>of(OctoEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(256).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("ach_octo").toString()));
    public static final RegistryObject<EntityType<QuadEntity>> QUAD = E.register("ach_quad",
            () -> EntityType.Builder.<QuadEntity>of(QuadEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(256).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("ach_quad").toString()));
    public static final RegistryObject<EntityType<RainleafEntity>> RAINLEAF = E.register("ach_rainleaf",
            () -> EntityType.Builder.<RainleafEntity>of(RainleafEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(256).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("ach_rainleaf").toString()));
    public static final RegistryObject<EntityType<BladeEntity>> RAINBLADE = E.register("ach_blade",
            () -> EntityType.Builder.<BladeEntity>of(BladeEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(256).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("ach_blade").toString()));
    public static final RegistryObject<EntityType<StygianEntity>> STYGIAN = E.register("ach_stygian",
            () -> EntityType.Builder.<StygianEntity>of(StygianEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(256).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("ach_stygian").toString()));
    public static final RegistryObject<EntityType<UltimateEntity>> ULTIMATE = E.register("ach_ultimate",
            () -> EntityType.Builder.<UltimateEntity>of(UltimateEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(256).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("ach_ultimate").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
