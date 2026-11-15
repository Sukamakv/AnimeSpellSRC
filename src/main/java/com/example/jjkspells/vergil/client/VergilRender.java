package com.example.jjkspells.vergil.client;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Fullbright render types for the Vergil effects: additive for light lines, plain alpha for glass and the rift. */
public final class VergilRender extends RenderType {
    public static final ResourceLocation LINE = tex("line");
    public static final ResourceLocation GLINT = tex("glint");
    public static final ResourceLocation GLOW = tex("glow");
    public static final ResourceLocation RING = tex("ring");
    public static final ResourceLocation SLASH = tex("slash");
    public static final ResourceLocation MIST = tex("mist");
    public static final ResourceLocation SHARD = tex("shard");
    public static final ResourceLocation RIFT = tex("rift");
    public static final ResourceLocation SWORD = tex("sword");
    public static final ResourceLocation DEMON = tex("demon");
    public static final ResourceLocation WAVE = tex("wave");

    private static final Map<ResourceLocation, RenderType> ADD = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> ALPHA = new HashMap<>();

    private VergilRender(String name, VertexFormat format, VertexFormat.Mode mode, int buffer, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, buffer, crumbling, sort, setup, clear);
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(JJKSpells.MODID, "textures/entity/vergil/" + name + ".png");
    }

    public static RenderType additive(ResourceLocation texture) {
        return ADD.computeIfAbsent(texture, t -> RenderType.create("vergil_additive_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 8192, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(t, true, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }

    private static RenderType dimType;

    /** Alpha-blended, untextured, depth-tested type used to darken the sky dome. */
    public static RenderType dim() {
        if (dimType == null) {
            dimType = RenderType.create("vergil_dim", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }
        return dimType;
    }

    public static RenderType alpha(ResourceLocation texture) {
        return ALPHA.computeIfAbsent(texture, t -> RenderType.create("vergil_alpha_" + t.getPath().replace('/', '_'),
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
