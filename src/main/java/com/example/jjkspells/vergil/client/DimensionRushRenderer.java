package com.example.jjkspells.vergil.client;

import com.example.jjkspells.vergil.entity.DimensionRushEntity;
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
 * Draws "Dimension Rush": a thin blue line is drawn from start to end of the blink, giant crescent afterimages hang along it, lines of
 * cut space fill a 24 block wide corridor, every rapid cut flashes slice lines through the victims, the sheathing click rings out at the
 * end point, then rifts open along the corridor and everything bursts into glass. Glow is kept to thin lines and small glints.
 */
public class DimensionRushRenderer extends EntityRenderer<DimensionRushEntity> {
    private static final int BLINK = DimensionRushEntity.BLINK;
    private static final int CUT0 = DimensionRushEntity.CUT0;
    private static final int STEP = DimensionRushEntity.STEP;
    private static final int SHEATH = DimensionRushEntity.SHEATH;
    private static final int FINAL = DimensionRushEntity.FINAL;
    private static final int END = DimensionRushEntity.END;
    private static final double W = DimensionRushEntity.W;
    private static final int LINES = 64;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 B;
    private Vec3 dir;
    private Vec3 dirH;
    private Vec3 side;
    private double len;
    private int seed;
    private DimensionRushEntity ent;
    private double fadeOut;

