package com.example.jjkspells.malenia.client.render;

import com.example.jjkspells.malenia.entity.BladeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.example.jjkspells.malenia.client.render.Gfx.*;

/** Miquella's Blade: a golden sword in her hand, silver trails, the four cuts, the thrust line and the descending execution. */
public class BladeRenderer extends MelRenderer<BladeEntity> {
    public BladeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(BladeEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        float in = c01(t / 6f);
        float out = 1f - c01((t - BladeEntity.END + 8f) / 8f);
        float a = in * out;
        Vec3 tp = e.getTargetPos().subtract(base);
        if (t < BladeEntity.EXEC) {
            Draw.disc(add(buf, TARGET), ps, 2.2f, t * 3f, (float) tp.y + 0.07f, GOLD, 0.5f * a * (0.75f + 0.25f * Mth.sin(t * 0.5f)));
        }
        if (owner != null) {
            Vec3 look = owner.getViewVector(pt);
            Vec3 right = new Vec3(-look.z, 0, look.x);
            right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
            Vec3 p0 = ownerP.add(0, 1.05, 0).add(right.scale(0.45)).add(look.scale(0.3));
            Vec3 dir = look.scale(0.9).add(0, 0.6, 0).add(right.scale(-0.25)).normalize();
            Vec3 p1 = p0.add(dir.scale(2.4));
            Fx.sword(buf, ps, p0, p1, cam, 0.16f, WHITE, 0.95f * a);
            if (t < BladeEntity.DASH0) {
                float s = c01(t / BladeEntity.DASH0);
                Fx.star(buf, ps, p1, 1.4f * s, t * 10f, WHITE, 0.9f * s);
            }
        }
        List<Vec3> pts = new ArrayList<>();
        for (Vec3 w : e.trail) {
            pts.add(w.subtract(base));
        }
        if (owner != null) {
            pts.add(ownerP.add(0, owner.getBbHeight() * 0.6, 0));
        }
        Fx.trail(buf, ps, pts, cam, 0.5f, SILVER, 0.85f * out);
        Fx.trail(buf, ps, pts, cam, 0.28f, GOLD, 0.8f * out);
        for (double[] f : e.fx) {
            int kind = (int) f[4];
            float u = c01((t - (float) f[5]) / 10f);
            float al = (1f - u) * (1f - u);
            Vec3 p = new Vec3(f[0], f[1], f[2]).subtract(base);
            float yaw = (float) f[3];
            switch (kind) {
                case 3 -> {
                    Fx.crescentRing(buf, ps, p.add(0, -0.6, 0), 1.6f + 1.2f * u, 1.4f, 6, u * 160f, GOLD, al);
                    Fx.crescentRing(buf, ps, p.add(0, -0.6, 0), 1.2f + 1.4f * u, 1.0f, 5, -u * 140f, SILVER, al);
                }
                case 4 -> {
                    Vec3 d = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
                    Fx.sword(buf, ps, p.subtract(d.scale(2.6)), p.add(d.scale(0.6)), cam, 0.2f, WHITE, al);
                    Fx.star(buf, ps, p.add(d.scale(0.6)), 1.8f * (1f - u), t * 12f, WHITE, al);
                    Draw.disc(add(buf, RING_WAVE), ps, 0.6f + 2.0f * u, 0f, (float) p.y, SILVER, 0.7f * al);
                }
                case 5 -> {
                    float uu = c01((t - (float) f[5]) / 16f);
                    float aa = 1f - uu * uu;
                    Vec3 g = new Vec3(p.x, tp.y, p.z);
                    Draw.disc(add(buf, RING_WAVE), ps, 0.6f + 6.2f * smooth(uu), 0f, (float) g.y + 0.1f, GOLD, aa);
                    Fx.crescentRing(buf, ps, g, 0.6f + 6f * smooth(uu), 2.4f, 12, uu * 40f, SILVER, 0.9f * aa);
                    ps.pushPose();
                    ps.translate(g.x, g.y, g.z);
                    Draw.cross(add(buf, LINE), ps, 0.7f, 0f, 7f, t * 5f, GOLD, aa * 0.9f, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
                    ps.popPose();
                }
                default -> Fx.star(buf, ps, p, 1.4f * (1f - u), t * 9f, WHITE, al);
            }
        }
    }
}
