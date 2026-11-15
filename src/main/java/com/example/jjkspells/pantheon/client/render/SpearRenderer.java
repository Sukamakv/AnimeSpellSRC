package com.example.jjkspells.pantheon.client.render;

import com.example.jjkspells.pantheon.entity.SpearEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.pantheon.client.render.Gfx.*;

/** The flying comet spear: two crossed spear sprites, a glow at the head and a long golden trail behind. */
public class SpearRenderer extends EntityRenderer<SpearEntity> {
    public SpearRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(SpearEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SpearEntity e) {
        return SPEAR;
    }

    @Override
    public void render(SpearEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        int state = e.getState();
        float fade = state <= 0 ? 1f : 1f - c01((state + pt) / (SpearEntity.ENDING_TICKS + 1f));
        if (fade <= 0.01f) {
            return;
        }
        float yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float pitch = Mth.lerp(pt, e.xRotO, e.getXRot());
        float f = e.getCharge();
        Vec3 base = e.getPosition(pt);
        Vec3 cam = cam(base);
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3 dir = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));

        // trail
        float len = (7f + 7f * f) * (state <= 0 ? 1f : 0.4f);
        Vec3 tail = dir.scale(-len);
        float w = 0.28f + 0.5f * f;
        Draw.ribbon(add(buf, TRAIL), ps, tail, Vec3.ZERO, cam, 0.02f, w, 0f, 1f, GOLD, 0f, fade);
        Draw.ribbon(add(buf, TRAIL), ps, tail.scale(0.7), Vec3.ZERO, cam, 0.02f, w * 0.45f, 0f, 1f, WARM, 0f, fade);
        Draw.ribbon(add(buf, TRAIL), ps, tail.scale(1.2), Vec3.ZERO, cam, 0.0f, w * 1.9f, 0f, 1f, AMBER, 0f, 0.45f * fade);

        // the spear: two crossed planes, long axis along the flight direction
        float hh = 0.22f + 0.3f * f;
        float hw = hh * 8f;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(-90f));
        for (int i = 0; i < 2; i++) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(i * 90f));
            ps.translate(0, 0, 0.0);
            Draw.xyAt(add(buf, SPEAR), ps, hw, hh, 0f, WHITE, fade);
            ps.popPose();
        }
        ps.popPose();

        // glow at the spearhead
        ps.pushPose();
        ps.translate(dir.x * hw * 0.55, dir.y * hw * 0.55, dir.z * hw * 0.55);
        billboard(ps);
        float g = 0.7f + 0.5f * f + 0.1f * Mth.sin((e.tickCount + pt) * 1.2f);
        Draw.xy(add(buf, GLOW), ps, g, g, GOLD, 0.9f * fade);
        Draw.xy(add(buf, STAR), ps, g * 1.4f, g * 1.4f, WHITE, 0.8f * fade);
        ps.popPose();
    }
}
