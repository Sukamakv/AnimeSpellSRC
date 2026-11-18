package com.example.jjkspells.acheron.client.render;

import com.example.jjkspells.acheron.entity.*;
import com.example.jjkspells.acheron.registry.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.acheron.client.render.Gfx.*;

/**
 * The seven renderers. No custom body and no custom sword: the caster is the normal player holding whatever is in the main hand, and
 * every effect is a flat texture (ribbons, discs, billboards) drawn around it. The black sun, the reflective ground and the crimson
 * slash are the three shapes the Ultimate is recognised by.
 */
public final class SeqRenderers {
    private SeqRenderers() {
    }

    // ---------------------------------------------------------------- helpers

    private static Vec3 rel(double x, double y, double z, Vec3 base) {
        return new Vec3(x - base.x, y - base.y, z - base.z);
    }

    private static Vec3 dir(double yawRad, double tilt) {
        double c = Math.cos(tilt);
        return new Vec3(Math.cos(yawRad) * c, Math.sin(tilt), Math.sin(yawRad) * c);
    }

    private static float fl(float t) {
        return t - (float) Math.floor(t);
    }

    /** 1 at t0 falling to 0 at t0 + dur; 0 before t0. */
    private static float fade(float t, float t0, float dur) {
        float x = (t - t0) / dur;
        return x < 0f || x > 1f ? 0f : 1f - x;
    }

    /** Fast rise, slow fall. */
    private static float flick(float t, float t0, float dur) {
        float x = (t - t0) / dur;
        if (x < 0f || x > 1f) {
            return 0f;
        }
        return Math.min(1f, x * 6f) * (1f - x);
    }

    /** 0..1 ramp in, hold, ramp out (ticks). */
    private static float env(float t, float in0, float in1, float out0, float out1) {
        return Math.min(c01((t - in0) / Math.max(0.01f, in1 - in0)), 1f - c01((t - out0) / Math.max(0.01f, out1 - out0)));
    }

    private static float tk(double sec) {
        return (float) (sec * 20.0);
    }

    /** A ribbon through c along d (unit), additive. */
    private static void cut(MultiBufferSource buf, PoseStack ps, String tx, Vec3 c, Vec3 d, float len, float hw, float[] col, float a, Vec3 cam) {
        if (a <= 0.01f) {
            return;
        }
        Vec3 h = d.scale(len * 0.5);
        Fx.ribbon(buf, ps, tex(tx), true, c.subtract(h), c.add(h), cam, hw, col, a);
    }

    private static Vec3 bez(Vec3 a, Vec3 b, Vec3 c, float f) {
        float g = 1f - f;
        return a.scale(g * g).add(b.scale(2f * g * f)).add(c.scale(f * f));
    }

    /**
     * A curved crescent slash: a quadratic arc through c along d (unit) bulging in the screen plane, drawn as a ribbon that tapers
     * at both ends. prog (0..1) is how much of it has been swept so far. Texture runs along it.
     */
    private static void swoosh(MultiBufferSource buf, PoseStack ps, String tx, Vec3 c, Vec3 d, float len, float hw, float bend, float[] col, float a, float prog, Vec3 cam) {
        if (a <= 0.01f || prog <= 0.01f) {
            return;
        }
        Vec3 view = cam.subtract(c);
        if (view.lengthSqr() < 1.0e-6) {
            return;
        }
        view = view.normalize();
        Vec3 b = view.cross(d);
        b = b.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : b.normalize();
        Vec3 half = d.scale(len * 0.5);
        Vec3 p0 = c.subtract(half), p2 = c.add(half), p1 = c.add(b.scale(bend * len));
        var vc = add(buf, tex(tx));
        int n = 20;
        for (int i = 0; i < n; i++) {
            float f0 = i / (float) n;
            if (f0 >= prog) {
                break;
            }
            float f1 = Math.min((i + 1) / (float) n, prog);
            float w0 = hw * (float) Math.pow(Math.sin(Math.PI * f0), 0.65) + 0.03f, w1 = hw * (float) Math.pow(Math.sin(Math.PI * f1), 0.65) + 0.03f;
            Draw.ribbon(vc, ps, bez(p0, p1, p2, f0), bez(p0, p1, p2, f1), cam, w0, w1, f0, f1, col, a * (0.35f + 0.65f * f0), a * (0.35f + 0.65f * f1));
        }
    }