    public DimensionRushRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(DimensionRushEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(DimensionRushEntity e) {
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

    private Vec3 at(double f, double lat, double h) {
        return A.add(B.subtract(A).scale(f)).add(side.scale(lat)).add(0, h, 0);
    }

    @Override
    public void render(DimensionRushEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        seed = e.getId() * 4099;
        A = e.getPosition(pt);
        B = e.endPos();
        Vec3 ab = B.subtract(A);
        len = Math.max(1.0, ab.length());
        dir = ab.scale(1.0 / len);
        dirH = new Vec3(dir.x, 0, dir.z);
        dirH = dirH.lengthSqr() < 1.0E-6 ? X : dirH.normalize();
        side = new Vec3(-dirH.z, 0, dirH.x);
        fadeOut = 1.0 - c01((t - FINAL - 8) / 30.0);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        alpha(buf, VergilRender.SHARD);
        shardPass();
        alpha(buf, VergilRender.RIFT);
        riftPass();
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

    // ------------------------------------------------------------------ passes

    private void linePass() {
        // the blink line itself
        double hf = ease(t / 3.0);
        float la = (float) (1.0 - c01((t - 4) / 14.0));
        line(A.add(0, 1.2, 0), A.add(B.subtract(A).scale(hf)).add(0, 1.2, 0), 0.14, la, false);
        // corridor edges
        double edge = ease((t - BLINK) / 12.0) * fadeOut;
        if (edge > 0.004) {
            line(at(0, W, 0.15), at(1, W, 0.15), 0.07, (float) (edge * 0.6), false);
            line(at(0, -W, 0.15), at(1, -W, 0.15), 0.07, (float) (edge * 0.6), false);
        }
        // cage lines, sweeping from start to end
        double flash = 0;
        List<int[]> cuts = ent.cuts();
        for (int k = 0; k < cuts.size(); k++) {
            double age = t - (CUT0 + k * STEP);
            if (age >= 0 && age < 3) {
                flash = Math.max(flash, 1.0 - age / 3.0);
            }
        }
        for (int i = 0; i < LINES; i++) {
            Random r = new Random(seed + i * 31L);
            double f0 = r.nextDouble();
            double f1 = Math.max(0, Math.min(1, f0 + (r.nextDouble() - 0.5) * (22.0 / len)));
            Vec3 a = at(f0, (r.nextDouble() * 2 - 1) * W, r.nextDouble() * 12);
            Vec3 b = at(f1, (r.nextDouble() * 2 - 1) * W, r.nextDouble() * 12);
            double spawn = BLINK + f0 * 28.0;
            double u = c01((t - spawn) / 3.0);
            if (u <= 0) {
                continue;
            }
            Vec3 head = a.add(b.subtract(a).scale(u));
            float al = (float) ((0.42 + 0.4 * flash) * c01(0.4 + u) * fadeOut);
            double w = 0.045 + r.nextDouble() * 0.05;
            line(a, head, w, al, r.nextInt(7) == 0);
            if (u < 1.0 && a.distanceTo(head) > 0.5) {
                line(head.add(a.subtract(head).normalize().scale(1.2)), head, w * 1.6, 0.9f, false);
            }
        }
        // slice lines through every victim, per cut
        for (int k = 0; k < cuts.size(); k++) {
            double age = t - (CUT0 + k * STEP);
            if (age < 0) {
                continue;
            }
            double persist = c01((FINAL + 6 - t) / 8.0);
            float al = (float) ((age < 8 ? 1.0 - 0.7 * (age / 8.0) : 0.3) * persist);
            for (int id : cuts.get(k)) {
                Vec3 c = targetCentre(id);
                if (c == null) {
                    continue;
                }
                Random r = new Random(seed + id * 131L + k * 17L);
                for (int s = 0; s < 2; s++) {
                    Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                    double l = 6.0 * ease(age / 2.0);
                    line(c.subtract(d.scale(l)), c.add(d.scale(l)), 0.06, al, s == 1 && k % 3 == 0);
                }
            }
        }
        // the sheathing click: thin vertical flash at the end point
        if (t >= SHEATH && t < SHEATH + 10) {
            float al = (float) (1.0 - (t - SHEATH) / 10.0);
            line(B, B.add(0, 10 * ease((t - SHEATH) / 3.0), 0), 0.16, al, false);
        }
    }

    /** Giant crescent afterimages along the blink line, plus a crescent per victim per cut. */
    private void slashPass() {
        for (int i = 0; i < 7; i++) {
            double f = (i + 0.5) / 7.0;
            double age = t - (BLINK + i * 1.5);
            if (age < 0 || age > 16) {
                continue;
            }
            float al = (float) ((1.0 - age / 16.0) * 0.8);
            p.billboard(at(f, 0, 3.0 + (i % 2) * 2.0), 9.0 * (0.7 + 0.3 * c01(age / 4.0)), -0.5 + i * 0.15, Pen.ICE, al, 0f, 0f, 1f, 1f);
        }
        List<int[]> cuts = ent.cuts();
        for (int k = 0; k < cuts.size(); k++) {
            double age = t - (CUT0 + k * STEP);
            if (age < 0 || age > 5) {
                continue;
            }
            float al = (float) (1.0 - age / 5.0);
            Random r = new Random(seed + k * 977L);
            for (int id : cuts.get(k)) {
                Vec3 c = targetCentre(id);
                if (c != null) {
                    p.billboard(c, 2.6, r.nextDouble() * 6.28, Pen.ICE, al, 0f, 0f, 1f, 1f);
                }
            }
        }
    }

    private void glintPass() {
        double on = ease((t - 6) / 12.0) * fadeOut;
        for (int i = 0; i < 44; i++) {
            Random r = new Random(seed + i * 53L + 9);
            Vec3 pos = at(r.nextDouble(), (r.nextDouble() * 2 - 1) * W, 0.3 + r.nextDouble() * 10.0);
            double tw = 0.5 + 0.5 * Math.sin(t * (0.25 + r.nextDouble() * 0.3) + r.nextDouble() * 6.28);
            p.billboard(pos, 0.3 + 0.3 * tw, 0, Pen.WHITE, (float) (on * tw * 0.8));
        }
        List<int[]> cuts = ent.cuts();
        for (int k = 0; k < cuts.size(); k++) {
            double age = t - (CUT0 + k * STEP);
            if (age < 0 || age > 6) {
                continue;
            }
            float al = (float) (1.0 - age / 6.0);
            for (int id : cuts.get(k)) {
                Vec3 c = targetCentre(id);
                if (c != null) {
                    p.billboard(c, 1.8 + 0.8 * (1 - al), 0.0, Pen.WHITE, al);
                }
            }
        }
        if (t < 8) {
            p.billboard(A.add(0, 1, 0), 2.4, 0, Pen.WHITE, (float) (1.0 - t / 8.0));
        }
        if (t >= BLINK && t < BLINK + 10) {
            double age = t - BLINK;
            p.billboard(B.add(0, 1.2, 0), 2.5 + age * 0.3, 0, Pen.WHITE, (float) (1.0 - age / 10.0));
        }
        if (t >= SHEATH && t < SHEATH + 8) {
            double age = t - SHEATH;
            p.billboard(B.add(0, 1.4, 0), 3.6, 0, Pen.CORE, (float) (1.0 - age / 8.0));
        }
        if (t >= FINAL - 1 && t < FINAL + 12) {
            double age = t - FINAL + 1;
            for (int i = 0; i < 5; i++) {
                p.billboard(at(0.1 + i * 0.2, 0, 6), 4.0 + age * 1.4, 0, Pen.CORE, (float) (1.0 - age / 13.0));
            }
        }
    }

    private void ringPass() {
        double on = ease((t - 1) / 10.0) * fadeOut;
        if (on > 0.003) {
            p.plane(A.add(0, 0.07, 0), X, Z, 7.0, t * 0.02, Pen.ICE, (float) (on * 0.6 * (1 - c01((t - 10) / 20.0) * 0.5)));
            p.plane(B.add(0, 0.07, 0), X, Z, 9.0, -t * 0.02, Pen.ICE, (float) (on * 0.7));
            p.plane(B.add(0, 0.08, 0), X, Z, 5.0, t * 0.04, Pen.CYAN, (float) (on * 0.5));
        }
        List<int[]> cuts = ent.cuts();
        for (int k = 0; k < cuts.size(); k++) {
            double age = t - (CUT0 + k * STEP);
            if (age < 0 || age > 10) {
                continue;
            }
            p.plane(B.add(0, 0.1, 0), X, Z, 1.5 + W * 0.7 * ease(age / 10.0), 0, Pen.ICE, (float) (0.4 * (1 - age / 10.0)));
        }
        if (t >= SHEATH && t < SHEATH + 16) {
            double age = t - SHEATH;
            p.plane(B.add(0, 0.12, 0), X, Z, 2.0 + W * 1.1 * ease(age / 14.0), 0, Pen.CORE, (float) (0.8 * (1 - age / 16.0)));
        }
        if (t >= FINAL && t < FINAL + 18) {
            double age = t - FINAL;
            for (int i = 0; i < 5; i++) {
                p.plane(at(0.1 + i * 0.2, 0, 0.12), X, Z, W * (0.3 + 1.2 * ease(age / 16.0)), i, Pen.CORE, (float) (0.6 * (1 - age / 18.0)));
            }
        }
    }

    private void mistPass() {
        double on = ease((t - 3) / 14.0) * fadeOut;
        if (on <= 0.003) {
            return;
        }
        int n = (int) Math.ceil(len / (W * 1.6));
        for (int i = 0; i < n; i++) {
            double f = (i + 0.5) / n;
            Vec3 c = at(f, 0, 0.05 + 0.03 * (i % 2));
            p.plane(c, dirH, side, W * 1.05, t * 0.008 * (i % 2 == 0 ? 1 : -1) + i * 1.3, Pen.BLUE, (float) (on * 0.22));
        }
    }

    private void shardPass() {
        double age = t - FINAL;
        for (int i = 0; i < 90; i++) {
            Random r = new Random(seed + i * 211L + 5);
            double f = r.nextDouble();
            double lat = (r.nextDouble() * 2 - 1) * W;
            double spawn = BLINK + 3 + f * 30.0;
            double fin = c01((t - spawn) / 5.0);
            if (fin <= 0) {
                continue;
            }
            Vec3 home = at(f, lat, 0.8 + r.nextDouble() * 11.0);
            double half = 0.35 + r.nextDouble() * 0.9;
            int cell = r.nextInt(4);
            double rot = r.nextDouble() * 6.28;
            Vec3 pos = home.add(0, Math.sin(t * 0.05 + i) * 0.12, 0);
            double al = 0.9 * fin;
            if (t >= FINAL) {
                Vec3 out = side.scale(lat >= 0 ? 1 : -1).add(dirH.scale(r.nextGaussian() * 0.4)).add(0, 0.5 + r.nextDouble() * 0.6, 0).normalize();
                double sp = 0.5 + r.nextDouble() * 1.1;
                pos = home.add(out.scale(sp * age * 0.9)).add(0, -0.012 * age * age, 0);
                rot += age * (0.1 + r.nextDouble() * 0.2);
                al *= 1.0 - c01((age - 10) / 28.0);
            }
            shard(pos, half * (t >= FINAL ? 1.0 : (0.4 + 0.6 * fin)), rot, cell, (float) al);
        }
        if (t >= FINAL - 1 && t < END) {
            for (int i = 0; i < 70; i++) {
                Random r = new Random(seed + i * 457L + 77);
                double f = r.nextDouble();
                Vec3 o = at(f, (r.nextDouble() * 2 - 1) * 3, 1 + r.nextDouble() * 8);
                double ang = r.nextDouble() * 6.28;
                Vec3 v = new Vec3(Math.cos(ang), 0.3 + r.nextDouble() * 0.5, Math.sin(ang)).scale(0.6 + r.nextDouble() * 1.4);
                Vec3 pos = o.add(v.scale(age * 0.85)).add(0, -0.01 * age * age, 0);
                double al = 0.95 * c01((age + 1) / 2.0) * (1.0 - c01((age - 12) / 30.0));
                shard(pos, 0.3 + r.nextDouble() * 0.7, r.nextDouble() * 6.28 + age * (0.15 + r.nextDouble() * 0.3), r.nextInt(4), (float) al);
            }
            for (int i = 0; i < 12; i++) {
                Random r = new Random(seed + i * 613L + 3);
                Vec3 pos = at(r.nextDouble(), (r.nextDouble() * 2 - 1) * W, 20 + r.nextDouble() * 14 - age * 0.08);
                double al = 0.6 * ease((age + 6) / 12.0) * (1.0 - c01((age - 14) / 28.0));
                shard(pos, 2.5 + r.nextDouble() * 2.5, r.nextDouble() * 6.28 + age * 0.01, r.nextInt(4), (float) al);
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

    /** Black-interior tears in space along the corridor, opening at the shatter. */
    private void riftPass() {
        if (t < FINAL - 6 || t > FINAL + 46) {
            return;
        }
        double open = ease((t - FINAL + 6) / 14.0);
        double close = 1.0 - c01((t - FINAL - 24) / 20.0);
        double h = 9.0 * open;
        double w = (2.4 + 1.3 * open) * open * Math.sqrt(close);
        if (h <= 0.05 || w <= 0.02) {
            return;
        }
        float al = (float) Math.min(1.0, 0.4 + open);
        for (int i = 0; i < 5; i++) {
            Random r = new Random(seed + i * 71L + 13);
            Vec3 c = at(0.1 + i * 0.2 + (r.nextDouble() - 0.5) * 0.06, (r.nextDouble() * 2 - 1) * 5.0, 8.0);
            Vec3 d = p.cam.subtract(c);
            Vec3 s = new Vec3(-d.z, 0, d.x);
            s = s.lengthSqr() < 1.0E-6 ? X : s.normalize();
            Vec3 sw = s.scale(w);
            Vec3 up = Y.scale(h);
            p.quad(c.subtract(sw).add(up), c.add(sw).add(up), c.add(sw).subtract(up), c.subtract(sw).subtract(up), 0f, 0f, 1f, 1f, Pen.WHITE, al);
        }
    }
}
