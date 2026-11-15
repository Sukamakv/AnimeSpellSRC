package com.example.jjkspells.yone.client;

import com.example.jjkspells.yone.entity.FateSealedEntity;
import com.example.jjkspells.yone.spell.FateSealedSpell;
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
 * Draws "Final Sunder": a line is drawn across the ground (blue edge to the left, red to the right) with a thin seam at chest height; Yone's dash leaves a wind
 * streak, speed lines, a row of blue and red silhouette afterimages stepping out along it and vertical rings across the path. Sealed enemies hang above the
 * line with red marks while the seam winds into a blue-and-red double helix; on the sunder a huge sword plunges onto the end of the line, the seam splits into
 * a plane of light that slides apart into blue and red halves, sweeping lines rise along it, and every sealed enemy is ripped in crossed cuts.
 */
public class FateSealedRenderer extends EntityRenderer<FateSealedEntity> {
    private static final int DASH = FateSealedEntity.DASH;
    private static final int SUNDER = FateSealedEntity.SUNDER;
    private static final int END = FateSealedEntity.END;
    private static final double HW = FateSealedSpell.HALF_WIDTH;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final int IMAGES = 8;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private final PlayerGhost ghost;
    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 D;
    private Vec3 S;
    private Vec3 E;
    private double L;
    private PoseStack ps;
    private MultiBufferSource buf;
    private FateSealedEntity ent;
    private Entity own;