    /** The big slash used by every spell: a crescent, a bright thin core, and a fainter echo bending the other way. */
    private static void bigSlash(MultiBufferSource buf, PoseStack ps, boolean violet, Vec3 c, Vec3 d, float len, float hw, float bend, float a, float prog, Vec3 cam) {
        String tx = violet ? "acheron_violet_slash" : "acheron_rainblade_slash";
        swoosh(buf, ps, tx, c, d, len, hw, bend, WHITE, a, prog, cam);
        swoosh(buf, ps, "acheron_rainblade_cut", c, d, len * 0.95f, hw * 0.3f, bend, violet ? VIOHI : WHITE, a, prog, cam);
        swoosh(buf, ps, tx, c, d, len * 1.12f, hw * 0.55f, -bend * 0.6f, violet ? VIO : CRIMHI, a * 0.4f, prog, cam);
    }

    /** A unit direction in the plane facing the camera at c, turned by phi radians. */
    private static Vec3 screenDir(Vec3 c, Vec3 cam, double phi) {
        Vec3 view = cam.subtract(c);
        view = view.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : view.normalize();
        Vec3 right = view.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(view).normalize();
        return right.scale(Math.cos(phi)).add(up.scale(Math.sin(phi))).normalize();
    }

    private static void blackSun(MultiBufferSource buf, PoseStack ps, Vec3 c, float size, float a) {
        Fx.soft(buf, ps, tex("acheron_black_sun"), c, size, 0f, WHITE, a);
    }

    private static void groundMirror(MultiBufferSource buf, PoseStack ps, float radius, float a) {
        Fx.ground(buf, ps, tex("acheron_reflective_ground"), false, new Vec3(0, 0.04, 0), radius, 0f, WHITE, a);
    }

    private static void flower(MultiBufferSource buf, PoseStack ps, Vec3 c, float radius, float rot, float a) {
        Fx.ground(buf, ps, tex("acheron_red_flower"), true, c.add(0, 0.08, 0), radius, rot, WHITE, a);
    }

    private static void petals(MultiBufferSource buf, PoseStack ps, float t, int count, float radius, float height, float a) {
        for (int i = 0; i < count; i++) {
            float ang = hash(i, 11) * 6.2832f, rr = (float) Math.sqrt(hash(i, 12)) * radius;
            float fall = fl(t * 0.011f * (0.7f + hash(i, 13) * 0.6f) + hash(i, 14));
            Vec3 p = new Vec3(Mth.cos(ang) * rr + Mth.sin(t * 0.05f + i) * 0.9f, height * (1f - fall) + 0.2f, Mth.sin(ang) * rr);
            Fx.glow(buf, ps, tex("acheron_red_petals"), p, 0.3f + 0.25f * hash(i, 15), t * 3f + i * 40f, WHITE, a * (float) Math.sin(Math.PI * fall));
        }
    }

    /** Standing expanding wall of the Stygian crescent. */
    private static void crescent(MultiBufferSource buf, PoseStack ps, float r, float h, float a, float t) {
        if (a <= 0.01f || r <= 0.1f) {
            return;
        }
        ps.pushPose();
        ps.translate(0, 0.05, 0);
        Draw.band(add(buf, tex("acheron_stygian_resurge")), ps, r, 0f, h, 56, 2f, -t * 0.004f, WHITE, a, a * 0.35f);
        ps.popPose();
    }

    private static void fracturePlate(MultiBufferSource buf, PoseStack ps, float radius, float a, float rot) {
        Fx.ground(buf, ps, tex("rainblade_spatial_fracture"), true, new Vec3(0, 0.06, 0), radius, rot, WHITE, a);
    }

    private static void knotGlyph(MultiBufferSource buf, PoseStack ps, Vec3 p, float size, float rot, float a) {
        Fx.glow(buf, ps, tex("acheron_crimson_knot"), p, size, rot, WHITE, a);
    }

    // ================================================================ Trilateral Wiltcross

    public static final class Wilt extends AchRenderer<WiltEntity> {
        private static final float[] CUT = {9, 17, 25};

