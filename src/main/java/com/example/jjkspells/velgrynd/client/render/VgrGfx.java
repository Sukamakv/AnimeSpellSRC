package com.example.jjkspells.velgrynd.client.render;

import com.example.jjkspells.vfx.Mesh;
import com.example.jjkspells.vfx.MeshRender;
import com.example.jjkspells.vfx.MeshRender.Mode;
import com.example.jjkspells.vfx.Models;
import com.example.jjkspells.vfx.Prim;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/** Cached meshes and 3D drawing helpers for the Velgrynd renderers: stellar fire and sacred geometry (suns, gold armillaries, lances, crystals). */
final class VgrGfx {
    private VgrGfx() {
    }

    // tints multiply the textures
    static final float[] PURE = {1f, 1f, 1f}, GOLD = {1f, 0.93f, 0.74f}, HOT = {1f, 0.97f, 0.88f}, CRIM = {1f, 0.5f, 0.45f}, EMBER = {1f, 0.62f, 0.3f},
            DARK = {0.35f, 0.12f, 0.1f};

    // ------------------------------------------------------------------ cached meshes

    static Mesh sun() {
        return MeshRender.cached("vgr.sun", () -> Prim.lumpy(1, 30, 44, 0.03, 3.0, 11).scaleUv(3, 1.5));
    }

    static Mesh sunLo() {
        return MeshRender.cached("vgr.sunLo", () -> Prim.lumpy(1, 14, 22, 0.03, 3.0, 11).scaleUv(3, 1.5));
    }

    static Mesh sphereLo() {
        return MeshRender.cached("vgr.sphereLo", () -> Prim.sphere(1, 10, 16).scaleUv(2, 1));
    }

    static Mesh torusLo() {
        return MeshRender.cached("vgr.torusLo", () -> Prim.torus(1, 0.016, 40, 5).scaleUv(15, 1));
    }

    static Mesh bandLo() {
        return MeshRender.cached("vgr.bandLo", () -> VgrModels.pipe(1, 0.12, 40, 4, 1).xf(com.example.jjkspells.vfx.Xf.t(0, -0.06, 0)));
    }

    static Mesh sphere() {
        return MeshRender.cached("vgr.sphere", () -> Prim.sphere(1, 24, 36).scaleUv(2, 1));
    }

    static Mesh torus() {
        return MeshRender.cached("vgr.torus", () -> Prim.torus(1, 0.016, 96, 8).scaleUv(36, 1));
    }

    static Mesh torusFat() {
        return MeshRender.cached("vgr.torusFat", () -> Prim.torus(1, 0.05, 72, 10).scaleUv(18, 1));
    }

    static Mesh notched(int n) {
        return MeshRender.cached("vgr.notched" + n, () -> VgrModels.notchedRing(n, 0.02));
    }

    static Mesh band() {
        return MeshRender.cached("vgr.band", () -> VgrModels.pipe(1, 0.12, 96, 10, 1).xf(com.example.jjkspells.vfx.Xf.t(0, -0.06, 0)));
    }

    static Mesh solid(Prim.Poly k) {
        return MeshRender.cached("vgr.solid" + k, () -> Prim.poly(k));
    }

    static Mesh wire(Prim.Poly k) {
        return MeshRender.cached("vgr.wire" + k, () -> VgrModels.wire(k, 0.014));
    }

    static Mesh lance(int i) {
        return MeshRender.cached("vgr.lance" + (i & 3), () -> Models.lance(5 + (i & 3) * 7L));
    }

    static Mesh prom(int i) {
        return MeshRender.cached("vgr.prom" + (i & 7), () -> VgrModels.prominence(3 + (i & 7) * 11L, 1.0, 0.55, 0.045));
    }

    static Mesh crown(int n) {
        return MeshRender.cached("vgr.crown" + n, () -> VgrModels.crown(n, 1.0, 1.0, 0.06, 40 + n));
    }

    static Mesh magma() {
        return MeshRender.cached("vgr.magma", () -> Prim.disc(0.02, 1.0, 96, 12).scaleUv(9, 3));
    }

    static Mesh sigil() {
        return MeshRender.cached("vgr.sigil", VgrModels::sigil);
    }

    static Mesh pipe() {
        return MeshRender.cached("vgr.pipe", () -> VgrModels.pipe(1, 1, 28, 3, 6));
    }

    static Mesh pipeCone() {
        return MeshRender.cached("vgr.pipeCone", () -> VgrModels.pipe(0.35, 1, 1, 28, 3, 4));
    }

