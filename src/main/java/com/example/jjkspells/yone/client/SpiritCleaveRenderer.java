package com.example.jjkspells.yone.client;

import com.example.jjkspells.yone.entity.SpiritCleaveEntity;
import com.example.jjkspells.yone.spell.SpiritCleaveSpell;
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

/**
 * Draws "Spirit Cleave": both blades cross in front of Yone, tear apart (blue to the left, red to the right) leaving sweeping trail arcs and a fan of upright
 * crescents racing across the cone, then close again in a scissor X with a shock ring and streaks that drag toward the centre line. Afterwards the two
 * blades circle him as a veil: orbiting blade sprites with trailing arcs, spinning rings and a translucent striped wall in blue and red.
 */
public class SpiritCleaveRenderer extends EntityRenderer<SpiritCleaveEntity> {
    private static final int T1 = SpiritCleaveEntity.T1;
    private static final int T2 = SpiritCleaveEntity.T2;
    private static final int END = SpiritCleaveEntity.END;
    private static final int VEIL_FADE = SpiritCleaveEntity.VEIL_FADE;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final double R1 = SpiritCleaveSpell.RADIUS;
    private static final double R2 = SpiritCleaveSpell.CLOSE_RADIUS;
    private static final double H1 = Math.toRadians(SpiritCleaveSpell.HALF_ANGLE_DEG);
    private static final double H2 = Math.toRadians(SpiritCleaveSpell.CLOSE_HALF_ANGLE_DEG);
    private static final int FAN = 7;
    private static final int SEG = 20;
    private static final float DU = 0.25f;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private final PlayerGhost ghost;
    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 C;
    private Vec3 D;
    private Vec3 S;
    private PoseStack ps;
    private MultiBufferSource buf;
    private SpiritCleaveEntity ent;
    private Entity own;
    private double veil;

