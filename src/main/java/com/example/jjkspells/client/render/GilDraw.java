package com.example.jjkspells.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** One free oriented quad, drawn two-sided. Used by the Gilgamesh renderers. */
public final class GilDraw {
    private GilDraw() {}

    /**
     * A quad centred on {@code o}. {@code u} and {@code v} are the HALF vectors of its two axes. Texture u runs from -u to +u,
     * texture v from +v (top, 0) to -v (bottom, 1).
     */
    public static void quad(PoseStack ps, VertexConsumer vc, Vec3 o, Vec3 u, Vec3 v, int r, int g, int b, int a, int light) {
        if (a <= 0) return;
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        Vec3 nn = u.cross(v);
        nn = nn.lengthSqr() < 1e-9 ? new Vec3(0, 1, 0) : nn.normalize();
        float[][] c = {
                {(float) (o.x - u.x - v.x), (float) (o.y - u.y - v.y), (float) (o.z - u.z - v.z), 0f, 1f},
                {(float) (o.x + u.x - v.x), (float) (o.y + u.y - v.y), (float) (o.z + u.z - v.z), 1f, 1f},
                {(float) (o.x + u.x + v.x), (float) (o.y + u.y + v.y), (float) (o.z + u.z + v.z), 1f, 0f},
                {(float) (o.x - u.x + v.x), (float) (o.y - u.y + v.y), (float) (o.z - u.z + v.z), 0f, 0f}};
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? 1f : -1f;
            for (int k = 0; k < 4; k++) {
                float[] p = c[side == 0 ? k : 3 - k];
                vc.vertex(m, p[0], p[1], p[2]).color(r, g, b, a).uv(p[3], p[4])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                        .normal(n, (float) nn.x * s, (float) nn.y * s, (float) nn.z * s).endVertex();
            }
        }
    }

    /** A unit vector perpendicular to {@code d}. */
    public static Vec3 perp(Vec3 d) {
        Vec3 ref = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return d.cross(ref).normalize();
    }

    /** Rotates {@code v} about the unit axis {@code k} by {@code deg}. */
    public static Vec3 rot(Vec3 v, Vec3 k, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return v.scale(c).add(k.cross(v).scale(s)).add(k.scale(k.dot(v) * (1 - c)));
    }
}
