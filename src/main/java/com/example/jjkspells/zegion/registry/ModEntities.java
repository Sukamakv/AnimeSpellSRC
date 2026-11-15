package com.example.jjkspells.zegion.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.zegion.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<ImpactEntity>> IMPACT = reg("zeg_impact", ImpactEntity::new, 32);
    public static final RegistryObject<EntityType<DomainEntity>> DOMAIN = reg("zeg_domain", DomainEntity::new, 32);
    public static final RegistryObject<EntityType<ObsidianEntity>> OBSIDIAN = reg("zeg_obsidian", ObsidianEntity::new, 16);
    public static final RegistryObject<EntityType<AssaultEntity>> ASSAULT = reg("zeg_assault", AssaultEntity::new, 32);
    public static final RegistryObject<EntityType<LabyrinthEntity>> LABYRINTH = reg("zeg_labyrinth", LabyrinthEntity::new, 32);
    public static final RegistryObject<EntityType<SingularityEntity>> SINGULARITY = reg("zeg_singularity", SingularityEntity::new, 32);
    public static final RegistryObject<EntityType<SovereignEntity>> SOVEREIGN = reg("zeg_sovereign", SovereignEntity::new, 32);
    public static final RegistryObject<EntityType<MephistoEntity>> MEPHISTO = reg("zeg_mephisto", MephistoEntity::new, 32);

    private ModEntities() {
    }

    private static <T extends net.minecraft.world.entity.Entity> RegistryObject<EntityType<T>> reg(String name, EntityType.EntityFactory<T> factory, int range) {
        return E.register(name, () -> EntityType.Builder.<T>of(factory, MobCategory.MISC)
                .sized(0.5f, 0.5f).clientTrackingRange(range).updateInterval(1).fireImmune().noSave()
                .build(JJKSpells.id(name).toString()));
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
