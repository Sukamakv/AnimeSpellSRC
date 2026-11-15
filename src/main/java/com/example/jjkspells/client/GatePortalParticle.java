package com.example.jjkspells.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A golden magic portal: a flat disc with a glowing geometric pattern that turns slowly, standing in the world facing a
 * fixed direction. The velocity values are the facing direction scaled by the portal's radius. It opens quickly, stays,
 * then closes. A second, larger copy of the pattern turns the other way behind it.
 */
public class GatePortalParticle extends TextureSheetParticle {
    private final Vector3f n;      // unit normal
    private final Vector3f u;      // unit axis in the plane
    private final Vector3f v;      // unit axis in the plane
    private final float radius;
    private final int openTicks;
    private final float spinPhase;

    protected GatePortalParticle(ClientLevel level, double x, double y, double z, double nx, double ny, double nz,
                                 SpriteSet sprites, int life, int openTicks) {
        super(level, x, y, z, 0, 0, 0);
        this.pickSprite(sprites);
        this.radius = (float) Math.max(0.2, Math.sqrt(nx * nx + ny * ny + nz * nz));
        this.n = new Vector3f((float) nx, (float) ny, (float) nz);
        if (this.n.lengthSquared() < 1e-6f) this.n.set(0, 0, 1);
        this.n.normalize();
        Vector3f ref = Math.abs(this.n.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        this.u = new Vector3f(ref).cross(this.n).normalize();
        this.v = new Vector3f(this.n).cross(this.u).normalize();
        this.lifetime = life;
        this.openTicks = openTicks;
        this.spinPhase = this.random.nextFloat() * Mth.TWO_PI;
        this.hasPhysics = false;
        this.xd = this.yd = this.zd = 0;
        this.setBoundingBox(new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius));
    }

    @Override
    public void tick() {
        if (this.age++ >= this.lifetime) this.remove();
    }

    private void layer(VertexConsumer vc, Vec3 cam, float partial, float size, float angle, float a) {
        float cx = (float) (Mth.lerp(partial, this.xo, this.x) - cam.x);
        float cy = (float) (Mth.lerp(partial, this.yo, this.y) - cam.y);
        float cz = (float) (Mth.lerp(partial, this.zo, this.z) - cam.z);
        float c = Mth.cos(angle) * size, s = Mth.sin(angle) * size;
        // four corners of the rotated square, in (u, v) coordinates
        float[][] q = {{-c + s, -s - c}, {c + s, s - c}, {c - s, s + c}, {-c - s, -s + c}};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = 15728880;
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                int i = pass == 0 ? k : 3 - k;
                float px = cx + u.x * q[i][0] + v.x * q[i][1];
                float py = cy + u.y * q[i][0] + v.y * q[i][1];
                float pz = cz + u.z * q[i][0] + v.z * q[i][1];
                float tu = uv[i][0] == 0 ? u0 : u1, tv = uv[i][1] == 0 ? v0 : v1;
                vc.vertex(px, py, pz).uv(tu, tv).color(1f, 1f, 1f, a).uv2(light).endVertex();
            }
        }
    }

    @Override
    public void render(VertexConsumer vc, Camera camera, float partial) {
        Vec3 cam = camera.getPosition();
        float t = this.age + partial;
        float open = Mth.clamp(t / this.openTicks, 0f, 1f);
        open = 1f - (1f - open) * (1f - open) * (1f - open);
        float close = Mth.clamp((this.lifetime - t) / this.openTicks, 0f, 1f);
        float scale = open * close;
        if (scale <= 0.01f) return;
        float flick = 0.9f + 0.1f * Mth.sin(t * 0.7f + spinPhase);
        layer(vc, cam, partial, radius * scale, spinPhase + t * 0.04f, Math.min(1f, scale * 1.2f) * flick);
        layer(vc, cam, partial, radius * scale * 1.18f, -spinPhase - t * 0.025f, 0.45f * scale);
    }

    @Override
    public boolean shouldCull() {
        return false;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return FugaRenderTypes.ADDITIVE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final int life, open;

        public Provider(SpriteSet sprites, int life, int open) {
            this.sprites = sprites;
            this.life = life;
            this.open = open;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new GatePortalParticle(level, x, y, z, dx, dy, dz, sprites, life, open);
        }
    }
}
