package com.example.jjkspells.rimuru.client.render;

import com.example.jjkspells.rimuru.entity.CollapseEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.example.jjkspells.rimuru.client.render.Gfx.*;

/** Nihility Break: a void sphere at the hand, a thrown projectile, a black hole that pulls, then two explosions and a rupture. */
public class CollapseRenderer extends RimRenderer<CollapseEntity> {
    public CollapseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(CollapseEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        Vec3 hand = owner == null ? v(0, 1.4, 0) : handRel(owner, pt, base);
        Vec3 ground = v(0, 0.1, 0);
        // the sphere in the hand: manifests, compresses
        if (t < CollapseEntity.RELEASE + 2) {
            float grow = smooth(t / CollapseEntity.COMPRESS);
            float comp = smooth((t - CollapseEntity.COMPRESS) / (CollapseEntity.RELEASE - CollapseEntity.COMPRESS));
            float r = lerp(0.1f, 1.0f, grow) * (1f - comp * 0.6f);
            drawVoid(buf, ps, hand, r, t, cam, 1f - smooth((t - CollapseEntity.RELEASE) / 2f));
            Fx.disc(buf, ps, CIRCLE, ownerP.add(0, 0.06, 0), 2.6f * grow, t * 4f, ELECTRIC, 0.6f * (1f - comp));
        }
        // the projectile flies from the hand to the point
        for (double[] d : e.fx()) {
            if (d[0] != 1) {
                continue;
            }
            float f = (t - (float) d[1]) / (CollapseEntity.IMPACT - CollapseEntity.RELEASE);
            if (f >= 1f) {
                continue;
            }
            Vec3 from = v(d[2], d[3], d[4]);
            Vec3 to = v(0, 0.8, 0);
            float k = smooth(f);
            Vec3 p = from.lerp(to, k).add(0, Math.sin(k * Math.PI) * 1.5, 0);
            Vec3 tail = from.lerp(to, smooth(Math.max(0f, f - 0.12f)));
            Fx.beam(buf, ps, BEAM, tail, p, cam, 0.35f, 1f, VIOLET, 0.8f);
            drawVoid(buf, ps, p, 0.6f, t, cam, 1f);
        }
        // the void at the point
        if (t >= CollapseEntity.IMPACT && t < CollapseEntity.BLAST2 + 6) {
            float since = t - CollapseEntity.IMPACT;
            float r = 1.6f + smooth((t - CollapseEntity.PULL0) / 26f) * 2.2f;
            float fade = 1f - smooth((t - CollapseEntity.BLAST2) / 6f);
            Vec3 c = v(0, 1.4, 0);
            drawVoid(buf, ps, c, r, t, cam, fade);
            float disc = smooth((t - CollapseEntity.IMPACT) / 14f) * fade;
            Fx.disc(buf, ps, VORTEX, ground, 14f * disc + 3f, t * 7f, VIOLET, 0.5f * disc);
            if (t >= CollapseEntity.PULL0) {
                float p = smooth((t - CollapseEntity.PULL0) / 12f) * fade;
                Fx.disc(buf, ps, VORTEX, v(0, 0.14, 0), 22f * p, -t * 5f, ELECTRIC, 0.45f * p);
                Fx.disc(buf, ps, RINGS, v(0, 0.16, 0), 22f * p * (1f - (t % 24f) / 24f), 0f, PALE, 0.5f * p);
            }
            if (since < 14f) {
                float f = since / 14f;
                Fx.wave(buf, ps, ground, 14f * smooth(f) + 1f, VIOLET, 1f - f);
                Fx.orb(buf, ps, c, 14f * smooth(f), t * 0.05f, PURPLE, 0.4f * (1f - f));
            }
        }
        if (t >= CollapseEntity.BLAST2 && t < CollapseEntity.BLAST2 + 16) {
            float f = (t - CollapseEntity.BLAST2) / 16f;
            Fx.wave(buf, ps, ground, 14f * smooth(f) + 1f, ELECTRIC, 1f - f);
            Fx.orb(buf, ps, v(0, 1.4, 0), 14f * smooth(f), t * 0.05f, VIOLET, 0.45f * (1f - f));
            Fx.star(buf, ps, v(0, 1.4, 0), 9f * (1f - f), t * 9f, WHITE, 1f - f);
        }
        if (t >= CollapseEntity.RUPTURE && t < CollapseEntity.RUPTURE + 16) {
            float f = (t - CollapseEntity.RUPTURE) / 16f;
            float R = e.isOverload() ? 26f : 22f;
            Fx.wave(buf, ps, ground, R * smooth(f) + 1f, PALE, 1f - f);
            Fx.wave(buf, ps, v(0, 0.3, 0), R * 0.8f * smooth(f) + 1f, VIOLET, 0.9f * (1f - f));
            Fx.orb(buf, ps, v(0, 1.4, 0), R * 0.5f * smooth(f), t * 0.05f, PURPLE, 0.35f * (1f - f));
            Fx.star(buf, ps, v(0, 1.4, 0), 12f * (1f - f), t * 9f, WHITE, 1f - f);
        }
    }

    private static void drawVoid(MultiBufferSource buf, PoseStack ps, Vec3 p, float r, float t, Vec3 cam, float alpha) {
        Fx.blob(buf, ps, BLACK, p, r * 1.3f, t * 3f, WHITE, 0.95f * alpha);
        Fx.orb(buf, ps, p, r, t * 0.04f, VIOLET, 0.5f * alpha);
        Fx.glowTex(buf, ps, GLOW_VIOLET, p, r * 3.0f, 0f, ELECTRIC, 0.7f * alpha);
        Fx.glowTex(buf, ps, CORONA, p, r * 2.4f, -t * 6f, PURPLE, 0.6f * alpha);
    }
}
