package com.example.jjkspells.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Render types for smoke and mist: ordinary translucent blending but WITHOUT writing depth, so a puff of smoke never
 * hides the effects and entities that are drawn after it or stand behind it. Particles use {@link #PARTICLE},
 * entity-style meshes use {@link #entity(ResourceLocation)}.
 */
public final class SoftRender extends RenderType {
    private SoftRender(String name, VertexFormat format, VertexFormat.Mode mode, int buffer, boolean affectsCrumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, buffer, affectsCrumbling, sort, setup, clear);
    }

    /** Translucent particle sheet that does not write depth. */
    public static final ParticleRenderType PARTICLE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder buffer, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "jjk_spells:soft_particle";
        }
    };

    private static final Function<ResourceLocation, RenderType> ENTITY = Util.memoize(tex -> create("jjk_soft_entity",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(tex, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    /** Same as RenderType.entityTranslucent(tex) but without depth writes. */
    public static RenderType entity(ResourceLocation tex) {
        return ENTITY.apply(tex);
    }
}
