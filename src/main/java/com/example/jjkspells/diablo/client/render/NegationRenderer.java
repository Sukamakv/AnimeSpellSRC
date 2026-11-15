package com.example.jjkspells.diablo.client.render;

import com.example.jjkspells.diablo.entity.NegationEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.example.jjkspells.diablo.client.render.Gfx.*;

/** Law of the Primordial: a crimson lattice sphere around the caster, rune rings, reflected bolts and the release. */
public class NegationRenderer extends DiaRenderer<NegationEntity> {
    public NegationRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(NegationEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        int rel = e.getReleaseAge();
        Vec3 c = ownerP.add(0, 1.0, 0);
        float form = smooth(t / NegationEntity.FORM);
        float dur = e.getDuration();
        float warn = t > dur - 24 && rel < 0 ? 0.6f + 0.4f * (float) Math.sin(t * 1.3f) : 1f;
        if (rel < 0) {
            float r = 5f * form;
            Fx.shell(buf, ps, c, r, t * 0.01f, CRIM, 0.5f * form * warn);
            Fx.shell(buf, ps, c, r * 1.04f, -t * 0.014f, SCAR, 0.3f * form * warn);
            Fx.glowTex(buf, ps, GLOW, c, r * 2.2f, 0f, CRIM, 0.28f * form);
            Vec3 feet = ownerP.add(0, 0.06, 0);
            Fx.disc(buf, ps, RUNE, feet, 5.5f * form, t * 2.4f, CRIM, 0.8f * form * warn);
            Fx.disc(buf, ps, CIRCLE, feet, 4.5f * form, -t * 3.2f, SCAR, 0.6f * form * warn);
            for (int i = 0; i < 4; i++) {
                double a = t * 0.05 + i * Math.PI / 2;
                Fx.glowTex(buf, ps, STAR, ownerP.add(Math.cos(a) * 5.4 * form, 0.4 + 0.3 * Math.sin(t * 0.2 + i), Math.sin(a) * 5.4 * form), 0.45f, t * 4f, WHITE, 0.8f * form);
            }
        } else {
            float f = (t - rel) / NegationEntity.AFTER;
            if (f < 1f) {
                float R = e.getReleaseRadius();
                float r = lerp(5f, R, smooth(Math.min(1f, f * 1.6f)));
                Fx.shell(buf, ps, c, r, t * 0.03f, CRIM, 0.7f * (1f - f));
                Fx.orb(buf, ps, c, r * 0.97f, t * 0.04f, PALE, 0.25f * (1f - f));
                Fx.wave(buf, ps, ownerP.add(0, 0.15, 0), R * 1.2f * smooth(f), CRIM, 0.85f * (1f - f));
                Fx.star(buf, ps, c, 5f + e.getStored() * 0.03f, t * 10f, WHITE, 1f - f);
                Fx.glowTex(buf, ps, GLOW, c, r * 1.4f, 0f, CRIM, 0.5f * (1f - f));
            }
        }
        List<double[]> fx = e.fx();
        int n = 0;
        for (double[] d : fx) {
            n++;
            float f = 1f - (t - (float) d[1]) / 8f;
            if (d[0] != 1 || f <= 0f) {
                continue;
            }
            Vec3 a = v(d[2], d[3], d[4]).subtract(base), b = v(d[5], d[6], d[7]).subtract(base);
            Fx.bolt(buf, ps, a, b, cam, 0.3f * f, ELECTRIC, 0.95f * f, (long) (d[1] * 13) + n);
            Fx.glowTex(buf, ps, GLOW, b, 2f * f, 0f, ELECTRIC, 0.9f * f);
        }
    }
}
