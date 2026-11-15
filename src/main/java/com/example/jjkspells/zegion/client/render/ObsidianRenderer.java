package com.example.jjkspells.zegion.client.render;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.entity.ObsidianEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.zegion.client.render.Gfx.*;

/** Obsidian Kaiser: crystal plates that orbit the caster (one per negation charge), a hex barrier that flares on every hit, rings behind him and the ground-shattering counterattack. */
public class ObsidianRenderer extends ZegRenderer<ObsidianEntity> {
    public ObsidianRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(ObsidianEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base) {
        float yaw = 0f;
        Entity o = e.getOwnerEntity();
        if (o instanceof LivingEntity le) {
            yaw = Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot);
        }
        int w = e.getWindupAt(), p = e.getPunchAt();
        float form = smooth(t / ObsidianEntity.FORM);
        float wind = w >= 0 ? c01((t - w) / ObsidianEntity.WINDUP) : 0f;
        float store = e.getStore();
        int charges = e.getCharges();
        int max = Math.max(1, ZegConfig.OBSIDIAN.charges.get());
        float hit = c01(1f - (t - e.getHitAge()) / 9f);
        float live = p >= 0 ? 0f : 1f;
        float after = p >= 0 ? 1f - c01((t - p) / 18f) : 1f;

        // ---- shards and cracks while the armor forms
        if (t < ObsidianEntity.FORM + 10) {
            float g = form * (1f - c01((t - ObsidianEntity.FORM) / 10f));
            Draw.disc(add(buf, CRACKS), ps, 3.2f * form, t * 2f, 0.06f, DEEP, 0.8f * g);
            Draw.disc(add(buf, CIRCLE), ps, 2.4f * form, -t * 4f, 0.08f, VIOLET, 0.7f * g);
            for (int i = 0; i < 9; i++) {
                float a = i * Mth.TWO_PI / 9f + t * 0.02f;
                float rise = smooth((t - i * 1.5f) / 16f);
                ps.pushPose();
                ps.translate(Mth.cos(a) * 1.5f, 0f, Mth.sin(a) * 1.5f);
                ps.mulPose(Axis.YP.rotation(-a + Mth.HALF_PI));
                ps.mulPose(Axis.XP.rotationDegrees(-12f));
                shard(buf, ps, 0.5f, 1.4f + 1.4f * rise, 0.9f * rise * (1f - c01((t - ObsidianEntity.FORM) / 10f)));
                ps.popPose();
            }
        }

        if (form <= 0.01f || after <= 0.01f) {
            return;
        }
        float orbit = t * 3.4f;
        float radius = (1.45f + 0.15f * Mth.sin(t * 0.1f)) * (1f - 0.55f * wind);
        float glow = 0.55f + 0.45f * store + 0.6f * hit + 0.5f * wind;

        // ---- crystalline plates, one per negation charge, lock into place around the body
        for (int i = 0; i < max; i++) {
            boolean on = i < charges || charges < 0;
            float alive = on ? 1f : 0f;
            if (!on) {
                continue;
            }
            float a = Mth.TWO_PI * i / max + orbit * Mth.DEG_TO_RAD * 0.25f;
            float h = 1.1f + 0.5f * Mth.sin(i * 2f + t * 0.06f);
            ps.pushPose();
            if (wind > 0f) {
                // the plates converge on the fist
                float tx = 0.6f, ty = 1.2f, tz = 0.5f;
                Vec3 yv = new Vec3(tx, ty, tz).yRot(-yaw * Mth.DEG_TO_RAD);
                ps.translate(Mth.lerp(wind, Mth.cos(a) * radius, (float) yv.x), Mth.lerp(wind, h + 0.2f, (float) yv.y), Mth.lerp(wind, Mth.sin(a) * radius, (float) yv.z));
            } else {
                ps.translate(Mth.cos(a) * radius, h + 0.2f, Mth.sin(a) * radius);
            }
            ps.mulPose(Axis.YP.rotation(-a + Mth.HALF_PI));
            ps.mulPose(Axis.XP.rotationDegrees(-8f));
            float pw = 0.9f * (1f - 0.7f * wind), ph = 1.7f * (1f - 0.6f * wind);
            ps.translate(0, -ph * 0.5f, 0);
            plate(buf, ps, pw, ph, 0.95f * form * after);
            ps.translate(0, 0, 0.02f);
            Draw.xyAt(add(buf, MIRROR_FRAME), ps, pw * 0.5f, ph * 0.5f, ph * 0.5f, VIOLET, 0.8f * glow * form * after);
            ps.popPose();
        }

