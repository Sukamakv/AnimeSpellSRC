package com.example.jjkspells.overlord.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.overlord.client.model.BlackGoatModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Renders the big Black Goat (scale 1) and its Young (scale ~0.38) with the same model. */
public class GoatRenderer<T extends Mob> extends MobRenderer<T, BlackGoatModel<T>> {
    private static final ResourceLocation TEX = JJKSpells.id("textures/entity/black_goat.png");
    private static final ResourceLocation EYES = JJKSpells.id("textures/entity/black_goat_eyes.png");
    private final float scale;

    public GoatRenderer(EntityRendererProvider.Context ctx, float scale) {
        super(ctx, new BlackGoatModel<>(ctx.bakeLayer(BlackGoatModel.LAYER)), 1.6f * scale);
        this.scale = scale;
        this.addLayer(new EyesLayer<T, BlackGoatModel<T>>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(EYES);
            }
        });
    }

    @Override
    protected void scale(T e, PoseStack ps, float partialTick) {
        ps.scale(scale, scale, scale);
    }

    @Override
    public ResourceLocation getTextureLocation(T e) {
        return TEX;
    }
}
