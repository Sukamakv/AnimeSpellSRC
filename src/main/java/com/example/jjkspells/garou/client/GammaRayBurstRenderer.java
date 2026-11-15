package com.example.jjkspells.garou.client;

import com.example.jjkspells.garou.entity.GammaRayBurstEntity;
import com.example.jjkspells.garou.spell.GarouGammaRayBurstSpell;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

import static com.example.jjkspells.garou.entity.GammaRayBurstEntity.*;

/**
 * Draws the whole Gamma Ray Burst in the entity's local space (world - entity position), back to front:
 *   lens rings + swirl disc -> dark sphere (ALPHA type) -> orbit rings -> terrain glow -> beams -> stars/lightning -> impacts.
 * Everything is additive and fullbright except the sphere; glow stays low-alpha so the linework of the textures reads.
 * Distortion is faked (lens rings, accretion disc, FOV pinch in GarouClientHooks), terrain "lighting" is a heightmap-
 * sampled additive decal. Nothing here touches the world.
 */
public class GammaRayBurstRenderer extends EntityRenderer<GammaRayBurstEntity> {
    private static ResourceLocation tex(String n) { return new ResourceLocation("jjk_spells", "textures/entity/garou/" + n + ".png"); }
    static final ResourceLocation BEAM = tex("gamma_beam"), RING = tex("gamma_ring"), STAR = tex("gamma_star"),
            SPHERE = tex("gamma_sphere"), DISC = tex("gamma_disc"), ARC = tex("gamma_arc"),
            BURST = tex("gamma_burst"), GROUND = tex("gamma_ground");

    public GammaRayBurstRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override public ResourceLocation getTextureLocation(GammaRayBurstEntity e) { return SPHERE; }
    @Override public boolean shouldRender(GammaRayBurstEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) { return true; }

    private static float s(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }

    private Pen pen(MultiBufferSource buf, RenderType rt, PoseStack ps, Vec3 cam) { return new Pen(buf.getBuffer(rt), ps, cam); }

    @Override
    public void render(GammaRayBurstEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.age + pt;
        Vec3 pos = e.getPosition(pt);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(pos);
        Vec3 dir = e.getDir();
        if (dir.lengthSqr() < 1e-6) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();
        Vec3 u = dir.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1e-4) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = dir.cross(u).normalize();

        float appear = s(t / 6f);
        float fade = 1f - s(prog(t, T_BEAM_END, T_END));
        float compP = s(prog(t, T_FORM, T_COMPRESS));
        float beamAge = t - T_TWIN;
        boolean beamOn = beamAge >= 0 && t < T_END;
        float vis = appear * fade;
        if (vis <= 0.001f) return;

        // sphere radius: grows, compresses, then holds with a slow pulse while the beam runs
        float R = t < T_FORM ? 1.25f * s(t / T_FORM) : 1.25f - 0.7f * compP;
        if (beamOn) R = 0.55f + 0.05f * Mth.sin(t * 0.7f);
        R *= (0.35f + 0.65f * fade);

        float len = e.getLength();
        float reach = beamOn ? Math.min(len, Math.max(6f, len * s(beamAge / 8f))) : 0f;
        if (t >= T_BEAM_END) reach = len;

        // ---- 1. lens rings (fake distortion) + swirl disc behind the sphere
        Pen p = pen(buf, GarouRenderTypes.additive(DISC), ps, cam);
        p.billboard(Vec3.ZERO, R * 3.0f, t * 0.10f, 1, 1, 1, 0.5f * vis);
        p.billboard(Vec3.ZERO, R * 2.1f, -t * 0.17f, 1, 1, 1, 0.35f * vis);
        p = pen(buf, GarouRenderTypes.additive(RING), ps, cam);
        for (int i = 0; i < 3; i++) {
            float ph = ((t * 0.035f) + i / 3f) % 1f;
            p.billboard(Vec3.ZERO, R * (1.8f + ph * 3.2f), t * 0.02f * (i + 1), 0.7f, 0.85f, 1f, 0.22f * (1 - ph) * vis);
        }

        // ---- 2. the dark cosmic sphere (alpha blended: dark matter, never additive)
        p = pen(buf, GarouRenderTypes.alpha(SPHERE), ps, cam);
        p.billboard(Vec3.ZERO, R * 1.25f, t * 0.03f, 1, 1, 1, 0.98f * Math.min(1f, vis * 1.5f));

