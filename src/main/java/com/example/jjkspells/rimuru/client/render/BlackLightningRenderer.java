package com.example.jjkspells.rimuru.client.render;

import com.example.jjkspells.rimuru.entity.BlackLightningEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.example.jjkspells.rimuru.client.render.Gfx.*;

/** Black Lightning: a black sphere at the hand, a violet bolt to the target, arcs between the enemies, a detonation. */
public class BlackLightningRenderer extends RimRenderer<BlackLightningEntity> {
    public BlackLightningRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(BlackLightningEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        Vec3 hand = owner == null ? v(0, 1.4, 0) : handRel(owner, pt, base);
        if (t < BlackLightningEntity.RELEASE + 4) {
            float grow = smooth(t / BlackLightningEntity.COMPRESS);
            float comp = smooth((t - BlackLightningEntity.COMPRESS) / (BlackLightningEntity.RELEASE - BlackLightningEntity.COMPRESS));
            float r = lerp(0.1f, 0.6f, grow) * (1f - comp * 0.5f);
            float fade = 1f - smooth((t - BlackLightningEntity.RELEASE) / 4f);
            Fx.blob(buf, ps, BLACK, hand, r * 1.3f, t * 4f, WHITE, 0.9f * fade);
            Fx.orb(buf, ps, hand, r, t * 0.05f, VIOLET, 0.6f * fade);
            Fx.glowTex(buf, ps, GLOW_VIOLET, hand, r * 3.2f, 0f, VIOLET, 0.8f * fade);
            Fx.glowTex(buf, ps, CORONA, hand, r * 2.6f, t * 9f, PURPLE, 0.6f * fade * comp);
            for (int i = 0; i < 3; i++) {
                long seed = (long) (t * 0.5f) * 31 + i * 977L;
                var rnd = net.minecraft.util.RandomSource.create(seed);
                Vec3 to = hand.add((rnd.nextDouble() - 0.5) * 2.4, (rnd.nextDouble() - 0.5) * 2.4, (rnd.nextDouble() - 0.5) * 2.4);
                Fx.bolt(buf, ps, hand, to, cam, 0.1f, VIOLET, 0.7f * fade * (0.3f + grow * 0.7f), seed);
            }
        }
        List<double[]> fx = e.fx();
        int n = 0;
        for (double[] d : fx) {
            n++;
            float life = (float) (t - d[1]);
            long seed = (long) (d[1] * 31) + n * 7L + (long) (t * 0.5f);
            if (d[0] == 1) {
                float f = 1f - life / 12f;
                if (f <= 0f) {
                    continue;
                }
                Vec3 a = v(d[2], d[3], d[4]).subtract(base), b = v(d[5], d[6], d[7]).subtract(base);
                Fx.beam(buf, ps, BEAM, a, b, cam, 0.55f * f, 1f, VIOLET, 0.5f * f);
                Fx.bolt(buf, ps, a, b, cam, 0.7f * f, VIOLET, 0.95f * f, seed);
                Fx.bolt(buf, ps, a, b, cam, 0.35f * f, PALE, 0.8f * f, seed + 5);
                Fx.glowTex(buf, ps, GLOW_VIOLET, b, 3.5f * f, 0f, VIOLET, 0.9f * f);
                Fx.star(buf, ps, b, 3f * f, life * 20f, WHITE, f);
            } else if (d[0] == 2) {
                float f = 1f - life / 16f;
                if (f <= 0f) {
                    continue;
                }
                Vec3 a = v(d[2], d[3], d[4]).subtract(base), b = v(d[5], d[6], d[7]).subtract(base);
                Fx.bolt(buf, ps, a, b, cam, 0.4f * f, VIOLET, 0.9f * f, seed);
                Fx.bolt(buf, ps, a, b, cam, 0.18f * f, WHITE, 0.7f * f, seed + 9);
                Fx.glowTex(buf, ps, GLOW_VIOLET, b, 2.2f * f, 0f, VIOLET, 0.8f * f);
            } else if (d[0] == 3) {
                float f = life / 12f;
                if (f >= 1f) {
                    continue;
                }
                Vec3 c = v(d[2], d[3], d[4]).subtract(base);
                float r = (float) d[5] * smooth(Math.min(1f, f * 1.6f));
                Fx.orb(buf, ps, c, r, life * 0.05f, VIOLET, 0.55f * (1f - f));
                Fx.wave(buf, ps, c.add(0, -c.y + 0.1, 0), (float) d[5] * 1.3f * smooth(f), PALE, 0.8f * (1f - f));
                Fx.glowTex(buf, ps, GLOW_VIOLET, c, r * 2.2f, 0f, VIOLET, 0.7f * (1f - f));
                Fx.star(buf, ps, c, r * 1.6f, life * 14f, WHITE, 0.9f * (1f - f));
            }
        }
    }
}
