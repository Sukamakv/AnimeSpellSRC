package com.example.jjkspells.diablo.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.diablo.entity.NetherApocalypseEntity;
import com.example.jjkspells.diablo.entity.PrimordialDominionEntity;
import com.example.jjkspells.diablo.entity.DimensionalFuneralEntity;
import com.example.jjkspells.diablo.entity.NegationEntity;
import com.example.jjkspells.diablo.entity.DespairTimeEntity;
import com.example.jjkspells.diablo.entity.TemptationWorldEntity;
import com.example.jjkspells.diablo.entity.AbyssalGenesisEntity;
import com.example.jjkspells.diablo.entity.TemptationKingAzazelEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<NetherApocalypseEntity>> NETHER = E.register("dia_nether",
            () -> EntityType.Builder.<NetherApocalypseEntity>of(NetherApocalypseEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(64).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("dia_nether").toString()));
    public static final RegistryObject<EntityType<PrimordialDominionEntity>> DOMINION = E.register("dia_dominion",
            () -> EntityType.Builder.<PrimordialDominionEntity>of(PrimordialDominionEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("dia_dominion").toString()));
    public static final RegistryObject<EntityType<DimensionalFuneralEntity>> FUNERAL = E.register("dia_funeral",
            () -> EntityType.Builder.<DimensionalFuneralEntity>of(DimensionalFuneralEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("dia_funeral").toString()));
    public static final RegistryObject<EntityType<NegationEntity>> NEGATION = E.register("dia_negation",
            () -> EntityType.Builder.<NegationEntity>of(NegationEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(64).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("dia_negation").toString()));
    public static final RegistryObject<EntityType<DespairTimeEntity>> DESPAIR = E.register("dia_despair",
            () -> EntityType.Builder.<DespairTimeEntity>of(DespairTimeEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("dia_despair").toString()));
    public static final RegistryObject<EntityType<TemptationWorldEntity>> WORLD = E.register("dia_world",
            () -> EntityType.Builder.<TemptationWorldEntity>of(TemptationWorldEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("dia_world").toString()));
    public static final RegistryObject<EntityType<AbyssalGenesisEntity>> GENESIS = E.register("dia_genesis",
            () -> EntityType.Builder.<AbyssalGenesisEntity>of(AbyssalGenesisEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(160).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("dia_genesis").toString()));
    public static final RegistryObject<EntityType<TemptationKingAzazelEntity>> AZAZEL = E.register("dia_azazel",
            () -> EntityType.Builder.<TemptationKingAzazelEntity>of(TemptationKingAzazelEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(192).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("dia_azazel").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
