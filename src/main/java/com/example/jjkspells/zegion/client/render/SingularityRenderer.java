package com.example.jjkspells.zegion.client.render;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.entity.SingularityEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.zegion.client.render.Gfx.*;

/** Void Carapace: a small black sphere that is compressed, thrown, and swells into a gravitational anomaly with accretion discs, rifts and rings before it folds into a point. */
public class SingularityRenderer extends ZegRenderer<SingularityEntity> {
    public SingularityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(SingularityEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base) {
        int seed = e.getSeed();
        float R = (float) (double) ZegConfig.SING.radius.get();
        float FR = (float) (double) ZegConfig.SING.finalRadius.get();
        Vec3 cam = cam(base);

        // ---- size of the sphere over time
        float size;
        if (t < SingularityEntity.COMPRESS) {
            size = 0.25f + 0.35f * smooth(t / SingularityEntity.COMPRESS);
        } else if (t < SingularityEntity.IMPACT) {
            size = 0.6f + 0.9f * smooth((t - SingularityEntity.COMPRESS) / 30f);
        } else if (t < SingularityEntity.COLLAPSE) {
            size = 1.5f + (R * 0.34f - 1.5f) * smooth((t - SingularityEntity.IMPACT) / 40f) + 0.5f * Mth.sin(t * 0.3f);
        } else if (t < SingularityEntity.HIT) {
            float k = (t - SingularityEntity.COLLAPSE) / (float) (SingularityEntity.HIT - SingularityEntity.COLLAPSE);
            size = (R * 0.34f) * (1f - smooth(k)) + 0.1f;
        } else {
            size = 0.1f;
        }
        float vis = 1f - c01((t - SingularityEntity.HIT - 2f) / 6f);
        boolean flying = t >= SingularityEntity.THROW && t < SingularityEntity.IMPACT;

        if (vis > 0.01f) {
            // the black core with its glowing rim
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * 4f));
            ps.mulPose(Axis.XP.rotationDegrees(t * 2.4f));
            Draw.sphere(trans(buf, SINGULARITY), ps, size, 14, 28, t * 0.02f, WHITE, 0.98f * vis);
            Draw.sphere(add(buf, SPHERE_GLOW), ps, size * 1.04f, 10, 20, -t * 0.015f, VIOLET, 0.5f * vis);
            Draw.sphere(add(buf, RIFT_BOLT), ps, size * 1.02f, 10, 20, t * 0.03f, PALE, 0.75f * vis);
            ps.popPose();
            ps.pushPose();
            Gfx.billboard(ps);
            Draw.xy(add(buf, GLOW), ps, size * 2.6f, size * 2.6f, DEEP, 0.55f * vis);
            Draw.xy(add(buf, BURST), ps, size * 3f, size * 3f, VIOLET, 0.3f * vis * (t >= SingularityEntity.IMPACT ? 1f : 0.4f));
            ps.popPose();

