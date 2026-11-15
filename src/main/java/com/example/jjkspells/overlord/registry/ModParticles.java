package com.example.jjkspells.overlord.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> P = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, JJKSpells.MODID);

    /** Crimson soul wisp. */
    public static final RegistryObject<SimpleParticleType> SOUL = P.register("soul", () -> new SimpleParticleType(true));
    /** Skeletal bone fragment / tiny skull. */
    public static final RegistryObject<SimpleParticleType> BONE = P.register("bone", () -> new SimpleParticleType(true));
    /** Floating purple rune glyph. */
    public static final RegistryObject<SimpleParticleType> RUNE = P.register("rune", () -> new SimpleParticleType(true));
    /** Ominous green-black smoke. */
    public static final RegistryObject<SimpleParticleType> SMOKE = P.register("smoke", () -> new SimpleParticleType(true));
    /** Golden holy mote. */
    public static final RegistryObject<SimpleParticleType> GOLD = P.register("gold", () -> new SimpleParticleType(true));
    /** Purple-black void mote. */
    public static final RegistryObject<SimpleParticleType> VOID = P.register("void", () -> new SimpleParticleType(true));

    private ModParticles() {
    }

    public static void register(IEventBus bus) {
        P.register(bus);
    }
}
