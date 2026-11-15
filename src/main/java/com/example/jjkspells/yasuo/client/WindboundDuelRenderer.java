package com.example.jjkspells.yasuo.client;

import com.example.jjkspells.yasuo.entity.WindboundDuelEntity;
import com.example.jjkspells.yasuo.spell.WindboundDuelSpell;
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
 * Draws "Windbound Duel": a cylinder of streaked wind (two layers spinning in opposite directions) rises around the arena, with glowing rim rings, a rotating
 * floor circle, swirl ribbons, drifting debris and a gust that rushes inward when it closes. The chosen enemy wears a rotating mark of rings above the head
 * and a thin line links the two duellists.
 */
public class WindboundDuelRenderer extends EntityRenderer<WindboundDuelEntity> {
    private static final int RISE = WindboundDuelEntity.RISE;
    private static final int FADE = WindboundDuelEntity.FADE;
    private static final double H = WindboundDuelSpell.HEIGHT;
    private static final int SEG = 40;
    private static final int ROWS = 6;
    private static final float DU = 0.25f;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 C;
    private double R;
    private double rise;
    private double fade;
    private WindboundDuelEntity ent;

    public WindboundDuelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(WindboundDuelEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(WindboundDuelEntity e) {
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

    private Vec3 ring(double ang, double rad, double y) {
        return C.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
    }

    @Override
    public void render(WindboundDuelEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        int end = e.endTick();
        if (t < 0 || (end >= 0 && t > end + FADE)) {
            return;
        }
        ent = e;
        C = e.getPosition(pt);
        R = e.radius();
        rise = ease(t / RISE);
        fade = end >= 0 ? 1.0 - c01((t - end) / FADE) : 1.0;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), C, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        add(buf, YasuoRender.WIND);
        walls();
        add(buf, YasuoRender.MIST);
        mist();
        add(buf, YasuoRender.RING);
        rings();
        add(buf, YasuoRender.LINE);
        lines();
        add(buf, YasuoRender.GLINT);
        glints();
    }

    // ------------------------------------------------------------------ passes

    private void walls() {
        double h = H * rise;
        for (int k = 0; k < 2; k++) {
            double rad = R - k * 0.45;
            double spin = (k == 0 ? 1 : -1) * t * 0.025;
            float[] col = k == 0 ? Pen.PALE : Pen.AQUA;
            double scroll = t * 0.03 * (k == 0 ? 1 : -1) + k * 0.5;
            for (int i = 0; i < SEG; i++) {
                double a0 = spin + Math.PI * 2 * i / SEG;
                double a1 = spin + Math.PI * 2 * (i + 1) / SEG;
                double fr = ((scroll + i * 0.37) % 1.0 + 1.0) % 1.0;
                float u0 = (float) (fr * (1 - DU));
                float u1 = u0 + DU;
                for (int j = 0; j < ROWS; j++) {
                    double f0 = (double) j / ROWS;
                    double f1 = (double) (j + 1) / ROWS;
                    float al = (float) (0.28 * fade * (0.5 + 0.5 * (1 - f0)));
                    double tw = 0.35 * Math.sin(t * 0.09 + f0 * 4 + i * 0.4 + k);
                    double r0 = rad + tw * f0;
                    double r1 = rad + 0.35 * Math.sin(t * 0.09 + f1 * 4 + i * 0.4 + k) * f1;
                    p.quad(ring(a0, r0, h * f0), ring(a1, r0, h * f0), ring(a1, r1, h * f1), ring(a0, r1, h * f1),
                            u0, (float) f0, u1, (float) f1, col, al);
                }
            }
        }
    }

    private void mist() {
        for (int i = 0; i < 14; i++) {
            double a = Math.PI * 2 * i / 14 + t * 0.012;
            Vec3 c = ring(a, R - 0.5, 0.7 + 0.4 * Math.sin(t * 0.2 + i));
            p.billboard(c, 2.2, t * 0.02 + i, Pen.PALE, (float) (0.10 * fade * rise));
        }
        // closing burst
        if (t < 14) {
            double k = ease(t / 10.0);
            p.plane(C.add(0, 0.2, 0), X, Z, R * k, t * 0.1, Pen.PALE, (float) (0.5 * (1.0 - t / 14.0)));
        }
    }

    private void rings() {
        double a = fade * rise;
        // floor circle
        p.plane(C.add(0, 0.12, 0), X, Z, R + 0.6, t * 0.02, Pen.AQUA, (float) (0.55 * a));
        p.plane(C.add(0, 0.14, 0), X, Z, R * 0.55, -t * 0.03, Pen.PALE, (float) (0.3 * a));
        p.plane(C.add(0, 0.16, 0), X, Z, R * 0.2, t * 0.05, Pen.WHITE, (float) (0.25 * a));
        // shock ring while it closes
        if (t < 14) {
            double k = ease(t / 10.0);
            p.plane(C.add(0, 0.3, 0), X, Z, (R + 3.0) * k, 0, Pen.WHITE, (float) (0.8 * (1.0 - t / 14.0)));
        }
        // mark on the chosen enemy
        Entity tg = ent.target();
        if (tg != null && ent.endTick() < 0 && t > 2) {
            Vec3 m = tg.getPosition(1.0f).add(0, tg.getBbHeight() + 0.9, 0).subtract(0, 0, 0);
            p.plane(m, X, Z, 0.9, t * 0.12, Pen.AQUA, (float) (0.9 * a));
            p.plane(m.add(0, 0.25, 0), X, Z, 0.6, -t * 0.18, Pen.WHITE, (float) (0.7 * a));
            p.plane(tg.getPosition(1.0f).add(0, 0.1, 0), X, Z, 1.6 + 0.2 * Math.sin(t * 0.3), t * 0.08, Pen.AQUA, (float) (0.6 * a));
        }
    }

    private void lines() {
        double a = fade * rise;
        double h = H * rise;
        // rim lines
        for (int lvl = 0; lvl < 2; lvl++) {
            double y = lvl == 0 ? 0.1 : h;
            for (int i = 0; i < 48; i++) {
                double a0 = Math.PI * 2 * i / 48;
                double a1 = Math.PI * 2 * (i + 1) / 48;
                double wob = lvl == 0 ? 0 : 0.35 * Math.sin(t * 0.1 + i * 0.5);
                line(ring(a0, R, y + wob), ring(a1, R, y + (lvl == 0 ? 0 : 0.35 * Math.sin(t * 0.1 + (i + 1) * 0.5))), 0.07, (float) (0.75 * a), Pen.AQUA);
            }
        }
        // swirling ribbons climbing the wall
        for (int i = 0; i < 12; i++) {
            double base = Math.PI * 2 * i / 12 + t * 0.03;
            Vec3 prev = null;
            for (int j = 0; j <= 12; j++) {
                double f = j / 12.0;
                Vec3 cur = ring(base + f * 1.3, R - 0.2 + 0.3 * Math.sin(t * 0.15 + f * 5 + i), h * f);
                if (prev != null) {
                    line(prev, cur, 0.05, (float) (0.5 * a * (1 - f * 0.5)), Pen.PALE);
                }
                prev = cur;
            }
        }
        // link between the duellists
        Entity tg = ent.target();
        Entity ow = ent.owner();
        if (tg != null && ow != null && ent.endTick() < 0 && t > RISE) {
            Vec3 s = ow.getPosition(1.0f).add(0, 1.0, 0);
            Vec3 e = tg.getPosition(1.0f).add(0, 1.0, 0);
            Vec3 d = e.subtract(s);
            Vec3 prev = s;
            for (int j = 1; j <= 12; j++) {
                double f = j / 12.0;
                Vec3 cur = s.add(d.scale(f)).add(0, 0.25 * Math.sin(t * 0.4 + f * 9), 0);
                line(prev, cur, 0.035, (float) (0.35 * a), Pen.AQUA);
                prev = cur;
            }
        }
        // inward gust lines while closing
        if (t < 14) {
            Random r = new Random(ent.getId() * 17L);
            for (int i = 0; i < 28; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double y = r.nextDouble() * H * 0.6;
                double d0 = R * (1.1 - 0.9 * ease((t + r.nextDouble() * 3) / 14.0));
                float al = (float) (1.0 - t / 14.0);
                line(ring(ang, d0 + 2.5, y), ring(ang, d0, y), 0.06, al * 0.7f, Pen.WHITE);
            }
        }
    }

    private void glints() {
        double a = fade * rise;
        Random r = new Random(ent.getId() * 977L);
        for (int i = 0; i < 36; i++) {
            double ang = r.nextDouble() * Math.PI * 2 + t * 0.02 * (1 + r.nextDouble());
            double rad = R * (0.3 + 0.7 * r.nextDouble());
            double y = H * ((r.nextDouble() + t * 0.008 * (1 + r.nextDouble())) % 1.0) * rise;
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 0.4 + i * 2.3));
            p.billboard(ring(ang, rad, y), 0.2, t * 0.05, Pen.WHITE, (float) (0.6 * a * tw));
        }
        Entity tg = ent.target();
        if (tg != null && ent.endTick() < 0 && t > 2) {
            Vec3 m = tg.getPosition(1.0f).add(0, tg.getBbHeight() + 0.9, 0);
            p.billboard(m, 0.5, t * 0.1, Pen.WHITE, (float) (0.8 * a));
        }
    }
}
