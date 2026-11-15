package com.example.jjkspells.overlord.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> S = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<SoundEvent> DARK_CAST = reg("dark_cast");
    public static final RegistryObject<SoundEvent> BONE_RISE = reg("bone_rise");
    public static final RegistryObject<SoundEvent> BANSHEE_CRY = reg("banshee_cry");
    public static final RegistryObject<SoundEvent> HOLE_CHARGE = reg("hole_charge");
    public static final RegistryObject<SoundEvent> HOLE_COLLAPSE = reg("hole_collapse");
    public static final RegistryObject<SoundEvent> DEATH_HUM = reg("death_hum");
    public static final RegistryObject<SoundEvent> DEATH_PULSE = reg("death_pulse");
    public static final RegistryObject<SoundEvent> DEATH_WAVE = reg("death_wave");
    public static final RegistryObject<SoundEvent> HOLY_CHARGE = reg("holy_charge");
    public static final RegistryObject<SoundEvent> HOLY_BLAST = reg("holy_blast");
    public static final RegistryObject<SoundEvent> SLASH_LINE = reg("slash_line");
    public static final RegistryObject<SoundEvent> SLASH_TEAR = reg("slash_tear");
    public static final RegistryObject<SoundEvent> GOAT_ROAR = reg("goat_roar");
    public static final RegistryObject<SoundEvent> HEART_CRUSH = reg("heart_crush");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String n) {
        return S.register(n, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id(n)));
    }

    public static void register(IEventBus bus) {
        S.register(bus);
    }
}
