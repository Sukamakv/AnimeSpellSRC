package com.example.jjkspells.particle;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleType;

/** Particle type for {@link MoonRibbonOptions}. The renderer is client/particle/MoonRibbonParticle. */
public class MoonRibbonType extends ParticleType<MoonRibbonOptions> {
    public MoonRibbonType() {
        super(true, MoonRibbonOptions.DESERIALIZER);
    }

    @Override
    public Codec<MoonRibbonOptions> codec() {
        return MoonRibbonOptions.CODEC;
    }
}
