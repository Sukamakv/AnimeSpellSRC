package com.example.jjkspells.yhwach.client;

import com.example.jjkspells.yhwach.entity.AlmightyEntity;
import com.example.jjkspells.yhwach.entity.AlmightyEntity.Fx;
import com.example.jjkspells.yhwach.spell.AlmightySpell;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Draws "The Almighty" as solid, alpha-blended black ink (not additive light): a ragged disc of ink that hugs the terrain,
 * tongues of dark flame rising from it, and eyes that are part of the ink (dark gray sclera sunk into black, amber slit pupils).
 * Eyed pillars fire black beams with thin red outlines, the zone is pulled into an orb, and the orb fires the giant beam.
 * Glow is kept minimal: thin red outlines and tiny pupil glints only.
 */
public class AlmightyRenderer extends EntityRenderer<AlmightyEntity> {
    private static final double R = AlmightySpell.ZONE_RADIUS;
    private static final int ZONE = AlmightyEntity.ZONE_TICKS;
    private static final int GATHER_END = AlmightyEntity.GATHER_END;
    private static final int BEAM_START = AlmightyEntity.BEAM_START;
    private static final int BEAM_END = AlmightyEntity.BEAM_END;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private static final class Eye {
        Vec3 c;
        Vec3 a;
        Vec3 b;
        double hw;
        double open;
        double px;
        double pupilScale = 1.0;
        float alpha;
    }

    private Pen p;
    private Level lvl;
    private float t;
    private float pt;
    private int seed;
    private Vec3 C;
    private Vec3 camPos;
    private Vec3 orb;
    private Vec3 dir;
    private double zoneIn;
    private double rCur;
    private double gather;
    private double orbR;
    private double beamK;
    private int tickNow;
    private final List<Eye> eyes = new ArrayList<>();
    private final List<double[]> glints = new ArrayList<>();
    private final List<Fx> pillars = new ArrayList<>();
    private final List<Fx> beams = new ArrayList<>();
    private final List<Fx> consumes = new ArrayList<>();

    public AlmightyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(AlmightyEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(AlmightyEntity e) {
        return YhwachRender.INK_TILE;
    }

    // ------------------------------------------------------------------ helpers

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void v(Vec3 w, float u, float vv, float a) {
        p.vc.vertex(p.m, (float) (w.x - p.org.x), (float) (w.y - p.org.y), (float) (w.z - p.org.z))
                .color(1f, 1f, 1f, Math.max(0f, Math.min(1f, a))).uv(u, vv).endVertex();
    }

    private void quad4(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] uv, float al) {
        if (al <= 0.004f) {
            return;
        }
        v(a, uv[0], uv[1], al);
        v(b, uv[2], uv[3], al);
        v(c, uv[4], uv[5], al);
        v(d, uv[6], uv[7], al);
    }

    private Vec3 camLeftH() {
        double lx = p.left.x();
        double lz = p.left.z();
        double l = Math.sqrt(lx * lx + lz * lz);
        return l < 1.0E-4 ? X : new Vec3(lx / l, 0, lz / l);
    }

    private double ground(double x, double z) {
        double y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        return Math.abs(y - C.y) > 9 ? C.y : y;
    }

    /** 0..1 how far a point at distance {@code rad} from the zone centre has been pulled into the orb. */
    private double pf(double rad) {
        if (gather <= 0) {
            return 0;
        }
        double delay = (1.0 - c01(rad / R)) * 0.5;
        double f = c01((gather - delay) / 0.5);
        return f * f;
    }

    /** Flame tongue standing on {@code base}. variant 0..3 picks the atlas cell. */
    private void wisp(Vec3 base, double w, double h, double sway, int variant, float a) {
        if (a <= 0.004f || w < 0.05) {
            return;
        }
        Vec3 L = camLeftH();
        float u0 = (variant & 1) * 0.5f;
        float v0 = ((variant >> 1) & 1) * 0.5f;
        Vec3 bl = base.add(L.scale(w * 0.5));
        Vec3 br = base.subtract(L.scale(w * 0.5));
        Vec3 top = base.add(0, h, 0).add(L.scale(sway));
        Vec3 tl = top.add(L.scale(w * 0.12));
        Vec3 tr = top.subtract(L.scale(w * 0.12));
        quad4(bl, br, tr, tl, new float[]{u0, v0 + 0.5f, u0 + 0.5f, v0 + 0.5f, u0 + 0.5f, v0, u0, v0}, a);
    }

