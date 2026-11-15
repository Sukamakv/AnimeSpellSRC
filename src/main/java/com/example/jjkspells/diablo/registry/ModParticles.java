package com.example.jjkspells.diablo.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> P = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, JJKSpells.MODID);

    public static final RegistryObject<SimpleParticleType> FLAME = reg("flame");
    public static final RegistryObject<SimpleParticleType> MOTE = reg("mote");
    public static final RegistryObject<SimpleParticleType> EMBER = reg("ember");
    public static final RegistryObject<SimpleParticleType> RUNE = reg("rune");
    public static final RegistryObject<SimpleParticleType> CRACK = reg("crack");
    public static final RegistryObject<SimpleParticleType> ASH = reg("ash");
    public static final RegistryObject<SimpleParticleType> SOUL = reg("soul");
    public static final RegistryObject<SimpleParticleType> FLASH = reg("flash");
    public static final RegistryObject<SimpleParticleType> RING = reg("ring");
    public static final RegistryObject<SimpleParticleType> GLOW = reg("glow");
    public static final RegistryObject<SimpleParticleType> STREAK = reg("streak");

    private ModParticles() {
    }

    private static RegistryObject<SimpleParticleType> reg(String n) {
        return P.register("dia_" + n, () -> new SimpleParticleType(true));
    }

    public static void register(IEventBus bus) {
        P.register(bus);
    }
}
