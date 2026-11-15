package com.example.jjkspells.yhwach.client;

import com.example.jjkspells.yhwach.entity.SanktZwingerEntity;
import com.example.jjkspells.yhwach.entity.SanktZwingerEntity.Fx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Draws "Kirchenlied: Sankt Zwinger": the expanding multi-layer ground insignia, eight pillars with wheel-cross capitals,
 * gothic arches and ribs converging on a crown, hex-lattice curtain walls with crenellations, rotating ritual rings,
 * floating crosses, streams of light, barrier ripples and the golden-white counterattacks.
 */
public class SanktRenderer extends EntityRenderer<SanktZwingerEntity> {
    private static final int N = SanktZwingerEntity.PILLARS;
    private static final double SEAL = SanktZwingerEntity.SEAL_R;
    private static final double PR = SanktZwingerEntity.PILLAR_R;
    private static final double PH = SanktZwingerEntity.PILLAR_H;
    private static final double WH = SanktZwingerEntity.WALL_H;
    private static final double CROWN = PH + 14.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    // per-frame state
    private Pen p;
    private float t;
    private float fade;
    private Vec3 C;
    private int seed;
    private final double[] pr = new double[N];
    private final float[] flash = new float[N];
    private double wallP;
    private double archP;
    private final List<Fx> ripples = new ArrayList<>();
    private final List<Fx> strikes = new ArrayList<>();
    private int tickNow;
    private float pt;

    public SanktRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(SanktZwingerEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SanktZwingerEntity e) {
        return YhwachRender.GLOW;
    }

    // ------------------------------------------------------------------ small helpers

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private double prog(double start, double dur) {
        return ease((t - start) / dur);
    }

    private Vec3 base(int k) {
        return SanktZwingerEntity.pillarBase(C, ((k % N) + N) % N);
    }

    private Vec3 top(int k, double h) {
        return base(k).add(0, h, 0);
    }

    private void use(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(YhwachRender.additive(tex));
    }

    private void glowLayer(Vec3 c, Vec3 A, Vec3 B, double half, double rot, float a) {
        if (a <= 0.003f || half <= 0.01) {
            return;
        }
        p.plane(c, A, B, half, rot, Pen.BLUE, a * 0.6f);
        p.plane(c, A, B, half, rot, Pen.CYAN, a);
        p.plane(c, A, B, half, rot, Pen.CORE, a * 0.5f);
    }

    /** Four-layer glowing column (STREAK texture). */
    private void column(Vec3 a, Vec3 b, double wa, double wb, float al, float u0, float u1, boolean gold) {
        if (al <= 0.003f) {
            return;
        }
        float[][] cols = gold ? new float[][]{Pen.GOLD, Pen.GOLD, Pen.GOLDW, Pen.WHITE} : new float[][]{Pen.BLUE, Pen.CYAN, Pen.CORE, Pen.WHITE};
        double[] mul = {1.0, 0.58, 0.27, 0.11};
        float[] am = {0.35f, 0.6f, 0.9f, 1.0f};
        for (int l = 0; l < 4; l++) {
            p.ribbon(a, b, wa * mul[l], wb * mul[l], am[l] * al, am[l] * al, u0, u1, cols[l]);
        }
    }

    private void glow(Vec3 c, double half, float[] col, float a) {
        p.billboard(c, half, 0, col, a);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(SanktZwingerEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        pt = partial;
        tickNow = e.tickCount;
        t = e.tickCount - 1 + partial;
        seed = e.getId();
        C = e.getPosition(partial);
        fade = t > SanktZwingerEntity.ACTIVE_END ? (float) (1.0 - c01((t - SanktZwingerEntity.ACTIVE_END) / SanktZwingerEntity.FADE)) : 1f;
        if (fade <= 0.003f) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), C, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        for (int k = 0; k < N; k++) {
            pr[k] = prog(SanktZwingerEntity.PILLAR_START + k * SanktZwingerEntity.PILLAR_STAGGER, SanktZwingerEntity.PILLAR_RISE);
            flash[k] = 0f;
        }
        wallP = prog(56, 34);
        archP = prog(SanktZwingerEntity.ARCH_START, SanktZwingerEntity.BUILD - SanktZwingerEntity.ARCH_START);
        ripples.clear();
        strikes.clear();
        for (Fx f : e.clientFx) {
            if (f.strike) {
                strikes.add(f);
                float a = age(f);
                flash[f.pillar] = Math.max(flash[f.pillar], (float) Math.max(0.0, 1.0 - a / 12.0));
            } else {
                ripples.add(f);
            }
        }

        use(buf, YhwachRender.GLOW);
        glowPass();
        use(buf, YhwachRender.INS_BASE);
        insBase();
        use(buf, YhwachRender.INS_OUTER);
        insOuter();
        use(buf, YhwachRender.INS_MID);
        insMid();
        use(buf, YhwachRender.INS_CORE);
        insCore();
        use(buf, YhwachRender.STREAK);
        streakPass();
        use(buf, YhwachRender.RING);
        ringPass();
        use(buf, YhwachRender.HEX);
        hexPass();
        use(buf, YhwachRender.WHEEL);
        wheelPass();
        use(buf, YhwachRender.CROSS);
        crossPass();
        use(buf, YhwachRender.SPARKLE);
        sparklePass();
        use(buf, YhwachRender.BURST);
        burstPass();
    }

