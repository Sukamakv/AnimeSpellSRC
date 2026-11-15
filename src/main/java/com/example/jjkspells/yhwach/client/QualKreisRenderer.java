package com.example.jjkspells.yhwach.client;

import com.example.jjkspells.yhwach.entity.BowFrame;
import com.example.jjkspells.yhwach.entity.QualKreisEntity;
import com.example.jjkspells.yhwach.spell.QualKreisSpell;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Draws "Qual Kreis": a ring of twelve Reishi bows above the caster that materialise one by one, draw in rhythm and hail
 * comet-fast arrows onto the aim point, where a Quincy sigil, light pillar and a constant spray of impact flashes mark the kill zone.
 */
public class QualKreisRenderer extends EntityRenderer<QualKreisEntity> {
    private static final int N = QualKreisSpell.BOW_COUNT;
    private static final double SCALE = 0.85;
    private static final int PERIOD = 6;
    private static final float TRAVEL = 3f;
    private static final float IMPACT_LIFE = 8f;
    private static final double ZR = QualKreisSpell.ZONE_RADIUS;
    private static final double RING = QualKreisSpell.RING_RADIUS;

    public QualKreisRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(QualKreisEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(QualKreisEntity e) {
        return YhwachRender.GLOW;
    }

    private interface ArrowVisitor {
        void visit(int bow, float age, Vec3 start, Vec3 land, long seed);
    }

    private static final class S {
        Vec3 base;
        Vec3 P;
        float t;
        float tt;
        float env;
        float in;
        int seed;
        BowFrame[] fr = new BowFrame[N];
        double[] reveal = new double[N];
        double[] draw = new double[N];
        double[] grow = new double[N];
    }

    @Override
    public void render(QualKreisEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Entity owner = e.level().getEntity(e.getOwnerId());
        if (owner == null) {
            return;
        }
        S s = new S();
        s.t = e.tickCount + pt;
        s.tt = s.t - QualKreisEntity.APPEAR;
        s.env = 1f - BowArt.clamp01((s.tt - QualKreisEntity.FIRE_TICKS) / (float) QualKreisEntity.FADE);
        if (s.env <= 0.003f) {
            return;
        }
        s.in = (float) BowArt.ease(BowArt.clamp01(s.t / 10f));
        s.seed = e.getId();
        s.base = owner.getPosition(pt);
        s.P = e.targetAt(pt);
        boolean firing = s.tt >= 0 && s.tt < QualKreisEntity.FIRE_TICKS;
        for (int i = 0; i < N; i++) {
            Vec3 c = QualKreisEntity.bowCenter(s.base, i, s.t);
            double ang = i * (Math.PI * 2.0 / N) + s.t * 0.025;
            Vec3 tangent = new Vec3(-Math.sin(ang), 0, Math.cos(ang));
            s.fr[i] = BowFrame.oriented(c, s.P.subtract(c), tangent);
            s.reveal[i] = BowArt.ease(BowArt.clamp01((s.t - i * 0.7f) / 8f));
            if (firing) {
                float cyc = BowArt.frac((s.tt - phase(i)) / PERIOD);
                s.draw[i] = BowArt.MAX_DRAW * cyc * cyc;
                s.grow[i] = Math.min(1.0, cyc * 2.2);
            } else {
                s.draw[i] = 0;
                s.grow[i] = 0;
            }
        }

        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Pen pen = new Pen(ps.last().pose(), e.getPosition(pt), camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        pen.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.GLOW));
        glowPhase(pen, s);
        pen.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.STREAK));
        streakPhase(pen, s);
        pen.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.RING));
        ringPhase(pen, s);
        pen.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.SEAL));
        sealPhase(pen, s);
        pen.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.SPARKLE));
        sparklePhase(pen, s);
    }

    private static int phase(int i) {
        return (i * 5) % PERIOD;
    }

    /** Visits every arrow that is currently in flight or still flashing on impact. */
    private void arrows(S s, ArrowVisitor v) {
        if (s.tt < 0) {
            return;
        }
        for (int i = 0; i < N; i++) {
            int ph = phase(i);
            int kc = (int) Math.floor((s.tt - ph) / PERIOD);
            for (int k = kc - 1; k <= kc; k++) {
                if (k < 0) {
                    continue;
                }
                float tf = ph + k * PERIOD;
                if (tf >= QualKreisEntity.FIRE_TICKS) {
                    continue;
                }
                float age = s.tt - tf;
                if (age < 0 || age > TRAVEL + IMPACT_LIFE) {
                    continue;
                }
                long seed = s.seed * 31L + i * 1009L + k * 7919L;
                Random r = new Random(seed);
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = ZR * 0.92 * Math.sqrt(r.nextDouble());
                Vec3 land = s.P.add(Math.cos(ang) * rad, 0.05, Math.sin(ang) * rad);
                Vec3 start = s.fr[i].center.add(s.fr[i].f.scale(SCALE));
                v.visit(i, age, start, land, seed);
            }
        }
    }

    // ------------------------------------------------------------------ glow

    private void glowPhase(Pen p, S s) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(s.t * 0.4f);
        for (int i = 0; i < N; i++) {
            BowArt.glow(p, s.fr[i], s.reveal[i], s.env, s.t, SCALE);
        }
        p.billboard(s.base.add(0, QualKreisSpell.BOW_HEIGHT, 0), RING * 1.4, 0, Pen.BLUE, 0.12f * s.in * s.env, 0, 0, 1, 1);
        p.billboard(s.P.add(0, 0.5, 0), ZR * 1.5, 0, Pen.BLUE, (0.20f + 0.06f * pulse) * s.in * s.env, 0, 0, 1, 1);
        p.billboard(s.P.add(0, 0.8, 0), ZR * 0.7, 0, Pen.CYAN, (0.16f + 0.06f * pulse) * s.in * s.env, 0, 0, 1, 1);
        arrows(s, (i, age, start, land, seed) -> {
            if (age < TRAVEL + 1.2f) {
                float q = BowArt.clamp01(age / TRAVEL);
                Vec3 head = start.add(land.subtract(start).scale(q));
                float sa = age < TRAVEL ? 1f : 1f - BowArt.clamp01((age - TRAVEL) / 1.2f);
                p.billboard(head, 1.0, 0, Pen.CYAN, 0.8f * sa * s.env, 0, 0, 1, 1);
                p.billboard(head, 0.45, 0, Pen.WHITE, 1.0f * sa * s.env, 0, 0, 1, 1);
            }
            if (age >= TRAVEL) {
                float k8 = BowArt.clamp01((age - TRAVEL) / IMPACT_LIFE);
                float f = 1f - k8;
                p.billboard(land, 1.8 * f + 0.4, 0, Pen.CYAN, 0.9f * f * s.env, 0, 0, 1, 1);
                p.billboard(land, 0.9 * f * f + 0.1, 0, Pen.WHITE, f * s.env, 0, 0, 1, 1);
            }
        });
    }

    // ------------------------------------------------------------------ ribbons

    private void streakPhase(Pen p, S s) {
        for (int i = 0; i < N; i++) {
            BowArt.bow(p, s.fr[i], s.reveal[i], s.env, s.draw[i], s.t, SCALE);
            BowArt.arrow(p, s.fr[i], s.draw[i], s.grow[i], s.env, s.t, SCALE);
        }
        arrows(s, (i, age, start, land, seed) -> {
            if (age >= TRAVEL + 1.2f) {
                return;
            }
            float q = BowArt.clamp01(age / TRAVEL);
            float fadeT = age < TRAVEL ? 0f : BowArt.clamp01((age - TRAVEL) / 1.2f);
            Vec3 d = land.subtract(start);
            Vec3 head = start.add(d.scale(q));
            double tailQ = Math.min(1.0, Math.max(0.0, q - 0.5) + fadeT * 0.5);
            Vec3 tail = start.add(d.scale(tailQ));
            float sa = (1f - fadeT) * s.env;
            float[][] c = {Pen.BLUE, Pen.CYAN, Pen.WHITE};
            double[] w = {1.1, 0.55, 0.2};
            float[] a = {0.4f, 0.7f, 1.0f};
            for (int l = 0; l < 3; l++) {
                p.ribbon(tail, head, w[l] * 0.15, w[l], 0f, a[l] * sa, 0f, 1f, c[l]);
            }
        });
        // faint light pillar over the kill zone
        Vec3 hx = new Vec3(0, 1, 0).cross(p.cam.subtract(s.P));
        hx = hx.lengthSqr() < 1.0E-8 ? new Vec3(1, 0, 0) : hx.normalize();
        float pa = s.in * s.env;
        double pw = ZR * 0.6 * (1.0 + 0.1 * Math.sin(s.t * 1.1));
        p.ribbonSide(s.P, s.P.add(0, 16, 0), hx, pw * 1.6, pw * 0.9, 0.22f * pa, 0f, 0f, 6f - s.t * 0.3f, Pen.BLUE);
        p.ribbonSide(s.P, s.P.add(0, 16, 0), hx, pw * 0.7, pw * 0.4, 0.35f * pa, 0f, 0f, 6f - s.t * 0.3f, Pen.CYAN);
    }

    // ------------------------------------------------------------------ rings

    private void ringPhase(Pen p, S s) {
        Vec3 X = new Vec3(1, 0, 0);
        Vec3 Z = new Vec3(0, 0, 1);
        float k = s.in * s.env;
        p.plane(s.P.add(0, 0.1, 0), X, Z, ZR * 1.05, 0, Pen.CORE, 0.5f * k * (0.75f + 0.25f * (float) Math.sin(s.t * 0.5f)));
        float ph = BowArt.frac(s.t / 14f);
        p.plane(s.P.add(0, 0.12, 0), X, Z, ZR * (0.3 + 1.2 * ph), 0, Pen.CYAN, 0.6f * k * (1f - ph));
        arrows(s, (i, age, start, land, seed) -> {
            if (age >= TRAVEL) {
                float k8 = BowArt.clamp01((age - TRAVEL) / IMPACT_LIFE);
                p.plane(land.add(0, 0.08, 0), X, Z, 0.4 + 2.4 * BowArt.ease(k8), 0, Pen.CYAN, 0.85f * (1f - k8) * s.env);
            }
        });
    }

    // ------------------------------------------------------------------ sigils

    private void sealPhase(Pen p, S s) {
        Vec3 X = new Vec3(1, 0, 0);
        Vec3 Z = new Vec3(0, 0, 1);
        float k = s.in * s.env;
        Vec3 over = s.base.add(0, QualKreisSpell.BOW_HEIGHT - 0.2, 0);
        p.plane(over, X, Z, RING * 1.2 * s.in, s.t * 0.02, Pen.CYAN, 0.5f * k);
        p.plane(over.add(0, 0.05, 0), X, Z, RING * 0.7 * s.in, -s.t * 0.035, Pen.CORE, 0.35f * k);
        Vec3 g = s.P.add(0, 0.06, 0);
        p.plane(g, X, Z, ZR * 1.2 * s.in, s.t * 0.05, Pen.CYAN, 0.7f * k);
        p.plane(g.add(0, 0.01, 0), X, Z, ZR * 0.7 * s.in, -s.t * 0.08, Pen.CORE, 0.5f * k);
    }

    // ------------------------------------------------------------------ sparkles

    private void sparklePhase(Pen p, S s) {
        for (int i = 0; i < N; i++) {
            BowArt.sparkles(p, s.fr[i], s.reveal[i], s.env, s.t, s.seed * 131L + i * 17L, 9, SCALE);
        }
        float k = s.in * s.env;
        Random rnd = new Random(s.seed * 977L + 3);
        for (int i = 0; i < 44; i++) {
            double th = rnd.nextDouble() * Math.PI * 2;
            double rr = Math.sqrt(rnd.nextDouble()) * ZR * 1.1;
            float rise = BowArt.frac((float) (rnd.nextDouble() + s.t * 0.03 * (0.6 + rnd.nextDouble())));
            Vec3 pos = s.P.add(Math.cos(th) * rr, 0.3 + rise * 9.0, Math.sin(th) * rr);
            float tw = 0.5f + 0.5f * (float) Math.sin(s.t * (0.3 + rnd.nextDouble() * 0.3) + rnd.nextDouble() * 6.28);
            p.billboard(pos, 0.12 + rnd.nextDouble() * 0.32, rnd.nextDouble() * 1.57 + s.t * 0.03, (i & 1) == 0 ? Pen.WHITE : Pen.CYAN, tw * (1f - rise * 0.7f) * k);
        }
        arrows(s, (i, age, start, land, seed) -> {
            if (age >= TRAVEL) {
                float k8 = BowArt.clamp01((age - TRAVEL) / IMPACT_LIFE);
                Random r = new Random(seed + 5);
                for (int n = 0; n < 4; n++) {
                    double th = r.nextDouble() * Math.PI * 2;
                    double sp = (0.8 + r.nextDouble() * 1.8) * BowArt.ease(k8);
                    Vec3 pos = land.add(Math.cos(th) * sp, 0.3 + r.nextDouble() * 1.4 * k8, Math.sin(th) * sp);
                    p.billboard(pos, (0.15 + r.nextDouble() * 0.25) * (1f - k8), r.nextDouble() * 1.57, Pen.WHITE, (1f - k8) * s.env);
                }
            }
        });
    }
}
