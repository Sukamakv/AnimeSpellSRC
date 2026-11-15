package com.example.jjkspells.malphite.client.render;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Textures, colours and buffer helpers shared by the Malphite renderers. */
final class Gfx {
    private Gfx() {
    }

    static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/environment/mal_" + n + ".png");
    }

    // additive light textures
    static final ResourceLocation GLOW = tex("glow"), CIRCLE = tex("circle"), RUNE = tex("rune_ring"), RING_WAVE = tex("ring_wave"), WAVE = tex("wave"),
            CRACKS = tex("cracks"), TARGET = tex("target"), LINE = tex("line"), STAR = tex("star"), EMBER_COL = tex("ember_col");
    // blended stone textures
    static final ResourceLocation MIST = tex("mist"), DUST_COL = tex("dust_col"), DUST_WAVE = tex("dust_wave"), SPIKE = tex("spike"), ROCK = tex("rock"),
            SHARD = tex("shard"), TRAIL = tex("trail"), PLATE = tex("plate"), DOME = tex("dome"), SILHOUETTE = tex("silhouette"), FIST = tex("fist"), ARC = tex("arc");

    static final float[] WHITE = {1f, 1f, 1f}, ORANGE = {1f, 0.5f, 0.12f}, AMBER = {1f, 0.72f, 0.28f}, HOT = {1f, 0.88f, 0.6f},
            STONE = {0.92f, 0.9f, 0.88f}, DUSTC = {0.86f, 0.76f, 0.62f}, DARK = {0.7f, 0.68f, 0.66f};

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

    /** A stone spike standing at (x, z) in the current local space: width, full height, how far it has risen (0..1), alpha. */
    static void spike(MultiBufferSource buf, PoseStack ps, float x, float z, float w, float h, float rise, float rot, float alpha, int light) {
        if (rise <= 0.01f || alpha <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(x, 0, z);
        Draw.cross(trans(buf, SPIKE), ps, w, -h * (1f - rise) * 0.15f, h * rise, rot, STONE, alpha, light);
        ps.popPose();
    }

    /** rise 0..1 over 'up' ticks after t0, then hold for 'hold' ticks, then sink away over 'down' ticks. */
    static float life(float t, float t0, float up, float hold, float down) {
        float a = smooth((t - t0) / Math.max(0.01f, up));
        float b = 1f - smooth((t - t0 - up - hold) / Math.max(0.01f, down));
        return Math.min(a, b);
    }
}
