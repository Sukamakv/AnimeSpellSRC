package com.example.jjkspells.dante.client;

import com.example.jjkspells.dante.entity.CavaliereEntity;
import com.example.jjkspells.dante.spell.CavaliereSpell;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * Draws "Cavaliere Angelo: Lightning Ride": a motorcycle made of two spinning lightning wheels and a wire-thin glowing frame follows the caster,
 * leaving a crackling ground trail with speed lines and arcs; rammed enemies flash. At the end a thunderstorm of jagged bolts crashes down around
 * the end point, closed by one colossal bolt with a pillar of light and rings. Glow stays low: thin lines, rings and small glints.
 */
public class CavaliereRenderer extends EntityRenderer<CavaliereEntity> {
    private static final int RIDE0 = CavaliereEntity.RIDE0;
    private static final int RIDE_END = CavaliereEntity.RIDE_END;
    private static final int BOLTS = CavaliereEntity.BOLTS;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final float[] ELEC = {0.35f, 0.65f, 1.0f};
    private static final float[] CYAN = {0.45f, 0.92f, 1.0f};
    private static final float[] VIOLET = {0.6f, 0.5f, 1.0f};
    private static final float[] YEL = {1.0f, 0.88f, 0.4f};
    private static final float[] NAVY = {0.05f, 0.1f, 0.35f};
    private static final double WR = 1.35;
    private static final double HALF_BASE = 2.0;

    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 F;
    private Vec3 S;
    private Entity own;
    private CavaliereEntity ent;
    private int seed;
    private double vis;
    private double spin;

