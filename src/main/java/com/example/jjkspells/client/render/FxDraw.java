package com.example.jjkspells.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Small geometry toolbox for the Tengai Shinsei visuals: ellipsoids, tubes, tori, flat rings, billboards and
 * vertical strips, all drawn as quads into whatever VertexConsumer is passed in. Every shape is drawn from both
 * sides so it is visible from inside and outside whatever the render type's culling does.
 */
public final class FxDraw {
    public static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    private FxDraw() {
    }

    /** Radius of a tube at height fraction t (0 = bottom, 1 = top). */
    public interface Profile {
        float radius(float t);
    }

    public static int c(float v) {
        return Mth.clamp((int) v, 0, 255);
    }

    public static float ease(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float k = 1f - t;
        return 1f - k * k * k;
    }

    public static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                             int r, int g, int b, int a, int light, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(n, nx, ny, nz)
                .endVertex();
    }

    /** One quad (a, b, c, d in order) drawn twice, once per side. */
    private static void quad2(VertexConsumer vc, Matrix4f m, Matrix3f n, int light,
                              float[] p, float[] nrm, float[] uv, int[] rgba) {
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                int i = pass == 0 ? k : 3 - k;
                vert(vc, m, n, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], uv[i * 2], uv[i * 2 + 1],
                        rgba[i * 4], rgba[i * 4 + 1], rgba[i * 4 + 2], rgba[i * 4 + 3], light,
                        pass == 0 ? nrm[i * 3] : -nrm[i * 3], pass == 0 ? nrm[i * 3 + 1] : -nrm[i * 3 + 1], pass == 0 ? nrm[i * 3 + 2] : -nrm[i * 3 + 2]);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    //  Ellipsoid
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Ellipsoid centred on the pose origin. u wraps around the equator (uRep times), v runs pole to pole (vRep times).
     * All vertices share one colour and alpha.
     */
    public static void ellipsoid(PoseStack.Pose pose, VertexConsumer vc, float rx, float ry, float rz, int lat, int lon,
                                 float uRep, float vRep, float uOff, float vOff,
                                 int r, int g, int b, int a, int light) {
        if (a <= 0 || rx <= 0.01f || ry <= 0.01f || rz <= 0.01f) {
            return;
        }
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float[] p = new float[12];
        float[] nr = new float[12];
        float[] uv = new float[8];
        int[] col = new int[16];
        for (int k = 0; k < 4; k++) {
            col[k * 4] = r;
            col[k * 4 + 1] = g;
            col[k * 4 + 2] = b;
            col[k * 4 + 3] = a;
        }
        for (int i = 0; i < lat; i++) {
            float t0 = (float) Math.PI * i / lat;
            float t1 = (float) Math.PI * (i + 1) / lat;
            float y0 = Mth.cos(t0), y1 = Mth.cos(t1);
            float s0 = Mth.sin(t0), s1 = Mth.sin(t1);
            float v0 = vOff + vRep * i / lat, v1 = vOff + vRep * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p0 = (float) (Math.PI * 2) * j / lon;
                float p1 = (float) (Math.PI * 2) * (j + 1) / lon;
                float c0 = Mth.cos(p0), sn0 = Mth.sin(p0), c1 = Mth.cos(p1), sn1 = Mth.sin(p1);
                float u0 = uOff + uRep * j / lon, u1 = uOff + uRep * (j + 1) / lon;

                // a (t0,p0)  b (t0,p1)  c (t1,p1)  d (t1,p0)
                set(p, nr, uv, 0, s0 * c0, y0, s0 * sn0, rx, ry, rz, u0, v0);
                set(p, nr, uv, 1, s0 * c1, y0, s0 * sn1, rx, ry, rz, u1, v0);
                set(p, nr, uv, 2, s1 * c1, y1, s1 * sn1, rx, ry, rz, u1, v1);
                set(p, nr, uv, 3, s1 * c0, y1, s1 * sn0, rx, ry, rz, u0, v1);
                quad2(vc, m, n, light, p, nr, uv, col);
            }
        }
    }

    private static void set(float[] p, float[] nr, float[] uv, int i, float dx, float dy, float dz,
                            float rx, float ry, float rz, float u, float v) {
        p[i * 3] = dx * rx;
        p[i * 3 + 1] = dy * ry;
        p[i * 3 + 2] = dz * rz;
        nr[i * 3] = dx;
        nr[i * 3 + 1] = dy;
        nr[i * 3 + 2] = dz;
        uv[i * 2] = u;
        uv[i * 2 + 1] = v;
    }

    // ------------------------------------------------------------------------------------------------------------
    //  Tube (open cylinder / column with a radius profile)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Open tube from y0 to y1 around the Y axis. Alpha runs from aBottom to aTop. v runs along the height (vRep times).
     */
    public static void tube(PoseStack.Pose pose, VertexConsumer vc, float y0, float y1, Profile prof, int segs, int rings,
                            float uRep, float vRep, float uOff, float vOff,
                            int r, int g, int b, float aBottom, float aTop, int light) {
        if (Math.max(aBottom, aTop) <= 0.5f || y1 <= y0) {
            return;
        }
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float[] p = new float[12];
        float[] nr = new float[12];
        float[] uv = new float[8];
        int[] col = new int[16];
        for (int i = 0; i < rings; i++) {
            float t0 = (float) i / rings, t1 = (float) (i + 1) / rings;
            float ya = lerp(t0, y0, y1), yb = lerp(t1, y0, y1);
            float ra = prof.radius(t0), rb = prof.radius(t1);
            int a0 = c(lerp(t0, aBottom, aTop)), a1 = c(lerp(t1, aBottom, aTop));
            float v0 = vOff + vRep * t0, v1 = vOff + vRep * t1;
            for (int j = 0; j < segs; j++) {
                float p0 = (float) (Math.PI * 2) * j / segs;
                float p1 = (float) (Math.PI * 2) * (j + 1) / segs;
                float cs0 = Mth.cos(p0), sn0 = Mth.sin(p0), cs1 = Mth.cos(p1), sn1 = Mth.sin(p1);
                float u0 = uOff + uRep * j / segs, u1 = uOff + uRep * (j + 1) / segs;
                put(p, nr, uv, col, 0, cs0 * ra, ya, sn0 * ra, cs0, 0, sn0, u0, v0, r, g, b, a0);
                put(p, nr, uv, col, 1, cs1 * ra, ya, sn1 * ra, cs1, 0, sn1, u1, v0, r, g, b, a0);
                put(p, nr, uv, col, 2, cs1 * rb, yb, sn1 * rb, cs1, 0, sn1, u1, v1, r, g, b, a1);
                put(p, nr, uv, col, 3, cs0 * rb, yb, sn0 * rb, cs0, 0, sn0, u0, v1, r, g, b, a1);
                quad2(vc, m, n, light, p, nr, uv, col);
            }
        }
    }

    private static void put(float[] p, float[] nr, float[] uv, int[] col, int i, float x, float y, float z,
                            float nx, float ny, float nz, float u, float v, int r, int g, int b, int a) {
        p[i * 3] = x;
        p[i * 3 + 1] = y;
        p[i * 3 + 2] = z;
        nr[i * 3] = nx;
        nr[i * 3 + 1] = ny;
        nr[i * 3 + 2] = nz;
        uv[i * 2] = u;
        uv[i * 2 + 1] = v;
        col[i * 4] = r;
        col[i * 4 + 1] = g;
        col[i * 4 + 2] = b;
        col[i * 4 + 3] = a;
    }

    // ------------------------------------------------------------------------------------------------------------
    //  Torus (the rolling rim of the mushroom head)
    // ------------------------------------------------------------------------------------------------------------

    /** Torus around the Y axis at the pose origin. yScale squashes it vertically. */
    public static void torus(PoseStack.Pose pose, VertexConsumer vc, float major, float minor, float yScale, int segs, int sides,
                             float uRep, float vRep, float uOff, float vOff, int r, int g, int b, int a, int light) {
        if (a <= 0 || minor <= 0.01f) {
            return;
        }
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float[] p = new float[12];
        float[] nr = new float[12];
        float[] uv = new float[8];
        int[] col = new int[16];
        for (int i = 0; i < segs; i++) {
            float th0 = (float) (Math.PI * 2) * i / segs, th1 = (float) (Math.PI * 2) * (i + 1) / segs;
            float ct0 = Mth.cos(th0), st0 = Mth.sin(th0), ct1 = Mth.cos(th1), st1 = Mth.sin(th1);
            float u0 = uOff + uRep * i / segs, u1 = uOff + uRep * (i + 1) / segs;
            for (int j = 0; j < sides; j++) {
                float ph0 = (float) (Math.PI * 2) * j / sides, ph1 = (float) (Math.PI * 2) * (j + 1) / sides;
                float cp0 = Mth.cos(ph0), sp0 = Mth.sin(ph0), cp1 = Mth.cos(ph1), sp1 = Mth.sin(ph1);
                float v0 = vOff + vRep * j / sides, v1 = vOff + vRep * (j + 1) / sides;
                float r00 = major + minor * cp0, r01 = major + minor * cp1;
                put(p, nr, uv, col, 0, r00 * ct0, minor * yScale * sp0, r00 * st0, cp0 * ct0, sp0, cp0 * st0, u0, v0, r, g, b, a);
                put(p, nr, uv, col, 1, r00 * ct1, minor * yScale * sp0, r00 * st1, cp0 * ct1, sp0, cp0 * st1, u1, v0, r, g, b, a);
                put(p, nr, uv, col, 2, r01 * ct1, minor * yScale * sp1, r01 * st1, cp1 * ct1, sp1, cp1 * st1, u1, v1, r, g, b, a);
                put(p, nr, uv, col, 3, r01 * ct0, minor * yScale * sp1, r01 * st0, cp1 * ct0, sp1, cp1 * st0, u0, v1, r, g, b, a);
                quad2(vc, m, n, light, p, nr, uv, col);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    //  Flat ring (annulus) lying on a horizontal plane
    // ------------------------------------------------------------------------------------------------------------

    /** v runs from the inner edge (0) to the outer edge (1), so a "soft band" texture gives a soft ring. */
    public static void annulus(PoseStack.Pose pose, VertexConsumer vc, float rIn, float rOut, float y, int segs,
                               float uRep, float uOff, int r, int g, int b, int a, int light) {
        if (a <= 0 || rOut <= rIn) {
            return;
        }
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float[] p = new float[12];
        float[] nr = new float[12];
        float[] uv = new float[8];
        int[] col = new int[16];
        for (int j = 0; j < segs; j++) {
            float p0 = (float) (Math.PI * 2) * j / segs, p1 = (float) (Math.PI * 2) * (j + 1) / segs;
            float cs0 = Mth.cos(p0), sn0 = Mth.sin(p0), cs1 = Mth.cos(p1), sn1 = Mth.sin(p1);
            float u0 = uOff + uRep * j / segs, u1 = uOff + uRep * (j + 1) / segs;
            put(p, nr, uv, col, 0, cs0 * rIn, y, sn0 * rIn, 0, 1, 0, u0, 0f, r, g, b, a);
            put(p, nr, uv, col, 1, cs1 * rIn, y, sn1 * rIn, 0, 1, 0, u1, 0f, r, g, b, a);
            put(p, nr, uv, col, 2, cs1 * rOut, y, sn1 * rOut, 0, 1, 0, u1, 1f, r, g, b, a);
            put(p, nr, uv, col, 3, cs0 * rOut, y, sn0 * rOut, 0, 1, 0, u0, 1f, r, g, b, a);
            quad2(vc, m, n, light, p, nr, uv, col);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    //  Billboard (always faces the camera)
    // ------------------------------------------------------------------------------------------------------------

    public static void billboard(PoseStack ps, VertexConsumer vc, Quaternionf cameraOrientation, float half, float spinDeg,
                                 int r, int g, int b, int a, int light) {
        if (a <= 0 || half <= 0.001f) {
            return;
        }
        ps.pushPose();
        ps.mulPose(cameraOrientation);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        ps.mulPose(Axis.ZP.rotationDegrees(spinDeg));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        vert(vc, m, n, -half, -half, 0f, 0f, 1f, r, g, b, a, light, 0f, 1f, 0f);
        vert(vc, m, n, half, -half, 0f, 1f, 1f, r, g, b, a, light, 0f, 1f, 0f);
        vert(vc, m, n, half, half, 0f, 1f, 0f, r, g, b, a, light, 0f, 1f, 0f);
        vert(vc, m, n, -half, half, 0f, 0f, 0f, r, g, b, a, light, 0f, 1f, 0f);
        ps.popPose();
    }

    // ------------------------------------------------------------------------------------------------------------
    //  Vertical strip (a flat ribbon standing on its edge)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * A ribbon from y0 to y1, standing in the vertical plane that contains the direction (dx, dz) (a unit horizontal
     * vector). u runs across the ribbon, v from v0 (at y0) to v1 (at y1). Alpha runs from aBottom to aTop.
     */
    public static void strip(PoseStack.Pose pose, VertexConsumer vc, float dx, float dz, float halfBottom, float halfTop,
                             float y0, float y1, float v0, float v1, int r, int g, int b, int aBottom, int aTop, int light) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float[] p = new float[12];
        float[] nr = new float[12];
        float[] uv = new float[8];
        int[] col = new int[16];
        float nx = -dz, nz = dx;
        put(p, nr, uv, col, 0, -dx * halfBottom, y0, -dz * halfBottom, nx, 0, nz, 0f, v0, r, g, b, aBottom);
        put(p, nr, uv, col, 1, dx * halfBottom, y0, dz * halfBottom, nx, 0, nz, 1f, v0, r, g, b, aBottom);
        put(p, nr, uv, col, 2, dx * halfTop, y1, dz * halfTop, nx, 0, nz, 1f, v1, r, g, b, aTop);
        put(p, nr, uv, col, 3, -dx * halfTop, y1, -dz * halfTop, nx, 0, nz, 0f, v1, r, g, b, aTop);
        quad2(vc, m, n, light, p, nr, uv, col);
    }
}
