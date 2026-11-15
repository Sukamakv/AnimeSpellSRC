package com.example.jjkspells.zegion.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.zegion.effect.MarkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Visual markers: the real work is done by the spells and ZegEvents. */
    public static final RegistryObject<MobEffect> FRACTURE = E.register("zeg_fracture", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x9A5CFF));
    public static final RegistryObject<MobEffect> ILLUSION = E.register("zeg_illusion", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x6A32C8));
    public static final RegistryObject<MobEffect> CARAPACE = E.register("zeg_carapace", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0x7B4BD8));
    public static final RegistryObject<MobEffect> PHANTOM = E.register("zeg_phantom", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xB58CFF));
    public static final RegistryObject<MobEffect> IMPRISON = E.register("zeg_imprison", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x4B2A99));
    public static final RegistryObject<MobEffect> GRAVITY = E.register("zeg_gravity", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x2A0F55));
    public static final RegistryObject<MobEffect> SOVEREIGN = E.register("zeg_sovereign", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xC9A0FF));
    public static final RegistryObject<MobEffect> EXTINCTION = E.register("zeg_extinction", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0x8E3BFF));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
