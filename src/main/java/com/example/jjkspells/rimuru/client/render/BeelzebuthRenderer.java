package com.example.jjkspells.rimuru.client.render;

import com.example.jjkspells.rimuru.entity.BeelzebuthEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class BeelzebuthRenderer extends DevourRenderer<BeelzebuthEntity> {
    public BeelzebuthRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, Gfx.VIOLET, Gfx.NIGHT, true);
    }
}
