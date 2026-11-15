package com.example.jjkspells.frieren.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Frieren's render types. Extends RenderType only to reach the protected render-state shards.
 * One additive, fullbright, depth-tested (but not depth-writing) type per texture. Frieren's magic is all light, so no
 * alpha-blended family is needed yet.
 */
public final class ZolRenderTypes extends RenderType {
    private ZolRenderTypes() {
        super("jjk_frieren", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false, () -> {
        }, () -> {
        });
    }

    private static final Map<ResourceLocation, RenderType> CACHE = new HashMap<>();

    private static final TransparencyStateShard ADDITIVE = new TransparencyStateShard("jjk_frieren_additive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    /** Additive, fullbright, no culling, bilinear filtered. */
    public static RenderType additive(ResourceLocation texture) {
        return CACHE.computeIfAbsent(texture, tex -> create("jjk_frieren_add_" + tex.getPath(),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 2048, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new TextureStateShard(tex, true, false))
                        .setTransparencyState(ADDITIVE)
                        .setCullState(NO_CULL)
                        .setLightmapState(NO_LIGHTMAP)
                        .setWriteMaskState(COLOR_WRITE)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .createCompositeState(false)));
    }
}
