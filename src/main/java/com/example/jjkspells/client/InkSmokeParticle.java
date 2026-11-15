package com.example.jjkspells.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Black ink puff that lies FLAT on the ground (a horizontal quad instead of a camera-facing one).
 * Dozens of overlapping puffs build up the dark domain "floor".
 */
public class InkSmokeParticle extends TextureSheetParticle {
    private static final float MAX_ALPHA = 0.85f;
    private final float startSize;
    private final float spin;

    protected InkSmokeParticle(ClientLevel level, double x, double y, double z,
                               double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.pickSprite(sprites);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.friction = 0.96f;
        this.hasPhysics = false;
        this.lifetime = 80 + this.random.nextInt(50);
        this.startSize = 3.0f + this.random.nextFloat() * 3.0f; // half-width of the quad, in blocks
        this.quadSize = startSize;
        this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
        this.spin = (this.random.nextFloat() - 0.5f) * 0.02f;
        float shade = this.random.nextFloat() * 0.06f;
        this.setColor(shade, 0.0f, shade * 1.4f);
        this.alpha = 0f;
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += spin;

        float t = (float) this.age / (float) this.lifetime;
        float fade = t < 0.15f ? t / 0.15f : (t > 0.6f ? 1f - (t - 0.6f) / 0.4f : 1f);
        this.alpha = MAX_ALPHA * Mth.clamp(fade, 0f, 1f);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        float t = ((float) this.age + partialTicks) / (float) this.lifetime;
        return this.startSize * (0.8f + 0.4f * Mth.clamp(t, 0f, 1f)); // slowly spreads across the floor
    }

    /** Draws the texture on the horizontal (XZ) plane, visible from above and below. */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        var cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());

        float size = this.getQuadSize(partialTicks);
        float r = Mth.lerp(partialTicks, this.oRoll, this.roll);
        float cos = Mth.cos(r) * size;
        float sin = Mth.sin(r) * size;

        // corners (-1,-1) (-1,1) (1,1) (1,-1) rotated around the centre by the roll angle
        float[] px = {-cos + sin, -cos - sin, cos - sin, cos + sin};
        float[] pz = {-sin - cos, -sin + cos, sin + cos, sin - cos};

        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        float[] us = {u1, u1, u0, u0};
        float[] vs = {v1, v0, v0, v1};
        int light = this.getLightColor(partialTicks);

        for (int i = 0; i < 4; i++) { // top face
            buffer.vertex(x + px[i], y, z + pz[i]).uv(us[i], vs[i])
                    .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        }
        for (int i = 3; i >= 0; i--) { // same quad reversed, so it also shows from underneath
            buffer.vertex(x + px[i], y, z + pz[i]).uv(us[i], vs[i])
                    .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        }
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
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xd, double yd, double zd) {
            return new InkSmokeParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