        // ---- rotating black-purple rings behind the caster
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.translate(0, 1.3f, -0.9f);
        for (int i = 0; i < 3; i++) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(90));
            ps.mulPose(Axis.YP.rotationDegrees(t * (i % 2 == 0 ? 4f : -3f)));
            float r = 2.6f - i * 0.7f;
            Draw.disc(add(buf, CIRCLE), ps, r, 0f, i * 0.02f, i == 1 ? PALE : VIOLET, 0.7f * form * after);
            ps.popPose();
        }
        ps.popPose();

        // ---- the hex barrier
        float bubble = (1.95f - 1.15f * wind) * (0.8f + 0.2f * form);
        ps.pushPose();
        ps.translate(0, 1.0f, 0);
        Draw.sphere(add(buf, HEX), ps, bubble, 10, 22, t * 0.004f, VIOLET, (0.16f + 0.5f * hit + 0.5f * wind + 0.25f * store) * form * after);
        Draw.sphere(add(buf, SPHERE_GLOW), ps, bubble * 0.98f, 8, 16, -t * 0.003f, DEEP, (0.1f + 0.15f * glow) * form * after * live + 0.3f * wind * after);
        ps.popPose();

        // ---- impact flash where the last attack broke on the armor
        if (hit > 0.01f) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(e.getHitYaw()));
            ps.translate(0, 1.2f, bubble * 0.95f);
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, 2.2f * hit + 0.4f, 2.2f * hit + 0.4f, PALE, hit);
            Draw.xy(add(buf, CRACKS), ps, 1.2f, 1.2f, VIOLET, hit * 0.8f);
            ps.popPose();
        }

        // ---- the counterattack
        if (w >= 0) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.translate(0.55f, 1.25f, 0.55f);
            Gfx.billboard(ps);
            float s = (0.4f + 1.2f * wind) * (p >= 0 ? 0f : 1f);
            Draw.xy(add(buf, GLOW), ps, s * 1.6f, s * 1.6f, PALE, 0.9f * wind * live);
            Draw.xy(trans(buf, GLOW), ps, s * 0.3f, s * 0.3f, BLACK, 0.9f * wind * live);
            ps.popPose();
            Draw.disc(add(buf, TARGET), ps, 5f + 20f * (1f - wind), t * 3f, 0.09f, VIOLET, 0.5f * wind * live);
        }
        if (p >= 0) {
            float tp = t - p;
            float SR = (float) (double) ZegConfig.OBSIDIAN.radius.get();
            float k = c01(tp / 16f);
            float r = 2f + (SR - 2f) * smooth(k);
            float a = 1f - c01((tp - 4f) / 20f);
            if (a > 0.01f) {
                Draw.disc(add(buf, WAVE), ps, r, tp * 4f, 0.1f, VIOLET, 0.85f * a);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 2f), r + 0.8f, 0.14f, 96, 1f, 0f, PALE, a);
                Draw.disc(add(buf, CRACKS), ps, Math.min(SR, r * 1.05f), 5f, 0.05f, DEEP, 0.9f * a);
                Draw.band(add(buf, RIFT_BOLT), ps, r, 0f, 3.5f, 72, 9f, tp * 0.05f, VIOLET, 0.8f * a, 0f);
                ps.pushPose();
                ps.translate(0, 1.2f, 0);
                Gfx.billboard(ps);
                Draw.xy(add(buf, BURST), ps, 7f * (1f - k * 0.4f), 7f * (1f - k * 0.4f), PALE, a);
                ps.popPose();
            }
        }
    }
}
