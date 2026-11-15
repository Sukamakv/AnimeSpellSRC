package com.example.jjkspells.tempest.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.tempest.entity.AbyssEntity;
import com.example.jjkspells.tempest.entity.StormEntity;
import com.example.jjkspells.tempest.entity.ArrayEntity;
import com.example.jjkspells.tempest.entity.LawEntity;
import com.example.jjkspells.tempest.entity.FractureEntity;
import com.example.jjkspells.tempest.entity.GluttonyEntity;
import com.example.jjkspells.tempest.entity.NothingEntity;
import com.example.jjkspells.tempest.entity.AzathothEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<AbyssEntity>> ABYSS = E.register("tem_abyss",
            () -> EntityType.Builder.<AbyssEntity>of(AbyssEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(64).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("tem_abyss").toString()));
    public static final RegistryObject<EntityType<StormEntity>> STORM = E.register("tem_storm",
            () -> EntityType.Builder.<StormEntity>of(StormEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(96).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("tem_storm").toString()));
    public static final RegistryObject<EntityType<ArrayEntity>> ARRAY = E.register("tem_array",
            () -> EntityType.Builder.<ArrayEntity>of(ArrayEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("tem_array").toString()));
    public static final RegistryObject<EntityType<LawEntity>> LAW = E.register("tem_law",
            () -> EntityType.Builder.<LawEntity>of(LawEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(64).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("tem_law").toString()));
    public static final RegistryObject<EntityType<FractureEntity>> FRACTURE = E.register("tem_fracture",
            () -> EntityType.Builder.<FractureEntity>of(FractureEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("tem_fracture").toString()));
    public static final RegistryObject<EntityType<GluttonyEntity>> GLUTTONY = E.register("tem_gluttony",
            () -> EntityType.Builder.<GluttonyEntity>of(GluttonyEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(128).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("tem_gluttony").toString()));
    public static final RegistryObject<EntityType<NothingEntity>> NOTHING = E.register("tem_nothing",
            () -> EntityType.Builder.<NothingEntity>of(NothingEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(160).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("tem_nothing").toString()));
    public static final RegistryObject<EntityType<AzathothEntity>> AZATHOTH = E.register("tem_azathoth",
            () -> EntityType.Builder.<AzathothEntity>of(AzathothEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(192).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("tem_azathoth").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
