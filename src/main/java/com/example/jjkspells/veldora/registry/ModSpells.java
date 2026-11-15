package com.example.jjkspells.veldora.registry;

import com.example.jjkspells.veldora.spell.*;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> WRATH = SPELLS.register("storm_dragons_wrath", StormDragonsWrathSpell::new);
    public static final RegistryObject<AbstractSpell> THUNDER = SPELLS.register("black_thunder_apocalypse", BlackThunderApocalypseSpell::new);
    public static final RegistryObject<AbstractSpell> BREATH = SPELLS.register("dragon_breath", DragonBreathSpell::new);
    public static final RegistryObject<AbstractSpell> FIST = SPELLS.register("storm_dragons_fist", StormDragonsFistSpell::new);
    public static final RegistryObject<AbstractSpell> CHAOS = SPELLS.register("law_of_impossible_fate", LawOfImpossibleFateSpell::new);
    public static final RegistryObject<AbstractSpell> ASCEND = SPELLS.register("storm_dragons_ascension", StormDragonsAscensionSpell::new);
    public static final RegistryObject<AbstractSpell> NOVA = SPELLS.register("dragon_nova", DragonNovaSpell::new);
    public static final RegistryObject<AbstractSpell> ETERNAL = SPELLS.register("end_of_the_eternal_storm", EndOfTheEternalStormSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
