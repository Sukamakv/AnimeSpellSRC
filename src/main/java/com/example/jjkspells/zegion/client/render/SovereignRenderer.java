package com.example.jjkspells.zegion.client.render;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.entity.SovereignEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.zegion.client.render.Gfx.*;

/** Kaiser's Final Form: crystalline armor growing from the shoulders and back, two pairs of gigantic energy wings, rings behind him, melee shockwaves and the final detonation. */
public class SovereignRenderer extends ZegRenderer<SovereignEntity> {
    public SovereignRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(SovereignEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base) {
        float R = (float) (double) ZegConfig.FORM.radius.get();
        float FR = (float) (double) ZegConfig.FORM.finalRadius.get();
        int fold = e.getFoldAt();
        float yaw = 0f;
        Entity o = e.getOwnerEntity();
        Vec3 op = Vec3.ZERO;
        if (o instanceof LivingEntity le) {
            yaw = Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot);
            op = new Vec3(Mth.lerp(pt, le.xo, le.getX()), Mth.lerp(pt, le.yo, le.getY()), Mth.lerp(pt, le.zo, le.getZ())).subtract(base);
        }
        float armor = smooth((t - 4f) / (SovereignEntity.PLATES + 18f));
        float unfold = smooth((t - SovereignEntity.WINGS) / 16f);
        float foldK = fold >= 0 ? smooth((t - fold) / SovereignEntity.FOLD) : 0f;
        float open = unfold * (1f - foldK);
        float detT = fold >= 0 ? t - (fold + SovereignEntity.FOLD) : -1f;
        float vis = detT > 0f ? 1f - c01(detT / 14f) : 1f;
        float pulse = 0.8f + 0.2f * Mth.sin(t * 0.25f);
        float glow = (0.8f + 0.9f * foldK) * pulse;
        float[] tint = e.isOverdrive() || foldK > 0.5f ? PALE : VIOLET;

        // ---- the awakening: rings and rising black particles around his feet
        if (t < SovereignEntity.ACTIVE + 20) {
            float g = c01(t / 14f) * (1f - c01((t - SovereignEntity.ACTIVE) / 20f));
            ps.pushPose();
            ps.translate(op.x, 0, op.z);
            Draw.disc(add(buf, CIRCLE), ps, 3.6f * g + 0.3f, t * 5f, 0.06f, VIOLET, 0.8f * g);
            Draw.disc(add(buf, CIRCLE), ps, 2.4f * g + 0.2f, -t * 7f, 0.08f, PALE, 0.7f * g);
            Draw.disc(add(buf, CRACKS), ps, 4.4f * g, 21f, 0.05f, DEEP, 0.8f * g);
            ps.popPose();
        }

        if (o == null || vis <= 0.01f) {
            effects(e, t, ps, buf, base, R, FR, fold);
            return;
        }