    static Mesh plate() {
        return MeshRender.cached("vgr.plate", () -> Prim.box(1, 1, 0.01));
    }

    static Mesh disc() {
        return MeshRender.cached("vgr.disc", () -> Prim.disc(0.02, 1.0, 64, 4).scaleUv(2, 1));
    }

    static Mesh star() {
        return MeshRender.cached("vgr.star", () -> Models.spikedStar(0.5));
    }

    static Mesh shard(int i) {
        return MeshRender.cached("vgr.shard" + (i & 7), () -> Prim.shard(61 + (i & 7) * 9L, 1.0, 0.1));
    }

    static Map<String, Mesh> dragon() {
        return MeshRender.cached("vgr.dragon", () -> Models.western(9, true));
    }

    static Map<String, Mesh> wing() {
        return MeshRender.cached("vgr.wing", () -> Models.wing(4, 1.7));
    }

    // ------------------------------------------------------------------ drawing

    static void put(MultiBufferSource buf, PoseStack ps, Mesh m, String tex, Mode mode, Vec3 at, float sx, float sy, float sz, float yaw, float pitch, float roll,
                    float[] tint, float alpha, float glow, float uo, float vo) {
        if (alpha <= 0.01f || sx == 0f || sy == 0f || sz == 0f) {
            return;
        }
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        if (yaw != 0f) {
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
        }
        if (pitch != 0f) {
            ps.mulPose(Axis.XP.rotationDegrees(pitch));
        }
        if (roll != 0f) {
            ps.mulPose(Axis.ZP.rotationDegrees(roll));
        }
        ps.scale(sx, sy, sz);
        MeshRender.draw(buf, ps, m, tex, mode, tint, alpha, glow, uo, vo);
        ps.popPose();
    }

    static void put(MultiBufferSource buf, PoseStack ps, Mesh m, String tex, Mode mode, Vec3 at, float s, float yaw, float[] tint, float alpha, float glow) {
        put(buf, ps, m, tex, mode, at, s, s, s, yaw, 0f, 0f, tint, alpha, glow, 0f, 0f);
    }

    static Vec3 polar(double ang, double r, double y) {
        return new Vec3(Math.cos(ang) * r, y, Math.sin(ang) * r);
    }

    static float sm(float v) {
        v = Math.max(0f, Math.min(1f, v));
        return v * v * (3f - 2f * v);
    }

    /** rises over 'in' seconds from a, falls over 'out' seconds after b. */
    static float vis(float s, float a, float in, float b, float out) {
        return Math.min(sm((s - a) / Math.max(0.01f, in)), 1f - sm((s - b) / Math.max(0.01f, out)));
    }

    /** A real sun: granulated surface, slowly turning, with a faint additive corona shell. */
    static void sunBody(MultiBufferSource buf, PoseStack ps, Vec3 at, float r, float t, float alpha) {
        if (r < 0.03f || alpha <= 0.01f) {
            return;
        }
        boolean lo = r < 5f;
        put(buf, ps, lo ? sunLo() : sun(), "sun", Mode.SOLID, at, r, r, r, t * 0.5f, 8f, 0f, PURE, alpha, 1f, t * 0.0015f, 0f);
        put(buf, ps, lo ? sphereLo() : sphere(), "plasma", Mode.ADD, at, r * 1.08f, r * 1.08f, r * 1.08f, -t * 0.3f, 0f, 0f, EMBER, 0.28f * alpha, 1f, t * 0.004f, -t * 0.006f);
    }

    /** A sun translucent shell for expanding blasts. */
    static void sunShell(MultiBufferSource buf, PoseStack ps, Vec3 at, float r, float t, float alpha) {
        if (r < 0.05f || alpha <= 0.01f) {
            return;
        }
        put(buf, ps, sun(), "sun", Mode.TRANS, at, r, r, r, t * 0.4f, 0f, 0f, HOT, alpha, 1f, t * 0.003f, 0f);
    }

