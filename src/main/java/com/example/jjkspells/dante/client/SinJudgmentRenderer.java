package com.example.jjkspells.dante.client;

import com.example.jjkspells.dante.entity.SinJudgmentEntity;
import com.example.jjkspells.dante.spell.SinJudgmentSpell;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Draws "Sin Devil Trigger: Judgment of Sparda": a 30 block heptagram of hellfire with rotating rings and mist over the ground, twelve giant
 * Rebellions that drop from the sky and stand in a ring, huge flame wings and a halo on the hovering caster, red lock-on reticles on every target
 * followed by giant Rebellions crashing down, then the dive and a colossal detonation with a pillar of fire, rings, radial fire and shards.
 * Glow stays low: thin lines, solid sprites, rings and small glints.
 */
public class SinJudgmentRenderer extends EntityRenderer<SinJudgmentEntity> {
    private static final int FIN = SinJudgmentEntity.FIN;
    private static final int DIVE0 = SinJudgmentEntity.DIVE0;
    private static final int END = SinJudgmentEntity.END;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final float[][] CELL = {{0f, 0f}, {0.5f, 0f}, {0f, 0.5f}, {0.5f, 0.5f}};

    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 H;
    private Vec3 F;
    private Vec3 R;
    private Entity own;
    private SinJudgmentEntity ent;
    private int seed;
    private double vis;
    private double wv;

    public SinJudgmentRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(SinJudgmentEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SinJudgmentEntity e) {
        return DanteRender.GLOW;
    }

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void add(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(DanteRender.additive(tex));
    }

    private void alpha(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(DanteRender.alpha(tex));
    }

