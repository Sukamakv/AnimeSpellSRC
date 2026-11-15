package com.example.jjkspells.garou.registry;

import com.example.jjkspells.garou.spell.AllLifeEradicationFistSpell;
import com.example.jjkspells.garou.spell.NuclearFissionSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class NfSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> NUCLEAR_FISSION = SPELLS.register("nuclear_fission", NuclearFissionSpell::new);

    public static final RegistryObject<AbstractSpell> ALL_LIFE_ERADICATION_FIST = SPELLS.register("all_life_eradication_fist", AllLifeEradicationFistSpell::new);

    private NfSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
