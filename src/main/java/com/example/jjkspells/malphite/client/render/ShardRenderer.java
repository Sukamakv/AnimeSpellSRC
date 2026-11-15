package com.example.jjkspells.malphite.client.render;

import com.example.jjkspells.malphite.entity.ShardEntity;
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

/** Seismic Shard: the spinning jagged stone with orbiting rubble and a dust trail, then the shattering and the flying fragments. */
public class ShardRenderer extends EntityRenderer<ShardEntity> {
    public ShardRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(ShardEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ShardEntity e) {
        return GLOW;
    }

    @Override
    public void render(ShardEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        int state = e.getState();
        float t = e.tickCount + pt;
        Vec3 base = e.getPosition(pt);
        Vec3 cam = cam(base);
        if (state <= 0) {
            flying(e, pt, ps, buf, light, t, cam);
        } else {
            shattered(e, pt, ps, buf, light, state + pt, cam);
        }
    }

    private void flying(ShardEntity e, float pt, PoseStack ps, MultiBufferSource buf, int light, float t, Vec3 cam) {
        float yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float pitch = Mth.lerp(pt, e.xRotO, e.getXRot());
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3 dir = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        float spin = t * 33f;
        // dust and glowing mineral trail
        Vec3 tail = dir.scale(-7.0);
        Draw.ribbon(trans(buf, TRAIL), ps, tail, Vec3.ZERO, cam, 0.05f, 0.75f, 0f, 1f, DUSTC, 0f, 0.85f);
        Draw.ribbon(trans(buf, TRAIL), ps, tail.scale(0.6), Vec3.ZERO, cam, 0.03f, 0.45f, 0f, 1f, DARK, 0f, 0.9f);
        Draw.ribbon(add(buf, LINE), ps, tail.scale(0.5), Vec3.ZERO, cam, 0.0f, 0.22f, 0f, 1f, ORANGE, 0f, 0.7f);
        // the stone: two crossed planes spinning about the flight axis
        float hh = 0.55f, hw = hh * 2f;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(-90f));
        for (int i = 0; i < 2; i++) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(spin + i * 90f));
            Draw.xyAt(trans(buf, SHARD), ps, hw, hh, 0f, STONE, 1f);
            ps.popPose();
        }
        ps.popPose();
        // glow through the cracks
        ps.pushPose();
        billboard(ps);
        float g = 0.9f + 0.12f * Mth.sin(t * 1.3f);
        Draw.xy(add(buf, GLOW), ps, g, g, ORANGE, 0.55f);
        ps.popPose();
        // rubble orbiting the stone
        Vec3 u = dir.cross(new Vec3(0, 1, 0));
        u = u.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : u.normalize();
        Vec3 v = dir.cross(u).normalize();
        for (int i = 0; i < 4; i++) {
            double a = spin * 0.045 + i * Math.PI / 2;
            Vec3 o = u.scale(Math.cos(a) * 1.05).add(v.scale(Math.sin(a) * 1.05)).add(dir.scale(Math.sin(a * 1.7) * 0.4));
            ps.pushPose();
            ps.translate(o.x, o.y, o.z);
            billboard(ps);
            ps.mulPose(Axis.ZP.rotationDegrees(spin * 1.3f + i * 70f));
            float s = 0.16f + 0.05f * (i % 2);
            Draw.xy(trans(buf, ROCK), ps, s, s, STONE, 1f);
            ps.popPose();
        }
    }

    private void shattered(ShardEntity e, float pt, PoseStack ps, MultiBufferSource buf, int light, float t, Vec3 cam) {
        int n = e.getFrags(), seed = e.getSeed();
        float fadeAll = 1f - c01((t - ShardEntity.FRAG_TICKS) / 8f);
        // shockwave and dust
        float k = c01(t / 8f);
        ps.pushPose();
        billboard(ps);
        if (k < 1f) {
            float r = 0.8f + 4.2f * smooth(k);
            Draw.xy(add(buf, RING_WAVE), ps, r, r, HOT, 1f - k);
            Draw.xy(add(buf, GLOW), ps, 2.4f * (1f - k) + 0.6f, 2.4f * (1f - k) + 0.6f, ORANGE, 0.9f * (1f - k));
        }
        float d = c01(t / 20f);
        for (int i = 0; i < 5; i++) {
            ps.pushPose();
            double a = i * 1.2566 + seed;
            float r = 1.0f * smooth(d) * 3f;
            ps.translate(Math.cos(a) * r * 0.5, Math.sin(a * 1.7) * r * 0.3, 0);
            Draw.xy(trans(buf, MIST), ps, 1.3f + 1.6f * d, 1.3f + 1.6f * d, DUSTC, 0.8f * (1f - d) * fadeAll);
            ps.popPose();
        }
        ps.popPose();
        // the fragments
        int ti = (int) Math.floor(t);
        float fr = t - ti;
        for (int i = 0; i < n; i++) {
            Vec3 p0 = ShardEntity.fragPos(Vec3.ZERO, i, n, seed, ti), p1 = ShardEntity.fragPos(Vec3.ZERO, i, n, seed, ti + 1);
            Vec3 p = p0.lerp(p1, fr);
            Vec3 tailP = ShardEntity.fragPos(Vec3.ZERO, i, n, seed, Math.max(0, ti - 3));
            Draw.ribbon(trans(buf, TRAIL), ps, tailP, p, cam, 0.02f, 0.18f, 0f, 1f, DUSTC, 0f, 0.5f * fadeAll);
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            billboard(ps);
            ps.mulPose(Axis.ZP.rotationDegrees(t * 40f + i * 60f));
            float s = 0.32f;
            Draw.xy(trans(buf, SHARD), ps, s * 1.4f, s * 0.7f, STONE, fadeAll);
            ps.popPose();
        }
    }
}
