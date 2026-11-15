package com.example.jjkspells.overlord.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.overlord.entity.BlackGoatEntity;
import com.example.jjkspells.overlord.entity.GoatSpawnEntity;
import com.example.jjkspells.overlord.entity.OverlordFxEntity;
import com.example.jjkspells.overlord.entity.UndeadSoldierEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    /** Invisible controller that owns the timeline, effects and visuals of the non-summon spells. */
    public static final RegistryObject<EntityType<OverlordFxEntity>> FX =
            E.register("overlord_fx", () -> EntityType.Builder.<OverlordFxEntity>of(OverlordFxEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(16)
                    .updateInterval(Integer.MAX_VALUE)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("overlord_fx").toString()));

    public static final RegistryObject<EntityType<UndeadSoldierEntity>> UNDEAD_SOLDIER =
            E.register("undead_soldier", () -> EntityType.Builder.<UndeadSoldierEntity>of(UndeadSoldierEntity::new, MobCategory.CREATURE)
                    .sized(0.72f, 2.4f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("undead_soldier").toString()));

    public static final RegistryObject<EntityType<BlackGoatEntity>> BLACK_GOAT =
            E.register("black_goat", () -> EntityType.Builder.<BlackGoatEntity>of(BlackGoatEntity::new, MobCategory.CREATURE)
                    .sized(3.4f, 3.6f)
                    .clientTrackingRange(16)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("black_goat").toString()));

    public static final RegistryObject<EntityType<GoatSpawnEntity>> GOAT_SPAWN =
            E.register("goat_spawn", () -> EntityType.Builder.<GoatSpawnEntity>of(GoatSpawnEntity::new, MobCategory.CREATURE)
                    .sized(1.0f, 1.1f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("goat_spawn").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
