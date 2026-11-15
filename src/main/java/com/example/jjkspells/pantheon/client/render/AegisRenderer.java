package com.example.jjkspells.pantheon.client.render;

import com.example.jjkspells.pantheon.entity.AegisEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.pantheon.client.render.Gfx.*;

/** Aegis Storm: the golden energy shield in front of the caster, its cracks as it overloads, and the detonation sweep. */
public class AegisRenderer extends EntityRenderer<AegisEntity> {
    public AegisRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(AegisEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(AegisEntity e) {
        return SHIELD;
    }

    @Override
    public void render(AegisEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        Entity owner = e.getOwnerEntity();
        int dur = e.getDuration(), fin = e.getDetonationAt();
        Vec3 base = e.getPosition(pt);
        float form = smooth(t / 8f);
        float over = c01((t - (dur - 10)) / 10f);
        float charge = e.getCharge();

        if (owner != null && (fin < 0 || t < fin + 4)) {
            Vec3 op = owner.getPosition(pt).subtract(base);
            float yaw = owner.getViewYRot(pt);
            float alpha = form * (fin < 0 ? 1f : 1f - c01((t - fin) / 4f));
            float flick = 0.88f + 0.12f * Mth.sin(t * 0.9f) + over * 0.25f * Mth.sin(t * 3.1f);
            ps.pushPose();
            ps.translate(op.x, op.y + 1.05, op.z);
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.translate(0, 0, 0.95 - over * 0.25);
            float s = 1.1f + 0.15f * form;
            ps.pushPose();
            ps.translate(0, 0, -0.04f);
            Draw.xy(add(buf, GLOW), ps, s * 1.7f, s * 1.7f, AMBER, 0.55f * alpha * flick);
            ps.popPose();
            Draw.xy(add(buf, SHIELD), ps, s, s, WHITE, alpha * flick);
            ps.pushPose();
            ps.translate(0, 0, 0.03f);
            Draw.xy(add(buf, SHIELD_CRACKS), ps, s, s, WARM, alpha * c01(over * 1.2f + charge * 0.5f));
            ps.popPose();
            ps.popPose();
        }

        if (fin >= 0 && t >= fin) {
            float b = t - fin;
            Vec3 op = owner != null ? owner.getPosition(pt).subtract(base) : Vec3.ZERO;
            float yaw = owner != null ? owner.getViewYRot(pt) : 0f;
            float k = c01(b / 7f), range = e.getRange();
            // the golden semicircle roaring outward
            for (int i = 0; i < 3; i++) {
                float kk = c01((b - i * 1.5f) / 7f);
                if (kk <= 0f) {
                    continue;
                }
                float r = range * (0.25f + 0.85f * smooth(kk));
                ps.pushPose();
                ps.translate(op.x, op.y + 0.8, op.z);
                ps.mulPose(Axis.YP.rotationDegrees(-yaw));
                ps.mulPose(Axis.XP.rotationDegrees(90f));
                Draw.xyAt(add(buf, SHIELD_ARC), ps, r * 1.15f, r * 0.62f, r * 0.42f, i == 0 ? WHITE : GOLD, (1f - kk) * (1f - 0.25f * i));
                ps.popPose();
            }
            // flames lingering along the ground
            float glow = c01(b / 3f) * (1f - c01((b - 10f) / (AegisEntity.AFTERGLOW - 12f)));
            if (glow > 0.01f) {
                ps.pushPose();
                ps.translate(op.x, op.y, op.z);
                Draw.disc(add(buf, CRACKS), ps, range * 0.9f, yaw, 0.06f, GOLD, 0.8f * glow);
                Draw.disc(add(buf, GLOW), ps, range * 0.8f, 0f, 0.05f, AMBER, 0.35f * glow);
                ps.popPose();
            }
        }
    }
}
