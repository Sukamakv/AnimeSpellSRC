package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.GateWeaponEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a Gate of Babylon weapon: its sprite on two crossed planes so it reads from every side, glowing gold, spinning
 * about its own length while it forms in the portal, with a soft golden halo.
 */
public class GateWeaponRenderer extends EntityRenderer<GateWeaponEntity> {
    private static final ResourceLocation[] TEX = new ResourceLocation[GateWeaponEntity.TYPES];
    private static final ResourceLocation GLOW = JJKSpells.id("textures/entity/fx_glow.png");
    private static final float LEN = 2.8f;       // sprite length in blocks
    private static final float HEIGHT = LEN / 4f; // 128x32 sprite

    static {
        for (int i = 0; i < TEX.length; i++) {
            TEX[i] = JJKSpells.id("textures/entity/gate_weapon_" + i + ".png");
        }
    }

    public GateWeaponRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(GateWeaponEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(GateWeaponEntity entity) {
        return TEX[Math.floorMod(entity.getWeaponType(), TEX.length)];
    }

    @Override
    public void render(GateWeaponEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float age = e.tickCount + pt;
        int state = e.getState();
        float grow = Mth.clamp(age / 6f, 0f, 1f);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pt, e.yRotO, e.getYRot()) - 90f));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
        // spins about its length until it is fired
        if (state == 0) {
            ps.mulPose(Axis.XP.rotationDegrees(age * 28f));
        }
        ps.scale(grow, grow, grow);

        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(e)));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float len = LEN * (e.isHeavy() ? 1.5f : 1f);
        float half = len / 4f / 2f;
        float x0 = -len * 0.35f, x1 = len * 0.65f; // the grip end sits behind the entity position, the point ahead of it
        int full = LightTexture.FULL_BRIGHT;
        // plane 1: XY
        quad(vc, m, n, x0, x1, half, 0f, full);
        // plane 2: XZ
        quad(vc, m, n, x0, x1, 0f, half, full);
        ps.popPose();

        // soft golden halo (additive; fetched only after the weapon is fully written)
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(),
                0.95f * grow, 0f, 255, 205, 90, FxDraw.c(150f * grow), FxDraw.FULL_BRIGHT);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float x1, float hy, float hz, int light) {
        // two-sided
        for (int side = 0; side < 2; side++) {
            float s = side == 0 ? 1f : -1f;
            float[][] c = {{x0, -hy, -hz, 0f, 1f}, {x1, -hy, -hz, 1f, 1f}, {x1, hy, hz, 1f, 0f}, {x0, hy, hz, 0f, 0f}};
            for (int k = 0; k < 4; k++) {
                float[] p = c[side == 0 ? k : 3 - k];
                vc.vertex(m, p[0], p[1], p[2]).color(255, 255, 255, 255).uv(p[3], p[4])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0f, s * (hz > 0 ? 0f : 1f), s * (hz > 0 ? 1f : 0f)).endVertex();
            }
        }
    }
}
