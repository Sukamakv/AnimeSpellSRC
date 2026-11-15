package com.example.jjkspells.yone.client;

import com.example.jjkspells.yone.entity.DestinyCleavedEntity;
import com.example.jjkspells.yone.spell.DestinyCleavedSpell;
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
 * Draws "Destiny Cleaved": Yone and his spirit stand split side by side, then a blue and a red cut tear across the sky as a giant X while the same
 * two cuts are carved over the ground as a second X, with silhouettes of Yone and the spirit dashing along them. Everything caught is marked with an X
 * and frozen; at the crossing a white rift splits the sky from top to bottom with a huge flash, shock rings and ground cracks.
 */
public class DestinyCleavedRenderer extends EntityRenderer<DestinyCleavedEntity> {
    private static final int T1 = DestinyCleavedEntity.T1;
    private static final int T2 = DestinyCleavedEntity.T2;
    private static final int T3 = DestinyCleavedEntity.T3;
    private static final int AFTER_T = DestinyCleavedEntity.AFTER_T;
    private static final int END = DestinyCleavedEntity.END;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final double SKY_H = 24.0;
    private static final double SKY_HALF = 28.0;
    private static final double GROUND_HALF = 23.0;
    private static final double BLADE = 15.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private final PlayerGhost ghost;
    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 D;
    private Vec3 S;
    private Vec3 C;
    private Vec3 XC;
    private Vec3 camPos;
    private PoseStack ps;
    private MultiBufferSource buf;
    private DestinyCleavedEntity ent;
    private Entity own;

