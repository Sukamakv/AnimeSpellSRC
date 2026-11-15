package com.example.jjkspells.yasuo.client;

import com.example.jjkspells.yasuo.entity.WindWallEntity;
import com.example.jjkspells.yasuo.spell.WindWallSpell;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * Draws "Wind Wall": two translucent sheets of streaked wind (opposite scroll directions) rise from the ground into a huge wavy wall, edged with thin bright
 * lines, swirl ribbons and ground dust. Every destroyed projectile leaves a ripple: an expanding ring in the wall plane, radial streaks and a glint.
 */
public class WindWallRenderer extends EntityRenderer<WindWallEntity> {
    private static final int RISE = WindWallEntity.RISE;
    private static final int DURATION = WindWallEntity.DURATION;
    private static final int END = WindWallEntity.END;
    private static final double HW = WindWallSpell.HALF_WIDTH;
    private static final double H = WindWallSpell.HEIGHT;
    private static final int SX = 26;
    private static final int SY = 8;
    private static final float DU = 0.25f;
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 D;
    private Vec3 S;
    private WindWallEntity ent;
    private double rise;
    private double fade;

    public WindWallRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(WindWallEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(WindWallEntity e) {
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

    private void line(Vec3 a, Vec3 b, double w, float al, float[] col) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.4, al * 0.45f, 0f, 1f, col);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.CORE);
    }

    private Vec3 at(double x, double y, double off) {
        return A.add(S.scale(x)).add(0, y, 0).add(D.scale(off));
    }

    /** Wall height at lateral position x. */
    private double top(double x) {
        return H * rise * (0.9 + 0.1 * Math.sin(x * 0.5 + t * 0.12));
    }

    @Override
    public void render(WindWallEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        A = e.getPosition(pt);
        D = e.dir();
        D = D.lengthSqr() < 1.0E-6 ? Z : D.normalize();
        S = new Vec3(D.z, 0, -D.x);
        rise = ease(t / RISE);
        fade = t > DURATION ? 1.0 - (t - DURATION) / (END - DURATION) : 1.0;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        add(buf, YasuoRender.WIND);
        sheets();
        add(buf, YasuoRender.MIST);
        mist();
        add(buf, YasuoRender.RING);
        ripples();
        add(buf, YasuoRender.LINE);
        lines();
        add(buf, YasuoRender.GLINT);
        glints();
    }

    // ------------------------------------------------------------------ passes

    private void sheets() {
        for (int k = 0; k < 2; k++) {
            double off = k == 0 ? -0.35 : 0.35;
            float[] col = k == 0 ? Pen.PALE : Pen.AQUA;
            double dirn = k == 0 ? 1 : -1;
            double scroll = t * 0.035 * dirn + k * 0.5;
            for (int i = 0; i < SX; i++) {
                double xa = -HW + 2 * HW * i / SX;
                double xb = -HW + 2 * HW * (i + 1) / SX;
                double xm = (xa + xb) * 0.5;
                double edge = 1.0 - Math.pow(Math.abs(xm) / HW, 4);
                double fr = ((scroll + i * 0.37) % 1.0 + 1.0) % 1.0;
                float u0 = (float) (fr * (1 - DU));
                float u1 = u0 + DU;
                double ha = top(xa);
                double hb = top(xb);
                for (int j = 0; j < SY; j++) {
                    double f0 = (double) j / SY;
                    double f1 = (double) (j + 1) / SY;
                    float al = (float) (0.30 * edge * fade * (0.55 + 0.45 * (1 - f0)));
                    if (al <= 0.01f) {
                        continue;
                    }
                    double sway = 0.5 * Math.sin(t * 0.1 + f0 * 4 + k * 2);
                    p.quad(at(xa, ha * f0, off + sway * f0), at(xb, hb * f0, off + sway * f0),
                            at(xb, hb * f1, off + sway * f1), at(xa, ha * f1, off + sway * f1),
                            u0, (float) f0, u1, (float) f1, col, al);
                }
            }
        }
    }

    private void mist() {
        for (int i = 0; i < 16; i++) {
            double x = -HW + 2 * HW * (i + 0.5) / 16;
            double drift = (t * 0.03 + i * 0.173) % 1.0;
            double edge = 1.0 - Math.pow(Math.abs(x) / HW, 4);
            Vec3 c = at(x, 0.6 + 0.5 * Math.sin(t * 0.2 + i), (drift - 0.5) * 3.0);
            p.billboard(c, 2.4 + 0.6 * Math.sin(i * 1.7), t * 0.02 + i, Pen.PALE, (float) (0.10 * edge * fade * rise));
        }
    }

    private void ripples() {
        List<int[]> bl = ent.blocks();
        for (int[] b : bl) {
            double age = ent.tickCount - b[3] + (t - (ent.tickCount - 1));
            if (age < 0 || age > 14) {
                continue;
            }
            Vec3 c = new Vec3(b[0] / 10.0, b[1] / 10.0, b[2] / 10.0);
            double k = ease(age / 10.0);
            float al = (float) (1.0 - age / 14.0);
            p.plane(c, S, Y, 1.0 + 5.0 * k, 0, Pen.AQUA, al * 0.8f);
            p.plane(c, S, Y, 0.6 + 3.0 * k, age * 0.3, Pen.WHITE, al * 0.5f);
        }
    }

    private void lines() {
        double a = fade * rise;
        // frame
        for (int i = 0; i < 24; i++) {
            double x0 = -HW + 2 * HW * i / 24;
            double x1 = -HW + 2 * HW * (i + 1) / 24;
            double e0 = 1.0 - Math.pow(Math.abs(x0) / HW, 4);
            line(at(x0, top(x0), 0), at(x1, top(x1), 0), 0.07, (float) (0.8 * a * e0), Pen.AQUA);
            line(at(x0, 0.1, 0), at(x1, 0.1, 0), 0.07, (float) (0.7 * a * e0), Pen.AQUA);
        }
        line(at(-HW, 0, 0), at(-HW, top(-HW), 0), 0.07, (float) (0.5 * a), Pen.AQUA);
        line(at(HW, 0, 0), at(HW, top(HW), 0), 0.07, (float) (0.5 * a), Pen.AQUA);
        // swirling vertical ribbons
        for (int i = 0; i < 14; i++) {
            double x = -HW + 2 * HW * (i + 0.5) / 14;
            double ph = i * 1.9;
            double edge = 1.0 - Math.pow(Math.abs(x) / HW, 4);
            Vec3 prev = null;
            for (int j = 0; j <= 10; j++) {
                double f = j / 10.0;
                double y = top(x) * f;
                double off = 0.5 * Math.sin(t * 0.18 + f * 6 + ph);
                double xx = x + 0.7 * Math.sin(t * 0.13 + f * 4 + ph * 0.7);
                Vec3 cur = at(xx, y, off);
                if (prev != null) {
                    line(prev, cur, 0.045, (float) (0.5 * a * edge * (1 - f * 0.5)), Pen.PALE);
                }
                prev = cur;
            }
        }
        // initial gust speed lines
        if (t < 14) {
            Random r = new Random(ent.getId() * 31L);
            for (int i = 0; i < 26; i++) {
                double x = (r.nextDouble() * 2 - 1) * HW;
                double y = r.nextDouble() * H * 0.8;
                double d0 = -3.0 + t * 0.9 + r.nextDouble() * 2.0;
                float al = (float) (1.0 - t / 14.0);
                line(at(x, y, d0), at(x, y, d0 + 3.0), 0.06, al * 0.7f, Pen.WHITE);
            }
        }
        // ripple radial streaks
        for (int[] b : ent.blocks()) {
            double age = ent.tickCount - b[3] + (t - (ent.tickCount - 1));
            if (age < 0 || age > 10) {
                continue;
            }
            Vec3 c = new Vec3(b[0] / 10.0, b[1] / 10.0, b[2] / 10.0);
            double k = ease(age / 10.0);
            float al = (float) (1.0 - age / 10.0);
            for (int i = 0; i < 10; i++) {
                double an = i * Math.PI / 5 + b[3];
                Vec3 dir = S.scale(Math.cos(an)).add(0, Math.sin(an), 0);
                line(c.add(dir.scale(1.0 + 3.0 * k)), c.add(dir.scale(2.0 + 6.0 * k)), 0.06, al * 0.8f, Pen.WHITE);
            }
        }
    }

    private void glints() {
        double a = fade * rise;
        Random r = new Random(ent.getId() * 977L);
        for (int i = 0; i < 30; i++) {
            double x = (r.nextDouble() * 2 - 1) * HW * 0.95;
            double f = r.nextDouble();
            double y = top(x) * ((f + t * 0.01 * (1 + r.nextDouble())) % 1.0);
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 0.4 + i * 2.3));
            p.billboard(at(x, y, 0), 0.22, t * 0.05, Pen.WHITE, (float) (0.6 * a * tw));
        }
        for (int[] b : ent.blocks()) {
            double age = ent.tickCount - b[3] + (t - (ent.tickCount - 1));
            if (age < 0 || age > 6) {
                continue;
            }
            p.billboard(new Vec3(b[0] / 10.0, b[1] / 10.0, b[2] / 10.0), 0.8 + 0.4 * age, age, Pen.WHITE, (float) (1.0 - age / 6.0));
        }
    }
}
