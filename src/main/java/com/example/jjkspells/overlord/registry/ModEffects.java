package com.example.jjkspells.overlord.registry;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.overlord.effect.AscendanceEffect;
import com.example.jjkspells.overlord.effect.DeathCurseEffect;
import com.example.jjkspells.overlord.effect.FearEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> E = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, JJKSpells.MODID);

    public static final RegistryObject<MobEffect> FEAR = E.register("fear", FearEffect::new);
    public static final RegistryObject<MobEffect> DEATH_CURSE = E.register("death_curse", DeathCurseEffect::new);
    public static final RegistryObject<MobEffect> ASCENDANCE = E.register("death_ascendance", AscendanceEffect::new);

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        E.register(bus);
    }
}
