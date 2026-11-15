package com.example.jjkspells.overlord.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.overlord.spell.*;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    /** Shares the main mod's spell register, so the spells show up in its creative tab like the others. */
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> SUMMON_UNDEAD = SPELLS.register("summon_undead", SummonUndeadSpell::new);
    public static final RegistryObject<AbstractSpell> CRY_OF_THE_BANSHEE = SPELLS.register("cry_of_the_banshee", BansheeCrySpell::new);
    public static final RegistryObject<AbstractSpell> BLACK_HOLE = SPELLS.register("black_hole", BlackHoleSpell::new);
    public static final RegistryObject<AbstractSpell> GOAL_OF_ALL_LIFE_IS_DEATH = SPELLS.register("goal_of_all_life_is_death", GoalOfDeathSpell::new);
    public static final RegistryObject<AbstractSpell> FALLEN_DOWN = SPELLS.register("fallen_down", FallenDownSpell::new);
    public static final RegistryObject<AbstractSpell> REALITY_SLASH = SPELLS.register("reality_slash", RealitySlashSpell::new);
    public static final RegistryObject<AbstractSpell> BLACK_GOAT_SACRIFICE = SPELLS.register("black_goat_sacrifice", BlackGoatSpell::new);
    public static final RegistryObject<AbstractSpell> GRASP_HEART = SPELLS.register("grasp_heart", GraspHeartSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
