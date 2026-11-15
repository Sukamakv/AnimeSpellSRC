package com.example.jjkspells.frieren.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.frieren.entity.HeightOfMagicEntity;
import com.example.jjkspells.frieren.spell.HeightOfMagicSpell;
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

import static com.example.jjkspells.frieren.entity.HeightOfMagicEntity.*;

/**
 * The Height of Magic, drawn from tickCount + partial tick.
 *
 * 1 Seal - a huge three-layer circle (blue-white linework, gold accents) lying on the TERRAIN: the heightmap is sampled per
 *   tile and clamped, so it follows slopes and never dives into caves or climbs cliffs. A soft glow brightens the ground round it.
 * 2 Star - a miniature star overhead: white core, cyan corona layers, gold rune rings, spiral streams rising from the seal and
 *   motes drawn into the centre.
 * 3 Beam - white core, two transparent cyan layers, two gold + two cyan helical strands, rune rings sliding along it.
 * 4 Impact - flash + flip-book burst, expanding shell, four terrain-following shockwave rings out to 50 blocks, light columns
 *   rising as the front passes, embers.
 * Camera: a slight zoom while casting and screen shake that peaks at release and impact (see {@link FrierenCamera}).
 */
public class HeightOfMagicRenderer extends EntityRenderer<HeightOfMagicEntity> {
    private static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/entity/frieren/" + n + ".png");
    }

    private static final ResourceLocation CIRCLE_A = tex("hom_circle_a");
    private static final ResourceLocation CIRCLE_B = tex("hom_circle_b");
    private static final ResourceLocation CIRCLE_C = tex("hom_circle_c");
    private static final ResourceLocation RING = tex("hom_ring");
    private static final ResourceLocation CORONA = tex("hom_corona");
    private static final ResourceLocation CYAN = tex("hom_beam_cyan");
    private static final ResourceLocation GOLD = tex("hom_beam_gold");
    private static final ResourceLocation GLOW = tex("hom_glow");
    private static final ResourceLocation SHELL = tex("hom_shell");
    private static final ResourceLocation FLARE = tex("zoltraak_flare");
    private static final ResourceLocation CORE = tex("zoltraak_beam");
    private static final ResourceLocation BURST = tex("zoltraak_burst");
    private static final ResourceLocation WAVE = tex("zoltraak_wave");
    private static final ResourceLocation LINE = tex("barrier_line");

    private static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final float[] GOLDTINT = {1f, 0.85f, 0.5f};
    private static final double SEAL_R = 8.0;

    public HeightOfMagicRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(HeightOfMagicEntity e) {
        return CIRCLE_A;
    }

    @Override
    public boolean shouldRender(HeightOfMagicEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    // ---------------------------------------------------------------------------------------------- state
    private static final class Ctx {
        Pen pen;
        MultiBufferSource buf;
        HeightOfMagicEntity e;
        ClientLevel lvl;
        float t, cf, len, tI, env = 1f;
        int rel;
        boolean released;
        Vec3 base, Tl, Tw, dir, bu, bv;
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

    private static VertexConsumer vc(MultiBufferSource buf, ResourceLocation t) {
        return buf.getBuffer(ZolRenderTypes.additive(t));
    }

    /** Ground height at (x,z), kept within [refY+lo, refY+hi] so caves and cliffs cannot throw the mesh around. */
    private static double groundY(ClientLevel lvl, double x, double z, double refY, double lo, double hi) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!lvl.hasChunkAt(new BlockPos(bx, Mth.floor(refY), bz))) return refY + 0.05;
        double h = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return Mth.clamp(h, refY + lo, refY + hi) + 0.05;
    }

    // ---------------------------------------------------------------------------------------------- render
    @Override
    public void render(HeightOfMagicEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
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
        if (c.released) {
            c.Tw = e.target();
            c.Tl = c.Tw.subtract(c.base);
            c.len = (float) c.Tl.length();
            c.dir = c.len > 1.0E-4f ? c.Tl.scale(1.0 / c.len) : new Vec3(0, -1, 0);
            Vec3 up0 = Math.abs(c.dir.y) > 0.97 ? X : Y;
            c.bu = c.dir.cross(up0).normalize();
            c.bv = c.dir.cross(c.bu).normalize();
            c.tI = c.rel + c.len / BEAM_SPEED;
            double gh = c.lvl.hasChunkAt(new BlockPos(Mth.floor(c.Tw.x), Mth.floor(c.Tw.y), Mth.floor(c.Tw.z)))
                    ? c.lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(c.Tw.x), Mth.floor(c.Tw.z)) : c.Tw.y;
            c.impactY = Math.max(Math.min(gh, c.Tw.y + 1.0), c.Tw.y - 40.0);
        }

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camLocal = cam.getPosition().subtract(c.base);
        Vector3f l = cam.getLeftVector();
        Vector3f u = cam.getUpVector();
        c.pen = new Pen(ps.last().pose(), camLocal, new Vec3(-l.x(), -l.y(), -l.z()), new Vec3(u.x(), u.y(), u.z()));

        feedCamera(c, cam.getPosition(), mc);
        if (c.cf <= 0.003f) return;

        drawGround(c);
        drawStar(c);
        if (c.released) {
            drawBeam(c);
            drawImpact(c);
        }
    }

    /** Slight zoom while casting (caster only) and shake: small while gathering, strong at release, strongest at impact. */
    private void feedCamera(Ctx c, Vec3 camWorld, Minecraft mc) {
        boolean mine = mc.player != null && mc.player.getId() == c.e.ownerEntityId();
        double dStar = camWorld.distanceTo(c.base);
        float amp = 0f;
        float nearStar = mine ? 1f : Mth.clamp(1f - (float) dStar / 60f, 0f, 1f);
        if (!c.released && c.cf > 0.5f && c.t > 40f) amp = 0.35f * smooth((c.t - 40f) / 40f) * (mine ? 1f : nearStar * 0.6f);
        if (c.released) {
            float k = c.t - c.rel;
            if (k >= 0f) amp += 1.6f * (float) Math.exp(-k / 6f) * nearStar;
            float k2 = c.t - c.tI;
            if (k2 >= 0f) {
                float dT = (float) camWorld.distanceTo(c.Tw);
                amp += 2.6f * Math.min(1f, k2 / 1.5f) * (float) Math.exp(-k2 / 14f) * Mth.clamp(1f - dT / 90f, 0f, 1f);
            }
        }
        if (amp > 0f) FrierenCamera.feedShake(amp);
        if (mine) {
            float z;
            if (c.e.cancelTick() >= 0) z = 1f - 0.07f * (1f - smooth((c.t - c.e.cancelTick()) / 10f));
            else if (c.released) z = 1f - 0.07f * (1f - smooth((c.t - c.rel) / 20f));
            else z = 1f - 0.07f * smooth(c.t / 60f);
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

    /** Draws a square texture as a terrain-following tile mesh, spun by rot around (cx,cz). */
    private static void drawGrid(Ctx c, float[][] g, int n, double Rg, double cx, double cz, double Rl, double rot, ResourceLocation tex, float alpha, double yOff) {
        if (alpha <= 0.003f || Rl <= 0.01) return;
        Pen p = c.pen.use(vc(c.buf, tex));
        double cs = Math.cos(rot), sn = Math.sin(rot);
        Vec3[][] v = new Vec3[n + 1][n + 1];
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                double a = (i / (double) n * 2 - 1) * Rl, b = (j / (double) n * 2 - 1) * Rl;
                double wx = cx + a * cs - b * sn, wz = cz + a * sn + b * cs;
                double y = gridY(g, n, Rg, wx - cx, wz - cz) + yOff;
                v[i][j] = new Vec3(wx - c.base.x, y - c.base.y, wz - c.base.z);
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

    private void drawGround(Ctx c) {
        float scale = smooth(c.t / 24f);
        if (scale <= 0.003f) return;
        float inA = smooth(c.t / 10f);
        float tEnd = c.released ? c.tI + WAVE_TICKS + 15f : 1.0E9f;
        float a = inA * (1f - smooth((c.t - tEnd) / 15f)) * c.cf;
        if (a <= 0.003f) return;
        double cx = c.base.x, cz = c.base.z, refY = c.base.y - STAR_H;

        final int n = 20;
        float[][] g = sample(c, cx, cz, refY, SEAL_R, n, -2.0, 2.0);
        // the layers appear one after another, each turning at its own pace
        drawGrid(c, g, n, SEAL_R, cx, cz, SEAL_R * scale, 0.02 * c.t, CIRCLE_A, a, 0.05);
        drawGrid(c, g, n, SEAL_R, cx, cz, SEAL_R * 0.92 * scale, -0.035 * c.t, CIRCLE_B, a * smooth((c.t - 8f) / 12f), 0.07);
        drawGrid(c, g, n, SEAL_R, cx, cz, SEAL_R * 0.55 * scale, 0.06 * c.t, CIRCLE_C, a * smooth((c.t - 16f) / 12f), 0.09);

        // the surroundings slowly light up as the star grows
        float lit = smooth(c.t / CAST);
        final int gn = 12;
        double gr = 24.0;
        float[][] gg = sample(c, cx, cz, refY, gr, gn, -3.0, 3.0);
        drawGrid(c, gg, gn, gr, cx, cz, gr * smooth(c.t / 40f), 0.0, GLOW, 0.45f * lit * a, 0.03);
    }

    // ---------------------------------------------------------------------------------------------- the star
    private static Vec3 streamPoint(int i, float u) {
        double phi = i * Math.PI * 2 / 10 + hash(i) * 0.6;
        double ang = phi + ((i & 1) == 0 ? 1 : -1) * 2.6 * u;
        double r = 7.0 * Math.pow(1 - u, 1.25);
        double y = -STAR_H * (1 - Math.pow(u, 1.4)) + 0.2 * (1 - u);
        return new Vec3(Math.cos(ang) * r, y, Math.sin(ang) * r);
    }

    private void drawStar(Ctx c) {
        float t = c.t;
        float grow = smooth((t - 26f) / 54f);
        float sr = 0.25f + 2.4f * grow;
        if (c.released) sr *= 1f + 0.7f * (float) Math.exp(-(t - c.rel) * 0.35);
        float starA = c.cf * smooth((t - 20f) / 6f) * (c.released ? 1f - smooth((t - c.rel - HOLD) / FADE) : 1f);
        if (starA > 0.003f) {
            float pulse = 1f + 0.03f * (float) Math.sin(t * 0.7f);
            Pen p = c.pen.use(vc(c.buf, CORONA));
            p.billboard(Vec3.ZERO, sr * 1.6 * pulse, 0.03 * t, Pen.FULL, 1, 1, 1, 0.85f * starA);
            p.billboard(Vec3.ZERO, sr * 2.5 * pulse, -0.02 * t, Pen.FULL, 1, 1, 1, 0.5f * starA);
            p.billboard(Vec3.ZERO, sr * 3.8 * pulse, 0.012 * t, Pen.FULL, 1, 1, 1, 0.28f * starA);

            Pen f = c.pen.use(vc(c.buf, FLARE));
            f.billboard(Vec3.ZERO, sr * 1.25 * pulse, 0.0, Pen.FULL, 1, 1, 1, starA);
            if (c.released) { // the flash as it lets go
                float k = t - c.rel;
                if (k >= 0f && k < 14f) f.billboard(Vec3.ZERO, sr * 3.0 * (float) Math.exp(-k * 0.25), t * 0.2, Pen.FULL, 1, 1, 1, (float) Math.exp(-k * 0.2) * starA);
            }

            if (grow > 0.02f) { // gold rune rings turning like a gyroscope
                Pen r = c.pen.use(vc(c.buf, RING));
                double[] tilt = {70, 50, 90}, spd = {0.03, -0.022, 0.017}, size = {2.7, 3.3, 4.0}, spin = {0.05, -0.04, 0.03};
                for (int j = 0; j < 3; j++) {
                    double th = Math.toRadians(tilt[j]), al = t * spd[j] + j * 2.1;
                    Vec3 n = new Vec3(Math.sin(th) * Math.cos(al), Math.cos(th), Math.sin(th) * Math.sin(al));
                    Vec3 uu = n.cross(Y).normalize();
                    Vec3 vv = n.cross(uu).normalize();
                    r.plane(Vec3.ZERO, uu, vv, sr * size[j], t * spin[j], Pen.FULL, 1, 1, 1, 0.8f * starA * grow);
                }
            }
        }

        // long streams spiralling from the seal up into the star
        float inten = smooth((t - 30f) / 12f) * (c.released ? 1f - smooth((t - c.rel) / 6f) : 1f) * c.cf;
        if (inten > 0.003f) {
            for (int par = 0; par < 2; par++) {
                Pen s = c.pen.use(vc(c.buf, par == 0 ? CYAN : GOLD));
                for (int i = par; i < 10; i += 2) {
                    float head = frac(t * 0.035f + hash(i * 3));
                    float u0 = Math.max(0f, head - 0.5f);
                    int seg = 14;
                    for (int m = 0; m < seg; m++) {
                        float ua = u0 + (head - u0) * m / seg, ub = u0 + (head - u0) * (m + 1) / seg;
                        float a0 = (float) Math.pow(m / (float) seg, 1.5) * inten, a1 = (float) Math.pow((m + 1) / (float) seg, 1.5) * inten;
                        s.ribbon(streamPoint(i, ua), streamPoint(i, ub), 0.32 * (1 - ua * 0.4), 0.32 * (1 - ub * 0.4), m * 0.2f, (m + 1) * 0.2f, a0, a1, 1, 1, 1);
                    }
                }
            }
            // motes being pulled into the centre
            Pen f = c.pen.use(vc(c.buf, FLARE));
            for (int i = 0; i < 90; i++) {
                float ph = frac(t * 0.02f + hash(i * 3 + 1));
                double ang = hash(i * 5 + 2) * Math.PI * 2 + ph * 3.0;
                double yy = hash(i * 7 + 3) * 2 - 1, rr = Math.sqrt(1 - yy * yy);
                double rad = 0.4 + 15.0 * Math.pow(1 - ph, 1.6);
                Vec3 pos = new Vec3(Math.cos(ang) * rr * rad, yy * rad * 0.8, Math.sin(ang) * rr * rad);
                float al = inten * (float) Math.pow(Math.sin(Math.PI * ph), 0.8);
                float[] tint = (i % 3 == 0) ? GOLDTINT : WHITE;
                f.billboard(pos, 0.10 + 0.06 * hash(i * 11), ph * 6, Pen.FULL, tint[0], tint[1], tint[2], al);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- the beam
    private static float ramp(Ctx c, float d) {
        return smooth((c.t - (c.rel + d / BEAM_SPEED)) / 0.3f);
    }

    private static double bw(Ctx c, float d) {
        return ramp(c, d) * (0.55f + 0.45f * smooth(d / 5f)) * (0.12f + 0.88f * c.env);
    }

    private static float ba(Ctx c, float d) {
        return (float) Math.sqrt(ramp(c, d)) * c.env;
    }

    private void beamLayer(Ctx c, ResourceLocation tex, double W, float aMul, float headD, int n) {
        Pen p = c.pen.use(vc(c.buf, tex));
        for (int i = 0; i < n; i++) {
            float d0 = headD * i / n, d1 = headD * (i + 1) / n;
            p.ribbon(c.dir.scale(d0), c.dir.scale(d1), bw(c, d0) * W, bw(c, d1) * W, d0 * 0.12f - c.t * 1.5f, d1 * 0.12f - c.t * 1.5f,
                    ba(c, d0) * aMul, ba(c, d1) * aMul, 1, 1, 1);
        }
    }

    private void drawBeam(Ctx c) {
        float k = c.t - c.rel;
        if (k < 0f) return;
        float headD = Mth.clamp(BEAM_SPEED * k, 0f, c.len);
        float tEnd = c.rel + c.len / BEAM_SPEED + HOLD;
        c.env = 1f - smooth((c.t - tEnd) / FADE);
        if (c.env <= 0.002f || headD <= 0.01f) return;
        int n = Mth.clamp((int) Math.ceil(headD / 1.5f), 1, 48);

        // an intensely bright core inside two transparent cyan layers
        beamLayer(c, CYAN, 8.2, 0.22f, headD, n);
        beamLayer(c, CYAN, 5.6, 0.55f, headD, n);
        beamLayer(c, CORE, 2.4, 1.0f, headD, n);

        // helical strands: two gold, two cyan
        for (int s = 0; s < 4; s++) {
            Pen p = c.pen.use(vc(c.buf, s < 2 ? GOLD : CYAN));
            double phase = s < 2 ? s * Math.PI : Math.PI / 2 + (s - 2) * Math.PI, radius = s < 2 ? 2.2 : 3.4;
            int m = Mth.clamp((int) Math.ceil(headD / 0.8f), 2, 90);
            Vec3 prev = null;
            float prevA = 0f;
            for (int i = 0; i <= m; i++) {
                float d = headD * i / m;
                double ang = d * 0.45 - c.t * 0.55 + phase;
                double rad = radius * (0.55 + 0.45 * smooth(d / 5f)) * (0.2 + 0.8 * c.env);
                Vec3 pos = c.dir.scale(d).add(c.bu.scale(Math.cos(ang) * rad)).add(c.bv.scale(Math.sin(ang) * rad));
                float a = ba(c, d) * 0.85f;
                if (prev != null) p.ribbon(prev, pos, 0.24, 0.24, i * 0.1f, (i + 1) * 0.1f, prevA, a, 1, 1, 1);
                prev = pos;
                prevA = a;
            }
        }

        // rune rings sliding down the beam, each turning its own way
        Pen r = c.pen.use(vc(c.buf, RING));
        double spacing = 7.0;
        int cnt = (int) (c.len / spacing) + 1;
        for (int j = 0; j < cnt; j++) {
            float d = (float) ((j * spacing + c.t * 9.0) % Math.max(1f, c.len));
            if (d > headD) continue;
            double half = 3.8 * (0.55 + 0.45 * smooth(d / 5f)) * (0.3 + 0.7 * c.env);
            double rot = c.t * 0.15 * ((j & 1) == 0 ? 1 : -1) + j;
            r.plane(c.dir.scale(d), c.bu, c.bv, half, rot, Pen.FULL, 1, 1, 1, 0.7f * c.env * ba(c, d) * (1f - 0.5f * d / c.len));
        }

        // head flare and glitter thrown off the beam
        Pen f = c.pen.use(vc(c.buf, FLARE));
        float headA = 1f - Mth.clamp(c.t - (c.rel + c.len / BEAM_SPEED), 0f, 1f);
        if (headA > 0.003f) f.billboard(c.dir.scale(headD), 3.6 + 0.3 * Math.sin(c.t * 3.0), c.t * 0.3, Pen.FULL, 1, 1, 1, headA * c.env);
        Random rnd = new Random(c.e.seed() * 17L + 5);
        for (int i = 0; i < 90; i++) {
            float d = rnd.nextFloat() * c.len;
            double ang = rnd.nextDouble() * Math.PI * 2, rad0 = 3.2 + 1.4 * rnd.nextDouble();
            float ph = rnd.nextFloat() * 6.28f;
            double size = 0.07 + 0.09 * rnd.nextDouble();
            float age = c.t - (c.rel + d / BEAM_SPEED);
            if (age < 0f || d > headD) continue;
            double rad = rad0 + 0.08 * age;
            float al = c.env * Mth.clamp(1f - age / 16f, 0f, 1f) * (0.55f + 0.45f * (float) Math.sin(c.t * 1.7 + ph));
            if (al <= 0.01f) continue;
            float[] tint = (i & 1) == 0 ? GOLDTINT : WHITE;
            Vec3 pos = c.dir.scale(d).add(c.bu.scale(Math.cos(ang) * rad)).add(c.bv.scale(Math.sin(ang) * rad));
            f.billboard(pos, size, rnd.nextDouble() * 6.28, Pen.FULL, tint[0], tint[1], tint[2], al);
        }
    }

    // ---------------------------------------------------------------------------------------------- the impact
    private void drawImpact(Ctx c) {
        float a = c.t - c.tI;
        if (a < 0f) return;
        Vec3 Tl = c.Tl;

        // 1 the impact point turns blindingly bright
        if (a < 45f) {
            Pen f = c.pen.use(vc(c.buf, FLARE));
            f.billboard(Tl, 16.0 * Math.exp(-a * 0.10) + 2.0, a * 0.1, Pen.FULL, 1, 1, 1, (float) Math.exp(-a * 0.14));
            f.billboard(Tl, 11.0 * Math.exp(-a * 0.15), 0.78 + a * 0.05, Pen.FULL, 1, 1, 1, (float) Math.exp(-a * 0.12));
        }
        if (a < 16f) {
            c.pen.use(vc(c.buf, BURST)).billboard(Tl, 18.0, 0.5, Pen.frame4(Math.min(15, (int) a)), 1, 1, 1, 1);
        }

        // 2 a magical explosion expands outward
        if (a < 16f) {
            float x = a / 14f;
            double rad = 22.0 * (1.0 - Math.pow(1.0 - Math.min(1f, x), 3));
            drawShell(c, Tl, rad, 0.5f * (float) Math.pow(1.0 - Math.min(1f, x), 1.5), a);
        }

        // 3 shockwave rings run across the terrain out to the full radius
        for (int k = 0; k < 4; k++) {
            float x = (a - 3f * k) / 26f;
            if (x <= 0f || x >= 1.15f) continue;
            float xc = Math.min(x, 1f);
            double r = HeightOfMagicSpell.EXPLOSION_RADIUS * (1.0 - Math.pow(1.0 - xc, 2.2));
            double w = 2.2 + 2.0 * (1 - xc);
            float al = (float) Math.pow(1 - xc, 0.7) * 0.95f * (1f - smooth((x - 1f) / 0.15f));
            drawRing(c, r, w, al, (k & 1) == 0 ? LINE : GOLD);
        }

        // 4 columns of blue-white light rise from the ground as the front passes, then 5 embers drift up and die
        drawColumns(c, a);
        drawEmbers(c, a);

        // the ground stays lit for a while
        if (a < 70f) {
            final int gn = 12;
            double gr = 26.0;
            float[][] gg = sample(c, c.Tw.x, c.Tw.z, c.impactY, gr, gn, -4.0, 4.0);
            drawGrid(c, gg, gn, gr, c.Tw.x, c.Tw.z, gr * smooth(a / 10f), 0.0, GLOW, 0.9f * (float) Math.exp(-a * 0.05), 0.04);
        }
    }

    private void drawShell(Ctx c, Vec3 center, double radius, float alpha, float a) {
        if (alpha <= 0.003f || radius <= 0.05) return;
        Pen p = c.pen.use(vc(c.buf, SHELL));
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

    /** One shockwave ring: a strip that follows the heightmap round the impact point (U across the strip). */
    private void drawRing(Ctx c, double r, double w, float alpha, ResourceLocation tex) {
        if (alpha <= 0.003f) return;
        Pen p = c.pen.use(vc(c.buf, tex));
        final int seg = 72;
        double in = Math.max(0.0, r - w * 0.5), out = r + w * 0.5;
        Vec3[] a = new Vec3[seg + 1], b = new Vec3[seg + 1];
        for (int m = 0; m <= seg; m++) {
            double th = Math.PI * 2 * m / seg, cs = Math.cos(th), sn = Math.sin(th);
            double x0 = c.Tw.x + cs * in, z0 = c.Tw.z + sn * in, x1 = c.Tw.x + cs * out, z1 = c.Tw.z + sn * out;
            a[m] = new Vec3(x0 - c.base.x, groundY(c.lvl, x0, z0, c.impactY, -5, 5) + 0.05 - c.base.y, z0 - c.base.z);
            b[m] = new Vec3(x1 - c.base.x, groundY(c.lvl, x1, z1, c.impactY, -5, 5) + 0.05 - c.base.y, z1 - c.base.z);
        }
        float[] col = {1, 1, 1, alpha, 1, 1, 1, alpha, 1, 1, 1, alpha, 1, 1, 1, alpha};
        for (int m = 0; m < seg; m++) {
            p.quadG(new Vec3[]{a[m], b[m], b[m + 1], a[m + 1]}, new float[]{0, 0, 1, 0, 1, 1, 0, 1}, col);
        }
    }

    private void drawColumns(Ctx c, float a) {
        Pen core = c.pen.use(vc(c.buf, CORE));
        for (int i = 0; i < 36; i++) {
            float dist = 2f + 44f * (float) Math.pow(hash(i * 5 + 1), 0.8);
            float ag = a - dist / WAVE_SPEED;
            if (ag < 0f || ag > 36f) continue;
            double ang = hash(i * 11 + 2) * Math.PI * 2;
            double x = c.Tw.x + Math.cos(ang) * dist, z = c.Tw.z + Math.sin(ang) * dist;
            double gy = groundY(c.lvl, x, z, c.impactY, -6, 6);
            float H = 22f + 26f * hash(i * 7 + 3), W = 1.0f + 1.6f * hash(i * 13 + 4);
            float h = H * (float) (1.0 - Math.pow(1.0 - Math.min(1f, ag / 5f), 3));
            float w = W * (1f - smooth((ag - 8f) / 28f));
            float al = 1f - smooth((ag - 6f) / 30f);
            if (al <= 0.003f || w <= 0.01f) continue;
            Vec3 bot = new Vec3(x - c.base.x, gy - c.base.y, z - c.base.z);
            Vec3 top = bot.add(0, h, 0);
            if ((i & 1) == 0) core.ribbon(bot, top, w, w * 0.55, 0f, 1f, 0.9f * al, 0.3f * al, 1, 1, 1);
        }
        Pen cy = c.pen.use(vc(c.buf, CYAN));
        for (int i = 0; i < 36; i++) {
            float dist = 2f + 44f * (float) Math.pow(hash(i * 5 + 1), 0.8);
            float ag = a - dist / WAVE_SPEED;
            if (ag < 0f || ag > 36f) continue;
            double ang = hash(i * 11 + 2) * Math.PI * 2;
            double x = c.Tw.x + Math.cos(ang) * dist, z = c.Tw.z + Math.sin(ang) * dist;
            double gy = groundY(c.lvl, x, z, c.impactY, -6, 6);
            float H = 22f + 26f * hash(i * 7 + 3), W = 1.0f + 1.6f * hash(i * 13 + 4);
            float h = H * (float) (1.0 - Math.pow(1.0 - Math.min(1f, ag / 5f), 3));
            float w = W * (1f - smooth((ag - 8f) / 28f));
            float al = 1f - smooth((ag - 6f) / 30f);
            if (al <= 0.003f || w <= 0.01f) continue;
            Vec3 bot = new Vec3(x - c.base.x, gy - c.base.y, z - c.base.z);
            Vec3 top = bot.add(0, h, 0);
            cy.ribbon(bot, top, w * 2.2, w * 1.2, 0f, 1f, 0.5f * al, 0.15f * al, 1, 1, 1);
        }
        Pen gd = c.pen.use(vc(c.buf, GOLD));
        for (int i = 0; i < 36; i += 3) {
            float dist = 2f + 44f * (float) Math.pow(hash(i * 5 + 1), 0.8);
            float ag = a - dist / WAVE_SPEED;
            if (ag < 0f || ag > 36f) continue;
            double ang = hash(i * 11 + 2) * Math.PI * 2;
            double x = c.Tw.x + Math.cos(ang) * dist, z = c.Tw.z + Math.sin(ang) * dist;
            double gy = groundY(c.lvl, x, z, c.impactY, -6, 6);
            float H = 22f + 26f * hash(i * 7 + 3), W = 1.0f + 1.6f * hash(i * 13 + 4);
            float h = H * 0.8f * (float) (1.0 - Math.pow(1.0 - Math.min(1f, ag / 5f), 3));
            float w = W * (1f - smooth((ag - 8f) / 28f));
            float al = 1f - smooth((ag - 6f) / 30f);
            if (al <= 0.003f || w <= 0.01f) continue;
            Vec3 bot = new Vec3(x - c.base.x, gy - c.base.y, z - c.base.z);
            gd.ribbon(bot, bot.add(0, h, 0), w * 3.2, w * 2.0, 0f, 1f, 0.4f * al, 0.1f * al, 1, 1, 1);
        }
        // a flat flash where each column is born
        Pen wv = c.pen.use(vc(c.buf, WAVE));
        for (int i = 0; i < 36; i++) {
            float dist = 2f + 44f * (float) Math.pow(hash(i * 5 + 1), 0.8);
            float ag = a - dist / WAVE_SPEED;
            if (ag < 0f || ag > 10f) continue;
            double ang = hash(i * 11 + 2) * Math.PI * 2;
            double x = c.Tw.x + Math.cos(ang) * dist, z = c.Tw.z + Math.sin(ang) * dist;
            double gy = groundY(c.lvl, x, z, c.impactY, -6, 6);
            float W = 1.0f + 1.6f * hash(i * 13 + 4);
            wv.plane(new Vec3(x - c.base.x, gy - c.base.y + 0.05, z - c.base.z), X, Z, W * (1.2 + ag * 0.35), 0.0, Pen.FULL, 1, 1, 1, 0.8f * (1f - ag / 10f));
        }
    }

    private void drawEmbers(Ctx c, float a) {
        Pen f = c.pen.use(vc(c.buf, FLARE));
        for (int i = 0; i < 100; i++) {
            float dist = 2f + 44f * hash(i * 3 + 91);
            float ag = a - dist / WAVE_SPEED;
            if (ag < 0f || ag > 45f) continue;
            double ang = hash(i * 5 + 17) * Math.PI * 2;
            double x = c.Tw.x + Math.cos(ang) * dist, z = c.Tw.z + Math.sin(ang) * dist;
            double gy = groundY(c.lvl, x, z, c.impactY, -6, 6);
            double y = gy + ag * 0.28 * (0.5 + hash(i * 7 + 5));
            float al = (float) Math.sin(Math.PI * ag / 45f);
            float[] tint = (i & 1) == 0 ? GOLDTINT : WHITE;
            f.billboard(new Vec3(x - c.base.x, y - c.base.y, z - c.base.z), 0.09 + 0.1 * hash(i * 9 + 3), ag * 0.1 + i, Pen.FULL, tint[0], tint[1], tint[2], al);
        }
    }
}
