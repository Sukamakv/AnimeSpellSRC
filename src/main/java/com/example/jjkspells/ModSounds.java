package com.example.jjkspells;

import com.example.jjkspells.JJKSpells;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<SoundEvent> LAPSE_BLUE_CHARGE = register("lapse_blue_charge");
    public static final RegistryObject<SoundEvent> LAPSE_BLUE_CAST = register("lapse_blue_cast");
    public static final RegistryObject<SoundEvent> LAPSE_BLUE_HUM = register("lapse_blue_hum");
    public static final RegistryObject<SoundEvent> REVERSAL_RED_CHARGE = register("reversal_red_charge");
    public static final RegistryObject<SoundEvent> REVERSAL_RED_CAST = register("reversal_red_cast");
    public static final RegistryObject<SoundEvent> REVERSAL_RED_IMPACT = register("reversal_red_impact");

    public static final RegistryObject<SoundEvent> TIME_TICK = register("time_tick");
    public static final RegistryObject<SoundEvent> TIME_CHARGE = register("time_charge");
    public static final RegistryObject<SoundEvent> TIME_STOP = register("time_stop");
    public static final RegistryObject<SoundEvent> TIME_RESUME = register("time_resume");
    public static final RegistryObject<SoundEvent> TIME_BELL = register("time_bell");
    public static final RegistryObject<SoundEvent> TIME_THROB = register("time_throb");

    public static final RegistryObject<SoundEvent> STORM_CHARGE = register("storm_charge");
    public static final RegistryObject<SoundEvent> STORM_SUMMON = register("storm_summon");
    public static final RegistryObject<SoundEvent> STORM_LOOP = register("storm_loop");
    public static final RegistryObject<SoundEvent> THUNDER_CRACK = register("thunder_crack");
    public static final RegistryObject<SoundEvent> STORM_END = register("storm_end");

    public static final RegistryObject<SoundEvent> BARRAGE_START = register("barrage_start");
    public static final RegistryObject<SoundEvent> SWORD_SWING = register("sword_swing");
    public static final RegistryObject<SoundEvent> SLASH_HIT = register("slash_hit");
    public static final RegistryObject<SoundEvent> BARRAGE_END = register("barrage_end");

    public static final RegistryObject<SoundEvent> GATE_OPEN = register("gate_open");
    public static final RegistryObject<SoundEvent> GATE_FIRE = register("gate_fire");
    public static final RegistryObject<SoundEvent> WEAPON_IMPACT = register("weapon_impact");
    public static final RegistryObject<SoundEvent> CHAIN_BURST = register("chain_burst");
    public static final RegistryObject<SoundEvent> CHAIN_SHATTER = register("chain_shatter");
    public static final RegistryObject<SoundEvent> AUTHORITY = register("authority");
    public static final RegistryObject<SoundEvent> EA_SPIN = register("ea_spin");
    public static final RegistryObject<SoundEvent> EA_FIRE = register("ea_fire");
    public static final RegistryObject<SoundEvent> EA_RUMBLE = register("ea_rumble");

    public static final RegistryObject<SoundEvent> SUN_SLASH = register("sun_slash");
    public static final RegistryObject<SoundEvent> SUN_THRUST = register("sun_thrust");
    public static final RegistryObject<SoundEvent> SUN_ERUPTION = register("sun_eruption");

    public static final RegistryObject<SoundEvent> SP_SUMMON = register("sp_summon");
    public static final RegistryObject<SoundEvent> SP_DASH = register("sp_dash");
    public static final RegistryObject<SoundEvent> SP_GUARD = register("sp_guard");
    public static final RegistryObject<SoundEvent> SP_SMASH = register("sp_smash");
    public static final RegistryObject<SoundEvent> DIO_SUMMON = register("dio_summon");
    public static final RegistryObject<SoundEvent> DIO_BARRAGE = register("dio_barrage");
    public static final RegistryObject<SoundEvent> DIO_BEAM = register("dio_beam");
    public static final RegistryObject<SoundEvent> DIO_IMPACT = register("dio_impact");
    public static final RegistryObject<SoundEvent> ROLLER_CRASH = register("roller_crash");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id(name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
