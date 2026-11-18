package com.example.jjkspells.malenia.client.render;

import com.example.jjkspells.malenia.entity.PhantomEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.example.jjkspells.malenia.client.render.Gfx.*;

/** Phantom Blossom: the attack circle, seven crimson phantoms with their blades, rot clouds' shimmer and the afterimages of the dive. */
public class PhantomRenderer extends MelRenderer<PhantomEntity> {
    public PhantomRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(PhantomEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        float in = c01(t / 8f);
        float out = 1f - c01((t - PhantomEntity.END + 8f) / 8f);
        float a = in * out;
        Vec3 tp = e.getTargetPos().subtract(base);
        if (t < PhantomEntity.HIT + 4) {
            float pulse = 0.75f + 0.25f * Mth.sin(t * 0.45f);
            Draw.disc(add(buf, TARGET), ps, 12f * 0.55f, t * 2f, (float) tp.y + 0.07f, CRIMSON, 0.7f * a * pulse);
            Draw.disc(add(buf, CIRCLE), ps, 3.2f, -t * 4f, (float) tp.y + 0.08f, GOLD, 0.55f * a);
        }
        if (owner != null && t < PhantomEntity.DIVE) {
            Draw.disc(add(buf, RUNE), ps, 2.6f * c01(t / 14f), t * 5f, (float) ownerP.y - 0.5f, CRIMSON, 0.6f * a);
        }
        float[][] ph = e.ph();
        for (int i = 0; i < PhantomEntity.N; i++) {
            if (ph[i][8] < 0.5f) {
                continue;
            }
            float appear = c01((t - (PhantomEntity.SHOW - 8f)) / 8f);
            Vec3 p0 = new Vec3(ph[i][0], ph[i][1], ph[i][2]).subtract(base);
            Vec3 end = new Vec3(ph[i][3], ph[i][4], ph[i][5]).subtract(base);
            float t0 = ph[i][6];
            int kind = (int) ph[i][7];
            float alpha = appear * a;
            Vec3 pos = p0.add(0, 0.25 * Mth.sin(t * 0.2f + i), 0);
            if (t0 >= 0) {
                float u = c01((t - t0) / 5f);
                Vec3 stop = end.add(p0.subtract(end).normalize().scale(1.6));
                pos = p0.lerp(stop, smooth(u));
                float fade = 1f - c01((t - t0 - 7f) / 10f);
                alpha *= fade;
                float since = t - t0;
                Vec3 hand = pos.add(0, 1.25, 0);
                Vec3 tip = end.add(0, 1.0, 0);
                Vec3 dir = tip.subtract(hand);
                dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
                float yaw = Util_yaw(dir);
                // ribbon of the dash
                Fx.trail(buf, ps, List.of(p0.add(0, 1.0, 0), hand), cam, 0.5f, CRIMSON, 0.7f * alpha);
                switch (kind) {
                    case 0 -> {
                        Fx.sword(buf, ps, hand, hand.add(dir.scale(3.2)), cam, 0.17f, WHITE, alpha);
                        if (since > 3 && since < 9) {
                            Fx.star(buf, ps, tip, 1.5f * (1f - c01((since - 3f) / 6f)), t * 10f, WHITE, alpha);
                        }
                    }
                    case 1, 2 -> {
                        Fx.sword(buf, ps, hand, hand.add(dir.scale(2.4)), cam, 0.16f, WHITE, alpha * 0.8f);
                        float su = c01((since - 3f) / 7f);
                    }
                    case 3 -> {
                        float su = c01((since - 2f) / 9f);
                        Fx.crescentRing(buf, ps, end, 1.0f + 1.4f * su, 1.6f, 6, t * 30f, CRIMSON, (1f - su) * alpha);
                        Fx.sword(buf, ps, hand, hand.add(dir.scale(2.4)), cam, 0.16f, WHITE, alpha * 0.8f);
                    }
                    default -> {
                        Vec3 top = tip.add(0, 4.5, 0);
                        float su = c01((since - 2f) / 4f);
                        Fx.sword(buf, ps, top, tip.add(0, -0.2 * su, 0), cam, 0.2f, WHITE, alpha);
                        Fx.star(buf, ps, tip, 1.6f * (1f - c01((since - 4f) / 6f)), t * 12f, WHITE, alpha);
                    }
                }
                if (since > 2f && since < 10f) {
                    Draw.disc(add(buf, RING_WAVE), ps, 0.5f + 1.8f * c01((since - 2f) / 8f), 0f, (float) end.y + 0.12f, CRIMSON, 0.7f * (1f - c01((since - 2f) / 8f)));
                    if (kind != 0) {
                        Draw.disc(trans(buf, MIST), ps, 2.4f, 0f, (float) end.y + 0.1f, ROTC, 0.4f * alpha);
                    }
                }
            }
            float yawp = 0f;
            Fx.phantom(buf, ps, pos, yawp, 2.2f, 0.85f * alpha, WHITE);
        }
        // the real one: afterimages of the dive
        if (owner != null && t >= PhantomEntity.DIVE - 2 && t < PhantomEntity.LAND + 14) {
            List<Vec3> pts = new ArrayList<>();
            for (Vec3 w : e.trail) {
                pts.add(w.subtract(base));
            }
            pts.add(ownerP.add(0, owner.getBbHeight() * 0.6, 0));
            float f = 1f - c01((t - PhantomEntity.LAND - 4f) / 10f);
            Fx.trail(buf, ps, pts, cam, 0.8f, CRIMSON, 0.9f * f);
            Fx.trail(buf, ps, pts, cam, 0.35f, WHITE, 0.8f * f);
            for (int k = 1; k <= 3; k++) {
                int idx = pts.size() - 1 - k * 3;
                if (idx >= 0) {
                    Fx.phantom(buf, ps, pts.get(idx).add(0, -1.0, 0), 0f, 2.1f, 0.35f * f * (1f - k / 4f), WHITE);
                }
            }
        }
        if (t >= PhantomEntity.HIT && t < PhantomEntity.HIT + 14) {
            float u = c01((t - PhantomEntity.HIT) / 14f);
            Draw.disc(add(buf, RING_WAVE), ps, 0.8f + 5f * u, 0f, (float) tp.y + 0.1f, e.isPerfect() ? GOLD : CRIMSON, 1f - u);
        }
    }

    private static float Util_yaw(Vec3 d) {
        return (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
    }
}
