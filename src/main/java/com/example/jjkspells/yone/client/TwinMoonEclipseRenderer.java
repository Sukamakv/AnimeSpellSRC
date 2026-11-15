package com.example.jjkspells.yone.client;

import com.example.jjkspells.yone.entity.TwinMoonEclipseEntity;
import com.example.jjkspells.yone.spell.TwinMoonEclipseSpell;
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
 * Draws "Twin Moon Eclipse": a blue moon and a red moon rise high over the target spot and slide together until they overlap into an eclipse with a
 * two-coloured corona. Beneath them a double-helix funnel of wind and shrinking rune rings drags everything in. When the moons align, a giant X of a
 * blue and a red katana cuts through the middle with flash, shock rings, ground cracks and an aftershock.
 */
public class TwinMoonEclipseRenderer extends EntityRenderer<TwinMoonEclipseEntity> {
    private static final int CHARGE = TwinMoonEclipseEntity.CHARGE;
    private static final int X_T = TwinMoonEclipseEntity.X_T;
    private static final int AFTER_T = TwinMoonEclipseEntity.AFTER_T;
    private static final int END = TwinMoonEclipseEntity.END;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final double MOON_H = 27.0;
    private static final double MOON_R = 8.5;
    private static final double X_H = 7.0;
    private static final double X_HALF = 15.0;
    private static final double BLADE = 11.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 D;
    private Vec3 S;
    private Vec3 M;
    private Vec3 XC;
    private Vec3 camPos;
    private MultiBufferSource buf;
    private TwinMoonEclipseEntity ent;

