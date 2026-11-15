package com.example.jjkspells.garou.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Garou render types. Extends RenderType only to reach its protected state shards.
 *  - additive(tex): fullbright light/energy, no depth write, depth tested (so the terrain still hides it).
 *  - alpha(tex): ordinary alpha blending for solid / dark matter (the cosmic sphere). Never fake darkness with additive.
 * One render type per texture, cached.
 */
public final class GarouRenderTypes extends RenderType {
    private GarouRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean a, boolean s, Runnable st, Runnable cl) {
        super(n, f, m, b, a, s, st, cl);
        throw new IllegalStateException("not instantiable");
    }

    private static final Map<ResourceLocation, RenderType> ADD = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> ALPHA = new HashMap<>();

    public static RenderType additive(ResourceLocation tex) {
        return ADD.computeIfAbsent(tex, t -> create("garou_additive_" + t.getPath(),
                DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 4096, false, true,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorTexShader))
                        .setTextureState(new TextureStateShard(t, true, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }

    public static RenderType alpha(ResourceLocation tex) {
        return ALPHA.computeIfAbsent(tex, t -> create("garou_alpha_" + t.getPath(),
                DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 4096, false, true,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorTexShader))
                        .setTextureState(new TextureStateShard(t, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }
}
