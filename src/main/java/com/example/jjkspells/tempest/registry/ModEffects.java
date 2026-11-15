package com.example.jjkspells.tempest.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.tempest.effect.MarkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Visual markers: the real work (armor, speed, immobilising, buffs) is done by the spells and TemEvents. */
    public static final RegistryObject<MobEffect> PARALYSIS = E.register("tem_paralysis", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x6B5BFF));
    public static final RegistryObject<MobEffect> DEVOUR = E.register("tem_devour", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x2A7BFF));
    public static final RegistryObject<MobEffect> WOUND = E.register("tem_wound", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x3050C0));
    public static final RegistryObject<MobEffect> FRACTURE = E.register("tem_fracture", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x6A2BC0));
    public static final RegistryObject<MobEffect> MARK = E.register("tem_mark", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x1030A0));
    public static final RegistryObject<MobEffect> BLESSING = E.register("tem_blessing", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0x40A8FF));
    public static final RegistryObject<MobEffect> BARRIER = E.register("tem_barrier", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xF0C860));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