    private void line(Vec3 a, Vec3 b, double w, float al, float[] col) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.4, al * 0.45f, 0f, 1f, col);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.CORE);
    }

    /** Line lying flat on the ground. */
    private void flat(Vec3 a, Vec3 b, double w, float al, float[] col) {
        Vec3 d = b.subtract(a);
        if (al <= 0.004f || d.x * d.x + d.z * d.z < 1.0E-6) {
            return;
        }
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
        p.ribbonSide(a, b, side, w * 2.4, w * 2.4, al * 0.5f, al * 0.5f, 0f, 1f, col);
        p.ribbonSide(a.add(0, 0.01, 0), b.add(0, 0.01, 0), side, w, w, al, al, 0f, 1f, Pen.CORE);
    }

    private Vec3 camSide(Vec3 at) {
        Vec3 tc = p.cam.subtract(at);
        Vec3 s = new Vec3(tc.z, 0, -tc.x);
        return s.lengthSqr() < 1.0E-6 ? X : s.normalize();
    }

    @Override
    public void render(SinJudgmentEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        own = e.owner();
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        seed = e.getId() * 8123;
        A = e.getPosition(pt);
        H = own != null ? own.getPosition(pt) : A;
        float by = own instanceof LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : 0f;
        double rad = Math.toRadians(by);
        F = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        R = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
        vis = ease(t / 20.0) * (1.0 - c01((t - (FIN + 24)) / 40.0));
        wv = ease((t - 8) / 22.0) * (1.0 - c01((t - FIN) / 6.0));
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        alpha(buf, DanteRender.SHARD);
        shardPass();
        alpha(buf, DanteRender.REBELLION);
        swordPass();
        add(buf, DanteRender.MIST);
        mistPass();
        add(buf, DanteRender.RING);
        ringPass();
        add(buf, DanteRender.BEAM);
        beamPass();
        add(buf, DanteRender.LINE);
        linePass();
        add(buf, DanteRender.GLINT);
        glintPass();
    }

    // ------------------------------------------------------------------ sprites

    private void blade(Vec3 hilt, Vec3 tip, float al) {
        if (al <= 0.01f) {
            return;
        }
        double w = hilt.distanceTo(tip) * ASPECT;
        Vec3 side = camSide(hilt.add(tip).scale(0.5));
        p.ribbonSide(hilt, tip, side, w, w, al, al, 0f, 1f, Pen.WHITE);
    }

    private void swordPass() {
        double fade = 1.0 - c01((t - FIN) / 12.0);
        // ring of giant blades dropping from the sky
        for (int i = 0; i < 12; i++) {
            double drop = 1.0 - ease((t - 6 - i * 1.5) / 16.0);
            if (t < 6 + i * 1.5 || fade <= 0.01) {
                continue;
            }
            double ang = i * Math.PI / 6 + 0.26;
            Vec3 base = A.add(Math.cos(ang) * 26.5, 0, Math.sin(ang) * 26.5);
            Vec3 tip = base.add(0, 70 * drop - 1.0, 0);
            Vec3 hilt = tip.add(0, 24, 0);
            blade(hilt, tip, (float) fade);
        }
        // the rain
        for (int[] st : ent.strikes()) {
            double impact = st[2];
            double age = t - (impact - 6);
            if (age < 0 || age > 40) {
                continue;
            }
            Vec3 pos = new Vec3(st[0] / 10.0, A.y, st[1] / 10.0);
            double k = c01(age / 6.0);
            double y = 46 * (1 - k * k);
            Vec3 tip = pos.add(0, y - 0.8 * k, 0);
            Vec3 hilt = tip.add(0, 18, 0);
            blade(hilt, tip, (float) (1.0 - c01((age - 26) / 14.0)));
        }
    }

    private void shardPass() {
        double a = t - FIN;
        if (a < 0 || a > 54) {
            return;
        }
        Random r = new Random(seed + 313);
        double fade = 1.0 - c01((a - 34) / 20.0);
        for (int i = 0; i < 44; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double sp = 0.5 + r.nextDouble() * 1.5;
            double vy = 0.5 + r.nextDouble() * 0.9;
            double size = 0.8 + r.nextDouble() * 1.6;
            double spin = (r.nextDouble() - 0.5) * 0.4;
            int cell = r.nextInt(4);
            double hh = 0.4 + vy * a - 0.02 * a * a;
            Vec3 pos = A.add(Math.cos(ang) * sp * a, Math.max(0.2, hh), Math.sin(ang) * sp * a);
            p.billboard(pos, size, a * spin + i, Pen.WHITE, (float) (0.95 * fade), CELL[cell][0], CELL[cell][1], CELL[cell][0] + 0.5f, CELL[cell][1] + 0.5f);
        }
    }

    // ------------------------------------------------------------------ passes

    private void mistPass() {
        if (vis <= 0.01) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            p.plane(A.add(0, 0.05 + 0.4 * i, 0), X, Z, 30 - 3 * i, t * 0.004 * (i % 2 == 0 ? 1 : -1) + i * 1.7, Pen.BLOOD, (float) (vis * (0.32 - 0.07 * i)));
        }
    }

    private void ringPass() {
        Vec3 g = A.add(0, 0.08, 0);
        if (vis > 0.01) {
            p.plane(g, X, Z, 31, t * 0.006, Pen.EMBER, (float) (0.5 * vis));
            p.plane(g.add(0, 0.01, 0), X, Z, 21, -t * 0.011, Pen.SCARLET, (float) (0.5 * vis));
            p.plane(g.add(0, 0.02, 0), X, Z, 11, t * 0.02, Pen.CORE, (float) (0.4 * vis));
            p.plane(g.add(0, 0.03, 0), X, Z, 5, -t * 0.04, Pen.EMBER, (float) (0.45 * vis));
        }
        if (t < 30) {
            double f = 1.0 - t / 30.0;
            p.plane(g.add(0, 0.1, 0), X, Z, 30 * ease(t / 20.0), 0, Pen.CORE, (float) (0.8 * f));
        }
        // halo behind the caster
        if (wv > 0.01 && own != null) {
            p.billboard(H.add(0, 1.9, 0).subtract(F.scale(0.6)), 3.4, t * 0.03, Pen.EMBER, (float) (0.6 * wv));
            p.billboard(H.add(0, 1.9, 0).subtract(F.scale(0.6)), 2.2, -t * 0.05, Pen.CORE, (float) (0.45 * wv));
        }
        // reticles and impacts
        for (int[] st : ent.strikes()) {
            double impact = st[2];
            Vec3 pos = new Vec3(st[0] / 10.0, A.y + 0.1, st[1] / 10.0);
            double la = t - (impact - SinJudgmentEntity.WARN);
            if (la >= 0 && la < SinJudgmentEntity.WARN) {
                double f = la / SinJudgmentEntity.WARN;
                p.plane(pos, X, Z, 8.0 - 2.5 * f, t * 0.1, Pen.SCARLET, (float) (0.5 + 0.4 * f));
                p.plane(pos.add(0, 0.01, 0), X, Z, 2.8, -t * 0.4, Pen.CORE, (float) (0.4 + 0.4 * f));
            }
            double ia = t - impact;
            if (ia >= 0 && ia < 18) {
                double f = 1.0 - ia / 18.0;
                p.plane(pos, X, Z, 5.5 + 7 * ease(ia / 8.0), 0, Pen.CORE, (float) (0.85 * f));
                p.plane(pos.add(0, 0.01, 0), X, Z, 3.5 + 5 * ease(ia / 8.0), 1.0, Pen.EMBER, (float) (0.6 * f));
            }
        }
        // judgment
        double a = t - FIN;
        if (a >= 0 && a < 60) {
            double f = 1.0 - a / 60.0;
            double Rf = SinJudgmentSpell.FINAL_RADIUS;
            p.plane(g.add(0, 0.12, 0), X, Z, Rf * 1.05 * ease(a / 16.0), 0, Pen.CORE, (float) (0.9 * f));
            p.plane(g.add(0, 0.13, 0), X, Z, Rf * 0.78 * ease((a - 3) / 16.0), 1.0, Pen.EMBER, (float) (0.75 * f));
            p.plane(g.add(0, 0.14, 0), X, Z, Rf * 0.45 * ease((a - 5) / 14.0), 2.0, Pen.SCARLET, (float) (0.65 * f));
            for (int i = 0; i < 4; i++) {
                double wr = a - i * 3;
                if (wr >= 0 && wr < 24) {
                    p.plane(A.add(0, 0.6 + wr * 0.9, 0), X, Z, 3 + 20 * ease(wr / 24.0), 0, Pen.SCARLET, (float) (0.5 * (1 - wr / 24.0)));
                }
            }
        }
    }

    /** Flame wings: nine large feathers per side. */
    private void wings() {
        double open = wv;
        if (open <= 0.01 || own == null) {
            return;
        }
        double flap = Math.sin(t * 0.09) * 0.1;
        for (int s = -1; s <= 1; s += 2) {
            Vec3 root = H.add(0, 1.7, 0).subtract(F.scale(0.3)).add(R.scale(0.3 * s));
            for (int i = 0; i < 9; i++) {
                double k = i / 8.0;
                double ang = Math.toRadians(15 + 100 * k) * open + flap * (1 - k * 0.5);
                double len = (8.0 + 5.0 * Math.sin(k * Math.PI * 0.9) + 1.5 * (1 - k)) * open;
                Vec3 dir = R.scale(s * Math.cos(ang)).add(0, Math.sin(ang) * 0.9 + 0.25, 0).subtract(F.scale(0.5 + 0.25 * k)).normalize();
                Vec3 tip = root.add(dir.scale(len)).add(0, -0.8 * k, 0);
                Vec3 mid = root.add(dir.scale(len * 0.5)).add(0, 0.7 * (1 - k), 0);
                double w = 2.2 - 0.8 * k;
                Vec3 sd = camSide(mid);
                float al = (float) (0.85 * wv);
                p.ribbonSide(root, mid, sd, 0.4, w, al * 0.5f, al * 0.7f, 0f, 1f, Pen.SCARLET);
                p.ribbonSide(mid, tip, sd, w, 0.1, al * 0.7f, 0f, 0f, 1f, Pen.SCARLET);
                p.ribbonSide(root, mid, sd, 0.2, w * 0.5, al * 0.5f, al * 0.7f, 0f, 1f, Pen.EMBER);
                p.ribbonSide(mid, tip, sd, w * 0.5, 0.06, al * 0.7f, 0f, 0f, 1f, Pen.EMBER);
            }
        }
    }

    private void column(Vec3 base, double height, double w, double fade) {
        Vec3 side = camSide(base);
        double[] ws = {w, w * 0.55, w * 0.22};
        float[][] cols = {Pen.SCARLET, Pen.EMBER, Pen.WHITE};
        float[] als = {0.55f, 0.75f, 1.0f};
        int segs = 8;
        for (int l = 0; l < 3; l++) {
            for (int s = 0; s < segs; s++) {
                double y0 = height * s / segs;
                double y1 = height * (s + 1) / segs;
                double w0 = ws[l] * fade * (1.0 - 0.35 * s / segs);
                double w1 = ws[l] * fade * (1.0 - 0.35 * (s + 1.0) / segs);
                p.ribbonSide(base.add(0, y0, 0), base.add(0, y1, 0), side, w0, w1, (float) (als[l] * fade), (float) (als[l] * fade), 0f, 1f, cols[l]);
            }
        }
    }

    private void beamPass() {
        wings();
        if (t < 16) {
            double f = 1.0 - t / 16.0;
            column(A, 60 * ease(t / 3.0), 6.0, f);
        }
        // the dive
        if (t >= DIVE0 && t < FIN + 2 && own != null) {
            double f = 1.0 - c01((t - FIN) / 2.0);
            Vec3 top = H.add(0, 18, 0);
            Vec3 sd = camSide(H);
            p.ribbonSide(H.add(0, 1.0, 0), top, sd, 3.6, 0.4, (float) (0.8 * f), 0f, 0f, 1f, Pen.EMBER);
            p.ribbonSide(H.add(0, 1.0, 0), top, sd, 1.4, 0.2, (float) (0.95 * f), 0f, 0f, 1f, Pen.WHITE);
        }
        // the pillar of judgment
        double a = t - FIN;
        if (a >= 0 && a < 34) {
            column(A, 140 * ease(a / 4.0), 24.0 * (1.0 - 0.3 * c01(a / 34.0)), 1.0 - a / 34.0);
        }
    }

    private void linePass() {
        Random r = new Random(seed + 9);
        double wave = SinJudgmentSpell.RADIUS;
        // heptagram of hellfire on the ground
        if (vis > 0.01) {
            double rr = 27.0 * ease(t / 20.0);
            double rot = t * 0.003;
            Vec3 g = A.add(0, 0.14, 0);
            for (int i = 0; i < 7; i++) {
                double a0 = rot + i * Math.PI * 2 / 7;
                double a1 = rot + ((i + 3) % 7) * Math.PI * 2 / 7;
                flat(g.add(Math.cos(a0) * rr, 0, Math.sin(a0) * rr), g.add(Math.cos(a1) * rr, 0, Math.sin(a1) * rr), 0.28, (float) (0.85 * vis), Pen.SCARLET);
            }
            // runes: small flames on the points
            for (int i = 0; i < 7; i++) {
                double a0 = rot + i * Math.PI * 2 / 7;
                Vec3 pt = g.add(Math.cos(a0) * rr, 0, Math.sin(a0) * rr);
                line(pt, pt.add(0, 3.0 + 1.5 * Math.sin(t * 0.3 + i), 0), 0.12, (float) (0.8 * vis), Pen.EMBER);
            }
        }
        // sparks around the hovering caster
        if (wv > 0.01 && own != null) {
            for (int i = 0; i < 10; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rr = 0.6 + r.nextDouble() * 2.0;
                double y = (r.nextDouble() * 5 + t * (0.05 + r.nextDouble() * 0.08)) % 5.5;
                Vec3 b = H.add(Math.cos(ang) * rr, y - 1, Math.sin(ang) * rr);
                line(b, b.add(0, 0.8, 0), 0.04, (float) (0.8 * wv), Pen.GOLD);
            }
        }
        // lock-on beacons and impact sparks
        for (int[] st : ent.strikes()) {
            double impact = st[2];
            Vec3 pos = new Vec3(st[0] / 10.0, A.y + 0.1, st[1] / 10.0);
            double la = t - (impact - SinJudgmentEntity.WARN);
            if (la >= 0 && la < SinJudgmentEntity.WARN) {
                line(pos, pos.add(0, 10, 0), 0.07, (float) (0.5 + 0.4 * la / SinJudgmentEntity.WARN), Pen.SCARLET);
            }
            double ia = t - impact;
            if (ia >= 0 && ia < 12) {
                Random q = new Random(seed + st[2] * 31L);
                double f = 1.0 - ia / 12.0;
                for (int i = 0; i < 9; i++) {
                    double ang = q.nextDouble() * Math.PI * 2;
                    Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                    double e = ease(ia / 8.0);
                    line(pos.add(d.scale(2 + 3 * e)).add(0, 0.3, 0), pos.add(d.scale(4 + 7 * e)).add(0, 0.3 + q.nextDouble() * 2, 0), 0.07, (float) (0.9 * f), Pen.EMBER);
                }
            }
        }
        // judgment
        double a = t - FIN;
        if (a >= 0 && a < 50) {
            double f = 1.0 - a / 50.0;
            double rw = SinJudgmentSpell.FINAL_RADIUS * ease(a / 16.0);
            for (int i = 0; i < 64; i++) {
                double ang = (i + r.nextDouble() * 0.4) * Math.PI * 2 / 64;
                Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                line(A.add(d.scale(rw * 0.55)).add(0, 0.3, 0), A.add(d.scale(rw)).add(0, 0.3, 0), 0.12, (float) (0.9 * f), Pen.EMBER);
            }
            for (int i = 0; i < 40; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = Math.sqrt(r.nextDouble()) * rw;
                Vec3 b = A.add(Math.cos(ang) * rad, 0.2, Math.sin(ang) * rad);
                line(b, b.add(0, (6 + r.nextDouble() * 24) * ease(a / 10.0), 0), 0.1, (float) (0.8 * f), r.nextBoolean() ? Pen.GOLD : Pen.EMBER);
            }
        }
    }

    private void glintPass() {
        if (t < 12) {
            p.billboard(A.add(0, 1.4, 0), 20 * (1 - t / 12.0) + 1, 0, Pen.CORE, (float) (0.9 * (1 - t / 12.0)));
        }
        if (wv > 0.01 && own != null) {
            p.billboard(H.add(0, 1.4, 0), 1.0 + 0.15 * Math.sin(t * 0.3), 0, Pen.HOT, (float) (0.4 * wv));
        }
        for (int[] st : ent.strikes()) {
            double ia = t - st[2];
            if (ia >= 0 && ia < 7) {
                Vec3 pos = new Vec3(st[0] / 10.0, A.y + 1.5, st[1] / 10.0);
                p.billboard(pos, 8 * (1 - ia / 7.0) + 0.5, 0, Pen.CORE, (float) (0.9 * (1 - ia / 7.0)));
            }
        }
        double a = t - FIN;
        if (a >= 0 && a < 14) {
            p.billboard(A.add(0, 1.5, 0), 26 * (1 - a / 14.0) + 2, 0, Pen.CORE, (float) (0.95 * (1 - a / 14.0)));
        }
        if (a >= 0 && a < 52) {
            Random r = new Random(seed + 77);
            double rw = SinJudgmentSpell.FINAL_RADIUS * ease(a / 16.0);
            double fade = 1.0 - a / 52.0;
            for (int i = 0; i < 70; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = Math.sqrt(r.nextDouble()) * rw;
                double y = 0.5 + ((r.nextDouble() * 20 + a * (0.2 + r.nextDouble() * 0.6)) % 24);
                p.billboard(A.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad), 0.3 + r.nextDouble() * 0.5, 0, Pen.GOLD, (float) (0.9 * fade));
            }
        }
    }
}