    /** Gold ring(s) of an armillary sphere: n rings tilted around the Y axis, each turning at its own speed. */
    static void armillary(MultiBufferSource buf, PoseStack ps, Vec3 at, float R, float t, float alpha, int n, float speed) {
        if (alpha <= 0.01f || R < 0.05f) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float yaw = t * speed * (i % 2 == 0 ? 1f : -1.3f) + i * (180f / n);
            float tilt = 90f - i * 18f;
            float rr = R * (1f - 0.06f * i);
            put(buf, ps, R < 9f ? torusLo() : (i % 3 == 2 ? notched(24) : torus()), "gold_plate", Mode.SOLID, at, rr, rr, rr, yaw, tilt, i * 30f, GOLD, alpha, 0.85f, 0f, 0f);
        }
        put(buf, ps, R < 9f ? bandLo() : band(), "gold_plate", Mode.SOLID, at, R * 0.96f, R * 0.96f, R * 0.96f, t * speed * 0.4f, 0f, 0f, GOLD, alpha, 0.85f, 0f, 0f);
    }

    static void wire(MultiBufferSource buf, PoseStack ps, Prim.Poly k, Vec3 at, float sx, float sy, float sz, float yaw, float pitch, float roll, float alpha) {
        put(buf, ps, wire(k), "gold_plate", Mode.SOLID, at, sx, sy, sz, yaw, pitch, roll, GOLD, alpha, 0.9f, 0f, 0f);
    }

    static void crystal(MultiBufferSource buf, PoseStack ps, Prim.Poly k, Vec3 at, float s, float yaw, float pitch, float alpha) {
        put(buf, ps, solid(k), "crystal_red", Mode.SOLID, at, s, s, s, yaw, pitch, 0f, PURE, alpha, 0.7f, 0f, 0f);
    }

    /** A lance from a to b (a = butt, b = tip); thick scales the radius against the length. */
    static void lance(MultiBufferSource buf, PoseStack ps, int i, Vec3 a, Vec3 b, float thick, float alpha) {
        double len = b.subtract(a).length();
        if (alpha <= 0.01f || len < 0.1) {
            return;
        }
        MeshRender.pushAlong(ps, a, b);
        float w = (float) len * thick;
        ps.scale(w, (float) len, w);
        MeshRender.draw(buf, ps, lance(i), "gold_plate", Mode.SOLID, HOT, alpha, 1f, 0f, 0f);
        ps.popPose();
    }

    /** A straight plasma column between two points: white-hot core inside a streaming plasma sheath. */
    static void column(MultiBufferSource buf, PoseStack ps, Vec3 a, Vec3 b, float w, float t, float alpha) {
        double len = b.subtract(a).length();
        if (alpha <= 0.01f || w < 0.02f || len < 0.1) {
            return;
        }
        MeshRender.pushAlong(ps, a, b);
        ps.pushPose();
        ps.scale(w, (float) len, w);
        MeshRender.draw(buf, ps, pipe(), "plasma", Mode.TRANS, EMBER, 0.85f * alpha, 1f, 0f, -t * 0.045f);
        ps.popPose();
        ps.pushPose();
        ps.scale(w * 0.42f, (float) len, w * 0.42f);
        MeshRender.draw(buf, ps, pipe(), "plasma", Mode.SOLID, PURE, alpha, 1f, t * 0.01f, -t * 0.09f);
        ps.popPose();
        ps.pushPose();
        ps.scale(w * 0.2f, (float) len, w * 0.2f);
        MeshRender.draw(buf, ps, pipe(), "white", Mode.SOLID, PURE, alpha, 1f, 0f, 0f);
        ps.popPose();
        ps.popPose();
    }

    /** A column of white fire rising from the ground. */
    static void pillar(MultiBufferSource buf, PoseStack ps, Vec3 at, float h, float r, float t, float alpha) {
        if (alpha <= 0.01f || h < 0.5f || r < 0.1f) {
            return;
        }
        column(buf, ps, at, at.add(0, h, 0), r, t, alpha);
        put(buf, ps, torus(), "gold_plate", Mode.SOLID, at.add(0, 0.2, 0), r * 1.9f, r * 1.9f, r * 1.9f, t * 4f, 0f, 0f, GOLD, alpha, 0.9f, 0f, 0f);
        put(buf, ps, magma(), "magma", Mode.SOLID, at.add(0, 0.08, 0), r * 3f, r * 3f, r * 3f, 0f, 0f, 0f, PURE, alpha, 0.9f, t * 0.002f, 0f);
    }

    /** A ring of gold lying on the ground (shockwave): flat torus. */
    static void shock(MultiBufferSource buf, PoseStack ps, Vec3 c, float R, float alpha) {
        if (R < 0.2f || alpha <= 0.01f) {
            return;
        }
        put(buf, ps, torusFat(), "gold_plate", Mode.SOLID, c.add(0, 0.25, 0), R, R * 0.35f, R, 0f, 0f, 0f, GOLD, alpha, 1f, 0f, 0f);
    }

    static void sigil(MultiBufferSource buf, PoseStack ps, Vec3 c, float R, float yaw, float alpha) {
        put(buf, ps, sigil(), "gold_plate", Mode.SOLID, c, R, R, R, yaw, 0f, 0f, GOLD, alpha, 0.8f, 0f, 0f);
    }

    static void magma(MultiBufferSource buf, PoseStack ps, Vec3 c, float R, float t, float alpha) {
        put(buf, ps, magma(), "magma", Mode.SOLID, c.add(0, 0.06, 0), R, R, R, 0f, 0f, 0f, PURE, alpha, 0.85f, t * 0.0007f, 0f);
    }

    static void crownBlades(MultiBufferSource buf, PoseStack ps, Vec3 c, float R, float len, float yaw, float pitch, float alpha) {
        put(buf, ps, crown(16), "gold_plate", Mode.SOLID, c, R, len, R, yaw, pitch, 0f, HOT, alpha, 1f, 0f, 0f);
    }

    /** Eruption at a point: a rising sun shell, a shock ring and a crown of blades. f runs 0..1. */
    static void burst(MultiBufferSource buf, PoseStack ps, Vec3 p, float R, float f, float t) {
        if (f < 0f || f > 1f) {
            return;
        }
        float a = 1f - f;
        sunShell(buf, ps, p.add(0, R * 0.2f * sm(f), 0), R * 0.7f * sm(Math.min(1f, f * 1.4f)), t, a * 0.85f);
        shock(buf, ps, p, R * 1.1f * sm(Math.min(1f, f * 1.2f)), a);
        crownBlades(buf, ps, p.add(0, 0.2, 0), R * (0.3f + 0.7f * sm(f)), R * 0.5f * (1f - f * 0.5f), t * 2f, 0f, a);
    }

    /** The faceted crystal fire dragon: body, head, horns and flapping wings. dir = the way it faces. */
    static void dragon(MultiBufferSource buf, PoseStack ps, Vec3 at, Vec3 dir, float scale, float t, float alpha, float flap) {
        if (alpha <= 0.01f || scale < 0.05f) {
            return;
        }
        Map<String, Mesh> d = dragon();
        MeshRender.pushFacing(ps, at, dir);
        ps.scale(scale, scale, scale);
        MeshRender.draw(buf, ps, d.get("body"), "crystal_red", Mode.SOLID, PURE, alpha, 0.55f, 0f, 0f);
        MeshRender.draw(buf, ps, d.get("head"), "crystal_red", Mode.SOLID, HOT, alpha, 0.7f, 0f, 0f);
        MeshRender.draw(buf, ps, d.get("horns"), "gold_plate", Mode.SOLID, GOLD, alpha, 0.9f, 0f, 0f);
        MeshRender.draw(buf, ps, d.get("eyes"), "white", Mode.SOLID, HOT, alpha, 1f, 0f, 0f);
        Map<String, Mesh> w = wing();
        float ang = (float) Math.sin(t * flap) * 26f + 12f;
        for (int side = -1; side <= 1; side += 2) {
            ps.pushPose();
            ps.translate(side * 0.08, 0.2, 0.42);
            ps.scale(side, 1f, 1f);
            ps.mulPose(Axis.ZP.rotationDegrees(ang));
            MeshRender.draw(buf, ps, w.get("bones"), "gold_plate", Mode.SOLID, GOLD, alpha, 0.8f, 0f, 0f);
            MeshRender.draw(buf, ps, w.get("membrane"), "membrane", Mode.TRANS, PURE, 0.85f * alpha, 0.8f, 0f, 0f);
            ps.popPose();
        }
        ps.popPose();
    }

    /** Only the dragon's head with a gold ring at the muzzle (the fourfold bodies). */
    static void dragonHead(MultiBufferSource buf, PoseStack ps, Vec3 at, Vec3 dir, float scale, float alpha) {
        if (alpha <= 0.01f || scale < 0.05f) {
            return;
        }
        Map<String, Mesh> d = dragon();
        MeshRender.pushFacing(ps, at, dir);
        ps.scale(scale, scale, scale);
        ps.translate(0.0, -0.68, -0.92);
        MeshRender.draw(buf, ps, d.get("head"), "crystal_red", Mode.SOLID, HOT, alpha, 0.8f, 0f, 0f);
        MeshRender.draw(buf, ps, d.get("horns"), "gold_plate", Mode.SOLID, GOLD, alpha, 0.9f, 0f, 0f);
        MeshRender.draw(buf, ps, d.get("eyes"), "white", Mode.SOLID, HOT, alpha, 1f, 0f, 0f);
        ps.popPose();
    }
}
