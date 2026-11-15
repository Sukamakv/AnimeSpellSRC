package com.example.jjkspells.yhwach.client;

import com.example.jjkspells.yhwach.entity.AuswaehlenEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Draws "Auswählen" entirely client-side (no weather or world change): a translucent dusk dome over the sky, the gigantic
 * golden insignia, marks over each target, descending pillars of light with rotating rings, sacred inscriptions and rising
 * Reishi fragments, energy streams that gather into an enormous halo behind the caster, and the halo's collapse into the caster.
 */
public class AuswaehlenRenderer extends EntityRenderer<AuswaehlenEntity> {
    private static final int MAXN = 12;
    private static final double SKY_H = 110.0;
    private static final double SKY_R = 105.0;
    private static final double COL_W = 8.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private float pt;
    private int seed;
    private Vec3 C;
    private double skyY;
    private double skyA;
    private int n;
    private final Vec3[] tp = new Vec3[MAXN];
    private final double[] th = new double[MAXN];
    private final double[] drop = new double[MAXN];
    private final double[] headY = new double[MAXN];
    private double pil;
    private Vec3 haloC;
    private Vec3 haloR;
    private double haloS;
    private double haloA;
    private double haloBright;
    private double collapse;

    public AuswaehlenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(AuswaehlenEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(AuswaehlenEntity e) {
        return YhwachRender.GLOW;
    }

    // ------------------------------------------------------------------ helpers

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private static double smooth(double x) {
        x = c01(x);
        return x * x * (3.0 - 2.0 * x);
    }

    private void use(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(YhwachRender.additive(tex));
    }

    private void glow(Vec3 c, double half, float[] col, float a) {
        p.billboard(c, half, 0, col, a);
    }

    private void layer(Vec3 c, Vec3 A, Vec3 B, double half, double rot, float a) {
        if (a <= 0.003f || half <= 0.01) {
            return;
        }
        p.plane(c, A, B, half, rot, Pen.GOLD, a * 0.6f);
        p.plane(c, A, B, half, rot, Pen.GOLDW, a);
        p.plane(c, A, B, half, rot, Pen.WHITE, a * 0.35f);
    }

    private void gcol(Vec3 a, Vec3 b, double wa, double wb, float al, float u0, float u1) {
        if (al <= 0.003f) {
            return;
        }
        float[][] cols = {Pen.GOLD, Pen.GOLDW, Pen.GOLDW, Pen.WHITE};
        double[] mul = {1.0, 0.58, 0.27, 0.11};
        float[] am = {0.35f, 0.6f, 0.9f, 1.0f};
        for (int l = 0; l < 4; l++) {
            p.ribbon(a, b, wa * mul[l], wb * mul[l], am[l] * al, am[l] * al, u0, u1, cols[l]);
        }
    }

    private double start(int i) {
        return AuswaehlenEntity.PILLAR0 + i * AuswaehlenEntity.PILLAR_STAGGER;
    }

    private double landAge(int i) {
        return t - (start(i) + AuswaehlenEntity.DESCEND);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(AuswaehlenEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        pt = partial;
        t = e.tickCount - 1 + partial;
        seed = e.getId();
        C = e.ownerPos(partial);
        n = Math.min(MAXN, e.targetCount());
        double in = smooth(t / AuswaehlenEntity.SKY_IN);
        double out = 1.0 - c01((t - AuswaehlenEntity.SKY_OUT0) / (double) (AuswaehlenEntity.SKY_OUT1 - AuswaehlenEntity.SKY_OUT0));
        skyA = in * out;
        if (out <= 0.0) {
            return;
        }
        skyY = C.y + SKY_H;
        pil = 1.0 - c01((t - (AuswaehlenEntity.PILLAR_END - 14)) / 14.0);
        for (int i = 0; i < n; i++) {
            tp[i] = e.targetPos(i, partial);
            th[i] = e.targetHeight(i);
            double d = c01((t - start(i)) / AuswaehlenEntity.DESCEND);
            drop[i] = d;
            headY[i] = skyY + (tp[i].y - skyY) * d * d;
        }
        Vec3 hd = e.haloDir != null ? e.haloDir : (e.castDir != null ? e.castDir : Z);
        double g = ease((t - AuswaehlenEntity.HALO0) / 40.0);
        collapse = smooth((t - AuswaehlenEntity.COLLAPSE0) / (double) (AuswaehlenEntity.COLLAPSE_END - AuswaehlenEntity.COLLAPSE0));
        double kk = collapse * collapse;
        Vec3 back = hd.scale(-8.0);
        Vec3 hc = C.add(back).add(0, 11.0, 0);
        Vec3 chest = C.add(0, 1.3, 0);
        haloC = hc.add(chest.subtract(hc).scale(kk));
        haloR = new Vec3(-hd.z, 0, hd.x);
        haloS = 17.0 * g * (1.0 - kk) * (1.0 + 0.03 * Math.sin(t * 0.3));
        haloA = t >= AuswaehlenEntity.COLLAPSE_END ? 0.0 : g;
        double arrive = 0;
        for (int i = 0; i < n; i++) {
            arrive += c01((t - (AuswaehlenEntity.FLOW0 + i * 2 + 30)) / 12.0);
        }
        haloBright = n == 0 ? 0.7 : 0.45 + 0.55 * arrive / n;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), e.getPosition(partial), camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        dome(buf, camera.getPosition());
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
        use(buf, YhwachRender.GLYPH);
        glyphPass();
        use(buf, YhwachRender.STREAK);
        streakPass();
        use(buf, YhwachRender.RING);
        ringPass();
        use(buf, YhwachRender.CROSS);
        crossPass();
        use(buf, YhwachRender.WHEEL);
        wheelPass();
        use(buf, YhwachRender.SPARKLE);
        sparklePass();
        use(buf, YhwachRender.BURST);
        burstPass();
    }

    // ------------------------------------------------------------------ sky dome (alpha-blended, client only)

    private void dv(VertexConsumer vc, Vec3 cam, double el, double az, float r, float g, float b, float a) {
        double ce = Math.cos(el);
        double R = 180.0;
        double x = cam.x + Math.cos(az) * ce * R;
        double y = cam.y + Math.sin(el) * R;
        double z = cam.z + Math.sin(az) * ce * R;
        vc.vertex(p.m, (float) (x - p.org.x), (float) (y - p.org.y), (float) (z - p.org.z)).color(r, g, b, Math.max(0f, Math.min(1f, a))).endVertex();
    }

    private void dome(MultiBufferSource buf, Vec3 cam) {
        if (skyA <= 0.003) {
            return;
        }
        VertexConsumer vc = buf.getBuffer(YhwachRender.dim());
        int lat = 9;
        int lon = 32;
        double e0 = Math.toRadians(-6);
        double span = Math.toRadians(96);
        for (int a = 0; a < lat; a++) {
            double el0 = e0 + span * a / lat;
            double el1 = e0 + span * (a + 1) / lat;
            double f0 = c01(Math.toDegrees(el0) / 90.0);
            double f1 = c01(Math.toDegrees(el1) / 90.0);
            float[] c0 = domeColor(f0);
            float[] c1 = domeColor(f1);
            for (int b = 0; b < lon; b++) {
                double az0 = Math.PI * 2 * b / lon;
                double az1 = Math.PI * 2 * (b + 1) / lon;
                dv(vc, cam, el0, az0, c0[0], c0[1], c0[2], c0[3]);
                dv(vc, cam, el0, az1, c0[0], c0[1], c0[2], c0[3]);
                dv(vc, cam, el1, az1, c1[0], c1[1], c1[2], c1[3]);
                dv(vc, cam, el1, az0, c1[0], c1[1], c1[2], c1[3]);
            }
        }
    }

    /** Warm gold at the horizon, deep violet at the zenith. */
    private float[] domeColor(double f) {
        double s = Math.sqrt(f);
        float r = (float) (0.60 + (0.05 - 0.60) * s);
        float g = (float) (0.36 + (0.03 - 0.36) * s);
        float b = (float) (0.10 + (0.17 - 0.10) * s);
        float a = (float) (skyA * (0.30 + 0.38 * f));
        return new float[]{r, g, b, a};
    }

    // ------------------------------------------------------------------ GLOW

    private void glowPass() {
        float sa = (float) skyA;
        Vec3 sc = new Vec3(C.x, skyY, C.z);
        glow(sc, 80.0 * smooth(t / 40.0), Pen.GOLD, 0.22f * sa);
        glow(sc, 40.0 * smooth(t / 40.0), Pen.GOLDW, 0.25f * sa);
        for (int i = 0; i < n; i++) {
            Vec3 g = tp[i];
            double ma = ease((t - (AuswaehlenEntity.MARK0 + i * AuswaehlenEntity.MARK_STAGGER)) / 8.0);
            if (ma > 0.01 && t < AuswaehlenEntity.PILLAR_END) {
                Vec3 mk = g.add(0, th[i] + 3.4, 0);
                glow(mk, 3.4 * ma, Pen.GOLD, 0.6f * (float) (ma * pil));
                glow(mk, 1.5 * ma, Pen.WHITE, 0.9f * (float) (ma * pil));
            }
            double d = drop[i];
            if (d > 0.0 && pil > 0.0) {
                Vec3 head = new Vec3(g.x, headY[i], g.z);
                glow(head, 11.0, Pen.GOLD, 0.5f * (float) pil);
                glow(head, 5.0, Pen.WHITE, 0.8f * (float) pil);
                double la = landAge(i);
                if (la >= 0) {
                    float pl = (float) (pil * (0.45 + 0.1 * Math.sin(t * 0.2 + i)));
                    p.plane(g.add(0, 0.1, 0), X, Z, 12.0, 0, Pen.GOLD, pl * 0.7f);
                    p.plane(g.add(0, 0.11, 0), X, Z, 5.0, 0, Pen.GOLDW, pl);
                    double k = c01(la / 20.0);
                    if (k < 1.0) {
                        glow(g.add(0, 1.5, 0), 20.0 * (1.0 - k) + 4.0, Pen.GOLD, (float) (1.0 - k));
                        glow(g.add(0, 1.5, 0), 9.0 * (1.0 - k) + 2.0, Pen.WHITE, (float) (1.0 - k));
                    }
                }
            }
            double ra = t - AuswaehlenEntity.RECLAIM;
            if (ra >= 0 && ra < 22) {
                double k = ra / 22.0;
                glow(g.add(0, th[i] * 0.5, 0), 12.0 * (1.0 - k) + 2.0, Pen.GOLDW, (float) (1.0 - k));
            }
            double fl = t - (AuswaehlenEntity.FLOW0 + i * 2);
            if (fl > 0 && fl < 40) {
                glow(g.add(0, th[i] * 0.5, 0), 4.0, Pen.GOLDW, (float) (0.8 * Math.sin(Math.PI * fl / 40.0)));
            }
        }
        if (haloA > 0.01 && haloS > 0.1) {
            float ha = (float) (haloA * haloBright);
            glow(haloC, haloS * 1.9, Pen.GOLD, 0.30f * ha);
            glow(haloC, haloS * 1.0, Pen.GOLDW, 0.35f * ha);
            glow(haloC, haloS * 0.35, Pen.WHITE, 0.55f * ha);
        }
        double fa = t - AuswaehlenEntity.COLLAPSE_END;
        if (fa >= 0 && fa < 30) {
            double k = fa / 30.0;
            Vec3 ch = C.add(0, 1.3, 0);
            glow(ch, 26.0 * (1.0 - k) + 3.0, Pen.GOLD, (float) (1.0 - k));
            glow(ch, 12.0 * (1.0 - k) + 1.5, Pen.WHITE, (float) (1.0 - k));
        }
    }

    // ------------------------------------------------------------------ INSIGNIA LAYERS (sky, ground seals, pillar rings, halo)

    private Vec3 skyCenter() {
        return new Vec3(C.x, skyY, C.z);
    }

    private void insBase() {
        double s = ease(t / 40.0);
        layer(skyCenter(), X, Z, SKY_R * s, 0, (float) skyA);
        for (int i = 0; i < n; i++) {
            double d = drop[i];
            if (d > 0 && pil > 0) {
                layer(tp[i].add(0, 0.14, 0), X, Z, 9.0 * ease(d * 2.0), 0, (float) pil * 0.9f);
            }
        }
        if (haloA > 0.01) {
            layer(haloC, haloR, Y, haloS, 0, (float) (haloA * haloBright));
        }
    }

    private void insOuter() {
        double s = ease((t - 4.0) / 40.0);
        layer(skyCenter().add(0, 0.1, 0), X, Z, SKY_R * 0.99 * s, t * 0.004, (float) skyA);
        layer(skyCenter().add(0, 0.2, 0), X, Z, SKY_R * 1.18 * s, -t * 0.002, (float) skyA * 0.55f);
        for (int i = 0; i < n; i++) {
            double d = drop[i];
            if (d <= 0 || pil <= 0) {
                continue;
            }
            Vec3 g = tp[i];
            layer(g.add(0, 0.15, 0), X, Z, 10.5 * ease(d * 2.0), t * 0.02, (float) pil * 0.85f);
            // rings that climb the pillar
            double top = Math.max(0.0, headY[i] - g.y);
            for (int k = 0; k < 6; k++) {
                double y = ((t * 0.55 + k * 11.0) % 66.0);
                if (y > top) {
                    continue;
                }
                float fa = (float) (Math.sin(Math.PI * y / 66.0) * pil);
                layer(g.add(0, y, 0), X, Z, 4.6, ((k & 1) == 0 ? 1 : -1) * t * 0.05, 0.9f * fa);
            }
        }
        if (haloA > 0.01) {
            layer(haloC, haloR, Y, haloS * 0.98, t * 0.008 * (1.0 + 4.0 * collapse), (float) (haloA * haloBright));
        }
    }

    private void insMid() {
        double s = ease((t - 8.0) / 40.0);
        layer(skyCenter().add(0, 0.3, 0), X, Z, SKY_R * 0.72 * s, -t * 0.007, (float) skyA);
        for (int i = 0; i < n; i++) {
            double d = drop[i];
            if (d > 0 && pil > 0) {
                layer(tp[i].add(0, 0.16, 0), X, Z, 7.0 * ease(d * 2.0), -t * 0.035, (float) pil * 0.9f);
            }
        }
        if (haloA > 0.01) {
            layer(haloC, haloR, Y, haloS * 0.72, -t * 0.016 * (1.0 + 4.0 * collapse), (float) (haloA * haloBright));
        }
    }

    private void insCore() {
        double s = ease((t - 12.0) / 40.0);
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.12);
        layer(skyCenter().add(0, 0.4, 0), X, Z, SKY_R * 0.38 * s, t * 0.01, (float) skyA * pulse);
        for (int i = 0; i < n; i++) {
            double d = drop[i];
            if (d > 0 && pil > 0) {
                layer(tp[i].add(0, 0.17, 0), X, Z, 3.6 * ease(d * 2.0), t * 0.07, (float) pil);
            }
        }
        if (haloA > 0.01) {
            layer(haloC, haloR, Y, haloS * 0.38, t * 0.03 * (1.0 + 4.0 * collapse), (float) (haloA * haloBright) * pulse);
        }
    }

