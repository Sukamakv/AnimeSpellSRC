package com.example.jjkspells.client.particle;

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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Expanding shockwave ring.
 * Parameters passed to addParticle: xd = final radius in blocks, yd = 0 for a ring lying flat (horizontal), 1 for a ring facing the camera.
 */
@OnlyIn(Dist.CLIENT)
public class RingParticle extends TextureSheetParticle {
    private final float finalRadius;
    private final boolean facingCamera;

    protected RingParticle(ClientLevel level, double x, double y, double z, float finalRadius, boolean facingCamera, SpriteSet sprites) {
        super(level, x, y, z);
        this.finalRadius = Math.max(0.5f, finalRadius);
        this.facingCamera = facingCamera;
        this.lifetime = 11;
        this.hasPhysics = false;
        this.gravity = 0f;
        this.quadSize = 0.3f;
        this.pickSprite(sprites);
    }

    private float progress(float partialTick) {
        return Mth.clamp(((float) this.age + partialTick) / (float) this.lifetime, 0f, 1f);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float p = progress(partialTick);
        float eased = 1f - (1f - p) * (1f - p) * (1f - p); // fast burst outward, then slow down
        return Mth.lerp(eased, 0.3f, this.finalRadius);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        float p = progress(partialTick);
        this.alpha = (1f - p) * (1f - p);

        if (this.facingCamera) {
            super.render(buffer, camera, partialTick);
            return;
        }

        // Ring lying flat on the horizontal plane, drawn double-sided.
        Vec3 cam = camera.getPosition();
        float cx = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x());
        float cy = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y());
        float cz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z());
        float s = getQuadSize(partialTick);
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTick);

        // top face
        vertex(buffer, cx - s, cy, cz - s, u1, v1, light);
        vertex(buffer, cx - s, cy, cz + s, u1, v0, light);
        vertex(buffer, cx + s, cy, cz + s, u0, v0, light);
        vertex(buffer, cx + s, cy, cz - s, u0, v1, light);
        // bottom face (reverse winding)
        vertex(buffer, cx + s, cy, cz - s, u0, v1, light);
        vertex(buffer, cx + s, cy, cz + s, u0, v0, light);
        vertex(buffer, cx - s, cy, cz + s, u1, v0, light);
        vertex(buffer, cx - s, cy, cz - s, u1, v1, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(x, y, z).uv(u, v).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public boolean shouldCull() {
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new RingParticle(level, x, y, z, (float) xd, yd > 0.5, sprites);
        }
    }
}
