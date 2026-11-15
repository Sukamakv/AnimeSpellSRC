package com.example.jjkspells.rimuru.client.render;

import com.example.jjkspells.rimuru.entity.MegiddoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.example.jjkspells.rimuru.client.render.Gfx.*;

/** Megiddo: water droplets, three lenses in the sky, beams of light, a focused judgment and a final rain. */
public class MegiddoRenderer extends RimRenderer<MegiddoEntity> {
    public MegiddoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(MegiddoEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        float R = e.getRadius();
        float[] tint = e.isDay() ? GOLD : WATER;
        float active = 1f - smooth((t - (MegiddoEntity.END - 8)) / 8f);
        // marked area
        float mark = smooth(t / 10f) * active;
        Fx.disc(buf, ps, TARGET, v(0, 0.06, 0), R * mark, t * 0.8f, tint, 0.55f * mark);
        Fx.disc(buf, ps, RUNE, v(0, 0.08, 0), R * 0.6f * mark, -t * 1.5f, WATER, 0.4f * mark);
        // droplets rising
        float drops = smooth(t / MegiddoEntity.DROPS) * active;
        for (int i = 0; i < 4; i++) {
            float h = 1.5f + ((t * 0.12f + i * 2.2f) % 9f);
            double a = i * 1.7 + t * 0.02;
            Fx.glowTex(buf, ps, DROPLETS, v(Math.cos(a) * 3.0, h, Math.sin(a) * 3.0), 3.2f, i * 40f, WATER, 0.6f * drops * (1f - h / 12f));
        }
        // lenses
        float form = smooth((t - MegiddoEntity.DROPS) / 16f) * active;
        int age = (int) t;
        for (int i = 0; i < 3; i++) {
            Vec3 p = MegiddoEntity.lensPos(e.getSeed(), i, age);
            Fx.glowTex(buf, ps, LENS, p, 3.6f * form, t * 1.5f * (i % 2 == 0 ? 1 : -1), tint, 0.95f * form);
            Fx.glowTex(buf, ps, GLOW, p, 5f * form, 0f, WATER, 0.45f * form);
            Fx.glowTex(buf, ps, CORONA, p, 4.5f * form, -t * 3f, WHITE, 0.4f * form);
        }
        List<double[]> fx = e.fx();
        for (double[] d : fx) {
            float life = (float) (t - d[1]);
            if (d[0] == 1) {
                float f = 1f - life / 12f;
                if (f <= 0f) {
                    continue;
                }
                Vec3 a = v(d[2], d[3], d[4]), b = v(d[5], d[6], d[7]);
                float w = (float) d[8];
                Fx.beam(buf, ps, BEAM, a, b, cam, (0.3f + w * 0.45f) * f, 1f, tint, 0.9f * f);
                Fx.beam(buf, ps, BEAM, a, b, cam, (0.12f + w * 0.18f) * f, 1f, WHITE, f);
                Fx.glowTex(buf, ps, GLOW, b, (1.4f + w * 1.4f) * f, 0f, tint, 0.9f * f);
                Fx.star(buf, ps, b, (1.2f + w) * f, life * 25f, WHITE, f);
                Fx.wave(buf, ps, v(b.x, 0.1, b.z), (1f + w * 2f) * (1f - f) + 0.5f, WATER, 0.6f * f);
            } else if (d[0] == 2) {
                float f = smooth(life / 8f) * (1f - smooth((life - 22f) / 8f));
                Vec3 p = v(d[2], d[3], d[4]);
                Fx.glowTex(buf, ps, LENS, p, 4.6f * f, t * 2f, GOLD, f);
                Fx.glowTex(buf, ps, GLOW, p, 7f * f, 0f, GOLD, 0.5f * f);
            }
        }
    }
}
