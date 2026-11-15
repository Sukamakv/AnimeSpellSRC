package com.example.jjkspells.vergil.client;

import com.example.jjkspells.vergil.entity.DimensionSeverEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
 * Draws "Dimension Sever: Shattered Sky": the sky is darkened (client-side only), a cage of cut space closes around a 50 block zone, the sky
 * dome cracks outward from the zenith into glass panes, eight sheets of falling sky crash down in pulses, a black rift opens at the centre
 * while debris spirals into it, and at the end the panes fall and everything bursts. Glow stays low: thin lines, glass panes and small glints.
 */
public class DimensionSeverRenderer extends EntityRenderer<DimensionSeverEntity> {
    private static final int SLASH = DimensionSeverEntity.SLASH;
    private static final int VOID = DimensionSeverEntity.VOID;
    private static final int FINAL = DimensionSeverEntity.FINAL;
    private static final int END = DimensionSeverEntity.END;
    private static final int PULSES = DimensionSeverEntity.PULSES;
    private static final double R = DimensionSeverEntity.R;
    private static final double RD = 130.0;
    private static final double[] DEG = {90, 72, 54, 38, 24, 12, 4};
    private static final int SECT = 20;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 E;
    private int seed;
    private DimensionSeverEntity ent;
    private double fade;

