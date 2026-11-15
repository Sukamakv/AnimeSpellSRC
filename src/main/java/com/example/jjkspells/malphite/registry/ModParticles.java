package com.example.jjkspells.malphite.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> P = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, JJKSpells.MODID);

    public static final RegistryObject<SimpleParticleType> DUST = reg("dust");
    public static final RegistryObject<SimpleParticleType> GRAVEL = reg("gravel");
    public static final RegistryObject<SimpleParticleType> ROCK = reg("rock");
    public static final RegistryObject<SimpleParticleType> SMOKE = reg("smoke");
    public static final RegistryObject<SimpleParticleType> SHARD = reg("shard");
    public static final RegistryObject<SimpleParticleType> SPARK = reg("spark");
    public static final RegistryObject<SimpleParticleType> EMBER = reg("ember");
    public static final RegistryObject<SimpleParticleType> FLASH = reg("flash");
    public static final RegistryObject<SimpleParticleType> RING = reg("ring");
    public static final RegistryObject<SimpleParticleType> GLOW = reg("glow");

    private ModParticles() {
    }

    private static RegistryObject<SimpleParticleType> reg(String n) {
        return P.register("mal_" + n, () -> new SimpleParticleType(true));
    }

    public static void register(IEventBus bus) {
        P.register(bus);
    }
}
