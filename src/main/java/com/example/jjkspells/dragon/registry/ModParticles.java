package com.example.jjkspells.dragon.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> P = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, JJKSpells.MODID);

    /** Golden sparkle mote. */
    public static final RegistryObject<SimpleParticleType> GOLD = P.register("dragon_gold", () -> new SimpleParticleType(true));
    /** Floating golden rune. */
    public static final RegistryObject<SimpleParticleType> RUNE = P.register("dragon_rune", () -> new SimpleParticleType(true));

    private ModParticles() {
    }

    public static void register(IEventBus bus) {
        P.register(bus);
    }
}
