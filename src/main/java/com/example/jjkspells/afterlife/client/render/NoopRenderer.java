package com.example.jjkspells.afterlife.client.render;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** For controller entities that only emit particles. */
public class NoopRenderer<T extends Entity> extends EntityRenderer<T> {
    public NoopRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(T e, Frustum f, double x, double y, double z) {
        return false;
    }

    @Override
    public void render(T e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
    }

    @Override
    public ResourceLocation getTextureLocation(T e) {
        return JJKSpells.id("textures/environment/al_glow.png");
    }
}