        public Wilt(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(WiltEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            double y0 = Math.toRadians(e.yaw());
            Vec3 c = new Vec3(0, 1.2, 0);
            float rad = Math.max(2.5f, e.r1() * 0.75f);
            Vec3[] v = new Vec3[3];
            for (int k = 0; k < 3; k++) {
                double a = y0 + Math.PI / 2 + k * Math.PI * 2 / 3;
                v[k] = new Vec3(Math.cos(a) * rad, 1.2, Math.sin(a) * rad);
            }
            for (int i = 0; i < 3; i++) {
                float lt = t - CUT[i];
                if (lt < 0) {
                    continue;
                }
                float a = flick(t, CUT[i], 10f);
                Vec3 d = dir(y0 + i * Math.PI * 2 / 3 + 0.3, (i % 2 == 0 ? 1 : -1) * 0.45).normalize();
                Fx.glow(buf, ps, tex("acheron_lightning_spark"), c.add(d.scale((hash(i, 3) * 2 - 1) * 4)), 1.8f, lt * 20f, VIOHI, a);
                // the edge of the triangle stays a little while
                float edge = fade(t, CUT[i] + 2f, 42f);
                Fx.ribbon(buf, ps, LINE, true, v[i], v[(i + 1) % 3], cam, 0.06f, VIO, 0.9f * edge);
                Fx.ribbon(buf, ps, LINE, true, v[i], v[(i + 1) % 3], cam, 0.18f, VIODEEP, 0.4f * edge);
            }
            float ct = t - 34f;
            if (ct >= 0 && ct < 16f) {
                float a = 1f - ct / 16f;
                Vec3 p = new Vec3(-cam.z, 0, cam.x);
                p = p.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : p.normalize();
                Fx.ring(buf, ps, tex("acheron_line"), new Vec3(0, 0.08, 0), rad * ct / 16f, rad * ct / 16f + 0.5f, 40, 6f, 0f, VIO, a * 0.8f);
                for (int i = 0; i < 5; i++) {
                    Fx.glow(buf, ps, tex("acheron_lightning_spark"), c.add((hash(i, 5) - 0.5) * 8, (hash(i, 6) - 0.5) * 5, (hash(i, 7) - 0.5) * 8), 1.4f, ct * 30f + i * 50f, VIOHI, a);
                }
            }
        }
    }

    // ================================================================ Octobolt Flash

    public static final class Octo extends AchRenderer<OctoEntity> {
        private static final float[] HIT = {15, 21, 27, 33, 39};

        public Octo(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(OctoEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            Vec3 c = new Vec3(0, 1.1, 0);
            for (double[] d : e.fx()) {
                int type = (int) d[0];
                float lt = t - (float) d[1];
                if (lt < 0) {
                    continue;
                }
                if (type == 1) {
                    Vec3 from = rel(d[2], d[3] + 1.0, d[4], base), to = rel(d[5], d[6] + 1.0, d[7], base);
                    float a = fade(lt, 0f, 14f);
                    Fx.ribbon(buf, ps, tex("acheron_octobolt_lightning"), true, from, to, cam, 1.7f, VIO, a);
                    Fx.ribbon(buf, ps, tex("acheron_octobolt_lightning"), true, from, to, cam, 0.7f, VIOHI, a);
                    Ghost.draw(buf, ps, owner, rel(d[2], d[3], d[4], base), e.yaw(), VIO, 0.5f * fade(lt, 0f, 12f), Ghost.DASH);
                    for (int i = 0; i < 5; i++) {
                        float lh = t - HIT[i];
                        if (lh < 0) {
                            continue;
                        }
                        float ah = flick(t, HIT[i], 9f);
                        Vec3 dd = dir(i * 1.2566 + 0.4, (i % 2 == 0 ? 0.55 : -0.55)).normalize();
                        Fx.glow(buf, ps, tex("acheron_lightning_spark"), c.add(dd.scale((hash(i, 8) * 2 - 1) * 3)), 1.8f, lh * 25f, VIOHI, ah);
                    }
                } else if (type == 2) {
                    Vec3 g = rel(d[2], d[3], d[4], base);
                    float a = fade(lt, 0f, 9f);
                    Fx.ribbon(buf, ps, tex("acheron_octobolt_lightning"), true, g.add(0, 16, 0), g, cam, 1.4f, VIO, a);
                    Fx.ribbon(buf, ps, tex("acheron_octobolt_lightning"), true, g.add(0, 16, 0), g, cam, 0.5f, VIOHI, a);
                    Fx.glow(buf, ps, tex("acheron_lightning_spark"), g.add(0, 0.8, 0), 3.2f * (1f - 0.4f * lt / 9f), lt * 30f, VIOHI, a);
                } else if (type == 3 && lt < 26f) {
                    Vec3 g = rel(d[2], d[3] + 1.0, d[4], base);
                    knotGlyph(buf, ps, g, 2.4f - 1.0f * Math.min(1f, lt / 12f), lt * 6f, fade(lt, 0f, 26f));
                    Fx.glow(buf, ps, tex("acheron_crimson_knot_shatter"), g, 2.2f * (0.5f + lt / 26f), lt * 4f, WHITE, fade(lt, 0f, 18f));
                }
            }
        }
    }

