package com.example.jjkspells.dragon.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.dragon.effect.AuthorityEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    public static final RegistryObject<MobEffect> AUTHORITY = E.register("true_dragon_authority", AuthorityEffect::new);

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
