package com.example.jjkspells.vergil.client;

import com.example.jjkspells.vergil.entity.DevilTriggerEntity;
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
import java.util.Random;

/**
 * Draws "Devil Trigger": a 30 block aura seal turns on the ground, shards orbit the caster, and every phantom slash links the caster to its victims with
 * a thin line, a crescent and slice lines. The form ends with a burst of glass. Glow stays minimal.
 */
public class DevilTriggerRenderer extends EntityRenderer<DevilTriggerEntity> {
    private static final int RISE = DevilTriggerEntity.RISE;
    private static final int SLASH0 = DevilTriggerEntity.SLASH0;
    private static final int STEP = DevilTriggerEntity.STEP;
    private static final int DURATION = DevilTriggerEntity.DURATION;
    private static final int END = DevilTriggerEntity.END;
    private static final double AURA = 30.0;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private Vec3 E;
    private int seed;
    private DevilTriggerEntity ent;
    private double rise;
    private double fadeOut;

    public DevilTriggerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(DevilTriggerEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(DevilTriggerEntity e) {
        return VergilRender.GLOW;
    }

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void add(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(VergilRender.additive(tex));
    }

    private void alpha(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(VergilRender.alpha(tex));
    }

    private void line(Vec3 a, Vec3 b, double w, float al, boolean violet) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.6, al * 0.45f, 0f, 1f, violet ? Pen.VIOLET : Pen.CYAN);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.CORE);
    }

    @Override
    public void render(DevilTriggerEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        if (t < 0 || t > END) {
            return;
        }
        ent = e;
        seed = e.getId() * 3571;
        Entity own = e.owner();
        E = own != null ? own.getPosition(pt) : e.getPosition(pt);
        rise = ease((t - 4) / 20.0);
        fadeOut = 1.0 - c01((t - DURATION - 6) / 30.0);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), E, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        alpha(buf, VergilRender.SHARD);
        shardPass();
        add(buf, VergilRender.MIST);
        mistPass();
        add(buf, VergilRender.RING);
        ringPass();
        add(buf, VergilRender.LINE);
        linePass();
        add(buf, VergilRender.SLASH);
        slashPass();
        add(buf, VergilRender.GLINT);
        glintPass();
    }

    private Vec3 targetCentre(int id) {
        Entity en = ent.level().getEntity(id);
        if (en == null || !en.isAlive()) {
            return null;
        }
        return new Vec3(en.getX(), en.getY() + en.getBbHeight() * 0.5, en.getZ());
    }

    private void linePass() {
        List<int[]> sl = ent.slashes();
        Vec3 chest = E.add(0, 1.2, 0);
        for (int k = 0; k < sl.size(); k++) {
            double age = t - (SLASH0 + k * STEP);
            if (age < 0 || age > 9) {
                continue;
            }
            float al = (float) (1.0 - age / 9.0);
            for (int id : sl.get(k)) {
                Vec3 c = targetCentre(id);
                if (c == null) {
                    continue;
                }
                Vec3 head = chest.add(c.subtract(chest).scale(ease(age / 2.0)));
                line(chest, head, 0.07, al * 0.7f, false);
                Random r = new Random(seed + id * 131L + k * 17L);
                for (int s = 0; s < 2; s++) {
                    Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                    double l = 6.0 * ease(age / 2.0);
                    line(c.subtract(d.scale(l)), c.add(d.scale(l)), 0.06, al, s == 1 && k % 3 == 0);
                }
            }
        }
        if (t >= DURATION - 2 && t < DURATION + 16) {
            float al = (float) (1.0 - c01((t - DURATION) / 16.0));
            line(E, E.add(0, 40, 0), 0.4, al, false);
        }
    }

    private void slashPass() {
        List<int[]> sl = ent.slashes();
        for (int k = 0; k < sl.size(); k++) {
            double age = t - (SLASH0 + k * STEP);
            if (age < 0 || age > 5) {
                continue;
            }
            float al = (float) (1.0 - age / 5.0);
            Random r = new Random(seed + k * 977L);
            for (int id : sl.get(k)) {
                Vec3 c = targetCentre(id);
                if (c != null) {
                    p.billboard(c, 2.8, r.nextDouble() * 6.28, Pen.ICE, al, 0f, 0f, 1f, 1f);
                }
            }
        }
    }

    private void glintPass() {
        double on = rise * fadeOut;
        for (int i = 0; i < 30; i++) {
            Random r = new Random(seed + i * 53L + 9);
            double ang = r.nextDouble() * 6.28;
            double rad = Math.sqrt(r.nextDouble()) * AURA * 0.6;
            Vec3 at = E.add(Math.cos(ang) * rad, 0.3 + r.nextDouble() * 10.0, Math.sin(ang) * rad);
            double tw = 0.5 + 0.5 * Math.sin(t * (0.25 + r.nextDouble() * 0.3) + r.nextDouble() * 6.28);
            p.billboard(at, 0.3 + 0.3 * tw, 0, Pen.WHITE, (float) (on * tw * 0.8));
        }
        List<int[]> sl = ent.slashes();
        for (int k = 0; k < sl.size(); k++) {
            double age = t - (SLASH0 + k * STEP);
            if (age < 0 || age > 6) {
                continue;
            }
            float al = (float) (1.0 - age / 6.0);
            for (int id : sl.get(k)) {
                Vec3 c = targetCentre(id);
                if (c != null) {
                    p.billboard(c, 1.8 + 0.8 * (1 - al), 0.0, Pen.WHITE, al);
                }
            }
        }
        if (t < 16) {
            p.billboard(E.add(0, 1.2, 0), 3.0 + t * 0.3, 0, Pen.CORE, (float) (1.0 - t / 16.0));
        }
        if (t >= DURATION - 1 && t < DURATION + 14) {
            double age = t - DURATION + 1;
            p.billboard(E.add(0, 3, 0), 5.0 + age * 1.8, 0, Pen.CORE, (float) (1.0 - age / 15.0));
        }
    }

    private void ringPass() {
        double on = rise * fadeOut;
        if (on > 0.003) {
            Vec3 c = E.add(0, 0.07, 0);
            p.plane(c, X, Z, AURA, t * 0.01, Pen.ICE, (float) (on * 0.6));
            p.plane(c.add(0, 0.01, 0), X, Z, AURA * 0.5, -t * 0.018, Pen.CYAN, (float) (on * 0.5));
            p.plane(c.add(0, 0.02, 0), X, Z, AURA * 0.2, t * 0.035, Pen.CORE, (float) (on * 0.5));
        }
        // aura pulse: a faint ring leaving the caster every slash
        if (t > SLASH0 && t < DURATION) {
            double age = (t - SLASH0) % (STEP * 2);
            p.plane(E.add(0, 0.1, 0), X, Z, AURA * (0.1 + 0.9 * ease(age / (STEP * 2.0))), 0, Pen.ICE, (float) (0.3 * (1 - age / (STEP * 2.0))));
        }
        if (t < 20) {
            p.plane(E.add(0, 0.12, 0), X, Z, AURA * (0.1 + 0.9 * ease(t / 18.0)), 0, Pen.CORE, (float) (0.8 * (1 - t / 20.0)));
        }
        if (t >= DURATION && t < DURATION + 20) {
            double age = t - DURATION;
            p.plane(E.add(0, 0.12, 0), X, Z, AURA * (0.2 + 1.0 * ease(age / 16.0)), 0, Pen.CORE, (float) (0.9 * (1 - age / 20.0)));
            p.plane(E.add(0, 0.12, 0), X, Z, AURA * (0.1 + 0.8 * ease(age / 14.0)), 1.0, Pen.CYAN, (float) (0.6 * (1 - age / 20.0)));
        }
    }

    private void mistPass() {
        double on = rise * fadeOut;
        if (on <= 0.003) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            double s = AURA * (1.05 - 0.12 * i);
            double rot = t * (0.008 + 0.004 * i) * (i % 2 == 0 ? 1 : -1) + i * 1.7;
            p.plane(E.add(0, 0.05 + 0.6 * i, 0), X, Z, s, rot, Pen.BLUE, (float) (on * (0.2 - 0.04 * i)));
        }
    }

    /** Shards orbiting the caster; they burst outward when the form ends. */
    private void shardPass() {
        double age = t - DURATION;
        for (int i = 0; i < 28; i++) {
            Random r = new Random(seed + i * 211L + 5);
            double fin = c01((t - 6 - i * 0.6) / 6.0);
            if (fin <= 0) {
                continue;
            }
            double rad = 3.0 + r.nextDouble() * 4.5;
            double h = 0.8 + r.nextDouble() * 6.0;
            double ang = r.nextDouble() * 6.28 + t * (0.03 + r.nextDouble() * 0.03) * (i % 2 == 0 ? 1 : -1);
            Vec3 pos = E.add(Math.cos(ang) * rad, h + Math.sin(t * 0.06 + i) * 0.25, Math.sin(ang) * rad);
            double half = 0.3 + r.nextDouble() * 0.7;
            double rot = r.nextDouble() * 6.28 + t * 0.03;
            double al = 0.85 * fin;
            if (t >= DURATION) {
                Vec3 out = new Vec3(pos.x - E.x, 0.3, pos.z - E.z).normalize();
                pos = pos.add(out.scale((0.6 + r.nextDouble() * 1.4) * age)).add(0, -0.012 * age * age, 0);
                al *= 1.0 - c01((age - 10) / 28.0);
            }
            shard(pos, half, rot, r.nextInt(4), (float) al);
        }
        if (t >= DURATION - 1 && t < END) {
            for (int i = 0; i < 90; i++) {
                Random r = new Random(seed + i * 457L + 77);
                double ang = r.nextDouble() * 6.28;
                double el = (r.nextDouble() - 0.2) * 1.2;
                Vec3 v = new Vec3(Math.cos(ang) * Math.cos(el), Math.sin(el), Math.sin(ang) * Math.cos(el)).scale(0.7 + r.nextDouble() * 1.8);
                Vec3 pos = E.add(0, 7, 0).add(v.scale(age * 0.85)).add(0, -0.01 * age * age, 0);
                double al = 0.95 * c01((age + 1) / 2.0) * (1.0 - c01((age - 12) / 30.0));
                shard(pos, 0.35 + r.nextDouble() * 0.9, r.nextDouble() * 6.28 + age * (0.15 + r.nextDouble() * 0.3), r.nextInt(4), (float) al);
            }
        }
    }

    private void shard(Vec3 c, double half, double rot, int cell, float al) {
        if (al <= 0.01f) {
            return;
        }
        double u0 = (cell % 2) * 0.5;
        double v0 = (cell / 2) * 0.5;
        Vec3 L = new Vec3(p.left.x(), p.left.y(), p.left.z());
        Vec3 U = new Vec3(p.up.x(), p.up.y(), p.up.z());
        double tilt = 0.55 * Math.sin(rot * 1.7 + cell);
        Vec3 look = p.cam.subtract(c).normalize();
        Vec3 ax = L.scale(Math.cos(rot) * half).add(U.scale(Math.sin(rot) * half));
        Vec3 ay = L.scale(-Math.sin(rot) * half).add(U.scale(Math.cos(rot) * half)).add(look.scale(-tilt * half));
        p.quad(c.subtract(ax).subtract(ay), c.add(ax).subtract(ay), c.add(ax).add(ay), c.subtract(ax).add(ay),
                (float) u0, (float) v0, (float) (u0 + 0.5), (float) (v0 + 0.5), Pen.WHITE, al);
    }
}
