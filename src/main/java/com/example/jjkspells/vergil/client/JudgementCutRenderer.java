package com.example.jjkspells.vergil.client;

import com.example.jjkspells.vergil.entity.JudgementCutEntity;
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
 * Draws "Judgement Cut End": thin crisp blue-white lines of cut space form a cage, hovering glass shards gather,
 * each cut flashes slice lines through every victim, then a black rift opens and the whole space bursts into glass.
 * Glow is kept minimal on purpose: lines are thin, detail comes from the textures.
 */
public class JudgementCutRenderer extends EntityRenderer<JudgementCutEntity> {
    private static final int RING = JudgementCutEntity.RING;
    private static final int CUT0 = JudgementCutEntity.CUT0;
    private static final int STEP = JudgementCutEntity.STEP;
    private static final int FINAL = JudgementCutEntity.FINAL;
    private static final int END = JudgementCutEntity.END;
    private static final double R = JudgementCutEntity.R;
    private static final int LINES = 150;
    private static final int SHARDS = 180;
    private static final int BURST = 140;
    private static final int GIANT = 20;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 E;
    private Vec3 C;
    private int seed;
    private JudgementCutEntity ent;
    private double fadeOut;

    public JudgementCutRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(JudgementCutEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(JudgementCutEntity e) {
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

    /** Thin crisp line: pale core ribbon over a slightly wider tinted one. */
    private void line(Vec3 a, Vec3 b, double w, float al, boolean violet) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.6, al * 0.45f, 0f, 1f, violet ? Pen.VIOLET : Pen.CYAN);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.CORE);
    }

    @Override
    public void render(JudgementCutEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        ent = e;
        seed = e.getId() * 7919;
        E = e.getPosition(pt);
        C = E.add(0, 9.0, 0);
        fadeOut = 1.0 - c01((t - FINAL - 8) / 30.0);
        if (t > END) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), E, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

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

    // ------------------------------------------------------------------ helpers

    private Vec3 ground(double ox, double oy, double oz) {
        return E.add(ox, oy, oz);
    }

    private Vec3 horizontalSide(Vec3 at) {
        Vec3 d = p.cam.subtract(at);
        Vec3 s = new Vec3(-d.z, 0, d.x);
        return s.lengthSqr() < 1.0E-6 ? X : s.normalize();
    }

    private Vec3 targetCentre(int id) {
        Entity en = ent.level().getEntity(id);
        if (en == null || !en.isAlive()) {
            return null;
        }
        return new Vec3(en.getX(), en.getY() + en.getBbHeight() * 0.5, en.getZ());
    }

    // ------------------------------------------------------------------ passes

    /** Cage: chords of cut space drawn across the zone. */
    private void linePass() {
        double zoneA = ease((t - 2) / 8.0) * fadeOut;
        // caster -> point cut line
        Entity own = ent.owner();
        if (own != null && t < RING + 4) {
            Vec3 look = own.getLookAngle();
            Vec3 from = own.getEyePosition().add(look.scale(1.2)).add(0, -0.25, 0);
            Vec3 to = E.add(0, 1.0, 0);
            double f = ease(t / 6.0);
            float a = (float) (1.0 - c01((t - 6) / 10.0));
            line(from, from.add(to.subtract(from).scale(f)), 0.07, a, false);
        }
        for (int i = 0; i < LINES; i++) {
            Random r = new Random(seed + i * 31L);
            Vec3 a = randomInZone(r);
            Vec3 b = randomInZone(r);
            int tries = 0;
            while (a.distanceTo(b) < 16.0 && tries++ < 6) {
                b = randomInZone(r);
            }
            double spawn = RING + r.nextDouble() * 28.0;
            double u = c01((t - spawn) / 3.0);
            if (u <= 0) {
                continue;
            }
            Vec3 head = a.add(b.subtract(a).scale(u));
            double flash = 0;
            for (int k = 0; k < ent.cuts().size(); k++) {
                double age = t - (CUT0 + k * STEP);
                if (age >= 0 && age < 4) {
                    flash = Math.max(flash, 1.0 - age / 4.0);
                }
            }
            float al = (float) ((0.42 + 0.4 * flash) * c01(0.4 + u) * fadeOut);
            double w = 0.06 + r.nextDouble() * 0.06;
            line(a, head, w, al, r.nextInt(7) == 0);
            // bright leading tip while the line is still being drawn
            if (u < 1.0) {
                line(head.add(a.subtract(head).normalize().scale(1.2)), head, w * 1.6, 0.9f, false);
            }
        }
        // slice lines through every victim, one burst per cut
        List<int[]> cuts = ent.cuts();
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
                    double len = 8.0 * ease(age / 2.0);
                    line(c.subtract(d.scale(len)), c.add(d.scale(len)), 0.06, al, s == 1 && k % 3 == 0);
                }
            }
        }
        // final rift edge line
        if (t > FINAL - 4 && t < FINAL + 10) {
            double f = ease((t - FINAL + 4) / 6.0);
            float al = (float) (1.0 - c01((t - FINAL - 2) / 8.0));
            Vec3 a = E.add(0, 9 - 14 * f, 0);
            Vec3 b = E.add(0, 9 + 14 * f, 0);
            line(a, b, 0.14, al, false);
        }
    }

    private Vec3 randomInZone(Random r) {
        double ang = r.nextDouble() * Math.PI * 2;
        double rad = Math.sqrt(r.nextDouble()) * R;
        return E.add(Math.cos(ang) * rad, -0.5 + r.nextDouble() * 20.0, Math.sin(ang) * rad);
    }

    /** Crescent flashes: the caster's slash and one per cut (on each victim, or in the zone when empty). */
    private void slashPass() {
        Entity own = ent.owner();
        if (own != null && t < RING) {
            Vec3 look = own.getLookAngle();
            Vec3 at = own.getEyePosition().add(look.scale(1.6)).add(0, -0.3, 0);
            float al = (float) (1.0 - c01(t / (double) RING));
            p.billboard(at, 1.6 * ease(t / 4.0) + 0.3, -0.5, Pen.CORE, al, 0f, 0f, 1f, 1f);
        }
        List<int[]> cuts = ent.cuts();
        for (int k = 0; k < cuts.size(); k++) {
            double age = t - (CUT0 + k * STEP);
            if (age < 0 || age > 5) {
                continue;
            }
            float al = (float) (1.0 - age / 5.0);
            int[] ids = cuts.get(k);
            Random r = new Random(seed + k * 977L);
            if (ids.length == 0) {
                Vec3 at = E.add((r.nextDouble() - 0.5) * R, 2 + r.nextDouble() * 10, (r.nextDouble() - 0.5) * R);
                p.billboard(at, 3.6, r.nextDouble() * 6.28, Pen.ICE, al, 0f, 0f, 1f, 1f);
            }
            for (int id : ids) {
                Vec3 c = targetCentre(id);
                if (c != null) {
                    p.billboard(c, 3.4, r.nextDouble() * 6.28, Pen.ICE, al, 0f, 0f, 1f, 1f);
                }
            }
        }
    }

    private void glintPass() {
        // twinkling sparks drifting over the zone
        double on = ease((t - 6) / 12.0) * fadeOut;
        for (int i = 0; i < 60; i++) {
            Random r = new Random(seed + i * 53L + 9);
            double ang = r.nextDouble() * 6.28;
            double rad = Math.sqrt(r.nextDouble()) * R;
            Vec3 at = E.add(Math.cos(ang) * rad, 0.3 + r.nextDouble() * 16.0, Math.sin(ang) * rad);
            double tw = 0.5 + 0.5 * Math.sin(t * (0.25 + r.nextDouble() * 0.3) + r.nextDouble() * 6.28);
            p.billboard(at, 0.22 + 0.25 * tw, 0, Pen.WHITE, (float) (on * tw * 0.8));
        }
        // glint on every victim per cut and on the point itself
        List<int[]> cuts = ent.cuts();
        for (int k = 0; k < cuts.size(); k++) {
            double age = t - (CUT0 + k * STEP);
            if (age < 0 || age > 7) {
                continue;
            }
            float al = (float) (1.0 - age / 7.0);
            for (int id : cuts.get(k)) {
                Vec3 c = targetCentre(id);
                if (c != null) {
                    p.billboard(c, 2.2 + 1.0 * (1 - al), 0.0, Pen.WHITE, al);
                }
            }
        }
        Entity own = ent.owner();
        if (own != null && t < RING) {
            Vec3 at = own.getEyePosition().add(own.getLookAngle().scale(1.6)).add(0, -0.3, 0);
            p.billboard(at, 1.0, 0, Pen.WHITE, (float) (1.0 - c01(t / 8.0)));
        }
        if (t < 10) {
            p.billboard(E.add(0, 1, 0), 1.8, 0, Pen.WHITE, (float) (1.0 - t / 10.0));
        }
        // shatter flash
        if (t >= FINAL - 1 && t < FINAL + 12) {
            double age = t - FINAL + 1;
            p.billboard(C, 7.0 + age * 2.4, 0, Pen.CORE, (float) (1.0 - age / 13.0));
        }
    }

    /** Ground seal, ripples and low blue mist. */
    private void ringPass() {
        double on = ease((t - 1) / 12.0) * fadeOut;
        if (on > 0.003) {
            Vec3 c = E.add(0, 0.06, 0);
            p.plane(c, X, Z, R * 1.04, t * 0.012, Pen.ICE, (float) (on * 0.75));
            p.plane(c.add(0, 0.01, 0), X, Z, R * 0.62, -t * 0.02, Pen.CYAN, (float) (on * 0.5));
            p.plane(c.add(0, 0.02, 0), X, Z, R * 0.28, t * 0.035, Pen.CORE, (float) (on * 0.5));
        }
        // a ripple per cut
        List<int[]> cuts = ent.cuts();
        for (int k = 0; k < cuts.size(); k++) {
            double age = t - (CUT0 + k * STEP);
            if (age < 0 || age > 12) {
                continue;
            }
            p.plane(E.add(0, 0.08, 0), X, Z, R * (0.15 + 0.9 * ease(age / 12.0)), 0, Pen.ICE, (float) (0.5 * (1 - age / 12.0)));
        }
        // shock ring at the shatter
        if (t >= FINAL && t < FINAL + 18) {
            double age = t - FINAL;
            p.plane(E.add(0, 0.12, 0), X, Z, R * (0.3 + 1.7 * ease(age / 16.0)), 0, Pen.CORE, (float) (0.9 * (1 - age / 18.0)));
            p.plane(E.add(0, 0.12, 0), X, Z, R * (0.2 + 1.2 * ease(age / 14.0)), 1.0, Pen.CYAN, (float) (0.6 * (1 - age / 18.0)));
        }
    }

    private void mistPass() {
        double on = ease((t - 4) / 16.0) * fadeOut;
        if (on <= 0.003) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            double s = R * (1.05 - 0.12 * i);
            double rot = t * (0.006 + 0.004 * i) * (i % 2 == 0 ? 1 : -1) + i * 1.7;
            p.plane(E.add(0, 0.05 + 0.5 * i, 0), X, Z, s, rot, Pen.BLUE, (float) (on * (0.22 - 0.05 * i)));
        }
    }

    /** Glass: hovering shards that gather per cut, then burst; giant shards of the broken sky above. */
    private void shardPass() {
        for (int i = 0; i < SHARDS; i++) {
            Random r = new Random(seed + i * 211L + 5);
            double spawn = RING + 4 + r.nextDouble() * (CUT0 + 7 * STEP - RING - 4);
            double fin = c01((t - spawn) / 5.0);
            if (fin <= 0) {
                continue;
            }
            double ang = r.nextDouble() * 6.28;
            double rad = Math.sqrt(r.nextDouble()) * R * 0.95;
            Vec3 home = E.add(Math.cos(ang) * rad, 0.8 + r.nextDouble() * 18.0, Math.sin(ang) * rad);
            double half = 0.5 + r.nextDouble() * 1.2;
            int cell = r.nextInt(4);
            double rot = r.nextDouble() * 6.28;
            double drift = Math.sin(t * 0.05 + i) * 0.12;
            Vec3 pos = home.add(0, drift, 0);
            double al = 0.9 * fin;
            if (t >= FINAL) {
                double age = t - FINAL;
                Vec3 dir = new Vec3(home.x - E.x, home.y - C.y + r.nextGaussian() * 2, home.z - E.z).normalize();
                double sp = 0.5 + r.nextDouble() * 1.0;
                pos = home.add(dir.scale(sp * age * 0.9)).add(0, -0.012 * age * age, 0);
                rot += age * (0.1 + r.nextDouble() * 0.2);
                al *= 1.0 - c01((age - 10) / 28.0);
            }
            shard(pos, half * (t >= FINAL ? 1.0 : (0.4 + 0.6 * fin)), rot, cell, (float) al, r);
        }
        if (t >= FINAL - 1 && t < END) {
            double age = t - FINAL;
            for (int i = 0; i < BURST; i++) {
                Random r = new Random(seed + i * 457L + 77);
                double ang = r.nextDouble() * 6.28;
                double el = (r.nextDouble() - 0.35) * 1.2;
                double sp = 0.6 + r.nextDouble() * 1.6;
                Vec3 v = new Vec3(Math.cos(ang) * Math.cos(el), Math.sin(el), Math.sin(ang) * Math.cos(el)).scale(sp);
                Vec3 pos = C.add(v.scale(age * 0.85)).add(0, -0.01 * age * age, 0);
                double al = 0.95 * c01(age / 2.0) * (1.0 - c01((age - 12) / 30.0));
                shard(pos, 0.3 + r.nextDouble() * 0.7, r.nextDouble() * 6.28 + age * (0.15 + r.nextDouble() * 0.3), r.nextInt(4), (float) al, r);
            }
            for (int i = 0; i < GIANT; i++) {
                Random r = new Random(seed + i * 613L + 3);
                double ang = r.nextDouble() * 6.28;
                double rad = r.nextDouble() * R * 1.1;
                Vec3 pos = E.add(Math.cos(ang) * rad, 28 + r.nextDouble() * 16 - age * 0.08, Math.sin(ang) * rad);
                double al = 0.6 * ease((age + 6) / 12.0) * (1.0 - c01((age - 14) / 28.0));
                shard(pos, 3.5 + r.nextDouble() * 3.5, r.nextDouble() * 6.28 + age * 0.01, r.nextInt(4), (float) al, r);
            }
        }
    }

    /** One glass shard sprite, picked from the 2x2 atlas, turned around a random axis-ish direction for variety. */
    private void shard(Vec3 c, double half, double rot, int cell, float al, Random r) {
        if (al <= 0.01f) {
            return;
        }
        double u0 = (cell % 2) * 0.5;
        double v0 = (cell / 2) * 0.5;
        Vec3 L = new Vec3(p.left.x(), p.left.y(), p.left.z());
        Vec3 U = new Vec3(p.up.x(), p.up.y(), p.up.z());
        // tilt the sprite out of the camera plane so shards read as 3D debris
        double tilt = 0.55 * Math.sin(rot * 1.7 + cell);
        Vec3 look = p.cam.subtract(c).normalize();
        Vec3 ax = L.scale(Math.cos(rot) * half).add(U.scale(Math.sin(rot) * half));
        Vec3 ay = L.scale(-Math.sin(rot) * half).add(U.scale(Math.cos(rot) * half)).add(look.scale(-tilt * half));
        p.quad(c.subtract(ax).subtract(ay), c.add(ax).subtract(ay), c.add(ax).add(ay), c.subtract(ax).add(ay),
                (float) u0, (float) v0, (float) (u0 + 0.5), (float) (v0 + 0.5), Pen.WHITE, al);
    }

    /** Black-interior tear in space that opens at the shatter. */
    private void riftPass() {
        if (t < FINAL - 6 || t > FINAL + 46) {
            return;
        }
        double open = ease((t - FINAL + 6) / 14.0);
        double close = 1.0 - c01((t - FINAL - 24) / 20.0);
        double h = 14.0 * open;
        double w = (3.5 + 1.8 * open) * open * Math.sqrt(close);
        if (h <= 0.05 || w <= 0.02) {
            return;
        }
        float al = (float) (close > 0 ? Math.min(1.0, 0.4 + open) : 0);
        Vec3 side = horizontalSide(C).scale(w);
        Vec3 up = Y.scale(h);
        p.quad(C.subtract(side).add(up), C.add(side).add(up), C.add(side).subtract(up), C.subtract(side).subtract(up),
                0f, 0f, 1f, 1f, Pen.WHITE, al);
    }
}
