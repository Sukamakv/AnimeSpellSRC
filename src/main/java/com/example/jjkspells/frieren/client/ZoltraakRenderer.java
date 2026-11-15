package com.example.jjkspells.frieren.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.frieren.entity.ZoltraakEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.example.jjkspells.frieren.entity.ZoltraakEntity.*;

/**
 * Draws Zoltraak entirely from tickCount + partial tick (see {@link ZoltraakEntity}). Everything is additive and fullbright.
 *
 * Layers: (1) the seal - rune band, four-lobed seal and hub turning at different speeds, popping in with an overshoot;
 * (2) the laser - a thick solid beam with a white core and blue-violet edge, plus edge glitter; (3) impacts - 4x4 flip-book burst + flat shock ring + streak sparks at every strike and at
 * the end of the beam.
 */
public class ZoltraakRenderer extends EntityRenderer<ZoltraakEntity> {
    private static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/entity/frieren/" + n + ".png");
    }

    private static final ResourceLocation RING_A = tex("zoltraak_ring_a");
    private static final ResourceLocation RING_B = tex("zoltraak_ring_b");
    private static final ResourceLocation RING_C = tex("zoltraak_ring_c");
    private static final ResourceLocation FLARE = tex("zoltraak_flare");
    private static final ResourceLocation BEAM = tex("zoltraak_beam");
    private static final ResourceLocation BURST = tex("zoltraak_burst");
    private static final ResourceLocation WAVE = tex("zoltraak_wave");
    private static final ResourceLocation SPARK = tex("zoltraak_spark");

    /** Full width of the laser, in blocks. */
    private static final float BEAM_W = 0.95f;
    /** Half-size of the outer seal ring, in blocks. */
    private static final double SEAL = 1.15;
    private static final int GLITTER = 70;

    public ZoltraakRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(ZoltraakEntity e) {
        return RING_A;
    }

    @Override
    public boolean shouldRender(ZoltraakEntity e, Frustum frustum, double x, double y, double z) {
        return true; // the laser spans up to 45 blocks; never cull it by the (tiny) entity box
    }

    /** Per-frame state shared by the layers. */
    private static final class Shot {
        ZolPen pen;
        ZoltraakEntity e;
        float t, len, headD, headA, env, formA, formS;
        Vec3 dir, bu, bv;
        List<float[]> imps;
    }

    /** Tick at which the head passes distance d along the beam. */
    private static float tp(float d) {
        return LAUNCH + d / SPEED;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1f, k = x - 1f;
        return 1f + c3 * k * k * k + c1 * k * k;
    }

    @Override
    public void render(ZoltraakEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Shot s = new Shot();
        s.e = e;
        s.t = e.tickCount + pt;
        s.dir = e.dir();
        s.len = e.length();

        Vec3 up0 = Math.abs(s.dir.y) > 0.97 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        s.bu = s.dir.cross(up0).normalize();
        s.bv = s.dir.cross(s.bu).normalize();

        float tEnd = tp(s.len);
        s.headD = Mth.clamp(SPEED * (s.t - LAUNCH), 0f, s.len);
        s.headA = s.t < LAUNCH ? 0f : 1f - Mth.clamp(s.t - tEnd, 0f, 1f);
        // the laser holds at full width, then thins and fades
        s.env = 1f - smooth((s.t - (tEnd + HOLD)) / FADE);
        s.formA = smooth(s.t / 2.5f) * s.env;
        s.formS = easeOutBack(Mth.clamp(s.t / 4f, 0f, 1f)) * (0.9f + 0.1f * s.env);

        s.imps = new ArrayList<>(e.clientHits);
        s.imps.add(new float[]{s.len, s.len < RANGE - 0.05f ? K_WALL : K_FIZZLE});

        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camLocal = cam.getPosition().subtract(e.getPosition(pt));
        Vector3f l = cam.getLeftVector();
        Vector3f u = cam.getUpVector();
        s.pen = new ZolPen(ps.last().pose(), camLocal, new Vec3(-l.x(), -l.y(), -l.z()), new Vec3(u.x(), u.y(), u.z()));

        layerRingA(s, buf);
        layerRingB(s, buf);
        layerRingC(s, buf);
        layerBeam(s, buf);
        layerFlare(s, buf);
        layerBurst(s, buf);
        layerWave(s, buf);
        layerSpark(s, buf);
    }

    private static VertexConsumer vc(MultiBufferSource buf, ResourceLocation t) {
        return buf.getBuffer(ZolRenderTypes.additive(t));
    }

    // ---------------------------------------------------------------------------------------------- the seal
    // Three layers turning at different speeds: outer rune band, four-lobed seal, central hub.
    private void layerRingA(Shot s, MultiBufferSource buf) {
        if (s.formA <= 0.001f) return;
        s.pen.use(vc(buf, RING_A)).plane(Vec3.ZERO, s.bu, s.bv, SEAL * s.formS, 0.035 * s.t, ZolPen.FULL, 1, 1, 1, s.formA);
    }

    private void layerRingB(Shot s, MultiBufferSource buf) {
        if (s.formA <= 0.001f) return;
        s.pen.use(vc(buf, RING_B)).plane(s.dir.scale(0.02), s.bu, s.bv, SEAL * s.formS, -0.05 * s.t, ZolPen.FULL, 1, 1, 1, s.formA);
    }

    private void layerRingC(Shot s, MultiBufferSource buf) {
        if (s.formA <= 0.001f) return;
        double rot = 0.12 * s.t + 0.01 * s.t * s.t;
        s.pen.use(vc(buf, RING_C)).plane(s.dir.scale(0.04), s.bu, s.bv, 0.46 * s.formS, rot, ZolPen.FULL, 1, 1, 1, s.formA);
    }

    // ---------------------------------------------------------------------------------------------- flares + glitter
    private void layerFlare(Shot s, MultiBufferSource buf) {
        ZolPen p = s.pen.use(vc(buf, FLARE));
        if (s.formA > 0.001f) {
            double h = (0.35 + 0.5 * smooth(s.t / LAUNCH)) * s.formS;
            p.billboard(s.dir.scale(0.06), h, 0.0, ZolPen.FULL, 1, 1, 1, s.formA);
            if (s.t >= LAUNCH) { // muzzle flash as the laser leaves the seal
                float ex = (float) Math.exp(-(s.t - LAUNCH) * 0.5);
                p.billboard(s.dir.scale(0.1), 1.8 * ex, s.t * 0.2, ZolPen.FULL, 1, 1, 1, ex * s.formA);
            }
        }
        if (s.headA > 0.001f && s.headD > 0.1f) {
            p.billboard(s.dir.scale(s.headD), 0.75 + 0.08 * Math.sin(s.t * 3.0), s.t * 0.4, ZolPen.FULL, 1, 1, 1, s.headA);
        }
        // fine glitter thrown off the edges of the beam (deterministic per entity, drifts outward with age)
        if (s.headD > 0.5f && s.env > 0.01f) {
            Random r = new Random(s.e.seed() * 17L + 5);
            for (int i = 0; i < GLITTER; i++) {
                float d = r.nextFloat() * s.len;
                double ang = r.nextDouble() * Math.PI * 2;
                double rad0 = BEAM_W * (0.42 + 0.35 * r.nextDouble());
                float ph = r.nextFloat() * 6.28f;
                double size = 0.05 + 0.07 * r.nextDouble();
                double rot = r.nextDouble() * 6.28;
                float age = s.t - tp(d);
                if (age < 0f || d > s.headD) continue;
                double rad = rad0 + 0.05 * age;
                float al = s.env * Mth.clamp(1f - age / 14f, 0f, 1f) * (0.55f + 0.45f * (float) Math.sin(s.t * 1.7 + ph));
                if (al <= 0.01f) continue;
                Vec3 pos = s.dir.scale(d).add(s.bu.scale(Math.cos(ang) * rad)).add(s.bv.scale(Math.sin(ang) * rad));
                p.billboard(pos, size, rot, ZolPen.FULL, 1, 1, 1, al);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- the laser
    /**
     * A thick solid beam: white core, pale-blue then violet edge. Built from short camera-facing ribbons whose width ramps up
     * in the first fraction of a tick after the head passes (so the head is a taper, not a slab) and thins/fades with the
     * global envelope once the shot is over.
     */
    private void layerBeam(Shot s, MultiBufferSource buf) {
        if (s.headD <= 0.01f) return;
        ZolPen p = s.pen.use(vc(buf, BEAM));
        Vec3 dir = s.dir;
        int n = Mth.clamp((int) Math.ceil(s.headD / 0.6f), 1, 80);
        for (int i = 0; i < n; i++) {
            float d0 = s.headD * i / n, d1 = s.headD * (i + 1) / n;
            double w0 = width(s, d0), w1 = width(s, d1);
            float a0 = alpha(s, d0), a1 = alpha(s, d1);
            if (a0 <= 0.003f && a1 <= 0.003f) continue;
            p.ribbon(dir.scale(d0), dir.scale(d1), w0, w1, d0 * 0.35f - s.t * 1.8f, d1 * 0.35f - s.t * 1.8f, a0, a1, 1, 1, 1);
        }
    }

    private static double width(Shot s, float d) {
        float ramp = smooth((s.t - tp(d)) / 0.25f);          // pointed head
        float start = 0.5f + 0.5f * smooth(d / 2f);          // narrower where it leaves the seal
        return BEAM_W * ramp * start * (0.2f + 0.8f * s.env);
    }

    private static float alpha(Shot s, float d) {
        return (float) Math.sqrt(smooth((s.t - tp(d)) / 0.25f)) * s.env;
    }

    // ---------------------------------------------------------------------------------------------- impacts
    private static double impactHalf(int kind) {
        return switch (kind) {
            case K_DIRECT -> 3.2;
            case K_PIERCE -> 2.2;
            case K_WALL -> 3.0;
            default -> 1.4;
        };
    }

    private static Vec3 impactPos(Shot s, float d, int kind) {
        return s.dir.scale(kind == K_WALL ? Math.max(0f, d - 0.2f) : d);
    }

    private void layerBurst(Shot s, MultiBufferSource buf) {
        ZolPen p = null;
        for (float[] im : s.imps) {
            float a = s.t - tp(im[0]);
            if (a < 0f || a >= 7.6f) continue;
            int kind = (int) im[1];
            if (p == null) p = s.pen.use(vc(buf, BURST));
            int frame = Math.min(15, (int) (a * 2.1f));
            double rot = new Random(s.e.seed() * 31L + (long) (im[0] * 100)).nextDouble() * Math.PI * 2;
            p.billboard(impactPos(s, im[0], kind), impactHalf(kind), rot, ZolPen.frame4(frame), 1, 1, 1, 1);
        }
    }

    private void layerWave(Shot s, MultiBufferSource buf) {
        ZolPen p = null;
        for (float[] im : s.imps) {
            int kind = (int) im[1];
            if (kind == K_FIZZLE) continue;
            float a = s.t - tp(im[0]);
            if (a < 0f || a >= 7f) continue;
            if (p == null) p = s.pen.use(vc(buf, WAVE));
            float x = a / 7f;
            float ease = 1f - (1f - x) * (1f - x) * (1f - x);
            double r = impactHalf(kind) * (0.15 + 0.85 * ease) * 0.9;
            float al = (float) Math.pow(1f - x, 1.3) * 0.85f;
            p.plane(impactPos(s, im[0], kind), s.bu, s.bv, r, 0.0, ZolPen.FULL, 1, 1, 1, al);
        }
    }

    private void layerSpark(Shot s, MultiBufferSource buf) {
        ZolPen p = null;
        for (float[] im : s.imps) {
            int kind = (int) im[1];
            if (kind == K_FIZZLE) continue;
            float a = s.t - tp(im[0]);
            if (a < 0f || a > 14f) continue;
            if (p == null) p = s.pen.use(vc(buf, SPARK));
            Random r = new Random(s.e.seed() * 31L + (long) (im[0] * 100) + kind * 7L);
            Vec3 hit = impactPos(s, im[0], kind);
            int n = kind == K_PIERCE ? 5 : 9;
            for (int i = 0; i < n; i++) {
                Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                if (kind != K_PIERCE && v.dot(s.dir) > 0) v = v.subtract(s.dir.scale(2 * v.dot(s.dir))); // bounce back off the surface
                double speed = 0.35 + r.nextDouble() * 0.9;
                double life = 9 + r.nextDouble() * 5;
                if (a >= life) continue;
                double dist = speed * (1 - Math.pow(0.84, a)) / 0.16;
                double vel = speed * Math.pow(0.84, a);
                Vec3 head = hit.add(v.scale(dist));
                Vec3 tail = head.subtract(v.scale(0.25 + vel * 1.3));
                float al = (float) Math.pow(1 - a / life, 1.2);
                p.ribbon(head, tail, 0.08, 0.04, 0f, 1f, al, al * 0.4f, 1, 1, 1);
            }
        }
    }
}