        ps.pushPose();
        ps.translate(op.x, op.y, op.z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));

        // ---- exoskeletal armor: crystal plates on shoulders, arms and back
        if (armor > 0.01f) {
            for (int s = -1; s <= 1; s += 2) {
                for (int k = 0; k < 3; k++) {
                    ps.pushPose();
                    ps.translate(0.55f * s + 0.1f * s * k, 1.85f - 0.05f * k, 0.0f);
                    ps.mulPose(Axis.ZP.rotationDegrees(-s * (35f + 22f * k)));
                    ps.mulPose(Axis.YP.rotationDegrees(s * 20f));
                    shard(buf, ps, 0.45f - 0.07f * k, (0.9f + 0.4f * k) * armor, 0.95f * armor * vis);
                    ps.popPose();
                }
                // forearm blades
                ps.pushPose();
                ps.translate(0.55f * s, 1.0f, 0.1f);
                ps.mulPose(Axis.XP.rotationDegrees(-100f));
                shard(buf, ps, 0.3f, 1.0f * armor, 0.9f * armor * vis);
                ps.popPose();
            }
            // spine crystals
            for (int k = 0; k < 4; k++) {
                ps.pushPose();
                ps.translate((k - 1.5f) * 0.14f, 1.5f + 0.12f * k, -0.3f);
                ps.mulPose(Axis.XP.rotationDegrees(-55f - 8f * k));
                shard(buf, ps, 0.26f, (0.9f + 0.2f * k) * armor, 0.95f * armor * vis);
                ps.popPose();
            }
            // violet energy flowing between the plates
            ps.pushPose();
            ps.translate(0, 1.15f, 0);
            Gfx.billboard(ps);
            Draw.xy(add(buf, GLOW), ps, 1.5f * armor, 1.9f * armor, VIOLET, 0.35f * glow * armor * vis);
            ps.popPose();
        }

        // ---- the wings of the sovereign
        if (open > 0.01f) {
            ps.pushPose();
            ps.translate(0f, 1.55f, -0.28f);
            float len = 15f * Math.max(0.15f, open);
            float flap = 3f * Mth.sin(t * 0.12f) * unfold;
            for (int side = -1; side <= 1; side += 2) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(-side * (12f + 14f * (1f - open))));
                ps.mulPose(Axis.ZP.rotationDegrees(side * (Mth.lerp(open, -105f, 8f) + flap)));
                wing(buf, ps, len, side, 0.95f * vis * Math.min(1f, open * 1.4f), tint);
                wing(buf, ps, len * 1.02f, side, 0.35f * vis * glow, PALE);
                ps.popPose();
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(-side * (20f + 10f * (1f - open))));
                ps.mulPose(Axis.ZP.rotationDegrees(side * (Mth.lerp(open, -120f, -38f) - flap)));
                wing(buf, ps, len * 0.66f, side, 0.8f * vis * Math.min(1f, open * 1.4f), tint);
                ps.popPose();
            }
            // purple lightning crawling over the wing surfaces
            ps.translate(0, 3f, -0.2f);
            for (int side = -1; side <= 1; side += 2) {
                ps.pushPose();
                ps.mulPose(Axis.ZP.rotationDegrees(side * 40f));
                Draw.xyAt(add(buf, RIFT_BOLT), ps, 5f * open * 1.0f, 7f * open, 6f * open, VIOLET, 0.45f * vis * open * (0.6f + 0.4f * Mth.sin(t * 0.9f + side)));
                ps.popPose();
            }
            ps.popPose();
            // enormous rings behind him
            ps.pushPose();
            ps.translate(0, 2.4f, -1.4f);
            for (int i = 0; i < 3; i++) {
                ps.pushPose();
                ps.mulPose(Axis.XP.rotationDegrees(90));
                float r = 6.5f - i * 1.7f;
                Draw.disc(add(buf, CIRCLE), ps, r * open, t * (i % 2 == 0 ? 2f : -3f), i * 0.03f, i == 1 ? PALE : VIOLET, 0.65f * vis * open);
                ps.popPose();
            }
            ps.popPose();
        }
        ps.popPose();

        effects(e, t, ps, buf, base, R, FR, fold);
    }

    /** Everything that is not attached to the body: shockwaves, melee waves and the final detonation. */
    private void effects(SovereignEntity e, float t, PoseStack ps, MultiBufferSource buf, Vec3 base, float R, float FR, int fold) {
        Entity o = e.getOwnerEntity();
        Vec3 op = Vec3.ZERO;
        if (o != null) {
            op = new Vec3(o.getX(), o.getY(), o.getZ()).subtract(base);
        }
        // transformation shockwave
        float ts = t - SovereignEntity.SHOCK;
        if (ts >= 0f && ts < 24f) {
            float k = ts / 24f;
            float r = R * smooth(Math.min(1f, ts / 14f));
            ps.pushPose();
            ps.translate(op.x, 0, op.z);
            Draw.disc(add(buf, WAVE), ps, r, ts * 3f, 0.1f, VIOLET, 0.9f * (1f - k));
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 2.4f), r + 0.8f, 0.14f, 96, 1f, 0f, PALE, 1f - k);
            Draw.band(add(buf, RIFT_BOLT), ps, r, 0f, 6f, 72, 8f, ts * 0.05f, VIOLET, 0.85f * (1f - k), 0f);
            ps.popPose();
        }
        // the small spatial shockwave of every melee hit
        float tm = t - e.getMeleeAge();
        if (tm >= 0f && tm < 7f) {
            Vec3 mp = e.getMeleePos().subtract(base);
            float k = tm / 7f;
            ps.pushPose();
            ps.translate(mp.x, mp.y, mp.z);
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, 1.8f * (1f - k) + 0.3f, 1.8f * (1f - k) + 0.3f, PALE, 1f - k);
            ps.popPose();
            ps.pushPose();
            ps.translate(mp.x, mp.y, mp.z);
            Draw.ring(add(buf, RING_WAVE), ps, 0.3f + 2.4f * k, 0.5f + 2.8f * k, 0f, 32, 1f, 0f, VIOLET, 0.8f * (1f - k));
            ps.popPose();
        }
        // Sovereign's Strikes: the spatial explosion
        float tb = t - e.getBlastAge();
        if (tb >= 0f && tb < 16f) {
            Vec3 mp = e.getMeleePos().subtract(base);
            float k = tb / 16f;
            float br = (float) (double) ZegConfig.FORM.blastRadius.get();
            ps.pushPose();
            ps.translate(mp.x, mp.y - 1f, mp.z);
            Draw.disc(add(buf, WAVE), ps, br * smooth(k), tb * 5f, 0.1f, VIOLET, 0.9f * (1f - k));
            Draw.ring(add(buf, RING_WAVE), ps, br * smooth(k) - 1.2f, br * smooth(k) + 0.5f, 0.14f, 64, 1f, 0f, PALE, 1f - k);
            Draw.disc(add(buf, CRACKS), ps, br * 0.8f, 3f, 0.06f, DEEP, 0.9f * (1f - k));
            ps.popPose();
        }
        // the compressing sphere and the final detonation
        if (fold >= 0 && o != null) {
            float fk = c01((t - fold) / SovereignEntity.FOLD);
            float s = 5.5f * smooth(fk) * (1f - c01((t - fold - SovereignEntity.FOLD) / 6f));
            ps.pushPose();
            ps.translate(op.x, op.y + 1.4f, op.z);
            Draw.sphere(trans(buf, SINGULARITY), ps, s * 0.8f, 12, 24, t * 0.02f, WHITE, 0.9f * fk);
            Draw.sphere(add(buf, SPHERE_GLOW), ps, s, 10, 20, -t * 0.02f, PALE, 0.65f * fk);
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, s * 2.2f, s * 2.2f, VIOLET, 0.6f * fk);
            ps.popPose();
            ps.pushPose();
            ps.translate(op.x, 0, op.z);
            Draw.disc(add(buf, CIRCLE), ps, 6f * fk, t * 8f, 0.06f, PALE, 0.8f * fk);
            ps.popPose();
            float td = t - (fold + SovereignEntity.FOLD);
            if (td >= 0f && td < 30f) {
                float k = td / 30f;
                float r = FR * smooth(Math.min(1f, td / 18f));
                ps.pushPose();
                ps.translate(op.x, 0, op.z);
                Draw.disc(add(buf, WAVE), ps, r, td * 3f, 0.14f, PALE, 0.95f * (1f - k));
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 3f), r + 1f, 0.18f, 112, 1f, 0f, VIOLET, 1f - k);
                Draw.band(add(buf, RIFT_BOLT), ps, r, 0f, 10f, 80, 10f, td * 0.06f, VIOLET, 0.9f * (1f - k), 0f);
                Draw.disc(add(buf, CRACKS), ps, Math.min(FR, r * 1.05f), 11f, 0.05f, DEEP, 0.9f * (1f - k * 0.5f));
                ps.translate(0, 1.5f, 0);
                Gfx.billboard(ps);
                Draw.xy(add(buf, BURST), ps, FR * 0.5f * (1f - k * 0.4f), FR * 0.5f * (1f - k * 0.4f), PALE, 1f - k);
                ps.popPose();
            }
        }
    }
}
