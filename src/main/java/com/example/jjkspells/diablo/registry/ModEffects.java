package com.example.jjkspells.diablo.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.diablo.effect.MarkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Visual markers: the real work (armor, speed, immobilising, buffs) is done by the spells and DiaEvents. */
    public static final RegistryObject<MobEffect> HELLFIRE = E.register("dia_hellfire", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xC01818));
    public static final RegistryObject<MobEffect> SUPPRESS = E.register("dia_suppress", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x601040));
    public static final RegistryObject<MobEffect> WOUND = E.register("dia_wound", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x7A1040));
    public static final RegistryObject<MobEffect> DESPAIR = E.register("dia_despair", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x401060));
    public static final RegistryObject<MobEffect> DOMAIN = E.register("dia_domain", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x8A0A20));
    public static final RegistryObject<MobEffect> MARK = E.register("dia_mark", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xD01030));
    public static final RegistryObject<MobEffect> AUTHORITY = E.register("dia_authority", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xE0A030));
    public static final RegistryObject<MobEffect> NEGATION = E.register("dia_negation", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xD04050));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
