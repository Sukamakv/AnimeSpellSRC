package com.example.jjkspells.pantheon.registry;

import com.example.jjkspells.pantheon.spell.AegisStormSpell;
import com.example.jjkspells.pantheon.spell.CometSpearSpell;
import com.example.jjkspells.pantheon.spell.GrandStarfallSpell;
import com.example.jjkspells.pantheon.spell.ShieldVaultSpell;
import com.example.jjkspells.pantheon.spell.StarfallExecutionSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> COMET = SPELLS.register("comet_spear", CometSpearSpell::new);
    public static final RegistryObject<AbstractSpell> VAULT = SPELLS.register("shield_vault", ShieldVaultSpell::new);
    public static final RegistryObject<AbstractSpell> AEGIS = SPELLS.register("aegis_storm", AegisStormSpell::new);
    public static final RegistryObject<AbstractSpell> STARFALL = SPELLS.register("starfall_execution", StarfallExecutionSpell::new);
    public static final RegistryObject<AbstractSpell> GRAND = SPELLS.register("grand_starfall", GrandStarfallSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
