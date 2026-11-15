package com.example.jjkspells.yone.client;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Fullbright render types for the Yone effects: additive for fire and light lines, plain alpha for glass and the katana sprite. */
public final class YoneRender extends RenderType {
    public static final ResourceLocation LINE = tex("line");
    public static final ResourceLocation GLINT = tex("glint");
    public static final ResourceLocation GLOW = tex("glow");
    public static final ResourceLocation RING = tex("ring");
    public static final ResourceLocation SLASH = tex("slash");
    public static final ResourceLocation MIST = tex("mist");
    public static final ResourceLocation WIND = tex("wind");
    public static final ResourceLocation KATANA = tex("katana");
    public static final ResourceLocation KATANA_RED = tex("katana_red");
    public static final ResourceLocation MASK = tex("oni_mask");
    public static final ResourceLocation MOON = tex("moon");
    public static final ResourceLocation GHOST = tex("ghost");

    private static final Map<ResourceLocation, RenderType> ADD = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> ALPHA = new HashMap<>();

    private YoneRender(String name, VertexFormat format, VertexFormat.Mode mode, int buffer, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, buffer, crumbling, sort, setup, clear);
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(JJKSpells.MODID, "textures/entity/yone/" + name + ".png");
    }

    public static RenderType additive(ResourceLocation texture) {
        return ADD.computeIfAbsent(texture, t -> RenderType.create("yone_additive_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 8192, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(t, true, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }

    public static RenderType alpha(ResourceLocation texture) {
        return ALPHA.computeIfAbsent(texture, t -> RenderType.create("yone_alpha_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 8192, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(t, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }
}