    // ------------------------------------------------------------------ GLYPH inscriptions inside the pillars

    private void glyphPass() {
        for (int i = 0; i < n; i++) {
            if (drop[i] <= 0 || pil <= 0) {
                continue;
            }
            Vec3 g = tp[i];
            Vec3 bot = new Vec3(g.x, headY[i], g.z);
            Vec3 top = new Vec3(g.x, skyY, g.z);
            double len = top.y - bot.y;
            double th0 = t * 0.025 + i;
            float u0 = -t * 0.012f;
            for (int s = 0; s < 2; s++) {
                double ang = th0 + s * Math.PI / 2;
                Vec3 side = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                p.ribbonSide(bot, top, side, 6.4, 6.4, (float) (0.7 * pil), (float) (0.7 * pil), u0, u0 + (float) (len / 52.0 * 1.8), Pen.GOLD);
                p.ribbonSide(bot, top, side, 6.0, 6.0, (float) (0.55 * pil), (float) (0.55 * pil), u0, u0 + (float) (len / 52.0 * 1.8), Pen.WHITE);
            }
        }
    }

    // ------------------------------------------------------------------ STREAK (pillars, streams, spikes)

    private void streakPass() {
        float sa = (float) skyA;
        // marks: thin selection beams
        for (int i = 0; i < n; i++) {
            double ma = ease((t - (AuswaehlenEntity.MARK0 + i * AuswaehlenEntity.MARK_STAGGER)) / 8.0);
            if (ma > 0.01 && t < AuswaehlenEntity.PILLAR_END) {
                Vec3 mk = tp[i].add(0, th[i] + 3.4, 0);
                p.ribbon(mk, tp[i].add(0, th[i] * 0.8, 0), 0.5, 0.15, (float) (0.8 * ma * pil), (float) (0.3 * ma * pil), 0f, 1f, Pen.GOLDW);
            }
        }
        // pillars of light
        for (int i = 0; i < n; i++) {
            if (drop[i] <= 0 || pil <= 0) {
                continue;
            }
            Vec3 g = tp[i];
            Vec3 bot = new Vec3(g.x, headY[i], g.z);
            Vec3 top = new Vec3(g.x, skyY, g.z);
            double la = landAge(i);
            double flare = la >= 0 && la < 10 ? 1.0 + 1.3 * (1.0 - la / 10.0) : 1.0;
            double w = COL_W * flare * (1.0 + 0.05 * Math.sin(t * 0.3 + i));
            float u0 = -t * 0.06f;
            gcol(bot, top, w, w * 1.15, (float) pil, u0, u0 + (float) ((top.y - bot.y) / 10.0));
            // bright head while descending
            if (la < 0) {
                p.ribbon(bot.add(0, 6, 0), bot.add(0, -2, 0), 12.0, 5.0, 0.6f * (float) pil, 0.9f * (float) pil, 0f, 1f, Pen.GOLDW);
            }
            if (la >= 0) {
                landing(i, la);
            }
        }
        // streams to the halo
        for (int i = 0; i < n; i++) {
            stream(i);
        }
        // halo rays
        if (haloA > 0.01 && haloS > 0.5) {
            float ha = (float) (haloA * haloBright);
            for (int k = 0; k < 24; k++) {
                double ang = k * Math.PI * 2 / 24 + t * 0.01;
                double len = haloS * (0.25 + 0.2 * (0.5 + 0.5 * Math.sin(t * 0.2 + k * 2.3)));
                Vec3 d = haloR.scale(Math.cos(ang)).add(Y.scale(Math.sin(ang)));
                Vec3 a = haloC.add(d.scale(haloS * 1.0));
                Vec3 b = haloC.add(d.scale(haloS + len));
                p.ribbon(a, b, 1.6 * (haloS / 17.0), 0.1, 0.85f * ha, 0f, 0f, 1f, Pen.GOLD);
                p.ribbon(a, b, 0.5 * (haloS / 17.0), 0.05, 1.0f * ha, 0f, 0f, 1f, Pen.WHITE);
            }
        }
        // reclamation: pull lines from the targets into the caster, and the final burst spikes
        double ra = t - AuswaehlenEntity.RECLAIM;
        if (ra >= 0 && ra < 14) {
            double k = ra / 14.0;
            Vec3 ch = C.add(0, 1.3, 0);
            for (int i = 0; i < n; i++) {
                Vec3 g = tp[i].add(0, th[i] * 0.5, 0);
                p.ribbon(g, ch, 2.5 * (1.0 - k), 0.5, (float) (1.0 - k), (float) (1.0 - k), -t * 0.2f, -t * 0.2f + 6f, Pen.GOLDW);
                p.ribbon(g, ch, 0.8 * (1.0 - k), 0.2, (float) (1.0 - k), (float) (1.0 - k), -t * 0.2f, -t * 0.2f + 6f, Pen.WHITE);
            }
        }
        double fa = t - AuswaehlenEntity.COLLAPSE_END;
        if (fa >= 0 && fa < 30) {
            double k = fa / 30.0;
            Vec3 ch = C.add(0, 1.3, 0);
            Random r = new Random(seed * 53L);
            for (int j = 0; j < 20; j++) {
                double ang = j * Math.PI * 2 / 20 + r.nextDouble() * 0.2;
                double el = (r.nextDouble() - 0.4) * 1.2;
                Vec3 d = new Vec3(Math.cos(ang) * Math.cos(el), Math.sin(el), Math.sin(ang) * Math.cos(el));
                double len = (8.0 + r.nextDouble() * 14.0) * ease(fa / 10.0);
                p.ribbon(ch.add(d.scale(1.5)), ch.add(d.scale(1.5 + len)), 1.4 * (1.0 - k), 0.1, (float) (1.0 - k), 0f, 0f, 1f, Pen.GOLD);
                p.ribbon(ch.add(d.scale(1.5)), ch.add(d.scale(1.5 + len)), 0.4 * (1.0 - k), 0.05, (float) (1.0 - k), 0f, 0f, 1f, Pen.WHITE);
            }
        }
        // sky: faint rays from the insignia to its edge
        if (sa > 0.01) {
            Vec3 sc = skyCenter();
            for (int k = 0; k < 16; k++) {
                double ang = k * Math.PI * 2 / 16 + t * 0.003;
                Vec3 e = sc.add(Math.cos(ang) * SKY_R * 1.2, 0, Math.sin(ang) * SKY_R * 1.2);
                p.ribbon(sc, e, 3.0, 0.5, 0.3f * sa, 0f, 0f, 1f, Pen.GOLD);
            }
        }
    }

