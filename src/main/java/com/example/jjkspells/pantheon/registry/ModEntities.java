package com.example.jjkspells.pantheon.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.pantheon.entity.AegisEntity;
import com.example.jjkspells.pantheon.entity.GrandEntity;
import com.example.jjkspells.pantheon.entity.SpearEntity;
import com.example.jjkspells.pantheon.entity.StarfallEntity;
import com.example.jjkspells.pantheon.entity.VaultEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<SpearEntity>> SPEAR = E.register("pan_spear",
            () -> EntityType.Builder.<SpearEntity>of(SpearEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("pan_spear").toString()));
    public static final RegistryObject<EntityType<VaultEntity>> VAULT = E.register("pan_vault",
            () -> EntityType.Builder.<VaultEntity>of(VaultEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("pan_vault").toString()));
    public static final RegistryObject<EntityType<AegisEntity>> AEGIS = E.register("pan_aegis",
            () -> EntityType.Builder.<AegisEntity>of(AegisEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("pan_aegis").toString()));
    public static final RegistryObject<EntityType<StarfallEntity>> STARFALL = E.register("pan_starfall",
            () -> EntityType.Builder.<StarfallEntity>of(StarfallEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(24).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("pan_starfall").toString()));
    public static final RegistryObject<EntityType<GrandEntity>> GRAND = E.register("pan_grand",
            () -> EntityType.Builder.<GrandEntity>of(GrandEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(32).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("pan_grand").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
