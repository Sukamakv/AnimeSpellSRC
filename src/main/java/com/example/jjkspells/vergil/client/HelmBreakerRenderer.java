package com.example.jjkspells.vergil.client;

import com.example.jjkspells.vergil.entity.HelmBreakerEntity;
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
 * Draws "Helm Breaker: Dimensional Fissure": a seal marks the impact point, the caster leaps and dives along a thin line with crescent
 * afterimages, then fourteen black-interior fissures split the ground across 25 blocks, glass slabs rise out of them and glass spikes shoot
 * up; every pulse flashes along the fissures and through the victims, and at the end the fissures slam shut and everything bursts.
 * Glow is kept to thin lines and small glints.
 */
public class HelmBreakerRenderer extends EntityRenderer<HelmBreakerEntity> {
    private static final int APEX = HelmBreakerEntity.APEX;
    private static final int IMPACT = HelmBreakerEntity.IMPACT;
    private static final int STEP = HelmBreakerEntity.STEP;
    private static final int FINAL = HelmBreakerEntity.FINAL;
    private static final int END = HelmBreakerEntity.END;
    private static final double R = HelmBreakerEntity.R;
    private static final int FISSURES = 14;
    private static final int PILLARS = 18;
    private static final int DEBRIS = 130;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 E;
    private int seed;
    private HelmBreakerEntity ent;
    private double fadeOut;

