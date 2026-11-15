package com.example.jjkspells.dragon.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> S = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<SoundEvent> ASCEND = reg("dragon_ascend");
    public static final RegistryObject<SoundEvent> BEAM = reg("dragon_beam");
    public static final RegistryObject<SoundEvent> AUTHORITY = reg("dragon_authority");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String n) {
        return S.register(n, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id(n)));
    }

    public static void register(IEventBus bus) {
        S.register(bus);
    }
}
