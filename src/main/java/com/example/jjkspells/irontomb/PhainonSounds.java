package com.example.jjkspells.irontomb;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Phainon's own synthesised sounds, in the resource namespace jjk_phainon (its own sounds.json, no clash with yours). */
public final class PhainonSounds {
    public static final String NS = "jjk_phainon";
    public static final SoundEvent CHARGE = make("ph_charge");
    public static final SoundEvent LANCE = make("ph_lance");
    public static final SoundEvent DASH = make("ph_dash");
    public static final SoundEvent FEATHER = make("ph_feather");
    public static final SoundEvent IMPACT = make("ph_impact");
    public static final SoundEvent SUNFALL = make("ph_sunfall");
    public static final SoundEvent HALO = make("ph_halo");
    public static final SoundEvent SWEEP = make("ph_sweep");
    public static final SoundEvent WING = make("ph_wing");
    public static final SoundEvent ROAR = make("ph_roar");
    public static final SoundEvent DEATH = make("ph_death");
    public static final SoundEvent JUDGMENT = make("ph_judgment");
    public static final SoundEvent[] ALL = {CHARGE, LANCE, DASH, FEATHER, IMPACT, SUNFALL, HALO, SWEEP, WING, ROAR, DEATH, JUDGMENT};

    private PhainonSounds() {
    }

    private static SoundEvent make(String n) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation(NS, n));
    }
}
