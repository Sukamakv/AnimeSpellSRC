package com.example.jjkspells.dante.client;

import com.example.jjkspells.dante.entity.DevilTriggerEntity;
import com.example.jjkspells.dante.spell.DevilTriggerSpell;
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
 * Draws "Devil Trigger: Crimson Awakening": a huge crimson shockwave with a pillar of fire and flying red shards at the awakening, then for the whole
 * duration flame wings made of feathers behind the caster, tongues of flame around him, a rotating crest on the ground, rising embers, a heartbeat
 * ring and a Devil Burst ring every few seconds. Glow stays low: thin lines, rings, solid shards and small glints.
 */
public class DevilTriggerRenderer extends EntityRenderer<DevilTriggerEntity> {
    private static final int DURATION = DevilTriggerEntity.DURATION;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final float[][] CELL = {{0f, 0f}, {0.5f, 0f}, {0f, 0.5f}, {0.5f, 0.5f}};

    private Pen p;
    private float t;
    private Vec3 O;
    private Vec3 C;
    private Vec3 F;
    private Vec3 R;
    private Entity own;
    private DevilTriggerEntity ent;
    private int seed;
    private double on;

    public DevilTriggerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(DevilTriggerEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(DevilTriggerEntity e) {
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

    @Override
    public void render(DevilTriggerEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        own = e.owner();
        if (t < 0 || t > DevilTriggerEntity.END || own == null) {
            return;
        }
        ent = e;
        seed = e.getId() * 6151;
        O = own.getPosition(pt);
        C = O.add(0, 1.3, 0);
        float by = own instanceof LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : own.getYRot();
        double rad = Math.toRadians(by);
        F = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        R = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
        on = ease(t / 24.0) * (1.0 - c01((t - DURATION) / 20.0));
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), O, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        alpha(buf, DanteRender.SHARD);
        shardPass();
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

    private Vec3 camSide(Vec3 at) {
        Vec3 tc = p.cam.subtract(at);
        Vec3 s = new Vec3(tc.z, 0, -tc.x);
        return s.lengthSqr() < 1.0E-6 ? X : s.normalize();
    }

    // ------------------------------------------------------------------ passes

    private void shardPass() {
        shards(t - 1, 24, 0);
        shards(t - DURATION, 14, 1);
    }

    private void shards(double age, int n, int salt) {
        if (age < 0 || age > 44) {
            return;
        }
        Random r = new Random(seed + salt * 313L);
        double fade = 1.0 - c01((age - 28) / 16.0);
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double sp = 0.35 + r.nextDouble() * 1.0;
            double vy = 0.35 + r.nextDouble() * 0.6;
            double size = 0.5 + r.nextDouble() * 1.0;
            double spin = (r.nextDouble() - 0.5) * 0.4;
            int cell = r.nextInt(4);
            double hh = 0.4 + vy * age - 0.018 * age * age;
            Vec3 pos = O.add(Math.cos(ang) * sp * age, Math.max(0.2, hh), Math.sin(ang) * sp * age);
            p.billboard(pos, size, age * spin + i, Pen.WHITE, (float) (0.95 * fade), CELL[cell][0], CELL[cell][1], CELL[cell][0] + 0.5f, CELL[cell][1] + 0.5f);
        }
    }

    private void mistPass() {
        if (on <= 0.01) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            p.plane(O.add(0, 0.05 + 0.3 * i, 0), X, Z, 5.5 - 0.7 * i, t * 0.012 * (i % 2 == 0 ? 1 : -1) + i * 1.7, Pen.BLOOD, (float) (on * (0.3 - 0.07 * i)));
        }
    }

