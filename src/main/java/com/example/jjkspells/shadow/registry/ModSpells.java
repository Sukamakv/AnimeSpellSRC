package com.example.jjkspells.shadow.registry;

import com.example.jjkspells.shadow.spell.*;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> GARDEN = SPELLS.register("shadows_garden", ShadowsGardenSpell::new);
    public static final RegistryObject<AbstractSpell> THRONE = SPELLS.register("nameless_king", NamelessKingSpell::new);
    public static final RegistryObject<AbstractSpell> SERPENT = SPELLS.register("slime_armament", SlimeArmamentSpell::new);
    public static final RegistryObject<AbstractSpell> COUNTER = SPELLS.register("perfect_counter", PerfectCounterSpell::new);
    public static final RegistryObject<AbstractSpell> ATOMIC = SPELLS.register("i_am_atomic", IAmAtomicSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
