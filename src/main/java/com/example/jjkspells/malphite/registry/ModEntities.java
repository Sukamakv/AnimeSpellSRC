package com.example.jjkspells.malphite.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.malphite.entity.ClapEntity;
import com.example.jjkspells.malphite.entity.ForceEntity;
import com.example.jjkspells.malphite.entity.GraniteEntity;
import com.example.jjkspells.malphite.entity.ShardEntity;
import com.example.jjkspells.malphite.entity.StrikesEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<ShardEntity>> SHARD = E.register("mal_shard",
            () -> EntityType.Builder.<ShardEntity>of(ShardEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("mal_shard").toString()));
    public static final RegistryObject<EntityType<ClapEntity>> CLAP = E.register("mal_clap",
            () -> EntityType.Builder.<ClapEntity>of(ClapEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("mal_clap").toString()));
    public static final RegistryObject<EntityType<StrikesEntity>> STRIKES = E.register("mal_strikes",
            () -> EntityType.Builder.<StrikesEntity>of(StrikesEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("mal_strikes").toString()));
    public static final RegistryObject<EntityType<GraniteEntity>> GRANITE = E.register("mal_granite",
            () -> EntityType.Builder.<GraniteEntity>of(GraniteEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("mal_granite").toString()));
    public static final RegistryObject<EntityType<ForceEntity>> FORCE = E.register("mal_force",
            () -> EntityType.Builder.<ForceEntity>of(ForceEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(32).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("mal_force").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
