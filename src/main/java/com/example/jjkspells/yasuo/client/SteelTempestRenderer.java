package com.example.jjkspells.yasuo.client;

import com.example.jjkspells.yasuo.entity.SteelTempestEntity;
import com.example.jjkspells.yasuo.spell.SteelTempestSpell;
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
 * Draws "Steel Tempest": a spectral katana thrusts forward three times with spiralling wind lines and expanding rings around the lane, and the third thrust
 * births a tornado: three nested cones of streaked wind rotating at different speeds, helical wind ribbons, orbiting debris glints, a dust ring on the ground
 * and speed lines trailing behind. Glow stays low: thin lines, rings, solid sprites and small glints.
 */
public class SteelTempestRenderer extends EntityRenderer<SteelTempestEntity> {
    private static final int T0 = SteelTempestEntity.T0;
    private static final int LAUNCH = SteelTempestEntity.LAUNCH;
    private static final int TT = SteelTempestEntity.TORNADO_TICKS;
    private static final int END = SteelTempestEntity.END;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final double HEIGHT = SteelTempestSpell.TORNADO_HEIGHT;
    private static final int SEG = 18;
    private static final int LV = 9;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 D;
    private Vec3 S;
    private Vec3 O;
    private Entity own;
    private SteelTempestEntity ent;
    private int seed;

    public SteelTempestRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(SteelTempestEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SteelTempestEntity e) {
        return YasuoRender.GLOW;
    }

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void add(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(YasuoRender.additive(tex));
    }

    private void alpha(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(YasuoRender.alpha(tex));
    }

