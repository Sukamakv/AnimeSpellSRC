package com.example.jjkspells.zegion.client.render;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.entity.MephistoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.zegion.client.render.Gfx.*;

/**
 * Illusion King Mephisto: the violet mist and the dome of a fractured cosmic sky, mirrored structures joined by spectral chains,
 * twelve towering phantoms, a celestial sphere that eats the artificial world, and the final annihilation.
 */
public class MephistoRenderer extends ZegRenderer<MephistoEntity> {
    private static final int STRUCTS = 16;

    public MephistoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(MephistoEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base) {
        var c = ZegConfig.MEPHISTO;
        int seed = e.getSeed();
        float R = (float) (double) c.domainRadius.get();
        int n = c.phantoms.get();
        Vec3 cam = cam(base);

        Entity owner = e.getOwnerEntity();
        Vec3 op = Vec3.ZERO;
        float yaw = 0f;
        if (owner instanceof LivingEntity le) {
            op = new Vec3(Mth.lerp(pt, le.xo, le.getX()), Mth.lerp(pt, le.yo, le.getY()), Mth.lerp(pt, le.zo, le.getZ())).subtract(base);
            yaw = Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot);
        }

        // ---- timeline factors
        float mistK = smooth(t / (float) MephistoEntity.DOME);
        float domeK = smooth((t - MephistoEntity.DOME) / 40f);
        float folding = smooth((t - MephistoEntity.SPHERE) / (MephistoEntity.COLLAPSE - MephistoEntity.SPHERE));
        float shatterK = smooth((t - MephistoEntity.FINAL) / 10f);
        float shatterEarly = smooth((t - MephistoEntity.RISE) / 50f);
        float vis = (1f - shatterK) * (1f - c01((t - MephistoEntity.END + 20f) / 20f) * 0f);
        float domeR = R * domeK * (1f - 0.3f * folding) * (1f - 0.1f * shatterEarly);
        float end = 1f - c01((t - MephistoEntity.FINAL - 20f) / 30f);

