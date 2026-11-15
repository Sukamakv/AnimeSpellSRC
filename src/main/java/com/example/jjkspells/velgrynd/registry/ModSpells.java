package com.example.jjkspells.velgrynd.registry;

import com.example.jjkspells.velgrynd.spell.*;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSpells {
    private static final DeferredRegister<AbstractSpell> SPELLS = com.example.jjkspells.ModSpells.SPELLS;

    public static final RegistryObject<AbstractSpell> BREATH = SPELLS.register("scorch_dragons_breath", ScorchDragonsBreathSpell::new);
    public static final RegistryObject<AbstractSpell> INFERNO = SPELLS.register("cardinal_inferno", CardinalInfernoSpell::new);
    public static final RegistryObject<AbstractSpell> ACCEL = SPELLS.register("cardinal_acceleration", CardinalAccelerationSpell::new);
    public static final RegistryObject<AbstractSpell> DOMINION = SPELLS.register("scorch_dragons_dominion", ScorchDragonsDominionSpell::new);
    public static final RegistryObject<AbstractSpell> PARALLEL = SPELLS.register("parallel_existence", ParallelExistenceSpell::new);
    public static final RegistryObject<AbstractSpell> PURSUIT = SPELLS.register("dimensional_scorch", DimensionalScorchSpell::new);
    public static final RegistryObject<AbstractSpell> STAR = SPELLS.register("cardinal_cataclysm", CardinalCataclysmSpell::new);
    public static final RegistryObject<AbstractSpell> CTHUGHA = SPELLS.register("flame_god_cthugha", FlameGodCthughaSpell::new);

    private ModSpells() {
    }

    /** Touching this class registers the spells; the main mod's register is added to the event bus by JJKSpells. */
    public static void init() {
    }
}
