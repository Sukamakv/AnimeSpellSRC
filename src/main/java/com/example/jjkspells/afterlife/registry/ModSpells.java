package com.example.jjkspells.afterlife.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.afterlife.spell.FuneralSpell;
import com.example.jjkspells.afterlife.spell.GuideToAfterlifeSpell;
import com.example.jjkspells.afterlife.spell.SecretSpearSpell;
import com.example.jjkspells.afterlife.spell.SpiritSootherSpell;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> GUIDE = SPELLS.register("guide_to_afterlife", GuideToAfterlifeSpell::new);
    public static final RegistryObject<AbstractSpell> SECRET_SPEAR = SPELLS.register("secret_spear_of_wangsheng", SecretSpearSpell::new);
    public static final RegistryObject<AbstractSpell> SPIRIT_SOOTHER = SPELLS.register("spirit_soother", SpiritSootherSpell::new);
    public static final RegistryObject<AbstractSpell> FUNERAL = SPELLS.register("funeral_of_a_thousand_butterflies", FuneralSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