    // ================================================================ Quadrivalent Ascendance

    public static final class Quad extends AchRenderer<QuadEntity> {
        public Quad(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(QuadEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            double y0 = Math.toRadians(e.yaw());
            Vec3[] corner = new Vec3[4];
            for (int i = 0; i < 4; i++) {
                double a = y0 + Math.PI / 4 + i * Math.PI / 2;
                corner[i] = new Vec3(-Math.sin(a) * 7.0, 0.9, Math.cos(a) * 7.0);
            }
            float final0 = 60f;
            float line = env(t, 8f, 12f, final0 + 4f, final0 + 20f);
            // the execution lines of the square, drawn as the dashes come
            for (int i = 0; i < 4; i++) {
                float seen = t >= 10f + 11f * i ? 1f : 0f;
                float a = line * seen;
                Fx.ribbon(buf, ps, tex("acheron_line"), true, corner[i], corner[(i + 1) % 4], cam, 0.08f, CRIM, a);
                Fx.ribbon(buf, ps, tex("acheron_line"), true, corner[i], corner[(i + 1) % 4], cam, 0.3f, CRIMDEEP, a * 0.5f);
                Fx.ribbon(buf, ps, tex("acheron_line"), true, corner[i], new Vec3(0, 0.9, 0), cam, 0.04f, CRIMHI, a * 0.5f);
            }
            for (double[] d : e.fx()) {
                int type = (int) d[0];
                float lt = t - (float) d[1];
                if (lt < 0) {
                    continue;
                }
                if (type == 2) {
                    Vec3 from = rel(d[2], d[3] + 1.0, d[4], base), to = rel(d[5], d[6] + 1.0, d[7], base);
                    Ghost.draw(buf, ps, owner, rel(d[2], d[3], d[4], base), e.yaw(), CRIM, 0.45f * fade(lt, 0f, 14f), Ghost.DASH);
                } else if (type == 3 && lt < 22f) {
                    Vec3 g = rel(d[2], d[3], d[4], base);
                    knotGlyph(buf, ps, g, 1.8f, lt * 8f, fade(lt, 0f, 22f));
                    Fx.glow(buf, ps, tex("acheron_crimson_knot_shatter"), g, 2.0f * (0.5f + lt / 22f), 0f, WHITE, fade(lt, 0f, 16f));
                } else if (type == 4 && lt < 26f) {
                    float a = fade(lt, 0f, 26f);
                    Vec3 top = new Vec3(0, 26, 0);
                    Fx.ribbon(buf, ps, tex("acheron_rainblade_slash"), true, top, new Vec3(0, 0.1, 0), cam, 4.5f * (0.4f + a), WHITE, a);
                    Fx.ribbon(buf, ps, tex("acheron_rainblade_cut"), true, top, new Vec3(0, 0.1, 0), cam, 1.4f, WHITE, a);
                    Fx.ring(buf, ps, tex("acheron_line"), new Vec3(0, 0.08, 0), e.r1() * lt / 26f, e.r1() * lt / 26f + 0.9f, 64, 10f, 0f, CRIM, a);
                    fracturePlate(buf, ps, e.r1() * 0.9f, a * 0.8f, 0f);
                }
            }
            // the ascent: a glow gathers under the caster
            float asc = env(t, 50f, 56f, 62f, 70f);
        }
    }

    // ================================================================ Atop Rainleaf Hangs Oneness

