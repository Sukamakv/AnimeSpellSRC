package com.example.jjkspells.dante.client;

import com.example.jjkspells.dante.entity.MillionStabEntity;
import com.example.jjkspells.dante.spell.MillionStabSpell;
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
 * Draws "Million Stab": a crimson streak with speed lines follows the Stinger while a Rebellion sprite leads it, skewered victims are sliced,
 * fourteen ghost Rebellion blades thrust in from random directions with sparks, then a 30 block Rebellion plunges onto the end point,
 * detonates in a ring of embers and shatters into red glass. Glow stays low: thin lines, solid sprites and small glints.
 */
public class MillionStabRenderer extends EntityRenderer<MillionStabEntity> {
    private static final int DASH0 = MillionStabEntity.DASH0;
    private static final int B_TICK = MillionStabEntity.B_TICK;
    private static final int STAB0 = MillionStabEntity.STAB0;
    private static final int STABS = MillionStabEntity.STABS;
    private static final int FINAL = MillionStabEntity.FINAL;
    private static final int END = MillionStabEntity.END;
    private static final double RS = MillionStabSpell.STAB_RADIUS;
    private static final double RF = MillionStabSpell.FINAL_RADIUS;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 B;
    private Vec3 dir;
    private Vec3 side;
    private int seed;
    private MillionStabEntity ent;
    private Entity own;
    private double fade;

