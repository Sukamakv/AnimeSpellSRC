package com.example.jjkspells.yasuo.client;

import com.example.jjkspells.yasuo.entity.LastBreathEntity;
import com.example.jjkspells.yasuo.spell.LastBreathSpell;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Draws "Last Breath": marked enemies hang in a column of rising wind with a crossed-blade mark and spinning rings; Yasuo blinks between them leaving a
 * wind streak, flat blue afterimages and a full-colour doppelganger standing at every cut; on the last tick every mark detonates in huge crossed slashes and
 * shock rings while a circle of doppelgangers closes in.
 */
public class LastBreathRenderer extends EntityRenderer<LastBreathEntity> {
    private static final int T0 = LastBreathEntity.T0;
    private static final int FADE = LastBreathEntity.FADE;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private final PlayerGhost ghost;
    private Pen p;
    private float t;
    private Vec3 A;
    private PoseStack ps;
    private MultiBufferSource buf;
    private LastBreathEntity ent;
    private Entity own;
    private int fin;
    private List<Vec3> marks = new ArrayList<>();

    public LastBreathRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
        this.ghost = new PlayerGhost(ctx);
    }

    @Override
    public boolean shouldRender(LastBreathEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(LastBreathEntity e) {
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

    private static Vec3 pt(int[] d, int o) {
        return new Vec3(d[o] / 10.0, d[o + 1] / 10.0, d[o + 2] / 10.0);
    }

    private double age(int[] d) {
        return t - d[6];
    }

    @Override
    public void render(LastBreathEntity e, float yaw, float pt, PoseStack ps0, MultiBufferSource buf0, int light) {
        t = e.tickCount - 1 + pt;
        fin = e.finalTick();
        if (t < 0 || fin < 0 || t > fin + FADE) {
            return;
        }
        ent = e;
        ps = ps0;
        buf = buf0;
        own = e.owner();
        A = e.getPosition(pt);
        marks = new ArrayList<>();
        for (int id : e.targetIds()) {
            Entity te = e.level().getEntity(id);
            if (te != null) {
                marks.add(te.getPosition(pt).add(0, te.getBbHeight() * 0.5, 0));
            }
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        ghosts();
        alpha(YasuoRender.KATANA);
        katanas();
        add(YasuoRender.WIND);
        winds();
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

    private void ghosts() {
        boolean slim = PlayerGhost.slimOf(own);
        ResourceLocation skin = PlayerGhost.skinOf(own);
        List<int[]> ds = ent.dashes();
        for (int i = 0; i < ds.size(); i++) {
            int[] d = ds.get(i);
            double a = age(d);
            if (a < 0) {
                continue;
            }
            Vec3 s = pt(d, 0);
            Vec3 en = pt(d, 3);
            Vec3 dir = en.subtract(s);
            if (dir.lengthSqr() < 1.0E-6) {
                continue;
            }
            Vec3 dn = dir.normalize();
            double yaw = PlayerGhost.yawOf(dn);
            // flat afterimages along the blink
            for (int j = 0; j < 3; j++) {
                double f = (j + 0.5) / 3.0;
                double aj = a - (1.0 - f) * 1.5;
                double life = 12.0;
                if (aj < 0 || aj > life) {
                    continue;
                }
                float al = (float) (0.5 * (1.0 - aj / life));
                ghost.silhouette(ps, buf, slim, s.add(dir.scale(f)).subtract(A), yaw, PlayerGhost.Pose.lunge(j % 2 == 0 ? 1f : -1f), j % 2 == 0 ? Pen.AQUA : Pen.SKY, al);
            }
            // doppelganger left at the cut until the mark detonates
            double stay = fin - d[6];
            if (a < stay + 2) {
                float al = (float) (Math.min(1.0, a / 2.0) * 0.8);
                if (t > fin - 3) {
                    al *= (float) c01((fin + 1 - t) / 4.0);
                }
                ghost.skinned(ps, buf, skin, slim, en.subtract(A), yaw, i % 2 == 0 ? PlayerGhost.Pose.slash() : PlayerGhost.Pose.finish(), al);
            }
        }
        // executioners closing in at the detonation
        double fa = t - fin;
        if (fa >= -2 && fa <= 16 && !marks.isEmpty()) {
            Vec3 cen = Vec3.ZERO;
            for (Vec3 m : marks) {
                cen = cen.add(m);
            }
            cen = cen.scale(1.0 / marks.size());
            double k = ease((fa + 2) / 6.0);
            for (int i = 0; i < 6; i++) {
                double ang = Math.PI * 2 * i / 6 + 0.4;
                double rad = 6.5 - 2.0 * k;
                Vec3 pos = cen.add(Math.cos(ang) * rad, -1.0, Math.sin(ang) * rad);
                Vec3 face = cen.subtract(pos);
                double yaw = PlayerGhost.yawOf(new Vec3(face.x, 0, face.z).normalize());
                float al = (float) (0.85 * c01((fa + 2) / 3.0) * (1.0 - c01((fa - 8) / 8.0)));
                ghost.skinned(ps, buf, skin, slim, pos.subtract(A), yaw, PlayerGhost.Pose.slash(), al);
            }
        }
    }

    private void katanas() {
        for (int[] d : ent.dashes()) {
            double a = age(d);
            if (a < 0 || a > 6) {
                continue;
            }
            Vec3 s = pt(d, 0);
            Vec3 en = pt(d, 3);
            Vec3 dn = en.subtract(s).normalize();
            Vec3 side = new Vec3(-dn.z, 0, dn.x);
            double sw = -1.1 + 2.2 * ease(a / 5.0);
            Vec3 h = en.add(0, 1.2, 0).subtract(dn.scale(0.4));
            Vec3 tip = h.add(dn.scale(Math.cos(sw) * 4.2)).add(side.scale(Math.sin(sw) * 4.2)).add(0, 0.5 * Math.sin(sw), 0);
            float al = (float) (1.0 - a / 6.0);
            double w = h.distanceTo(tip) * ASPECT;
            p.ribbon(h, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
        }
    }

    private void winds() {
        // blink streaks
        for (int[] d : ent.dashes()) {
            double a = age(d);
            if (a < 0 || a > 16) {
                continue;
            }
            Vec3 s = pt(d, 0).add(0, 1.0, 0);
            Vec3 en = pt(d, 3).add(0, 1.0, 0);
            float al = (float) (0.4 * (1.0 - a / 16.0));
            double w = 2.4 - 1.2 * c01(a / 16.0);
            p.ribbon(s, en, w * 0.3, w, al * 0.3f, al, 0f, 1f, Pen.PALE);
        }
        // rising wind columns under the suspended enemies
        if (t < fin) {
            double env = ease(t / T0);
            for (Vec3 m : marks) {
                for (int k = 0; k < 3; k++) {
                    Vec3 prev = null;
                    double ph = k * 2.1;
                    for (int j = 0; j <= 8; j++) {
                        double f = j / 8.0;
                        double y = -LastBreathSpell.LIFT * (1 - f) - 0.9 * 0;
                        double ang = ph + t * 0.25 + f * 5.0;
                        double rad = 0.9 + 0.8 * (1 - f);
                        Vec3 cur = m.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
                        if (prev != null) {
                            p.ribbon(prev, cur, 0.45, 0.45, (float) (0.35 * env * f), (float) (0.35 * env * f), 0f, 1f, Pen.PALE);
                        }
                        prev = cur;
                    }
                }
            }
        }
    }

    private void mist() {
        if (t < fin) {
            double env = ease(t / T0);
            for (Vec3 m : marks) {
                p.billboard(m.add(0, -LastBreathSpell.LIFT + 0.2, 0), 2.6, t * 0.03, Pen.PALE, (float) (0.2 * env));
            }
        }
        for (int[] d : ent.dashes()) {
            double a = age(d);
            if (a < 0 || a > 14) {
                continue;
            }
            float al = (float) (0.2 * (1.0 - a / 14.0));
            p.billboard(pt(d, 0).add(0, 0.8, 0), 1.8 + 2.0 * ease(a / 8.0), a * 0.1, Pen.PALE, al);
        }
        double fa = t - fin;
        if (fa >= 0 && fa < 16) {
            for (Vec3 m : marks) {
                p.billboard(m, 3.0 + 4.0 * ease(fa / 8.0), fa * 0.1, Pen.PALE, (float) (0.35 * (1.0 - fa / 16.0)));
            }
        }
    }

    private void rings() {
        for (int[] d : ent.dashes()) {
            double a = age(d);
            if (a < 0 || a > 12) {
                continue;
            }
            Vec3 s = pt(d, 0);
            Vec3 en = pt(d, 3);
            Vec3 dn = en.subtract(s).normalize();
            Vec3 side = new Vec3(-dn.z, 0, dn.x);
            double k = ease(a / 9.0);
            float al = (float) (1.0 - a / 12.0);
            p.plane(en.add(0, 1.0, 0), side, Y, 0.8 + 4.0 * k, 0, Pen.AQUA, al * 0.8f);
        }
        if (t < fin) {
            double env = ease(t / T0);
            for (Vec3 m : marks) {
                Vec3 h = m.add(0, 1.4, 0);
                p.plane(h, X, Z, 0.9, t * 0.12, Pen.AQUA, (float) (0.8 * env));
                p.plane(h.add(0, 0.3, 0), X, Z, 0.6, -t * 0.2, Pen.WHITE, (float) (0.6 * env));
            }
        }
        double fa = t - fin;
        if (fa >= 0 && fa < 16) {
            double k = ease(fa / 10.0);
            float al = (float) (1.0 - fa / 16.0);
            for (Vec3 m : marks) {
                p.plane(m, X, Z, 1.0 + 7.0 * k, 0, Pen.AQUA, al * 0.8f);
                p.plane(m, X, Z, 0.6 + 4.0 * k, fa * 0.3, Pen.WHITE, al * 0.6f);
                p.plane(m, X, Y, 1.0 + 5.0 * k, 0, Pen.PALE, al * 0.5f);
            }
            p.plane(ent.originPos().add(0, 0.15, 0), X, Z, 0.8 + 5.0 * k, fa * 0.2, Pen.PALE, al * 0.6f);
        }
    }

    private void lines() {
        for (int[] d : ent.dashes()) {
            double a = age(d);
            if (a < 0 || a > 16) {
                continue;
            }
            Vec3 s = pt(d, 0);
            Vec3 en = pt(d, 3);
            Vec3 dn = en.subtract(s).normalize();
            Vec3 side = new Vec3(-dn.z, 0, dn.x);
            float al = (float) (1.0 - a / 16.0);
            line(s.add(0, 1.0, 0), en.add(0, 1.0, 0), 0.15 * (1.0 - c01(a / 16.0) * 0.6), al * 0.9f, Pen.AQUA);
            if (a < 10) {
                double k = ease(a / 3.0);
                float al2 = (float) (1.0 - a / 10.0);
                Vec3 c = en.add(0, 1.0, 0);
                for (int i = 0; i < 2; i++) {
                    double ang = i == 0 ? 0.6 : -0.6;
                    Vec3 v = side.scale(Math.cos(ang)).add(0, Math.sin(ang), 0).scale(3.0 * k);
                    line(c.subtract(v), c.add(v), 0.1, al2, Pen.WHITE);
                }
            }
        }
        // crossed-blade marks
        if (t < fin) {
            double env = ease(t / T0);
            double pulse = 0.85 + 0.15 * Math.sin(t * 0.5);
            double charge = 1.0 + 0.6 * c01((t - (fin - 10)) / 10.0);
            for (Vec3 m : marks) {
                for (int i = 0; i < 2; i++) {
                    double ang = i == 0 ? 0.785 : -0.785;
                    Vec3 u = camRight().scale(Math.cos(ang)).add(0, Math.sin(ang), 0).scale(1.3 * env * pulse * charge);
                    line(m.subtract(u), m.add(u), 0.07, (float) (0.8 * env), Pen.WHITE);
                }
            }
        }
        double fa = t - fin;
        if (fa >= 0 && fa < 14) {
            double k = ease(fa / 3.0);
            float al = (float) (1.0 - fa / 14.0);
            for (int mi = 0; mi < marks.size(); mi++) {
                Vec3 m = marks.get(mi);
                for (int i = 0; i < 3; i++) {
                    double ang = 0.5 + i * 1.05 + mi * 0.4;
                    Vec3 v = camRight().scale(Math.cos(ang)).add(0, Math.sin(ang), 0).scale(5.0 * k);
                    line(m.subtract(v), m.add(v), 0.12, al, Pen.WHITE);
                }
                for (int i = 0; i < 12; i++) {
                    double an = i * Math.PI / 6 + mi;
                    Vec3 r = camRight().scale(Math.cos(an)).add(0, Math.sin(an), 0);
                    line(m.add(r.scale(1.5 + 4.0 * k)), m.add(r.scale(2.5 + 7.0 * k)), 0.05, al * 0.8f, Pen.AQUA);
                }
                // slam streak straight down
                line(m.add(0, 1.0, 0), m.add(0, -LastBreathSpell.LIFT, 0), 0.12, al * 0.8f, Pen.AQUA);
            }
        }
    }

    private Vec3 camRight() {
        Camera c = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f l = c.getLeftVector();
        return new Vec3(-l.x(), -l.y(), -l.z());
    }

    private void glints() {
        double fa = t - fin;
        if (t < fin) {
            double env = ease(t / T0);
            for (int mi = 0; mi < marks.size(); mi++) {
                Vec3 m = marks.get(mi);
                for (int i = 0; i < 6; i++) {
                    double ang = t * 0.1 + i * 1.05 + mi;
                    double y = -LastBreathSpell.LIFT * ((i * 0.37 + t * 0.03) % 1.0);
                    float tw = (float) (0.5 + 0.5 * Math.sin(t * 0.4 + i * 2.3));
                    p.billboard(m.add(Math.cos(ang) * 1.2, y, Math.sin(ang) * 1.2), 0.17, t * 0.05, Pen.WHITE, (float) (0.6 * env * tw));
                }
                p.billboard(m, 0.35 + 0.1 * Math.sin(t * 0.5), t * 0.05, Pen.WHITE, (float) (0.6 * env));
            }
        }
        for (int[] d : ent.dashes()) {
            double a = age(d);
            if (a < 0 || a > 10) {
                continue;
            }
            float al = (float) (1.0 - a / 10.0);
            p.billboard(pt(d, 3).add(0, 1.0, 0), 1.0 + 0.8 * ease(a / 4.0), a * 0.2, Pen.WHITE, al * 0.9f);
        }
        if (fa >= 0 && fa < 10) {
            float al = (float) (1.0 - fa / 10.0);
            for (Vec3 m : marks) {
                p.billboard(m, 2.0 + 3.0 * ease(fa / 4.0), fa * 0.2, Pen.WHITE, al);
            }
        }
    }
}
