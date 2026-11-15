package com.example.jjkspells.zegion.client.render;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.entity.DomainEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.zegion.client.render.Gfx.*;

/** Mist Lord's Domain: the spreading mist, the violet dome and its false sky, the giant mirrors and the twelve towering phantoms. */
public class DomainRenderer extends ZegRenderer<DomainEntity> {
    public DomainRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(DomainEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base) {
        int seed = e.getSeed();
        float R = e.getRadius();
        int n = ZegConfig.DOMAIN.phantoms.get();
        // the domain freezes for a moment before it collapses
        float tf = Math.min(t, DomainEntity.FREEZE);
        float shrink = smooth((t - DomainEntity.FREEZE - 4f) / (DomainEntity.END - DomainEntity.FREEZE - 4f));
        float grow = smooth((t - DomainEntity.SILENT) / (float) (DomainEntity.DOME - DomainEntity.SILENT));
        float radius = R * grow * (1f - 0.97f * shrink);
        float vis = grow * (1f - c01((t - DomainEntity.END + 6f) / 6f));

        // ---- the silent mist spreading over the ground
        float mistK = c01(t / (float) DomainEntity.DOME);
        if (mistK > 0f && vis > 0.01f || t < DomainEntity.DOME) {
            float mr = Math.max(radius, R * mistK * 0.9f);
            float ma = 1f - c01((t - DomainEntity.HIT - 6f) / 10f);
            Draw.band(trans(buf, MIST_WAVE), ps, mr, 0f, 4.5f, 96, 18f, tf * 0.004f, WHITE, 0.85f * ma, 0f);
            Draw.band(add(buf, MIST_GLOW), ps, mr * 0.96f, 0f, 7f, 96, 14f, -tf * 0.003f, DEEP, 0.5f * ma, 0f);
            Draw.disc(trans(buf, MIST), ps, mr, tf * 0.3f, 0.05f, WHITE, 0.6f * ma);
            Draw.disc(add(buf, CIRCLE), ps, mr * 0.9f, -tf * 0.6f, 0.07f, VIOLET, 0.35f * ma * grow);
        }
        if (radius <= 0.5f) {
            return;
        }

        // ---- the false sky: a dark violet dome with a fractured cosmic void
        float skyA = 0.92f * vis;
        ps.pushPose();
        Draw.sphere(trans(buf, SKY), ps, radius, 18, 36, tf * 0.0006f, WHITE, skyA);
        ps.mulPose(Axis.YP.rotationDegrees(tf * 0.12f));
        Draw.sphere(add(buf, RIFT_BOLT), ps, radius * 0.985f, 12, 24, tf * 0.001f, DEEP, 0.55f * vis);
        ps.popPose();

        // ---- black-purple rings turning overhead
        for (int i = 0; i < 3; i++) {
            ps.pushPose();
            ps.translate(0, radius * (0.62f + 0.12f * i), 0);
            float rr = radius * (0.55f - 0.12f * i);
            Draw.disc(add(buf, CIRCLE), ps, rr, tf * (i % 2 == 0 ? 1.1f : -0.8f), 0f, i == 1 ? PALE : VIOLET, 0.55f * vis);
            Draw.ring(add(buf, RUNE), ps, rr * 0.8f, rr * 1.3f, 0.2f, 72, 1f, tf * 0.004f, DEEP, 0.45f * vis);
            ps.popPose();
        }

        // ---- enormous reflective structures
        int mirrors = 14;
        float rot = c01((tf - DomainEntity.SHIFT) / 60f) * 90f;
        float shatter = c01((t - DomainEntity.FREEZE) / 18f);
        float mirrorA = 0.8f * vis * (1f - shatter);
        if (mirrorA > 0.01f) {
            for (int i = 0; i < mirrors; i++) {
                float ang = i * Mth.TWO_PI / mirrors + rnd(seed, i) * 0.3f;
                float rr = radius * (0.72f + 0.18f * rnd(seed, i + 20));
                float h = 14f + rnd(seed, i + 40) * 12f;
                ps.pushPose();
                ps.translate(Mth.cos(ang) * rr, h * 0.62f + Mth.sin(tf * 0.03f + i) * 0.8f, Mth.sin(ang) * rr);
                ps.mulPose(Axis.YP.rotation(-ang + Mth.HALF_PI));
                ps.mulPose(Axis.YP.rotationDegrees(rot * (i % 2 == 0 ? 1 : -1) + (rnd(seed, i + 60) - 0.5f) * 30f));
                ps.mulPose(Axis.ZP.rotationDegrees((rnd(seed, i + 80) - 0.5f) * 14f));
                mirror(buf, ps, 7f + rnd(seed, i + 100) * 4f, h, mirrorA, 0.9f * vis * (1f - shatter));
                ps.popPose();
            }
        }

        // ---- the phantoms
        Vec3 off = e.getTargetOffset();
        float ph = 15f;
        for (int i = 0; i < n; i++) {
            float appear = smooth((t - DomainEntity.DOME - i * 1.6f) / 14f);
            if (appear <= 0.01f) {
                continue;
            }
            double[] p = DomainEntity.phantomPos(seed, i, n, (int) tf, R, off.x, off.z);
            double[] p2 = DomainEntity.phantomPos(seed, i, n, (int) tf + 1, R, off.x, off.z);
            float f = tf - (int) tf;
            float px = (float) (p[0] + (p2[0] - p[0]) * f), pz = (float) (p[1] + (p2[1] - p[1]) * f);
            float a = appear;
            if (tf >= DomainEntity.SHIFT && tf < DomainEntity.GATHER) {
                float since = (tf - DomainEntity.SHIFT) % 20f;
                a *= Math.min(smooth(since / 4f), 1f - smooth((since - 16f) / 4f));
            }
            a *= vis;
            float bob = Mth.sin(tf * 0.07f + i * 1.3f) * 0.5f;
            phantom(buf, ps, base, px, bob, pz, ph, 0.85f * a, 1.0f * a, i % 3 == 0 ? PALE : VIOLET);
            attack(buf, ps, base, px, pz, i, tf, a, ph);
        }

        // ---- the mirror execution: everything flies at the central target
        if (t >= DomainEntity.GATHER && t < DomainEntity.HIT) {
            float k = smooth((t - DomainEntity.GATHER) / (DomainEntity.LAUNCH - DomainEntity.GATHER));
            float ex = (float) off.x, ey = (float) off.y, ez = (float) off.z;
            float burst = c01((t - DomainEntity.LAUNCH) / 12f);
            float sa = (1f - 0.7f * burst) * vis;
            ps.pushPose();
            ps.translate(ex, ey + 1.2f, ez);
            Draw.sphere(trans(buf, SINGULARITY), ps, 0.5f + 4.2f * k + 2f * burst, 12, 24, tf * 0.01f, WHITE, 0.95f * sa);
            Draw.sphere(add(buf, SPHERE_GLOW), ps, 0.55f + 4.4f * k + 2f * burst, 10, 20, -tf * 0.01f, VIOLET, 0.6f * sa);
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, 7f * k + 5f * burst, 7f * k + 5f * burst, PALE, 0.55f * sa);
            ps.popPose();
            if (t >= DomainEntity.LAUNCH && t < DomainEntity.LAUNCH + 8f) {
                float la = 1f - (t - DomainEntity.LAUNCH) / 8f;
                Vec3 cam = cam(base);
                for (int i = 0; i < n; i++) {
                    double[] p = DomainEntity.phantomPos(seed, i, n, DomainEntity.LAUNCH - 1, R, off.x, off.z);
                    Vec3 a = new Vec3(p[0], 6, p[1]);
                    Vec3 b = new Vec3(ex, ey + 1.2, ez);
                    Draw.ribbon(add(buf, TRAIL), ps, a, b, cam, 1.4f * la, 0.4f * la, 0f, 1f, PALE, 0.9f * la, 0.9f * la);
                    Draw.ribbon(add(buf, LINE), ps, a, b, cam, 0.3f * la, 0.3f * la, 0f, 1f, WHITE, la, la);
                }
            }
        }

