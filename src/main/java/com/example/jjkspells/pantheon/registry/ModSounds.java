package com.example.jjkspells.pantheon.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> S = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<SoundEvent> CHARGE = reg("charge");
    public static final RegistryObject<SoundEvent> THROW = reg("throw");
    public static final RegistryObject<SoundEvent> PIERCE = reg("pierce");
    public static final RegistryObject<SoundEvent> CLANG = reg("clang");
    public static final RegistryObject<SoundEvent> LEAP = reg("leap");
    public static final RegistryObject<SoundEvent> THRUST = reg("thrust");
    public static final RegistryObject<SoundEvent> BASH = reg("bash");
    public static final RegistryObject<SoundEvent> AEGIS = reg("aegis");
    public static final RegistryObject<SoundEvent> DIVE = reg("dive");
    public static final RegistryObject<SoundEvent> BOOM = reg("boom");
    public static final RegistryObject<SoundEvent> DIVINE = reg("divine");
    public static final RegistryObject<SoundEvent> BEAM = reg("beam");
    public static final RegistryObject<SoundEvent> FLAME = reg("flame");
    public static final RegistryObject<SoundEvent> STAR = reg("star");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String n) {
        return S.register("pan_" + n, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id("pan_" + n)));
    }

    public static void register(IEventBus bus) {
        S.register(bus);
    }
}
