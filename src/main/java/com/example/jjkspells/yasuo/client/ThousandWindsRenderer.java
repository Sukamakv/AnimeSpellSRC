package com.example.jjkspells.yasuo.client;

import com.example.jjkspells.yasuo.entity.ThousandWindsEntity;
import com.example.jjkspells.yasuo.spell.ThousandWindsSpell;
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
 * Draws "Thousand Winds": while Yasuo charges a spectral katana rises over him and rings of wind sweep out to the edge of the field, where twelve skinned
 * doppelgangers appear. During the barrage a giant katana falls from the sky at every strike, with a trailing streak, ground ring, crossed ground cuts and
 * dust; a low wind wall rims the field. On the final tick the doppelgangers lunge to the centre while two huge slashes, a pillar of wind and shock rings
 * level the arena.
 */
public class ThousandWindsRenderer extends EntityRenderer<ThousandWindsEntity> {
    private static final int CHARGE = ThousandWindsEntity.CHARGE;
    private static final int FIN = ThousandWindsEntity.FIN;
    private static final int END = ThousandWindsEntity.END;
    private static final double R = ThousandWindsSpell.RADIUS;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final int CLONES = 12;
    private static final int SEG = 36;
    private static final float DU = 0.25f;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private final PlayerGhost ghost;
    private Pen p;
    private float t;
    private Vec3 C;
    private Vec3 A;
    private PoseStack ps;
    private MultiBufferSource buf;
    private ThousandWindsEntity ent;
    private Entity own;
    private double env;

