package com.example.jjkspells.overlord.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** One configurable particle class for all six mod particles (soul, bone, rune, smoke, gold, void). */
public class OverlordParticle extends TextureSheetParticle {
    public record Cfg(int lifeMin, int lifeMax, float sizeMin, float sizeMax, float sizeEnd, float gravity, float friction,
                      float spin, boolean fullBright, float fadeIn, float riseBias) {
    }

    private final Cfg cfg;
    private final float baseSize;

    protected OverlordParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Cfg cfg) {
        super(level, x, y, z);
        this.cfg = cfg;
        this.xd = xd;
        this.yd = yd + cfg.riseBias;
        this.zd = zd;
        this.lifetime = cfg.lifeMin + this.random.nextInt(Math.max(1, cfg.lifeMax - cfg.lifeMin + 1));
        this.baseSize = cfg.sizeMin + this.random.nextFloat() * (cfg.sizeMax - cfg.sizeMin);
        this.quadSize = baseSize;
        this.gravity = cfg.gravity;
        this.friction = cfg.friction;
        this.hasPhysics = false;
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        this.roll += cfg.spin;
        super.tick();
        float life = this.age / (float) this.lifetime;
        this.quadSize = baseSize * Mth.lerp(life, 1f, cfg.sizeEnd);
        float fadeIn = cfg.fadeIn <= 0 ? 1f : Mth.clamp(life / cfg.fadeIn, 0f, 1f);
        this.alpha = fadeIn * (1f - life * life);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return cfg.fullBright ? 15728880 : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return com.example.jjkspells.client.SoftRender.PARTICLE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Cfg cfg;

        public Provider(SpriteSet sprites, Cfg cfg) {
            this.sprites = sprites;
            this.cfg = cfg;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double xd, double yd, double zd) {
            return new OverlordParticle(l, x, y, z, xd, yd, zd, sprites, cfg);
        }
    }

    // lifeMin, lifeMax, sizeMin, sizeMax, sizeEnd, gravity, friction, spin, fullBright, fadeIn, riseBias
    public static final Cfg SOUL = new Cfg(24, 44, 0.28f, 0.5f, 0.5f, -0.01f, 0.94f, 0.03f, true, 0.2f, 0.01f);
    public static final Cfg BONE = new Cfg(18, 34, 0.12f, 0.22f, 0.8f, 0.55f, 0.92f, 0.25f, false, 0.0f, 0.0f);
    public static final Cfg RUNE = new Cfg(40, 80, 0.30f, 0.55f, 0.9f, -0.002f, 0.96f, 0.01f, true, 0.3f, 0.01f);
    public static final Cfg SMOKE = new Cfg(40, 74, 0.7f, 1.2f, 2.0f, -0.003f, 0.95f, 0.015f, false, 0.12f, 0.0f);
    public static final Cfg GOLD = new Cfg(18, 40, 0.14f, 0.3f, 0.2f, 0.02f, 0.93f, 0.05f, true, 0.1f, 0.0f);
    public static final Cfg VOID = new Cfg(14, 30, 0.2f, 0.4f, 0.1f, 0.0f, 0.88f, 0.08f, true, 0.1f, 0.0f);
}