    public static final class Rainleaf extends AchRenderer<RainleafEntity> {
        public Rainleaf(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(RainleafEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float end = e.total() - 10f;
            float life = env(t, 0f, 24f, end - 10f, end + 10f);
            float rad = e.r1();
            // a great red flower lies over the ground of the area, with petals falling through it
            flower(buf, ps, Vec3.ZERO, rad * 0.85f, t * 0.35f, 0.5f * life);
            petals(buf, ps, t, 44, rad * 0.8f, 12f, life);
            Minecraft mc = Minecraft.getInstance();
            Vec3 hand = owner != null ? handRel(owner, pt, base) : Vec3.ZERO;
            for (double[] d : e.fx()) {
                int type = (int) d[0];
                float lt = t - (float) d[1];
                if (lt < 0) {
                    continue;
                }
                if (type == 2 && mc.level != null && owner != null && t < end + 4f) {
                    Entity en = mc.level.getEntity((int) d[2]);
                    if (en instanceof LivingEntity le && le.isAlive() && le.position().distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) < 120.0 * 120.0) {
                        Vec3 c = le.getPosition(pt).subtract(base).add(0, le.getBbHeight() * 0.6, 0);
                        var k = le.getEffect(ModEffects.KNOT.get());
                        int n = k == null ? 1 : k.getAmplifier() + 1;
                        float a = Math.min(1f, lt / 8f) * (1f - c01((t - end) / 4f));
                        float pul = 0.75f + 0.25f * Mth.sin(t * 0.3f + (float) d[2]);
                        Fx.ribbon(buf, ps, tex("acheron_crimson_thread"), true, hand, c, cam, 0.05f + 0.012f * n, WHITE, a * pul);
                        Fx.ribbon(buf, ps, tex("acheron_crimson_thread"), true, hand, c, cam, 0.16f + 0.02f * n, CRIMDEEP, a * 0.35f * pul);
                        knotGlyph(buf, ps, c.add(0, le.getBbHeight() * 0.5 + 0.7, 0), 0.28f + 0.05f * n, t * 2f, a);
                    }
                } else if (type == 3 && lt < 14f) {
                    Vec3 a = rel(d[2], d[3], d[4], base), b = rel(d[5], d[6], d[7], base);
                    float f = c01(lt / 10f);
                    Vec3 head = a.lerp(b, f), tail = a.lerp(b, Math.max(0f, f - 0.35f));
                    Fx.ribbon(buf, ps, tex("acheron_crimson_thread"), true, tail, head, cam, 0.35f, WHITE, 1f - lt / 14f);
                    Fx.glow(buf, ps, tex("acheron_crimson_knot_shatter"), head, 1.4f, lt * 20f, WHITE, 1f - lt / 14f);
                } else if (type == 4 && lt < 24f && mc.level != null) {
                    Entity en = mc.level.getEntity((int) d[2]);
                    if (en != null) {
                        Vec3 c = en.getPosition(pt).subtract(base).add(0, en.getBbHeight() * 0.5, 0);
                    }
                }
            }
        }
    }

    // ================================================================ Rainblade

    public static final class Blade extends AchRenderer<BladeEntity> {
        private static final float[] WAVE = {44f, 76f, 108f};

        public Blade(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(BladeEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float rad = e.r1();
            float gl = env(t, 10f, 30f, 120f, 140f);
            Fx.ground(buf, ps, tex("acheron_reflective_ground"), false, new Vec3(0, 0.04, 0), rad * 1.1f, 0f, WHITE, 0.6f * gl);
            // the fracture grows with every wave
            float grown = t < WAVE[0] ? 0.2f : t < WAVE[1] ? 0.45f : t < WAVE[2] ? 0.7f : 1f;
            fracturePlate(buf, ps, rad * grown, 0.7f * gl, t * 0.1f);
            Vec3 hand = owner != null ? handRel(owner, pt, base) : Vec3.ZERO;
            for (int i = 0; i < 3; i++) {
                float lt = t - WAVE[i];
                if (lt < 0) {
                    continue;
                }
                float a = fade(t, WAVE[i], 18f);
                Fx.ring(buf, ps, tex("acheron_line"), new Vec3(0, 0.08, 0), rad * c01(lt / 14f), rad * c01(lt / 14f) + 0.8f, 72, 12f, 0f, CRIM, a);
            }
            // the swing at the caster's hand before each wave
            for (int i = 0; i < 3; i++) {
                float lt = t - (WAVE[i] - 6f);
                if (lt >= 0 && lt < 7f && owner != null) {
                    Vec3 d = dir(i * 2.1 + 0.5, 0.6).normalize();
                    swoosh(buf, ps, "acheron_rainblade_slash", hand.add(d.scale(1.8)), d, 9f, 0.9f, 0.3f, WHITE, fade(lt, 0f, 7f), c01(lt / 2f), cam);
                }
            }
            for (double[] d : e.fx()) {
                float lt = t - (float) d[1];
                if ((int) d[0] == 3 && lt >= 0 && lt < 16f) {
                    Vec3 g = rel(d[2], d[3], d[4], base);
                    if (d[5] > 0) {
                        Fx.glow(buf, ps, tex("acheron_crimson_knot_shatter"), g, 1.6f + 0.3f * (float) d[5], lt * 5f, WHITE, fade(lt, 0f, 16f));
                    }
                }
            }
        }
    }

