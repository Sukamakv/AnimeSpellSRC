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
 * A glowing straight line from its position to position + (velocity fields). Used for the bow
 * limbs, the bowstring, the arrow shaft and fletching, and the streaks around the arrow.
 * Draws with additive blending so overlapping lines burn white-hot.
 */
public class FugaLineParticle extends TextureSheetParticle {

    public static class Style {
        float minWidth, maxWidth;
        int minLife, maxLife;
        float[] tint;
        boolean translucent = false;

        /** Normal (non-glowing) blending: needed for black ink to show up. */
        public Style translucent() { this.translucent = true; return this; }

        public Style(float minWidth, float maxWidth, int minLife, int maxLife, float r, float g, float b) {
            this.minWidth = minWidth;
            this.maxWidth = maxWidth;
            this.minLife = minLife;
            this.maxLife = maxLife;
            this.tint = new float[]{r, g, b};
        }
    }

    /** Thin streaks, string, fletching. */
    public static final Style LINE = new Style(0.05f, 0.14f, 2, 5, 1f, 0.95f, 0.85f);
    /** Thick bright shafts: the arrow and the bow limbs. */
    public static final Style BEAM = new Style(0.22f, 0.45f, 2, 3, 1f, 0.97f, 0.9f);

    /** Purple arcs for Hollow Purple. */
    public static final Style HP_LINE = new Style(0.08f, 0.22f, 2, 4, 1f, 1f, 1f);

    /** Cold white-blue streaks for Unlimited Void. */
    public static final Style UV_LINE = new Style(0.06f, 0.18f, 2, 4, 1f, 1f, 1f);

    /** Black-ink cut with a white core, used by World Cutting Slash. */
    public static final Style WC_INK = new Style(0.35f, 0.75f, 18, 28, 1f, 1f, 1f).translucent();

    /** The crimson laser: a block wide, black outline, red body, white-hot core. */
    public static final Style CW_LASER = new Style(1.0f, 1.0f, 6, 8, 1f, 1f, 1f).translucent();
    /** Crimson Ray: 1.75x the width of CW_LASER, lives only a few ticks because it is resent every tick. */
    public static final Style CR_BEAM = new Style(1.75f, 1.75f, 3, 3, 1f, 1f, 1f).translucent();
    public static final Style CR_CORE = new Style(0.75f, 0.75f, 3, 3, 1f, 0.8f, 0.8f);
    /** Gilgamesh: a golden chain link strip, a thin weapon trail, a glowing crack in the ground. */
    public static final Style GOLD_CHAIN = new Style(0.2f, 0.2f, 4, 4, 1f, 0.92f, 0.7f);
    public static final Style GOLD_LINE = new Style(0.08f, 0.22f, 5, 9, 1f, 0.82f, 0.38f);
    public static final Style EA_CRACK = new Style(0.14f, 0.32f, 60, 90, 1f, 0.28f, 0.2f);
    /** Sun Breathing: thick golden beam, thin orange streak, crimson beam. */
    /** Darius: a thin red line (outlines, trails) and a heavier iron-red one (Apprehend's chain). */
    public static final Style DR_LINE = new Style(0.03f, 0.06f, 3, 4, 1f, 0.2f, 0.18f);
    public static final Style DR_CHAIN = new Style(0.05f, 0.09f, 2, 3, 0.85f, 0.12f, 0.12f);
    /** Steel-white streaks for Garen's blade trails. */
    public static final Style GR_LINE = new Style(0.1f, 0.26f, 3, 6, 0.9f, 0.96f, 1f);
    /** Thick golden shaft for Demacian Justice's beam. */
    public static final Style GOLD_BEAM = new Style(0.6f, 1.2f, 3, 5, 1f, 0.85f, 0.4f);
    public static final Style SUN_BEAM = new Style(0.5f, 0.7f, 5, 7, 1f, 0.78f, 0.3f);
    public static final Style SUN_LINE = new Style(0.1f, 0.26f, 6, 10, 1f, 0.55f, 0.15f);
    public static final Style SUN_BEAM_RED = new Style(0.4f, 0.6f, 4, 6, 1f, 0.2f, 0.2f);
    /** Martial arts: thin warm speed streak, and a thick heavy one. */
    public static final Style MA_LINE = new Style(0.05f, 0.14f, 2, 4, 1f, 0.93f, 0.78f);
    public static final Style MA_LINE_HEAVY = new Style(0.16f, 0.34f, 3, 5, 1f, 0.85f, 0.55f);