    private void ringPass() {
        Vec3 g = O.add(0, 0.08, 0);
        if (on > 0.01) {
            p.plane(g, X, Z, 4.2, t * 0.02, Pen.SCARLET, (float) (0.6 * on));
            p.plane(g.add(0, 0.01, 0), X, Z, 2.8, -t * 0.035, Pen.EMBER, (float) (0.55 * on));
            p.plane(g.add(0, 0.02, 0), X, Z, 1.5, t * 0.06, Pen.CORE, (float) (0.45 * on));
            // heartbeat
            double hb = (t % 40) / 40.0;
            p.plane(g.add(0, 0.03, 0), X, Z, 2.0 + 4.5 * hb, 0, Pen.RED, (float) (0.4 * (1 - hb) * on));
        }
        // awakening shockwave
        double a = t - 1;
        if (a >= 0 && a < 34) {
            double f = 1.0 - a / 34.0;
            double R1 = DevilTriggerSpell.OPEN_RADIUS;
            p.plane(g.add(0, 0.1, 0), X, Z, R1 * 1.05 * ease(a / 14.0), 0, Pen.CORE, (float) (0.9 * f));
            p.plane(g.add(0, 0.11, 0), X, Z, R1 * 0.75 * ease((a - 2) / 14.0), 1.0, Pen.EMBER, (float) (0.7 * f));
            p.plane(g.add(0, 0.12, 0), X, Z, R1 * 0.4 * ease((a - 4) / 12.0), 2.0, Pen.SCARLET, (float) (0.6 * f));
            for (int i = 0; i < 3; i++) {
                double wr = a - i * 3;
                if (wr >= 0 && wr < 18) {
                    p.plane(O.add(0, 0.5 + wr * 0.6, 0), X, Z, 2.5 + 10 * ease(wr / 18.0), 0, Pen.SCARLET, (float) (0.5 * (1 - wr / 18.0)));
                }
            }
        }
        // devil bursts
        for (int k = 0; k < DevilTriggerEntity.pulseCount(); k++) {
            double age = t - DevilTriggerEntity.pulseTick(k);
            if (age >= 0 && age < 20) {
                double f = 1.0 - age / 20.0;
                double R2 = DevilTriggerSpell.PULSE_RADIUS;
                p.plane(g.add(0, 0.1, 0), X, Z, R2 * ease(age / 9.0), 0, Pen.CORE, (float) (0.8 * f));
                p.plane(g.add(0, 0.11, 0), X, Z, R2 * 0.7 * ease((age - 2) / 9.0), 1.0, Pen.EMBER, (float) (0.6 * f));
                p.plane(O.add(0, 0.6 + age * 0.2, 0), X, Z, 2 + R2 * 0.6 * ease(age / 12.0), 0, Pen.SCARLET, (float) (0.4 * f));
            }
        }
        // calming down
        double e = t - DURATION;
        if (e >= 0 && e < 22) {
            p.plane(g.add(0, 0.1, 0), X, Z, 3 + 8 * ease(e / 14.0), 0, Pen.EMBER, (float) (0.7 * (1 - e / 22.0)));
        }
    }

    /** One tongue of flame: base, height, base width, lean vector, flicker seed. */
    private void flame(Vec3 base, double h, double w, Vec3 lean, double ph, double al) {
        Vec3 side = camSide(base);
        int segs = 4;
        double[] ws = {w * 1.0, w * 0.55, w * 0.18};
        float[][] cols = {Pen.SCARLET, Pen.EMBER, Pen.HOT};
        float[] als = {0.55f, 0.6f, 0.8f};
        for (int l = 0; l < 3; l++) {
            double hh = h * (1.0 - 0.18 * l);
            for (int s = 0; s < segs; s++) {
                double f0 = (double) s / segs;
                double f1 = (double) (s + 1) / segs;
                Vec3 a = base.add(lean.scale(f0 * f0 * Math.sin(ph + f0 * 3))).add(0, hh * f0, 0);
                Vec3 b = base.add(lean.scale(f1 * f1 * Math.sin(ph + f1 * 3))).add(0, hh * f1, 0);
                double w0 = ws[l] * (1.0 - 0.85 * f0);
                double w1 = ws[l] * (1.0 - 0.85 * f1);
                float a0 = (float) (als[l] * al * (1.0 - 0.7 * f0));
                float a1 = (float) (als[l] * al * (1.0 - 0.95 * f1));
                p.ribbonSide(a, b, side, w0, w1, a0, a1, 0f, 1f, cols[l]);
            }
        }
    }