    private void landing(int i, double la) {
        if (la > 34) {
            return;
        }
        double k = c01(la / 34.0);
        Vec3 g = tp[i];
        Random r = new Random(seed * 71L + i * 7L);
        double sp = ease(la / 10.0);
        for (int j = 0; j < 14; j++) {
            double ang = j * Math.PI * 2 / 14 + r.nextDouble() * 0.2;
            double len = (9.0 + r.nextDouble() * 12.0) * sp;
            double r0 = 2.0;
            Vec3 q0 = g.add(Math.cos(ang) * r0, 0.25, Math.sin(ang) * r0);
            Vec3 q1 = g.add(Math.cos(ang) * (r0 + len), 0.25, Math.sin(ang) * (r0 + len));
            p.ribbon(q0, q1, 1.5 * (1.0 - k), 0.1, (float) (1.0 - k), 0f, 0f, 1f, Pen.GOLD);
            p.ribbon(q0, q1, 0.4 * (1.0 - k), 0.05, (float) (1.0 - k), 0f, 0f, 1f, Pen.WHITE);
        }
        for (int j = 0; j < 14; j++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double sh = 0.3 + r.nextDouble() * 0.7;
            double vy = 0.6 + r.nextDouble() * 0.9;
            Vec3 q = g.add(Math.cos(ang) * sh * la, vy * la - 0.04 * la * la, Math.sin(ang) * sh * la);
            Vec3 v = new Vec3(Math.cos(ang) * sh, vy - 0.08 * la, Math.sin(ang) * sh).normalize();
            p.ribbon(q, q.add(v.scale(1.6)), 0.5, 0.05, (float) (1.0 - k), 0f, 0f, 1f, j % 2 == 0 ? Pen.WHITE : Pen.GOLD);
        }
    }

