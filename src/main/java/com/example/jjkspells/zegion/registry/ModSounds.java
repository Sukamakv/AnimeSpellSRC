package com.example.jjkspells.zegion.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> S = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<SoundEvent> HUM = reg("hum");
    public static final RegistryObject<SoundEvent> CHARGE = reg("charge");
    public static final RegistryObject<SoundEvent> BLINK = reg("blink");
    public static final RegistryObject<SoundEvent> PUNCH = reg("punch");
    public static final RegistryObject<SoundEvent> SHATTER = reg("shatter");
    public static final RegistryObject<SoundEvent> BOOM = reg("boom");
    public static final RegistryObject<SoundEvent> WHOOSH = reg("whoosh");
    public static final RegistryObject<SoundEvent> MIST = reg("mist");
    public static final RegistryObject<SoundEvent> CHIME = reg("chime");
    public static final RegistryObject<SoundEvent> SLASH = reg("slash");
    public static final RegistryObject<SoundEvent> VOID = reg("void");
    public static final RegistryObject<SoundEvent> ARMOR = reg("armor");
    public static final RegistryObject<SoundEvent> WING = reg("wing");
    public static final RegistryObject<SoundEvent> TOLL = reg("toll");
    public static final RegistryObject<SoundEvent> RIFT = reg("rift");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String n) {
        return S.register("zeg_" + n, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id("zeg_" + n)));
    }

    public static void register(IEventBus bus) {
        S.register(bus);
    }
}
