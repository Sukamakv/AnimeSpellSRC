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
import org.joml.Vector3f;

/**
 * One of the eyes that open in the sky above the caster of The Ruler of Death's Crimson Wrath.
 * Parameters: xd = width in blocks, yd = lifetime in ticks. The eye opens through its six sprites during its first
 * 18 ticks, then glares down (a slow pulse) until it fades out over its last 20 ticks.
 */
public class CrimsonEyeParticle extends TextureSheetParticle {
    private static final int OPEN_TICKS = 18;
    private final SpriteSet sprites;
    private final float width;
    private final float tilt;
    private final float phase;

    protected CrimsonEyeParticle(ClientLevel level, double x, double y, double z, double width, double life, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.width = (float) Math.max(1.0, width);
        this.lifetime = (int) Math.max(40, life);
        this.hasPhysics = false;
        this.xd = this.yd = this.zd = 0;
        this.tilt = (this.random.nextFloat() - 0.5f) * 0.36f;
        this.phase = this.random.nextFloat() * Mth.TWO_PI;
        this.alpha = 0f;
        this.setSprite(sprites.get(0, OPEN_TICKS));
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        this.setSprite(this.sprites.get(Math.min(this.age, OPEN_TICKS), OPEN_TICKS));
        float a = Math.min(1f, this.age / 6f);
        int left = this.lifetime - this.age;
        if (left < 20) a *= left / 20f;
        a *= 0.88f + 0.12f * Mth.sin(this.age * 0.12f + this.phase);
        this.alpha = Mth.clamp(a, 0f, 1f);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float cx = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float cy = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float cz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());

        float open = Math.min(1f, (this.age + partialTicks) / OPEN_TICKS);
        float breathe = 1f + 0.025f * Mth.sin((this.age + partialTicks) * 0.09f + this.phase);
        float w = this.width * 0.5f * (0.9f + 0.1f * open) * breathe;
        float h = w * 0.5f; // the texture is 2:1

        Vector3f right = new Vector3f(1, 0, 0).rotate(camera.rotation());
        Vector3f up = new Vector3f(0, 1, 0).rotate(camera.rotation());
        // a slight roll so the eyes do not all sit perfectly level
        float c = Mth.cos(this.tilt), s = Mth.sin(this.tilt);
        Vector3f r2 = new Vector3f(right).mul(c).add(new Vector3f(up).mul(s));
        Vector3f u2 = new Vector3f(up).mul(c).sub(new Vector3f(right).mul(s));

        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        int light = 15728880;
        vertex(buffer, cx - r2.x * w - u2.x * h, cy - r2.y * w - u2.y * h, cz - r2.z * w - u2.z * h, u1, v1, light);
        vertex(buffer, cx - r2.x * w + u2.x * h, cy - r2.y * w + u2.y * h, cz - r2.z * w + u2.z * h, u1, v0, light);
        vertex(buffer, cx + r2.x * w + u2.x * h, cy + r2.y * w + u2.y * h, cz + r2.z * w + u2.z * h, u0, v0, light);
        vertex(buffer, cx + r2.x * w - u2.x * h, cy + r2.y * w - u2.y * h, cz + r2.z * w - u2.z * h, u0, v1, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(x, y, z).uv(u, v).color(1f, 1f, 1f, this.alpha).uv2(light).endVertex();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public boolean shouldCull() {
        return false;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new CrimsonEyeParticle(level, x, y, z, xd, yd, this.sprites);
        }
    }
}
