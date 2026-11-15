package com.example.jjkspells.dante.client;

import com.example.jjkspells.dante.entity.RoundTripEntity;
import com.example.jjkspells.dante.spell.RoundTripSpell;
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
 * Draws "Round Trip: Twin Rebellion": two giant Rebellion sprites spin flat like boomerang blades along their mirrored loops, each leaving a long
 * crimson ribbon trail with spinning crescents and a ring of embers; where they meet a slam ring and shards burst out, and on their return a big ring
 * of radial blades and shards sweeps the area. Glow stays low: thin lines, solid sprites, rings and small glints.
 */
public class RoundTripRenderer extends EntityRenderer<RoundTripEntity> {
    private static final int START = RoundTripEntity.START;
    private static final int TRIP = RoundTripEntity.TRIP;
    private static final int APEX = RoundTripEntity.APEX;
    private static final int HOME = RoundTripEntity.HOME;
    private static final int END = RoundTripEntity.END;
    private static final double ASPECT = 192.0 / 1024.0;
    private static final double LEN = 10.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final float[][] CELL = {{0f, 0f}, {0.5f, 0f}, {0f, 0.5f}, {0.5f, 0.5f}};

    private Pen p;
    private float t;
    private Vec3 A;
    private Vec3 D;
    private Vec3 O;
    private Entity own;
    private RoundTripEntity ent;
    private int seed;
    private double vis;

