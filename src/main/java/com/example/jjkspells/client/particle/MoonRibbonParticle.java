package com.example.jjkspells.client.particle;

import com.example.jjkspells.particle.MoonRibbonOptions;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

/**
 * Draws a Moon Breathing slash (purple) or crescent (blue) as a smooth tapering ribbon built from a strip of quads,
 * so the curve, the width and the sweep-on / shrink-away animation are all exact instead of baked into a sprite.
 * The sprite is just a plain white texel; colour comes from per-vertex colours (bright core, soft edges).
 */
@OnlyIn(Dist.CLIENT)
public class MoonRibbonParticle extends TextureSheetParticle {
    // cross-section: edge, mid, core, mid, edge  (r, g, b, a)
    private static final float[][] PURPLE = {
            {0.50f, 0.28f, 0.86f, 0.30f},
            {0.78f, 0.62f, 0.98f, 0.92f},
            {1.00f, 0.93f, 1.00f, 1.00f},
            {0.78f, 0.62f, 0.98f, 0.92f},
            {0.50f, 0.28f, 0.86f, 0.30f}};
    private static final float[][] BLUE = {
            {0.06f, 0.26f, 0.70f, 0.95f},
            {0.26f, 0.70f, 1.00f, 1.00f},
            {0.82f, 0.97f, 1.00f, 1.00f},
            {0.26f, 0.70f, 1.00f, 1.00f},
            {0.06f, 0.26f, 0.70f, 0.95f}};
    private static final float[][] GREEN = {
            {0.05f, 0.35f, 0.22f, 0.35f},
            {0.20f, 0.85f, 0.55f, 0.92f},
            {0.85f, 1.00f, 0.92f, 1.00f},
            {0.20f, 0.85f, 0.55f, 0.92f},
            {0.05f, 0.35f, 0.22f, 0.35f}};
    private static final float[][] DARK_GREEN = {
            {0.00f, 0.05f, 0.04f, 0.55f},
            {0.04f, 0.30f, 0.22f, 0.95f},
            {0.35f, 0.95f, 0.60f, 1.00f},
            {0.04f, 0.30f, 0.22f, 0.95f},
            {0.00f, 0.05f, 0.04f, 0.55f}};
    private static final float[][] BLOOD = {
            {0.03f, 0.00f, 0.01f, 0.70f},
            {0.45f, 0.02f, 0.04f, 0.95f},
            {1.00f, 0.28f, 0.22f, 1.00f},
            {0.45f, 0.02f, 0.04f, 0.95f},
            {0.03f, 0.00f, 0.01f, 0.70f}};
    private static final float[][] SUN = {
            {0.75f, 0.12f, 0.02f, 0.35f},
            {1.00f, 0.45f, 0.06f, 0.95f},
            {1.00f, 0.88f, 0.45f, 1.00f},
            {1.00f, 0.45f, 0.06f, 0.95f},
            {0.75f, 0.12f, 0.02f, 0.35f}};
    private static final float[][] CRIMSON = {
            {0.35f, 0.00f, 0.04f, 0.40f},
            {0.90f, 0.06f, 0.12f, 0.95f},
            {1.00f, 0.80f, 0.74f, 1.00f},
            {0.90f, 0.06f, 0.12f, 0.95f},
            {0.35f, 0.00f, 0.04f, 0.40f}};
    private static final float[][] RED = {
            {0.42f, 0.02f, 0.05f, 0.35f},
            {0.80f, 0.08f, 0.10f, 0.92f},
            {1.00f, 0.52f, 0.45f, 1.00f},
            {0.80f, 0.08f, 0.10f, 0.92f},
            {0.42f, 0.02f, 0.05f, 0.35f}};
    private static final float[][] BRIGHT_RED = {
            {0.75f, 0.08f, 0.08f, 0.40f},
            {1.00f, 0.22f, 0.18f, 0.95f},
            {1.00f, 0.88f, 0.82f, 1.00f},
            {1.00f, 0.22f, 0.18f, 0.95f},
            {0.75f, 0.08f, 0.08f, 0.40f}};
    private static final float[][] STEEL = {
            {0.22f, 0.24f, 0.28f, 0.40f},
            {0.62f, 0.65f, 0.72f, 0.95f},
            {0.96f, 0.97f, 1.00f, 1.00f},
            {0.62f, 0.65f, 0.72f, 0.95f},
            {0.22f, 0.24f, 0.28f, 0.40f}};
    private static final float[][] DARK_RED = {
            {0.15f, 0.00f, 0.03f, 0.50f},
            {0.50f, 0.03f, 0.08f, 0.95f},
            {0.92f, 0.25f, 0.28f, 1.00f},
            {0.50f, 0.03f, 0.08f, 0.95f},
            {0.15f, 0.00f, 0.03f, 0.50f}};
    private static final float[] LATERAL = {-1f, -0.5f, 0f, 0.5f, 1f};
    private static final int M = LATERAL.length;

