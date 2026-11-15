package com.example.jjkspells.garou.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Immediate-mode drawing helper (Garou's own copy). Works in the renderer's local space (world position minus the
 * entity's interpolated position). {@code cam} is the camera in that same space, used for camera-facing shapes.
 * Vertex format: POSITION_COLOR_TEX. Every shape takes colour + per-end / per-corner alpha.
 */
public final class Pen {
    private final VertexConsumer vc;
    private final Matrix4f m;
    public final Vec3 cam;

    public Pen(VertexConsumer vc, PoseStack ps, Vec3 cam) {
        this.vc = vc;
        this.m = ps.last().pose();
        this.cam = cam;
    }

    private void v(Vec3 p, float u, float vv, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).uv(u, vv).endVertex();
    }

    /** Generic quad: p0(u0,v0) p1(u1,v0) p2(u1,v1) p3(u0,v1) with an alpha per vertex. */
    public void quad(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float u0, float v0, float u1, float v1,
                     float r, float g, float b, float a0, float a1, float a2, float a3) {
        v(p0, u0, v0, r, g, b, a0);
        v(p1, u1, v0, r, g, b, a1);
        v(p2, u1, v1, r, g, b, a2);
        v(p3, u0, v1, r, g, b, a3);
    }

    /** Plane in any orientation: centre +/- axisU +/- axisV (axes are half extents). */
    public void plane(Vec3 c, Vec3 axisU, Vec3 axisV, float u0, float v0, float u1, float v1,
                      float r, float g, float b, float a) {
        quad(c.subtract(axisU).subtract(axisV), c.add(axisU).subtract(axisV), c.add(axisU).add(axisV), c.subtract(axisU).add(axisV),
                u0, v0, u1, v1, r, g, b, a, a, a, a);
    }

    public void plane(Vec3 c, Vec3 axisU, Vec3 axisV, float r, float g, float b, float a) {
        plane(c, axisU, axisV, 0, 0, 1, 1, r, g, b, a);
    }

    /** Camera-facing billboard with roll (radians). */
    public void billboard(Vec3 c, float half, float roll, float u0, float v0, float u1, float v1,
                          float r, float g, float b, float a) {
        Vec3 f = c.subtract(cam);
        if (f.lengthSqr() < 1e-8) return;
        f = f.normalize();
        Vec3 right = f.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(f).normalize();
        double cs = Math.cos(roll), sn = Math.sin(roll);
        Vec3 ru = right.scale(cs).add(up.scale(sn)).scale(half);
        Vec3 uu = right.scale(-sn).add(up.scale(cs)).scale(half);
        plane(c, ru, uu, u0, v0, u1, v1, r, g, b, a);
    }

    public void billboard(Vec3 c, float half, float roll, float r, float g, float b, float a) {
        billboard(c, half, roll, 0, 0, 1, 1, r, g, b, a);
    }

    /** Ribbon between a and b that always turns its face to the camera. u spans the width, v runs along the length. */
    public void ribbonFacing(Vec3 a, Vec3 b, float wa, float wb, float va, float vb,
                             float r, float g, float bl, float alphaA, float alphaB) {
        Vec3 d = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = d.cross(cam.subtract(mid));
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize();
        quad(a.subtract(side.scale(wa)), a.add(side.scale(wa)), b.add(side.scale(wb)), b.subtract(side.scale(wb)),
                0, va, 1, vb, r, g, bl, alphaA, alphaA, alphaB, alphaB);
    }

    /** Ribbon with a fixed side direction (does not turn with the camera). */
    public void ribbonFixed(Vec3 a, Vec3 b, Vec3 side, float wa, float wb, float va, float vb,
                            float r, float g, float bl, float alphaA, float alphaB) {
        Vec3 s = side.normalize();
        quad(a.subtract(s.scale(wa)), a.add(s.scale(wa)), b.add(s.scale(wb)), b.subtract(s.scale(wb)),
                0, va, 1, vb, r, g, bl, alphaA, alphaA, alphaB, alphaB);
    }
}
