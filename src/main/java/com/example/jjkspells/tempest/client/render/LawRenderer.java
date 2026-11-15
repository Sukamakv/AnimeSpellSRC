package com.example.jjkspells.tempest.client.render;

import com.example.jjkspells.tempest.entity.LawEntity;
import com.example.jjkspells.vfx.MeshRender.Mode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.example.jjkspells.tempest.client.render.Gfx.*;
import static com.example.jjkspells.tempest.client.render.RimGfx.*;

/** Absolute Law of Protection: a geodesic slime-glass dome with a hex lattice, counter-turning inner shell, floor ring and the release shockwave. */
public class LawRenderer extends TemRenderer<LawEntity> {
    public LawRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(LawEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        int rel = e.getReleaseAge();
        Vec3 c = ownerP.add(0, 1.0, 0);
        float form = smooth(t / LawEntity.FORM);
        float dur = e.getDuration();
        float warn = t > dur - 24 && rel < 0 ? 0.6f + 0.4f * (float) Math.sin(t * 1.3f) : 1f;
        if (rel < 0) {
            float r = 8f * form;
            if (form > 0.02f) {
                put(buf, ps, geo(1), "glass", Mode.TRANS, c, r, r, r, t * 0.25f, 0f, 0f, AQUA, 0.3f * form * warn, 0.7f, 0f, 0f);
                put(buf, ps, geoEdges(1), "white", Mode.SOLID, c, r, r, r, t * 0.25f, 0f, 0f, ICE, 1f, 1f, 0f, 0f);
                put(buf, ps, hexSphere(), "rim_hex", Mode.TRANS, c, r * 1.015f, r * 1.015f, r * 1.015f, -t * 0.4f, 0f, 0f, AQUA, 0.55f * form * warn, 1f, t * 0.0008f, 0f);
                float ri = r * 0.88f;
                put(buf, ps, geo(2), "glass", Mode.TRANS, c, ri, ri, ri, -t * 0.4f, 0f, 0f, NAVY, 0.22f * form * warn, 0.5f, 0f, 0f);
            }
            Vec3 feet = ownerP.add(0, 0.08, 0);
            put(buf, ps, discFlat(), "glass", Mode.TRANS, feet, 8f * form, 1f, 8f * form, t * 0.6f, 0f, 0f, TEAL, 0.35f * form * warn, 0.7f, 0f, 0f);
            put(buf, ps, torusMed(), "white", Mode.SOLID, feet.add(0, 0.05, 0), 8f * form, 8f * form, 8f * form, t * 0.8f, 0f, 0f, ICE, 1f, 1f, 0f, 0f);
            put(buf, ps, torusMed(), "rock", Mode.SOLID, feet.add(0, 0.1, 0), 6.4f * form, 6.4f * form, 6.4f * form, -t * 1.1f, 0f, 0f, STEEL, 1f, 0.4f, 0f, 0f);
            for (int i = 0; i < 4; i++) {
                double a = t * 0.05 + i * Math.PI / 2;
                put(buf, ps, pylon(), "glass", Mode.SOLID, ownerP.add(Math.cos(a) * 7.6 * form, 0.4 + 0.4 * Math.sin(t * 0.2 + i), Math.sin(a) * 7.6 * form), 1.4f, 1.8f, 1.4f, t * 4f, 0f, 180f, AQUA, 1f, 0.9f, 0f, 0f);
            }
        } else {
            float f = (t - rel) / LawEntity.AFTER;
            if (f < 1f) {
                float R = e.getReleaseRadius();
                float r = lerp(8f, R, smooth(Math.min(1f, f * 1.6f)));
                sphere(buf, ps, "glass", Mode.TRANS, c, r, AQUA, 0.55f * (1f - f), 1f, t);
                put(buf, ps, hexSphere(), "rim_hex", Mode.TRANS, c, r * 0.98f, r * 0.98f, r * 0.98f, t * 0.8f, 0f, 0f, ICE, 0.6f * (1f - f), 1f, 0f, 0f);
                ringFlat(buf, ps, ownerP.add(0, 0.25, 0), R * 1.2f * smooth(f), ICE, 1f - f);
                ringFlat(buf, ps, ownerP.add(0, 0.5, 0), R * 0.8f * smooth(f), AQUA, 1f - f);
                Fx.star(buf, ps, c, 5f + e.getStored() * 0.03f, t * 10f, ICE, 1f - f);
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
            bolt(buf, ps, a, b, 1, (int) (d[1] * 13) + n, ICE, 0.95f * f);
            put(buf, ps, sphereLo(), "white", Mode.SOLID, b, 0.7f * f, 0f, ICE, 1f, 1f);
        }
    }
}
