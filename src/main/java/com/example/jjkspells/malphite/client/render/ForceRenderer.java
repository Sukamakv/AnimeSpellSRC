package com.example.jjkspells.malphite.client.render;

import com.example.jjkspells.malphite.entity.ForceEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.malphite.client.render.Gfx.*;

/** Unstoppable Force: the targeting circle, the stone aura and charge trail, the impact, five seismic waves, the stone formations, the Living Mountain and the final judgment. */
public class ForceRenderer extends EntityRenderer<ForceEntity> {
    public ForceRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(ForceEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ForceEntity e) {
        return GLOW;
    }

    @Override
    public void render(ForceEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        var c = com.example.jjkspells.malphite.config.MalConfig.FORCE;
        Vec3 base = e.getPosition(pt);
        Vec3 cam = cam(base);
        Entity owner = e.getOwnerEntity();
        Vec3 ownerP = owner != null ? owner.getPosition(pt).subtract(base) : Vec3.ZERO;
        Vec3 start = e.getStart().subtract(base);
        int seed = e.getSeed();
        float Ri = (float) (double) c.impactRadius.get();
        float Rs = (float) (double) c.seismicRadius.get();
        float Rf = (float) (double) c.finalRadius.get();
        int zone = e.getZoneTicks();
        boolean impacted = t >= ForceEntity.T_IMPACT;

        // phase 1: the destination is marked on the ground
        if (!impacted) {
            float a = c01(t / 10f);
            float pulse = 0.75f + 0.25f * Mth.sin(t * 0.5f);
            Draw.disc(add(buf, TARGET), ps, Ri, t * 2f, 0.07f, ORANGE, 0.9f * a * pulse);
            Draw.disc(add(buf, CIRCLE), ps, Ri * 0.45f, -t * 4f, 0.08f, AMBER, 0.8f * a);
            Draw.disc(add(buf, GLOW), ps, Ri * 1.05f, 0f, 0.05f, ORANGE, 0.15f * a * pulse);
        }
        // phases 1-2: the caster is wrapped in stone and glowing cracks
        if (t < ForceEntity.T_CHARGE && owner != null) {
            float a = c01(t / 14f);
            float gat = c01((t - ForceEntity.T_GATHER) / 16f);
            ps.pushPose();
            ps.translate(ownerP.x, ownerP.y, ownerP.z);
            Draw.disc(add(buf, CRACKS), ps, 2.8f * a, t * 3f, 0.06f, ORANGE, 0.85f * a);
            Draw.disc(add(buf, GLOW), ps, 3.2f * a, 0f, 0.05f, ORANGE, (0.2f + 0.25f * gat) * a);
            Draw.band(trans(buf, DUST_WAVE), ps, 1.8f, 0f, 1.1f, 36, 10f, t * 0.03f, DUSTC, 0.6f * a, 0f);
            int plates = 8 + (int) (gat * 6);
            for (int i = 0; i < plates; i++) {
                double ang = t * 0.12 + i * Math.PI * 2 / plates;
                double rr = 1.0 + 0.15 * Math.sin(t * 0.3 + i);
                ps.pushPose();
                ps.translate(Math.cos(ang) * rr, 0.35 + 1.5 * ((i * 5) % 7) / 7.0 + 0.1 * Math.sin(t * 0.2 + i), Math.sin(ang) * rr);
                billboard(ps);
                ps.mulPose(Axis.ZP.rotationDegrees(i * 40f + t * 3f));
                float s = 0.3f + 0.14f * (i % 3);
                Draw.xy(trans(buf, i % 3 == 0 ? ROCK : PLATE), ps, s, s, STONE, a);
                ps.popPose();
            }
            if (t >= ForceEntity.T_GATHER) {
                ps.pushPose();
                ps.translate(0, 1.0, 0);
                billboard(ps);
                float s = 1.2f + 1.3f * gat;
                Draw.xy(add(buf, GLOW), ps, s, s, ORANGE, 0.5f * gat);
                ps.popPose();
            }
            ps.popPose();
        }
        // phase 3: the charge - a broad road of dust and shattered ground behind the caster, a shock cone in front
        if (t >= ForceEntity.T_CHARGE && t < ForceEntity.T_IMPACT + 10 && owner != null) {
            float a = 1f - c01((t - ForceEntity.T_IMPACT) / 10f);
            Vec3 head = ownerP.add(0, 1.0, 0);
            Vec3 line = head.subtract(start.add(0, 1.0, 0));
            Vec3 d = line.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : line.normalize();
            Vec3 side = new Vec3(-d.z, 0, d.x);
            for (int s = -1; s <= 1; s++) {
                Vec3 off = side.scale(s * 1.4);
                Draw.ribbon(trans(buf, TRAIL), ps, start.add(0, 0.8, 0).add(off), head.add(off), cam, 0.8f, 1.7f, 0f, 1f, DUSTC, 0.2f * a, 0.9f * a);
            }
            Draw.ribbon(add(buf, LINE), ps, start.add(0, 0.6, 0), head, cam, 0.2f, 0.6f, 0f, 1f, ORANGE, 0f, 0.7f * a);
            ps.pushPose();
            ps.translate(head.x + d.x * 1.6, head.y, head.z + d.z * 1.6);
            billboard(ps);
            Draw.xy(add(buf, GLOW), ps, 3.2f, 3.2f, ORANGE, 0.6f * a);
            Draw.xy(trans(buf, MIST), ps, 3.6f, 3.0f, DUSTC, 0.8f * a);
            ps.popPose();
            ps.pushPose();
            ps.translate(ownerP.x, ownerP.y, ownerP.z);
            Draw.band(trans(buf, DUST_COL), ps, 1.7f, 0f, 4.2f, 24, 3f, t * 0.04f, DUSTC, 0.8f * a, 0.2f * a);
            ps.popPose();
        }
        // phase 4: the impact
        float b = t - ForceEntity.T_IMPACT;
        if (b >= 0f) {
            float cr = c01(b / 3f) * (1f - c01((b - zone + 30f) / 30f));
            if (cr > 0.01f) {
                float rad = b < 28f ? Mth.lerp(c01(b / 20f), Ri * 0.6f, Ri * 1.15f) : (t < ForceEntity.T_Q0 + 20 ? Ri * 1.15f : Mth.lerp(c01((t - ForceEntity.T_Q0 - 20f) / 30f), Ri * 1.15f, Rs * 1.02f));
                Draw.disc(add(buf, CRACKS), ps, rad, 8f, 0.1f, ORANGE, (0.55f + 0.4f * c01(1f - b / 80f)) * cr);
                Draw.disc(add(buf, GLOW), ps, rad * 0.95f, 0f, 0.06f, ORANGE, (0.13f + 0.05f * Mth.sin(t * 0.3f)) * cr);
            }
            float col = c01(b / 3f) * (1f - c01((b - 14f) / 40f));
            if (col > 0.01f) {
                Draw.band(trans(buf, DUST_COL), ps, 5.0f, 0f, 22f, 36, 3f, t * 0.008f, DUSTC, 0.95f * col, 0.05f * col);
                Draw.band(trans(buf, DUST_COL), ps, Ri * 0.55f, 0f, 6f, 72, 10f, -t * 0.01f, DUSTC, 0.8f * col, 0f);
                Draw.band(add(buf, EMBER_COL), ps, 2.4f, 0f, 20f, 24, 3f, t * 0.04f, ORANGE, 0.7f * col, 0f);
            }
            float k = c01(b / 11f);
            if (k < 1f) {
                float r = Ri * (0.12f + 0.95f * smooth(k));
                Draw.disc(add(buf, WAVE), ps, r + 1.2f, b * 3f, 0.18f, HOT, 1f - k);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 2.6f), r + 1.2f, 0.2f, 120, 1f, 0f, ORANGE, 1f - k);
                Draw.band(trans(buf, DUST_WAVE), ps, r, 0f, 4f, 96, 22f, b * 0.02f, DUSTC, 0.9f * (1f - k * 0.6f), 0f);
            }
            // huge jagged spikes erupting around the impact
            for (int i = 0; i < 16; i++) {
                double ang = ((seed + i * 53) % 360) * Math.PI / 180.0 + i * 2.399963;
                float r = 2.5f + (Ri - 3f) * (float) Math.sqrt((i + 0.5) / 16.0);
                float h = 4.5f + 4f * (((seed + i * 7) % 10) / 10f);
                float life = life(b, i * 0.3f, 5f, 36f, 24f);
                Gfx.spike(buf, ps, (float) (Math.cos(ang) * r), (float) (Math.sin(ang) * r), 0.9f + 0.4f * (i % 3), h, life, i * 29f, 1f, light);
            }
        }
        // phase 5: five concentric seismic waves, and stone formations rising from the earth
        for (int w = 0; w < ForceEntity.WAVES; w++) {
            float wt = t - (ForceEntity.T_Q0 + w * ForceEntity.WAVE_GAP);
            if (wt < 0f || wt > ForceEntity.WAVE_LIFE + 14) {
                continue;
            }
            float k = c01(wt / ForceEntity.WAVE_LIFE);
            float r0 = w == 0 ? 0f : (float) ForceEntity.waveRadius(w - 1), r1 = (float) ForceEntity.waveRadius(w);
            float r = Mth.lerp(smooth(k), r0, r1);
            float a = 1f - c01((wt - ForceEntity.WAVE_LIFE) / 14f);
            Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 1.6f), r + 1.2f, 0.22f + 0.01f * w, 120, 1f, 0f, AMBER, 0.9f * a);
            Draw.band(trans(buf, DUST_WAVE), ps, Math.max(0.2f, r), 0f, 3.2f, 96, 24f, wt * 0.02f, DUSTC, 0.85f * a, 0f);
        }
        if (t >= ForceEntity.T_Q0) {
            for (int i = 0; i < ForceEntity.SPIKES; i++) {
                double[] o = ForceEntity.spikeOffset(i, seed, Rs);
                float life = life(t, ForceEntity.T_Q0 + i * 1.6f, 6f, 26f, 18f);
                float h = 3.5f + 3.5f * (((seed + i * 17) % 10) / 10f);
                Gfx.spike(buf, ps, (float) o[0], (float) o[1], 0.8f + 0.3f * (i % 3), h, life, i * 37f, 1f, light);
            }
        }
        // phase 6: the Living Mountain stands behind the caster
        if (t >= ForceEntity.T_RISE && t < ForceEntity.T_JUDG + 14 && owner != null) {
            float a = c01((t - ForceEntity.T_RISE) / 10f) * (1f - c01((t - ForceEntity.T_JUDG - 4f) / 10f));
            float rise = smooth((t - ForceEntity.T_RISE) / 20f);
            Vec3 away = ownerP.subtract(cam);
            away = away.lengthSqr() < 1.0E-6 ? Vec3.ZERO : new Vec3(away.x, 0, away.z).normalize();
            ps.pushPose();
            ps.translate(ownerP.x + away.x * 2.5, ownerP.y + 5.5 + 3.0 * rise, ownerP.z + away.z * 2.5);
            billboard(ps);
            float s = 8.5f + 2f * rise;
            Draw.xy(trans(buf, SILHOUETTE), ps, s, s, DARK, 0.75f * a);
            Draw.xy(add(buf, GLOW), ps, s * 0.8f, s * 0.8f, ORANGE, 0.15f * a);
            ps.popPose();
            ps.pushPose();
            ps.translate(ownerP.x, ownerP.y, ownerP.z);
            Draw.disc(add(buf, CRACKS), ps, 3.5f, t * 2f, 0.1f, ORANGE, 0.8f * a);
            ps.popPose();
        }
        // phase 7: the final judgment
        float j = t - ForceEntity.T_JUDG;
        if (j >= 0f) {
            float k = c01(j / 12f);
            if (k < 1f) {
                float r = Rf * (0.1f + 1.0f * smooth(k));
                Draw.disc(add(buf, WAVE), ps, r + 1.2f, j * 3f, 0.24f, HOT, 1f - k);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 2.4f), r + 1.2f, 0.26f, 120, 1f, 0f, ORANGE, 1f - k);
                Draw.band(trans(buf, DUST_WAVE), ps, r, 0f, 4.5f, 96, 22f, j * 0.02f, DUSTC, 0.9f * (1f - k * 0.5f), 0f);
            }
            float col = c01(j / 3f) * (1f - c01((j - 12f) / 40f));
            if (col > 0.01f) {
                Draw.band(trans(buf, DUST_COL), ps, 4.4f, 0f, 24f, 36, 3f, -t * 0.008f, DUSTC, 0.95f * col, 0.05f * col);
            }
            for (int i = 0; i < 14; i++) {
                double ang = i * Math.PI * 2 / 14 + seed * 0.01;
                float r = Rf * (0.45f + 0.25f * (i % 2));
                float life = life(j, i * 0.35f, 5f, 30f, 22f);
                Gfx.spike(buf, ps, (float) (Math.cos(ang) * r), (float) (Math.sin(ang) * r), 1.1f, 6f + 3f * (i % 3), life, i * 25f, 1f, light);
            }
        }
        // the lasting battlefield and the latest eruption
        if (impacted && b < zone) {
            float za = c01(b / 14f) * (1f - c01((b - zone + 24f) / 24f));
            Draw.band(add(buf, EMBER_COL), ps, Rs, 0f, 3.6f, 96, 12f, t * 0.01f, ORANGE, 0.55f * za, 0f);
            Draw.band(trans(buf, DUST_WAVE), ps, Rs, 0f, 1.4f, 96, 22f, -t * 0.006f, DUSTC, 0.35f * za, 0f);
            int ea = e.getEruptAge();
            if (ea >= 0) {
                float et = e.tickCount - 1 + pt - ea;
                if (et >= 0f && et < ForceEntity.ERUPT_LIFE) {
                    Vec3 ep = e.getEruptPos().subtract(e.getPosition(pt));
                    float life = life(et, 0f, 2f, 5f, 6f);
                    Gfx.spike(buf, ps, (float) ep.x, (float) ep.z, 0.8f, 4.2f, life, et * 20f, 1f, light);
                    Gfx.spike(buf, ps, (float) ep.x + 0.6f, (float) ep.z - 0.4f, 0.55f, 2.8f, life, 40f + et * 20f, 1f, light);
                    ps.pushPose();
                    ps.translate(ep.x, 0.05, ep.z);
                    Draw.disc(add(buf, GLOW), ps, 2.6f, 0f, 0.1f, ORANGE, 0.7f * (1f - c01(et / ForceEntity.ERUPT_LIFE)));
                    ps.popPose();
                }
            }
        }
    }
}
