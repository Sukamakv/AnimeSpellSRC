package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.irontomb.IrontombAttack;
import com.example.jjkspells.irontomb.IrontombCut;
import com.example.jjkspells.irontomb.entity.IrontombEntity;
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

import java.util.List;

/** Draws the Irontomb colossus procedurally (boxes and spikes), its halo and ground array, and dispatches the attack / cutscene VFX. */
public class IrontombRenderer extends EntityRenderer<IrontombEntity> {
    public IrontombRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(IrontombEntity e) {
        return FxTypes.tex("white");
    }

    @Override
    public boolean shouldRender(IrontombEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    /** Animation values for one frame. Angles are in degrees (arms) or blocks (offsets). */
    private static final class BodyPose {
        float y, lL, lR, spread, headDip, glow, shake, grip, scale = 1f;
        boolean hidden;
    }

    private static BodyPose pose(FxCtx c) {
        BodyPose b = new BodyPose();
        float t = c.at, ct = c.ct, age = c.age;
        b.y = 0.7f + Mth.sin(age * 0.06f) * 0.25f;
        b.glow = 0.40f + 0.08f * Mth.sin(age * 0.11f) + 0.18f * (c.phase - 1);
        b.spread = 6f;
        switch (c.atk) {
            case IrontombAttack.CAST_DOWN -> {
                b.lR = Fx.smooth(Fx.ramp(t, 0, 20)) * (1 - 0.9f * Fx.smooth(Fx.ramp(t, 30, 36)));
                b.lL = 0.15f;
                b.glow += 0.5f * Fx.window(t, 8, 22, 34, 50);
            }
            case IrontombAttack.WEAVE -> {
                float up = Fx.smooth(Fx.ramp(t, 0, 28));
                float pump = 0.12f * Mth.sin(t * 0.5f) * Fx.window(t, 30, 36, 58, 70);
                b.lL = b.lR = (0.85f + pump) * up * (1 - Fx.smooth(Fx.ramp(t, 62, 80)));
                b.spread = 6f + 34f * up;
                b.glow += 0.5f * Fx.window(t, 10, 30, 56, 80);
            }
            case IrontombAttack.YIELD -> {
                b.lR = 0.55f * Fx.window(t, 0, 14, 56, 72);
                b.glow += 0.5f * Fx.window(t, 0, 30, 54, 72);
            }
            case IrontombAttack.MIRE -> {
                b.lL = 0.55f * Fx.window(t, 0, 14, 62, 82);
                b.glow += 0.5f * Fx.window(t, 0, 30, 60, 82);
            }
            case IrontombAttack.CROWN -> {
                b.lL = b.lR = Fx.smooth(Fx.ramp(t, 0, 30)) * (1 - 0.85f * Fx.smooth(Fx.ramp(t, 84, 92)));
                b.spread = 6f + 18f * Fx.ramp(t, 0, 30);
                b.glow += 0.8f * Fx.window(t, 10, 60, 84, 110);
                b.y += 1.5f * Fx.window(t, 10, 50, 84, 100);
            }
            case IrontombAttack.PRISON -> {
                b.lL = b.lR = 0.5f * Fx.window(t, 0, 16, 62, 90);
                b.spread = 2f;
                b.glow += 0.6f * Fx.window(t, 0, 30, 60, 90);
            }
            case IrontombAttack.IMMOLATE -> {
                b.lL = b.lR = Fx.smooth(Fx.ramp(t, 6, 50)) * (1 - 0.9f * Fx.smooth(Fx.ramp(t, 150, 160)));
                b.spread = 6f + 24f * Fx.ramp(t, 6, 60);
                b.y += 2.2f * Fx.window(t, 10, 60, 150, 175);
                b.glow += 1.1f * Fx.window(t, 10, 100, 150, 190);
            }
            case IrontombAttack.GENESIS -> {
                b.lL = b.lR = Fx.smooth(Fx.ramp(t, 6, 50)) * (1 - 0.9f * Fx.smooth(Fx.ramp(t, 232, 245)));
                b.spread = 6f + 30f * Fx.ramp(t, 6, 80);
                b.y += 7f * Fx.ease(Fx.ramp(t, 20, 150)) * (1 - Fx.smooth(Fx.ramp(t, 232, 250)));
                b.glow += 1.4f * Fx.window(t, 10, 120, 232, 280);
            }
            case IrontombAttack.SINGULARITY -> {
                float up = Fx.smooth(Fx.ramp(t, 0, 45)) * (1f - Fx.smooth(Fx.ramp(t, 336, 368)));
                b.lL = b.lR = up;
                b.spread = 6f + 22f * up;
                b.grip = SpecialFx.grip(t);
                b.y += 3f * Fx.window(t, 20, 70, 320, 345);
                b.glow += 1.3f * Fx.window(t, 10, 120, 322, 350);
                b.shake = 0.04f * Fx.window(t, 100, 200, 326, 330) + 0.22f * Fx.window(t, 300, 328, 330, 345);
            }
            case IrontombAttack.ERASURE -> {
                float up = Fx.smooth(Fx.ramp(t, 10, 70)) * (1f - Fx.smooth(Fx.ramp(t, 400, 440)));
                b.lL = b.lR = 0.9f * up;
                b.spread = 6f + 34f * up;
                b.y += 5f * Fx.ease(Fx.ramp(t, 20, 160)) * (1f - Fx.smooth(Fx.ramp(t, 396, 440)));
                b.glow += 1.6f * Fx.window(t, 20, 160, 400, 470);
                b.shake = 0.05f * Fx.window(t, 260, 380, 396, 400) + 0.3f * Fx.window(t, 396, 400, 410, 470);
            }
            case IrontombAttack.RESIST -> {
                b.lL = b.lR = 0.5f;
                b.spread = -10f;
                b.glow += 0.7f;
            }
            case IrontombAttack.STAGGER -> {
                b.y -= 1.7f * Fx.window(t, 0, 10, 76, 90);
                b.headDip = 34f * Fx.window(t, 0, 10, 76, 90);
                b.glow += 0.4f;
                b.shake = 0.05f;
            }
            default -> { }
        }
        if (c.e.isErasing() && c.atk == 0) {
            b.lL = b.lR = Math.max(b.lL, 0.55f);
            b.glow += 0.7f;
            b.spread = 6f + 14f;
        }
        switch (c.cut) {
            case IrontombCut.INTRO -> {
                b.y -= 9f * (1 - Fx.ease(Fx.ramp(ct, 10, 100)));
                b.glow = 0.1f + 0.9f * Fx.ramp(ct, 60, 115);
                b.shake = 0.08f * Fx.window(ct, 10, 20, 90, 105);
                b.headDip = 28f * (1 - Fx.smooth(Fx.ramp(ct, 96, 118)));
                b.lL = b.lR = 0.25f * Fx.smooth(Fx.ramp(ct, 100, 140));
            }
            case IrontombCut.PHASE2, IrontombCut.PHASE3 -> {
                b.lL = b.lR = Fx.window(ct, 14, 50, 70, 100) * 0.95f;
                b.spread = 6f + 30f * Fx.window(ct, 14, 50, 70, 100);
                b.glow += 1.0f * Fx.window(ct, 10, 56, 70, 110);
                b.shake = 0.06f * Fx.window(ct, 5, 20, 56, 70);
                b.y += 1.5f * Fx.window(ct, 10, 50, 70, 100);
            }
            case IrontombCut.DEATH -> {
                float k = Fx.ramp(ct, 0, 100);
                b.shake = 0.05f + 0.35f * k;
                b.glow = 0.6f + 1.8f * k;
                b.lL = b.lR = 0.4f * k;
                b.headDip = 22f * k;
                b.y -= 0.8f * k;
                b.hidden = ct >= 102;
            }
            default -> { }
        }
        return b;
    }

    @Override
    public void render(IrontombEntity e, float yawIgnored, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 ep = e.getPosition(pt);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition().subtract(ep);
        FxCtx c = new FxCtx(ps, buf, cam, e, pt);
        BodyPose bp = pose(c);

        // sky first so everything else draws over it
        SkyDefaults.apply(c);

        // facing: the current target, otherwise the camera
        // the server turns the body (it also moves the hit boxes), everyone sees the same facing
        float yaw = -(float) Math.toRadians(Mth.rotLerp(pt, e.yRotO, e.getYRot()));
        CosmicFx.yaw = yaw;
        float sh = bp.shake * 3f;
        float K = 4f;   // the cosmic form is ~4x the scale the pose values were authored for

        CosmicFx.Pose cp = new CosmicFx.Pose();
        cp.lL = bp.lL;
        cp.lR = bp.lR;
        cp.spread = bp.spread;
        cp.headDip = bp.headDip;
        cp.glow = bp.glow;
        cp.grip = bp.grip;
        float vis = 1f;
        if (c.cut == IrontombCut.INTRO) {
            vis = Fx.ease(Fx.ramp(c.ct, 40, 120));
        } else if (c.cut == IrontombCut.DEATH) {
            vis = 1f - Fx.ramp(c.ct, 100, 150);
        }

        ps.pushPose();
        if (sh > 0f) {
            ps.translate((Fx.rnd((int) (c.age * 7)) - 0.5f) * sh, (Fx.rnd((int) (c.age * 7) + 5) - 0.5f) * sh * 0.5f, (Fx.rnd((int) (c.age * 7) + 9) - 0.5f) * sh);
        }
        ps.translate(0, bp.y * K, 0);
        ps.mulPose(Axis.YP.rotation(yaw));
        if (!bp.hidden) {
            CosmicFx.body(c, cp);
            CosmicFx.chains(c, cp);
        }
        ps.popPose();

        CosmicFx.ring(c, yaw, bp.hidden ? 0f : vis, bp.glow);
        CosmicFx.environment(c, yaw, vis, bp.glow);
        groundArray(c);
        satelliteLinks(c);

        AttackFx.draw(c);
        CutFx.draw(c);
        if (c.atk != IrontombAttack.ERASURE) {
            SpecialFx.erasureCharge(c);
        }

        if (e.getLockLeft() > 0) {
            Entity v = c.level.getEntity(e.getLockId());
            if (v != null && v.isAlive()) {
                AttackFx.lockCage(c, v, e.getLockLeft() / 20f);
            }
        }
    }

    private static void groundArray(FxCtx c) {
        float vis = 1f;
        if (c.cut == IrontombCut.INTRO) {
            vis = Fx.ramp(c.ct, 30, 100);
        } else if (c.cut == IrontombCut.DEATH) {
            vis = 1f - Fx.ramp(c.ct, 90, 104);
        }
        if (vis < 0.01f) {
            return;
        }
        float rad = 46f + 8f * (c.phase - 1);
        float busy = c.atk != 0 ? 0.3f : 0f;
        float[] col = c.phase >= 3 ? new float[]{1f, 0.3f, 0.22f} : new float[]{1f, 0.6f, 0.25f};
        c.flat("glyph", new Vec3(0, 0.06, 0), rad, c.age * 0.012f, col[0], col[1], col[2], (0.55f + busy) * vis);
        c.flat("halo_ring", new Vec3(0, 0.07, 0), rad * 1.05f, -c.age * 0.008f, 1f, 0.85f, 0.55f, 0.4f * vis);
    }

    /** Tethers from the chest to the two satellites. */
    private static void satelliteLinks(FxCtx c) {
        if (c.cut == IrontombCut.DEATH || c.cut == IrontombCut.INTRO && c.ct < 100) {
            return;
        }
        List<IrontombSatellite> l = c.level.getEntitiesOfClass(IrontombSatellite.class, c.e.getBoundingBox().inflate(40, 30, 40));
        Vec3 ep = c.e.getPosition(c.pt);
        for (IrontombSatellite s : l) {
            if (s.getBossId() != c.e.getId()) {
                continue;
            }
            Vec3 sp = s.getPosition(c.pt).add(0, 0.8, 0).subtract(ep);
            boolean logos = s.getSatType() == 0;
            float[] col = logos ? AttackFx.VIOLET : AttackFx.GOLD;
            float k = s.isGrave() ? 0.15f : 0.6f;
            Vec3 ch = AttackFx.chest(c);
            Vec3 mid = ch.add(sp).scale(0.5).add(0, Math.sin(c.age * 0.1 + (logos ? 0 : 2)) * 0.8, 0);
            c.beam(ch, mid, 0.22f, 0.3f, col[0], col[1], col[2], k);
            c.beam(mid, sp, 0.22f, 0.3f, col[0], col[1], col[2], k);
        }
    }
}