    public CavaliereRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(CavaliereEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(CavaliereEntity e) {
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

    private void line(Vec3 a, Vec3 b, double w, float al, float[] col) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.5, al * 0.5f, 0f, 1f, col);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.WHITE);
    }

    /** Jagged lightning from a to b. */
    private void zap(Vec3 a, Vec3 b, int segs, double jag, double w, float al, float[] col, long sd) {
        Random r = new Random(sd);
        Vec3 d = b.subtract(a);
        Vec3 u = d.normalize();
        Vec3 p1 = u.cross(Math.abs(u.y) > 0.9 ? X : Y).normalize();
        Vec3 p2 = u.cross(p1).normalize();
        Vec3 prev = a;
        for (int i = 1; i <= segs; i++) {
            double f = (double) i / segs;
            Vec3 pt = a.add(d.scale(f));
            if (i < segs) {
                pt = pt.add(p1.scale(r.nextGaussian() * jag)).add(p2.scale(r.nextGaussian() * jag));
            }
            line(prev, pt, w, al, col);
            prev = pt;
        }
    }

    @Override
    public void render(CavaliereEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        own = e.owner();
        if (t < 0 || t > CavaliereEntity.END || own == null) {
            return;
        }
        ent = e;
        seed = e.getId() * 3571;
        A = own.getPosition(pt);
        double rad = Math.toRadians(e.heading());
        F = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        S = new Vec3(F.z, 0, -F.x);
        vis = ease(t / RIDE0) * (1.0 - c01((t - (RIDE_END + 6)) / 8.0));
        spin = t * 0.55;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

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
        add(buf, DanteRender.GLOW);
        glowPass();
    }

    private Vec3 wheelCentre(int sign) {
        return A.add(F.scale(HALF_BASE * sign)).add(0, WR + 0.05, 0);
    }

    // ------------------------------------------------------------------ passes

    private void mistPass() {
        for (int k = 0; k <= BOLTS; k++) {
            double age = t - CavaliereEntity.boltTick(k);
            Vec3 c = ent.boltPos(k);
            if (c == null || age < 0 || age > 40) {
                continue;
            }
            double r = (k == BOLTS ? CavaliereSpell.BIG_RADIUS : CavaliereSpell.BOLT_RADIUS) * 0.9;
            p.plane(c.add(0, 0.05, 0), X, Z, r, age * 0.01 + k, NAVY, (float) (0.35 * ease(age / 4.0) * (1 - age / 40.0)));
        }
    }

    private void ringPass() {
        if (vis > 0.01) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 c = wheelCentre(s);
                p.plane(c, Y, F, WR * 1.08 * vis, spin * s, ELEC, (float) (0.85 * vis));
                p.plane(c, Y, F, WR * 0.78 * vis, -spin * 1.3, CYAN, (float) (0.6 * vis));
                p.plane(c, Y, F, WR * 1.3 * vis, 0, VIOLET, (float) (0.3 * vis));
            }
        }
        if (t < RIDE0) {
            double k = t / RIDE0;
            p.plane(A.add(0, 0.08, 0), X, Z, 6.0 * (1 - 0.4 * k), t * 0.2, ELEC, (float) (0.5 * k + 0.2));
            p.plane(A.add(0, 0.09, 0), X, Z, 3.5, -t * 0.3, CYAN, (float) (0.4 * k));
        }
        if (t >= RIDE0 && t < RIDE0 + 10) {
            double a = t - RIDE0;
            p.plane(A.add(0, 0.1, 0), X, Z, 2 + 9 * ease(a / 10.0), 0, YEL, (float) (0.7 * (1 - a / 10.0)));
        }
        for (int k = 0; k <= BOLTS; k++) {
            double age = t - CavaliereEntity.boltTick(k);
            Vec3 c = ent.boltPos(k);
            if (c == null || age < 0 || age > 26) {
                continue;
            }
            boolean big = k == BOLTS;
            double r = big ? CavaliereSpell.BIG_RADIUS : CavaliereSpell.BOLT_RADIUS;
            double dur = big ? 26 : 14;
            if (age < dur) {
                p.plane(c.add(0, 0.12, 0), X, Z, r * ease(age / (big ? 12.0 : 7.0)), 0, Pen.WHITE, (float) (0.85 * (1 - age / dur)));
                p.plane(c.add(0, 0.13, 0), X, Z, r * 0.6 * ease(age / (big ? 10.0 : 6.0)), 1.0, CYAN, (float) (0.6 * (1 - age / dur)));
                if (big) {
                    for (int i = 0; i < 3; i++) {
                        double wr = age - i * 2.5;
                        if (wr >= 0 && wr < 16) {
                            p.plane(c.add(0, 0.5 + wr * 0.7, 0), X, Z, 3 + 12 * ease(wr / 16.0), 0, ELEC, (float) (0.5 * (1 - wr / 16.0)));
                        }
                    }
                }
            }
        }
    }

    private void beamPass() {
        List<Vec3> tr = ent.trail();
        int n = tr.size();
        for (int i = 0; i + 1 < n; i++) {
            Vec3 a = tr.get(i);
            Vec3 b = tr.get(i + 1);
            Vec3 d = b.subtract(a);
            if (d.x * d.x + d.z * d.z < 1.0E-5) {
                continue;
            }
            double f = (double) (n - 1 - i) / n;
            Vec3 side = new Vec3(d.z, 0, -d.x).normalize();
            double w = 4.4 * (1.0 - 0.6 * f);
            float al = (float) (1.0 - f);
            Vec3 ga = a.add(0, 0.08, 0);
            Vec3 gb = b.add(0, 0.08, 0);
            p.ribbonSide(ga, gb, side, w, w, al * 0.4f, al * 0.4f, 0f, 1f, ELEC);
            p.ribbonSide(ga.add(0, 0.01, 0), gb.add(0, 0.01, 0), side, w * 0.4, w * 0.4, al * 0.75f, al * 0.75f, 0f, 1f, CYAN);
        }
        // headlight on the road
        if (vis > 0.05 && t < RIDE_END + 4) {
            Vec3 a = A.add(F.scale(3.0)).add(0, 0.1, 0);
            Vec3 b = A.add(F.scale(20.0)).add(0, 0.1, 0);
            p.ribbonSide(a, b, S, 1.4, 7.0, (float) (0.4 * vis), 0f, 0f, 1f, YEL);
        }
        // pillar of the giant bolt
        Vec3 c = ent.boltPos(BOLTS);
        double age = t - CavaliereEntity.BIG;
        if (c != null && age >= 0 && age < 14) {
            Vec3 toCam = p.cam.subtract(c);
            Vec3 side = new Vec3(toCam.z, 0, -toCam.x);
            side = side.lengthSqr() < 1.0E-6 ? X : side.normalize();
            double fade = 1.0 - age / 14.0;
            double[] ws = {11.0, 5.0};
            float[][] cols = {ELEC, Pen.WHITE};
            for (int l = 0; l < 2; l++) {
                for (int s = 0; s < 6; s++) {
                    double y0 = 90.0 * s / 6;
                    double y1 = 90.0 * (s + 1) / 6;
                    p.ribbonSide(c.add(0, y0, 0), c.add(0, y1, 0), side, ws[l] * fade, ws[l] * fade, (float) (0.6 * fade), (float) (0.6 * fade), 0f, 1f, cols[l]);
                }
            }
        }
    }

    private void linePass() {
        // frame of the bike
        if (vis > 0.02) {
            float al = (float) vis;
            Vec3 rear = wheelCentre(-1);
            Vec3 front = wheelCentre(1);
            Vec3 seat = A.add(F.scale(-0.7)).add(0, 2.1, 0);
            Vec3 tank = A.add(F.scale(0.9)).add(0, 2.4, 0);
            Vec3 bar = A.add(F.scale(1.6)).add(0, 3.0, 0);
            Vec3 lowA = A.add(F.scale(-1.0)).add(0, 1.1, 0);
            Vec3 lowB = A.add(F.scale(1.0)).add(0, 1.1, 0);
            line(rear, seat, 0.13, al, ELEC);
            line(seat, tank, 0.16, al, CYAN);
            line(tank, bar, 0.12, al, ELEC);
            line(bar, front, 0.13, al, ELEC);
            line(tank, front, 0.11, al * 0.8f, VIOLET);
            line(rear, lowA, 0.12, al, VIOLET);
            line(lowA, lowB, 0.28, al, CYAN);
            line(lowB, front, 0.12, al, VIOLET);
            line(bar.add(S.scale(0.95)), bar.subtract(S.scale(0.95)), 0.09, al, YEL);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 c = wheelCentre(s);
                for (int i = 0; i < 5; i++) {
                    double ang = spin * s + i * Math.PI * 2 / 5;
                    Vec3 d = Y.scale(Math.cos(ang) * WR * 0.95).add(F.scale(Math.sin(ang) * WR * 0.95));
                    line(c.subtract(d.scale(0.2)), c.add(d), 0.05, al * 0.8f, CYAN);
                }
            }
            // arcs jumping around the bike
            long fl = seed + (long) (t * 1.5) * 131L;
            Random r = new Random(fl);
            int arcs = t >= RIDE0 ? 12 : 6;
            for (int i = 0; i < arcs; i++) {
                Vec3 from = r.nextBoolean() ? wheelCentre(r.nextBoolean() ? 1 : -1).add(Y.scale((r.nextDouble() - 0.5) * 2 * WR)).add(F.scale((r.nextDouble() - 0.5) * 2 * WR)) : A.add(F.scale((r.nextDouble() - 0.5) * 3)).add(0, 1 + r.nextDouble() * 2, 0);
                Vec3 to = from.add((r.nextDouble() - 0.5) * 5, (r.nextDouble() - 0.4) * 4, (r.nextDouble() - 0.5) * 5);
                zap(from, to, 4, 0.35, 0.05, (float) (0.9 * vis), r.nextInt(4) == 0 ? YEL : ELEC, fl + i * 17L);
            }
        }
        // speed lines behind the bike
        if (t >= RIDE0 && t < RIDE_END + 3) {
            Random r = new Random(seed + 8);
            for (int i = 0; i < 16; i++) {
                double lat = (r.nextDouble() - 0.5) * 7;
                double y = 0.4 + r.nextDouble() * 3.2;
                double st = 2.5 + r.nextDouble() * 5 + ((t * (1 + r.nextDouble())) % 3);
                double len = 6 + r.nextDouble() * 10;
                Vec3 a = A.subtract(F.scale(st)).add(S.scale(lat)).add(0, y, 0);
                line(a, a.subtract(F.scale(len)), 0.05, (float) (0.6 * vis), YEL);
            }
        }
        // arcs standing up from the trail
        List<Vec3> tr = ent.trail();
        for (int i = 2; i + 1 < tr.size(); i += 3) {
            double f = (double) (tr.size() - 1 - i) / tr.size();
            Random r = new Random(seed + i * 53L + (long) (t * 0.8));
            Vec3 b = tr.get(i).add((r.nextDouble() - 0.5) * 3, 0.1, (r.nextDouble() - 0.5) * 3);
            zap(b, b.add((r.nextDouble() - 0.5) * 2, 1.5 + r.nextDouble() * 3, (r.nextDouble() - 0.5) * 2), 3, 0.3, 0.05, (float) (0.85 * (1 - f)), ELEC, seed + i * 7L + (long) (t * 0.8));
        }
        // gathering lightning during the summon
        if (t < RIDE0) {
            double k = t / RIDE0;
            Random r = new Random(seed + 33 + (long) (t * 1.2));
            for (int i = 0; i < 5; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = 3 + r.nextDouble() * 4;
                Vec3 g = A.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad);
                zap(g.add(0, 28, 0), g, 9, 1.4, 0.06, (float) (0.3 + 0.6 * k), ELEC, seed + i * 11L + (long) (t * 1.2));
            }
        }
        // the storm
        for (int k = 0; k <= BOLTS; k++) {
            double age = t - CavaliereEntity.boltTick(k);
            Vec3 c = ent.boltPos(k);
            boolean big = k == BOLTS;
            double dur = big ? 22 : 12;
            if (c == null || age < 0 || age >= dur) {
                continue;
            }
            float al = (float) (1.0 - 0.8 * age / dur);
            long sd = seed + k * 211L + (long) (age / 2) * 17L;
            double w = big ? 0.55 : 0.22;
            zap(c.add(0, 80, 0), c, 20, big ? 2.6 : 1.6, w, al, big ? CYAN : ELEC, sd);
            if (big) {
                zap(c.add(0, 80, 0), c, 20, 3.6, 0.3, al * 0.8f, ELEC, sd + 5);
            }
            Random r = new Random(sd + 1);
            int branches = big ? 7 : 3;
            for (int i = 0; i < branches; i++) {
                double h = 3 + r.nextDouble() * 40;
                Vec3 from = c.add((r.nextDouble() - 0.5) * 3, h, (r.nextDouble() - 0.5) * 3);
                Vec3 to = from.add((r.nextDouble() - 0.5) * 16, -(4 + r.nextDouble() * 12), (r.nextDouble() - 0.5) * 16);
                zap(from, to, 5, 0.8, 0.1, al * 0.8f, ELEC, sd + 40 + i);
            }
            if (age < 6) {
                for (int i = 0; i < (big ? 18 : 8); i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double len = (big ? 14 : 6) * (0.4 + r.nextDouble());
                    Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                    zap(c.add(0, 0.3, 0), c.add(d.scale(len)).add(0, 0.3, 0), 5, 0.9, 0.07, (float) (1 - age / 6.0), CYAN, sd + 90 + i);
                }
            }
        }
    }

    private void glintPass() {
        if (vis > 0.02) {
            for (int s = -1; s <= 1; s += 2) {
                p.billboard(wheelCentre(s), 0.7, spin, Pen.WHITE, (float) (0.8 * vis));
            }
            p.billboard(A.add(F.scale(1.9)).add(0, 2.5, 0), 0.9 + 0.2 * Math.sin(t), 0, YEL, (float) (0.9 * vis));
        }
        if (t >= RIDE0 && t < RIDE0 + 6) {
            p.billboard(A.add(0, 1.4, 0), 8 * (1 - (t - RIDE0) / 6.0), 0, Pen.CORE, (float) (0.8 * (1 - (t - RIDE0) / 6.0)));
        }
        for (int[] rm : ent.rams()) {
            double a = t - rm[1];
            Entity en = ent.level().getEntity(rm[0]);
            if (en != null && a >= 0 && a < 7) {
                p.billboard(new Vec3(en.getX(), en.getY() + en.getBbHeight() * 0.5, en.getZ()), 3.0, 0, Pen.WHITE, (float) (1 - a / 7.0));
            }
        }
        for (int k = 0; k <= BOLTS; k++) {
            double age = t - CavaliereEntity.boltTick(k);
            Vec3 c = ent.boltPos(k);
            boolean big = k == BOLTS;
            if (c == null || age < 0 || age > 8) {
                continue;
            }
            double sz = (big ? 16 : 6) * (1 - age / 8.0) + 0.5;
            p.billboard(c.add(0, 1.0, 0), sz, 0, Pen.WHITE, (float) (1 - age / 8.0));
            double r = big ? CavaliereSpell.BIG_RADIUS : CavaliereSpell.BOLT_RADIUS;
            AABB box = new AABB(c.x - r, c.y - 6, c.z - r, c.x + r, c.y + 14, c.z + r);
            for (LivingEntity le : ent.level().getEntitiesOfClass(LivingEntity.class, box)) {
                if (le == own) {
                    continue;
                }
                double dx = le.getX() - c.x;
                double dz = le.getZ() - c.z;
                if (dx * dx + dz * dz <= r * r) {
                    p.billboard(new Vec3(le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ()), 2.4, 0, Pen.WHITE, (float) (0.9 * (1 - age / 8.0)));
                }
            }
        }
        // sparks along the trail
        List<Vec3> tr = ent.trail();
        Random r = new Random(seed + (long) (t * 0.7));
        for (int i = 0; i < Math.min(18, tr.size()); i++) {
            Vec3 b = tr.get(r.nextInt(tr.size()));
            p.billboard(b.add((r.nextDouble() - 0.5) * 3, 0.2 + r.nextDouble() * 2, (r.nextDouble() - 0.5) * 3), 0.25 + r.nextDouble() * 0.3, 0, YEL, 0.9f);
        }
    }

    private void glowPass() {
        if (vis > 0.02) {
            p.billboard(A.add(0, 1.6, 0), 3.2, 0, ELEC, (float) (0.16 * vis));
        }
    }
}
