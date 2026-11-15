package com.example.jjkspells.acheron.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> S = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<SoundEvent> ACHERON_WEAPON_SWING_01 = reg("acheron_weapon_swing_01");
    public static final RegistryObject<SoundEvent> ACHERON_WEAPON_SWING_02 = reg("acheron_weapon_swing_02");
    public static final RegistryObject<SoundEvent> ACHERON_VIOLET_IMPACT = reg("acheron_violet_impact");
    public static final RegistryObject<SoundEvent> ACHERON_SHEATH = reg("acheron_sheath");
    public static final RegistryObject<SoundEvent> WILTCROSS_CROSS = reg("wiltcross_cross");
    public static final RegistryObject<SoundEvent> OCTOBOLT_DASH = reg("octobolt_dash");
    public static final RegistryObject<SoundEvent> OCTOBOLT_SLASH = reg("octobolt_slash");
    public static final RegistryObject<SoundEvent> OCTOBOLT_LIGHTNING = reg("octobolt_lightning");
    public static final RegistryObject<SoundEvent> OCTOBOLT_FINAL = reg("octobolt_final");
    public static final RegistryObject<SoundEvent> CRIMSON_KNOT_APPLY = reg("crimson_knot_apply");
    public static final RegistryObject<SoundEvent> CRIMSON_KNOT_SHATTER = reg("crimson_knot_shatter");
    public static final RegistryObject<SoundEvent> QUADRIVALENT_DASH = reg("quadrivalent_dash");
    public static final RegistryObject<SoundEvent> QUADRIVALENT_EXECUTION = reg("quadrivalent_execution");
    public static final RegistryObject<SoundEvent> QUADRIVALENT_IMPACT = reg("quadrivalent_impact");
    public static final RegistryObject<SoundEvent> RAINLEAF_CAST = reg("rainleaf_cast");
    public static final RegistryObject<SoundEvent> RAINLEAF_TICK = reg("rainleaf_tick");
    public static final RegistryObject<SoundEvent> RAINLEAF_TRANSFER = reg("rainleaf_transfer");
    public static final RegistryObject<SoundEvent> RAINBLADE_CHARGE = reg("rainblade_charge");
    public static final RegistryObject<SoundEvent> RAINBLADE_CUT = reg("rainblade_cut");
    public static final RegistryObject<SoundEvent> RAINBLADE_FRACTURE = reg("rainblade_fracture");
    public static final RegistryObject<SoundEvent> RAINBLADE_FINAL = reg("rainblade_final");
    public static final RegistryObject<SoundEvent> STYGIAN_CHARGE = reg("stygian_charge");
    public static final RegistryObject<SoundEvent> STYGIAN_DESCENT = reg("stygian_descent");
    public static final RegistryObject<SoundEvent> STYGIAN_IMPACT = reg("stygian_impact");
    public static final RegistryObject<SoundEvent> STYGIAN_SHOCKWAVE = reg("stygian_shockwave");
    public static final RegistryObject<SoundEvent> ACHERON_ULTIMATE_SILENCE = reg("acheron_ultimate_silence");
    public static final RegistryObject<SoundEvent> ACHERON_ULTIMATE_SUN = reg("acheron_ultimate_sun");
    public static final RegistryObject<SoundEvent> ACHERON_ULTIMATE_FLOWER = reg("acheron_ultimate_flower");
    public static final RegistryObject<SoundEvent> ACHERON_ULTIMATE_RAINBLADE = reg("acheron_ultimate_rainblade");
    public static final RegistryObject<SoundEvent> ACHERON_ULTIMATE_CHARGE = reg("acheron_ultimate_charge");
    public static final RegistryObject<SoundEvent> ACHERON_ULTIMATE_FLASH = reg("acheron_ultimate_flash");
    public static final RegistryObject<SoundEvent> ACHERON_ULTIMATE_AFTERMATH = reg("acheron_ultimate_aftermath");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String n) {
        return S.register("ach_" + n, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id("ach_" + n)));
    }

    public static void register(IEventBus bus) {
        S.register(bus);
    }
}
