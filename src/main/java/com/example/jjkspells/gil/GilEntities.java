package com.example.jjkspells.gil;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.gil.entity.GilFxEntity;
import com.example.jjkspells.gil.entity.VimanaEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class GilEntities {
    private static final DeferredRegister<EntityType<?>> E = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, JJKSpells.MODID);

    /** Giant weapons, Merodach, slashes, magic circles. Server-placed, client-drawn. */
    public static final RegistryObject<EntityType<GilFxEntity>> FX = E.register("gil_fx", () -> EntityType.Builder.<GilFxEntity>of(GilFxEntity::new, MobCategory.MISC)
            .sized(0.5f, 0.5f).clientTrackingRange(24).updateInterval(1).fireImmune().noSave().build(JJKSpells.id("gil_fx").toString()));

    /** Vimana, the flying vessel. */
    public static final RegistryObject<EntityType<VimanaEntity>> VIMANA = E.register("vimana", () -> EntityType.Builder.<VimanaEntity>of(VimanaEntity::new, MobCategory.MISC)
            .sized(3.0f, 1.0f).clientTrackingRange(24).updateInterval(1).fireImmune().noSave().build(JJKSpells.id("vimana").toString()));

    private GilEntities() {}

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
