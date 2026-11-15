package com.example.jjkspells.garou.client.particle;

import com.example.jjkspells.client.SoftRender;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Full-bright particle whose sprite is a PNG animation: the frame follows the particle's age, so a 12 frame burst plays
 * once from start to end over its lifetime. Additive (glowing) unless {@link Style#translucent()} is used.
 */
public class GarParticle extends TextureSheetParticle {
    /** Glowing particles: additive blending on the particle atlas. */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public void begin(com.mojang.blaze3d.vertex.BufferBuilder b, net.minecraft.client.renderer.texture.TextureManager tm) {
            com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
            com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getParticleShader);
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            b.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(com.mojang.blaze3d.vertex.Tesselator t) {
            t.end();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "garou:additive";
        }
    };

    public static final class Style {
        float size = 0.2f, sizeVar = 0f, grow = 0f, gravity = 0f, friction = 0.95f, spin = 0f, maxAlpha = 1f, fadeIn = 0.1f, fadeOut = 0.5f;
        int life = 20, lifeVar = 0;
        boolean additive = true, randomRoll = true, loop = false;

        public Style size(float s, float v) {
            size = s;
            sizeVar = v;
            return this;
        }

        public Style life(int l, int v) {
            life = l;
            lifeVar = v;
            return this;
        }

        public Style grow(float g) {
            grow = g;
            return this;
        }

        public Style gravity(float g, float f) {
            gravity = g;
            friction = f;
            return this;
        }

        public Style spin(float s) {
            spin = s;
            return this;
        }

        public Style fade(float in, float out) {
            fadeIn = in;
            fadeOut = out;
            return this;
        }

        public Style alpha(float a) {
            maxAlpha = a;
            return this;
        }

        public Style translucent() {
            additive = false;
            return this;
        }

        public Style fixedRoll() {
            randomRoll = false;
            return this;
        }

        /** Loop the animation twice instead of playing it once (twinkling stars). */
        public Style loop() {
            loop = true;
            return this;
        }
    }

    public static Style style() {
        return new Style();
    }

    private final Style st;
    private final SpriteSet sprites;
    private final float baseSize, spinDir;

    protected GarParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Style st) {
        super(level, x, y, z);
        this.st = st;
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = st.life + (st.lifeVar > 0 ? random.nextInt(st.lifeVar + 1) : 0);
        this.baseSize = st.size + (st.sizeVar > 0 ? random.nextFloat() * st.sizeVar : 0f);
        this.quadSize = baseSize;
        this.gravity = st.gravity;
        this.friction = st.friction;
        this.hasPhysics = false;
        this.spinDir = random.nextBoolean() ? 1f : -1f;
        this.roll = this.oRoll = st.randomRoll ? random.nextFloat() * Mth.TWO_PI : 0f;
        this.alpha = 0f;
        setSprite(sprites.get(0, Math.max(1, lifetime)));
    }

    @Override
    public void tick() {
        super.tick();
        float p = Mth.clamp(age / (float) lifetime, 0f, 1f);
        float q = st.loop ? (p * 2f) % 1f : p;
        setSprite(sprites.get(Math.min((int) (q * 1000f), 999), 1000));
        if (st.spin != 0f) {
            oRoll = roll;
            roll += st.spin * spinDir;
        }
        quadSize = baseSize * (1f + st.grow * p);
        float in = st.fadeIn <= 0f ? 1f : Mth.clamp(p / st.fadeIn, 0f, 1f);
        float out = st.fadeOut <= 0f ? 1f : Mth.clamp((1f - p) / st.fadeOut, 0f, 1f);
        alpha = in * out * st.maxAlpha;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return st.additive ? ADDITIVE : SoftRender.PARTICLE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Style style;

        public Provider(SpriteSet sprites, Style style) {
            this.sprites = sprites;
            this.style = style;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double xd, double yd, double zd) {
            return new GarParticle(l, x, y, z, xd, yd, zd, sprites, style);
        }
    }
}
