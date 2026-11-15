package com.example.jjkspells.prime.fx.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Immediate-mode quad helper. Every position is WORLD space; it is converted to entity-local space on the fly.
 * Sprites are described by two axes: the image's +x runs along X, the image's up runs along Y.
 */
final class PrimePen {
    static final float[] WHITE = {1f, 1f, 1f};
    final Matrix4f m;
    final Vec3 org;
    final Vec3 cam;
    final Vec3 right;
    final Vec3 up;
    VertexConsumer vc;

    PrimePen(Matrix4f m, Vec3 org, Vec3 cam, Vector3f left, Vector3f upv) {
        this.m = m;
        this.org = org;
        this.cam = cam;
        this.right = new Vec3(-left.x(), -left.y(), -left.z());
        this.up = new Vec3(upv.x(), upv.y(), upv.z());
    }

    private void v(Vec3 w, float u, float v, float[] c, float a) {
        vc.vertex(m, (float) (w.x - org.x), (float) (w.y - org.y), (float) (w.z - org.z))
                .color(c[0], c[1], c[2], Math.max(0f, Math.min(1f, a)))
                .uv(u, v).endVertex();
    }

    /** Unit vector from the camera to a point. */
    Vec3 view(Vec3 at) {
        Vec3 d = at.subtract(cam);
        return d.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : d.normalize();
    }

    /** One quad. (u0,v0) is the top-left of the image. */
    void sprite(Vec3 c, Vec3 X, Vec3 Y, double hw, double hh, float u0, float v0, float u1, float v1, float[] col, float a) {
        if (hw <= 1.0E-4 || hh <= 1.0E-4 || a <= 0.002f) return;
        Vec3 ax = X.scale(hw), ay = Y.scale(hh);
        v(c.subtract(ax).add(ay), u0, v0, col, a);
        v(c.add(ax).add(ay), u1, v0, col, a);
        v(c.add(ax).subtract(ay), u1, v1, col, a);
        v(c.subtract(ax).subtract(ay), u0, v1, col, a);
    }

    /** One frame of an n-frame horizontal strip. */
    void frame(Vec3 c, Vec3 X, Vec3 Y, double hw, double hh, int f, int n, float[] col, float a) {
        float e = 0.0008f;
        f = Math.floorMod(f, n);
        sprite(c, X, Y, hw, hh, (float) f / n + e, 0f + e, (float) (f + 1) / n - e, 1f - e, col, a);
    }

    /** Camera facing sprite, rotated by {@code rot} radians. */
    void bill(Vec3 c, double half, double rot, float[] col, float a) {
        double cs = Math.cos(rot), sn = Math.sin(rot);
        Vec3 X = right.scale(cs).add(up.scale(sn));
        Vec3 Y = up.scale(cs).subtract(right.scale(sn));
        sprite(c, X, Y, half, half, 0f, 0f, 1f, 1f, col, a);
    }

    void billFrame(Vec3 c, double half, double rot, int f, int n, float[] col, float a) {
        double cs = Math.cos(rot), sn = Math.sin(rot);
        Vec3 X = right.scale(cs).add(up.scale(sn));
        Vec3 Y = up.scale(cs).subtract(right.scale(sn));
        frame(c, X, Y, half, half, f, n, col, a);
    }

    /**
     * A strip of frames that runs from a to b with the given width: the image's x runs along a to b, so a beam or an arrow
     * points where it should. {@code segLen} is the length of one repeat (tileable textures are repeated).
     */
    void strip(Vec3 a, Vec3 b, double width, double segLen, int f, int n, float[] col, float alpha, boolean tile) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-4) return;
        Vec3 dir = d.scale(1.0 / len);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = dir.cross(cam.subtract(mid));
        if (side.lengthSqr() < 1.0E-10) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-10) side = dir.cross(new Vec3(1, 0, 0));
        side = side.normalize();
        if (!tile) {
            frame(mid, dir, side, len * 0.5, width * 0.5, f, n, col, alpha);
            return;
        }
        int segs = (int) Math.max(1, Math.min(64, Math.ceil(len / segLen)));
        double each = len / segs;
        for (int i = 0; i < segs; i++) {
            Vec3 c = a.add(dir.scale(each * (i + 0.5)));
            frame(c, dir, side, each * 0.5, width * 0.5, f, n, col, alpha);
        }
    }

    /**
     * A disc-shaped sprite that is a plane perpendicular to {@code axis} when seen along the axis, and fades into a camera
     * facing sprite when seen edge-on, so it never vanishes.
     */
    void disc(Vec3 c, Vec3 axis, double half, double rot, int f, int n, float[] col, float a) {
        double face = Math.abs(view(c).dot(axis));
        float aMain = (float) Math.min(1.0, face * 2.2);
        if (aMain > 0.01f) {
            Vec3 A = axis.cross(new Vec3(0, 1, 0));
            if (A.lengthSqr() < 1.0E-4) A = new Vec3(1, 0, 0);
            A = A.normalize();
            Vec3 B = axis.cross(A).normalize();
            double cs = Math.cos(rot), sn = Math.sin(rot);
            frame(c, A.scale(cs).add(B.scale(sn)), B.scale(cs).subtract(A.scale(sn)), half, half, f, n, col, a * aMain);
        }
        if (aMain < 0.99f) billFrame(c, half, rot, f, n, col, a * (1f - aMain) * 0.7f);
    }

    /** A sprite lying flat on the ground (the XZ plane). */
    void flat(Vec3 c, double half, double rot, int f, int n, float[] col, float a) {
        double cs = Math.cos(rot), sn = Math.sin(rot);
        frame(c, new Vec3(cs, 0, sn), new Vec3(-sn, 0, cs), half, half, f, n, col, a);
    }

    /** A vertical strip that turns around the Y axis to face the camera. */
    void column(Vec3 base, double width, double height, int f, int n, float[] col, float a, double segH) {
        Vec3 h = new Vec3(cam.x - base.x, 0, cam.z - base.z);
        if (h.lengthSqr() < 1.0E-6) h = new Vec3(1, 0, 0);
        Vec3 X = h.normalize().cross(new Vec3(0, 1, 0)).normalize();
        Vec3 Y = new Vec3(0, 1, 0);
        int segs = (int) Math.max(1, Math.min(48, Math.ceil(height / segH)));
        double each = height / segs;
        for (int i = 0; i < segs; i++) {
            Vec3 c = base.add(0, each * (i + 0.5), 0);
            frame(c, X, Y, width * 0.5, each * 0.5, f + i * 3, n, col, a);
        }
    }
}
