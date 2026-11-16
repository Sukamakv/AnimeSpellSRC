package com.example.jjkspells.vfx;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Draws {@link Mesh}es through Minecraft's entity render types, so they get real normal based lighting from the engine.
 * <ul>
 * <li>SOLID: opaque, lit (use for planets, stars, dragons, rock, metal). 'glow' 0..1 lifts the light level so a piece looks self-lit.</li>
 * <li>TRANS: alpha blended, lit (clouds, glass, smoke).</li>
 * <li>ADD: additive and unlit (energy shells, beams); keep these sparing.</li>
 * </ul>
 * Textures live in textures/vfx/NAME.png (see tools/gen_vfx_textures.py).
 */
public final class MeshRender {
    private MeshRender() {
    }

    public enum Mode {SOLID, TRANS, ADD}

    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation> TEX = new ConcurrentHashMap<>();

    /** Builds an object once and keeps it (meshes, part maps). */
    @SuppressWarnings("unchecked")
    public static <T> T cached(String key, Supplier<T> make) {
        return (T) CACHE.computeIfAbsent(key, k -> make.get());
    }

    public static ResourceLocation tex(String name) {
        return TEX.computeIfAbsent(name, n -> JJKSpells.id("textures/vfx/" + n + ".png"));
    }

    public static final float[] WHITE = {1f, 1f, 1f};

    /** Moves the pose to 'at' and turns +Y to point along (to - at); the caller pops the pose. */
    public static void pushAlong(PoseStack ps, net.minecraft.world.phys.Vec3 at, net.minecraft.world.phys.Vec3 to) {
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        net.minecraft.world.phys.Vec3 d = to.subtract(at);
        double l = d.length();
        if (l > 1e-6) {
            ps.mulPose(new org.joml.Quaternionf().rotationTo(0f, 1f, 0f, (float) (d.x / l), (float) (d.y / l), (float) (d.z / l)));
        }
    }

    /** Moves the pose to 'at' and turns +Z to point along 'dir' (a unit-ish vector); the caller pops the pose. */
    public static void pushFacing(PoseStack ps, net.minecraft.world.phys.Vec3 at, net.minecraft.world.phys.Vec3 dir) {
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        double l = dir.length();
        if (l > 1e-6) {
            ps.mulPose(new org.joml.Quaternionf().rotationTo(0f, 0f, 1f, (float) (dir.x / l), (float) (dir.y / l), (float) (dir.z / l)));
        }
    }

    /** Tint helper: linear mix of two colours. */
    public static float[] mix(float[] a, float[] b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        return new float[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }

    public static void draw(MultiBufferSource buf, PoseStack ps, Mesh m, String texture, Mode mode, float[] rgb, float alpha, float glow) {
        draw(buf, ps, m, tex(texture), mode, rgb, alpha, glow, 0f, 0f);
    }

    public static void draw(MultiBufferSource buf, PoseStack ps, Mesh m, String texture, Mode mode, float[] rgb, float alpha, float glow, float uOff, float vOff) {
        draw(buf, ps, m, tex(texture), mode, rgb, alpha, glow, uOff, vOff);
    }

    public static void draw(MultiBufferSource buf, PoseStack ps, Mesh m, ResourceLocation tex, Mode mode, float[] rgb, float alpha, float glow, float uOff, float vOff) {
        if (alpha <= 0.01f || m == null) {
            return;
        }
        RenderType rt = switch (mode) {
            case SOLID -> RenderType.entityCutoutNoCull(tex);
            case TRANS -> com.example.jjkspells.client.SoftRender.entity(tex);
            case ADD -> RenderType.eyes(tex);
        };
        VertexConsumer vc = buf.getBuffer(rt);
        int lv = Math.max(0, Math.min(15, 5 + Math.round(10f * glow)));
        int light = mode == Mode.ADD ? LightTexture.FULL_BRIGHT : LightTexture.pack(lv, lv);
        PoseStack.Pose pose = ps.last();
        Matrix4f pm = pose.pose();
        Matrix3f nm = pose.normal();
        int r = (int) (Math.min(1f, rgb[0]) * 255), g = (int) (Math.min(1f, rgb[1]) * 255), b = (int) (Math.min(1f, rgb[2]) * 255);
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        float[] p = m.pos, n = m.nrm, uv = m.uv;
        int[] q = m.quads;
        // one quad per two triangles (see Mesh.quads): half the vertices of the old triangle-per-quad loop
        for (int k = 0; k < q.length; k++) {
            int v = q[k], v3 = v * 3, v2 = v * 2;
            vc.vertex(pm, p[v3], p[v3 + 1], p[v3 + 2]).color(r, g, b, a).uv(uv[v2] + uOff, uv[v2 + 1] + vOff)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, n[v3], n[v3 + 1], n[v3 + 2]).endVertex();
        }
    }
}
