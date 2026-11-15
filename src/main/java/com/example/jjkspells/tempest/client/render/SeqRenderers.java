package com.example.jjkspells.tempest.client.render;

import com.example.jjkspells.tempest.entity.*;
import com.example.jjkspells.vfx.MeshRender;
import com.example.jjkspells.vfx.MeshRender.Mode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.tempest.client.render.Gfx.*;
import static com.example.jjkspells.tempest.client.render.RimGfx.*;

/**
 * The seven timeline renderers of the world-ending Rimuru set: predation and the void. Real 3D meshes (black holes, accretion discs,
 * tendrils, glass lenses, broken space, nebulae) lit by the engine; additive light is kept for thin accents only.
 */
public final class SeqRenderers {
    private SeqRenderers() {
    }

    static Vec3 polar(double ang, double r, double y) {
        return new Vec3(Math.cos(ang) * r, y, Math.sin(ang) * r);
    }

    static float vis(float sec, float a, float in, float b, float out) {
        return Math.min(smooth((sec - a) / Math.max(0.01f, in)), 1f - smooth((sec - b) / Math.max(0.01f, out)));
    }

    abstract static class Base<T extends SeqEntity> extends TemRenderer<T> {
        Base(EntityRendererProvider.Context ctx) {
            super(ctx);
        }
    }

    /** A black hole: event horizon, tilted differential accretion disc, photon ring and thin polar jets. */
    static void blackHole(MultiBufferSource buf, PoseStack ps, Vec3 c, float rh, float t, Vec3 cam, float alpha, float tilt) {
        if (rh < 0.05f || alpha <= 0.01f) {
            return;
        }
        put(buf, ps, sphere(), "void", Mode.TRANS, c, rh, rh, rh, t * 0.2f, 0f, 0f, ABYSS, 1f, 0f, 0f, 0f);
        float yaw = t * 0.35f;
        put(buf, ps, discIn(), "accretion", Mode.TRANS, c, rh, rh, rh, yaw, tilt, 6f, PURE, alpha, 1f, -t * 0.014f, 0f);
        put(buf, ps, discOut(), "accretion", Mode.TRANS, c, rh, rh, rh, yaw, tilt, 6f, AQUA, 0.8f * alpha, 0.8f, t * 0.005f, 0f);
        camRing(buf, ps, c, rh * 1.04f, cam, ICE, alpha);
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.mulPose(Axis.XP.rotationDegrees(tilt));
        ps.mulPose(Axis.ZP.rotationDegrees(6f));
        ps.scale(rh * 0.45f, rh * 6f, rh * 0.45f);
        MeshRender.draw(buf, ps, cone(), "electric", Mode.ADD, AQUA, 0.3f * alpha, 1f, 0f, t * 0.03f);
        ps.scale(1f, -1f, 1f);
        MeshRender.draw(buf, ps, cone(), "electric", Mode.ADD, AQUA, 0.3f * alpha, 1f, 0f, t * 0.03f);
        ps.popPose();
    }

    // ------------------------------------------------------------------ World-Devouring Abyss

