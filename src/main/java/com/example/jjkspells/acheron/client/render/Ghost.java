package com.example.jjkspells.acheron.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A translucent, tinted copy of the caster's own player model (skin included) in a fixed pose. Used for the Acheron afterimages
 * and the Doppelganger instead of a separate model of Acheron.
 */
final class Ghost {
    static final int STAND = 0, SLASH = 1, DASH = 2;

    private Ghost() {
    }

    private static void rot(net.minecraft.client.model.geom.ModelPart p, float x, float y, float z) {
        p.xRot = x;
        p.yRot = y;
        p.zRot = z;
    }

    /** Draws the model with its feet at 'at' (relative to the current pose stack), facing the entity yaw (degrees). */
    static void draw(MultiBufferSource buf, PoseStack ps, Entity owner, Vec3 at, float yaw, float[] tint, float alpha, int pose) {
        if (alpha <= 0.01f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin();
        String type = "default";
        if (owner instanceof AbstractClientPlayer ap) {
            skin = ap.getSkinTextureLocation();
            type = ap.getModelName();
        }
        var r = mc.getEntityRenderDispatcher().getSkinMap().get(type);
        if (!(r instanceof PlayerRenderer pr)) {
            return;
        }
        PlayerModel<AbstractClientPlayer> m = pr.getModel();
        m.setAllVisible(true);
        m.young = false;
        m.riding = false;
        m.crouching = false;
        m.attackTime = 0f;
        m.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        m.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        float ay = "slim".equals(type) ? 2.5f : 2.0f;
        m.head.setPos(0, 0, 0);
        m.body.setPos(0, 0, 0);
        m.rightArm.setPos(-5, ay, 0);
        m.leftArm.setPos(5, ay, 0);
        m.rightLeg.setPos(-1.9f, 12, 0);
        m.leftLeg.setPos(1.9f, 12, 0);
        float lean = 0f;
        switch (pose) {
            case SLASH -> {
                rot(m.head, -0.15f, 0.25f, 0);
                rot(m.body, 0, 0.4f, 0);
                rot(m.rightArm, -1.9f, 0.1f, 0.2f);
                rot(m.leftArm, 0.5f, 0, -0.35f);
                rot(m.rightLeg, -0.55f, 0, 0.1f);
                rot(m.leftLeg, 0.5f, 0, -0.1f);
            }
            case DASH -> {
                rot(m.head, -0.35f, 0, 0);
                rot(m.body, 0.1f, 0, 0);
                rot(m.rightArm, 0.9f, 0, 0.12f);
                rot(m.leftArm, 0.9f, 0, -0.12f);
                rot(m.rightLeg, -0.9f, 0, 0);
                rot(m.leftLeg, 0.75f, 0, 0);
                lean = 0.3f;
            }
            default -> {
                rot(m.head, 0, 0, 0);
                rot(m.body, 0, 0, 0);
                rot(m.rightArm, 0.05f, 0, 0.08f);
                rot(m.leftArm, 0.05f, 0, -0.08f);
                rot(m.rightLeg, 0, 0, 0.03f);
                rot(m.leftLeg, 0, 0, -0.03f);
            }
        }
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        if (lean != 0f) {
            ps.translate(0, 0.8, 0);
            ps.mulPose(Axis.XP.rotation(-lean));
            ps.translate(0, -0.8, 0);
        }
        ps.scale(-1f, -1f, 1f);
        ps.translate(0f, -1.501f, 0f);
        int full = LightTexture.FULL_BRIGHT;
        VertexConsumer body = buf.getBuffer(com.example.jjkspells.client.SoftRender.entity(skin));
        m.renderToBuffer(ps, body, full, OverlayTexture.NO_OVERLAY, tint[0], tint[1], tint[2], alpha);
        VertexConsumer glow = buf.getBuffer(RenderType.eyes(skin));
        m.renderToBuffer(ps, glow, full, OverlayTexture.NO_OVERLAY, tint[0] * 0.8f, tint[1] * 0.9f, tint[2], alpha * 0.55f);
        ps.popPose();
    }
}
