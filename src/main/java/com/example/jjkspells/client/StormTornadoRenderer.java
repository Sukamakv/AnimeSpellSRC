package com.example.jjkspells.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.StormConfig;
import com.example.jjkspells.entity.StormTornadoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Giant black funnel: three nested translucent shells with scrolling storm texture, a rotating cloud cap on top,
 * a flickering veil of electric arcs, and procedurally generated white lightning bolts that wrap around it.
 */
public class StormTornadoRenderer extends EntityRenderer<StormTornadoEntity> {
    private static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/entity/" + n + ".png");
    }

    private static final ResourceLocation SWIRL = tex("tornado_swirl");
    private static final ResourceLocation CLOUD = tex("tornado_cloud");
    private static final ResourceLocation BOLT = tex("storm_bolt");
    private static final ResourceLocation ARCS = tex("storm_arcs");

    private static final int SEG = 28;
    private static final int RINGS = 34;

    public StormTornadoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(StormTornadoEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(StormTornadoEntity e) {
        return SWIRL;
    }

    @Override
    public void render(StormTornadoEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        int life = e.getLifetime();
        float grow = Mth.clamp(t / StormTornadoEntity.FADE_IN, 0f, 1f) * Mth.clamp((life - t) / StormTornadoEntity.FADE_OUT, 0f, 1f);
        if (grow <= 0.01f) {
            return;
        }
        grow = grow * grow * (3f - 2f * grow);
        float Hfull = StormConfig.SPELL.tornadoHeight.get().floatValue();
        float H = Hfull * grow;
        int slot = e.getSlot();
        float dir = slot == 0 ? 1f : -1f;

        // camera position in this entity's local space (for the lightning ribbons)
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double ex = Mth.lerp(pt, e.xo, e.getX()), ey = Mth.lerp(pt, e.yo, e.getY()), ez = Mth.lerp(pt, e.zo, e.getZ());
        Vec3 lc = new Vec3(cam.x - ex, cam.y - ey, cam.z - ez);

        ps.pushPose();
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();

        // ---- dark shells (outer, middle, inner)
        float[] scale = {1.0f, 0.80f, 0.58f};
        float[] alpha = {0.90f, 0.85f, 0.95f};
        float[] spinSpeed = {0.045f, -0.065f, 0.085f};
        float[] uScroll = {0.010f, -0.016f, 0.022f};
        float[] vScroll = {0.020f, 0.032f, 0.045f};
        for (int s = 0; s < 3; s++) {
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(SWIRL));
            shell(vc, m, n, H, Hfull, scale[s], alpha[s] * grow, t * spinSpeed[s] * dir, t * uScroll[s] * dir, t * vScroll[s], t, s, 1f);
        }

        // ---- cloud cap(s) on top
        VertexConsumer cloud = buf.getBuffer(RenderType.entityTranslucent(CLOUD));
        float capR = (float) (StormConfig.SPELL.topRadius.get() * 1.7);
        cap(cloud, m, n, H + 0.3f, capR, t * 0.02f * dir, 0.92f * grow);
        cap(cloud, m, n, H - 1.6f, capR * 0.8f, -t * 0.03f * dir, 0.8f * grow);
        cap(cloud, m, n, H + 2.0f, capR * 0.55f, t * 0.05f * dir, 0.7f * grow);

        // ---- electric veil around the funnel (additive)
        VertexConsumer arcs = buf.getBuffer(RenderType.eyes(ARCS));
        long flick = (long) (t / 2f);
        RandomSource fr = RandomSource.create(flick * 7919L + e.getId() * 31L);
        float veil = (0.45f + fr.nextFloat() * 0.55f) * grow;
        shell(arcs, m, n, H, Hfull, 1.12f, veil, t * 0.12f * dir, t * 0.03f * dir, -t * 0.05f, t, 7, 2.0f);
        shell(arcs, m, n, H, Hfull, 0.95f, veil * 0.8f, -t * 0.17f * dir, 0.31f, t * 0.07f, t + 40, 9, 3.0f);

        // ---- white lightning bolts
        VertexConsumer bolt = buf.getBuffer(RenderType.eyes(BOLT));
        int bolts = 7;
        for (int b = 0; b < bolts; b++) {
            long step = (long) Math.floor(t / 2.0 + b * 0.9);
            if ((step + b) % 3 == 0) {
                continue; // dark moments between flashes
            }
            RandomSource r = RandomSource.create(step * 1009L + b * 7717L + e.getId() * 131L);
            float a = 0.55f + r.nextFloat() * 0.45f;
            drawBolt(bolt, m, n, lc, r, H, Hfull, grow * a, b);
        }

        ps.popPose();
    }

    // ------------------------------------------------------------------------------------------------ funnel

    private static float profileR(float h, float Hfull) {
        return (float) StormTornadoEntity.radiusAt(h, Hfull);
    }

    /** Centre line sway: the funnel snakes as it rises. */
    private static float swayX(float h, float t, int s) {
        return Mth.sin(h * 0.22f - t * 0.11f + s) * 0.07f * h;
    }

    private static float swayZ(float h, float t, int s) {
        return Mth.cos(h * 0.19f - t * 0.09f + s * 1.7f) * 0.07f * h;
    }

    private void shell(VertexConsumer vc, Matrix4f m, Matrix3f n, float H, float Hfull, float k, float alpha,
                       float rot, float uOff, float vOff, float t, int s, float vRepeat) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        if (a <= 2 || H <= 0.1f) {
            return;
        }
        float twist = 0.065f; // radians of twist per block of height
        for (int j = 0; j < RINGS; j++) {
            float h0 = H * j / RINGS, h1 = H * (j + 1) / RINGS;
            float f0 = j / (float) RINGS, f1 = (j + 1) / (float) RINGS;
            float r0 = profileR(h0, Hfull) * k * (1f + 0.05f * Mth.sin(h0 * 0.9f - t * 0.25f + s));
            float r1 = profileR(h1, Hfull) * k * (1f + 0.05f * Mth.sin(h1 * 0.9f - t * 0.25f + s));
            float sx0 = swayX(h0, t, s), sz0 = swayZ(h0, t, s), sx1 = swayX(h1, t, s), sz1 = swayZ(h1, t, s);
            // fade at the very bottom so the foot dissolves into the dust
            int al0 = (int) (a * Mth.clamp(h0 / 1.5f + 0.25f, 0f, 1f));
            int al1 = (int) (a * Mth.clamp(h1 / 1.5f + 0.25f, 0f, 1f));
            for (int i = 0; i < SEG; i++) {
                float p0 = (float) (i * Math.PI * 2 / SEG), p1 = (float) ((i + 1) * Math.PI * 2 / SEG);
                float u0 = i / (float) SEG * 2f + uOff + h0 * 0.05f, u1 = (i + 1) / (float) SEG * 2f + uOff + h0 * 0.05f;
                float u0b = i / (float) SEG * 2f + uOff + h1 * 0.05f, u1b = (i + 1) / (float) SEG * 2f + uOff + h1 * 0.05f;
                float v0 = f0 * 3f * vRepeat - vOff, v1 = f1 * 3f * vRepeat - vOff;
                float a00 = p0 + rot + h0 * twist, a10 = p1 + rot + h0 * twist;
                float a01 = p0 + rot + h1 * twist, a11 = p1 + rot + h1 * twist;
                v(vc, m, n, sx0 + Mth.cos(a00) * r0, h0, sz0 + Mth.sin(a00) * r0, u0, v0, al0);
                v(vc, m, n, sx0 + Mth.cos(a10) * r0, h0, sz0 + Mth.sin(a10) * r0, u1, v0, al0);
                v(vc, m, n, sx1 + Mth.cos(a11) * r1, h1, sz1 + Mth.sin(a11) * r1, u1b, v1, al1);
                v(vc, m, n, sx1 + Mth.cos(a01) * r1, h1, sz1 + Mth.sin(a01) * r1, u0b, v1, al1);
            }
        }
    }

    private void cap(VertexConsumer vc, Matrix4f m, Matrix3f n, float y, float R, float rot, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        if (a <= 2) {
            return;
        }
        float c = Mth.cos(rot), s = Mth.sin(rot);
        float[][] q = {{-R, -R}, {-R, R}, {R, R}, {R, -R}};
        float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        // two faces so it is visible from above and below
        for (int face = 0; face < 2; face++) {
            for (int k = 0; k < 4; k++) {
                int idx = face == 0 ? k : 3 - k;
                float[] p = q[idx];
                float u = (uv[idx][0] - 0.5f) * c - (uv[idx][1] - 0.5f) * s + 0.5f;
                float w = (uv[idx][0] - 0.5f) * s + (uv[idx][1] - 0.5f) * c + 0.5f;
                v(vc, m, n, p[0], y, p[1], u, w, a);
            }
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float w, int a) {
        vc.vertex(m, x, y, z).color(255, 255, 255, a).uv(u, w)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0f, 1f, 0f).endVertex();
    }

    // ------------------------------------------------------------------------------------------------ lightning

    private void drawBolt(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, RandomSource r, float H, float Hfull, float alpha, int id) {
        if (H < 2f) {
            return;
        }
        float h = H * (0.15f + r.nextFloat() * 0.85f);
        float len = H * (0.25f + r.nextFloat() * 0.5f);
        float dir = r.nextBoolean() ? -1f : 1f;
        float ang = r.nextFloat() * Mth.TWO_PI;
        float angSpeed = (r.nextFloat() - 0.5f) * 0.5f;
        int segs = 16;
        Vec3[] pts = new Vec3[segs + 1];
        for (int i = 0; i <= segs; i++) {
            float hh = Mth.clamp(h + dir * len * i / segs, 0f, H);
            float rad = profileR(hh, Hfull) * (1.05f + r.nextFloat() * 0.18f) + 0.25f;
            float aa = ang + angSpeed * i + hh * 0.065f;
            pts[i] = new Vec3(swayX(hh, 0, 0) + Mth.cos(aa) * rad + (r.nextFloat() - 0.5f) * 0.9f, hh, swayZ(hh, 0, 0) + Mth.sin(aa) * rad + (r.nextFloat() - 0.5f) * 0.9f);
        }
        ribbon(vc, m, n, cam, pts, 0.55f, alpha * 0.55f);   // glow
        ribbon(vc, m, n, cam, pts, 0.17f, alpha);           // core
        // a fork
        if (r.nextFloat() < 0.8f) {
            int from = 3 + r.nextInt(segs - 6);
            Vec3 o = pts[from];
            Vec3[] fork = new Vec3[6];
            Vec3 d = new Vec3((r.nextFloat() - 0.5f) * 2.4f, dir * (0.4f + r.nextFloat() * 0.8f), (r.nextFloat() - 0.5f) * 2.4f);
            for (int i = 0; i < fork.length; i++) {
                fork[i] = o.add(d.scale(i * 0.9)).add((r.nextFloat() - 0.5f) * 0.7f, (r.nextFloat() - 0.5f) * 0.4f, (r.nextFloat() - 0.5f) * 0.7f);
            }
            ribbon(vc, m, n, cam, fork, 0.28f, alpha * 0.45f);
            ribbon(vc, m, n, cam, fork, 0.09f, alpha * 0.8f);
        }
    }

    private void ribbon(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3[] p, float halfWidth, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        if (a <= 2) {
            return;
        }
        Vec3[] perp = new Vec3[p.length];
        for (int i = 0; i < p.length; i++) {
            Vec3 d = (i < p.length - 1 ? p[i + 1].subtract(p[i]) : p[i].subtract(p[i - 1]));
            Vec3 toCam = cam.subtract(p[i]);
            Vec3 pr = d.cross(toCam);
            double l = pr.length();
            perp[i] = l < 1e-5 ? new Vec3(halfWidth, 0, 0) : pr.scale(halfWidth / l);
        }
        for (int i = 0; i < p.length - 1; i++) {
            float v0 = 0f, v1 = 1f;
            bv(vc, m, n, p[i].subtract(perp[i]), 0f, v0, a);
            bv(vc, m, n, p[i].add(perp[i]), 1f, v0, a);
            bv(vc, m, n, p[i + 1].add(perp[i + 1]), 1f, v1, a);
            bv(vc, m, n, p[i + 1].subtract(perp[i + 1]), 0f, v1, a);
        }
    }

    private static void bv(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float w, int a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, a).uv(u, w)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0f, 1f, 0f).endVertex();
    }
}
