package com.example.jjkspells.zegion.client.render;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.entity.AssaultEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.zegion.client.render.Gfx.*;

/** Phantom Kaiser: the vanishing flash, a dozen violet afterimages striking from every side, the gathering sphere, the dimensional drop and the phantom collapse. */
public class AssaultRenderer extends ZegRenderer<AssaultEntity> {
    public AssaultRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(AssaultEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base) {
        int seed = e.getSeed();
        int n = ZegConfig.PHANTOM.afterimages.get();
        float BR = e.getBlastRadius();
        Vec3 cam = cam(base);

        // ---- the predator's silence: faint images trail behind Zegion, then he vanishes in a flash
        if (t < AssaultEntity.VANISH + 6) {
            Entity o = e.getOwnerEntity();
            if (o != null) {
                Vec3 op = new Vec3(Mth.lerp(pt, o.xo, o.getX()), Mth.lerp(pt, o.yo, o.getY()), Mth.lerp(pt, o.zo, o.getZ())).subtract(base);
                float k = c01(t / AssaultEntity.SILENCE);
                float fade = 1f - c01((t - AssaultEntity.VANISH) / 6f);
                for (int i = 0; i < 4; i++) {
                    float a = k * (0.5f - 0.1f * i) * fade;
                    Vec3 d = new Vec3(Mth.cos(i * 1.7f + seed) * 0.6f * (i + 1), 0, Mth.sin(i * 1.7f + seed) * 0.6f * (i + 1));
                    phantom(buf, ps, base, (float) (op.x + d.x), (float) op.y, (float) (op.z + d.z), 2.0f, 0.5f * a, a, VIOLET);
                }
                Draw.disc(add(buf, CIRCLE), ps, 2.2f * k, t * 6f, 0.07f, VIOLET, 0.6f * fade * (float) 1);
                if (t >= AssaultEntity.VANISH && t < AssaultEntity.VANISH + 6) {
                    ps.pushPose();
                    ps.translate(op.x, op.y + 1f, op.z);
                    Gfx.billboard(ps);
                    float f = 1f - (t - AssaultEntity.VANISH) / 6f;
                    Draw.xy(add(buf, BURST), ps, 4f * (1.4f - f), 4f * (1.4f - f), PALE, f);
                    ps.popPose();
                }
            }
        }
        if (t < AssaultEntity.VANISH) {
            return;
        }

        // ---- the afterimages
        float tt = t - AssaultEntity.VANISH;
        float appear = smooth(tt / 8f);
        float gather = c01((t - AssaultEntity.ASSAULT) / (AssaultEntity.DROP - AssaultEntity.ASSAULT));
        float det = t >= AssaultEntity.BLAST ? c01((t - AssaultEntity.BLAST) / 10f) : 0f;
        float imgA = appear * (1f - det);
        if (imgA > 0.01f) {
            Vec3 prev = null;
            for (int i = 0; i < n; i++) {
                float a0 = i * Mth.TWO_PI / n + rnd(seed, i) * 0.4f;
                float spin = t < AssaultEntity.ASSAULT ? tt * 0.01f * (i % 2 == 0 ? 1 : -1) : (t - AssaultEntity.ASSAULT) * (0.05f + 0.1f * gather);
                float a = a0 + spin;
                float r = t < AssaultEntity.ASSAULT ? 3.2f + 2.6f * rnd(seed, i + 7) : (4.4f - 1.6f * gather);
                float x = Mth.cos(a) * r, z = Mth.sin(a) * r;
                float flick = t < AssaultEntity.ASSAULT ? 0.55f + 0.45f * Mth.sin(t * 1.3f + i * 2.1f) : 1f;
                float bright = (0.55f + 0.7f * gather) * flick;
                phantom(buf, ps, base, x, 0f, z, 2.3f, 0.6f * imgA * flick, Math.min(1.2f, bright) * imgA, gather > 0.5f ? PALE : VIOLET);
                // an attack stance: a fist punches towards the target for a moment
                if (t < AssaultEntity.ASSAULT) {
                    float cyc = (t * 0.2f + i * 0.31f) % 1f;
                    if (cyc < 0.25f) {
                        float bell = Mth.sin(cyc / 0.25f * Mth.PI);
                        ps.pushPose();
                        ps.translate(x, 1.2f, z);
                        ps.mulPose(Axis.YP.rotation(-a + Mth.PI));
                        ps.mulPose(Axis.ZP.rotationDegrees(0));
                        Draw.xyAt(add(buf, ARC), ps, 1.3f * bell, 0.5f * bell, 0f, i % 2 == 0 ? PALE : VIOLET, 0.9f * bell * imgA);
                        ps.popPose();
                    }
                }
                Vec3 p = new Vec3(x, 1.0, z);
                if (prev != null && imgA > 0.2f && t < AssaultEntity.ASSAULT) {
                    Draw.ribbon(trans(buf, TRAIL), ps, prev, p, cam, 0.06f, 0.06f, 0f, 1f, BLACK, 0.55f * imgA, 0.55f * imgA);
                    Draw.ribbon(add(buf, TRAIL), ps, prev, p, cam, 0.1f, 0.1f, 0f, 1f, DEEP, 0.4f * imgA, 0.4f * imgA);
                }
                prev = p;
            }
        }

        // ---- every strike: a crescent of black light and a small spatial shockwave
        float since = t - e.getStrikeAge();
        if (since >= 0f && since < 7f && t < AssaultEntity.ASSAULT + 2) {
            Vec3 sp = e.getStrikePos().subtract(base);
            float k = since / 7f;
            ps.pushPose();
            ps.translate(sp.x, sp.y, sp.z);
            Gfx.billboard(ps);
            ps.mulPose(Axis.ZP.rotationDegrees((e.getStrikes() * 67 % 360)));
            Draw.xyAt(add(buf, CRESCENT), ps, 2.4f * (0.6f + k), 2.4f * (0.6f + k), -0.6f, PALE, 1f - k);
            Draw.xyAt(trans(buf, CRESCENT), ps, 2.3f * (0.6f + k), 2.3f * (0.6f + k), -0.6f, BLACK, 0.8f * (1f - k));
            Draw.xy(add(buf, BURST), ps, 1.8f * (1f - k) + 0.3f, 1.8f * (1f - k) + 0.3f, VIOLET, 1f - k);
            ps.popPose();
            ps.pushPose();
            ps.translate(sp.x, sp.y, sp.z);
            Draw.ring(add(buf, RING_WAVE), ps, 0.4f + k * 2.2f, 0.6f + k * 2.8f, 0f, 24, 1f, 0f, VIOLET, 0.7f * (1f - k));
            ps.popPose();
        }

        // ---- the thousand-fold strike: a dark sphere forms around the target
        if (t >= AssaultEntity.ASSAULT && t < AssaultEntity.BLAST + 2) {
            float k = smooth(gather);
            float sr = (0.3f + 3.2f * k) * (1f - det);
            ps.pushPose();
            ps.translate(0, 1.1f, 0);
            Draw.sphere(trans(buf, SINGULARITY), ps, sr, 12, 24, t * 0.01f, WHITE, 0.85f * (1f - det));
            Draw.sphere(add(buf, SPHERE_GLOW), ps, sr * 1.08f, 10, 20, -t * 0.012f, VIOLET, 0.6f * k * (1f - det));
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, 5f * k, 5f * k, PALE, 0.5f * k * (1f - det));
            ps.popPose();
            Draw.disc(add(buf, CIRCLE), ps, 4.6f * (0.4f + 0.6f * k), t * 5f, 0.07f, VIOLET, 0.8f * k * (1f - det));
            Draw.disc(add(buf, TARGET), ps, 6f - 2f * k, -t * 3f, 0.09f, PALE, 0.5f * k * (1f - det));
            // the beam Zegion will fall along
            if (t >= AssaultEntity.GATHER && t < AssaultEntity.DROP) {
                float b = smooth((t - AssaultEntity.GATHER) / 8f);
                Draw.cross(add(buf, LINE), ps, 0.9f, 0f, 16f, t * 10f, PALE, 0.9f * b, 15728880);
            }
        }

