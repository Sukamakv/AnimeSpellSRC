package com.example.jjkspells.velgrynd.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.velgrynd.effect.MarkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Visual markers: the real work (armor, speed, immobilising, buffs) is done by the spells and VgrEvents. */
    public static final RegistryObject<MobEffect> SCORCH = E.register("vgr_scorch", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xFF4020));
    public static final RegistryObject<MobEffect> BRAND = E.register("vgr_brand", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xFFB030));
    public static final RegistryObject<MobEffect> OVERHEAT = E.register("vgr_overheat", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xFF7030));
    public static final RegistryObject<MobEffect> MARK = E.register("vgr_mark", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xC01030));
    public static final RegistryObject<MobEffect> SOVEREIGN = E.register("vgr_sovereign", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xFFD060));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