    public DestinyCleavedRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
        this.ghost = new PlayerGhost(ctx);
    }

    @Override
    public boolean shouldRender(DestinyCleavedEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(DestinyCleavedEntity e) {
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

    /** Flat ribbon lying on the ground. */
    private void flat(Vec3 a, Vec3 b, double w, float al, boolean blue) {
        if (al <= 0.004f) {
            return;
        }
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(Y);
        if (side.lengthSqr() < 1.0E-6) {
            return;
        }
        side = side.normalize();
        p.ribbonSide(a, b, side, w * 2.6, w * 2.6, al * 0.5f, al * 0.5f, 0f, 1f, blue ? Pen.BLUE : Pen.RED);
        p.ribbonSide(a, b, side, w, w, al, al, 0f, 1f, blue ? Pen.ICE : Pen.ROSE);
        p.ribbonSide(a, b, side, w * 0.35, w * 0.35, al, al, 0f, 1f, Pen.WHITE);
    }

    // ------------------------------------------------------------------ geometry

    private Vec3 skyStart(int col) {
        return XC.add(S.scale(col == 0 ? -SKY_HALF : SKY_HALF)).add(0, SKY_HALF, 0);
    }

    private Vec3 skyEnd(int col) {
        return XC.add(S.scale(col == 0 ? SKY_HALF : -SKY_HALF)).add(0, -SKY_HALF, 0);
    }

    private Vec3 groundStart(int col) {
        return C.add(S.scale(col == 0 ? -GROUND_HALF : GROUND_HALF)).subtract(D.scale(GROUND_HALF)).add(0, 0.2, 0);
    }

    private Vec3 groundEnd(int col) {
        return C.add(S.scale(col == 0 ? GROUND_HALF : -GROUND_HALF)).add(D.scale(GROUND_HALF)).add(0, 0.2, 0);
    }

    private int cutTick(int col) {
        return col == 0 ? T1 : T2;
    }

    private double cutProgress(int col) {
        return ease((t - (cutTick(col) - 2)) / 3.0);
    }

    private double cutFade(int col) {
        return 1.0 - c01((t - cutTick(col) - 4) / (T3 + 14 - cutTick(col) - 4));
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DestinyCleavedEntity e, float yaw, float pt, PoseStack ps0, MultiBufferSource buf0, int light) {
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
        C = A.add(D.scale(DestinyCleavedSpell.CENTRE_DISTANCE));
        XC = C.add(0, SKY_H, 0);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        camPos = camera.getPosition();
        p = new Pen(ps0.last().pose(), A, camPos, camera.getLeftVector(), camera.getUpVector());

        ghosts();
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

    private void ghosts() {
        if (own == null) {
            return;
        }
        boolean slim = PlayerGhost.slimOf(own);
        double yawDeg = PlayerGhost.yawOf(D);
        double fadeOut = 1.0 - c01((t - T3 - 12) / 14.0);
        // before the cuts: Yone (blue) and his spirit (red) split apart
        double split = 2.4 * ease(t / 11.0);
        if (t < T3 - 2) {
            float base = (float) (0.55 * ease(t / 8.0));
            if (t < T1) {
                ghost.silhouette(ps, buf, slim, S.scale(-split), yawDeg, PlayerGhost.Pose.finish(), Pen.BLUE, base);
            }
            if (t < T2) {
                ghost.silhouette(ps, buf, slim, S.scale(split), yawDeg, PlayerGhost.Pose.finish(), Pen.RED, base);
            }
        }
        // dashes along the ground X, with afterimages
        for (int col = 0; col < 2; col++) {
            double start = cutTick(col) - 2;
            double age = t - start;
            if (age < 0 || age > 26) {
                continue;
            }
            Vec3 s = groundStart(col);
            Vec3 e = groundEnd(col);
            Vec3 dir = e.subtract(s).normalize();
            double yd = PlayerGhost.yawOf(dir);
            float[] tint = col == 0 ? Pen.BLUE : Pen.RED;
            double f = ease(age / 3.0);
            if (f < 1.0) {
                Vec3 pos = s.add(e.subtract(s).scale(f));
                ghost.silhouette(ps, buf, slim, pos.subtract(A), yd, PlayerGhost.Pose.lunge(col == 0 ? 1f : -1f), tint, 0.85f);
            }
            for (int i = 0; i < 7; i++) {
                double fr = (i + 0.5) / 7.0;
                double a2 = age - 2.0 - fr * 3.0;
                if (a2 < 0 || a2 > 18) {
                    continue;
                }
                Vec3 pos = s.add(e.subtract(s).scale(fr));
                ghost.silhouette(ps, buf, slim, pos.subtract(A), yd, i % 2 == 0 ? PlayerGhost.Pose.slash() : PlayerGhost.Pose.lunge(1f), tint, (float) (0.5 * (1.0 - a2 / 18.0)));
            }
        }
        // after the cleave: back to back at the crossing
        if (t >= T3 - 2 && fadeOut > 0.01) {
            float al = (float) (0.75 * ease((t - T3 + 2) / 4.0) * fadeOut);
            ghost.silhouette(ps, buf, slim, C.subtract(A).add(S.scale(-1.0)), yawDeg + 90, PlayerGhost.Pose.finish(), Pen.BLUE, al);
            ghost.silhouette(ps, buf, slim, C.subtract(A).add(S.scale(1.0)), yawDeg - 90, PlayerGhost.Pose.finish(), Pen.RED, al);
        }
    }

    private void blade(int col) {
        double f = cutProgress(col);
        if (f <= 0.004 || t >= T3 + 8) {
            return;
        }
        Vec3 s = skyStart(col);
        Vec3 e = skyEnd(col);
        Vec3 d = e.subtract(s).normalize();
        Vec3 tip = s.add(e.subtract(s).scale(f));
        Vec3 hilt = tip.subtract(d.scale(BLADE));
        float al = (float) (1.0 - c01((t - T3) / 8.0));
        double w = BLADE * ASPECT;
        p.ribbon(hilt, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
    }

    private void glow() {
        // windup aura around Yone and the spirit
        double env = ease(t / 8.0) * (1.0 - c01((t - T2) / 4.0));
        if (env > 0.004) {
            double split = 2.4 * ease(t / 11.0);
            p.billboard(A.add(0, 1.0, 0).add(S.scale(-split)), 1.8, 0, Pen.BLUE, (float) (0.22 * env));
            p.billboard(A.add(0, 1.0, 0).add(S.scale(split)), 1.8, 0, Pen.RED, (float) (0.22 * env));
        }
        // glow at the tips of the sky cuts
        for (int col = 0; col < 2; col++) {
            double f = cutProgress(col);
            if (f <= 0.004 || f >= 0.999 && t > cutTick(col) + 6) {
                continue;
            }
            Vec3 tip = skyStart(col).add(skyEnd(col).subtract(skyStart(col)).scale(f));
            float al = (float) (cutFade(col) * 0.7);
            p.billboard(tip, 4.0, 0, col == 0 ? Pen.BLUE : Pen.RED, al);
            p.billboard(tip, 2.0, 0, Pen.WHITE, al);
        }
        // marks glow on frozen enemies
        if (t > T1 && t < T3) {
            for (int id : ent.caughtIds()) {
                Entity v = ent.level().getEntity(id);
                if (v == null) {
                    continue;
                }
                Vec3 c = v.getPosition(1.0f).add(0, v.getBbHeight() * 0.5, 0);
                double pulse = 0.8 + 0.2 * Math.sin(t * 0.7 + id);
                p.billboard(c, (1.0 + v.getBbWidth()) * pulse, 0, id % 2 == 0 ? Pen.BLUE : Pen.RED, 0.4f);
            }
        }
        // the flash of the cleave
        double fa = t - T3;
        if (fa >= -1 && fa < 18) {
            double k = ease((fa + 1) / 4.0);
            float al = (float) (1.0 - c01((fa + 1) / 18.0));
            p.billboard(XC, 12.0 + 40.0 * k, 0, Pen.WHITE, 0.5f * al);
            p.billboard(XC, 8.0 + 22.0 * k, 0, Pen.BLUE, 0.4f * al);
            p.billboard(XC.add(S.scale(3.0)), 6.0 + 14.0 * k, 0, Pen.RED, 0.4f * al);
            p.billboard(C.add(0, 1.5, 0), 4.0 + 18.0 * k, 0, Pen.WHITE, 0.6f * al);
        }
    }

    private void rings() {
        // rings where each ground cut starts
        for (int col = 0; col < 2; col++) {
            double age = t - cutTick(col);
            if (age >= -2 && age < 14) {
                double k = ease((age + 2) / 8.0);
                float al = (float) (1.0 - c01((age + 2) / 16.0));
                p.plane(C.add(0, 0.2, 0), X, Z, 2.0 + 16.0 * k, age * 0.1, col == 0 ? Pen.BLUE : Pen.RED, 0.7f * al);
            }
        }
        // rune circle under the area during the freeze
        double env = ease((t - T1) / 6.0) * (1.0 - c01((t - T3) / 4.0));
        if (env > 0.004) {
            double R = DestinyCleavedSpell.RADIUS;
            p.plane(C.add(0, 0.15, 0), X, Z, R, t * 0.01, Pen.BLUE, (float) (0.45 * env));
            p.plane(C.add(0, 0.17, 0), X, Z, R * 0.75, -t * 0.015, Pen.RED, (float) (0.45 * env));
            p.plane(C.add(0, 0.19, 0), X, Z, R * 0.4, t * 0.03, Pen.WHITE, (float) (0.4 * env));
        }
        double fa = t - T3;
        if (fa >= 0 && fa < 20) {
            double k = ease(fa / 10.0);
            float al = (float) (1.0 - fa / 20.0);
            double R = DestinyCleavedSpell.RADIUS;
            p.plane(C.add(0, 0.2, 0), X, Z, R * k + 1.0, 0, Pen.BLUE, 0.85f * al);
            p.plane(C.add(0, 0.25, 0), X, Z, R * 0.75 * k + 1.0, fa * 0.1, Pen.RED, 0.8f * al);
            p.plane(C.add(0, 0.3, 0), X, Z, R * 0.45 * k + 1.0, 0, Pen.WHITE, 0.7f * al);
            // the sky X rings out
            p.plane(XC, S, Y, 4.0 + 36.0 * k, 0, Pen.WHITE, 0.6f * al);
            p.plane(XC, S, Y, 2.0 + 24.0 * k, fa * 0.08, Pen.RED, 0.5f * al);
        }
        double aa = t - AFTER_T;
        if (aa >= 0 && aa < 14) {
            double k = ease(aa / 10.0);
            float al = (float) (1.0 - aa / 14.0);
            p.plane(C.add(0, 0.3, 0), X, Z, 1.0 + DestinyCleavedSpell.RADIUS * k, 0, Pen.ROSE, 0.7f * al);
            p.plane(C.add(0, 0.35, 0), X, Z, 1.0 + DestinyCleavedSpell.RADIUS * 0.6 * k, 0, Pen.ICE, 0.6f * al);
        }
    }

    private void lines() {
        // the sky X: thick cut lines left behind the blades
        for (int col = 0; col < 2; col++) {
            double f = cutProgress(col);
            if (f <= 0.004) {
                continue;
            }
            Vec3 s = skyStart(col);
            Vec3 tip = s.add(skyEnd(col).subtract(s).scale(f));
            float al = (float) cutFade(col);
            double wid = 2.0 * (1.0 - 0.5 * c01((t - cutTick(col)) / 14.0));
            line(s, tip, wid, al, col == 0);
            p.ribbon(s, tip, wid * 0.35, al, 0f, 1f, Pen.WHITE);
        }
        // the ground X, carved after the silhouettes
        for (int col = 0; col < 2; col++) {
            double f = ease((t - (cutTick(col) - 1)) / 3.0);
            if (f <= 0.004) {
                continue;
            }
            Vec3 s = groundStart(col);
            Vec3 tip = s.add(groundEnd(col).subtract(s).scale(f));
            float al = (float) (cutFade(col) * 0.95);
            flat(s, tip, 0.9 * (1.0 - 0.4 * c01((t - cutTick(col)) / 14.0)), al, col == 0);
        }
        // X marks on frozen enemies
        if (t > T1 && t < T3) {
            Vec3 L = new Vec3(p.left.x(), p.left.y(), p.left.z());
            Vec3 U = new Vec3(p.up.x(), p.up.y(), p.up.z());
            double grow = ease((t - T1) / 4.0);
            for (int id : ent.caughtIds()) {
                Entity v = ent.level().getEntity(id);
                if (v == null) {
                    continue;
                }
                Vec3 c = v.getPosition(1.0f).add(0, v.getBbHeight() * 0.5, 0);
                double s = (0.7 + v.getBbWidth() * 0.6) * grow;
                Vec3 a = L.add(U).scale(s);
                Vec3 b = L.subtract(U).scale(s);
                line(c.subtract(a), c.add(a), 0.1, 0.9f, true);
                line(c.subtract(b), c.add(b), 0.1, 0.9f, false);
            }
        }
        double fa = t - T3;
        // the rift: a white pillar splitting the sky from the ground to the top
        if (fa >= -1 && fa < 22) {
            Vec3 toCam = new Vec3(camPos.x - C.x, 0, camPos.z - C.z);
            Vec3 side = toCam.cross(Y);
            side = side.lengthSqr() < 1.0E-6 ? S : side.normalize();
            double k = ease((fa + 1) / 3.0);
            float al = (float) (1.0 - c01((fa + 1) / 22.0));
            double wid = 7.0 * (1.0 - 0.8 * c01(fa / 20.0));
            Vec3 a = C.add(0, 0.0, 0);
            Vec3 b = C.add(0, 90.0 * k, 0);
            p.ribbonSide(a, b, side, wid * 2.2, wid * 1.2, al * 0.4f, al * 0.2f, 0f, 1f, Pen.RED);
            p.ribbonSide(a, b, side, wid * 1.6, wid * 0.9, al * 0.5f, al * 0.25f, 0f, 1f, Pen.BLUE);
            p.ribbonSide(a, b, side, wid, wid * 0.5, al, al * 0.5f, 0f, 1f, Pen.WHITE);
        }
        // ground cracks
        if (fa >= 0 && fa < 26) {
            Random rnd = new Random(77);
            float al = (float) (1.0 - fa / 26.0);
            double k = ease(fa / 8.0);
            for (int i = 0; i < 24; i++) {
                double ang = i / 24.0 * Math.PI * 2 + rnd.nextDouble() * 0.25;
                double len = (6.0 + rnd.nextDouble() * 16.0) * k;
                Vec3 base = C.add(0, 0.2, 0);
                Vec3 a0 = base.add(Math.cos(ang) * 1.5, 0, Math.sin(ang) * 1.5);
                Vec3 mid = base.add(Math.cos(ang + 0.1) * (1.5 + len * 0.5), 0, Math.sin(ang + 0.1) * (1.5 + len * 0.5));
                Vec3 a1 = base.add(Math.cos(ang) * (1.5 + len), 0, Math.sin(ang) * (1.5 + len));
                line(a0, mid, 0.28, al, i % 2 == 0);
                line(mid, a1, 0.16, al, i % 2 == 0);
            }
        }
    }

    private void glints() {
        double fa = t - T3;
        if (fa >= -1 && fa < 22) {
            Random rnd = new Random(5);
            float al = (float) (1.0 - c01((fa + 1) / 22.0));
            double age = Math.max(0, fa + 1);
            for (int i = 0; i < 60; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double sp = 0.3 + rnd.nextDouble() * 1.0;
                double up = rnd.nextDouble() * 0.7;
                Vec3 c = C.add(Math.cos(ang) * sp * age * 1.8, 0.5 + up * age * 1.6 - 0.02 * age * age, Math.sin(ang) * sp * age * 1.8);
                p.billboard(c, 0.5 + rnd.nextDouble() * 0.6, age * 0.3, i % 3 == 0 ? Pen.WHITE : (i % 3 == 1 ? Pen.ICE : Pen.ROSE), 0.9f * al);
            }
            double k = ease((fa + 1) / 3.0);
            p.billboard(XC, 3.0 + 9.0 * k, fa * 0.2, Pen.WHITE, 0.9f * al);
        }
        // sparks at the crossing of the ground cuts
        for (int col = 0; col < 2; col++) {
            double age = t - cutTick(col);
            if (age >= 0 && age < 10) {
                Random rnd = new Random(10 + col);
                for (int i = 0; i < 12; i++) {
                    double f = rnd.nextDouble();
                    Vec3 base = groundStart(col).add(groundEnd(col).subtract(groundStart(col)).scale(f));
                    Vec3 c = base.add(0, 0.4 + age * (0.3 + rnd.nextDouble() * 0.5), 0);
                    p.billboard(c, 0.4 + rnd.nextDouble() * 0.3, age, col == 0 ? Pen.ICE : Pen.ROSE, (float) (0.8 * (1.0 - age / 10.0)));
                }
            }
        }
    }
}
