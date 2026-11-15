package com.example.jjkspells.pantheon.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.pantheon.effect.MarkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Visual markers: the real work (immobilising, burning, buffs) is done by the spells and PanEvents. */
    public static final RegistryObject<MobEffect> STUN = E.register("pan_stun", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xFFD24A));
    public static final RegistryObject<MobEffect> BURN = E.register("pan_burn", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xFFB030));
    public static final RegistryObject<MobEffect> UNBROKEN = E.register("pan_unbroken", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xFFE070));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
