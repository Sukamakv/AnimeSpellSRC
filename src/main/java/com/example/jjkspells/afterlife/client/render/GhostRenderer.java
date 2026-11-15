package com.example.jjkspells.afterlife.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.afterlife.entity.SpiritGhostEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The summoned ghost: an animated camera-facing spirit (bobbing, tilting, mouth opening before the blast) with a crimson aura. */
public class GhostRenderer extends EntityRenderer<SpiritGhostEntity> {
    private static final ResourceLocation SMILE = JJKSpells.id("textures/environment/al_ghost_smile.png");
    private static final ResourceLocation OPEN = JJKSpells.id("textures/environment/al_ghost_open.png");
    private static final ResourceLocation GLOW = JJKSpells.id("textures/environment/al_glow.png");
    private static final float[] CRIMSON = {1.0f, 0.22f, 0.18f}, WHITE = {1f, 1f, 1f};

    public GhostRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(SpiritGhostEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritGhostEntity e) {
        return SMILE;
    }

    @Override
    public void render(SpiritGhostEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        float a = SpiritGhostEntity.alphaAt(t);
        if (a <= 0.01f) {
            return;
        }
        float s = SpiritGhostEntity.scaleAt(t);
        float bob = Mth.sin(t * 0.3f) * 0.08f;
        float tilt = Mth.sin(t * 0.22f) * 12f;
        boolean open = SpiritGhostEntity.mouthOpenAt(t);
        ps.pushPose();
        ps.translate(0, bob, 0);
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        ps.mulPose(Axis.ZP.rotationDegrees(tilt));
        ps.pushPose();
        ps.translate(0, 0, -0.02);
        Draw.xy(buf.getBuffer(RenderType.eyes(GLOW)), ps, 1.5f * s, 1.5f * s, CRIMSON, 0.6f * a);
        ps.popPose();
        Draw.xy(buf.getBuffer(RenderType.entityTranslucent(open ? OPEN : SMILE)), ps, 0.85f * s, 0.85f * s, WHITE, 0.92f * a);
        ps.popPose();
    }
}
