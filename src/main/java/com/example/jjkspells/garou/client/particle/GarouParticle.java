package com.example.jjkspells.garou.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** One configurable full-bright particle used for every Nuclear Fission effect (see {@link Style}). */
public class GarouParticle extends TextureSheetParticle {
    /** Glowing particles: additive blending on the particle atlas. */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder b, TextureManager tm) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator t) {
            t.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "jjk_spells:gau_additive";
        }
    };

    public static final class Style {
        float size = 0.2f, sizeVar = 0f;
        int life = 20, lifeVar = 0;
        float gravity = 0f, friction = 0.95f;
        /** relative size change over the lifetime (-0.5 shrinks to half, +1 doubles) */
        float grow = 0f;
        float fadeIn = 0.1f, fadeOut = 0.5f;
        boolean additive = true, animated = false;
        float spin = 0f, maxAlpha = 1f;
        float[] from = {1f, 1f, 1f}, to = {1f, 1f, 1f};

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

        public Style gravity(float g, float f) {
            gravity = g;
            friction = f;
            return this;
        }

        public Style grow(float g) {
            grow = g;
            return this;
        }

        public Style fade(float in, float out) {
            fadeIn = in;
            fadeOut = out;
            return this;
        }

        public Style translucent() {
            additive = false;
            return this;
        }

        /** The sprite follows the age (frames of an animation). */
        public Style animated() {
            animated = true;
            return this;
        }

        public Style spin(float s) {
            spin = s;
            return this;
        }

        public Style alpha(float a) {
            maxAlpha = a;
            return this;
        }

        /** Colour tint at birth and at death (a fireball cools into smoke). */
        public Style tint(float[] a, float[] b) {
            from = a;
            to = b;
            return this;
        }
    }

    private final Style style;
    private final SpriteSet sprites;
    private final float baseSize;
    private final float spinDir;

    protected GarouParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Style st) {
        super(level, x, y, z);
        this.style = st;
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = st.life + (st.lifeVar > 0 ? this.random.nextInt(st.lifeVar + 1) : 0);
        this.baseSize = st.size + (st.sizeVar > 0 ? this.random.nextFloat() * st.sizeVar : 0f);
        this.quadSize = baseSize;
        this.gravity = st.gravity;
        this.friction = st.friction;
        this.hasPhysics = false;
        this.spinDir = this.random.nextBoolean() ? 1f : -1f;
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
        this.alpha = 0f;
        this.rCol = st.from[0];
        this.gCol = st.from[1];
        this.bCol = st.from[2];
        if (st.animated) {
            this.setSpriteFromAge(sprites);
        } else {
            this.pickSprite(sprites);
        }
    }

    @Override
    public void tick() {
        super.tick();
        float life = Mth.clamp(this.age / (float) this.lifetime, 0f, 1f);
        if (style.animated) {
            this.setSpriteFromAge(sprites);
        }
        if (style.spin != 0f) {
            this.oRoll = this.roll;
            this.roll += style.spin * spinDir;
        }
        this.quadSize = baseSize * (1f + style.grow * life);
        this.rCol = Mth.lerp(life, style.from[0], style.to[0]);
        this.gCol = Mth.lerp(life, style.from[1], style.to[1]);
        this.bCol = Mth.lerp(life, style.from[2], style.to[2]);
        float in = style.fadeIn <= 0f ? 1f : Mth.clamp(life / style.fadeIn, 0f, 1f);
        float out = style.fadeOut <= 0f ? 1f : Mth.clamp((1f - life) / style.fadeOut, 0f, 1f);
        this.alpha = in * out * style.maxAlpha;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return style.additive ? ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
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
            return new GarouParticle(l, x, y, z, xd, yd, zd, sprites, style);
        }
    }

    public static Style style() {
        return new Style();
    }
}
