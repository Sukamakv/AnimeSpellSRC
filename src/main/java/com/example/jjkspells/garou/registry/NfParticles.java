package com.example.jjkspells.garou.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class NfParticles {
    private static final DeferredRegister<ParticleType<?>> P = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, JJKSpells.MODID);

    /** Animated fireball puff (8 frames, see textures/particle/gau_fire_*.png). */
    public static final RegistryObject<SimpleParticleType> FIRE = reg("fire");
    public static final RegistryObject<SimpleParticleType> SMOKE = reg("smoke");
    public static final RegistryObject<SimpleParticleType> SPARK = reg("spark");
    public static final RegistryObject<SimpleParticleType> EMBER = reg("ember");
    public static final RegistryObject<SimpleParticleType> DEBRIS = reg("debris");
    public static final RegistryObject<SimpleParticleType> GLOW = reg("glow");

    private NfParticles() {
    }

    private static RegistryObject<SimpleParticleType> reg(String n) {
        return P.register("gau_" + n, () -> new SimpleParticleType(true));
    }

    public static void register(IEventBus bus) {
        P.register(bus);
    }
}
