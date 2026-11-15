package com.example.jjkspells.garou.client;

import com.example.jjkspells.garou.entity.CosmicPortalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

import static com.example.jjkspells.garou.entity.CosmicPortalEntity.*;

/**
 * Draws Cosmic Portal in the controller's local space (world - controller position, which is where the caster stood).
 *
 * Each portal is a small 3D object, drawn back to front:
 *   distortion ripples -> dark cosmic interior (ALPHA, matter) -> additive swirl layers at increasing depth (parallax tunnel)
 *   -> 3D torus frame (tube geometry with a fresnel-style alpha so it reads as a solid ring from any angle)
 *   -> rotating gyroscope rings -> orbiting sparks and lightning arcs.
 * Around them: afterimages of the player (entry: where he vanished; exit: trailing behind his arrival), speed streaks, an
 * exit flash and, when the punch connected, a burst + terrain-following shockwave + vertical shock ring at the target.
 * Glow is kept low so the frame linework and the dark centre stay readable. Nothing here touches the world.
 */
public class CosmicPortalRenderer extends EntityRenderer<CosmicPortalEntity> {
    private static ResourceLocation tex(String n) { return new ResourceLocation("jjk_spells", "textures/entity/garou/" + n + ".png"); }
    private static final ResourceLocation COSMOS = tex("cp_cosmos"), FRAME = tex("cp_frame"), WARP = tex("cp_warp"), GHOST = tex("cp_afterimage"),
            SPIRAL = tex("gk_spiral"), STREAK = tex("gk_streak"),
            DISC = tex("gamma_disc"), RING = tex("gamma_ring"), STAR = tex("gamma_star"), ARC = tex("gamma_arc"), BURST = tex("gamma_burst");
    private static final Vec3 UP = new Vec3(0, 1, 0);

    public CosmicPortalRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override public ResourceLocation getTextureLocation(CosmicPortalEntity e) { return COSMOS; }
    @Override public boolean shouldRender(CosmicPortalEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) { return true; }

