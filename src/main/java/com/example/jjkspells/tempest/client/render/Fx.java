package com.example.jjkspells.tempest.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.tempest.client.render.Gfx.*;

/** Composite effects shared by the Tempest renderers. Positions are relative to the entity base (the current pose). */
final class Fx {
    private Fx() {
    }

    /** A camera-facing additive texture. */
    static void glowTex(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, Vec3 p, float size, float rot, float[] c, float alpha) {
        if (alpha <= 0.01f || size <= 0.001f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        billboard(ps);
        ps.mulPose(Axis.ZP.rotationDegrees(rot));
        Draw.xy(add(buf, tex), ps, size, size, c, alpha);
        ps.popPose();
    }

    static void glow(MultiBufferSource buf, PoseStack ps, Vec3 p, float size, float[] c, float alpha) {
        glowTex(buf, ps, GLOW, p, size, 0f, c, alpha);
    }

    static void star(MultiBufferSource buf, PoseStack ps, Vec3 p, float size, float rot, float[] c, float alpha) {
        glowTex(buf, ps, STAR, p, size, rot, c, alpha);
    }

    /** A camera-facing blended texture (dark or slimy shapes that must not glow). */
    static void blob(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, Vec3 p, float size, float rot, float[] c, float alpha) {
        if (alpha <= 0.01f || size <= 0.001f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        billboard(ps);
        ps.mulPose(Axis.ZP.rotationDegrees(rot));
        Draw.xy(trans(buf, tex), ps, size, size, c, alpha);
        ps.popPose();
    }

    /** A flat additive texture lying on the ground (or at any height) around a centre. */
    static void disc(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, Vec3 centre, float radius, float rot, float[] c, float alpha) {
        if (alpha <= 0.01f || radius <= 0.001f) {
            return;
        }
        ps.pushPose();
        ps.translate(centre.x, centre.y, centre.z);
        Draw.disc(add(buf, tex), ps, radius, rot, 0f, c, alpha);
        ps.popPose();
    }

    /** A flat blended texture lying on the ground. */
    static void discT(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, Vec3 centre, float radius, float rot, float[] c, float alpha) {
        if (alpha <= 0.01f || radius <= 0.001f) {
            return;
        }
        ps.pushPose();
        ps.translate(centre.x, centre.y, centre.z);
        Draw.disc(trans(buf, tex), ps, radius, rot, 0f, c, alpha);
        ps.popPose();
    }

    /** A glowing sphere with a swirling surface. */
    static void orb(MultiBufferSource buf, PoseStack ps, Vec3 p, float radius, float scroll, float[] c, float alpha) {
        if (alpha <= 0.01f || radius <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        Draw.sphere(add(buf, SPHERE), ps, radius, 10, 16, scroll, c, alpha);
        ps.popPose();
    }

    /** A sphere seen through a tileable lattice (barriers). */
    static void shell(MultiBufferSource buf, PoseStack ps, Vec3 p, float radius, float scroll, float[] c, float alpha) {
        if (alpha <= 0.01f || radius <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        Draw.sphere(add(buf, BARRIER), ps, radius, 12, 20, scroll, c, alpha);
        ps.popPose();
    }

    /** A camera-facing ribbon from a to b, the texture running along its length (repeated 'repeat' times). */
    static void beam(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, Vec3 a, Vec3 b, Vec3 cam, float w, float repeat, float[] c, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        Draw.ribbon(add(buf, tex), ps, a, b, cam, w, w, 0f, repeat, c, alpha, alpha);
    }

    /** A jagged lightning bolt from a to b made of a few ribbons (seed fixes the shape). */
    static void bolt(MultiBufferSource buf, PoseStack ps, Vec3 a, Vec3 b, Vec3 cam, float w, float[] c, float alpha, long seed) {
        if (alpha <= 0.01f) {
            return;
        }
        RandomSource r = RandomSource.create(seed);
        int seg = Math.max(3, (int) (a.distanceTo(b) / 1.6));
        seg = Math.min(seg, 14);
        Vec3 prev = a;
        double jit = Math.min(1.2, a.distanceTo(b) * 0.06 + 0.15);
        for (int i = 1; i <= seg; i++) {
            Vec3 next = a.lerp(b, i / (double) seg);
            if (i < seg) {
                next = next.add((r.nextDouble() - 0.5) * 2 * jit, (r.nextDouble() - 0.5) * 2 * jit, (r.nextDouble() - 0.5) * 2 * jit);
            }
            Draw.ribbon(add(buf, LIGHTNING), ps, prev, next, cam, w, w, 0f, 1f, c, alpha, alpha);
            Draw.ribbon(add(buf, BEAM), ps, prev, next, cam, w * 0.35f, w * 0.35f, 0f, 1f, WHITE, alpha * 0.8f, alpha * 0.8f);
            prev = next;
        }
    }

    /** A strip from a to b with the texture's long axis (v) running along it, base (v=1) at a: tendrils and drips. */
    static void strip(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, boolean additive, Vec3 a, Vec3 b, Vec3 cam, float w0, float w1, float[] c, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        Vec3 dir = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = dir.cross(cam.subtract(mid));
        if (side.lengthSqr() < 1.0e-8) {
            return;
        }
        side = side.normalize();
        Vec3 a0 = a.add(side.scale(-w0)), a1 = a.add(side.scale(w0)), b1 = b.add(side.scale(w1)), b0 = b.add(side.scale(-w1));
        float[][] p = {{(float) a0.x, (float) a0.y, (float) a0.z}, {(float) a1.x, (float) a1.y, (float) a1.z}, {(float) b1.x, (float) b1.y, (float) b1.z}, {(float) b0.x, (float) b0.y, (float) b0.z}};
        Draw.quad(additive ? add(buf, tex) : trans(buf, tex), ps, p, Draw.UV, c[0], c[1], c[2], alpha, LightTexture.FULL_BRIGHT);
    }

    /** Slime tendrils reaching from a centre to points around it. */
    static void tendrils(MultiBufferSource buf, PoseStack ps, Vec3 centre, float reach, int n, float spin, float width, Vec3 cam, float[] c, float alpha) {
        for (int i = 0; i < n; i++) {
            float ang = (spin + i * 360f / n) * Mth.DEG_TO_RAD;
            float wob = Mth.sin(spin * 0.07f + i * 1.7f) * 0.25f;
            Vec3 tip = centre.add(Mth.cos(ang) * reach, -centre.y + 0.15 + wob, Mth.sin(ang) * reach);
            strip(buf, ps, TENDRIL, false, centre, tip, cam, width, width * 0.4f, c, alpha);
        }
    }

    /** A flat expanding wave at a height. */
    static void wave(MultiBufferSource buf, PoseStack ps, Vec3 centre, float radius, float[] c, float alpha) {
        disc(buf, ps, RING_WAVE, centre, radius, 0f, c, alpha);
    }

    /** A crescent slash standing in the plane across 'yaw' (the direction it travels), rolled by 'roll' degrees. */
    static void slash(MultiBufferSource buf, PoseStack ps, Vec3 p, float yaw, float pitch, float roll, float size, float[] c, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        Draw.xyAt(add(buf, CRESCENT), ps, size, size * 0.5f, 0f, c, alpha);
        Draw.xyAt(add(buf, SLASH), ps, size * 1.15f, size * 0.14f, 0f, WHITE, alpha * 0.9f);
        ps.popPose();
    }

    /** A crack (a flat decal-like glyph) lying on the ground. */
    static void crack(MultiBufferSource buf, PoseStack ps, Vec3 p, float size, float rot, float[] c, float alpha) {
        disc(buf, ps, CRACK, p, size, rot, c, alpha);
    }
}
