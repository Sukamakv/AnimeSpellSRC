package com.example.jjkspells.malenia.client.render;

import com.example.jjkspells.malenia.entity.WaterfowlEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.example.jjkspells.malenia.client.render.Gfx.*;

/** Waterfowl Dance: a ground mark under the target, silver ribbons behind the dancer, crescents for every slash and the final blade ring. */
public class WaterfowlRenderer extends MelRenderer<WaterfowlEntity> {
    public WaterfowlRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(WaterfowlEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        Vec3 c = e.getCenter().subtract(base);
        float in = c01(t / 8f);
        float out = 1f - c01((t - WaterfowlEntity.END + 8f) / 8f);
        float a = in * out;
        // the mark on the ground
        if (t < WaterfowlEntity.CRES) {
            float pulse = 0.75f + 0.25f * Mth.sin(t * 0.5f);
            Draw.disc(add(buf, TARGET), ps, 3.2f, t * 3f, (float) c.y + 0.07f, SILVER, 0.55f * a * pulse);
            Draw.disc(add(buf, CIRCLE), ps, 2.0f, -t * 4f, (float) c.y + 0.08f, GOLD, 0.5f * a);
            if (t >= WaterfowlEntity.F2 && t < WaterfowlEntity.F3 + 4) {
                Draw.disc(add(buf, RING_WAVE), ps, 5f, t * 2f, (float) c.y + 0.09f, GOLD, 0.7f * a);
            }
        }
        // the ribbon behind her
        List<Vec3> pts = new ArrayList<>();
        for (Vec3 w : e.trail) {
            pts.add(w.subtract(base));
        }
        if (owner != null) {
            pts.add(ownerP.add(0, owner.getBbHeight() * 0.6, 0));
        }
        Fx.trail(buf, ps, pts, cam, 0.55f, SILVER, 0.85f * out);
        Fx.trail(buf, ps, pts, cam, 0.3f, GOLD, 0.8f * out);
        // the slashes
        for (double[] f : e.fx) {
            float u = c01((t - (float) f[5]) / 9f);
            float al = (1f - u) * (1f - u);
            float[] col = f[4] >= 0 ? SILVER : GOLD;
            Fx.star(buf, ps, new Vec3(f[0], f[1], f[2]).subtract(base), 0.9f * (1f - u), t * 8f, WHITE, al);
        }
        // the final crescent
        int ca = e.getCresAge();
        if (ca >= 0) {
            float u = c01((t - ca) / 18f);
            float al = 1f - u * u;
            float r = 0.6f + 7.2f * smooth(u);
            Draw.disc(add(buf, RING_WAVE), ps, r, 0f, (float) c.y + 0.1f, SILVER, al);
            Fx.crescentRing(buf, ps, c, r, 2.4f, 12, u * 50f, SILVER, 0.9f * al);
            Fx.crescentRing(buf, ps, c, r * 0.7f, 1.8f, 9, -u * 70f, GOLD, 0.8f * al);
            if (e.isDance()) {
                Draw.disc(add(buf, RING_WAVE), ps, r * 1.25f, 0f, (float) c.y + 0.12f, GOLD, 0.8f * al);
            }
        }
    }

}