    public FateSealedRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
        this.ghost = new PlayerGhost(ctx);
    }

    @Override
    public boolean shouldRender(FateSealedEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(FateSealedEntity e) {
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

    private Vec3 at(double f, double y, double lat) {
        return A.add(D.scale(f)).add(S.scale(lat)).add(0, y, 0);
    }

    @Override
    public void render(FateSealedEntity e, float yaw, float pt, PoseStack ps0, MultiBufferSource buf0, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        ps = ps0;
        buf = buf0;
        own = e.owner();
        A = e.getPosition(pt);
        D = e.dir();
        D = D.lengthSqr() < 1.0E-6 ? Z : D.normalize();
        S = new Vec3(D.z, 0, -D.x);
        L = e.length();
        E = A.add(D.scale(L));
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        ghosts();
        alpha(YoneRender.KATANA);
        blades(false);
        alpha(YoneRender.KATANA_RED);
        blades(true);
        add(YoneRender.GLOW);
        planes();
        add(YoneRender.WIND);
        streak();
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
        double yawDeg = PlayerGhost.yawOf(D);
        for (int i = 0; i < IMAGES; i++) {
            double fr = (i + 0.5) / IMAGES;
            double age = t - DASH - fr * 2.0;
            if (age < 0 || age > 16) {
                continue;
            }
            float al = (float) (0.6 * (1.0 - age / 16.0));
            ghost.silhouette(ps, buf, slim, at(fr * L, 0, 0).subtract(A), yawDeg, PlayerGhost.Pose.lunge(i % 2 == 0 ? 1f : -1f), i % 2 == 0 ? Pen.BLUE : Pen.RED, al);
        }
        if (t < DASH) {
            ghost.silhouette(ps, buf, slim, Vec3.ZERO, yawDeg, PlayerGhost.Pose.finish(), Pen.BLUE, (float) (0.4 * ease(t / DASH)));
        }
    }

    private void blades(boolean red) {
        if (!red && t < DASH + 2) {
            double k = ease(t / DASH);
            Vec3 h = A.add(0, 1.3, 0).add(D.scale(0.4)).add(S.scale(0.4));
            Vec3 tip = h.add(D.scale(4.6 * k));
            double w = h.distanceTo(tip) * ASPECT;
            float al = (float) (k * (t < DASH ? 1.0 : 1.0 - (t - DASH) / 2.0));
            p.ribbon(h, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
        }
        double fa = t - SUNDER;
        if (red && fa >= 0 && fa < 8) {
            double k = ease(fa / 2.0);
            Vec3 tip = E.add(0, 9.0 * (1.0 - k) + 0.3, 0);
            Vec3 hilt = tip.add(0, 9.0, 0);
            double w = 9.0 * ASPECT;
            float al = (float) (fa < 4 ? 1.0 : 1.0 - (fa - 4) / 4.0);
            p.ribbonSide(hilt, tip, camRight(), w, w, al, al, 0f, 1f, Pen.WHITE);
        }
    }

    private void planes() {
        double fa = t - SUNDER;
        if (fa < 0 || fa > 24) {
            return;
        }
        double k = ease(fa / 4.0);
        double split = ease((fa - 2) / 14.0) * 3.0;
        float al = (float) (0.45 * (1.0 - fa / 24.0));
        double h = 16.0 * k;
        for (int col = 0; col < 2; col++) {
            double off = (col == 0 ? 1 : -1) * (0.1 + split);
            p.quad(at(0, 0, off), at(L, 0, off), at(L, h, off), at(0, h, off), 0f, 0f, 1f, 1f, body(col), al);
        }
    }

    private void streak() {
        double age = t - DASH;
        if (age < 0 || age > 16) {
            return;
        }
        float al = (float) (0.4 * (1.0 - age / 16.0));
        double w = 2.8 - 1.4 * c01(age / 16.0);
        p.ribbon(at(0, 1.1, 0), at(L, 1.1, 0), w * 0.5, w, al * 0.3f, al, 0f, 1f, Pen.PALE);
        p.ribbon(at(0, 0.5, 0), at(L, 0.5, 0), w * 0.4, w * 0.9, al * 0.25f, al * 0.8f, 0.2f, 0.8f, Pen.BLUE);
        p.ribbon(at(0, 1.7, 0), at(L, 1.7, 0), w * 0.4, w * 0.9, al * 0.25f, al * 0.8f, 0.1f, 0.9f, Pen.RED);
    }

    private void mist() {
        double age = t - DASH;
        if (age < 0 || age > 16) {
            return;
        }
        float al = (float) (0.2 * (1.0 - age / 16.0));
        for (int i = 0; i < 10; i++) {
            double f = (i + 0.5) / 10.0;
            p.billboard(at(f * L, 0.7, 0), 1.6 + 1.6 * ease(age / 8.0), i + age * 0.05, Pen.PALE, al);
        }
        double fa = t - SUNDER;
        if (fa >= 0 && fa < 16) {
            p.billboard(E.add(0, 1.0, 0), 3.0 + 6.0 * ease(fa / 8.0), fa * 0.1, Pen.PALE, (float) (0.3 * (1.0 - fa / 16.0)));
        }
    }

    private void rings() {
        double w = ease(t / DASH);
        if (t < DASH) {
            p.plane(A.add(0, 0.12, 0), X, Z, 1.4 * w, t * 0.1, Pen.BLUE, (float) (0.6 * w));
            p.plane(E.add(0, 0.12, 0), X, Z, 1.2 * w, -t * 0.1, Pen.RED, (float) (0.5 * w));
        }
        for (int i = 0; i < 6; i++) {
            double f = (i + 0.5) / 6.0;
            double age = t - DASH - f * 2.0;
            if (age < 0 || age > 12) {
                continue;
            }
            double k = ease(age / 8.0);
            float al = (float) (1.0 - age / 12.0);
            p.plane(at(f * L, 1.2, 0), S, Y, 0.8 + 3.2 * k, 0, i % 2 == 0 ? Pen.BLUE : Pen.RED, al * 0.8f);
        }
        double a0 = t - DASH;
        if (a0 >= 0 && a0 < 14) {
            double k = ease(a0 / 8.0);
            p.plane(E.add(0, 0.2, 0), X, Z, 1.0 + 5.0 * k, 0, Pen.WHITE, (float) (0.7 * (1.0 - a0 / 14.0)));
        }
        // marks above the sealed enemies
        if (t > DASH && t < SUNDER + 2) {
            for (int id : ent.sealedIds()) {
                Entity e = ent.level().getEntity(id);
                if (e == null) {
                    continue;
                }
                Vec3 m = e.getPosition(1.0f).add(0, e.getBbHeight() + 0.8, 0);
                p.plane(m, X, Z, 0.7, t * 0.15, Pen.RED, 0.9f);
                p.plane(m.add(0, 0.2, 0), X, Z, 0.45, -t * 0.2, Pen.WHITE, 0.6f);
            }
        }
        double fa = t - SUNDER;
        if (fa >= 0 && fa < 18) {
            double k = ease(fa / 8.0);
            float al = (float) (1.0 - fa / 18.0);
            p.plane(E.add(0, 0.25, 0), X, Z, 1.0 + 9.0 * k, 0, Pen.BLUE, al * 0.85f);
            p.plane(E.add(0, 0.3, 0), X, Z, 1.0 + 6.0 * k, fa * 0.25, Pen.RED, al * 0.85f);
            for (int id : ent.sealedIds()) {
                Entity e = ent.level().getEntity(id);
                if (e != null) {
                    p.plane(e.getPosition(1.0f).add(0, 0.2, 0), X, Z, 1.0 + 4.0 * k, 0, Pen.WHITE, al * 0.7f);
                }
            }
        }
    }

    private void lines() {
        Vec3 chest = Vec3.ZERO;
        // the line drawn on the ground before the dash
        if (t < SUNDER) {
            double w = t < DASH ? ease(t / DASH) : 1.0 - c01((t - DASH) / 10.0);
            float al = (float) (0.7 * w);
            if (al > 0.01f) {
                int n = 16;
                for (int i = 0; i < n; i++) {
                    double f0 = L * i / n;
                    double f1 = L * (i + 1) / n * (t < DASH ? w : 1.0);
                    if (f0 >= f1) {
                        break;
                    }
                    line(at(f0, 0.15, -HW), at(f1, 0.15, -HW), 0.06, al, 0);
                    line(at(f0, 0.15, HW), at(f1, 0.15, HW), 0.06, al, 1);
                    line(at(f0, 0.15, 0), at(f1, 0.15, 0), 0.05, al * 0.8f, i % 2);
                }
            }
        }
        // the seam of fate at chest height: a thin line that winds into a double helix after the dash
        double seamA = t < DASH ? ease(t / DASH) * 0.7 : (t < SUNDER ? 1.0 : 1.0 - c01((t - SUNDER) / 6.0));
        if (seamA > 0.02) {
            int n = 40;
            for (int col = 0; col < 2; col++) {
                Vec3 prev = null;
                double wind = t >= DASH ? ease((t - DASH) / 6.0) : 0.0;
                for (int i = 0; i <= n; i++) {
                    double f = L * i / n;
                    double ang = f * 0.9 - t * 0.3 + col * Math.PI;
                    double rad = 0.45 * wind;
                    Vec3 cur = at(f, 1.2 + Math.sin(ang) * rad, Math.cos(ang) * rad);
                    if (prev != null) {
                        line(prev, cur, 0.045, (float) (seamA * (0.6 + 0.2 * Math.sin(t * 0.7 + i))), col);
                    }
                    prev = cur;
                }
            }
        }
        // dash speed lines
        double age = t - DASH;
        if (age >= 0 && age < 12) {
            float al = (float) (1.0 - age / 12.0);
            double k = ease(age / 3.0);
            line(at(0, 1.2, 0), at(L * k, 1.2, 0), 0.18, al * 0.9f, 0);
            Random r = new Random(ent.getId() * 31L);
            for (int i = 0; i < 18; i++) {
                double lat = (r.nextDouble() * 2 - 1) * HW;
                double y = 0.3 + r.nextDouble() * 2.4;
                double f0 = r.nextDouble() * L * 0.8;
                line(at(f0, y, lat), at(Math.min(L, f0 + 5.0 + 6.0 * k), y, lat), 0.05, al * 0.7f, i % 2);
            }
        }
        // sealed victims are tied to the seam
        if (t > DASH && t < SUNDER) {
            for (int id : ent.sealedIds()) {
                Entity e = ent.level().getEntity(id);
                if (e == null) {
                    continue;
                }
                Vec3 m = e.getPosition(1.0f).add(0, e.getBbHeight() * 0.5, 0);
                Vec3 down = new Vec3(m.x, A.y + 1.2, m.z);
                for (int i = 0; i < 3; i++) {
                    double an = i * 2.1 + t * 0.2;
                    Vec3 off = new Vec3(Math.cos(an) * 0.3, 0, Math.sin(an) * 0.3);
                    line(m.add(off), down.add(off), 0.03, 0.4f, i % 2);
                }
            }
        }
        // the sunder
        double fa = t - SUNDER;
        if (fa >= 0 && fa < 22) {
            double k = ease(fa / 4.0);
            float al = (float) (1.0 - fa / 22.0);
            double h = 16.0 * k;
            line(at(0, h, 0), at(L, h, 0), 0.2, al, 0);
            line(at(0, 0.1, 0), at(L, 0.1, 0), 0.25, al, 1);
            int cols = (int) (L / 2.0);
            for (int i = 0; i <= cols; i++) {
                double f = L * i / Math.max(1, cols);
                double hh = h * (0.5 + 0.5 * ((i * 0.37) % 1.0));
                line(at(f, 0, 0), at(f, hh, 0), 0.07, al * 0.8f, i % 2);
            }
            for (int id : ent.sealedIds()) {
                Entity e = ent.level().getEntity(id);
                if (e == null) {
                    continue;
                }
                Vec3 m = e.getPosition(1.0f).add(0, e.getBbHeight() * 0.5, 0);
                for (int i = 0; i < 3; i++) {
                    double an = 0.5 + i * 1.05 + id * 0.4;
                    Vec3 v = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0).scale(4.5 * k);
                    line(m.subtract(v), m.add(v), 0.11, al, i % 2);
                }
            }
            for (int i = 0; i < 16; i++) {
                double an = i * Math.PI * 2 / 16;
                Vec3 r2 = new Vec3(Math.cos(an), 0, Math.sin(an));
                line(E.add(r2.scale(1.5 + 3.0 * k)).add(0, 0.4, 0), E.add(r2.scale(3.0 + 7.0 * k)).add(0, 0.4 + 0.8 * k, 0), 0.05, al * 0.8f, i % 2);
            }
        }
    }

    private void glints() {
        Random r = new Random(ent.getId() * 977L);
        double seam = t < DASH ? 0.0 : (t < SUNDER ? 1.0 : 1.0 - c01((t - SUNDER) / 8.0));
        for (int i = 0; i < 24; i++) {
            double f = r.nextDouble() * L;
            double y = 0.8 + r.nextDouble() * 1.2;
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 0.5 + i * 2.3));
            p.billboard(at(f, y, (r.nextDouble() - 0.5) * 1.2), 0.18, t * 0.1 + i, Pen.WHITE, (float) (0.6 * seam * tw));
        }
        double age = t - DASH;
        if (age >= 0 && age < 8) {
            p.billboard(A.add(0, 1.2, 0), 1.2 + 1.4 * ease(age / 3.0), age * 0.2, Pen.ICE, (float) (1.0 - age / 8.0));
            p.billboard(E.add(0, 1.2, 0), 1.4 + 1.6 * ease(age / 3.0), age * 0.2, Pen.ROSE, (float) (1.0 - age / 8.0));
        }
        double fa = t - SUNDER;
        if (fa >= 0 && fa < 10) {
            p.billboard(E.add(0, 1.0, 0), 2.5 + 3.5 * ease(fa / 4.0), fa * 0.2, Pen.WHITE, (float) (1.0 - fa / 10.0));
            for (int id : ent.sealedIds()) {
                Entity e = ent.level().getEntity(id);
                if (e != null) {
                    p.billboard(e.getPosition(1.0f).add(0, e.getBbHeight() * 0.5, 0), 1.4 + 1.6 * ease(fa / 3.0), fa * 0.2, Pen.ROSE, (float) (1.0 - fa / 10.0));
                }
            }
        }
    }
}
