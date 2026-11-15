package com.example.jjkspells.malphite.client.render;

import com.example.jjkspells.malphite.entity.GraniteEntity;
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

/** Granite Shield: plates flying in from the ground, the translucent granite dome with glowing seams, and the shattering detonation. */
public class GraniteRenderer extends EntityRenderer<GraniteEntity> {
    public GraniteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(GraniteEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(GraniteEntity e) {
        return GLOW;
    }

    @Override
    public void render(GraniteEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        int form = e.getFormTicks();
        int fin = e.getDetonationAt();
        float R = e.getRadius();
        float charge = e.getCharge();
        boolean unbroken = e.isUnbroken();
        Entity owner = e.getOwnerEntity();
        Vec3 base = e.getPosition(pt);
        Vec3 body = owner != null ? owner.getPosition(pt).subtract(base).add(0, 0.95, 0) : new Vec3(0, 0.95, 0);
        float vis = fin >= 0 && t > fin ? 0f : 1f;

        // forming: runic glow under the feet and plates rushing in
        float f = c01(t / form);
        if (vis > 0f && t < form + 12) {
            float a = (1f - c01((t - form) / 12f));
            Draw.disc(add(buf, CIRCLE), ps, 2.8f * smooth(f), t * 3f, 0.06f, ORANGE, 0.9f * a);
            Draw.disc(add(buf, CRACKS), ps, 3.4f * smooth(f), 15f, 0.07f, ORANGE, 0.8f * a);
            Draw.disc(add(buf, GLOW), ps, 4.0f * smooth(f), 0f, 0.05f, ORANGE, 0.3f * a);
            for (int i = 0; i < 12; i++) {
                double ang = i * Math.PI / 6 + i * 0.7;
                float k = smooth(f * 1.2f - i * 0.03f);
                float r = Mth.lerp(k, 3.2f, 0.9f);
                float y = Mth.lerp(k, 0.1f, 0.4f + 1.4f * ((i % 4) / 3f));
                ps.pushPose();
                ps.translate(body.x + Math.cos(ang) * r, y, body.z + Math.sin(ang) * r);
                billboard(ps);
                ps.mulPose(Axis.ZP.rotationDegrees(i * 33f + t * 5f));
                float s = 0.3f + 0.12f * (i % 3);
                Draw.xy(trans(buf, PLATE), ps, s, s, STONE, c01(f * 3f) * (1f - 0.9f * c01((t - form) / 6f)));
                ps.popPose();
            }
        }
        // the armor: a dome of granite plates around the body, with glowing seams that grow brighter as damage is stored
        if (vis > 0f && t >= 2f) {
            float a = c01((t - 4f) / (float) form) * (1f - c01((t - fin) / 3f) * (fin >= 0 ? 1f : 0f));
            float pulse = 0.5f + 0.5f * Mth.sin(t * (unbroken ? 0.9f : 0.35f));
            ps.pushPose();
            ps.translate(body.x, body.y + 0.15, body.z);
            ps.scale(1f, 1.45f, 1f);
            Draw.sphere(trans(buf, DOME), ps, 1.05f, 14, 24, t * 0.003f, STONE, 0.75f * a);
            ps.popPose();
            ps.pushPose();
            ps.translate(body.x, body.y + 0.15, body.z);
            ps.scale(1f, 1.45f, 1f);
            Draw.sphere(add(buf, GLOW), ps, 1.1f, 10, 16, 0f, ORANGE, (0.07f + 0.22f * charge + (unbroken ? 0.18f * pulse : 0f)) * a);
            ps.popPose();
            // broad shoulders and chest plates that always face the camera
            for (int i = 0; i < 5; i++) {
                double ang = i * 1.2566 + 0.4;
                ps.pushPose();
                ps.translate(body.x + Math.cos(ang) * 0.8, body.y + 0.55 + 0.4 * (i % 2), body.z + Math.sin(ang) * 0.8);
                billboard(ps);
                float s = 0.42f;
                Draw.xy(trans(buf, PLATE), ps, s, s, STONE, 0.95f * a);
                ps.popPose();
            }
        }
        // the detonation
        if (fin >= 0 && t >= fin) {
            float b = t - fin;
            float k = c01(b / 11f);
            if (k < 1f) {
                float r = R * (0.1f + 0.95f * smooth(k));
                Draw.disc(add(buf, WAVE), ps, r + 1f, b * 4f, 0.14f, HOT, (1f - k) * 0.95f);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 2f), r + 1f, 0.16f, 96, 1f, 0f, ORANGE, 1f - k);
                Draw.band(trans(buf, DUST_WAVE), ps, r, 0f, 2.6f, 80, 18f, b * 0.02f, DUSTC, 0.9f * (1f - k * 0.7f), 0f);
                ps.pushPose();
                ps.translate(body.x, body.y, body.z);
                billboard(ps);
                float s = 4.5f * (1f - k) + 0.5f;
                Draw.xy(add(buf, GLOW), ps, s, s, HOT, 0.9f * (1f - k));
                Draw.xy(add(buf, STAR), ps, s * 1.4f, s * 1.4f, WHITE, 0.8f * (1f - k));
                ps.popPose();
            }
            // hundreds of fragments: plates flung outward and falling
            int seed = e.tickCount;
            float fa = 1f - c01((b - 12f) / 14f);
            for (int i = 0; i < 28; i++) {
                double ang = i * 2.399963;
                double sp = 0.45 + 0.35 * ((i * 7) % 5) / 5.0;
                double h = sp * b * 0.8;
                double y = body.y + 0.8 + 0.5 * ((i * 3) % 5) * 0.2 * b * 0.1 - 0.5 * 0.05 * b * b * 0.6 + 0.2 * b;
                ps.pushPose();
                ps.translate(body.x + Math.cos(ang) * h * (1f + i % 3), Math.max(0.1, y), body.z + Math.sin(ang) * h * (1f + i % 3));
                billboard(ps);
                ps.mulPose(Axis.ZP.rotationDegrees(b * (10f + i * 3f) + i * 40f));
                float s = 0.22f + 0.1f * (i % 3);
                Draw.xy(trans(buf, i % 2 == 0 ? PLATE : ROCK), ps, s, s, STONE, fa);
                ps.popPose();
            }
            float cr = c01(b / 3f) * (1f - c01((b - 12f) / 14f));
            Draw.disc(add(buf, CRACKS), ps, R * 0.6f, 40f, 0.09f, ORANGE, 0.9f * cr);
        }
    }
}
