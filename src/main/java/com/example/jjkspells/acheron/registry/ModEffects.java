package com.example.jjkspells.acheron.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.acheron.effect.MarkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Slashed Dream on the caster (amplifier = count - 1, 0..8) and Crimson Knot on enemies (amplifier = count - 1). Visual counters; Nihility does the work. */
    public static final RegistryObject<MobEffect> DREAM = E.register("ach_dream", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xB0132A));
    public static final RegistryObject<MobEffect> KNOT = E.register("ach_knot", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xD81E3C));
    public static final RegistryObject<MobEffect> SUPPRESS = E.register("ach_suppress", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x5A4B7A));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
