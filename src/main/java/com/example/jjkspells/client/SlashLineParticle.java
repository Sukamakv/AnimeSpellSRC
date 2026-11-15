package com.example.jjkspells.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One straight slash. The server sends the cut's vector in the "velocity" fields; the particle
 * doesn't move, it just draws a bright thin quad from its position to position + vector, turned
 * to face the camera. The texture tapers to a point at both ends like a blade mark.
 */
public class SlashLineParticle extends TextureSheetParticle {
    private final float dx, dy, dz;
    private final float width;

    protected SlashLineParticle(ClientLevel level, double x, double y, double z,
                                double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.pickSprite(sprites);
        this.dx = (float) dx;
        this.dy = (float) dy;
        this.dz = (float) dz;
        this.xd = this.yd = this.zd = 0;
        this.hasPhysics = false;
        this.lifetime = 5 + this.random.nextInt(4);
        this.width = 0.10f + this.random.nextFloat() * 0.14f;
        // mostly white-hot cuts, some tinted red
        if (this.random.nextFloat() < 0.35f) {
            this.setColor(1.0f, 0.45f, 0.45f);
        } else {
            this.setColor(1.0f, 1.0f, 1.0f);
        }
        // make sure the line isn't frustum-culled when only its far end is on screen
        this.setBoundingBox(new AABB(x, y, z, x + dx, y + dy, z + dz).inflate(0.3));
    }

    @Override
    public void tick() {
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float sx = (float) (this.x - cam.x());
        float sy = (float) (this.y - cam.y());
        float sz = (float) (this.z - cam.z());

        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4f) return;

        // width direction: perpendicular to both the line and the direction to the camera
        float mx = sx + dx * 0.5f, my = sy + dy * 0.5f, mz = sz + dz * 0.5f;
        float wx = dy * mz - dz * my;
        float wy = dz * mx - dx * mz;
        float wz = dx * my - dy * mx;
        float wl = Mth.sqrt(wx * wx + wy * wy + wz * wz);
        if (wl < 1.0E-4f) return;

        float t = ((float) this.age + partialTicks) / (float) this.lifetime;
        float half = this.width * (1.0f - 0.6f * Mth.clamp(t, 0f, 1f)) * 0.5f;
        float k = half / wl;
        wx *= k; wy *= k; wz *= k;

        float ax = sx, ay = sy, az = sz;
        float bx = sx + dx, by = sy + dy, bz = sz + dz;

        float flicker = 0.75f + 0.25f * this.random.nextFloat();
        float a = Mth.clamp(1.0f - t * t, 0f, 1f) * flicker;
        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        int light = 15728880; // full bright

        // front face
        vertex(buffer, ax + wx, ay + wy, az + wz, u0, v0, a, light);
        vertex(buffer, bx + wx, by + wy, bz + wz, u1, v0, a, light);
        vertex(buffer, bx - wx, by - wy, bz - wz, u1, v1, a, light);
        vertex(buffer, ax - wx, ay - wy, az - wz, u0, v1, a, light);
        // back face (reversed winding)
        vertex(buffer, ax - wx, ay - wy, az - wz, u0, v1, a, light);
        vertex(buffer, bx - wx, by - wy, bz - wz, u1, v1, a, light);
        vertex(buffer, bx + wx, by + wy, bz + wz, u1, v0, a, light);
        vertex(buffer, ax + wx, ay + wy, az + wz, u0, v0, a, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, float a, int light) {
        buffer.vertex(x, y, z).uv(u, v).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
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
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double dx, double dy, double dz) {
            return new SlashLineParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