        // ---- the dimensional execution: Zegion lands
        float td = t - AssaultEntity.DROP;
        if (td >= 0f && td < 16f) {
            float k = td / 16f;
            Draw.disc(add(buf, WAVE), ps, 2f + 11f * smooth(k), td * 4f, 0.1f, PALE, 0.9f * (1f - k));
            Draw.ring(add(buf, RING_WAVE), ps, 1f + 10f * smooth(k), 2.4f + 11f * smooth(k), 0.14f, 72, 1f, 0f, VIOLET, 1f - k);
            Draw.disc(add(buf, CRACKS), ps, 9f, 33f, 0.05f, DEEP, 0.9f * (1f - k * 0.6f));
            ps.pushPose();
            ps.translate(0, 1.2f, 0);
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, 8f * (1f - k * 0.5f), 8f * (1f - k * 0.5f), PALE, 1f - k);
            ps.popPose();
        }

        // ---- the phantom collapse
        float tc = t - AssaultEntity.BLAST;
        if (tc >= 0f && tc < 24f) {
            float k = tc / 24f;
            float r = BR * smooth(Math.min(1f, tc / 14f));
            Draw.disc(add(buf, WAVE), ps, r, tc * 3f, 0.12f, VIOLET, 0.9f * (1f - k));
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 2.4f), r + 0.8f, 0.16f, 96, 1f, 0f, PALE, 1f - k);
            Draw.band(add(buf, RIFT_BOLT), ps, r, 0f, 6f, 72, 8f, tc * 0.06f, VIOLET, 0.9f * (1f - k), 0f);
            ps.pushPose();
            ps.translate(0, 1.5f, 0);
            Draw.sphere(trans(buf, SINGULARITY), ps, r * 0.35f * (1f - k), 12, 24, tc * 0.03f, WHITE, 0.9f * (1f - k));
            Draw.sphere(add(buf, SPHERE_GLOW), ps, r * 0.4f, 10, 20, tc * 0.03f, PALE, 0.6f * (1f - k));
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, r * 0.6f, r * 0.6f, PALE, 0.7f * (1f - k));
            ps.popPose();
        }
    }
}
