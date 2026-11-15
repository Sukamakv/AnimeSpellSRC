package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.TheWorldEntity;
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

/**
 * Draws The World: a fullbright body, a pulsing golden aura shell, and, during the fast attack poses, two fading
 * afterimages of the arms a moment behind the real thing.
 */
public class TheWorldRenderer extends EntityRenderer<TheWorldEntity> {
    private static final ResourceLocation TEXTURE = JJKSpells.id("textures/entity/the_world.png");
    private static final ResourceLocation STAR_TEXTURE = JJKSpells.id("textures/entity/star_platinum.png");
    private static final float SCALE = 1.0f;

    private final TheWorldModel model;
    private final TheWorldModel starModel;

    public TheWorldRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new TheWorldModel(context.bakeLayer(TheWorldModel.LAYER));
        this.starModel = new TheWorldModel(context.bakeLayer(TheWorldModel.LAYER_STAR));
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(TheWorldEntity entity) {
        return entity.isStar() ? STAR_TEXTURE : TEXTURE;
    }

    @Override
    public boolean shouldRender(TheWorldEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(TheWorldEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        final boolean star = e.isStar();
        final TheWorldModel model = star ? this.starModel : this.model;
        final ResourceLocation TEXTURE = star ? STAR_TEXTURE : TheWorldRenderer.TEXTURE;
        float a = e.alpha(pt);
        if (a <= 0.01f) return;
        float age = e.tickCount + pt;
        int pose = e.getPose2();
        float t = e.poseTicks(pt);
        int bright = LightTexture.pack(15, 15);

        ps.pushPose();
        float yRot = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        ps.mulPose(Axis.YP.rotationDegrees(180f - yRot));
        ps.scale(-SCALE, -SCALE, SCALE);
        ps.translate(0, -1.501, 0);

        // afterimages first, so the real body is drawn over them
        if (pose == TheWorldEntity.POSE_BARRAGE || pose == TheWorldEntity.POSE_SMASH || pose == TheWorldEntity.POSE_SLAM
                || (star && (pose == TheWorldEntity.POSE_DASH || pose == TheWorldEntity.POSE_FINGER))) {
            for (int i = 2; i >= 1; i--) {
                model.animate(pose, Math.max(0f, t - i * 1.5f), age);
                VertexConsumer ghost = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
                float ga = a * (i == 1 ? 0.38f : 0.2f);
                if (star) model.render(ps, ghost, bright, OverlayTexture.NO_OVERLAY, 0.62f, 0.5f, 1f, ga);
                else model.render(ps, ghost, bright, OverlayTexture.NO_OVERLAY, 1f, 0.92f, 0.5f, ga);
            }
        }

        model.animate(pose, t, age);
        VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        model.render(ps, body, bright, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, a);

        // golden aura: a slightly larger shell with scrolling energy
        boolean attacking = pose != TheWorldEntity.POSE_IDLE;
        float pulse = ((attacking ? 0.42f : 0.26f) + 0.08f * Mth.sin(age * 0.5f)) * a;
        ps.pushPose();
        ps.scale(1.045f, 1.03f, 1.045f);
        VertexConsumer aura = buffers.getBuffer(RenderType.energySwirl(TEXTURE, (age * 0.006f) % 1f, (age * -0.02f) % 1f));
        if (star) {
            float cy = 0.5f + 0.5f * Mth.sin(age * 0.7f);   // flickers between royal purple and electric blue
            model.render(ps, aura, bright, OverlayTexture.NO_OVERLAY, (0.54f - 0.3f * cy) * pulse * 1.3f, (0.17f + 0.35f * cy) * pulse * 1.3f, (0.89f + 0.1f * cy) * pulse * 1.3f, 1f);
        } else {
            model.render(ps, aura, bright, OverlayTexture.NO_OVERLAY, 1f * pulse, 0.78f * pulse, 0.12f * pulse, 1f);
        }
        ps.popPose();

        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }
}
