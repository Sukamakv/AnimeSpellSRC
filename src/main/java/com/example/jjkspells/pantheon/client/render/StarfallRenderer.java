package com.example.jjkspells.pantheon.client.render;

import com.example.jjkspells.pantheon.config.PanConfig;
import com.example.jjkspells.pantheon.entity.StarfallEntity;
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
 * Starfall Execution: the rune and pillar at take-off, the halo behind the caster, the landing circle, the cone of golden fire
 * during the dive, and the column, shockwave and glowing ground cracks of the impact.
 */
public class StarfallRenderer extends EntityRenderer<StarfallEntity> {
    public StarfallRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(StarfallEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(StarfallEntity e) {
        return GLOW;
    }

    @Override
    public void render(StarfallEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        int A = e.getA(), S = e.getS(), D = e.getD(), hit = A + S + D, dive = A + S;
        float R = e.getR();
        Vec3 base = e.getPosition(pt);
        Vec3 cam = cam(base);
        Entity owner = e.getOwnerEntity();
        Vec3 tk = e.getTakeoff().subtract(base);
        int aftershocks = PanConfig.STAR.aftershocks.get();
        float total = hit + StarfallEntity.FREE_AFTER + aftershocks * StarfallEntity.AFTERSHOCK_GAP + e.getFlameTicks();

        // take-off: golden rune on the ground and a pillar of light
        float up = c01(t / 2f) * (1f - c01((t - A * 0.6f) / (A * 0.6f + 12f)));
        if (up > 0.01f) {
            ps.pushPose();
            ps.translate(tk.x, tk.y, tk.z);
            Draw.disc(add(buf, CIRCLE), ps, 3.0f, t * 8f, 0.06f, GOLD, 0.95f * up);
            Draw.disc(add(buf, RUNE), ps, 2.1f, -t * 11f, 0.07f, WARM, 0.85f * up);
            Draw.disc(add(buf, GLOW), ps, 4.2f, 0f, 0.05f, AMBER, 0.5f * up);
            Draw.band(add(buf, BEAM), ps, 0.95f, 0f, 46f, 24, 3f, t * 0.06f, WHITE, 0.95f * up, 0.0f);
            Draw.band(add(buf, FLAME_COL), ps, 1.25f, 0f, 9f, 24, 3f, t * 0.04f, WARM, 0.8f * up, 0f);
            ps.popPose();
        }

        // the halo behind the caster while it hangs in the sky
        if (owner != null && t >= A && t < dive + 4) {
            float a = c01((t - A) / 4f) * (1f - c01((t - dive) / 4f));
            Vec3 op = owner.getPosition(pt).subtract(base).add(0, owner.getBbHeight() * 0.6, 0);
            Vec3 away = op.subtract(cam);
            away = away.lengthSqr() < 1.0E-6 ? Vec3.ZERO : away.normalize();
            ps.pushPose();
            ps.translate(op.x + away.x * 0.7, op.y + away.y * 0.7, op.z + away.z * 0.7);
            billboard(ps);
            Draw.xy(add(buf, HALO), ps, 2.3f, 2.3f, WHITE, a);
            Draw.xy(add(buf, GLOW), ps, 3.4f, 3.4f, AMBER, 0.5f * a);
            ps.popPose();
        }

        // the landing circle grows brighter as the dive approaches
        float ringIn = c01((t - 1f) / 4f) * (t < hit ? 1f : 1f - c01((t - hit) / 6f));
        if (ringIn > 0.01f) {
            float br = 0.35f + 0.65f * c01((t - dive) / (float) D);
            Draw.disc(add(buf, TARGET), ps, R, t * 3f, 0.07f, GOLD, br * ringIn);
            Draw.disc(add(buf, CIRCLE), ps, R * 0.34f, -t * 6f, 0.08f, WARM, br * ringIn);
            Draw.disc(add(buf, GLOW), ps, R * 1.05f, 0f, 0.05f, AMBER, 0.25f * br * ringIn);
        }

        // the cone of golden fire around the diving caster
        if (owner != null && t >= dive && t <= hit + 1) {
            Vec3 op = owner.getPosition(pt).subtract(base).add(0, 1.0, 0);
            Vec3 d = op.subtract(new Vec3(0, 1.0, 0));
            Vec3 toGround = new Vec3(0, 0, 0).subtract(d);
            Vec3 dir = toGround.lengthSqr() < 1.0E-6 ? new Vec3(0, -1, 0) : toGround.normalize();
            float a = c01((t - dive) / 3f);
            Vec3 tail = op.subtract(dir.scale(9.0));
            Draw.ribbon(add(buf, TRAIL), ps, tail, op, cam, 0.0f, 1.5f, 0f, 1f, AMBER, 0f, 0.85f * a);
            Draw.ribbon(add(buf, TRAIL), ps, tail, op, cam, 0.0f, 1.0f, 0f, 1f, GOLD, 0f, a);
            Draw.ribbon(add(buf, TRAIL), ps, op.subtract(dir.scale(6.0)), op, cam, 0.0f, 0.55f, 0f, 1f, WHITE, 0f, a);
            ps.pushPose();
            ps.translate(op.x, op.y, op.z);
            billboard(ps);
            Draw.xy(add(buf, GLOW), ps, 2.2f, 2.2f, GOLD, 0.8f * a);
            ps.popPose();
        }

        // impact
        if (t >= hit) {
            float b = t - hit;
            float col = c01(b / 2f) * (1f - c01((b - 8f) / 20f));
            if (col > 0.01f) {
                Draw.band(add(buf, BEAM), ps, 1.8f, 0f, 70f, 28, 3f, t * 0.05f, WHITE, col, 0f);
                Draw.band(add(buf, FLAME_COL), ps, 2.4f, 0f, 20f, 28, 3f, t * 0.05f, GOLD, 0.9f * col, 0f);
            }
            float k = c01(b / 9f);
            if (k < 1f) {
                float r = R * (0.2f + 0.8f * smooth(k));
                Draw.disc(add(buf, WAVE), ps, r + 0.5f, b * 5f, 0.12f, GOLD, (1f - k) * 0.9f);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 1.2f), r + 0.8f, 0.14f, 72, 1f, 0f, WARM, 1f - k);
            }
            float k2 = c01((b - 3f) / 12f);
            if (k2 > 0f && k2 < 1f) {
                float r = R * 1.1f * smooth(k2);
                Draw.ring(add(buf, RING_WAVE), ps, Math.max(0f, r - 1.0f), r + 1.0f, 0.16f, 72, 1f, 0f, AMBER, (1f - k2) * 0.8f);
            }
            // fractured ground: glowing cracks, then the flames that remain
            float cr = c01(b / 3f) * (1f - c01((b - (total - hit - 30f)) / 30f));
            if (cr > 0.01f) {
                Draw.disc(add(buf, CRACKS), ps, R * 1.05f, 20f, 0.09f, GOLD, 0.95f * cr);
                float fl = 0.22f + 0.1f * Mth.sin(t * 0.5f);
                Draw.disc(add(buf, GLOW), ps, R * 1.0f, 0f, 0.06f, AMBER, fl * cr);
                Draw.band(add(buf, FLAME_COL), ps, R * 0.6f, 0f, 2.4f, 48, 9f, t * 0.015f, AMBER, 0.55f * cr, 0f);
            }
        }
    }
}
