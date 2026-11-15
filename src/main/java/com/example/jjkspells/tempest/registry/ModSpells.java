package com.example.jjkspells.tempest.registry;

import com.example.jjkspells.tempest.spell.WorldDevouringAbyssSpell;
import com.example.jjkspells.tempest.spell.CivilizationStormSpell;
import com.example.jjkspells.tempest.spell.HeavenlyExecutionArraySpell;
import com.example.jjkspells.tempest.spell.AbsoluteLawOfProtectionSpell;
import com.example.jjkspells.tempest.spell.FractureOfInfiniteWorldsSpell;
import com.example.jjkspells.tempest.spell.AbyssThatConsumesAllSpell;
import com.example.jjkspells.tempest.spell.BirthOfNothingnessSpell;
import com.example.jjkspells.tempest.spell.EndOfAllCreationSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> ABYSS = SPELLS.register("world_devouring_abyss", WorldDevouringAbyssSpell::new);
    public static final RegistryObject<AbstractSpell> STORM = SPELLS.register("civilization_storm", CivilizationStormSpell::new);
    public static final RegistryObject<AbstractSpell> ARRAY = SPELLS.register("heavenly_execution_array", HeavenlyExecutionArraySpell::new);
    public static final RegistryObject<AbstractSpell> LAW = SPELLS.register("absolute_law_of_protection", AbsoluteLawOfProtectionSpell::new);
    public static final RegistryObject<AbstractSpell> FRACTURE = SPELLS.register("fracture_of_infinite_worlds", FractureOfInfiniteWorldsSpell::new);
    public static final RegistryObject<AbstractSpell> GLUTTONY = SPELLS.register("abyss_that_consumes_all", AbyssThatConsumesAllSpell::new);
    public static final RegistryObject<AbstractSpell> NOTHING = SPELLS.register("birth_of_nothingness", BirthOfNothingnessSpell::new);
    public static final RegistryObject<AbstractSpell> AZATHOTH = SPELLS.register("end_of_all_creation", EndOfAllCreationSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
