package com.example.jjkspells.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Big dark smoky puff that is flung around the tornado base. */
public class StormCloudParticle extends TextureSheetParticle {
    private final float baseSize;
    private final float spin;

    protected StormCloudParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 25 + this.random.nextInt(25);
        this.baseSize = 0.9f + this.random.nextFloat() * 1.2f;
        this.quadSize = baseSize;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.friction = 0.94f;
        float shade = 0.05f + this.random.nextFloat() * 0.10f;
        this.rCol = shade;
        this.gCol = shade * 1.1f;
        this.bCol = shade * 1.6f;
        this.spin = (this.random.nextFloat() - 0.5f) * 0.12f;
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        this.roll += spin;
        super.tick();
        float life = this.age / (float) this.lifetime;
        this.alpha = Mth.clamp(Math.min(life * 6f, (1f - life) * 1.6f), 0f, 1f) * 0.65f;
        this.quadSize = baseSize * (0.7f + 0.9f * life);
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
            return new StormCloudParticle(l, x, y, z, xd, yd, zd, sprites);
        }
    }
}
