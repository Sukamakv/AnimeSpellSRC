package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.JJKSpells;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Render types for the boss VFX: additive and alpha-blended, both POSITION_COLOR_TEX, no culling, no depth write.
 * Subclassing RenderType is the usual way to reach its protected state shards.
 */
public final class FxTypes extends RenderType {
    private FxTypes() {
        super("irontomb_dummy", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256, false, false, () -> { }, () -> { });
    }

    private static final RenderStateShard.TransparencyStateShard ADD = new RenderStateShard.TransparencyStateShard("irontomb_add",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            },
            () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final RenderStateShard.TransparencyStateShard ALPHA = new RenderStateShard.TransparencyStateShard("irontomb_alpha",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            },
            () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final Map<String, RenderType> CACHE = new HashMap<>();

    public static ResourceLocation tex(String name) {
        return new ResourceLocation(JJKSpells.MODID, "textures/irontomb/" + name + ".png");
    }

    private static RenderType make(String key, ResourceLocation tex, RenderStateShard.TransparencyStateShard tr, boolean depthWrite) {
        return CACHE.computeIfAbsent(key, k -> RenderType.create("irontomb_" + k, DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 4096, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorTexShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                        .setTransparencyState(tr)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(depthWrite ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                        .createCompositeState(false)));
    }

    /** Additive glow (colour * alpha added onto the frame). */
    public static RenderType add(String texture) {
        return make("add_" + texture, tex(texture), ADD, false);
    }

    /** Opaque-ish alpha textures that write depth: the iron body. */
    public static RenderType solid(String texture) {
        return make("solid_" + texture, tex(texture), ALPHA, true);
    }

    /** Normal alpha blending, used for darkening, the star dome and the planet. */
    public static RenderType alpha(String texture) {
        return make("alpha_" + texture, tex(texture), ALPHA, false);
    }
}
