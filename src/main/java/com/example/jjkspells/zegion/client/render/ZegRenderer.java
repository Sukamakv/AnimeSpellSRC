package com.example.jjkspells.zegion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Common shell of the controller renderers: never culled, no shadow, one draw method with the age in ticks. */
abstract class ZegRenderer<T extends Entity> extends EntityRenderer<T> {
    protected ZegRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(T e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(T e) {
        return Gfx.GLOW;
    }

    @Override
    public void render(T e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        draw(e, t, pt, ps, buf, Gfx.base(e, pt));
    }

    /** ps is at the entity's position; base is that position in world space (for camera maths). */
    protected abstract void draw(T e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base);
}
