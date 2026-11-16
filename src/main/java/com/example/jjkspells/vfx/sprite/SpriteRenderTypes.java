package com.example.jjkspells.vfx.sprite;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Render types for sprite effects: particle shader (position, uv, colour, lightmap), linear filtering so frames stay smooth,
 * no depth writes. One render type per texture, blend and depth mode, so all quads of one sheet go out in one draw call.
 * Client only. Extends RenderType only to reach the protected state shards.
 */
public final class SpriteRenderTypes extends RenderType {
    private SpriteRenderTypes() {
        super("jjk_sprite_unused", DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {});
        throw new UnsupportedOperationException("not instantiable");
    }

    private record Key(ResourceLocation tex, boolean additive, boolean ignoreDepth) {}

    private static final Map<Key, RenderType> CACHE = new HashMap<>();

    public static RenderType get(SpriteEffectDef d) {
        return CACHE.computeIfAbsent(new Key(d.texture, d.blend == SpriteEffectDef.Blend.ADDITIVE, d.ignoreDepth), k -> {
            CompositeState state = CompositeState.builder()
                    .setShaderState(new ShaderStateShard(GameRenderer::getParticleShader))
                    .setTextureState(new TextureStateShard(k.tex(), true, false))
                    .setTransparencyState(k.additive() ? ADDITIVE_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setDepthTestState(k.ignoreDepth() ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST)
                    .createCompositeState(false);
            return RenderType.create("jjk_sprite_" + k.tex().getPath().replace('/', '_') + (k.additive() ? "_add" : "_alpha")
                            + (k.ignoreDepth() ? "_nodepth" : ""),
                    DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 1536, false, false, state);
        });
    }
}