    public ThousandWindsRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
        this.ghost = new PlayerGhost(ctx);
    }

    @Override
    public boolean shouldRender(ThousandWindsEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ThousandWindsEntity e) {
        return YasuoRender.GLOW;
    }

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void add(ResourceLocation tex) {
        p.vc = buf.getBuffer(YasuoRender.additive(tex));
    }

    private void alpha(ResourceLocation tex) {
        p.vc = buf.getBuffer(YasuoRender.alpha(tex));
    }

    private void line(Vec3 a, Vec3 b, double w, float al, float[] col) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.4, al * 0.45f, 0f, 1f, col);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.CORE);
    }

    private Vec3 at(double ang, double rad, double y) {
        return C.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
    }

    private static Vec3 pt(int[] d) {
        return new Vec3(d[0] / 10.0, d[1] / 10.0, d[2] / 10.0);
    }

    /** Where a strike's katana comes from: a point high above and to one side of the impact. */
    private static Vec3 top(int[] d) {
        double ang = ((d[0] * 7 + d[2] * 13) % 628 + 628) % 628 / 100.0;
        return pt(d).add(Math.cos(ang) * 5.0, 17.0, Math.sin(ang) * 5.0);
    }

    @Override
    public void render(ThousandWindsEntity e, float yaw, float pt, PoseStack ps0, MultiBufferSource buf0, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        ps = ps0;
        buf = buf0;
        own = e.owner();
        A = e.getPosition(pt);
        C = A;
        env = ease(t / 10.0) * (1.0 - c01((t - FIN) / (END - FIN)));
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        clones();
        alpha(YasuoRender.KATANA);
        katanas();
        add(YasuoRender.WIND);
        wall();
        add(YasuoRender.MIST);
        mist();
        add(YasuoRender.RING);
        rings();
        add(YasuoRender.LINE);
        lines();
        add(YasuoRender.GLINT);
        glints();
    }

    // ------------------------------------------------------------------ passes

    private void clones() {
        boolean slim = PlayerGhost.slimOf(own);
        ResourceLocation skin = PlayerGhost.skinOf(own);
        double fa = t - FIN;
        for (int i = 0; i < CLONES; i++) {
            double appear = ease((t - i * 1.5) / 6.0);
            if (appear <= 0) {
                continue;
            }
            double ang = Math.PI * 2 * i / CLONES;
            double rad = R - 2.0;
            double k = ease(fa / 6.0);
            if (fa > 0) {
                rad = rad * (1 - k) + 3.0 * k;
            }
            Vec3 pos = at(ang, rad, 0);
            Vec3 face = C.subtract(pos);
            double yawDeg = PlayerGhost.yawOf(new Vec3(face.x, 0, face.z).normalize());
            double fade = 1.0 - c01((fa - 6) / 10.0);
            float al = (float) (0.75 * appear * fade);
            PlayerGhost.Pose pose;
            if (fa > 0) {
                pose = PlayerGhost.Pose.lunge(i % 2 == 0 ? 1f : -1f);
            } else {
                pose = (((int) (t / 7) + i) % 2 == 0) ? PlayerGhost.Pose.slash() : PlayerGhost.Pose.finish();
            }
            ghost.skinned(ps, buf, skin, slim, pos.subtract(A), yawDeg, pose, al);
            if (fa > 0 && fa < 8) {
                // speed afterimage behind a lunging clone
                Vec3 back = at(ang, rad + 3.0 * (1 - k) + 1.5, 0);
                ghost.silhouette(ps, buf, slim, back.subtract(A), yawDeg, pose, Pen.AQUA, (float) (0.4 * (1 - fa / 8.0)));
            }
        }
    }

    private void katanas() {
        // charge katana over Yasuo
        if (t < FIN) {
            double k = ease(t / CHARGE);
            double fade = 1.0 - c01((t - CHARGE - 6) / 10.0);
            Vec3 h = C.add(0, 2.4, 0);
            Vec3 tip = C.add(0, 2.4 + 8.0 * k, 0);
            double w = h.distanceTo(tip) * ASPECT;
            float al = (float) (0.9 * k * fade);
            if (al > 0.02f && t < CHARGE + 16) {
                p.ribbon(h, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
            }
        }
        // falling blades
        for (int[] d : ent.strikes()) {
            double age = t - d[3];
            if (age < 0 || age > 8) {
                continue;
            }
            Vec3 pt = pt(d);
            Vec3 tp = top(d);
            Vec3 up = tp.subtract(pt).normalize();
            double f = c01(age / 2.0);
            Vec3 tip = pt.add(tp.subtract(pt).scale(1.0 - f * f));
            Vec3 hilt = tip.add(up.scale(13.0));
            double w = 13.0 * ASPECT;
            float al = (float) (age < 4 ? 1.0 : 1.0 - (age - 4) / 4.0);
            p.ribbon(hilt, tip, w, w, al * 0.95f, al * 0.95f, 0f, 1f, Pen.WHITE);
        }
    }

    private void wall() {
        double h = 9.0 * env;
        for (int i = 0; i < SEG; i++) {
            double a0 = t * 0.02 + Math.PI * 2 * i / SEG;
            double a1 = t * 0.02 + Math.PI * 2 * (i + 1) / SEG;
            double fr = ((t * 0.03 + i * 0.37) % 1.0 + 1.0) % 1.0;
            float u0 = (float) (fr * (1 - DU));
            float u1 = u0 + DU;
            for (int j = 0; j < 4; j++) {
                double f0 = j / 4.0;
                double f1 = (j + 1) / 4.0;
                float al = (float) (0.2 * env * (1.0 - 0.6 * f0));
                p.quad(at(a0, R, h * f0), at(a1, R, h * f0), at(a1, R, h * f1), at(a0, R, h * f1), u0, (float) f0, u1, (float) f1, Pen.PALE, al);
            }
        }
    }

    private void mist() {
        for (int i = 0; i < 18; i++) {
            double a = Math.PI * 2 * i / 18 + t * 0.01;
            p.billboard(at(a, R - 1.5, 0.8 + 0.4 * Math.sin(t * 0.2 + i)), 2.6, t * 0.02 + i, Pen.PALE, (float) (0.10 * env));
        }
        for (int[] d : ent.strikes()) {
            double age = t - d[3];
            if (age < 0 || age > 14) {
                continue;
            }
            Vec3 pt = pt(d);
            p.billboard(pt.add(0, 0.8, 0), 2.0 + 3.0 * ease(age / 8.0), age * 0.1, Pen.PALE, (float) (0.28 * (1 - age / 14.0)));
        }
        double fa = t - FIN;
        if (fa >= 0 && fa < 18) {
            p.billboard(C.add(0, 2.0, 0), 6.0 + 14.0 * ease(fa / 10.0), fa * 0.05, Pen.PALE, (float) (0.35 * (1 - fa / 18.0)));
        }
    }

    private void rings() {
        // glyph rings on the floor
        p.plane(C.add(0, 0.12, 0), X, Z, (R + 0.8) * env, t * 0.01, Pen.AQUA, (float) (0.5 * env));
        p.plane(C.add(0, 0.14, 0), X, Z, R * 0.7 * env, -t * 0.02, Pen.PALE, (float) (0.3 * env));
        p.plane(C.add(0, 0.16, 0), X, Z, R * 0.4 * env, t * 0.03, Pen.SKY, (float) (0.3 * env));
        p.plane(C.add(0, 0.18, 0), X, Z, 3.0 * env, -t * 0.08, Pen.WHITE, (float) (0.4 * env));
        // charge shock rings sweeping out
        if (t < CHARGE + 8) {
            for (int i = 0; i < 3; i++) {
                double age = t - i * 7;
                if (age < 0 || age > 18) {
                    continue;
                }
                p.plane(C.add(0, 0.3, 0), X, Z, R * ease(age / 14.0), 0, Pen.WHITE, (float) (0.6 * (1 - age / 18.0)));
            }
        }
        for (int[] d : ent.strikes()) {
            double age = t - d[3];
            if (age < 1 || age > 14) {
                continue;
            }
            Vec3 pt = pt(d);
            double k = ease((age - 1) / 6.0);
            float al = (float) (1.0 - age / 14.0);
            p.plane(pt.add(0, 0.15, 0), X, Z, 0.6 + ThousandWindsSpell.STRIKE_RADIUS * 1.2 * k, age * 0.2, Pen.AQUA, al * 0.8f);
            p.plane(pt.add(0, 0.18, 0), X, Z, 0.4 + ThousandWindsSpell.STRIKE_RADIUS * 0.7 * k, -age * 0.3, Pen.WHITE, al * 0.6f);
        }
        double fa = t - FIN;
        if (fa >= 0 && fa < 20) {
            double k = ease(fa / 12.0);
            float al = (float) (1.0 - fa / 20.0);
            p.plane(C.add(0, 0.3, 0), X, Z, 2.0 + (R + 10.0) * k, 0, Pen.WHITE, al * 0.9f);
            p.plane(C.add(0, 0.5, 0), X, Z, 2.0 + (R + 4.0) * k, fa * 0.2, Pen.AQUA, al * 0.8f);
            p.plane(C.add(0, 6.0 * k, 0), X, Z, 2.0 + 12.0 * k, 0, Pen.PALE, al * 0.5f);
        }
    }

    private Vec3 camRight() {
        Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f l = c.getLeftVector();
        return new Vec3(-l.x(), -l.y(), -l.z());
    }

    private void lines() {
        // charge: wind converging up to the raised blade
        if (t < CHARGE + 10) {
            double k = ease(t / CHARGE);
            double fade = 1.0 - c01((t - CHARGE) / 10.0);
            for (int i = 0; i < 10; i++) {
                double base = Math.PI * 2 * i / 10 + t * 0.15;
                Vec3 prev = null;
                for (int j = 0; j <= 12; j++) {
                    double f = j / 12.0;
                    double rad = (R * 0.45) * (1 - f) * (1 - 0.5 * k) + 0.8;
                    Vec3 cur = at(base + f * 4.0, rad, 0.2 + 9.0 * f * k);
                    if (prev != null) {
                        line(prev, cur, 0.06, (float) (0.6 * k * fade), Pen.PALE);
                    }
                    prev = cur;
                }
            }
        }
        // rim wall lines
        double h = 9.0 * env;
        for (int lv = 0; lv < 2; lv++) {
            double y = lv == 0 ? 0.1 : h;
            for (int i = 0; i < 72; i++) {
                double a0 = Math.PI * 2 * i / 72;
                double a1 = Math.PI * 2 * (i + 1) / 72;
                line(at(a0, R, y), at(a1, R, y), 0.07, (float) (0.6 * env), Pen.AQUA);
            }
        }
        // strikes
        for (int[] d : ent.strikes()) {
            double age = t - d[3];
            if (age < 0 || age > 14) {
                continue;
            }
            Vec3 pt = pt(d);
            Vec3 tp = top(d);
            Vec3 up = tp.subtract(pt).normalize();
            double f = c01(age / 2.0);
            Vec3 tip = pt.add(tp.subtract(pt).scale(1.0 - f * f));
            if (age < 8) {
                float al = (float) (1.0 - age / 8.0);
                line(tip, tip.add(up.scale(16.0)), 0.18, al * 0.9f, Pen.AQUA);
                Vec3 side = up.cross(camRight()).normalize().scale(0.8);
                line(tip.add(side), tip.add(side).add(up.scale(11.0)), 0.06, al * 0.6f, Pen.PALE);
                line(tip.subtract(side), tip.subtract(side).add(up.scale(11.0)), 0.06, al * 0.6f, Pen.PALE);
            }
            if (age >= 1 && age < 10) {
                double k = ease((age - 1) / 3.0);
                float al = (float) (1.0 - (age - 1) / 9.0);
                double base = ((d[0] * 3 + d[2] * 5) % 314 + 314) % 314 / 100.0;
                for (int i = 0; i < 2; i++) {
                    double an = base + i * Math.PI / 2;
                    Vec3 v = new Vec3(Math.cos(an), 0, Math.sin(an)).scale(4.2 * k);
                    line(pt.add(0, 0.25, 0).subtract(v), pt.add(0, 0.25, 0).add(v), 0.12, al, Pen.WHITE);
                }
                for (int i = 0; i < 8; i++) {
                    double an = i * Math.PI / 4 + base;
                    Vec3 v = new Vec3(Math.cos(an), 0, Math.sin(an));
                    line(pt.add(v.scale(1.0 + 2.0 * k)).add(0, 0.3, 0), pt.add(v.scale(2.0 + 4.0 * k)).add(0, 0.8 * k, 0), 0.05, al * 0.8f, Pen.AQUA);
                }
            }
        }
        // finale: two huge crossing slashes, a pillar of wind, radial streaks
        double fa = t - FIN;
        if (fa >= 0 && fa < 20) {
            double k = ease(fa / 4.0);
            float al = (float) (1.0 - fa / 20.0);
            for (int i = 0; i < 2; i++) {
                double an = 0.6 + i * 1.3;
                Vec3 v = new Vec3(Math.cos(an), 0.05, Math.sin(an)).scale((R + 6.0) * k);
                Vec3 c0 = C.add(0, 3.0, 0);
                line(c0.subtract(v), c0.add(v), 0.45 * (1 - fa / 24.0), al, Pen.WHITE);
                line(c0.subtract(v).add(0, 0.9, 0), c0.add(v).add(0, 0.9, 0), 0.15, al * 0.7f, Pen.AQUA);
            }
            line(C, C.add(0, 36.0 * k, 0), 0.7 * (1 - fa / 24.0), al * 0.9f, Pen.AQUA);
            Random r = new Random(ent.getId() * 53L);
            for (int i = 0; i < 40; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double d0 = 2.0 + r.nextDouble() * R * 0.6;
                line(at(ang, d0, 0.5), at(ang, d0 + 4.0 + 14.0 * k, 0.5 + 6.0 * k * r.nextDouble()), 0.07, al * 0.8f, Pen.AQUA);
            }
        }
    }

    private void glints() {
        Random r = new Random(ent.getId() * 977L);
        for (int i = 0; i < 48; i++) {
            double ang = r.nextDouble() * Math.PI * 2 + t * 0.02 * (1 + r.nextDouble());
            double rad = R * (0.2 + 0.8 * r.nextDouble());
            double y = 14.0 * ((r.nextDouble() + t * 0.012 * (1 + r.nextDouble())) % 1.0);
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 0.4 + i * 2.3));
            p.billboard(at(ang, rad, y), 0.24, t * 0.05, Pen.WHITE, (float) (0.6 * env * tw));
        }
        for (int[] d : ent.strikes()) {
            double age = t - d[3];
            if (age < 0 || age > 8) {
                continue;
            }
            p.billboard(pt(d).add(0, 0.8, 0), 1.2 + 2.2 * ease(age / 3.0), age * 0.2, Pen.WHITE, (float) (1.0 - age / 8.0) * 0.9f);
        }
        double fa = t - FIN;
        if (fa >= 0 && fa < 10) {
            p.billboard(C.add(0, 3.0, 0), 5.0 + 9.0 * ease(fa / 5.0), fa * 0.1, Pen.WHITE, (float) (1.0 - fa / 10.0));
        }
        if (t < CHARGE + 6) {
            double k = ease(t / CHARGE);
            p.billboard(C.add(0, 2.4 + 8.0 * k, 0), 0.7 + 0.3 * Math.sin(t * 0.6), t * 0.1, Pen.WHITE, (float) (0.8 * k));
        }
    }
}
