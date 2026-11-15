package com.example.jjkspells.rimuru.client.render;

import com.example.jjkspells.rimuru.entity.AzathothEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.example.jjkspells.rimuru.client.render.Gfx.*;

/** End of Creation: the caster's imaginary space, a targeting circle, the black sun, the beam, the descent, the soul pull and the final compression. */
public class AzathothRenderer extends RimRenderer<AzathothEntity> {
    public AzathothRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(AzathothEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        Vec3 ground = v(0, 0.1, 0);
        // the caster: awakening aura, imaginary space, chaos creator
        float awake = smooth(t / AzathothEntity.SPACE) * (1f - smooth((t - AzathothEntity.END + 20f) / 20f));
        Vec3 c = ownerP.add(0, 1.0, 0);
        Fx.disc(buf, ps, RUNE, ownerP.add(0, 0.07, 0), 3.2f * awake, t * 2f, VIOLET, 0.8f * awake);
        Fx.disc(buf, ps, VORTEX, ownerP.add(0, 0.09, 0), 4.5f * awake, -t * 5f, PURPLE, 0.5f * awake);
        Fx.glowTex(buf, ps, CORONA, c, 3.4f * awake, t * 4f, t > AzathothEntity.FINAL ? ELECTRIC : VIOLET, 0.55f * awake);
        float space = smooth((t - AzathothEntity.SPACE) / 20f) * (1f - smooth((t - AzathothEntity.COMPRESS) / 20f)) * awake;
        if (space > 0.01f) {
            Fx.blob(buf, ps, BLACK, c, 7f * space, t, WHITE, 0.55f * space);
            Fx.orb(buf, ps, c, 6f * space, t * 0.02f, PURPLE, 0.35f * space);
            Fx.shell(buf, ps, c, 6.2f * space, -t * 0.012f, ELECTRIC, 0.25f * space);
        }
        // targeting circle at the point
        float mark = smooth((t - AzathothEntity.SUN) / 20f) * (1f - smooth((t - AzathothEntity.COMPRESS) / 12f));
        Fx.disc(buf, ps, TARGET, ground, 25f * mark, t, VIOLET, 0.55f * mark);
        Fx.disc(buf, ps, RUNE, v(0, 0.12, 0), 18f * mark, -t * 1.5f, ELECTRIC, 0.5f * mark);
        Fx.disc(buf, ps, RINGS, v(0, 0.14, 0), 25f * mark * (1f - (t % 30f) / 30f), 0f, PALE, 0.35f * mark);
        // the black sun
        float form = smooth((t - AzathothEntity.SUN) / (AzathothEntity.BEAM - AzathothEntity.SUN));
        float desc = smooth((t - AzathothEntity.DESCEND) / (float) (AzathothEntity.FALL - AzathothEntity.DESCEND));
        float vanish = 1f - smooth((t - AzathothEntity.FALL) / 8f);
        Vec3 sun = v(0, lerp(36f, 4f, desc), 0);
        float sr = lerp(1.2f, 4.0f, form) * lerp(1f, 1.5f, desc);
        if (form > 0.01f && vanish > 0.01f) {
            Fx.blob(buf, ps, BLACK, sun, sr * 1.4f, t * 1.5f, WHITE, 0.97f * vanish);
            Fx.orb(buf, ps, sun, sr, t * 0.03f, VIOLET, 0.55f * vanish);
            Fx.glowTex(buf, ps, CORONA, sun, sr * 3.4f, t * 2f, PURPLE, 0.8f * vanish);
            Fx.glowTex(buf, ps, GLOW_VIOLET, sun, sr * 4.2f, 0f, ELECTRIC, 0.5f * vanish);
            Fx.star(buf, ps, sun, sr * 3f, t * 4f, VIOLET, 0.5f * vanish);
        }
        // reality rupture: the beam
        if (t >= AzathothEntity.BEAM && t < AzathothEntity.BEAM_HIT + 14) {
            float f = smooth((t - AzathothEntity.BEAM) / 10f) * (1f - smooth((t - AzathothEntity.BEAM_HIT - 2f) / 12f));
            Vec3 top = v(0, 36, 0);
            Fx.beam(buf, ps, BEAM, top, ground, cam, 1.6f * f, 4f, VIOLET, 0.85f * f);
            Fx.beam(buf, ps, BEAM, top, ground, cam, 0.6f * f, 4f, WHITE, f);
            Fx.bolt(buf, ps, top, ground, cam, 0.8f * f, ELECTRIC, 0.9f * f, (long) (t * 0.5f));
            Fx.glowTex(buf, ps, GLOW_VIOLET, v(0, 1, 0), 9f * f, 0f, VIOLET, 0.8f * f);
        }
        // the central void
        if (t >= AzathothEntity.BEAM_HIT && t < AzathothEntity.COMPRESS) {
            float f = smooth((t - AzathothEntity.BEAM_HIT) / 10f) * (1f - smooth((t - AzathothEntity.COMPRESS + 14f) / 14f));
            Fx.disc(buf, ps, VORTEX, v(0, 0.16, 0), 8f * f, t * 8f, PURPLE, 0.7f * f);
            Fx.orb(buf, ps, v(0, 1.2, 0), 2.2f * f, t * 0.04f, VIOLET, 0.5f * f);
        }
        // souls drifting in
        if (t >= AzathothEntity.SOUL && t < AzathothEntity.COMPRESS + 20) {
            float f = smooth((t - AzathothEntity.SOUL) / 10f) * (1f - smooth((t - AzathothEntity.COMPRESS - 10f) / 10f));
            for (int i = 0; i < 36; i++) {
                float k = ((t * 0.02f) + i / 36f) % 1f;
                double a = i * 2.399 + k * 2.0;
                float r = 40f * (1f - k);
                Vec3 p = v(Math.cos(a) * r, 0.8 + 3.0 * Math.sin(i * 1.3 + t * 0.05) + (1f - k) * 2f, Math.sin(a) * r);
                Fx.glowTex(buf, ps, SOULWISP, p, 0.9f + k * 0.6f, i * 25f, VIOLET, 0.8f * f * Math.min(1f, k * 4f));
            }
        }
        // the sphere that compresses the whole area
        if (t >= AzathothEntity.COMPRESS && t < AzathothEntity.FINAL + 4) {
            float comp = smooth((t - AzathothEntity.COMPRESS) / (AzathothEntity.FINAL - AzathothEntity.COMPRESS));
            float r = lerp(30f, 1.2f, comp);
            Vec3 p = v(0, 3.0 + comp * 1.5, 0);
            Fx.orb(buf, ps, p, r, t * 0.04f, PURPLE, 0.35f);
            Fx.shell(buf, ps, p, r, -t * 0.01f, ELECTRIC, 0.25f);
            Fx.blob(buf, ps, BLACK, p, Math.max(1.5f, r * 0.3f), t, WHITE, 0.9f);
            Fx.glowTex(buf, ps, CORONA, p, Math.max(2f, r * 0.8f), t * 5f, VIOLET, 0.6f);
        }
        // shock waves
        for (double[] d : e.fx()) {
            if (d[0] != 2) {
                continue;
            }
            float f = (t - (float) d[1]) / 20f;
            if (f >= 1f) {
                continue;
            }
            float R = (float) d[2];
            Fx.wave(buf, ps, ground, R * smooth(f) + 1f, PALE, 1f - f);
            Fx.wave(buf, ps, v(0, 0.5, 0), R * 0.85f * smooth(f) + 1f, VIOLET, 0.9f * (1f - f));
            Fx.orb(buf, ps, v(0, 1.0, 0), R * 0.5f * smooth(f), t * 0.04f, PURPLE, 0.35f * (1f - f));
            Fx.star(buf, ps, v(0, 2.0, 0), R * 0.4f * (1f - f), t * 8f, WHITE, 1f - f);
        }
    }
}