        // ---- phase 1: the sovereign of illusions - mist across the battlefield, rings in the sky, wings
        float mr = R * mistK;
        if (mr > 0.5f && end > 0.01f) {
            Draw.band(trans(buf, MIST_WAVE), ps, mr, 0f, 6f, 112, 26f, t * 0.003f, WHITE, 0.9f * end, 0f);
            Draw.band(add(buf, MIST_GLOW), ps, mr * 0.97f, 0f, 10f, 112, 20f, -t * 0.003f, DEEP, 0.5f * end, 0f);
            Draw.disc(trans(buf, MIST), ps, mr, t * 0.2f, 0.05f, WHITE, 0.55f * end);
            Draw.disc(add(buf, CIRCLE), ps, mr * 0.85f, -t * 0.5f, 0.07f, VIOLET, 0.4f * end * domeK);
        }
        if (t < MephistoEntity.LAND + 10 && owner != null) {
            float wk = smooth(t / 40f) * (1f - c01((t - MephistoEntity.LAND) / 10f));
            ps.pushPose();
            ps.translate(op.x, op.y + 1.55f, op.z - 0.0f);
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.translate(0, 0, -0.28f);
            float big = t >= MephistoEntity.RISE ? 1f : 0.75f;
            float len = 16f * wk * big;
            for (int side = -1; side <= 1; side += 2) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(-side * 14f));
                ps.mulPose(Axis.ZP.rotationDegrees(side * (Mth.lerp(wk, -100f, 8f) + 3f * Mth.sin(t * 0.1f))));
                wing(buf, ps, len, side, 0.9f * wk, VIOLET);
                wing(buf, ps, len * 1.03f, side, 0.3f * wk, PALE);
                ps.popPose();
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(-side * 22f));
                ps.mulPose(Axis.ZP.rotationDegrees(side * (Mth.lerp(wk, -120f, -38f))));
                wing(buf, ps, len * 0.66f, side, 0.75f * wk, VIOLET);
                ps.popPose();
            }
            ps.popPose();
            // glowing rings gather in the sky above the caster
            for (int i = 0; i < 3; i++) {
                ps.pushPose();
                ps.translate(op.x, op.y + 6f + i * 3.5f, op.z);
                Draw.disc(add(buf, CIRCLE), ps, (5f + i * 2.5f) * wk, t * (i % 2 == 0 ? 3f : -2f), 0f, i == 1 ? PALE : VIOLET, 0.65f * wk);
                ps.popPose();
            }
        }
        if (domeR <= 1f) {
            return;
        }

        // ---- phase 2: the dome, a fractured cosmic void
        if (vis > 0.01f) {
            ps.pushPose();
            Draw.sphere(trans(buf, SKY), ps, domeR, 20, 40, t * 0.0005f, WHITE, 0.95f * vis * end);
            ps.mulPose(Axis.YP.rotationDegrees(t * 0.1f));
            Draw.sphere(add(buf, RIFT_BOLT), ps, domeR * 0.985f, 12, 24, t * 0.0012f, DEEP, (0.5f + 0.5f * shatterEarly) * vis * end);
            ps.popPose();
        }

        // ---- the mirrored structures and the chains that bind them
        float structA = domeK * vis * (1f - smooth((t - MephistoEntity.COLLAPSE - 20f) / 50f)) * end;
        Vec3[] pos = new Vec3[STRUCTS];
        Vec3 sphereC = new Vec3(0, R * 0.55f, 0);
        for (int i = 0; i < STRUCTS; i++) {
            float ang = i * Mth.TWO_PI / STRUCTS + rnd(seed, i) * 0.2f;
            float rr = domeR * (0.78f + 0.12f * rnd(seed, i + 20));
            float hy = R * (0.28f + 0.3f * rnd(seed, i + 40));
            Vec3 p = new Vec3(Mth.cos(ang) * rr, hy, Mth.sin(ang) * rr);
            // the structures are dragged towards the sphere while the sky folds
            p = p.lerp(sphereC, folding * 0.8f);
            pos[i] = p;
            if (structA <= 0.02f) {
                continue;
            }
            float h = 22f + rnd(seed, i + 60) * 18f;
            float spin = t >= MephistoEntity.FIRST ? (t - MephistoEntity.FIRST) * (i % 2 == 0 ? 0.6f : -0.5f) : 0f;
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            ps.mulPose(Axis.YP.rotation(-ang + Mth.HALF_PI));
            ps.mulPose(Axis.YP.rotationDegrees(spin + (rnd(seed, i + 80) - 0.5f) * 40f));
            ps.mulPose(Axis.ZP.rotationDegrees((rnd(seed, i + 100) - 0.5f) * 20f));
            mirror(buf, ps, 12f + rnd(seed, i + 120) * 6f, h, 0.85f * structA, 0.95f * structA);
            ps.popPose();
        }
        // spectral chains between neighbouring structures and down to the sphere
        float chainA = smooth((t - MephistoEntity.DOME - 20f) / 30f) * (1f - smooth((t - MephistoEntity.SPHERE) / 40f)) * vis * end;
        if (chainA > 0.02f) {
            for (int i = 0; i < STRUCTS; i++) {
                Vec3 a = pos[i], b = pos[(i + 1) % STRUCTS];
                float len = (float) a.distanceTo(b);
                Draw.ribbon(add(buf, CHAIN), ps, a, b, cam, 0.9f, 0.9f, 0f, len / 4f, VIOLET, 0.8f * chainA, 0.8f * chainA);
                if (i % 2 == 0) {
                    Vec3 c2 = sphereC.add(0, 4, 0);
                    float l2 = (float) a.distanceTo(c2);
                    Draw.ribbon(add(buf, CHAIN), ps, a, c2, cam, 0.7f, 0.7f, 0f, l2 / 4f, DEEP, 0.55f * chainA, 0.55f * chainA);
                }
            }
        }

        // ---- phase 3: the twelve towering Kaisers
        for (int i = 0; i < n; i++) {
            float ang = i * Mth.TWO_PI / n + rnd(seed, 500) * 6f + t * 0.002f;
            float ring = R * 0.52f;
            float appear = smooth((t - MephistoEntity.GIANTS - i * 2.5f) / 20f);
            if (appear <= 0.01f) {
                continue;
            }
            float launch = c01((t - MephistoEntity.LAUNCH - i * 0.6f) / 18f);
            float L = launch * launch * (3f - 2f * launch);
            float x = Mth.cos(ang) * ring * (1f - L * 0.92f), z = Mth.sin(ang) * ring * (1f - L * 0.92f);
            float a = appear * (1f - c01((t - MephistoEntity.FIRST + 2f) / 8f) * (t >= MephistoEntity.FIRST ? 1f : 0f));
            float h = 20f + 3f * Mth.sin(t * 0.04f + i);
            phantom(buf, ps, base, x, L * 3f, z, h, 0.8f * a * vis, 1.0f * a * vis, i % 3 == 0 ? PALE : VIOLET);
            // spatial cracks gather behind each phantom
            if (L < 0.2f) {
                ps.pushPose();
                ps.translate(Mth.cos(ang) * (ring + 9f), h * 0.45f, Mth.sin(ang) * (ring + 9f));
                ps.mulPose(Axis.YP.rotation(-ang + Mth.HALF_PI));
                rift(buf, ps, 6f, 12f, 0.8f * appear, t, 0.8f * vis);
                ps.popPose();
            }
            // the launch: each phantom streaks to the centre
            if (t >= MephistoEntity.LAUNCH && t < MephistoEntity.FIRST) {
                Vec3 a0 = new Vec3(Mth.cos(ang) * ring, h * 0.5f, Mth.sin(ang) * ring);
                Vec3 b0 = new Vec3(x, h * 0.5f, z);
                Draw.ribbon(add(buf, TRAIL), ps, a0, b0, cam, 3f, 1f, 0f, 1f, PALE, 0.8f * (1f - L), 0.1f);
            }
        }

        // ---- phase 4: the first judgment
        float tf = t - MephistoEntity.FIRST;
        if (tf >= 0f && tf < 24f) {
            float k = tf / 24f;
            float r = c.firstRadius.get().floatValue() * smooth(Math.min(1f, tf / 14f));
            wave(buf, ps, r, tf, k, PALE);
            ps.pushPose();
            ps.translate(0, 6f, 0);
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, 24f * (1f - k * 0.5f), 24f * (1f - k * 0.5f), PALE, 1f - k);
            ps.popPose();
        }

        // ---- phase 5: dimensional execution: blades come down from the structures
        if (t >= MephistoEntity.EXEC_START && t < MephistoEntity.EXEC + 12) {
            float k = c01((t - MephistoEntity.EXEC_START) / (float) (MephistoEntity.EXEC - MephistoEntity.EXEC_START));
            for (int i = 0; i < STRUCTS; i++) {
                float ang = i * Mth.TWO_PI / STRUCTS + rnd(seed, i + 700);
                float rr = R * (0.15f + 0.65f * rnd(seed, i + 720));
                float fall = k * k;
                float y = Mth.lerp(fall, R * 0.55f, 4f);
                float a = Mth.sin(c01(k * 1.1f) * Mth.PI * 0.5f + 0.0f) * (1f - c01((t - MephistoEntity.EXEC) / 10f));
                ps.pushPose();
                ps.translate(Mth.cos(ang) * rr, y, Mth.sin(ang) * rr);
                ps.mulPose(Axis.YP.rotation(-ang + Mth.HALF_PI));
                Draw.xyAt(add(buf, SPEAR), ps, 2.4f, 16f, -8f, PALE, a);
                ps.popPose();
                ps.pushPose();
                ps.translate(Mth.cos(ang) * rr, 0.3f, Mth.sin(ang) * rr);
                Draw.disc(add(buf, TARGET), ps, 3.5f * k, t * 6f, 0.05f, VIOLET, 0.8f * a);
                ps.popPose();
            }
        }
        float te = t - MephistoEntity.EXEC;
        if (te >= 0f && te < 26f) {
            float k = te / 26f;
            wave(buf, ps, c.execRadius.get().floatValue() * smooth(Math.min(1f, te / 14f)), te, k, VIOLET);
            ps.pushPose();
            ps.translate(0, 8f, 0);
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, 30f * (1f - k * 0.5f), 30f * (1f - k * 0.5f), VIOLET, 1f - k);
            ps.popPose();
        }

        // ---- phase 6: the celestial sphere and the vortex beneath it
        float sphereGrow = smooth((t - MephistoEntity.GIANTS - 20f) / 60f);
        float sphereBig = smooth((t - MephistoEntity.SPHERE) / (MephistoEntity.COLLAPSE - MephistoEntity.SPHERE));
        float sr = (6f + 8f * sphereGrow + 14f * sphereBig) * (t >= MephistoEntity.COLLAPSE ? 1f - smooth((t - MephistoEntity.COLLAPSE) / 6f) : 1f);
        if (t >= MephistoEntity.GIANTS && sr > 0.2f && t < MephistoEntity.COLLAPSE + 8f) {
            ps.pushPose();
            ps.translate(sphereC.x, sphereC.y, sphereC.z);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * 1.2f));
            ps.mulPose(Axis.XP.rotationDegrees(20f));
            Draw.sphere(trans(buf, SPHERE), ps, sr, 18, 36, t * 0.001f, WHITE, 0.98f * sphereGrow);
            Draw.sphere(add(buf, SPHERE_GLOW), ps, sr * 1.03f, 12, 24, -t * 0.002f, VIOLET, 0.7f * sphereGrow);
            ps.popPose();
            // violet rings rotating around its surface and mirrored fragments in orbit
            for (int i = 0; i < 4; i++) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(i * 47f + t * (1.5f + i * 0.5f)));
                ps.mulPose(Axis.XP.rotationDegrees(65f + i * 12f));
                float rr = sr * (1.5f + 0.35f * i);
                Draw.disc(add(buf, ACCRETION), ps, rr, t * (i % 2 == 0 ? 3f : -2f), 0f, i % 2 == 0 ? VIOLET : PALE, 0.75f * sphereGrow);
                Draw.ring(add(buf, RING_WAVE), ps, rr * 0.92f, rr * 1.04f, 0.02f, 96, 1f, t * 0.005f, DEEP, 0.5f * sphereGrow);
                ps.popPose();
            }
            for (int i = 0; i < 18; i++) {
                float a = i * Mth.TWO_PI / 18f + t * 0.03f * (0.6f + rnd(seed, i + 900));
                float rr = sr * (1.15f + 0.5f * rnd(seed, i + 920));
                ps.pushPose();
                ps.translate(Mth.cos(a) * rr, (rnd(seed, i + 940) - 0.5f) * sr * 1.4f, Mth.sin(a) * rr);
                Gfx.billboard(ps);
                ps.mulPose(Axis.ZP.rotationDegrees(t * 4f + i * 40f));
                mirror(buf, ps, 1.8f, 3.2f, 0.8f * sphereGrow, 0.9f * sphereGrow);
                ps.popPose();
            }
            ps.popPose();
        }
        if (t >= MephistoEntity.SPHERE && t < MephistoEntity.COLLAPSE + 6f) {
            // the vortex beneath it
            float v = smooth((t - MephistoEntity.SPHERE) / 20f);
            ps.pushPose();
            Draw.disc(add(buf, ACCRETION), ps, R * 0.75f * v, t * 7f, 0.12f, VIOLET, 0.75f * v);
            Draw.disc(add(buf, ACCRETION), ps, R * 0.5f * v, -t * 9f, 0.16f, PALE, 0.7f * v);
            Draw.disc(add(buf, CRACKS), ps, R * 0.7f * v, t, 0.1f, DEEP, 0.8f * v);
            ps.popPose();
        }
        float tc = t - MephistoEntity.COLLAPSE;
        if (tc >= 0f && tc < 28f) {
            float k = tc / 28f;
            wave(buf, ps, c.collapseRadius.get().floatValue() * smooth(Math.min(1f, tc / 14f)), tc, k, PALE);
            ps.pushPose();
            ps.translate(sphereC.x, sphereC.y, sphereC.z);
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, 55f * (1f - k * 0.6f), 55f * (1f - k * 0.6f), PALE, 1f - k);
            Draw.xy(add(buf, GLOW), ps, 34f, 34f, VIOLET, 0.95f * (1f - k));
            ps.popPose();
        }

        // ---- phase 7 and 8: the singularity above Zegion, then the annihilation
        float singK = smooth((t - MephistoEntity.RISE) / (MephistoEntity.FINAL - MephistoEntity.RISE));
        float comp = smooth((t - MephistoEntity.CLOSE) / (MephistoEntity.FINAL - MephistoEntity.CLOSE));
        if (t >= MephistoEntity.RISE && t < MephistoEntity.FINAL + 3f && owner != null) {
            float s = (2f + 7f * singK) * (1f - 0.96f * comp);
            ps.pushPose();
            ps.translate(op.x, op.y + 9f, op.z);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(t * 5f));
            ps.mulPose(Axis.XP.rotationDegrees(t * 3f));
            Draw.sphere(trans(buf, SINGULARITY), ps, s, 14, 28, t * 0.02f, WHITE, 0.98f);
            Draw.sphere(add(buf, RIFT_BOLT), ps, s * 1.03f, 10, 20, t * 0.03f, PALE, 0.7f);
            ps.popPose();
            for (int i = 0; i < 3; i++) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(i * 60f + t * (2f + i)));
                ps.mulPose(Axis.XP.rotationDegrees(75f));
                Draw.disc(add(buf, ACCRETION), ps, s * (2.2f + 0.7f * i), t * 6f, 0f, VIOLET, 0.8f * (1f - comp * 0.5f));
                ps.popPose();
            }
            Gfx.billboard(ps);
            Draw.xy(add(buf, GLOW), ps, s * 3f, s * 3f, DEEP, 0.6f);
            ps.popPose();
        }
        float tF = t - MephistoEntity.FINAL;
        if (tF >= 0f && tF < 50f) {
            float k = tF / 50f;
            float FR = c.finalRadius.get().floatValue();
            float r = FR * smooth(Math.min(1f, tF / 26f));
            Draw.disc(add(buf, WAVE), ps, r, tF * 2.5f, 0.16f, PALE, 0.95f * (1f - k));
            for (int i = 0; i < 3; i++) {
                float rr = r * (1f - 0.18f * i);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, rr - 4f), rr + 1.2f, 0.18f + i * 0.04f, 128, 1f, 0f, i == 1 ? VIOLET : PALE, (1f - k) * (1f - 0.2f * i));
            }
            Draw.band(add(buf, RIFT_BOLT), ps, r, 0f, 16f, 96, 14f, tF * 0.05f, VIOLET, 0.9f * (1f - k), 0f);
            Draw.band(trans(buf, MIST_WAVE), ps, r * 0.97f, 0f, 14f, 96, 24f, tF * 0.02f, WHITE, 0.8f * (1f - k), 0f);
            Draw.disc(add(buf, CRACKS), ps, Math.min(FR, r * 1.05f), 3f, 0.06f, DEEP, 0.9f * (1f - k * 0.5f));
            ps.pushPose();
            ps.translate(op.x, op.y + 9f, op.z);
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, FR * 0.8f * (1f - k * 0.4f), FR * 0.8f * (1f - k * 0.4f), PALE, 1f - k);
            Draw.xy(add(buf, GLOW), ps, FR * 0.5f, FR * 0.5f, VIOLET, 0.95f * (1f - k));
            ps.popPose();
            // fragments of the shattered dimension
            for (int i = 0; i < 40; i++) {
                float a = rnd(seed, i + 1200) * Mth.TWO_PI;
                float rr = r * (0.2f + 0.8f * rnd(seed, i + 1240));
                ps.pushPose();
                ps.translate(Mth.cos(a) * rr, 4f + rnd(seed, i + 1280) * R * 0.5f - k * 12f, Mth.sin(a) * rr);
                Gfx.billboard(ps);
                ps.mulPose(Axis.ZP.rotationDegrees(tF * 6f + i * 31f));
                mirror(buf, ps, 3f, 5.5f, 0.85f * (1f - k), 0.9f * (1f - k));
                ps.popPose();
            }
        }
    }

    /** A flat expanding shockwave: disc, ring and a lit wall. */
    private void wave(MultiBufferSource buf, PoseStack ps, float r, float age, float k, float[] col) {
        Draw.disc(add(buf, WAVE), ps, r, age * 3f, 0.14f, col, 0.9f * (1f - k));
        Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 3f), r + 1f, 0.18f, 128, 1f, 0f, PALE, 1f - k);
        Draw.band(add(buf, RIFT_BOLT), ps, r, 0f, 12f, 96, 12f, age * 0.06f, VIOLET, 0.85f * (1f - k), 0f);
    }
}
