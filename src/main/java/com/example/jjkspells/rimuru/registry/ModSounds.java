package com.example.jjkspells.rimuru.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> S = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<SoundEvent> SLIME = reg("slime");
    public static final RegistryObject<SoundEvent> SUCTION = reg("suction");
    public static final RegistryObject<SoundEvent> CRACKLE = reg("crackle");
    public static final RegistryObject<SoundEvent> THUNDER = reg("thunder");
    public static final RegistryObject<SoundEvent> LASER = reg("laser");
    public static final RegistryObject<SoundEvent> BARRIER = reg("barrier");
    public static final RegistryObject<SoundEvent> PING = reg("ping");
    public static final RegistryObject<SoundEvent> TEAR = reg("tear");
    public static final RegistryObject<SoundEvent> VORTEX = reg("vortex");
    public static final RegistryObject<SoundEvent> CHARGE = reg("charge");
    public static final RegistryObject<SoundEvent> COLLAPSE = reg("collapse");
    public static final RegistryObject<SoundEvent> VOID = reg("void");
    public static final RegistryObject<SoundEvent> HUM = reg("hum");
    public static final RegistryObject<SoundEvent> BOOM = reg("boom");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String n) {
        return S.register("rim_" + n, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id("rim_" + n)));
    }

    public static void register(IEventBus bus) {
        S.register(bus);
    }
}
