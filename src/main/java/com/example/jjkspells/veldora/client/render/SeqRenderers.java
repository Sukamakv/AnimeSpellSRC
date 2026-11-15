package com.example.jjkspells.veldora.client.render;

import com.example.jjkspells.veldora.entity.*;
import com.example.jjkspells.vfx.Mesh;
import com.example.jjkspells.vfx.MeshRender;
import com.example.jjkspells.vfx.MeshRender.Mode;
import com.example.jjkspells.vfx.Models;
import com.example.jjkspells.vfx.Prim;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Random;

import static com.example.jjkspells.veldora.client.render.Gfx.WHITE;
import static com.example.jjkspells.veldora.client.render.Gfx.smooth;

/**
 * The eight Veldora renderers: the Storm Dragon. Everything is real 3D geometry built once (see VelModels and the vfx package):
 * a scaled serpent dragon, thunderhead clouds, twisting funnels, wind-shear tori and jagged lightning meshes. Thin additive
 * accents are used only on bolts and cores.
 */
public final class SeqRenderers {
    private SeqRenderers() {
    }

    // palette: slate, steel blue, white-blue lightning with a little electric violet
    static final float[] STEEL = {0.62f, 0.72f, 0.95f}, SLATE = {0.42f, 0.46f, 0.6f}, CHAR = {0.24f, 0.25f, 0.32f}, BOLT = {0.82f, 0.9f, 1f},
            ELEC = {0.62f, 0.5f, 1f}, SKY = {0.55f, 0.68f, 0.92f}, DARK = {0.12f, 0.12f, 0.17f};

    // ------------------------------------------------------------------ cached meshes

    static Map<String, Mesh> serpent() {
        return MeshRender.<Map<String, Mesh>>cached("vel_serpent", () -> Models.serpent(7));
    }

    static Map<String, Mesh> helixBig() {
        return MeshRender.<Map<String, Mesh>>cached("vel_helix_big", () -> VelModels.dragonPath(VelModels.helix(2.2, 30, 90, u -> 8 + 3 * u, 0), 1.2, 11));
    }

    static Map<String, Mesh> helixCaster() {
        return MeshRender.<Map<String, Mesh>>cached("vel_helix_caster", () -> VelModels.dragonPath(VelModels.helix(2.6, 3.4, 70, u -> 1.5 + 0.25 * u, 0), 0.13, 5));
    }

    static Map<String, Mesh> headOpen() {
        return MeshRender.<Map<String, Mesh>>cached("vel_head_open", () -> VelModels.head(3, 0.8));
    }

    static Mesh cloudMesh(int i) {
        return MeshRender.<Mesh>cached("vel_cloud" + (i % 6), () -> Prim.cloud(100 + (i % 6) * 17, 1.0, 7));
    }

    static Mesh funnelMesh() {
        return MeshRender.<Mesh>cached("vel_funnel", () -> Prim.funnel(1.0, u -> 0.10 + 0.9 * Math.pow(u, 1.5), 28, 20, 5.0, 0.10, 4).scaleUv(3, 3));
    }

    static Mesh boltMesh(long i) {
        int k = (int) Math.floorMod(i, 12L);
        return MeshRender.<Mesh>cached("vel_bolt" + k, () -> Prim.lightning(31 + k * 7, new double[]{0, 0, 0}, new double[]{0, 1, 0}, 5, 0.3, 0.006, 3));
    }

    static Mesh torusMesh() {
        return MeshRender.<Mesh>cached("vel_torus", () -> Prim.torus(1, 0.04, 40, 8));
    }

    static Mesh cylShort() {
        return MeshRender.<Mesh>cached("vel_cyl_s", () -> Prim.lathe(new double[][]{{1, 0}, {1, 1}}, 20, 0).scaleUv(2, 3));
    }

    static Mesh cylLong() {
        return MeshRender.<Mesh>cached("vel_cyl_l", () -> Prim.lathe(new double[][]{{1, 0}, {1, 1}}, 20, 0).scaleUv(2, 30));
    }

    static Mesh orbMesh() {
        return MeshRender.<Mesh>cached("vel_orb", () -> Prim.lumpy(1, 16, 24, 0.18, 2.5, 9));
    }

    static Mesh sphereMesh() {
        return MeshRender.<Mesh>cached("vel_sphere", () -> Prim.sphere(1, 16, 28));
    }

    static Map<String, Mesh> wingMesh() {
        return MeshRender.<Map<String, Mesh>>cached("vel_wing", () -> Models.wing(3, 1.5));
    }

    // ------------------------------------------------------------------ helpers

    static Vec3 polar(double ang, double r, double y) {
        return new Vec3(Math.cos(ang) * r, y, Math.sin(ang) * r);
    }

    static float vis(float sec, float a, float in, float b, float out) {
        return Math.min(smooth((sec - a) / Math.max(0.01f, in)), 1f - smooth((sec - b) / Math.max(0.01f, out)));
    }

    static Vec3 dir(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    static float bodyYaw(Entity o, float pt) {
        if (o instanceof LivingEntity le) {
            return Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot);
        }
        return o.getViewYRot(pt);
    }

    static void gather(MultiBufferSource buf, PoseStack ps, float s, float until, Vec3 at, float t, long salt) {
        if (s < until) {
            float f = s / until;
            ring(buf, ps, at.add(0, 0.8, 0), 1.6f + 1.6f * f, 0.8f, 0.8f, t, 0.0f);
            ring(buf, ps, at.add(0, 1.4, 0), 2.2f - 0.8f * f, 0.8f, 0.7f, -t, 0.5f);
            lightning(buf, ps, at.add(Math.cos(t * 0.5) * 1.4, 0.3, Math.sin(t * 0.5) * 1.4), at.add(0, 2.4 * f + 0.6, 0), 0.8f, 0.8f, (long) (t / 2) + salt);
        }
    }

    /** Draws every part of a dragon model set. alpha &lt; 0.98 blends instead of cutting. */
    static void parts(MultiBufferSource buf, PoseStack ps, Map<String, Mesh> p, float alpha, float[] tint, float glow) {
        if (alpha <= 0.01f) {
            return;
        }
        Mode m = alpha >= 0.98f ? Mode.SOLID : Mode.TRANS;
        mdraw(buf, ps, p.get("body"), "scales", m, tint, alpha, glow);
        mdraw(buf, ps, p.get("head"), "scales", m, tint, alpha, glow);
        mdraw(buf, ps, p.get("horns"), "bone", m, SLATE, alpha, glow);
        mdraw(buf, ps, p.get("spines"), "bone", m, STEEL, alpha, glow);
        mdraw(buf, ps, p.get("whiskers"), "electric", Mode.ADD, ELEC, 0.7f * alpha, 1f);
        mdraw(buf, ps, p.get("eyes"), "bolt", Mode.ADD, BOLT, alpha, 1f);
    }

