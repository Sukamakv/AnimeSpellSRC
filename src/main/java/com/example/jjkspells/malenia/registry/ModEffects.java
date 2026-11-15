package com.example.jjkspells.malenia.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.malenia.effect.MarkEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    /** Visual markers: the real work (armor, speed, immobilising, buffs) is done by the spells and MelEvents. */
    public static final RegistryObject<MobEffect> WOUND = E.register("mel_wound", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xC02030));
    public static final RegistryObject<MobEffect> ROT = E.register("mel_rot", () -> new MarkEffect(MobEffectCategory.HARMFUL, 0xB8452A));
    public static final RegistryObject<MobEffect> GODDESS = E.register("mel_goddess", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xE03040));
    public static final RegistryObject<MobEffect> SWIFT = E.register("mel_swift", () -> new MarkEffect(MobEffectCategory.BENEFICIAL, 0xD8D0C0));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
