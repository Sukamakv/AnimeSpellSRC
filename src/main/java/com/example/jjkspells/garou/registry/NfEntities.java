package com.example.jjkspells.garou.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.entity.AllLifeEntity;
import com.example.jjkspells.garou.entity.NuclearFissionEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class NfEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<NuclearFissionEntity>> NUCLEAR_FISSION = E.register("gau_nuclear_fission",
            () -> EntityType.Builder.<NuclearFissionEntity>of(NuclearFissionEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(32).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("gau_nuclear_fission").toString()));

    public static final RegistryObject<EntityType<AllLifeEntity>> ALL_LIFE = E.register("gau_all_life_fist",
            () -> EntityType.Builder.<AllLifeEntity>of(AllLifeEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(48).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("gau_all_life_fist").toString()));

    private NfEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