    private void stream(int i) {
        double ts = AuswaehlenEntity.FLOW0 + i * 2;
        double hp = c01((t - ts) / 40.0);
        double tl = c01((t - ts - 22) / 46.0);
        if (hp <= 0.0 || tl >= 1.0) {
            return;
        }
        Vec3 s0 = tp[i].add(0, th[i] * 0.5, 0);
        Vec3 p1 = tp[i].add(0, 34.0, 0);
        Vec3 H = haloC;
        Vec3 side = new Vec3(Math.cos(i * 2.399), 0, Math.sin(i * 2.399));
        Vec3 p2 = p1.add(H).scale(0.5).add(0, 20.0, 0).add(side.scale(6.0));
        int S = 40;
        Vec3 prev = null;
        for (int j = 0; j <= S; j++) {
            double s = j / (double) S;
            if (s > hp || s < tl) {
                prev = null;
                continue;
            }
            Vec3 pos;
            if (s < 0.35) {
                pos = s0.add(p1.subtract(s0).scale(s / 0.35));
            } else {
                double u = (s - 0.35) / 0.65;
                double q = 1.0 - u;
                pos = p1.scale(q * q).add(p2.scale(2 * q * u)).add(H.scale(u * u));
            }
            if (prev != null) {
                double w = 1.5 - 0.9 * s;
                float al = (float) (0.55 + 0.45 * Math.sin(j * 0.8 - t * 0.7)) * (float) Math.min(1.0, (s - tl) * 8.0) * (float) Math.min(1.0, (hp - s) * 3.0 + 0.4);
                p.ribbon(prev, pos, w * 1.7, 0.5f * al * 0.6f, 0f, 1f, Pen.GOLD);
                p.ribbon(prev, pos, w, 0.9f * al, 0f, 1f, Pen.GOLDW);
                p.ribbon(prev, pos, w * 0.35, 1.0f * al, 0f, 1f, Pen.WHITE);
            }
            prev = pos;
        }
    }