    private static float s(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static float easeOutBack(float x) {
        x = Mth.clamp(x, 0, 1);
        float c1 = 1.2f, c3 = c1 + 1f;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }
    private static Pen add(MultiBufferSource b, ResourceLocation t, PoseStack ps, Vec3 cam) { return new Pen(b.getBuffer(GarouRenderTypes.additive(t)), ps, cam); }
    private static Pen alpha(MultiBufferSource b, ResourceLocation t, PoseStack ps, Vec3 cam) { return new Pen(b.getBuffer(GarouRenderTypes.alpha(t)), ps, cam); }

    /** Ring plane perpendicular to `axis` (basis u,v), spun and tilted. */
    private static void ring(Pen p, Vec3 c, Vec3 u, Vec3 v, Vec3 axis, float rad, float spin, float tilt, float a) {
        if (a <= 0.01f) return;
        Vec3 a1 = u.scale(Mth.cos(spin)).add(v.scale(Mth.sin(spin)));
        Vec3 a2 = u.scale(-Mth.sin(spin)).add(v.scale(Mth.cos(spin)));
        a2 = a2.scale(Mth.cos(tilt)).add(axis.scale(Mth.sin(tilt)));
        p.plane(c, a1.scale(rad), a2.scale(rad), 1, 1, 1, a);
    }

    private static void burst(Pen p, Vec3 c, float half, int frame, float a) {
        if (a <= 0.01f) return;
        int col = frame % 4, row = frame / 4;
        float u0 = col / 4f, v0 = row / 4f;
        p.billboard(c, half, 0f, u0, v0, u0 + 0.25f, v0 + 0.25f, 1, 1, 1, a);
    }

    /** Upright afterimage of the player (silhouette faces `face`). */
    private static void ghost(Pen p, Vec3 feet, Vec3 face, float h, float a) {
        if (a <= 0.01f) return;
        Vec3 toCam = new Vec3(p.cam.x - feet.x, 0, p.cam.z - feet.z);
        if (toCam.lengthSqr() < 1e-6) return;
        Vec3 r = UP.cross(toCam.normalize()).normalize();
        boolean flip = r.dot(face) < 0;
        p.plane(feet.add(0, h * 0.5, 0), r.scale(h * 0.25), new Vec3(0, h * 0.5, 0), flip ? 1 : 0, 1, flip ? 0 : 1, 0, 1, 1, 1, a);
    }

    @Override
    public void render(CosmicPortalEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.age + pt;
        Vec3 pos = e.getPosition(pt);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(pos);
        Vec3 entryC = e.entryCenter().subtract(pos), exitC = e.exitCenter().subtract(pos);
        float entryOpen = s(prog(t, 0, 6)) * (1f - s(prog(t, 10, 16)));
        float exitOpen = s(prog(t, T_VANISH, 13)) * (1f - s(prog(t, 22, T_END)));

        portal(buf, ps, cam, entryC, e.entryNormal(), 1.6f, entryOpen, t, 1);
        portal(buf, ps, cam, exitC, e.exitNormal(), 1.6f, exitOpen, t, 2);

        // ---- afterimages
        Vec3 f0 = CosmicPortalEntity.dirOf(e.getEntryYaw());
        Pen g = add(buf, GHOST, ps, cam);
        float ea = t - T_VANISH;
        if (ea > -0.5f && ea < 11f) {
            float fade = 1f - s(prog(ea, 0, 10));
            for (int k = 0; k < 4; k++) ghost(g, f0.scale(0.45 * k), f0, 1.95f, 0.75f * fade * (1f - 0.22f * k));
        }
        Vec3 destL = e.getDest().subtract(pos);
        Vec3 f1 = CosmicPortalEntity.dirOf(e.getDestYaw());
        float a = t - T_EMERGE;
        if (a >= 0 && a < 11f) {
            float fade = 1f - s(prog(a, 0, 10));
            for (int k = 0; k < 5; k++) ghost(g, destL.subtract(f1.scale(0.9 * k)), f1, 1.95f, 0.8f * fade * (1f - 0.17f * k));

            // speed streaks trailing behind the emerging player (manga speed lines)
            Pen st = add(buf, STREAK, ps, cam);
            for (int i = 0; i < 6; i++) {
                Random r = new Random(i * 977L + 3);
                Vec3 start = destL.add(0, 0.3 + 0.28 * i, 0).add(f1.cross(UP).scale((r.nextFloat() - 0.5f) * 1.2));
                float len = 3f + 3f * r.nextFloat();
                st.ribbonFacing(start, start.subtract(f1.scale(len)), 0.07f, 0.02f, 0, 1, 1, 1, 1, 0.85f * (1f - prog(a, 0, 8)), 0f);
            }
            // exit flash
            Pen pb = add(buf, BURST, ps, cam);
            burst(pb, destL.add(0, 1.0, 0), e.wasHit() ? 2.6f : 3.4f, Math.min(15, (int) (a / 10f * 16f)), 1f - s(prog(a, 4, 10)));
        }

        // ---- punch connected: burst, terrain-following shockwave, vertical ring
        if (e.wasHit() && a >= 0 && a < 16f) {
            Vec3 impL = e.getImpact().subtract(pos);
            Pen pb = add(buf, BURST, ps, cam);
            burst(pb, impL, 6.5f + 2f * s(a / 5f), Math.min(15, (int) (a / 14f * 16f)), 1f - s(prog(a, 7, 14)));
            Pen pr = add(buf, RING, ps, cam);
            float rr = 1.3f + a * 0.65f;
            Vec3 fu = f1.cross(UP).normalize();
            ring(pr, impL, fu, UP, f1, rr, a * 0.15f, 0f, 0.75f * (1f - prog(a, 0, 14)));      // vertical cosmic ring facing the punch
            GroundDecal.draw(pr, e.level(), pos, e.getImpact(), 1.2f + a * 0.75f, a * 0.1f, 8, 0.8f * (1f - prog(a, 0, 15)));   // shockwave hugging the terrain
            Pen sp = add(buf, STAR, ps, cam);
            for (int i = 0; i < 14; i++) {
                Random r = new Random(i * 3571L + 8);
                Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                sp.billboard(impL.add(d.scale(1.0 + a * (0.7 + r.nextFloat()))), 0.14f + 0.12f * r.nextFloat(), a, 1, 1, 1, 1f - prog(a, 2, 13));
            }
        }
    }

    // ===================================================================== a single portal
    private void portal(MultiBufferSource buf, PoseStack ps, Vec3 cam, Vec3 c, Vec3 n, float R0, float open, float t, int seed) {
        if (open < 0.02f) return;
        float R = R0 * Math.max(0.08f, easeOutBack(open));
        float al = Math.min(1f, open * 2.5f);
        Vec3 u = n.cross(UP);
        if (u.lengthSqr() < 1e-4) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = n.cross(u).normalize();
        float sd = seed * 1.7f;

        // 1. distortion ripples around the edge (fake lens)
        Pen p = add(buf, WARP, ps, cam);
        ring(p, c, u, v, n, R * 1.75f, t * 0.03f + sd, 0f, 0.55f * al);
        ring(p, c.add(n.scale(0.1)), u, v, n, R * 2.3f, -t * 0.045f + sd, 0.2f, 0.3f * al);

        // 2. dark cosmic interior (matter: alpha blended)
        p = alpha(buf, COSMOS, ps, cam);
        ring(p, c.subtract(n.scale(0.05)), u, v, n, R * 0.98f, t * 0.05f + sd, 0f, 0.98f * al);

        // 3. animated interior: swirls at increasing depth give a tunnel parallax
        p = add(buf, SPIRAL, ps, cam);
        ring(p, c.subtract(n.scale(0.12)), u, v, n, R * 0.92f, -t * 0.09f + sd, 0f, 0.5f * al);
        ring(p, c.subtract(n.scale(0.35)), u, v, n, R * 0.7f, t * 0.14f + sd, 0f, 0.38f * al);
        p = add(buf, DISC, ps, cam);
        ring(p, c.subtract(n.scale(0.22)), u, v, n, R * 0.8f, t * 0.07f + sd, 0f, 0.28f * al);
        p = add(buf, RING, ps, cam);
        for (int k = 1; k <= 4; k++) {
            ring(p, c.subtract(n.scale(0.18 * k)), u, v, n, R * (0.97f - 0.13f * k), t * 0.1f * (k % 2 == 0 ? 1 : -1) + k, 0f, 0.32f * al * (1f - 0.15f * k));
        }

        // 4. solid 3D frame
        frame(buf, ps, cam, c, n, u, v, R, 0.17f * (0.6f + 0.4f * R / R0), al, t);

        // 5. rotating spatial rings (gyroscope)
        p = add(buf, RING, ps, cam);
        for (int i = 0; i < 3; i++) {
            float rad = R * (1.28f + 0.26f * i);
            float spin = t * (0.08f + 0.04f * i) * (i % 2 == 0 ? 1 : -1) + sd;
            ring(p, c, u, v, n, rad, spin, 0.4f * Mth.sin(t * 0.05f + i * 2f + sd), 0.55f * al);
        }
        ring(p, c, u, v, n, R * 1.12f, t * 0.2f + sd, 1.45f, 0.5f * al);

        // 6. orbiting sparks + lightning arcs from the rim
        p = add(buf, STAR, ps, cam);
        for (int i = 0; i < 14; i++) {
            Random r = new Random(i * 7541L + seed);
            float ang = r.nextFloat() * 6.2832f + t * (0.05f + 0.06f * r.nextFloat()) * (i % 2 == 0 ? 1 : -1);
            float rad = R * (1.0f + 0.7f * r.nextFloat());
            Vec3 sp = c.add(u.scale(Mth.cos(ang) * rad)).add(v.scale(Mth.sin(ang) * rad)).add(n.scale((r.nextFloat() - 0.5f) * 1.2f));
            p.billboard(sp, 0.08f + 0.14f * r.nextFloat(), t * 0.1f, 1, 1, 1, (0.5f + 0.5f * Mth.sin(t * 0.5f + i)) * al);
        }
        p = add(buf, ARC, ps, cam);
        Random ra = new Random((long) (t / 2) * 31L + seed * 101L);
        for (int i = 0; i < 4; i++) {
            float ang = ra.nextFloat() * 6.2832f;
            Vec3 d = u.scale(Mth.cos(ang)).add(v.scale(Mth.sin(ang)));
            Vec3 out = d.add(n.scale(ra.nextGaussian() * 0.5)).normalize();
            float l = 1.4f + ra.nextFloat() * 2.2f;
            Vec3 a0 = c.add(d.scale(R * 1.02)), a1 = a0.add(out.scale(l * 0.5)).add(ra.nextGaussian() * 0.15, ra.nextGaussian() * 0.15, ra.nextGaussian() * 0.15), a2 = a0.add(out.scale(l));
            float aa = 0.8f * al * (0.5f + 0.5f * ra.nextFloat());
            p.ribbonFacing(a0, a1, 0.16f, 0.12f, 0, 0.5f, 1, 1, 1, aa, aa);
            p.ribbonFacing(a1, a2, 0.12f, 0.03f, 0.5f, 1f, 1, 1, 1, aa, aa * 0.2f);
        }
    }

    /** A torus built from quads (N around the ring x M around the tube). Alpha rises where the tube faces away from the view. */
    private void frame(MultiBufferSource buf, PoseStack ps, Vec3 cam, Vec3 c, Vec3 n, Vec3 u, Vec3 v, float R, float rt, float al, float t) {
        Pen p = add(buf, FRAME, ps, cam);
        final int N = 40, M = 6;
        float spin = t * 0.03f;
        float scroll = t * 0.02f;
        for (int i = 0; i < N; i++) {
            float a0 = i / (float) N * 6.2832f + spin, a1 = (i + 1) / (float) N * 6.2832f + spin;
            for (int j = 0; j < M; j++) {
                float b0 = j / (float) M * 6.2832f, b1 = (j + 1) / (float) M * 6.2832f;
                Vec3 p00 = torus(c, u, v, n, R, rt, a0, b0), p10 = torus(c, u, v, n, R, rt, a1, b0);
                Vec3 p11 = torus(c, u, v, n, R, rt, a1, b1), p01 = torus(c, u, v, n, R, rt, a0, b1);
                p.quad(p00, p10, p11, p01,
                        i / (float) N * 3f - scroll, 0.5f - 0.5f * Mth.cos(b0), (i + 1) / (float) N * 3f - scroll, 0.5f - 0.5f * Mth.cos(b1),
                        1, 1, 1,
                        fres(c, u, v, n, R, rt, a0, b0, cam, al), fres(c, u, v, n, R, rt, a1, b0, cam, al),
                        fres(c, u, v, n, R, rt, a1, b1, cam, al), fres(c, u, v, n, R, rt, a0, b1, cam, al));
            }
        }
    }

    private static Vec3 torus(Vec3 c, Vec3 u, Vec3 v, Vec3 n, float R, float rt, float a, float b) {
        Vec3 d = u.scale(Mth.cos(a)).add(v.scale(Mth.sin(a)));
        return c.add(d.scale(R + rt * Mth.cos(b))).add(n.scale(rt * Mth.sin(b)));
    }

    private static float fres(Vec3 c, Vec3 u, Vec3 v, Vec3 n, float R, float rt, float a, float b, Vec3 cam, float al) {
        Vec3 d = u.scale(Mth.cos(a)).add(v.scale(Mth.sin(a)));
        Vec3 nrm = d.scale(Mth.cos(b)).add(n.scale(Mth.sin(b)));
        Vec3 view = cam.subtract(torus(c, u, v, n, R, rt, a, b));
        double l = view.length();
        if (l < 1e-4) return al;
        double f = 1.0 - Math.abs(nrm.dot(view.scale(1.0 / l)));
        return (float) (al * (0.7 + 0.3 * f));
    }
}
