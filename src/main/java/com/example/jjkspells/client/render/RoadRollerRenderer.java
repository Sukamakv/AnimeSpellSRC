package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.RoadRollerEntity;
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

/** Draws DIO's road roller, shuddering harder as the crushing blows land and fading in and out. */
public class RoadRollerRenderer extends EntityRenderer<RoadRollerEntity> {
    private static final ResourceLocation TEXTURE = JJKSpells.id("textures/entity/road_roller.png");
    private static final float SCALE = 0.72f;

    private final RoadRollerModel model;

    public RoadRollerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new RoadRollerModel(context.bakeLayer(RoadRollerModel.LAYER));
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(RoadRollerEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(RoadRollerEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(RoadRollerEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float a = e.alpha(pt);
        if (a <= 0.01f) return;
        float age = e.tickCount + pt;
        ps.pushPose();
        int shake = e.getShake();
        if (shake > 0) {
            float k = shake * 0.012f;
            ps.translate(Mth.sin(age * 3.1f) * k, Mth.sin(age * 4.3f + 1f) * k * 0.8f, Mth.sin(age * 2.7f + 2f) * k);
        }
        float yRot = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        ps.mulPose(Axis.YP.rotationDegrees(180f - yRot));
        ps.scale(-SCALE, -SCALE, SCALE);
        ps.translate(0, -1.501, 0);
        model.animate(age * 0.12f);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        model.render(ps, vc, LightTexture.pack(Math.max(LightTexture.block(light), 10), Math.max(LightTexture.sky(light), 10)), OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, a);
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }
}
