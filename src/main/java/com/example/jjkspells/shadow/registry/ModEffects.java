package com.example.jjkspells.shadow.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.shadow.effect.MarkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Visual markers: the real work (marks, speed, immobilising, buffs) is done by the spells and ShdEvents. */
    public static final RegistryObject<MobEffect> MARK = E.register("shd_mark", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x31104F));
    public static final RegistryObject<MobEffect> BIND = E.register("shd_bind", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x24232B));
    public static final RegistryObject<MobEffect> SUPPRESS = E.register("shd_suppress", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x6D19B8));
    public static final RegistryObject<MobEffect> RESIDUAL = E.register("shd_residual", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xAE4DFF));
    public static final RegistryObject<MobEffect> FOCUS = E.register("shd_focus", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xD8D6E8));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
