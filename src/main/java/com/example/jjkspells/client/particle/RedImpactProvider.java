package com.example.jjkspells.client.particle;

import com.example.jjkspells.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Builds the whole Reversal: Red impact on the client from a single network particle:
 * a flat repulsion ring, a camera-facing ring, a bright flash and an outward burst of sparks.
 * The explosion radius arrives as xd.
 */
@OnlyIn(Dist.CLIENT)
public class RedImpactProvider implements ParticleProvider<SimpleParticleType> {
    private final SpriteSet flashSprites;

    public RedImpactProvider(SpriteSet flashSprites) {
        this.flashSprites = flashSprites;
    }

    @Override
    public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
        double radius = Math.max(1.0, xd);

        // Shockwave rings (see RingParticle: xd = final radius, yd = 0 flat / 1 camera-facing)
        // 'true' = always render, even at long range or with the Particles option on Minimal
        level.addParticle(ModParticles.RED_RING.get(), true, x, y + 0.05, z, radius, 0, 0);
        level.addParticle(ModParticles.RED_RING.get(), true, x, y, z, radius * 0.85, 1, 0);

        // Outward burst of sparks
        int sparks = 24 + (int) (radius * 7);
        for (int i = 0; i < sparks; i++) {
            double dx = level.random.nextGaussian();
            double dy = level.random.nextGaussian() * 0.8;
            double dz = level.random.nextGaussian();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0E-4) {
                continue;
            }
            double speed = (0.25 + level.random.nextDouble() * 0.45) * (0.6 + radius * 0.12);
            level.addParticle(ModParticles.RED_SPARK.get(), true, x, y, z, dx / len * speed, dy / len * speed, dz / len * speed);
        }

        // The returned particle is the bright core flash.
        return GlowParticle.flash(level, x, y, z, flashSprites, (float) (radius * 0.9));
    }
}
