package com.example.jjkspells.vergil.client;

import com.example.jjkspells.vergil.entity.CrescentWaveEntity;
import com.example.jjkspells.vergil.spell.CrescentWaveSpell;
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

import java.util.List;
import java.util.Random;

/**
 * Draws "Crescent Wave": a curved wall of cut space, 40 blocks wide and tapering to points like a crescent, with thin bright edge lines and two
 * fading echoes, sweeping forward 84 blocks; glass flies off its front, dimensional scars stay hanging behind it, every victim is sliced twice
 * (once as the wave crosses, once when the marked cut catches up). Glow stays low: thin lines, a translucent streaked sheet and small glints.
 */
public class CrescentWaveRenderer extends EntityRenderer<CrescentWaveEntity> {
    private static final int LAUNCH = CrescentWaveEntity.LAUNCH;
    private static final int STEPS = CrescentWaveEntity.STEPS;
    private static final int DELAY = CrescentWaveEntity.DELAY;
    private static final int END = CrescentWaveEntity.END;
    private static final double W = CrescentWaveEntity.W;
    private static final double CURVE = CrescentWaveEntity.CURVE;
    private static final double SPEED = CrescentWaveEntity.SPEED;
    private static final double DIST = CrescentWaveSpell.DISTANCE;
    private static final double HMAX = 16.0;
    private static final int SEG = 36;
    /** Crescent shape: outer arc radius, half opening angle, thickness of the moon at its middle, height of its centre. */
    private static final double RC = 10.5;
    private static final double PHI0 = Math.toRadians(68);
    private static final double TH = 6.0;
    private static final double HC = RC * Math.sin(PHI0) + 0.6;
    private static final int SX = 14;
    private static final int SP = 14;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 O;
    private Vec3 d;
    private Vec3 side;
    private int seed;
    private CrescentWaveEntity ent;
    private double dist;
    private double waveA;
    private double base;

