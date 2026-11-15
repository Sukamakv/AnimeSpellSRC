package com.example.jjkspells.prime.fx.client;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Fullbright render types for the PRIME PNG animations: additive (light) and normal alpha (the black and white slash). */
public final class PrimeRender extends RenderType {
    public static final ResourceLocation SLASH = tex("slash");
    public static final ResourceLocation CSLASH = tex("cslash");
    public static final ResourceLocation SWIRL = tex("swirl");
    public static final ResourceLocation ACCRETION = tex("accretion");
    public static final ResourceLocation HOLE = tex("hole");
    public static final ResourceLocation BEAM = tex("beam");
    public static final ResourceLocation CUTLINE = tex("cutline");
    public static final ResourceLocation ARROW = tex("arrow");
    public static final ResourceLocation DISTORT = tex("distort");
    public static final ResourceLocation PILLAR = tex("pillar");
    public static final ResourceLocation TONGUES = tex("tongues");
    public static final ResourceLocation TEXT = tex("dismantle");
    /** Shared with the Yhwach pack. */
    public static final ResourceLocation GLOW = new ResourceLocation(JJKSpells.MODID, "textures/entity/yhwach/glow.png");
    public static final ResourceLocation SPARKLE = new ResourceLocation(JJKSpells.MODID, "textures/entity/yhwach/sparkle.png");
    public static final ResourceLocation RING = new ResourceLocation(JJKSpells.MODID, "textures/entity/yhwach/ring.png");
    public static final ResourceLocation BURST = new ResourceLocation(JJKSpells.MODID, "textures/entity/yhwach/burst.png");

    private static final Map<ResourceLocation, RenderType> ADD = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> BLEND = new HashMap<>();

    private PrimeRender(String name, VertexFormat format, VertexFormat.Mode mode, int buffer, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, buffer, crumbling, sort, setup, clear);
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(JJKSpells.MODID, "textures/entity/prime/" + name + ".png");
    }

    public static RenderType additive(ResourceLocation texture) {
        return ADD.computeIfAbsent(texture, t -> RenderType.create("prime_add_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 16384, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(t, true, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }

    public static RenderType blend(ResourceLocation texture) {
        return BLEND.computeIfAbsent(texture, t -> RenderType.create("prime_blend_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 16384, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(t, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }
}