            // rotating rings (forming rings, then the giant anomaly rings)
            int rings = t < SingularityEntity.IMPACT ? 3 : 6;
            for (int i = 0; i < rings; i++) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(i * 55f + t * (2f + i)));
                ps.mulPose(Axis.XP.rotationDegrees(70f + i * 17f));
                float rr = size * (1.7f + 0.55f * i);
                Draw.disc(add(buf, ACCRETION), ps, rr, t * (i % 2 == 0 ? 5f : -4f), 0f, i % 2 == 0 ? VIOLET : PALE, 0.8f * vis);
                if (t >= SingularityEntity.IMPACT) {
                    Draw.ring(add(buf, RING_WAVE), ps, rr * 0.9f, rr * 1.05f, 0.02f, 80, 1f, t * 0.01f, DEEP, 0.55f * vis);
                }
                ps.popPose();
            }
        }

        // ---- the throw: a black-purple trail back to the hand and spatial cracks along the path
        if (flying && vis > 0.01f) {
            net.minecraft.world.entity.Entity o = e.getOwnerEntity();
            if (o instanceof net.minecraft.world.entity.LivingEntity le) {
                float f = c01((t - SingularityEntity.THROW) / (float) (SingularityEntity.IMPACT - SingularityEntity.THROW));
                Vec3 hand = SingularityEntity.hand(le).subtract(base);
                Vec3 mid = hand.lerp(new Vec3(0, 0, 0), 0.55);
                float fade = 1f - 0.6f * f;
                Draw.ribbon(trans(buf, TRAIL), ps, mid, new Vec3(0, 0, 0), cam, 0.2f, 0.9f, 0f, 1f, BLACK, 0f, 0.95f * fade);
                Draw.ribbon(add(buf, TRAIL), ps, mid, new Vec3(0, 0, 0), cam, 0.3f, 1.3f, 0f, 1f, VIOLET, 0f, 0.8f * fade);
                Draw.ribbon(add(buf, LINE), ps, mid, new Vec3(0, 0, 0), cam, 0.05f, 0.25f, 0f, 1f, PALE, 0f, fade);
            }
        }

        // ---- the impact: a shock of black and violet, the target marker and the pull
        float ti = t - SingularityEntity.IMPACT;
        if (ti >= 0f && ti < 18f) {
            float k = ti / 18f;
            Vec3 g = groundOffset(e, base);
            ps.pushPose();
            ps.translate(g.x, g.y, g.z);
            Draw.disc(add(buf, WAVE), ps, 3f + R * 1.2f * smooth(k), ti * 4f, 0.12f, VIOLET, 0.9f * (1f - k));
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, R * 1.2f * smooth(k) - 2f), R * 1.2f * smooth(k) + 1f, 0.16f, 96, 1f, 0f, PALE, 1f - k);
            ps.popPose();
        }
        if (t >= SingularityEntity.IMPACT && vis > 0.01f) {
            Vec3 g = groundOffset(e, base);
            float grow = smooth((t - SingularityEntity.IMPACT) / 20f);
            float collapsing = t >= SingularityEntity.COLLAPSE ? c01((t - SingularityEntity.COLLAPSE) / (SingularityEntity.HIT - SingularityEntity.COLLAPSE)) : 0f;
            float zr = (collapsing > 0f ? FR : R) * grow * (1f - 0.1f * collapsing);
            ps.pushPose();
            ps.translate(g.x, g.y, g.z);
            Draw.disc(add(buf, TARGET), ps, R * grow, t * 1.4f, 0.1f, VIOLET, 0.6f * vis);
            Draw.disc(add(buf, CIRCLE), ps, R * 0.85f * grow, -t * 2f, 0.12f, DEEP, 0.55f * vis);
            Draw.disc(add(buf, CRACKS), ps, R * 1.1f * grow, 9f, 0.08f, VIOLET, 0.8f * vis);
            Draw.disc(trans(buf, MIST), ps, R * grow, t * 0.3f, 0.06f, WHITE, 0.55f * vis);
            // matter streaming inwards along the ground
            Draw.band(trans(buf, MIST_WAVE), ps, zr * (1f - 0.55f * ((t * 0.03f) % 1f)), 0f, 5f, 80, 16f, t * 0.01f, WHITE, 0.6f * vis, 0f);
            Draw.band(add(buf, MIST_GLOW), ps, zr * (1f - 0.55f * ((t * 0.03f + 0.5f) % 1f)), 0f, 7f, 80, 12f, -t * 0.012f, DEEP, 0.5f * vis, 0f);
            ps.popPose();
            // spatial cracks in the air around the anomaly
            for (int i = 0; i < 8; i++) {
                float ang = i * Mth.TWO_PI / 8 + rnd(seed, i) * 0.6f + t * 0.01f;
                float rr = R * (0.35f + 0.5f * rnd(seed, i + 20)) * grow;
                float open = smooth((t - SingularityEntity.IMPACT - i * 2f) / 14f) * vis;
                ps.pushPose();
                ps.translate(Mth.cos(ang) * rr, 0f, Mth.sin(ang) * rr);
                ps.mulPose(Axis.YP.rotation(-ang + Mth.HALF_PI + (rnd(seed, i + 40) - 0.5f)));
                rift(buf, ps, 5f + 5f * rnd(seed, i + 60), 5f + 6f * rnd(seed, i + 80), open, t, 0.9f);
                ps.popPose();
            }
        }

        // ---- the collapse
        float th = t - SingularityEntity.HIT;
        if (th >= 0f && th < 26f) {
            float k = th / 26f;
            float r = FR * smooth(Math.min(1f, th / 16f));
            Vec3 g = groundOffset(e, base);
            ps.pushPose();
            ps.translate(g.x, g.y, g.z);
            Draw.disc(add(buf, WAVE), ps, r, th * 3f, 0.14f, PALE, 0.95f * (1f - k));
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 3f), r + 1f, 0.18f, 112, 1f, 0f, VIOLET, 1f - k);
            Draw.band(add(buf, RIFT_BOLT), ps, r, 0f, 10f, 80, 10f, th * 0.06f, VIOLET, 0.9f * (1f - k), 0f);
            ps.popPose();
            ps.pushPose();
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, FR * 0.6f * (1f - k * 0.4f), FR * 0.6f * (1f - k * 0.4f), PALE, 1f - k);
            Draw.xy(add(buf, GLOW), ps, FR * 0.35f, FR * 0.35f, VIOLET, 0.9f * (1f - k));
            ps.popPose();
        }
    }

    /** The ground point under the singularity, in the local space of the entity. */
    private Vec3 groundOffset(SingularityEntity e, Vec3 base) {
        return new Vec3(0, -(base.y - e.getTarget().y) - 5.0, 0);
    }
}