    /** Viego: a thin spectral green line and a heavier dark-green tendril. */
    public static final Style SP_LINE = new Style(0.04f, 0.09f, 3, 5, 0.62f, 0.35f, 1f);
    public static final Style SP_BEAM = new Style(0.07f, 0.12f, 3, 4, 0.55f, 0.95f, 1f);
    public static final Style VG_LINE = new Style(0.03f, 0.06f, 3, 4, 0.45f, 1f, 0.65f);
    public static final Style VG_TENDRIL = new Style(0.06f, 0.11f, 3, 5, 0.12f, 0.6f, 0.35f);

    private final boolean translucent;
    private final float dx, dy, dz;
    private final float width;

    protected FugaLineParticle(ClientLevel level, double x, double y, double z,
                               double dx, double dy, double dz, SpriteSet sprites, Style style) {
        super(level, x, y, z, 0, 0, 0);
        this.pickSprite(sprites);
        this.translucent = style.translucent;
        this.dx = (float) dx;
        this.dy = (float) dy;
        this.dz = (float) dz;
        this.xd = this.yd = this.zd = 0;
        this.hasPhysics = false;
        this.lifetime = Mth.nextInt(this.random, style.minLife, style.maxLife);
        this.width = style.minWidth + this.random.nextFloat() * (style.maxWidth - style.minWidth);
        this.setColor(style.tint[0], style.tint[1], style.tint[2]);
        this.setBoundingBox(new AABB(x, y, z, x + dx, y + dy, z + dz).inflate(0.5));
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

        // width direction: perpendicular to the line and to the direction towards the camera
        float mx = sx + dx * 0.5f, my = sy + dy * 0.5f, mz = sz + dz * 0.5f;
        float wx = dy * mz - dz * my;
        float wy = dz * mx - dx * mz;
        float wz = dx * my - dy * mx;
        float wl = Mth.sqrt(wx * wx + wy * wy + wz * wz);
        if (wl < 1.0E-4f) return;

        float t = Mth.clamp(((float) this.age + partialTicks) / (float) this.lifetime, 0f, 1f);
        float half = this.width * (1.0f - 0.5f * t) * 0.5f;
        float k = half / wl;
        wx *= k; wy *= k; wz *= k;

        float flicker = 0.8f + 0.2f * this.random.nextFloat();
        float a = Mth.clamp(1.0f - t * t, 0f, 1f) * flicker;
        float ax = sx, ay = sy, az = sz, bx = sx + dx, by = sy + dy, bz = sz + dz;
        float u0 = this.getU0(), u1 = this.getU1(), v0 = this.getV0(), v1 = this.getV1();
        int light = 15728880;

        vertex(buffer, ax + wx, ay + wy, az + wz, u0, v0, a, light);
        vertex(buffer, bx + wx, by + wy, bz + wz, u1, v0, a, light);
        vertex(buffer, bx - wx, by - wy, bz - wz, u1, v1, a, light);
        vertex(buffer, ax - wx, ay - wy, az - wz, u0, v1, a, light);
        vertex(buffer, ax - wx, ay - wy, az - wz, u0, v1, a, light);
        vertex(buffer, bx - wx, by - wy, bz - wz, u1, v1, a, light);
        vertex(buffer, bx + wx, by + wy, bz + wz, u1, v0, a, light);
        vertex(buffer, ax + wx, ay + wy, az + wz, u0, v0, a, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, float a, int light) {
        buffer.vertex(x, y, z).uv(u, v).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
    }

    /** A line can be far longer than its anchor point suggests; never cull it just because its start is off screen. */
    @Override
    public boolean shouldCull() {
        return false;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return translucent ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT : FugaRenderTypes.ADDITIVE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Style style;

        public Provider(SpriteSet sprites, Style style) {
            this.sprites = sprites;
            this.style = style;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double dx, double dy, double dz) {
            return new FugaLineParticle(level, x, y, z, dx, dy, dz, sprites, style);
        }
    }
}
