package com.example.jjkspells.pantheon.client.render;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Textures, colours and buffer helpers shared by the Pantheon renderers. */
final class Gfx {
    private Gfx() {
    }

    static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/environment/pan_" + n + ".png");
    }

    static final ResourceLocation GLOW = tex("glow"), CIRCLE = tex("circle"), RUNE = tex("rune_ring"), RING_WAVE = tex("ring_wave"), WAVE = tex("wave"),
            CRACKS = tex("cracks"), LINE = tex("line"), CRESCENT = tex("crescent"), MIST = tex("mist"), BEAM = tex("beam"), FLAME_COL = tex("flame_col"),
            SHIELD = tex("shield"), SHIELD_CRACKS = tex("shield_cracks"), SPEAR = tex("spear"), TRAIL = tex("trail"), CONSTELLATION = tex("constellation"),
            HALO = tex("halo"), STAR = tex("star"), TARGET = tex("target"), DOME = tex("dome"), SHIELD_ARC = tex("shield_arc");

    static final float[] WHITE = {1f, 1f, 1f}, GOLD = {1f, 0.82f, 0.36f}, WARM = {1f, 0.93f, 0.72f}, AMBER = {1f, 0.55f, 0.12f};

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
}