    public static class Abyss extends Base<AbyssEntity> {
        public Abyss(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(AbyssEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            gather(buf, ps, s, 0.6f, Vec3.ZERO, t);
            if (s >= 0.4f && s < 8.05f) {
                float grow = smooth((s - 0.5f) / 1.5f);
                float out = s > 7f ? 1f - smooth((s - 7f) / 1f) : 1f;
                float rh = Math.max(0.8f, R1 * 0.14f * grow) * (s > 6.4f ? 1f + 1.4f * smooth((s - 6.4f) / 0.6f) : 1f);
                Vec3 c = new Vec3(0, 3f + rh * 1.1f, 0);
                // the ground is dragged into a spiral and darkened
                put(buf, ps, discFlat(), "void", Mode.TRANS, new Vec3(0, 0.06, 0), R1 * grow, R1 * grow, R1 * grow, 0f, 0f, 0f, ABYSS, 0.75f * out, 0f, 0f, 0f);
                put(buf, ps, discFlat(), "accretion", Mode.TRANS, new Vec3(0, 0.1, 0), R1 * grow, R1 * grow, R1 * grow, 0f, 0f, 0f, AQUA, 0.5f * out, 0.8f, -t * 0.012f, 0f);
                blackHole(buf, ps, c, rh * out, t, cam.subtract(Vec3.ZERO), out, 24f);
                debris(buf, ps, 16, c, R1 * 0.95f, rh * 1.6f, s, t, grow, out);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4 + t * 0.006;
                    float L = R1 * 0.42f * grow * out;
                    float sway = (float) Math.sin(t * 0.05 + i) * 10f;
                    put(buf, ps, tent(i), "tendril", Mode.SOLID, polar(a, R1 * 0.82f * grow, 0.1), L, L, L, (float) -Math.toDegrees(a), 0f, 52f + sway, TEAL, 1f, 0.25f, 0f, 0f);
                }
            }
            if (s >= 2.5f && s < 3.4f) {
                float f = (s - 2.5f) / 0.9f;
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), R1 * smooth(f), AQUA, 1f - f);
            }
            if (s >= 7f && s < 8f) {
                float f = s - 7f;
                Vec3 c = new Vec3(0, 6, 0);
                sphere(buf, ps, "void", Mode.TRANS, c, R2 * smooth(f), NAVY, 0.55f * (1f - f), 0.2f, 0f);
                sphere(buf, ps, "glass", Mode.TRANS, c, R2 * 0.92f * smooth(f), AQUA, 0.3f * (1f - f), 0.9f, t);
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), R2 * smooth(Math.min(1f, f * 1.3f)), ICE, 1f - f);
                ringFlat(buf, ps, new Vec3(0, 0.5, 0), R2 * 0.7f * smooth(f), AQUA, 1f - f);
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (d[0] != 1 || age < 0 || age > 12) {
                    continue;
                }
                Vec3 q = v(d[2], d[3], d[4]).subtract(base);
                Vec3 p = q.lerp(new Vec3(0, 4, 0), age / 12f);
                float k = 1f - age / 12f;
                put(buf, ps, sphereLo(), "white", Mode.SOLID, p, 0.5f * k, 0f, ICE, 1f, 1f);
            }
        }
    }

    // ------------------------------------------------------------------ Civilization Storm

    public static class Storm extends Base<StormEntity> {
        public Storm(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(StormEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            gather(buf, ps, s, 0.6f, Vec3.ZERO, t);
            if (s >= 0.3f && s < 8.05f) {
                float f = vis(s, 0.3f, 1.5f, 7.2f, 0.8f);
                float spin = t * 0.004f;
                for (int i = 0; i < 9; i++) {
                    double a = i * Math.PI * 2 / 9 + spin;
                    float sz = R1 * 0.4f;
                    boolean lit = ((int) (t / 3f) + i * 7) % 6 == 0;
                    put(buf, ps, cloud(i), "storm_cloud", Mode.TRANS, polar(a, R1 * 0.55, 32 + (i % 3) * 3), sz, sz * 0.7f, sz, i * 40f + t * 0.05f, 0f, 0f, lit ? AQUA : CLOUD, 0.92f * f, lit ? 0.9f : 0.15f, t * 0.0008f, 0f);
                }
                put(buf, ps, cloud(0), "storm_cloud", Mode.TRANS, new Vec3(0, 35, 0), R1 * 0.75f, R1 * 0.5f, R1 * 0.75f, -t * 0.08f, 0f, 0f, CLOUD, 0.95f * f, 0.1f, 0f, 0f);
                put(buf, ps, discFlat(), "void", Mode.TRANS, new Vec3(0, 0.06, 0), R1 * smooth(Math.min(1f, s)), 1f, R1 * smooth(Math.min(1f, s)), 0f, 0f, 0f, ABYSS, 0.5f * f, 0f, 0f, 0f);
                if (s < 7.2f) {
                    int step = (int) (t / 3f);
                    for (int j = 0; j < 3; j++) {
                        int seed = step * 3 + j;
                        if (hash(seed, 9) < 0.45) {
                            double a = hash(seed, 1) * Math.PI * 2, d = Math.sqrt(hash(seed, 2)) * R1 * 0.85;
                            Vec3 top = polar(a, d * 0.9, 34), bot = polar(a + (hash(seed, 3) - 0.5) * 0.3, d, 0.1);
                            bolt(buf, ps, top, bot, 1, seed, ICE, 0.85f * f);
                        }
                    }
                }
            }
            if (s >= 2.5f && s < 3.2f) {
                float f = (s - 2.5f) / 0.7f;
                for (int j = 0; j < 3; j++) {
                    bolt(buf, ps, new Vec3(j - 1, 70, j * 0.5 - 0.5), new Vec3(0, 0, 0), 2, j + (int) (t * 0.5f), ICE, 1f - f);
                }
                put(buf, ps, openTube(), "electric", Mode.ADD, Vec3.ZERO, 3f * (1f - f), 70f, 3f * (1f - f), 0f, 0f, 0f, ICE, 0.6f * (1f - f), 1f, 0f, t * 0.05f);
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), 20f * smooth(f), ICE, 1f - f);
            }
            if (s >= 7.2f && s < 8f) {
                float f = (s - 7.2f) / 0.8f;
                float w = R2 * 0.2f * (1f - f * 0.6f);
                put(buf, ps, openTube(), "electric", Mode.ADD, Vec3.ZERO, w, 90f, w, 0f, 0f, 0f, ICE, 0.9f * (1f - f), 1f, 0f, t * 0.06f);
                put(buf, ps, openTube(), "funnel_wall", Mode.TRANS, Vec3.ZERO, w * 1.8f, 90f, w * 1.8f, t * 2f, 0f, 0f, AQUA, 0.6f * (1f - f), 0.8f, 0f, t * 0.04f);
                for (int j = 0; j < 7; j++) {
                    double a = j * Math.PI * 2 / 7;
                    bolt(buf, ps, polar(a, w * 0.5, 80), polar(a + 0.3, R2 * 0.3, 0.1), 2, j + (int) t, ICE, 1f - f);
                }
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), R2 * smooth(Math.min(1f, f * 1.4f)), ICE, 1f - f);
                Fx.crack(buf, ps, new Vec3(0, 0.12, 0), R2 * 0.6f, 0f, CYAN, 1f - f * 0.6f);
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (age < 0 || age > 8 || (d[0] != 1 && d[0] != 2)) {
                    continue;
                }
                Vec3 q = v(d[2], d[3], d[4]).subtract(base);
                float f = 1f - age / 8f;
                int th = d[0] == 2 ? 2 : 1;
                bolt(buf, ps, q.add(0, 60, 0), q, th, (int) d[1], ICE, f);
                ringFlat(buf, ps, q.add(0, 0.3, 0), (d[0] == 2 ? 7f : 3.5f) * smooth(1f - f), AQUA, f);
                Fx.crack(buf, ps, q.add(0, 0.12, 0), d[0] == 2 ? 6f : 3f, (float) d[1] * 37f, CYAN, f);
            }
        }
    }

    // ------------------------------------------------------------------ Heavenly Execution Array

    public static class Array extends Base<ArrayEntity> {
        public Array(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        static Vec3 lensPos(int i, float R, float t) {
            double ang = i * Math.PI * 2 / 8 + t * 0.006;
            return polar(ang, R * 0.55, 40 + (i % 2) * 5);
        }

        @Override
        protected void draw(ArrayEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            gather(buf, ps, s, 0.6f, Vec3.ZERO, t);
            float f = vis(s, 0.4f, 1.5f, 8f, 1f);
            if (f > 0.01f) {
                float g = smooth(Math.min(1f, s));
                put(buf, ps, discFlat(), "glass", Mode.TRANS, new Vec3(0, 0.07, 0), R1 * g, R1 * g, R1 * g, t * 0.5f, 0f, 0f, TEAL, 0.3f * f, 0.7f, 0f, 0f);
                put(buf, ps, torusMed(), "white", Mode.SOLID, new Vec3(0, 0.15, 0), R1 * g * 0.9f, R1 * g * 0.9f, R1 * g * 0.9f, t * 0.6f, 0f, 0f, ICE, 1f, 1f, 0f, 0f);
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8 + t * 0.003;
                    float rise = smooth(Math.min(1f, (s - 0.4f - i * 0.04f) / 0.8f));
                    put(buf, ps, pylon(), "glass", Mode.SOLID, polar(a, R1 * 0.9 * g, 0), 3.2f, 5f * rise, 3.2f, 0f, 0f, 0f, AQUA, 1f, 0.8f, 0f, 0f);
                }
                Vec3 ringC = new Vec3(0, 42.5, 0);
                put(buf, ps, torusMed(), "rock", Mode.SOLID, ringC, R1 * 0.55f, R1 * 0.55f, R1 * 0.55f, t * 0.2f, 0f, 0f, STEEL, 1f, 0.35f, 0f, 0f);
                for (int i = 0; i < 8; i++) {
                    Vec3 p = lensPos(i, R1, t), q = lensPos((i + 1) % 8, R1, t);
                    strut(buf, ps, p, q, 0.35f, STEEL, 0.35f);
                    ps.pushPose();
                    ps.translate(p.x, p.y, p.z);
                    MeshRender.pushAlong(ps, Vec3.ZERO, Vec3.ZERO.subtract(p));
                    ps.scale(5.2f, 5.2f, 5.2f);
                    MeshRender.draw(buf, ps, lens(), "rim_lensband", Mode.TRANS, ICE, 0.75f * f, 0.8f, 0f, t * 0.002f);
                    ps.pushPose();
                    ps.mulPose(Axis.YP.rotationDegrees(t * 1.2f + i * 30f));
                    ps.scale(1.12f, 1.12f, 1.12f);
                    MeshRender.draw(buf, ps, torusMed(), "rock", Mode.SOLID, STEEL, 1f, 0.5f, 0f, 0f);
                    ps.popPose();
                    ps.pushPose();
                    ps.mulPose(Axis.XP.rotationDegrees(90f + 25f * (float) Math.sin(t * 0.04 + i)));
                    ps.mulPose(Axis.YP.rotationDegrees(-t * 0.9f));
                    ps.scale(1.28f, 1.28f, 1.28f);
                    MeshRender.draw(buf, ps, torusMed(), "rock", Mode.SOLID, STEEL, 1f, 0.5f, 0f, 0f);
                    ps.popPose();
                    ps.pushPose();
                    ps.mulPose(Axis.ZP.rotationDegrees(90f));
                    ps.mulPose(Axis.YP.rotationDegrees(t * 0.7f));
                    ps.scale(1.44f, 1.44f, 1.44f);
                    MeshRender.draw(buf, ps, torusMed(), "white", Mode.SOLID, AQUA, 1f, 1f, 0f, 0f);
                    ps.popPose();
                    ps.popPose();
                    ps.popPose();
                }
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (age < 0 || age > 10) {
                    continue;
                }
                float k = 1f - age / 10f;
                if (d[0] == 1) {
                    Vec3 q = v(d[3], d[4], d[5]).subtract(base);
                    beam(buf, ps, lensPos((int) d[2] % 8, R1, t), q, 0.45f * k, ICE, k);
                    ringFlat(buf, ps, q.add(0, 0.2, 0), 2f + 3f * (1f - k), AQUA, k);
                } else if (d[0] == 2) {
                    Vec3 q = v(d[2], d[3], d[4]).subtract(base);
                    beam(buf, ps, q.add(0, 70, 0), q, 2.2f * k, AQUA, k);
                    beam(buf, ps, q.add(0, 70, 0), q, 0.9f * k, ICE, k);
                    ringFlat(buf, ps, q.add(0, 0.3, 0), 3f + 8f * (1f - k), ICE, k);
                }
            }
            if (s >= 7.5f && s < 8.5f) {
                float g = s - 7.5f;
                for (int i = 0; i < 8; i++) {
                    beam(buf, ps, lensPos(i, R1, t), new Vec3(0, 3, 0), 1.4f * (1f - g), ICE, 1f - g);
                }
                beam(buf, ps, new Vec3(0, 85, 0), Vec3.ZERO, R2 * 0.18f * (1f - g * 0.5f), AQUA, 0.9f * (1f - g));
                beam(buf, ps, new Vec3(0, 85, 0), Vec3.ZERO, R2 * 0.07f, ICE, 1f - g);
                sphere(buf, ps, "glass", Mode.TRANS, new Vec3(0, 4, 0), R2 * smooth(g), AQUA, 0.35f * (1f - g), 0.9f, t);
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), R2 * smooth(Math.min(1f, g * 1.4f)), ICE, 1f - g);
            }
        }
    }

    // ------------------------------------------------------------------ Fracture of Infinite Worlds

    /** A gate in broken space: a glass ring around a window onto another universe. Faces along 'yaw'. */
    static void gate(MultiBufferSource buf, PoseStack ps, Vec3 at, float yaw, float size, float t, float alpha) {
        if (size < 0.05f || alpha <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(90f));
        ps.scale(size, size, size);
        MeshRender.draw(buf, ps, discFlat(), "cosmos", Mode.SOLID, PURE, 1f, 1f, t * 0.002f, 0f);
        ps.pushPose();
        ps.scale(1.06f, 1.06f, 1.06f);
        MeshRender.draw(buf, ps, torusMed(), "glass", Mode.SOLID, ICE, 1f, 1f, 0f, 0f);
        ps.popPose();
        for (int i = 0; i < 8; i++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(i * 45f + t * 2f));
            ps.translate(1.1, 0, 0);
            ps.mulPose(Axis.ZP.rotationDegrees(-90f));
            ps.scale(0.35f, 0.5f, 0.35f);
            MeshRender.draw(buf, ps, shard(i), "cosmos", Mode.SOLID, PURE, 1f, 1f, 0f, 0f);
            ps.popPose();
        }
        ps.popPose();
    }

    public static class Fracture extends Base<FractureEntity> {
        public Fracture(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(FractureEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, L = e.r1(), R2 = e.r2(), yaw = e.yaw();
            Vec3 dir = FractureEntity.dir(yaw);
            Vec3 origin = dir.scale(-10);
            if (s < 1.0f) {
                gate(buf, ps, ownerP.add(0, 1.2, 0), yaw, 1.9f * smooth(s), t, 1f);
            }
            if (s >= 0.8f && s < 2.2f) {
                float f = vis(s, 0.8f, 0.3f, 1.8f, 0.4f);
                gate(buf, ps, origin.add(0, 1.2, 0), yaw, 2.2f * f, t, f);
            }
            if (s >= 2.5f && s < 3.8f) {
                float f = (s - 2.5f) / 1.3f;
                float open = (float) Math.pow(Math.sin(Math.PI * Math.min(1f, f)), 0.6);
                Vec3 mid = origin.add(dir.scale(L * 0.5)).add(0, 1.4, 0);
                put(buf, ps, rift(0), "cosmos", Mode.SOLID, mid, 1f, 7f * open, L, -yaw, 0f, 0f, PURE, 1f, 1f, t * 0.003f, 0f);
                put(buf, ps, rift(1), "glass", Mode.TRANS, mid, 1.2f, 8f * open, L * 1.02f, -yaw, 0f, 0f, ICE, 0.5f * (1f - f), 1f, 0f, 0f);
                beam(buf, ps, origin.add(0, 1.2, 0), origin.add(dir.scale(L)).add(0, 1.2, 0), 0.9f * (1f - f), ICE, 1f - f);
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (d[0] != 1 || age < 0 || age > 14) {
                    continue;
                }
                Vec3 q = v(d[2], d[3], d[4]).subtract(base);
                float k = 1f - age / 14f;
                for (int i = 0; i < 6; i++) {
                    double a = i * 1.047 + d[1];
                    Vec3 p = q.add(Math.cos(a) * age * 0.35, 1.5 + age * 0.12 * (i % 3), Math.sin(a) * age * 0.35);
                    put(buf, ps, shard(i), "cosmos", Mode.SOLID, p, 0.5f * k, 1.6f * k, 0.5f * k, i * 60f, age * 18f, i * 40f, PURE, 1f, 1f, 0f, 0f);
                }
                ringFlat(buf, ps, q.add(0, 0.2, 0), 4f * smooth(1f - k), AQUA, k);
            }
            if (s >= 4.5f && s < 8f) {
                float f = vis(s, 4.5f, 1f, 7f, 1f);
                float g = smooth(Math.min(1f, s - 4.5f));
                put(buf, ps, discFlat(), "cosmos", Mode.TRANS, new Vec3(0, 0.1, 0), R2 * g, R2 * g, R2 * g, 0f, 0f, 0f, PURE, 0.85f * f, 1f, t * 0.0008f, 0f);
                put(buf, ps, torusMed(), "glass", Mode.SOLID, new Vec3(0, 0.15, 0), R2 * g, R2 * g, R2 * g, 0f, 0f, 0f, ICE, 1f, 1f, 0f, 0f);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6 + t * 0.004;
                    float rr = (i % 2 == 0 ? 0.62f : 0.36f) * R2;
                    float rise = smooth((s - 4.5f - i * 0.05f) / 0.8f);
                    float H = (9f + 7f * (float) Math.abs(Math.sin(i * 2.7))) * rise;
                    Vec3 p = polar(a, rr, 0);
                    put(buf, ps, shard(i), "cosmos", Mode.SOLID, p, 2.4f, H, 2.4f, (float) -Math.toDegrees(a), 0f, -(14f + (i % 3) * 5f), PURE, 1f, 0.95f, 0f, 0f);
                    put(buf, ps, shard(i), "glass", Mode.TRANS, p, 2.6f, H * 1.04f, 2.6f, (float) -Math.toDegrees(a), 0f, -(14f + (i % 3) * 5f), ICE, 0.35f * f, 0.9f, 0f, 0f);
                }
            }
            if (s >= 7f && s < 8f) {
                float f = s - 7f;
                sphere(buf, ps, "cosmos", Mode.TRANS, new Vec3(0, 3, 0), R2 * smooth(f), PURE, 0.5f * (1f - f), 1f, t);
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), R2 * smooth(Math.min(1f, f * 1.3f)), ICE, 1f - f);
            }
        }
    }

    // ------------------------------------------------------------------ Abyss That Consumes All

    public static class Gluttony extends Base<GluttonyEntity> {
        public Gluttony(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(GluttonyEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            gather(buf, ps, s, 0.8f, Vec3.ZERO, t);
            if (s >= 0.8f && s < 11f) {
                float grow = smooth((s - 0.8f) / 1.7f);
                float out = s > 9.6f ? 1f - smooth((s - 9.6f) / 1.4f) : 1f;
                float h = 14f + 6f * grow;
                float r = (3f + R1 * 0.18f) * grow * (s > 8.5f && s < 9.2f ? 1f + 1.6f * smooth((s - 8.5f) / 0.7f) : 1f) * out;
                Vec3 c = new Vec3(0, h, 0);
                // floor: darkened and spiralling
                put(buf, ps, discFlat(), "void", Mode.TRANS, new Vec3(0, 0.06, 0), R1 * grow, R1 * grow, R1 * grow, 0f, 0f, 0f, ABYSS, 0.8f * out, 0f, 0f, 0f);
                put(buf, ps, discFlat(), "accretion", Mode.TRANS, new Vec3(0, 0.1, 0), R1 * grow, R1 * grow, R1 * grow, 0f, 0f, 0f, TEAL, 0.55f * out, 0.8f, -t * 0.014f, 0f);
                // the maw: a roiling black core with counter-rotating rings of teeth opening toward the ground
                put(buf, ps, lumpy(0), "tendril", Mode.SOLID, c, r, r, r, t * 0.6f, 0f, 0f, ABYSS, 1f, 0.1f, 0f, 0f);
                put(buf, ps, lumpy(1), "void", Mode.TRANS, c, r * 1.15f, r * 1.15f, r * 1.15f, -t * 0.4f, 0f, 0f, NAVY, 0.5f * out, 0.3f, 0f, 0f);
                for (int k = 0; k < 3; k++) {
                    float sg = k % 2 == 0 ? 1f : -1f;
                    put(buf, ps, mawRing(k), "bone", Mode.SOLID, c.add(0, -r * (0.35 + 0.18 * k), 0), r * 1.3f, r * 1.3f, r * 1.3f, sg * t * 2.2f, 0f, 0f, ICE, 1f, 0.45f, 0f, 0f);
                }
                // suction column
                put(buf, ps, openTube(), "accretion", Mode.TRANS, Vec3.ZERO, r * 1.1f, h, r * 1.1f, 0f, 0f, 0f, AQUA, 0.22f * out, 0.8f, -t * 0.02f, 0f);
                // tentacles hanging from the core and reaching the ground
                for (int i = 0; i < 14; i++) {
                    double a = i * Math.PI * 2 / 14 + 0.3 * Math.sin(t * 0.01 + i);
                    float contract = s > 9f ? 1f - smooth((s - 9f) / 1.2f) * 0.8f : 1f;
                    float L = h * 1.5f * grow * contract * out;
                    float wr = 8f * (float) Math.sin(t * 0.06 + i * 1.7);
                    put(buf, ps, tent(i), "tendril", Mode.SOLID, c.add(Math.cos(a) * r * 0.7, -r * 0.3, Math.sin(a) * r * 0.7), L, L, L, (float) -Math.toDegrees(a), 0f, 205f + wr, TEAL, 1f, 0.2f, 0f, 0f);
                }
                // tendrils closing in from the rim
                for (int i = 0; i < 20; i++) {
                    double a = i * Math.PI * 2 / 20 - t * 0.004;
                    float L = R1 * 0.2f * grow * out * (0.8f + 0.2f * (float) Math.sin(t * 0.07 + i));
                    put(buf, ps, tent(i + 3), "tendril", Mode.SOLID, polar(a, R1 * 0.9 * grow, 0.1), L, L, L, (float) -Math.toDegrees(a), 0f, 58f, ABYSS, 1f, 0.15f, 0f, 0f);
                }
            }
            if (s >= 2.5f && s < 3.4f) {
                float f = (s - 2.5f) / 0.9f;
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), R1 * smooth(f), AQUA, 1f - f);
            }
            if (s >= 9f && s < 10.2f) {
                float f = (s - 9f) / 1.2f;
                sphere(buf, ps, "void", Mode.TRANS, new Vec3(0, 6, 0), R2 * smooth(f), NAVY, 0.55f * (1f - f), 0.2f, 0f);
                sphere(buf, ps, "glass", Mode.TRANS, new Vec3(0, 6, 0), R2 * 0.9f * smooth(f), AQUA, 0.3f * (1f - f), 0.9f, t);
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), R2 * smooth(Math.min(1f, f * 1.3f)), ICE, 1f - f);
            }
        }
    }

    // ------------------------------------------------------------------ Birth of Nothingness

    public static class Nothing extends Base<NothingEntity> {
        public Nothing(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(NothingEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            Vec3 star = null;
            float sr = 0f, sa = 1f;
            if (s < 6f) {
                float f = smooth(s / 4f);
                Vec3 a = ownerP.add(0, 12, 0), b = new Vec3(0, 45, 0);
                if (s < 3.5f) {
                    star = a.lerp(b, smooth(s / 3.5f));
                    sr = 1f + 5f * f;
                } else {
                    float k = (s - 3.5f) / 2.5f;
                    star = b.lerp(new Vec3(0, 6, 0), k * k);
                    sr = 6f + 4f * k;
                }
                gather(buf, ps, s, 0.5f, Vec3.ZERO, t);
            } else if (s < 6.5f) {
                star = new Vec3(0, 6, 0);
                sr = 10f + 14f * smooth((s - 6f) / 0.5f);
                sa = 1f - 0.4f * (s - 6f) / 0.5f;
            }
            if (star != null) {
                float dark = smooth((s - 3.8f) / 2.2f);
                if (s < 6f) {
                    // the star burns out into a void: lit sphere, nebulous shell, then a black core
                    put(buf, ps, sphere(), "cosmos", Mode.SOLID, star, sr, sr, sr, t * 0.5f, 0f, 0f, MeshRender.mix(ICE, ABYSS, dark), 1f, 1f - dark, 0f, 0f);
                    put(buf, ps, sphere(), "nebula", Mode.TRANS, star, sr * 1.18f, sr * 1.18f, sr * 1.18f, -t * 0.7f, 0f, 0f, AQUA, 0.5f * (1f - dark), 1f, t * 0.002f, 0f);
                    beam(buf, ps, star, new Vec3(0, 0.2, 0), 0.5f + sr * 0.12f, AQUA, 0.2f * (1f - dark));
                    Fx.glowTex(buf, ps, CORONA, star, sr * 2.4f, t * 1.2f, ICE, 0.55f * (1f - dark));
                    camRing(buf, ps, star, sr * 1.05f, cam, ICE, dark * 1f);
                } else {
                    blackHole(buf, ps, star, sr * 0.5f, t, cam, sa, 70f);
                }
                if (s > 2f) {
                    put(buf, ps, discFlat(), "accretion", Mode.TRANS, new Vec3(0, 0.1, 0), R1 * smooth((s - 2f) / 1f), 1f, R1 * smooth((s - 2f) / 1f), 0f, 0f, 0f, AQUA, 0.5f, 0.8f, -t * 0.01f, 0f);
                }
            }
            if (s >= 6f && s < 7.5f) {
                float f = (s - 6f) / 1.5f;
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), R1 * smooth(Math.min(1f, f * 1.3f)), ICE, 1f - f);
                sphere(buf, ps, "glass", Mode.TRANS, new Vec3(0, 6, 0), R2 * 0.75f * smooth(f), AQUA, 0.4f * (1f - f), 1f, t);
            }
            if (s >= 6.5f && s < 12f) {
                float f = vis(s, 6.5f, 1f, 10.5f, 1.2f);
                float g = smooth((s - 6.5f) / 1.5f);
                Vec3 c = new Vec3(0, 8, 0);
                float core = (4f + 6f * smooth((s - 8f) / 2.5f)) * g;
                put(buf, ps, discFlat(), "accretion", Mode.TRANS, new Vec3(0, 0.1, 0), 45f * g, 1f, 45f * g, 0f, 0f, 0f, AQUA, 0.6f * f, 0.8f, -t * 0.012f, 0f);
                put(buf, ps, discFlat(), "void", Mode.TRANS, new Vec3(0, 0.07, 0), 45f * g, 1f, 45f * g, 0f, 0f, 0f, ABYSS, 0.7f * f, 0f, 0f, 0f);
                blackHole(buf, ps, c, core * f, t, cam, f, 78f);
                debris(buf, ps, 14, c, 40f * g, core * 1.7f, s - 6f, t, g, f);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6 - t * 0.006;
                    float L = 18f * g * f;
                    put(buf, ps, tent(i), "tendril", Mode.SOLID, polar(a, 36f * g, 0.1), L, L, L, (float) -Math.toDegrees(a), 0f, 50f + 8f * (float) Math.sin(t * 0.05 + i), ABYSS, 1f, 0.15f, 0f, 0f);
                }
            }
            if (s >= 10.5f && s < 12f) {
                float f = (s - 10.5f) / 1.5f;
                Vec3 c = new Vec3(0, 8, 0);
                sphere(buf, ps, "white", Mode.TRANS, c, R2 * smooth(f), ICE, 0.6f * (1f - f), 1f, 0f);
                sphere(buf, ps, "cosmos", Mode.TRANS, c, R2 * 0.8f * smooth(f), PURE, 0.5f * (1f - f), 1f, t);
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), R2 * smooth(Math.min(1f, f * 1.2f)), ICE, 1f - f);
                ringFlat(buf, ps, new Vec3(0, 0.6, 0), R2 * 0.6f * smooth(f), AQUA, 1f - f);
                Fx.crack(buf, ps, new Vec3(0, 0.12, 0), R2 * 0.8f, 0f, CYAN, 1f - f);
            }
        }
    }

    // ------------------------------------------------------------------ End of All Creation (Azathoth)

    public static class Azathoth extends Base<AzathothEntity> {
        public Azathoth(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(AzathothEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            float f = vis(s, 0.2f, 3f, 19f, 3f);
            if (f > 0.01f) {
                float g = smooth(Math.min(1f, s / 3f));
                // an artificial universe: the sky is replaced, the ground goes dark and starry
                put(buf, ps, hemi(), "cosmos", Mode.TRANS, Vec3.ZERO, R1 * 0.98f, R1 * 0.98f, R1 * 0.98f, t * 0.04f, 0f, 0f, PURE, 0.6f * f, 1f, t * 0.0004f, 0f);
                put(buf, ps, discFlat(), "void", Mode.TRANS, new Vec3(0, 0.06, 0), R1 * g, 1f, R1 * g, 0f, 0f, 0f, PURE, 0.85f * f, 0.2f, 0f, 0f);
                put(buf, ps, discFlat(), "accretion", Mode.TRANS, new Vec3(0, 0.1, 0), R1 * 0.85f * g, 1f, R1 * 0.85f * g, 0f, 0f, 0f, TEAL, 0.4f * f, 0.8f, t * 0.006f, 0f);
                for (int i = 0; i < 5; i++) {
                    float dir = i % 2 == 0 ? 1f : -1f;
                    put(buf, ps, sphere(), "nebula", Mode.TRANS, new Vec3(0, 18 + 6 * i, 0), R1 * (0.5f + 0.08f * i), R1 * 0.12f, R1 * (0.5f + 0.08f * i), dir * t * 0.06f + i * 30f, 4f * i, 0f, AQUA, 0.3f * f, 1f, t * 0.0005f * dir, 0f);
                }
            }
            if (s >= 2f && s < 20f) {
                float k = vis(s, 2f, 3f, 17f, 2.5f);
                Vec3 c = new Vec3(0, 28, 0);
                float r = (6f + 4f * smooth((s - 6f) / 6f) + (s > 16.5f ? 8f * smooth((s - 16.5f) / 0.5f) : 0f)) * k;
                put(buf, ps, lumpy(0), "tendril", Mode.SOLID, c, r, r, r, t * 0.5f, 0f, 0f, ABYSS, 1f, 0.1f, 0f, 0f);
                put(buf, ps, lumpy(2), "void", Mode.TRANS, c, r * 1.2f, r * 1.2f, r * 1.2f, -t * 0.35f, 0f, 0f, NAVY, 0.5f * k, 0.3f, 0f, 0f);
                put(buf, ps, discIn(), "accretion", Mode.TRANS, c, r, r, r, t * 0.3f, 75f, 0f, PURE, k, 1f, -t * 0.012f, 0f);
                put(buf, ps, discOut(), "accretion", Mode.TRANS, c, r, r, r, t * 0.3f, 75f, 0f, AQUA, 0.8f * k, 0.8f, t * 0.004f, 0f);
                camRing(buf, ps, c, r * 1.04f, cam.subtract(Vec3.ZERO), ICE, k);
                for (int i = 0; i < 20; i++) {
                    double a = i * Math.PI * 2 / 20 + t * 0.004;
                    float L = r * 3.2f * (0.8f + 0.2f * (float) Math.sin(t * 0.06 + i));
                    put(buf, ps, tent(i), "tendril", Mode.SOLID, c.add(Math.cos(a) * r * 0.8, r * 0.3, Math.sin(a) * r * 0.8), L, L, L, (float) -Math.toDegrees(a), 0f, -(35f + 40f * (i % 2)) + 8f * (float) Math.sin(t * 0.05 + i), TEAL, 1f, 0.2f, 0f, 0f);
                }
                if (s > 6f) {
                    for (int i = 0; i < 10; i++) {
                        double a = i * Math.PI / 5 - t * 0.003;
                        float L = Math.min(R1 * 0.5f, 28f) * smooth((s - 6f) / 2f) * k;
                        put(buf, ps, tent(i + 2), "tendril", Mode.SOLID, c.add(Math.cos(a) * r, -r * 0.4, Math.sin(a) * r), L, L, L, (float) -Math.toDegrees(a), 0f, 200f + 10f * (float) Math.sin(t * 0.04 + i), ABYSS, 1f, 0.15f, 0f, 0f);
                    }
                }
            }
            if (s >= 6.5f && s < 7.6f) {
                float g = (s - 6.5f) / 1.1f;
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), R1 * smooth(g), AQUA, 1f - g);
                beam(buf, ps, new Vec3(0, 28, 0), ownerP.add(0, 1, 0), 3f * (1f - g), ICE, 1f - g);
            }
            if (s >= 10f && s < 11.5f) {
                float g = (s - 10f) / 1.5f;
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), R2 * 1.2f * smooth(g), ICE, 1f - g);
                Fx.crack(buf, ps, new Vec3(0, 0.12, 0), R2, 0f, CYAN, 1f - g);
            }
            if (s >= 13f && s < 14.5f) {
                float g = (s - 13f) / 1.5f;
                put(buf, ps, discFlat(), "accretion", Mode.TRANS, new Vec3(0, 0.2, 0), R1 * (1f - 0.5f * g), 1f, R1 * (1f - 0.5f * g), 0f, 0f, 0f, PURE, 0.8f * (1f - g), 1f, -t * 0.03f, 0f);
            }
            if (s >= 17f && s < 19f) {
                float g = (s - 17f) / 2f;
                Vec3 c = new Vec3(0, 14, 0);
                sphere(buf, ps, "white", Mode.TRANS, c, R2 * smooth(g), ICE, 0.6f * (1f - g), 1f, 0f);
                sphere(buf, ps, "cosmos", Mode.TRANS, c, R2 * 0.85f * smooth(g), PURE, 0.5f * (1f - g), 1f, t);
                ringFlat(buf, ps, new Vec3(0, 0.3, 0), R2 * smooth(Math.min(1f, g * 1.3f)), ICE, 1f - g);
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (d[0] != 1 || age < 0 || age > 14) {
                    continue;
                }
                Vec3 q = v(d[2], d[3], d[4]).subtract(base);
                float k = 1f - age / 14f;
                Vec3 tip = q.add(0, 40 * (1f - c01(age / 5f)), 0);
                put(buf, ps, shard(2), "cosmos", Mode.SOLID, tip.add(0, 22, 0), 2.2f, 22f, 2.2f, 0f, 180f, 0f, PURE, 1f, 1f, 0f, 0f);
                if (age > 5f) {
                    ringFlat(buf, ps, q.add(0, 0.2, 0), 5f * smooth((age - 5f) / 6f), AQUA, k);
                }
                Fx.crack(buf, ps, q.add(0, 0.12, 0), 5f, (float) d[1] * 37f, CYAN, k);
            }
        }
    }
}
