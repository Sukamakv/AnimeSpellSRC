package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.entity.MeteorEntity;
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
 * Draws a Tengai Shinsei meteor: a lumpy grey boulder whose underside glows white-orange while it plunges, trailing a
 * long comet tail of fire and smoke. After it lands the glow cools off. The boulder is a real mesh (MeteorMesh),
 * not a sprite, so it has a proper silhouette and is lit by the world.
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {
    private static final ResourceLocation[] ROCK = {
            JJKSpells.id("textures/entity/meteor_rock_0.png"),
            JJKSpells.id("textures/entity/meteor_rock_1.png")};
    private static final ResourceLocation FIRE = JJKSpells.id("textures/entity/fx_fire.png");
    private static final ResourceLocation SMOKE = JJKSpells.id("textures/entity/fx_smoke.png");
    private static final ResourceLocation GLOW = JJKSpells.id("textures/entity/fx_glow.png");
    private static final ResourceLocation TAIL = JJKSpells.id("textures/entity/meteor_tail.png");

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    /** The boulder is far bigger than its hitbox and often far away or high up: never cull it. */
    @Override
    public boolean shouldRender(MeteorEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEntity entity) {
        return ROCK[0];
    }

    @Override
    public void render(MeteorEntity meteor, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffer, int packedLight) {
        float age = meteor.tickCount + partialTick;
        float R = meteor.getRadius();
        double y = Mth.lerp(partialTick, meteor.yo, meteor.getY());
        boolean falling = meteor.isFalling();
        float fall = falling ? meteor.getFallFraction(y) : 1f;

        // how hot the underside is: rises as it plunges, cools after landing
        float heat;
        if (falling) {
            heat = 0.30f + 0.70f * fall;
        } else {
            heat = Mth.clamp(1f - (meteor.landedTicks + partialTick) / 160f, 0f, 1f) * 0.85f;
        }

        int baseBlock = Math.max(LightTexture.block(packedLight), 7);

        // ---- the boulder ----------------------------------------------------------------------------------
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(age * 0.35f + meteor.getVariant() * 137f));
        ps.scale(R, R, R);
        drawBoulder(ps, buffer.getBuffer(RenderType.entityCutoutNoCull(ROCK[Math.floorMod(meteor.getVariant(), ROCK.length)])),
                MeteorMesh.get(meteor.getVariant()), heat, baseBlock);
        ps.popPose();

        // ---- glow around it ------------------------------------------------------------------------------------
        int glowA = FxDraw.c(200 * heat);
        if (glowA > 0) {
            FxDraw.billboard(ps, buffer.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(),
                    R * (1.9f + 0.25f * Mth.sin(age * 0.5f)), age * 2f, 255, 150, 70, glowA, FxDraw.FULL_BRIGHT);
        }

        // ---- the comet tail ----------------------------------------------------------------------------------------
        if (falling) {
            drawTail(ps, buffer, R, fall, age);
        }

        super.render(meteor, yaw, partialTick, ps, buffer, packedLight);
    }

    private static void drawBoulder(PoseStack ps, VertexConsumer vc, MeteorMesh mesh, float heat, int baseBlock) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float[] pos = mesh.pos, nrm = mesh.nrm, uv = mesh.uv, patch = mesh.patch;
        for (int t = 0; t < mesh.triangles; t++) {
            for (int k = 0; k < 4; k++) {
                int c = k == 3 ? 2 : k; // triangle -> degenerate quad
                int i = t * 3 + c;
                float ny = nrm[i * 3 + 1];
                // glow gathers on the side that faces the way it is moving (down), in uneven patches
                float h = Mth.clamp((0.30f - ny) * 1.0f, 0f, 1f) * (0.55f + 0.9f * patch[i]) * heat;
                h = Mth.clamp(h, 0f, 1f);
                int r = 255;
                int g = FxDraw.c(255f - h * 100f);
                int b = FxDraw.c(255f - h * 190f);
                int block = Mth.clamp(baseBlock + (int) (h * (15 - baseBlock)), 0, 15);
                vc.vertex(m, pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2])
                        .color(r, g, b, 255)
                        .uv(uv[i * 2], uv[i * 2 + 1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.pack(block, 15))
                        .normal(n, nrm[i * 3], ny, nrm[i * 3 + 2])
                        .endVertex();
            }
        }
    }

    private void drawTail(PoseStack ps, MultiBufferSource buffer, float R, float fall, float age) {
        float length = R * 20f;
        float strength = 0.35f + 0.65f * fall;
        float scroll = -age * 0.025f;
        PoseStack.Pose pose = ps.last();

        // soft dark smoke streaming up behind the fire
        FxDraw.tube(pose, buffer.getBuffer(com.example.jjkspells.client.SoftRender.entity(SMOKE)), R * 0.3f, length * 1.15f,
                t -> R * (1.15f + 0.9f * t), 24, 10, 4f, 6f, age * 0.002f, scroll * 0.6f,
                120, 108, 100, 165f * strength, 0f, LightTexture.pack(9, 15));

        // fire tube
        FxDraw.tube(pose, buffer.getBuffer(RenderType.eyes(FIRE)), R * 0.25f, length, t -> R * (1.0f + 0.65f * t), 24, 10,
                3f, 5f, age * 0.003f, scroll, 255, 175, 90, 230f * strength, 0f, FxDraw.FULL_BRIGHT);

        // bright streak down the middle: three ribbons so it never turns edge-on
        VertexConsumer vc = buffer.getBuffer(RenderType.eyes(TAIL));
        int aBottom = FxDraw.c(255f * strength);
        for (int k = 0; k < 3; k++) {
            float a = (float) (Math.PI * k / 3.0) + age * 0.01f;
            FxDraw.strip(pose, vc, Mth.cos(a), Mth.sin(a), R * 1.05f, R * 1.7f, R * 0.2f, length * 0.9f, 0f, 1f,
                    255, 215, 150, aBottom, 0, FxDraw.FULL_BRIGHT);
        }
    }
}
