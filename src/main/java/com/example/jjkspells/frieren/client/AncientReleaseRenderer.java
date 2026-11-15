package com.example.jjkspells.frieren.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.frieren.entity.AncientReleaseEntity;
import com.example.jjkspells.frieren.spell.AncientReleaseSpell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Random;

import static com.example.jjkspells.frieren.entity.AncientReleaseEntity.*;

/**
 * Zoltraak: Ancient Release - the Zoltraak seal scaled up to the extreme. Everything is blue-white (no gold: this is raw mana).
 *
 * 1 Mana release - a huge four-lobed seal on the terrain (heightmap-sampled, clamped), the surroundings lighting up, motes drifting
 *   in towards the caster, eyes and hands glowing.
 * 2 Compression - a vast backdrop circle behind the caster with six smaller circles orbiting it; a sphere forms in front, layers of
 *   geometric patterns turning inside it, then it collapses into one brilliant point.
 * 3 Release - an 8-block beam (white core, three transparent cyan layers, rings sliding along it, glitter), lighting the ground
 *   along its path; a sustained impact glows at its end.
 * 4 Final impact - flash, expanding shell, shockwave rings across the terrain, a pillar of light into the sky, fading motes.
 *
 * Camera: a slight zoom while charging and a screen shake at release/impact (see {@link FrierenCamera}).
 */
