package com.example.jjkspells.client.render;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.gil.entity.GilFxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.client.render.FxDraw.FULL_BRIGHT;
import static com.example.jjkspells.client.render.FxDraw.c;
import static com.example.jjkspells.client.render.FxDraw.ease;

/** Draws GilFxEntity: giant plunging weapons, Merodach's slash, Merodach in hand, magic circles and ground scars. */
public class GilFxRenderer extends EntityRenderer<GilFxEntity> {
    private static final ResourceLocation CIRCLE = JJKSpells.id("textures/entity/gil_circle.png");
    private static final ResourceLocation SLASH = JJKSpells.id("textures/entity/gil_slash.png");
    private static final ResourceLocation BLADE = JJKSpells.id("textures/entity/merodach_blade.png");
    private static final ResourceLocation GLOW = JJKSpells.id("textures/entity/fx_glow.png");
    private static final ResourceLocation[] WEAPON = new ResourceLocation[8];

    static {
        for (int i = 0; i < 8; i++) WEAPON[i] = JJKSpells.id("textures/entity/gate_weapon_" + i + ".png");
    }

    public GilFxRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(GilFxEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(GilFxEntity e) {
        return CIRCLE;
    }

    @Override
    public void render(GilFxEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float a = e.tickCount + pt;
        switch (e.kind()) {
            case GilFxEntity.GIANT -> giant(e, a, ps, buf);
            case GilFxEntity.SLASH -> slash(e, a, ps, buf);
            case GilFxEntity.HELD -> held(e, a, pt, ps, buf);
            case GilFxEntity.CIRCLE -> circle(e, a, ps, buf, false);
            case GilFxEntity.MARK -> circle(e, a, ps, buf, true);
            default -> {
            }
        }
    }

    private static float fade(float a, int life, float in, float out) {
        return Mth.clamp(a / in, 0f, 1f) * Mth.clamp((life - a) / out, 0f, 1f);
    }

    // ---- circle / mark ---------------------------------------------------------------------------------------------
    private void circle(GilFxEntity e, float a, PoseStack ps, MultiBufferSource buf, boolean mark) {
        Vec3 n = e.end();
        if (mark || n.lengthSqr() < 1e-6) n = new Vec3(0, 1, 0);
        n = n.normalize();
        float R = e.scale();
        float al = fade(a, e.life(), mark ? 3f : 8f, mark ? 14f : 10f);
        int tint = e.type();
        int cr = 255, cg = tint == 1 ? 90 : 255, cb = tint == 1 ? 70 : 255;
        Vec3 u0 = GilDraw.perp(n);
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(CIRCLE));
        for (int layer = 0; layer < 2; layer++) {
            double ang = (layer == 0 ? 1 : -1) * a * (mark ? 1.5 : 3.0);
            double k = layer == 0 ? 1.0 : 0.72;
            Vec3 u = GilDraw.rot(u0, n, ang).scale(R * 1.05 * k);
            Vec3 v = n.cross(u.normalize()).scale(R * 1.05 * k);
            Vec3 o = n.scale(0.02 + layer * 0.03);
            GilDraw.quad(ps, vc, o, u, v, cr, cg, cb, c(255f * al * (layer == 0 ? 1f : 0.7f)), FULL_BRIGHT);
        }
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(),
                R * (mark ? 0.9f : 0.6f), 0f, 255, cg, cb, c(110f * al), FULL_BRIGHT);
    }

    // ---- giant weapon ----------------------------------------------------------------------------------------------
    private void giant(GilFxEntity e, float a, PoseStack ps, MultiBufferSource buf) {
        Vec3 pos = e.position();
        Vec3 s = e.start().subtract(pos), en = e.end().subtract(pos);
        Vec3 dv = en.subtract(s);
        double full = dv.length();
        Vec3 dir = full < 1e-4 ? new Vec3(0, -1, 0) : dv.normalize();
        float L = e.scale();
        int E = Math.max(1, e.emerge()), D = Math.max(1, e.dur());
        Vec3 tail, tip;
        if (a < E) {
            float k = ease(a / E);
            tail = s;
            tip = s.add(dir.scale(L * k));
        } else {
            float f = ease((a - E) / D);
            double travel = (full - L) * f;
            tail = s.add(dir.scale(travel));
            tip = tail.add(dir.scale(L));
        }
        float al = Mth.clamp((e.life() - a) / 8f, 0f, 1f);
        Vec3 mid = tail.add(tip).scale(0.5);
        Vec3 along = tip.subtract(tail).scale(0.5);
        if (along.lengthSqr() < 1e-6) return;
        float hw = L / 8f;
        Vec3 side = GilDraw.perp(dir);
        Vec3 side2 = dir.cross(side).normalize();
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(WEAPON[Math.floorMod(e.type(), 8)]));
        int alpha = c(255f * al);
        GilDraw.quad(ps, vc, mid, along, side.scale(hw), 255, 255, 255, alpha, FULL_BRIGHT);
        GilDraw.quad(ps, vc, mid, along, side2.scale(hw), 255, 255, 255, alpha, FULL_BRIGHT);
        // gold halo along the blade and a bright point
        VertexConsumer g = buf.getBuffer(RenderType.eyes(GLOW));
        ps.pushPose();
        ps.translate(tip.x, tip.y, tip.z);
        FxDraw.billboard(ps, g, this.entityRenderDispatcher.cameraOrientation(), L * 0.28f, 0f, 255, 205, 90, c(190f * al), FULL_BRIGHT);
        ps.popPose();
        ps.pushPose();
        ps.translate(mid.x, mid.y, mid.z);
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(), L * 0.2f, 0f, 255, 205, 90, c(120f * al), FULL_BRIGHT);
        ps.popPose();
    }

    // ---- slash -----------------------------------------------------------------------------------------------------
    private void slash(GilFxEntity e, float a, PoseStack ps, MultiBufferSource buf) {
        Vec3 d = e.end().subtract(e.start());
        Vec3 f = new Vec3(d.x, 0, d.z);
        f = f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
        Vec3 right = new Vec3(-f.z, 0, f.x);
        float W = e.scale();
        float al = fade(a, e.life(), 2f, 8f);
        float grow = 0.55f + 0.45f * ease(a / 6f);
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(SLASH));
        // lying flat: the sweep seen from above
        GilDraw.quad(ps, vc, Vec3.ZERO, right.scale(W * 0.5 * grow), f.scale(W * 0.25 * grow), 255, 255, 255, c(255f * al), FULL_BRIGHT);
        // standing: the sweep seen from the front, a crescent that tilts back
        Vec3 up = new Vec3(0, 1, 0);
        GilDraw.quad(ps, vc, new Vec3(0, 0.2, 0), right.scale(W * 0.5 * grow), up.scale(W * 0.2 * grow).add(f.scale(-W * 0.06)), 255, 255, 255, c(200f * al), FULL_BRIGHT);
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(), W * 0.5f, 0f, 255, 205, 90, c(90f * al), FULL_BRIGHT);
    }

    // ---- Merodach in the hand ---------------------------------------------------------------------------------------
    private void held(GilFxEntity e, float a, float pt, PoseStack ps, MultiBufferSource buf) {
        Entity o = e.level().getEntity(e.ownerId());
        if (o == null) return;
        Vec3 ep = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 op = new Vec3(Mth.lerp(pt, o.xo, o.getX()), Mth.lerp(pt, o.yo, o.getY()), Mth.lerp(pt, o.zo, o.getZ()));
        double psi = Math.toRadians(Mth.rotLerp(pt, o.yRotO, o.getYRot()));
        Vec3 f = new Vec3(-Math.sin(psi), 0, Math.cos(psi));
        Vec3 right = new Vec3(-Math.cos(psi), 0, -Math.sin(psi));
        Vec3 hand = op.subtract(ep).add(right.scale(0.7)).add(f.scale(0.3)).add(0, 1.3, 0);
        float grow = ease(a / 14f);
        // angle from vertical (0) back (-) and forward (+), degrees
        double phi;
        if (a < 16) phi = 0;
        else if (a < 44) phi = -55.0 * ease((a - 16f) / 28f);
        else if (a < 56) phi = -55.0 + 150.0 * ease((a - 44f) / 12f);
        else phi = 95;
        float al = Mth.clamp((e.life() - a) / 6f, 0f, 1f);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 dir = up.scale(Math.cos(Math.toRadians(phi))).add(f.scale(Math.sin(Math.toRadians(phi)))).normalize();
        float L = 5.2f * grow;
        if (L < 0.1f) return;
        Vec3 mid = hand.add(dir.scale(L * 0.4));
        Vec3 along = dir.scale(L * 0.5);
        Vec3 s1 = right.scale(L * 0.09);
        Vec3 s2 = dir.cross(right).normalize().scale(L * 0.09);
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(BLADE));
        GilDraw.quad(ps, vc, mid, along, s1, 255, 255, 255, c(255f * al), FULL_BRIGHT);
        GilDraw.quad(ps, vc, mid, along, s2, 255, 255, 255, c(255f * al), FULL_BRIGHT);
        ps.pushPose();
        Vec3 tip = hand.add(dir.scale(L * 0.9));
        ps.translate(tip.x, tip.y, tip.z);
        FxDraw.billboard(ps, buf.getBuffer(RenderType.eyes(GLOW)), this.entityRenderDispatcher.cameraOrientation(), 1.2f * grow, 0f, 255, 205, 90, c(170f * al), FULL_BRIGHT);
        ps.popPose();
    }
}
