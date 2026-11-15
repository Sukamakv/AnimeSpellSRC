package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.DeathWraithEntity;
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
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a wraith: a hooded, faceless black shape with a tattered flowing hem, always turned towards the viewer,
 * swaying as it drifts, with a faint cold halo behind it.
 */
public class DeathWraithRenderer extends EntityRenderer<DeathWraithEntity> {
    private static final ResourceLocation TEX = JJKSpells.id("textures/entity/death_wraith.png");
    private static final ResourceLocation GLOW = JJKSpells.id("textures/entity/fx_glow.png");
    private static final float HALF_W = 1.05f;
    private static final float HALF_H = 1.6f;

    public DeathWraithRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(DeathWraithEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(DeathWraithEntity entity) {
        return TEX;
    }

    @Override
    public void render(DeathWraithEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float age = e.tickCount + pt;
        float grow = Mth.clamp(age / 10f, 0f, 1f);
        float bob = Mth.sin(age * 0.25f + e.getId()) * 0.08f;
        float roll = Mth.sin(age * 0.18f + e.getId() * 1.7f) * 6f;

        ps.pushPose();
        ps.translate(0, bob, 0);
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        ps.scale(grow, grow, grow);

        // cold halo behind it (additive texture, premultiplied)
        VertexConsumer glow = buf.getBuffer(RenderType.eyes(GLOW));
        ps.pushPose();
        ps.translate(0, 0, 0.02);
        quad(glow, ps, 1.9f, 2.4f, 130, 120, 230, (int) (70 * grow), LightTexture.FULL_BRIGHT);
        ps.popPose();

        // the wraith itself (normal alpha blending)
        VertexConsumer body = buf.getBuffer(RenderType.entityTranslucent(TEX));
        quad(body, ps, HALF_W, HALF_H, 255, 255, 255, 255, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack ps, float hw, float hh, int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        v(vc, m, n, -hw, -hh, 0f, 1f, r, g, b, a, light);
        v(vc, m, n, hw, -hh, 1f, 1f, r, g, b, a, light);
        v(vc, m, n, hw, hh, 1f, 0f, r, g, b, a, light);
        v(vc, m, n, -hw, hh, 0f, 0f, r, g, b, a, light);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float vv,
                          int r, int g, int b, int a, int light) {
        vc.vertex(m, x, y, 0f).color(r, g, b, a).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0f, 1f, 0f).endVertex();
    }
}
