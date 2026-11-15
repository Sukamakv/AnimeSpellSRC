package com.example.jjkspells.yasuo.client;

import com.example.jjkspells.yasuo.entity.SweepingBladeEntity;
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
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws "Sweeping Blade": each dash leaves a streaking wind trail, a row of fading blue afterimages of Yasuo, a gust ring where he vanished and, at the
 * cut, a spectral katana sweep, a crossing pair of slash lines, radial sparks and an expanding ring. Glow stays low: thin lines and solid sprites.
 */
public class SweepingBladeRenderer extends EntityRenderer<SweepingBladeEntity> {
    private static final int END = SweepingBladeEntity.END;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final int IMAGES = 5;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private final PlayerGhost ghost;
    private Vec3 A;
    private PoseStack ps;
    private MultiBufferSource buf;
    private Entity own;
    private float t;
    private Vector3f left;

    public SweepingBladeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.ghost = new PlayerGhost(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(SweepingBladeEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SweepingBladeEntity e) {
        return YasuoRender.GLOW;
    }

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void add(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(YasuoRender.additive(tex));
    }

    private void alpha(MultiBufferSource buf, ResourceLocation tex) {
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

    private double age(SweepingBladeEntity e, int[] d, float pt) {
        return (e.tickCount - 1 + pt) - d[6];
    }

    @Override
    public void render(SweepingBladeEntity e, float yaw, float pt, PoseStack ps0, MultiBufferSource buf0, int light) {
        this.ps = ps0;
        this.buf = buf0;
        PoseStack ps = ps0;
        MultiBufferSource buf = buf0;
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        List<int[]> ds = e.dashes();
        if (ds.isEmpty()) {
            return;
        }
        A = e.getPosition(pt);
        own = e.owner();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        left = camera.getLeftVector();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        for (int[] d : ds) {
            afterimages(e, d, pt);
        }
        alpha(buf, YasuoRender.KATANA);
        for (int[] d : ds) {
            katana(e, d, pt);
        }
        add(buf, YasuoRender.WIND);
        for (int[] d : ds) {
            wind(e, d, pt);
        }
        add(buf, YasuoRender.MIST);
        for (int[] d : ds) {
            mist(e, d, pt);
        }
        add(buf, YasuoRender.RING);
        for (int[] d : ds) {
            rings(e, d, pt);
        }
        add(buf, YasuoRender.LINE);
        for (int[] d : ds) {
            lines(e, d, pt);
        }
        add(buf, YasuoRender.GLINT);
        for (int[] d : ds) {
            glints(e, d, pt);
        }
    }

    // ------------------------------------------------------------------ passes

    private void afterimages(SweepingBladeEntity e, int[] d, float pt) {
        double a = age(e, d, pt);
        if (a < 0 || a > 26) {
            return;
        }
        Vec3 s = pt(d, 0);
        Vec3 en = pt(d, 3);
        Vec3 dir = en.subtract(s);
        if (dir.lengthSqr() < 1.0E-6) {
            return;
        }
        double yaw = PlayerGhost.yawOf(dir.normalize());
        boolean slim = PlayerGhost.slimOf(own);
        for (int j = 0; j < IMAGES; j++) {
            double f = (j + 0.5) / IMAGES;
            double life = 8.0 + 14.0 * (1.0 - f * 0.5);
            double aj = a - (1.0 - f) * 1.5;
            if (aj < 0 || aj > life) {
                continue;
            }
            float al = (float) (0.55 * (1.0 - aj / life) * (0.5 + 0.5 * f));
            Vec3 c = s.add(dir.scale(f)).subtract(A);
            ghost.silhouette(ps, buf, slim, c, yaw, PlayerGhost.Pose.lunge(j % 2 == 0 ? 1f : -1f), j % 2 == 0 ? Pen.AQUA : Pen.SKY, al);
        }
    }

    private void katana(SweepingBladeEntity e, int[] d, float pt) {
        double a = age(e, d, pt);
        if (a < 0 || a > 6) {
            return;
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

    private void wind(SweepingBladeEntity e, int[] d, float pt) {
        double a = age(e, d, pt);
        if (a < 0 || a > 16) {
            return;
        }
        Vec3 s = pt(d, 0).add(0, 1.0, 0);
        Vec3 en = pt(d, 3).add(0, 1.0, 0);
        float al = (float) (0.4 * (1.0 - a / 16.0));
        double w = 2.6 - 1.4 * c01(a / 16.0);
        p.ribbon(s, en, w * 0.3, w, al * 0.3f, al, 0f, 1f, Pen.PALE);
        Vec3 up = s.add(0, 0.5, 0);
        Vec3 up2 = en.add(0, 0.5, 0);
        p.ribbon(up, up2, w * 0.2, w * 0.7, al * 0.2f, al * 0.6f, 0.2f, 0.8f, Pen.AQUA);
    }

    private void mist(SweepingBladeEntity e, int[] d, float pt) {
        double a = age(e, d, pt);
        if (a < 0 || a > 14) {
            return;
        }
        float al = (float) (0.22 * (1.0 - a / 14.0));
        Vec3 s = pt(d, 0).add(0, 0.8, 0);
        Vec3 en = pt(d, 3).add(0, 0.8, 0);
        p.billboard(s, 1.8 + 2.2 * ease(a / 8.0), a * 0.1, Pen.PALE, al);
        p.billboard(en, 1.6 + 2.0 * ease(a / 8.0), -a * 0.1, Pen.PALE, al);
    }

    private void rings(SweepingBladeEntity e, int[] d, float pt) {
        double a = age(e, d, pt);
        if (a < 0 || a > 12) {
            return;
        }
        Vec3 s = pt(d, 0);
        Vec3 en = pt(d, 3);
        Vec3 dn = en.subtract(s).normalize();
        Vec3 side = new Vec3(-dn.z, 0, dn.x);
        double k = ease(a / 9.0);
        float al = (float) (1.0 - a / 12.0);
        // gust ring on the ground where he vanished
        p.plane(s.add(0, 0.15, 0), X, Z, 0.8 + 4.0 * k, a * 0.2, Pen.PALE, al * 0.7f);
        // vertical ring across the path at the cut
        p.plane(en.add(0, 1.0, 0), side, Y, 0.8 + 4.5 * k, 0, Pen.AQUA, al * 0.8f);
        p.plane(en.add(0, 1.0, 0), side, Y, 0.5 + 2.8 * k, a * 0.3, Pen.WHITE, al * 0.5f);
    }

    private void lines(SweepingBladeEntity e, int[] d, float pt) {
        double a = age(e, d, pt);
        if (a < 0 || a > 16) {
            return;
        }
        Vec3 s = pt(d, 0);
        Vec3 en = pt(d, 3);
        Vec3 dn = en.subtract(s).normalize();
        Vec3 side = new Vec3(-dn.z, 0, dn.x);
        // main streak with two thin companions
        float al = (float) (1.0 - a / 16.0);
        Vec3 a0 = s.add(0, 1.0, 0);
        Vec3 a1 = en.add(0, 1.0, 0);
        line(a0, a1, 0.16 * (1.0 - c01(a / 16.0) * 0.6), al * 0.9f, Pen.AQUA);
        for (int i = -1; i <= 1; i += 2) {
            double wob = 0.25 * Math.sin(t * 0.8 + i);
            Vec3 off = new Vec3(0, 0.5 * i + wob, 0).add(side.scale(0.25 * i));
            line(a0.add(off), a1.add(off), 0.06, al * 0.6f, Pen.PALE);
        }
        // crossing slash lines through the cut point
        if (a < 10) {
            double k = ease(a / 3.0);
            float al2 = (float) (1.0 - a / 10.0);
            Vec3 c = en.add(0, 1.0, 0);
            for (int i = 0; i < 2; i++) {
                double ang = (i == 0 ? 0.6 : -0.6);
                Vec3 v = side.scale(Math.cos(ang)).add(0, Math.sin(ang), 0).scale(3.2 * k);
                line(c.subtract(v), c.add(v), 0.1, al2, Pen.WHITE);
            }
            for (int i = 0; i < 10; i++) {
                double an = i * Math.PI / 5 + d[6];
                Vec3 r = side.scale(Math.cos(an)).add(0, Math.sin(an), 0);
                line(c.add(r.scale(1.2 + 3.0 * k)), c.add(r.scale(2.0 + 5.0 * k)), 0.05, al2 * 0.8f, Pen.AQUA);
            }
        }
    }

    private void glints(SweepingBladeEntity e, int[] d, float pt) {
        double a = age(e, d, pt);
        if (a < 0 || a > 12) {
            return;
        }
        Vec3 s = pt(d, 0);
        Vec3 en = pt(d, 3);
        Vec3 dir = en.subtract(s);
        float al = (float) (1.0 - a / 12.0);
        p.billboard(en.add(0, 1.0, 0), 1.0 + 0.8 * ease(a / 4.0), a * 0.2, Pen.WHITE, al * 0.9f);
        p.billboard(s.add(0, 1.0, 0), 0.9, 0, Pen.WHITE, al * 0.6f);
        for (int i = 0; i < 12; i++) {
            double f = (i * 0.618 + d[6] * 0.13) % 1.0;
            double up = 0.4 + 1.4 * ((i * 0.37 + d[6] * 0.21) % 1.0) + a * 0.04;
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 0.5 + i * 2.1));
            p.billboard(s.add(dir.scale(f)).add(0, up, 0), 0.17, a * 0.2 + i, Pen.WHITE, al * 0.7f * tw);
        }
    }
}
