package com.example.jjkspells.diablo.client.render;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Textures, colours and buffer helpers shared by the Diablo renderers. */
final class Gfx {
    private Gfx() {
    }

    static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/environment/dia_" + n + ".png");
    }

    // additive light textures
    static final ResourceLocation GLOW = tex("glow"), GLOW_BLUE = tex("glow_blue"), GLOW_VIOLET = tex("glow_violet"), CIRCLE = tex("circle"), RUNE = tex("rune_ring"),
            RING_WAVE = tex("ring_wave"), WAVE = tex("wave"), TARGET = tex("target"), CRACK = tex("crack"), LENS = tex("lens"), VORTEX = tex("vortex"), CORONA = tex("corona"),
            RINGS = tex("rings"), LINE = tex("line"), STAR = tex("star"), CRESCENT = tex("crescent"), SLASH = tex("slash"), LIGHTNING = tex("lightning"), BEAM = tex("beam"),
            BARRIER = tex("barrier"), SPHERE = tex("sphere"), DROPLETS = tex("droplets"), SOULWISP = tex("soulwisp"),
            CLOCK = tex("clock"), CHAIN = tex("chain"), SPEAR = tex("spear"), RIFT = tex("rift"), EYE = tex("eye");
    // blended textures
    static final ResourceLocation MIST = tex("mist"), SLIME_WAVE = tex("slime_wave"), TENDRIL = tex("tendril"), BLACK = tex("black"), FRAGMENT = tex("fragment"), BLOB = tex("blob"),
            DEMON = tex("demon"), WING = tex("wing"), WING_L = tex("wing_l");

    static final float[] WHITE = {1f, 1f, 1f}, PALE = {1f, 0.85f, 0.8f}, GOLD = {0.95f, 0.7f, 0.25f}, CRIM = {0.85f, 0.05f, 0.12f}, SCAR = {1f, 0.22f, 0.12f},
            BLOODL = {1f, 0.1f, 0.18f}, DVIO = {0.4f, 0.1f, 0.55f}, DEEP = {0.35f, 0.02f, 0.06f}, NIGHT = {0.05f, 0.0f, 0.08f}, ELECTRIC = {1f, 0.15f, 0.2f};

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
}