    static void mdraw(MultiBufferSource buf, PoseStack ps, Mesh mesh, String tex, Mode mode, float[] tint, float alpha, float glow) {
        if (mesh != null) {
            MeshRender.draw(buf, ps, mesh, tex, mode, tint, alpha, glow);
        }
    }

    static void mdraw(MultiBufferSource buf, PoseStack ps, Mesh mesh, String tex, Mode mode, float[] tint, float alpha, float glow, float uo, float vo) {
        if (mesh != null) {
            MeshRender.draw(buf, ps, mesh, tex, mode, tint, alpha, glow, uo, vo);
        }
    }

    /** A serpent (tail at its local origin, head at +Z) of the given length centred on 'at' and facing 'fwd'. */
    static void serpentAt(MultiBufferSource buf, PoseStack ps, Map<String, Mesh> p, Vec3 at, Vec3 fwd, float length, float roll, float alpha, float[] tint, float glow) {
        if (alpha <= 0.01f || length < 0.1f || fwd.lengthSqr() < 1e-6) {
            return;
        }
        MeshRender.pushFacing(ps, at, fwd);
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        ps.scale(length, length, length);
        ps.translate(0, 0, -0.5);
        parts(buf, ps, p, alpha, tint, glow);
        ps.popPose();
    }

    /** A serpent circling the vertical axis through 'centre'. */
    static void orbit(MultiBufferSource buf, PoseStack ps, Map<String, Mesh> p, Vec3 centre, float R, float y, double ang, float length, float alpha, float[] tint, float glow) {
        Vec3 pos = centre.add(polar(ang, R, y));
        Vec3 fwd = new Vec3(-Math.sin(ang), 0.12 * Math.sin(ang * 3), Math.cos(ang));
        serpentAt(buf, ps, p, pos, fwd, length, (float) Math.toDegrees(ang) * 0.5f, alpha, tint, glow);
    }

    static void head(MultiBufferSource buf, PoseStack ps, Vec3 at, Vec3 fwd, float scale, float alpha, float jawGlow) {
        if (alpha <= 0.01f || scale < 0.1f || fwd.lengthSqr() < 1e-6) {
            return;
        }
        Map<String, Mesh> h = headOpen();
        MeshRender.pushFacing(ps, at, fwd);
        ps.scale(scale, scale, scale);
        Mode m = alpha >= 0.98f ? Mode.SOLID : Mode.TRANS;
        mdraw(buf, ps, h.get("head"), "scales", m, STEEL, alpha, jawGlow);
        mdraw(buf, ps, h.get("horns"), "bone", m, SLATE, alpha, jawGlow);
        mdraw(buf, ps, h.get("whiskers"), "electric", Mode.ADD, ELEC, 0.7f * alpha, 1f);
        mdraw(buf, ps, h.get("eyes"), "bolt", Mode.ADD, BOLT, alpha, 1f);
        ps.popPose();
    }

    static void cloudDeck(MultiBufferSource buf, PoseStack ps, float y, float R, int n, float t, float alpha) {
        if (alpha <= 0.01f || R < 1f) {
            return;
        }
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n + t * 0.004 * (1 + (i % 2) * 0.3);
            Vec3 p = polar(ang, R * (0.45 + 0.28 * (i % 3) / 2.0), y + (i % 3) * 3);
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            ps.mulPose(Axis.YP.rotationDegrees(i * 53f + t * 0.1f));
            ps.scale(R * 0.42f, R * 0.22f, R * 0.42f);
            mdraw(buf, ps, cloudMesh(i), "storm_cloud", Mode.TRANS, i % 2 == 0 ? CHAR : SLATE, 0.9f * alpha, 0.15f, t * 0.0006f, 0f);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(0, y + 2, 0);
        ps.scale(R * 0.8f, R * 0.2f, R * 0.8f);
        mdraw(buf, ps, cloudMesh(5), "storm_cloud", Mode.TRANS, DARK, 0.92f * alpha, 0.05f);
        ps.popPose();
    }

    static void funnel(MultiBufferSource buf, PoseStack ps, Vec3 at, float h, float r, float t, float alpha, float speed) {
        if (alpha <= 0.01f || h < 0.5f || r < 0.2f) {
            return;
        }
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(Axis.YP.rotationDegrees(t * speed));
        ps.scale(r, h, r);
        mdraw(buf, ps, funnelMesh(), "funnel_wall", Mode.TRANS, SLATE, 0.8f * alpha, 0.3f, 0f, -t * 0.02f);
        ps.scale(0.94f, 1.0f, 0.94f);
        ps.mulPose(Axis.YP.rotationDegrees(-t * speed * 2.4f));
        mdraw(buf, ps, funnelMesh(), "electric", Mode.ADD, ELEC, 0.16f * alpha, 1f, 0f, -t * 0.05f);
        ps.popPose();
    }

    /** A lightning bolt mesh from a to b. wk thickens it. */
    static void lightning(MultiBufferSource buf, PoseStack ps, Vec3 a, Vec3 b, float wk, float alpha, long seed) {
        double len = b.subtract(a).length();
        if (alpha <= 0.01f || len < 0.3) {
            return;
        }
        MeshRender.pushAlong(ps, a, b);
        float l = (float) len;
        ps.scale(l * wk, l, l * wk);
        mdraw(buf, ps, boltMesh(seed), "bolt", Mode.ADD, BOLT, alpha, 1f);
        ps.scale(2.2f, 1f, 2.2f);
        mdraw(buf, ps, boltMesh(seed), "electric", Mode.ADD, ELEC, 0.45f * alpha, 1f);
        ps.popPose();
    }

    /** A horizontal wind-shear ring (torus) at 'at'. tilt in radians about X. */
    static void ring(MultiBufferSource buf, PoseStack ps, Vec3 at, float R, float sy, float alpha, float t, float tilt) {
        if (alpha <= 0.01f || R < 0.05f) {
            return;
        }
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(Axis.XP.rotationDegrees((float) Math.toDegrees(tilt)));
        ps.mulPose(Axis.YP.rotationDegrees(t * 3f));
        ps.scale(R, R * sy, R);
        mdraw(buf, ps, torusMesh(), "vel_gust", Mode.TRANS, SKY, alpha, 0.7f, t * 0.02f, 0f);
        ps.popPose();
    }

    /** A ring whose axis points from p toward 'to'. */
    static void ringAlong(MultiBufferSource buf, PoseStack ps, Vec3 p, Vec3 to, float R, float sy, float alpha, float t) {
        if (alpha <= 0.01f) {
            return;
        }
        MeshRender.pushAlong(ps, p, to);
        ps.scale(R, R * sy, R);
        mdraw(buf, ps, torusMesh(), "vel_gust", Mode.TRANS, SKY, alpha, 0.7f, t * 0.03f, 0f);
        ps.popPose();
    }

