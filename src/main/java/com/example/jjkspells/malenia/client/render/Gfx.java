package com.example.jjkspells.malenia.client.render;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Textures, colours and buffer helpers shared by the Malenia renderers. */
final class Gfx {
    private Gfx() {
    }

    static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/environment/mel_" + n + ".png");
    }

    // additive light textures
    static final ResourceLocation GLOW = tex("glow"), GLOW_SILVER = tex("glow_silver"), CIRCLE = tex("circle"), RUNE = tex("rune_ring"), RING_WAVE = tex("ring_wave"),
            WAVE = tex("wave"), TARGET = tex("target"), LINE = tex("line"), STAR = tex("star"), CRESCENT = tex("crescent"), SLASH = tex("slash"), HALO = tex("halo"),
            PETAL = tex("petal"), FLOWER = tex("flower"), SPORE = tex("spore"), CRACKS = tex("cracks"), SPHERE = tex("sphere"), WING = tex("wing"), BUTTERFLY = tex("butterfly");
    // blended textures
    static final ResourceLocation MIST = tex("mist"), ROT_WAVE = tex("rot_wave"), PHANTOM = tex("phantom"), SWORD = tex("sword"), TRAIL = tex("trail");

    static final float[] WHITE = {1f, 1f, 1f}, SILVER = {0.86f, 0.9f, 1f}, GOLD = {1f, 0.84f, 0.45f}, CRIMSON = {0.9f, 0.12f, 0.2f}, SCARLET = {1f, 0.3f, 0.24f},
            DARKRED = {0.6f, 0.06f, 0.1f}, PINK = {1f, 0.55f, 0.62f}, ROTC = {0.85f, 0.4f, 0.25f}, BROWN = {0.6f, 0.38f, 0.3f};

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
}
