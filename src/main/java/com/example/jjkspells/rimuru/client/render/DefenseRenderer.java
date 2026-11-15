package com.example.jjkspells.rimuru.client.render;

import com.example.jjkspells.rimuru.entity.DefenseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.example.jjkspells.rimuru.client.render.Gfx.*;

/** Uriel's Covenant: a golden lattice sphere around the caster, rune rings, reflected bolts and the release. */
public class DefenseRenderer extends RimRenderer<DefenseEntity> {
    public DefenseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(DefenseEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        int rel = e.getReleaseAge();
        Vec3 c = ownerP.add(0, 1.0, 0);
        float form = smooth(t / DefenseEntity.FORM);
        float dur = e.getDuration();
        // never blind the player from inside: the lattice dims when the camera is within it, and glows centred on the caster fade out near the camera
        float dist = (float) cam.distanceTo(c);
        float far = Math.max(0f, Math.min(1f, (dist - 0.9f) / 3.0f));
        float warn = t > dur - 24 && rel < 0 ? 0.6f + 0.4f * (float) Math.sin(t * 1.3f) : 1f;
        if (rel < 0) {
            float r = 2.3f * form;
            float in = dist < r * 1.04f ? 0.22f : 1f;
            Fx.shell(buf, ps, c, r, t * 0.01f, GOLD, 0.45f * form * warn * in);
            Fx.shell(buf, ps, c, r * 1.04f, -t * 0.014f, PALE, 0.3f * form * warn * in);
            Fx.glowTex(buf, ps, GLOW, c, r * 2.2f, 0f, GOLD, 0.28f * form * far);
            Vec3 feet = ownerP.add(0, 0.06, 0);
            Fx.disc(buf, ps, RUNE, feet, 2.8f * form, t * 2.4f, GOLD, 0.8f * form * warn);
            Fx.disc(buf, ps, CIRCLE, feet, 2.2f * form, -t * 3.2f, PALE, 0.6f * form * warn);
            for (int i = 0; i < 4; i++) {
                double a = t * 0.05 + i * Math.PI / 2;
                Fx.glowTex(buf, ps, STAR, ownerP.add(Math.cos(a) * 2.5 * form, 0.4 + 0.3 * Math.sin(t * 0.2 + i), Math.sin(a) * 2.5 * form), 0.45f, t * 4f, WHITE, 0.8f * form);
            }
        } else {
            float f = (t - rel) / DefenseEntity.AFTER;
            if (f < 1f) {
                float R = e.getReleaseRadius();
                float r = lerp(2.3f, R, smooth(Math.min(1f, f * 1.6f)));
                float in = dist < r ? 0.15f : 1f;
                Fx.shell(buf, ps, c, r, t * 0.03f, GOLD, 0.7f * (1f - f) * in);
                Fx.orb(buf, ps, c, r * 0.97f, t * 0.04f, PALE, 0.25f * (1f - f) * (in < 1f ? 0f : 1f));
                Fx.wave(buf, ps, ownerP.add(0, 0.15, 0), R * 1.2f * smooth(f), GOLD, 0.85f * (1f - f));
                Fx.star(buf, ps, c, 5f + e.getStored() * 0.03f, t * 10f, WHITE, (1f - f) * far);
                Fx.glowTex(buf, ps, GLOW, c, r * 1.4f, 0f, GOLD, 0.5f * (1f - f) * far);
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
            Fx.glowTex(buf, ps, GLOW_BLUE, b, 2f * f, 0f, ELECTRIC, 0.9f * f);
        }
    }
}
