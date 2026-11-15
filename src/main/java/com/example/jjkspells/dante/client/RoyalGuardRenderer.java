package com.example.jjkspells.dante.client;

import com.example.jjkspells.dante.entity.RoyalGuardEntity;
import com.example.jjkspells.dante.spell.RoyalGuardSpell;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Draws "Royal Guard: Release": three gyroscope rings and a crest around the caster while the guard is up, a golden flash and ring on every
 * absorbed hit, an orb that swells with the stored power, then the release: everything converges, a pillar of fire rises and a huge
 * shockwave of rings, radial fire and embers sweeps out over the whole radius. Glow stays low: thin lines, rings and small glints.
 */
public class RoyalGuardRenderer extends EntityRenderer<RoyalGuardEntity> {
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final double R = RoyalGuardSpell.RADIUS;

    private Pen p;
    private float t;
    private Vec3 O;
    private Vec3 C;
    private Entity own;
    private RoyalGuardEntity ent;
    private int seed;
    private int ph;
    private double age;
    private double frac;

    public RoyalGuardRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(RoyalGuardEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(RoyalGuardEntity e) {
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

    private void line(Vec3 a, Vec3 b, double w, float al, float[] col) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.4, al * 0.45f, 0f, 1f, col);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.CORE);
    }

    @Override
    public void render(RoyalGuardEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        own = e.owner();
        if (t < 0 || own == null) {
            return;
        }
        ent = e;
        seed = e.getId() * 4421;
        O = own.getPosition(pt);
        C = O.add(0, 1.2, 0);
        ph = e.phase();
        age = t - e.phaseTick();
        frac = Math.min(1.0, e.stored() / RoyalGuardEntity.REF_STORED);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), e.getPosition(pt), camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        add(buf, DanteRender.MIST);
        mistPass();
        add(buf, DanteRender.RING);
        ringPass();
        add(buf, DanteRender.BEAM);
        pillarPass();
        add(buf, DanteRender.LINE);
        linePass();
        add(buf, DanteRender.GLINT);
        glintPass();
        add(buf, DanteRender.GLOW);
        glowPass();
    }

    private double blastAge() {
        return ph == RoyalGuardEntity.PH_RELEASE ? age - RoyalGuardEntity.CHARGE : -1;
    }

    private double guardOn() {
        double on = ease((ph == RoyalGuardEntity.PH_GUARD ? age : t) / 8.0);
        double w = blastAge();
        if (w > 0) {
            on *= 1.0 - c01(w / 8.0);
        }
        return on;
    }

    // ------------------------------------------------------------------ passes

    private void ringPass() {
        double on = guardOn();
        double k = ph == RoyalGuardEntity.PH_RELEASE ? c01(age / RoyalGuardEntity.CHARGE) : 0;
        double flick = (ph == RoyalGuardEntity.PH_GUARD && age > RoyalGuardSpell.GUARD_TICKS - 24 && ((int) (t / 2) % 2 == 0)) ? 0.5 : 1.0;
        if (on > 0.01) {
            for (int i = 0; i < 3; i++) {
                double th = t * (0.045 + 0.015 * i) * (1.0 + 3.0 * k) + i * 2.09;
                Vec3 H = new Vec3(Math.cos(th), 0, Math.sin(th));
                double half = (2.3 + 0.35 * i) * (1.0 - 0.45 * k);
                p.plane(C, Y, H, half, t * 0.03 * (i % 2 == 0 ? 1 : -1), i == 0 ? Pen.EMBER : Pen.GOLD, (float) (0.5 * on * flick));
            }
            double cr = 3.4 * (1.0 - 0.4 * k);
            p.plane(O.add(0, 0.08, 0), X, Z, cr, t * 0.05, Pen.EMBER, (float) (0.55 * on * flick));
            p.plane(O.add(0, 0.09, 0), X, Z, cr * 1.5, -t * 0.03, Pen.GOLD, (float) (0.3 * on * flick));
            p.plane(O.add(0, 0.1, 0), X, Z, cr * 0.55, t * 0.09, Pen.CORE, (float) (0.4 * on));
        }
        // flash ring of an absorbed hit
        double a = t - ent.hitTick();
        if (ph == RoyalGuardEntity.PH_GUARD && a >= 0 && a < 9) {
            Vec3 d = ent.hitDir();
            Vec3 pos = C.add(d.scale(1.9));
            Vec3 A = d.cross(Y);
            A = A.lengthSqr() < 1.0E-6 ? X : A.normalize();
            Vec3 B = d.cross(A).normalize();
            p.plane(pos, A, B, 0.8 + 2.6 * ease(a / 9.0), 0, Pen.GOLD, (float) (0.9 * (1 - a / 9.0)));
            p.plane(pos.add(d.scale(0.05)), A, B, 0.4 + 1.5 * ease(a / 7.0), 1.0, Pen.CORE, (float) (0.8 * (1 - a / 7.0)));
        }
        // the shockwave
        double w = blastAge();
        if (w >= 0 && w < RoyalGuardEntity.WAVE_TICKS + 6) {
            double fade = 1.0 - c01(w / (RoyalGuardEntity.WAVE_TICKS + 6.0));
            double r1 = R * ease(w / 12.0);
            double r2 = R * ease((w - 4) / 13.0);
            double r3 = R * 0.7 * ease((w - 2) / 11.0);
            p.plane(O.add(0, 0.12, 0), X, Z, r1 * 1.04, 0, Pen.CORE, (float) (0.9 * fade));
            p.plane(O.add(0, 0.13, 0), X, Z, r2, 1.0, Pen.EMBER, (float) (0.7 * fade));
            p.plane(O.add(0, 0.14, 0), X, Z, r3, 2.0, Pen.GOLD, (float) (0.6 * fade));
            p.plane(O.add(0, 0.15, 0), X, Z, r1 * 0.4, -w * 0.1, Pen.CORE, (float) (0.5 * fade));
            // vertical shock rings rising from the caster
            for (int i = 0; i < 3; i++) {
                double wr = w - i * 2;
                if (wr < 0 || wr > 16) {
                    continue;
                }
                p.plane(O.add(0, 0.5 + wr * 0.55, 0), X, Z, 3 + R * 0.45 * ease(wr / 16.0), 0, Pen.GOLD, (float) (0.5 * (1 - wr / 16.0)));
            }
        }
    }

    private void mistPass() {
        double w = blastAge();
        if (w < 0) {
            return;
        }
        double fade = ease(w / 6.0) * (1.0 - c01((w - 8) / 30.0));
        for (int i = 0; i < 3; i++) {
            p.plane(O.add(0, 0.06 + 0.4 * i, 0), X, Z, R * (0.95 - 0.1 * i), t * 0.01 * (i % 2 == 0 ? 1 : -1) + i * 1.7, Pen.BLOOD, (float) (fade * (0.3 - 0.07 * i)));
        }
    }

    private void pillarPass() {
        double w = blastAge();
        if (w < 0 || w > 26) {
            return;
        }
        double fade = 1.0 - c01(w / 26.0);
        double grow = ease(w / 3.0);
        Vec3 toCam = p.cam.subtract(O);
        Vec3 side = new Vec3(toCam.z, 0, -toCam.x);
        side = side.lengthSqr() < 1.0E-6 ? X : side.normalize();
        double[] widths = {11.0, 6.5, 2.8};
        float[][] cols = {Pen.EMBER, Pen.GOLD, Pen.WHITE};
        float[] als = {0.55f, 0.8f, 1.0f};
        double sc = (0.7 + 0.5 * frac) * (1.0 - 0.7 * c01(w / 26.0));
        int segs = 7;
        double h = 70.0 * grow;
        for (int l = 0; l < 3; l++) {
            for (int s = 0; s < segs; s++) {
                double y0 = h * s / segs;
                double y1 = h * (s + 1) / segs;
                double w0 = widths[l] * sc * (1.0 - 0.5 * s / segs);
                double w1 = widths[l] * sc * (1.0 - 0.5 * (s + 1.0) / segs);
                p.ribbonSide(O.add(0, y0, 0), O.add(0, y1, 0), side, w0, w1, (float) (als[l] * fade), (float) (als[l] * fade), 0f, 1f, cols[l]);
            }
        }
    }

    private void linePass() {
        Random r = new Random(seed + 5);
        // sparks of an absorbed hit
        double a = t - ent.hitTick();
        if (ph == RoyalGuardEntity.PH_GUARD && a >= 0 && a < 9) {
            Vec3 d = ent.hitDir();
            Vec3 pos = C.add(d.scale(1.9));
            Random q = new Random(seed + ent.hits() * 131L);
            for (int i = 0; i < 12; i++) {
                Vec3 dir = d.scale(0.8).add(q.nextGaussian() * 0.6, q.nextGaussian() * 0.6, q.nextGaussian() * 0.6).normalize();
                double e = ease(a / 9.0);
                line(pos.add(dir.scale(0.3 + 0.8 * e)), pos.add(dir.scale(0.9 + 3.0 * e)), 0.05, (float) (1 - a / 9.0), Pen.GOLD);
            }
        }
        // converging power
        if (ph == RoyalGuardEntity.PH_RELEASE && age < RoyalGuardEntity.CHARGE) {
            double k = age / RoyalGuardEntity.CHARGE;
            for (int i = 0; i < 34; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double el = (r.nextDouble() - 0.35) * 1.3;
                double d0 = (7 + r.nextDouble() * 7) * (1.0 - k) + 1.2;
                Vec3 d = new Vec3(Math.cos(ang) * Math.cos(el), Math.sin(el), Math.sin(ang) * Math.cos(el));
                line(C.add(d.scale(d0)), C.add(d.scale(d0 * 0.6 + 0.2)), 0.05, (float) (0.4 + 0.6 * k), r.nextBoolean() ? Pen.EMBER : Pen.GOLD);
            }
        }
        // crest spokes on the ground while guarding
        if (guardOn() > 0.01 && blastAge() < 0) {
            for (int i = 0; i < 8; i++) {
                double ang = t * 0.03 + i * Math.PI / 4;
                Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                line(O.add(d.scale(1.7)).add(0, 0.12, 0), O.add(d.scale(3.4)).add(0, 0.12, 0), 0.05, (float) (0.6 * guardOn()), Pen.EMBER);
            }
        }
        double w = blastAge();
        if (w >= 0 && w < RoyalGuardEntity.WAVE_TICKS + 6) {
            double fade = 1.0 - c01(w / (RoyalGuardEntity.WAVE_TICKS + 6.0));
            double rw = R * ease(w / 12.0);
            for (int i = 0; i < 48; i++) {
                double ang = (i + r.nextDouble() * 0.4) * Math.PI * 2 / 48;
                Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                line(O.add(d.scale(rw * 0.55)).add(0, 0.25, 0), O.add(d.scale(rw)).add(0, 0.25, 0), 0.1, (float) (0.9 * fade), Pen.EMBER);
            }
            for (int i = 0; i < 34; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = Math.sqrt(r.nextDouble()) * rw;
                double hgt = (4 + r.nextDouble() * 14) * ease(w / 10.0);
                Vec3 b = O.add(Math.cos(ang) * rad, 0.2, Math.sin(ang) * rad);
                line(b, b.add(0, hgt, 0), 0.08, (float) (0.8 * fade), r.nextBoolean() ? Pen.GOLD : Pen.EMBER);
            }
        }
    }

    private void glintPass() {
        double w = blastAge();
        double a = t - ent.hitTick();
        if (ph == RoyalGuardEntity.PH_GUARD && a >= 0 && a < 9) {
            Vec3 pos = C.add(ent.hitDir().scale(1.9));
            p.billboard(pos, 0.6 + 3.0 * (1 - a / 9.0), 0, Pen.WHITE, (float) (1 - a / 9.0));
        }
        if (ph == RoyalGuardEntity.PH_RELEASE && age < RoyalGuardEntity.CHARGE) {
            double k = age / RoyalGuardEntity.CHARGE;
            p.billboard(C, 0.6 + 2.6 * k * k, t * 0.2, Pen.CORE, (float) (0.4 + 0.6 * k));
        }
        if (w >= 0 && w < 40) {
            if (w < 9) {
                p.billboard(C, 15 * (1 - w / 9.0) + 1, 0, Pen.CORE, (float) (0.9 * (1 - w / 9.0)));
            }
            double rw = R * ease(w / 12.0);
            double fade = 1.0 - c01(w / 38.0);
            Random r = new Random(seed + 77);
            for (int i = 0; i < 56; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = Math.sqrt(r.nextDouble()) * rw;
                double y = 0.5 + ((r.nextDouble() * 12 + w * (0.2 + r.nextDouble() * 0.5)) % 14);
                p.billboard(O.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad), 0.3 + r.nextDouble() * 0.4, 0, Pen.GOLD, (float) (0.9 * fade));
            }
            if (w < 10) {
                AABB box = new AABB(O.x - R, O.y - 6, O.z - R, O.x + R, O.y + 14, O.z + R);
                for (LivingEntity t2 : ent.level().getEntitiesOfClass(LivingEntity.class, box)) {
                    if (t2 == own) {
                        continue;
                    }
                    double dx = t2.getX() - O.x;
                    double dz = t2.getZ() - O.z;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist <= rw) {
                        p.billboard(new Vec3(t2.getX(), t2.getY() + t2.getBbHeight() * 0.5, t2.getZ()), 2.6, 0, Pen.WHITE, (float) (1 - w / 10.0));
                    }
                }
            }
        }
    }

    private void glowPass() {
        double on = guardOn();
        if (blastAge() < 0 && on > 0.01) {
            double pulse = 0.5 + 0.5 * Math.sin(t * 0.25);
            p.billboard(C, 0.7 + 1.9 * frac + 0.15 * pulse, 0, Pen.GOLD, (float) (on * (0.25 + 0.3 * frac)));
            double a = t - ent.hitTick();
            if (a >= 0 && a < 8) {
                p.billboard(C.add(ent.hitDir().scale(1.9)), 2.2, 0, Pen.EMBER, (float) (0.5 * (1 - a / 8.0)));
            }
        }
        if (ph == RoyalGuardEntity.PH_RELEASE && age < RoyalGuardEntity.CHARGE) {
            double k = age / RoyalGuardEntity.CHARGE;
            p.billboard(C, 1.5 + 4.0 * k * k, 0, Pen.GOLD, (float) (0.3 + 0.35 * k));
        }
    }
}
