package com.example.jjkspells.prime.fx;

import com.example.jjkspells.JJKSpells;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Registers the visual entity of the PRIME Sukuna spells straight on the RegisterEvent (no other file needs editing). */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PrimeFxRegistry {
    public static final String ID = "prime_fx";

    /** Assigned while entity types are registered, long before anything spawns or renders. */
    public static EntityType<PrimeFxEntity> TYPE;

    private PrimeFxRegistry() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            TYPE = EntityType.Builder.<PrimeFxEntity>of(PrimeFxEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build(JJKSpells.id(ID).toString());
            helper.register(JJKSpells.id(ID), TYPE);
        });
    }
}