    public RoundTripRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(RoundTripEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(RoundTripEntity e) {
        return DanteRender.GLOW;
    }

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void add(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(DanteRender.additive(tex));
    }

    private void alpha(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(DanteRender.alpha(tex));
    }

    private void line(Vec3 a, Vec3 b, double w, float al, float[] col) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.4, al * 0.45f, 0f, 1f, col);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.CORE);
    }

    private Vec3 spos(int s, double tt) {
        return RoundTripEntity.swordPos(A, D, s, tt, O);
    }

    private double phi(int s, double tt) {
        return s * tt * 0.95 + (s > 0 ? 0 : 1.3);
    }

    @Override
    public void render(RoundTripEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        own = e.owner();
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        seed = e.getId() * 2713;
        A = e.getPosition(pt);
        D = e.dir();
        D = D.lengthSqr() < 1.0E-6 ? Z : D.normalize();
        O = own != null ? own.getPosition(pt) : A;
        vis = ease(t / START) * (1.0 - c01((t - HOME) / 6.0));
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), A, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        alpha(buf, DanteRender.SHARD);
        shardPass();
        alpha(buf, DanteRender.REBELLION);
        swordPass();
        add(buf, DanteRender.MIST);
        mistPass();
        add(buf, DanteRender.RING);
        ringPass();
        add(buf, DanteRender.BEAM);
        trailPass();
        add(buf, DanteRender.SLASH);
        slashPass();
        add(buf, DanteRender.LINE);
        linePass();
        add(buf, DanteRender.GLINT);
        glintPass();
    }

    // ------------------------------------------------------------------ passes

    private void swordPass() {
        if (vis <= 0.01) {
            return;
        }
        for (int s = -1; s <= 1; s += 2) {
            Vec3 c = spos(s, t);
            double ph = phi(s, t);
            Vec3 d = new Vec3(Math.cos(ph), 0, Math.sin(ph));
            Vec3 perp = new Vec3(-d.z, 0, d.x);
            double len = LEN * (0.5 + 0.5 * vis);
            Vec3 hilt = c.subtract(d.scale(len * 0.5));
            Vec3 tip = c.add(d.scale(len * 0.5));
            double w = len * ASPECT;
            float al = (float) vis;
            p.ribbonSide(hilt, tip, Y, w, w, al, al, 0f, 1f, Pen.WHITE);
            p.ribbonSide(hilt, tip, perp, w, w, al * 0.9f, al * 0.9f, 0f, 1f, Pen.WHITE);
        }
    }

    private void mistPass() {
        double a = t - APEX;
        if (a >= 0 && a < 40) {
            Vec3 g = spos(1, APEX);
            p.plane(new Vec3(g.x, A.y + 0.05, g.z), X, Z, RoundTripSpell.APEX_RADIUS * 0.9, a * 0.01, Pen.BLOOD, (float) (0.35 * ease(a / 5.0) * (1 - a / 40.0)));
        }
        double h = t - HOME;
        if (h >= 0 && h < 40) {
            p.plane(O.add(0, 0.05, 0), X, Z, RoundTripSpell.FINAL_RADIUS * 0.9, h * 0.01, Pen.BLOOD, (float) (0.35 * ease(h / 5.0) * (1 - h / 40.0)));
        }
    }

    private void ringPass() {
        if (t < START) {
            double k = ease(t / START);
            p.plane(O.add(0, 0.08, 0), X, Z, 5.0 * k, t * 0.2, Pen.EMBER, (float) (0.6 * k));
            p.plane(O.add(0, 0.1, 0), X, Z, 3.0 * k, -t * 0.3, Pen.CORE, (float) (0.4 * k));
        }
        if (vis > 0.01 && t >= START) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 c = spos(s, t);
                double ph = phi(s, t);
                p.plane(c, X, Z, 6.0, ph * 0.5, Pen.EMBER, (float) (0.45 * vis));
                p.plane(c.add(0, 0.02, 0), X, Z, 3.6, -ph, Pen.SCARLET, (float) (0.4 * vis));
            }
        }
        double a = t - APEX;
        if (a >= 0 && a < 26) {
            Vec3 g = spos(1, APEX);
            Vec3 gg = new Vec3(g.x, A.y + 0.1, g.z);
            double f = 1.0 - a / 26.0;
            double R = RoundTripSpell.APEX_RADIUS;
            p.plane(gg, X, Z, R * 1.05 * ease(a / 10.0), 0, Pen.CORE, (float) (0.9 * f));
            p.plane(gg.add(0, 0.01, 0), X, Z, R * 0.7 * ease((a - 2) / 10.0), 1.0, Pen.EMBER, (float) (0.7 * f));
            for (int i = 0; i < 3; i++) {
                double wr = a - i * 2.5;
                if (wr >= 0 && wr < 16) {
                    p.plane(gg.add(0, 0.5 + wr * 0.5, 0), X, Z, 2 + R * 0.6 * ease(wr / 16.0), 0, Pen.SCARLET, (float) (0.5 * (1 - wr / 16.0)));
                }
            }
        }
        double h = t - HOME;
        if (h >= 0 && h < 28) {
            double f = 1.0 - h / 28.0;
            double R = RoundTripSpell.FINAL_RADIUS;
            p.plane(O.add(0, 0.1, 0), X, Z, R * 1.05 * ease(h / 11.0), 0, Pen.CORE, (float) (0.9 * f));
            p.plane(O.add(0, 0.11, 0), X, Z, R * 0.75 * ease((h - 2) / 11.0), 1.0, Pen.EMBER, (float) (0.7 * f));
            p.plane(O.add(0, 0.12, 0), X, Z, R * 0.4 * ease((h - 4) / 10.0), 2.0, Pen.SCARLET, (float) (0.6 * f));
        }
    }

    private void trailPass() {
        if (t < START) {
            return;
        }
        int n = 18;
        double dt = 0.9;
        for (int s = -1; s <= 1; s += 2) {
            Vec3 prev = spos(s, t);
            for (int k = 1; k <= n; k++) {
                double tt = t - k * dt;
                if (tt < START) {
                    break;
                }
                Vec3 cur = spos(s, tt);
                double f = (double) k / n;
                double w = 3.4 * (1.0 - 0.8 * f);
                float al = (float) ((1.0 - f) * vis);
                p.ribbon(prev, cur, w * 1.8, al * 0.35f, 0f, 1f, Pen.SCARLET);
                p.ribbon(prev, cur, w, al * 0.7f, 0f, 1f, Pen.EMBER);
                p.ribbon(prev, cur, w * 0.35, al * 0.9f, 0f, 1f, Pen.CORE);
                prev = cur;
            }
        }
    }

    private void slashPass() {
        if (vis <= 0.01 || t < START) {
            return;
        }
        for (int s = -1; s <= 1; s += 2) {
            Vec3 c = spos(s, t);
            double ph = phi(s, t);
            Vec3 d = new Vec3(Math.cos(ph), 0, Math.sin(ph));
            Vec3 perp = new Vec3(-d.z, 0, d.x);
            // two flat crescents sweeping round the blade, lying in the plane of its spin
            p.plane(c, d, perp, 6.5, ph - 0.6, Pen.EMBER, (float) (0.7 * vis));
            p.plane(c.add(0, 0.02, 0), d, perp, 6.5, ph + Math.PI - 0.6, Pen.SCARLET, (float) (0.6 * vis));
        }
    }

    private void linePass() {
        Random r = new Random(seed + 7);
        if (t < START) {
            double k = t / START;
            for (int i = 0; i < 18; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double d0 = (4 + r.nextDouble() * 4) * (1 - k) + 1;
                Vec3 d = new Vec3(Math.cos(ang), (r.nextDouble() - 0.2), Math.sin(ang)).normalize();
                Vec3 c = O.add(0, 1.4, 0);
                line(c.add(d.scale(d0)), c.add(d.scale(d0 * 0.55)), 0.05, (float) (0.4 + 0.6 * k), Pen.EMBER);
            }
        }
        if (vis > 0.01 && t >= START) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 c = spos(s, t);
                for (int i = 0; i < 12; i++) {
                    double ang = r.nextDouble() * Math.PI * 2 + t * 0.2 * s;
                    double rr = 4 + r.nextDouble() * 4;
                    double h = (r.nextDouble() - 0.5) * 3;
                    Vec3 a = c.add(Math.cos(ang) * rr, h, Math.sin(ang) * rr);
                    Vec3 tg = new Vec3(-Math.sin(ang), 0, Math.cos(ang)).scale(s);
                    line(a, a.add(tg.scale(1.8 + r.nextDouble() * 1.6)), 0.05, (float) (0.8 * vis), Pen.GOLD);
                }
            }
        }
        double a = t - APEX;
        if (a >= 0 && a < 18) {
            Vec3 g = spos(1, APEX);
            Vec3 gg = new Vec3(g.x, A.y + 0.25, g.z);
            double f = 1.0 - a / 18.0;
            double rw = RoundTripSpell.APEX_RADIUS * ease(a / 9.0);
            for (int i = 0; i < 36; i++) {
                double ang = i * Math.PI * 2 / 36;
                Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                line(gg.add(d.scale(rw * 0.55)), gg.add(d.scale(rw)), 0.09, (float) (0.9 * f), Pen.EMBER);
            }
        }
        double h = t - HOME;
        if (h >= 0 && h < 26) {
            double f = 1.0 - h / 26.0;
            double rw = RoundTripSpell.FINAL_RADIUS * ease(h / 11.0);
            for (int i = 0; i < 48; i++) {
                double ang = i * Math.PI * 2 / 48;
                Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                line(O.add(d.scale(rw * 0.55)).add(0, 0.25, 0), O.add(d.scale(rw)).add(0, 0.25, 0), 0.1, (float) (0.9 * f), Pen.EMBER);
            }
            for (int i = 0; i < 16; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = Math.sqrt(r.nextDouble()) * rw;
                Vec3 b = O.add(Math.cos(ang) * rad, 0.2, Math.sin(ang) * rad);
                line(b, b.add(0, (3 + r.nextDouble() * 8) * ease(h / 8.0), 0), 0.07, (float) (0.8 * f), Pen.GOLD);
            }
        }
    }

    private void shardPass() {
        Vec3 g = spos(1, APEX);
        shards(new Vec3(g.x, A.y, g.z), t - APEX, 22, 1);
        shards(O, t - HOME, 28, 2);
    }

    private void shards(Vec3 c, double age, int n, int salt) {
        if (age < 0 || age > 42) {
            return;
        }
        Random r = new Random(seed + salt * 313L);
        double fade = 1.0 - c01((age - 26) / 16.0);
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double sp = 0.35 + r.nextDouble() * 1.1;
            double vy = 0.35 + r.nextDouble() * 0.6;
            double size = 0.5 + r.nextDouble() * 1.1;
            double spin = (r.nextDouble() - 0.5) * 0.4;
            int cell = r.nextInt(4);
            double hh = 0.4 + vy * age - 0.018 * age * age;
            Vec3 pos = c.add(Math.cos(ang) * sp * age, Math.max(0.2, hh), Math.sin(ang) * sp * age);
            p.billboard(pos, size, age * spin + i, Pen.WHITE, (float) (0.95 * fade), CELL[cell][0], CELL[cell][1], CELL[cell][0] + 0.5f, CELL[cell][1] + 0.5f);
        }
    }

    private void glintPass() {
        if (vis > 0.01) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 c = spos(s, t);
                p.billboard(c, 1.6 + 0.2 * Math.sin(t * 0.7 + s), 0, Pen.CORE, (float) (0.55 * vis));
            }
        }
        double a = t - APEX;
        if (a >= 0 && a < 8) {
            Vec3 g = spos(1, APEX);
            p.billboard(new Vec3(g.x, A.y + 1.4, g.z), 12 * (1 - a / 8.0) + 1, 0, Pen.CORE, (float) (0.9 * (1 - a / 8.0)));
        }
        double h = t - HOME;
        if (h >= 0 && h < 9) {
            p.billboard(O.add(0, 1.3, 0), 14 * (1 - h / 9.0) + 1, 0, Pen.CORE, (float) (0.9 * (1 - h / 9.0)));
        }
        if (t >= START && t < HOME + 4) {
            Random r = new Random(seed + 61 + (long) (t * 0.6));
            for (int s = -1; s <= 1; s += 2) {
                Vec3 c = spos(s, t);
                for (int i = 0; i < 8; i++) {
                    p.billboard(c.add((r.nextDouble() - 0.5) * 9, (r.nextDouble() - 0.5) * 3, (r.nextDouble() - 0.5) * 9), 0.25 + r.nextDouble() * 0.3, 0, Pen.GOLD, (float) (0.9 * vis));
                }
            }
        }
        if (t < START) {
            p.billboard(O.add(0, 1.4, 0), 0.6 + 0.2 * t, t * 0.2, Pen.CORE, (float) (0.3 + 0.05 * t));
        }
    }
}