    private void line(Vec3 a, Vec3 b, double w, float al, float[] col) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.4, al * 0.45f, 0f, 1f, col);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.CORE);
    }

    @Override
    public void render(SteelTempestEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        own = e.owner();
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        seed = e.getId() * 1699;
        A = e.getPosition(pt);
        D = e.dir();
        D = D.lengthSqr() < 1.0E-6 ? Z : D.normalize();
        S = new Vec3(D.z, 0, -D.x);
        O = own != null ? own.getPosition(pt) : A;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        alpha(buf, YasuoRender.KATANA);
        katanaPass();
        add(buf, YasuoRender.MIST);
        mistPass();
        add(buf, YasuoRender.WIND);
        tornadoPass();
        add(buf, YasuoRender.RING);
        ringPass();
        add(buf, YasuoRender.LINE);
        linePass();
        add(buf, YasuoRender.GLINT);
        glintPass();
    }

    private double thrustLen(int k) {
        return k == 2 ? SteelTempestSpell.LAST_LENGTH : SteelTempestSpell.THRUST_LENGTH;
    }

    private Vec3 chest() {
        return O.add(0, 1.35, 0);
    }

    // ------------------------------------------------------------------ passes

    private void katanaPass() {
        if (t < T0) {
            double k = ease(t / T0);
            Vec3 h = chest().add(S.scale(0.6)).add(D.scale(0.2));
            Vec3 tip = h.add(D.scale(3.8 * k)).add(0, 0.3 * Math.sin(t), 0);
            blade(h, tip, (float) k);
        }
        for (int k = 0; k < 3; k++) {
            double age = t - SteelTempestEntity.thrustTick(k);
            if (age < 0 || age > 10) {
                continue;
            }
            double len = thrustLen(k);
            Vec3 h = chest().add(D.scale(0.4)).subtract(S.scale(0.2 * (1 - k % 2 * 2)));
            Vec3 tip = h.add(D.scale(len * ease(age / 2.5)));
            blade(h, tip, (float) (1.0 - c01((age - 3) / 7.0)));
        }
    }

    private void blade(Vec3 h, Vec3 tip, float al) {
        if (al <= 0.01f) {
            return;
        }
        double w = h.distanceTo(tip) * ASPECT;
        p.ribbon(h, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
    }

    private void mistPass() {
        int tt = (int) (t - LAUNCH);
        Vec3 ax = ent.tornadoAt(t - LAUNCH);
        if (ax == null || t < LAUNCH) {
            return;
        }
        double env = ease((t - LAUNCH) / 6.0) * (1.0 - c01((t - LAUNCH - TT) / 10.0));
        p.plane(ax.add(0, 0.12, 0), X, Z, 7.0 * env, t * 0.04, Pen.PALE, (float) (0.3 * env));
        p.plane(ax.add(0, 0.2, 0), X, Z, 4.5 * env, -t * 0.06, Pen.SKY, (float) (0.25 * env));
    }

    private void tornadoPass() {
        if (t < LAUNCH) {
            return;
        }
        double tt = t - LAUNCH;
        Vec3 ax = ent.tornadoAt(tt);
        if (ax == null) {
            return;
        }
        double env = ease(tt / 6.0) * (1.0 - c01((tt - TT) / 10.0));
        if (env <= 0.01) {
            return;
        }
        double h = HEIGHT * env;
        float[][] cols = {Pen.PALE, Pen.AQUA, Pen.SKY};
        double[] fs = {1.0, 0.72, 0.46};
        float[] als = {0.6f, 0.55f, 0.5f};
        for (int l = 0; l < 3; l++) {
            double spin = t * 0.16 * (l + 1) * (l % 2 == 0 ? 1 : -1) * 0.5;
            for (int j = 0; j < LV; j++) {
                double f0 = (double) j / LV;
                double f1 = (double) (j + 1) / LV;
                float a = (float) (als[l] * env * (0.3 + 0.7 * Math.sin(Math.PI * (j + 0.5) / LV)));
                for (int i = 0; i < SEG; i++) {
                    double a0 = spin + 2 * Math.PI * i / SEG;
                    double a1 = spin + 2 * Math.PI * (i + 1) / SEG;
                    Vec3 p0 = ringPt(ax, f0, a0, fs[l], h, env);
                    Vec3 p1 = ringPt(ax, f0, a1, fs[l], h, env);
                    Vec3 p2 = ringPt(ax, f1, a1, fs[l], h, env);
                    Vec3 p3 = ringPt(ax, f1, a0, fs[l], h, env);
                    p.quad(p0, p1, p2, p3, (float) i / SEG, (float) f0, (float) (i + 1) / SEG, (float) f1, cols[l], a);
                }
            }
        }
    }

    private Vec3 ringPt(Vec3 ax, double f, double ang, double factor, double h, double env) {
        double r = (1.3 + 4.2 * Math.pow(f, 1.2)) * factor * env;
        double y = h * f;
        double sway = Math.sin(t * 0.2 + f * 4.0) * 0.5 * f;
        Vec3 c = ax.add(0, y, 0).subtract(D.scale(f * f * 3.5)).add(S.scale(sway));
        return c.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
    }

    private void ringPass() {
        Vec3 g = O.add(0, 0.08, 0);
        if (t < T0) {
            double k = ease(t / T0);
            p.plane(g, X, Z, 3.0 * k, t * 0.3, Pen.AQUA, (float) (0.5 * k));
        }
        for (int k = 0; k < 3; k++) {
            double age = t - SteelTempestEntity.thrustTick(k);
            if (age < 0 || age > 12) {
                continue;
            }
            double len = thrustLen(k);
            double scale = k == 2 ? 1.5 : 1.0;
            double f = 1.0 - age / 12.0;
            for (int j = 0; j < 4; j++) {
                double d = len * (0.2 + 0.22 * j) * ease(age / 5.0);
                Vec3 c = chest().add(D.scale(d));
                p.plane(c, Y, S, (0.9 + 0.7 * j) * scale, j * 0.7, j % 2 == 0 ? Pen.AQUA : Pen.PALE, (float) (0.6 * f));
            }
        }
        double tt = t - LAUNCH;
        if (tt >= 0 && tt < 10) {
            Vec3 ax = ent.tornadoAt(tt);
            if (ax != null) {
                p.plane(ax.add(0, 0.1, 0), X, Z, 2 + 9 * ease(tt / 8.0), 0, Pen.CORE, (float) (0.8 * (1 - tt / 10.0)));
            }
        }
        if (tt >= TT && tt < TT + 12) {
            Vec3 ax = ent.tornadoAt(TT);
            double a = tt - TT;
            p.plane(ax.add(0, 0.1, 0), X, Z, 3 + 10 * ease(a / 8.0), 0, Pen.CORE, (float) (0.8 * (1 - a / 12.0)));
            p.plane(ax.add(0, 0.12, 0), X, Z, 2 + 7 * ease(a / 8.0), 1.0, Pen.AQUA, (float) (0.6 * (1 - a / 12.0)));
        }
    }

    private void linePass() {
        Random r = new Random(seed + 3);
        if (t < T0) {
            double k = t / T0;
            for (int i = 0; i < 14; i++) {
                double ang = r.nextDouble() * Math.PI * 2 + t * 0.5;
                double rr = 0.8 + r.nextDouble() * 1.6;
                Vec3 a = O.add(Math.cos(ang) * rr, 0.2 + r.nextDouble() * 2.2, Math.sin(ang) * rr);
                Vec3 tg = new Vec3(-Math.sin(ang), 0.15, Math.cos(ang));
                line(a, a.add(tg.scale(1.6)), 0.04, (float) (0.4 + 0.6 * k), Pen.AQUA);
            }
        }
        for (int k = 0; k < 3; k++) {
            double age = t - SteelTempestEntity.thrustTick(k);
            if (age < 0 || age > 9) {
                continue;
            }
            double len = thrustLen(k);
            double e = ease(age / 3.0);
            float al = (float) (1.0 - age / 9.0);
            Vec3 start = chest();
            Random q = new Random(seed + k * 131L);
            for (int i = 0; i < 12; i++) {
                double th = q.nextDouble() * Math.PI * 2;
                double rad = (0.5 + q.nextDouble() * 0.9) * (k == 2 ? 1.5 : 1.0);
                double[] fs = {0.1, 0.35, 0.65, 1.0};
                Vec3 prev = null;
                for (double f : fs) {
                    double ang = th + f * 5.0;
                    double rr = rad * (0.4 + f);
                    Vec3 pt = start.add(D.scale(len * f * e)).add(S.scale(Math.cos(ang) * rr)).add(0, Math.sin(ang) * rr, 0);
                    if (prev != null) {
                        line(prev, pt, 0.05, al, Pen.AQUA);
                    }
                    prev = pt;
                }
            }
        }
        double tt = t - LAUNCH;
        Vec3 ax = tt > 0 ? ent.tornadoAt(tt) : null;
        if (ax != null) {
            double env = ease(tt / 6.0) * (1.0 - c01((tt - TT) / 10.0));
            if (env > 0.01) {
                double h = HEIGHT * env;
                // helical wind ribbons
                for (int i = 0; i < 10; i++) {
                    double th0 = i * Math.PI * 2 / 10 + t * 0.3;
                    Vec3 prev = null;
                    for (int j = 0; j <= 10; j++) {
                        double f = j / 10.0;
                        Vec3 pt = ringPt(ax, f, th0 + f * 5.0, 1.08, h, env);
                        if (prev != null) {
                            line(prev, pt, 0.07, (float) (0.8 * env), Pen.PALE);
                        }
                        prev = pt;
                    }
                }
                // speed lines trailing behind
                for (int i = 0; i < 14; i++) {
                    double lat = (r.nextDouble() - 0.5) * 8;
                    double y = r.nextDouble() * 10;
                    double st = 3 + r.nextDouble() * 8 + ((t * (1 + r.nextDouble())) % 3);
                    double len = 4 + r.nextDouble() * 7;
                    Vec3 a = ax.subtract(D.scale(st)).add(S.scale(lat)).add(0, y, 0);
                    line(a, a.subtract(D.scale(len)), 0.05, (float) (0.55 * env), Pen.PALE);
                }
            }
        }
    }

    private void glintPass() {
        for (int k = 0; k < 3; k++) {
            double age = t - SteelTempestEntity.thrustTick(k);
            if (age < 0 || age > 6) {
                continue;
            }
            double len = thrustLen(k);
            float al = (float) (1.0 - age / 6.0);
            double sz = k == 2 ? 5.0 : 3.2;
            p.billboard(chest().add(D.scale(len * ease(age / 2.5))), sz * al + 0.5, 0, Pen.CORE, al);
            p.billboard(chest().add(D.scale(0.6)), 2.0 * al + 0.3, 0, Pen.WHITE, al * 0.7f);
            for (int[] arr : ent.thrustHits().size() > k ? new int[][]{ent.thrustHits().get(k)} : new int[0][]) {
                for (int id : arr) {
                    Entity en = ent.level().getEntity(id);
                    if (en != null) {
                        p.billboard(new Vec3(en.getX(), en.getY() + en.getBbHeight() * 0.5, en.getZ()), 2.4, 0, Pen.WHITE, al);
                    }
                }
            }
        }
        double tt = t - LAUNCH;
        Vec3 ax = tt > 0 ? ent.tornadoAt(tt) : null;
        if (ax != null) {
            double env = ease(tt / 6.0) * (1.0 - c01((tt - TT) / 10.0));
            if (tt < 8) {
                p.billboard(ax.add(0, 1.5, 0), 8 * (1 - tt / 8.0) + 1, 0, Pen.CORE, (float) (0.9 * (1 - tt / 8.0)));
            }
            Random r = new Random(seed + 17);
            for (int i = 0; i < 30; i++) {
                double f = r.nextDouble();
                double ang = r.nextDouble() * Math.PI * 2 + t * (0.25 + r.nextDouble() * 0.2) * (r.nextBoolean() ? 1 : -1);
                Vec3 pt = ringPt(ax, f, ang, 1.15, HEIGHT * env, env);
                p.billboard(pt, 0.18 + r.nextDouble() * 0.28, 0, Pen.PALE, (float) (0.9 * env));
            }
            for (int id : ent.caughtIds()) {
                Entity en = ent.level().getEntity(id);
                if (en != null) {
                    p.billboard(new Vec3(en.getX(), en.getY() + en.getBbHeight() * 0.5, en.getZ()), 1.8 + 0.3 * Math.sin(t * 0.5 + id), 0, Pen.CORE, (float) (0.55 * env));
                }
            }
        }
    }
}
