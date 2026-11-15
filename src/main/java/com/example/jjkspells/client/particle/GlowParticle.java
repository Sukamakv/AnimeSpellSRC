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
 * A fullbright, fading, shrinking glow sprite. Used for blue/red sparks and the impact flash.
 * Parameters passed to addParticle are the particle's velocity.
 */
@OnlyIn(Dist.CLIENT)
public class GlowParticle extends TextureSheetParticle {

    public GlowParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                        SpriteSet sprites, int lifetime, float size, float friction) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = lifetime;
        this.quadSize = size;
        this.friction = friction;
        this.gravity = 0f;
        this.hasPhysics = false;
        float shade = 0.85f + this.random.nextFloat() * 0.15f;
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
        this.move(this.xd, this.yd, this.zd);
        this.xd *= this.friction;
        this.yd *= this.friction;
        this.zd *= this.friction;
        float t = (float) this.age / (float) this.lifetime;
        this.alpha = 1.0f - t * t;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = ((float) this.age + partialTick) / (float) this.lifetime;
        return this.quadSize * Mth.clamp(1.0f - t * t * 0.85f, 0.05f, 1.0f);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880; // always full brightness
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Small spark: lifetime and size randomised a little. */
    @OnlyIn(Dist.CLIENT)
    public static class SparkProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SparkProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            int life = 10 + level.random.nextInt(8);
            float size = 0.10f + level.random.nextFloat() * 0.12f;
            return new GlowParticle(level, x, y, z, xd, yd, zd, sprites, life, size, 0.93f);
        }
    }

    /** Creates a big short-lived flash. Used by the impact effect, not registered as its own particle type. */
    public static GlowParticle flash(ClientLevel level, double x, double y, double z, SpriteSet sprites, float size) {
        return new GlowParticle(level, x, y, z, 0, 0, 0, sprites, 6, size, 1.0f);
    }
}
