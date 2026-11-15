package com.example.jjkspells.afterlife.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.afterlife.effect.BloodBlossomEffect;
import com.example.jjkspells.afterlife.effect.GuideEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    public static final RegistryObject<MobEffect> GUIDE = E.register("al_guide", GuideEffect::new);
    public static final RegistryObject<MobEffect> BLOOD_BLOSSOM = E.register("al_blood_blossom", BloodBlossomEffect::new);

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
