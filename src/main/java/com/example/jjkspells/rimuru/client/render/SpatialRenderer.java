package com.example.jjkspells.rimuru.client.render;

import com.example.jjkspells.rimuru.entity.SpatialEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.example.jjkspells.rimuru.client.render.Gfx.*;

/** Severance of Dimensions: distortion, a space tear at both ends of the step, a travelling slash, four cracks and a contracting sphere. */
public class SpatialRenderer extends RimRenderer<SpatialEntity> {
    public SpatialRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(SpatialEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        // distortion around the caster before the step
        if (t < SpatialEntity.STEP + 6) {
            float f = smooth(t / SpatialEntity.STEP) * (1f - smooth((t - SpatialEntity.STEP) / 6f));
            Vec3 c = ownerP.add(0, 1.0, 0);
            Fx.disc(buf, ps, RINGS, ownerP.add(0, 0.08, 0), 2.6f * f, t * 6f, CYAN, 0.7f * f);
            Fx.glowTex(buf, ps, GLOW_BLUE, c, 3.2f * f, 0f, CYAN, 0.5f * f);
            Fx.orb(buf, ps, c, 1.1f * f, t * 0.05f, PURPLE, 0.4f * f);
            Fx.glowTex(buf, ps, CORONA, c, 2.4f * f, -t * 8f, VIOLET, 0.5f * f);
        }
        List<double[]> fx = e.fx();
        for (double[] d : fx) {
            float life = (float) (t - d[1]);
            if (d[0] == 1 || d[0] == 2) {
                float f = 1f - life / 14f;
                if (f <= 0f) {
                    continue;
                }
                Vec3 p = v(d[2], d[3] + 1.0, d[4]).subtract(base);
                float s = d[0] == 1 ? 1f - f * 0.3f : 0.6f + (1f - f) * 0.8f;
                Fx.glowTex(buf, ps, LENS, p, 2.6f * s, life * 4f, CYAN, f);
                Fx.glowTex(buf, ps, GLOW_BLUE, p, 3.4f * f, 0f, ELECTRIC, 0.6f * f);
                Fx.disc(buf, ps, RINGS, p.add(0, -0.9, 0), 2.2f * (1f - f) + 0.6f, life * 8f, VIOLET, 0.6f * f);
            } else if (d[0] == 3) {
                Vec3 o = v(d[2], d[3], d[4]).subtract(base), dir = v(d[5], d[6], d[7]);
                float len = (float) d[8];
                float prog = Mth.clamp(life / 8f, 0f, 1f);
                float f = life < 8f ? 1f : 1f - (life - 8f) / 8f;
                if (f <= 0f) {
                    continue;
                }
                Vec3 p = o.add(dir.scale(len * prog));
                float yaw = (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
                float pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp((float) dir.y, -1f, 1f)));
                Fx.slash(buf, ps, p, yaw, pitch, 0f, 3.4f, CYAN, f);
                Fx.slash(buf, ps, p.subtract(dir.scale(0.9)), yaw, pitch, 0f, 2.6f, VIOLET, 0.6f * f);
                Fx.beam(buf, ps, BEAM, o, p, cam, 0.22f * f, 2f, ELECTRIC, 0.55f * f);
                Fx.glowTex(buf, ps, GLOW_BLUE, p, 3f * f, 0f, CYAN, 0.7f * f);
            } else if (d[0] == 4) {
                float f = smooth(life / 4f) * (1f - smooth((life - 14f) / 8f));
                if (f <= 0f) {
                    continue;
                }
                Vec3 p = v(d[2], d[3], d[4]).subtract(base);
                Fx.crack(buf, ps, p, 3.0f * f, (float) (d[2] * 37.0), CYAN, 0.9f * f);
                Fx.beam(buf, ps, BEAM, p, p.add(0, 3.6 * f, 0), cam, 0.32f, 1f, CYAN, 0.8f * f);
                Fx.glowTex(buf, ps, GLOW_BLUE, p.add(0, 1.2, 0), 2.4f * f, 0f, VIOLET, 0.6f * f);
            } else if (d[0] == 5) {
                Vec3 c = v(d[2], d[3] + 1.0, d[4]).subtract(base);
                float R = (float) d[5];
                float cont = smooth(life / 12f);
                float after = smooth((life - 12f) / 6f);
                if (life < 12f) {
                    float r = lerp(R, 0.7f, cont);
                    Fx.orb(buf, ps, c, r, t * 0.04f, PURPLE, 0.4f);
                    Fx.shell(buf, ps, c, r, t * 0.02f, CYAN, 0.35f);
                    Fx.glowTex(buf, ps, GLOW_VIOLET, c, r * 2.3f, 0f, VIOLET, 0.6f);
                } else if (after < 1f) {
                    Fx.wave(buf, ps, c.add(0, -0.9, 0), R * 1.2f * after + 1f, CYAN, 1f - after);
                    Fx.star(buf, ps, c, R * (1f - after), t * 12f, WHITE, 1f - after);
                }
            }
        }
    }
}
