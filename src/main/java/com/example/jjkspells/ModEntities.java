package com.example.jjkspells;

import com.example.jjkspells.entity.JusticeSwordEntity;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.GaleBarrageEntity;
import com.example.jjkspells.entity.LapseBlueOrb;
import com.example.jjkspells.entity.ReversalRedProjectile;
import com.example.jjkspells.entity.StormTornadoEntity;
import com.example.jjkspells.entity.UnlimitedVoidDome;
import com.example.jjkspells.entity.AzureColossus;
import com.example.jjkspells.entity.AzureOrbProjectile;
import com.example.jjkspells.entity.MeteorEntity;
import com.example.jjkspells.entity.TengaiFxEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    public static final RegistryObject<EntityType<LapseBlueOrb>> LAPSE_BLUE_ORB =
            ENTITIES.register("lapse_blue_orb", () -> EntityType.Builder.<LapseBlueOrb>of(LapseBlueOrb::new, MobCategory.MISC)
                    .sized(1f, 1f)
                    .clientTrackingRange(64)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("lapse_blue_orb").toString()));

    public static final RegistryObject<EntityType<ReversalRedProjectile>> REVERSAL_RED =
            ENTITIES.register("reversal_red", () -> EntityType.Builder.<ReversalRedProjectile>of(ReversalRedProjectile::new, MobCategory.MISC)
                    .sized(.8f, .8f)
                    .clientTrackingRange(64)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("reversal_red").toString()));

    public static final RegistryObject<EntityType<UnlimitedVoidDome>> UNLIMITED_VOID_DOME =
            ENTITIES.register("unlimited_void_dome", () -> EntityType.Builder.<UnlimitedVoidDome>of(UnlimitedVoidDome::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(16)
                    .updateInterval(20)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("unlimited_void_dome").toString()));

    public static final RegistryObject<EntityType<StormTornadoEntity>> STORM_TORNADO =
            ENTITIES.register("storm_tornado", () -> EntityType.Builder.<StormTornadoEntity>of(StormTornadoEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(16)
                    .updateInterval(Integer.MAX_VALUE) // position is computed from the caster on both sides, never streamed
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("storm_tornado").toString()));

    public static final RegistryObject<EntityType<GaleBarrageEntity>> BARRAGE =
            ENTITIES.register("gale_barrage", () -> EntityType.Builder.<GaleBarrageEntity>of(GaleBarrageEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(16)
                    .updateInterval(Integer.MAX_VALUE)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("gale_barrage").toString()));

    /** The 25-block-tall blue spectral guardian. */
    public static final RegistryObject<EntityType<AzureColossus>> AZURE_COLOSSUS =
            ENTITIES.register("azure_colossus", () -> EntityType.Builder.<AzureColossus>of(AzureColossus::new, MobCategory.MISC)
                    .sized(7f, 25f)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("azure_colossus").toString()));

    /** Blue chakra fire orb thrown by the colossus and by Azure Orb Barrage. */
    public static final RegistryObject<EntityType<AzureOrbProjectile>> AZURE_ORB =
            ENTITIES.register("azure_orb", () -> EntityType.Builder.<AzureOrbProjectile>of(AzureOrbProjectile::new, MobCategory.MISC)
                    .sized(1.2f, 1.2f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("azure_orb").toString()));

    /** The two giant meteors of Tengai Shinsei. Tiny hitbox on purpose: it is only drawn, never collided with. */
    public static final RegistryObject<EntityType<MeteorEntity>> METEOR =
            ENTITIES.register("tengai_meteor", () -> EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC)
                    .sized(1f, 1f)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("tengai_meteor").toString()));

    /** Purely visual: the landing shockwave and the mushroom cloud of Tengai Shinsei. */
    public static final RegistryObject<EntityType<TengaiFxEntity>> TENGAI_FX =
            ENTITIES.register("tengai_fx", () -> EntityType.Builder.<TengaiFxEntity>of(TengaiFxEntity::new, MobCategory.MISC)
                    .sized(1f, 1f)
                    .clientTrackingRange(32)
                    .updateInterval(20)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("tengai_fx").toString()));

    /** Demacian Justice: the giant spectral sword (purely visual + damage timeline). */
    public static final RegistryObject<EntityType<JusticeSwordEntity>> JUSTICE_SWORD =
            ENTITIES.register("justice_sword", () -> EntityType.Builder.<JusticeSwordEntity>of(JusticeSwordEntity::new, MobCategory.MISC)
                    .sized(1f, 1f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("justice_sword").toString()));

    /** DIO's Stand and road roller. Server-moved, client-drawn. */
    public static final RegistryObject<EntityType<com.example.jjkspells.entity.TheWorldEntity>> THE_WORLD =
            ENTITIES.register("the_world", () -> EntityType.Builder.<com.example.jjkspells.entity.TheWorldEntity>of(com.example.jjkspells.entity.TheWorldEntity::new, MobCategory.MISC)
                    .sized(1.0f, 2.5f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("the_world").toString()));

    public static final RegistryObject<EntityType<com.example.jjkspells.entity.RoadRollerEntity>> ROAD_ROLLER =
            ENTITIES.register("road_roller", () -> EntityType.Builder.<com.example.jjkspells.entity.RoadRollerEntity>of(com.example.jjkspells.entity.RoadRollerEntity::new, MobCategory.MISC)
                    .sized(4.0f, 3.0f).clientTrackingRange(16).updateInterval(1).fireImmune().noSave()
                    .build(JJKSpells.id("road_roller").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }

    /** A wraith of Death's Wraiths. Moved by the server every tick; only drawn on the client. */
    public static final RegistryObject<EntityType<com.example.jjkspells.entity.DeathWraithEntity>> DEATH_WRAITH =
            ENTITIES.register("death_wraith", () -> EntityType.Builder.<com.example.jjkspells.entity.DeathWraithEntity>of(com.example.jjkspells.entity.DeathWraithEntity::new, MobCategory.MISC)
                    .sized(0.8f, 1.6f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("death_wraith").toString()));

    /** A weapon of the Gate of Babylon. Server-moved, client-drawn. */
    public static final RegistryObject<EntityType<com.example.jjkspells.entity.GateWeaponEntity>> GATE_WEAPON =
            ENTITIES.register("gate_weapon", () -> EntityType.Builder.<com.example.jjkspells.entity.GateWeaponEntity>of(com.example.jjkspells.entity.GateWeaponEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("gate_weapon").toString()));

    /** Ea and its vortex, wave and explosion. Server-moved, client-drawn. */
    public static final RegistryObject<EntityType<com.example.jjkspells.entity.EaFxEntity>> EA_FX =
            ENTITIES.register("ea_fx", () -> EntityType.Builder.<com.example.jjkspells.entity.EaFxEntity>of(com.example.jjkspells.entity.EaFxEntity::new, MobCategory.MISC)
                    .sized(1f, 1f)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("ea_fx").toString()));

    /** Everything Laevatain conjures: fragments, pillars, slashes, her greatsword, explosions. Server-moved, client-drawn. */
    public static final RegistryObject<EntityType<com.example.jjkspells.entity.LaevatainFxEntity>> LAEVATAIN_FX =
            ENTITIES.register("laevatain_fx", () -> EntityType.Builder.<com.example.jjkspells.entity.LaevatainFxEntity>of(com.example.jjkspells.entity.LaevatainFxEntity::new, MobCategory.MISC)
                    .sized(1f, 1f)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id("laevatain_fx").toString()));
}
