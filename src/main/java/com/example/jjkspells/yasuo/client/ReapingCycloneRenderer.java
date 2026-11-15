package com.example.jjkspells.yasuo.client;

import com.example.jjkspells.yasuo.entity.ReapingCycloneEntity;
import com.example.jjkspells.yasuo.spell.ReapingCycloneSpell;
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
 * Draws "Reaping Cyclone": three nested cones of streaked wind spinning at different speeds round Yasuo, spectral katanas whirling at chest height with
 * trailing slash arcs, a spin of fading flat afterimages of Yasuo himself, helical wind ribbons, debris glints and a dust ring; each slice puts crossed cuts
 * on every caught enemy, and the final turn blasts a shock ring and big slashes upward.
 */
public class ReapingCycloneRenderer extends EntityRenderer<ReapingCycloneEntity> {
    private static final int DURATION = ReapingCycloneEntity.DURATION;
    private static final int END = ReapingCycloneEntity.END;
    private static final double R = ReapingCycloneSpell.RADIUS;
    private static final double H = ReapingCycloneSpell.HEIGHT;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final int SEG = 18;
    private static final int LV = 9;
    private static final float DU = 0.25f;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private final PlayerGhost ghost;
    private Pen p;
    private float t;
    private Vec3 C;
    private PoseStack ps;
    private MultiBufferSource buf;
    private ReapingCycloneEntity ent;
    private Entity own;
    private double env;
    private double spin;

