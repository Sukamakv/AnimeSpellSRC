package com.example.jjkspells.frieren.client;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Frieren's render types (own copy). Everything here is light/energy: additive, fullbright, no depth write. Format POSITION_TEX_COLOR. */
public abstract class VolRenderTypes extends RenderType {
    private VolRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean a, boolean b, Runnable r1, Runnable r2) {
        super(n, f, m, s, a, b, r1, r2);
    }

    private static final String DIR = "textures/entity/frieren/";
    public static final ResourceLocation CIRCLE_A = tex("circle_a");
    public static final ResourceLocation CIRCLE_B = tex("circle_b");
    public static final ResourceLocation CIRCLE_C = tex("circle_c");
    public static final ResourceLocation LANCE = tex("lance");
    public static final ResourceLocation SPHERE = tex("sphere");
    public static final ResourceLocation SWIRL = tex("swirl");
    public static final ResourceLocation STREAKS = tex("streaks");
    public static final ResourceLocation BURST = tex("burst");
    public static final ResourceLocation SHOCK = tex("shock_ring");
    public static final ResourceLocation MOTE = tex("mote");
    public static final ResourceLocation STARS = tex("stars");
    // Vollzanbel (fire)
    public static final ResourceLocation FIRE_CIRCLE_A = tex("fire_circle_a");
    public static final ResourceLocation FIRE_CIRCLE_B = tex("fire_circle_b");
    public static final ResourceLocation FIRE_CIRCLE_C = tex("fire_circle_c");
    public static final ResourceLocation FLAME_PUFF = tex("flame_puff");
    public static final ResourceLocation FLAME_BEAM = tex("flame_beam");
    public static final ResourceLocation FIRE_SWIRL = tex("fire_swirl");
    public static final ResourceLocation FIRE_STREAKS = tex("fire_streaks");
    public static final ResourceLocation FIRE_BURST = tex("fire_burst");
    public static final ResourceLocation FIRE_SHOCK = tex("fire_shock");
    public static final ResourceLocation GLOW = tex("glow");
    public static final ResourceLocation EMBER = tex("ember");
    public static final ResourceLocation GROUND_FIRE = tex("ground_fire");
    // alpha-blended (matter): smoke, debris, scorch, flat tint
    public static final ResourceLocation SMOKE = tex("smoke");
    public static final ResourceLocation DEBRIS = tex("debris");
    public static final ResourceLocation SCORCH = tex("scorch");
    public static final ResourceLocation FLAT = tex("flat");

    private static final RenderStateShard.ShaderStateShard SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);
    private static final Map<ResourceLocation, RenderType> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> CACHE_ALPHA = new HashMap<>();

    @SuppressWarnings("removal")
    private static ResourceLocation tex(String name) {
        return new ResourceLocation(JJKSpells.MODID, DIR + name + ".png");
    }

    public static RenderType additive(ResourceLocation texture) {
        return CACHE.computeIfAbsent(texture, k -> RenderType.create("jjk_frieren_add_" + texture.getPath(),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 16384, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }

    /** Normal alpha blending (non additive): smoke, soot, debris, scorch marks, screen tint. Never fake darkness with additive. */
    public static RenderType alpha(ResourceLocation texture) {
        return CACHE_ALPHA.computeIfAbsent(texture, k -> RenderType.create("jjk_frieren_alpha_" + texture.getPath(),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 16384, false, false,
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