        // ---- the sky cracks as the domain freezes and falls into a point
        if (t >= DomainEntity.FREEZE) {
            float ck = c01((t - DomainEntity.FREEZE) / 10f);
            Draw.sphere(add(buf, RIFT_BOLT), ps, radius * 0.97f, 12, 24, 0.37f, PALE, 0.85f * ck * vis);
            Draw.sphere(add(buf, SPHERE_GLOW), ps, radius * 0.96f, 10, 20, 0f, VIOLET, 0.35f * ck * vis);
        }
        if (t >= DomainEntity.HIT && t < DomainEntity.HIT + 14f) {
            float k = (t - DomainEntity.HIT) / 14f;
            ps.pushPose();
            ps.translate(0, 2f, 0);
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, R * 0.45f * (1f + k), R * 0.45f * (1f + k), PALE, 1f - k);
            Draw.xy(add(buf, GLOW), ps, R * 0.3f, R * 0.3f, VIOLET, 0.9f * (1f - k));
            ps.popPose();
            Draw.disc(add(buf, WAVE), ps, R * (0.2f + 0.9f * k), t * 3f, 0.12f, PALE, 0.9f * (1f - k));
        }
    }

    /** The martial attack a phantom performs: a thrust, a crushing kick or a sweeping blade. */
    private void attack(MultiBufferSource buf, PoseStack ps, Vec3 base, float px, float pz, int i, float t, float a, float ph) {
        float cyc = ((t * 0.045f + i * 0.37f) % 1f);
        if (cyc > 0.22f || t < DomainEntity.DOME + 10 || t >= DomainEntity.GATHER) {
            return;
        }
        float k = cyc / 0.22f;
        float bell = Mth.sin(k * Mth.PI);
        ps.pushPose();
        ps.translate(px, ph * 0.5f, pz);
        faceCamera(ps, base, px, ph * 0.5f, pz);
        switch (i % 3) {
            case 0 -> { // piercing strike
                ps.translate(0, 1f, 0);
                Draw.xyAt(add(buf, LINE), ps, 0.9f * bell, ph * 0.9f, 0f, PALE, a * bell);
                ps.mulPose(Axis.ZP.rotationDegrees(90));
                Draw.xyAt(add(buf, TRAIL), ps, 1.6f * bell, ph * 0.8f, 0f, VIOLET, a * bell);
            }
            case 1 -> { // crushing kick from above
                ps.translate(0, -ph * 0.2f, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(-30 + k * 60f));
                Draw.xyAt(add(buf, CRESCENT), ps, ph * 0.6f, ph * 0.6f, ph * 0.2f, VIOLET, a * bell);
            }
            default -> { // sweeping blade
                ps.translate(0, 0, 0.5f);
                ps.mulPose(Axis.ZP.rotationDegrees(160 - k * 220f));
                Draw.xyAt(add(buf, CRESCENT), ps, ph * 0.75f, ph * 0.75f, ph * 0.1f, PALE, a * bell);
            }
        }
        ps.popPose();
    }
}