    public HelmBreakerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(HelmBreakerEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(HelmBreakerEntity e) {
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

    @Override
    public void render(HelmBreakerEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        seed = e.getId() * 2749;
        E = e.getPosition(pt);
        fadeOut = 1.0 - c01((t - FINAL - 10) / 30.0);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), E, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        alpha(buf, VergilRender.RIFT);
        fissurePass();
        alpha(buf, VergilRender.SHARD);
        shardPass();
        add(buf, VergilRender.MIST);
        mistPass();
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

    private Vec3 fissureDir(int i) {
        Random r = new Random(seed + i * 71L);
        double ang = i * Math.PI * 2 / FISSURES + (r.nextDouble() - 0.5) * 0.25;
        return new Vec3(Math.cos(ang), 0, Math.sin(ang));
    }

    private double fissureLen(int i) {
        Random r = new Random(seed + i * 71L + 1);
        return R * (0.75 + 0.35 * r.nextDouble());
    }

    private Vec3 apex() {
        Vec3 a = ent.apexPos();
        return a.lengthSqr() < 1.0E-6 ? null : a;
    }

    // ------------------------------------------------------------------ passes

    /** Cracks in the ground: flat quads of the rift texture, black inside with bright edges. */
    private void fissurePass() {
        double age = t - IMPACT;
        if (age < -0.5) {
            return;
        }
        double g = ease(age / 8.0);
        double shut = 1.0 - c01((t - FINAL + 3) / 7.0);
        if (g <= 0.01 || shut <= 0.01) {
            return;
        }
        for (int i = 0; i < FISSURES; i++) {
            Random r = new Random(seed + i * 71L + 2);
            Vec3 d = fissureDir(i);
            Vec3 s = new Vec3(-d.z, 0, d.x);
            double w = 5.0 * (0.7 + 0.5 * r.nextDouble()) * shut;
            Vec3 a = E.add(d.scale(0.8)).add(0, 0.1, 0);
            Vec3 b = a.add(d.scale(fissureLen(i) * g));
            p.quad(a.subtract(s.scale(w)), a.add(s.scale(w)), b.add(s.scale(w)), b.subtract(s.scale(w)), 0f, 0f, 1f, 1f, Pen.WHITE, 0.97f);
        }
    }

    private void linePass() {
        Entity own = ent.owner();
        Vec3 apex = apex();
        // target beam at the impact point until the slam
        if (t < IMPACT) {
            float al = (float) (0.35 * ease(t / 10.0));
            line(E, E.add(0, 70, 0), 0.12, al, false);
        }
        // rising streak below the caster
        if (own != null && t > 1 && t < APEX + 2) {
            Vec3 o = own.getPosition(1.0f);
            line(o, o.add(0, -Math.min(14, 2 + t * 1.5), 0), 0.12, 0.6f, false);
        }
        // the dive
        if (apex != null && t >= APEX - 2 && t < IMPACT + 10) {
            double f = ease((t - APEX + 2) / 4.0);
            float al = (float) (1.0 - c01((t - IMPACT) / 8.0));
            Vec3 head = apex.add(E.subtract(apex).scale(f));
            Vec3 d = E.subtract(apex);
            Vec3 side = new Vec3(-d.z, 0, d.x);
            side = side.lengthSqr() < 1.0E-6 ? X : side.normalize();
            line(apex, head, 0.32, al, false);
            line(apex.add(side.scale(1.0)), head.add(side.scale(0.5)), 0.08, al * 0.6f, false);
            line(apex.subtract(side.scale(1.0)), head.subtract(side.scale(0.5)), 0.08, al * 0.6f, false);
        }
        double age = t - IMPACT;
        if (age >= 0) {
            // vertical beam at the slam
            if (age < 22) {
                line(E, E.add(0, 70, 0), 0.9 * (1 - age / 22.0) + 0.1, (float) (1.0 - age / 22.0), false);
            }
            // fissure flash on each pulse, glass spikes rising out of the cracks
            double flash = 0;
            for (int k = 0; k < HelmBreakerEntity.EVENTS; k++) {
                double a = t - (IMPACT + k * STEP);
                if (a >= 0 && a < 4) {
                    flash = Math.max(flash, 1.0 - a / 4.0);
                }
            }
            double shut = 1.0 - c01((t - FINAL + 2) / 6.0);
            if (flash > 0.01 && shut > 0.01) {
                for (int i = 0; i < FISSURES; i++) {
                    Vec3 d = fissureDir(i);
                    line(E.add(d.scale(0.8)).add(0, 0.2, 0), E.add(d.scale(0.8 + fissureLen(i))).add(0, 0.2, 0), 0.14, (float) (flash * shut * 0.9), i % 5 == 0);
                }
            }
            for (int i = 0; i < PILLARS; i++) {
                Random r = new Random(seed + i * 977L + 3);
                int fi = r.nextInt(FISSURES);
                Vec3 base = E.add(fissureDir(fi).scale(2.0 + r.nextDouble() * (fissureLen(fi) - 2.0))).add(0, 0.1, 0);
                double h = 8.0 + r.nextDouble() * 20.0;
                double grow = ease((age - r.nextDouble() * 5.0) / 6.0);
                float al = (float) (0.75 * c01(grow * 3) * (1.0 - c01((t - FINAL) / 12.0)));
                line(base, base.add(0, h * grow, 0), 0.16, al, r.nextInt(6) == 0);
            }
        }
        // slice lines through every victim of each event
        List<int[]> ev = ent.events();
        for (int k = 0; k < ev.size(); k++) {
            double a = t - (IMPACT + k * STEP);
            if (a < 0 || a > 10) {
                continue;
            }
            float al = (float) (1.0 - 0.7 * (a / 10.0));
            for (int id : ev.get(k)) {
                Vec3 c = targetCentre(id);
                if (c == null) {
                    continue;
                }
                Random r = new Random(seed + id * 131L + k * 17L);
                for (int s = 0; s < 2; s++) {
                    Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                    double l = 8.0 * ease(a / 2.0);
                    line(c.subtract(d.scale(l)), c.add(d.scale(l)), 0.07, al, s == 1 && k % 3 == 0);
                }
            }
        }
    }

    private void slashPass() {
        Vec3 apex = apex();
        if (apex != null) {
            for (int i = 0; i < 5; i++) {
                double f = (i + 0.5) / 5.0;
                double a = t - (APEX - 1 + i);
                if (a < 0 || a > 10) {
                    continue;
                }
                Vec3 c = apex.add(E.subtract(apex).scale(f));
                p.billboard(c, 7.0, -0.4 + i * 0.2, Pen.ICE, (float) (0.8 * (1.0 - a / 10.0)), 0f, 0f, 1f, 1f);
            }
        }
        List<int[]> ev = ent.events();
        for (int k = 0; k < ev.size(); k++) {
            double a = t - (IMPACT + k * STEP);
            if (a < 0 || a > 5) {
                continue;
            }
            float al = (float) (1.0 - a / 5.0);
            Random r = new Random(seed + k * 977L);
            for (int id : ev.get(k)) {
                Vec3 c = targetCentre(id);
                if (c != null) {
                    p.billboard(c, 3.4, r.nextDouble() * 6.28, Pen.ICE, al, 0f, 0f, 1f, 1f);
                }
            }
        }
    }

    private void glintPass() {
        double on = ease((t - 4) / 12.0) * fadeOut;
        for (int i = 0; i < 50; i++) {
            Random r = new Random(seed + i * 53L + 9);
            double ang = r.nextDouble() * 6.28;
            double rad = Math.sqrt(r.nextDouble()) * R;
            Vec3 at = E.add(Math.cos(ang) * rad, 0.3 + r.nextDouble() * 14.0, Math.sin(ang) * rad);
            double tw = 0.5 + 0.5 * Math.sin(t * (0.25 + r.nextDouble() * 0.3) + r.nextDouble() * 6.28);
            p.billboard(at, 0.3 + 0.3 * tw, 0, Pen.WHITE, (float) (on * tw * 0.7 * (t < IMPACT ? 0.3 : 1.0)));
        }
        Entity own = ent.owner();
        if (own != null && t < APEX) {
            p.billboard(own.getPosition(1.0f).add(0, 1, 0), 1.4, 0, Pen.WHITE, (float) (0.6 + 0.4 * Math.sin(t)));
        }
        if (t < 8) {
            p.billboard(E.add(0, 0.5, 0), 3.0, 0, Pen.WHITE, (float) (1.0 - t / 8.0));
        }
        List<int[]> ev = ent.events();
        for (int k = 0; k < ev.size(); k++) {
            double a = t - (IMPACT + k * STEP);
            if (a < 0 || a > 6) {
                continue;
            }
            float al = (float) (1.0 - a / 6.0);
            for (int id : ev.get(k)) {
                Vec3 c = targetCentre(id);
                if (c != null) {
                    p.billboard(c, 2.2 + 1.0 * (1 - al), 0.0, Pen.WHITE, al);
                }
            }
        }
        double age = t - IMPACT;
        if (age >= -1 && age < 14) {
            p.billboard(E.add(0, 2, 0), 9.0 + (age + 1) * 2.2, 0, Pen.CORE, (float) (1.0 - (age + 1) / 15.0));
        }
        double fa = t - FINAL;
        if (fa >= -1 && fa < 14) {
            p.billboard(E.add(0, 2, 0), 9.0 + (fa + 1) * 2.0, 0, Pen.CORE, (float) (1.0 - (fa + 1) / 15.0));
        }
    }

    private void ringPass() {
        double on = ease(t / 12.0) * fadeOut;
        if (on > 0.003) {
            Vec3 c = E.add(0, 0.06, 0);
            p.plane(c, X, Z, R * 1.04, t * 0.01, Pen.ICE, (float) (on * 0.65));
            p.plane(c.add(0, 0.01, 0), X, Z, R * 0.6, -t * 0.018, Pen.CYAN, (float) (on * 0.5));
            p.plane(c.add(0, 0.02, 0), X, Z, R * 0.25, t * 0.035, Pen.CORE, (float) (on * 0.5));
        }
        double age = t - IMPACT;
        if (age >= 0 && age < 22) {
            p.plane(E.add(0, 0.12, 0), X, Z, R * (0.2 + 1.5 * ease(age / 16.0)), 0, Pen.CORE, (float) (0.9 * (1 - age / 22.0)));
            p.plane(E.add(0, 0.12, 0), X, Z, R * (0.1 + 1.0 * ease(age / 12.0)), 1.0, Pen.CYAN, (float) (0.6 * (1 - age / 22.0)));
        }
        for (int k = 1; k < HelmBreakerEntity.EVENTS - 1; k++) {
            double a = t - (IMPACT + k * STEP);
            if (a < 0 || a > 12) {
                continue;
            }
            p.plane(E.add(0, 0.1, 0), X, Z, R * (0.1 + 0.9 * ease(a / 12.0)), 0, Pen.ICE, (float) (0.4 * (1 - a / 12.0)));
        }
        double fa = t - FINAL;
        if (fa >= 0 && fa < 22) {
            p.plane(E.add(0, 0.12, 0), X, Z, R * (0.3 + 1.8 * ease(fa / 18.0)), 0, Pen.CORE, (float) (0.9 * (1 - fa / 22.0)));
        }
    }

    private void mistPass() {
        double on = ease((t - 4) / 16.0) * fadeOut;
        if (on <= 0.003) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            double s = R * (1.06 - 0.12 * i);
            double rot = t * (0.008 + 0.004 * i) * (i % 2 == 0 ? 1 : -1) + i * 1.7;
            p.plane(E.add(0, 0.05 + 0.6 * i, 0), X, Z, s, rot, Pen.BLUE, (float) (on * (0.2 - 0.04 * i)));
        }
    }

    /** Glass slabs and splinters thrown out of the fissures, hovering until the fissures shut, then bursting outward. */
    private void shardPass() {
        double age = t - IMPACT;
        if (age < 0) {
            return;
        }
        double fa = Math.max(0, t - FINAL);
        for (int i = 0; i < DEBRIS; i++) {
            Random r = new Random(seed + i * 211L + 5);
            int fi = r.nextInt(FISSURES);
            Vec3 origin = E.add(fissureDir(fi).scale(1.5 + r.nextDouble() * (fissureLen(fi) - 1.5))).add(0, 0.2, 0);
            boolean slab = i < 30;
            double half = slab ? 1.6 + r.nextDouble() * 2.2 : 0.4 + r.nextDouble() * 1.0;
            double rise = ease((age - r.nextDouble() * 4.0) / 10.0);
            double H = (slab ? 2.0 : 3.0) + r.nextDouble() * (slab ? 7.0 : 16.0);
            Vec3 pos = origin.add((r.nextDouble() - 0.5) * 2.0 * rise, H * rise + Math.sin(t * 0.05 + i) * 0.2, (r.nextDouble() - 0.5) * 2.0 * rise);
            double rot = r.nextDouble() * 6.28 + t * 0.02 * (r.nextDouble() - 0.5) * 4;
            double al = 0.9 * c01(age / 3.0);
            if (t >= FINAL) {
                Vec3 out = new Vec3(pos.x - E.x, 0.35, pos.z - E.z).normalize();
                pos = pos.add(out.scale((0.5 + r.nextDouble() * 1.4) * fa)).add(0, -0.012 * fa * fa, 0);
                rot += fa * (0.1 + r.nextDouble() * 0.2);
                al *= 1.0 - c01((fa - 10) / 30.0);
            }
            shard(pos, half * (0.4 + 0.6 * rise), rot, r.nextInt(4), (float) al);
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