public class AncientReleaseRenderer extends EntityRenderer<AncientReleaseEntity> {
    private static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/entity/frieren/" + n + ".png");
    }

    private static final ResourceLocation RING_A = tex("zoltraak_ring_a");
    private static final ResourceLocation RING_B = tex("zoltraak_ring_b");
    private static final ResourceLocation RING_C = tex("zoltraak_ring_c");
    private static final ResourceLocation FLARE = tex("zoltraak_flare");
    private static final ResourceLocation CORE = tex("zoltraak_beam");
    private static final ResourceLocation BURST = tex("zoltraak_burst");
    private static final ResourceLocation SPARK = tex("zoltraak_spark");
    private static final ResourceLocation CYAN = tex("hom_beam_cyan");
    private static final ResourceLocation GLOW = tex("hom_glow");
    private static final ResourceLocation SHELL = tex("hom_shell");
    private static final ResourceLocation CORONA = tex("hom_corona");
    private static final ResourceLocation HALO = tex("barrier_halo");
    private static final ResourceLocation SEAL = tex("barrier_seal");
    private static final ResourceLocation LINE = tex("barrier_line");
    private static final ResourceLocation GEO = tex("ar_geo");

    private static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);
    private static final double SEAL_R = 9.0;
    private static final float BEAM_W = AncientReleaseSpell.BEAM_WIDTH;

    public AncientReleaseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(AncientReleaseEntity e) {
        return RING_A;
    }

    @Override
    public boolean shouldRender(AncientReleaseEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    // ---------------------------------------------------------------------------------------------- state / helpers
    private static final class Ctx {
        ZolPen pen;
        MultiBufferSource buf;
        AncientReleaseEntity e;
        ClientLevel lvl;
        float t, cf, len, tX, tArr, env = 1f;
        int rel;
        boolean released;
        float eyeH;
        Vec3 base, look, hdir, S, O, dir, bu, bv, Tl, Tw;
        double impactY;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float hash(int i) {
        int h = i * 374761393;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xffff) / 65535f;
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1f, k = x - 1f;
        return 1f + c3 * k * k * k + c1 * k * k;
    }

    private static VertexConsumer vc(MultiBufferSource buf, ResourceLocation t) {
        return buf.getBuffer(ZolRenderTypes.additive(t));
    }

    private static double groundY(ClientLevel lvl, double x, double z, double refY, double lo, double hi) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!lvl.hasChunkAt(new BlockPos(bx, Mth.floor(refY), bz))) return refY + 0.05;
        double h = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return Mth.clamp(h, refY + lo, refY + hi) + 0.05;
    }

    // ---------------------------------------------------------------------------------------------- render
    @Override
    public void render(AncientReleaseEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Ctx c = new Ctx();
        c.e = e;
        c.buf = buf;
        c.lvl = mc.level;
        c.t = e.tickCount + pt;
        c.rel = e.release();
        c.released = c.rel >= 0;
        int cancel = e.cancelTick();
        c.cf = cancel >= 0 ? 1f - smooth((c.t - cancel) / 12f) : 1f;
        c.base = e.getPosition(pt);
        c.eyeH = e.eyeHeight();

        Vec3 look = e.lookPrev.lerp(e.lookCur, pt);
        if (look.lengthSqr() < 1.0E-6) look = e.look();
        c.look = look.normalize();
        Vec3 h = new Vec3(c.look.x, 0, c.look.z);
        c.hdir = h.lengthSqr() < 0.04 ? Z : h.normalize();
        c.S = new Vec3(0, c.eyeH, 0).add(c.look.scale(ORIGIN_DIST));

        if (c.released) {
            c.O = e.origin().subtract(c.base);
            c.S = c.O;
            c.len = e.beamLength();
            c.dir = e.look().normalize();
            Vec3 up0 = Math.abs(c.dir.y) > 0.97 ? X : Y;
            c.bu = c.dir.cross(up0).normalize();
            c.bv = c.dir.cross(c.bu).normalize();
            c.Tl = c.O.add(c.dir.scale(c.len - 0.5f));
            c.Tw = c.Tl.add(c.base);
            c.tX = c.rel + BEAM_TICKS;
            c.tArr = c.rel + c.len / BEAM_SPEED;
            double gh = c.lvl.hasChunkAt(new BlockPos(Mth.floor(c.Tw.x), Mth.floor(c.Tw.y), Mth.floor(c.Tw.z)))
                    ? c.lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(c.Tw.x), Mth.floor(c.Tw.z)) : c.Tw.y;
            c.impactY = Math.max(Math.min(gh, c.Tw.y + 1.0), c.Tw.y - 40.0);
            c.env = 1f - smooth((c.t - c.tX) / 12f);
        }

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camLocal = cam.getPosition().subtract(c.base);
        Vector3f l = cam.getLeftVector();
        Vector3f u = cam.getUpVector();
        c.pen = new ZolPen(ps.last().pose(), camLocal, new Vec3(-l.x(), -l.y(), -l.z()), new Vec3(u.x(), u.y(), u.z()));

        boolean mine = mc.player != null && mc.player.getId() == e.ownerEntityId();
        feedCamera(c, cam.getPosition(), mc, mine);
        if (c.cf <= 0.003f) return;

        drawGround(c);
        drawMotes(c);
        drawBody(c, mc, mine);
        drawBackdrop(c);
        if (!c.released) drawSphere(c);
        if (c.released) {
            drawBeam(c);
            drawTerminal(c);
            drawBlast(c);
        }
    }

    private void feedCamera(Ctx c, Vec3 camWorld, Minecraft mc, boolean mine) {
        double dBase = camWorld.distanceTo(c.base);
        float near = mine ? 1f : Mth.clamp(1f - (float) dBase / 70f, 0f, 1f);
        float amp = 0f;
        if (!c.released && c.cf > 0.5f && c.t > 50f) amp = 0.3f * smooth((c.t - 50f) / 30f) * (mine ? 1f : near * 0.6f);
        if (c.released) {
            float k = c.t - c.rel;
            if (k >= 0f) amp += (1.8f * (float) Math.exp(-k / 6f) + (k < BEAM_TICKS ? 0.5f : 0f)) * near;
            float a = c.t - c.tX;
            if (a >= 0f) {
                float dT = (float) camWorld.distanceTo(c.Tw);
                amp += 2.8f * Math.min(1f, a / 1.5f) * (float) Math.exp(-a / 14f) * Mth.clamp(1f - dT / 110f, 0f, 1f);
            }
        }
        if (amp > 0f) FrierenCamera.feedShake(amp);
        if (mine) {
            float z;
            if (c.e.cancelTick() >= 0) z = 1f - 0.05f * (1f - smooth((c.t - c.e.cancelTick()) / 10f));
            else if (c.released) z = 1f - 0.05f * (1f - smooth((c.t - c.rel) / 15f));
            else z = 1f - 0.05f * smooth(c.t / 60f);
            FrierenCamera.feedFov(z);
        }
    }

    // ---------------------------------------------------------------------------------------------- terrain meshes
    private static float[][] sample(Ctx c, double cx, double cz, double refY, double R, int n, double lo, double hi) {
        float[][] g = new float[n + 1][n + 1];
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                g[i][j] = (float) groundY(c.lvl, cx + (i / (double) n * 2 - 1) * R, cz + (j / (double) n * 2 - 1) * R, refY, lo, hi);
            }
        }
        return g;
    }

    private static double gridY(float[][] g, int n, double Rg, double a, double b) {
        double fx = Mth.clamp((a / Rg * 0.5 + 0.5) * n, 0, n - 1.0E-3), fz = Mth.clamp((b / Rg * 0.5 + 0.5) * n, 0, n - 1.0E-3);
        int i = (int) fx, j = (int) fz;
        double tx = fx - i, tz = fz - j;
        double y0 = g[i][j] * (1 - tx) + g[i + 1][j] * tx, y1 = g[i][j + 1] * (1 - tx) + g[i + 1][j + 1] * tx;
        return y0 * (1 - tz) + y1 * tz;
    }

    private static void drawGrid(Ctx c, float[][] g, int n, double Rg, double cx, double cz, double Rl, double rot, ResourceLocation tex, float alpha, double yOff) {
        if (alpha <= 0.003f || Rl <= 0.01) return;
        ZolPen p = c.pen.use(vc(c.buf, tex));
        double cs = Math.cos(rot), sn = Math.sin(rot);
        Vec3[][] v = new Vec3[n + 1][n + 1];
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                double a = (i / (double) n * 2 - 1) * Rl, b = (j / (double) n * 2 - 1) * Rl;
                double wx = cx + a * cs - b * sn, wz = cz + a * sn + b * cs;
                v[i][j] = new Vec3(wx - c.base.x, gridY(g, n, Rg, wx - cx, wz - cz) + yOff - c.base.y, wz - c.base.z);
            }
        }
        float[] col = {1f, 1f, 1f, alpha, 1f, 1f, 1f, alpha, 1f, 1f, 1f, alpha, 1f, 1f, 1f, alpha};
        double halfDiag = Rl / n * 1.4143;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double ac = ((i + 0.5) / n * 2 - 1) * Rl, bc = ((j + 0.5) / n * 2 - 1) * Rl;
                if (Math.hypot(ac, bc) - halfDiag > Rl) continue;
                float u0 = i / (float) n, u1 = (i + 1) / (float) n, v0 = j / (float) n, v1 = (j + 1) / (float) n;
                p.quadG(new Vec3[]{v[i][j], v[i + 1][j], v[i + 1][j + 1], v[i][j + 1]}, new float[]{u0, v0, u1, v0, u1, v1, u0, v1}, col);
            }
        }
    }

    /** A terrain-following ring strip round (cx,cz) (U across the strip). */
    private static void drawRing(Ctx c, double cx, double cz, double refY, double r, double w, float alpha, ResourceLocation tex) {
        if (alpha <= 0.003f || r <= 0.0) return;
        ZolPen p = c.pen.use(vc(c.buf, tex));
        final int seg = 72;
        double in = Math.max(0.0, r - w * 0.5), out = r + w * 0.5;
        Vec3[] a = new Vec3[seg + 1], b = new Vec3[seg + 1];
        for (int m = 0; m <= seg; m++) {
            double th = Math.PI * 2 * m / seg, cs = Math.cos(th), sn = Math.sin(th);
            double x0 = cx + cs * in, z0 = cz + sn * in, x1 = cx + cs * out, z1 = cz + sn * out;
            a[m] = new Vec3(x0 - c.base.x, groundY(c.lvl, x0, z0, refY, -5, 5) - c.base.y, z0 - c.base.z);
            b[m] = new Vec3(x1 - c.base.x, groundY(c.lvl, x1, z1, refY, -5, 5) - c.base.y, z1 - c.base.z);
        }
        float[] col = {1, 1, 1, alpha, 1, 1, 1, alpha, 1, 1, 1, alpha, 1, 1, 1, alpha};
        for (int m = 0; m < seg; m++) {
            p.quadG(new Vec3[]{a[m], b[m], b[m + 1], a[m + 1]}, new float[]{0, 0, 1, 0, 1, 1, 0, 1}, col);
        }
    }

    private void drawShell(Ctx c, Vec3 center, double radius, float alpha, float a) {
        if (alpha <= 0.003f || radius <= 0.05) return;
        ZolPen p = c.pen.use(vc(c.buf, SHELL));
        final int lat = 10, lon = 20;
        Vec3[][] pos = new Vec3[lat + 1][lon + 1];
        float[][] al = new float[lat + 1][lon + 1];
        for (int i = 0; i <= lat; i++) {
            double th = Math.PI * i / lat;
            for (int j = 0; j <= lon; j++) {
                double ph = Math.PI * 2 * j / lon;
                Vec3 n = new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
                Vec3 pt = center.add(n.scale(radius));
                Vec3 v = c.pen.camPos.subtract(pt);
                double len = v.length();
                float f = len < 1.0E-4 ? 0f : (float) (1.0 - Math.abs(n.dot(v)) / len);
                pos[i][j] = pt;
                al[i][j] = alpha * (0.25f + 0.75f * f); // brighter at the rim, see-through in the middle
            }
        }
        float scroll = a * 0.04f;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                float u0 = j / (float) lon * 2f + scroll, u1 = (j + 1) / (float) lon * 2f + scroll, v0 = i / (float) lat, v1 = (i + 1) / (float) lat;
                float[] col = {1, 1, 1, al[i][j], 1, 1, 1, al[i + 1][j], 1, 1, 1, al[i + 1][j + 1], 1, 1, 1, al[i][j + 1]};
                p.quadG(new Vec3[]{pos[i][j], pos[i + 1][j], pos[i + 1][j + 1], pos[i][j + 1]}, new float[]{u0, v0, u0, v1, u1, v1, u1, v0}, col);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- phase 1: mana release
    private float groundFade(Ctx c) {
        return c.cf * (c.released ? 1f - smooth((c.t - (c.tX + 30f)) / 20f) : 1f);
    }

    private void drawGround(Ctx c) {
        float scale = smooth(c.t / 20f);
        float a = smooth(c.t / 10f) * groundFade(c);
        if (scale <= 0.003f || a <= 0.003f) return;
        double cx = c.base.x, cz = c.base.z, refY = c.base.y;
        final int n = 22;
        float[][] g = sample(c, cx, cz, refY, SEAL_R, n, -2.0, 2.0);
        drawGrid(c, g, n, SEAL_R, cx, cz, SEAL_R * scale, 0.015 * c.t, RING_A, a, 0.05);
        drawGrid(c, g, n, SEAL_R, cx, cz, SEAL_R * 0.9 * scale, -0.03 * c.t, RING_B, a * smooth((c.t - 6f) / 10f), 0.07);
        drawGrid(c, g, n, SEAL_R, cx, cz, SEAL_R * 0.5 * scale, 0.05 * c.t, RING_C, a * smooth((c.t - 12f) / 10f), 0.09);
        // the surroundings light up
        final int gn = 12;
        double gr = 22.0;
        float[][] gg = sample(c, cx, cz, refY, gr, gn, -3.0, 3.0);
        drawGrid(c, gg, gn, gr, cx, cz, gr * smooth(c.t / 40f), 0.0, GLOW, 0.6f * smooth(c.t / 60f) * a, 0.03);
    }

    private void drawMotes(Ctx c) {
        float inten = smooth(c.t / 20f) * (c.released ? 1f - smooth((c.t - c.rel) / 6f) : 1f) * c.cf;
        if (inten <= 0.003f) return;
        ZolPen f = c.pen.use(vc(c.buf, FLARE));
        Vec3 chest = new Vec3(0, c.eyeH * 0.7, 0);
        for (int i = 0; i < 120; i++) {
            float ph = frac(c.t * 0.018f + hash(i * 3));
            double ang = hash(i * 5) * Math.PI * 2 + ph * 3.5;
            double yy = (hash(i * 7) * 2 - 1) * 0.9, rr = Math.sqrt(1 - yy * yy);
            double rad = 0.5 + 11.0 * Math.pow(1 - ph, 1.5);
            Vec3 pos = chest.add(Math.cos(ang) * rr * rad, yy * rad * 0.7, Math.sin(ang) * rr * rad);
            float al = inten * (float) Math.pow(Math.sin(Math.PI * ph), 0.8);
            f.billboard(pos, 0.09 + 0.06 * hash(i * 11), ph * 6, ZolPen.FULL, 1, 1, 1, al);
        }
    }

    /** Eyes and hands glow (the eyes are skipped in first person: they would sit right on the camera). */
    private void drawBody(Ctx c, Minecraft mc, boolean mine) {
        float a = smooth((c.t - 6f) / 20f) * c.cf * (c.released ? 1f - smooth((c.t - c.tX) / 15f) : 1f);
        if (a <= 0.003f) return;
        ZolPen f = c.pen.use(vc(c.buf, FLARE));
        Vec3 right = c.hdir.cross(Y).normalize();
        Vec3 eye = new Vec3(0, c.eyeH, 0);
        boolean firstPerson = mine && mc.options.getCameraType().isFirstPerson();
        if (!firstPerson) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 p = eye.add(c.look.scale(0.27)).add(right.scale(s * 0.11));
                f.billboard(p, 0.17 + 0.03 * Math.sin(c.t * 0.8), 0.0, ZolPen.FULL, 1, 1, 1, a);
            }
        }
        float thrust = c.released ? smooth((c.t - c.rel) / 3f) : 0f;
        for (int s = -1; s <= 1; s += 2) {
            Vec3 rest = new Vec3(0, c.eyeH - 0.65, 0).add(right.scale(s * 0.42)).add(c.hdir.scale(0.2));
            Vec3 pos = rest;
            if (s == 1) { // the right hand thrusts forward as the beam is released
                Vec3 out = new Vec3(0, c.eyeH - 0.35, 0).add(c.look.scale(1.3)).add(right.scale(0.15));
                pos = rest.lerp(out, thrust);
            }
            f.billboard(pos, 0.45 * (0.8 + 0.2 * Math.sin(c.t * 0.9 + s)), c.t * 0.1, ZolPen.FULL, 1, 1, 1, a);
        }
    }

    // ---------------------------------------------------------------------------------------------- phase 2: compression
    private void drawBackdrop(Ctx c) {
        float ap = smooth((c.t - 36f) / 14f) * c.cf * (c.released ? 1f - smooth((c.t - c.tX) / 20f) : 1f);
        if (ap <= 0.003f) return;
        float t = c.t;
        Vec3 Bc = new Vec3(0, c.eyeH * 0.75, 0).subtract(c.hdir.scale(5.5));
        Vec3 u = c.hdir.cross(Y).normalize();
        double R = 7.5 * easeOutBack(Mth.clamp((t - 36f) / 14f, 0f, 1f));
        c.pen.use(vc(c.buf, RING_A)).plane(Bc, u, Y, R, 0.015 * t, ZolPen.FULL, 1, 1, 1, ap);
        c.pen.use(vc(c.buf, RING_B)).plane(Bc.subtract(c.hdir.scale(0.02)), u, Y, R * 0.92, -0.03 * t, ZolPen.FULL, 1, 1, 1, ap * smooth((t - 40f) / 10f));
        c.pen.use(vc(c.buf, RING_C)).plane(Bc.subtract(c.hdir.scale(0.04)), u, Y, R * 0.5, 0.05 * t, ZolPen.FULL, 1, 1, 1, ap * smooth((t - 44f) / 10f));
        // six smaller circles orbit it
        ResourceLocation[] texs = {RING_C, HALO, SEAL, RING_C, HALO, SEAL};
        for (int i = 0; i < 6; i++) {
            float api = ap * smooth((t - 44f - 2 * i) / 8f);
            if (api <= 0.003f) continue;
            double ang = t * 0.03 + i * Math.PI / 3;
            Vec3 center = Bc.add(u.scale(Math.cos(ang) * 9.3)).add(0, Math.sin(ang) * 9.3, 0);
            double half = 1.8 * easeOutBack(Mth.clamp((t - 44f - 2 * i) / 8f, 0f, 1f));
            c.pen.use(vc(c.buf, texs[i])).plane(center, u, Y, half, ((i & 1) == 0 ? 1 : -1) * 0.06 * t, ZolPen.FULL, 1, 1, 1, api);
        }
    }

    private void drawSphere(Ctx c) {
        if (c.t < 40f) return;
        float t = c.t;
        float k = smooth((t - 62f) / 18f);
        double r = t < 62f ? 3.0 * easeOutBack(Mth.clamp((t - 40f) / 20f, 0f, 1f)) : 3.0 * Math.pow(1.0 - k, 1.5) + 0.12;
        float a = c.cf;
        Vec3 S = c.S;
        drawShell(c, S, r, 0.40f * (1f - 0.5f * k) * a, t);
        // layers of geometric patterns turning inside the sphere, like a gyroscope
        ResourceLocation[] tx = {RING_B, SEAL, GEO};
        double[] th = {Math.toRadians(90), Math.toRadians(60), Math.toRadians(25)}, spd = {0.05, -0.04, 0.06}, fac = {0.92, 0.72, 0.52};
        for (int j = 0; j < 3; j++) {
            double al = t * spd[j] + j * 2.0;
            Vec3 n = new Vec3(Math.sin(th[j]) * Math.cos(al), Math.cos(th[j]), Math.sin(th[j]) * Math.sin(al));
            Vec3 uu = n.cross(Math.abs(n.y) > 0.97 ? X : Y).normalize();
            Vec3 vv = n.cross(uu).normalize();
            float aj = 0.85f * (1f - 0.4f * k) * a * smooth((t - 42f - 4 * j) / 8f);
            c.pen.use(vc(c.buf, tx[j])).plane(S, uu, vv, r * fac[j], t * (0.08 + 0.03 * j) * (j == 1 ? -1 : 1), ZolPen.FULL, 1, 1, 1, aj);
        }
        c.pen.use(vc(c.buf, CORONA)).billboard(S, r * 1.6, 0.03 * t, ZolPen.FULL, 1, 1, 1, 0.5f * (1f - k) * a);
        ZolPen f = c.pen.use(vc(c.buf, FLARE));
        f.billboard(S, r * 0.55 * (0.4 + 0.6 * k) + 0.1, t * 0.3, ZolPen.FULL, 1, 1, 1, a);
        if (k > 0f) f.billboard(S, 0.5 + 2.0 * k, 0.0, ZolPen.FULL, 1, 1, 1, k * a); // the brilliant point of light
    }

    // ---------------------------------------------------------------------------------------------- phase 3: release
    private static float tp(Ctx c, float d) {
        return c.rel + d / BEAM_SPEED;
    }

    private static float ramp(Ctx c, float d) {
        return smooth((c.t - tp(c, d)) / 0.3f);
    }

    private static double bw(Ctx c, float d) {
        return ramp(c, d) * (0.5f + 0.5f * smooth(d / 4f)) * (0.15f + 0.85f * c.env) * (1f + 0.04f * (float) Math.sin(c.t * 1.7f));
    }

    private static float ba(Ctx c, float d) {
        return (float) Math.sqrt(ramp(c, d)) * c.env;
    }

    private void beamLayer(Ctx c, ResourceLocation tex, double W, float aMul, float headD, int n) {
        ZolPen p = c.pen.use(vc(c.buf, tex));
        for (int i = 0; i < n; i++) {
            float d0 = headD * i / n, d1 = headD * (i + 1) / n;
            p.ribbon(c.O.add(c.dir.scale(d0)), c.O.add(c.dir.scale(d1)), bw(c, d0) * W, bw(c, d1) * W, d0 * 0.1f - c.t * 1.5f, d1 * 0.1f - c.t * 1.5f,
                    ba(c, d0) * aMul, ba(c, d1) * aMul, 1, 1, 1);
        }
    }

    private void drawBeam(Ctx c) {
        float k = c.t - c.rel;
        if (k < 0f || c.env <= 0.002f) return;
        float headD = Mth.clamp(BEAM_SPEED * k, 0f, c.len);

        // the sphere's point of light stays where the beam leaves it
        ZolPen f0 = c.pen.use(vc(c.buf, FLARE));
        f0.billboard(c.O, 3.0 + 0.3 * Math.sin(c.t * 2.0), c.t * 0.4, ZolPen.FULL, 1, 1, 1, c.env);
        c.pen.use(vc(c.buf, CORONA)).billboard(c.O, 7.0, c.t * 0.03, ZolPen.FULL, 1, 1, 1, 0.6f * c.env);

        if (headD > 0.01f) {
            int n = Mth.clamp((int) Math.ceil(headD / 2f), 1, 48);
            beamLayer(c, CYAN, BEAM_W * 1.3, 0.12f, headD, n);   // transparent outer layers
            beamLayer(c, CYAN, BEAM_W * 0.95, 0.30f, headD, n);
            beamLayer(c, CYAN, BEAM_W * 0.75, 0.55f, headD, n);
            beamLayer(c, CORE, BEAM_W * 0.40, 1.0f, headD, n);    // white core

            // smaller rings slide along the beam, each turning its own way
            double spacing = 12.0;
            int cnt = (int) (c.len / spacing) + 1;
            for (int j = 0; j < cnt; j++) {
                float d = (float) ((j * spacing + c.t * 7.0) % Math.max(1f, c.len));
                if (d > headD) continue;
                double half = 4.9 * (0.5 + 0.5 * smooth(d / 4f)) * (0.3 + 0.7 * c.env);
                double rot = c.t * 0.12 * ((j & 1) == 0 ? 1 : -1) + j;
                c.pen.use(vc(c.buf, (j & 1) == 0 ? HALO : RING_B)).plane(c.O.add(c.dir.scale(d)), c.bu, c.bv, half, rot, ZolPen.FULL, 1, 1, 1,
                        0.75f * c.env * ba(c, d) * (1f - 0.4f * d / c.len));
            }

            // head flare and glitter
            ZolPen f = c.pen.use(vc(c.buf, FLARE));
            float headA = 1f - Mth.clamp(c.t - c.tArr, 0f, 1f);
            if (headA > 0.003f) f.billboard(c.O.add(c.dir.scale(headD)), 6.0 + 0.5 * Math.sin(c.t * 3.0), c.t * 0.3, ZolPen.FULL, 1, 1, 1, headA * c.env);
            Random rnd = new Random(c.e.seed() * 17L + 5);
            for (int i = 0; i < 140; i++) {
                float d = rnd.nextFloat() * c.len;
                double ang = rnd.nextDouble() * Math.PI * 2, rad0 = BEAM_W * (0.55 + 0.3 * rnd.nextDouble());
                float ph = rnd.nextFloat() * 6.28f;
                double size = 0.08 + 0.1 * rnd.nextDouble();
                float age = c.t - tp(c, d);
                if (age < 0f || d > headD) continue;
                double rad = rad0 + 0.08 * age;
                float al = c.env * Mth.clamp(1f - age / 18f, 0f, 1f) * (0.55f + 0.45f * (float) Math.sin(c.t * 1.7 + ph));
                if (al <= 0.01f) continue;
                Vec3 pos = c.O.add(c.dir.scale(d)).add(c.bu.scale(Math.cos(ang) * rad)).add(c.bv.scale(Math.sin(ang) * rad));
                f.billboard(pos, size, rnd.nextDouble() * 6.28, ZolPen.FULL, 1, 1, 1, al);
            }
            drawPathLight(c, headD);
        }
    }

    /** Lights the ground along the beam's path (terrain-following strip) and round the caster. */
    private void drawPathLight(Ctx c, float headD) {
        Vec3 dh = new Vec3(c.dir.x, 0, c.dir.z);
        if (dh.lengthSqr() < 0.04) return;
        dh = dh.normalize();
        Vec3 side = new Vec3(-dh.z, 0, dh.x);
        double ox = c.O.x + c.base.x, oz = c.O.z + c.base.z;
        double hl = headD * Math.sqrt(c.dir.x * c.dir.x + c.dir.z * c.dir.z);
        int n = Mth.clamp((int) (hl / 3.0), 2, 30);
        ZolPen p = c.pen.use(vc(c.buf, CYAN));
        Vec3[] a = new Vec3[n + 1], b = new Vec3[n + 1];
        float[] al = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double d = hl * i / n;
            double x = ox + dh.x * d, z = oz + dh.z * d;
            double y = groundY(c.lvl, x, z, c.base.y, -8, 8) - c.base.y;
            a[i] = new Vec3(x - c.base.x - side.x * 8.0, y, z - c.base.z - side.z * 8.0);
            b[i] = new Vec3(x - c.base.x + side.x * 8.0, y, z - c.base.z + side.z * 8.0);
            al[i] = 0.35f * c.env * (float) Math.sqrt(ramp(c, (float) (hl > 0 ? headD * i / n : 0)));
        }
        for (int i = 0; i < n; i++) {
            float[] col = {1, 1, 1, al[i], 1, 1, 1, al[i], 1, 1, 1, al[i + 1], 1, 1, 1, al[i + 1]};
            p.quadG(new Vec3[]{a[i], b[i], b[i + 1], a[i + 1]}, new float[]{0, 0, 1, 0, 1, 1, 0, 1}, col);
        }
        final int gn = 10;
        double gr = 18.0;
        float[][] gg = sample(c, c.base.x, c.base.z, c.base.y, gr, gn, -3.0, 3.0);
        drawGrid(c, gg, gn, gr, c.base.x, c.base.z, gr, 0.0, GLOW, 0.6f * c.env, 0.04);
    }

    /** The sustained impact where the beam ends: a bright flare, flip-book fire, light on the ground and pulsing rings. */
    private void drawTerminal(Ctx c) {
        float a = c.t - c.tArr;
        if (a < 0f || c.t > c.tX + 4f) return;
        float envT = c.t < c.tX ? 1f : 1f - smooth((c.t - c.tX) / 4f);
        ZolPen f = c.pen.use(vc(c.buf, FLARE));
        f.billboard(c.Tl, 7.0 + 0.8 * Math.sin(c.t * 1.9), c.t * 0.2, ZolPen.FULL, 1, 1, 1, envT);
        c.pen.use(vc(c.buf, BURST)).billboard(c.Tl, 10.0, 0.4, ZolPen.frame4(6 + (int) (c.t * 0.7f) % 6), 1, 1, 1, 0.6f * envT);

        final int gn = 10;
        double gr = 16.0;
        float[][] gg = sample(c, c.Tw.x, c.Tw.z, c.impactY, gr, gn, -4.0, 4.0);
        drawGrid(c, gg, gn, gr, c.Tw.x, c.Tw.z, gr * smooth(a / 6f), 0.0, GLOW, 0.7f * envT, 0.04);

        // a pulse for every damage tick
        float ap = a % AncientReleaseSpell.INTERVAL_TICKS;
        double rad = 1.0 + 7.0 * ap / AncientReleaseSpell.INTERVAL_TICKS;
        drawRing(c, c.Tw.x, c.Tw.z, c.impactY, rad, 1.6, 0.7f * (1f - ap / AncientReleaseSpell.INTERVAL_TICKS) * envT, LINE);

        ZolPen sp = c.pen.use(vc(c.buf, SPARK));
        int pulse = (int) (a / 4f);
        float age = a % 4f;
        Random r = new Random(c.e.seed() * 31L + pulse * 7919L);
        for (int i = 0; i < 24; i++) {
            Vec3 v = new Vec3(r.nextGaussian(), Math.abs(r.nextGaussian()), r.nextGaussian()).normalize();
            double speed = 0.5 + r.nextDouble() * 1.0;
            double dist = speed * (1 - Math.pow(0.85, age)) / 0.15;
            Vec3 head = c.Tl.add(v.scale(dist));
            Vec3 tail = head.subtract(v.scale(0.4 + speed * 0.6));
            float al = (1f - age / 4f) * envT;
            sp.ribbon(head, tail, 0.12, 0.05, 0f, 1f, al, al * 0.4f, 1, 1, 1);
        }
    }

    // ---------------------------------------------------------------------------------------------- phase 4: final impact
    private void drawBlast(Ctx c) {
        float a = c.t - c.tX;
        if (a < 0f) return;
        Vec3 Tl = c.Tl;
        if (a < 45f) {
            ZolPen f = c.pen.use(vc(c.buf, FLARE));
            f.billboard(Tl, 20.0 * Math.exp(-a * 0.10) + 3.0, a * 0.1, ZolPen.FULL, 1, 1, 1, (float) Math.exp(-a * 0.12));
            f.billboard(Tl, 14.0 * Math.exp(-a * 0.15), 0.78 + a * 0.05, ZolPen.FULL, 1, 1, 1, (float) Math.exp(-a * 0.12));
        }
        if (a < 16f) c.pen.use(vc(c.buf, BURST)).billboard(Tl, 26.0, 0.5, ZolPen.frame4(Math.min(15, (int) a)), 1, 1, 1, 1);
        if (a < 18f) {
            float x = Math.min(1f, a / 16f);
            drawShell(c, Tl, AncientReleaseSpell.EXPLOSION_RADIUS * (1.0 - Math.pow(1.0 - x, 3)), 0.6f * (float) Math.pow(1.0 - x, 1.4), a);
        }
        // several circular shockwaves across the terrain
        for (int k = 0; k < 3; k++) {
            float x = (a - 3f * k) / 22f;
            if (x <= 0f || x >= 1.15f) continue;
            float xc = Math.min(x, 1f);
            double r = AncientReleaseSpell.EXPLOSION_RADIUS * 1.2 * (1.0 - Math.pow(1.0 - xc, 2.2));
            float al = (float) Math.pow(1 - xc, 0.7) * 0.95f * (1f - smooth((x - 1f) / 0.15f));
            drawRing(c, c.Tw.x, c.Tw.z, c.impactY, r, 2.6 + 1.5 * (1 - xc), al, LINE);
        }

        // a bright blue-white pillar of light rises into the sky and thins out
        if (a < 46f) {
            double gy = groundY(c.lvl, c.Tw.x, c.Tw.z, c.impactY, -6, 6);
            Vec3 bot = new Vec3(c.Tw.x - c.base.x, gy - c.base.y, c.Tw.z - c.base.z);
            float h = 150f * (float) (1.0 - Math.pow(1.0 - Math.min(1f, a / 5f), 3));
            float alpha = 1f - smooth((a - 10f) / 34f);
            float wCore = 7f * (1f - smooth((a - 8f) / 34f));
            float wCyan = 15f * (1f - smooth((a - 6f) / 36f));
            if (alpha > 0.003f) {
                Vec3 top = bot.add(0, h, 0);
                c.pen.use(vc(c.buf, CYAN)).ribbon(bot, top, wCyan, wCyan * 0.6, 0f, 1f, 0.45f * alpha, 0.12f * alpha, 1, 1, 1);
                c.pen.use(vc(c.buf, CORE)).ribbon(bot, top, wCore, wCore * 0.5, 0f, 1f, alpha, 0.35f * alpha, 1, 1, 1);
                c.pen.use(vc(c.buf, FLARE)).billboard(bot.add(0, 1.0, 0), 6.0 * alpha, a * 0.1, ZolPen.FULL, 1, 1, 1, alpha);
            }
        }

        // the ground stays lit, then the leftover motes slowly disappear
        if (a < 70f) {
            final int gn = 12;
            double gr = 24.0;
            float[][] gg = sample(c, c.Tw.x, c.Tw.z, c.impactY, gr, gn, -4.0, 4.0);
            drawGrid(c, gg, gn, gr, c.Tw.x, c.Tw.z, gr * smooth(a / 10f), 0.0, GLOW, 0.8f * (float) Math.exp(-a * 0.05), 0.04);
        }
        ZolPen f = c.pen.use(vc(c.buf, FLARE));
        for (int i = 0; i < 110; i++) {
            float dist = 2f + 13f * hash(i * 3 + 91);
            float ag = a - dist / BLAST_SPEED;
            if (ag < 0f || ag > 60f) continue;
            double ang = hash(i * 5 + 17) * Math.PI * 2;
            double x = c.Tw.x + Math.cos(ang) * dist, z = c.Tw.z + Math.sin(ang) * dist;
            double gy = groundY(c.lvl, x, z, c.impactY, -6, 6);
            double y = gy + ag * 0.22 * (0.5 + hash(i * 7 + 5));
            float al = (float) Math.sin(Math.PI * ag / 60f);
            f.billboard(new Vec3(x - c.base.x, y - c.base.y, z - c.base.z), 0.09 + 0.1 * hash(i * 9 + 3), ag * 0.1 + i, ZolPen.FULL, 1, 1, 1, al);
        }
        if (a < 16f) {
            ZolPen sp = c.pen.use(vc(c.buf, SPARK));
            Random r = new Random(c.e.seed() * 13L + 3);
            for (int i = 0; i < 40; i++) {
                Vec3 v = new Vec3(r.nextGaussian(), Math.abs(r.nextGaussian()) * 0.7, r.nextGaussian()).normalize();
                double speed = 0.8 + r.nextDouble() * 1.6;
                double dist = speed * (1 - Math.pow(0.86, a)) / 0.14;
                double vel = speed * Math.pow(0.86, a);
                Vec3 head = Tl.add(v.scale(1.0 + dist));
                Vec3 tail = head.subtract(v.scale(0.5 + vel * 1.3));
                float al = 1f - a / 16f;
                sp.ribbon(head, tail, 0.14, 0.06, 0f, 1f, al, al * 0.4f, 1, 1, 1);
            }
        }
    }
}
