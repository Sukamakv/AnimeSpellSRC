package com.example.jjkspells.shadow.client.render;

import com.example.jjkspells.shadow.entity.*;
import com.example.jjkspells.vfx.Mesh;
import com.example.jjkspells.vfx.MeshRender;
import com.example.jjkspells.vfx.MeshRender.Mode;
import com.example.jjkspells.vfx.Prim;
import com.example.jjkspells.vfx.Xf;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.jjkspells.shadow.client.render.Gfx.*;

/**
 * The five renderers. Each spell has its own vocabulary: Garden is thin lines and afterimages, the Throne is stone and silhouettes,
 * the Serpent is liquid metal, the Counter is almost nothing, Atomic is one sphere that becomes a dome.
 */
public final class SeqRenderers {
    private SeqRenderers() {
    }

    private static Vec3 rel(double x, double y, double z, Vec3 base) {
        return new Vec3(x - base.x, y - base.y, z - base.z);
    }

    private static Vec3 fwd(float yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    private static Vec3 left(float yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec3(Math.cos(r), 0, Math.sin(r));
    }

    private static float yawOf(Entity o, float pt) {
        return Mth.rotLerp(pt, o.yRotO, o.getYRot());
    }

    private static float[] mix(float[] a, float[] b, float f) {
        return MeshRender.mix(a, b, f);
    }

    // ---------------------------------------------------------------- shared meshes

    private static Mesh blade() {
        return MeshRender.cached("shd_blade", ShdModels::blade);
    }

    private static Mesh hilt() {
        return MeshRender.cached("shd_hilt", ShdModels::hilt);
    }

    private static Mesh sheath() {
        return MeshRender.cached("shd_sheath", ShdModels::sheath);
    }

    /** A katana: the hilt at 'at', the blade pointing along dir; 'out' 0..1 is how much of the blade is drawn outside the sheath. */
    private static void katana(MultiBufferSource buf, PoseStack ps, Vec3 at, Vec3 dir, float alpha, boolean translucent) {
        Vec3 d = dir.normalize();
        MeshRender.pushAlong(ps, at, at.add(d));
        MeshRender.draw(buf, ps, hilt(), "katana_saya", translucent ? Mode.TRANS : Mode.SOLID, MeshRender.WHITE, alpha, 0.3f, 0f, 0f);
        ps.translate(0, 0.1, 0);
        MeshRender.draw(buf, ps, blade(), "katana_steel", translucent ? Mode.TRANS : Mode.SOLID, MeshRender.WHITE, alpha, 0.5f, 0f, 0f);
        ps.popPose();
    }

    private static float hit(Entity e, float t, double[] d) {
        return t - (float) d[1];
    }

    // ================================================================ Shadow's Garden

    public static final class Garden extends ShdRenderer<GardenEntity> {
        public Garden(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(GardenEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float sec = t / 20f;
            boolean mine = owner != null && owner == Minecraft.getInstance().player;
            // the caster dissolves into mist
            if (sec < 1.8f) {
                for (int i = 0; i < 9; i++) {
                    float h = hash(i, 3), a = h * 6.283f, rise = c01((sec - 0.1f * (i % 4)) / 0.9f);
                    Vec3 p = new Vec3(Mth.cos(a) * (0.3f + 0.4f * rise), 0.2 + rise * 1.9 * (0.6 + h * 0.5), Mth.sin(a) * (0.3f + 0.4f * rise));
                    float al = 0.42f * life(sec, 0.0f, 0.3f, 0.25f, 0.9f);
                    Fx.soft(buf, ps, tex("shadow_mist"), p, 0.8f + rise * 0.8f, h * 90f + sec * 20f, i % 2 == 0 ? DEEP : ASH, al);
                }
                Fx.glow(buf, ps, tex("shd_glow"), new Vec3(0, 1.0, 0), 1.8f * (1f - c01(Math.abs(sec - 0.6f) / 0.25f)), 0f, PALE, 0.6f);
            }
            List<double[]> fx = e.fx();
            for (double[] d : fx) {
                float dt = t - (float) d[1];
                if (dt < 0) {
                    continue;
                }
                int type = (int) d[0];
                if (type == 2 && dt < 16) {
                    // the afterimage: small, faint, gone in less than a second
                    float al = 0.42f * (1f - dt / 16f) * c01(dt / 2f);
                    Vec3 p = rel(d[2], d[3], d[4], base);
                    Ghost.draw(buf, ps, owner, p, (float) d[5], VIOLET, al, ShdModels.LEAN, 0f, 0.9f);
                    Fx.glow(buf, ps, tex("shadow_eye_glow"), p.add(0, 1.55, 0).add(fwd((float) d[5]).scale(0.25)), 0.22f, 0f, SILVER, al * 1.5f);
                } else if (type == 1 && dt < 12) {
                    Vec3 tp = rel(d[2], d[3], d[4], base), f = rel(d[6], d[7], d[8], base);
                    Vec3 dir = tp.subtract(f);
                    dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
                    Vec3 right = dir.cross(new Vec3(0, 1, 0));
                    right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
                    Vec3 up = right.cross(dir).normalize();
                    float ang = (float) d[5] * 0.95f + 0.45f;
                    Vec3 l = right.scale(Mth.cos(ang)).add(up.scale(Mth.sin(ang)));
                    float prog = smooth(dt / 2.5f), al = (float) Math.pow(1f - dt / 12f, 1.6);
                    Vec3 a = tp.subtract(l.scale(1.5)), b = a.add(l.scale(3.0 * prog));
                    Fx.ribbon(buf, ps, tex("shadow_slash_thin"), true, a, b, cam, 0.11f, VIOLET, al * 0.3f);
                    Fx.ribbon(buf, ps, tex("shadow_slash_thin"), true, a, b, cam, 0.04f, mix(VIOLET, SILVER, 0.65f), al * 0.95f);
                }
                if (type == 1 && mine && sec < 4.5f) {
                    Entity tgt = e.level().getEntity((int) d[9]);
                    if (tgt != null && tgt.isAlive()) {
                        Vec3 mp = tgt.getPosition(pt).subtract(base).add(0, tgt.getBbHeight() + 0.45, 0);
                        float al = 0.3f * (0.7f + 0.3f * Mth.sin(t * 0.3f + (float) d[5]));
                        Fx.glow(buf, ps, tex("execution_mark"), mp, 0.22f, t * 1.5f, VIOLET, al * c01(dt / 6f));
                    }
                }
                if (type == 3 && sec < 4.62f) {
                    // the final line: one clean cut through the aimed target, held until the sword is sheathed
                    float grow = smooth(dt / 7f), pulse = 0.65f + 0.2f * Mth.sin(t * 0.55f);
                    float out = sec < 4.5f ? 1f : 1f - (sec - 4.5f) / 0.12f;
                    Vec3 c = rel(d[2], d[3], d[4], base);
                    float a = (float) Math.toRadians(d[5]);
                    Vec3 dir = new Vec3(Mth.cos(a), 0.28, Mth.sin(a)).normalize();
                    Vec3 p0 = c.subtract(dir.scale(2.6 * grow)), p1 = c.add(dir.scale(2.6 * grow));
                    Fx.ribbon(buf, ps, tex("shadow_slash_thin"), true, p0, p1, cam, 0.1f, VIOLET, 0.28f * out * grow);
                    Fx.ribbon(buf, ps, tex("shadow_slash_thin"), true, p0, p1, cam, 0.035f, SILVER, pulse * out * grow);
                    Fx.glow(buf, ps, tex("shd_glow"), c, 0.35f * grow, 0f, PALE, 0.5f * out);
                } else if (type == 4 && dt < 6) {
                    Fx.glow(buf, ps, tex("shd_glow"), rel(d[2], d[3], d[4], base).add(0, 1, 0), 1.3f * (1f - dt / 6f), 0f, SILVER, 0.5f);
                } else if (type == 5 && dt < 26) {
                    execution(buf, ps, rel(d[2], d[3], d[4], base), dt, cam, (int) d[1]);
                }
            }
            // the sword and its sheath on the caster
            if (owner != null && sec > 2.4f && sec < 5.7f) {
                float yaw = yawOf(owner, pt);
                float vis = c01((sec - 2.4f) / 0.3f) * (1f - smooth((sec - 5.2f) / 0.5f));
                Vec3 ow = ownerP;
                Vec3 mouth = ow.add(0, 1.0, 0).add(left(yaw).scale(0.3));
                Vec3 tipDir = fwd(yaw).scale(-0.8).add(0, -0.55, 0).add(left(yaw).scale(0.15)).normalize();
                Vec3 kojiri = mouth.add(tipDir.scale(1.0));
                MeshRender.pushAlong(ps, kojiri, mouth);
                MeshRender.draw(buf, ps, sheath(), "katana_saya", Mode.SOLID, MeshRender.WHITE, vis, 0.3f, 0f, 0f);
                ps.popPose();
                if (sec < 3.8f) {
                    Vec3 hp = Gfx.hand(owner, pt).subtract(base);
                    Vec3 view = owner.getViewVector(pt);
                    float raise = smooth((sec - 3.0f) / 0.8f);
                    Vec3 dir = view.lerp(new Vec3(0, 1, 0.2), raise);
                    katana(buf, ps, hp, dir, vis, false);
                } else {
                    float p = smooth((sec - 3.8f) / 0.7f);
                    Vec3 bladeStart = mouth.subtract(tipDir.scale(0.95 * (1f - p)));
                    Vec3 hiltAt = bladeStart.subtract(tipDir.scale(0.1));
                    katana(buf, ps, hiltAt, tipDir, vis, false);
                }
                if (sec >= 4.5f && sec < 4.5f + 0.4f) {
                    float dt = (sec - 4.5f) / 0.4f;
                    Fx.glow(buf, ps, tex("shd_ring"), mouth, 0.25f + dt * 1.6f, 0f, SILVER, (1f - dt) * 0.9f);
                    Fx.glow(buf, ps, tex("shd_glow"), mouth, 0.9f * (1f - dt), 0f, WHITE, 0.8f * (1f - dt));
                }
            }
        }

        /** Thin purple cracks and a small burst. Nothing larger. */
        private static void execution(MultiBufferSource buf, PoseStack ps, Vec3 c, float dt, Vec3 cam, int salt) {
            float grow = smooth(dt / 3.5f), al = (float) Math.pow(1f - dt / 26f, 1.3);
            for (int k = 0; k < 8; k++) {
                float a = hash(k, salt) * 6.283f, el = (hash(k, salt + 7) - 0.5f) * 1.6f, len = 1.2f + hash(k, salt + 3) * 1.1f;
                Vec3 dir = new Vec3(Mth.cos(a) * Mth.cos(el), Mth.sin(el), Mth.sin(a) * Mth.cos(el));
                Vec3 b = c.add(dir.scale(len * grow));
                Fx.ribbon(buf, ps, tex("shadow_slash_thin"), true, c, b, cam, 0.03f, VIOLET, al * 0.95f);
                Vec3 mid = c.add(dir.scale(len * grow * 0.55));
                Vec3 side = new Vec3(-dir.z, 0.3, dir.x).normalize().scale(0.45 * grow);
                Fx.ribbon(buf, ps, tex("shadow_slash_thin"), true, mid, mid.add(side), cam, 0.018f, PALE, al * 0.7f);
            }
            if (dt < 7) {
                Fx.glow(buf, ps, tex("shd_glow"), c, 1.1f * (1f - dt / 7f), 0f, PALE, 0.85f * (1f - dt / 7f));
            }
            if (dt < 10) {
                Fx.glow(buf, ps, tex("shd_ring"), c, 0.3f + dt * 0.2f, 0f, SILVER, 0.7f * (1f - dt / 10f));
            }
        }
    }

    // ================================================================ The Nameless King

    public static final class Throne extends ShdRenderer<ThroneEntity> {
        public Throne(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        private static Mesh throne() {
            return MeshRender.cached("shd_throne", ShdModels::throne);
        }

        /** A silhouette: the figure, its sword if the pose has one, and its eyes. */
        private static void figureAt(MultiBufferSource buf, PoseStack ps, Entity owner, int pose, Vec3 at, float yaw, float alpha, float dissolve, float t, float[] tint, int id) {
            if (alpha <= 0.02f) {
                return;
            }
            Vec3 p = at.add(0, dissolve * 0.7, 0);
            float sy = 1f + dissolve * 0.18f;
            // the real one on the throne keeps its colours; the false ones are dark violet
            float[] col = id == 99 ? WHITE : tint == WHITE ? new float[]{0.3f, 0.24f, 0.5f} : tint;
            Ghost.draw(buf, ps, owner, p, yaw, col, alpha, pose, dissolve, sy);
            ps.pushPose();
            ps.translate(at.x, at.y, at.z);
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            if (pose == ShdModels.SWORD || pose == ShdModels.KNEEL) {
                Vec3 hilt = pose == ShdModels.SWORD ? new Vec3(-0.02, 0.95, 0.55) : new Vec3(-0.02, 0.6, 0.55);
                katana(buf, ps, hilt.add(0, dissolve * 0.7, 0), new Vec3(0, -1, 0.03), alpha, true);
            }
            double[] ey = Ghost.eyes(pose);
            Vec3 e0 = new Vec3(ey[0], ey[1] + dissolve * 0.7, ey[2]);
            float flick = 0.75f + 0.25f * Mth.sin(t * 0.37f + id * 1.7f);
            for (int s = -1; s <= 1; s += 2) {
                Fx.glow(buf, ps, tex("shadow_eye_glow"), e0.add(s * 0.11, 0, 0), 0.15f, 0f, LILAC, Math.min(1f, alpha * 1.8f) * flick * (1f - dissolve));
            }
            ps.popPose();
        }

        @Override
        protected void draw(ThroneEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float sec = t / 20f, yaw = e.yaw(), R = e.r1();
            float rise = smooth(sec / 1.5f), fade = 1f - smooth((sec - 9.0f) / 0.9f);
            Vec3 th = ThroneEntity.throneAt(base, yaw).subtract(base);
            Vec3 dir = fwd(yaw);
            // the stage: a thin ring, cracks spreading from the throne
            float stage = rise * fade;
            Fx.ring(buf, ps, tex("shd_ring"), th.add(0, 0.05, 0), R - 0.2f, R, 72, 12f, sec * 0.02f, VIOLET, 0.32f * stage);
            Fx.ring(buf, ps, tex("shd_ring"), th.add(0, 0.05, 0), R * 0.5f - 0.1f, R * 0.5f, 56, 8f, -sec * 0.03f, DEEP, 0.4f * stage);
            Fx.ground(buf, ps, tex("throne_glow"), true, th.add(0, 0.06, 0), 3.5f + 5f * rise, yaw + 15f, VIOLET, 0.8f * stage);
            Fx.ground(buf, ps, tex("shadow_mist"), false, th.add(0, 0.08, 0), 2.6f + 3.5f * rise, sec * 6f, DEEP, 0.55f * stage);
            // the throne itself
            Vec3 tp = th.add(0, -3.9 * (1f - rise) * (1f - rise), 0);
            if (sec < 9.0f) {
                Fx.mesh(buf, ps, throne(), "throne_cracks", Mode.SOLID, WHITE, 1f, 0.25f, tp, yaw, 1f);
            } else {
                float dis = 1f - fade;
                Fx.mesh(buf, ps, throne(), "throne_cracks", Mode.TRANS, WHITE, fade, 0.25f, tp.add(0, -dis * 1.4, 0), yaw, 1f);
                Fx.mesh(buf, ps, throne(), "illusion_dissolve", Mode.ADD, VIOLET, dis * 0.6f, 1f, tp.add(0, -dis * 1.4, 0), yaw, 1.02f, 1.02f, 1.02f, sec * 0.004f, -sec * 0.01f);
            }
            // the cracks wake up
            float pulse = 0.7f + 0.3f * Mth.sin(sec * 3f);
            Fx.mesh(buf, ps, throne(), "throne_glow_tile", Mode.ADD, VIOLET, 0.35f * pulse * stage, 1f, tp, yaw, 1.005f, 1.005f, 1.005f, 0f, 0f);
            // the real one on the throne (the caster is hidden meanwhile)
            if (sec >= 1.5f && sec < 7.95f) {
                float a = 0.9f * smooth((sec - 1.5f) / 0.5f);
                Vec3 seat = th.add(dir.scale(0.1)).add(0, 0.55, 0);
                figureAt(buf, ps, owner, ShdModels.SEATED, seat, yaw, a, 0f, t, WHITE, 99);
            }
            // the false ones
            Map<Integer, Float> gone = new HashMap<>();
            List<double[]> fx = e.fx();
            for (double[] d : fx) {
                if ((int) d[0] == 2) {
                    gone.put((int) d[2], (float) d[1]);
                }
            }
            for (double[] d : fx) {
                int type = (int) d[0];
                float dt = t - (float) d[1];
                if (dt < 0) {
                    continue;
                }
                if (type == 1) {
                    int id = (int) d[7], pose = (int) d[6];
                    Float g = gone.get(id);
                    float dis = g == null ? 0f : smooth((t - g) / 14f);
                    float appear = smooth(dt / 10f);
                    float flick = 0.82f + 0.18f * Mth.sin(t * 0.43f + id * 2.1f);
                    boolean glitch = hash((int) (t / 3f), id) > 0.93f;
                    Vec3 p = rel(d[2], d[3], d[4], base);
                    if (glitch) {
                        p = p.add((hash((int) t, id + 4) - 0.5f) * 0.25, 0, (hash((int) t, id + 9) - 0.5f) * 0.25);
                    }
                    float sway = pose == ShdModels.STILL ? 0f : 2.5f * Mth.sin(t * 0.05f + id);
                    float a = 0.62f * appear * (1f - dis) * flick * (glitch ? 0.45f : 1f);
                    figureAt(buf, ps, owner, pose, p, (float) d[5] + sway, a, dis, t, WHITE, id);
                    if (dt < 14) {
                        Fx.soft(buf, ps, tex("shadow_mist"), p.add(0, 0.9, 0), 1.5f, t * 4f, DEEP, 0.4f * (1f - dt / 14f));
                    }
                } else if (type == 3 && dt < 30) {
                    counter(buf, ps, owner, d, dt, base, cam);
                } else if (type == 4 && sec < 8.6f) {
                    Vec3 c = rel(d[2], d[3], d[4], base).add(0, 0.08, 0);
                    float k = smooth(dt / 14f), a = 0.7f * c01(dt / 6f) * (sec < 8.5f ? 1f : 1f - (sec - 8.5f) / 0.1f);
                    Fx.ring(buf, ps, tex("shd_ring"), c, 1.7f + 2.3f * (1f - k) - 0.1f, 1.7f + 2.3f * (1f - k), 40, 6f, 0f, LILAC, a);
                    Fx.ground(buf, ps, tex("execution_mark"), true, c, 1.1f, t * 2f, VIOLET, a * 0.8f);
                } else if (type == 5 && dt < 34) {
                    judgment(buf, ps, rel(d[2], d[3], d[4], base), dt, cam, R);
                }
            }
        }

        /** The silhouette that appears behind an attacker and cuts through it. */
        private static void counter(MultiBufferSource buf, PoseStack ps, Entity owner, double[] d, float dt, Vec3 base, Vec3 cam) {
            Vec3 c = rel(d[2], d[3], d[4], base);
            float yaw = (float) d[5];
            Vec3 behind = c.add(-fwd(yaw).x * -1.7 * 0 + (-fwd(yaw).x) * 1.7, -0.9, (-fwd(yaw).z) * 1.7);
            float a = (dt < 3 ? dt / 3f : 1f - (dt - 3f) / 27f) * 0.85f;
            figureAt(buf, ps, owner, ShdModels.LEAN, behind, yaw, a, 0f, dt, VIOLET, (int) d[1]);
            if (dt < 14) {
                float prog = smooth(dt / 3f), al = (float) Math.pow(1f - dt / 14f, 1.4);
                Vec3 l = left(yaw).scale(1.0).add(0, 0.85, 0).normalize();
                Vec3 p0 = c.subtract(l.scale(1.7)), p1 = p0.add(l.scale(3.4 * prog));
                Fx.ribbon(buf, ps, tex("shadow_slash_thin"), true, p0, p1, cam, 0.075f, VIOLET, 0.35f * al);
                Fx.ribbon(buf, ps, tex("shadow_slash_thin"), true, p0, p1, cam, 0.03f, SILVER, al);
                Fx.glow(buf, ps, tex("counter_impact"), c, 1.1f * (1f - dt / 14f), dt * 9f, PALE, 0.7f * al);
            }
        }

        private static void judgment(MultiBufferSource buf, PoseStack ps, Vec3 c, float dt, Vec3 cam, float stageR) {
            float k = smooth(dt / 5f), al = (float) Math.pow(1f - dt / 34f, 1.2);
            Vec3 a = c.add(0, -0.5, 0), b = c.add(0, -0.5 + 8.0 * k, 0);
            Fx.ribbon(buf, ps, tex("shadow_slash_thin"), true, a, b, cam, 0.16f, VIOLET, 0.4f * al);
            Fx.ribbon(buf, ps, tex("shadow_slash_thin"), true, a, b, cam, 0.05f, WHITE, al);
            for (int i = 0; i < 4; i++) {
                float ang = hash(i, 5) * 3.14159f, tilt = (hash(i, 8) - 0.5f) * 1.2f;
                Vec3 l = new Vec3(Mth.cos(ang), tilt, Mth.sin(ang)).normalize();
                Vec3 m = c.add(0, 1.0, 0);
                Fx.ribbon(buf, ps, tex("shadow_slash_thin"), true, m.subtract(l.scale(4.5 * k)), m.add(l.scale(4.5 * k)), cam, 0.04f, PALE, al * 0.8f);
            }
            float rr = 15f * (1f - (1f - c01(dt / 14f)) * (1f - c01(dt / 14f)));
            Fx.ring(buf, ps, tex("shd_ring"), c.add(0, 0.1, 0), Math.max(0f, rr - 0.5f), rr, 72, 14f, 0f, LILAC, 0.8f * al);
            Fx.ground(buf, ps, tex("throne_glow"), true, c.add(0, 0.07, 0), 7f, dt * 3f, VIOLET, 0.9f * al);
            if (dt < 8) {
                Fx.glow(buf, ps, tex("shd_glow"), c.add(0, 1.0, 0), 2.4f * (1f - dt / 8f), 0f, WHITE, 0.8f * (1f - dt / 8f));
            }
        }
    }

    // ================================================================ Black Serpent's Requiem

    public static final class Serpent extends ShdRenderer<SerpentEntity> {
        private static final int N = 28;
        private static final double SPACING = 0.66;

        public Serpent(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        private static float radiusAt(float u) {
            // neck, shoulders, long body, thin tail
            float[][] k = {{0f, 0.5f}, {0.08f, 0.66f}, {0.22f, 0.78f}, {0.55f, 0.6f}, {0.85f, 0.3f}, {1f, 0.07f}};
            for (int i = 1; i < k.length; i++) {
                if (u <= k[i][0]) {
                    float f = (u - k[i - 1][0]) / (k[i][0] - k[i - 1][0]);
                    return k[i - 1][1] + (k[i][1] - k[i - 1][1]) * f;
                }
            }
            return k[k.length - 1][1];
        }

        private static Mesh tube(double[][] pts, int m, double[] rad, double v) {
            double[][] path = new double[m][];
            for (int i = 0; i < m; i++) {
                path[i] = pts[i];
            }
            return Prim.tube(path, java.util.Arrays.copyOf(rad, m), 10, v, false, true);
        }

        @Override
        protected void draw(SerpentEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float sec = t / 20f;
            List<double[]> fx = e.fx();
            if (e.clientPath == null) {
                for (double[] d : fx) {
                    if ((int) d[0] == 1) {
                        e.clientPath = new SerpentPath(d[5], d[6], d[7], d[2], d[3], d[4], e.r2());
                        break;
                    }
                }
            }
            SerpentPath path = e.clientPath;
            if (path == null) {
                return;
            }
            for (double[] d : fx) {
                if ((int) d[0] == 4) {
                    path.setCoil(d[2], d[3], d[4]);
                }
            }
            Vec3 pool = rel(path.bx, path.by, path.bz, base);
            // the pool the serpent is made of
            float grow = smooth(sec / 0.9f) * (1f - smooth((sec - 7.4f) / 0.6f));
            if (grow > 0.02f) {
                float wob = 1f + 0.05f * Mth.sin(t * 0.4f);
                Fx.ground(buf, ps, tex("slime_liquid"), false, pool.add(0, 0.04, 0), 3.4f * grow * wob, sec * 12f, MeshRender.WHITE, 0.95f);
                Fx.ground(buf, ps, tex("slime_splash"), false, pool.add(0, 0.07, 0), 4.2f * grow, -sec * 7f, SLIME, 0.8f);
                if (sec < 1.6f) {
                    float rim = 0.55f * (1f - smooth(sec / 1.6f));
                    Fx.glow(buf, ps, tex("shd_glow"), pool.add(0, 0.6, 0), 3f * grow, 0f, SLIMEHI, rim);
                }
            }
            if (sec >= 1.0f && sec < 8.0f) {
                double[][] sp = new double[N][3];
                path.spine(sec, N, SPACING, sp);
                // points the route has not produced yet stay in the pool; they are dropped so the tube has no zero-length steps
                double[][] pts = new double[N][];
                double[] rad = new double[N];
                int m = 0;
                for (int i = 0; i < N; i++) {
                    double px = sp[i][0] - base.x, py = sp[i][1] - base.y, pz = sp[i][2] - base.z;
                    if (m > 0) {
                        double dx = px - pts[m - 1][0], dy = py - pts[m - 1][1], dz = pz - pts[m - 1][2];
                        if (dx * dx + dy * dy + dz * dz < 0.0004) {
                            continue;
                        }
                    }
                    pts[m] = new double[]{px, py, pz};
                    float u = i / (float) (N - 1);
                    float melt = smooth((sec - (7.0f + (1f - u) * 0.5f)) / 0.5f);
                    rad[m] = radiusAt(u) * (1f - melt) * (1f + 0.07f * Mth.sin(i * 1.3f - sec * 6f));
                    m++;
                }
                if (m >= 3) {
                    for (int i = 0; i < 3; i++) {
                        rad[m - 1 - i] *= Math.min(1.0, (i + 0.6) / 3.0);
                    }
                    Mesh body = tube(pts, m, rad, 0.35);
                    ps.pushPose();
                    MeshRender.draw(buf, ps, body, "slime_serpent_body", Mode.SOLID, MeshRender.WHITE, 1f, 0.3f, 0f, 0f);
                    double[] rad2 = new double[m];
                    for (int i = 0; i < m; i++) {
                        rad2[i] = rad[i] * 1.035 + (rad[i] > 0.01 ? 0.015 : 0.0);
                    }
                    float veins = 0.3f + 0.25f * Mth.sin(sec * 5f);
                    if (sec >= 5.45f && sec < 6.9f) {
                        veins = 0.7f + 0.3f * Mth.sin(sec * 18f);
                    }
                    MeshRender.draw(buf, ps, tube(pts, m, rad2, 0.35), "slime_energy_veins", Mode.ADD, SLIMEHI, veins, 1f, 0f, -sec * 0.3f);
                    ps.popPose();
                }
                headAndSpear(buf, ps, sp, base, sec, cam);
                if (sec >= 4.0f && sec < 6.1f) {
                    tendrils(buf, ps, sp, base, sec);
                }
            }
            for (double[] d : fx) {
                float dt = t - (float) d[1];
                if (dt < 0) {
                    continue;
                }
                int type = (int) d[0];
                if (type == 2 && dt < 30) {
                    impact(buf, ps, rel(d[2], d[3], d[4], base), dt, cam);
                } else if (type == 3 && dt < 12) {
                    strike(buf, ps, d, dt, base, cam);
                } else if (type == 5 && dt < 20) {
                    Vec3 c = rel(d[2], d[3], d[4], base);
                    float k = dt / 20f;
                    Fx.glow(buf, ps, tex("shd_glow"), c, 2.6f * (1f - k), 0f, SLIMEHI, 0.8f * (1f - k));
                    Fx.glow(buf, ps, tex("shd_ring"), c, 0.6f + dt * 0.3f, 0f, SLIMEHI, 0.8f * (1f - k));
                    Fx.soft(buf, ps, tex("slime_splash"), c, 2.2f + dt * 0.1f, dt * 6f, SLIME, 0.7f * (1f - k));
                }
            }
        }

        private void headAndSpear(MultiBufferSource buf, PoseStack ps, double[][] sp, Vec3 base, float sec, Vec3 cam) {
            Vec3 h = new Vec3(sp[0][0] - base.x, sp[0][1] - base.y, sp[0][2] - base.z);
            Vec3 n = new Vec3(sp[1][0] - base.x, sp[1][1] - base.y, sp[1][2] - base.z);
            Vec3 dir = h.subtract(n);
            if (dir.lengthSqr() < 1e-6) {
                dir = new Vec3(0, 1, 0);
            }
            dir = dir.normalize();
            float spearAmt = smooth((sec - 2.85f) / 0.2f) * (1f - smooth((sec - 4.0f) / 0.35f));
            float headAmt = 1f - spearAmt;
            float melt = smooth((sec - 7.0f) / 0.5f);
            if (headAmt > 0.03f && melt < 0.98f) {
                float s = headAmt * (1f - melt);
                MeshRender.pushFacing(ps, h.add(dir.scale(0.35)), dir);
                ps.scale(s, s, s);
                MeshRender.draw(buf, ps, MeshRender.cached("shd_shead", ShdModels::serpentHead), "slime_serpent_body", Mode.SOLID, MeshRender.WHITE, 1f, 0.35f, 0f, 0f);
                ps.popPose();
                Vec3 right = dir.cross(new Vec3(0, 1, 0));
                right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
                Vec3 up = right.cross(dir).normalize();
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    Vec3 eye = h.add(dir.scale(0.75 * s)).add(right.scale(sgn * 0.27 * s)).add(up.scale(0.12 * s));
                    Fx.glow(buf, ps, tex("slime_serpent_eyes"), eye, 0.38f * s, 0f, WHITE, 0.95f * s);
                }
            }
            if (spearAmt > 0.03f) {
                MeshRender.pushFacing(ps, h.subtract(dir.scale(0.2)), dir);
                ps.scale(0.8f * spearAmt + 0.2f, 0.8f * spearAmt + 0.2f, 5.0f * spearAmt);
                MeshRender.draw(buf, ps, MeshRender.cached("shd_spear", ShdModels::spear), "slime_serpent_body", Mode.SOLID, MeshRender.WHITE, 1f, 0.4f, 0f, 0f);
                MeshRender.draw(buf, ps, MeshRender.cached("shd_spear", ShdModels::spear), "slime_energy_veins", Mode.ADD, SLIMEHI, 0.7f * spearAmt, 1f, 0f, 0f);
                ps.popPose();
                if (sec >= 3.0f && sec < 3.5f) {
                    Vec3 back = h.subtract(dir.scale(6.0));
                    Fx.ribbon(buf, ps, tex("slime_energy_veins"), true, back, h, cam, 0.5f, SLIMEHI, 0.5f * (1f - (sec - 3.0f) / 0.5f));
                }
            }
        }

        /** Tendrils grow from the body while the predator circles. */
        private void tendrils(MultiBufferSource buf, PoseStack ps, double[][] sp, Vec3 base, float sec) {
            float amt = smooth((sec - 4.0f) / 0.3f) * (1f - smooth((sec - 5.7f) / 0.4f));
            if (amt < 0.03f) {
                return;
            }
            Mesh tent = MeshRender.cached("shd_tent", () -> Prim.tentacle(11, 2.4, 0.16, 1.9, 14, 6));
            for (int k = 0; k < 6; k++) {
                int i = 3 + k * 4;
                if (i + 1 >= N) {
                    break;
                }
                Vec3 p = new Vec3(sp[i][0] - base.x, sp[i][1] - base.y, sp[i][2] - base.z);
                Vec3 tan = new Vec3(sp[i - 1][0] - sp[i + 1][0], sp[i - 1][1] - sp[i + 1][1], sp[i - 1][2] - sp[i + 1][2]);
                if (tan.lengthSqr() < 1e-6) {
                    continue;
                }
                tan = tan.normalize();
                Vec3 perp = tan.cross(new Vec3(0, 1, 0));
                perp = perp.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : perp.normalize();
                Vec3 perp2 = tan.cross(perp);
                float th = k * 1.9f + sec * 0.9f;
                Vec3 out = perp.scale(Mth.cos(th)).add(perp2.scale(Mth.sin(th))).normalize();
                MeshRender.pushAlong(ps, p, p.add(out));
                float s = amt * (0.8f + 0.25f * Mth.sin(sec * 6f + k));
                ps.scale(s, s, s);
                MeshRender.draw(buf, ps, tent, "slime_serpent_body", Mode.SOLID, MeshRender.WHITE, 1f, 0.3f, 0f, 0f);
                ps.popPose();
            }
        }

        /** One lash from the head to an enemy. */
        private void strike(MultiBufferSource buf, PoseStack ps, double[] d, float dt, Vec3 base, Vec3 cam) {
            Vec3 a = rel(d[6], d[7], d[8], base), b = rel(d[2], d[3], d[4], base);
            float ext = smooth(dt / 3f) * (1f - smooth((dt - 7f) / 5f));
            Vec3 mid = a.add(b).scale(0.5).add(0, 1.2, 0);
            int steps = 9;
            double[][] pts = new double[steps + 1][3];
            double[] rad = new double[steps + 1];
            for (int i = 0; i <= steps; i++) {
                float u = i / (float) steps * ext;
                Vec3 q = a.scale((1 - u) * (1 - u)).add(mid.scale(2 * u * (1 - u))).add(b.scale(u * u));
                pts[i] = new double[]{q.x + Mth.sin(i * 1.4f + dt) * 0.07, q.y, q.z + Mth.cos(i * 1.2f + dt) * 0.07};
                rad[i] = 0.2 * (1.0 - u * 0.75);
            }
            if (ext < 0.06f) {
                return;
            }
            Mesh tube = Prim.tube(pts, rad, 7, 0.5, false, true);
            ps.pushPose();
            MeshRender.draw(buf, ps, tube, "slime_serpent_body", Mode.SOLID, MeshRender.WHITE, 1f, 0.35f, 0f, 0f);
            MeshRender.draw(buf, ps, tube, "slime_energy_veins", Mode.ADD, SLIMEHI, 0.5f, 1f, 0f, 0f);
            ps.popPose();
            if (dt < 4) {
                Fx.glow(buf, ps, tex("shd_glow"), b, 0.9f * (1f - dt / 4f), 0f, SLIMEHI, 0.7f);
            }
        }

        /** Where the spear lands: a ring of black spikes and a splash on the ground. */
        private void impact(MultiBufferSource buf, PoseStack ps, Vec3 c, float dt, Vec3 cam) {
            float k = dt / 30f;
            Fx.ground(buf, ps, tex("slime_splash"), false, c.add(0, 0.06, 0), 2.0f + 3.2f * smooth(dt / 10f), dt * 3f, SLIME, 0.9f * (1f - smooth((dt - 10f) / 20f)));
            Fx.glow(buf, ps, tex("shd_ring"), c.add(0, 0.2, 0), 0.4f + dt * 0.35f, 0f, SLIMEHI, 0.8f * (1f - k));
            Mesh spike = MeshRender.cached("shd_spike", () -> Prim.shard(21, 1.0, 0.12));
            float pop = smooth(dt / 4f) * (1f - smooth((dt - 12f) / 14f));
            for (int i = 0; i < 9; i++) {
                float a = i * 0.698f + hash(i, 1) * 0.3f, r = 1.1f + hash(i, 2) * 1.5f;
                Vec3 at = c.add(Mth.cos(a) * r, 0, Mth.sin(a) * r);
                MeshRender.pushAlong(ps, at, at.add(Mth.cos(a) * 0.5, 1.0, Mth.sin(a) * 0.5));
                float s = pop * (0.9f + hash(i, 3) * 1.1f);
                ps.scale(s, s, s);
                MeshRender.draw(buf, ps, spike, "slime_serpent_body", Mode.SOLID, MeshRender.WHITE, 1f, 0.3f, 0f, 0f);
                ps.popPose();
            }
        }
    }

    // ================================================================ The Perfect Counter

    public static final class Counter extends ShdRenderer<CounterEntity> {
        public Counter(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(CounterEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float sec = t / 20f;
            List<double[]> fx = e.fx();
            double[] counter = null, slash = null;
            boolean missed = false;
            for (double[] d : fx) {
                switch ((int) d[0]) {
                    case 1 -> counter = d;
                    case 2 -> slash = d;
                    case 3 -> missed = true;
                    default -> {
                    }
                }
            }
            float window = 3f, perfectFrom = 2f;
            float end = counter != null ? (float) counter[1] / 20f : window;
            boolean waiting = counter == null && !missed && sec < window;
            // the stance: sword lowered, a faint ring that tightens, a heartbeat now and then
            if (owner != null && sec < (counter != null ? (float) counter[1] / 20f + 1.2f : window + 0.6f)) {
                float yaw = yawOf(owner, pt);
                float lowerK = smooth(sec / 0.5f);
                Vec3 grip = ownerP.add(0, 0.95, 0).add(left(yaw).scale(-0.28)).add(fwd(yaw).scale(0.22));
                Vec3 dir = fwd(yaw).scale(1.0 - lowerK).add(0, -1.0 * lowerK + (1.0 - lowerK) * 0.15, 0).add(fwd(yaw).scale(0.12 * lowerK));
                float vis = 1f - smooth((sec - end - 0.9f) / 0.4f);
                if (counter == null || t < counter[1] + 3) {
                    katana(buf, ps, grip, dir, vis, false);
                }
                float k = c01(sec / window);
                float ringR = Gfx.lerp(2.6f, 1.2f, smooth(k));
                float a = (waiting ? 0.28f : 0.1f) * c01(sec / 0.4f);
                boolean perfect = sec >= perfectFrom && waiting;
                Fx.ground(buf, ps, tex("counter_mist"), false, ownerP.add(0, 0.05, 0), ringR, sec * 15f, perfect ? SILVER : DEEP, a * (perfect ? 1.4f : 1f));
                for (int i = 0; i < 3; i++) {
                    float hb = sec - (i == 0 ? 0.0f : i == 1 ? 1.0f : 2.0f);
                    if (hb >= 0f && hb < 0.5f && waiting) {
                        float f = hb / 0.5f;
                        Fx.glow(buf, ps, tex("shd_ring"), ownerP.add(0, 0.12, 0), 0.4f + f * 1.2f, 0f, i == 2 ? SILVER : VIOLET, 0.45f * (1f - f));
                    }
                }
                // the glint of an eye when the perfect moment opens
                float gl = sec - perfectFrom;
                if (gl >= 0f && gl < 0.45f && owner != null) {
                    float f = gl / 0.45f;
                    Vec3 eye = ownerP.add(0, owner.getEyeHeight() - 0.02, 0).add(fwd(yaw).scale(0.28)).add(left(yaw).scale(0.1));
                    Fx.glow(buf, ps, tex("shadow_eye_glint"), eye, 0.5f * (1f - f * 0.5f), 45f, WHITE, 1f - f);
                }
            }
            if (counter != null) {
                float dt = t - (float) counter[1];
                boolean perfect = counter[2] > 0.5;
                Vec3 from = rel(counter[6], counter[7], counter[8], base), to = rel(counter[3], counter[4], counter[5], base);
                if (dt < 14) {
                    float al = 0.5f * (1f - dt / 14f) * c01(dt / 1.5f);
                    Ghost.draw(buf, ps, owner, from, owner != null ? yawOf(owner, pt) : 0f, perfect ? SILVER : VIOLET, al, ShdModels.STILL, 0f, 1f);
                }
                if (dt < 7) {
                    float k = 1f - dt / 7f;
                    Vec3 a = from.add(0, 1.0, 0), b = to;
                    Fx.ribbon(buf, ps, tex("perfect_counter_flash"), true, a, b, cam, perfect ? 0.22f : 0.12f, perfect ? SILVER : PALE, 0.8f * k);
                }
            }
            if (slash != null) {
                float dt = t - (float) slash[1];
                boolean perfect = slash[6] > 0.5;
                if (dt >= 0 && dt < 16) {
                    Vec3 c = rel(slash[2], slash[3], slash[4], base);
                    float yaw = (float) slash[5];
                    float len = perfect ? 3.0f : 1.9f;
                    float prog = smooth(dt / 2.5f), al = (float) Math.pow(1f - dt / 16f, 1.5);
                    Vec3 l = left(yaw).add(0, 0.7, 0).normalize();
                    Vec3 p0 = c.subtract(l.scale(len * 0.5)), p1 = p0.add(l.scale(len * prog));
                    Fx.ribbon(buf, ps, tex("counter_impact"), true, p0, p1, cam, perfect ? 0.1f : 0.06f, perfect ? WHITE : SILVER, al);
                    if (dt < 8) {
                        Fx.glow(buf, ps, tex("counter_impact"), c, (perfect ? 1.6f : 0.9f) * (1f - dt / 8f), dt * 11f, perfect ? WHITE : PALE, 0.9f * (1f - dt / 8f));
                    }
                    if (perfect && dt < 12) {
                        Fx.glow(buf, ps, tex("shd_ring"), c, 0.5f + dt * 0.28f, 0f, SILVER, 0.7f * (1f - dt / 12f));
                        Fx.glow(buf, ps, tex("perfect_counter_flash"), c, 2.4f * (1f - dt / 12f), 0f, WHITE, 0.6f * (1f - dt / 12f));
                    }
                }
            }
        }
    }

    // ================================================================ I Am Atomic

    /**
     * Anime accurate: the magic is HIS. Violet streams on the body and the ebony sword, flat interlocking circles, a calm declaration,
     * one stroke of the sword and one luminous torrent of blue-violet light at the target. Nothing is thrown, nothing is a sphere.
     */
    public static final class Atomic extends ShdRenderer<AtomicEntity> {
        private static final float[] DEEPV = {0.275f, 0.067f, 0.549f}, PURP = {0.541f, 0.188f, 0.949f}, LAV = {0.757f, 0.482f, 1f}, VWHITE = {0.949f, 0.863f, 1f};

        public Atomic(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        private static Mesh dome() {
            return MeshRender.cached("shd_adome", () -> ShdModels.dome(16, 48));
        }

        private static Mesh domeIn() {
            return MeshRender.cached("shd_adome_in", () -> ShdModels.invert(ShdModels.dome(16, 48)));
        }

        private static Mesh eBlade() {
            return MeshRender.cached("shd_eblade", ShdModels::ebonyBlade);
        }

        private static Mesh eHilt() {
            return MeshRender.cached("shd_ehilt", ShdModels::ebonyHilt);
        }

        private static float easeOut(float k) {
            k = c01(k);
            return 1f - (1f - k) * (1f - k) * (1f - k);
        }

        /** The ebony sword: a long dark blade; 'lit' (0..1) is the violet energy running along it. */
        private static void sword(MultiBufferSource buf, PoseStack ps, Vec3 at, Vec3 dir, float vis, float lit, float sec, float[] col) {
            if (vis <= 0.01f) {
                return;
            }
            Vec3 d = dir.normalize();
            MeshRender.pushAlong(ps, at, at.add(d));
            MeshRender.draw(buf, ps, eHilt(), "ebony_sword", Mode.SOLID, MeshRender.WHITE, vis, 0.1f, 0f, 0f);
            ps.translate(0, 0.1, 0);
            MeshRender.draw(buf, ps, eBlade(), "ebony_sword", Mode.SOLID, MeshRender.WHITE, vis, 0.1f, 0f, 0f);
            if (lit > 0.02f) {
                ps.pushPose();
                ps.scale(1.9f, 1f, 3.6f);
                MeshRender.draw(buf, ps, eBlade(), "atomic_sword_energy", Mode.ADD, col, 0.95f * lit * vis, 1f, -sec * 0.7f, 0f);
                ps.popPose();
                ps.pushPose();
                ps.scale(3.6f, 1f, 7.5f);
                MeshRender.draw(buf, ps, eBlade(), "atomic_sword_energy", Mode.ADD, col, 0.38f * lit * vis, 1f, sec * 0.45f, 0.3f);
                ps.popPose();
            }
            ps.popPose();
        }

        /** Where the sword points: down, rising, upright for the declaration, one stroke at the target, lowered again. */
        private static Vec3 swordDir(float sec, Vec3 aim) {
            Vec3 d0 = aim.scale(0.5).add(0, -0.85, 0), d1 = aim.scale(0.85).add(0, 0.5, 0), d2 = aim.scale(0.2).add(0, 1.0, 0), d3 = aim.scale(1.0).add(0, -0.04, 0), d4 = aim.scale(0.6).add(0, -0.8, 0);
            Vec3 d;
            if (sec < 1.0f) {
                d = d0;
            } else if (sec < 2.5f) {
                d = d0.lerp(d1, smooth((sec - 1.0f) / 1.5f));
            } else if (sec < 4.5f) {
                d = d1.lerp(d2, smooth((sec - 2.5f) / 2.0f));
            } else if (sec < 7.0f) {
                d = d2.add(aim.scale(0.015 * Mth.sin(sec * 9f)));
            } else if (sec < 7.28f) {
                d = d2.lerp(d3, smooth((sec - 7.0f) / 0.28f));
            } else if (sec < 9.0f) {
                d = d3;
            } else {
                d = d3.lerp(d4, smooth((sec - 9.0f) / 1.5f));
            }
            return d.normalize();
        }

        private static void circle(MultiBufferSource buf, PoseStack ps, Vec3 c, double y, float radius, float rot, float[] col, float alpha) {
            Fx.ground(buf, ps, tex("atomic_magic_circle"), true, c.add(0, y, 0), radius, rot, col, alpha);
        }

        @Override
        protected void draw(AtomicEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float sec = t / 20f, R = e.r1(), S = e.r2();
            float yaw = owner != null ? yawOf(owner, pt) : e.yaw();
            Vec3 feet = ownerP;
            Vec3 aim = new Vec3(-feet.x, 0, -feet.z);
            aim = aim.lengthSqr() < 4.0 ? fwd(yaw) : aim.normalize();
            Vec3 hand = feet.add(0, 1.05, 0).add(fwd(yaw).scale(0.35)).add(left(yaw).scale(-0.3));
            Vec3 dir = swordDir(sec, aim);
            Vec3 tip = hand.add(dir.scale(1.62));

            float rev = smooth(sec / 2.0f), form = smooth((sec - 2.0f) / 2.5f), decl = smooth((sec - 4.5f) / 1.5f);
            float K = sec < 7.0f ? 1f : 1f - smooth((sec - 7.0f) / 1.2f);
            float power = (0.2f * rev + 0.35f * form + 0.45f * decl);
            float[] lit = mix(PURP, VWHITE, decl * 0.85f);

            // ------------------------------------------------ the sword itself, from the first second to the last
            float vis = smooth(sec / 0.8f) * (sec < 13.0f ? 1f : 1f - smooth((sec - 13.0f) / 1.3f));
            float swordLit = (0.12f + 0.88f * smooth(sec / 6.0f)) * (sec < 7.0f ? 1f : 1f - 0.7f * smooth((sec - 7.2f) / 1.8f)) * (1f + 0.08f * Mth.sin(sec * 7f));
            if (sec > 9.0f) {
                swordLit *= 1f - smooth((sec - 9.0f) / 2.0f);
            }
            sword(buf, ps, hand, dir, vis, c01(swordLit), sec, lit);

            if (sec < 8.4f) {
                // ------------------------------------------------ 1. revelation: streams along the body, arm and blade, thin lines in the air
                Vec3 c0 = feet.add(0, 0.05, 0);
                for (int k = 0; k < 6; k++) {
                    float ph = k / 6f * 6.283f;
                    Vec3 prev = null;
                    for (int j = 0; j <= 12; j++) {
                        float f = j / 12f;
                        float a = ph + f * 5.4f + sec * (0.9f + 0.25f * (k % 3)) * (1f - 0.8f * decl);
                        float rr = (0.55f - 0.25f * f) * (1f + 0.15f * Mth.sin(sec * 2f + k));
                        Vec3 p = feet.add(Mth.cos(a) * rr, 0.1 + f * 2.05 * (0.55f + 0.45f * rev), Mth.sin(a) * rr);
                        if (prev != null) {
                            float seg = Mth.sin(f * 3.14159f);
                            Fx.ribbon(buf, ps, tex("atomic_energy_stream"), true, prev, p, cam, 0.075f, lit, 0.8f * rev * K * seg);
                        }
                        prev = p;
                    }
                }
                // the current runs up the arm and into the sword
                Vec3 shoulder = feet.add(0, 1.5, 0).add(left(yaw).scale(-0.3));
                Fx.ribbon(buf, ps, tex("atomic_energy_stream"), true, shoulder, hand, cam, 0.11f, lit, 0.85f * rev * K);
                Fx.ribbon(buf, ps, tex("atomic_energy_stream"), true, feet.add(0, 0.9, 0), hand, cam, 0.08f, PURP, 0.5f * rev * K);
                Fx.ribbon(buf, ps, tex("atomic_energy_stream"), true, hand, tip, cam, 0.13f, mix(PURP, VWHITE, decl), (0.55f + 0.45f * decl) * rev * K);
                for (int k = 0; k < 4; k++) {
                    Vec3 q = hand.add(dir.scale(0.4 + 0.4 * k));
                    Fx.glow(buf, ps, tex("shd_glow"), q, (0.35f + 0.7f * decl) * (0.7f + 0.3f * Mth.sin(sec * 8f + k)), 0f, lit, 0.5f * rev * K);
                }
                Fx.glow(buf, ps, tex("atomic_release_flash"), tip, 0.6f + 1.8f * decl * decl, sec * 20f, mix(LAV, VWHITE, decl), (0.25f + 0.65f * decl) * rev * K);
                // thin lines hanging in the air, slowly drifting
                for (int i = 0; i < 16; i++) {
                    float h1 = hash(i, 31), h2 = hash(i, 32), h3 = hash(i, 33), a = h1 * 6.283f + sec * 0.06f * (h2 - 0.5f);
                    float rr = 1.6f + 6f * h2, len = 1.5f + 3.5f * h3;
                    Vec3 p = feet.add(Mth.cos(a) * rr, 0.6 + 3.2 * h3 + 0.4 * Mth.sin(sec * 0.7f + i), Mth.sin(a) * rr);
                    Vec3 dd = new Vec3(Mth.cos(a + 1.57f) * 0.7, 0.55 + 0.3 * h1, Mth.sin(a + 1.57f) * 0.7).normalize().scale(len);
                    float pulse = 0.5f + 0.5f * Mth.sin(sec * 1.3f + i * 2.1f);
                    Fx.ribbon(buf, ps, tex("atomic_energy_stream"), true, p, p.add(dd), cam, 0.045f, mix(DEEPV, LAV, h2), 0.55f * rev * K * pulse * (0.4f + 0.6f * form));
                }
                // particles that rise from the ground, and later are drawn in to the sword
                for (int i = 0; i < 70; i++) {
                    float h1 = hash(i, 41), h2 = hash(i, 42), h3 = hash(i, 43), h4 = hash(i, 44);
                    float ph = (sec * (0.1f + 0.14f * h1) + h2) % 1f;
                    float a = h3 * 6.283f + ph * 2.0f;
                    float rr = (0.4f + 2.6f * h4) * (1f - 0.3f * ph);
                    Vec3 p = feet.add(Mth.cos(a) * rr, 0.1 + ph * 3.8, Mth.sin(a) * rr);
                    Fx.glow(buf, ps, tex("atomic_residual_particles"), p, 0.14f + 0.16f * h1, 0f, mix(PURP, LAV, h2), 0.8f * rev * K * Mth.sin(ph * 3.14159f));
                }
                // ------------------------------------------------ 2. formation: large flat circles, the energy is pulled in
                float g1 = smooth((sec - 2.0f) / 1.3f), g2 = smooth((sec - 2.6f) / 1.3f), g3 = smooth((sec - 3.2f) / 1.3f);
                float turn = 10f * (sec < 5.8f ? sec : 5.8f + (sec - 5.8f) * 0.08f);
                float glowUp = (0.55f + 0.45f * decl) * K;
                circle(buf, ps, c0, 0.05, 8.5f * g1, turn, PURP, 0.8f * g1 * glowUp);
                circle(buf, ps, c0, 0.06, 5.6f * g2, -turn * 1.5f, LAV, 0.75f * g2 * glowUp);
                circle(buf, ps, c0, 0.07, 3.3f * g3, turn * 2.1f, mix(LAV, VWHITE, decl), 0.7f * g3 * glowUp);
                // vertical stack of smaller horizontal circles around the sword arm
                for (int k = 0; k < 3; k++) {
                    float gk = smooth((sec - 3.0f - 0.4f * k) / 1.0f);
                    circle(buf, ps, feet, 2.2 + 0.85 * k, (1.55f - 0.4f * k) * gk, (k % 2 == 0 ? 1 : -1) * turn * 2.4f, mix(PURP, LAV, k / 2f), 0.65f * gk * glowUp);
                }
                for (int i = 0; i < 80; i++) {
                    float h1 = hash(i, 51), h2 = hash(i, 52), h3 = hash(i, 53), h4 = hash(i, 54);
                    float ph = (sec * (0.2f + 0.2f * h1) * (1f - 0.7f * decl) + h2) % 1f;
                    float rad = (1f + 13f * h3) * (1f - ph) * (1f - ph) + 0.15f;
                    float a = h4 * 6.283f + ph * 2.2f;
                    Vec3 p = tip.add(Mth.cos(a) * rad, (hash(i, 55) - 0.55f) * rad * 0.8f, Mth.sin(a) * rad);
                    Fx.glow(buf, ps, tex("atomic_residual_particles"), p, 0.16f + 0.2f * h1, 0f, mix(LAV, VWHITE, ph), 0.9f * form * K * Mth.sin(ph * 3.14159f));
                }
                // ------------------------------------------------ 3. declaration: the eyes, the light going up, patterns at their brightest
                if (decl > 0.01f) {
                    Vec3 eye = (owner != null ? owner.getEyePosition(pt).subtract(base) : feet.add(0, 1.62, 0)).add(fwd(yaw).scale(0.24));
                    for (int side = -1; side <= 1; side += 2) {
                        Fx.glow(buf, ps, tex("shadow_eye_glow"), eye.add(left(yaw).scale(0.1 * side)), 0.26f * decl, 0f, VWHITE, 0.95f * decl * K);
                    }
                    Fx.glow(buf, ps, tex("shd_glow"), eye, 0.7f * decl, 0f, LAV, 0.3f * decl * K);
                    // one thin shaft of light rises from the blade
                    Fx.ribbon(buf, ps, tex("atomic_sword_energy"), true, tip, tip.add(0, 55.0 * decl, 0), cam, 0.35f, LAV, 0.4f * decl * K);
                    Fx.ribbon(buf, ps, tex("atomic_sword_energy"), true, tip, tip.add(0, 55.0 * decl, 0), cam, 0.09f, VWHITE, 0.8f * decl * K);
                    float pr = c01((sec - 4.5f) / 1.0f);
                    Fx.ring(buf, ps, tex("atomic_shock_ring"), feet.add(0, 0.1, 0), 9.5f * pr, 10.2f * pr + 0.1f, 80, 24f, 0f, LAV, 0.8f * (1f - pr) * K);
                    Fx.glow(buf, ps, tex("atomic_release_flash"), tip, 1.0f + 2.5f * decl, 0f, VWHITE, 0.6f * decl * K);
                }
            }

            // ------------------------------------------------ 4. release: a violet-white flash, one stroke, one line to the target
            if (sec >= 6.95f && sec < 8.6f) {
                float f = 1f - smooth((sec - 7.0f) / 0.7f);
                Fx.glow(buf, ps, tex("atomic_release_flash"), tip, 7f * f + 1f, 0f, VWHITE, 0.95f * f * c01((sec - 6.95f) / 0.05f));
                Fx.glow(buf, ps, tex("shd_ring"), tip, 0.6f + 12f * c01((sec - 7.0f) / 0.5f), 0f, LAV, 0.8f * (1f - c01((sec - 7.0f) / 0.5f)));
                // the sword stroke: a fading crescent behind the tip
                for (int j = 0; j < 9; j++) {
                    float ts = Math.min(sec, 7.3f) - j * 0.03f;
                    if (ts < 7.0f) {
                        continue;
                    }
                    Vec3 d2 = swordDir(ts, aim), d3 = swordDir(ts - 0.03f, aim);
                    Vec3 a = hand.add(d2.scale(1.62)), b = hand.add(d3.scale(1.62));
                    Fx.ribbon(buf, ps, tex("atomic_sword_energy"), true, a, b, cam, 0.5f * (1f - j / 9f), mix(VWHITE, LAV, j / 9f), 0.9f * (1f - smooth((sec - 7.3f) / 0.5f)));
                }
                Vec3 target = new Vec3(0, 1.0, 0);
                float prog = smooth((sec - 7.05f) / 0.4f), al = 1f - smooth((sec - 7.4f) / 0.5f);
                Vec3 head = tip.lerp(target, prog);
                Fx.ribbon(buf, ps, tex("atomic_energy_stream"), true, tip, head, cam, 1.1f, PURP, 0.6f * al);
                Fx.ribbon(buf, ps, tex("atomic_energy_stream"), true, tip, head, cam, 0.3f, VWHITE, 0.95f * al);
                Fx.glow(buf, ps, tex("atomic_release_flash"), head, 4f, sec * 30f, VWHITE, 0.8f * al * (1f - prog * 0.5f));
            }

            // ------------------------------------------------ 5. the torrent of blue-violet light at the target
            float s = sec - (float) AtomicEntity.BLAST;
            if (s >= 0f && sec < 13f) {
                float grow = easeOut(s / 1.1f), rise = easeOut(s / 0.8f);
                float fade = smooth((sec - 8.8f) / 2.6f), life = smooth(s / 0.2f) * (1f - fade);
                float rO = R * 0.5f * grow * (1f - 0.3f * fade), H = 150f * rise;
                float dc = (float) Math.hypot(cam.x, cam.z);
                float inside = smooth((rO - dc) / Math.max(1f, rO * 0.12f));
                float dim = 1f - 0.85f * inside;
                ps.pushPose();
                ps.translate(0, 0.1, 0);
                float[] cOut = mix(PURP, DEEPV, fade), cMid = mix(LAV, PURP, smooth((sec - 8.0f) / 2.0f)), cCore = mix(VWHITE, LAV, smooth((sec - 7.8f) / 1.6f));
                Draw.band(add(buf, tex("atomic_blast_surface")), ps, rO * 1.0f, 0f, H, 56, 3f, sec * 0.12f, cOut, 0.6f * life * dim, 0.25f * life * dim);
                Draw.band(add(buf, tex("atomic_blast_surface")), ps, rO * 0.82f, 0f, H, 56, 4f, -sec * 0.2f, cMid, 0.7f * life * dim, 0.3f * life * dim);
                Draw.band(add(buf, tex("atomic_blast_surface")), ps, rO * 0.55f, 0f, H, 48, 3f, sec * 0.3f, cMid, 0.85f * life * dim, 0.4f * life * dim);
                Draw.band(add(buf, tex("atomic_blast_surface")), ps, rO * 0.32f, 0f, H, 40, 2f, -sec * 0.45f, cCore, 0.95f * life * (0.35f + 0.65f * dim), 0.6f * life * (0.35f + 0.65f * dim));
                ps.popPose();
                // the surface breaks open: a wide dome of light over the ground
                float rd = R * 0.95f * easeOut(s / 1.7f);
                float domeA = 0.5f * life * (1f - 0.8f * smooth((rd - dc) / Math.max(1f, rd * 0.1f)));
                for (int side = 0; side < 2; side++) {
                    ps.pushPose();
                    ps.scale(rd, rd * 0.6f, rd);
                    MeshRender.draw(buf, ps, side == 0 ? dome() : domeIn(), "atomic_blast_surface", Mode.ADD, mix(LAV, PURP, fade), domeA, 1f, sec * 0.03f, -sec * 0.05f);
                    ps.popPose();
                }
                // the light itself
                float hot = 1f - smooth(s / 1.0f);
                Fx.glow(buf, ps, tex("atomic_release_flash"), new Vec3(0, 4, 0), 60f * grow + 8f, 0f, VWHITE, 0.75f * hot * dim);
                Fx.glow(buf, ps, tex("shd_glow"), new Vec3(0, 6, 0), R * 1.5f * grow, 0f, mix(LAV, PURP, fade), 0.5f * life * dim);
                float sky = smooth((s - 0.2f) / 1.0f) * (1f - smooth((sec - 9.5f) / 2.5f));
                Fx.glow(buf, ps, tex("shd_glow"), new Vec3(0, 70, 0), 230f, 0f, PURP, 0.42f * sky);
                Fx.glow(buf, ps, tex("shd_glow"), new Vec3(0, 38, 0), 130f, 0f, LAV, 0.34f * sky * dim);
                Fx.glow(buf, ps, tex("shd_glow"), new Vec3(0, 110, 0), 300f, 0f, DEEPV, 0.5f * sky);
                // the edge of the blast on the ground
                float er = R * easeOut(s / 1.4f);
                float ea = (1f - smooth((sec - 8.4f) / 1.5f)) * 0.9f;
                Fx.ring(buf, ps, tex("atomic_shock_ring"), new Vec3(0, 0.15, 0), Math.max(0f, er - 3.5f), er, 128, 48f, 0f, mix(VWHITE, LAV, smooth(s / 1.4f)), ea);
            }
            // the delayed shockwave
            float sa = (float) AtomicEntity.SHOCK_A, sb = (float) AtomicEntity.SHOCK_B;
            if (sec >= sa && sec < sb + 1.6f) {
                float f = c01((sec - sa) / (sb - sa));
                float al = 1f - smooth((sec - sb + 0.6f) / 2.2f);
                for (int i = 0; i < 2; i++) {
                    float ff = c01(f - i * 0.06f), rr = S * (1f - (1f - ff) * (1f - ff));
                    if (rr < 1f) {
                        continue;
                    }
                    float w = 2f + 6f * ff;
                    Fx.ring(buf, ps, tex("atomic_shock_ring"), new Vec3(0, 0.2 + 0.05 * i, 0), Math.max(0f, rr - w), rr, 160, 64f, 0f, i == 0 ? LAV : PURP, (0.75f - 0.25f * i) * al);
                    ps.pushPose();
                    ps.translate(0, 0.1, 0);
                    Draw.band(add(buf, tex("atomic_blast_surface")), ps, rr, 0f, 3.5f + 3f * ff, 96, 24f, sec * 0.2f, i == 0 ? LAV : PURP, 0.28f * al, 0f);
                    ps.popPose();
                }
            }
            // clouds of light and dust that disperse, then a thin haze
            if (sec >= 8.0f && sec < 14.5f) {
                float a1 = smooth((sec - 8.0f) / 1.0f) * (1f - smooth((sec - 12.0f) / 2.4f));
                float sp = smooth((sec - 8.0f) / 3.5f);
                for (int i = 0; i < 26; i++) {
                    float h1 = hash(i, 61), h2 = hash(i, 62), h3 = hash(i, 63), az = h1 * 6.283f;
                    float rr = R * (0.1f + 0.7f * h2) * (0.3f + 0.7f * sp);
                    Vec3 p = new Vec3(Mth.cos(az) * rr, 3f + 26f * h3 * sp, Mth.sin(az) * rr);
                    Fx.soft(buf, ps, tex("shadow_mist"), p, 12f + 14f * h1, sec * 4f + i * 31f, mix(DEEPV, PURP, h1), 0.17f * a1);
                }
                float hz = smooth((sec - 11.0f) / 1.5f) * (1f - smooth((sec - 13.0f) / 1.5f));
                for (int i = 0; i < 14; i++) {
                    float h1 = hash(i, 71), h2 = hash(i, 72), az = h1 * 6.283f, rr = h2 * R * 0.55f;
                    Fx.soft(buf, ps, tex("shadow_mist"), new Vec3(Mth.cos(az) * rr, 1.5 + 3 * h1, Mth.sin(az) * rr), 10f + 8f * h2, sec * 3f + i * 17f, DEEPV, 0.14f * hz);
                }
                float md = smooth((sec - 10.5f) / 1.0f) * (1f - smooth((sec - 13.0f) / 1.5f));
                for (int i = 0; i < 34; i++) {
                    float h1 = hash(i, 81), h2 = hash(i, 82), az = h1 * 6.283f, rr = h2 * R * 0.6f;
                    float rs = ((sec - 10.5f) * (0.04f + 0.06f * h2) + h1) % 1f;
                    Fx.glow(buf, ps, tex("atomic_residual_particles"), new Vec3(Mth.cos(az) * rr, 1 + rs * 22, Mth.sin(az) * rr), 0.5f + 0.5f * h2, 0f, mix(PURP, LAV, h1), 0.55f * md * Mth.sin(rs * 3.14159f));
                }
            }
            // the crater: only an illusion, and it goes away
            if (sec >= 8.2f && com.example.jjkspells.shadow.config.ShdConfig.ATOMIC.craterIllusion.get()) {
                float ca = smooth((sec - 8.2f) / 1.5f) * (1f - smooth((sec - 13.2f) / 1.3f));
                float cr = R * 0.5f;
                Fx.ground(buf, ps, tex("atomic_crater"), false, new Vec3(0, 0.06, 0), cr, 0f, MeshRender.WHITE, 0.9f * ca);
                Fx.ground(buf, ps, tex("atomic_crater"), true, new Vec3(0, 0.07, 0), cr * 1.01f, 0f, DEEPV, 0.45f * ca * (1f - smooth((sec - 10.5f) / 2.5f)));
            }
        }
    }
}
