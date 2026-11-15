package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.UnlimitedVoidDome;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the dome as a big sphere seen from inside (and outside): a deep-space sky texture plus a
 * second, transparent star layer that turns the other way, so the stars drift past each other.
 */
public class UnlimitedVoidDomeRenderer extends EntityRenderer<UnlimitedVoidDome> {
    private static final ResourceLocation SKY = JJKSpells.id("textures/entity/void_sky.png");
    private static final ResourceLocation STARS = JJKSpells.id("textures/entity/void_stars.png");

    private static final int LAT = 22;
    private static final int LON = 44;

    public UnlimitedVoidDomeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(UnlimitedVoidDome entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(UnlimitedVoidDome entity) {
        return SKY;
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    @Override
    public void render(UnlimitedVoidDome dome, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
        float age = dome.tickCount + partialTick;
        float grow = ease(age / 18f) * ease((dome.getDuration() - age) / 14f);
        if (grow <= 0.003f) return;
        float R = dome.getRadius() * grow;
        int alpha = (int) (245 * Mth.clamp(grow * 2f, 0f, 1f));

        pose.pushPose();
        sphere(pose, buffer.getBuffer(RenderType.entityTranslucentEmissive(SKY)), R, age * 0.0004f, 2f, alpha);
        sphere(pose, buffer.getBuffer(RenderType.entityTranslucentEmissive(STARS)), R * 0.992f, -age * 0.0011f, 3f, alpha);
        pose.popPose();
    }

    private static void sphere(PoseStack pose, VertexConsumer vc, float R, float scroll, float uRepeat, int alpha) {
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        scroll = scroll - (float) Math.floor(scroll);

        for (int i = 0; i < LAT; i++) {
            float t0 = (float) Math.PI * i / LAT, t1 = (float) Math.PI * (i + 1) / LAT;
            float y0 = Mth.cos(t0) * R, y1 = Mth.cos(t1) * R;
            float r0 = Mth.sin(t0) * R, r1 = Mth.sin(t1) * R;
            float v0 = (float) i / LAT, v1 = (float) (i + 1) / LAT;
            for (int j = 0; j < LON; j++) {
                float p0 = (float) (Math.PI * 2) * j / LON, p1 = (float) (Math.PI * 2) * (j + 1) / LON;
                float c0 = Mth.cos(p0), s0 = Mth.sin(p0), c1 = Mth.cos(p1), s1 = Mth.sin(p1);
                float u0 = scroll + uRepeat * j / LON, u1 = scroll + uRepeat * (j + 1) / LON;

                float ax = r0 * c0, az = r0 * s0, bx = r0 * c1, bz = r0 * s1;
                float cx = r1 * c1, cz = r1 * s1, dx = r1 * c0, dz = r1 * s0;
                // both windings, so it is visible from inside and from outside
                v(vc, m, n, ax, y0, az, u0, v0, alpha);
                v(vc, m, n, bx, y0, bz, u1, v0, alpha);
                v(vc, m, n, cx, y1, cz, u1, v1, alpha);
                v(vc, m, n, dx, y1, dz, u0, v1, alpha);

                v(vc, m, n, dx, y1, dz, u0, v1, alpha);
                v(vc, m, n, cx, y1, cz, u1, v1, alpha);
                v(vc, m, n, bx, y0, bz, u1, v0, alpha);
                v(vc, m, n, ax, y0, az, u0, v0, alpha);
            }
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int alpha) {
        vc.vertex(m, x, y, z)
                .color(255, 255, 255, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(n, 0f, 1f, 0f)
                .endVertex();
    }
}
