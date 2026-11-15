package com.example.jjkspells.malphite.client.render;

import com.example.jjkspells.malphite.entity.StrikesEntity;
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

import static com.example.jjkspells.malphite.client.render.Gfx.*;

/** Brutal Strikes: growing stone plates and fists, the swing arcs of each blow, the shockwave of the third punch and the spikes of the Titanic Smash. */
public class StrikesRenderer extends EntityRenderer<StrikesEntity> {
    public StrikesRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(StrikesEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(StrikesEntity e) {
        return GLOW;
    }

    @Override
    public void render(StrikesEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        Entity owner = e.getOwnerEntity();
        float bodyYaw = owner != null ? owner.getViewYRot(pt) : 0f;
        int k = e.getBlow();
        float bt = k > 0 ? e.tickCount - 1 + pt - e.getBlowAge() : -1f;
        float blowYaw = e.getBlowYaw();
        int seed = e.getSeed();
        float range = e.getSmashRange();
        var c = com.example.jjkspells.malphite.config.MalConfig.STRIKES;
        float strikeR = (float) (double) c.strikeRadius.get();
        float coneDeg = (float) (double) c.coneDegrees.get();

        // reinforcement: stone plates and big fists on both arms until the combo ends
        float armor = c01(t / StrikesEntity.S1) * (1f - c01((t - StrikesEntity.END + 8f) / 8f));
        if (armor > 0.01f) {
            double yr = Math.toRadians(bodyYaw);
            Vec3 right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
            Vec3 fwd = new Vec3(-Math.sin(yr), 0, Math.cos(yr));
            boolean raised = t > StrikesEntity.S3 + 4 && t < StrikesEntity.SM + 2;
            for (int side = -1; side <= 1; side += 2) {
                Vec3 p = right.scale(0.55 * side).add(fwd.scale(raised ? 0.1 : 0.25)).add(0, raised ? 2.35 : 1.1, 0);
                ps.pushPose();
                ps.translate(p.x, p.y, p.z);
                billboard(ps);
                float s = (0.35f + 0.3f * armor) * (raised ? 1.25f : 1f);
                Draw.xy(trans(buf, FIST), ps, s, s, STONE, armor);
                Draw.xy(add(buf, GLOW), ps, s * 1.5f, s * 1.5f, ORANGE, 0.35f * armor * (raised ? 1.6f : 1f));
                ps.popPose();
                // shoulder plates
                Vec3 sp = right.scale(0.5 * side).add(0, 1.65, 0);
                ps.pushPose();
                ps.translate(sp.x, sp.y, sp.z);
                billboard(ps);
                Draw.xy(trans(buf, PLATE), ps, 0.38f * armor, 0.38f * armor, STONE, armor);
                ps.popPose();
            }
        }
        // the swing arcs of the first two blows
        if ((k == 1 || k == 2) && bt >= 0f && bt < 10f) {
            float sweep = smooth(bt / 6f);
            float dir = k == 1 ? 1f : -1f;
            float a = 1f - c01((bt - 3f) / 7f);
            ps.pushPose();
            ps.translate(0, 1.2f, 0);
            ps.mulPose(Axis.YP.rotationDegrees(-blowYaw + dir * (70f - 140f * sweep)));
            ps.mulPose(Axis.XP.rotationDegrees(90f));
            ps.translate(0, strikeR * 0.5f, 0);
            Draw.xyAt(trans(buf, ARC), ps, strikeR * 0.6f, strikeR * 0.5f, 0f, DUSTC, a);
            Draw.xyAt(add(buf, ARC), ps, strikeR * 0.6f, strikeR * 0.5f, 0f, ORANGE, 0.25f * a);
            ps.popPose();
        }
        // the third punch: a cone shockwave cracking the ground
        if (k >= 3 && bt >= 0f) {
            float b3 = k == 3 ? bt : t - StrikesEntity.S3;
            if (b3 >= 0f && b3 < 16f) {
                float kk = c01(b3 / 10f);
                float a = 1f - c01((b3 - 6f) / 10f);
                float sr = (float) (double) c.smashRange.get();
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(-blowYaw));
                ps.translate(0, 0, sr * 0.45f);
                Draw.disc(add(buf, CRACKS), ps, sr * 0.55f, 0f, 0.08f, ORANGE, 0.9f * a);
                Draw.disc(add(buf, WAVE), ps, sr * (0.2f + 0.45f * smooth(kk)), 0f, 0.1f, HOT, (1f - kk) * 0.7f);
                ps.popPose();
                ps.pushPose();
                ps.translate(0, 1.3f, 0);
                ps.mulPose(Axis.YP.rotationDegrees(-blowYaw));
                ps.mulPose(Axis.XP.rotationDegrees(90f));
                ps.translate(0, sr * (0.2f + 0.5f * smooth(kk)), 0);
                Draw.xyAt(trans(buf, ARC), ps, sr * 0.55f, sr * 0.3f, 0f, DUSTC, a);
                ps.popPose();
            }
        }
        // the Titanic Smash
        if (k == 4 && bt >= 0f) {
            ps.pushPose();
            int n = 18;
            float sa = 1f;
            for (int i = 0; i < n; i++) {
                double[] o = StrikesEntity.spikeOffset(i, n, seed, blowYaw, range, coneDeg);
                float h = 2.4f + 2.2f * (((seed + i * 11) % 10) / 10f);
                float life = life(bt, i * 0.25f, 3f, 16f, 12f);
                Gfx.spike(buf, ps, (float) o[0], (float) o[1], 0.55f + 0.3f * (i % 3), h, life, i * 47f, sa, light);
            }
            float kk = c01(bt / 10f);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-blowYaw));
            ps.translate(0, 0, range * 0.5f);
            Draw.disc(add(buf, CRACKS), ps, range * 0.6f, 25f, 0.09f, ORANGE, 0.9f * (1f - c01((bt - 10f) / 20f)));
            if (kk < 1f) {
                Draw.disc(add(buf, WAVE), ps, range * (0.2f + 0.5f * smooth(kk)), bt * 4f, 0.12f, HOT, 1f - kk);
            }
            ps.popPose();
            float cl = c01(bt / 3f) * (1f - c01((bt - 8f) / 22f));
            for (int i = 0; i < 6; i++) {
                double[] o = StrikesEntity.spikeOffset(i * 3, n, seed + 5, blowYaw, range, coneDeg);
                ps.pushPose();
                ps.translate(o[0], 0.8 + bt * 0.03, o[1]);
                billboard(ps);
                float s = 1.2f + bt * 0.09f;
                Draw.xy(trans(buf, MIST), ps, s, s, DUSTC, 0.85f * cl);
                ps.popPose();
            }
            ps.popPose();
        }
    }
}
