package com.example.jjkspells.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Ordinary billowing smoke: a camera-facing, lit-looking puff that rises, swells and thins out.
 * (The flat ink smoke is reserved for Malevolent Shrine's floor.)
 */
public class BillowSmokeParticle extends TextureSheetParticle {
    private final float startSize;
    private final float maxAlpha;

    protected BillowSmokeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.pickSprite(sprites);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.friction = 0.93f;
        this.hasPhysics = false;
        this.lifetime = 34 + this.random.nextInt(26);
        this.startSize = 0.28f + this.random.nextFloat() * 0.22f;
        this.quadSize = startSize;
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
        float shade = 0.07f + this.random.nextFloat() * 0.12f;
        this.setColor(shade, shade * 0.97f, shade * 1.05f);
        this.maxAlpha = 0.5f + this.random.nextFloat() * 0.25f;
        this.alpha = 0f;
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        super.tick();
        this.roll += (this.xd > 0 ? 0.02f : -0.02f);
        this.yd += 0.0016; // warm smoke drifts up
        float t = (float) this.age / (float) this.lifetime;
        float fade = t < 0.12f ? t / 0.12f : 1f - Mth.clamp((t - 0.12f) / 0.88f, 0f, 1f);
        this.alpha = maxAlpha * fade;
    }

    @Override
    public float getQuadSize(float partialTicks) {
        float t = ((float) this.age + partialTicks) / (float) this.lifetime;
        return startSize * (1f + 1.9f * (1f - (1f - Mth.clamp(t, 0f, 1f)) * (1f - Mth.clamp(t, 0f, 1f))));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return SoftRender.PARTICLE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new BillowSmokeParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
