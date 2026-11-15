package com.example.jjkspells.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Shapes specific to Laevatain's effects: jagged rocks, the greatsword, the crescent slash, ground quads. */
final class LaevatainDraw {
    private LaevatainDraw() {
    }

    // ------------------------------------------------------------------------------------------------
    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                             int r, int g, int b, int a, int light, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    /** A quad (p0..p3, 3 floats each; uv 2 each; rgba 4 each) drawn from both sides. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, int light, float[] p, float[] uv, int[] c, float nx, float ny, float nz) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int k = 0; k < 4; k++) {
            vert(vc, m, n, p[k * 3], p[k * 3 + 1], p[k * 3 + 2], uv[k * 2], uv[k * 2 + 1], c[k * 4], c[k * 4 + 1], c[k * 4 + 2], c[k * 4 + 3], light, nx, ny, nz);
        }
        for (int k = 3; k >= 0; k--) {
            vert(vc, m, n, p[k * 3], p[k * 3 + 1], p[k * 3 + 2], uv[k * 2], uv[k * 2 + 1], c[k * 4], c[k * 4 + 1], c[k * 4 + 2], c[k * 4 + 3], light, -nx, -ny, -nz);
        }
    }

    private static void quad1(VertexConsumer vc, PoseStack.Pose pose, int light, float x0, float y0, float z0, float x1, float y1, float z1,
                              float x2, float y2, float z2, float x3, float y3, float z3, float u0, float v0, float u1, float v1,
                              int r, int g, int b, int a, float nx, float ny, float nz) {
        quad(vc, pose, light, new float[]{x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3},
                new float[]{u0, v1, u1, v1, u1, v0, u0, v0},
                new int[]{r, g, b, a, r, g, b, a, r, g, b, a, r, g, b, a}, nx, ny, nz);
    }

    // ------------------------------------------------------------------------------------------------
    //  Rock
    // ------------------------------------------------------------------------------------------------
    /** Draws a unit rock in the current pose. {@code glow} 0..1 is how much the molten cracks light up. */
    static void rock(PoseStack ps, VertexConsumer vc, RockMesh mesh, float glow, int baseLight, int alpha) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int baseBlock = LightTexture.block(baseLight);
        for (int t = 0; t < mesh.triangles; t++) {
            for (int k = 0; k < 4; k++) {
                int c = k == 3 ? 2 : k;
                int i = t * 3 + c;
                float gl = Mth.clamp((mesh.glow[i] - 0.45f) * 2.2f, 0f, 1f) * glow;
                int r = 255, g = FxDraw.c(255f - gl * 80f), b = FxDraw.c(255f - gl * 200f);
                int block = Mth.clamp(baseBlock + (int) (gl * (15 - baseBlock)), 0, 15);
                vc.vertex(m, mesh.pos[i * 3], mesh.pos[i * 3 + 1], mesh.pos[i * 3 + 2])
                        .color(r, g, b, alpha)
                        .uv(mesh.uv[i * 2], mesh.uv[i * 2 + 1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.pack(block, Math.max(LightTexture.sky(baseLight), 10)))
                        .normal(n, mesh.nrm[i * 3], mesh.nrm[i * 3 + 1], mesh.nrm[i * 3 + 2])
                        .endVertex();
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    //  Box
    // ------------------------------------------------------------------------------------------------
    static void box(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                    float uvScale, int r, int g, int b, int a, int light) {
        float u = (x1 - x0) * uvScale, v = (y1 - y0) * uvScale, w = (z1 - z0) * uvScale;
        quad1(vc, pose, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, u, v, r, g, b, a, 0, 0, 1);
        quad1(vc, pose, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, u, v, r, g, b, a, 0, 0, -1);
        quad1(vc, pose, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 0, 0, w, v, r, g, b, a, 1, 0, 0);
        quad1(vc, pose, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, 0, 0, w, v, r, g, b, a, -1, 0, 0);
        quad1(vc, pose, light, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 0, u, w, r, g, b, a, 0, 1, 0);
        quad1(vc, pose, light, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, 0, u, w, r, g, b, a, 0, -1, 0);
    }

    // ------------------------------------------------------------------------------------------------
    //  The greatsword (blade along +Y, hilt at the origin, length L)
    // ------------------------------------------------------------------------------------------------
    static void sword(PoseStack.Pose pose, VertexConsumer vc, float L, int r, int g, int b, int a, int light) {
        float th = 0.022f * L;   // half thickness
        float hw = 0.085f * L;   // half width of the blade
        float shoulder = 0.045f * L;
        // handle and pommel
        box(pose, vc, -0.028f * L, -0.17f * L, -0.028f * L, 0.028f * L, 0f, 0.028f * L, 3f / L, r, g, b, a, light);
        box(pose, vc, -0.05f * L, -0.20f * L, -0.05f * L, 0.05f * L, -0.17f * L, 0.05f * L, 3f / L, r, g, b, a, light);
        // cross guard with swept-up ends
        box(pose, vc, -0.20f * L, 0f, -0.045f * L, 0.20f * L, shoulder, 0.045f * L, 3f / L, r, g, b, a, light);
        box(pose, vc, -0.25f * L, shoulder, -0.035f * L, -0.17f * L, shoulder + 0.05f * L, 0.035f * L, 3f / L, r, g, b, a, light);
        box(pose, vc, 0.17f * L, shoulder, -0.035f * L, 0.25f * L, shoulder + 0.05f * L, 0.035f * L, 3f / L, r, g, b, a, light);
        // blade: a long pointed prism, drawn as stacked slices so it tapers smoothly to the tip
        int slices = 16;
        float y0 = shoulder;
        float tipStart = 0.84f * L;
        for (int i = 0; i < slices; i++) {
            float ya = y0 + (L - y0) * i / slices, yb = y0 + (L - y0) * (i + 1) / slices;
            float wa = widthAt(ya, y0, tipStart, L, hw), wb = widthAt(yb, y0, tipStart, L, hw);
            float ta = th * (wa / hw * 0.6f + 0.4f), tb = th * (wb / hw * 0.6f + 0.4f);
            float v0 = ya * 2.4f / L, v1 = yb * 2.4f / L;
            // front and back faces, with a raised ridge down the middle (two planes each)
            quad1(vc, pose, light, -wa, ya, 0f, 0f, ya, ta, 0f, yb, tb, -wb, yb, 0f, 0f, v0, 1f, v1, r, g, b, a, 0f, 0f, 1f);
            quad1(vc, pose, light, 0f, ya, ta, wa, ya, 0f, wb, yb, 0f, 0f, yb, tb, 0f, v0, 1f, v1, r, g, b, a, 0f, 0f, 1f);
            quad1(vc, pose, light, wa, ya, 0f, 0f, ya, -ta, 0f, yb, -tb, wb, yb, 0f, 0f, v0, 1f, v1, r, g, b, a, 0f, 0f, -1f);
            quad1(vc, pose, light, 0f, ya, -ta, -wa, ya, 0f, -wb, yb, 0f, 0f, yb, -tb, 0f, v0, 1f, v1, r, g, b, a, 0f, 0f, -1f);
        }
    }

    private static float widthAt(float y, float y0, float tipStart, float L, float hw) {
        if (y <= tipStart) {
            return hw * (0.92f + 0.08f * (y - y0) / (tipStart - y0));
        }
        float k = (y - tipStart) / (L - tipStart);
        return hw * (1f - k * k) * 1.0f;
    }

    /** Half width of the blade at height y (used to put the cutting-edge glow exactly on the edge). */
    static float bladeHalfWidth(float L, float y) {
        float shoulder = 0.045f * L;
        return widthAt(y, shoulder, 0.84f * L, L, 0.085f * L);
    }

    /** A soft bright ribbon hugging one cutting edge of the blade (sign = +1 or -1 picks the side). */
    static void edgeGlow(PoseStack.Pose pose, VertexConsumer vc, float L, float sign, float halfWidth, int r, int g, int b, int a, int light) {
        int slices = 14;
        float y0 = 0.045f * L;
        for (int i = 0; i < slices; i++) {
            float ya = y0 + (L - y0) * i / slices, yb = y0 + (L - y0) * (i + 1) / slices;
            float xa = sign * widthAt(ya, y0, 0.84f * L, L, 0.085f * L), xb = sign * widthAt(yb, y0, 0.84f * L, L, 0.085f * L);
            quad1(vc, pose, light, xa - halfWidth, ya, 0f, xa + halfWidth, ya, 0f, xb + halfWidth, yb, 0f, xb - halfWidth, yb, 0f,
                    0f, 0f, 1f, 1f, r, g, b, a, 0f, 0f, 1f);
        }
    }

    // ------------------------------------------------------------------------------------------------
    //  Crescent slash (lying in the horizontal plane, bulging towards +Z)
    // ------------------------------------------------------------------------------------------------
    /**
     * A stack of curved ribbons forming a volume: bright, thin leading edge in front, darker flames trailing behind.
     * {@code halfWidth} is half the chord (6 for a 12-block slash), {@code height} the total height.
     */
    static void crescent(PoseStack.Pose pose, VertexConsumer vc, float halfWidth, float height, float trail, int layers,
                         float scroll, int er, int eg, int eb, int br, int bg, int bb, float alpha, int light) {
        final float phi = (float) Math.toRadians(62);
        float R = halfWidth / Mth.sin(phi);
        float cz = R * Mth.cos(phi);
        int segs = 28;
        for (int li = 0; li < layers; li++) {
            float fl = layers == 1 ? 0.5f : (float) li / (layers - 1);       // 0..1 bottom to top
            float centre = 1f - Math.abs(fl * 2f - 1f);                      // 1 in the middle
            float y = (fl - 0.5f) * height;
            float depth = trail * (0.35f + 0.65f * centre);                  // the middle of the slash trails further
            int a = FxDraw.c(alpha * (0.35f + 0.65f * centre));
            for (int s = 0; s < segs; s++) {
                float a0 = -phi + 2f * phi * s / segs, a1 = -phi + 2f * phi * (s + 1) / segs;
                float t0 = Mth.cos((a0 / phi) * Mth.HALF_PI), t1 = Mth.cos((a1 / phi) * Mth.HALF_PI);
                float rf0 = R, rf1 = R;
                float rb0 = R - depth * t0, rb1 = R - depth * t1;
                float u0 = scroll + 2f * s / segs, u1 = scroll + 2f * (s + 1) / segs;
                quad(vc, pose, light,
                        new float[]{Mth.sin(a0) * rf0, y, Mth.cos(a0) * rf0 - cz,
                                Mth.sin(a1) * rf1, y, Mth.cos(a1) * rf1 - cz,
                                Mth.sin(a1) * rb1, y, Mth.cos(a1) * rb1 - cz,
                                Mth.sin(a0) * rb0, y, Mth.cos(a0) * rb0 - cz},
                        new float[]{u0, 0f, u1, 0f, u1, 1f, u0, 1f},
                        new int[]{er, eg, eb, a, er, eg, eb, a, br, bg, bb, 0, br, bg, bb, 0}, 0f, 1f, 0f);
            }
        }
        // the cutting edge itself: a bright vertical fin along the front arc
        for (int s = 0; s < segs; s++) {
            float a0 = -phi + 2f * phi * s / segs, a1 = -phi + 2f * phi * (s + 1) / segs;
            float h0 = 0.5f * height * Mth.cos((a0 / phi) * Mth.HALF_PI * 0.9f), h1 = 0.5f * height * Mth.cos((a1 / phi) * Mth.HALF_PI * 0.9f);
            float nx = Mth.sin((a0 + a1) * 0.5f), nz = Mth.cos((a0 + a1) * 0.5f);
            int ea = FxDraw.c(alpha * 0.95f);
            quad(vc, pose, light,
                    new float[]{Mth.sin(a0) * R, -h0, Mth.cos(a0) * R - cz, Mth.sin(a1) * R, -h1, Mth.cos(a1) * R - cz,
                            Mth.sin(a1) * R, h1, Mth.cos(a1) * R - cz, Mth.sin(a0) * R, h0, Mth.cos(a0) * R - cz},
                    new float[]{0f, 0.5f, 1f, 0.5f, 1f, 0.5f, 0f, 0.5f},
                    new int[]{255, 250, 225, 0, 255, 250, 225, 0, 255, 250, 225, ea, 255, 250, 225, ea}, nx, 0f, nz);
        }
    }

    // ------------------------------------------------------------------------------------------------
    //  Flat things on the ground
    // ------------------------------------------------------------------------------------------------
    /** A square quad of half size {@code half} lying flat at height y, textured 0..1. */
    static void groundQuad(PoseStack.Pose pose, VertexConsumer vc, float half, float y, int r, int g, int b, int a, int light) {
        quad1(vc, pose, light, -half, y, -half, half, y, -half, half, y, half, -half, y, half, 0f, 0f, 1f, 1f, r, g, b, a, 0f, 1f, 0f);
    }

    /** A flat ribbon along +Z from 0 to length, half width w, at height y. u across, v along (repeating every 'vRep' blocks). */
    static void groundStrip(PoseStack.Pose pose, VertexConsumer vc, float length, float w, float y, float vRep, float scroll,
                            int r, int g, int b, int a, int light) {
        quad1(vc, pose, light, -w, y, 0f, w, y, 0f, w, y, length, -w, y, length, 0f, scroll, 1f, scroll + length / vRep,
                r, g, b, a, 0f, 1f, 0f);
    }

    /** A vertical sheet along +Z (length) at x = xOff, from y=0 to height, u along the length, v up. Alpha fades towards the top. */
    static void sheet(PoseStack.Pose pose, VertexConsumer vc, float length, float height, float xOff, float uRep, float uScroll,
                      float vScroll, int r, int g, int b, int aBottom, int aTop, int light) {
        quad(vc, pose, light,
                new float[]{xOff, 0f, 0f, xOff, 0f, length, xOff, height, length, xOff, height, 0f},
                new float[]{uScroll, 1f + vScroll, uScroll + length / uRep, 1f + vScroll, uScroll + length / uRep, vScroll, uScroll, vScroll},
                new int[]{r, g, b, aBottom, r, g, b, aBottom, r, g, b, aTop, r, g, b, aTop}, 1f, 0f, 0f);
    }
}
