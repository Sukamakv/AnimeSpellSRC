package com.example.jjkspells.garou.client;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Garou's render types (own copy, no dependency on other characters).
 *  - additive(): fullbright light/energy (stars, streaks, motes, halo, shimmer)
 *  - alpha():    normal alpha blending for matter-like effects (the dark purple waves) - never fake darkness with additive
 * Vertex format is POSITION_TEX_COLOR (see Pen).
 */
public abstract class RadRenderTypes extends RenderType {
    private RadRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean a, boolean b, Runnable r1, Runnable r2) {
        super(n, f, m, s, a, b, r1, r2);
    }

    private static final String DIR = "textures/entity/garou/";
    public static final ResourceLocation STREAKS = tex("cosmic_aura_streaks");
    public static final ResourceLocation STARS = tex("cosmic_stars");
    public static final ResourceLocation WAVE = tex("cosmic_wave");
    public static final ResourceLocation RING = tex("cosmic_ring");
    public static final ResourceLocation MOTE = tex("cosmic_mote");
    public static final ResourceLocation DISTORT = tex("cosmic_distort");
    public static final ResourceLocation HALO = tex("cosmic_halo");

    private static final RenderStateShard.ShaderStateShard SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);
    private static final Map<String, RenderType> CACHE = new HashMap<>();

    @SuppressWarnings("removal")
    private static ResourceLocation tex(String name) {
        return new ResourceLocation(JJKSpells.MODID, DIR + name + ".png");
    }

    public static RenderType additive(ResourceLocation texture) {
        return CACHE.computeIfAbsent("add:" + texture, k -> RenderType.create("jjk_garou_add_" + texture.getPath(),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 8192, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }

    public static RenderType alpha(ResourceLocation texture) {
        return CACHE.computeIfAbsent("alpha:" + texture, k -> RenderType.create("jjk_garou_alpha_" + texture.getPath(),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 8192, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }
}