    // ------------------------------------------------------------------ RING

    private void ringPass() {
        for (int i = 0; i < n; i++) {
            double la = landAge(i);
            if (la >= 0 && la < 40) {
                Vec3 g = tp[i].add(0, 0.2, 0);
                for (int r = 0; r < 3; r++) {
                    double k = c01((la - r * 4.0) / 28.0);
                    if (k <= 0.0 || k >= 1.0) {
                        continue;
                    }
                    double rad = 22.0 * ease(k);
                    p.plane(g, X, Z, rad, 0, Pen.GOLD, (float) (1.0 - k));
                    p.plane(g.add(0, 0.01, 0), X, Z, rad * 0.97, 0, Pen.WHITE, (float) (0.7 * (1.0 - k)));
                }
            }
            double ra = t - AuswaehlenEntity.RECLAIM;
            if (ra >= 0 && ra < 24) {
                double k = ra / 24.0;
                p.plane(tp[i].add(0, 0.2, 0), X, Z, 3.0 + 14.0 * ease(k), 0, Pen.GOLDW, (float) (1.0 - k));
            }
            double ma = ease((t - (AuswaehlenEntity.MARK0 + i * AuswaehlenEntity.MARK_STAGGER)) / 8.0);
            if (ma > 0.01 && t < AuswaehlenEntity.PILLAR_END) {
                Vec3 mk = tp[i].add(0, th[i] + 3.4, 0);
                p.plane(mk, X, Z, 2.4 * ma, 0, Pen.GOLD, (float) (0.9 * ma * pil));
                p.plane(mk, X, Z, 1.7 * ma, 0, Pen.GOLDW, (float) (0.8 * ma * pil));
            }
        }
        if (haloA > 0.01 && haloS > 0.5) {
            float ha = (float) (haloA * haloBright);
            p.plane(haloC, haloR, Y, haloS * 1.05, 0, Pen.GOLD, 0.9f * ha);
            p.plane(haloC, haloR, Y, haloS * 1.03, 0, Pen.WHITE, 0.6f * ha);
            p.plane(haloC, haloR, Y, haloS * 0.62, 0, Pen.GOLDW, 0.6f * ha);
        }
        double fa = t - AuswaehlenEntity.COLLAPSE_END;
        if (fa >= 0 && fa < 34) {
            for (int r = 0; r < 3; r++) {
                double k = c01((fa - r * 5.0) / 26.0);
                if (k <= 0.0 || k >= 1.0) {
                    continue;
                }
                p.plane(C.add(0, 0.2, 0), X, Z, 16.0 * ease(k), 0, Pen.GOLDW, (float) (1.0 - k));
                p.plane(C.add(0, 1.3, 0), X, Z, 10.0 * ease(k), 0, Pen.GOLD, (float) (0.8 * (1.0 - k)));
            }
        }
    }

