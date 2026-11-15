package com.example.jjkspells.garou.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.entity.GammaBeamEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<GammaBeamEntity>> GAMMA_BEAM = E.register("gar_gamma_beam",
            () -> EntityType.Builder.<GammaBeamEntity>of(GammaBeamEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(256).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("gar_gamma_beam").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
