package com.example.jjkspells.shadow.client.render;

import com.example.jjkspells.vfx.MeshRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Colours and buffer helpers shared by the Shadow renderers. All textures live in textures/vfx/ (see tools/gen_shd_tex.py). */
final class Gfx {
    private Gfx() {
    }

    static ResourceLocation tex(String n) {
        return MeshRender.tex(n);
    }

    static final ResourceLocation GLOW = tex("shd_glow"), LINE = tex("shd_line"), RINGT = tex("shd_ring");

    // palette: #09070F void, #31104F deep violet, #D8D6E8 silver, #A45CFF violet; atomic #31005E #6D19B8 #AE4DFF white
    static final float[] WHITE = {1f, 1f, 1f}, VOID = {0.04f, 0.03f, 0.06f}, DEEP = {0.19f, 0.06f, 0.31f}, SILVER = {0.85f, 0.84f, 0.91f}, VIOLET = {0.64f, 0.36f, 1f},
            PALE = {0.78f, 0.68f, 1f}, ROYAL = {0.29f, 0.12f, 0.47f}, ASH = {0.42f, 0.4f, 0.46f}, LILAC = {0.71f, 0.6f, 1f}, SLIME = {0.34f, 0.15f, 0.54f},
            SLIMEHI = {0.7f, 0.42f, 1f}, A_DEEP = {0.19f, 0f, 0.37f}, A_MID = {0.43f, 0.1f, 0.72f}, A_HI = {0.68f, 0.3f, 1f}, A_CORE = {0.95f, 0.9f, 1f}, A_DARK = {0.03f, 0.02f, 0.06f};

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
