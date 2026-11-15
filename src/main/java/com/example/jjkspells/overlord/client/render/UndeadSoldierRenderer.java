package com.example.jjkspells.overlord.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.overlord.client.model.SoldierModel;
import com.example.jjkspells.overlord.entity.UndeadSoldierEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Skeleton soldier: dark bone, netherite armour layer, glowing purple eye flames and a pulsing purple summoning aura. */
public class UndeadSoldierRenderer extends HumanoidMobRenderer<UndeadSoldierEntity, SoldierModel> {
    private static final ResourceLocation TEX = new ResourceLocation("textures/entity/skeleton/wither_skeleton.png");
    private static final ResourceLocation AURA = tex("undead_aura");

    private static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/entity/" + n + ".png");
    }

    public UndeadSoldierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SoldierModel(ctx.bakeLayer(ModelLayers.WITHER_SKELETON)), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.SKELETON_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.SKELETON_OUTER_ARMOR)),
                ctx.getModelManager()));
        this.addLayer(new AuraLayer(this));
    }

    @Override
    protected void scale(UndeadSoldierEntity e, PoseStack ps, float partialTick) {
        ps.scale(1.2f, 1.2f, 1.2f);
    }

    @Override
    public ResourceLocation getTextureLocation(UndeadSoldierEntity e) {
        return TEX;
    }

    /** Additive swirling purple shell (what marks these skeletons as summons). */
    private static class AuraLayer extends RenderLayer<UndeadSoldierEntity, SoldierModel> {
        AuraLayer(RenderLayerParent<UndeadSoldierEntity, SoldierModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, UndeadSoldierEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float age, float headYaw, float headPitch) {
            if (e.isInvisible()) {
                return;
            }
            float f = age * 0.01f;
            VertexConsumer vc = buf.getBuffer(RenderType.energySwirl(AURA, (f % 1.0f), f * 0.7f % 1.0f));
            this.getParentModel().renderToBuffer(ps, vc, 15728880, OverlayTexture.NO_OVERLAY, 0.55f, 0.2f, 0.95f, 1.0f);
        }
    }
}