    private void beamPass() {
        if (on > 0.01) {
            // tongues of flame around the caster
            int n = 12;
            for (int i = 0; i < n; i++) {
                double ang = i * Math.PI * 2 / n + t * 0.015;
                double rr = 1.0 + 0.7 * ((i * 7) % 3) / 2.0;
                double h = (3.2 + 2.0 * (((i * 5) % 4) / 3.0)) * (0.85 + 0.15 * Math.sin(t * 0.3 + i)) * on;
                Vec3 base = O.add(Math.cos(ang) * rr, 0.05, Math.sin(ang) * rr);
                Vec3 lean = new Vec3(Math.cos(ang) * 0.6, 0, Math.sin(ang) * 0.6);
                flame(base, h, 0.9, lean, t * 0.2 + i * 1.3, 0.9 * on);
            }
            wings();
        }
        // pillar of the awakening
        double a = t - 1;
        if (a >= 0 && a < 22) {
            double f = 1.0 - a / 22.0;
            double g = ease(a / 3.0);
            Vec3 side = camSide(O);
            double[] ws = {10.0, 5.5, 2.2};
            float[][] cols = {Pen.SCARLET, Pen.EMBER, Pen.WHITE};
            float[] als = {0.55f, 0.75f, 1.0f};
            for (int l = 0; l < 3; l++) {
                for (int s = 0; s < 6; s++) {
                    double y0 = 70 * g * s / 6;
                    double y1 = 70 * g * (s + 1) / 6;
                    double w0 = ws[l] * f * (1.0 - 0.4 * s / 6);
                    double w1 = ws[l] * f * (1.0 - 0.4 * (s + 1.0) / 6);
                    p.ribbonSide(O.add(0, y0, 0), O.add(0, y1, 0), side, w0, w1, (float) (als[l] * f), (float) (als[l] * f), 0f, 1f, cols[l]);
                }
            }
        }
    }

    /** Flame wings: seven feathers per side fanning from the shoulder blades. */
    private void wings() {
        double open = ease((t - 6) / 24.0);
        if (open <= 0.01) {
            return;
        }
        double flap = Math.sin(t * 0.11) * 0.12;
        Vec3 fwd = F;
        for (int s = -1; s <= 1; s += 2) {
            Vec3 root = O.add(0, 1.55, 0).subtract(fwd.scale(0.25)).add(R.scale(0.25 * s));
            for (int i = 0; i < 7; i++) {
                double k = i / 6.0;
                double ang = Math.toRadians(20 + 95 * k) * open + flap * (1 - k * 0.5);
                double len = (3.8 + 2.2 * Math.sin(k * Math.PI * 0.9) + 0.6 * (1 - k)) * open;
                Vec3 dir = R.scale(s * Math.cos(ang)).add(0, Math.sin(ang) * 0.9 + 0.25, 0).subtract(fwd.scale(0.55 + 0.25 * k)).normalize();
                Vec3 tip = root.add(dir.scale(len)).add(0, -0.4 * k, 0);
                Vec3 mid = root.add(dir.scale(len * 0.5)).add(0, 0.35 * (1 - k), 0);
                double w = 0.95 - 0.35 * k;
                Vec3 sd = camSide(mid);
                float al = (float) (0.85 * on);
                p.ribbonSide(root, mid, sd, 0.2, w, al * 0.5f, al * 0.7f, 0f, 1f, Pen.SCARLET);
                p.ribbonSide(mid, tip, sd, w, 0.05, al * 0.7f, 0f, 0f, 1f, Pen.SCARLET);
                p.ribbonSide(root, mid, sd, 0.1, w * 0.5, al * 0.5f, al * 0.7f, 0f, 1f, Pen.EMBER);
                p.ribbonSide(mid, tip, sd, w * 0.5, 0.03, al * 0.7f, 0f, 0f, 1f, Pen.EMBER);
            }
        }
    }

