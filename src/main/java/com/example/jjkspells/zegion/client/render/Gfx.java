package com.example.jjkspells.zegion.client.render;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Textures, colours and shared figures (phantoms, wings, mirrors, rifts) for the Zegion renderers. */
final class Gfx {
    private Gfx() {
    }

    static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/environment/zeg_" + n + ".png");
    }

    // additive light textures
    static final ResourceLocation GLOW = tex("glow"), CIRCLE = tex("circle"), RUNE = tex("rune_ring"), RING_WAVE = tex("ring_wave"), WAVE = tex("wave"),
            CRACKS = tex("cracks"), TARGET = tex("target"), LINE = tex("line"), TRAIL = tex("trail"), SPEAR = tex("spear"), ARC = tex("arc"), CRESCENT = tex("crescent"),
            BURST = tex("burst"), CHAIN = tex("chain"), HEX = tex("hex"), RIFT_EDGE = tex("rift_edge"), RIFT_BOLT = tex("rift_bolt"), MIST_GLOW = tex("mist_glow"),
            MIRROR_FRAME = tex("mirror_frame"), LATTICE = tex("lattice"), SPHERE_GLOW = tex("sphere_glow"), ACCRETION = tex("accretion"), WING = tex("wing"),
            PHANTOM_GLOW = tex("phantom_glow");
    // blended textures
    static final ResourceLocation MIST = tex("mist"), MIST_WAVE = tex("mist_wave"), MIRROR = tex("mirror"), WALL = tex("wall"), SPHERE = tex("sphere"), SKY = tex("sky"),
            SINGULARITY = tex("singularity"), RIFT_VOID = tex("rift_void"), PLATE = tex("plate"), SHARD = tex("shard"), PHANTOM = tex("phantom");

    static final float[] WHITE = {1f, 1f, 1f}, VIOLET = {0.8f, 0.55f, 1f}, DEEP = {0.55f, 0.3f, 0.95f}, PALE = {0.92f, 0.85f, 1f}, CYAN = {0.55f, 0.88f, 1f},
            DARK = {0.5f, 0.42f, 0.7f}, BLACK = {0.12f, 0.08f, 0.2f};

    static float c01(float v) {
        return Mth.clamp(v, 0f, 1f);
    }

    static float smooth(float v) {
        v = c01(v);
        return v * v * (3f - 2f * v);
    }

    /** rise 0..1 over 'up' ticks after t0, then hold, then fade over 'down'. */
    static float life(float t, float t0, float up, float hold, float down) {
        float a = smooth((t - t0) / Math.max(0.01f, up));
        float b = 1f - smooth((t - t0 - up - hold) / Math.max(0.01f, down));
        return Math.min(a, b);
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

    static Vec3 base(Entity e, float pt) {
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    /** Turns the pose around Y to face the camera from the current origin (which sits at local x, y, z of the entity). */
    static void faceCamera(PoseStack ps, Vec3 base, double lx, double ly, double lz) {
        Vec3 c = cam(base);
        ps.mulPose(Axis.YP.rotation((float) Mth.atan2(c.x - lx, c.z - lz)));
    }

    static void billboard(PoseStack ps) {
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
    }

    /**
     * A spectral Zegion standing at local (x, y, z), 'h' blocks tall, turned to face the camera.
     * body: opacity of the dark armor, glow: strength of the violet rim light and eyes.
     */
    static void phantom(MultiBufferSource buf, PoseStack ps, Vec3 base, float x, float y, float z, float h, float body, float glow, float[] tint) {
        if (body <= 0.01f && glow <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(x, y, z);
        faceCamera(ps, base, x, y, z);
        float hw = h * 0.25f, hh = h * 0.5f;
        Draw.xyAt(trans(buf, PHANTOM), ps, hw, hh, hh, WHITE, body);
        ps.translate(0, 0, 0.03f);
        Draw.xyAt(add(buf, PHANTOM_GLOW), ps, hw, hh, hh, tint, glow);
        ps.popPose();
    }

    /** One energy wing; the root sits at the current origin. side = +1 right / -1 left. len is the size of the texture square. */
    static void wing(MultiBufferSource buf, PoseStack ps, float len, float side, float alpha, float[] tint) {
        if (alpha <= 0.01f || len <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.scale(side, 1f, 1f);
        float bx = 0.06f * len, by = 0.092f * len;
        float[][] p = {{-bx, -by, 0}, {len - bx, -by, 0}, {len - bx, len - by, 0}, {-bx, len - by, 0}};
        Draw.quad(add(buf, WING), ps, p, Draw.UV, tint[0], tint[1], tint[2], alpha, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    /** A free-standing mirror panel in the local XY plane (centred at the origin): dark glass plus a glowing frame. */
    static void mirror(MultiBufferSource buf, PoseStack ps, float w, float h, float alpha, float glow) {
        if (alpha > 0.01f) {
            Draw.xy(trans(buf, MIRROR), ps, w * 0.5f, h * 0.5f, WHITE, alpha);
        }
        if (glow > 0.01f) {
            ps.pushPose();
            ps.translate(0, 0, 0.03f);
            Draw.xy(add(buf, MIRROR_FRAME), ps, w * 0.5f, h * 0.5f, VIOLET, glow);
            ps.popPose();
        }
    }

    /** A wound in space in the local XY plane: black void inside a jagged glowing rim with lightning. */
    static void rift(MultiBufferSource buf, PoseStack ps, float halfLen, float maxH, float open, float t, float alpha) {
        if (open <= 0.01f || alpha <= 0.01f) {
            return;
        }
        Draw.lens(trans(buf, RIFT_VOID), ps, halfLen, maxH, open, 20, Math.max(1f, halfLen / 4f), 0f, WHITE, alpha);
        ps.pushPose();
        ps.translate(0, 0, 0.02f);
        Draw.lens(add(buf, RIFT_BOLT), ps, halfLen, maxH, open, 20, Math.max(1f, halfLen / 6f), t * 0.03f, VIOLET, alpha * 0.55f);
        Draw.lensEdge(add(buf, RIFT_EDGE), ps, halfLen, maxH, open, 28, 0.35f + maxH * 0.05f, Math.max(1f, halfLen / 3f), t * 0.02f, PALE, alpha);
        ps.popPose();
    }

    /** A crystalline plate (obsidian armor) in the local XY plane. */
    static void plate(MultiBufferSource buf, PoseStack ps, float w, float h, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        Draw.xyAt(trans(buf, PLATE), ps, w * 0.5f, h * 0.5f, h * 0.5f, WHITE, alpha);
    }

    /** A pointed crystal shard standing up from the origin. */
    static void shard(MultiBufferSource buf, PoseStack ps, float w, float h, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        Draw.xyAt(trans(buf, SHARD), ps, w * 0.5f, h * 0.5f, h * 0.5f, WHITE, alpha);
    }

    /** Pseudo random number from a seed, 0..1. */
    static float rnd(int seed, int k) {
        long h = seed * 73856093L ^ k * 19349663L ^ 0x9E3779B9L;
        h ^= (h >>> 13);
        h *= 0x5bd1e995L;
        h ^= (h >>> 15);
        return ((h & 0xFFFFFFL) / (float) 0x1000000L);
    }
}
