package com.example.jjkspells.yasuo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws real Minecraft player models as afterimages / doppelgangers: either flat tinted silhouettes with a soft outline, or in full colour with the caster's
 * own skin. Poses are set by hand, since the copies are not entities and have no animation of their own.
 */
public final class PlayerGhost {
    /** Bone rotations in radians. */
    public static final class Pose {
        public float headX, headY, bodyX, bodyY;
        public float rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ;
        public float rLegX, rLegZ, lLegX, lLegZ;
        /** Whole-figure forward lean in radians. */
        public float lean;

        /** Mid-dash lunge: leaning forward, sword arm stretched out, legs in full stride. stride = +1 or -1 swaps the legs. */
        public static Pose lunge(float stride) {
            Pose p = new Pose();
            p.lean = 0.5f;
            p.headX = -0.35f;
            p.rArmX = -1.55f;
            p.rArmZ = 0.1f;
            p.lArmX = 0.9f;
            p.lArmZ = -0.35f;
            p.rLegX = -0.95f * stride;
            p.lLegX = 0.85f * stride;
            return p;
        }

        /** Overhead cut: sword arm raised back, off arm forward, wide stance. */
        public static Pose slash() {
            Pose p = new Pose();
            p.lean = 0.25f;
            p.bodyY = 0.35f;
            p.headY = -0.3f;
            p.rArmX = -2.6f;
            p.rArmZ = 0.35f;
            p.lArmX = -0.9f;
            p.lArmZ = -0.5f;
            p.rLegX = -0.6f;
            p.lLegX = 0.5f;
            p.lLegZ = -0.15f;
            p.rLegZ = 0.15f;
            return p;
        }

        /** Spinning cut: sword arm stretched straight out, off arm tucked back, feet planted. */
        public static Pose spin() {
            Pose p = new Pose();
            p.lean = 0.1f;
            p.bodyY = 0.5f;
            p.headY = -0.5f;
            p.rArmX = -1.57f;
            p.lArmX = -0.3f;
            p.lArmZ = -0.9f;
            p.rLegX = -0.35f;
            p.lLegX = 0.35f;
            p.rLegZ = 0.2f;
            p.lLegZ = -0.2f;
            return p;
        }

        /** Low sweeping cut after the strike: crouched, arm trailing behind. */
        public static Pose finish() {
            Pose p = new Pose();
            p.lean = 0.7f;
            p.headX = -0.5f;
            p.rArmX = -1.2f;
            p.rArmY = -0.7f;
            p.lArmX = 0.6f;
            p.rLegX = -1.1f;
            p.lLegX = 0.9f;
            return p;
        }
    }

    private final PlayerModel<LivingEntity> wide;
    private final PlayerModel<LivingEntity> slim;

    public PlayerGhost(EntityRendererProvider.Context ctx) {
        this.wide = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    public static ResourceLocation skinOf(Entity e) {
        return e instanceof AbstractClientPlayer ap ? ap.getSkinTextureLocation() : DefaultPlayerSkin.getDefaultSkin();
    }

    public static boolean slimOf(Entity e) {
        return e instanceof AbstractClientPlayer ap && "slim".equals(ap.getModelName());
    }

    private void apply(PlayerModel<LivingEntity> m, Pose p) {
        m.head.xRot = p.headX;
        m.head.yRot = p.headY;
        m.head.zRot = 0;
        m.body.xRot = p.bodyX;
        m.body.yRot = p.bodyY;
        m.body.zRot = 0;
        m.rightArm.xRot = p.rArmX;
        m.rightArm.yRot = p.rArmY;
        m.rightArm.zRot = p.rArmZ;
        m.leftArm.xRot = p.lArmX;
        m.leftArm.yRot = p.lArmY;
        m.leftArm.zRot = p.lArmZ;
        m.rightLeg.xRot = p.rLegX;
        m.rightLeg.yRot = 0;
        m.rightLeg.zRot = p.rLegZ;
        m.leftLeg.xRot = p.lLegX;
        m.leftLeg.yRot = 0;
        m.leftLeg.zRot = p.lLegZ;
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    private void draw(PoseStack ps, MultiBufferSource buf, PlayerModel<LivingEntity> m, ResourceLocation tex, Vec3 rel, double yawDeg, Pose pose, double scale, float[] col, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        apply(m, pose);
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(tex));
        ps.pushPose();
        ps.translate(rel.x, rel.y, rel.z);
        ps.mulPose(Axis.YP.rotationDegrees((float) (180.0 - yawDeg)));
        ps.translate(0, 0.9, 0);
        ps.scale((float) scale, (float) scale, (float) scale);
        ps.mulPose(Axis.XP.rotation(-pose.lean));
        ps.translate(0, -0.9, 0);
        ps.scale(-1f, -1f, 1f);
        ps.scale(0.9375f, 0.9375f, 0.9375f);
        ps.translate(0, -1.501, 0);
        m.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, col[0], col[1], col[2], alpha);
        ps.popPose();
    }

    /**
     * Flat tinted silhouette with a soft outline.
     *
     * @param rel    feet position relative to the entity being rendered (the pose stack's origin)
     * @param yawDeg facing as Minecraft yaw (0 = +Z, 90 = -X)
     */
    public void silhouette(PoseStack ps, MultiBufferSource buf, boolean slimArms, Vec3 rel, double yawDeg, Pose pose, float[] col, float alpha) {
        PlayerModel<LivingEntity> m = slimArms ? slim : wide;
        draw(ps, buf, m, YasuoRender.GHOST, rel, yawDeg, pose, 1.07, new float[]{col[0] * 0.8f + 0.2f, col[1] * 0.8f + 0.2f, col[2]}, alpha * 0.4f);
        draw(ps, buf, m, YasuoRender.GHOST, rel, yawDeg, pose, 1.0, col, alpha);
    }

    /** Full-colour copy wearing the given skin. */
    public void skinned(PoseStack ps, MultiBufferSource buf, ResourceLocation skin, boolean slimArms, Vec3 rel, double yawDeg, Pose pose, float alpha) {
        PlayerModel<LivingEntity> m = slimArms ? slim : wide;
        draw(ps, buf, m, skin, rel, yawDeg, pose, 1.0, new float[]{1f, 1f, 1f}, alpha);
    }

    public static double yawOf(Vec3 dir) {
        return Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }
}
