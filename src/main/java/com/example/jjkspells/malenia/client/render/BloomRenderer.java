package com.example.jjkspells.malenia.client.render;

import com.example.jjkspells.malenia.entity.BloomEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.malenia.client.render.Gfx.*;

/** Scarlet Bloom: the marked circle, the dive, the giant flower and the rotting garden. */
public class BloomRenderer extends MelRenderer<BloomEntity> {
    public BloomRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(BloomEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        Vec3 p = e.getPoint().subtract(base);
        float R = e.getRadius();
        float Ri = R * (e.isCatastrophe() ? 0.6f : 0.75f);
        int gardenEnd = BloomEntity.BLOOM1 + e.getGardenTicks();
        float in = c01(t / 8f);
        float y = (float) p.y;
        // the marked circle until the dive lands
        if (t < BloomEntity.IMPACT + 2) {
            float pulse = 0.75f + 0.25f * Mth.sin(t * 0.5f);
            float s = in;
            Draw.disc(add(buf, TARGET), ps, Ri, t * 2f, y + 0.07f, CRIMSON, 0.85f * s * pulse);
            Draw.disc(add(buf, RUNE), ps, Ri * 0.6f, -t * 3f, y + 0.08f, GOLD, 0.7f * s);
            Draw.disc(trans(buf, MIST), ps, Ri, 0f, y + 0.05f, ROTC, 0.25f * s);
        }
        if (owner != null && t < BloomEntity.IMPACT) {
            float s = c01(t / BloomEntity.ARMS);
            Fx.glow(buf, ps, ownerP.add(0, 1.2, 0), 1.6f + 0.8f * s, CRIMSON, 0.45f * s);
            Draw.disc(add(buf, RUNE), ps, 2.4f * s, t * 5f, (float) ownerP.y - 0.4f, CRIMSON, 0.6f * s);
            if (t >= BloomEntity.TOP - 4) {
                Fx.trail(buf, ps, java.util.List.of(ownerP.add(0, 4.0, 0), ownerP.add(0, 1.0, 0)), cam, 0.8f, CRIMSON, 0.6f);
            }
        }
        // the impact
        if (t >= BloomEntity.IMPACT && t < BloomEntity.IMPACT + 14) {
            float u = c01((t - BloomEntity.IMPACT) / 14f);
            Draw.disc(add(buf, RING_WAVE), ps, 0.8f + Ri * u, 0f, y + 0.1f, CRIMSON, 1f - u);
            Draw.disc(add(buf, GLOW), ps, Ri * 0.8f, 0f, y + 0.06f, SCARLET, 0.6f * (1f - u));
            Fx.star(buf, ps, p.add(0, 1.0, 0), 4f * (1f - u), 0f, WHITE, 1f - u);
        }
        // the flower
        if (t >= BloomEntity.BLOOM0) {
            float open = smooth((t - BloomEntity.BLOOM0) / (BloomEntity.BLOOM1 - BloomEntity.BLOOM0));
            float fade = 1f - c01((t - gardenEnd + 24f) / 24f);
            float pulse = t > BloomEntity.BLOOM1 ? 1f + 0.03f * Mth.sin(t * 0.15f) : 1f;
            Fx.flower(buf, ps, p, R * pulse, open, t * 0.4f, 20, WHITE, 0.95f * fade);
            if (open > 0.3f) {
                Fx.glow(buf, ps, p.add(0, 1.6, 0), 3.0f * open, SCARLET, 0.45f * fade);
            }
        }
        if (t >= BloomEntity.BLOOM1 - 2 && t < BloomEntity.BLOOM1 + 16) {
            float u = c01((t - BloomEntity.BLOOM1) / 16f);
            Draw.disc(add(buf, RING_WAVE), ps, 1f + R * u, 0f, y + 0.12f, CRIMSON, 1f - u);
            Draw.disc(add(buf, WAVE), ps, 1f + R * u, 0f, y + 0.11f, SCARLET, 0.7f * (1f - u));
            Fx.star(buf, ps, p.add(0, 1.4, 0), 5f * (1f - u), 0f, WHITE, 1f - u);
        }
        // the garden
        if (t >= BloomEntity.BLOOM1) {
            float g = c01((t - BloomEntity.BLOOM1) / 12f) * (1f - c01((t - gardenEnd + 24f) / 24f));
            Draw.disc(trans(buf, MIST), ps, R, 0f, y + 0.06f, ROTC, 0.35f * g);
            Draw.disc(add(buf, WAVE), ps, R, t * 0.5f, y + 0.07f, DARKRED, 0.4f * g);
            Draw.disc(add(buf, RING_WAVE), ps, R, 0f, y + 0.09f, CRIMSON, 0.7f * g);
            Draw.band(trans(buf, ROT_WAVE), ps, R, 0f, 1.1f, 48, 10f, t * 0.01f, ROTC, 0.55f * g, 0f);
        }
    }
}
