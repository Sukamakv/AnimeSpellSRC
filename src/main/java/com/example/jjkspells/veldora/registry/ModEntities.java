package com.example.jjkspells.veldora.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.veldora.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<WrathEntity>> WRATH = E.register("vel_wrath",
            () -> EntityType.Builder.<WrathEntity>of(WrathEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vel_wrath").toString()));
    public static final RegistryObject<EntityType<ThunderEntity>> THUNDER = E.register("vel_thunder",
            () -> EntityType.Builder.<ThunderEntity>of(ThunderEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vel_thunder").toString()));
    public static final RegistryObject<EntityType<BreathEntity>> BREATH = E.register("vel_breath",
            () -> EntityType.Builder.<BreathEntity>of(BreathEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vel_breath").toString()));
    public static final RegistryObject<EntityType<FistEntity>> FIST = E.register("vel_fist",
            () -> EntityType.Builder.<FistEntity>of(FistEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(64).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vel_fist").toString()));
    public static final RegistryObject<EntityType<ChaosEntity>> CHAOS = E.register("vel_chaos",
            () -> EntityType.Builder.<ChaosEntity>of(ChaosEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vel_chaos").toString()));
    public static final RegistryObject<EntityType<AscendEntity>> ASCEND = E.register("vel_ascend",
            () -> EntityType.Builder.<AscendEntity>of(AscendEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vel_ascend").toString()));
    public static final RegistryObject<EntityType<NovaEntity>> NOVA = E.register("vel_nova",
            () -> EntityType.Builder.<NovaEntity>of(NovaEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vel_nova").toString()));
    public static final RegistryObject<EntityType<EternalEntity>> ETERNAL = E.register("vel_eternal",
            () -> EntityType.Builder.<EternalEntity>of(EternalEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(160).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("vel_eternal").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
