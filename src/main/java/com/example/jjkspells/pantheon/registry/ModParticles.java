package com.example.jjkspells.pantheon.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> P = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, JJKSpells.MODID);

    public static final RegistryObject<SimpleParticleType> SPARK = reg("spark");
    public static final RegistryObject<SimpleParticleType> FLAME = reg("flame");
    public static final RegistryObject<SimpleParticleType> EMBER = reg("ember");
    public static final RegistryObject<SimpleParticleType> STREAK = reg("streak");
    public static final RegistryObject<SimpleParticleType> STAR = reg("star");
    public static final RegistryObject<SimpleParticleType> FLASH = reg("flash");
    public static final RegistryObject<SimpleParticleType> SHARD = reg("shard");
    public static final RegistryObject<SimpleParticleType> DUST = reg("dust");
    public static final RegistryObject<SimpleParticleType> RING = reg("ring");
    public static final RegistryObject<SimpleParticleType> METAL = reg("metal");
    public static final RegistryObject<SimpleParticleType> GLYPH = reg("glyph");

    private ModParticles() {
    }

    private static RegistryObject<SimpleParticleType> reg(String n) {
        return P.register("pan_" + n, () -> new SimpleParticleType(true));
    }

    public static void register(IEventBus bus) {
        P.register(bus);
    }
}
