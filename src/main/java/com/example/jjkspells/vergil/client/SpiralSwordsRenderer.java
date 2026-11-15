package com.example.jjkspells.vergil.client;

import com.example.jjkspells.vergil.entity.SpiralSwordsEntity;
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
 * Draws "Spiral Swords": 48 giant spectral katanas materialise in three rings (each in a small summoning circle), turn and tighten in a
 * spiral above a huge ground seal, launch in six volleys that pin every enemy, then burst into glass.
 * Swords are textured alpha-blended models (detail stays readable); light is limited to thin trails and small glints.
 */
public class SpiralSwordsRenderer extends EntityRenderer<SpiralSwordsEntity> {
    private static final int SWORDS = SpiralSwordsEntity.VOLLEYS * SpiralSwordsEntity.PER_VOLLEY;
    private static final int PER = SpiralSwordsEntity.PER_VOLLEY;
    private static final int PULL = SpiralSwordsEntity.PULL;
    private static final int VOLLEY0 = SpiralSwordsEntity.VOLLEY0;
    private static final int STEP = SpiralSwordsEntity.STEP;
    private static final int FLIGHT = SpiralSwordsEntity.FLIGHT;
    private static final int FINAL = SpiralSwordsEntity.FINAL;
    private static final int END = SpiralSwordsEntity.END;
    private static final double R = SpiralSwordsEntity.R;
    private static final double LEN = 10.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 E;
    private int seed;
    private SpiralSwordsEntity ent;
    private double fadeOut;

    // output of pose()
    private Vec3 hilt;
    private Vec3 tip;
    private Vec3 prevTip;
    private Vec3 centre;
    private double flight;
    private double fin;

    public SpiralSwordsRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(SpiralSwordsEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SpiralSwordsEntity e) {
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
    public void render(SpiralSwordsEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        seed = e.getId() * 6151;
        E = e.getPosition(pt);
        fadeOut = 1.0 - c01((t - FINAL) / 6.0);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), E, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        alpha(buf, VergilRender.SHARD);
        shardPass();
        alpha(buf, VergilRender.SWORD);
        swordPass();
        add(buf, VergilRender.MIST);
        mistPass();
        add(buf, VergilRender.RING);
        ringPass();
        add(buf, VergilRender.LINE);
        linePass();
        add(buf, VergilRender.GLINT);
        glintPass();
    }

    // ------------------------------------------------------------------ sword model

    private Vec3 orbitPos(int j, double tt) {
        Random r = new Random(seed + j * 7919L);
        int ring = j % 3;
        double rad0 = 12.5 + ring * 3.8 + r.nextDouble() * 1.5;
        double h0 = 5.0 + ring * 3.2 + r.nextDouble() * 2.0;
        double th = j * 2.399963 + 0.018 * tt + (tt > PULL ? 0.0007 * (tt - PULL) * (tt - PULL) : 0);
        double rf = 1.0 - 0.4 * ease((tt - 30) / 40.0);
        return E.add(Math.cos(th) * rad0 * rf, h0 + 0.4 * Math.sin(tt * 0.07 + j), Math.sin(th) * rad0 * rf);
    }

    private Vec3 inward(Vec3 from) {
        Vec3 d = E.add(0, 1.5, 0).subtract(from);
        return new Vec3(d.x, d.y * 0.35, d.z).normalize();
    }

    private Vec3 targetFor(int j) {
        int k = j / PER;
        List<int[]> vs = ent.volleys();
        Random rr = new Random(seed + j * 131L);
        Vec3 off = new Vec3(rr.nextGaussian() * 0.5, rr.nextGaussian() * 0.4, rr.nextGaussian() * 0.5);
        if (k < vs.size() && vs.get(k).length > 0) {
            int[] ids = vs.get(k);
            Entity en = ent.level().getEntity(ids[(j % PER) % ids.length]);
            if (en != null && en.isAlive()) {
                return new Vec3(en.getX(), en.getY() + en.getBbHeight() * 0.5, en.getZ()).add(off);
            }
        }
        double ang = rr.nextDouble() * 6.28;
        double rad = Math.sqrt(rr.nextDouble()) * R * 0.8;
        return E.add(Math.cos(ang) * rad, 0.1, Math.sin(ang) * rad);
    }

