package com.example.jjkspells.acheron.client.render;

import com.example.jjkspells.vfx.Mesh;
import com.example.jjkspells.vfx.MeshRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.acheron.client.render.Gfx.*;

/** Small drawing helpers; positions are relative to the entity base (the current pose). */
final class Fx {
    private Fx() {
    }

    /** A camera-facing additive texture. */
    static void glow(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, Vec3 p, float size, float rot, float[] c, float alpha) {
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

    /** A camera-facing blended texture (smoke, mist, dark shapes). */
    static void soft(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, Vec3 p, float size, float rot, float[] c, float alpha) {
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

    /** A flat texture lying at a point (additive or blended). */
    static void ground(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, boolean additive, Vec3 centre, float radius, float rot, float[] c, float alpha) {
        if (alpha <= 0.01f || radius <= 0.001f) {
            return;
        }
        ps.pushPose();
        ps.translate(centre.x, centre.y, centre.z);
        Draw.disc(additive ? add(buf, tex) : trans(buf, tex), ps, radius, rot, 0f, c, alpha);
        ps.popPose();
    }

    /** A camera-facing ribbon from a to b; the texture runs across it (u along the length). */
    static void ribbon(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, boolean additive, Vec3 a, Vec3 b, Vec3 cam, float w, float[] c, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        Draw.ribbon(additive ? add(buf, tex) : trans(buf, tex), ps, a, b, cam, w, w, 0f, 1f, c, alpha, alpha);
    }

    /** A flat ring on the ground (u around, v across). */
    static void ring(MultiBufferSource buf, PoseStack ps, ResourceLocation tex, Vec3 centre, float rIn, float rOut, int seg, float repeat, float scroll, float[] c, float alpha) {
        if (alpha <= 0.01f || rOut <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(centre.x, centre.y, centre.z);
        Draw.ring(add(buf, tex), ps, rIn, rOut, 0f, seg, repeat, scroll, c, alpha);
        ps.popPose();
    }

    /** A mesh at a point, turned by yaw (degrees, like an entity: 0 faces +Z) and scaled. */
    static void mesh(MultiBufferSource buf, PoseStack ps, Mesh m, String tex, MeshRender.Mode mode, float[] c, float alpha, float glow, Vec3 at, float yaw, float sx, float sy, float sz, float uOff, float vOff) {
        if (alpha <= 0.01f) {
            return;
        }
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.scale(sx, sy, sz);
        MeshRender.draw(buf, ps, m, tex, mode, c, alpha, glow, uOff, vOff);
        ps.popPose();
    }

    static void mesh(MultiBufferSource buf, PoseStack ps, Mesh m, String tex, MeshRender.Mode mode, float[] c, float alpha, float glow, Vec3 at, float yaw, float s) {
        mesh(buf, ps, m, tex, mode, c, alpha, glow, at, yaw, s, s, s, 0f, 0f);
    }
}
