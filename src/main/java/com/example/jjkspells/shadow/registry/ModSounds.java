package com.example.jjkspells.shadow.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> S = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, JJKSpells.MODID);

    public static final RegistryObject<SoundEvent> DISAPPEAR = reg("disappear");
    public static final RegistryObject<SoundEvent> SWORD_DRAW = reg("sword_draw");
    public static final RegistryObject<SoundEvent> SWORD_SLICE = reg("sword_slice");
    public static final RegistryObject<SoundEvent> SHEATH_CLICK = reg("sheath_click");
    public static final RegistryObject<SoundEvent> DELAYED_EXECUTION = reg("delayed_execution");
    public static final RegistryObject<SoundEvent> THRONE_MANIFEST = reg("throne_manifest");
    public static final RegistryObject<SoundEvent> ILLUSION_WHISPER = reg("illusion_whisper");
    public static final RegistryObject<SoundEvent> COUNTERATTACK = reg("counterattack");
    public static final RegistryObject<SoundEvent> ROYAL_AMBIENCE = reg("royal_ambience");
    public static final RegistryObject<SoundEvent> FINAL_JUDGMENT = reg("final_judgment");
    public static final RegistryObject<SoundEvent> SLIME_MORPH = reg("slime_morph");
    public static final RegistryObject<SoundEvent> SLIME_SERPENT_SCREECH = reg("slime_serpent_screech");
    public static final RegistryObject<SoundEvent> SLIME_SPEAR_PIERCE = reg("slime_spear_pierce");
    public static final RegistryObject<SoundEvent> SLIME_TENDRIL_WHIP = reg("slime_tendril_whip");
    public static final RegistryObject<SoundEvent> SLIME_CONSTRICTION = reg("slime_constriction");
    public static final RegistryObject<SoundEvent> SLIME_REFORMATION = reg("slime_reformation");
    public static final RegistryObject<SoundEvent> HEARTBEAT = reg("heartbeat");
    public static final RegistryObject<SoundEvent> COUNTER_READY = reg("counter_ready");
    public static final RegistryObject<SoundEvent> PERFECT_DODGE = reg("perfect_dodge");
    public static final RegistryObject<SoundEvent> COUNTER_SLASH = reg("counter_slash");
    public static final RegistryObject<SoundEvent> SWORD_LOWER = reg("sword_lower");
    public static final RegistryObject<SoundEvent> ATOMIC_CHARGE_START = reg("atomic_charge_start");
    public static final RegistryObject<SoundEvent> ATOMIC_COMPRESSION_LOOP = reg("atomic_compression_loop");
    public static final RegistryObject<SoundEvent> ATOMIC_HEARTBEAT = reg("atomic_heartbeat");
    public static final RegistryObject<SoundEvent> ATOMIC_DECLARATION = reg("atomic_declaration");
    public static final RegistryObject<SoundEvent> ATOMIC_RELEASE = reg("atomic_release");
    public static final RegistryObject<SoundEvent> ATOMIC_EXPLOSION = reg("atomic_explosion");
    public static final RegistryObject<SoundEvent> ATOMIC_SHOCKWAVE = reg("atomic_shockwave");
    public static final RegistryObject<SoundEvent> ATOMIC_AFTERMATH = reg("atomic_aftermath");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String n) {
        return S.register("shd_" + n, () -> SoundEvent.createVariableRangeEvent(JJKSpells.id("shd_" + n)));
    }

    public static void register(IEventBus bus) {
        S.register(bus);
    }
}
