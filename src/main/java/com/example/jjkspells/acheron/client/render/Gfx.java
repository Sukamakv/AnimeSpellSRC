package com.example.jjkspells.acheron.client.render;

import com.example.jjkspells.vfx.MeshRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Colours and buffer helpers shared by the Acheron renderers. All textures live in textures/vfx/ (see tools/gen_ach_tex.py). */
final class Gfx {
    private Gfx() {
    }

    static ResourceLocation tex(String n) {
        return MeshRender.tex(n);
    }

    static final ResourceLocation GLOW = tex("acheron_glow"), LINE = tex("acheron_line");

    // palette: violet (Wiltcross, Octobolt), crimson (knots, Rainblade, the Ultimate), white, black
    static final float[] WHITE = {1f, 1f, 1f}, BLK = {0.02f, 0.02f, 0.03f},
            VIO = {0.62f, 0.45f, 1f}, VIOHI = {0.88f, 0.8f, 1f}, VIODEEP = {0.3f, 0.12f, 0.6f},
            CRIM = {0.86f, 0.1f, 0.21f}, CRIMHI = {1f, 0.55f, 0.6f}, CRIMDEEP = {0.55f, 0.06f, 0.14f},
            GREY = {0.55f, 0.55f, 0.58f};

    /** The caster's hand (a bit in front of the eyes, lowered). */
    static Vec3 hand(net.minecraft.world.entity.Entity o, float pt) {
        return o.getEyePosition(pt).add(o.getViewVector(pt).scale(0.7)).add(0, -0.35, 0);
    }

    static Vec3 v(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    static float c01(float v) {
        return Mth.clamp(v, 0f, 1f);
    }

    static float smooth(float v) {
        v = c01(v);
        return v * v * (3f - 2f * v);
    }

    static VertexConsumer add(MultiBufferSource b, ResourceLocation t) {
        return b.getBuffer(RenderType.eyes(t));
    }

    static VertexConsumer trans(MultiBufferSource b, ResourceLocation t) {
        return b.getBuffer(com.example.jjkspells.client.SoftRender.entity(t));
    }

    /** Camera position in the local space of an entity rendered at base. */
    static Vec3 cam(Vec3 base) {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(base);
    }

    /** Turns the pose to face the camera (billboard). */
    static void billboard(PoseStack ps) {
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
    }

    /** rise 0..1 over 'up' ticks after t0, then hold for 'hold' ticks, then sink away over 'down' ticks. */
    static float life(float t, float t0, float up, float hold, float down) {
        float a = smooth((t - t0) / Math.max(0.01f, up));
        float b = 1f - smooth((t - t0 - up - hold) / Math.max(0.01f, down));
        return Math.min(a, b);
    }

    /** A stable pseudo random number in 0..1 for an index and a salt. */
    static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
