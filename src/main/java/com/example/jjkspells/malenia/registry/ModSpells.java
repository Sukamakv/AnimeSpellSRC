package com.example.jjkspells.malenia.registry;

import com.example.jjkspells.malenia.spell.WaterfowlDanceSpell;
import com.example.jjkspells.malenia.spell.PhantomBlossomSpell;
import com.example.jjkspells.malenia.spell.ScarletBloomSpell;
import com.example.jjkspells.malenia.spell.MiquellasBladeSpell;
import com.example.jjkspells.malenia.spell.GoddessOfRotSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> WATERFOWL = SPELLS.register("waterfowl_dance", WaterfowlDanceSpell::new);
    public static final RegistryObject<AbstractSpell> PHANTOM = SPELLS.register("phantom_blossom", PhantomBlossomSpell::new);
    public static final RegistryObject<AbstractSpell> BLOOM = SPELLS.register("scarlet_bloom", ScarletBloomSpell::new);
    public static final RegistryObject<AbstractSpell> BLADE = SPELLS.register("miquellas_blade", MiquellasBladeSpell::new);
    public static final RegistryObject<AbstractSpell> GODDESS = SPELLS.register("goddess_of_rot", GoddessOfRotSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
