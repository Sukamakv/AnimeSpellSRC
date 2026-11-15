package com.example.jjkspells.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * One layer of the golden clock circle lying on the ground (face, runes, gears, outer arcs, clock hands).
 * Parameters: xd = radius in blocks, yd = lifetime in ticks, zd = rotation per tick (radians, positive = clockwise).
 * It unfolds over its first 28 ticks, turns, and fades out over its last 40 ticks. When time stops it stops turning,
 * like everything else; the clock hands (stopAge &gt; 0) instead stop at a fixed age so they land exactly on twelve.
 */
public class ClockCircleParticle extends TextureSheetParticle {
    private static final int GROW_TICKS = 28;
    private static final int FADE_TICKS = 40;
    private final float radius;
    private final float spin;
    private final int stopAge;
    private float angle;
    private float oAngle;

    protected ClockCircleParticle(ClientLevel level, double x, double y, double z, double radius, double life, double spin,
                                  int stopAge, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.radius = (float) Math.max(1.0, radius);
        this.lifetime = (int) Math.max(40, life);
        this.spin = (float) spin;
        this.stopAge = stopAge;
        this.hasPhysics = false;
        this.xd = this.yd = this.zd = 0;
        this.alpha = 0f;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oAngle = this.angle;
        boolean frozen = this.stopAge > 0 ? this.age >= this.stopAge : TimeStopClient.isStopped();
        if (!frozen) this.angle += this.spin;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float a = Math.min(1f, this.age / 14f);
        int left = this.lifetime - this.age;
        if (left < FADE_TICKS) a *= left / (float) FADE_TICKS;
        this.alpha = Mth.clamp(a, 0f, 1f);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float cx = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float cy = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float cz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());

        float g = Mth.clamp((this.age + partialTicks) / GROW_TICKS, 0f, 1f);
        float ease = 1f - (1f - g) * (1f - g) * (1f - g);
        float size = this.radius * (0.1f + 0.9f * ease);
        float ang = Mth.lerp(partialTicks, this.oAngle, this.angle) + (1f - ease) * (this.spin >= 0 ? -2.5f : 2.5f);
        float cos = Mth.cos(ang) * size, sin = Mth.sin(ang) * size;

        float[] px = {-cos + sin, -cos - sin, cos - sin, cos + sin};
        float[] pz = {-sin - cos, -sin + cos, sin + cos, sin - cos};
        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        float[] us = {u1, u1, u0, u0};
        float[] vs = {v1, v0, v0, v1};
        int light = 15728880;
        for (int i = 0; i < 4; i++) {
            buffer.vertex(cx + px[i], cy, cz + pz[i]).uv(us[i], vs[i]).color(1f, 1f, 1f, this.alpha).uv2(light).endVertex();
        }
        for (int i = 3; i >= 0; i--) {
            buffer.vertex(cx + px[i], cy, cz + pz[i]).uv(us[i], vs[i]).color(1f, 1f, 1f, this.alpha).uv2(light).endVertex();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return FugaRenderTypes.ADDITIVE;
    }

    @Override
    public boolean shouldCull() {
        return false;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final int stopAge;

        public Provider(SpriteSet sprites, int stopAge) {
            this.sprites = sprites;
            this.stopAge = stopAge;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new ClockCircleParticle(level, x, y, z, xd, yd, zd, this.stopAge, this.sprites);
        }
    }
}
