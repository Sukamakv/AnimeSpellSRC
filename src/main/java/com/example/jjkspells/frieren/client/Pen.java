package com.example.jjkspells.frieren.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Immediate-mode drawing helper (Frieren's own copy). Works in the entity's local space = world coordinates minus the
 * entity position; the camera position and axes are supplied in that same space.
 */
public final class Pen {
    public static final float[] FULL = {0f, 0f, 1f, 1f};

    private final Matrix4f m;
    public final Vec3 camPos, camRight, camUp;
    private VertexConsumer vc;

    public Pen(Matrix4f m, Vec3 camPos, Vec3 camRight, Vec3 camUp) {
        this.m = m;
        this.camPos = camPos;
        this.camRight = camRight;
        this.camUp = camUp;
    }

    public Pen use(VertexConsumer vc) {
        this.vc = vc;
        return this;
    }

    /** UV rectangle {u0,v0,u1,v1} of frame f in a 4x4 flip-book. */
    public static float[] frame4(int f) {
        float u = (f & 3) / 4f, v = (f >> 2) / 4f;
        return new float[]{u, v, u + 0.25f, v + 0.25f};
    }

    private void vert(Vec3 p, float u, float v, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).uv(u, v).color(r, g, b, a).endVertex();
    }

    private void vertC(Vec3 p, float u, float v, float[] c) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).uv(u, v).color(c[0], c[1], c[2], c[3]).endVertex();
    }

    /** A quad with its own colour (r,g,b,a) at every corner - used for per-vertex alpha shading. */
    public void quadC(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float[] uv, float[] c0, float[] c1, float[] c2, float[] c3) {
        vertC(p0, uv[0], uv[3], c0);
        vertC(p1, uv[2], uv[3], c1);
        vertC(p2, uv[2], uv[1], c2);
        vertC(p3, uv[0], uv[1], c3);
    }

    /** A quad with its own UV pair (8 floats) and colour (16 floats r,g,b,a) at every corner - for terrain meshes. */
    public void quadG(Vec3[] p, float[] uv, float[] c) {
        for (int i = 0; i < 4; i++) {
            vc.vertex(m, (float) p[i].x, (float) p[i].y, (float) p[i].z).uv(uv[i * 2], uv[i * 2 + 1]).color(c[i * 4], c[i * 4 + 1], c[i * 4 + 2], c[i * 4 + 3]).endVertex();
        }
    }

    /** Four corners: p0 bottom-left, p1 bottom-right, p2 top-right, p3 top-left. */
    public void quad(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float[] uv, float r, float g, float b, float a) {
        vert(p0, uv[0], uv[3], r, g, b, a);
        vert(p1, uv[2], uv[3], r, g, b, a);
        vert(p2, uv[2], uv[1], r, g, b, a);
        vert(p3, uv[0], uv[1], r, g, b, a);
    }

    /** A square in the plane spanned by the unit axes ax, ay, centred on c, half-size h, spun by rot radians. */
    public void plane(Vec3 c, Vec3 ax, Vec3 ay, double h, double rot, float[] uv, float r, float g, float b, float a) {
        double cs = Math.cos(rot) * h, sn = Math.sin(rot) * h;
        Vec3 x = ax.scale(cs).add(ay.scale(sn));
        Vec3 y = ax.scale(-sn).add(ay.scale(cs));
        quad(c.subtract(x).subtract(y), c.add(x).subtract(y), c.add(x).add(y), c.subtract(x).add(y), uv, r, g, b, a);
    }

    /** A camera-facing square. */
    public void billboard(Vec3 c, double h, double rot, float[] uv, float r, float g, float b, float a) {
        plane(c, camRight, camUp, h, rot, uv, r, g, b, a);
    }

    /**
     * A ribbon from a to b that always turns its face to the camera (U across the width, V along the length), with its own
     * width and alpha at each end.
     */
    public void ribbon(Vec3 a, Vec3 b, double wa, double wb, float v0, float v1, float alphaA, float alphaB, float r, float g, float bl) {
        Vec3 axis = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = axis.cross(camPos.subtract(mid));
        side = side.lengthSqr() < 1.0E-8 ? camUp : side.normalize();
        Vec3 sa = side.scale(wa * 0.5), sb = side.scale(wb * 0.5);
        vert(a.subtract(sa), 0f, v0, r, g, bl, alphaA);
        vert(a.add(sa), 1f, v0, r, g, bl, alphaA);
        vert(b.add(sb), 1f, v1, r, g, bl, alphaB);
        vert(b.subtract(sb), 0f, v1, r, g, bl, alphaB);
    }
}