        // ---- 3. orbit rings around the origin, perpendicular-ish to the beam axis; they tighten as the sphere compresses
        p = pen(buf, GarouRenderTypes.additive(RING), ps, cam);
        if (t < T_BEAM_END + 6) {
            for (int i = 0; i < 6; i++) {
                float rad = R * (1.7f + i * 0.5f) * (1f + 1.2f * (1f - compP) * (t < T_TWIN ? 1f : 0f));
                float spin = t * (0.09f + 0.03f * i) * (i % 2 == 0 ? 1 : -1) + i * 1.3f;
                float tilt = 0.5f * Mth.sin(t * 0.05f + i) + (i % 3) * 0.35f;
                ring(p, dir.scale(0.0), u, v, dir, rad, spin, tilt, 0.8f * vis * (0.6f + 0.4f * Mth.sin(t * 0.3f + i)));
            }
        }

        // ---- 4. terrain glow decals under the beam (heightmap sampled per corner)
        if (beamOn && reach > 4) groundGlow(e.level(), buf, ps, cam, pos, dir, u, reach, t, beamAge, fade);

        // ---- 5a. twin beams: two ribbons from the sphere's sides curving forward and merging
        float tw = prog(t, T_COMPRESS, T_TWIN);
        if (t >= T_COMPRESS && t < T_TWIN + 8) {
            float twinFade = 1f - s(prog(t, T_TWIN, T_TWIN + 8));
            float L = 40f * s(tw);
            p = pen(buf, GarouRenderTypes.additive(BEAM), ps, cam);
            for (int side = -1; side <= 1; side += 2) {
                Vec3 prev = null;
                int N = 14;
                for (int k = 0; k <= N; k++) {
                    float f = k / (float) N;
                    double lateral = side * (R * 1.1 + 3.0 * (1 - s(f / 0.65f)));
                    Vec3 pt0 = dir.scale(f * L).add(u.scale(lateral)).add(v.scale(0.4 * Math.sin(f * 6 + t * 0.4) * (1 - f)));
                    if (prev != null) {
                        float w = 0.45f + 0.9f * f;
                        p.ribbonFacing(prev, pt0, w, w + 0.06f, -t * 0.12f + (k - 1) * 0.25f, -t * 0.12f + k * 0.25f,
                                1, 1, 1, 0.85f * twinFade * (1 - 0.4f * f), 0.85f * twinFade * (1 - 0.4f * f));
                    }
                    prev = pt0;
                }
            }
        }

        // ---- 5b. merged main beam
        if (reach > 0.5f && t < T_END) {
            float widthF = 0.4f + 0.6f * (float) Math.pow(fade, 0.6);
            float W = GarouGammaRayBurstSpell.WIDTH * 0.5f * widthF;
            Vec3 toCam = cam.normalize();
            float face = (float) Mth.clamp(dir.cross(toCam).length(), 0, 1);   // 0 when looking straight down the beam
            p = pen(buf, GarouRenderTypes.additive(BEAM), ps, cam);
            float tile = (float) (W * 2.4);
            float scroll = -t * 0.11f;
            Vec3 o = dir.scale(R * 0.4);
            Vec3 e0 = dir.scale(Math.min(10f, reach)), e1 = dir.scale(reach);
            // outer haze, mid body and white core (tapering out of the sphere over the first 10 blocks)
            float[] wf = {1.0f, 0.62f, 0.26f};
            float[] al = {0.50f, 0.62f, 0.85f};
            float[] sp = {1f, 1.6f, 2.4f};
            for (int L = 0; L < 3; L++) {
                float w = W * wf[L];
                float a = al[L] * fade * (0.3f + 0.7f * face);
                float va = scroll * sp[L];
                p.ribbonFacing(o, e0, R * 0.8f, w, va, va + (float) e0.length() / tile, 1, 1, 1, a * 0.8f, a);
                if (reach > 10f) p.ribbonFacing(e0, e1, w, w * 0.9f, va + (float) e0.length() / tile, va + reach / tile, 1, 1, 1, a, a * 0.9f);
            }
            // two crossed fixed planes keep volume when the beam is seen from the side or along its axis
            float a2 = 0.22f * fade * (1.2f - 0.5f * face);
            p.ribbonFixed(e0, e1, u, W * 0.7f, W * 0.65f, scroll * 1.3f, scroll * 1.3f + reach / tile, 1, 1, 1, a2, a2);
            p.ribbonFixed(e0, e1, v, W * 0.7f, W * 0.65f, scroll * 1.3f, scroll * 1.3f + reach / tile, 1, 1, 1, a2, a2);

            // rotating ribbon rings travelling outward along the beam (the manga's spiral rings)
            Pen pr = pen(buf, GarouRenderTypes.additive(RING), ps, cam);
            int n = 14;
            for (int k = 0; k < n; k++) {
                float f = ((k / (float) n) + beamAge * 0.012f) % 1f;
                float d = f * reach;
                float edge = (float) Math.min(1.0, Math.min(f * 8, (1 - f) * 4));
                float rad = W * (0.82f + 0.14f * Mth.sin(t * 0.25f + k));
                float spin = t * 0.15f * (k % 2 == 0 ? 1 : -1) + k * 0.8f;
                ring(pr, dir.scale(d), u, v, dir, rad, spin, 0.18f * Mth.sin(k * 1.7f + t * 0.06f), 0.62f * fade * edge);
            }
            // blocked end: continuous impact fire
            if (len < GarouGammaRayBurstSpell.RANGE - 0.5f) {
                Pen pb = pen(buf, GarouRenderTypes.additive(BURST), ps, cam);
                int fr = ((int) (t * 1.3f)) % 16;
                burstFrame(pb, e1, 8f * widthF, fr, 0.75f * fade);
            }
        }

