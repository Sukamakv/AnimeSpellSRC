package com.example.jjkspells.rimuru.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.rimuru.effect.MarkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Visual markers: the real work (armor, speed, immobilising, buffs) is done by the spells and RimEvents. */
    public static final RegistryObject<MobEffect> PARALYSIS = E.register("rim_paralysis", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x9A4DFF));
    public static final RegistryObject<MobEffect> DISTORT = E.register("rim_distort", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x5A3AC0));
    public static final RegistryObject<MobEffect> SPATIAL = E.register("rim_spatial", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x20B0D0));
    public static final RegistryObject<MobEffect> GLUTTONY = E.register("rim_gluttony", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x1A2A80));
    public static final RegistryObject<MobEffect> CHAOS = E.register("rim_chaos", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0x4060FF));
    public static final RegistryObject<MobEffect> BARRIER = E.register("rim_barrier", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xFFD870));
    public static final RegistryObject<MobEffect> ADAPT = E.register("rim_adapt", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0x3A7BFF));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