    public TwinMoonEclipseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(TwinMoonEclipseEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(TwinMoonEclipseEntity e) {
        return YoneRender.GLOW;
    }

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void add(ResourceLocation tex) {
        p.vc = buf.getBuffer(YoneRender.additive(tex));
    }

    private void alpha(ResourceLocation tex) {
        p.vc = buf.getBuffer(YoneRender.alpha(tex));
    }

    private void line(Vec3 a, Vec3 b, double w, float al, boolean blue) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.4, al * 0.45f, 0f, 1f, blue ? Pen.BLUE : Pen.RED);
        p.ribbon(a, b, w, al, 0f, 1f, blue ? Pen.ICE : Pen.ROSE);
    }

    // ------------------------------------------------------------------ geometry

    /** Moon centre: slides in from the side and ends overlapped on the axis. */
    private Vec3 moon(int col) {
        double k = ease(t / (double) CHARGE);
        double off = (1.0 - k) * 17.0 + 0.0;
        double rise = ease(t / 14.0);
        double s = (col == 0 ? -1 : 1) * off;
        return A.add(0, MOON_H * (0.45 + 0.55 * rise), 0).add(S.scale(s)).subtract(D.scale(6.0 * k));
    }

    private double moonAlpha() {
        return ease(t / 10.0) * (1.0 - c01((t - X_T - 2) / 12.0));
    }

    private double eclipse() {
        return ease((t - (CHARGE - 12)) / 12.0);
    }

    /** Start / end of the X blades (blue 0: top to bottom on the S side, red 1: mirrored). */
    private Vec3 bladeStart(int col) {
        return XC.add(S.scale(col == 0 ? -X_HALF : X_HALF)).add(0, X_HALF, 0);
    }

    private Vec3 bladeEnd(int col) {
        return XC.add(S.scale(col == 0 ? X_HALF : -X_HALF)).add(0, -X_HALF, 0);
    }

    private double bladeProgress(int col) {
        return ease((t - (X_T - 3 + col * 3)) / 4.0);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(TwinMoonEclipseEntity e, float yaw, float pt, PoseStack ps0, MultiBufferSource buf0, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        buf = buf0;
        A = e.getPosition(pt);
        D = e.dir();
        D = D.lengthSqr() < 1.0E-6 ? Z : D.normalize();
        S = new Vec3(D.z, 0, -D.x);
        XC = A.add(0, X_H, 0);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        camPos = camera.getPosition();
        p = new Pen(ps0.last().pose(), A, camPos, camera.getLeftVector(), camera.getUpVector());
        M = moon(0).add(moon(1)).scale(0.5);

        alpha(YoneRender.MOON);
        moons();
        alpha(YoneRender.KATANA);
        blade(0);
        alpha(YoneRender.KATANA_RED);
        blade(1);
        add(YoneRender.GLOW);
        glow();
        add(YoneRender.RING);
        rings();
        add(YoneRender.LINE);
        lines();
        add(YoneRender.GLINT);
        glints();
    }

    // ------------------------------------------------------------------ passes

    private void moons() {
        double a = moonAlpha();
        if (a <= 0.004) {
            return;
        }
        double grow = 0.55 + 0.45 * ease(t / 12.0);
        for (int col = 0; col < 2; col++) {
            Vec3 c = moon(col);
            p.billboard(c, MOON_R * grow, 0, col == 0 ? Pen.BLUE : Pen.RED, (float) (0.97 * a));
        }
        double ec = eclipse();
        if (ec > 0.004) {
            // black disc, pulled slightly toward the camera so it covers both moons without z-fighting
            Vec3 toCam = camPos.subtract(M).normalize();
            Vec3 c = M.add(toCam.scale(0.8));
            double sz = MOON_R * grow * 0.93;
            p.billboard(c, sz, 0, new float[]{0.01f, 0.01f, 0.03f}, (float) (ec * a));
        }
    }

    private void blade(int col) {
        double f = bladeProgress(col);
        if (f <= 0.004 || t >= X_T + 16) {
            return;
        }
        Vec3 s = bladeStart(col);
        Vec3 e = bladeEnd(col);
        Vec3 d = e.subtract(s).normalize();
        Vec3 tip = s.add(e.subtract(s).scale(f));
        Vec3 hilt = tip.subtract(d.scale(BLADE));
        float al = (float) (1.0 - c01((t - X_T - 6) / 10.0));
        double w = BLADE * ASPECT;
        p.ribbon(hilt, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
    }

    private void glow() {
        double a = moonAlpha();
        double ec = eclipse();
        double grow = 0.55 + 0.45 * ease(t / 12.0);
        if (a > 0.004) {
            // moon halos
            for (int col = 0; col < 2; col++) {
                Vec3 c = moon(col);
                p.billboard(c, MOON_R * grow * 1.7, 0, col == 0 ? Pen.BLUE : Pen.RED, (float) (0.22 * a));
            }
            // eclipse corona: two offset glows peeking around the black disc
            if (ec > 0.004) {
                double pulse = 1.0 + 0.04 * Math.sin(t * 0.5);
                p.billboard(M.add(S.scale(-0.9)), MOON_R * grow * 1.07 * pulse, 0, Pen.BLUE, (float) (0.85 * ec * a));
                p.billboard(M.add(S.scale(0.9)), MOON_R * grow * 1.07 * pulse, 0, Pen.RED, (float) (0.85 * ec * a));
                p.billboard(M, MOON_R * grow * 2.4, 0, Pen.WHITE, (float) (0.13 * ec * a));
            }
        }
        // ground glow where the funnel lands
        double env = ease(t / 10.0) * (1.0 - c01((t - X_T) / 10.0));
        p.billboard(A.add(0, 0.5, 0), 5.0 * env, 0, Pen.BLUE, (float) (0.20 * env));
        p.billboard(A.add(0, 0.5, 0), 3.0 * env, 0, Pen.RED, (float) (0.20 * env));
        // marks on caught enemies
        if (t < X_T) {
            for (int id : ent.caughtIds()) {
                Entity v = ent.level().getEntity(id);
                if (v == null) {
                    continue;
                }
                Vec3 c = v.getPosition(1.0f).add(0, v.getBbHeight() * 0.5, 0);
                double pulse = 0.8 + 0.2 * Math.sin(t * 0.6 + id);
                p.billboard(c, (0.9 + v.getBbWidth()) * pulse, 0, id % 2 == 0 ? Pen.BLUE : Pen.RED, 0.45f);
            }
        }
        // flash of the X
        double fa = t - X_T;
        if (fa >= -1 && fa < 14) {
            double k = ease((fa + 1) / 4.0);
            float al = (float) (1.0 - c01((fa + 1) / 14.0));
            p.billboard(XC, 6.0 + 20.0 * k, 0, Pen.WHITE, 0.55f * al);
            p.billboard(XC, 4.0 + 12.0 * k, 0, Pen.BLUE, 0.45f * al);
            p.billboard(XC.add(S.scale(2.0)), 3.0 + 8.0 * k, 0, Pen.RED, 0.4f * al);
        }
    }

    private void rings() {
        double env = ease(t / 8.0) * (1.0 - c01((t - X_T) / 8.0));
        double R = TwinMoonEclipseSpell.PULL_RADIUS;
        if (env > 0.004) {
            // runes on the ground, counter rotating
            p.plane(A.add(0, 0.12, 0), X, Z, R * 0.55 * env, t * 0.03, Pen.BLUE, (float) (0.55 * env));
            p.plane(A.add(0, 0.14, 0), X, Z, R * 0.40 * env, -t * 0.05, Pen.RED, (float) (0.55 * env));
            p.plane(A.add(0, 0.16, 0), X, Z, R * 0.22 * env, t * 0.08, Pen.WHITE, (float) (0.5 * env));
            // rings that shrink toward the centre (the pull)
            for (int k = 0; k < 4; k++) {
                double f = ((t * 0.035) + k * 0.25) % 1.0;
                double rr = R * (1.0 - f) + 1.0;
                float al = (float) (Math.sin(f * Math.PI) * 0.7 * env);
                p.plane(A.add(0, 0.2 + f, 0), X, Z, rr, 0, k % 2 == 0 ? Pen.BLUE : Pen.RED, al);
            }
        }
        // vertical halo around the funnel top and the eclipse
        double ec = eclipse() * moonAlpha();
        if (ec > 0.004) {
            Vec3 toCam = camPos.subtract(M).normalize();
            Vec3 ax = toCam.cross(Y);
            ax = ax.lengthSqr() < 1.0E-6 ? X : ax.normalize();
            Vec3 ay = toCam.cross(ax).normalize();
            p.plane(M, ax, ay, MOON_R * 1.5, t * 0.02, Pen.WHITE, (float) (0.45 * ec));
            p.plane(M, ax, ay, MOON_R * 1.2, -t * 0.03, Pen.RED, (float) (0.4 * ec));
        }
        // the X: shock rings
        double fa = t - X_T;
        if (fa >= 0 && fa < 16) {
            double k = ease(fa / 8.0);
            float al = (float) (1.0 - fa / 16.0);
            double r = TwinMoonEclipseSpell.X_RADIUS * 1.2 * k + 1.0;
            p.plane(A.add(0, 0.2, 0), X, Z, r, 0, Pen.BLUE, 0.85f * al);
            p.plane(A.add(0, 0.25, 0), X, Z, r * 0.7, fa * 0.2, Pen.RED, 0.8f * al);
            p.plane(A.add(0, 0.3, 0), X, Z, r * 0.4, 0, Pen.WHITE, 0.7f * al);
            p.plane(XC, S, Y, 2.0 + 14.0 * k, 0, Pen.WHITE, 0.6f * al);
            p.plane(XC, S, Y, 1.0 + 9.0 * k, fa * 0.1, Pen.RED, 0.6f * al);
        }
        double aa = t - AFTER_T;
        if (aa >= 0 && aa < 14) {
            double k = ease(aa / 10.0);
            float al = (float) (1.0 - aa / 14.0);
            p.plane(A.add(0, 0.3, 0), X, Z, 1.0 + TwinMoonEclipseSpell.AFTER_RADIUS * k, 0, Pen.ROSE, 0.7f * al);
            p.plane(A.add(0, 0.35, 0), X, Z, 1.0 + TwinMoonEclipseSpell.AFTER_RADIUS * 0.7 * k, 0, Pen.ICE, 0.6f * al);
        }
    }

    private void lines() {
        double a = moonAlpha();
        double ec = eclipse();
        // light beams from the moons down to the ground while they converge
        if (a > 0.004 && t < X_T) {
            double k = ease(t / 14.0);
            for (int col = 0; col < 2; col++) {
                Vec3 c = moon(col);
                float al = (float) (0.35 * k * a * (0.8 + 0.2 * Math.sin(t * 0.4 + col)));
                line(c.subtract(0, MOON_R * 0.6, 0), A, 0.35, al, col == 0);
            }
            if (ec > 0.004) {
                line(M.subtract(0, MOON_R * 0.5, 0), A, 0.9, (float) (0.7 * ec * a), false);
                line(M.subtract(0, MOON_R * 0.5, 0), A, 0.45, (float) (0.9 * ec * a), true);
            }
        }
        // double-helix funnel
        double env = ease((t - 2) / 10.0) * (1.0 - c01((t - X_T) / 6.0));
        if (env > 0.004) {
            int seg = 36;
            double H = 16.0;
            for (int h = 0; h < 2; h++) {
                Vec3 prev = null;
                for (int i = 0; i <= seg; i++) {
                    double f = i / (double) seg;
                    double r = 1.0 + 9.0 * (1.0 - f) * (1.0 - f);
                    double ang = f * 9.0 - t * 0.28 + h * Math.PI;
                    Vec3 pt = A.add(Math.cos(ang) * r, 0.4 + f * H, Math.sin(ang) * r);
                    if (prev != null) {
                        float al = (float) ((0.35 + 0.5 * f) * env);
                        line(prev, pt, 0.22 + 0.1 * (1 - f), al, h == 0);
                    }
                    prev = pt;
                }
            }
        }
        // pull lines toward the centre
        if (env > 0.004) {
            Random rnd = new Random(7);
            for (int i = 0; i < 18; i++) {
                double a0 = rnd.nextDouble() * Math.PI * 2;
                double ph = rnd.nextDouble();
                double f = (t * 0.045 + ph) % 1.0;
                double r0 = TwinMoonEclipseSpell.PULL_RADIUS * (1.0 - f);
                double r1 = Math.max(0.0, r0 - 2.5);
                double sw = 0.9 * f;
                Vec3 s0 = A.add(Math.cos(a0 + sw) * r0, 0.3 + f * 3.0, Math.sin(a0 + sw) * r0);
                Vec3 s1 = A.add(Math.cos(a0 + sw + 0.15) * r1, 0.3 + f * 3.0 + 0.3, Math.sin(a0 + sw + 0.15) * r1);
                line(s0, s1, 0.12, (float) (Math.sin(f * Math.PI) * 0.7 * env), i % 2 == 0);
            }
        }
        // caught enemies are tethered to the eclipse
        if (t < X_T && a > 0.004) {
            for (int id : ent.caughtIds()) {
                Entity v = ent.level().getEntity(id);
                if (v == null) {
                    continue;
                }
                Vec3 c = v.getPosition(1.0f).add(0, v.getBbHeight() * 0.5, 0);
                line(c, c.add(0, 5.0, 0).add(S.scale(Math.sin(t * 0.3 + id) * 0.6)), 0.12, (float) (0.5 * a), id % 2 == 0);
            }
        }
        // the X: thick cut lines left behind the blades
        for (int col = 0; col < 2; col++) {
            double f = bladeProgress(col);
            if (f <= 0.004) {
                continue;
            }
            Vec3 s = bladeStart(col);
            Vec3 e = bladeEnd(col);
            Vec3 tip = s.add(e.subtract(s).scale(f));
            float al = (float) (1.0 - c01((t - X_T - 3) / 13.0));
            double wid = 1.4 * (1.0 - 0.5 * c01((t - X_T) / 10.0));
            line(s, tip, wid, al, col == 0);
            p.ribbon(s, tip, wid * 0.35, al, 0f, 1f, Pen.WHITE);
        }
        // ground cracks from the centre
        double fa = t - X_T;
        if (fa >= 0 && fa < 22) {
            Random rnd = new Random(42);
            float al = (float) (1.0 - fa / 22.0);
            double k = ease(fa / 6.0);
            for (int i = 0; i < 16; i++) {
                double ang = i / 16.0 * Math.PI * 2 + rnd.nextDouble() * 0.3;
                double len = (4.0 + rnd.nextDouble() * 7.0) * k;
                Vec3 a0 = A.add(0, 0.15, 0).add(Math.cos(ang) * 1.0, 0, Math.sin(ang) * 1.0);
                Vec3 mid = A.add(0, 0.15, 0).add(Math.cos(ang + 0.12) * (1.0 + len * 0.5), 0, Math.sin(ang + 0.12) * (1.0 + len * 0.5));
                Vec3 a1 = A.add(0, 0.15, 0).add(Math.cos(ang) * (1.0 + len), 0, Math.sin(ang) * (1.0 + len));
                line(a0, mid, 0.22, al, i % 2 == 0);
                line(mid, a1, 0.14, al, i % 2 == 0);
            }
        }
    }

    private void glints() {
        double a = moonAlpha();
        // sparkles near the corona
        if (a > 0.004 && eclipse() > 0.2) {
            Random rnd = new Random(3);
            for (int i = 0; i < 14; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2 + t * 0.02;
                double rr = MOON_R * (1.0 + 0.3 * rnd.nextDouble());
                Vec3 toCam = camPos.subtract(M).normalize();
                Vec3 ax = toCam.cross(Y);
                ax = ax.lengthSqr() < 1.0E-6 ? X : ax.normalize();
                Vec3 ay = toCam.cross(ax).normalize();
                Vec3 c = M.add(ax.scale(Math.cos(ang) * rr)).add(ay.scale(Math.sin(ang) * rr));
                double tw = 0.5 + 0.5 * Math.sin(t * 0.4 + i * 1.7);
                p.billboard(c, 0.5 + 0.7 * tw, t * 0.05 + i, i % 2 == 0 ? Pen.ICE : Pen.ROSE, (float) (0.8 * tw * a * eclipse()));
            }
        }
        // sparks at the moment of the cut
        double fa = t - X_T;
        if (fa >= -1 && fa < 18) {
            Random rnd = new Random(99);
            float al = (float) (1.0 - c01((fa + 1) / 18.0));
            for (int i = 0; i < 36; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double sp = 0.3 + rnd.nextDouble() * 0.8;
                double up = rnd.nextDouble() * 0.5;
                double age = Math.max(0, fa + 1);
                Vec3 c = XC.add(Math.cos(ang) * sp * age * 1.6, up * age * 1.4 - 0.02 * age * age, Math.sin(ang) * sp * age * 1.6)
                        .add(S.scale((rnd.nextDouble() - 0.5) * 6.0));
                p.billboard(c, 0.5 + rnd.nextDouble() * 0.5, age * 0.3, i % 2 == 0 ? Pen.WHITE : (i % 4 == 1 ? Pen.ICE : Pen.ROSE), 0.9f * al);
            }
            // crossing point flare
            double k = ease((fa + 1) / 3.0);
            p.billboard(XC, 1.5 + 3.5 * k, fa * 0.2, Pen.WHITE, 0.9f * al);
        }
    }
}
