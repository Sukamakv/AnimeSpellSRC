package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.ReversalRedProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Glowing red orb: a soft red glow, rotating four-armed tendrils and a dark crimson core. */
public class ReversalRedRenderer extends OrbRenderBase<ReversalRedProjectile> {
    private static final ResourceLocation GLOW = JJKSpells.id("textures/entity/reversal_red_glow.png");
    private static final ResourceLocation ARMS = JJKSpells.id("textures/entity/reversal_red_arms.png");
    private static final ResourceLocation CORE = JJKSpells.id("textures/entity/reversal_red_core.png");

    public ReversalRedRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ReversalRedProjectile orb, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float age = orb.tickCount + partialTick;
        float appear = easeOut(age / 3f);
        float pulse = 1f + 0.08f * Mth.sin(age * 0.9f);

        poseStack.pushPose();
        poseStack.translate(0.0, orb.getBbHeight() * 0.5, 0.0);

        drawLayer(poseStack, buffer, GLOW, true, 1.35f * appear * pulse, 0f, 200);
        drawLayer(poseStack, buffer, ARMS, true, 1.7f * appear, age * 22f, 230);
        drawLayer(poseStack, buffer, CORE, false, 0.62f * appear * pulse, -age * 9f, 255);
        drawLayer(poseStack, buffer, GLOW, true, 0.55f * appear, 0f, 255);

        poseStack.popPose();
        super.render(orb, yaw, partialTick, poseStack, buffer, packedLight);
    }
}
