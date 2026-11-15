package com.example.jjkspells.yone.client;

import com.example.jjkspells.yone.entity.HornedVolleyEntity;
import com.example.jjkspells.yone.spell.HornedVolleySpell;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws "Horned Volley": a big Oni mask floats over Yone with a red glow, and twelve spectral swords (blue Mortal Steel, red Spirit Blade) slide out of its
 * mouth into two curving horns. One by one they swing toward a target and fly in a curved arc with a streak behind them, then stay buried in the enemy,
 * pulsing; at the end every buried sword explodes in crossed cuts, a ring and sparks while the mask roars and fades.
 */
public class HornedVolleyRenderer extends EntityRenderer<HornedVolleyEntity> {
    private static final int FIRE_START = HornedVolleyEntity.FIRE_START;
    private static final int FLIGHT = HornedVolleyEntity.FLIGHT;
    private static final int FIN = HornedVolleyEntity.FIN;
    private static final int END = HornedVolleyEntity.END;
    private static final int SWORDS = HornedVolleySpell.SWORDS;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final double LEN = 3.6;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 D;
    private Vec3 S;
    private Vec3 M;
    private MultiBufferSource buf;
    private HornedVolleyEntity ent;
    private double maskA;

    public HornedVolleyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(HornedVolleyEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(HornedVolleyEntity e) {
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

    private Vec3 camRight() {
        Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f l = c.getLeftVector();
        return new Vec3(-l.x(), -l.y(), -l.z());
    }

    // ------------------------------------------------------------------ sword model

    private static int side(int i) {
        return i % 2 == 0 ? 1 : -1;
    }

    /** Centre of sword i resting in its horn slot. */
    private Vec3 slotCentre(int i) {
        int j = i / 2;
        double lat = side(i) * (0.9 + 0.55 * j);
        double y = 3.4 + 0.65 * j - 0.04 * j * j;
        return A.add(0, y, 0).subtract(D.scale(0.4)).add(S.scale(lat)).add(tipDir(i).scale(1.7));
    }

    private Vec3 tipDir(int i) {
        return S.scale(side(i) * 0.55).add(0, 0.85, 0).subtract(D.scale(0.1)).normalize();
    }

    private int[] launchOf(int i) {
        for (int[] l : ent.launches()) {
            if (l[0] == i) {
                return l;
            }
        }
        return null;
    }

    private Vec3 targetPoint(int[] l) {
        if (l[2] >= 0) {
            Entity e = ent.level().getEntity(l[2]);
            if (e == null) {
                return null;
            }
            return e.getPosition(1.0f).add(0, e.getBbHeight() * 0.5, 0);
        }
        return new Vec3(l[3] / 10.0, l[4] / 10.0, l[5] / 10.0);
    }

    private Vec3 control(Vec3 p0, Vec3 p1, int i) {
        return p0.add(p1).scale(0.5).add(0, 3.5, 0).add(S.scale(side(i) * 2.5));
    }

    private static Vec3 bez(Vec3 a, Vec3 c, Vec3 b, double f) {
        double q = 1 - f;
        return a.scale(q * q).add(c.scale(2 * q * f)).add(b.scale(f * f));
    }

    /** Draws sword i if it is visible; fills nothing else. Returns hilt/tip via the array. */
    private Vec3[] swordPose(int i) {
        double appear = 2 + i * 1.2;
        if (t < appear) {
            return null;
        }
        int[] l = launchOf(i);
        Vec3 slot = slotCentre(i);
        Vec3 mouth = M.add(0, -0.5, 0);
        double ap = ease((t - appear) / 5.0);
        Vec3 dir = tipDir(i);
        Vec3 c = mouth.add(slot.subtract(mouth).scale(ap));
        if (l == null || t < l[1] - 2) {
            double wob = 0.05 * Math.sin(t * 0.1 + i);
            dir = dir.add(D.scale(wob)).normalize();
            double bob = 0.06 * Math.sin(t * 0.12 + i);
            c = c.add(0, bob, 0);
            return new Vec3[]{c.subtract(dir.scale(LEN * 0.5)), c.add(dir.scale(LEN * 0.5))};
        }
        Vec3 p1 = targetPoint(l);
        if (p1 == null) {
            return null;
        }
        double fl = (t - l[1]) / (double) FLIGHT;
        if (fl < 0) {
            // swinging toward the target during the two ticks before launch
            Vec3 aim = p1.subtract(slot).normalize();
            double b = c01((t - (l[1] - 2)) / 2.0);
            dir = dir.scale(1 - b).add(aim.scale(b)).normalize();
            return new Vec3[]{slot.subtract(dir.scale(LEN * 0.5)), slot.add(dir.scale(LEN * 0.5))};
        }
        Vec3 ctl = control(slot, p1, i);
        if (fl < 1.0) {
            double f = Math.pow(fl, 1.5);
            Vec3 cur = bez(slot, ctl, p1, f);
            Vec3 ahead = bez(slot, ctl, p1, Math.min(1.0, f + 0.04));
            Vec3 behind = bez(slot, ctl, p1, Math.max(0.0, f - 0.04));
            Vec3 d = ahead.subtract(behind);
            d = d.lengthSqr() < 1.0E-6 ? dir : d.normalize();
            return new Vec3[]{cur.subtract(d.scale(LEN * 0.5)), cur.add(d.scale(LEN * 0.5))};
        }
        if (t >= FIN + 4) {
            return null;
        }
        Vec3 d = p1.subtract(ctl);
        d = d.lengthSqr() < 1.0E-6 ? dir : d.normalize();
        Vec3 c2 = p1.subtract(d.scale(1.2));
        return new Vec3[]{c2.subtract(d.scale(LEN * 0.5)), c2.add(d.scale(LEN * 0.5))};
    }

    @Override
    public void render(HornedVolleyEntity e, float yaw, float pt, PoseStack ps0, MultiBufferSource buf0, int light) {
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
        M = A.add(0, 3.4 + 0.15 * Math.sin(t * 0.1), 0).add(D.scale(0.3));
        maskA = ease(t / 6.0) * (1.0 - c01((t - FIN) / 10.0));
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps0.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        alpha(YoneRender.MASK);
        p.billboard(M, 1.25 * (0.6 + 0.4 * ease(t / 6.0)), 0.04 * Math.sin(t * 0.07), Pen.WHITE, (float) maskA);
        for (int col = 0; col < 2; col++) {
            alpha(col == 0 ? YoneRender.KATANA : YoneRender.KATANA_RED);
            swords(col);
        }
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

    private void swords(int col) {
        for (int i = col; i < SWORDS; i += 2) {
            Vec3[] hp = swordPose(i);
            if (hp == null) {
                continue;
            }
            double w = LEN * ASPECT;
            float al = 0.95f;
            int[] l = launchOf(i);
            if (l != null && t >= FIN) {
                al = (float) (1.0 - c01((t - FIN) / 4.0));
            }
            p.ribbon(hp[0], hp[1], w, w, al, al, 0f, 1f, Pen.WHITE);
        }
    }

    private void glow() {
        // red aura behind the mask
        p.billboard(M, 2.4 + 0.3 * Math.sin(t * 0.2), 0, Pen.CRIMSON, (float) (0.35 * maskA));
        p.billboard(M, 1.4, 0, Pen.RED, (float) (0.25 * maskA));
        // roar flash at the mouth with each launch
        for (int[] l : ent.launches()) {
            double age = t - l[1];
            if (age < 0 || age > 5) {
                continue;
            }
            p.billboard(M.add(0, -0.5, 0), 0.5 + 0.8 * ease(age / 3.0), age, l[0] % 2 == 0 ? Pen.ICE : Pen.ROSE, (float) (0.9 * (1.0 - age / 5.0)));
        }
        double fa = t - FIN;
        if (fa >= 0 && fa < 12) {
            p.billboard(M.add(0, -0.5, 0), 1.0 + 2.5 * ease(fa / 5.0), 0, Pen.RED, (float) (0.8 * (1.0 - fa / 12.0)));
        }
    }

    private void rings() {
        double env = ease(t / 8.0) * (1.0 - c01((t - FIN - 4) / 14.0));
        p.plane(A.add(0, 0.12, 0), X, Z, 3.6 * env, t * 0.04, Pen.RED, (float) (0.5 * env));
        p.plane(A.add(0, 0.14, 0), X, Z, 2.4 * env, -t * 0.06, Pen.BLUE, (float) (0.5 * env));
        for (int[] l : ent.launches()) {
            double age = t - l[1];
            if (age >= 0 && age < 6) {
                double k = ease(age / 4.0);
                p.plane(M.add(0, -0.5, 0).add(D.scale(0.3)), S, Y, 0.4 + 1.8 * k, 0, l[0] % 2 == 0 ? Pen.BLUE : Pen.RED, (float) (0.8 * (1.0 - age / 6.0)));
            }
            // landing ring
            double la = t - (l[1] + FLIGHT);
            if (la >= 0 && la < 8) {
                Vec3 p1 = targetPoint(l);
                if (p1 != null) {
                    double k = ease(la / 5.0);
                    p.plane(p1, S, Y, 0.4 + 1.6 * k, 0, l[0] % 2 == 0 ? Pen.ICE : Pen.ROSE, (float) (0.8 * (1.0 - la / 8.0)));
                }
            }
        }
        double fa = t - FIN;
        if (fa >= 0 && fa < 16) {
            double k = ease(fa / 8.0);
            float al = (float) (1.0 - fa / 16.0);
            Set<Integer> done = new HashSet<>();
            for (int[] l : ent.launches()) {
                if (l[2] < 0 || !done.add(l[2])) {
                    continue;
                }
                Vec3 p1 = targetPoint(l);
                if (p1 != null) {
                    p.plane(p1, X, Z, 0.8 + 4.5 * k, 0, Pen.RED, al * 0.8f);
                    p.plane(p1, X, Z, 0.5 + 2.5 * k, fa * 0.3, Pen.WHITE, al * 0.6f);
                }
            }
            p.plane(A.add(0, 0.3, 0), X, Z, 1.0 + 7.0 * k, 0, Pen.BLUE, al * 0.5f);
        }
    }

    private void lines() {
        double env = ease(t / 8.0) * (1.0 - c01((t - FIN) / 10.0));
        // summoning swirls from the mouth into the horns
        if (t < FIRE_START + 6) {
            for (int i = 0; i < SWORDS; i++) {
                double appear = 2 + i * 1.2;
                double age = t - appear;
                if (age < 0 || age > 8) {
                    continue;
                }
                Vec3 mouth = M.add(0, -0.5, 0);
                Vec3 slot = slotCentre(i);
                Vec3 prev = mouth;
                for (int s = 1; s <= 8; s++) {
                    double f = s / 8.0;
                    Vec3 cur = mouth.add(slot.subtract(mouth).scale(f)).add(0, 0.4 * Math.sin(f * Math.PI) * side(i), 0).add(S.scale(0.3 * Math.sin(f * 6.0 + t * 0.4)));
                    line(prev, cur, 0.04, (float) (0.6 * (1.0 - age / 8.0) * f), i % 2);
                    prev = cur;
                }
            }
        }
        // flight streaks and impact cuts
        for (int[] l : ent.launches()) {
            int i = l[0];
            double fl = (t - l[1]) / (double) FLIGHT;
            Vec3 p1 = targetPoint(l);
            if (p1 == null) {
                continue;
            }
            Vec3 slot = slotCentre(i);
            Vec3 ctl = control(slot, p1, i);
            if (fl >= 0 && fl < 1.0) {
                double f = Math.pow(fl, 1.5);
                Vec3 prev = null;
                int n = 8;
                for (int s = 0; s <= n; s++) {
                    double ff = Math.max(0.0, f - 0.22 * (1.0 - (double) s / n));
                    Vec3 cur = bez(slot, ctl, p1, ff);
                    if (prev != null) {
                        line(prev, cur, 0.1 * s / n, (float) (0.9 * s / n), i % 2);
                    }
                    prev = cur;
                }
            }
            double la = t - (l[1] + FLIGHT);
            if (la >= 0 && la < 8) {
                double k = ease(la / 2.0);
                float al = (float) (1.0 - la / 8.0);
                for (int s = 0; s < 2; s++) {
                    double an = s == 0 ? 0.7 : -0.7;
                    Vec3 v = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0).scale(1.8 * k);
                    line(p1.subtract(v), p1.add(v), 0.07, al, i % 2);
                }
            }
            // buried swords pulse harder as the blast nears
            if (la >= 8 && t < FIN) {
                double charge = c01((t - (FIN - 14)) / 14.0);
                float pulse = (float) ((0.3 + 0.5 * charge) * (0.6 + 0.4 * Math.sin(t * (0.4 + charge))));
                for (int s = 0; s < 2; s++) {
                    double an = s == 0 ? 0.7 : -0.7;
                    Vec3 v = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0).scale(0.6 + 0.8 * charge);
                    line(p1.subtract(v), p1.add(v), 0.04, pulse, i % 2);
                }
            }
        }
        // the final blast
        double fa = t - FIN;
        if (fa >= 0 && fa < 14) {
            double k = ease(fa / 3.0);
            float al = (float) (1.0 - fa / 14.0);
            Set<Integer> done = new HashSet<>();
            for (int[] l : ent.launches()) {
                if (l[2] < 0 || !done.add(l[2])) {
                    continue;
                }
                Vec3 p1 = targetPoint(l);
                if (p1 == null) {
                    continue;
                }
                for (int s = 0; s < 3; s++) {
                    double an = 0.5 + s * 1.05 + l[2] * 0.4;
                    Vec3 v = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0).scale(4.2 * k);
                    line(p1.subtract(v), p1.add(v), 0.11, al, s % 2);
                }
                for (int s = 0; s < 10; s++) {
                    double an = s * Math.PI / 5 + l[2];
                    Vec3 r = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0);
                    line(p1.add(r.scale(1.2 + 3.0 * k)), p1.add(r.scale(2.0 + 6.0 * k)), 0.05, al * 0.8f, s % 2);
                }
            }
        }
    }

    private void glints() {
        double env = ease(t / 8.0) * (1.0 - c01((t - FIN) / 10.0));
        for (int i = 0; i < 14; i++) {
            double ang = t * 0.06 + i * 0.9;
            double rad = 1.2 + 0.4 * (i % 3);
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 0.5 + i * 2.1));
            p.billboard(M.add(Math.cos(ang) * rad, -0.6 + 0.3 * Math.sin(ang * 2), Math.sin(ang) * rad), 0.14, t * 0.1 + i, Pen.WHITE, (float) (0.6 * env * tw));
        }
        for (int[] l : ent.launches()) {
            double fl = (t - l[1]) / (double) FLIGHT;
            Vec3 p1 = targetPoint(l);
            if (p1 == null) {
                continue;
            }
            if (fl >= 0 && fl < 1.0) {
                Vec3 slot = slotCentre(l[0]);
                Vec3 cur = bez(slot, control(slot, p1, l[0]), p1, Math.pow(fl, 1.5));
                p.billboard(cur.add(D.scale(0.0)), 0.5, t * 0.3, l[0] % 2 == 0 ? Pen.ICE : Pen.ROSE, 0.9f);
            }
            double la = t - (l[1] + FLIGHT);
            if (la >= 0 && la < 6) {
                p.billboard(p1, 0.6 + 0.8 * ease(la / 3.0), la * 0.2, Pen.WHITE, (float) (1.0 - la / 6.0));
            }
        }
        double fa = t - FIN;
        if (fa >= 0 && fa < 8) {
            Set<Integer> done = new HashSet<>();
            for (int[] l : ent.launches()) {
                if (l[2] < 0 || !done.add(l[2])) {
                    continue;
                }
                Vec3 p1 = targetPoint(l);
                if (p1 != null) {
                    p.billboard(p1, 1.4 + 2.0 * ease(fa / 3.0), fa * 0.2, Pen.ROSE, (float) (1.0 - fa / 8.0));
                }
            }
        }
    }
}
