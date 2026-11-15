package com.example.jjkspells.tempest.client.render;

import com.example.jjkspells.vfx.Mesh;
import com.example.jjkspells.vfx.MeshRender;
import com.example.jjkspells.vfx.MeshRender.Mode;
import com.example.jjkspells.vfx.Noise;
import com.example.jjkspells.vfx.Prim;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/** Cached meshes and small 3D drawing helpers for the Rimuru (void / predation) renderers. */
final class RimGfx {
    private RimGfx() {
    }

    // cold palette: black, navy, teal, ice. Tints multiply the textures.
    static final float[] ICE = {0.78f, 0.94f, 1f}, AQUA = {0.3f, 0.88f, 1f}, TEAL = {0.14f, 0.62f, 0.74f}, NAVY = {0.07f, 0.12f, 0.36f},
            ABYSS = {0.02f, 0.03f, 0.07f}, STEEL = {0.42f, 0.55f, 0.72f}, CLOUD = {0.26f, 0.32f, 0.48f}, PURE = {1f, 1f, 1f};

    // ------------------------------------------------------------------ cached meshes

    static Mesh sphere() {
        return MeshRender.cached("rim.sphere", () -> Prim.sphere(1, 28, 40));
    }

    static Mesh sphereLo() {
        return MeshRender.cached("rim.sphereLo", () -> Prim.sphere(1, 12, 18));
    }

    static Mesh hexSphere() {
        return MeshRender.cached("rim.hexSphere", () -> Prim.sphere(1, 24, 36).scaleUv(6, 4));
    }

    static Mesh lumpy(int i) {
        return MeshRender.cached("rim.lumpy" + (i & 3), () -> Prim.lumpy(1, 20, 28, 0.32, 1.7, 100 + (i & 3) * 17));
    }

    static Mesh rock(int i) {
        return MeshRender.cached("rim.rock" + (i & 5), () -> Prim.lumpy(1, 7, 10, 0.38, 1.6, 7 + (i % 6) * 13).flat());
    }

    static Mesh tent(int i) {
        return MeshRender.cached("rim.tent" + (i & 7), () -> Prim.tentacle(40 + (i & 7) * 7L, 1.0, 0.065, 1.8 + 0.28 * (i & 7), 28, 8));
    }

    static Mesh cloud(int i) {
        return MeshRender.cached("rim.cloud" + (i & 3), () -> Prim.cloud(5 + (i & 3) * 11L, 1.0, 8));
    }

    static Mesh shard(int i) {
        return MeshRender.cached("rim.shard" + (i & 7), () -> Prim.shard(21 + (i & 7) * 5L, 1.0, 0.16));
    }

    static Mesh pylon() {
        return MeshRender.cached("rim.pylon", () -> Prim.crystal(0.12, 1.0, 6, 0.35, 0.0));
    }

    static Mesh torusThin() {
        return MeshRender.cached("rim.torusThin", () -> Prim.torus(1, 0.012, 96, 6));
    }

    static Mesh torusMed() {
        return MeshRender.cached("rim.torusMed", () -> Prim.torus(1, 0.03, 72, 8));
    }

    static Mesh discIn() {
        return MeshRender.cached("rim.discIn", () -> Prim.disc(1.2, 2.2, 96, 5));
    }

    static Mesh discOut() {
        return MeshRender.cached("rim.discOut", () -> Prim.disc(2.2, 4.2, 96, 10));
    }

    static Mesh discFlat() {
        return MeshRender.cached("rim.discFlat", () -> Prim.disc(0.02, 1.0, 96, 12));
    }

    static Mesh cone() {
        return MeshRender.cached("rim.cone", () -> Prim.cone(1, 1, 12));
    }

    static Mesh beamMesh() {
        return MeshRender.cached("rim.beam", RimModels::beam);
    }

    static Mesh openTube() {
        return MeshRender.cached("rim.openTube", () -> RimModels.openTube(48));
    }

    static Mesh hemi() {
        return MeshRender.cached("rim.hemi", () -> RimModels.hemi(16, 56));
    }

    static Mesh lens() {
        return MeshRender.cached("rim.lens", () -> RimModels.lens(1, 0.13));
    }

    static Mesh geo(int f) {
        return MeshRender.cached("rim.geo" + f, () -> RimModels.geodesic(f));
    }

    static Mesh geoEdges(int f) {
        return MeshRender.cached("rim.geoEdges" + f, () -> RimModels.geodesicEdges(f, 0.008));
    }

    static Mesh rift(int i) {
        return MeshRender.cached("rim.rift" + (i & 3), () -> RimModels.rift(3 + (i & 3) * 7L, 26));
    }

    static Mesh mawRing(int k) {
        return MeshRender.cached("rim.maw" + k, () -> k == 0 ? RimModels.mawRing(26, 1.0, 0.34, 0.45) : (k == 1 ? RimModels.mawRing(18, 0.7, 0.3, 0.55) : RimModels.mawRing(12, 0.44, 0.26, 0.65)));
    }

    static Mesh bolt(int seed, int thick) {
        double r = thick == 0 ? 0.0035 : (thick == 1 ? 0.0075 : 0.015);
        return MeshRender.cached("rim.bolt" + (seed & 7) + "." + thick, () -> Prim.lightning(300 + (seed & 7) * 13L, new double[]{0, 0, 0}, new double[]{0, 1, 0}, 5, 0.2, r, 3));
    }

    // ------------------------------------------------------------------ drawing

