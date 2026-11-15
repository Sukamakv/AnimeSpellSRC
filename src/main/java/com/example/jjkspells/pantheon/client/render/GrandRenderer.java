package com.example.jjkspells.pantheon.client.render;

import com.example.jjkspells.pantheon.entity.GrandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.pantheon.client.render.Gfx.*;

/**
 * Grand Starfall: the sigil and star at the start, the pillar of light, the constellation, the targeting formation, the falling
 * spear meteor, the impact, the six giant spears, the Celestial Wave, the dome and the final beam.
 */
public class GrandRenderer extends EntityRenderer<GrandEntity> {
    public GrandRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(GrandEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(GrandEntity e) {
        return GLOW;
    }

    @Override
    public void render(GrandEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        Vec3 base = e.getPosition(pt);
        Vec3 cam = cam(base);
        Vec3 tk = e.getTakeoff().subtract(base);
        Entity owner = e.getOwnerEntity();
        int zone = e.getZoneTicks();
        float skyH = (float) (e.getSkyY() - base.y);

        takeoff(buf, ps, t, tk);
        constellation(buf, ps, t, tk, skyH);
        formation(buf, ps, t, e, zone);
        beams(buf, ps, t, skyH);
        meteor(buf, ps, t, cam, skyH);
        impact(buf, ps, t, e, zone);
        spears(buf, ps, t, cam, e);
        wave(buf, ps, t, e);
        finale(buf, ps, t, e, owner);
    }

    /** P1/P2: sigil under the caster, the bright star above, the pillar of light into the sky. */
    private void takeoff(MultiBufferSource buf, PoseStack ps, float t, Vec3 tk) {
        float a = c01(t / 12f) * (1f - c01((t - GrandEntity.T_UP - 6f) / 12f));
        ps.pushPose();
        ps.translate(tk.x, tk.y, tk.z);
        if (a > 0.01f) {
            float g = smooth(t / 16f);
            Draw.disc(add(buf, CIRCLE), ps, 9.5f * g, t * 1.4f, 0.06f, GOLD, 0.95f * a);
            Draw.disc(add(buf, RUNE), ps, 7.0f * g, -t * 2.2f, 0.07f, WARM, 0.9f * a);
            Draw.disc(add(buf, RUNE), ps, 4.4f * g, t * 3.4f, 0.08f, GOLD, 0.85f * a);
            Draw.disc(add(buf, TARGET), ps, 3.0f * g, -t * 1.0f, 0.09f, WHITE, 0.6f * a);
            Draw.disc(add(buf, GLOW), ps, 11f * g, 0f, 0.05f, AMBER, 0.4f * a);
        }
        float sa = c01((t - 6f) / 6f) * (1f - c01((t - GrandEntity.T_HIDE) / 10f));
        if (sa > 0.01f) {
            ps.pushPose();
            ps.translate(0, 14, 0);
            billboard(ps);
            float s = 3.2f + 1.6f * smooth((t - 6f) / 20f) + 0.25f * Mth.sin(t * 0.7f);
            Draw.xy(add(buf, GLOW), ps, s * 1.8f, s * 1.8f, AMBER, 0.6f * sa);
            Draw.xy(add(buf, STAR), ps, s * 2.0f, s * 2.0f, WHITE, sa);
            ps.popPose();
        }
        float pa = c01((t - GrandEntity.T_THRUST) / 2f) * (1f - c01((t - GrandEntity.T_HIDE - 6f) / 10f));
        if (pa > 0.01f) {
            Draw.band(add(buf, BEAM), ps, 1.5f, 0f, 120f, 28, 3f, t * 0.06f, WHITE, pa, 0.2f * pa);
            Draw.band(add(buf, FLAME_COL), ps, 2.1f, 0f, 20f, 28, 3f, t * 0.04f, GOLD, 0.9f * pa, 0f);
        }
        ps.popPose();
    }

    /** P2/P3: the giant spear-and-shield constellation overhead. */
    private void constellation(MultiBufferSource buf, PoseStack ps, float t, Vec3 tk, float skyH) {
        float a = c01((t - GrandEntity.T_UP) / 8f) * (1f - c01((t - GrandEntity.T_P3) / 10f));
        if (a > 0.01f) {
            ps.pushPose();
            ps.translate(tk.x, tk.y + 34, tk.z);
            billboard(ps);
            Draw.xy(add(buf, CONSTELLATION), ps, 24f, 24f, WHITE, a);
            ps.popPose();
        }
        float b = c01((t - GrandEntity.T_P3 - 6f) / 14f) * (1f - c01((t - GrandEntity.T_IMPACT) / 4f));
        if (b > 0.01f) {
            ps.pushPose();
            ps.translate(0, 42, 0);
            billboard(ps);
            Draw.xy(add(buf, CONSTELLATION), ps, 30f, 30f, WARM, 0.55f * b);
            ps.popPose();
        }
    }

    /** P3 and after: three huge rotating circles on the ground (the outer one marks the danger zone). */
    private void formation(MultiBufferSource buf, PoseStack ps, float t, GrandEntity e, int zone) {
        float in = c01((t - GrandEntity.T_P3) / 10f);
        if (in <= 0f) {
            return;
        }
        float after = t < GrandEntity.T_IMPACT ? 1f : 0.55f * (1f - c01((t - (GrandEntity.T_IMPACT + zone - 20f)) / 20f));
        float a = in * after;
        if (a <= 0.01f) {
            return;
        }
        float rw = e.getRWave();
        Draw.disc(add(buf, CIRCLE), ps, rw, t * 0.8f, 0.07f, GOLD, 0.95f * a);
        Draw.disc(add(buf, RUNE), ps, rw * 0.7f, -t * 1.3f, 0.08f, WARM, 0.9f * a);
        Draw.disc(add(buf, CIRCLE), ps, rw * 0.38f, t * 2.1f, 0.09f, GOLD, 0.9f * a);
        Draw.disc(add(buf, TARGET), ps, rw * 0.5f, -t * 0.6f, 0.1f, WHITE, 0.55f * a);
        Draw.disc(add(buf, GLOW), ps, rw * 1.05f, 0f, 0.05f, AMBER, 0.22f * a);
    }

    /** P3: golden beams descending from the sky around the battlefield. */
    private void beams(MultiBufferSource buf, PoseStack ps, float t, float skyH) {
        if (t < GrandEntity.T_P3 || t > GrandEntity.T_IMPACT + 8) {
            return;
        }
        float fade = 1f - c01((t - GrandEntity.T_IMPACT) / 8f);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + 0.2;
            float r = i % 2 == 0 ? 21f : 12f;
            float prog = smooth((t - GrandEntity.T_P3 - i * 2.5f) / 22f);
            if (prog <= 0f) {
                continue;
            }
            float head = Mth.lerp(prog, 120f, 0f);
            ps.pushPose();
            ps.translate(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            Draw.band(add(buf, BEAM), ps, 0.55f, head, head + 40f, 12, 2f, t * 0.08f, WARM, 0.95f * fade, 0f);
            if (prog >= 1f) {
                Draw.band(add(buf, BEAM), ps, 0.5f, 0f, 110f, 12, 2f, t * 0.08f, GOLD, 0.45f * fade, 0f);
                Draw.disc(add(buf, GLOW), ps, 2.2f, 0f, 0.1f, GOLD, 0.7f * fade);
            }
            ps.popPose();
        }
    }

    /** P3: the burning streak that turns into the gigantic spear-shaped meteor. */
    private void meteor(MultiBufferSource buf, PoseStack ps, float t, Vec3 cam, float skyH) {
        float t0 = GrandEntity.T_P3 + 4f;
        if (t < t0 || t > GrandEntity.T_IMPACT + 1) {
            return;
        }
        float f = c01((t - GrandEntity.T_METEOR) / (float) (GrandEntity.T_IMPACT - GrandEntity.T_METEOR));
        float y = skyH * (1f - f * f);
        float size = 0.12f + 0.88f * smooth(f * 1.15f);
        float a = c01((t - t0) / 5f) * (1f - c01((t - GrandEntity.T_IMPACT) / 1.2f));
        Vec3 head = new Vec3(0, y, 0);
        Vec3 tail = new Vec3(0, y + 90f * (0.3f + 0.7f * size), 0);
        Draw.ribbon(add(buf, TRAIL), ps, tail, head, cam, 0.0f, 5.0f * size + 0.2f, 0f, 1f, AMBER, 0f, 0.9f * a);
        Draw.ribbon(add(buf, TRAIL), ps, tail, head, cam, 0.0f, 3.0f * size + 0.15f, 0f, 1f, GOLD, 0f, a);
        Draw.ribbon(add(buf, TRAIL), ps, head.add(0, 45f * size, 0), head, cam, 0.0f, 1.4f * size + 0.1f, 0f, 1f, WHITE, 0f, a);
        ps.pushPose();
        ps.translate(0, y, 0);
        Draw.band(add(buf, FLAME_COL), ps, 2.6f * size + 0.1f, 0f, 36f * size + 2f, 20, 3f, t * 0.09f, GOLD, 0.8f * a, 0f);
        billboard(ps);
        // the spear-shaped meteor itself: a huge spear sprite with its head pointing down
        ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90f));
        float hh = 3.2f * size + 0.1f;
        Draw.xyAt(add(buf, SPEAR), ps, hh * 8f, hh, 0f, WHITE, a);
        ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90f));
        Draw.xy(add(buf, GLOW), ps, 9f * size + 0.5f, 9f * size + 0.5f, GOLD, 0.9f * a);
        Draw.xy(add(buf, STAR), ps, 14f * size + 0.6f, 14f * size + 0.6f, WHITE, 0.8f * a);
        ps.popPose();
    }

    /** P4: the colossal explosion, the beam of celestial fire into the sky, shockwaves and ground cracks, then the burning battlefield. */
    private void impact(MultiBufferSource buf, PoseStack ps, float t, GrandEntity e, int zone) {
        float b = t - GrandEntity.T_IMPACT;
        if (b < 0) {
            return;
        }
        float ri = e.getRImpact(), rw = e.getRWave();
        float col = c01(b / 2f) * (1f - c01((b - 30f) / 30f));
        if (col > 0.01f) {
            Draw.band(add(buf, BEAM), ps, 3.2f, 0f, 150f, 36, 4f, t * 0.05f, WHITE, col, 0.3f * col);
            Draw.band(add(buf, FLAME_COL), ps, 4.4f, 0f, 34f, 36, 4f, t * 0.05f, GOLD, 0.95f * col, 0f);
        }
        float k = c01(b / 12f);
        if (k < 1f) {
            float r = ri * (0.1f + 0.9f * smooth(k));
            Draw.disc(add(buf, WAVE), ps, r + 1f, b * 4f, 0.14f, GOLD, (1f - k) * 0.95f);
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 2.4f), r + 1.6f, 0.16f, 96, 1f, 0f, WARM, 1f - k);
        }
        float k2 = c01((b - 4f) / 16f);
        if (k2 > 0f && k2 < 1f) {
            float r = ri * 1.2f * smooth(k2);
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 2f), r + 2f, 0.18f, 96, 1f, 0f, AMBER, (1f - k2) * 0.85f);
        }
        float k3 = c01((b - 9f) / 22f);
        if (k3 > 0f && k3 < 1f) {
            float r = rw * smooth(k3);
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 1.6f), r + 1.6f, 0.2f, 96, 1f, 0f, GOLD, (1f - k3) * 0.6f);
        }
        float cr = c01(b / 3f) * (1f - c01((b - (zone - 30f)) / 30f));
        if (cr > 0.01f) {
            Draw.disc(add(buf, CRACKS), ps, ri * 1.1f, 10f, 0.1f, GOLD, 0.95f * cr);
            Draw.disc(add(buf, GLOW), ps, rw * 1.0f, 0f, 0.06f, AMBER, (0.2f + 0.08f * Mth.sin(t * 0.4f)) * cr);
            // a wall of celestial fire along the edge of the battlefield
            Draw.band(add(buf, FLAME_COL), ps, rw, 0f, 3.2f, 120, 24f, t * 0.012f, AMBER, 0.7f * cr, 0f);
            Draw.band(add(buf, FLAME_COL), ps, rw * 0.55f, 0f, 1.8f, 90, 14f, -t * 0.01f, GOLD, 0.35f * cr, 0f);
        }
    }

    /** P5: six giant golden spears, each pointing at the centre, slamming down one after another. */
    private void spears(MultiBufferSource buf, PoseStack ps, float t, Vec3 cam, GrandEntity e) {
        int n = e.getSpears();
        float appear = c01((t - (GrandEntity.T_IMPACT + 8f)) / 12f);
        if (appear <= 0f) {
            return;
        }
        double tau = Math.toRadians(52);
        for (int i = 0; i < n; i++) {
            float slam = GrandEntity.slamTime(i);
            float fadeOut = 1f - c01((t - slam - 10f) / 14f);
            if (fadeOut <= 0f) {
                continue;
            }
            double a = GrandEntity.spearAngle(i, n);
            Vec3 slot = new Vec3(Math.cos(a) * GrandEntity.SPEAR_RING, 0, Math.sin(a) * GrandEntity.SPEAR_RING);
            Vec3 inward = new Vec3(-Math.cos(a), 0, -Math.sin(a));
            Vec3 axis = inward.scale(Math.sin(tau)).add(0, -Math.cos(tau), 0).normalize();
            float drop = smooth((t - (slam - 4f)) / 4f);
            float hover = 15f * (1f - drop) + (float) (Math.sin(t * 0.15 + i) * 0.5) * (1f - drop);
            Vec3 tip = slot.add(0, hover, 0).add(inward.scale(0.0));
            float len = 38f;
            Vec3 tail = tip.subtract(axis.scale(len));
            float al = appear * fadeOut;
            // the glow, then the spear sprite along its axis
            Draw.ribbon(add(buf, TRAIL), ps, tail, tip, cam, 0.0f, 3.4f, 0f, 1f, AMBER, 0f, 0.6f * al);
            Draw.ribbon(add(buf, SPEAR), ps, tail, tip, cam, len / 16f, len / 16f, 0f, 1f, WHITE, al, al);
            ps.pushPose();
            ps.translate(tip.x, tip.y, tip.z);
            billboard(ps);
            Draw.xy(add(buf, GLOW), ps, 3.2f, 3.2f, GOLD, 0.8f * al);
            ps.popPose();
            float sb = t - slam;
            if (sb >= 0f && sb < 12f) {
                float k = sb / 12f;
                ps.pushPose();
                ps.translate(slot.x, 0, slot.z);
                Draw.disc(add(buf, WAVE), ps, 4.6f * (0.2f + 0.8f * smooth(k)), 0f, 0.15f, GOLD, (1f - k) * 0.9f);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, 4.6f * k - 0.8f), 4.6f * k + 0.6f, 0.17f, 48, 1f, 0f, WARM, 1f - k);
                Draw.band(add(buf, BEAM), ps, 1.0f, 0f, 18f, 16, 2f, t * 0.08f, WHITE, (1f - k), 0f);
                ps.popPose();
            }
        }
    }

    /** P5: the Celestial Wave out to 25 blocks. */
    private void wave(MultiBufferSource buf, PoseStack ps, float t, GrandEntity e) {
        float b = t - GrandEntity.T_WAVE;
        if (b < 0f || b > GrandEntity.WAVE_TICKS + 12) {
            return;
        }
        float k = c01(b / GrandEntity.WAVE_TICKS);
        float r = e.getRWave() * smooth(k);
        float a = 1f - c01((b - GrandEntity.WAVE_TICKS) / 12f);
        Draw.disc(add(buf, WAVE), ps, r + 0.8f, b * 3f, 0.18f, GOLD, 0.4f * a * (1f - 0.5f * k));
        Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 3.2f), r + 1.4f, 0.2f, 120, 1f, 0f, WARM, a);
        Draw.band(add(buf, FLAME_COL), ps, Math.max(0.1f, r), 0f, 4.5f, 120, 24f, 0f, GOLD, 0.85f * a, 0f);
    }

    /** P6: the spearhead flare, the dome that expands, collapses and detonates, and the final vertical beam. */
    private void finale(MultiBufferSource buf, PoseStack ps, float t, GrandEntity e, Entity owner) {
        int tr = GrandEntity.T_RAISE, td = GrandEntity.T_DRIVE, tc = GrandEntity.T_COLLAPSE, tf = GrandEntity.T_FINAL;
        float rf = e.getRFinal();
        if (owner != null && t >= tr && t < tf) {
            Vec3 op = owner.getPosition(0f).subtract(e.getPosition(0f));
            float g = c01((t - tr) / (float) (td - tr));
            float s = (0.5f + 4.5f * g) * (t < td ? 1f : 1f - c01((t - td) / 6f));
            ps.pushPose();
            ps.translate(op.x, op.y + 2.8f * (t < td ? 1f : 0.2f), op.z);
            billboard(ps);
            Draw.xy(add(buf, GLOW), ps, s * 1.6f, s * 1.6f, GOLD, 0.8f);
            Draw.xy(add(buf, STAR), ps, s * 2.0f, s * 2.0f, WHITE, 0.95f);
            ps.popPose();
        }
        if (t >= td && t < tf + 3) {
            float rad;
            float a;
            if (t < tc) {
                float k = c01((t - td) / (float) (tc - td));
                rad = rf * (1f - (1f - k) * (1f - k));
                a = 0.9f;
            } else {
                float k = c01((t - tc) / (float) (tf - tc));
                rad = rf * (1f - 0.8f * smooth(k));
                a = 0.9f + 0.1f * k;
            }
            ps.pushPose();
            Draw.sphere(add(buf, DOME), ps, Math.max(0.1f, rad), 26, 40, t * 0.004f, GOLD, a * (1f - c01((t - tf) / 3f)));
            Draw.sphere(add(buf, GLOW), ps, Math.max(0.1f, rad * 0.97f), 12, 24, 0f, AMBER, 0.28f * (1f - c01((t - tf) / 3f)));
            ps.popPose();
            Draw.disc(add(buf, CIRCLE), ps, Math.max(0.1f, rad), t * 5f, 0.1f, GOLD, 0.8f);
        }
        float b = t - tf;
        if (b >= 0f) {
            float col = c01(b / 2f) * (1f - c01((b - (GrandEntity.BEAM_TICKS - 12)) / 12f));
            if (col > 0.01f) {
                Draw.band(add(buf, BEAM), ps, 2.6f, 0f, 160f, 36, 4f, t * 0.06f, WHITE, col, 0.4f * col);
                Draw.band(add(buf, FLAME_COL), ps, 3.6f, 0f, 30f, 36, 4f, t * 0.05f, GOLD, 0.9f * col, 0f);
                Draw.disc(add(buf, GLOW), ps, 9f, 0f, 0.2f, AMBER, 0.5f * col);
            }
            float k = c01(b / 12f);
            if (k < 1f) {
                float r = rf * 1.25f * smooth(k);
                Draw.disc(add(buf, WAVE), ps, r + 1f, -b * 4f, 0.2f, GOLD, (1f - k) * 0.95f);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 2.5f), r + 1.5f, 0.22f, 96, 1f, 0f, WARM, 1f - k);
            }
        }
    }
}
