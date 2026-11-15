package com.example.jjkspells.afterlife.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> S = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<SoundEvent> IGNITE = reg("ignite");
    public static final RegistryObject<SoundEvent> DASH = reg("dash");
    public static final RegistryObject<SoundEvent> STRIKE = reg("strike");
    public static final RegistryObject<SoundEvent> GHOST_SUMMON = reg("ghost_summon");
    public static final RegistryObject<SoundEvent> GHOST_RUSH = reg("ghost_rush");
    public static final RegistryObject<SoundEvent> GHOST_BOOM = reg("ghost_boom");
    public static final RegistryObject<SoundEvent> HEAL = reg("heal");
    public static final RegistryObject<SoundEvent> PORTAL = reg("portal");
    public static final RegistryObject<SoundEvent> GHOST_CRY = reg("ghost_cry");
    public static final RegistryObject<SoundEvent> ULT_BOOM = reg("ult_boom");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String n) {
        return S.register("al_" + n, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id("al_" + n)));
    }

    public static void register(IEventBus bus) {
        S.register(bus);
    }
}