        // ---- 6. stars + lightning arcs
        Pen ps2 = pen(buf, GarouRenderTypes.additive(STAR), ps, cam);
        for (int i = 0; i < 56; i++) {
            Random r = new Random(i * 7919L + 13);
            float fr = r.nextFloat(), ang = r.nextFloat() * 6.2832f, sz = 0.10f + r.nextFloat() * 0.26f, spd = 0.4f + r.nextFloat();
            Vec3 sp;
            if (beamOn && reach > 2f) {
                float f = (fr + beamAge * 0.02f * spd) % 1f;
                double lat = (0.2 + r.nextDouble() * 1.1) * GarouGammaRayBurstSpell.WIDTH * 0.5;
                sp = dir.scale(f * reach).add(u.scale(Math.cos(ang + t * 0.05 * spd) * lat)).add(v.scale(Math.sin(ang + t * 0.05 * spd) * lat));
            } else {
                double rad = (2.2 + fr * 4.0) * (1.0 - 0.6 * compP);
                double a = ang + t * 0.08 * spd;
                double b = (r.nextDouble() - 0.5) * 2.4;
                sp = u.scale(Math.cos(a) * rad * Math.cos(b)).add(v.scale(Math.sin(a) * rad * Math.cos(b))).add(dir.scale(Math.sin(b) * rad));
            }
            float tw2 = 0.55f + 0.45f * Mth.sin(t * 0.5f * spd + i);
            ps2.billboard(sp, sz, t * 0.05f, 1, 1, 1, tw2 * vis);
        }
        if (t < T_BEAM_END) {
            Pen pa = pen(buf, GarouRenderTypes.additive(ARC), ps, cam);
            Random r = new Random((long) (t / 2) * 31L + 5);
            int arcs = t < T_COMPRESS ? 5 : 8;
            for (int i = 0; i < arcs; i++) {
                Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
                if (beamOn) d = d.scale(0.6).add(dir.scale(1.4));
                d = d.normalize();
                float l = 2.5f + r.nextFloat() * 5f;
                Vec3 a0 = d.scale(R * 0.9), a1 = d.scale(R * 0.9 + l * 0.5).add(r.nextGaussian() * 0.5, r.nextGaussian() * 0.5, r.nextGaussian() * 0.5),
                        a2 = d.scale(R * 0.9 + l);
                float al = 0.85f * vis * (0.5f + 0.5f * r.nextFloat());
                pa.ribbonFacing(a0, a1, 0.28f, 0.2f, 0, 0.5f, 1, 1, 1, al, al);
                pa.ribbonFacing(a1, a2, 0.2f, 0.05f, 0.5f, 1f, 1, 1, 1, al, al * 0.2f);
            }
        }

