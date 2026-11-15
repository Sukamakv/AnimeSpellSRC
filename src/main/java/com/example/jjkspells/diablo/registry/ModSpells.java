package com.example.jjkspells.diablo.registry;

import com.example.jjkspells.diablo.spell.NetherApocalypseSpell;
import com.example.jjkspells.diablo.spell.PrimordialDominionSpell;
import com.example.jjkspells.diablo.spell.DimensionalFuneralSpell;
import com.example.jjkspells.diablo.spell.AbsoluteNegationSpell;
import com.example.jjkspells.diablo.spell.DespairTimeSpell;
import com.example.jjkspells.diablo.spell.TemptationWorldSpell;
import com.example.jjkspells.diablo.spell.AbyssalGenesisSpell;
import com.example.jjkspells.diablo.spell.TemptationKingAzazelSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> NETHER = SPELLS.register("nether_apocalypse", NetherApocalypseSpell::new);
    public static final RegistryObject<AbstractSpell> DOMINION = SPELLS.register("primordial_dominion", PrimordialDominionSpell::new);
    public static final RegistryObject<AbstractSpell> FUNERAL = SPELLS.register("dimensional_funeral", DimensionalFuneralSpell::new);
    public static final RegistryObject<AbstractSpell> NEGATION = SPELLS.register("absolute_negation", AbsoluteNegationSpell::new);
    public static final RegistryObject<AbstractSpell> DESPAIR = SPELLS.register("despair_time", DespairTimeSpell::new);
    public static final RegistryObject<AbstractSpell> WORLD = SPELLS.register("temptation_world", TemptationWorldSpell::new);
    public static final RegistryObject<AbstractSpell> GENESIS = SPELLS.register("abyssal_genesis", AbyssalGenesisSpell::new);
    public static final RegistryObject<AbstractSpell> AZAZEL = SPELLS.register("temptation_king_azazel", TemptationKingAzazelSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