    /** Computes the pose of sword j at time tt into the output fields; false when it does not exist yet. */
    private boolean pose(int j, double tt) {
        double s = 2.0 + j * 28.0 / SWORDS;
        fin = ease((tt - s) / 6.0);
        if (fin <= 0) {
            return false;
        }
        int k = j / PER;
        double len = LEN * (0.35 + 0.65 * fin);
        double launch = VOLLEY0 + k * STEP + (j % PER) * 0.35;
        boolean flying = tt >= launch && k < ent.volleys().size();
        flight = -1;
        if (!flying) {
            double at = Math.min(tt, launch);
            Vec3 c = orbitPos(j, at);
            Vec3 d = inward(c);
            centre = c;
            hilt = c.subtract(d.scale(len * 0.5));
            tip = c.add(d.scale(len * 0.5));
            prevTip = tip;
            return true;
        }
        Vec3 S = orbitPos(j, launch);
        Vec3 d0 = inward(S);
        Vec3 tip0 = S.add(d0.scale(len * 0.5));
        Vec3 T = targetFor(j);
        Vec3 dF = T.subtract(tip0).normalize();
        double f = (tt - launch) / FLIGHT;
        flight = f;
        double fe = c01(f);
        double b = c01(f * 3.0);
        Vec3 dir = d0.scale(1 - b).add(dF.scale(b)).normalize();
        tip = tip0.add(T.subtract(tip0).scale(fe * fe));
        double fp = c01(f - 0.3);
        prevTip = tip0.add(T.subtract(tip0).scale(fp * fp));
        hilt = tip.subtract(dir.scale(len));
        centre = tip.add(hilt).scale(0.5);
        return true;
    }

    // ------------------------------------------------------------------ passes

    private void swordPass() {
        if (fadeOut <= 0.003) {
            return;
        }
        for (int j = 0; j < SWORDS; j++) {
            if (!pose(j, t)) {
                continue;
            }
            float al = (float) (Math.min(1.0, fin * 1.3) * 0.95 * fadeOut);
            double w = LEN * 0.125 * (0.35 + 0.65 * fin);
            p.ribbon(hilt, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
        }
    }

    private void linePass() {
        // ground spiral arms that drag everything inward
        double on = ease((t - PULL) / 10.0) * fadeOut;
        if (on > 0.003) {
            for (int a = 0; a < 12; a++) {
                double a0 = a * Math.PI * 2 / 12.0;
                Vec3 prev = null;
                for (int s = 0; s <= 20; s++) {
                    double f = s / 20.0;
                    double rad = R * (1.0 - f) + 0.6;
                    double ang = a0 + f * 2.4 - t * 0.06;
                    Vec3 cur = E.add(Math.cos(ang) * rad, 0.12, Math.sin(ang) * rad);
                    if (prev != null) {
                        p.ribbon(prev, cur, 0.08, (float) (on * (0.15 + 0.45 * f)), 0f, 1f, Pen.ICE);
                    }
                    prev = cur;
                }
            }
        }
        // comet trails behind flying swords
        for (int j = 0; j < SWORDS; j++) {
            if (!pose(j, t) || flight < 0) {
                continue;
            }
            double fl = flight;
            float al = (float) ((1.0 - c01((fl - 1.0) / 0.4)) * fadeOut);
            if (al > 0.01f) {
                line(prevTip, tip, 0.12, al, j % 5 == 0);
                Vec3 back = tip.subtract(hilt).normalize();
                line(tip.subtract(back.scale(LEN * 0.9)), tip.subtract(back.scale(LEN * 0.9 + 6.0 * c01(fl * 2))), 0.05, al * 0.5f, false);
            }
        }
        // the vertical thin beam at the shatter
        if (t >= FINAL - 2 && t < FINAL + 16) {
            float al = (float) (1.0 - c01((t - FINAL) / 16.0));
            line(E, E.add(0, 38, 0), 0.4, al, false);
        }
    }

    private void glintPass() {
        double on = ease((t - 6) / 12.0) * fadeOut;
        for (int i = 0; i < 40; i++) {
            Random r = new Random(seed + i * 53L + 9);
            double ang = r.nextDouble() * 6.28;
            double rad = Math.sqrt(r.nextDouble()) * R;
            Vec3 at = E.add(Math.cos(ang) * rad, 0.3 + r.nextDouble() * 14.0, Math.sin(ang) * rad);
            double tw = 0.5 + 0.5 * Math.sin(t * (0.25 + r.nextDouble() * 0.3) + r.nextDouble() * 6.28);
            p.billboard(at, 0.3 + 0.3 * tw, 0, Pen.WHITE, (float) (on * tw * 0.8));
        }
        // landing flashes
        for (int j = 0; j < SWORDS; j++) {
            if (!pose(j, t) || flight < 1.0 || flight > 1.9) {
                continue;
            }
            float al = (float) (1.0 - (flight - 1.0) / 0.9);
            p.billboard(tip, 1.6 + 1.2 * (1 - al), 0, Pen.WHITE, al * (float) fadeOut);
        }
        Entity own = ent.owner();
        if (own != null && t < 12) {
            Vec3 at = own.getEyePosition().add(own.getLookAngle().scale(1.5)).add(0, -0.3, 0);
            p.billboard(at, 1.1, 0, Pen.WHITE, (float) (1.0 - t / 12.0));
        }
        if (t >= FINAL - 1 && t < FINAL + 14) {
            double age = t - FINAL + 1;
            p.billboard(E.add(0, 2, 0), 5.0 + age * 1.8, 0, Pen.CORE, (float) (1.0 - age / 15.0));
        }
    }

    /** Summoning circles at each sword, ground seal, volley ripples, shatter shock. */
    private void ringPass() {
        for (int j = 0; j < SWORDS; j++) {
            double age = t - (2.0 + j * 28.0 / SWORDS);
            if (age < 0 || age > 12) {
                continue;
            }
            double s = 2.0 + j * 28.0 / SWORDS;
            Vec3 c = orbitPos(j, s + 0.01);
            float al = (float) (c01(age / 3.0) * (1.0 - c01((age - 3.0) / 9.0)) * 0.8);
            p.billboard(c, 3.0 * (0.6 + 0.4 * c01(age / 6.0)), age * 0.25, Pen.ICE, al);
        }
        double on = ease((t - 1) / 14.0) * fadeOut;
        if (on > 0.003) {
            Vec3 c = E.add(0, 0.06, 0);
            p.plane(c, X, Z, R * 1.04, t * 0.012, Pen.ICE, (float) (on * 0.7));
            p.plane(c.add(0, 0.01, 0), X, Z, R * 0.66, -t * (0.02 + 0.0006 * Math.max(0, t - PULL)), Pen.CYAN, (float) (on * 0.5));
            p.plane(c.add(0, 0.02, 0), X, Z, R * 0.3, t * 0.04, Pen.CORE, (float) (on * 0.5));
        }
        for (int k = 0; k < ent.volleys().size(); k++) {
            double age = t - (VOLLEY0 + k * STEP + FLIGHT);
            if (age < 0 || age > 14) {
                continue;
            }
            p.plane(E.add(0, 0.09, 0), X, Z, R * (0.1 + 0.7 * ease(age / 14.0)), 0, Pen.ICE, (float) (0.45 * (1 - age / 14.0)));
        }
        if (t >= FINAL && t < FINAL + 20) {
            double age = t - FINAL;
            p.plane(E.add(0, 0.12, 0), X, Z, R * (0.3 + 1.9 * ease(age / 18.0)), 0, Pen.CORE, (float) (0.9 * (1 - age / 20.0)));
            p.plane(E.add(0, 0.12, 0), X, Z, R * (0.2 + 1.3 * ease(age / 15.0)), 1.0, Pen.CYAN, (float) (0.6 * (1 - age / 20.0)));
        }
    }

    private void mistPass() {
        double on = ease((t - 4) / 16.0) * fadeOut;
        if (on <= 0.003) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            double s = R * (1.08 - 0.12 * i);
            double rot = t * (0.012 + 0.006 * i + 0.0003 * Math.max(0, t - PULL)) * (i % 2 == 0 ? 1 : -1) + i * 1.7;
            p.plane(E.add(0, 0.05 + 0.6 * i, 0), X, Z, s, rot, Pen.BLUE, (float) (on * (0.22 - 0.05 * i)));
        }
    }