    // ------------------------------------------------------------------ CROSS marks

    private void crossPass() {
        for (int i = 0; i < n; i++) {
            double ma = ease((t - (AuswaehlenEntity.MARK0 + i * AuswaehlenEntity.MARK_STAGGER)) / 8.0);
            if (ma <= 0.01 || t >= AuswaehlenEntity.PILLAR_END) {
                continue;
            }
            Vec3 mk = tp[i].add(0, th[i] + 3.4 + 0.3 * Math.sin(t * 0.1 + i), 0);
            double half = (1.7 + 0.3 * Math.sin(t * 0.2 + i)) * ma;
            double rot = t * 0.04 + i;
            float a = (float) (ma * pil);
            p.billboard(mk, half * 1.6, rot, Pen.GOLD, 0.45f * a);
            p.billboard(mk, half, rot, Pen.GOLDW, 1.0f * a);
            p.billboard(mk, half * 0.9, rot, Pen.WHITE, 0.5f * a);
        }
    }

    // ------------------------------------------------------------------ WHEEL ornaments on the halo

    private void wheelPass() {
        if (haloA <= 0.01 || haloS < 1.0) {
            return;
        }
        float ha = (float) (haloA * haloBright);
        for (int k = 0; k < 8; k++) {
            double ang = k * Math.PI / 4 + t * 0.004;
            Vec3 d = haloR.scale(Math.cos(ang)).add(Y.scale(Math.sin(ang)));
            Vec3 c = haloC.add(d.scale(haloS * 1.12));
            double half = haloS * 0.13;
            p.billboard(c, half * 1.15, 0, Pen.GOLD, 0.6f * ha, 0f, 1f, 1f, 0f);
            p.billboard(c, half, 0, Pen.GOLDW, 0.95f * ha, 0f, 1f, 1f, 0f);
        }
    }

