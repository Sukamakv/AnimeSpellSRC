package com.example.jjkspells.rimuru.client.render;

import com.example.jjkspells.rimuru.entity.PredatorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class PredatorRenderer extends DevourRenderer<PredatorEntity> {
    public PredatorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, Gfx.BLUE, Gfx.DEEP, false);
    }
}
