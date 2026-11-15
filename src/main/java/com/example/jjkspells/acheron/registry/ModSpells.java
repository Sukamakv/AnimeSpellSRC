package com.example.jjkspells.acheron.registry;

import com.example.jjkspells.acheron.spell.*;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> WILTCROSS = SPELLS.register("trilateral_wiltcross", TrilateralWiltcrossSpell::new);
    public static final RegistryObject<AbstractSpell> OCTOBOLT = SPELLS.register("octobolt_flash", OctoboltFlashSpell::new);
    public static final RegistryObject<AbstractSpell> QUAD = SPELLS.register("quadrivalent_ascendance", QuadrivalentAscendanceSpell::new);
    public static final RegistryObject<AbstractSpell> RAINLEAF = SPELLS.register("atop_rainleaf_hangs_oneness", AtopRainleafHangsOnenessSpell::new);
    public static final RegistryObject<AbstractSpell> RAINBLADE = SPELLS.register("rainblade", RainbladeSpell::new);
    public static final RegistryObject<AbstractSpell> STYGIAN = SPELLS.register("stygian_resurge", StygianResurgeSpell::new);
    public static final RegistryObject<AbstractSpell> ULTIMATE = SPELLS.register("slashed_dream_cries_in_red", SlashedDreamCriesInRedSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
