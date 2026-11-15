package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.LapseBlueOrb;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Swirling blue orb: a counter-rotating halo, a spiral-armed swirl and a bright pulsing core. */
public class LapseBlueRenderer extends OrbRenderBase<LapseBlueOrb> {
    private static final ResourceLocation CORE = JJKSpells.id("textures/entity/lapse_blue_core.png");
    private static final ResourceLocation SWIRL = JJKSpells.id("textures/entity/lapse_blue_swirl.png");
    private static final ResourceLocation HALO = JJKSpells.id("textures/entity/lapse_blue_halo.png");

    public LapseBlueRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LapseBlueOrb orb, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float age = orb.tickCount + partialTick;
        int life = orb.getLifetime();
        float grow = easeOut(age / 6f) * easeOut((life - age) / 10f);
        float radius = orb.getRadius() * grow;
        float pulse = 1f + 0.06f * Mth.sin(age * 0.6f);

        poseStack.pushPose();
        poseStack.translate(0.0, orb.getBbHeight() * 0.5, 0.0);

        drawLayer(poseStack, buffer, HALO, true, radius * 2.3f, -age * 4f, 150);
        drawLayer(poseStack, buffer, SWIRL, true, radius * 1.5f * pulse, age * 16f, 235);
        drawLayer(poseStack, buffer, SWIRL, true, radius * 1.05f, -age * 24f, 200);
        drawLayer(poseStack, buffer, CORE, true, radius * 0.95f * pulse, 0f, 255);

        poseStack.popPose();
        super.render(orb, yaw, partialTick, poseStack, buffer, packedLight);
    }
}
