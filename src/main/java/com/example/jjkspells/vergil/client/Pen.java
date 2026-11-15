package com.example.jjkspells.vergil.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Tiny immediate-mode helper. Every position is given in WORLD space and converted to entity-local space on the fly,
 * so the renderer can describe the effect with real coordinates (bow centre, impact point...).
 */
final class Pen {
    static final float[] WHITE = {1.00f, 1.00f, 1.00f};
    static final float[] CORE = {0.86f, 0.97f, 1.00f};
    static final float[] CYAN = {0.36f, 0.82f, 1.00f};
    static final float[] BLUE = {0.10f, 0.45f, 1.00f};
    static final float[] DEEP = {0.05f, 0.22f, 0.95f};
    static final float[] GOLD = {1.00f, 0.74f, 0.26f};
    static final float[] RED = {0.80f, 0.04f, 0.06f};
    static final float[] CRIM = {0.42f, 0.02f, 0.04f};
    static final float[] ICE = {0.62f, 0.86f, 1.00f};
    static final float[] VIOLET = {0.55f, 0.42f, 1.00f};

    final Matrix4f m;
    final Vec3 org;
    final Vec3 cam;
    final Vector3f left;
    final Vector3f up;
    VertexConsumer vc;

    Pen(Matrix4f m, Vec3 org, Vec3 camWorld, Vector3f left, Vector3f up) {
        this.m = m;
        this.org = org;
        this.cam = camWorld;
        this.left = left;
        this.up = up;
    }

    private void v(Vec3 w, float u, float v, float[] c, float a) {
        vc.vertex(m, (float) (w.x - org.x), (float) (w.y - org.y), (float) (w.z - org.z))
                .color(c[0], c[1], c[2], Math.max(0f, Math.min(1f, a)))
                .uv(u, v).endVertex();
    }

    /** General quad; corners in order (u0,v0) (u1,v0) (u1,v1) (u0,v1). */
    void quad(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float u0, float v0, float u1, float v1, float[] c, float a) {
        v(p0, u0, v0, c, a);
        v(p1, u1, v0, c, a);
        v(p2, u1, v1, c, a);
        v(p3, u0, v1, c, a);
    }

    /** Camera facing sprite. {@code half} is half the edge length. */
    void billboard(Vec3 c, double half, double rotRad, float[] col, float a, float u0, float v0, float u1, float v1) {
        if (half <= 1.0E-4 || a <= 0.002f) {
            return;
        }
        double cs = Math.cos(rotRad);
        double sn = Math.sin(rotRad);
        Vec3 L = new Vec3(left.x(), left.y(), left.z());
        Vec3 U = new Vec3(up.x(), up.y(), up.z());
        Vec3 ax = L.scale(cs * half).add(U.scale(sn * half));
        Vec3 ay = L.scale(-sn * half).add(U.scale(cs * half));
        quad(c.subtract(ax).subtract(ay), c.add(ax).subtract(ay), c.add(ax).add(ay), c.subtract(ax).add(ay), u0, v0, u1, v1, col, a);
    }

    void billboard(Vec3 c, double half, double rotRad, float[] col, float a) {
        billboard(c, half, rotRad, col, a, 0f, 0f, 1f, 1f);
    }

    /** Camera facing ribbon from a to b with a width and alpha per end. u runs along the length, v across it. */
    void ribbon(Vec3 a, Vec3 b, double wa, double wb, float aa, float ab, float uA, float uB, float[] col) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-8 || (aa <= 0.002f && ab <= 0.002f)) {
            return;
        }
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = d.cross(cam.subtract(mid));
        if (side.lengthSqr() < 1.0E-10) {
            side = d.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1.0E-10) {
                side = d.cross(new Vec3(1, 0, 0));
            }
        }
        ribbonSide(a, b, side.normalize(), wa, wb, aa, ab, uA, uB, col);
    }

    /** Ribbon with an explicit unit side vector (used for the vertical light pillar, which only turns around the Y axis). */
    void ribbonSide(Vec3 a, Vec3 b, Vec3 side, double wa, double wb, float aa, float ab, float uA, float uB, float[] col) {
        Vec3 sa = side.scale(wa * 0.5);
        Vec3 sb = side.scale(wb * 0.5);
        v(a.subtract(sa), uA, 0f, col, aa);
        v(a.add(sa), uA, 1f, col, aa);
        v(b.add(sb), uB, 1f, col, ab);
        v(b.subtract(sb), uB, 0f, col, ab);
    }

    void ribbon(Vec3 a, Vec3 b, double w, float alpha, float uA, float uB, float[] col) {
        ribbon(a, b, w, w, alpha, alpha, uA, uB, col);
    }

    /** Square sprite lying in the plane spanned by axes A and B (unit vectors), {@code half} the half edge. */
    void plane(Vec3 c, Vec3 A, Vec3 B, double half, double rotRad, float[] col, float a, float u0, float v0, float u1, float v1) {
        if (half <= 1.0E-4 || a <= 0.002f) {
            return;
        }
        double cs = Math.cos(rotRad);
        double sn = Math.sin(rotRad);
        Vec3 ax = A.scale(cs * half).add(B.scale(sn * half));
        Vec3 ay = A.scale(-sn * half).add(B.scale(cs * half));
        quad(c.subtract(ax).subtract(ay), c.add(ax).subtract(ay), c.add(ax).add(ay), c.subtract(ax).add(ay), u0, v0, u1, v1, col, a);
    }

    void plane(Vec3 c, Vec3 A, Vec3 B, double half, double rotRad, float[] col, float a) {
        plane(c, A, B, half, rotRad, col, a, 0f, 0f, 1f, 1f);
    }
}