    private void linePass() {
        Random r = new Random(seed + 9);
        if (on > 0.01) {
            // feather edges and spine lines for crisp wings
            double open = ease((t - 6) / 24.0);
            double flap = Math.sin(t * 0.11) * 0.12;
            for (int s = -1; s <= 1; s += 2) {
                Vec3 root = O.add(0, 1.55, 0).subtract(F.scale(0.25)).add(R.scale(0.25 * s));
                for (int i = 0; i < 7; i++) {
                    double k = i / 6.0;
                    double ang = Math.toRadians(20 + 95 * k) * open + flap * (1 - k * 0.5);
                    double len = (3.8 + 2.2 * Math.sin(k * Math.PI * 0.9) + 0.6 * (1 - k)) * open;
                    Vec3 dir = R.scale(s * Math.cos(ang)).add(0, Math.sin(ang) * 0.9 + 0.25, 0).subtract(F.scale(0.55 + 0.25 * k)).normalize();
                    Vec3 tip = root.add(dir.scale(len)).add(0, -0.4 * k, 0);
                    Vec3 mid = root.add(dir.scale(len * 0.5)).add(0, 0.35 * (1 - k), 0);
                    line(root, mid, 0.035, (float) (0.8 * on), Pen.EMBER);
                    line(mid, tip, 0.03, (float) (0.8 * on), Pen.EMBER);
                }
            }
            // sparks rising along the body
            for (int i = 0; i < 10; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rr = 0.5 + r.nextDouble() * 1.6;
                double y = (r.nextDouble() * 4 + t * (0.05 + r.nextDouble() * 0.08)) % 4.5;
                Vec3 b = O.add(Math.cos(ang) * rr, y, Math.sin(ang) * rr);
                line(b, b.add(0, 0.7 + r.nextDouble() * 0.8, 0), 0.03, (float) (0.8 * on), Pen.GOLD);
            }
        }
        double a = t - 1;
        if (a >= 0 && a < 30) {
            double f = 1.0 - a / 30.0;
            double rw = DevilTriggerSpell.OPEN_RADIUS * ease(a / 14.0);
            for (int i = 0; i < 44; i++) {
                double ang = (i + r.nextDouble() * 0.4) * Math.PI * 2 / 44;
                Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                line(O.add(d.scale(rw * 0.55)).add(0, 0.25, 0), O.add(d.scale(rw)).add(0, 0.25, 0), 0.1, (float) (0.9 * f), Pen.EMBER);
            }
        }
        for (int k = 0; k < DevilTriggerEntity.pulseCount(); k++) {
            double age = t - DevilTriggerEntity.pulseTick(k);
            if (age >= 0 && age < 16) {
                double f = 1.0 - age / 16.0;
                double rw = DevilTriggerSpell.PULSE_RADIUS * ease(age / 9.0);
                for (int i = 0; i < 28; i++) {
                    double ang = i * Math.PI * 2 / 28 + k;
                    Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                    line(O.add(d.scale(rw * 0.6)).add(0, 0.25, 0), O.add(d.scale(rw)).add(0, 0.25, 0), 0.08, (float) (0.8 * f), Pen.EMBER);
                }
            }
        }
    }

    private void glintPass() {
        double a = t - 1;
        if (a >= 0 && a < 10) {
            p.billboard(C, 14 * (1 - a / 10.0) + 1, 0, Pen.CORE, (float) (0.9 * (1 - a / 10.0)));
        }
        for (int k = 0; k < DevilTriggerEntity.pulseCount(); k++) {
            double age = t - DevilTriggerEntity.pulseTick(k);
            if (age >= 0 && age < 6) {
                p.billboard(C, 6 * (1 - age / 6.0) + 0.5, 0, Pen.HOT, (float) (0.7 * (1 - age / 6.0)));
            }
        }
        if (on > 0.01) {
            Random r = new Random(seed + 21);
            for (int i = 0; i < 26; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rr = 0.4 + r.nextDouble() * 3.0;
                double y = (r.nextDouble() * 6 + t * (0.04 + r.nextDouble() * 0.08)) % 6.5;
                p.billboard(O.add(Math.cos(ang) * rr, y, Math.sin(ang) * rr), 0.14 + r.nextDouble() * 0.2, 0, Pen.GOLD, (float) (0.9 * on));
            }
            p.billboard(C, 0.8 + 0.1 * Math.sin(t * 0.3), 0, Pen.HOT, (float) (0.35 * on));
        }
    }
}
