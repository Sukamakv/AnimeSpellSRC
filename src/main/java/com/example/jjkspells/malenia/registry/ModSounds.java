package com.example.jjkspells.malenia.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> S = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<SoundEvent> SLASH = reg("slash");
    public static final RegistryObject<SoundEvent> FLURRY = reg("flurry");
    public static final RegistryObject<SoundEvent> RING = reg("ring");
    public static final RegistryObject<SoundEvent> DASH = reg("dash");
    public static final RegistryObject<SoundEvent> HUM = reg("hum");
    public static final RegistryObject<SoundEvent> BLOOM = reg("bloom");
    public static final RegistryObject<SoundEvent> WINGS = reg("wings");
    public static final RegistryObject<SoundEvent> BOOM = reg("boom");
    public static final RegistryObject<SoundEvent> TRANSFORM = reg("transform");
    public static final RegistryObject<SoundEvent> STAB = reg("stab");
    public static final RegistryObject<SoundEvent> PHANTOM = reg("phantom");
    public static final RegistryObject<SoundEvent> ROT = reg("rot");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String n) {
        return S.register("mel_" + n, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id("mel_" + n)));
    }

    public static void register(IEventBus bus) {
        S.register(bus);
    }
}
