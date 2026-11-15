package com.example.jjkspells.veldora.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.veldora.effect.MarkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Visual markers: the real work (armor, speed, immobilising, buffs) is done by the spells and VelEvents. */
    public static final RegistryObject<MobEffect> PARALYSIS = E.register("vel_paralysis", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x8A4BFF));
    public static final RegistryObject<MobEffect> OVERLOAD = E.register("vel_overload", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x7A2BD0));
    public static final RegistryObject<MobEffect> MARK = E.register("vel_mark", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x3A1070));
    public static final RegistryObject<MobEffect> BLESSING = E.register("vel_blessing", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xB070FF));
    public static final RegistryObject<MobEffect> ASCEND = E.register("vel_ascend", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xA040FF));
    public static final RegistryObject<MobEffect> CHAOS = E.register("vel_chaos", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0x6040E0));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
