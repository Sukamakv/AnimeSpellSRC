package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.gil.entity.VimanaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.client.render.FxDraw.FULL_BRIGHT;
import static com.example.jjkspells.client.render.FxDraw.c;
import static com.example.jjkspells.client.render.FxDraw.ease;

/** The Vimana: a cream-gold hull, folding green latticed wings, a red gem and a cannon that charges at the bow. */
public class VimanaRenderer extends EntityRenderer<VimanaEntity> {
    private static final ResourceLocation HULL = JJKSpells.id("textures/entity/vimana_hull.png");
    private static final ResourceLocation WING = JJKSpells.id("textures/entity/vimana_wing.png");
    private static final ResourceLocation GLOW = JJKSpells.id("textures/entity/fx_glow.png");

    public VimanaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(VimanaEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(VimanaEntity e) {
        return HULL;
    }

    @Override
    public void render(VimanaEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float a = e.tickCount + pt;
        float unfold = ease(e.unfold());
        float charge = Mth.clamp(e.charge(), 0f, 1f);
        boolean stag = e.staggerTicks() > 0;
        ps.pushPose();
        if (stag) ps.translate(Mth.sin(a * 2.7f) * 0.08, Mth.sin(a * 3.9f) * 0.06, Mth.cos(a * 3.1f) * 0.08);
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
        ps.translate(0, 0.2, 0);

        VertexConsumer hull = buf.getBuffer(RenderType.entityCutoutNoCull(HULL));
        PoseStack.Pose pose = ps.last();
        // main body, a long flattened ellipsoid with the bow along +Z
        FxDraw.ellipsoid(pose, hull, 0.95f, 0.42f, 2.7f, 10, 20, 3f, 2f, 0f, 0f, 255, 255, 255, 255, FULL_BRIGHT);
        // the keel and a bow point
        ps.pushPose();
        ps.translate(0, -0.25, 2.2);
        FxDraw.ellipsoid(ps.last(), hull, 0.28f, 0.22f, 1.1f, 8, 12, 1f, 1f, 0f, 0f, 255, 220, 140, 255, FULL_BRIGHT);
        ps.popPose();
        // deck rail
        ps.pushPose();
        ps.translate(0, 0.32, -0.3);
        FxDraw.ellipsoid(ps.last(), hull, 0.7f, 0.12f, 1.8f, 6, 12, 2f, 1f, 0f, 0f, 255, 235, 180, 255, FULL_BRIGHT);
        ps.popPose();

        // wings: translucent green lattice in gold frames, folding out from the hull
        if (unfold > 0.02f) {
            VertexConsumer wing = buf.getBuffer(RenderType.entityTranslucent(WING));
            double span = 3.6 * unfold;
            double sweep = 0.9;
            for (int side = -1; side <= 1; side += 2) {
                Vec3 o = new Vec3(side * (0.7 + span * 0.5), 0.12 + 0.12 * (1 - unfold), -0.4 - sweep * 0.4);
                Vec3 u = new Vec3(side * span * 0.5, 0.15 * (1 - unfold) + Math.sin(a * 0.12) * 0.04 * unfold, 0.0);
                Vec3 v = new Vec3(0, 0, 1.8);
                GilDraw.quad(ps, wing, o, u, v, 255, 255, 255, c(235f * unfold), FULL_BRIGHT);
                Vec3 o2 = new Vec3(side * (0.7 + span * 0.35), 0.2, -1.6);
                GilDraw.quad(ps, wing, o2, new Vec3(side * span * 0.32, 0.1, 0), new Vec3(0, 0, 0.9), 255, 255, 255, c(200f * unfold), FULL_BRIGHT);
            }
        }

        // red gem on the prow, the cannon glow gathering at the bow
        float pulse = 0.75f + 0.25f * Mth.sin(a * 0.3f);
        ps.pushPose();
        ps.translate(0, 0.28, 1.5);
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(), 0.35f * pulse, 0f, 255, 50, 60, 255, FULL_BRIGHT);
        ps.popPose();
        ps.pushPose();
        ps.translate(0, 0.0, 3.4);
        float cr = 0.3f + 2.2f * charge;
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(), cr, a * 9f, 255, 215, 110, c(40f + 215f * charge), FULL_BRIGHT);
        ps.popPose();
        // a soft engine glow at the stern
        ps.pushPose();
        ps.translate(0, 0.0, -2.8);
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(), 0.8f, 0f, 255, 200, 90, c(150f * pulse), FULL_BRIGHT);
        ps.popPose();
        ps.popPose();
    }
}