    /** Flame tongue lying along {@code dirUnit}, growing from P (streaming smoke). */
    private void streamWisp(Vec3 P, Vec3 dirUnit, double len, double w, int variant, float a) {
        Vec3 mid = P.add(dirUnit.scale(len * 0.5));
        Vec3 side = dirUnit.cross(camPos.subtract(mid));
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize();
        float u0 = (variant & 1) * 0.5f;
        float v0 = ((variant >> 1) & 1) * 0.5f;
        Vec3 tip = P.add(dirUnit.scale(len));
        quad4(P.add(side.scale(w * 0.5)), P.subtract(side.scale(w * 0.5)), tip.subtract(side.scale(w * 0.06)), tip.add(side.scale(w * 0.06)),
                new float[]{u0, v0 + 0.5f, u0 + 0.5f, v0 + 0.5f, u0 + 0.5f, v0, u0, v0}, a);
    }

    private void inkRibbon(Vec3 a, Vec3 b, double wa, double wb, float al, float uA, float uB) {
        Vec3 d = b.subtract(a);
        Vec3 side = d.cross(camPos.subtract(a.add(b).scale(0.5)));
        if (side.lengthSqr() < 1.0E-10) {
            return;
        }
        inkRibbonSide(a, b, side.normalize(), wa, wb, al, uA, uB);
    }

    private void inkRibbonSide(Vec3 a, Vec3 b, Vec3 side, double wa, double wb, float al, float uA, float uB) {
        quad4(a.subtract(side.scale(wa * 0.5)), a.add(side.scale(wa * 0.5)), b.add(side.scale(wb * 0.5)), b.subtract(side.scale(wb * 0.5)),
                new float[]{uA, 0f, uA, 1f, uB, 1f, uB, 0f}, al);
    }

    private double blink(double phase, double spawnAge) {
        double len = 90 + phase * 80;
        double local = (t + phase * 100) % len;
        double o = local < 7 ? 1.0 - 0.95 * Math.sin(Math.PI * local / 7.0) : 1.0;
        return o * ease(spawnAge / 8.0);
    }

