package com.example.jjkspells.malphite.registry;

import com.example.jjkspells.malphite.spell.BrutalStrikesSpell;
import com.example.jjkspells.malphite.spell.GraniteShieldSpell;
import com.example.jjkspells.malphite.spell.SeismicShardSpell;
import com.example.jjkspells.malphite.spell.ThunderclapSpell;
import com.example.jjkspells.malphite.spell.UnstoppableForceSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> SHARD = SPELLS.register("seismic_shard", SeismicShardSpell::new);
    public static final RegistryObject<AbstractSpell> CLAP = SPELLS.register("thunderclap", ThunderclapSpell::new);
    public static final RegistryObject<AbstractSpell> STRIKES = SPELLS.register("brutal_strikes", BrutalStrikesSpell::new);
    public static final RegistryObject<AbstractSpell> GRANITE = SPELLS.register("granite_shield", GraniteShieldSpell::new);
    public static final RegistryObject<AbstractSpell> FORCE = SPELLS.register("unstoppable_force", UnstoppableForceSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