    // ------------------------------------------------------------------ SPARKLE (thousands of Reishi fragments)

    private void sparklePass() {
        float sa = (float) skyA;
        Random r = new Random(seed * 131L + 7);
        for (int i = 0; i < 160; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = Math.sqrt(r.nextDouble()) * SKY_R;
            double size = 0.6 + r.nextDouble() * 1.4;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.1 + r.nextDouble() * 0.2) + r.nextDouble() * 6.28);
            Vec3 pos = skyCenter().add(Math.cos(ang) * rad, (r.nextDouble() - 0.5) * 14.0 - ((t * 0.1 + r.nextDouble() * 20) % 20), Math.sin(ang) * rad);
            p.billboard(pos, size * (0.6 + 0.6 * tw), r.nextDouble() * 1.57 + t * 0.01, (i & 1) == 0 ? Pen.WHITE : Pen.GOLD, 0.9f * tw * sa);
        }
        for (int i = 0; i < n; i++) {
            if (drop[i] <= 0 || pil <= 0) {
                continue;
            }
            Vec3 g = tp[i];
            Random q = new Random(seed * 977L + i * 31L);
            for (int k = 0; k < 120; k++) {
                double ang = q.nextDouble() * Math.PI * 2 + t * 0.01 * (0.5 + q.nextDouble());
                double rad = Math.sqrt(q.nextDouble()) * COL_W * 0.46;
                double size = 0.18 + q.nextDouble() * 0.4;
                double spd = 0.35 + q.nextDouble() * 0.9;
                double y = (t * spd + q.nextDouble() * 80.0) % 80.0;
                float tw = 0.55f + 0.45f * (float) Math.sin(t * (0.2 + q.nextDouble() * 0.3) + q.nextDouble() * 6.28);
                if (g.y + y < headY[i]) {
                    continue;
                }
                float edge = (float) Math.min(1.0, Math.min(y / 3.0, (80.0 - y) / 12.0));
                Vec3 pos = g.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
                p.billboard(pos, size * (0.7 + 0.5 * tw), q.nextDouble() * 1.57 + t * 0.03, (k & 1) == 0 ? Pen.WHITE : Pen.GOLD, 0.95f * tw * edge * (float) pil);
            }
        }
        if (haloA > 0.01 && haloS > 1.0) {
            Random q = new Random(seed * 41L);
            for (int k = 0; k < 90; k++) {
                double ang = q.nextDouble() * Math.PI * 2;
                double rad = haloS * (0.4 + 0.75 * q.nextDouble());
                double size = 0.3 + q.nextDouble() * 0.7;
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 0.25 + k);
                double drift = ((t * 0.02 + q.nextDouble()) % 1.0);
                Vec3 d = haloR.scale(Math.cos(ang)).add(Y.scale(Math.sin(ang)));
                Vec3 pos = haloC.add(d.scale(rad * (1.0 + 0.3 * drift)));
                p.billboard(pos, size * (0.6 + 0.6 * tw), k + t * 0.02, k % 2 == 0 ? Pen.WHITE : Pen.GOLD, 0.9f * tw * (float) haloA);
            }
        }
        double fa = t - AuswaehlenEntity.COLLAPSE_END;
        if (fa >= 0 && fa < 34) {
            double k = fa / 34.0;
            Random q = new Random(seed * 17L);
            for (int j = 0; j < 50; j++) {
                double ang = q.nextDouble() * Math.PI * 2;
                double sp = 0.3 + q.nextDouble() * 0.7;
                double vy = (q.nextDouble() - 0.3) * 0.8;
                Vec3 pos = C.add(Math.cos(ang) * sp * fa, 1.3 + vy * fa, Math.sin(ang) * sp * fa);
                p.billboard(pos, 0.5 * (1.0 - k) + 0.1, j + fa * 0.2, j % 3 == 0 ? Pen.WHITE : Pen.GOLD, (float) (1.0 - k));
            }
        }
    }

    // ------------------------------------------------------------------ BURST flip-books

    private void flip(Vec3 c, double half, double age, double dur, float[] col, float a) {
        double fr = age / dur;
        if (fr < 0 || fr >= 1.0) {
            return;
        }
        int fi = Math.min(15, (int) (fr * 16));
        float u0 = (fi % 4) / 4f;
        float v0 = (fi / 4) / 4f;
        p.billboard(c, half, 0, col, a, u0, v0, u0 + 0.25f, v0 + 0.25f);
    }

    private void burstPass() {
        for (int i = 0; i < n; i++) {
            double la = landAge(i);
            Vec3 g = tp[i].add(0, 3.0, 0);
            flip(g, 15.0, la, 22.0, Pen.GOLD, 0.95f);
            flip(g, 8.5, la, 22.0, Pen.WHITE, 0.9f);
            double ra = t - AuswaehlenEntity.RECLAIM;
            flip(tp[i].add(0, th[i] * 0.5, 0), 9.0, ra, 16.0, Pen.GOLDW, 0.95f);
        }
        double fa = t - AuswaehlenEntity.COLLAPSE_END;
        Vec3 ch = C.add(0, 1.3, 0);
        flip(ch, 14.0, fa, 24.0, Pen.GOLD, 1.0f);
        flip(ch, 8.0, fa, 24.0, Pen.WHITE, 1.0f);
    }
}