    public MillionStabRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(MillionStabEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(MillionStabEntity e) {
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

    private void line(Vec3 a, Vec3 b, double w, float al, boolean bloody) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.6, al * 0.5f, 0f, 1f, bloody ? Pen.BLOOD : Pen.EMBER);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.CORE);
    }

    /** Rebellion sprite from hilt to tip. */
    private void blade(Vec3 hilt, Vec3 tip, float al) {
        if (al <= 0.01f) {
            return;
        }
        double w = hilt.distanceTo(tip) * ASPECT;
        p.ribbon(hilt, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
    }

    @Override
    public void render(MillionStabEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        seed = e.getId() * 5167;
        A = e.getPosition(pt);
        B = e.endPos();
        dir = e.dir();
        dir = dir.lengthSqr() < 1.0E-6 ? Z : dir.normalize();
        side = new Vec3(-dir.z, 0, dir.x);
        own = e.owner();
        fade = 1.0 - c01((t - FINAL - 26) / 12.0);
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
        add(buf, DanteRender.LINE);
        linePass();
        add(buf, DanteRender.SLASH);
        slashPass();
        add(buf, DanteRender.GLINT);
        glintPass();
    }

    private Vec3 targetCentre(int id) {
        Entity en = ent.level().getEntity(id);
        if (en == null || !en.isAlive()) {
            return null;
        }
        return new Vec3(en.getX(), en.getY() + en.getBbHeight() * 0.5, en.getZ());
    }

    private Vec3 head() {
        if (B != null) {
            return B;
        }
        return own != null ? own.getPosition(1.0f) : A.add(dir.scale(MillionStabSpell.DASH_SPEED * Math.max(0, t - DASH0)));
    }

    private Vec3 stabDir(int k, int id) {
        Random r = new Random(seed + k * 131L + id * 7L);
        double az = r.nextDouble() * 6.28;
        double el = 0.2 + r.nextDouble() * 0.6;
        return new Vec3(Math.cos(az) * Math.cos(el), Math.sin(el), Math.sin(az) * Math.cos(el));
    }

    private Vec3 stabTarget(int k, int id) {
        if (id >= 0) {
            return targetCentre(id);
        }
        Random r = new Random(seed + k * 977L);
        return B == null ? null : B.add((r.nextDouble() - 0.5) * 10, 1.0 + r.nextDouble() * 3, (r.nextDouble() - 0.5) * 10);
    }

    // ------------------------------------------------------------------ sprites

    private void swordPass() {
        // the blade leading the Stinger
        if (t >= DASH0 && t < B_TICK + 6) {
            Vec3 h = head().add(0, 1.1, 0);
            float al = (float) (1.0 - c01((t - B_TICK) / 6.0));
            blade(h.subtract(dir.scale(12.0)), h.add(dir.scale(1.5)), al);
        }
        // ghost blades of the Million Stab
        List<int[]> ev = ent.events();
        for (int k = 0; k < Math.min(ev.size(), STABS); k++) {
            double age = t - (STAB0 + k);
            if (age < 0 || age > 4) {
                continue;
            }
            float al = (float) (1.0 - age / 4.0);
            int[] ids = ev.get(k);
            int n = Math.max(1, Math.min(ids.length, 8));
            for (int i = 0; i < n; i++) {
                int id = ids.length > 0 ? ids[i] : -1;
                Vec3 c = stabTarget(k, id);
                if (c == null) {
                    continue;
                }
                Vec3 d = stabDir(k, id);
                Vec3 tip = c.add(d.scale(4.0 * (1.0 - ease(age / 1.2))));
                blade(tip.add(d.scale(11.0)), tip, al);
            }
        }
        // the colossal Rebellion plunging onto the end point
        if (B != null && t >= FINAL - 6 && t < END) {
            double f = c01((t - (FINAL - 6)) / 6.0);
            Vec3 tip = B.add(0, 0.6 + 46.0 * (1.0 - f * f), 0);
            blade(tip.add(0, 30, 0), tip, (float) (c01((t - (FINAL - 6)) / 2.0) * fade));
        }
    }

    // ------------------------------------------------------------------ lines

    private void linePass() {
        Vec3 h = head();
        // preview of the dash
        if (t < DASH0) {
            line(A.add(0, 1.0, 0), A.add(dir.scale(30.0 * ease(t / 5.0))).add(0, 1.0, 0), 0.07, (float) (0.35 * ease(t / 5.0)), false);
        }
        // the crimson streak and speed lines
        if (t >= DASH0) {
            double dist = h.subtract(A).multiply(1, 0, 1).length();
            float al = (float) (1.0 - c01((t - B_TICK - 2) / 12.0));
            line(A.add(0, 1.0, 0), h.add(0, 1.0, 0), 0.45, al, false);
            for (int i = 0; i < 18; i++) {
                Random r = new Random(seed + i * 53L);
                double s0 = r.nextDouble() * Math.max(1.0, dist * 0.85);
                double len = 4.0 + r.nextDouble() * 10.0;
                Vec3 o = A.add(side.scale((r.nextDouble() * 2 - 1) * 2.4)).add(0, 0.2 + r.nextDouble() * 2.6, 0);
                line(o.add(dir.scale(s0)), o.add(dir.scale(Math.min(dist, s0 + len))), 0.06, al * 0.6f, i % 3 == 0);
            }
        }
        // skewered victims
        for (int[] pv : ent.pierced()) {
            double age = t - pv[1];
            Vec3 c = targetCentre(pv[0]);
            if (c == null || age < 0 || age > 10) {
                continue;
            }
            Random r = new Random(seed + pv[0] * 131L);
            float al = (float) (1.0 - 0.7 * age / 10.0);
            for (int s = 0; s < 2; s++) {
                Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                double l = 7.0 * ease(age / 2.0);
                line(c.subtract(d.scale(l)), c.add(d.scale(l)), 0.07, al, s == 1);
            }
            line(c.subtract(dir.scale(6.0)), c.add(dir.scale(6.0)), 0.1, al, false);
        }
        // stab streaks and sparks
        List<int[]> ev = ent.events();
        for (int k = 0; k < Math.min(ev.size(), STABS); k++) {
            double age = t - (STAB0 + k);
            if (age < 0 || age > 4) {
                continue;
            }
            float al = (float) (1.0 - age / 4.0);
            int[] ids = ev.get(k);
            int n = Math.max(1, Math.min(ids.length, 8));
            for (int i = 0; i < n; i++) {
                int id = ids.length > 0 ? ids[i] : -1;
                Vec3 c = stabTarget(k, id);
                if (c == null) {
                    continue;
                }
                Vec3 d = stabDir(k, id);
                line(c.add(d.scale(13.0)), c, 0.1, al * 0.7f, false);
                Random r = new Random(seed + k * 31L + i);
                for (int s = 0; s < 4; s++) {
                    Vec3 sp = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                    line(c, c.add(sp.scale(1.5 + r.nextDouble() * 3.0)), 0.05, al, true);
                }
            }
        }
        // the final landing: ground cracks and a beam
        if (B != null) {
            double fa = t - FINAL;
            if (fa >= 0 && fa < 22) {
                for (int i = 0; i < 14; i++) {
                    Random r = new Random(seed + i * 71L + 4);
                    double ang = i * 6.283 / 14 + r.nextDouble() * 0.3;
                    Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                    line(B.add(0, 0.2, 0), B.add(d.scale(RF * (0.7 + 0.3 * r.nextDouble()) * ease(fa / 6.0))).add(0, 0.2, 0), 0.12, (float) (1.0 - fa / 22.0), i % 2 == 0);
                }
                line(B, B.add(0, 90, 0), 1.4 * (1.0 - fa / 22.0) + 0.2, (float) (1.0 - fa / 22.0), false);
            }
        }
    }

    private void slashPass() {
        for (int[] pv : ent.pierced()) {
            double age = t - pv[1];
            Vec3 c = targetCentre(pv[0]);
            if (c != null && age >= 0 && age < 5) {
                p.billboard(c, 3.0, new Random(seed + pv[0]).nextDouble() * 6.28, Pen.HOT, (float) (1.0 - age / 5.0), 0f, 0f, 1f, 1f);
            }
        }
        List<int[]> ev = ent.events();
        for (int k = 0; k < Math.min(ev.size(), STABS); k++) {
            double age = t - (STAB0 + k);
            if (age < 0 || age > 3) {
                continue;
            }
            for (int id : ev.get(k)) {
                Vec3 c = targetCentre(id);
                if (c != null) {
                    p.billboard(c, 2.4, new Random(seed + k * 7L + id).nextDouble() * 6.28, Pen.EMBER, (float) (1.0 - age / 3.0), 0f, 0f, 1f, 1f);
                }
            }
        }
    }

    private void glintPass() {
        if (t < DASH0 && own != null) {
            p.billboard(own.getPosition(1.0f).add(0, 1.1, 0).add(dir.scale(1.2)), 1.0 + 0.2 * t, 0, Pen.CORE, (float) (0.4 + 0.1 * t));
        }
        if (t >= DASH0 && t < B_TICK + 4) {
            p.billboard(head().add(0, 1.1, 0).add(dir.scale(1.8)), 3.0, 0, Pen.CORE, (float) (1.0 - c01((t - B_TICK) / 4.0)));
        }
        for (int[] pv : ent.pierced()) {
            double age = t - pv[1];
            Vec3 c = targetCentre(pv[0]);
            if (c != null && age >= 0 && age < 7) {
                p.billboard(c, 2.2, 0, Pen.WHITE, (float) (1.0 - age / 7.0));
            }
        }
        List<int[]> ev = ent.events();
        for (int k = 0; k < Math.min(ev.size(), STABS); k++) {
            double age = t - (STAB0 + k);
            if (age < 0 || age > 4) {
                continue;
            }
            float al = (float) (1.0 - age / 4.0);
            for (int id : ev.get(k)) {
                Vec3 c = targetCentre(id);
                if (c != null) {
                    p.billboard(c, 2.2, 0, Pen.WHITE, al);
                }
            }
            if (B != null) {
                p.billboard(B.add(0, 1.5, 0), 2.5, 0, Pen.CORE, al * 0.6f);
            }
        }
        if (B != null) {
            double on = ease((t - B_TICK) / 8.0) * fade;
            for (int i = 0; i < 40; i++) {
                Random r = new Random(seed + i * 59L + 9);
                double ang = r.nextDouble() * 6.28;
                double rad = Math.sqrt(r.nextDouble()) * RS;
                double rise = (t * 0.05 + r.nextDouble() * 6.0) % 6.0;
                double tw = 0.5 + 0.5 * Math.sin(t * (0.25 + r.nextDouble() * 0.3) + r.nextDouble() * 6.28);
                p.billboard(B.add(Math.cos(ang) * rad, 0.3 + rise * 1.5, Math.sin(ang) * rad), 0.3 + 0.3 * tw, 0, Pen.EMBER, (float) (on * tw * 0.8));
            }
            double fa = t - FINAL;
            if (fa >= -1 && fa < 16) {
                p.billboard(B.add(0, 2, 0), 12.0 + (fa + 1) * 2.8, 0, Pen.CORE, (float) (1.0 - (fa + 1) / 17.0));
            }
        }
        if (t < DASH0 + 2) {
            p.billboard(A.add(0, 1, 0), 2.5, 0, Pen.CORE, (float) (1.0 - t / (DASH0 + 2.0)));
        }
    }

    private void ringPass() {
        if (t < B_TICK) {
            double a = ease(t / 6.0);
            p.plane(A.add(0, 0.08, 0), X, Z, 5.0 * a, t * 0.15, Pen.EMBER, (float) (0.6 * a));
            p.plane(A.add(0, 0.1, 0), X, Z, 3.0 * a, -t * 0.2, Pen.CORE, (float) (0.4 * a));
        }
        if (B == null) {
            return;
        }
        double on = ease((t - B_TICK) / 10.0) * fade;
        if (on > 0.003) {
            p.plane(B.add(0, 0.06, 0), X, Z, RS, t * 0.012, Pen.EMBER, (float) (on * 0.6));
            p.plane(B.add(0, 0.07, 0), X, Z, RS * 0.5, -t * 0.02, Pen.SCARLET, (float) (on * 0.55));
            p.plane(B.add(0, 0.08, 0), X, Z, RS * 0.2, t * 0.04, Pen.CORE, (float) (on * 0.5));
        }
        for (int k = 0; k < STABS; k++) {
            double age = t - (STAB0 + k);
            if (age < 0 || age > 6) {
                continue;
            }
            p.plane(B.add(0, 0.1, 0), X, Z, 2.0 + 10.0 * ease(age / 6.0), 0, Pen.EMBER, (float) (0.35 * (1 - age / 6.0)));
        }
        double fa = t - FINAL;
        if (fa >= 0 && fa < 24) {
            p.plane(B.add(0, 0.12, 0), X, Z, RF * (0.2 + 1.3 * ease(fa / 18.0)), 0, Pen.CORE, (float) (0.9 * (1 - fa / 24.0)));
            p.plane(B.add(0, 0.12, 0), X, Z, RF * (0.1 + 0.9 * ease(fa / 14.0)), 1.0, Pen.EMBER, (float) (0.6 * (1 - fa / 24.0)));
        }
    }

    private void mistPass() {
        if (B == null) {
            return;
        }
        double on = ease((t - B_TICK) / 12.0) * fade;
        if (on <= 0.003) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            double s = RF * (0.9 - 0.12 * i);
            double rot = t * (0.01 + 0.004 * i) * (i % 2 == 0 ? 1 : -1) + i * 1.7;
            p.plane(B.add(0, 0.05 + 0.5 * i, 0), X, Z, s, rot, Pen.BLOOD, (float) (on * (0.28 - 0.06 * i)));
        }
    }

    // ------------------------------------------------------------------ glass

    /** Red glass thrown out by the landing and by the shattering blade. */
    private void shardPass() {
        if (B == null || t < FINAL - 1 || t >= END) {
            return;
        }
        double age = t - FINAL;
        for (int i = 0; i < 100; i++) {
            Random r = new Random(seed + i * 211L + 5);
            double ang = r.nextDouble() * 6.28;
            Vec3 v = new Vec3(Math.cos(ang), 0.3 + r.nextDouble() * 0.7, Math.sin(ang)).scale(0.6 + r.nextDouble() * 1.8);
            Vec3 pos = B.add(0, 1, 0).add(v.scale(age * 0.85)).add(0, -0.012 * age * age, 0);
            double al = 0.95 * c01((age + 1) / 2.0) * (1.0 - c01((age - 12) / 24.0));
            shard(pos, 0.4 + r.nextDouble() * 1.1, r.nextDouble() * 6.28 + age * (0.15 + r.nextDouble() * 0.3), r.nextInt(4), (float) al);
        }
        double sa = t - (FINAL + 26);
        if (sa >= 0) {
            for (int i = 0; i < 40; i++) {
                Random r = new Random(seed + i * 313L + 8);
                Vec3 pos = B.add((r.nextDouble() - 0.5) * 3, 1.0 + r.nextDouble() * 30.0, (r.nextDouble() - 0.5) * 3);
                Vec3 v = new Vec3(r.nextGaussian() * 0.3, 0.1 + r.nextDouble() * 0.3, r.nextGaussian() * 0.3);
                pos = pos.add(v.scale(sa)).add(0, -0.02 * sa * sa, 0);
                double al = 0.9 * (1.0 - c01(sa / 14.0));
                shard(pos, 0.6 + r.nextDouble() * 1.4, r.nextDouble() * 6.28 + sa * 0.2, r.nextInt(4), (float) al);
            }
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
