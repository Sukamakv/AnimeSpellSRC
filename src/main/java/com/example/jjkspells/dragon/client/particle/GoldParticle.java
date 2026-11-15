package com.example.jjkspells.dragon.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Full-bright golden mote; rune variant lives longer, is bigger and drifts. */
public class GoldParticle extends TextureSheetParticle {
    private final float baseSize;

    protected GoldParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, boolean rune) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = rune ? 40 + this.random.nextInt(40) : 20 + this.random.nextInt(20);
        this.baseSize = rune ? 0.35f + this.random.nextFloat() * 0.25f : 0.12f + this.random.nextFloat() * 0.2f;
        this.quadSize = baseSize;
        this.gravity = 0f;
        this.friction = rune ? 0.96f : 0.94f;
        this.hasPhysics = false;
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float life = this.age / (float) this.lifetime;
        this.alpha = Mth.clamp(life * 8f, 0f, 1f) * (1f - life * life);
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
        private final boolean rune;

        public Provider(SpriteSet sprites, boolean rune) {
            this.sprites = sprites;
            this.rune = rune;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double xd, double yd, double zd) {
            return new GoldParticle(l, x, y, z, xd, yd, zd, sprites, rune);
        }
    }
}