    /** Draws a mesh at a position with yaw / pitch / roll (degrees) and a per-axis scale. */
    static void put(MultiBufferSource buf, PoseStack ps, Mesh m, String tex, Mode mode, Vec3 at, float sx, float sy, float sz, float yaw, float pitch, float roll, float[] tint, float alpha, float glow, float uo, float vo) {
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

    /** A flat expanding ring on the ground (thin torus, additive). */
    static void ringFlat(MultiBufferSource buf, PoseStack ps, Vec3 c, float R, float[] tint, float alpha) {
        if (R < 0.2f) {
            return;
        }
        put(buf, ps, torusThin(), "white", Mode.ADD, c, R, R, R, 0f, 0f, 0f, tint, alpha, 1f, 0f, 0f);
    }

    /** A thin ring around a centre that always faces the camera (photon ring, lens flare ring). */
    static void camRing(MultiBufferSource buf, PoseStack ps, Vec3 c, float R, Vec3 cam, float[] tint, float alpha) {
        MeshRender.pushAlong(ps, c, cam);
        ps.scale(R, R, R);
        MeshRender.draw(buf, ps, torusMed(), "white", Mode.SOLID, tint, alpha, 1f, 0f, 0f);
        ps.popPose();
    }

    /** A straight beam from a to b, width w (additive column). */
    static void beam(MultiBufferSource buf, PoseStack ps, Vec3 a, Vec3 b, float w, float[] tint, float alpha) {
        if (alpha <= 0.01f || w <= 0.01f) {
            return;
        }
        double len = b.subtract(a).length();
        if (len < 0.01) {
            return;
        }
        MeshRender.pushAlong(ps, a, b);
        ps.scale(w, (float) len, w);
        MeshRender.draw(buf, ps, beamMesh(), "white", Mode.ADD, tint, alpha, 1f, 0f, 0f);
        ps.popPose();
    }

    /** A dark solid strut between two points. */
    static void strut(MultiBufferSource buf, PoseStack ps, Vec3 a, Vec3 b, float w, float[] tint, float glow) {
        double len = b.subtract(a).length();
        if (len < 0.01) {
            return;
        }
        MeshRender.pushAlong(ps, a, b);
        ps.scale(w, (float) len, w);
        MeshRender.draw(buf, ps, beamMesh(), "rock", Mode.SOLID, tint, 1f, glow, 0f, 0f);
        ps.popPose();
    }

    /** A 3D lightning bolt from a to b (thick 0 thin .. 2 heavy). */
    static void bolt(MultiBufferSource buf, PoseStack ps, Vec3 a, Vec3 b, int thick, int seed, float[] tint, float alpha) {
        double len = b.subtract(a).length();
        if (alpha <= 0.01f || len < 0.1) {
            return;
        }
        MeshRender.pushAlong(ps, a, b);
        ps.scale((float) len, (float) len, (float) len);
        MeshRender.draw(buf, ps, bolt(seed, thick), "bolt", Mode.ADD, tint, alpha, 1f, 0f, 0f);
        ps.popPose();
    }

    static void sphere(MultiBufferSource buf, PoseStack ps, String tex, Mode mode, Vec3 at, float r, float[] tint, float alpha, float glow, float yaw) {
        if (r < 0.02f) {
            return;
        }
        put(buf, ps, sphere(), tex, mode, at, r, r, r, yaw, 0f, 0f, tint, alpha, glow, 0f, 0f);
    }

    static double hash(int a, int b) {
        return Noise.hash(a, b);
    }


    /** Rocks torn from the ground spiralling into a point c: they stretch along the orbit and shrink as they fall in. */
    static void debris(MultiBufferSource buf, PoseStack ps, int count, Vec3 c, float rOut, float rIn, float s, float t, float grow, float fade) {
        for (int i = 0; i < count; i++) {
            float ph = (s * 0.16f + i / (float) count) % 1f;
            float rad = rOut + (rIn - rOut) * ph * ph;
            double ang = i * 2.399 + 0.9 * s + 5.0 * ph * ph * ph;
            float y = (float) (c.y * ph * ph + 0.6 + 1.5 * Math.sin(i + s) * (1f - ph));
            float size = (float) (0.7 + 1.1 * hash(i, 3)) * (1f - 0.7f * ph * ph) * grow * fade;
            Vec3 p = new Vec3(c.x + Math.cos(ang) * rad, y, c.z + Math.sin(ang) * rad);
            float yaw = (float) -(Math.toDegrees(ang) + 90.0);
            put(buf, ps, rock(i), "rock", Mode.SOLID, p, size * (1f + 2f * ph), size * (1f - 0.3f * ph), size, yaw, t * 3f + i * 40f, i * 20f, STEEL, 1f, 0.2f, 0f, 0f);
        }
    }

    /** The shared pre-cast gathering: a small cold core above the point and a turning ring on the floor. */
    static void gather(MultiBufferSource buf, PoseStack ps, float s, float until, Vec3 at, float t) {
        if (s >= until) {
            return;
        }
        float f = s / until;
        sphere(buf, ps, "white", Mode.SOLID, at.add(0, 1.8 + f * 1.5, 0), 0.25f + f * 0.9f, AQUA, 1f, 1f, 0f);
        put(buf, ps, torusMed(), "white", Mode.SOLID, at.add(0, 0.1, 0), 2.5f + 3f * f, 2.5f + 3f * f, 2.5f + 3f * f, t * 3f, 0f, 0f, ICE, 1f, 1f, 0f, 0f);
    }
}
