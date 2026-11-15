package com.example.jjkspells.prime.fx.client;

import com.example.jjkspells.prime.fx.PrimeFxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every PRIME Sukuna visual from PNG animation sheets: flip-books of 8 frames, tinted and layered with additive light
 * (fire, beams, stars) or ordinary alpha (the black and white slash and the black hole).
 */
public class PrimeFxRenderer extends EntityRenderer<PrimeFxEntity> {
    private static final float[] RED_DARK = {0.50f, 0.03f, 0.06f};
    private static final float[] RED = {0.95f, 0.12f, 0.08f};
    private static final float[] ORANGE = {1.00f, 0.45f, 0.12f};
    private static final float[] HOT = {1.00f, 0.86f, 0.68f};
    private static final float[] PURPLE = {0.45f, 0.08f, 0.85f};
    private static final float[] VIOLET = {0.62f, 0.32f, 1.00f};
    private static final float[] PINK = {1.00f, 0.35f, 0.75f};
    private static final float[] CYAN = {0.55f, 0.88f, 1.00f};
    private static final float[] WHITE = {1f, 1f, 1f};
    private static final Vec3 UP = new Vec3(0, 1, 0);

    // the crescent in slash.png: outer arc circle
    private static final double XA = 0.38, RA = 0.475;

    public PrimeFxRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(PrimeFxEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(PrimeFxEntity e) {
        return PrimeRender.GLOW;
    }

    private static float clamp01(double v) {
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    private static double ease(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }

    /** Deterministic pseudo random in [0,1). */
    private static float h(int i, int salt) {
        int x = i * 374761393 + salt * 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        x ^= x >>> 16;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float frac(double v) {
        return (float) (v - Math.floor(v));
    }

    @Override
    public void render(PrimeFxEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        int kind = e.getKind();
        if (kind < 0) return;
        float a = e.tickCount + pt;
        int life = e.getLife();
        if (a > life + 1) return;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        PrimePen pen = new PrimePen(ps.last().pose(), e.getPosition(pt), camera.getPosition(), camera.getLeftVector(), camera.getUpVector());
        switch (kind) {
            case PrimeFxEntity.SLASH -> slash(e, pen, buf, a, life, false);
            case PrimeFxEntity.CSLASH -> slash(e, pen, buf, a, life, true);
            case PrimeFxEntity.CHARGE -> charge(e, pen, buf, a, life, pt);
            case PrimeFxEntity.BEAM -> beam(e, pen, buf, a, life);
            case PrimeFxEntity.PILLAR -> pillar(e, pen, buf, a, life);
            case PrimeFxEntity.LATTICE -> lattice(e, pen, buf, a, life);
            case PrimeFxEntity.BLAST -> blast(e, pen, buf, a, life);
            case PrimeFxEntity.TEXT -> text(e, pen, buf, a, life);
            default -> {
            }
        }
    }

    private static void add(PrimePen pen, MultiBufferSource buf, ResourceLocation tex) {
        pen.vc = buf.getBuffer(PrimeRender.additive(tex));
    }

    private static void blend(PrimePen pen, MultiBufferSource buf, ResourceLocation tex) {
        pen.vc = buf.getBuffer(PrimeRender.blend(tex));
    }

    // ================================================================================================ crescent slashes
    private void slash(PrimeFxEntity e, PrimePen pen, MultiBufferSource buf, float a, int life, boolean cosmic) {
        Vec3 dir = e.getFxDir();
        if (dir.lengthSqr() < 1.0E-6) return;
        dir = dir.normalize();
        float size = e.getSize();
        double tilt = e.getAux();
        Vec3 pos = e.position().add(dir.scale(e.getSpeed() * a));
        Vec3 r = dir.cross(UP);
        if (r.lengthSqr() < 1.0E-4) r = new Vec3(1, 0, 0);
        r = r.normalize();
        Vec3 u = r.cross(dir).normalize();
        double ct = Math.cos(tilt), st = Math.sin(tilt);
        Vec3 X = r.scale(ct).subtract(u.scale(st));
        Vec3 Y = u.scale(ct).add(r.scale(st));
        double half = size * 0.5 / 0.92;
        int f;
        if (a < 3.6f) f = (int) (a / 1.2f);
        else if (a > life - 4) f = a > life - 2 ? 7 : 6;
        else f = 3 + ((int) (a * 0.7f)) % 3;
        float fadeEnd = clamp01((life - a) / 2.0);
        float face = (float) Math.abs(pen.view(pos).dot(dir));
        float aMain = Math.min(1f, face * 2.2f);
        float[] glowCol = cosmic ? VIOLET : WHITE;

        // light trail: streaks behind the whole blade
        add(pen, buf, PrimeRender.BEAM);
        for (int j = -4; j <= 4; j++) {
            double along = j / 4.5;
            Vec3 o = pos.add(Y.scale(along * size * 0.46));
            double len = e.getSpeed() * 5.0 * (1.0 - Math.abs(along) * 0.7) * (0.6 + 0.4 * h(j + 9, 3));
            Vec3 tail = o.subtract(dir.scale(len));
            pen.strip(tail, o, 0.5 + size * 0.02, 4, (int) (a * 2) % 8, 8, glowCol, 0.30f * fadeEnd, true);
        }
        // sparkles riding the edge of the blade
        add(pen, buf, PrimeRender.SPARKLE);
        int ns = cosmic ? 26 : 14;
        for (int i = 0; i < ns; i++) {
            double t = h(i, 5) * 2 - 1;
            double ox = (XA + Math.sqrt(Math.max(0, RA * RA - (0.46 * t) * (0.46 * t))) - 0.5) * 2 * half;
            double drift = frac(a * 0.05 + h(i, 6)) * size * 0.3;
            Vec3 p = pos.add(Y.scale(t * size * 0.46)).add(X.scale(ox + (h(i, 7) - 0.5) * 3.0)).subtract(dir.scale(drift));
            float tw = 0.5f + 0.5f * (float) Math.sin(a * 0.9 + i * 1.7);
            float[] c = cosmic ? (i % 3 == 0 ? CYAN : i % 3 == 1 ? VIOLET : WHITE) : WHITE;
            pen.bill(p, 0.35 + 0.55 * h(i, 8), a * 0.1 + i, c, tw * 0.9f * fadeEnd);
        }
        // the blade: the world plane when seen head-on, a camera facing copy when seen from the side, plus afterimages
        blend(pen, buf, cosmic ? PrimeRender.CSLASH : PrimeRender.SLASH);
        for (int g = 3; g >= 1; g--) {
            Vec3 gp = pos.subtract(dir.scale(e.getSpeed() * g * 1.5));
            float ga = (0.34f - 0.09f * g) * fadeEnd;
            if (aMain > 0.01f) pen.frame(gp, X, Y, half, half, f, 8, WHITE, ga * aMain);
            if (aMain < 0.99f) pen.billFrame(gp, half, -tilt, f, 8, WHITE, ga * (1 - aMain));
        }
        if (aMain > 0.01f) pen.frame(pos, X, Y, half, half, f, 8, WHITE, aMain * fadeEnd);
        if (aMain < 0.99f) pen.billFrame(pos, half, -tilt, f, 8, WHITE, (1 - aMain) * fadeEnd);
        // a glow on the cutting edge
        add(pen, buf, PrimeRender.GLOW);
        pen.bill(pos, size * 0.55, 0, glowCol, 0.14f * fadeEnd);
    }

    // ================================================================================================ World Disintegration: charge
    private void charge(PrimeFxEntity e, PrimePen pen, MultiBufferSource buf, float a, int life, float pt) {
        Entity owner = e.level().getEntity(e.getOwnerId());
        if (owner == null) return;
        Vec3 look = owner.getViewVector(pt);
        if (look.lengthSqr() < 1.0E-6) return;
        look = look.normalize();
        double c = clamp01(a / (double) Math.max(1, life));
        double cc = c * c;
        Vec3 base = owner.getEyePosition(pt).add(look.scale(3.4)).subtract(0, 0.35, 0);
        double lenTail = 2.8 + 3.8 * c, lenHead = 1.6 + 2.6 * c;
        Vec3 tail = base.subtract(look.scale(lenTail)), head = base.add(look.scale(lenHead));
        float fl = a > life - 5 ? 1f + (a - (life - 5)) * 0.35f : 1f;
        int fr = (int) (a * 1.5f);

        // the ground rune under the caster
        Vec3 feet = owner.getPosition(pt).add(0, 0.06, 0);
        add(pen, buf, PrimeRender.TONGUES);
        pen.flat(feet, 2.0 + 4.0 * c, a * 0.04, (int) (a * 0.8) % 8, 8, RED_DARK, (float) (0.20 + 0.45 * c));
        add(pen, buf, PrimeRender.RING);
        pen.flat(feet, 1.8 + 3.8 * c, -a * 0.09, 0, 1, RED, (float) (0.30 + 0.5 * c));
        pen.flat(feet.add(0, 0.03, 0), 3.0 + 5.0 * c, a * 0.06, 0, 1, PURPLE, (float) (0.25 + 0.45 * c));

        // glows
        add(pen, buf, PrimeRender.GLOW);
        pen.bill(head, (3.5 + 8.0 * c) * fl, 0, PURPLE, (float) (0.20 + 0.25 * c));
        pen.bill(head, (2.2 + 4.2 * c) * fl, 0, RED_DARK, (float) (0.45 + 0.35 * c));
        pen.bill(head, (1.1 + 2.2 * c) * fl, 0, RED, (float) (0.6 + 0.3 * c));
        pen.bill(head, (0.45 + 1.0 * c) * fl, 0, HOT, 1f);
        pen.bill(tail, 0.8 + 1.4 * c, 0, RED, 0.5f);

        // the arrow
        add(pen, buf, PrimeRender.ARROW);
        double w = 0.9 + 1.9 * c;
        pen.strip(tail, head, w * 2.1, 1, fr, 8, RED_DARK, 0.85f, false);
        pen.strip(tail, head, w * 1.25, 1, fr + 1, 8, RED, 0.95f, false);
        pen.strip(tail.add(look.scale(lenTail * 0.25)), head, w * 0.55, 1, fr + 2, 8, HOT, 1f, false);

        // the purple fire swirling around it
        add(pen, buf, PrimeRender.SWIRL);
        for (int i = 0; i < 5; i++) {
            double t = i / 4.0;
            Vec3 p = lerp(tail, head, 0.12 + 0.8 * t);
            double rad = (1.0 + 2.6 * c) * (1.55 - 0.95 * t);
            double rot = a * 0.28 * (i % 2 == 0 ? 1 : -1) + i * 1.1;
            pen.disc(p, look, rad, rot, (int) (a * 1.3) + i * 2, 8, i % 2 == 0 ? PURPLE : VIOLET, (float) (0.55 + 0.3 * c));
        }
        pen.disc(base.subtract(look.scale(0.6)), look, 2.8 + 5.2 * c, a * 0.15, (int) (a * 1.1), 8, PINK, (float) (0.20 + 0.35 * c));
        pen.disc(base.subtract(look.scale(1.2)), look, 2.2 + 4.0 * c, -a * 0.2, (int) (a * 1.4) + 3, 8, RED, (float) (0.25 + 0.35 * c));

        // embers and stars rushing in
        add(pen, buf, PrimeRender.SPARKLE);
        for (int i = 0; i < 30; i++) {
            Vec3 d = new Vec3(h(i, 1) * 2 - 1, h(i, 2) * 2 - 1, h(i, 3) * 2 - 1);
            d = d.lengthSqr() < 1.0E-4 ? UP : d.normalize();
            float p = frac(a * 0.06 + h(i, 4));
            Vec3 pos = head.add(d.scale((3 + 15 * h(i, 5)) * (1 - p)));
            pen.bill(pos, (0.28 + 0.35 * h(i, 6)) * (0.5 + c), a * 0.2 + i, i % 3 == 0 ? PINK : i % 3 == 1 ? ORANGE : VIOLET, (float) (Math.sin(Math.PI * p) * 0.95));
        }
    }

    // ================================================================================================ World Disintegration: laser
    private void beam(PrimeFxEntity e, PrimePen pen, MultiBufferSource buf, float a, int life) {
        Vec3 O = e.position(), P = e.getP2();
        Vec3 d = P.subtract(O);
        double len = d.length();
        if (len < 0.5) return;
        Vec3 dir = d.scale(1.0 / len);
        double reveal = clamp01(a / 2.2);
        Vec3 head = O.add(dir.scale(len * reveal));
        float fade = a < 4 ? 1f : clamp01(1.0 - (a - 4) / Math.max(1.0, life - 4));
        double wk = 1.0 + 0.7 * (1 - clamp01(a / 6.0));
        int fr = (int) (a * 2);

        add(pen, buf, PrimeRender.BEAM);
        pen.strip(O, head, 15 * wk, 40, fr, 8, PURPLE, 0.10f * fade, true);
        pen.strip(O, head, 7.0 * wk, 18, fr + 1, 8, PURPLE, 0.55f * fade, true);
        pen.strip(O, head, 4.6 * wk, 12, fr + 2, 8, RED_DARK, 0.85f * fade, true);
        pen.strip(O, head, 2.6 * wk, 9, fr + 3, 8, RED, 0.95f * fade, true);
        pen.strip(O, head, 1.0 * wk, 5, fr + 4, 8, HOT, fade, true);

        // reality coming apart along the path
        add(pen, buf, PrimeRender.DISTORT);
        int n = (int) Math.min(40, Math.max(2, len / 4.0));
        for (int k = 0; k < n; k++) {
            double s = (k + 0.5) * len / n;
            if (s > len * reveal) continue;
            double age = a - 0.14 * k;
            if (age < 0) continue;
            double p = clamp01(age / 15.0);
            double rad = 1.6 + p * (9.0 + (k % 3) * 3.5);
            float al = (float) ((1 - p) * 0.85) * clamp01(fade * 1.5);
            pen.disc(O.add(dir.scale(s)), dir, rad, k * 1.3 + a * 0.08 * (k % 2 == 0 ? 1 : -1), k + (int) a, 8, k % 2 == 0 ? PINK : VIOLET, al);
        }

        add(pen, buf, PrimeRender.GLOW);
        float early = clamp01(1.0 - a / 8.0);
        pen.bill(O, 10 * early + 2, 0, HOT, 0.9f * early);
        pen.bill(O, 18 * early + 3, 0, RED, 0.5f * early);
        if (reveal >= 1.0) {
            pen.bill(P, 16 * early + 3, 0, HOT, 0.9f * early);
            pen.bill(P, 30 * early + 4, 0, RED, 0.6f * early);
        }
        add(pen, buf, PrimeRender.BURST);
        if (a < 8) {
            pen.bill(O, 7 + a * 1.2, a * 0.2, ORANGE, 0.7f * early);
            if (reveal >= 1.0) pen.bill(P, 10 + a * 3.0, a * 0.1, RED, 0.8f * early);
        }
    }

    // ================================================================================================ the pillar of fire
    private void pillar(PrimeFxEntity e, PrimePen pen, MultiBufferSource buf, float a, int life) {
        Vec3 C = e.position();
        double R = e.getSize();
        double H = e.getAux() > 1 ? e.getAux() : 360;
        double grow = ease(a / 9.0);
        float fadeK = a > life - 30 ? clamp01((life - a) / 30.0) : 1f;
        int fr = (int) (a * 1.4);
        double sway = 1 + 0.06 * Math.sin(a * 0.6);

        // blinding flash
        add(pen, buf, PrimeRender.GLOW);
        if (a < 14) {
            float fl = clamp01(1.0 - a / 14.0);
            pen.bill(C.add(0, R * 0.3, 0), R * 2.4 * (0.5 + fl), 0, HOT, fl);
            pen.bill(C.add(0, R * 0.3, 0), R * 3.6 * (0.5 + fl), 0, RED, fl * 0.7f);
        }
        pen.bill(C.add(0, 2, 0), R * 1.6, 0, RED_DARK, 0.30f * fadeK);
        pen.bill(C.add(0, H * grow, 0), R * 3.0, 0, RED, 0.28f * fadeK);

        add(pen, buf, PrimeRender.BURST);
        if (a < 12) pen.bill(C.add(0, R * 0.4, 0), R * (0.6 + 0.9 * ease(a / 8.0)), a * 0.05, ORANGE, clamp01(1.0 - a / 12.0));

        // ground fire
        Vec3 g = C.add(0, 0.12, 0);
        add(pen, buf, PrimeRender.TONGUES);
        pen.flat(g, R * (0.35 + 0.7 * grow), a * 0.03, fr % 8, 8, RED_DARK, 0.9f * fadeK);
        pen.flat(g.add(0, 0.05, 0), R * (0.25 + 0.5 * grow), -a * 0.05, fr % 8 + 3, 8, ORANGE, 0.75f * fadeK);
        pen.flat(g.add(0, 0.09, 0), R * (0.15 + 0.28 * grow), a * 0.08, fr % 8 + 5, 8, HOT, 0.8f * fadeK);

        // shock rings along the ground
        add(pen, buf, PrimeRender.RING);
        for (int j = 0; j < 3; j++) {
            double aj = a - j * 4;
            if (aj <= 0) continue;
            double p = clamp01(aj / 18.0);
            pen.flat(g.add(0, 0.2 + j * 0.05, 0), R * 1.4 * ease(p), j, 0, 1, j == 1 ? PURPLE : RED, (float) ((1 - p) * 0.95));
        }
        add(pen, buf, PrimeRender.DISTORT);
        for (int j = 0; j < 2; j++) {
            double aj = a - 1 - j * 6;
            if (aj <= 0) continue;
            double p = clamp01(aj / 22.0);
            pen.flat(g.add(0, 0.3, 0), R * 1.6 * ease(p), j * 1.1, (int) a % 8, 8, j == 0 ? PINK : VIOLET, (float) ((1 - p) * 0.85));
        }

        // the pillar
        double h = H * grow;
        add(pen, buf, PrimeRender.PILLAR);
        pen.column(C, R * 1.15 * sway, h, fr, 8, RED_DARK, 0.45f * fadeK, R * 2.2);
        pen.column(C, R * 0.80 * sway, h, fr + 2, 8, RED, 0.75f * fadeK, R * 1.6);
        pen.column(C, R * 0.50 * sway, h, fr + 4, 8, ORANGE, 0.85f * fadeK, R * 1.0);
        pen.column(C, R * 0.26 * sway, h, fr + 6, 8, HOT, 0.95f * fadeK, R * 0.52);
        pen.column(C, R * 1.9, Math.min(h, R * 1.6), fr + 1, 8, RED_DARK, 0.35f * fadeK, R * 1.6);

        // dark purple fire turning around it
        add(pen, buf, PrimeRender.SWIRL);
        for (int i = 0; i < 8; i++) {
            if (grow < i / 8.0) break;
            double y = 3 + i * (H / 10.0) * grow;
            double rad = R * (1.15 - 0.07 * i);
            pen.flat(C.add(0, y, 0), rad, a * 0.12 * (i % 2 == 0 ? 1 : -1) + i, fr + i, 8, i % 2 == 0 ? PURPLE : PINK, 0.42f * fadeK);
        }

        // embers rising to the heavens
        add(pen, buf, PrimeRender.SPARKLE);
        for (int i = 0; i < 70; i++) {
            double ang = h(i, 11) * Math.PI * 2;
            double rr = R * Math.sqrt(h(i, 12)) * 1.05;
            double y = frac(a * (0.006 + 0.012 * h(i, 13)) + h(i, 14)) * Math.max(10, h);
            pen.bill(C.add(Math.cos(ang) * rr, y, Math.sin(ang) * rr), 0.7 + 1.1 * h(i, 15), a * 0.1 + i,
                    i % 3 == 0 ? HOT : i % 3 == 1 ? ORANGE : PINK, 0.85f * fadeK);
        }
    }

    // ================================================================================================ Universe Severing Slash
    private static final int START = 4, SPACING = 8;
    private static final double[] HEIGHTS = {-8, 0, 10, 20, 30};
    private static final int CONDENSE = 20;

    private void lattice(PrimeFxEntity e, PrimePen pen, MultiBufferSource buf, float a, int life) {
        Vec3 O = e.position();
        Vec3 F = e.getFxDir();
        if (F.lengthSqr() < 1.0E-6) return;
        F = new Vec3(F.x, 0, F.z).normalize();
        Vec3 L = new Vec3(-F.z, 0, F.x);
        double W = e.getAux(), Rg = e.getSize(), v = Math.max(0.5, e.getSpeed());
        Vec3 H = e.getP2();
        double T1 = (Rg - START) / v;
        double d = Math.min(Rg, START + a * v);
        double s = a < T1 ? 0 : ease((a - T1) / CONDENSE);
        float keep = (float) Math.pow(1 - s, 0.7);
        int fr = (int) (a * 1.5);
        float[][] cols = {{0.78f, 0.92f, 1f}, {0.72f, 0.46f, 1f}, {1f, 0.58f, 0.92f}};

        add(pen, buf, PrimeRender.CUTLINE);
        int across = 0;
        for (int fwd = START; fwd <= d; fwd += SPACING, across++) {
            double rev = clamp01((d - fwd) / 6.0 + 0.15);
            for (int k = 0; k < HEIGHTS.length; k++) {
                Vec3 mid = O.add(F.scale(fwd)).add(0, HEIGHTS[k], 0);
                warpedLine(pen, mid.subtract(L.scale(W * rev)), mid.add(L.scale(W * rev)), H, s, 1.5 * (1 - 0.8 * s), fr + k + across, cols[(across + k) % 3], 0.9f * keep);
            }
        }
        int nl = (int) Math.round(2 * W / SPACING);
        for (int j = 0; j <= nl; j++) {
            double l = -W + j * SPACING;
            for (int k = 0; k < HEIGHTS.length; k++) {
                Vec3 a0 = O.add(F.scale(START)).add(L.scale(l)).add(0, HEIGHTS[k], 0);
                Vec3 b0 = O.add(F.scale(d)).add(L.scale(l)).add(0, HEIGHTS[k], 0);
                warpedLine(pen, a0, b0, H, s, 1.2 * (1 - 0.8 * s), fr + k * 2 + j, cols[(j + k) % 3], 0.7f * keep);
            }
        }
        // slits standing at the crossings
        int idx = 0;
        for (int fwd = START; fwd <= d; fwd += SPACING) {
            for (int j = 0; j <= nl; j++, idx++) {
                if (((idx * 7 + j) & 1) == 1) continue;
                Vec3 bottom = O.add(F.scale(fwd)).add(L.scale(-W + j * SPACING)).add(0, -10, 0);
                Vec3 top = bottom.add(0, 44, 0);
                warpedLine(pen, bottom, top, H, s, 0.9 * (1 - 0.8 * s), fr + idx, cols[idx % 3], 0.55f * keep);
            }
        }

        // stars all over the cuts
        add(pen, buf, PrimeRender.SPARKLE);
        for (int i = 0; i < 170; i++) {
            double fw = START + h(i, 21) * (Rg - START);
            if (fw > d) continue;
            Vec3 p = O.add(F.scale(fw)).add(L.scale((h(i, 22) * 2 - 1) * W)).add(0, -10 + h(i, 23) * 44, 0);
            p = lerp(p, H, s);
            float tw = 0.5f + 0.5f * (float) Math.sin(a * 0.45 + i * 2.3);
            pen.bill(p, (0.45 + 0.8 * h(i, 24)) * (1 - 0.6 * s), a * 0.05 + i, cols[i % 3], tw * keep);
        }

        // the leading wall: a cosmic crescent sweeping ahead and its glow
        if (d < Rg - 0.5 && a < T1 + 2) {
            Vec3 fc = O.add(F.scale(d)).add(0, 8, 0);
            add(pen, buf, PrimeRender.GLOW);
            pen.bill(fc, 30, 0, VIOLET, 0.22f);
            pen.bill(fc, 12, 0, WHITE, 0.25f);
            blend(pen, buf, PrimeRender.CSLASH);
            double ct = Math.cos(0.55), st = Math.sin(0.55);
            Vec3 X = L.scale(ct).subtract(UP.scale(st)), Y = UP.scale(ct).add(L.scale(st));
            float face = (float) Math.abs(pen.view(fc).dot(F));
            float aM = Math.min(1f, face * 2.2f);
            int cf = 3 + ((int) (a * 0.7)) % 3;
            if (aM > 0.01f) pen.frame(fc, X, Y, 20, 20, cf, 8, WHITE, aM);
            if (aM < 0.99f) pen.billFrame(fc, 20, -0.55, cf, 8, WHITE, 1 - aM);
        }

        // the black hole
        if (a >= T1) {
            double toBlow = life - a;
            double pulse = toBlow < 14 ? 1 + 0.35 * (1 - toBlow / 14.0) + 0.08 * Math.sin(a * 2.2) : 1;
            double rad = lerp(0.4, 3.4, s) * pulse;
            Vec3 shake = toBlow < 14 ? new Vec3(Math.sin(a * 9) * 0.12, Math.cos(a * 11) * 0.12, Math.sin(a * 13) * 0.12) : Vec3.ZERO;
            Vec3 hc = H.add(shake);
            add(pen, buf, PrimeRender.GLOW);
            pen.bill(hc, rad * 5.5, 0, VIOLET, (float) (0.30 * s + 0.1));
            pen.bill(hc, rad * 3.2, 0, PURPLE, (float) (0.40 * s));
            add(pen, buf, PrimeRender.ACCRETION);
            pen.billFrame(hc, rad * 3.4, a * 0.15, (int) (a * 1.5) % 8, 8, VIOLET, (float) (0.8 * s));
            pen.billFrame(hc, rad * 2.5, -a * 0.28, (int) (a * 1.9) % 8, 8, CYAN, (float) (0.9 * s));
            pen.billFrame(hc, rad * 1.9, a * 0.4, (int) (a * 2.3) % 8, 8, WHITE, (float) (0.8 * s));
            add(pen, buf, PrimeRender.DISTORT);
            pen.bill(hc, rad * (3.6 + 0.6 * Math.sin(a * 0.3)), a * 0.07, PINK, (float) (0.35 * s));
            add(pen, buf, PrimeRender.SPARKLE);
            for (int i = 0; i < 36; i++) {
                Vec3 dd = new Vec3(h(i, 31) * 2 - 1, h(i, 32) * 2 - 1, h(i, 33) * 2 - 1);
                dd = dd.lengthSqr() < 1.0E-4 ? UP : dd.normalize();
                float p = frac(a * 0.07 + h(i, 34));
                pen.bill(hc.add(dd.scale(rad * 1.4 + 20 * (1 - p) * (0.4 + h(i, 35)))), 0.4 + 0.5 * h(i, 36), a * 0.3 + i, cols[i % 3], (float) (Math.sin(Math.PI * p) * s));
            }
            blend(pen, buf, PrimeRender.HOLE);
            pen.bill(hc, rad * 1.45, 0, WHITE, (float) Math.min(1, s * 4));
        }
    }

    /** A cut line from a to b whose points are pulled toward the black hole by {@code s}. Drawn in pieces so it bends as it is drawn in. */
    private void warpedLine(PrimePen pen, Vec3 a, Vec3 b, Vec3 hole, double s, double width, int f, float[] col, float al) {
        if (al <= 0.01f) return;
        if (s <= 0.001) {
            pen.strip(a, b, width, 8, f, 8, col, al, true);
            return;
        }
        int pieces = 6;
        Vec3 prev = lerp(a, hole, s);
        for (int i = 1; i <= pieces; i++) {
            Vec3 p = lerp(lerp(a, b, i / (double) pieces), hole, s);
            pen.strip(prev, p, width, 8, f + i, 8, col, al, true);
            prev = p;
        }
    }

    // ================================================================================================ small flashes
    private void blast(PrimeFxEntity e, PrimePen pen, MultiBufferSource buf, float a, int life) {
        Vec3 c = e.position();
        double S = e.getSize();
        int pal = (int) e.getAux();
        float[] A = pal == 1 ? RED : pal == 2 ? WHITE : VIOLET;
        float[] B = pal == 1 ? ORANGE : pal == 2 ? CYAN : CYAN;
        float[] C = pal == 1 ? HOT : WHITE;
        double p = clamp01(a / (double) life);
        float inv = clamp01(1 - p);
        double ex = ease(Math.min(1.0, p * 1.4));

        add(pen, buf, PrimeRender.GLOW);
        pen.bill(c, S * (0.8 + 1.4 * p), 0, C, inv * inv);
        pen.bill(c, S * (1.4 + 2.2 * p), 0, A, inv * 0.6f);
        add(pen, buf, PrimeRender.BURST);
        pen.bill(c, S * (0.5 + 1.1 * ex), a * 0.1, B, inv);
        add(pen, buf, PrimeRender.RING);
        for (int i = 0; i < 3; i++) {
            double pi = clamp01((a - i * 2) / (double) life);
            if (pi <= 0) continue;
            pen.bill(c, S * (0.3 + 1.8 * ease(pi)), 0, i == 1 ? B : A, clamp01(1 - pi) * 0.9f);
        }
        add(pen, buf, PrimeRender.DISTORT);
        pen.bill(c, S * (0.4 + 1.6 * ex), a * 0.12, A, inv * 0.8f);
        add(pen, buf, PrimeRender.SPARKLE);
        for (int i = 0; i < 20; i++) {
            Vec3 d = new Vec3(h(i, 41) * 2 - 1, h(i, 42) * 2 - 1, h(i, 43) * 2 - 1);
            d = d.lengthSqr() < 1.0E-4 ? UP : d.normalize();
            pen.bill(c.add(d.scale(S * (0.3 + 1.6 * ex * (0.5 + h(i, 44))))), 0.4 + 0.7 * h(i, 45), a * 0.2 + i, i % 2 == 0 ? B : C, inv);
        }
    }

    private void text(PrimeFxEntity e, PrimePen pen, MultiBufferSource buf, float a, int life) {
        double w = 3.4 * Math.max(0.3, e.getSize());
        double pop = a < 4 ? 0.45 + 0.55 * ease(a / 4.0) + 0.18 * Math.sin(a / 4.0 * Math.PI) : 1.0;
        float fade = a > life - 8 ? clamp01((life - a) / 8.0) : 1f;
        Vec3 c = e.position().add(Math.sin(a * 7.0) * 0.05, Math.cos(a * 9.0) * 0.05, 0);
        double rot = 0.09;
        Vec3 X = pen.right.scale(Math.cos(rot)).add(pen.up.scale(Math.sin(rot)));
        Vec3 Y = pen.up.scale(Math.cos(rot)).subtract(pen.right.scale(Math.sin(rot)));
        blend(pen, buf, PrimeRender.TEXT);
        pen.sprite(c, X, Y, w * pop, w * pop * 0.25, 0f, 0f, 1f, 1f, WHITE, fade);
    }
}
