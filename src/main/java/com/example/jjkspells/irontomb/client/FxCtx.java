package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.irontomb.IrontombAttack;
import com.example.jjkspells.irontomb.IrontombCut;
import com.example.jjkspells.irontomb.entity.IrontombEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Everything an effect needs: the pose (origin = boss feet), buffers, camera position relative to the boss and timing. */
public final class FxCtx {
    public final PoseStack ps;
    public final MultiBufferSource buf;
    public final Vec3 cam;          // camera position relative to the boss
    public final IrontombEntity e;
    public final Level level;
    public final float age, pt;
    public final float at;          // attack tick (+partial), 0 when idle
    public final float ct;          // cutscene tick (+partial), 0 when none
    public final int atk, cut, phase;
    public final Vec3 aim;          // attack aim point relative to the boss

    public FxCtx(PoseStack ps, MultiBufferSource buf, Vec3 cam, IrontombEntity e, float pt) {
        this.ps = ps;
        this.buf = buf;
        this.cam = cam;
        this.e = e;
        this.level = e.level();
        this.pt = pt;
        this.age = e.tickCount + pt;
        this.atk = e.getAttack();
        this.cut = e.getCut();
        this.phase = e.getPhase();
        this.at = atk == 0 ? 0 : (float) ((e.getAtkT() + pt) * IrontombAttack.scale(atk));
        this.ct = cut == 0 ? 0 : (float) ((e.getCutT() + pt) * IrontombCut.scale(cut));
        Vec3 ep = e.getPosition(pt);
        this.aim = e.getAim().subtract(ep);
    }

    public VertexConsumer add(String tex) {
        return buf.getBuffer(FxTypes.add(tex));
    }

    public VertexConsumer alpha(String tex) {
        return buf.getBuffer(FxTypes.alpha(tex));
    }

    public VertexConsumer solid(String tex) {
        return buf.getBuffer(FxTypes.solid(tex));
    }

    /** Draws a camera-facing quad at pos (boss space) using an additive texture. */
    public void billboard(String tex, Vec3 pos, float size, float roll, float r, float g, float b, float a) {
        ps.pushPose();
        ps.translate(pos.x, pos.y, pos.z);
        ps.mulPose(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        if (roll != 0f) {
            ps.mulPose(com.mojang.math.Axis.ZP.rotation(roll));
        }
        Fx.quadXY(add(tex), ps, size, size, r, g, b, a);
        ps.popPose();
    }

    public void billboardFrame(String tex, Vec3 pos, float size, int frame, int cols, float r, float g, float b, float a) {
        ps.pushPose();
        ps.translate(pos.x, pos.y, pos.z);
        ps.mulPose(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        Fx.flipXY(add(tex), ps, size, size, frame, cols, r, g, b, a);
        ps.popPose();
    }

    /** Flat additive quad on the ground (or any height) with optional spin. */
    public void flat(String tex, Vec3 pos, float half, float spinRad, float r, float g, float b, float a) {
        ps.pushPose();
        ps.translate(pos.x, pos.y, pos.z);
        if (spinRad != 0f) {
            ps.mulPose(com.mojang.math.Axis.YP.rotation(spinRad));
        }
        Fx.quadXZ(add(tex), ps, half, 0f, r, g, b, a);
        ps.popPose();
    }

    public void flatFrame(String tex, Vec3 pos, float half, int frame, int cols, float r, float g, float b, float a) {
        ps.pushPose();
        ps.translate(pos.x, pos.y, pos.z);
        Fx.flipXZ(add(tex), ps, half, 0f, frame, cols, r, g, b, a);
        ps.popPose();
    }

    public void beam(Vec3 a, Vec3 b, float width, float scrollSpeed, float r, float g, float bl, float al) {
        Fx.ribbon(add("beam"), ps, a, b, cam, width, age * scrollSpeed, 0.25f, r, g, bl, al);
    }


    /** A vertical light pillar at pos (boss space). */
    public void pillarAt(Vec3 base, float height, float radius, float[] col, float alpha) {
        Fx.pillar(add("beam"), ps, base, height, radius, age * 0.05f, col[0], col[1], col[2], alpha);
        Fx.pillar(add("beam"), ps, base, height, radius * 0.45f, -age * 0.07f, 1f, 1f, 1f, alpha * 0.8f);
    }
}
