package com.example.jjkspells.garou;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.garou.entity.CosmicPortalEntity;
import com.example.jjkspells.garou.entity.CosmicRadiationEntity;
import com.example.jjkspells.garou.spell.GarouCosmicRadiationSpell;
import com.example.jjkspells.garou.entity.GammaRayBurstEntity;
import com.example.jjkspells.garou.entity.GravityKnuckleEntity;
import com.example.jjkspells.garou.spell.GarouCosmicPortalSpell;
import com.example.jjkspells.garou.spell.GarouGammaRayBurstSpell;
import com.example.jjkspells.garou.spell.GarouGravityKnuckleSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Garou (One Punch Man, Cosmic Fear Mode) self-registration. Nothing outside the garou package is edited:
 * this MOD-bus subscriber registers the entity type and the spells through RegisterEvent.
 * Every future Garou spell adds one line to {@link #onRegister}.
 */
@SuppressWarnings("removal")
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GarouRegistry {
    private GarouRegistry() {}

    // Built inside onRegister, NOT here. A static initializer runs while Forge loads
    // the class during mod construction, when the entity registry is already frozen.
    public static EntityType<GammaRayBurstEntity> GAMMA_RAY_BURST;
    public static EntityType<GravityKnuckleEntity> GRAVITY_KNUCKLE;
    public static EntityType<CosmicPortalEntity> COSMIC_PORTAL;

    public static final String ENTITY_COSMIC_RADIATION = "garou_cosmic_radiation_aura";

    public static EntityType<CosmicRadiationEntity> COSMIC_RADIATION_AURA;

    /** Filled when the spell registry is built. */
    public static GarouCosmicRadiationSpell COSMIC_RADIATION;

    private static void buildEntityTypes() {
        GAMMA_RAY_BURST = EntityType.Builder.<GammaRayBurstEntity>of(GammaRayBurstEntity::new, MobCategory.MISC)
                .sized(0.2f, 0.2f)
                .fireImmune()
                .noSummon()
                .clientTrackingRange(12)   // 12 chunks: the 80 block beam stays visible from far away
                .updateInterval(1)
                .setShouldReceiveVelocityUpdates(false)
                .build("garou_gamma_ray_burst");

        GRAVITY_KNUCKLE = EntityType.Builder.<GravityKnuckleEntity>of(GravityKnuckleEntity::new, MobCategory.MISC)
                .sized(0.2f, 0.2f)
                .fireImmune()
                .noSummon()
                .clientTrackingRange(12)
                .updateInterval(1)
                .setShouldReceiveVelocityUpdates(false)
                .build("garou_gravity_knuckle");

        COSMIC_PORTAL = EntityType.Builder.<CosmicPortalEntity>of(CosmicPortalEntity::new, MobCategory.MISC)
                .sized(0.2f, 0.2f)
                .fireImmune()
                .noSummon()
                .clientTrackingRange(12)
                .updateInterval(1)
                .setShouldReceiveVelocityUpdates(false)
                .build("garou_cosmic_portal");

        COSMIC_RADIATION_AURA = EntityType.Builder.<CosmicRadiationEntity>of(CosmicRadiationEntity::new, MobCategory.MISC)
                .sized(0.2f, 0.2f)
                .noSave()
                .noSummon()
                .fireImmune()
                .clientTrackingRange(10)
                .updateInterval(1)
                .setShouldReceiveVelocityUpdates(false)
                .build(ENTITY_COSMIC_RADIATION);
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper ->
        {
                buildEntityTypes();
                helper.register(new ResourceLocation(JJKSpells.MODID, "garou_gamma_ray_burst"), GAMMA_RAY_BURST);
                helper.register(new ResourceLocation(JJKSpells.MODID, "garou_gravity_knuckle"), GRAVITY_KNUCKLE);
                helper.register(new ResourceLocation(JJKSpells.MODID, "garou_cosmic_portal"), COSMIC_PORTAL);
                helper.register(JJKSpells.id(ENTITY_COSMIC_RADIATION), COSMIC_RADIATION_AURA);
        });
        event.register(SpellRegistry.SPELL_REGISTRY_KEY, helper ->
        {
                helper.register(new ResourceLocation(JJKSpells.MODID, "garou_gamma_ray_burst"), new GarouGammaRayBurstSpell());
                helper.register(new ResourceLocation(JJKSpells.MODID, "garou_gravity_knuckle"), new GarouGravityKnuckleSpell());
                helper.register(new ResourceLocation(JJKSpells.MODID, "garou_cosmic_portal"), new GarouCosmicPortalSpell());
                COSMIC_RADIATION = new GarouCosmicRadiationSpell();
                helper.register(JJKSpells.id(GarouCosmicRadiationSpell.NAME), COSMIC_RADIATION);
        });
    }
}
