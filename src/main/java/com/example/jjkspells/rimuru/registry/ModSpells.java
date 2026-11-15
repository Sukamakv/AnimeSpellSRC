package com.example.jjkspells.rimuru.registry;

import com.example.jjkspells.rimuru.spell.PredatorSpell;
import com.example.jjkspells.rimuru.spell.BlackLightningSpell;
import com.example.jjkspells.rimuru.spell.MegiddoSpell;
import com.example.jjkspells.rimuru.spell.AbsoluteDefenseSpell;
import com.example.jjkspells.rimuru.spell.SpatialDominationSpell;
import com.example.jjkspells.rimuru.spell.BeelzebuthSpell;
import com.example.jjkspells.rimuru.spell.ImaginaryCollapseSpell;
import com.example.jjkspells.rimuru.spell.VoidGodAzathothSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> PREDATOR = SPELLS.register("predator", PredatorSpell::new);
    public static final RegistryObject<AbstractSpell> LIGHTNING = SPELLS.register("black_lightning", BlackLightningSpell::new);
    public static final RegistryObject<AbstractSpell> MEGIDDO = SPELLS.register("megiddo", MegiddoSpell::new);
    public static final RegistryObject<AbstractSpell> DEFENSE = SPELLS.register("absolute_defense", AbsoluteDefenseSpell::new);
    public static final RegistryObject<AbstractSpell> SPATIAL = SPELLS.register("spatial_domination", SpatialDominationSpell::new);
    public static final RegistryObject<AbstractSpell> BEELZEBUTH = SPELLS.register("beelzebuth", BeelzebuthSpell::new);
    public static final RegistryObject<AbstractSpell> COLLAPSE = SPELLS.register("imaginary_collapse", ImaginaryCollapseSpell::new);
    public static final RegistryObject<AbstractSpell> AZATHOTH = SPELLS.register("void_god_azathoth", VoidGodAzathothSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
