package com.example.jjkspells.dragon.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dragon.spell.TrueDragonAscensionSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, JJKSpells.MODID);

    public static final RegistryObject<AbstractSpell> TRUE_DRAGON_ASCENSION = SPELLS.register("true_dragon_ascension", TrueDragonAscensionSpell::new);

    private ModSpells() {
    }

    public static void register(IEventBus bus) {
        SPELLS.register(bus);
    }
}
