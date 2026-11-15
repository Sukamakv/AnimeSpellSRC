package com.example.jjkspells.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Soft white puff of wind that fades quickly. */
public class WindWispParticle extends TextureSheetParticle {
    private final float baseSize;

    protected WindWispParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 8 + this.random.nextInt(8);
        this.baseSize = 0.35f + this.random.nextFloat() * 0.5f;
        this.quadSize = baseSize;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.friction = 0.9f;
        this.rCol = 0.92f;
        this.gCol = 0.97f;
        this.bCol = 1.0f;
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float life = this.age / (float) this.lifetime;
        this.alpha = Mth.clamp((1f - life) * 0.7f, 0f, 1f);
        this.quadSize = baseSize * (1f + life * 1.4f);
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
            return new WindWispParticle(l, x, y, z, xd, yd, zd, sprites);
        }
    }
}
