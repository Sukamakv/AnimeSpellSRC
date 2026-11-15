package com.example.jjkspells.malenia.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> P = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, JJKSpells.MODID);

    public static final RegistryObject<SimpleParticleType> PETAL = reg("petal");
    public static final RegistryObject<SimpleParticleType> BUTTERFLY = reg("butterfly");
    public static final RegistryObject<SimpleParticleType> GOLD = reg("gold");
    public static final RegistryObject<SimpleParticleType> SILVER = reg("silver");
    public static final RegistryObject<SimpleParticleType> SPORE = reg("spore");
    public static final RegistryObject<SimpleParticleType> ROT = reg("rot");
    public static final RegistryObject<SimpleParticleType> FLASH = reg("flash");
    public static final RegistryObject<SimpleParticleType> RING = reg("ring");
    public static final RegistryObject<SimpleParticleType> GLOW = reg("glow");
    public static final RegistryObject<SimpleParticleType> STREAK = reg("streak");

    private ModParticles() {
    }

    private static RegistryObject<SimpleParticleType> reg(String n) {
        return P.register("mel_" + n, () -> new SimpleParticleType(true));
    }

    public static void register(IEventBus bus) {
        P.register(bus);
    }
}