    private final MoonRibbonOptions o;

    protected MoonRibbonParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                                 MoonRibbonOptions options, SpriteSet sprites) {
        super(level, x, y, z);
        this.o = options;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = Math.max(2, options.life);
        this.hasPhysics = false;
        this.gravity = 0f;
        this.friction = 0.9f;
        this.pickSprite(sprites);
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    @Override
    public void render(VertexConsumer buf, Camera camera, float partialTick) {
        final boolean crescent = o.mode == MoonRibbonOptions.CRESCENT;
        final boolean inPlane = o.mode != MoonRibbonOptions.RIBBON;          // width runs across the arc inside its plane (crescent, flat)
        float t = Mth.clamp((this.age + partialTick) / (float) this.lifetime, 0f, 1f);

        // the ribbon sweeps on quickly, lingers, then its tail catches up and it vanishes
        float headFrac = crescent ? 0.22f : 0.34f;
        float tailStart = crescent ? 0.55f : 0.42f;
        float head = Mth.clamp(t / headFrac, 0f, 1f);
        head = 1f - (1f - head) * (1f - head);
        float tail = Mth.clamp((t - tailStart) / (1f - tailStart), 0f, 1f);
        tail = tail * tail;
        if (head - tail < 0.03f) return;
        float fade = 1f - smooth(0.82f, 1f, t);

        Vec3 cam = camera.getPosition();
        float cx = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x());
        float cy = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y());
        float cz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z());

        // plane axes
        float ux, uy, uz, vx, vy, vz;
        if (crescent) {
            Vector3f l = camera.getLeftVector();
            Vector3f up = camera.getUpVector();
            float c = Mth.cos(o.roll), s = Mth.sin(o.roll);
            ux = l.x * c + up.x * s; uy = l.y * c + up.y * s; uz = l.z * c + up.z * s;
            vx = up.x * c - l.x * s; vy = up.y * c - l.y * s; vz = up.z * c - l.z * s;
        } else {
            ux = o.ux; uy = o.uy; uz = o.uz;
            vx = o.vx; vy = o.vy; vz = o.vz;
        }
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;

        final int n = crescent ? 14 : 28;
        float[] px = new float[n + 1], py = new float[n + 1], pz = new float[n + 1];
        float[] rx = new float[n + 1], ry = new float[n + 1], rz = new float[n + 1];
        float[] half = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float p = tail + (head - tail) * i / n;
            float th = Mth.lerp(p, o.th0, o.th1);
            float r = Mth.lerp(p, o.r0, o.r1);
            float h = o.lift * p;
            float c = Mth.cos(th), s = Mth.sin(th);
            rx[i] = c * ux + s * vx; ry[i] = c * uy + s * vy; rz[i] = c * uz + s * vz;
            px[i] = cx + rx[i] * r + nx * h;
            py[i] = cy + ry[i] * r + ny * h;
            pz[i] = cz + rz[i] * r + nz * h;
            float taper = (float) Math.pow(Math.sin(Math.PI * Mth.clamp(p, 0f, 1f)), 0.75);
            float endCut = Math.min(1f, Math.min(i, n - i) / (n * 0.16f));
            half[i] = o.width * 0.5f * taper * endCut;
        }

        float[] pos = new float[(n + 1) * M * 3];
        for (int i = 0; i <= n; i++) {
            float sx, sy, sz;
            if (inPlane) {
                sx = rx[i]; sy = ry[i]; sz = rz[i];                   // width runs across the arc, inside the plane
            } else {
                int a = Math.max(0, i - 1), b = Math.min(n, i + 1);
                float tx = px[b] - px[a], ty = py[b] - py[a], tz = pz[b] - pz[a];
                sx = ty * pz[i] - tz * py[i];                         // tangent x view direction -> faces the camera
                sy = tz * px[i] - tx * pz[i];
                sz = tx * py[i] - ty * px[i];
                float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (len < 1.0E-5f) { sx = 0f; sy = 1f; sz = 0f; } else { sx /= len; sy /= len; sz /= len; }
            }
            for (int k = 0; k < M; k++) {
                float f = LATERAL[k] * half[i];
                int idx = (i * M + k) * 3;
                pos[idx] = px[i] + sx * f;
                pos[idx + 1] = py[i] + sy * f;
                pos[idx + 2] = pz[i] + sz * f;
            }
        }

        float[][] pal = switch (o.palette) {
            case MoonRibbonOptions.BLUE -> BLUE;
            case MoonRibbonOptions.SUN -> SUN;
            case MoonRibbonOptions.CRIMSON -> CRIMSON;
            case MoonRibbonOptions.RED -> RED;
            case MoonRibbonOptions.BRIGHT_RED -> BRIGHT_RED;
            case MoonRibbonOptions.STEEL -> STEEL;
            case MoonRibbonOptions.DARK_RED -> DARK_RED;
            case MoonRibbonOptions.GREEN -> GREEN;
            case MoonRibbonOptions.DARK_GREEN -> DARK_GREEN;
            case MoonRibbonOptions.BLOOD -> BLOOD;
            default -> PURPLE;
        };
        float u = (this.getU0() + this.getU1()) * 0.5f;
        float v = (this.getV0() + this.getV1()) * 0.5f;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < M - 1; k++) {
                int a = (i * M + k) * 3, b = (i * M + k + 1) * 3;
                int c = ((i + 1) * M + k + 1) * 3, d = ((i + 1) * M + k) * 3;
                // front face, then the same quad reversed so it shows from both sides
                vertex(buf, pos, a, pal[k], fade, u, v);
                vertex(buf, pos, b, pal[k + 1], fade, u, v);
                vertex(buf, pos, c, pal[k + 1], fade, u, v);
                vertex(buf, pos, d, pal[k], fade, u, v);
                vertex(buf, pos, d, pal[k], fade, u, v);
                vertex(buf, pos, c, pal[k + 1], fade, u, v);
                vertex(buf, pos, b, pal[k + 1], fade, u, v);
                vertex(buf, pos, a, pal[k], fade, u, v);
            }
        }
    }

    private static void vertex(VertexConsumer buf, float[] pos, int i, float[] col, float fade, float u, float v) {
        buf.vertex(pos[i], pos[i + 1], pos[i + 2]).uv(u, v).color(col[0], col[1], col[2], col[3] * fade).uv2(15728880).endVertex();
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** A ribbon can be large and its centre far off screen while part of it is still in view. */
    @Override
    public boolean shouldCull() {
        return false;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<MoonRibbonOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(MoonRibbonOptions options, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new MoonRibbonParticle(level, x, y, z, xd, yd, zd, options, sprites);
        }
    }
}