    public ReapingCycloneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
        this.ghost = new PlayerGhost(ctx);
    }

    @Override
    public boolean shouldRender(ReapingCycloneEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ReapingCycloneEntity e) {
        return YasuoRender.GLOW;
    }

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void add(ResourceLocation tex) {
        p.vc = buf.getBuffer(YasuoRender.additive(tex));
    }

    private void alpha(ResourceLocation tex) {
        p.vc = buf.getBuffer(YasuoRender.alpha(tex));
    }

    private void line(Vec3 a, Vec3 b, double w, float al, float[] col) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.4, al * 0.45f, 0f, 1f, col);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.CORE);
    }

    private Vec3 at(double ang, double rad, double y) {
        return C.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
    }

    private double coneR(double h, double scale) {
        double f = c01(h / H);
        return (1.3 + (R - 1.3) * Math.pow(f, 1.15)) * scale;
    }

    @Override
    public void render(ReapingCycloneEntity e, float yaw, float pt, PoseStack ps0, MultiBufferSource buf0, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        ps = ps0;
        buf = buf0;
        own = e.owner();
        Vec3 A = e.getPosition(pt);
        C = (own != null && t <= DURATION) ? own.getPosition(pt) : A;
        env = ease(t / 8.0) * (1.0 - c01((t - DURATION) / (END - DURATION)));
        spin = t * 0.62;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        spinGhosts(A);
        alpha(YasuoRender.KATANA);
        katanas();
        add(YasuoRender.WIND);
        cones();
        add(YasuoRender.MIST);
        mist();
        add(YasuoRender.RING);
        rings();
        add(YasuoRender.LINE);
        lines();
        add(YasuoRender.GLINT);
        glints();
    }

    // ------------------------------------------------------------------ passes

    private void spinGhosts(Vec3 A) {
        if (t > DURATION || own == null) {
            return;
        }
        boolean slim = PlayerGhost.slimOf(own);
        for (int k = 1; k <= 4; k++) {
            double yaw = Math.toDegrees(-spin) + 55.0 * k;
            float al = (float) (0.55 * env / k);
            ghost.silhouette(ps, buf, slim, C.subtract(A), yaw, PlayerGhost.Pose.spin(), k % 2 == 0 ? Pen.SKY : Pen.AQUA, al);
        }
    }

    private void katanas() {
        double r0 = 0.9;
        double len = 4.2;
        for (int i = 0; i < 3; i++) {
            double ang = spin + i * Math.PI * 2 / 3;
            double y = 1.1 + 0.55 * Math.sin(t * 0.15 + i * 2.0);
            Vec3 h = at(ang, r0, y);
            Vec3 tip = at(ang, r0 + len, y + 0.3 * Math.sin(t * 0.2 + i));
            double w = h.distanceTo(tip) * ASPECT;
            float al = (float) (0.95 * env);
            p.ribbon(h, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
        }
    }

    private void cones() {
        for (int k = 0; k < 3; k++) {
            double scale = 1.0 - 0.28 * k;
            double sp = (k % 2 == 0 ? 1 : -1) * t * (0.12 + 0.05 * k);
            float[] col = k == 0 ? Pen.PALE : (k == 1 ? Pen.AQUA : Pen.SKY);
            double scroll = t * (0.05 + 0.02 * k) * (k % 2 == 0 ? 1 : -1);
            for (int i = 0; i < SEG; i++) {
                double a0 = sp + Math.PI * 2 * i / SEG;
                double a1 = sp + Math.PI * 2 * (i + 1) / SEG;
                double fr = ((scroll + i * 0.37) % 1.0 + 1.0) % 1.0;
                float u0 = (float) (fr * (1 - DU));
                float u1 = u0 + DU;
                for (int j = 0; j < LV; j++) {
                    double h0 = H * j / LV;
                    double h1 = H * (j + 1) / LV;
                    double f0 = (double) j / LV;
                    double f1 = (double) (j + 1) / LV;
                    float al = (float) (0.24 * env * (1.0 - 0.5 * f0) * (k == 0 ? 1.0 : 0.85));
                    double r0 = coneR(h0, scale);
                    double r1 = coneR(h1, scale);
                    // spiral lag: higher levels trail behind
                    double lag0 = f0 * 0.9;
                    double lag1 = f1 * 0.9;
                    p.quad(at(a0 - lag0, r0, h0), at(a1 - lag0, r0, h0), at(a1 - lag1, r1, h1), at(a0 - lag1, r1, h1), u0, (float) f0, u1, (float) f1, col, al);
                }
            }
        }
    }

    private void mist() {
        p.plane(C.add(0, 0.12, 0), X, Z, R * 0.95 * env, t * 0.05, Pen.PALE, (float) (0.28 * env));
        p.plane(C.add(0, 0.2, 0), X, Z, R * 0.6 * env, -t * 0.08, Pen.SKY, (float) (0.25 * env));
        for (int i = 0; i < 8; i++) {
            double a = spin * 0.6 + i * Math.PI / 4;
            p.billboard(at(a, 3.5 + 0.8 * Math.sin(i), 0.8), 1.8, t * 0.03 + i, Pen.PALE, (float) (0.12 * env));
        }
    }

    private void rings() {
        p.plane(C.add(0, 0.15, 0), X, Z, R * 0.9 * env, t * 0.04, Pen.AQUA, (float) (0.5 * env));
        p.plane(C.add(0, 0.18, 0), X, Z, 3.2 * env, -t * 0.12, Pen.WHITE, (float) (0.4 * env));
        // blade-height spin rings
        for (int i = 0; i < 2; i++) {
            p.plane(C.add(0, 1.0 + 0.5 * i, 0), X, Z, (3.0 + i * 1.5) * env, spin * (i == 0 ? 1 : -1) * 0.5, Pen.PALE, (float) (0.35 * env));
        }
        double fa = t - DURATION;
        if (fa >= 0 && fa < 14) {
            double k = ease(fa / 9.0);
            float al = (float) (1.0 - fa / 14.0);
            p.plane(C.add(0, 0.3, 0), X, Z, 1.0 + (R + 4.0) * k, 0, Pen.WHITE, al * 0.8f);
            p.plane(C.add(0, 0.5, 0), X, Z, 1.0 + (R + 1.0) * k, fa * 0.3, Pen.AQUA, al * 0.7f);
            p.plane(C.add(0, 4.0 * k, 0), X, Z, 1.0 + 6.0 * k, 0, Pen.PALE, al * 0.5f);
        }
    }

    private Vec3 camRight() {
        Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f l = c.getLeftVector();
        return new Vec3(-l.x(), -l.y(), -l.z());
    }

    private void lines() {
        // slash arcs trailing each blade
        for (int i = 0; i < 3; i++) {
            double ang = spin + i * Math.PI * 2 / 3;
            double y = 1.1 + 0.55 * Math.sin(t * 0.15 + i * 2.0);
            for (int j = 0; j < 12; j++) {
                double f0 = j / 12.0;
                double f1 = (j + 1) / 12.0;
                double a0 = ang - 1.4 * (1 - f0);
                double a1 = ang - 1.4 * (1 - f1);
                line(at(a0, 4.4, y + 0.3 * f0), at(a1, 4.4, y + 0.3 * f1), 0.1 * f1, (float) (0.9 * env * f1), Pen.AQUA);
            }
        }
        // helical ribbons up the cone
        for (int i = 0; i < 8; i++) {
            double base = Math.PI * 2 * i / 8 - t * 0.2;
            Vec3 prev = null;
            for (int j = 0; j <= 14; j++) {
                double f = j / 14.0;
                double h = H * f;
                Vec3 cur = at(base + f * 5.5, coneR(h, 0.9), h);
                if (prev != null) {
                    line(prev, cur, 0.06, (float) (0.5 * env * (1 - f * 0.6)), Pen.PALE);
                }
                prev = cur;
            }
        }
        // crossed cuts on each caught enemy at every slice
        int phase = ent.tickCount % ReapingCycloneSpell.SLICE_INTERVAL;
        double age = phase + (t - (ent.tickCount - 1));
        if (t <= DURATION && age < 4) {
            double k = ease(age / 2.0);
            float al = (float) (1.0 - age / 4.0);
            for (int id : ent.caughtIds()) {
                Entity te = ent.level().getEntity(id);
                if (te == null) {
                    continue;
                }
                Vec3 m = te.getPosition(1.0f).add(0, te.getBbHeight() * 0.5, 0);
                for (int i = 0; i < 2; i++) {
                    double an = i == 0 ? 0.7 + id * 0.3 : -0.7 + id * 0.3;
                    Vec3 v = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0).scale(1.8 * k);
                    line(m.subtract(v), m.add(v), 0.07, al, Pen.WHITE);
                }
            }
        }
        // final harvest
        double fa = t - DURATION;
        if (fa >= 0 && fa < 14) {
            double k = ease(fa / 3.0);
            float al = (float) (1.0 - fa / 14.0);
            for (int id : ent.caughtIds()) {
                Entity te = ent.level().getEntity(id);
                if (te == null) {
                    continue;
                }
                Vec3 m = te.getPosition(1.0f).add(0, te.getBbHeight() * 0.5, 0);
                for (int i = 0; i < 3; i++) {
                    double an = 0.5 + i * 1.05 + id * 0.4;
                    Vec3 v = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0).scale(4.5 * k);
                    line(m.subtract(v), m.add(v), 0.11, al, Pen.WHITE);
                }
            }
            Random r = new Random(ent.getId() * 41L);
            for (int i = 0; i < 24; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double d0 = 2.0 + r.nextDouble() * R;
                line(at(ang, d0, 0.5 + 4.0 * k), at(ang, d0 + 1.5 + 3.0 * k, 4.0 + 9.0 * k), 0.06, al * 0.8f, Pen.AQUA);
            }
        }
    }

    private void glints() {
        Random r = new Random(ent.getId() * 977L);
        for (int i = 0; i < 40; i++) {
            double h = H * ((r.nextDouble() + t * 0.012 * (1 + r.nextDouble())) % 1.0);
            double ang = r.nextDouble() * Math.PI * 2 + t * (0.1 + 0.1 * r.nextDouble());
            double rad = coneR(h, 0.35 + 0.65 * r.nextDouble());
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 0.4 + i * 2.3));
            p.billboard(at(ang, rad, h), 0.22, t * 0.05, Pen.WHITE, (float) (0.6 * env * tw));
        }
        for (int i = 0; i < 3; i++) {
            double ang = spin + i * Math.PI * 2 / 3;
            p.billboard(at(ang, 5.2, 1.3), 0.5, t * 0.2, Pen.WHITE, (float) (0.7 * env));
        }
        double fa = t - DURATION;
        if (fa >= 0 && fa < 8) {
            p.billboard(C.add(0, 1.2, 0), 2.5 + 3.0 * ease(fa / 4.0), fa * 0.2, Pen.WHITE, (float) (1.0 - fa / 8.0));
        }
    }
}
