package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.irontomb.entity.IrontombSatellite;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The Manipulated Logos (violet, a spinning octahedron of quantum shards inside three rings) and the
 * Imprisoned Mythos (gold, a chained iron moon with rings of light). Grave Wound dims them and cracks them open.
 */
public class SatelliteRenderer extends EntityRenderer<IrontombSatellite> {
    /** Visual size of a satellite (was 1.35). */
    private static final float SAT_SCALE = 6.5f;

    public SatelliteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(IrontombSatellite e) {
        return FxTypes.tex("white");
    }

    @Override
    public boolean shouldRender(IrontombSatellite e, Frustum f, double x, double y, double z) {
        return true;
    }

    private static void bill(PoseStack ps, MultiBufferSource buf, String tex, Vec3 p, float size, float roll, float r, float g, float b, float a) {
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        if (roll != 0f) {
            ps.mulPose(Axis.ZP.rotation(roll));
        }
        Fx.quadXY(buf.getBuffer(FxTypes.add(tex)), ps, size, size, r, g, b, a);
        ps.popPose();
    }

    private static void ring(PoseStack ps, MultiBufferSource buf, float size, float ax, float ay, float az, float spin, float r, float g, float b, float a) {
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(ax));
        ps.mulPose(Axis.YP.rotationDegrees(ay));
        ps.mulPose(Axis.ZP.rotationDegrees(az));
        ps.mulPose(Axis.ZP.rotation(spin));
        Fx.quadXY(buf.getBuffer(FxTypes.add("halo_ring")), ps, size, size, r, g, b, a);
        ps.popPose();
    }

    @Override
    public void render(IrontombSatellite s, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float age = s.tickCount + pt;
        boolean logos = s.getSatType() == 0;
        boolean grave = s.isGrave();
        float[] col = logos ? AttackFx.VIOLET : AttackFx.GOLD;
        float dim = grave ? 0.18f : 1f;
        float hp = Mth.clamp(s.getHealth() / s.getMaxHealth(), 0f, 1f);
        Vec3 ep = s.getPosition(pt);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(ep);

        ps.pushPose();
        ps.translate(0, 0.8 * SAT_SCALE / 1.35f + (grave ? Mth.sin(age * 0.3f) * 0.05f : 0), 0);
        ps.scale(SAT_SCALE, SAT_SCALE, SAT_SCALE);

        if (logos) {
            // octahedron of shards
            ps.pushPose();
            ps.mulPose(Axis.YP.rotation(age * 0.05f));
            ps.mulPose(Axis.XP.rotationDegrees(18f * Mth.sin(age * 0.04f)));
            float ext = grave ? 0.6f : 1.5f + 0.15f * Mth.sin(age * 0.2f);
            for (int pass = 0; pass < 2; pass++) {
                VertexConsumer vc = pass == 0 ? buf.getBuffer(FxTypes.solid("body_iron")) : buf.getBuffer(FxTypes.add("body_glow"));
                float cr = pass == 0 ? 0.8f : col[0], cg = pass == 0 ? 0.7f : col[1], cb = pass == 0 ? 1f : col[2], ca = pass == 0 ? 1f : 0.9f * dim;
                float rad = pass == 0 ? 0.5f : 0.54f;
                Vec3[] d = {new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1)};
                for (Vec3 v : d) {
                    Fx.spike(vc, ps, v.scale(0.25), v, ext, rad, cr, cg, cb, ca);
                }
            }
            ps.popPose();
            if (!grave) {
                ring(ps, buf, 1.9f, 0, 0, 0, age * 0.05f, col[0], col[1], col[2], 0.9f);
                ring(ps, buf, 1.9f, 90, 0, 0, -age * 0.04f, col[0], col[1], col[2], 0.8f);
                ring(ps, buf, 1.9f, 0, 90, 0, age * 0.06f, 1f, 0.9f, 1f, 0.7f);
            }
        } else {
            // iron moon bound in chains
            ps.pushPose();
            ps.mulPose(Axis.YP.rotation(age * 0.02f));
            Fx.sphere(buf.getBuffer(FxTypes.solid("planet")), ps, 0.95f, 12, 20, 0f, 1f, 1f, 1f, 1f);
            ps.popPose();
            ps.pushPose();
            ps.mulPose(Axis.YP.rotation(-age * 0.015f));
            Fx.sphere(buf.getBuffer(FxTypes.add("planet")), ps, 0.99f, 10, 16, 0f, col[0], col[1], col[2], 0.55f * dim);
            ps.popPose();
            if (!grave) {
                ring(ps, buf, 2.1f, 70, 0, 0, age * 0.05f, col[0], col[1], col[2], 0.95f);
                ring(ps, buf, 2.1f, -20, 40, 0, -age * 0.05f, 1f, 0.95f, 0.7f, 0.85f);
                for (int i = 0; i < 4; i++) {
                    float ang = i * 1.5708f + age * 0.03f;
                    Vec3 a = new Vec3(Math.cos(ang) * 0.95, -0.2, Math.sin(ang) * 0.95);
                    Vec3 b = new Vec3(Math.cos(ang) * 1.6, -2.6 - Mth.sin(age * 0.1f + i) * 0.3, Math.sin(ang) * 1.6);
                    Fx.ribbon(buf.getBuffer(FxTypes.add("beam")), ps, a, b, cam.scale(1.0 / SAT_SCALE), 0.16f, age * 0.05f, 0.5f, col[0], col[1], col[2], 0.8f);
                }
            }
        }
        // core glow
        bill(ps, buf, "glow", Vec3.ZERO, (grave ? 1.6f : 3.6f) * (0.9f + 0.1f * Mth.sin(age * 0.15f)), 0, col[0], col[1], col[2], 0.55f * dim + 0.2f * (1 - hp) * dim);
        if (!grave) {
            bill(ps, buf, "spark", Vec3.ZERO, 1.2f, age * 0.05f, 1f, 1f, 1f, 0.9f);
        } else {
            bill(ps, buf, "glow", Vec3.ZERO, 2.4f, 0, 1f, 0.25f, 0.1f, 0.35f);
            ps.pushPose();
            ps.translate(0, 0, 0);
            ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            float[] f = Fx.frame(15 - (int) Mth.clamp((age % 40) / 40f * 3f, 0f, 3f), 4);
            Fx.quadXY(buf.getBuffer(FxTypes.add("crack")), ps, 1.7f, 1.7f, f[0], f[1], f[2], f[3], 1f, 0.45f, 0.2f, 0.8f);
            ps.popPose();
        }
        ps.popPose();

        // what the satellite is doing
        Entity boss = s.level().getEntity(s.getBossId());
        Vec3 center = new Vec3(0, 1.1, 0);
        int mark = s.getMark();
        if (mark >= 0 && !grave) {
            Entity t = s.level().getEntity(mark);
            if (t != null) {
                Vec3 tp = t.getPosition(pt).subtract(ep).add(0, t.getBbHeight() * 0.5, 0);
                Fx.ribbon(buf.getBuffer(FxTypes.add("beam")), ps, center, tp, cam, 0.16f, age * 0.2f, 0.4f, col[0], col[1], col[2], 0.55f);
                float pulse = 0.65f + 0.35f * Mth.sin(age * 0.4f);
                bill(ps, buf, "spark", tp.add(0, 1.2, 0), 1.1f * pulse, age * 0.1f, col[0], col[1], col[2], 0.95f);
                ps.pushPose();
                ps.translate(tp.x, tp.y - t.getBbHeight() * 0.5 + 0.06, tp.z);
                Fx.quadXZ(buf.getBuffer(FxTypes.add("glyph")), ps, 1.5f, 0f, col[0], col[1], col[2], 0.9f * pulse);
                ps.popPose();
            }
        }
        int bt = s.getBoltT();
        if (bt >= 0 && !grave) {
            Entity t = s.level().getEntity(s.getBoltTarget());
            if (t != null) {
                Vec3 tp = t.getPosition(pt).subtract(ep).add(0, t.getBbHeight() * 0.5, 0);
                float f = bt + pt;
                float charge = Fx.ramp(f, 0, IrontombSatellite.BOLT_FIRE);
                bill(ps, buf, "glow", center, 3.2f * charge, 0, col[0], col[1], col[2], 0.9f);
                bill(ps, buf, "spark", center, 2.2f * charge, age * 0.2f, 1f, 1f, 1f, 1f);
                if (f < IrontombSatellite.BOLT_FIRE) {
                    Fx.ribbon(buf.getBuffer(FxTypes.add("beam")), ps, center, tp, cam, 0.1f, age * 0.3f, 0.5f, col[0], col[1], col[2], 0.35f * charge);
                    bill(ps, buf, "spark", tp, 0.7f * charge, age * 0.2f, col[0], col[1], col[2], 0.7f);
                } else {
                    float k = Fx.ramp(f, IrontombSatellite.BOLT_FIRE, IrontombSatellite.BOLT_FIRE + 10);
                    float w = 1 - k;
                    Fx.ribbon(buf.getBuffer(FxTypes.add("beam")), ps, center, tp, cam, 1.2f * w + 0.2f, age * 1.2f, 0.4f, col[0], col[1], col[2], w);
                    Fx.ribbon(buf.getBuffer(FxTypes.add("beam")), ps, center, tp, cam, 0.5f * w, age * 1.6f, 0.4f, 1f, 1f, 1f, w);
                    bill(ps, buf, "glow", tp, 7f * w, 0, col[0], col[1], col[2], w);
                    ps.pushPose();
                    ps.translate(tp.x, tp.y - t.getBbHeight() * 0.5 + 0.1, tp.z);
                    Fx.flipXZ(buf.getBuffer(FxTypes.add("ring")), ps, 6f, 0f, (int) (k * 15), 4, col[0], col[1], col[2], w);
                    ps.popPose();
                }
            }
        }
    }
}
