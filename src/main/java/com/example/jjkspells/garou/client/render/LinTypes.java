package com.example.jjkspells.garou.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * The same layers as RenderType.eyes (additive, glowing) and RenderType.entityTranslucent, but with LINEAR texture
 * filtering. The vanilla ones sample with NEAREST, so a big soft glow or smoke sprite (tens of blocks wide) shows its
 * texels as visible blocky squares. Extends RenderType only to reach its protected state shards.
 */
public abstract class LinTypes extends RenderType {
    private LinTypes(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean a, boolean b, Runnable r1, Runnable r2) {
        super(n, f, m, s, a, b, r1, r2);
        throw new IllegalStateException("not instantiable");
    }

    private static final Function<ResourceLocation, RenderType> ADD = Util.memoize(tex -> create("jjk_garou_lin_add",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, false))
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    private static final Function<ResourceLocation, RenderType> TRANS = Util.memoize(tex -> create("jjk_garou_lin_trans",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(tex, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)));

    public static RenderType additive(ResourceLocation tex) {
        return ADD.apply(tex);
    }

    public static RenderType translucent(ResourceLocation tex) {
        return TRANS.apply(tex);
    }
}
