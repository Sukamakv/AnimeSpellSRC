package com.example.jjkspells.yone.client;

import com.example.jjkspells.yone.entity.TwinCrescentsEntity;
import com.example.jjkspells.yone.spell.TwinCrescentsSpell;
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

/**
 * Draws "Twin Crescents": every stroke shows Yone's blade sweeping through the cut plane (blue Mortal Steel or red Spirit Blade sprite) with a body-sized
 * arc and a flat afterimage of him in the stroke pose; the crescent that leaves is the slash sprite standing in its cut plane, bulging forward with its tips
 * trailing behind, with two fading echoes, bright edge lines that face the camera from any angle, speed lines and glints. Each hit leaves crossed cuts
 * in the stroke's colour.
 */
public class TwinCrescentsRenderer extends EntityRenderer<TwinCrescentsEntity> {
    private static final double ASPECT = 192.0 / 1024.0;
    private static final int LIFE = TwinCrescentsEntity.LIFE;
    private static final Vec3 Y = new Vec3(0, 1, 0);

    private final PlayerGhost ghost;
    private Pen p;
    private float t;
    private Vec3 A;
    private PoseStack ps;
    private MultiBufferSource buf;
    private TwinCrescentsEntity ent;
    private Entity own;

    public TwinCrescentsRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
        this.ghost = new PlayerGhost(ctx);
    }

    @Override
    public boolean shouldRender(TwinCrescentsEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(TwinCrescentsEntity e) {
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

    private static Vec3 pos(int[] c) {
        return new Vec3(c[0] / 10.0, c[1] / 10.0, c[2] / 10.0);
    }

    private static Vec3 dir(int[] c) {
        return new Vec3(c[4] / 1000.0, 0, c[5] / 1000.0).normalize();
    }

    /** Cut axis: the lateral direction rolled around the travel direction. */
    private static Vec3 axis(int[] c) {
        Vec3 d = dir(c);
        Vec3 s = new Vec3(d.z, 0, -d.x);
        double r = Math.toRadians(c[6]);
        return s.scale(Math.cos(r)).add(0, Math.sin(r), 0).normalize();
    }

    private static double half(int[] c) {
        return c[8] == 1 ? TwinCrescentsSpell.HALF_WIDTH_BIG : TwinCrescentsSpell.HALF_WIDTH;
    }

    @Override
    public void render(TwinCrescentsEntity e, float yaw, float pt, PoseStack ps0, MultiBufferSource buf0, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > TwinCrescentsEntity.END) {
            return;
        }
        List<int[]> cs = e.crescents();
        if (cs.isEmpty()) {
            return;
        }
        ent = e;
        ps = ps0;
        buf = buf0;
        own = e.owner();
        A = e.getPosition(pt);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        for (int[] c : cs) {
            ghosts(c);
        }
        for (int[] c : cs) {
            alpha(c[7] == 0 ? YoneRender.KATANA : YoneRender.KATANA_RED);
            blade(c);
        }
        add(YoneRender.SLASH);
        for (int[] c : cs) {
            swingArc(c);
            crescent(c);
        }
        add(YoneRender.MIST);
        for (int[] c : cs) {
            mist(c);
        }
        add(YoneRender.LINE);
        for (int[] c : cs) {
            edges(c);
        }
        for (int[] h : e.hits()) {
            hitLines(h);
        }
        add(YoneRender.GLINT);
        for (int[] c : cs) {
            glints(c);
        }
        for (int[] h : e.hits()) {
            hitGlint(h);
        }
    }

    // ------------------------------------------------------------------ passes

    private void ghosts(int[] c) {
        double age = t - c[3];
        if (age < 0 || age > 9 || own == null) {
            return;
        }
        Vec3 d = dir(c);
        double yaw = PlayerGhost.yawOf(d);
        boolean slim = PlayerGhost.slimOf(own);
        Vec3 feet = pos(c).subtract(0, 1.2, 0).subtract(d.scale(0.8));
        PlayerGhost.Pose pose = c[8] == 1 ? PlayerGhost.Pose.lunge(c[7] == 0 ? 1f : -1f) : (c[7] == 0 ? PlayerGhost.Pose.slash() : PlayerGhost.Pose.finish());
        float al = (float) (0.55 * (1.0 - age / 9.0));
        ghost.silhouette(ps, buf, slim, feet.subtract(A), yaw, pose, c[7] == 0 ? Pen.BLUE : Pen.RED, al);
        ghost.silhouette(ps, buf, slim, feet.subtract(d.scale(1.8)).subtract(A), yaw, pose, c[7] == 0 ? Pen.ICE : Pen.ROSE, al * 0.5f);
    }

    private void blade(int[] c) {
        double age = t - c[3];
        if (age < 0 || age > 6) {
            return;
        }
        Vec3 d = dir(c);
        Vec3 u = axis(c);
        double sw = -1.25 + 2.5 * ease(age / 4.0);
        Vec3 h = pos(c).subtract(d.scale(0.5));
        double len = c[8] == 1 ? 5.2 : 4.2;
        Vec3 tip = h.add(d.scale(Math.cos(sw) * len)).add(u.scale(Math.sin(sw) * len));
        double w = h.distanceTo(tip) * ASPECT;
        float al = (float) (1.0 - age / 6.0);
        p.ribbon(h, tip, w, w, al, al, 0f, 1f, Pen.WHITE);
    }

    private void swingArc(int[] c) {
        double age = t - c[3];
        if (age < 0 || age > 6) {
            return;
        }
        Vec3 d = dir(c);
        Vec3 u = axis(c);
        double a = c[8] == 1 ? 3.6 : 2.8;
        double k = ease(age / 3.0);
        p.arc(pos(c).add(d.scale(0.9)), u, d, a * (0.6 + 0.4 * k), a * 0.5 * (0.6 + 0.4 * k), body(c[7]), (float) (0.9 * (1.0 - age / 6.0)));
    }

    private void crescent(int[] c) {
        double age = t - c[3];
        if (age < 1 || age > LIFE + 6) {
            return;
        }
        Vec3 d = dir(c);
        Vec3 u = axis(c);
        double a = half(c);
        double fade = ease(age / 2.0) * (1.0 - c01((age - LIFE + 2) / 8.0));
        for (int l = 0; l < 3; l++) {
            double back = l * 2.6;
            double dist = Math.min(age, LIFE) * TwinCrescentsSpell.SPEED + 1.0 - back;
            if (dist < 0.5) {
                continue;
            }
            double sc = 1.0 - 0.14 * l;
            float al = (float) (fade * (l == 0 ? 0.9 : l == 1 ? 0.45 : 0.22));
            Vec3 cen = pos(c).add(d.scale(dist));
            p.arc(cen, u, d, a * sc, a * 0.5 * sc, l == 0 ? edge(c[7]) : body(c[7]), al);
        }
    }

    private Vec3 crescentPoint(int[] c, double age, double theta, boolean outer, double sc) {
        Vec3 d = dir(c);
        Vec3 u = axis(c);
        double a = half(c) * sc;
        double b = a * 0.5;
        double dist = Math.min(age, LIFE) * TwinCrescentsSpell.SPEED + 1.0;
        Vec3 cen = pos(c).add(d.scale(dist));
        if (outer) {
            return cen.add(u.scale(a * Math.cos(theta))).add(d.scale(-b + 2 * b * Math.sin(theta)));
        }
        return cen.add(u.scale(0.8 * a * Math.cos(theta))).add(d.scale(-b + 1.5 * b * Math.sin(theta)));
    }

    private void mist(int[] c) {
        double age = t - c[3];
        if (age < 0 || age > 12) {
            return;
        }
        p.billboard(pos(c).add(dir(c).scale(0.5)), 1.4 + 1.6 * ease(age / 6.0), age * 0.1, body(c[7]), (float) (0.22 * (1.0 - age / 12.0)));
    }

    private void edges(int[] c) {
        double age = t - c[3];
        if (age < 1 || age > LIFE + 6) {
            return;
        }
        double fade = ease(age / 2.0) * (1.0 - c01((age - LIFE + 2) / 8.0));
        int n = 18;
        for (int lv = 0; lv < 2; lv++) {
            boolean outer = lv == 0;
            Vec3 prev = null;
            for (int i = 0; i <= n; i++) {
                double th = Math.PI * i / n;
                Vec3 cur = crescentPoint(c, age, th, outer, 1.0);
                if (prev != null) {
                    line(prev, cur, outer ? 0.09 : 0.05, (float) (fade * (outer ? 0.95 : 0.6)), c[7]);
                }
                prev = cur;
            }
        }
        // speed lines streaming back from the tips and the bulge
        Vec3 d = dir(c);
        for (int i = 0; i < 5; i++) {
            double th = Math.PI * (i + 0.5) / 5.0;
            Vec3 s0 = crescentPoint(c, age, th, true, 1.0);
            line(s0, s0.subtract(d.scale(4.0 + 2.0 * Math.sin(th))), 0.05, (float) (fade * 0.5), c[7]);
        }
    }

    private void hitLines(int[] h) {
        double age = t - h[3];
        if (age < 0 || age > 10) {
            return;
        }
        Vec3 m = pos(h);
        double k = ease(age / 3.0);
        float al = (float) (1.0 - age / 10.0);
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f l = cam.getLeftVector();
        Vec3 right = new Vec3(-l.x(), -l.y(), -l.z());
        for (int i = 0; i < 2; i++) {
            double an = i == 0 ? 0.7 : -0.7;
            Vec3 v = right.scale(Math.cos(an)).add(0, Math.sin(an), 0).scale(2.0 * k);
            line(m.subtract(v), m.add(v), 0.08, al, h[4]);
        }
    }

    private void glints(int[] c) {
        double age = t - c[3];
        if (age < 1 || age > LIFE + 4) {
            return;
        }
        double fade = 1.0 - c01((age - LIFE + 2) / 6.0);
        for (int i = 0; i < 7; i++) {
            double th = Math.PI * (i + 0.5) / 7.0;
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 0.5 + i * 2.1));
            p.billboard(crescentPoint(c, age, th, true, 1.0), 0.2, t * 0.1 + i, Pen.WHITE, (float) (0.7 * fade * tw));
        }
    }

    private void hitGlint(int[] h) {
        double age = t - h[3];
        if (age < 0 || age > 6) {
            return;
        }
        p.billboard(pos(h), 0.8 + 0.7 * ease(age / 3.0), age * 0.2, h[4] == 0 ? Pen.ICE : Pen.ROSE, (float) (1.0 - age / 6.0));
    }
}
