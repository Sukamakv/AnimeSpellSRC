package com.example.jjkspells.irontomb;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Irontomb's own (synthesised) sound effects. They live in their own resource namespace, jjk_irontomb, so
 * assets/jjk_irontomb/sounds.json never collides with a sounds.json you already have for jjk_spells.
 */
public final class IrontombSounds {
    public static final String NS = "jjk_irontomb";
    public static final SoundEvent CHARGE = make("tomb_charge");
    public static final SoundEvent IMPACT = make("tomb_impact");
    public static final SoundEvent SLASH = make("tomb_slash");
    public static final SoundEvent BELL = make("tomb_bell");
    public static final SoundEvent SHATTER = make("tomb_shatter");
    public static final SoundEvent ROAR = make("tomb_roar");
    public static final SoundEvent GENESIS = make("tomb_genesis");
    public static final SoundEvent BOLT = make("tomb_bolt");
    public static final SoundEvent GRAVE = make("tomb_grave");
    public static final SoundEvent LOCK = make("tomb_lock");
    public static final SoundEvent SUCK = make("tomb_suck");
    public static final SoundEvent ERASE = make("tomb_erase");
    public static final SoundEvent[] ALL = {CHARGE, IMPACT, SLASH, BELL, SHATTER, ROAR, GENESIS, BOLT, GRAVE, LOCK, SUCK, ERASE};

    private IrontombSounds() {
    }

    private static SoundEvent make(String n) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation(NS, n));
    }
}