    private float age(Fx f) {
        return (tickNow - f.born) + pt;
    }

    // ------------------------------------------------------------------ GLOW

    private void glowPass() {
        double ex = prog(0, SanktZwingerEntity.EXPAND);
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.12f);
        Vec3 g = C.add(0, 0.05, 0);
        p.plane(g, X, Z, SEAL * 1.18 * ex, 0, Pen.BLUE, (0.30f + 0.06f * pulse) * fade);
        p.plane(g, X, Z, SEAL * 0.8 * ex, 0, Pen.CYAN, 0.22f * fade);
        p.plane(g, X, Z, SEAL * 0.32 * ex, 0, Pen.WHITE, (0.35f + 0.1f * pulse) * fade);
        // camera-facing bloom at the heart of the seal while it opens
        if (t < 60) {
            glow(C.add(0, 0.8, 0), 3.0 + 6.0 * ex, Pen.CYAN, (float) (0.5 * (1.0 - c01((t - 20) / 40.0))) * fade);
        }
        for (int k = 0; k < N; k++) {
            if (pr[k] <= 0.01) {
                continue;
            }
            double h = PH * pr[k];
            Vec3 b = base(k);
            float a = (float) c01(pr[k] * 3.0) * fade;
            glow(b.add(0, 0.8, 0), 3.8, Pen.CYAN, 0.45f * a);
            glow(b.add(0, 0.8, 0), 1.8, Pen.WHITE, 0.5f * a);
            for (double y = 3.0; y < h; y += 4.0) {
                glow(b.add(0, y, 0), 2.6, Pen.BLUE, 0.22f * a);
            }
            Vec3 tp = b.add(0, h, 0);
            float capA = (float) c01((pr[k] - 0.6) / 0.4) * fade;
            glow(tp, 6.0, Pen.BLUE, 0.35f * capA);
            glow(tp, 3.0, Pen.CYAN, 0.45f * capA);
            if (flash[k] > 0.01f) {
                glow(tp, 8.0 * flash[k] + 2.0, Pen.GOLD, 0.8f * flash[k] * fade);
                glow(tp, 3.5 * flash[k] + 1.0, Pen.GOLDW, 1.0f * flash[k] * fade);
            }
        }
        if (archP > 0.02) {
            double pls = 0.85 + 0.15 * Math.sin(t * 0.1);
            glow(C.add(0, CROWN, 0), 9.0 * archP * pls, Pen.BLUE, 0.35f * fade);
            glow(C.add(0, CROWN, 0), 4.0 * archP * pls, Pen.CYAN, 0.5f * fade);
            glow(C.add(0, CROWN, 0), 1.8 * archP, Pen.WHITE, 0.8f * fade);
        }
        for (int k = 0; k < N; k++) {
            if (wallP > 0.05) {
                Vec3 m = base(k).add(base(k + 1)).scale(0.5).add(0, 0.1, 0);
                Vec3 tan = base(k + 1).subtract(base(k)).normalize();
                p.plane(m, tan, new Vec3(-tan.z, 0, tan.x), 5.5 * wallP, 0, Pen.CYAN, 0.18f * fade);
            }
        }
        for (Fx f : ripples) {
            float a = age(f);
            double life = 1.0 - a / SanktZwingerEntity.RIPPLE_LIFE;
            glow(f.pos, 3.5 + a * 0.1, Pen.CYAN, (float) (0.8 * life) * fade);
            glow(f.pos, 1.6, Pen.WHITE, (float) (1.0 * Math.max(0.0, 1.0 - a / 8.0)) * fade);
        }
        for (Fx f : strikes) {
            float a = age(f);
            double life = Math.max(0.0, 1.0 - a / SanktZwingerEntity.STRIKE_LIFE);
            glow(f.pos, 7.0 * (0.4 + life), Pen.GOLD, (float) (0.75 * life) * fade);
            glow(f.pos, 3.0 * (0.4 + life), Pen.GOLDW, (float) life * fade);
            Vec3 g0 = f.pos.multiply(1, 0, 1).add(0, C.y + 0.05, 0);
            p.plane(g0, X, Z, 6.0 + a * 0.35, 0, Pen.GOLD, (float) (0.45 * life) * fade);
        }
    }

    // ------------------------------------------------------------------ INSIGNIA LAYERS

    private void insBase() {
        double s = prog(0, SanktZwingerEntity.EXPAND);
        glowLayer(C.add(0, 0.06, 0), X, Z, SEAL * s, 0, (float) c01(t / 5.0) * fade);
    }

    private void insOuter() {
        double s = prog(4, SanktZwingerEntity.EXPAND);
        glowLayer(C.add(0, 0.07, 0), X, Z, SEAL * 0.99 * s, t * 0.012, (float) c01(t / 8.0) * fade);
        // ritual rings that wrap the pillars at four heights (alternating direction)
        for (int i = 0; i < 4; i++) {
            double rv = prog(SanktZwingerEntity.ARCH_START - 10 + i * 6, 16);
            if (rv < 0.02) {
                continue;
            }
            double y = 5.0 + i * 6.0 + Math.sin(t * 0.04 + i) * 0.4;
            double dir = (i & 1) == 0 ? 1 : -1;
            glowLayer(C.add(0, y, 0), X, Z, PR * 1.12 * rv, dir * (t * 0.01 + i), 0.8f * (float) rv * fade);
        }
        // tilted great rings (gyroscope) around the whole fortress
        if (archP > 0.02) {
            for (int j = 0; j < 3; j++) {
                double th = Math.toRadians(j == 0 ? 62 : j == 1 ? -62 : 90);
                double ph = t * (0.006 + 0.003 * j) * (j == 1 ? -1 : 1) + j * Math.PI / 3.0;
                Vec3 A0 = X;
                Vec3 B0 = new Vec3(0, Math.sin(th), Math.cos(th));
                Vec3 A = rotY(A0, ph);
                Vec3 B = rotY(B0, ph);
                glowLayer(C.add(0, 14.0, 0), A, B, PR * 1.36 * archP, t * 0.008 * (j + 1), 0.55f * (float) archP * fade);
            }
        }
    }

    private void insMid() {
        double s = prog(8, SanktZwingerEntity.EXPAND);
        glowLayer(C.add(0, 0.08, 0), X, Z, SEAL * 0.72 * s, -t * 0.02, (float) c01((t - 6) / 8.0) * fade);
        if (archP > 0.02) {
            glowLayer(C.add(0, PH + 3.0, 0), X, Z, PR * 0.85 * archP, t * 0.015, 0.55f * (float) archP * fade);
        }
    }

    private void insCore() {
        double s = prog(12, SanktZwingerEntity.EXPAND);
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.15f);
        glowLayer(C.add(0, 0.09, 0), X, Z, SEAL * 0.38 * s, t * 0.01, (float) c01((t - 10) / 8.0) * pulse * fade);
        if (archP > 0.02) {
            glowLayer(C.add(0, CROWN - 4.0, 0), X, Z, 6.0 * archP, -t * 0.03, 0.8f * (float) archP * pulse * fade);
        }
    }

    private static Vec3 rotY(Vec3 v, double a) {
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
    }

    // ------------------------------------------------------------------ STREAK (pillars, arches, walls, streams, beams)

    private void streakPass() {
        // pillars
        for (int k = 0; k < N; k++) {
            if (pr[k] <= 0.01) {
                continue;
            }
            double h = PH * pr[k];
            Vec3 b = base(k);
            Vec3 tp = b.add(0, h, 0);
            float a = fade;
            float u0 = -t * 0.025f;
            column(b, tp, 3.0, 2.0, a, u0, u0 + (float) (h / 7.0), false);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    Vec3 off = new Vec3(sx * 0.95, 0, sz * 0.95);
                    p.ribbon(b.add(off), tp.add(off.scale(0.7)), 0.14, 0.12, 0.75f * a, 0.75f * a, u0, u0 + (float) (h / 5.0), Pen.CORE);
                }
            }
            // plinth flare and capital trumpet
            p.ribbon(b, b.add(0, 3.0, 0), 5.4, 2.8, 0.55f * a, 0.35f * a, 0f, 1f, Pen.CYAN);
            p.ribbon(b, b.add(0, 1.6, 0), 3.4, 2.4, 0.8f * a, 0.5f * a, 0f, 1f, Pen.WHITE);
            if (pr[k] > 0.5) {
                float ca = (float) c01((pr[k] - 0.5) / 0.5) * a;
                p.ribbon(tp.add(0, -4.0, 0), tp, 2.0, 4.4, 0.45f * ca, 0.8f * ca, 0f, 1f, Pen.CYAN);
                p.ribbon(tp.add(0, -2.5, 0), tp, 1.2, 2.2, 0.6f * ca, 0.9f * ca, 0f, 1f, Pen.WHITE);
            }
            // sacred beam into the sky
            if (pr[k] > 0.95) {
                float sa = (float) c01((t - SanktZwingerEntity.PILLAR_START - k * 3 - 24) / 10.0) * a;
                float fl = 0.2f * flash[k];
                p.ribbon(tp, tp.add(0, 46, 0), 1.6, 0.9, 0.6f * sa, 0f, u0 * 3, u0 * 3 + 6f, Pen.CYAN);
                p.ribbon(tp, tp.add(0, 46, 0), 0.6, 0.3, 0.9f * sa, 0f, u0 * 3, u0 * 3 + 6f, Pen.WHITE);
                if (fl > 0f) {
                    p.ribbon(tp, tp.add(0, 46, 0), 3.4, 1.4, fl * 2f, 0f, 0f, 1f, Pen.GOLD);
                }
            }
        }

        // central beam through the fortress
        if (archP > 0.02) {
            Vec3 a0 = C.add(0, 0.2, 0);
            Vec3 a1 = C.add(0, CROWN * archP, 0);
            float u0 = -t * 0.04f;
            column(a0, a1, 3.2, 1.2, 0.55f * fade, u0, u0 + 8f, false);
        }

        // curtain walls: light strands with ragged edges, merlons, rails
        for (int k = 0; k < N; k++) {
            if (wallP <= 0.02) {
                continue;
            }
            Vec3 b0 = base(k);
            Vec3 b1 = base(k + 1);
            Vec3 tan = b1.subtract(b0).normalize();
            Random r = new Random(seed * 131L + k * 7919L);
            for (int m = 0; m < 14; m++) {
                double s = (m + 0.15 + r.nextDouble() * 0.7) / 14.0;
                double hh = WH * (0.45 + 0.55 * r.nextDouble()) * wallP;
                double w = 0.35 + 0.5 * r.nextDouble();
                Vec3 pa = b0.add(b1.subtract(b0).scale(s)).add(0, 0.1, 0);
                float tw = 0.6f + 0.4f * (float) Math.sin(t * (0.1 + 0.1 * r.nextDouble()) + r.nextDouble() * 6.28);
                float u0 = (float) (-t * 0.03 * (0.5 + r.nextDouble()));
                p.ribbonSide(pa, pa.add(0, hh, 0), tan, w, w * 0.4, 0.6f * tw * fade, 0.0f, u0, u0 + 2.2f, Pen.CYAN);
                p.ribbonSide(pa, pa.add(0, hh * 0.6, 0), tan, w * 0.35, w * 0.15, 0.9f * tw * fade, 0.0f, u0, u0 + 1.4f, Pen.WHITE);
            }
            // top rail and crenellations
            double railP = c01((wallP - 0.7) / 0.3);
            if (railP > 0.01) {
                Vec3 t0 = b0.add(0, WH, 0);
                Vec3 t1 = b1.add(0, WH, 0);
                p.ribbon(t0, t1, 0.9, 0.9, 0.6f * (float) railP * fade, 0.6f * (float) railP * fade, 0f, 3f, Pen.CYAN);
                p.ribbon(t0, t1, 0.3, 0.3, 0.95f * (float) railP * fade, 0.95f * (float) railP * fade, 0f, 3f, Pen.WHITE);
                p.ribbon(b0.add(0, 0.2, 0), b1.add(0, 0.2, 0), 0.7, 0.7, 0.5f * fade, 0.5f * fade, 0f, 3f, Pen.CYAN);
                for (int m = 1; m < 8; m++) {
                    double s = m / 8.0;
                    Vec3 mp = t0.add(t1.subtract(t0).scale(s));
                    double mh = (m & 1) == 0 ? 2.0 : 1.2;
                    p.ribbon(mp, mp.add(0, mh * railP, 0), 0.7, 0.45, 0.8f * (float) railP * fade, 0.8f * (float) railP * fade, 0f, 1f, Pen.CYAN);
                    p.ribbon(mp, mp.add(0, mh * railP, 0), 0.25, 0.15, 1f * (float) railP * fade, 1f * (float) railP * fade, 0f, 1f, Pen.WHITE);
                }
            }
        }

        // arches between neighbouring pillars (low gate arches + high arches) and ribs to the crown
        if (archP > 0.02) {
            for (int k = 0; k < N; k++) {
                arch(top(k, WH), top(k + 1, WH), 5.0, archP, 0.9);
                arch(top(k, PH - 3.0), top(k + 1, PH - 3.0), 8.0, archP, 1.0);
                rib(k);
            }
        }

        // streams of sacred light rising inside
        if (archP > 0.1) {
            Random r = new Random(seed * 17L + 5);
            for (int i = 0; i < 34; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = Math.sqrt(r.nextDouble()) * PR * 0.9;
                double len = 5.0 + r.nextDouble() * 9.0;
                double spd = 0.25 + r.nextDouble() * 0.45;
                double cyc = 34.0;
                double y = ((t * spd + r.nextDouble() * cyc) % cyc) - 4.0;
                float edge = (float) Math.sin(Math.PI * c01((y + 4.0) / cyc));
                double w = 0.25 + r.nextDouble() * 0.6;
                Vec3 q = C.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
                column(q, q.add(0, len, 0), w, w * 0.4, 0.6f * edge * (float) archP * fade, -t * 0.05f, -t * 0.05f + 2f, false);
            }
        }

        // counterattacks
        for (Fx f : strikes) {
            strikeBeam(f);
        }
    }

    private void arch(Vec3 a, Vec3 b, double rise, double reveal, double alphaMul) {
        int n = 18;
        double half = 0.5 * reveal;
        for (int side = 0; side < 2; side++) {
            Vec3 prev = null;
            for (int i = 0; i <= n; i++) {
                double s = side == 0 ? half * i / n : 1.0 - half + half * i / n;
                double e = 1.0 - Math.pow(Math.abs(2 * s - 1.0), 1.6);
                Vec3 pos = a.add(b.subtract(a).scale(s)).add(0, rise * e, 0);
                if (prev != null) {
                    float u = i * 0.3f - t * 0.04f;
                    float[][] cols = {Pen.BLUE, Pen.CYAN, Pen.CORE, Pen.WHITE};
                    double[] w = {1.5, 0.85, 0.4, 0.16};
                    float[] al = {0.3f, 0.55f, 0.9f, 1f};
                    for (int l = 0; l < 4; l++) {
                        p.ribbon(prev, pos, w[l], w[l], al[l] * (float) alphaMul * fade, al[l] * (float) alphaMul * fade, u, u + 0.3f, cols[l]);
                    }
                }
                prev = pos;
            }
        }
    }

    /** Rib from a pillar top, curving in and up to the crown. */
    private void rib(int k) {
        Vec3 P0 = top(k, PH);
        Vec3 P2 = C.add(0, CROWN, 0);
        Vec3 P1 = C.add((P0.x - C.x) * 0.45, PH + 9.0, (P0.z - C.z) * 0.45);
        int n = 22;
        double reveal = archP;
        Vec3 prev = null;
        for (int i = 0; i <= n * reveal; i++) {
            double s = i / (double) n;
            double q = 1 - s;
            Vec3 pos = P0.scale(q * q).add(P1.scale(2 * q * s)).add(P2.scale(s * s));
            if (prev != null) {
                float u = i * 0.3f - t * 0.05f;
                float[][] cols = {Pen.BLUE, Pen.CYAN, Pen.CORE, Pen.WHITE};
                double[] w = {1.2, 0.7, 0.32, 0.12};
                float[] al = {0.28f, 0.5f, 0.85f, 1f};
                for (int l = 0; l < 4; l++) {
                    p.ribbon(prev, pos, w[l], w[l], al[l] * fade, al[l] * fade, u, u + 0.3f, cols[l]);
                }
            }
            prev = pos;
        }
    }

    private void strikeBeam(Fx f) {
        float a = age(f);
        double life = Math.max(0.0, 1.0 - a / SanktZwingerEntity.STRIKE_LIFE);
        if (life <= 0.0) {
            return;
        }
        Vec3 o = top(f.pillar, PH + 1.0);
        Vec3 tg = f.pos;
        Random r = new Random(f.id * 7919L + (long) (a * 0.5f));
        int n = 12;
        Vec3 d = tg.subtract(o);
        Vec3 side = d.cross(Y);
        if (side.lengthSqr() < 1.0E-6) {
            side = X;
        }
        side = side.normalize();
        Vec3 up2 = d.cross(side).normalize();
        float punch = (float) Math.min(1.0, life * 1.6);
        Vec3 prev = o;
        for (int i = 1; i <= n; i++) {
            double s = i / (double) n;
            double env = Math.sin(Math.PI * s) * 2.4 * (0.3 + 0.7 * life);
            Vec3 pos = o.add(d.scale(s)).add(side.scale((r.nextDouble() - 0.5) * 2 * env)).add(up2.scale((r.nextDouble() - 0.5) * 2 * env));
            if (i == n) {
                pos = tg;
            }
            double wd = 3.4 * life;
            float u = i * 0.4f;
            p.ribbon(prev, pos, wd, wd, 0.55f * punch * fade, 0.55f * punch * fade, u, u + 0.4f, Pen.GOLD);
            p.ribbon(prev, pos, wd * 0.45, wd * 0.45, 0.9f * punch * fade, 0.9f * punch * fade, u, u + 0.4f, Pen.GOLDW);
            p.ribbon(prev, pos, wd * 0.16, wd * 0.16, 1f * punch * fade, 1f * punch * fade, u, u + 0.4f, Pen.WHITE);
            if (i == 5 || i == 8) {
                Vec3 br = pos.add(side.scale((r.nextDouble() - 0.5) * 7)).add(0, (r.nextDouble() - 0.7) * 5, 0).add(up2.scale((r.nextDouble() - 0.5) * 5));
                p.ribbon(pos, br, 1.0 * life, 0.1, 0.8f * punch * fade, 0f, 0f, 1f, Pen.GOLDW);
            }
            prev = pos;
        }
        // pillar of golden-white light at the victim
        double w = (6.0 * Math.pow(life, 0.7) + 1.0) * (a < 3 ? (0.5 + a / 6.0) : 1.0);
        Vec3 g0 = tg.add(0, -2.0, 0);
        Vec3 g1 = tg.add(0, 30.0, 0);
        column(g0, g1, w, w * 0.5, punch * fade, -t * 0.2f, -t * 0.2f + 5f, true);
    }

    // ------------------------------------------------------------------ RING

    private void ringPass() {
        // opening shockwaves
        for (int i = 0; i < 3; i++) {
            double f = c01((t - i * 6) / 36.0);
            if (f <= 0.0 || f >= 1.0) {
                continue;
            }
            double rad = SEAL * 1.15 * ease(f);
            p.plane(C.add(0, 0.1, 0), X, Z, rad, 0, Pen.CYAN, (float) (0.9 * (1.0 - f)) * fade);
            p.plane(C.add(0, 0.11, 0), X, Z, rad * 0.98, 0, Pen.WHITE, (float) (0.6 * (1.0 - f)) * fade);
        }
        // pillar eruption rings and fluting bands
        for (int k = 0; k < N; k++) {
            double st = SanktZwingerEntity.PILLAR_START + k * SanktZwingerEntity.PILLAR_STAGGER;
            double f = c01((t - st) / 24.0);
            Vec3 b = base(k);
            if (f > 0.0 && f < 1.0) {
                p.plane(b.add(0, 0.15, 0), X, Z, 2.0 + 9.0 * ease(f), 0, Pen.CYAN, (float) (0.9 * (1.0 - f)) * fade);
            }
            double h = PH * pr[k];
            for (int i = 0; i < 5; i++) {
                double y = 3.0 + i * 5.0;
                if (y > h) {
                    break;
                }
                float pls = 0.6f + 0.4f * (float) Math.sin(t * 0.12 - i * 0.9 + k);
                p.plane(b.add(0, y, 0), X, Z, 1.9 + 0.2 * pls, ((i & 1) == 0 ? 1 : -1) * t * 0.02, Pen.CYAN, 0.8f * pls * fade);
            }
            Vec3 tp = b.add(0, h, 0);
            if (pr[k] > 0.8) {
                p.plane(tp, X, Z, 3.0 + 0.4 * Math.sin(t * 0.1 + k), t * 0.05, Pen.CORE, 0.7f * fade);
            }
        }
        // barrier ripples: expanding ring on the wall plane
        for (Fx f : ripples) {
            float a = age(f);
            double life = 1.0 - a / SanktZwingerEntity.RIPPLE_LIFE;
            if (life <= 0) {
                continue;
            }
            Vec3 n = new Vec3(f.pos.x - C.x, 0, f.pos.z - C.z).normalize();
            Vec3 tan = new Vec3(-n.z, 0, n.x);
            double rad = 1.0 + a * 0.75;
            p.plane(f.pos.add(n.scale(0.05)), tan, Y, rad, 0, Pen.CYAN, (float) (0.9 * life) * fade);
            p.plane(f.pos.add(n.scale(0.06)), tan, Y, rad * 0.55, 0, Pen.WHITE, (float) (0.7 * life) * fade);
        }
        // counterattack shock rings
        for (Fx f : strikes) {
            float a = age(f);
            double life = 1.0 - a / SanktZwingerEntity.STRIKE_LIFE;
            if (life <= 0) {
                continue;
            }
            Vec3 g = f.pos.multiply(1, 0, 1).add(0, C.y + 0.12, 0);
            p.plane(g, X, Z, 1.0 + a * 0.9, 0, Pen.GOLD, (float) life * fade);
            p.plane(g.add(0, 0.01, 0), X, Z, 0.6 + a * 0.7, 0, Pen.GOLDW, (float) (0.8 * life) * fade);
            p.plane(f.pos, X, Z, 1.0 + a * 0.5, a * 0.1, Pen.GOLDW, (float) (0.6 * life) * fade);
        }
    }

    // ------------------------------------------------------------------ HEX barrier lattice

    private void hexPass() {
        if (wallP <= 0.02) {
            return;
        }
        for (int k = 0; k < N; k++) {
            Vec3 b0 = base(k);
            Vec3 b1 = base(k + 1);
            Vec3 chord = b1.subtract(b0);
            Vec3 T = chord.scale(0.25 * 1.18);
            Vec3 U = Y.scale(WH / 6.0 * 1.18);
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 3; j++) {
                    double rv = c01(wallP * 3.0 - j);
                    if (rv <= 0.01) {
                        continue;
                    }
                    Vec3 c = b0.add(chord.scale((i + 0.5) / 2.0)).add(0, (j + 0.5) * WH / 3.0, 0);
                    double lit = 0.0;
                    for (Fx f : ripples) {
                        float a = age(f);
                        double dist = c.distanceTo(f.pos);
                        double life = 1.0 - a / SanktZwingerEntity.RIPPLE_LIFE;
                        if (life <= 0) {
                            continue;
                        }
                        double dv = (dist - a * 0.75) / 3.0;
                        lit += Math.exp(-dv * dv) * life + Math.exp(-(dist * dist) / 14.0) * Math.max(0.0, 1.0 - a / 12.0);
                    }
                    for (Fx f : strikes) {
                        if (f.pillar == k || f.pillar == (k + 1) % N) {
                            lit += 0.35 * Math.max(0.0, 1.0 - age(f) / 14.0);
                        }
                    }
                    float shimmer = 0.85f + 0.15f * (float) Math.sin(t * 0.09 + k * 1.7 + i * 2.1 + j);
                    float base = 0.17f * shimmer * (float) rv;
                    float aa = (float) Math.min(1.2, base + lit) * fade;
                    float[] col = lit > 0.5 ? Pen.WHITE : Pen.CYAN;
                    p.quad(c.subtract(T).subtract(U), c.add(T).subtract(U), c.add(T).add(U), c.subtract(T).add(U), 0f, 1f, 1f, 0f, col, aa);
                    if (lit > 0.15) {
                        p.quad(c.subtract(T).subtract(U), c.add(T).subtract(U), c.add(T).add(U), c.subtract(T).add(U), 0f, 1f, 1f, 0f, Pen.CORE, (float) Math.min(1.0, lit * 0.6) * fade);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ WHEEL capitals and crown

    private void wheelPass() {
        for (int k = 0; k < N; k++) {
            double s = c01((pr[k] - 0.55) / 0.45);
            if (s <= 0.01) {
                continue;
            }
            double sc = ease(s);
            Vec3 c = top(k, PH).add(0, 2.2 * sc, 0);
            double half = 4.2 * sc;
            float pls = 0.9f + 0.1f * (float) Math.sin(t * 0.1 + k);
            p.billboard(c, half * 1.12, 0, Pen.BLUE, 0.7f * fade * pls, 0f, 1f, 1f, 0f);
            p.billboard(c, half, 0, Pen.CYAN, 0.95f * fade, 0f, 1f, 1f, 0f);
            p.billboard(c, half * 0.98, 0, Pen.CORE, 0.6f * fade, 0f, 1f, 1f, 0f);
            if (flash[k] > 0.01f) {
                p.billboard(c, half * (1.0 + 0.3 * flash[k]), 0, Pen.GOLD, 0.9f * flash[k] * fade, 0f, 1f, 1f, 0f);
                p.billboard(c, half, 0, Pen.GOLDW, 1.0f * flash[k] * fade, 0f, 1f, 1f, 0f);
            }
        }
        if (archP > 0.1) {
            Vec3 c = C.add(0, CROWN + 3.0, 0);
            double half = 7.0 * archP;
            p.billboard(c, half * 1.1, 0, Pen.BLUE, 0.7f * fade, 0f, 1f, 1f, 0f);
            p.billboard(c, half, 0, Pen.CYAN, 0.95f * fade, 0f, 1f, 1f, 0f);
            p.billboard(c, half, 0, Pen.CORE, 0.6f * fade, 0f, 1f, 1f, 0f);
        }
    }

    // ------------------------------------------------------------------ CROSS ornaments

    private void crossPass() {
        if (archP > 0.05) {
            double half = 9.5 * archP;
            p.billboard(C.add(0, CROWN + 3.0, 0), half, t * 0.012, Pen.BLUE, 0.35f * fade);
            p.billboard(C.add(0, CROWN + 3.0, 0), half * 0.9, t * 0.012, Pen.CYAN, 0.45f * fade);
            p.billboard(C.add(0, CROWN + 3.0, 0), half * 0.9, t * 0.012, Pen.WHITE, 0.2f * fade);
        }
        double rv = prog(SanktZwingerEntity.ARCH_START + 6, 24);
        if (rv < 0.02) {
            return;
        }
        Random r = new Random(seed * 53L + 11);
        for (int i = 0; i < 18; i++) {
            double dir = (i & 1) == 0 ? 1 : -1;
            double ang = i * Math.PI * 2 / 18 + t * 0.007 * dir + r.nextDouble();
            double rad = PR * (0.25 + 0.65 * r.nextDouble());
            double y = 3.0 + r.nextDouble() * 20.0 + Math.sin(t * 0.05 + i) * 0.9;
            double half = (0.8 + r.nextDouble() * 0.9) * rv;
            float tw = 0.7f + 0.3f * (float) Math.sin(t * 0.13 + i * 1.3);
            Vec3 pos = C.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
            double rot = t * 0.03 * dir + i;
            p.billboard(pos, half * 1.6, rot, Pen.BLUE, 0.4f * tw * fade);
            p.billboard(pos, half, rot, Pen.CYAN, 0.95f * tw * fade);
            p.billboard(pos, half * 0.9, rot, Pen.WHITE, 0.55f * tw * fade);
        }
    }

    // ------------------------------------------------------------------ SPARKLE

    private void sparklePass() {
        Random r = new Random(seed * 97L + 3);
        for (int i = 0; i < 90; i++) {
            int k = r.nextInt(N);
            double hh = r.nextDouble();
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 0.6 + r.nextDouble() * 2.6;
            double size = 0.2 + r.nextDouble() * 0.45;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.15 + r.nextDouble() * 0.3) + r.nextDouble() * 6.28);
            double y = ((hh * PH * 1.2 + t * (0.05 + r.nextDouble() * 0.12)) % (PH * 1.2));
            if (y > PH * pr[k]) {
                continue;
            }
            Vec3 pos = base(k).add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
            p.billboard(pos, size * (0.6 + 0.6 * tw), r.nextDouble() * 1.57 + t * 0.02, (i & 1) == 0 ? Pen.WHITE : Pen.CYAN, 0.95f * tw * fade);
        }
        if (t < 60) {
            for (int i = 0; i < 40; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = r.nextDouble() * SEAL * prog(0, SanktZwingerEntity.EXPAND);
                double y = 0.2 + ((t * (0.1 + r.nextDouble() * 0.2) + r.nextDouble() * 6) % 6.0);
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 0.3 + i);
                p.billboard(C.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad), 0.3 + 0.25 * tw, i, Pen.WHITE, 0.9f * tw * fade);
            }
        }
        for (Fx f : strikes) {
            float a = age(f);
            double life = 1.0 - a / SanktZwingerEntity.STRIKE_LIFE;
            if (life <= 0) {
                continue;
            }
            Random rr = new Random(f.id * 31L);
            for (int i = 0; i < 16; i++) {
                double ang = rr.nextDouble() * Math.PI * 2;
                double sp = 0.2 + rr.nextDouble() * 0.5;
                double up = (rr.nextDouble() - 0.2) * 0.4;
                Vec3 pos = f.pos.add(Math.cos(ang) * sp * a, up * a, Math.sin(ang) * sp * a);
                p.billboard(pos, 0.5 * life + 0.1, i + a * 0.2, i % 3 == 0 ? Pen.WHITE : Pen.GOLD, (float) life * fade);
            }
        }
    }

    // ------------------------------------------------------------------ BURST flip-book

    private void burstPass() {
        for (Fx f : strikes) {
            float a = age(f);
            double fr = a / (SanktZwingerEntity.STRIKE_LIFE * 0.7);
            if (fr >= 1.0) {
                continue;
            }
            int fi = (int) (fr * 16);
            float u0 = (fi % 4) / 4f;
            float v0 = (fi / 4) / 4f;
            p.billboard(f.pos, 6.5, f.id, Pen.GOLD, 0.95f * fade, u0, v0, u0 + 0.25f, v0 + 0.25f);
            p.billboard(f.pos, 4.0, f.id, Pen.GOLDW, 0.9f * fade, u0, v0, u0 + 0.25f, v0 + 0.25f);
            if (a < 10) {
                int pf = (int) (a / 10.0 * 16);
                float pu = (pf % 4) / 4f;
                float pv = (pf / 4) / 4f;
                p.billboard(top(f.pillar, PH + 1.0), 5.0, 0, Pen.GOLDW, 0.9f * fade, pu, pv, pu + 0.25f, pv + 0.25f);
            }
        }
        for (Fx f : ripples) {
            float a = age(f);
            if (a >= 12) {
                continue;
            }
            int fi = (int) (a / 12.0 * 16);
            float u0 = (fi % 4) / 4f;
            float v0 = (fi / 4) / 4f;
            p.billboard(f.pos, 3.2, 0, Pen.CYAN, 0.9f * fade, u0, v0, u0 + 0.25f, v0 + 0.25f);
            p.billboard(f.pos, 2.0, 0, Pen.WHITE, 0.8f * fade, u0, v0, u0 + 0.25f, v0 + 0.25f);
        }
    }
}
