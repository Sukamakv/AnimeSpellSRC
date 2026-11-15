package com.example.jjkspells.afterlife.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.afterlife.entity.FuneralEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Funeral circle, spirit gate with independently rotating rings, giant ghost, cremation column, shockwave and cracks. */
public class FuneralRenderer extends EntityRenderer<FuneralEntity> {
    private static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/environment/al_" + n + ".png");
    }

    private static final ResourceLocation CIRCLE = tex("circle_crimson"), RUNE = tex("rune_ring"), SIGIL = tex("butterfly_sigil"), GLOW = tex("glow"),
            MIST = tex("mist"), P_OUTER = tex("portal_outer"), P_MID = tex("portal_mid"), P_INNER = tex("portal_inner"), P_CORE = tex("portal_core"),
            GIANT = tex("ghost_giant"), GIANT_OPEN = tex("ghost_giant_open"), BAND = tex("flame_band"), SHOCK = tex("butterfly_shock"),
            RING = tex("ring_wave"), CRACKS = tex("cracks");
    private static final float[] CRIMSON = {0.9f, 0.12f, 0.14f}, ORANGE = {1f, 0.55f, 0.16f}, GOLD = {1f, 0.82f, 0.36f}, PINK = {1f, 0.6f, 0.58f},
            WHITE = {1f, 1f, 1f}, DARK = {0.16f, 0.02f, 0.04f}, DEEP = {0.55f, 0.03f, 0.08f};

    public FuneralRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(FuneralEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(FuneralEntity e) {
        return GLOW;
    }

    private static float c01(float v) {
        return Mth.clamp(v, 0f, 1f);
    }

    private static com.mojang.blaze3d.vertex.VertexConsumer add(MultiBufferSource b, ResourceLocation t) {
        return b.getBuffer(RenderType.eyes(t));
    }

    @Override
    public void render(FuneralEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        final int T3 = FuneralEntity.T3, T4 = FuneralEntity.T4, TB = FuneralEntity.T_BOOM, T5 = FuneralEntity.T5;
        float r1 = e.getR1(), r2 = e.getR2();
        boolean crimson = e.isCrimson();
        float[] flame = crimson ? DEEP : CRIMSON;

        // ---- ground: circle, runes, giant butterfly sigil, mist
        float ground = c01(t / 14f) * (1f - c01((t - (TB + 4)) / 16f));
        if (ground > 0.01f) {
            Draw.disc(add(buf, GLOW), ps, r1 * 1.6f, 0f, 0.02f, flame, 0.5f * ground);
            Draw.disc(add(buf, CIRCLE), ps, r1, t * 0.8f, 0.04f, flame, 0.95f * ground);
            Draw.disc(add(buf, RUNE), ps, r1 * 0.72f, -t * 1.3f, 0.05f, ORANGE, 0.85f * ground);
            Draw.disc(add(buf, SIGIL), ps, r1 * 0.4f, t * 0.6f, 0.06f, GOLD, 0.95f * ground);
            Draw.disc(buf.getBuffer(com.example.jjkspells.client.SoftRender.entity(MIST)), ps, r2 * 0.95f, t * 0.4f, 0.1f, DARK, 0.75f * ground);
        }

        // ---- spirit gate
        float pa = c01((t - T3) / 12f) * (1f - c01((t - (TB + 2)) / 10f));
        Vec3 po = e.portalOffset();
        if (pa > 0.01f) {
            ps.pushPose();
            ps.translate(po.x, po.y, po.z);
            ps.mulPose(Axis.YP.rotationDegrees(-e.getCasterYaw()));
            ring(buf, ps, P_CORE, 4.6f, -t * 2.4f, 0f, ORANGE, 0.9f * pa);
            ring(buf, ps, P_INNER, 6.0f, t * 1.7f, 0.01f, flame, 0.95f * pa);
            ring(buf, ps, P_MID, 7.4f, -t * 1.1f, 0.02f, GOLD, 0.9f * pa);
            ring(buf, ps, P_OUTER, 9.0f, t * 0.6f, 0.03f, flame, 0.95f * pa);
            ring(buf, ps, GLOW, 12f, 0f, -0.02f, flame, 0.5f * pa);
            ps.popPose();
        }

        // ---- the giant ghost: emerges, dives, detonates
        float gin = c01((t - (T3 + 8)) / 18f), gout = 1f - c01((t - TB) / 7f);
        float ga = gin * gout;
        if (ga > 0.01f) {
            Vec3 rel;
            float scale;
            if (t < T4) {
                rel = po.add(0, Mth.sin(t * 0.2f) * 0.2, 0);
                scale = 0.5f + 0.5f * c01((t - (T3 + 8)) / 22f);
            } else {
                double f = Mth.clamp((t - T4) / (double) (TB - T4), 0.0, 1.0);
                rel = e.ghostRel(f);
                scale = 1.0f + 0.7f * (float) f + (t > TB ? (t - TB) * 0.35f : 0f);
            }
            ps.pushPose();
            ps.translate(rel.x, rel.y, rel.z);
            ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.15f) * 6f));
            ps.pushPose();
            ps.translate(0, 0, -0.05);
            Draw.xy(add(buf, GLOW), ps, 7.5f * scale, 7.5f * scale, flame, 0.7f * ga);
            ps.popPose();
            Draw.xy(buf.getBuffer(RenderType.entityTranslucent(t >= T4 - 4 ? GIANT_OPEN : GIANT)), ps, 4.6f * scale, 4.6f * scale, WHITE, 0.95f * ga);
            ps.popPose();
        }

        // ---- cremation: spiral column of fire, butterfly shockwave, cracks in the ground
        float b = t - TB;
        if (b >= 0f) {
            float ca = 1f - c01(b / 28f);
            if (ca > 0.01f) {
                Draw.band(add(buf, BAND), ps, 3.2f + b * 0.12f, 0f, 36f + b * 2.5f, 40, 6f, -t * 0.07f, ORANGE, 0.95f * ca, 0.15f * ca);
                Draw.band(add(buf, BAND), ps, 1.8f + b * 0.06f, 0f, 44f + b * 3f, 32, 4f, t * 0.09f, GOLD, 0.95f * ca, 0.3f * ca);
                Draw.band(add(buf, GLOW), ps, 0.9f, 0f, 50f, 20, 1f, 0f, WHITE, 0.9f * ca, 0.5f * ca);
            }
            float sp = c01(b / 18f);
            Draw.disc(add(buf, SHOCK), ps, r2 * 1.15f * sp + 1f, t * 3f, 0.12f, ORANGE, (1f - sp) * 0.95f);
            Draw.ring(add(buf, RING), ps, Math.max(0f, r2 * 1.2f * sp - 2f), r2 * 1.2f * sp + 1.5f, 0.14f, 64, 1f, 0f, GOLD, (1f - sp));
            float cr = c01(b / 4f) * (1f - c01(b / 52f));
            Draw.disc(add(buf, CRACKS), ps, r2, 0f, 0.07f, ORANGE, cr);
            Draw.disc(add(buf, GLOW), ps, r2 * 1.4f, 0f, 0.03f, flame, 0.6f * (1f - c01(b / 22f)));
        }

        // ---- the director's last wave of crimson light
        float w = t - (T5 + 4);
        if (w >= 0f && w < 24f) {
            float k = w / 24f;
            Draw.ring(add(buf, RING), ps, Math.max(0f, 16f * k - 1.5f), 16f * k + 1.5f, 0.16f, 64, 1f, 0f, PINK, 1f - k);
        }
    }

    /** One portal ring rotating in the gate plane. */
    private static void ring(MultiBufferSource buf, PoseStack ps, ResourceLocation t, float radius, float deg, float z, float[] c, float a) {
        ps.pushPose();
        ps.translate(0, 0, z);
        ps.mulPose(Axis.ZP.rotationDegrees(deg));
        Draw.xy(add(buf, t), ps, radius, radius, c, a);
        ps.popPose();
    }
}