    // ================================================================ Stygian Resurge

    public static final class Stygian extends AchRenderer<StygianEntity> {
        public Stygian(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(StygianEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float rad = e.r1(), shock = e.r2();
            float gl = env(t, 6f, 40f, 124f, 156f);
            Fx.ground(buf, ps, tex("acheron_reflective_ground"), false, new Vec3(0, 0.04, 0), shock * 0.9f, 0f, WHITE, 0.75f * gl);
            // the black sun rises, swells, drops
            float impact = 84f;
            float rise = c01(t / 64f);
            float drop = c01((t - 64f) / 20f);
            float h = 8f + 26f * rise - 24f * drop * drop;
            float size = (5f + 20f * rise) * (t < impact ? 1f : Math.max(0f, 1f - (t - impact) / 8f));
            float sa = env(t, 0f, 20f, impact + 6f, impact + 10f);
            if (sa > 0.01f) {
                blackSun(buf, ps, new Vec3(0, h, 0), size, sa);
                // red lines converge on it as it is about to fall
                float ln = env(t, 40f, 60f, impact - 2f, impact + 2f);
                for (int i = 0; i < 12; i++) {
                    float a = hash(i, 41) * 6.28f;
                    Vec3 from = new Vec3(Mth.cos(a) * rad * 0.8f, 0.5f + hash(i, 42) * 4f, Mth.sin(a) * rad * 0.8f);
                    Fx.ribbon(buf, ps, tex("acheron_crimson_thread"), true, from, new Vec3(0, h, 0), cam, 0.12f, WHITE, 0.6f * ln);
                }
            }
            float fall = env(t, 68f, 74f, impact - 2f, impact + 3f);
            if (fall > 0.01f) {
                Fx.ribbon(buf, ps, tex("acheron_rainblade_cut"), true, new Vec3(0, h, 0), new Vec3(0, 0.1, 0), cam, 6.5f, WHITE, fall);
            }
            float it = t - impact;
            if (it >= 0) {
                crescent(buf, ps, rad * c01(it / 20f), 9f + 12f * c01(it / 20f), fade(t, impact, 34f), t);
                crescent(buf, ps, rad * 0.7f * c01(it / 18f), 6f, fade(t, impact + 2f, 28f), t + 40f);
                fracturePlate(buf, ps, rad * 0.9f * c01(it / 12f), 0.9f * fade(t, impact, 60f), 0f);
                flower(buf, ps, Vec3.ZERO, rad * 0.6f * c01(it / 30f), it * 0.4f, 0.8f * fade(t, impact, 70f));
            }
            float st = t - 92f;
            if (st >= 0) {
                crescent(buf, ps, shock * c01(st / 26f), 7f, 0.8f * fade(t, 92f, 40f), t + 90f);
                Fx.ring(buf, ps, tex("acheron_line"), new Vec3(0, 0.1, 0), shock * c01(st / 26f), shock * c01(st / 26f) + 1.2f, 96, 16f, 0f, CRIM, fade(t, 92f, 40f));
            }
            petals(buf, ps, t, 24, rad * 0.5f, 10f, 0.8f * env(t, 84f, 100f, 130f, 160f));
        }
    }

    // ================================================================ Slashed Dream Cries in Red

