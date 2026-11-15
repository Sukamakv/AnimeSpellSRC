package com.example.jjkspells.afterlife.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.afterlife.entity.FuneralEntity;
import com.example.jjkspells.afterlife.entity.SpearDashEntity;
import com.example.jjkspells.afterlife.entity.SpiritGhostEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<SpiritGhostEntity>> GHOST = E.register("al_spirit_ghost",
            () -> EntityType.Builder.<SpiritGhostEntity>of(SpiritGhostEntity::new, MobCategory.MISC)
                    .sized(0.8f, 0.8f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("al_spirit_ghost").toString()));
    public static final RegistryObject<EntityType<SpearDashEntity>> DASH = E.register("al_spear_dash",
            () -> EntityType.Builder.<SpearDashEntity>of(SpearDashEntity::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f).clientTrackingRange(4).updateInterval(Integer.MAX_VALUE).fireImmune().noSave()
                    .build(JJKSpells.id("al_spear_dash").toString()));
    public static final RegistryObject<EntityType<FuneralEntity>> FUNERAL = E.register("al_funeral",
            () -> EntityType.Builder.<FuneralEntity>of(FuneralEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(24).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("al_funeral").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
