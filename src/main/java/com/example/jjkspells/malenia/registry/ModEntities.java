package com.example.jjkspells.malenia.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.malenia.entity.WaterfowlEntity;
import com.example.jjkspells.malenia.entity.PhantomEntity;
import com.example.jjkspells.malenia.entity.BloomEntity;
import com.example.jjkspells.malenia.entity.BladeEntity;
import com.example.jjkspells.malenia.entity.GoddessEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<WaterfowlEntity>> WATERFOWL = E.register("mel_waterfowl",
            () -> EntityType.Builder.<WaterfowlEntity>of(WaterfowlEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(32).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("mel_waterfowl").toString()));
    public static final RegistryObject<EntityType<PhantomEntity>> PHANTOM = E.register("mel_phantom",
            () -> EntityType.Builder.<PhantomEntity>of(PhantomEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(32).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("mel_phantom").toString()));
    public static final RegistryObject<EntityType<BloomEntity>> BLOOM = E.register("mel_bloom",
            () -> EntityType.Builder.<BloomEntity>of(BloomEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(32).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("mel_bloom").toString()));
    public static final RegistryObject<EntityType<BladeEntity>> BLADE = E.register("mel_blade",
            () -> EntityType.Builder.<BladeEntity>of(BladeEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(32).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("mel_blade").toString()));
    public static final RegistryObject<EntityType<GoddessEntity>> GODDESS = E.register("mel_goddess",
            () -> EntityType.Builder.<GoddessEntity>of(GoddessEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(64).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("mel_goddess").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