    /** A cylinder from a to b with radius r. */
    static void cyl(MultiBufferSource buf, PoseStack ps, Vec3 a, Vec3 b, float r, String tex, Mode mode, float[] tint, float alpha, float glow, float vo) {
        double len = b.subtract(a).length();
        if (alpha <= 0.01f || len < 0.3 || r < 0.02f) {
            return;
        }
        MeshRender.pushAlong(ps, a, b);
        ps.scale(r, (float) len, r);
        mdraw(buf, ps, len > 40 ? cylLong() : cylShort(), tex, mode, tint, alpha, glow, 0f, vo);
        ps.popPose();
    }

    /** A storm core: dark churning sphere with a thin electric skin and a few arcs. */
    static void core(MultiBufferSource buf, PoseStack ps, Vec3 p, float r, float t, float alpha) {
        if (alpha <= 0.01f || r < 0.05f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        ps.mulPose(Axis.YP.rotationDegrees(t * 2f));
        ps.mulPose(Axis.ZP.rotationDegrees(t * 1.3f));
        ps.scale(r, r, r);
        mdraw(buf, ps, orbMesh(), "storm_cloud", Mode.SOLID, CHAR, 1f, 0.35f, t * 0.002f, 0f);
        ps.scale(1.06f, 1.06f, 1.06f);
        mdraw(buf, ps, sphereMesh(), "electric", Mode.ADD, ELEC, 0.32f * alpha, 1f, t * 0.01f, t * 0.015f);
        ps.popPose();
        for (int i = 0; i < 3; i++) {
            double a = t * 0.3 + i * 2.1;
            lightning(buf, ps, p.add(polar(a, r * 0.95, Math.sin(a * 2) * r * 0.5)), p.add(polar(a + 0.9, r * 1.9, Math.cos(a) * r)), 0.9f, 0.8f * alpha, (long) (t / 2) + i);
        }
    }

    /** Shock rings and a column of lightning: the detonation of the storm. */
    static void blast(MultiBufferSource buf, PoseStack ps, Vec3 c, float R, float f, float t) {
        float k = 1f - f;
        for (int i = 0; i < 3; i++) {
            float g = smooth(Math.min(1f, f * (1.3f - 0.2f * i)));
            ring(buf, ps, c.add(0, 1.0 + i * 2.5, 0), R * g * (1f - 0.12f * i), 1.6f + i, 0.85f * k, t + i * 20, 0f);
        }
        Fx.wave(buf, ps, c.add(0, 0.2, 0), R * smooth(Math.min(1f, f * 1.3f)), BOLT, k * 0.8f);
        cyl(buf, ps, c, c.add(0, 90, 0), Math.max(0.5f, R * 0.12f * k), "electric", Mode.ADD, BOLT, 0.55f * k, 1f, -t * 0.1f);
        lightning(buf, ps, c.add(0, 90, 0), c, 3.0f * (0.4f + k), k, (long) (t / 2));
    }

    static void wings(MultiBufferSource buf, PoseStack ps, Entity o, float pt, Vec3 base, float t, float spread, float alpha) {
        if (o == null || spread <= 0.01f || alpha <= 0.01f) {
            return;
        }
        float yaw = bodyYaw(o, pt);
        Vec3 at = o.getPosition(pt).subtract(base).add(0, o.getBbHeight() * 0.72, 0).subtract(dir(yaw).scale(0.3));
        Map<String, Mesh> w = wingMesh();
        float S = 3.6f * spread;
        float flap = (float) Math.toDegrees(Math.sin(t * 0.22) * 0.28 - 0.1);
        for (int side = -1; side <= 1; side += 2) {
            ps.pushPose();
            ps.translate(at.x, at.y, at.z);
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.scale(side * S, S, S);
            ps.mulPose(Axis.ZP.rotationDegrees(flap + 15f - (1f - spread) * 70f));
            mdraw(buf, ps, w.get("bones"), "bone", alpha >= 0.98f ? Mode.SOLID : Mode.TRANS, SLATE, alpha, 0.3f);
            mdraw(buf, ps, w.get("membrane"), "vel_membrane", Mode.TRANS, WHITE, alpha, 0.35f);
            ps.popPose();
        }
    }

    /** The small serpent coiling around the caster. */
    static void coil(MultiBufferSource buf, PoseStack ps, Vec3 at, float t, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(Axis.YP.rotationDegrees(-t * 9f));
        parts(buf, ps, helixCaster(), alpha, STEEL, 0.4f);
        ps.popPose();
    }

    abstract static class Base<T extends SeqEntity> extends VelRenderer<T> {
        Base(EntityRendererProvider.Context ctx) {
            super(ctx);
        }
    }

    // ------------------------------------------------------------------ Storm Dragon's Wrath

    public static class Wrath extends Base<WrathEntity> {
        public Wrath(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(WrathEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            gather(buf, ps, s, 1.0f, ownerP, t, 1);
            float cl = vis(s, 1.0f, 1.5f, 8.0f, 1.0f);
            if (cl > 0.01f) {
                float g = smooth(Math.min(1f, (s - 1f) / 1.5f));
                cloudDeck(buf, ps, 46f, R1 * 1.1f * g, 9, t, cl);
                if (s > 2.5f) {
                    Random r = new Random((long) (t / 3) * 31 + 7);
                    for (int i = 0; i < 4; i++) {
                        double ang = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * R1 * 0.9;
                        lightning(buf, ps, polar(ang, d * 0.8, 44), polar(ang + 0.1, d, 0.2), 1.0f, 0.6f * cl, (long) (t / 3) * 5 + i);
                    }
                }
            }
            if (s >= 2.5f && s < 8.4f) {
                float f = vis(s, 2.5f, 0.8f, 7.6f, 0.8f);
                float conv = s > 6.5f ? smooth((s - 6.5f) / 1.5f) : 0f;
                float grow = smooth(Math.min(1f, (s - 2.5f) / 1.5f));
                float fr = 20f * grow * (1f - 0.2f * conv);
                funnel(buf, ps, Vec3.ZERO, 50f, fr, t, f, 5f);
                for (int i = 0; i < 3; i++) {
                    float h = 10f + i * 14f, u = h / 50f;
                    float rr = fr * (0.1f + 0.9f * (float) Math.pow(u, 1.5)) + 3f;
                    ring(buf, ps, new Vec3(0, h, 0), rr, 1.8f, 0.55f * f, t * (1 + i), 0.05f * i);
                }
                if (s >= 3.5f && s < 8.2f) {
                    float p = smooth(Math.min(1f, (s - 3.5f) / 3.0f));
                    float a = vis(s, 3.5f, 0.8f, 7.6f, 0.6f);
                    ps.pushPose();
                    ps.translate(0, 24f * (1f - p) * (1f - conv * 0.5f) - 4f * p, 0);
                    ps.mulPose(Axis.YP.rotationDegrees(t * 7f));
                    ps.scale(1f - 0.35f * conv, 1f, 1f - 0.35f * conv);
                    parts(buf, ps, helixBig(), a, STEEL, 0.35f);
                    ps.popPose();
                }
                float spread = smooth(Math.min(1f, (s - 4f) / 1.0f));
                if (s >= 4f) {
                    for (int i = 0; i < 5; i++) {
                        double ang = i * Math.PI * 2 / 5 + t * 0.02;
                        Vec3 at = polar(ang, R1 * 0.6 * spread * (1f - conv), 0);
                        funnel(buf, ps, at, 28f, 6f, t + i * 20, f * spread, -7f);
                        lightning(buf, ps, at.add(0, 40, 0), at.add(0, 2, 0), 1.0f, 0.6f * f * spread, (long) (t / 3) + i);
                    }
                }
                Fx.crack(buf, ps, new Vec3(0, 0.12, 0), R1 * 0.7f * grow, 0f, BOLT, 0.3f * f);
            }
            if (s >= 6.5f && s < 8.0f) {
                float f = smooth((s - 6.5f) / 1.5f);
                Vec3 c = new Vec3(0, 34, 0);
                core(buf, ps, c, 9f * f, t, 1f);
                for (int i = 0; i < 6; i++) {
                    double ang = i * Math.PI / 3 + t * 0.1;
                    lightning(buf, ps, polar(ang, R1 * 0.5 * (1f - f), 40), c, 1.2f, 0.8f, (long) (t / 2) + i);
                }
            }
            if (s >= 2.5f && s < 3.3f) {
                float f = (s - 2.5f) / 0.8f;
                ring(buf, ps, new Vec3(0, 1, 0), R1 * smooth(f), 2f, 0.8f * (1f - f), t, 0f);
                lightning(buf, ps, new Vec3(0, 60, 0), Vec3.ZERO, 3f * (1f - f) + 0.5f, 1f - f, 3);
            }
            if (s >= 8.0f && s < 9.0f) {
                blast(buf, ps, Vec3.ZERO, R2, s - 8.0f, t);
            }
        }
    }

    // ------------------------------------------------------------------ Black Thunder Apocalypse

    public static class Thunder extends Base<ThunderEntity> {
        public Thunder(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(ThunderEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            gather(buf, ps, s, 1.0f, ownerP, t, 2);
            float cl = vis(s, 0.4f, 1.5f, 8.2f, 1.2f);
            if (cl > 0.01f) {
                float g = smooth(Math.min(1f, (s - 0.4f) / 2f));
                cloudDeck(buf, ps, 48f, R1 * 1.3f * g, 11, t, cl);
                // the dragon's shadow coiling through the cloud deck
                serpentLoop(buf, ps, t, R1 * 0.55f * g, cl);
                if (s > 1.5f) {
                    boolean white = s > 3.8f && s < 6.5f;
                    for (int i = 0; i < 6; i++) {
                        Random r = new Random((long) (t / 3) * 31 + i * 977L);
                        if (r.nextInt(3) != 0) {
                            continue;
                        }
                        double ang = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * R1;
                        lightning(buf, ps, polar(ang, d * 0.8, 46), polar(ang + (r.nextDouble() - 0.5) * 0.2, d, 0.2), white ? 1.6f : 1.0f, cl * 0.8f, (long) (t / 3) * 7 + i);
                    }
                }
                if (s < 6.5f) {
                    core(buf, ps, new Vec3(0, 50, 0), 6f * smooth(Math.min(1f, (s - 1f) / 1.5f)), t, cl);
                }
            }
            if (s >= 6.5f && s < 8.0f) {
                float f = smooth((s - 6.5f) / 1.5f);
                Vec3 c = new Vec3(0, 44 - 6 * f, 0);
                core(buf, ps, c, 6f + 9f * f, t, 1f);
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI / 4 + t * 0.12;
                    lightning(buf, ps, polar(ang, R1 * 0.7 * (1f - f), 46), c, 1.3f, 0.8f, (long) (t / 2) + i);
                }
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (age < 0 || age > 8 || (d[0] != 1 && d[0] != 2)) {
                    continue;
                }
                Vec3 q = Gfx.v(d[2], d[3], d[4]).subtract(base);
                float f = 1f - age / 8f;
                boolean big = d[0] == 2;
                lightning(buf, ps, q.add(0, 70, 0), q, big ? 4.0f : 1.2f, f, (long) d[1]);
                if (big) {
                    cyl(buf, ps, q, q.add(0, 70, 0), 1.6f * f, "electric", Mode.ADD, BOLT, 0.5f * f, 1f, -t * 0.1f);
                    ring(buf, ps, q.add(0, 1, 0), 25f * smooth(1f - f), 1.6f, f, t, 0f);
                }
                Fx.crack(buf, ps, q.add(0, 0.12, 0), big ? 12f : 4f, (float) d[1] * 37f, BOLT, f);
            }
            if (s >= 8.0f && s < 9.0f) {
                float f = s - 8.0f;
                lightning(buf, ps, new Vec3(0, 70, 0), Vec3.ZERO, 6f * (1f - f * 0.5f), 1f - f, 9);
                cyl(buf, ps, Vec3.ZERO, new Vec3(0, 70, 0), 9f * (1f - f), "electric", Mode.ADD, BOLT, 0.5f * (1f - f), 1f, -t * 0.1f);
                blast(buf, ps, Vec3.ZERO, R2, f, t);
            }
        }

        private static void serpentLoop(MultiBufferSource buf, PoseStack ps, float t, float R, float alpha) {
            orbit(buf, ps, serpent(), new Vec3(0, 0, 0), R, 52f, t * 0.015, 90f, 0.9f * alpha, DARK, 0.0f);
            orbit(buf, ps, serpent(), new Vec3(0, 0, 0), R * 0.65f, 56f, -t * 0.02 + 2.0, 60f, 0.8f * alpha, DARK, 0.0f);
        }
    }

    // ------------------------------------------------------------------ Dragon Breath

    public static class Breath extends Base<BreathEntity> {
        public Breath(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(BreathEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R2 = e.r2();
            if (owner == null) {
                return;
            }
            Vec3 look = owner.getViewVector(pt);
            Vec3 mouth = owner.getEyePosition(pt).add(look.scale(1.2)).add(0, -0.25, 0).subtract(base);
            Vec3 a = mouth, b = mouth.add(look.scale(e.r1()));
            for (double[] d : e.fx()) {
                if (d[0] == 1) {
                    a = Gfx.v(d[2], d[3], d[4]).subtract(base);
                    b = Gfx.v(d[5], d[6], d[7]).subtract(base);
                }
            }
            if (s < 7.2f) {
                float f = vis(s, 0.0f, 1.0f, 6.8f, 0.5f);
                float sc = 24f * smooth(Math.min(1f, s / 1.2f));
                head(buf, ps, a.subtract(look.scale(0.14 * sc)).add(0, 0.4, 0), look, sc, 0.85f * f, 0.45f);
                if (s < 2.5f) {
                    float c = smooth((s - 0.3f) / 2.2f);
                    core(buf, ps, a, 0.3f + 1.5f * c, t, f);
                }
            }
            if (s >= 2.5f && s < 7.6f) {
                float f = Math.min(smooth((s - 2.5f) / 0.3f), 1f - smooth((s - 7.0f) / 0.6f));
                float pulse = 1f + 0.08f * Mth.sin(t * 1.3f);
                float W = 6f * pulse;
                cyl(buf, ps, a, b, W * 0.55f, "vel_gust", Mode.TRANS, SKY, 0.5f * f, 0.7f, -t * 0.08f);
                cyl(buf, ps, a, b, W * 0.32f, "electric", Mode.ADD, ELEC, 0.6f * f, 1f, -t * 0.12f);
                cyl(buf, ps, a, b, W * 0.13f, "bolt", Mode.ADD, BOLT, 0.9f * f, 1f, -t * 0.2f);
                Vec3 d = b.subtract(a);
                double len = d.length();
                if (len > 2) {
                    for (int k = 0; k < 7; k++) {
                        double u = (k / 7.0 + t * 0.025) % 1.0;
                        Vec3 p = a.add(d.scale(u));
                        ringAlong(buf, ps, p, b, W * 0.95f * (0.5f + 0.5f * (float) u), 2.0f, 0.6f * f, t + k * 9);
                    }
                    for (int k = 0; k < 3; k++) {
                        double u = (k / 3.0 + t * 0.04) % 1.0;
                        Vec3 p = a.add(d.scale(u));
                        lightning(buf, ps, p, p.add(d.normalize().scale(len * 0.2)), 0.7f, 0.7f * f, (long) (t / 2) + k);
                    }
                }
                core(buf, ps, b, 4f + 5f * smooth(Math.max(0f, (s - 5f) / 2f)), t, f);
                Fx.crack(buf, ps, b.add(0, 0.12, 0), 14f, t, BOLT, 0.5f * f);
                if (s > 3.5f && s < 6.0f) {
                    ring(buf, ps, b.add(0, 0.6, 0), 8f + 14f * ((s * 2.5f) % 1f), 1.5f, 0.6f * f, t, 0f);
                }
            }
            if (s >= 6.0f && s < 7.0f) {
                float f = smooth((s - 6.0f) / 1.0f);
                core(buf, ps, b.add(0, 4, 0), 3f + 9f * f, t, 1f);
            }
            if (s >= 7.0f && s < 8.0f) {
                blast(buf, ps, b, R2, s - 7.0f, t);
            }
        }
    }

    // ------------------------------------------------------------------ Storm Dragon's Fist

    public static class Fist extends Base<FistEntity> {
        public Fist(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(FistEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R2 = e.r2(), yaw = e.yaw();
            Vec3 dir = dir(yaw);
            if (s < 0.8f) {
                gather(buf, ps, s, 0.8f, ownerP, t, 4);
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (age < 0) {
                    continue;
                }
                if (d[0] == 2 && s < 2.0f) {
                    Vec3 st = Gfx.v(d[2], d[3], d[4]).subtract(base).add(0, 1, 0);
                    Vec3 en = dir.scale(-3.5).add(0, 1, 0);
                    float f = 1f - Math.min(1f, age / 30f);
                    lightning(buf, ps, st, en, 1.0f, f, (long) d[1]);
                    for (int i = 0; i < 4; i++) {
                        Vec3 q = st.lerp(en, (i + 0.5) / 4.0);
                        ring(buf, ps, q, 1.6f + i * 0.4f, 1.5f, 0.7f * f, t, 0.6f * i);
                    }
                }
                if (d[0] == 1 && age <= 16) {
                    Vec3 q = Gfx.v(d[2], d[3], d[4]).subtract(base);
                    float k = 1f - age / 16f;
                    boolean fin = d[5] < 0;
                    float sc = fin ? 40f : 20f;
                    float drop = 30f * (1f - smooth(Math.min(1f, age / 4f)));
                    Vec3 hp = q.add(0, 1.5 + drop + 0.14 * sc * 0.9, 0);
                    head(buf, ps, hp, new Vec3(dir.x * 0.25, -1, dir.z * 0.25), sc, Math.min(1f, k * 1.6f), 0.5f);
                    ring(buf, ps, q.add(0, 0.5, 0), (fin ? 14f : 8f) * smooth(1f - k), 1.5f, k, t, 0f);
                    lightning(buf, ps, q.add(0, 24, 0), q, fin ? 2.5f : 1.0f, k, (long) d[1] + (long) d[5]);
                    Fx.crack(buf, ps, q.add(0, 0.12, 0), fin ? 9f : 4f, (float) d[5] * 61f, BOLT, k);
                }
            }
            if (s >= 4.5f && s < 6.2f) {
                float f = vis(s, 4.5f, 0.5f, 5.6f, 0.6f);
                Vec3 o = ownerP.add(0, 1.4, 0);
                coil(buf, ps, ownerP, t, f);
                Vec3 back = o.subtract(dir.scale(7)).add(0, 6, 0);
                serpentAt(buf, ps, serpent(), back, dir.add(0, -0.2, 0), 20f * smooth((s - 4.5f) / 0.8f), 0f, 0.85f * f, STEEL, 0.3f);
                for (int i = 0; i < 6; i++) {
                    double ang = i * 1.05 + t * 0.2;
                    lightning(buf, ps, o.add(polar(ang, 9 * (1f - smooth((s - 4.5f) / 1.0f)), 3)), o.add(dir.scale(1.2)), 0.8f, 0.8f * f, (long) (t / 2) + i);
                }
                Fx.disc(buf, ps, Gfx.CRACK, new Vec3(0, 0.12, 0), R2 * 0.5f * smooth((s - 4.5f) / 1.0f), 0f, BOLT, 0.4f * f);
            }
            if (s >= 5.5f && s < 6.7f) {
                float f = (s - 5.5f) / 1.2f;
                blast(buf, ps, Vec3.ZERO, R2, f, t);
                Vec3 o = ownerP.add(0, 1.4, 0);
                cyl(buf, ps, o, o.add(dir.scale(R2 * 0.8)), 2.4f * (1f - f), "electric", Mode.ADD, BOLT, 0.7f * (1f - f), 1f, -t * 0.2f);
            }
        }
    }

    // ------------------------------------------------------------------ Law of Impossible Fate

    public static class Chaos extends Base<ChaosEntity> {
        public Chaos(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(ChaosEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R = e.r1();
            if (s < 1.5f) {
                Vec3 hand = ownerP.add(0, 2.3, 0);
                float f = smooth(Math.min(1f, s / 0.8f)) * (1f - smooth((s - 1.0f) / 0.5f));
                core(buf, ps, hand, 0.9f * f, t, f);
                ring(buf, ps, ownerP.add(0, 0.5, 0), 4f * f, 1f, 0.7f * f, t, 0f);
            }
            float dome = vis(s, 1.0f, 1.5f, 11.0f, 1.0f);
            if (dome > 0.01f) {
                float g = smooth(Math.min(1f, (s - 1.0f) / 1.5f));
                float shat = s > 8f ? smooth((s - 8f) / 2f) : 0f;
                ps.pushPose();
                ps.scale(R * g, R * g, R * g);
                mdraw(buf, ps, sphereMesh(), "storm_cloud", Mode.TRANS, CHAR, 0.2f * dome, 0.2f, t * 0.002f, 0f);
                ps.scale(0.98f, 0.98f, 0.98f);
                mdraw(buf, ps, sphereMesh(), "vel_gust", Mode.ADD, ELEC, 0.1f * dome, 1f, -t * 0.004f, 0f);
                ps.popPose();
                // a lattice of twisted funnels: reality wrung into knots
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI / 4 + t * 0.006;
                    Vec3 at = polar(ang, R * 0.55 * g, 0);
                    float tilt = (i % 2 == 0 ? 1f : -1f) * 12f;
                    ps.pushPose();
                    ps.translate(at.x, 0, at.z);
                    ps.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(-ang)));
                    ps.mulPose(Axis.ZP.rotationDegrees(tilt));
                    funnel(buf, ps, Vec3.ZERO, 40f * g, 6.5f, t + i * 11, 0.7f * dome * (1f - shat), i % 2 == 0 ? 6f : -6f);
                    ps.popPose();
                }
                for (int i = 0; i < 3; i++) {
                    ring(buf, ps, new Vec3(0, 24f + i * 8f, 0), R * (0.7f - 0.15f * i) * g * (1f - 0.5f * shat), 2.4f, 0.6f * dome * (1f - shat), t * (i % 2 == 0 ? 1f : -1f), 0.1f * i);
                }
                if (s > 2.5f) {
                    for (int i = 0; i < 10; i++) {
                        Random r = new Random((long) (t / 4) * 53 + i * 131L);
                        double a1 = r.nextDouble() * Math.PI * 2, a2 = r.nextDouble() * Math.PI * 2;
                        lightning(buf, ps, polar(a1, R * g * 0.9, 4 + r.nextDouble() * 30), polar(a2, R * g * 0.9, 4 + r.nextDouble() * 30), 1.4f, 0.55f * dome * (1f - shat), (long) (t / 4) * 3 + i);
                    }
                }
            }
            if (s >= 2.0f && s < 2.8f) {
                float f = (s - 2.0f) / 0.8f;
                ring(buf, ps, new Vec3(0, 1, 0), R * smooth(f), 2.5f, 0.8f * (1f - f), t, 0f);
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (age < 0 || age > 12) {
                    continue;
                }
                Vec3 q = Gfx.v(d[2], d[3], d[4]).subtract(base);
                float k = 1f - age / 12f;
                int kind = (int) d[0];
                if (kind == 1) {
                    lightning(buf, ps, q.add(0, 50, 0), q, 1.3f, k, (long) d[1]);
                } else if (kind == 2) {
                    ring(buf, ps, q.add(0, 2, 0), 6f * (1.2f - 0.4f * k), 1.5f, k, t, 1.0f + (float) d[1] % 1.5f);
                    lightning(buf, ps, q.add(5, 8, -3), q.add(-5, -1, 3), 1.5f, k, (long) d[1]);
                    Fx.crack(buf, ps, q.add(0, 0.12, 0), 6f, (float) d[1] * 41f, BOLT, k);
                } else {
                    core(buf, ps, q.add(0, 2 + 4 * (1f - k), 0), 1.2f + 2.5f * (1f - k), t, k);
                    ring(buf, ps, q.add(0, 0.4, 0), 7f * smooth(1f - k), 1.5f, k, t, 0f);
                }
            }
            if (s >= 8.0f && s < 11.0f) {
                float f = smooth((s - 8.0f) / 3.0f);
                core(buf, ps, new Vec3(0, 26 - 6 * f, 0), 3f + 8f * Math.min(1f, f * 1.5f) * (1f - 0.9f * smooth((s - 10.4f) / 0.6f)), t, 1f);
            }
            if (s >= 11.0f && s < 12.0f) {
                blast(buf, ps, Vec3.ZERO, R * 1.05f, s - 11.0f, t);
            }
        }
    }

    // ------------------------------------------------------------------ Storm Dragon's Ascension

    public static class Ascend extends Base<AscendEntity> {
        public Ascend(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        static void wings(MultiBufferSource buf, PoseStack ps, Entity o, float pt, Vec3 base, float t, float spread, float alpha) {
            SeqRenderers.wings(buf, ps, o, pt, base, t, spread, alpha);
        }

        @Override
        protected void draw(AscendEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            boolean over = false;
            Vec3 fin = null;
            for (double[] d : e.fx()) {
                if (d[0] == 1) {
                    over = true;
                    fin = Gfx.v(d[2], d[3], d[4]).subtract(base);
                }
            }
            Vec3 o = ownerP;
            if (s < 6.0f) {
                float f = vis(s, 0.0f, 1.0f, 5.5f, 0.5f);
                ring(buf, ps, o.add(0, 0.6, 0), 3.2f * f, 1f, 0.7f * f, t, 0f);
                ring(buf, ps, o.add(0, 1.6, 0), 2.2f * f, 1f, 0.6f * f, -t, 0.3f);
            }
            if (s >= 1.0f && s < 6.0f) {
                float f = vis(s, 1.0f, 1.5f, 5.2f, 0.8f);
                cloudDeck(buf, ps, 36f + (float) o.y, 28f, 7, t, f);
                float g = smooth((s - 1.0f) / 1.5f);
                orbit(buf, ps, serpent(), new Vec3(0, o.y, 0).add(o.x, 0, o.z), 12f, 24f + 6f * (float) Math.sin(t * 0.05), t * 0.03, 34f * g, 0.9f * f, STEEL, 0.3f);
                if (s > 2f) {
                    Random r = new Random((long) (t / 3) * 17 + 3);
                    for (int i = 0; i < 3; i++) {
                        double ang = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * 20;
                        Vec3 top = o.add(polar(ang, d, 34));
                        lightning(buf, ps, top, o.add(polar(ang + 0.1, d, 0.2)), 1.0f, 0.5f * f, (long) (t / 3) * 3 + i);
                    }
                }
            }
            if (!over && s >= 2.5f) {
                wings(buf, ps, owner, pt, base, t, smooth((s - 2.5f) / 1.5f), 1f);
            }
            if (s >= 3.0f && s < 4.0f) {
                float f = s - 3.0f;
                ring(buf, ps, o.add(0, 1, 0), R1 * smooth(Math.min(1f, f * 1.2f)), 2f, 1f - f, t, 0f);
                Fx.glow(buf, ps, o.add(0, 2, 0), 9f * (1f - f), BOLT, 0.5f * (1f - f));
            }
            if (s >= 6.0f && !over && owner != null) {
                coil(buf, ps, o, t, 1f);
                for (int i = 0; i < 2; i++) {
                    Vec3 q = o.add(polar(t * 0.33 + i * Math.PI, 1.0, 1 + Math.sin(t * 0.2 + i * 2) * 0.8));
                    lightning(buf, ps, q, q.add(Math.sin(t + i) * 1.4, 1.6, Math.cos(t * 0.7 + i) * 1.4), 0.5f, 0.8f, (long) (t / 2) + i);
                }
            }
            if (over && fin != null) {
                float age = t - (float) e.fx().get(0)[1];
                if (age >= 0 && age < 36) {
                    float f = age / 36f;
                    blast(buf, ps, fin, R2, f, t);
                    if (age < 14) {
                        wings(buf, ps, owner, pt, base, t, 1f - age / 14f, 1f - age / 14f);
                    }
                    serpentAt(buf, ps, serpent(), fin.add(0, 14 * (1f - f), 0), new Vec3(0.2, -1, 0.1), 30f * (1f - f), t, 0.9f * (1f - f), STEEL, 0.3f);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Dragon Nova

    public static class Nova extends Base<NovaEntity> {
        public Nova(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(NovaEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, R1 = e.r1(), R2 = e.r2();
            float cl = vis(s, 1.5f, 1.5f, 10.5f, 1.2f);
            Vec3 star = null;
            float sr = 0f, sa = 1f;
            if (s < 1.5f) {
                float f = smooth(s / 1.5f);
                star = ownerP.add(0, 2.6, 0).add(dirOf(owner, pt).scale(0.8));
                sr = 0.3f + 0.7f * f;
                ring(buf, ps, ownerP.add(0, 0.5, 0), 3.5f * f, 1f, 0.7f, t, 0f);
            } else if (s < 3.0f) {
                float f = smooth((s - 1.5f) / 1.5f);
                star = ownerP.add(0, 2.6, 0).lerp(new Vec3(0, 48, 0), f);
                sr = 1f + 4f * f;
            } else if (s < 4.5f) {
                float f = smooth((s - 3.0f) / 1.5f);
                star = new Vec3(0, 48, 0);
                sr = 5f + 7.5f * f;
            } else if (s < 5.5f) {
                float f = (s - 4.5f) / 1.0f;
                star = new Vec3(0, 48, 0).lerp(new Vec3(0, 8, 0), f * f);
                sr = 12.5f;
            } else if (s < 6.0f) {
                float f = (s - 5.5f) / 0.5f;
                star = new Vec3(0, 8, 0);
                sr = 12.5f + 12f * f;
                sa = 1f - 0.3f * f;
            }
            if (cl > 0.01f) {
                cloudDeck(buf, ps, 54f, 40f, 9, t, cl);
            }
            if (star != null) {
                core(buf, ps, star, sr, t, sa);
                if (s > 1.5f) {
                    for (int i = 0; i < 2; i++) {
                        ring(buf, ps, star, sr * (1.5f + 0.4f * i), 1.5f, 0.55f * sa, t * (1 + i), 0.6f * i + 0.3f);
                        orbit(buf, ps, serpent(), star, sr * 1.7f, (float) Math.sin(t * 0.07 + i * 3) * sr * 0.6f, t * 0.09 * (i == 0 ? 1 : -1) + i * 3.14, sr * 2.8f, 0.9f * sa, STEEL, 0.3f);
                    }
                }
                if (s >= 3.0f && s < 5.5f) {
                    cyl(buf, ps, star, new Vec3(star.x, 0, star.z), 1.2f + sr * 0.2f, "vel_gust", Mode.TRANS, SKY, 0.35f, 0.7f, -t * 0.06f);
                    Fx.disc(buf, ps, Gfx.TARGET, new Vec3(0, 0.1, 0), R1 * smooth((s - 3.0f) / 1.0f), t * 2f, BOLT, 0.4f);
                }
            }
            if (s >= 5.5f && s < 6.8f) {
                float f = (s - 5.5f) / 1.3f;
                Fx.wave(buf, ps, new Vec3(0, 0.2, 0), R1 * smooth(Math.min(1f, f * 1.4f)), WHITE, 1f - f);
                Fx.crack(buf, ps, new Vec3(0, 0.12, 0), R1 * 0.8f, 0f, BOLT, 1f - f * 0.5f);
            }
            if (s >= 6.0f && s < 8.5f) {
                float f = (s - 6.0f) / 2.5f;
                float r = 45f * smooth(Math.min(1f, f * 1.3f));
                ps.pushPose();
                ps.translate(0, 9, 0);
                ps.scale(r, r, r);
                mdraw(buf, ps, sphereMesh(), "electric", Mode.ADD, ELEC, 0.3f * (1f - f * 0.5f), 1f, t * 0.01f, t * 0.015f);
                ps.scale(0.93f, 0.93f, 0.93f);
                mdraw(buf, ps, sphereMesh(), "storm_cloud", Mode.TRANS, CHAR, 0.4f * (1f - f * 0.5f), 0.3f, t * 0.003f, 0f);
                ps.popPose();
                ring(buf, ps, new Vec3(0, 1, 0), 45f * smooth(Math.min(1f, f * 2f)), 2f, 0.8f * (1f - f), t, 0f);
                for (int i = 0; i < 5; i++) {
                    double ang = i * 1.256 + t * 0.05;
                    lightning(buf, ps, new Vec3(0, 9, 0), polar(ang, r * 0.9, 9 + Math.sin(t * 0.1 + i) * 8), 1.4f, 0.7f, (long) (t / 3) + i);
                }
            }
            if (s >= 8.5f && s < 11.0f) {
                float f = smooth((s - 8.5f) / 2.0f);
                float r = 45f * (1f - f) + 1f;
                Vec3 c = new Vec3(0, 9, 0);
                core(buf, ps, c, Math.max(0.5f, r * 0.45f), t, 1f);
                for (int i = 0; i < 3; i++) {
                    orbit(buf, ps, serpent(), c, r * 0.7f, (float) Math.sin(t * 0.1 + i) * r * 0.2f, t * 0.14 + i * 2.09, 26f, 0.85f, STEEL, 0.3f);
                }
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI / 4 + t * 0.2;
                    lightning(buf, ps, polar(ang, r, 9 + Math.sin(t * 0.1 + i) * 6), c, 1.2f, 0.8f, (long) (t / 2) + i);
                }
            }
            if (s >= 11.0f && s < 12.0f) {
                blast(buf, ps, Vec3.ZERO, R2, s - 11.0f, t);
            }
        }

        static Vec3 dirOf(Entity o, float pt) {
            return o == null ? Vec3.ZERO : o.getViewVector(pt).multiply(1, 0, 1);
        }
    }

    // ------------------------------------------------------------------ End of the Eternal Storm

    public static class Eternal extends Base<EternalEntity> {
        public Eternal(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        protected void draw(EternalEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
            float s = t / 20f, D = e.r1(), R2 = e.r2();
            if (s < 20.5f && owner != null) {
                float wf = Math.min(smooth((s - 0.4f) / 1.6f), 1f - smooth((s - 19.5f) / 1.0f));
                wings(buf, ps, owner, pt, base, t, wf, 1f);
                if (s < 2.0f) {
                    ring(buf, ps, ownerP.add(0, 0.8, 0), 3f * smooth(s / 2f), 1f, 0.7f, t, 0f);
                }
            }
            float dome = vis(s, 2.0f, 2.0f, 19.0f, 1.5f);
            if (dome > 0.01f) {
                float g = smooth(Math.min(1f, (s - 2.0f) / 2.0f));
                float shat = s > 17f ? smooth((s - 17f) / 1.5f) : 0f;
                float contr = s > 14f && s < 18f ? smooth((s - 14f) / 3.5f) : 0f;
                ps.pushPose();
                ps.scale(D * g, D * g * 0.8f, D * g);
                mdraw(buf, ps, sphereMesh(), "storm_cloud", Mode.TRANS, DARK, 0.22f * dome, 0.1f, t * 0.0015f, 0f);
                ps.popPose();
                // the eyewall: a colossal funnel ring that the dragons climb
                funnel(buf, ps, Vec3.ZERO, 90f, D * 0.55f * g * (1f - 0.3f * contr), t, 0.5f * dome * (1f - shat), 1.5f);
                funnel(buf, ps, Vec3.ZERO, 70f, D * 0.3f * g, t, 0.45f * dome * (1f - shat), -3f);
                cloudDeck(buf, ps, 56f, D * 0.8f * g * (1f - 0.7f * contr), 11, t, dome * (1f - shat));
                for (int i = 0; i < 3; i++) {
                    ring(buf, ps, new Vec3(0, 20f + i * 18f, 0), D * (0.6f - 0.1f * i) * g * (1f - 0.8f * contr), 3f, 0.5f * dome * (1f - contr * 0.8f), t * (i % 2 == 0 ? 1f : -1.2f), 0f);
                }
                if (s > 4f) {
                    boolean white = s > 6.5f && s < 8.5f;
                    for (int i = 0; i < 6; i++) {
                        Random r = new Random((long) (t / 3) * 31 + i * 977L);
                        if (r.nextInt(3) != 0) {
                            continue;
                        }
                        double ang = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * D * 0.8;
                        lightning(buf, ps, polar(ang, d * 0.8, 54), polar(ang + 0.1, d, 0.2), white ? 2.0f : 1.2f, dome * 0.8f, (long) (t / 3) * 7 + i);
                    }
                }
            }
            // three serpents circle up the wall, 4.0 - 16.0
            if (s >= 4.0f && s < 16.5f) {
                float f = vis(s, 4.0f, 2.5f, 14.5f, 2.0f);
                float grow = smooth(Math.min(1f, (s - 4.0f) / 2.5f));
                float contr = s > 14f ? smooth((s - 14f) / 2f) : 0f;
                for (int i = 0; i < 3; i++) {
                    orbit(buf, ps, serpent(), Vec3.ZERO, D * 0.45f * grow * (1f - 0.5f * contr), 22f + i * 20f, t * 0.02 * (1 + i * 0.2) + i * 2.1, 46f, 0.95f * f, STEEL, 0.3f);
                }
                if (s < 14.0f) {
                    float cs = smooth(Math.min(1f, (s - 5.0f) / 1.5f)) * (s > 11f ? 1f - 0.4f * smooth((s - 11f) / 3f) : 1f);
                    core(buf, ps, new Vec3(0, 26, 0), 7f * cs, t, f);
                }
            }
            for (double[] d : e.fx()) {
                float age = t - (float) d[1];
                if (d[0] != 1 || age < 0 || age > 14) {
                    continue;
                }
                Vec3 q = Gfx.v(d[2], d[3], d[4]).subtract(base);
                float k = 1f - age / 14f;
                lightning(buf, ps, q.add(0, 70, 0), q, 2.2f, k, (long) d[1]);
                Fx.crack(buf, ps, q.add(0, 0.12, 0), 6f, (float) d[1] * 37f, BOLT, k);
            }
            if (s >= 6.5f && s < 8.0f) {
                float f = (s - 6.5f) / 1.5f;
                ring(buf, ps, new Vec3(0, 1, 0), 60f * smooth(Math.min(1f, f * 1.2f)), 2f, 0.9f * (1f - f), t, 0f);
            }
            if (s >= 8.5f && s < 11.5f) {
                float f = s < 10.0f ? smooth((s - 8.5f) / 1.5f) : 1f - smooth((s - 10.0f) / 1.5f);
                Vec3 mouth = new Vec3(0, 60, 0);
                head(buf, ps, mouth.add(0, 0.14 * 55, 0), new Vec3(0, -1, 0), 55f * f, 0.9f * f, 0.4f);
                if (s >= 8.5f && s < 10.0f) {
                    core(buf, ps, mouth, 5f * f, t, 1f);
                }
                if (s >= 9.5f && s < 10.6f) {
                    float b = Math.min(1f, (s - 9.5f) / 0.15f) * (1f - smooth((s - 10.2f) / 0.4f));
                    cyl(buf, ps, mouth, Vec3.ZERO, 5f * b, "vel_gust", Mode.TRANS, SKY, 0.55f * b, 0.7f, -t * 0.1f);
                    cyl(buf, ps, mouth, Vec3.ZERO, 3f * b, "electric", Mode.ADD, ELEC, 0.6f * b, 1f, -t * 0.15f);
                    cyl(buf, ps, mouth, Vec3.ZERO, 1.4f * b, "bolt", Mode.ADD, BOLT, 0.9f * b, 1f, -t * 0.2f);
                }
                if (s >= 10.0f && s < 11.5f) {
                    blast(buf, ps, Vec3.ZERO, 60f, (s - 10.0f) / 1.5f, t);
                }
            }
            if (s >= 11.0f && s < 14.0f) {
                float f = smooth((s - 11.0f) / 2.0f);
                for (int i = 0; i < 4; i++) {
                    double ang = i * Math.PI / 2 + t * 0.03;
                    Vec3 c = polar(ang, 26 * (1f - smooth((s - 12.5f) / 1.5f)), 30 + i * 4);
                    core(buf, ps, c, 4f * f, t + i * 9, 1f);
                }
            }
            if (s >= 13.0f && s < 14.5f) {
                blast(buf, ps, Vec3.ZERO, 75f, (s - 13.0f) / 1.5f, t);
            }
            if (s >= 14.0f && s < 18.0f) {
                float f = smooth((s - 14.0f) / 3.5f);
                Vec3 c = new Vec3(0, 24, 0);
                core(buf, ps, c, 14f * (1f - f) + 0.6f, t, 1f);
                for (int i = 0; i < 10; i++) {
                    double ang = i * 0.628 + t * 0.15;
                    lightning(buf, ps, polar(ang, 70 * (1f - f) + 3, 40 * (1f - f) + 20), c, 1.4f, 0.8f, (long) (t / 2) + i);
                }
            }
            if (s >= 18.0f && s < 20.5f) {
                blast(buf, ps, Vec3.ZERO, R2, (s - 18.0f) / 2.5f, t);
            }
        }
    }
}