        // ---- 7. one-shot bursts (ignite, secondary explosions, final blast)
        Pen pb = pen(buf, GarouRenderTypes.additive(BURST), ps, cam);
        Pen pring = pen(buf, GarouRenderTypes.additive(RING), ps, cam);
        for (ClientEvent ev : e.clientEvents) {
            float a = e.age - ev.tick() + pt;
            Vec3 lp = ev.pos().subtract(pos);
            float size = ev.type() == EV_IGNITE ? 15f : ev.type() == EV_END_BLAST ? 20f : 4.5f;
            float life = ev.type() == EV_BLAST ? 16f : 28f;
            if (a < 0 || a > life) continue;
            int fr = Math.min(15, (int) (a / life * 16f));
            burstFrame(pb, lp, size * (0.75f + 0.25f * s(a / 6f)), fr, 1f - s(prog(a, life * 0.55f, life)));
            float rr = ev.type() == EV_BLAST ? 1.5f + a * 0.8f : 3f + a * 2.2f;
            float ra = 0.8f * (1f - prog(a, 0, life * 0.8f));
            if (ev.type() == EV_IGNITE) ring(pring, lp, u, v, dir, rr, a * 0.3f, 0f, ra);
            else ring(pring, lp.add(0, 0.15, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0), rr, a * 0.2f, 0f, ra);
        }
    }

    /** A ring plane perpendicular to {@code axis} (basis u,v), spun and tilted, centred at c. */
    private static void ring(Pen p, Vec3 c, Vec3 u, Vec3 v, Vec3 axis, float rad, float spin, float tilt, float alpha) {
        if (alpha <= 0.01f) return;
        Vec3 a1 = u.scale(Mth.cos(spin)).add(v.scale(Mth.sin(spin)));
        Vec3 a2 = u.scale(-Mth.sin(spin)).add(v.scale(Mth.cos(spin)));
        a2 = a2.scale(Mth.cos(tilt)).add(axis.scale(Mth.sin(tilt)));
        p.plane(c, a1.scale(rad), a2.scale(rad), 1, 1, 1, alpha);
    }

    /** Flip-book frame of the 4x4 burst sheet. */
    private static void burstFrame(Pen p, Vec3 c, float half, int frame, float alpha) {
        if (alpha <= 0.01f) return;
        int col = frame % 4, row = frame / 4;
        float u0 = col / 4f, v0 = row / 4f;
        p.billboard(c, half, 0f, u0, v0, u0 + 0.25f, v0 + 0.25f, 1, 1, 1, alpha);
    }

    /** Heightmap-sampled glow decals along the ground below the beam. Per-corner heights follow slopes; clamped vs cliffs. */
    private void groundGlow(Level level, MultiBufferSource buf, PoseStack ps, Vec3 cam, Vec3 pos, Vec3 dir, Vec3 u, float reach, float t, float beamAge, float fade) {
        if (buf == null) return;
        Pen p = pen(buf, GarouRenderTypes.additive(GROUND), ps, cam);
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(1, 0, 0);
        flat = flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        float half = GarouGammaRayBurstSpell.WIDTH * 0.75f;
        float step = 5f;
        int n = (int) (reach / step);
        float pulse = 0.8f + 0.2f * Mth.sin(t * 0.4f);
        for (int i = 0; i < n; i++) {
            Vec3 c = flat.scale(i * step + step * 0.5);
            Vec3[] corner = new Vec3[4];
            float[] al = new float[4];
            double[][] o = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            boolean ok = true;
            for (int k = 0; k < 4; k++) {
                Vec3 q = c.add(flat.scale(o[k][1] * step * 0.6)).add(side.scale(o[k][0] * half));
                double wx = pos.x + q.x, wz = pos.z + q.z;
                BlockPos bp = BlockPos.containing(wx, pos.y, wz);
                if (!level.hasChunkAt(bp)) { ok = false; break; }
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(wx), Mth.floor(wz));
                double hy = Mth.clamp(h, pos.y - 14, pos.y + 1.0);       // caves / cliffs: never above the beam, never far below
                corner[k] = new Vec3(q.x, hy - pos.y + 0.07, q.z);
                float gap = (float) Mth.clamp((pos.y - hy) / 14.0, 0, 1);  // glow fades the further the ground is from the beam
                al[k] = 0.55f * pulse * fade * (1f - gap) * Mth.clamp((beamAge - i * 0.5f) / 4f, 0f, 1f);
            }
            if (!ok) continue;
            p.quad(corner[0], corner[1], corner[2], corner[3], 0, 0, 1, 1, 1, 1, 1, al[0], al[1], al[2], al[3]);
        }
    }
}