    public SpiritCleaveRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
        this.ghost = new PlayerGhost(ctx);
    }

    @Override
    public boolean shouldRender(SpiritCleaveEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritCleaveEntity e) {
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

    private static float[] body(int colour) {
        return colour == 0 ? Pen.BLUE : Pen.RED;
    }

    private static float[] edge(int colour) {
        return colour == 0 ? Pen.ICE : Pen.ROSE;
    }

    private void line(Vec3 a, Vec3 b, double w, float al, int colour) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.4, al * 0.45f, 0f, 1f, body(colour));
        p.ribbon(a, b, w, al, 0f, 1f, edge(colour));
    }

    /** Horizontal direction rotated toward the left (positive) or right (negative) of the facing. */
    private Vec3 dirAt(double ang) {
        return D.scale(Math.cos(ang)).add(S.scale(Math.sin(ang)));
    }

    private Vec3 camRight() {
        Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f l = c.getLeftVector();
        return new Vec3(-l.x(), -l.y(), -l.z());
    }

    @Override
    public void render(SpiritCleaveEntity e, float yaw, float pt, PoseStack ps0, MultiBufferSource buf0, int light) {
        t = e.tickCount - 1 + pt;
        int end = e.veilEnd();
        if (t < 0 || t > END || (end >= 0 && t > end + VEIL_FADE)) {
            return;
        }
        ent = e;
        ps = ps0;
        buf = buf0;
        own = e.owner();
        A = e.getPosition(pt);
        C = own != null ? own.getPosition(pt) : A;
        D = e.dir();
        D = D.lengthSqr() < 1.0E-6 ? Z : D.normalize();
        S = new Vec3(D.z, 0, -D.x);
        veil = ease((t - T1) / 6.0) * (end >= 0 ? 1.0 - c01((t - end) / VEIL_FADE) : 1.0);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        ghosts();
        for (int col = 0; col < 2; col++) {
            alpha(col == 0 ? YoneRender.KATANA : YoneRender.KATANA_RED);
            blades(col);
        }
        add(YoneRender.SLASH);
        fan();
        add(YoneRender.WIND);
        wall();
        add(YoneRender.MIST);
        mist();
        add(YoneRender.RING);
        rings();
        add(YoneRender.LINE);
        lines();
        add(YoneRender.GLINT);
        glints();
    }

    // ------------------------------------------------------------------ passes

    private void ghosts() {
        if (own == null) {
            return;
        }
        boolean slim = PlayerGhost.slimOf(own);
        double a1 = t - T1;
        if (a1 >= 0 && a1 < 9) {
            float al = (float) (0.55 * (1.0 - a1 / 9.0));
            ghost.silhouette(ps, buf, slim, C.subtract(A), PlayerGhost.yawOf(dirAt(0.55)), PlayerGhost.Pose.slash(), Pen.BLUE, al);
            ghost.silhouette(ps, buf, slim, C.subtract(A), PlayerGhost.yawOf(dirAt(-0.55)), PlayerGhost.Pose.slash(), Pen.RED, al);
        }
        double a2 = t - T2;
        if (a2 >= 0 && a2 < 9) {
            float al = (float) (0.55 * (1.0 - a2 / 9.0));
            ghost.silhouette(ps, buf, slim, C.add(D.scale(1.0)).subtract(A), PlayerGhost.yawOf(D), PlayerGhost.Pose.lunge(1f), Pen.ICE, al);
        }
    }

    private void blades(int col) {
        double sgn = col == 0 ? 1 : -1;
        Vec3 base = C.add(0, 1.3, 0);
        // wind-up: crossed in front
        if (t < T1 + 1) {
            double k = ease(t / T1);
            Vec3 h = base.add(D.scale(0.5)).add(S.scale(-0.9 * sgn)).add(0, -0.6, 0);
            Vec3 tip = h.add(D.scale(2.2)).add(S.scale(1.8 * sgn)).add(0, 2.0, 0);
            double w = h.distanceTo(tip) * ASPECT;
            float al = (float) k;
            p.ribbon(h, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
        }
        // sweep outward
        double a1 = t - T1;
        if (a1 >= 0 && a1 < 9) {
            double ang = sgn * H1 * ease(a1 / 4.0);
            Vec3 d = dirAt(ang);
            Vec3 h = base.add(d.scale(0.7));
            Vec3 tip = base.add(d.scale(0.7 + 5.2));
            double w = h.distanceTo(tip) * ASPECT;
            float al = (float) (1.0 - a1 / 9.0);
            p.ribbon(h, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
        }
        // scissor closing
        double a2 = t - T2;
        if (a2 >= 0 && a2 < 9) {
            double ang = sgn * H2 * (1.0 - ease(a2 / 4.0));
            Vec3 d = dirAt(ang);
            Vec3 h = base.add(d.scale(0.7));
            Vec3 tip = base.add(d.scale(0.7 + 5.2));
            double w = h.distanceTo(tip) * ASPECT;
            float al = (float) (1.0 - a2 / 9.0);
            p.ribbon(h, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
        }
        // veil: blades circling
        if (veil > 0.02) {
            double ang = t * 0.35 + (col == 0 ? 0 : Math.PI);
            double y = 1.0 + 0.5 * Math.sin(t * 0.12 + col * Math.PI);
            Vec3 pos = C.add(Math.cos(ang) * 1.9, y, Math.sin(ang) * 1.9);
            Vec3 tan = new Vec3(-Math.sin(ang), 0, Math.cos(ang));
            Vec3 tip = pos.add(tan.scale(2.6));
            double w = pos.distanceTo(tip) * ASPECT;
            float al = (float) (0.95 * veil);
            p.ribbon(pos, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
        }
    }

    private void fan() {
        double a1 = t - T1;
        if (a1 < 0 || a1 > 9) {
            return;
        }
        double k = ease(a1 / 5.0);
        float al = (float) (0.9 * (1.0 - a1 / 9.0));
        for (int i = 0; i < FAN; i++) {
            double th = -H1 + 2 * H1 * i / (FAN - 1);
            int col = th > 0.05 ? 0 : (th < -0.05 ? 1 : 0);
            Vec3 d = dirAt(th);
            Vec3 s = new Vec3(d.z, 0, -d.x);
            double roll = Math.toRadians(col == 0 ? 68 : -68);
            Vec3 u = s.scale(Math.cos(roll)).add(0, Math.sin(roll), 0).normalize();
            double dist = 1.5 + (R1 - 2.0) * k;
            Vec3 cen = C.add(0, 1.3, 0).add(d.scale(dist));
            double a = 2.0 * (1.0 - 0.25 * Math.abs(th) / H1);
            p.arc(cen, u, d, a, a * 0.5, edge(col), al);
            p.arc(cen.subtract(d.scale(1.8)), u, d, a * 0.85, a * 0.42, body(col), al * 0.5f);
        }
    }

    private void wall() {
        if (veil <= 0.02) {
            return;
        }
        double h = 2.8;
        double r = 1.95;
        for (int i = 0; i < SEG; i++) {
            double a0 = t * 0.06 + Math.PI * 2 * i / SEG;
            double a1 = t * 0.06 + Math.PI * 2 * (i + 1) / SEG;
            int col = (i / 5) % 2;
            double fr = ((t * 0.04 + i * 0.37) % 1.0 + 1.0) % 1.0;
            float u0 = (float) (fr * (1 - DU));
            float u1 = u0 + DU;
            for (int j = 0; j < 3; j++) {
                double f0 = j / 3.0;
                double f1 = (j + 1) / 3.0;
                float al = (float) (0.2 * veil * (1.0 - 0.4 * f0));
                Vec3 p00 = C.add(Math.cos(a0) * r, h * f0, Math.sin(a0) * r);
                Vec3 p10 = C.add(Math.cos(a1) * r, h * f0, Math.sin(a1) * r);
                Vec3 p11 = C.add(Math.cos(a1) * r, h * f1, Math.sin(a1) * r);
                Vec3 p01 = C.add(Math.cos(a0) * r, h * f1, Math.sin(a0) * r);
                p.quad(p00, p10, p11, p01, u0, (float) f0, u1, (float) f1, body(col), al);
            }
        }
    }

    private void mist() {
        double a1 = t - T1;
        if (a1 >= 0 && a1 < 12) {
            p.billboard(C.add(D.scale(2.0)).add(0, 0.8, 0), 2.0 + 3.0 * ease(a1 / 6.0), a1 * 0.1, Pen.PALE, (float) (0.25 * (1.0 - a1 / 12.0)));
        }
        double a2 = t - T2;
        if (a2 >= 0 && a2 < 12) {
            p.billboard(C.add(D.scale(5.0)).add(0, 1.2, 0), 2.0 + 3.0 * ease(a2 / 6.0), -a2 * 0.1, Pen.PALE, (float) (0.25 * (1.0 - a2 / 12.0)));
        }
    }

    private void rings() {
        double a1 = t - T1;
        if (a1 >= 0 && a1 < 12) {
            double k = ease(a1 / 8.0);
            float al = (float) (1.0 - a1 / 12.0);
            p.plane(C.add(0, 0.15, 0), X, Z, 1.0 + (R1 + 1.0) * k, 0, Pen.BLUE, al * 0.45f);
        }
        double a2 = t - T2;
        if (a2 >= 0 && a2 < 12) {
            double k = ease(a2 / 6.0);
            float al = (float) (1.0 - a2 / 12.0);
            Vec3 c = C.add(D.scale(6.0)).add(0, 1.3, 0);
            p.plane(c, S, Y, 0.6 + 4.0 * k, 0, Pen.RED, al * 0.8f);
            p.plane(c, S, Y, 0.4 + 2.5 * k, a2 * 0.3, Pen.WHITE, al * 0.6f);
            p.plane(C.add(0, 0.18, 0), X, Z, 1.0 + (R2 + 1.0) * k, 0, Pen.RED, al * 0.45f);
        }
        if (veil > 0.02) {
            for (int i = 0; i < 3; i++) {
                double y = 0.15 + i * 1.2;
                p.plane(C.add(0, y, 0), X, Z, 2.1 + 0.1 * Math.sin(t * 0.2 + i), t * 0.08 * (i % 2 == 0 ? 1 : -1), i % 2 == 0 ? Pen.BLUE : Pen.RED, (float) (0.4 * veil));
            }
        }
    }

    private void lines() {
        Vec3 base = C.add(0, 1.3, 0);
        // cone outline and sweep trails
        double a1 = t - T1;
        if (a1 >= 0 && a1 < 10) {
            double k = ease(a1 / 4.0);
            float al = (float) (1.0 - a1 / 10.0);
            for (int col = 0; col < 2; col++) {
                double sgn = col == 0 ? 1 : -1;
                for (int r = 0; r < 3; r++) {
                    double rad = R1 * (0.45 + 0.27 * r);
                    Vec3 prev = null;
                    int n = 14;
                    for (int i = 0; i <= n; i++) {
                        double ang = sgn * H1 * k * i / n;
                        Vec3 cur = base.add(dirAt(ang).scale(rad)).add(0, 0.25 * r, 0);
                        if (prev != null) {
                            line(prev, cur, 0.1 - 0.02 * r, al * (float) (0.4 + 0.6 * i / n), col);
                        }
                        prev = cur;
                    }
                }
                line(base, base.add(dirAt(sgn * H1 * k).scale(R1)), 0.06, al * 0.7f, col);
            }
        }
        // scissor closing: trails and X
        double a2 = t - T2;
        if (a2 >= 0 && a2 < 10) {
            double k = ease(a2 / 4.0);
            float al = (float) (1.0 - a2 / 10.0);
            for (int col = 0; col < 2; col++) {
                double sgn = col == 0 ? 1 : -1;
                for (int r = 0; r < 2; r++) {
                    double rad = R2 * (0.55 + 0.4 * r);
                    Vec3 prev = null;
                    int n = 12;
                    for (int i = 0; i <= n; i++) {
                        double ang = sgn * H2 * (1.0 - k * i / n);
                        Vec3 cur = base.add(dirAt(ang).scale(rad));
                        if (prev != null) {
                            line(prev, cur, 0.1, al * (float) (0.4 + 0.6 * i / n), col);
                        }
                        prev = cur;
                    }
                }
            }
            double kx = ease(a2 / 3.0);
            Vec3 c = base.add(D.scale(6.0));
            for (int i = 0; i < 2; i++) {
                double an = i == 0 ? 0.7 : -0.7;
                Vec3 v = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0).scale(3.4 * kx);
                line(c.subtract(v), c.add(v), 0.12, al, i);
            }
            for (int i = 0; i < 8; i++) {
                double side = (i % 2 == 0 ? 1 : -1);
                double dist = 3.0 + i * 0.8;
                Vec3 from = base.add(D.scale(dist)).add(S.scale(side * dist * 0.7)).add(0, 0.4 * (i % 3 - 1), 0);
                Vec3 to = base.add(D.scale(dist + 1.0)).add(0, 0.4 * (i % 3 - 1), 0);
                line(from, from.add(to.subtract(from).scale(0.6 + 0.4 * kx)), 0.05, al * 0.7f, i % 2);
            }
        }
        // hits
        for (int[] h : ent.hits()) {
            double age = t - h[3];
            if (age < 0 || age > 10) {
                continue;
            }
            Vec3 m = new Vec3(h[0] / 10.0, h[1] / 10.0, h[2] / 10.0);
            double k = ease(age / 3.0);
            float al = (float) (1.0 - age / 10.0);
            for (int i = 0; i < 2; i++) {
                double an = i == 0 ? 0.7 : -0.7;
                Vec3 v = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0).scale(2.0 * k);
                line(m.subtract(v), m.add(v), 0.08, al, h[4] == 0 ? 0 : 1);
            }
        }
        // veil trails behind the orbiting blades
        if (veil > 0.02) {
            for (int col = 0; col < 2; col++) {
                double ang = t * 0.35 + (col == 0 ? 0 : Math.PI);
                Vec3 prev = null;
                int n = 14;
                for (int i = 0; i <= n; i++) {
                    double aa = ang - 1.8 * (1.0 - (double) i / n);
                    double y = 1.0 + 0.5 * Math.sin(t * 0.12 + col * Math.PI - (1.0 - (double) i / n) * 0.8);
                    Vec3 cur = C.add(Math.cos(aa) * 1.9, y, Math.sin(aa) * 1.9);
                    if (prev != null) {
                        line(prev, cur, 0.08, (float) (0.8 * veil * i / n), col);
                    }
                    prev = cur;
                }
            }
        }
    }

    private void glints() {
        double a1 = t - T1;
        if (a1 >= 0 && a1 < 8) {
            p.billboard(C.add(0, 1.3, 0).add(D.scale(1.0)), 1.0 + 1.4 * ease(a1 / 3.0), a1 * 0.2, Pen.WHITE, (float) (1.0 - a1 / 8.0));
        }
        double a2 = t - T2;
        if (a2 >= 0 && a2 < 8) {
            p.billboard(C.add(0, 1.3, 0).add(D.scale(6.0)), 1.2 + 1.6 * ease(a2 / 3.0), a2 * 0.2, Pen.WHITE, (float) (1.0 - a2 / 8.0));
        }
        if (t < T1) {
            p.billboard(C.add(0, 1.6, 0).add(D.scale(1.4)), 0.5 + 0.3 * Math.sin(t), t * 0.2, Pen.WHITE, (float) (0.8 * ease(t / T1)));
        }
        if (veil > 0.02) {
            for (int i = 0; i < 8; i++) {
                double ang = t * 0.1 + i * Math.PI / 4;
                double y = (i * 0.37 + t * 0.02) % 1.0 * 2.6;
                float tw = (float) (0.5 + 0.5 * Math.sin(t * 0.5 + i * 2.1));
                p.billboard(C.add(Math.cos(ang) * 2.0, y, Math.sin(ang) * 2.0), 0.15, t * 0.1 + i, Pen.WHITE, (float) (0.6 * veil * tw));
            }
        }
    }
}
