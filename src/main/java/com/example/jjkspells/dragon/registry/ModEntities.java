package com.example.jjkspells.dragon.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dragon.entity.AscensionFxEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<AscensionFxEntity>> FX =
            E.register("dragon_ascension_fx", () -> EntityType.Builder.<AscensionFxEntity>of(AscensionFxEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(24)
                    .updateInterval(Integer.MAX_VALUE)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("dragon_ascension_fx").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
