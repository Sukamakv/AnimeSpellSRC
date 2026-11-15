package com.example.jjkspells.garou.registry;

import com.example.jjkspells.JJKSpells;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> P = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, JJKSpells.MODID);

    /** Twinkling four point star (6 frame PNG animation over its lifetime). */
    public static final RegistryObject<SimpleParticleType> STAR = reg("star");
    /** Soft round glow. */
    public static final RegistryObject<SimpleParticleType> GLOW = reg("glow");
    /** Short bright streak. */
    public static final RegistryObject<SimpleParticleType> SPARK = reg("spark");
    /** Expanding starburst explosion (12 frame PNG animation). */
    public static final RegistryObject<SimpleParticleType> BURST = reg("burst");
    /** Expanding shock ring (6 frame PNG animation). */
    public static final RegistryObject<SimpleParticleType> RING = reg("ring");
    /** Dark cosmic dust that the sphere swallows (normal blending, so it really looks dark). */
    public static final RegistryObject<SimpleParticleType> DUST = reg("dust");

    private ModParticles() {
    }

    private static RegistryObject<SimpleParticleType> reg(String n) {
        return P.register("gar_" + n, () -> new SimpleParticleType(true));
    }

    public static void register(IEventBus bus) {
        P.register(bus);
    }
}
