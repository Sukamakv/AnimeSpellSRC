package com.example.jjkspells.acheron.registry;

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
    public static final RegistryObject<SimpleParticleType> SPARK = reg("spark");
    public static final RegistryObject<SimpleParticleType> ARC_V = reg("arc_v");
    public static final RegistryObject<SimpleParticleType> ARC_R = reg("arc_r");
    public static final RegistryObject<SimpleParticleType> MIST = reg("mist");
    public static final RegistryObject<SimpleParticleType> SLASH_V = reg("slash_v");
    public static final RegistryObject<SimpleParticleType> SLASH_R = reg("slash_r");
    public static final RegistryObject<SimpleParticleType> CUT = reg("cut");
    public static final RegistryObject<SimpleParticleType> SHARD = reg("shard");
    public static final RegistryObject<SimpleParticleType> AFTER_V = reg("after_v");
    public static final RegistryObject<SimpleParticleType> AFTER_R = reg("after_r");
    public static final RegistryObject<SimpleParticleType> MARK_V = reg("mark_v");
    public static final RegistryObject<SimpleParticleType> MARK_R = reg("mark_r");
    public static final RegistryObject<SimpleParticleType> MARK_F = reg("mark_f");
    public static final RegistryObject<SimpleParticleType> FLASH_V = reg("flash_v");
    public static final RegistryObject<SimpleParticleType> FLASH_R = reg("flash_r");
    public static final RegistryObject<SimpleParticleType> EMBER = reg("ember");

    private ModParticles() {
    }

    private static RegistryObject<SimpleParticleType> reg(String n) {
        return P.register("ach_" + n, () -> new SimpleParticleType(true));
    }

    public static void register(IEventBus bus) {
        P.register(bus);
    }
}
