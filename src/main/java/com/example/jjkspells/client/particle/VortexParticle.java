package com.example.jjkspells.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A particle that spirals inward around a vertical axis through a centre point.
 * It receives the centre as the vector from its own spawn position to the centre (xd,yd,zd).
 */
@OnlyIn(Dist.CLIENT)
public class VortexParticle extends TextureSheetParticle {
    private final double cx, cy, cz;

    protected VortexParticle(ClientLevel level, double x, double y, double z, double toCx, double toCy, double toCz, SpriteSet sprites) {
        super(level, x, y, z);
        this.cx = x + toCx;
        this.cy = y + toCy;
        this.cz = z + toCz;
        this.lifetime = 80; // safety cap; normally it ends when it reaches the core
        this.quadSize = 0.10f + this.random.nextFloat() * 0.16f;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.alpha = 0f;
        float shade = 0.8f + this.random.nextFloat() * 0.2f;
        this.setColor(shade, shade, shade);
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        double rx = this.x - this.cx;
        double rz = this.z - this.cz;
        double ry = this.y - this.cy;
        double radius = Math.sqrt(rx * rx + rz * rz);
        if (radius < 0.15) {
            this.remove();
            return;
        }

        // Rotate faster as we get closer, and spiral inward: geometric far away, steady near the core.
        double angle = Math.atan2(rz, rx) + 0.10 + 0.55 / (radius + 1.0);
        radius -= Math.max(0.22, radius * 0.07);
        ry *= 0.96;
        this.setPos(this.cx + Math.cos(angle) * radius, this.cy + ry, this.cz + Math.sin(angle) * radius);

        // Fade in at the start, fade out as we fall into the core.
        float fadeIn = Mth.clamp(this.age / 5.0f, 0f, 1f);
        float fadeOut = Mth.clamp((float) (radius / 1.1), 0f, 1f);
        this.alpha = fadeIn * fadeOut;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public boolean shouldCull() {
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new VortexParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
