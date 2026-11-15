package com.example.jjkspells.zegion.registry;

import com.example.jjkspells.zegion.spell.*;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> IMPACT = SPELLS.register("kaisers_impact", KaisersImpactSpell::new);
    public static final RegistryObject<AbstractSpell> DOMAIN = SPELLS.register("mist_lords_domain", MistLordsDomainSpell::new);
    public static final RegistryObject<AbstractSpell> OBSIDIAN = SPELLS.register("obsidian_kaiser", ObsidianKaiserSpell::new);
    public static final RegistryObject<AbstractSpell> PHANTOM = SPELLS.register("phantom_kaiser", PhantomKaiserSpell::new);
    public static final RegistryObject<AbstractSpell> LABYRINTH = SPELLS.register("mephistos_labyrinth", MephistosLabyrinthSpell::new);
    public static final RegistryObject<AbstractSpell> VOID = SPELLS.register("void_carapace", VoidCarapaceSpell::new);
    public static final RegistryObject<AbstractSpell> FORM = SPELLS.register("kaisers_final_form", KaisersFinalFormSpell::new);
    public static final RegistryObject<AbstractSpell> MEPHISTO = SPELLS.register("illusion_king_mephisto", IllusionKingMephistoSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
