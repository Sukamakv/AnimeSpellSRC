package com.example.jjkspells.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Shared helper for drawing fullbright camera-facing sprites (billboards) for the two orbs.
 */
public abstract class OrbRenderBase<T extends Entity> extends EntityRenderer<T> {
    protected OrbRenderBase(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    /** The drawn glow is much larger than the tiny hitbox, so never frustum-cull by hitbox alone. */
    @Override
    public boolean shouldRender(T entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    /** Not used (we pick textures per layer), but required by EntityRenderer. */
    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return new ResourceLocation("textures/misc/white.png");
    }

    /**
     * Draws one billboard layer centred on the current pose.
     *
     * @param additive true = glowing additive blending (no depth write); false = normal translucent
     * @param halfSize half the quad width in blocks
     * @param spinDegrees rotation around the view axis
     * @param alpha 0..255
     */
    protected void drawLayer(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture, boolean additive,
                             float halfSize, float spinDegrees, int alpha) {
        if (halfSize <= 0.001f || alpha <= 0) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(spinDegrees));

        RenderType type = additive ? RenderType.eyes(texture) : RenderType.entityTranslucent(texture);
        VertexConsumer vc = buffer.getBuffer(type);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();

        vertex(vc, matrix, normal, -halfSize, -halfSize, 0f, 1f, alpha);
        vertex(vc, matrix, normal, halfSize, -halfSize, 1f, 1f, alpha);
        vertex(vc, matrix, normal, halfSize, halfSize, 1f, 0f, alpha);
        vertex(vc, matrix, normal, -halfSize, halfSize, 0f, 0f, alpha);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f matrix, Matrix3f normal, float x, float y, float u, float v, int alpha) {
        vc.vertex(matrix, x, y, 0f)
                .color(255, 255, 255, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0f, 1f, 0f)
                .endVertex();
    }

    protected static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t);
    }
}
