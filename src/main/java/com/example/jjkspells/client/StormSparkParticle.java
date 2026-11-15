package com.example.jjkspells.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Short, bright white-blue flicker. */
public class StormSparkParticle extends TextureSheetParticle {
    protected StormSparkParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 5 + this.random.nextInt(8);
        this.quadSize = 0.18f + this.random.nextFloat() * 0.35f;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.friction = 0.85f;
        this.rCol = 0.85f;
        this.gCol = 0.92f;
        this.bCol = 1.0f;
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float life = this.age / (float) this.lifetime;
        this.alpha = (this.random.nextFloat() < 0.25f ? 0.35f : 1f) * (1f - life);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double xd, double yd, double zd) {
            return new StormSparkParticle(l, x, y, z, xd, yd, zd, sprites);
        }
    }
}
