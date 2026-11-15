package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.AzureColossus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws the colossus: a glowing translucent body plus a scrolling blue-fire energy layer. */
public class AzureColossusRenderer extends MobRenderer<AzureColossus, AzureColossusModel> {
    private static final ResourceLocation TEXTURE = JJKSpells.id("textures/entity/azure_colossus.png");
    private static final ResourceLocation AURA = JJKSpells.id("textures/entity/azure_colossus_aura.png");

    public AzureColossusRenderer(EntityRendererProvider.Context context) {
        super(context, new AzureColossusModel(context.bakeLayer(AzureColossusModel.LAYER)), 0f);
        this.addLayer(new AuraLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(AzureColossus entity) {
        return TEXTURE;
    }

    /** The arms and sword reach far outside the hitbox: never cull by hitbox alone. */
    @Override
    public boolean shouldRender(AzureColossus entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    /** It rises out of the ground when summoned. */
    @Override
    protected void scale(AzureColossus entity, PoseStack poseStack, float partialTick) {
        float t = Mth.clamp((entity.tickCount + partialTick) / 26f, 0f, 1f);
        float grow = 1f - (1f - t) * (1f - t);
        float s = AzureColossusModel.SCALE * (0.1f + 0.9f * grow);
        poseStack.scale(s, s, s);
    }

    /** No tipping over when it dies. */
    @Override
    protected void setupRotations(AzureColossus entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
    }

    @Override
    protected RenderType getRenderType(AzureColossus entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(entity));
    }

    // self-lit: always fullbright
    @Override
    protected int getBlockLightLevel(AzureColossus entity, BlockPos pos) {
        return 9; // glows softly, but still darkens at night
    }

    @Override
    protected int getSkyLightLevel(AzureColossus entity, BlockPos pos) {
        return Math.max(super.getSkyLightLevel(entity, pos), 6);
    }

    /** A second, slightly larger copy of the body drawn with a scrolling flame texture: the "burning" energy. */
    private static class AuraLayer extends RenderLayer<AzureColossus, AzureColossusModel> {
        AuraLayer(RenderLayerParent<AzureColossus, AzureColossusModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AzureColossus entity, float limbSwing,
                           float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            float t = ageInTicks;
            VertexConsumer vc = buffer.getBuffer(RenderType.energySwirl(AURA, (t * 0.004f) % 1f, (t * -0.018f) % 1f));
            poseStack.pushPose();
            poseStack.scale(1.035f, 1.025f, 1.035f);
            float pulse = 0.28f + 0.07f * Mth.sin(t * 0.2f);
            this.getParentModel().renderToBuffer(poseStack, vc, 15728880, OverlayTexture.NO_OVERLAY, 0.3f * pulse + 0.05f, 0.6f * pulse + 0.08f, pulse + 0.12f, 1f);
            poseStack.popPose();
        }
    }
}