    public DimensionSeverRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(DimensionSeverEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(DimensionSeverEntity e) {
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
    public void render(DimensionSeverEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        seed = e.getId() * 8221;
        E = e.getPosition(pt);
        fade = 1.0 - c01((t - FINAL - 14) / 30.0);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), E, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        dimPass(buf);
        alpha(buf, VergilRender.SHARD);
        panePass();
        alpha(buf, VergilRender.RIFT);
        riftPass();
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

    // ------------------------------------------------------------------ sky

    private void dv(VertexConsumer vc, Vec3 w, float r, float g, float b, float a) {
        vc.vertex(p.m, (float) (w.x - p.org.x), (float) (w.y - p.org.y), (float) (w.z - p.org.z)).color(r, g, b, a).endVertex();
    }

    private Vec3 skyPoint(Vec3 c, double radius, double deg, double ang) {
        double e = Math.toRadians(deg);
        return c.add(Math.cos(e) * Math.cos(ang) * radius, Math.sin(e) * radius, Math.cos(e) * Math.sin(ang) * radius);
    }

    /** Client-only darkening of the sky: a translucent dome around the camera. Nothing in the world changes. */
    private void dimPass(MultiBufferSource buf) {
        double a = 0.55 * ease((t - 4) / 16.0) * (1.0 - c01((t - FINAL - 10) / 30.0));
        if (a < 0.01) {
            return;
        }
        VertexConsumer vc = buf.getBuffer(VergilRender.dim());
        Vec3 c = p.cam;
        int rings = 8;
        int sect = 24;
        for (int i = 0; i < rings; i++) {
            double e0 = -6.0 + 96.0 * i / rings;
            double e1 = -6.0 + 96.0 * (i + 1) / rings;
            for (int j = 0; j < sect; j++) {
                double a0 = j * Math.PI * 2 / sect;
                double a1 = (j + 1) * Math.PI * 2 / sect;
                float k = (float) (0.4 + 0.6 * (1.0 - (e0 + 6.0) / 96.0));
                float r = 0.01f * k;
                float g = 0.03f * k;
                float b = 0.11f * k;
                dv(vc, skyPoint(c, 160, e0, a0), r, g, b, (float) a);
                dv(vc, skyPoint(c, 160, e0, a1), r, g, b, (float) a);
                dv(vc, skyPoint(c, 160, e1, a1), r, g, b, (float) a);
                dv(vc, skyPoint(c, 160, e1, a0), r, g, b, (float) a);
            }
        }
    }

    /** Shared vertex of the crack web, slightly jittered so the glass looks irregular. */
    private Vec3 web(int i, int j) {
        int jj = ((j % SECT) + SECT) % SECT;
        Random r = new Random(seed * 31L + i * 131L + jj * 17L);
        double deg = DEG[i] + (i > 0 && i < DEG.length - 1 ? (r.nextDouble() - 0.5) * 6.0 : 0);
        double ang = jj * Math.PI * 2 / SECT + (i > 0 ? (r.nextDouble() - 0.5) * 0.12 : 0);
        return skyPoint(E, RD, deg, ang);
    }

    private double reveal(int i) {
        return SLASH + (90.0 - DEG[i]) / 90.0 * 26.0;
    }

    /** Glass panes of the cracked sky; they fall after the final shatter. */
    private void panePass() {
        for (int i = 0; i < DEG.length - 1; i++) {
            double rev = reveal(i + 1) + 2;
            double a = 0.6 * c01((t - rev) / 6.0);
            if (a <= 0.01) {
                continue;
            }
            for (int j = 0; j < SECT; j++) {
                Random r = new Random(seed * 7L + i * 53L + j * 11L);
                int cell = r.nextInt(4);
                Vec3 v00 = web(i, j);
                Vec3 v01 = web(i, j + 1);
                Vec3 v11 = web(i + 1, j + 1);
                Vec3 v10 = web(i + 1, j);
                double al = a;
                if (t >= FINAL) {
                    double age = t - FINAL - r.nextDouble() * 10.0;
                    if (age > 0) {
                        Vec3 centre = v00.add(v11).scale(0.5);
                        Vec3 out = new Vec3(centre.x - E.x, 0, centre.z - E.z).normalize().scale(0.25 * age);
                        Vec3 shift = out.add(0, -0.05 * age * age, 0);
                        v00 = v00.add(shift);
                        v01 = v01.add(shift);
                        v11 = v11.add(shift);
                        v10 = v10.add(shift);
                        al *= 1.0 - c01((age - 20) / 20.0);
                    }
                }
                double u0 = (cell % 2) * 0.5;
                double v0 = (cell / 2) * 0.5;
                p.quad(v00, v01, v11, v10, (float) u0, (float) v0, (float) (u0 + 0.5), (float) (v0 + 0.5), Pen.WHITE, (float) al);
            }
        }
    }

    // ------------------------------------------------------------------ lines

    private void linePass() {
        // caster slash towards the point
        Entity own = ent.owner();
        if (own != null && t < SLASH + 4) {
            Vec3 look = own.getLookAngle();
            Vec3 from = own.getEyePosition().add(look.scale(1.2)).add(0, -0.25, 0);
            Vec3 to = E.add(0, 1.0, 0);
            double f = ease(t / 8.0);
            line(from, from.add(to.subtract(from).scale(f)), 0.1, (float) (1.0 - c01((t - 8) / 8.0)), false);
        }
        // the opening cut: a vertical line from the ground to the zenith
        double sa = t - (SLASH - 2);
        if (sa >= 0 && sa < 26) {
            double grow = ease(sa / 3.0);
            float al = (float) (1.0 - c01((sa - 4) / 22.0));
            line(E, E.add(0, RD * grow, 0), 0.5 + 1.2 * (1.0 - sa / 26.0), al, false);
        }
        // cage of cut space around the zone
        double on = ease((t - SLASH) / 10.0) * fade;
        if (on > 0.004) {
            float al = (float) (0.45 * on);
            for (int h = 0; h < 3; h++) {
                double y = h == 0 ? 0.5 : (h == 1 ? 22 : 44);
                Vec3 prev = null;
                for (int s = 0; s <= 48; s++) {
                    double ang = s * Math.PI * 2 / 48;
                    Vec3 cur = E.add(Math.cos(ang) * R, y, Math.sin(ang) * R);
                    if (prev != null) {
                        line(prev, cur, 0.14, al, false);
                    }
                    prev = cur;
                }
            }
            for (int s = 0; s < 24; s++) {
                double ang = s * Math.PI * 2 / 24;
                Vec3 b = E.add(Math.cos(ang) * R, 0.5, Math.sin(ang) * R);
                line(b, b.add(0, 60 * ease((t - SLASH - s * 0.3) / 12.0), 0), 0.14, al, s % 7 == 0);
            }
        }
        // cracks in the sky
        double skyFade = 1.0 - c01((t - FINAL) / 8.0);
        for (int i = 1; i < DEG.length; i++) {
            double a = c01((t - reveal(i)) / 4.0) * skyFade;
            if (a > 0.01) {
                for (int j = 0; j < SECT; j++) {
                    line(web(i, j), web(i, j + 1), 0.8, (float) (0.8 * a), false);
                }
            }
        }
        for (int i = 0; i < DEG.length - 1; i++) {
            double a = c01((t - reveal(i + 1)) / 4.0) * skyFade;
            if (a > 0.01) {
                for (int j = 0; j < SECT; j++) {
                    line(web(i, j), web(i + 1, j), 0.8, (float) (0.8 * a), j % 9 == 0);
                }
            }
        }
        // trails of the falling sky sheets
        for (int k = 1; k <= PULSES; k++) {
            int tp = DimensionSeverEntity.eventTick(k);
            double f = (t - (tp - 10)) / 10.0;
            if (f < 0 || f > 1) {
                continue;
            }
            for (int i = 0; i < 14; i++) {
                Vec3 imp = impact(k, i);
                Vec3 pos = imp.add(0, 110 * (1 - f * f), 0);
                line(pos, pos.add(0, 16, 0), 0.25, 0.6f, false);
            }
        }
        // victims
        List<int[]> ev = ent.events();
        for (int k = 0; k < ev.size(); k++) {
            double a = t - DimensionSeverEntity.eventTick(k);
            if (a < 0 || a > 10) {
                continue;
            }
            float al = (float) (1.0 - 0.7 * a / 10.0);
            for (int id : ev.get(k)) {
                Vec3 c = targetCentre(id);
                if (c == null) {
                    continue;
                }
                Random r = new Random(seed + id * 131L + k * 17L);
                for (int s = 0; s < 2; s++) {
                    Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                    double l = 10.0 * ease(a / 2.0);
                    line(c.subtract(d.scale(l)), c.add(d.scale(l)), 0.08, al, s == 1 && k % 3 == 0);
                }
            }
        }
        // final beam
        double fa = t - FINAL;
        if (fa >= -2 && fa < 18) {
            line(E, E.add(0, 150, 0), 1.6 * (1.0 - c01(fa / 18.0)) + 0.2, (float) (1.0 - c01(fa / 18.0)), false);
        }
    }

    private Vec3 impact(int k, int i) {
        Random r = new Random(seed + k * 1009L + i * 13L);
        double ang = r.nextDouble() * 6.28;
        double rad = Math.sqrt(r.nextDouble()) * R * 0.92;
        return E.add(Math.cos(ang) * rad, 0.1, Math.sin(ang) * rad);
    }

    private void slashPass() {
        Entity own = ent.owner();
        if (own != null && t < SLASH + 4) {
            Vec3 at = own.getEyePosition().add(own.getLookAngle().scale(1.6)).add(0, -0.3, 0);
            p.billboard(at, 2.2 * ease(t / 4.0) + 0.3, -0.5, Pen.CORE, (float) (1.0 - c01(t / (SLASH + 4.0))), 0f, 0f, 1f, 1f);
        }
        List<int[]> ev = ent.events();
        for (int k = 0; k < ev.size(); k++) {
            double a = t - DimensionSeverEntity.eventTick(k);
            if (a < 0 || a > 5) {
                continue;
            }
            float al = (float) (1.0 - a / 5.0);
            Random r = new Random(seed + k * 977L);
            for (int id : ev.get(k)) {
                Vec3 c = targetCentre(id);
                if (c != null) {
                    p.billboard(c, 4.0, r.nextDouble() * 6.28, Pen.ICE, al, 0f, 0f, 1f, 1f);
                }
            }
        }
    }

    private void glintPass() {
        double on = ease((t - 8) / 14.0) * fade;
        for (int i = 0; i < 60; i++) {
            Random r = new Random(seed + i * 53L + 9);
            double ang = r.nextDouble() * 6.28;
            double rad = Math.sqrt(r.nextDouble()) * R;
            Vec3 at = E.add(Math.cos(ang) * rad, 0.3 + r.nextDouble() * 30.0, Math.sin(ang) * rad);
            double tw = 0.5 + 0.5 * Math.sin(t * (0.25 + r.nextDouble() * 0.3) + r.nextDouble() * 6.28);
            p.billboard(at, 0.4 + 0.4 * tw, 0, Pen.WHITE, (float) (on * tw * 0.8));
        }
        for (int k = 1; k <= PULSES; k++) {
            double a = t - DimensionSeverEntity.eventTick(k);
            if (a < 0 || a > 7) {
                continue;
            }
            for (int i = 0; i < 14; i++) {
                p.billboard(impact(k, i).add(0, 1, 0), 3.0 + a * 0.5, 0, Pen.WHITE, (float) (1.0 - a / 7.0));
            }
        }
        List<int[]> ev = ent.events();
        for (int k = 0; k < ev.size(); k++) {
            double a = t - DimensionSeverEntity.eventTick(k);
            if (a < 0 || a > 7) {
                continue;
            }
            float al = (float) (1.0 - a / 7.0);
            for (int id : ev.get(k)) {
                Vec3 c = targetCentre(id);
                if (c != null) {
                    p.billboard(c, 2.4 + 1.0 * (1 - al), 0.0, Pen.WHITE, al);
                }
            }
        }
        if (t >= SLASH - 1 && t < SLASH + 12) {
            double a = t - SLASH + 1;
            p.billboard(E.add(0, 3, 0), 8.0 + a * 2.0, 0, Pen.CORE, (float) (1.0 - a / 13.0));
        }
        double fa = t - FINAL;
        if (fa >= -1 && fa < 18) {
            p.billboard(E.add(0, 6, 0), 20.0 + (fa + 1) * 3.0, 0, Pen.CORE, (float) (1.0 - (fa + 1) / 19.0));
        }
    }

    private void ringPass() {
        double on = ease(t / 12.0) * fade;
        if (on > 0.003) {
            Vec3 c = E.add(0, 0.06, 0);
            p.plane(c, X, Z, R * 1.04, t * 0.008, Pen.ICE, (float) (on * 0.6));
            p.plane(c.add(0, 0.01, 0), X, Z, R * 0.6, -t * 0.015, Pen.CYAN, (float) (on * 0.5));
            p.plane(c.add(0, 0.02, 0), X, Z, R * 0.25, t * 0.03, Pen.CORE, (float) (on * 0.5));
        }
        double sa = t - SLASH;
        if (sa >= 0 && sa < 22) {
            p.plane(E.add(0, 0.12, 0), X, Z, R * (0.15 + 1.3 * ease(sa / 18.0)), 0, Pen.CORE, (float) (0.9 * (1 - sa / 22.0)));
        }
        for (int k = 1; k <= PULSES; k++) {
            double a = t - DimensionSeverEntity.eventTick(k);
            if (a < 0 || a > 12) {
                continue;
            }
            for (int i = 0; i < 14; i++) {
                p.plane(impact(k, i), X, Z, 2.0 + 8.0 * ease(a / 12.0), 0, Pen.ICE, (float) (0.5 * (1 - a / 12.0)));
            }
        }
        double fa = t - FINAL;
        if (fa >= 0 && fa < 26) {
            p.plane(E.add(0, 0.12, 0), X, Z, R * (0.2 + 2.0 * ease(fa / 22.0)), 0, Pen.CORE, (float) (0.9 * (1 - fa / 26.0)));
            p.plane(E.add(0, 0.12, 0), X, Z, R * (0.1 + 1.4 * ease(fa / 18.0)), 1.0, Pen.CYAN, (float) (0.6 * (1 - fa / 26.0)));
        }
    }

    private void mistPass() {
        double on = ease((t - 4) / 16.0) * fade;
        if (on <= 0.003) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            double s = R * (1.06 - 0.12 * i);
            double rot = t * (0.006 + 0.003 * i) * (i % 2 == 0 ? 1 : -1) + i * 1.7;
            p.plane(E.add(0, 0.05 + 0.6 * i, 0), X, Z, s, rot, Pen.BLUE, (float) (on * (0.2 - 0.04 * i)));
        }
    }

    // ------------------------------------------------------------------ rift and debris

    private void riftPass() {
        if (t < VOID || t > FINAL + 16) {
            return;
        }
        double open = ease((t - VOID) / 16.0);
        double close = c01((t - FINAL) / 12.0);
        double h = 30.0 * open * (1.0 - close);
        double w = 12.0 * open * (1.0 - close);
        if (h <= 0.1 || w <= 0.05) {
            return;
        }
        Vec3 c = E.add(0, h * 0.92, 0);
        Vec3 d = p.cam.subtract(c);
        Vec3 s0 = new Vec3(-d.z, 0, d.x);
        s0 = s0.lengthSqr() < 1.0E-6 ? X : s0.normalize();
        double[] rot = {0, 1.05, -1.05};
        for (double ang : rot) {
            double cs = Math.cos(ang);
            double sn = Math.sin(ang);
            Vec3 s = new Vec3(s0.x * cs - s0.z * sn, 0, s0.x * sn + s0.z * cs).scale(w);
            Vec3 up = Y.scale(h);
            p.quad(c.subtract(s).add(up), c.add(s).add(up), c.add(s).subtract(up), c.subtract(s).subtract(up), 0f, 0f, 1f, 1f, Pen.WHITE, 0.95f);
        }
    }

    private void shardPass() {
        // falling sky sheets
        for (int k = 1; k <= PULSES; k++) {
            int tp = DimensionSeverEntity.eventTick(k);
            double f = (t - (tp - 10)) / 10.0;
            if (f < 0 || f > 1.15) {
                continue;
            }
            for (int i = 0; i < 14; i++) {
                Random r = new Random(seed + k * 337L + i * 19L);
                Vec3 pos = impact(k, i).add(0, 110 * (1 - Math.min(f, 1.0) * Math.min(f, 1.0)) + 1.5, 0);
                double al = 0.95 * (1.0 - c01((f - 1.0) / 0.15));
                shard(pos, 3.0 + r.nextDouble() * 3.0, r.nextDouble() * 6.28 + t * 0.05, r.nextInt(4), (float) al);
            }
        }
        // debris spiralling into the rift, then bursting at the final shatter
        double fa = Math.max(0, t - FINAL);
        for (int i = 0; i < 160; i++) {
            if (i >= 80 && t < FINAL) {
                continue;
            }
            Random r = new Random(seed + i * 211L + 5);
            double a0 = r.nextDouble() * 6.28;
            double prog = c01((t - (VOID - 10)) / (FINAL - VOID + 10.0));
            double rad = (42 - 35 * prog * (0.6 + 0.4 * r.nextDouble())) * (0.6 + 0.4 * r.nextDouble());
            double h = 2.0 + r.nextDouble() * 38.0;
            double ang = a0 + (t - VOID) * (0.04 + 0.08 * prog);
            Vec3 pos = E.add(Math.cos(ang) * rad, h, Math.sin(ang) * rad);
            double al = 0.85 * ease((t - (VOID - 10) - i * 0.1) / 10.0);
            if (t >= FINAL) {
                Vec3 out = new Vec3(pos.x - E.x, 0.25, pos.z - E.z).normalize();
                pos = pos.add(out.scale((0.7 + r.nextDouble() * 1.8) * fa)).add(0, -0.012 * fa * fa, 0);
                al = 0.9 * c01(fa / 2.0 + (i < 80 ? 1 : 0)) * (1.0 - c01((fa - 12) / 32.0));
                if (i >= 80) {
                    pos = E.add(0, 12, 0).add(out.scale((0.7 + r.nextDouble() * 1.8) * fa)).add(0, -0.012 * fa * fa, 0);
                }
            }
            shard(pos, 0.5 + r.nextDouble() * 1.6, r.nextDouble() * 6.28 + t * 0.03, r.nextInt(4), (float) al);
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
