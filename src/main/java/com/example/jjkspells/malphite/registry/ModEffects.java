package com.example.jjkspells.malphite.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.malphite.effect.MarkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Visual markers: the real work (armor, speed, immobilising, buffs) is done by the spells and MalEvents. */
    public static final RegistryObject<MobEffect> SHATTERED = E.register("mal_shattered", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xB87A4A));
    public static final RegistryObject<MobEffect> TREMOR = E.register("mal_tremor", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x8C7B6A));
    public static final RegistryObject<MobEffect> GRANITE = E.register("mal_granite", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0x7A7F86));
    public static final RegistryObject<MobEffect> RESILIENCE = E.register("mal_resilience", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xE88A30));
    public static final RegistryObject<MobEffect> QUAKE = E.register("mal_quake", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xA0702F));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
