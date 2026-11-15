package com.example.jjkspells.malphite.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> S = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<SoundEvent> GRIND = reg("grind");
    public static final RegistryObject<SoundEvent> THROW = reg("throw");
    public static final RegistryObject<SoundEvent> CRACK = reg("crack");
    public static final RegistryObject<SoundEvent> SLAM = reg("slam");
    public static final RegistryObject<SoundEvent> RUMBLE = reg("rumble");
    public static final RegistryObject<SoundEvent> PUNCH = reg("punch");
    public static final RegistryObject<SoundEvent> GRAVEL = reg("gravel");
    public static final RegistryObject<SoundEvent> ARMOR = reg("armor");
    public static final RegistryObject<SoundEvent> CHARGE = reg("charge");
    public static final RegistryObject<SoundEvent> BOOM = reg("boom");
    public static final RegistryObject<SoundEvent> SPIKE = reg("spike");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String n) {
        return S.register("mal_" + n, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id("mal_" + n)));
    }

    public static void register(IEventBus bus) {
        S.register(bus);
    }
}
