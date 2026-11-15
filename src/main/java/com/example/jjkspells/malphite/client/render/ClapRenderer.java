package com.example.jjkspells.malphite.client.render;

import com.example.jjkspells.malphite.entity.ClapEntity;
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

/** Thunderclap: gathering cracks, the dust shockwave, the stone ring, the pillars of the final eruption and the glowing fractured ground. */
public class ClapRenderer extends EntityRenderer<ClapEntity> {
    public ClapRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(ClapEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ClapEntity e) {
        return GLOW;
    }

    @Override
    public void render(ClapEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        float R = (float) (double) com.example.jjkspells.malphite.config.MalConfig.CLAP.radius.get();
        float R2 = (float) (double) com.example.jjkspells.malphite.config.MalConfig.CLAP.outerRadius.get();
        int seed = e.getSeed();
        int zone = e.getZoneTicks();
        float tEnd = ClapEntity.T2 + zone;

        // gathering: the ground vibrates and starts to glow
        float g = c01(t / ClapEntity.T1) * (1f - c01((t - ClapEntity.T1) / 6f));
        if (g > 0.01f) {
            Draw.disc(add(buf, GLOW), ps, 2.6f * g + 0.5f, 0f, 0.05f, ORANGE, 0.5f * g);
            Draw.disc(add(buf, CRACKS), ps, 2.2f * g + 0.3f, t * 6f, 0.06f, ORANGE, 0.8f * g);
        }
        // first slam
        float b1 = t - ClapEntity.T1;
        if (b1 >= 0f) {
            float k = c01(b1 / 9f);
            if (k < 1f) {
                float r = R * (0.15f + 0.85f * smooth(k));
                Draw.disc(add(buf, WAVE), ps, r + 0.6f, b1 * 5f, 0.1f, HOT, (1f - k) * 0.9f);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 1.4f), r + 0.6f, 0.12f, 72, 1f, 0f, ORANGE, 1f - k);
                Draw.band(trans(buf, DUST_WAVE), ps, r, 0f, 1.7f, 64, 14f, b1 * 0.02f, DUSTC, 0.85f * (1f - k * 0.7f), 0f);
            }
        }
        // the shockwave grows to the outer radius
        float w = (t - ClapEntity.W0) / (ClapEntity.W1 - ClapEntity.W0);
        if (w >= 0f && w <= 1.4f) {
            float wk = c01(w);
            float r = R + (R2 - R) * wk;
            float fade = 1f - c01((w - 1f) / 0.4f);
            Draw.ring(add(buf, RING_WAVE), ps, r - 1.2f, r + 0.8f, 0.14f, 96, 1f, 0f, AMBER, 0.95f * fade);
            Draw.band(trans(buf, DUST_WAVE), ps, r, 0f, 2.2f, 80, 18f, -t * 0.015f, DUSTC, 0.9f * fade, 0f);
            for (int i = 0; i < 20; i++) {
                double a = i * Math.PI * 2 / 20 + (seed % 10) * 0.1;
                float h = 0.9f + 0.5f * ((i * 7) % 3);
                spike(buf, ps, (float) (Math.cos(a) * r), (float) (Math.sin(a) * r), 0.55f, h, c01(1f - Math.abs(w * 20f - i) / 3f) * fade, (float) Math.toDegrees(a), 1f, light);
            }
        }
        // compression: the cracks glow brighter and brighter
        float c = c01((t - ClapEntity.W1) / (ClapEntity.T2 - ClapEntity.W1));
        // cracks (from the first slam until the zone ends)
        float crackLife = c01(b1 / 3f) * (1f - c01((t - tEnd + 30f) / 30f));
        if (crackLife > 0.01f) {
            float zr = e.getZoneRadius();
            float rad = t < ClapEntity.T2 ? Mth.lerp(c01((t - ClapEntity.T1) / 24f), R * 0.7f, R2 * 1.05f) : Math.max(zr * 1.05f, R * 1.05f);
            float pulse = t < ClapEntity.T2 ? 0.7f + 0.3f * c + 0.1f * Mth.sin(t * 1.7f) : 0.55f + 0.1f * Mth.sin(t * 0.4f);
            Draw.disc(add(buf, CRACKS), ps, rad, 12f, 0.09f, ORANGE, pulse * crackLife);
            Draw.disc(add(buf, GLOW), ps, rad * 0.95f, 0f, 0.06f, ORANGE, (t < ClapEntity.T2 ? 0.1f + 0.2f * c : 0.14f) * crackLife);
        }
        // the final eruption
        float b2 = t - ClapEntity.T2;
        if (b2 >= 0f) {
            int pillars = 16;
            for (int i = 0; i < pillars; i++) {
                double a = ((seed * 7 + i * 137) % 360) * Math.PI / 180.0 + i * 2.399963;
                float r = 1.2f + (R - 1.5f) * (float) Math.sqrt((i + 0.5) / pillars);
                float h = 4.2f + 3.2f * (((seed + i * 13) % 10) / 10f);
                float life = life(b2, i * 0.4f, 5f, 18f, 14f);
                spike(buf, ps, (float) (Math.cos(a) * r), (float) (Math.sin(a) * r), 0.8f + 0.5f * (i % 3) * 0.3f, h, life, i * 31f, 1f, light);
            }
            float col = c01(b2 / 3f) * (1f - c01((b2 - 10f) / 30f));
            if (col > 0.01f) {
                Draw.band(trans(buf, DUST_COL), ps, 3.4f, 0f, 10f, 28, 3f, t * 0.01f, DUSTC, 0.95f * col, 0.1f * col);
                Draw.band(trans(buf, DUST_COL), ps, R * 0.8f, 0f, 3.5f, 64, 10f, -t * 0.01f, DUSTC, 0.8f * col, 0f);
            }
            float k = c01(b2 / 10f);
            if (k < 1f) {
                float r = R * (0.15f + 0.95f * smooth(k));
                Draw.disc(add(buf, WAVE), ps, r + 0.8f, b2 * 5f, 0.16f, HOT, (1f - k));
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 1.8f), r + 0.9f, 0.18f, 96, 1f, 0f, ORANGE, 1f - k);
            }
        }
        // the fractured-earth zone: a faint wall of rising embers at its edge and the latest eruption
        if (b2 >= 0f && b2 < zone) {
            float zr = e.getZoneRadius();
            float za = c01(b2 / 12f) * (1f - c01((b2 - zone + 20f) / 20f));
            Draw.band(add(buf, EMBER_COL), ps, zr, 0f, 2.8f, 64, 9f, t * 0.01f, ORANGE, 0.5f * za, 0f);
            int ea = e.getEruptAge();
            if (ea >= 0) {
                float et = e.tickCount - 1 + pt - ea;
                if (et >= 0f && et < ClapEntity.ERUPT_LIFE) {
                    Vec3 ep = e.getEruptPos().subtract(e.getPosition(pt));
                    float life = life(et, 0f, 2f, 5f, 6f);
                    spike(buf, ps, (float) ep.x, (float) ep.z, 0.7f, 3.4f, life, et * 20f, 1f, light);
                    spike(buf, ps, (float) ep.x + 0.5f, (float) ep.z - 0.3f, 0.5f, 2.3f, life, 40f + et * 20f, 1f, light);
                    ps.pushPose();
                    ps.translate(ep.x, 0.05, ep.z);
                    Draw.disc(add(buf, GLOW), ps, 2.4f, 0f, 0.1f, ORANGE, 0.7f * (1f - c01(et / ClapEntity.ERUPT_LIFE)));
                    ps.popPose();
                }
            }
        }
    }

    private static void spike(MultiBufferSource buf, PoseStack ps, float x, float z, float w, float h, float rise, float rot, float a, int light) {
        Gfx.spike(buf, ps, x, z, w, h, rise, rot, a, light);
    }
}