    public CrescentWaveRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(CrescentWaveEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(CrescentWaveEntity e) {
        return VergilRender.GLOW;
    }

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void add(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(VergilRender.additive(tex));
    }

    private void alpha(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(VergilRender.alpha(tex));
    }

    private void line(Vec3 a, Vec3 b, double w, float al, boolean violet) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.6, al * 0.45f, 0f, 1f, violet ? Pen.VIOLET : Pen.CYAN);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.CORE);
    }

    private double height(double x, double scale) {
        double q = 1.0 - (x / W) * (x / W);
        return q <= 0 ? 0 : HMAX * scale * Math.sqrt(q);
    }

    private Vec3 front(double x, double distance, double y) {
        return O.add(d.scale(distance - CURVE * x * x)).add(side.scale(x)).add(0, y, 0);
    }


    /** Point of the crescent shell: x across, f in 0..1 along the arc (tip to tip), inner selects the concave face. */
    private Vec3 moon(double x, double f, boolean inner, double distance, double scale) {
        double k = Math.sqrt(Math.max(0.0, 1.0 - (x / W) * (x / W)));
        double R = RC * k * scale;
        double phi = -PHI0 + 2 * PHI0 * f;
        double cp = Math.cos(phi) - Math.cos(PHI0);
        double fwd = distance + R * cp - (inner ? TH * k * scale * cp / (1.0 - Math.cos(PHI0)) : 0.0);
        return front(x, fwd, base - O.y + HC * scale + R * Math.sin(phi));
    }

    @Override
    public void render(CrescentWaveEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        seed = e.getId() * 1931;
        O = e.getPosition(pt);
        d = e.dir();
        d = d.lengthSqr() < 1.0E-6 ? Z : d.normalize();
        side = new Vec3(-d.z, 0, d.x);
        base = O.y - 1.0;
        dist = c01((t - LAUNCH) / (double) STEPS) * DIST;
        waveA = t < LAUNCH ? 0 : ease((t - LAUNCH) / 2.0) * (1.0 - c01((t - LAUNCH - STEPS + 2) / 6.0));
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), O, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        alpha(buf, VergilRender.SHARD);
        shardPass();
        add(buf, VergilRender.WAVE);
        wavePass();
        add(buf, VergilRender.RING);
        ringPass();
        add(buf, VergilRender.LINE);
        linePass();
        add(buf, VergilRender.SLASH);
        slashPass();
        add(buf, VergilRender.GLINT);
        glintPass();
    }

    private Vec3 targetCentre(int id) {
        Entity en = ent.level().getEntity(id);
        if (en == null || !en.isAlive()) {
            return null;
        }
        return new Vec3(en.getX(), en.getY() + en.getBbHeight() * 0.5, en.getZ());
    }

    // ------------------------------------------------------------------ passes

    /** The translucent streaked sheet of the wave and its two echoes. */
    private void wavePass() {
        if (waveA <= 0.004) {
            return;
        }
        double[] back = {0, 5, 10};
        double[] sc = {1.0, 0.8, 0.6};
        float[] al = {0.9f, 0.45f, 0.22f};
        for (int l = 0; l < 3; l++) {
            double dd = dist - back[l];
            if (dd <= 0.5) {
                continue;
            }
            for (int face = 0; face < 2; face++) {
                boolean inner = face == 1;
                for (int i = 0; i < SX; i++) {
                    double x0 = -W + 2 * W * i / SX;
                    double x1 = -W + 2 * W * (i + 1) / SX;
                    for (int j = 0; j < SP; j++) {
                        double f0 = (double) j / SP;
                        double f1 = (double) (j + 1) / SP;
                        Vec3 a00 = moon(x0, f0, inner, dd, sc[l]);
                        Vec3 a10 = moon(x1, f0, inner, dd, sc[l]);
                        Vec3 a11 = moon(x1, f1, inner, dd, sc[l]);
                        Vec3 a01 = moon(x0, f1, inner, dd, sc[l]);
                        float a = (float) (al[l] * waveA * (inner ? 0.8 : 1.0));
                        p.quad(a00, a10, a11, a01, (float) i / SX, (float) f0, (float) (i + 1) / SX, (float) f1, l == 0 ? Pen.ICE : Pen.CYAN, a);
                    }
                }
            }
        }
    }

    private void linePass() {
        // blade-draw path line
        double pathA = (t < LAUNCH ? ease(t / 8.0) * 0.35 : 0.3 * (1.0 - c01((t - LAUNCH - STEPS) / 20.0)));
        if (pathA > 0.01) {
            line(O.add(0, 0.2, 0), O.add(d.scale(DIST * (t < LAUNCH ? ease(t / 8.0) : 1.0))).add(0, 0.2, 0), 0.06, (float) pathA, false);
        }
        // sharp edge lines of the crescent: both tips swept across the width and the arcs at a few heights across
        if (waveA > 0.004) {
            for (int i = 0; i < SX; i++) {
                double x0 = -W + 2 * W * i / SX;
                double x1 = -W + 2 * W * (i + 1) / SX;
                line(moon(x0, 0, false, dist, 1.0), moon(x1, 0, false, dist, 1.0), 0.1, (float) (0.8 * waveA), false);
                line(moon(x0, 1, false, dist, 1.0), moon(x1, 1, false, dist, 1.0), 0.1, (float) (0.8 * waveA), false);
            }
            double[] xs = {0.0, -0.5 * W, 0.5 * W, -0.8 * W, 0.8 * W};
            for (int xi = 0; xi < xs.length; xi++) {
                for (int j = 0; j < SP; j++) {
                    double f0 = (double) j / SP;
                    double f1 = (double) (j + 1) / SP;
                    float k = xi == 0 ? 1.0f : 0.55f;
                    line(moon(xs[xi], f0, false, dist, 1.0), moon(xs[xi], f1, false, dist, 1.0), xi == 0 ? 0.2 : 0.1, (float) (waveA * k), false);
                    if (xi == 0) {
                        line(moon(xs[xi], f0, true, dist, 1.0), moon(xs[xi], f1, true, dist, 1.0), 0.08, (float) (0.6 * waveA), false);
                    }
                }
            }
        }
        // scars hanging where the wave has passed, flashing when the marked cuts land
        double flashAt = LAUNCH + STEPS + DELAY;
        for (int i = 0; i < 40; i++) {
            Random r = new Random(seed + i * 977L + 3);
            double along = r.nextDouble() * (DIST - 4);
            double lat = (r.nextDouble() * 2 - 1) * W * 0.85;
            double spawn = LAUNCH + (along + CURVE * lat * lat) / SPEED;
            double age = t - spawn;
            if (age < 0) {
                continue;
            }
            double h = 3.0 + r.nextDouble() * 13.0;
            double grow = ease(age / 4.0);
            double a = t < flashAt ? 0.45 : 1.0 - c01((t - flashAt) / 14.0);
            Vec3 b = O.add(d.scale(along)).add(side.scale(lat)).add(0, base - O.y, 0);
            line(b, b.add(0, h * grow, 0), 0.07, (float) (a * grow), r.nextInt(7) == 0);
        }
        // slice lines through every victim, once at the wave cut and once at the delayed cut
        List<int[]> hits = ent.hits();
        for (int[] h : hits) {
            double a1 = t - h[1];
            double a2 = t - (h[1] + DELAY);
            Vec3 c = targetCentre(h[0]);
            if (c == null) {
                continue;
            }
            Random r = new Random(seed + h[0] * 131L);
            if (a1 >= 0 && a1 < 10) {
                float al = (float) (1.0 - 0.7 * a1 / 10.0);
                for (int s = 0; s < 2; s++) {
                    Vec3 dd = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                    double l = 8.0 * ease(a1 / 2.0);
                    line(c.subtract(dd.scale(l)), c.add(dd.scale(l)), 0.07, al, false);
                }
            }
            if (a2 >= 0 && a2 < 12) {
                float al = (float) (1.0 - 0.6 * a2 / 12.0);
                for (int s = 0; s < 3; s++) {
                    Vec3 dd = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                    double l = 10.0 * ease(a2 / 2.0);
                    line(c.subtract(dd.scale(l)), c.add(dd.scale(l)), 0.08, al, s == 2);
                }
            }
        }
    }

    private void slashPass() {
        Entity own = ent.owner();
        if (own != null && t > 5 && t < LAUNCH + 6) {
            Vec3 at = own.getPosition(1.0f).add(0, 1.2, 0).add(d.scale(1.4));
            double a = t - 5;
            p.billboard(at, 3.2 * ease(a / 4.0), -0.4, Pen.CORE, (float) (1.0 - c01((t - LAUNCH) / 6.0)), 0f, 0f, 1f, 1f);
        }
        for (int[] h : ent.hits()) {
            Vec3 c = targetCentre(h[0]);
            if (c == null) {
                continue;
            }
            Random r = new Random(seed + h[0] * 977L);
            double a1 = t - h[1];
            double a2 = t - (h[1] + DELAY);
            if (a1 >= 0 && a1 < 5) {
                p.billboard(c, 3.6, r.nextDouble() * 6.28, Pen.ICE, (float) (1.0 - a1 / 5.0), 0f, 0f, 1f, 1f);
            }
            if (a2 >= 0 && a2 < 6) {
                p.billboard(c, 4.4, r.nextDouble() * 6.28, Pen.ICE, (float) (1.0 - a2 / 6.0), 0f, 0f, 1f, 1f);
            }
        }
    }

    private void glintPass() {
        Entity own = ent.owner();
        if (own != null && t < LAUNCH + 4) {
            p.billboard(own.getPosition(1.0f).add(0, 1.2, 0).add(d.scale(1.2)), 1.2 + 0.1 * t, 0, Pen.WHITE, (float) (t < LAUNCH ? 0.5 + 0.1 * t : 1.0 - (t - LAUNCH) / 4.0));
        }
        // sparkles along the wave front
        if (waveA > 0.004) {
            for (int i = 0; i < 32; i++) {
                Random r = new Random(seed + i * 53L + (long) (t * 0.5));
                double x = (r.nextDouble() * 2 - 1) * W;
                Vec3 at = moon(x, r.nextDouble(), r.nextBoolean(), dist, 1.0);
                p.billboard(at, 0.5 + r.nextDouble() * 0.7, 0, Pen.WHITE, (float) (waveA * 0.8));
            }
        }
        for (int[] h : ent.hits()) {
            Vec3 c = targetCentre(h[0]);
            if (c == null) {
                continue;
            }
            double a1 = t - h[1];
            double a2 = t - (h[1] + DELAY);
            if (a1 >= 0 && a1 < 7) {
                p.billboard(c, 2.4, 0, Pen.WHITE, (float) (1.0 - a1 / 7.0));
            }
            if (a2 >= 0 && a2 < 8) {
                p.billboard(c, 3.2 + a2 * 0.3, 0, Pen.WHITE, (float) (1.0 - a2 / 8.0));
            }
            // blinking mark above the head while the cut is catching up
            if (a1 >= 4 && a2 < 0) {
                Entity en = ent.level().getEntity(h[0]);
                if (en != null) {
                    double prog = a1 / DELAY;
                    double blink = 0.5 + 0.5 * Math.sin(a1 * (0.5 + 1.5 * prog));
                    p.billboard(new Vec3(en.getX(), en.getY() + en.getBbHeight() + 1.2, en.getZ()), 0.6 + 0.6 * prog, a1 * 0.1, Pen.ICE, (float) (0.4 + 0.5 * blink));
                }
            }
        }
    }

    private void ringPass() {
        if (t < LAUNCH) {
            double a = ease(t / 8.0);
            p.plane(O.add(0, 0.08, 0), X, Z, 7.0 * a, t * 0.1, Pen.ICE, (float) (0.7 * a));
            p.plane(O.add(0, 0.1, 0), X, Z, 4.0 * a, -t * 0.15, Pen.CORE, (float) (0.5 * a));
        } else if (t < LAUNCH + 12) {
            double a = (t - LAUNCH) / 12.0;
            p.plane(O.add(0, 0.1, 0), X, Z, 7.0 + 8.0 * ease(a), 0, Pen.CORE, (float) (0.8 * (1 - a)));
        }
        for (int[] h : ent.hits()) {
            double a2 = t - (h[1] + DELAY);
            if (a2 < 0 || a2 > 12) {
                continue;
            }
            Entity en = ent.level().getEntity(h[0]);
            if (en != null) {
                p.plane(new Vec3(en.getX(), en.getY() + 0.1, en.getZ()), X, Z, 1.0 + 4.0 * ease(a2 / 12.0), 0, Pen.ICE, (float) (0.5 * (1 - a2 / 12.0)));
            }
        }
    }

    /** Glass thrown off the wave front. */
    private void shardPass() {
        for (int i = 0; i < 110; i++) {
            Random r = new Random(seed + i * 211L + 5);
            double along = r.nextDouble() * (DIST - 2);
            double lat = (r.nextDouble() * 2 - 1) * W * 0.95;
            double spawn = LAUNCH + (along + CURVE * lat * lat) / SPEED;
            double age = t - spawn;
            if (age < 0) {
                continue;
            }
            Vec3 origin = O.add(d.scale(along)).add(side.scale(lat)).add(0, base - O.y + 0.5 + r.nextDouble() * 10.0, 0);
            Vec3 v = side.scale((lat >= 0 ? 1 : -1) * (0.1 + 0.5 * r.nextDouble())).add(d.scale(0.2 + 0.5 * r.nextDouble())).add(0, 0.2 + 0.4 * r.nextDouble(), 0);
            Vec3 pos = origin.add(v.scale(age)).add(0, -0.008 * age * age, 0);
            double al = 0.9 * c01(age / 2.0) * (1.0 - c01((age - 25) / 25.0));
            shard(pos, 0.4 + r.nextDouble() * 1.2, r.nextDouble() * 6.28 + age * (0.1 + r.nextDouble() * 0.2), r.nextInt(4), (float) al);
        }
    }

    private void shard(Vec3 c, double half, double rot, int cell, float al) {
        if (al <= 0.01f) {
            return;
        }
        double u0 = (cell % 2) * 0.5;
        double v0 = (cell / 2) * 0.5;
        Vec3 L = new Vec3(p.left.x(), p.left.y(), p.left.z());
        Vec3 U = new Vec3(p.up.x(), p.up.y(), p.up.z());
        double tilt = 0.55 * Math.sin(rot * 1.7 + cell);
        Vec3 look = p.cam.subtract(c).normalize();
        Vec3 ax = L.scale(Math.cos(rot) * half).add(U.scale(Math.sin(rot) * half));
        Vec3 ay = L.scale(-Math.sin(rot) * half).add(U.scale(Math.cos(rot) * half)).add(look.scale(-tilt * half));
        p.quad(c.subtract(ax).subtract(ay), c.add(ax).subtract(ay), c.add(ax).add(ay), c.subtract(ax).add(ay),
                (float) u0, (float) v0, (float) (u0 + 0.5), (float) (v0 + 0.5), Pen.WHITE, al);
    }
}
