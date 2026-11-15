package com.example.jjkspells.zegion.client.render;

import com.example.jjkspells.zegion.config.ZegConfig;
import com.example.jjkspells.zegion.entity.ImpactEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.zegion.client.render.Gfx.*;

/** Kaiser's Impact: the stance, the afterimage and the black line, the heaven-shattering shockwave of glass and the rupture of rifts and rings. */
public class ImpactRenderer extends ZegRenderer<ImpactEntity> {
    public ImpactRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(ImpactEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base) {
        int seed = e.getSeed();
        float SR = (float) (double) ZegConfig.IMPACT.shockRadius.get();
        float RR = e.getRuptureRadius();
        Vec3 o = e.getOrigin().subtract(base);

        // ---- the stance: energy compresses around the fist, the ground cracks under Zegion
        if (t < ImpactEntity.BLINK + 4) {
            float g = smooth(t / ImpactEntity.COMPRESS) * (1f - c01((t - ImpactEntity.BLINK) / 4f));
            ps.pushPose();
            ps.translate(o.x, o.y, o.z);
            Draw.disc(add(buf, CRACKS), ps, 3.2f * g + 0.5f, t * 3f, 0.06f, DEEP, 0.9f * g);
            Draw.disc(add(buf, CIRCLE), ps, 2.6f * g + 0.4f, -t * 5f, 0.08f, VIOLET, 0.7f * g);
            float comp = c01((t - ImpactEntity.COMPRESS) / (ImpactEntity.BLINK - ImpactEntity.COMPRESS));
            ps.pushPose();
            ps.translate(0, 1.15f, 0);
            Gfx.billboard(ps);
            float s = (1.2f - 0.7f * comp) * g;
            Draw.xy(add(buf, GLOW), ps, s * 1.6f, s * 1.6f, PALE, 0.85f * g);
            Draw.xy(trans(buf, GLOW), ps, s * 0.35f, s * 0.35f, BLACK, 0.9f * g);
            Draw.xy(add(buf, BURST), ps, s * 2.0f, s * 2.0f, VIOLET, 0.5f * g * comp);
            ps.popPose();
            if (comp > 0f) {
                for (int i = 0; i < 4; i++) {
                    ps.pushPose();
                    ps.translate(0, 0.1f + i * 0.5f, 0);
                    Draw.ring(add(buf, RING_WAVE), ps, 0.6f + (1f - comp) * 2.5f, 0.9f + (1f - comp) * 2.8f, 0f, 36, 1f, t * 0.02f, VIOLET, 0.5f * g);
                    ps.popPose();
                }
            }
            ps.popPose();
        }

        // ---- the afterimage that stays behind and the thin black line to the target
        float tb = t - ImpactEntity.BLINK;
        if (tb >= 0f && tb < 36f) {
            float k = c01(tb / 36f);
            phantom(buf, ps, base, (float) o.x, (float) o.y, (float) o.z, 2.1f, 0.75f * (1f - k), 1.0f * (1f - k), VIOLET);
            float line = 1f - c01(tb / 16f);
            if (line > 0.01f) {
                Vec3 a = o.add(0, 1.0, 0), b = new Vec3(0, 1.0, 0);
                Vec3 cam = cam(base);
                Draw.ribbon(trans(buf, TRAIL), ps, a, b, cam, 0.5f * line, 0.5f * line, 0f, 1f, BLACK, 0.95f * line, 0.95f * line);
                Draw.ribbon(add(buf, TRAIL), ps, a, b, cam, 0.9f * line, 0.9f * line, 0f, 1f, DEEP, 0.7f * line, 0.7f * line);
                Draw.ribbon(add(buf, LINE), ps, a, b, cam, 0.18f * line, 0.18f * line, 0f, 1f, PALE, 1f * line, 1f * line);
            }
            Vec3 d = o.subtract(0, 0, 0);
        }

        // ---- the punch: a flash, glass segments, the shockwave
        float tp = t - ImpactEntity.PUNCH;
        if (tp >= 0f) {
            float fl = 1f - c01(tp / 12f);
            if (fl > 0.01f) {
                ps.pushPose();
                ps.translate(0, 1.4f, 0);
                Gfx.billboard(ps);
                Draw.xy(add(buf, BURST), ps, 9f * (0.6f + tp * 0.12f), 9f * (0.6f + tp * 0.12f), PALE, fl);
                Draw.xy(add(buf, GLOW), ps, 5f, 5f, VIOLET, fl * 0.8f);
                ps.popPose();
            }
            float k = c01(tp / (ImpactEntity.SHOCK_END - ImpactEntity.PUNCH));
            float r = 2f + (SR - 2f) * smooth(k);
            float fade = 1f - c01((t - ImpactEntity.SHOCK_END) / 18f);
            if (fade > 0.01f) {
                float a = (1f - 0.5f * k) * fade;
                Draw.disc(add(buf, WAVE), ps, r, tp * 3f, 0.1f, VIOLET, 0.8f * a);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 2.4f), r + 0.8f, 0.14f, 96, 1f, 0f, PALE, a);
                Draw.band(add(buf, RIFT_BOLT), ps, r, 0f, 2.6f + r * 0.1f, 72, 10f, tp * 0.05f, VIOLET, 0.9f * a, 0f);
                Draw.disc(add(buf, CRACKS), ps, Math.min(SR, r * 1.05f), 17f, 0.05f, DEEP, 0.9f * a);
                // enormous segments of broken air standing on the wave front
                int n = 34;
                for (int i = 0; i < n; i++) {
                    float ang = (i + rnd(seed, i) * 0.6f) * Mth.TWO_PI / n;
                    float rr = r * (0.55f + 0.45f * rnd(seed, i + 50));
                    float h = 6f + rnd(seed, i + 100) * 8f;
                    float rise = smooth((tp - 3f - rnd(seed, i + 150) * 6f) / 8f);
                    if (rise <= 0.01f) {
                        continue;
                    }
                    ps.pushPose();
                    ps.translate(Mth.cos(ang) * rr, h * 0.5f * rise, Mth.sin(ang) * rr);
                    ps.mulPose(Axis.YP.rotation(-ang + (rnd(seed, i + 200) - 0.5f) * 1.2f));
                    ps.mulPose(Axis.XP.rotationDegrees((rnd(seed, i + 250) - 0.5f) * 40f));
                    mirror(buf, ps, 3.6f + rnd(seed, i + 300) * 3f, h * rise, 0.55f * a, 0.8f * a);
                    ps.popPose();
                }
            }
        }

        // ---- the rupture: rifts and rings hang over the impact, widen, then shut together
        float tr = t - (ImpactEntity.PUNCH + 22);
        if (tr >= 0f) {
            float open = smooth(tr / 14f) * (1f + 0.35f * c01((t - ImpactEntity.SHOCK_END) / (ImpactEntity.COLLAPSE - ImpactEntity.SHOCK_END)));
            float shut = 1f - smooth((t - ImpactEntity.COLLAPSE) / 7f);
            float o2 = open * shut;
            if (o2 > 0.01f) {
                int n = 9;
                for (int i = 0; i < n; i++) {
                    float ang = i * Mth.TWO_PI / n + rnd(seed, i) * 0.5f;
                    float rr = 4f + (RR * 0.75f - 4f) * rnd(seed, i + 10);
                    float hl = 6f + rnd(seed, i + 20) * 10f, mh = 6f + rnd(seed, i + 30) * 12f;
                    ps.pushPose();
                    ps.translate(Mth.cos(ang) * rr, mh * 0.7f, Mth.sin(ang) * rr);
                    ps.mulPose(Axis.YP.rotation(-ang + Mth.HALF_PI + (rnd(seed, i + 40) - 0.5f) * 0.8f));
                    ps.mulPose(Axis.ZP.rotationDegrees((rnd(seed, i + 50) - 0.5f) * 40f));
                    rift(buf, ps, hl, mh, o2, t, 0.95f);
                    ps.popPose();
                }
                for (int i = 0; i < 3; i++) {
                    ps.pushPose();
                    ps.translate(0, 4f + i * 5.5f, 0);
                    float rad = RR * (0.35f + 0.18f * i) * (1f - 0.7f * (1f - shut));
                    Draw.ring(add(buf, RING_WAVE), ps, rad - 1.6f, rad + 1.2f, 0f, 96, 1f, t * 0.01f * (i % 2 == 0 ? 1 : -1), i % 2 == 0 ? VIOLET : PALE, 0.8f * o2);
                    Draw.ring(add(buf, RUNE), ps, rad * 0.6f, rad * 1.1f, 0.05f, 72, 1f, t * 0.01f, DEEP, 0.5f * o2);
                    ps.popPose();
                }
                Draw.disc(add(buf, CIRCLE), ps, RR * 0.85f * o2, -t * 0.8f, 0.08f, VIOLET, 0.55f * o2);
            }
        }

        // ---- the collapse
        float tc = t - ImpactEntity.COLLAPSE;
        if (tc >= 0f && tc < 22f) {
            float k = c01(tc / 22f);
            float r = RR * (0.2f + 0.9f * smooth(k));
            Draw.disc(add(buf, WAVE), ps, r, tc * 4f, 0.12f, PALE, 0.9f * (1f - k));
            Draw.ring(add(buf, RING_WAVE), ps, r - 2.5f, r + 1f, 0.16f, 96, 1f, 0f, PALE, 1f - k);
            ps.pushPose();
            ps.translate(0, 2f, 0);
            Gfx.billboard(ps);
            Draw.xy(add(buf, BURST), ps, RR * 0.5f * (1f - k * 0.5f), RR * 0.5f * (1f - k * 0.5f), VIOLET, 0.8f * (1f - k));
            ps.popPose();
        }
    }
}
