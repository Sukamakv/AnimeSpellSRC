package com.example.jjkspells.pantheon.client.render;

import com.example.jjkspells.pantheon.entity.VaultEntity;
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

/** Shield Vault: the golden mark under the target, the collision shockwave, and the shield bash crescent. */
public class VaultRenderer extends EntityRenderer<VaultEntity> {
    public VaultRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(VaultEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(VaultEntity e) {
        return GLOW;
    }

    @Override
    public void render(VaultEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        Vec3 base = e.getPosition(pt);
        Entity target = e.level().getEntity(e.getTargetId());
        Entity owner = e.getOwnerEntity();
        int coll = e.getCollisionAt(), bash = e.getBashAt(), lock = Math.max(1, e.getLockTicks());
        boolean perfect = e.isPerfect();

        // the mark under the target
        if (target != null && (coll < 0 || t < coll + 10)) {
            Vec3 tp = target.getPosition(pt).subtract(base);
            float k = coll < 0 ? 1f : 1f - c01((t - coll) / 10f);
            float a = c01(t / 3f) * k;
            float r = Mth.lerp(c01(t / lock), 2.8f, 1.5f);
            ps.pushPose();
            ps.translate(tp.x, tp.y, tp.z);
            Draw.disc(add(buf, TARGET), ps, r, t * 5f, 0.05f, perfect ? WARM : GOLD, a);
            Draw.disc(add(buf, CIRCLE), ps, r * 0.7f, -t * 7f, 0.06f, GOLD, 0.7f * a);
            Draw.disc(add(buf, GLOW), ps, r * 1.2f, 0f, 0.04f, AMBER, 0.45f * a);
            ps.popPose();
        }

        // the shield collision shockwave
        if (coll >= 0 && t >= coll && target != null) {
            float k = (t - coll) / 9f;
            if (k < 1f) {
                Vec3 tp = target.getPosition(pt).subtract(base);
                ps.pushPose();
                ps.translate(tp.x, tp.y, tp.z);
                float r = 3.2f * (0.15f + 0.85f * smooth(k));
                Draw.disc(add(buf, WAVE), ps, r + 0.3f, t * 4f, 0.1f, GOLD, (1f - k) * 0.85f);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 0.7f), r + 0.5f, 0.12f, 48, 1f, 0f, WARM, 1f - k);
                ps.popPose();
            }
        }

        // the bash: a crescent sweeping along the shield's path
        if (bash >= 0 && owner != null && t >= bash - 2 && t <= bash + 8) {
            float p = c01((t - (bash - 2)) / 5f);
            Vec3 op = owner.getPosition(pt).subtract(base);
            float face = owner.getViewYRot(pt);
            if (target != null) {
                Vec3 d = target.getPosition(pt).subtract(owner.getPosition(pt));
                if (d.x * d.x + d.z * d.z > 1.0E-4) {
                    face = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
                }
            }
            float alpha = (float) Math.sin(Math.PI * Mth.clamp((t - (bash - 2)) / 9f, 0f, 1f));
            for (int i = 0; i < 4; i++) {
                float pp = c01(p - i * 0.1f);
                float ang = face + (pp - 0.5f) * 150f;
                ps.pushPose();
                ps.translate(op.x, op.y + 1.0, op.z);
                ps.mulPose(Axis.YP.rotationDegrees(-ang));
                ps.mulPose(Axis.XP.rotationDegrees(90f));
                float s = 1f - 0.07f * i;
                Draw.xyAt(add(buf, SHIELD_ARC), ps, 3.4f * s, 1.7f * s, 1.3f, i == 0 ? WHITE : GOLD, alpha * (1f - 0.25f * i));
                ps.popPose();
            }
            ps.pushPose();
            ps.translate(op.x, op.y + 1.0, op.z);
            Draw.disc(add(buf, GLOW), ps, 4.0f, 0f, 0f, AMBER, 0.3f * alpha);
            ps.popPose();
            if (perfect && t >= bash) {
                float k = c01((t - bash) / 8f);
                ps.pushPose();
                ps.translate(op.x, op.y, op.z);
                Draw.disc(add(buf, WAVE), ps, 5.2f * k + 0.3f, 0f, 0.1f, GOLD, (1f - k) * 0.7f);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, 5.2f * k - 0.6f), 5.2f * k + 0.5f, 0.12f, 48, 1f, 0f, WARM, 1f - k);
                ps.popPose();
            }
        }
    }
}
