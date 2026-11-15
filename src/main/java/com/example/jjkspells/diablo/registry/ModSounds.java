package com.example.jjkspells.diablo.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> S = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<SoundEvent> HUM = reg("hum");
    public static final RegistryObject<SoundEvent> HEARTBEAT = reg("heartbeat");
    public static final RegistryObject<SoundEvent> SNAP = reg("snap");
    public static final RegistryObject<SoundEvent> TICK = reg("tick");
    public static final RegistryObject<SoundEvent> BOOM = reg("boom");
    public static final RegistryObject<SoundEvent> ROAR = reg("roar");
    public static final RegistryObject<SoundEvent> TEAR = reg("tear");
    public static final RegistryObject<SoundEvent> CRACKLE = reg("crackle");
    public static final RegistryObject<SoundEvent> COLLAPSE = reg("collapse");
    public static final RegistryObject<SoundEvent> CHARGE = reg("charge");
    public static final RegistryObject<SoundEvent> PULSE = reg("pulse");
    public static final RegistryObject<SoundEvent> VORTEX = reg("vortex");
    public static final RegistryObject<SoundEvent> CHAIN = reg("chain");
    public static final RegistryObject<SoundEvent> BELL = reg("bell");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String n) {
        return S.register("dia_" + n, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id("dia_" + n)));
    }

    public static void register(IEventBus bus) {
        S.register(bus);
    }
}