    private void addEye(Vec3 c, Vec3 a, Vec3 b, double hw, double open, double px, float alpha, double pupilScale) {
        if (alpha <= 0.01f || open <= 0.01 || hw < 0.05) {
            return;
        }
        Eye e = new Eye();
        e.c = c;
        e.a = a;
        e.b = b;
        e.hw = hw;
        e.open = open;
        e.px = px;
        e.alpha = alpha;
        e.pupilScale = pupilScale;
        eyes.add(e);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(AlmightyEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        pt = partial;
        tickNow = e.tickCount;
        t = e.tickCount - 1 + partial;
        seed = e.getId();
        lvl = e.level();
        C = e.getPosition(partial);
        dir = e.beamDir(partial);
        orb = e.ownerEye(partial).add(0, -0.5, 0).add(dir.scale(2.8));
        zoneIn = ease(t / 20.0);
        gather = c01((t - ZONE) / (double) (GATHER_END - ZONE));
        rCur = R * zoneIn * (1.0 - ease(gather));
        double orbGrow = c01(gather * 3.0) * (0.5 + 1.5 * c01((t - ZONE) / (double) (BEAM_START - ZONE)));
        if (t >= BEAM_START) {
            orbGrow = 2.0 + 0.25 * Math.sin(t * 0.6);
        }
        orbR = orbGrow * (1.0 - c01((t - BEAM_END) / 20.0));
        beamK = ease((t - BEAM_START) / 8.0) * (1.0 - ease((t - (BEAM_END - 6)) / 16.0));

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        camPos = camera.getPosition();
        p = new Pen(ps.last().pose(), e.getPosition(partial), camPos, camera.getLeftVector(), camera.getUpVector());

        eyes.clear();
        glints.clear();
        pillars.clear();
        beams.clear();
        consumes.clear();
        for (Fx f : e.clientFx) {
            (f.type == 0 ? pillars : f.type == 1 ? beams : consumes).add(f);
        }
        buildGroundEyes();

        p.vc = buf.getBuffer(YhwachRender.ink(YhwachRender.INK_TILE));
        groundTiles();
        p.vc = buf.getBuffer(YhwachRender.ink(YhwachRender.INK_FLOW));
        flowPass();
        p.vc = buf.getBuffer(YhwachRender.ink(YhwachRender.INK_WISP));
        wispPass();
        p.vc = buf.getBuffer(YhwachRender.ink(YhwachRender.EYE_BODY));
        eyeBodies();
        p.vc = buf.getBuffer(YhwachRender.ink(YhwachRender.EYE_PUPIL));
        eyePupils();
        p.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.STREAK));
        redOutlines();
        p.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.GLOW));
        glintPass();
        p.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.RING));
        ringPass();
    }

    // ------------------------------------------------------------------ ground ink

    private double edgeNoise(double ang) {
        return 0.55 * Math.sin(3 * ang + t * 0.03) + 0.3 * Math.sin(7 * ang - t * 0.05) + 0.15 * Math.sin(13 * ang + t * 0.07);
    }

    private float edgeAlpha(double x, double z) {
        double dx = x - C.x;
        double dz = z - C.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        double rn = rCur * (0.90 + 0.10 * edgeNoise(Math.atan2(dz, dx)));
        return (float) (c01((rn - d) / 5.0) * 0.97 * c01(t / 6.0));
    }

    private void groundTiles() {
        if (rCur < 0.5) {
            return;
        }
        int step = 2;
        int span = (int) Math.ceil(R / step) + 2;
        int gx0 = Math.floorDiv((int) Math.floor(C.x), step) * step;
        int gz0 = Math.floorDiv((int) Math.floor(C.z), step) * step;
        int n = 2 * span + 2;
        double[][] h = new double[n][n];
        float[][] al = new float[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double x = gx0 + (i - span) * step;
                double z = gz0 + (j - span) * step;
                al[i][j] = edgeAlpha(x, z);
                h[i][j] = al[i][j] > 0.002f ? ground(x, z) + 0.1 : 0;
            }
        }
        double su = t * 0.003;
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1; j++) {
                float a00 = al[i][j];
                float a10 = al[i + 1][j];
                float a11 = al[i + 1][j + 1];
                float a01 = al[i][j + 1];
                if (a00 + a10 + a11 + a01 < 0.004f) {
                    continue;
                }
                double x0 = gx0 + (i - span) * step;
                double z0 = gz0 + (j - span) * step;
                double x1 = x0 + step;
                double z1 = z0 + step;
                float u0 = (float) (x0 / 16.0 + su);
                float u1 = (float) (x1 / 16.0 + su);
                float v0 = (float) (z0 / 16.0 - su * 0.7);
                float v1 = (float) (z1 / 16.0 - su * 0.7);
                v(new Vec3(x0, h[i][j], z0), u0, v0, a00);
                v(new Vec3(x1, h[i + 1][j], z0), u1, v0, a10);
                v(new Vec3(x1, h[i + 1][j + 1], z1), u1, v1, a11);
                v(new Vec3(x0, h[i][j + 1], z1), u0, v1, a01);
            }
        }
    }

    // ------------------------------------------------------------------ eyes in the ground

    private void buildGroundEyes() {
        if (rCur < 1.0) {
            return;
        }
        Random r = new Random(seed * 211L + 5);
        Vec3 right = camLeftHSafe().scale(-1);
        for (int i = 0; i < 38; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = Math.sqrt(r.nextDouble()) * (R - 3.0);
            double hw = 1.3 + r.nextDouble() * 2.2;
            double rot = r.nextDouble() * Math.PI * 2;
            double phase = r.nextDouble();
            if (rad > rCur - 3.0) {
                continue;
            }
            double x = C.x + Math.cos(ang) * rad;
            double z = C.z + Math.sin(ang) * rad;
            double f = pf(rad);
            if (f > 0.97) {
                continue;
            }
            Vec3 c = new Vec3(x, ground(x, z) + 0.2, z).lerp(orb, f);
            Vec3 A = new Vec3(Math.cos(rot), 0, Math.sin(rot));
            Vec3 B = new Vec3(-Math.sin(rot), 0, Math.cos(rot));
            float alpha = (float) (c01((rCur - rad - 2.0) / 5.0) * (1.0 - f * 0.7));
            double spawn = t - 6.0 - rad * 0.4;
            addEye(c, A, B, hwScale(hw, f), blink(phase, spawn), 0.12 * Math.sin(t * 0.03 + i), alpha, 1.0);
        }
    }

    private Vec3 camLeftHSafe() {
        return p == null ? X : camLeftH();
    }

    private static double hwScale(double hw, double f) {
        return hw * (1.0 - 0.85 * f);
    }

    // ------------------------------------------------------------------ INK_FLOW: pillar bodies, black beams, tendrils, giant beam

    private void flowPass() {
        // pillars of darkness
        for (Fx f : pillars) {
            double age = tickNow - f.born + pt;
            if (age >= AlmightyEntity.PILLAR_LIFE) {
                continue;
            }
            double rise = ease(age / 10.0);
            double sink = 1.0 - ease((age - 38.0) / 10.0);
            double H = AlmightyEntity.PILLAR_H * rise * sink;
            if (H < 0.2) {
                continue;
            }
            Vec3 b = f.pos;
            Vec3 top = b.add(0, H, 0);
            double spin = t * 0.02 + f.id;
            for (int s = 0; s < 3; s++) {
                double ang = spin + s * Math.PI / 3;
                Vec3 side = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                float u0 = (float) (-t * 0.04 + s * 0.3);
                inkRibbonSide(b, top, side, 8.6, 6.0, 0.97f, u0, u0 + (float) (H / 9.0));
            }
        }
        // pillar lasers: solid black core
        for (Fx f : beams) {
            double age = tickNow - f.born + pt;
            double k = 1.0 - age / AlmightyEntity.BEAM_LIFE;
            if (k <= 0) {
                continue;
            }
            float u0 = (float) (-t * 0.5);
            inkRibbon(f.pos, f.pos2, 4.6 * k + 0.4, 4.6 * k + 0.4, 0.8f, u0, u0 + (float) (f.pos.distanceTo(f.pos2) / 12.0));
            inkRibbon(f.pos, f.pos2, 3.0 * k + 0.2, 3.0 * k + 0.2, 1.0f, u0, u0 + (float) (f.pos.distanceTo(f.pos2) / 12.0));
        }
        // darkness flowing into the orb
        if (gather > 0 && t < BEAM_START + 4) {
            Random r = new Random(seed * 67L);
            double fade = 1.0 - c01((t - (BEAM_START - 8)) / 12.0);
            for (int i = 0; i < 18; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = 6.0 + r.nextDouble() * (R - 8.0);
                double dl = r.nextDouble() * 0.35;
                double prog = c01((gather - dl) / 0.65 + (t > GATHER_END ? 0.3 * Math.sin(t * 0.2 + i) : 0));
                if (prog <= 0.02 || t > GATHER_END && prog >= 1.0) {
                    continue;
                }
                Vec3 s0 = new Vec3(C.x + Math.cos(ang) * rad, C.y + 0.3, C.z + Math.sin(ang) * rad);
                Vec3 mid = s0.add(orb).scale(0.5).add(0, 4.0 + r.nextDouble() * 4.0, 0);
                Vec3 prev = null;
                int S = 12;
                for (int j = 0; j <= S; j++) {
                    double s = j / (double) S;
                    if (s > prog) {
                        break;
                    }
                    double q = 1 - s;
                    Vec3 pos = s0.scale(q * q).add(mid.scale(2 * q * s)).add(orb.scale(s * s));
                    if (prev != null) {
                        float u0 = (float) (j * 0.3 - t * 0.1);
                        inkRibbon(prev, pos, 2.6 - 1.6 * s, 2.6 - 1.6 * s, (float) (0.9 * fade), u0, u0 + 0.3f);
                    }
                    prev = pos;
                }
            }
        }
        // the giant beam
        if (beamK > 0.01) {
            Vec3 D = dir;
            double Lb = AlmightySpell.BEAM_LENGTH * ease((t - BEAM_START) / 5.0);
            int N = 8;
            double Rb = AlmightySpell.BEAM_RADIUS * beamK;
            for (int s = 0; s < N; s++) {
                Vec3 a = orb.add(D.scale(Lb * s / N));
                Vec3 b = orb.add(D.scale(Lb * (s + 1) / N));
                float fa = s < N - 2 ? 1f : (s == N - 2 ? 0.7f : 0.4f);
                float u0 = (float) (Lb * s / N / 22.0 - t * 0.4);
                float u1 = (float) (Lb * (s + 1) / N / 22.0 - t * 0.4);
                double wob = 1.0 + 0.04 * Math.sin(t * 0.5 + s);
                inkRibbon(a, b, 2 * Rb * 1.3 * wob / 0.66, 2 * Rb * 1.3 * wob / 0.66, 0.8f * fa, u0, u1);
                inkRibbon(a, b, 2 * Rb * wob / 0.66, 2 * Rb * wob / 0.66, 1.0f * fa, u0 * 1.3f, u1 * 1.3f);
                inkRibbon(a, b, 2 * Rb * 0.6 / 0.66, 2 * Rb * 0.6 / 0.66, 1.0f * fa, -u0, -u1);
            }
        }
    }

    // ------------------------------------------------------------------ INK_WISP: dark flame

    private void wispPass() {
        // 1. flame rising from the ground disc
        if (rCur > 1.0) {
            Random r = new Random(seed * 977L + 3);
            for (int i = 0; i < 280; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = Math.sqrt(r.nextDouble()) * R;
                double w = 3.5 + r.nextDouble() * 4.5;
                double h = 4.0 + r.nextDouble() * 6.0 + (rad / R > 0.8 ? r.nextDouble() * 5.0 : 0.0);
                double ph = r.nextDouble() * 6.28;
                int var = r.nextInt(4);
                boolean hasEye = i % 5 == 0;
                double eyePhase = r.nextDouble();
                if (rad > rCur - 1.0) {
                    continue;
                }
                double f = pf(rad);
                if (f > 0.97) {
                    continue;
                }
                double x = C.x + Math.cos(ang) * rad;
                double z = C.z + Math.sin(ang) * rad;
                double breathe = 0.85 + 0.15 * Math.sin(t * 0.1 + ph);
                double hh = h * breathe * (1.0 - 0.8 * f);
                double ww = w * (1.0 - 0.7 * f);
                double sway = Math.sin(t * 0.07 + ph) * 1.1 + Math.sin(t * 0.17 + ph * 2) * 0.4;
                Vec3 base = new Vec3(x, ground(x, z) - 0.2, z).lerp(orb, f);
                float a = (float) (0.95 * c01((rCur - rad) / 4.0) * (1.0 - 0.6 * f) * c01(t / 8.0));
                wisp(base, ww, hh, sway, var, a);
                if (hasEye && f < 0.5) {
                    Vec3 right = camLeftH().scale(-1);
                    addEye(base.add(0, hh * 0.42, 0), right, Y, ww * 0.26, blink(eyePhase, t - rad * 0.5), 0.10 * Math.sin(t * 0.04 + i), a, 1.0);
                }
            }
        }
        // 2. the darkness cloaking the caster
        double cl = 1.0 - c01(gather * 2.0);
        if (cl > 0.01 && t < GATHER_END) {
            Random r = new Random(seed * 431L + 1);
            for (int i = 0; i < 26; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = 0.4 + r.nextDouble() * 1.4;
                double w = 1.4 + r.nextDouble() * 1.6;
                double h = 3.0 + r.nextDouble() * 3.5;
                double ph = r.nextDouble() * 6.28;
                double sway = Math.sin(t * 0.12 + ph) * 0.9;
                Vec3 base = C.add(Math.cos(ang) * rad, -0.1, Math.sin(ang) * rad);
                wisp(base, w, h * (0.85 + 0.15 * Math.sin(t * 0.14 + ph)), sway, r.nextInt(4), (float) (0.95 * zoneIn * cl));
            }
        }
        // 3. pillars: flame collar and wrapped flame
        for (Fx f : pillars) {
            double age = tickNow - f.born + pt;
            if (age >= AlmightyEntity.PILLAR_LIFE) {
                continue;
            }
            double rise = ease(age / 10.0);
            double sink = 1.0 - ease((age - 38.0) / 10.0);
            double H = AlmightyEntity.PILLAR_H * rise * sink;
            if (H < 0.2) {
                continue;
            }
            Vec3 b = f.pos;
            for (int k = 0; k < 8; k++) {
                double ang = k * Math.PI / 4 + f.id;
                double sway = Math.sin(t * 0.15 + k + f.id) * 0.8;
                wisp(b.add(Math.cos(ang) * 2.4, -0.2, Math.sin(ang) * 2.4), 3.4, 6.5 * rise * sink, sway, (k + f.id) & 3, 0.95f);
            }
            for (int k = 0; k < 6; k++) {
                double y = 1.0 + k * (H / 6.0);
                double ang = t * 0.05 + k * 1.1 + f.id;
                Vec3 pos = b.add(Math.cos(ang) * 1.3, y, Math.sin(ang) * 1.3);
                wisp(pos, 3.8, 5.0, Math.sin(t * 0.2 + k) * 0.8, (k + f.id) & 3, 0.9f);
            }
            // the eye crowning the pillar
            double open = ease((age - 9.0) / 6.0) * (1.0 - ease((age - 40.0) / 6.0));
            double flashP = age > 11 && age < 24 ? 1.0 + 0.5 * Math.sin((age - 11) * 0.9) : 1.0;
            Vec3 right = camLeftH().scale(-1);
            Vec3 eyeC = b.add(0, H + 1.8 + 0.3 * Math.sin(t * 0.1 + f.id), 0);
            addEye(eyeC, right, Y, 3.4, open, 0.0, 1f, flashP);
        }
        // 4. beam impacts: a puff of ink where a black laser lands
        for (Fx f : beams) {
            double age = tickNow - f.born + pt;
            double k = 1.0 - age / AlmightyEntity.BEAM_LIFE;
            if (k <= 0) {
                continue;
            }
            for (int i = 0; i < 5; i++) {
                double ang = i * 1.256 + f.id;
                wisp(f.pos2.add(Math.cos(ang) * 0.8, -0.8, Math.sin(ang) * 0.8), 3.6 * (1.2 - k * 0.5), 5.0 * (1.2 - k * 0.5), Math.sin(ang) * 1.2, (i + f.id) & 3, (float) (0.95 * k));
            }
        }
        // 5. devoured corpses: a tongue of ink rises over the body, grows an eye and sinks back
        for (Fx f : consumes) {
            double age = tickNow - f.born + pt;
            if (age >= AlmightyEntity.CONSUME_LIFE) {
                continue;
            }
            double up = ease(age / 16.0);
            double down = 1.0 - ease((age - 38.0) / 16.0);
            double H = (f.aux / 10.0 + 6.0) * up * down;
            for (int k = 0; k < 5; k++) {
                double ang = k * 1.256 + f.id;
                Vec3 base = f.pos.add(Math.cos(ang) * 0.9, -0.3, Math.sin(ang) * 0.9);
                wisp(base, 3.4, 4.0 + H, Math.sin(t * 0.2 + k) * 1.0, (k + f.id) & 3, 0.97f);
            }
            wisp(f.pos.add(0, -0.3, 0), 4.6, 6.0 + H * 1.4, Math.sin(t * 0.15) * 1.2, f.id & 3, 0.97f);
            Vec3 right = camLeftH().scale(-1);
            addEye(f.pos.add(0, H * 0.7 + 1.5, 0), right, Y, 1.9, ease((age - 10.0) / 6.0) * down, 0.0, 1f, 1.0);
        }
        // 6. the orb: a ball of dark flame with eyes sunk into it
        if (orbR > 0.05 && t >= ZONE) {
            double Rr = orbR;
            Vec3 L = new Vec3(p.left.x(), p.left.y(), p.left.z());
            Vec3 U = new Vec3(p.up.x(), p.up.y(), p.up.z());
            for (int i = 0; i < 30; i++) {
                double a = i * Math.PI * 2 / 30 + t * 0.05 * ((i & 1) == 0 ? 1 : -1);
                double len = Rr * (1.3 + 0.55 * Math.sin(t * 0.22 + i * 1.7));
                Vec3 dv = L.scale(Math.cos(a)).add(U.scale(Math.sin(a)));
                Vec3 sv = L.scale(-Math.sin(a)).add(U.scale(Math.cos(a)));
                float u0 = (i & 1) * 0.5f;
                float v0 = ((i >> 1) & 1) * 0.5f;
                Vec3 tip = orb.add(dv.scale(len));
                quad4(orb.add(sv.scale(Rr * 0.5)), orb.subtract(sv.scale(Rr * 0.5)), tip.subtract(sv.scale(Rr * 0.07)), tip.add(sv.scale(Rr * 0.07)),
                        new float[]{u0, v0 + 0.5f, u0 + 0.5f, v0 + 0.5f, u0 + 0.5f, v0, u0, v0}, 1.0f);
            }
            Vec3 right = camLeftH().scale(-1);
            for (int i = 0; i < 6; i++) {
                double a = i * 1.047 + t * 0.01;
                Vec3 pos = orb.add(L.scale(Math.cos(a) * Rr * 0.5)).add(U.scale(Math.sin(a) * Rr * 0.5));
                addEye(pos, right, Y, Rr * 0.38, blink(i * 0.17, t), 0.1 * Math.sin(t * 0.05 + i), 1f, 1.0);
            }
        }
        // 7. giant beam: ragged smoke streaming away from the muzzle, with eyes embedded in the beam itself
        if (beamK > 0.01) {
            Vec3 D = dir;
            double Lb = AlmightySpell.BEAM_LENGTH * ease((t - BEAM_START) / 5.0);
            double Rb = AlmightySpell.BEAM_RADIUS * beamK;
            Random r = new Random(seed * 53L);
            for (int i = 0; i < 80; i++) {
                double s0 = r.nextDouble();
                double speed = 0.6 + r.nextDouble() * 0.8;
                double s = ((s0 + t * 0.012 * speed) % 1.0);
                double lat = (r.nextDouble() * 2 - 1) * 0.95;
                double len = 9.0 + r.nextDouble() * 9.0;
                double w = 3.0 + r.nextDouble() * 3.5;
                Vec3 P = orb.add(D.scale(s * Lb));
                Vec3 side = D.cross(camPos.subtract(P));
                if (side.lengthSqr() < 1.0E-8) {
                    continue;
                }
                P = P.add(side.normalize().scale(lat * Rb));
                float a = (float) (Math.sin(Math.PI * s) * 0.95 * (s > 0.85 ? (1.0 - s) / 0.15 : 1.0));
                streamWisp(P, D, len, w * beamK, r.nextInt(4), a);
            }
            Vec3 right = camLeftH().scale(-1);
            for (int i = 0; i < 14; i++) {
                double s = ((i / 14.0) + t * 0.004) % 1.0;
                if (s < 0.04 || s > 0.9) {
                    continue;
                }
                Vec3 P = orb.add(D.scale(s * Lb));
                Vec3 side = D.cross(camPos.subtract(P));
                if (side.lengthSqr() < 1.0E-8) {
                    continue;
                }
                double lat = ((i * 0.618) % 1.0 * 2 - 1) * 0.45;
                P = P.add(side.normalize().scale(lat * Rb));
                addEye(P, right, Y, 2.3 * beamK, blink(i * 0.13, t), 0.1 * Math.sin(t * 0.04 + i), (float) beamK, 1.0);
            }
        }
    }

    // ------------------------------------------------------------------ eyes

    private void eyeBodies() {
        for (Eye e : eyes) {
            double hh = e.hw * 0.5 * Math.max(0.06, e.open);
            Vec3 tl = e.c.subtract(e.a.scale(e.hw)).add(e.b.scale(hh));
            Vec3 tr = e.c.add(e.a.scale(e.hw)).add(e.b.scale(hh));
            Vec3 br = e.c.add(e.a.scale(e.hw)).subtract(e.b.scale(hh));
            Vec3 bl = e.c.subtract(e.a.scale(e.hw)).subtract(e.b.scale(hh));
            quad4(tl, tr, br, bl, new float[]{0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f}, e.alpha);
        }
    }

    private void eyePupils() {
        for (Eye e : eyes) {
            if (e.open < 0.15) {
                continue;
            }
            double ph = e.hw * 0.27 * e.open * e.pupilScale;
            double pw = ph * 0.5;
            Vec3 c = e.c.add(e.a.scale(e.px * e.hw));
            Vec3 tl = c.subtract(e.a.scale(pw)).add(e.b.scale(ph));
            Vec3 tr = c.add(e.a.scale(pw)).add(e.b.scale(ph));
            Vec3 br = c.add(e.a.scale(pw)).subtract(e.b.scale(ph));
            Vec3 bl = c.subtract(e.a.scale(pw)).subtract(e.b.scale(ph));
            quad4(tl, tr, br, bl, new float[]{0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f}, e.alpha);
            glints.add(new double[]{c.x, c.y, c.z, ph * 1.1, 0.22 * e.alpha * Math.min(1.0, e.open) * (e.pupilScale > 1.05 ? 1.6 : 1.0)});
        }
    }

    // ------------------------------------------------------------------ thin red outlines (additive, subdued)

    private void redOutlines() {
        for (Fx f : beams) {
            double age = tickNow - f.born + pt;
            double k = 1.0 - age / AlmightyEntity.BEAM_LIFE;
            if (k <= 0) {
                continue;
            }
            Vec3 mid = f.pos.add(f.pos2).scale(0.5);
            Vec3 side = f.pos2.subtract(f.pos).cross(camPos.subtract(mid));
            if (side.lengthSqr() < 1.0E-8) {
                continue;
            }
            side = side.normalize();
            double off = 1.15 * k + 0.1;
            for (int s = -1; s <= 1; s += 2) {
                Vec3 o = side.scale(off * s);
                p.ribbon(f.pos.add(o), f.pos2.add(o), 0.2, (float) (0.8 * k), 0f, 8f, Pen.RED);
                p.ribbon(f.pos.add(o.scale(0.7)), f.pos2.add(o.scale(0.7)), 0.12, (float) (0.4 * k), 0f, 8f, Pen.CRIM);
            }
        }
        if (beamK > 0.01) {
            Vec3 D = dir;
            double Lb = AlmightySpell.BEAM_LENGTH * ease((t - BEAM_START) / 5.0);
            double Rb = AlmightySpell.BEAM_RADIUS * beamK;
            int N = 8;
            for (int s = 0; s < N; s++) {
                Vec3 a = orb.add(D.scale(Lb * s / N));
                Vec3 b = orb.add(D.scale(Lb * (s + 1) / N));
                Vec3 mid = a.add(b).scale(0.5);
                Vec3 side = D.cross(camPos.subtract(mid));
                if (side.lengthSqr() < 1.0E-8) {
                    continue;
                }
                side = side.normalize();
                float fa = s < N - 2 ? 1f : (s == N - 2 ? 0.6f : 0.3f);
                for (int sg = -1; sg <= 1; sg += 2) {
                    Vec3 o = side.scale(Rb * sg);
                    p.ribbon(a.add(o), b.add(o), 0.5, 0.85f * fa, 0f, 3f, Pen.RED);
                    Vec3 o2 = side.scale(Rb * 0.84 * sg);
                    p.ribbon(a.add(o2), b.add(o2), 0.3, 0.5f * fa, 0f, 3f, Pen.CRIM);
                    Vec3 o3 = side.scale(Rb * 0.68 * sg);
                    p.ribbon(a.add(o3), b.add(o3), 0.18, 0.25f * fa, 0f, 3f, Pen.CRIM);
                }
            }
        }
        if (gather > 0 && t < BEAM_START + 4) {
            Random r = new Random(seed * 67L);
            double fade = 1.0 - c01((t - (BEAM_START - 8)) / 12.0);
            for (int i = 0; i < 18; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = 6.0 + r.nextDouble() * (R - 8.0);
                double dl = r.nextDouble() * 0.35;
                double prog = c01((gather - dl) / 0.65);
                if (prog <= 0.05 || prog >= 1.0) {
                    continue;
                }
                Vec3 s0 = new Vec3(C.x + Math.cos(ang) * rad, C.y + 0.3, C.z + Math.sin(ang) * rad);
                Vec3 q = s0.lerp(orb, prog);
                Vec3 q2 = s0.lerp(orb, Math.max(0, prog - 0.15));
                p.ribbon(q2, q, 0.2, (float) (0.5 * fade), 0f, 1f, Pen.RED);
            }
        }
    }

    // ------------------------------------------------------------------ tiny glints on the pupils

    private void glintPass() {
        for (double[] g : glints) {
            p.billboard(new Vec3(g[0], g[1], g[2]), g[3], 0, new float[]{1.0f, 0.78f, 0.18f}, (float) g[4]);
        }
        if (orbR > 0.2 && t >= ZONE) {
            p.billboard(orb, orbR * 1.6, 0, Pen.CRIM, (float) (0.35 * Math.min(1.0, orbR)));
        }
    }

    // ------------------------------------------------------------------ rings

    private void ringPass() {
        if (t < 24) {
            double k = t / 24.0;
            p.plane(C.add(0, 0.25, 0), X, Z, R * ease(k), 0, Pen.RED, (float) (0.5 * (1.0 - k)));
        }
        for (Fx f : beams) {
            double age = tickNow - f.born + pt;
            double k = age / AlmightyEntity.BEAM_LIFE;
            if (k >= 1) {
                continue;
            }
            p.plane(f.pos2.add(0, 0.15, 0), X, Z, 1.0 + 4.0 * ease(k), 0, Pen.RED, (float) (0.55 * (1.0 - k)));
        }
        if (orbR > 0.2 && t >= ZONE) {
            Vec3 L = new Vec3(p.left.x(), p.left.y(), p.left.z());
            Vec3 U = new Vec3(p.up.x(), p.up.y(), p.up.z());
            p.plane(orb, L, U, orbR * 1.75, t * 0.02, Pen.RED, (float) (0.5 * Math.min(1.0, orbR)));
        }
    }
}
