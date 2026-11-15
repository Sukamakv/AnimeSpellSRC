package com.example.jjkspells.yhwach.client;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Fullbright, additive, depth-tested (but not depth-writing) render types for the Quincy light effects. */
public final class YhwachRender extends RenderType {
    public static final ResourceLocation GLOW = tex("glow");
    public static final ResourceLocation STREAK = tex("streak");
    public static final ResourceLocation SPARKLE = tex("sparkle");
    public static final ResourceLocation RING = tex("ring");
    public static final ResourceLocation SEAL = tex("seal");
    public static final ResourceLocation BURST = tex("burst");
    public static final ResourceLocation INS_BASE = tex("ins_base");
    public static final ResourceLocation INS_OUTER = tex("ins_outer");
    public static final ResourceLocation INS_MID = tex("ins_mid");
    public static final ResourceLocation INS_CORE = tex("ins_core");
    public static final ResourceLocation WHEEL = tex("wheel");
    public static final ResourceLocation CROSS = tex("cross");
    public static final ResourceLocation HEX = tex("hex");
    public static final ResourceLocation VSWORD = tex("vsword");
    public static final ResourceLocation VSTAR = tex("vstar");
    public static final ResourceLocation GLYPH = tex("glyph");
    public static final ResourceLocation INK_TILE = tex("ink_tile");
    public static final ResourceLocation INK_WISP = tex("ink_wisp");
    public static final ResourceLocation INK_FLOW = tex("ink_flow");
    public static final ResourceLocation EYE_BODY = tex("eye_body");
    public static final ResourceLocation EYE_PUPIL = tex("eye_pupil");

    private static final Map<ResourceLocation, RenderType> CACHE = new HashMap<>();

    private YhwachRender(String name, VertexFormat format, VertexFormat.Mode mode, int buffer, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, buffer, crumbling, sort, setup, clear);
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(JJKSpells.MODID, "textures/entity/yhwach/" + name + ".png");
    }

    private static final Map<ResourceLocation, RenderType> INK_CACHE = new HashMap<>();

    /** Ordinary alpha-blended (NOT additive) textured type: used for solid black ink, eyes and wisps. */
    public static RenderType ink(ResourceLocation texture) {
        return INK_CACHE.computeIfAbsent(texture, t -> RenderType.create("yhwach_ink_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 8192, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(t, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }

    private static RenderType dimType;

    /** Ordinary alpha-blended, untextured, depth-tested type used to darken the sky dome. */
    public static RenderType dim() {
        if (dimType == null) {
            dimType = RenderType.create("yhwach_dim", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }
        return dimType;
    }

    public static RenderType additive(ResourceLocation texture) {
        return CACHE.computeIfAbsent(texture, t -> RenderType.create("yhwach_additive_" + t.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 8192, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(t, true, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)));
    }
}