    /** Glass bursting out of every embedded sword at the shatter. */
    private void shardPass() {
        if (t < FINAL - 1 || t >= END) {
            return;
        }
        double age = t - FINAL;
        for (int i = 0; i < 96; i++) {
            Random r = new Random(seed + i * 457L + 77);
            int j = i % SWORDS;
            Vec3 origin;
            if (pose(j, FINAL)) {
                origin = hilt.add(tip.subtract(hilt).scale(r.nextDouble()));
            } else {
                origin = E.add(0, 2, 0);
            }
            Vec3 out = new Vec3(origin.x - E.x, 0, origin.z - E.z);
            out = out.lengthSqr() < 0.01 ? new Vec3(r.nextGaussian(), 0, r.nextGaussian()).normalize() : out.normalize();
            double sp = 0.5 + r.nextDouble() * 1.6;
            Vec3 v = out.scale(sp).add(0, 0.2 + r.nextDouble() * 0.6, 0);
            Vec3 pos = origin.add(v.scale(Math.max(age, 0))).add(0, -0.012 * age * age, 0);
            double al = 0.95 * c01((age + 1) / 2.0) * (1.0 - c01((age - 12) / 32.0));
            shard(pos, 0.4 + r.nextDouble() * 1.0, r.nextDouble() * 6.28 + age * (0.15 + r.nextDouble() * 0.3), r.nextInt(4), (float) al);
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
