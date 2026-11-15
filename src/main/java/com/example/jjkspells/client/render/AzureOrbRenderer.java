package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.AzureOrbProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A spinning disc of blue chakra fire: soft glow, rotating flame fans and a white-hot core. */
public class AzureOrbRenderer extends OrbRenderBase<AzureOrbProjectile> {
    private static final ResourceLocation GLOW = JJKSpells.id("textures/entity/azure_orb_glow.png");
    private static final ResourceLocation ARMS = JJKSpells.id("textures/entity/azure_orb_arms.png");
    private static final ResourceLocation CORE = JJKSpells.id("textures/entity/azure_orb_core.png");

    public AzureOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(AzureOrbProjectile orb, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float age = orb.tickCount + partialTick;
        float appear = easeOut(age / 3f);
        float pulse = 1f + 0.08f * Mth.sin(age * 0.8f);

        poseStack.pushPose();
        poseStack.translate(0.0, orb.getBbHeight() * 0.5, 0.0);
        drawLayer(poseStack, buffer, GLOW, true, 3.4f * appear * pulse, 0f, 120);
        drawLayer(poseStack, buffer, ARMS, true, 2.6f * appear, age * 34f, 190);
        drawLayer(poseStack, buffer, ARMS, true, 1.9f * appear, -age * 21f, 150);
        drawLayer(poseStack, buffer, CORE, true, 1.25f * appear * pulse, 0f, 210);
        poseStack.popPose();
        super.render(orb, yaw, partialTick, poseStack, buffer, packedLight);
    }
}