    public static final class Ultimate extends AchRenderer<UltimateEntity> {
        public Ultimate(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(UltimateEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float rad = e.r1(), fin = e.r2();
            float fa = tk(UltimateEntity.FINAL_AT), open = tk(UltimateEntity.OPEN), s0 = tk(UltimateEntity.SLASH_AT), gap = tk(UltimateEntity.SLASH_GAP);
            Vec3 hole = new Vec3(0, UltimateEntity.HOLE_Y, 0);
            Vec3 hand = owner != null ? handRel(owner, pt, base) : Vec3.ZERO;
            // 1. the reflective ground spreads out under the whole pulled area
            float gl = env(t, 0f, 40f, tk(16.0), tk(18.5));
            Fx.ground(buf, ps, tex("acheron_reflective_ground"), false, new Vec3(0, 0.04, 0), (rad + 35f) * c01(t / 40f), 0f, WHITE, 0.9f * gl);
            // 2. the black hole in the middle of the zone: it opens, swells while it pulls, pulses with every slash, tightens before the end
            if (t < fa) {
                float grow = smoothstep(t / 50f) * 7f + 4f + 4f * smoothstep((t - open) / 90f);
                float tight = t > tk(11.3) ? smoothstep((t - tk(11.3)) / 50f) : 0f;
                float size = grow * (1f - 0.6f * tight) * (1f + 0.4f * c01((t - (fa - 8f)) / 8f));
                float pulse = 0f;
                for (int i = 0; i < UltimateEntity.SLASHES; i++) {
                    pulse = Math.max(pulse, flick(t, s0 + gap * i, 8f));
                }
                blackSun(buf, ps, hole, size * (1f + 0.15f * pulse), c01(t / 20f));
            }
            // 3. everything streams in: spiralling streaks and rings shrinking along the ground
            float inA = env(t, open, open + 12f, fa - 12f, fa);
            if (inA > 0.01f) {
                for (int i = 0; i < 28; i++) {
                    float th = hash(i, 91) * 6.2832f + t * (0.03f + 0.02f * hash(i, 92));
                    float rho = 1f - fl(t * 0.012f * (0.6f + hash(i, 93) * 0.8f) + hash(i, 94));
                    float r = 7f + rho * 72f, y = (float) hole.y + (hash(i, 95) - 0.5f) * 30f * rho;
                    Vec3 p = new Vec3(Mth.cos(th) * r, y, Mth.sin(th) * r);
                    Vec3 q = new Vec3(Mth.cos(th + 0.12f) * r * 0.86f, hole.y + (y - hole.y) * 0.86, Mth.sin(th + 0.12f) * r * 0.86f);
                    Fx.ribbon(buf, ps, tex("acheron_crimson_thread"), true, p, q, cam, 0.3f + 0.35f * rho, WHITE, inA * (0.3f + 0.7f * (1f - rho)));
                }
                for (int k = 0; k < 3; k++) {
                    float rr = 80f * (1f - fl(t / 60f + k / 3f));
                    Fx.ring(buf, ps, LINE, new Vec3(0, 0.1, 0), rr, rr + 1.4f, 96, 18f, 0f, CRIM, inA * 0.55f * c01(rr / 12f));
                }
            }
            // 4. thirteen cuts through the hole, each a huge crescent, each cracking the air and the ground
            int done = 0;
            for (int i = 0; i < UltimateEntity.SLASHES; i++) {
                float s = s0 + gap * i, lt = t - s;
                if (lt >= 0) {
                    done++;
                }
                if (lt >= 0 && lt < 18f) {
                    float a = fade(lt, 0f, 18f);
                    float phi = i * 0.9f + hash(i, 101) * 0.6f;
                    Vec3 d = screenDir(hole, cam, phi);
                    bigSlash(buf, ps, false, hole.add(d.scale(hash(i, 102) * 4f - 2f)), d, 90f, 8.5f, i % 2 == 0 ? 0.28f : -0.28f, a, c01(lt / 2.5f), cam);
                    swoosh(buf, ps, "acheron_rainblade_cut", hole, d, 130f, 2.0f, i % 2 == 0 ? -0.2f : 0.2f, WHITE, a, c01(lt / 2.5f), cam);
                    Fx.glow(buf, ps, tex("rainblade_spatial_fracture"), hole, 12f + 1.5f * i, i * 37f, WHITE, a * 0.8f);
                }
                float sw = t - (s - 3f);
                if (sw >= 0 && sw < 9f && owner != null) {
                    Vec3 d = screenDir(hand, cam, i * 0.9 + 1.2);
                    swoosh(buf, ps, "acheron_rainblade_slash", hand.add(d.scale(2.2)), d, 11f, 1.4f, i % 2 == 0 ? 0.3f : -0.3f, WHITE, fade(sw, 0f, 9f), c01(sw / 2f), cam);
                }
            }
            float grown = 0.1f + 0.9f * done / (float) UltimateEntity.SLASHES;
            fracturePlate(buf, ps, rad * grown, 0.8f * env(t, s0, s0 + 10f, tk(16.0), tk(18.5)), t * 0.06f);
            // 5. red flowers open on the ground, petals fall, the cutter gathers everything for the last slash
            float f0 = tk(11.0);
            for (int i = 0; i < 9; i++) {
                float at = f0 + i * 4f;
                float a = env(t, at, at + 12f, tk(16.2), tk(18.0));
                if (a > 0.01f) {
                    float ang = i * 0.6981f + 0.4f * hash(i, 71);
                    float r = rad * (0.25f + 0.55f * hash(i, 72));
                    flower(buf, ps, new Vec3(Mth.cos(ang) * r, 0, Mth.sin(ang) * r), 8f + 5f * hash(i, 73), t * 0.2f + i * 40f, 0.85f * a);
                }
            }
            petals(buf, ps, t, 70, rad * 0.9f, 24f, env(t, tk(10.5), tk(12.5), tk(16.5), tk(18.0)));
            float ch = env(t, tk(11.6), fa, fa + 2f, fa + 8f);
            if (ch > 0.01f && owner != null) {
                for (int i = 0; i < 10; i++) {
                    float a = hash(i, 81) * 6.28f + t * 0.1f;
                    Vec3 from = hand.add(Mth.cos(a) * 9f * (1f - ch), 1.0 + hash(i, 82) * 7f * (1f - ch), Mth.sin(a) * 9f * (1f - ch));
                    Fx.ribbon(buf, ps, tex("acheron_crimson_thread"), true, from, hand, cam, 0.12f, WHITE, 0.7f * ch);
                }
                if (t < fa) {
                    Fx.ribbon(buf, ps, tex("acheron_crimson_thread"), true, hand, hole, cam, 0.6f * ch, WHITE, 0.7f * ch);
                }
            }
            // 6. the last slash: the hole bursts and reality breaks into pieces that fly outward
            float it = t - fa;
            if (it >= 0) {
                float a = fade(t, fa, 30f);
                Vec3 d1 = screenDir(hole, cam, 0.5), d2 = screenDir(hole, cam, -0.55);
                bigSlash(buf, ps, false, hole, d1, 260f, 24f, 0.2f, fade(t, fa, 34f), c01(it / 3f), cam);
                bigSlash(buf, ps, false, hole, d2, 220f, 16f, -0.2f, fade(t, fa + 3f, 30f), c01((it - 3f) / 3f), cam);
                for (int i = 0; i < 7; i++) {
                    Fx.glow(buf, ps, tex("rainblade_spatial_fracture"), hole, 16f + it * (1.4f + 0.2f * i), i * 53f + it * 0.5f, WHITE, fade(t, fa + i, 70f) * 0.9f);
                }
                for (int i = 0; i < 64; i++) {
                    float z = hash(i, 111) * 2f - 1f, az = hash(i, 112) * 6.2832f, rr = (float) Math.sqrt(1f - z * z);
                    float k = c01(it / 55f), dist = 85f * (1f - (1f - k) * (1f - k)) * (0.35f + 0.65f * hash(i, 113));
                    Vec3 p = hole.add(rr * Mth.cos(az) * dist, z * dist * 0.8f, rr * Mth.sin(az) * dist);
                    Fx.glow(buf, ps, tex("acheron_crimson_knot_shatter"), p, 2.0f + 3f * hash(i, 114), it * 9f + i * 20f, WHITE, fade(t, fa, 70f));
                }
                crescent(buf, ps, fin * c01(it / 30f), 16f + 18f * c01(it / 30f), fade(t, fa, 50f), t);
                crescent(buf, ps, fin * 0.75f * c01(it / 26f), 10f, fade(t, fa + 2f, 44f), t + 40f);
                crescent(buf, ps, fin * 0.5f * c01(it / 22f), 7f, fade(t, fa + 4f, 38f), t + 80f);
                fracturePlate(buf, ps, fin * c01(it / 14f), 0.9f * fade(t, fa, 100f), 0f);
                flower(buf, ps, Vec3.ZERO, 34f * c01(it / 30f), it * 0.4f, 0.8f * fade(t, fa, 90f));
                Fx.ring(buf, ps, LINE, new Vec3(0, 0.1, 0), fin * c01(it / 30f), fin * c01(it / 30f) + 2.0f, 128, 22f, 0f, CRIM, fade(t, fa, 50f));
            }
        }
    }

    private static float smoothstep(float v) {
        v = c01(v);
        return v * v * (3f - 2f * v);
    }
}
