package com.example.jjkspells.frieren.client;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.frieren.entity.DefensiveMagicEntity;
import com.example.jjkspells.frieren.spell.DefensiveMagicSpell;
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

import static com.example.jjkspells.frieren.entity.DefensiveMagicEntity.*;

/**
 * Draws the barrier from tickCount + partial tick. Everything additive and fullbright, and kept see-through.
 *
 * - Seal: a ringed circle with a honeycomb opens in front of the caster, then flies into them as the barrier unfolds.
 * - Barrier: a real hexagonal tiling of a sphere ({@link HexSphere}). Each cell is a faint blue panel, edges are thin white
 *   lines; both get more opaque towards the rim (view-angle shading), so the middle stays transparent.
 * - Runes drift slowly over the surface, three thin ticked rings circle it.
 * - A hit brightens the panels near the impact to white, sends a ripple ring across the surface and throws a few shards.
 * - Shatter: every panel flies outward and fades, with a shock ring and a flash for the pulse.
 */
public class DefensiveMagicRenderer extends EntityRenderer<DefensiveMagicEntity> {
    private static ResourceLocation tex(String n) {
        return JJKSpells.id("textures/entity/frieren/" + n + ".png");
    }

    private static final ResourceLocation SEAL = tex("barrier_seal");
    private static final ResourceLocation HALO = tex("barrier_halo");
    private static final ResourceLocation GLYPH = tex("barrier_glyphs");
    private static final ResourceLocation LINE = tex("barrier_line");
    private static final ResourceLocation FILL = tex("barrier_fill");
    private static final ResourceLocation WAVE = tex("zoltraak_wave");
    private static final ResourceLocation SPARK = tex("zoltraak_spark");
    private static final ResourceLocation BURST = tex("zoltraak_burst");

    private static final float[] TINT = {0.38f, 0.68f, 1.0f};
    private static final int GLYPHS = 28;
    private static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);

    private final Vec3[] posN = new Vec3[HexSphere.NODES.length];
    private final float[] nodeFill = new float[HexSphere.NODES.length * 4];
    private final float[] nodeLine = new float[HexSphere.NODES.length];
    private final float[] ctrFill = new float[HexSphere.CENTERS.length * 4];
    private List<float[]> live = new ArrayList<>();
    private float tNow;

    public DefensiveMagicRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(DefensiveMagicEntity e) {
        return SEAL;
    }

    @Override
    public boolean shouldRender(DefensiveMagicEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158f, c3 = c1 + 1f, k = x - 1f;
        return 1f + c3 * k * k * k + c1 * k * k;
    }

    private static float hash(int i) {
        int h = i * 374761393;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xffff) / 65535f;
    }

    private static VertexConsumer vc(MultiBufferSource buf, ResourceLocation t) {
        return buf.getBuffer(VolRenderTypes.additive(t));
    }

    /** East / north tangent axes of the sphere at unit vector n. */
    private static Vec3[] tangent(Vec3 n) {
        Vec3 ref = Math.abs(n.y) > 0.97 ? X : Y;
        Vec3 east = ref.cross(n).normalize();
        return new Vec3[]{east, n.cross(east).normalize()};
    }

    @Override
    public void render(DefensiveMagicEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        tNow = t;
        float expand = 1f - (float) Math.pow(1f - Mth.clamp((t - 6f) / 12f, 0f, 1f), 3);
        int st = e.shatterTick();
        boolean shat = st >= 0 && t >= st;
        float sAge = shat ? t - st : 0f;
        float env = shat ? 1f : 1f - smooth((t - EXPIRE_TICK) / FADE_OUT);
        float appear = Mth.clamp(expand * 1.5f, 0f, 1f);
        double R = DefensiveMagicSpell.RADIUS * (0.15 + 0.85 * expand) * (0.92 + 0.08 * env);
        float dur = e.durabilityFraction();

        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camLocal = cam.getPosition().subtract(e.getPosition(pt));
        Vector3f l = cam.getLeftVector();
        Vector3f u = cam.getUpVector();
        DefPen pen = new DefPen(ps.last().pose(), camLocal, new Vec3(-l.x(), -l.y(), -l.z()), new Vec3(u.x(), u.y(), u.z()));

        live = new ArrayList<>();
        for (float[] h : e.clientHits) if (t - h[4] >= 0f && t - h[4] < 18f) live.add(h);

        if (t < 20f) drawSeal(e, pen, buf, t);

        if (shat) {
            if (sAge < SHATTER_LIFE) drawShatter(e, pen, buf, sAge);
            return;
        }
        if (t < 6f || env <= 0.002f) return;

        shade(camLocal, R, t, appear, env, dur);
        drawPanels(pen, buf, R);
        drawLines(pen, buf);
        drawGlyphs(pen, buf, camLocal, R, t, appear, env);
        drawRings(pen, buf, R, t, appear, env);
        drawHits(e, pen, buf, R, t);
    }

    // ---------------------------------------------------------------------------------------------- the seal
    private void drawSeal(DefensiveMagicEntity e, DefPen pen, MultiBufferSource buf, float t) {
        float a = smooth(t / 2.5f) * (1f - smooth((t - 10f) / 8f));
        if (a <= 0.003f) return;
        Vec3 n = e.sealDir().normalize();
        Vec3 ref = Math.abs(n.y) > 0.97 ? X : Y;
        Vec3 bu = n.cross(ref).normalize();
        Vec3 bv = n.cross(bu).normalize();
        double pop = easeOutBack(Mth.clamp(t / 4f, 0f, 1f)) * (1.0 - 0.35 * smooth((t - 10f) / 8f));
        Vec3 c = n.scale(1.7 * (1f - smooth((t - 8f) / 10f)));
        float reveal = smooth((t - 2f) / 6f); // the honeycomb forms inside the ring a moment later
        pen.use(vc(buf, HALO)).plane(c, bu, bv, 1.15 * pop, 0.05 * t, DefPen.FULL, 1, 1, 1, a);
        pen.use(vc(buf, SEAL)).plane(c.add(n.scale(0.02)), bu, bv, 1.05 * pop * (0.8 + 0.2 * reveal), -0.07 * t, DefPen.FULL, 1, 1, 1, a * (0.35f + 0.65f * reveal));
    }

    // ---------------------------------------------------------------------------------------------- shading
    private float glow(Vec3 n) {
        float sum = 0f;
        for (float[] h : live) {
            float age = tNow - h[4];
            double cs = n.x * h[0] + n.y * h[1] + n.z * h[2];
            double ang = Math.acos(Mth.clamp(cs, -1.0, 1.0));
            float s = h[3];
            sum += s * (float) Math.exp(-(ang / 0.55) * (ang / 0.55)) * (float) Math.exp(-age / 5f);   // local flash
            double dr = (ang - 0.15 * age) / 0.16;
            sum += s * 0.8f * (float) Math.exp(-dr * dr) * Math.max(0f, 1f - age / 16f);                // ripple ring
            sum += s * 0.25f * (float) Math.exp(-age / 4f);                                           // whole-barrier flash
        }
        return Math.min(sum, 1.4f);
    }

    private static float fresnel(Vec3 n, Vec3 p, Vec3 cam) {
        Vec3 v = cam.subtract(p);
        double len = v.length();
        return len < 1.0E-4 ? 0f : (float) (1.0 - Math.abs(n.dot(v)) / len);
    }

    /** Per-vertex colours: see-through in the middle, more opaque at the rim, white where something just hit. */
    private void shade(Vec3 cam, double R, float t, float appear, float env, float dur) {
        float low = Math.max(0f, (0.3f - dur) / 0.3f);
        for (int i = 0; i < HexSphere.NODES.length; i++) {
            Vec3 n = HexSphere.NODES[i];
            posN[i] = n.scale(R);
            float f = fresnel(n, posN[i], cam);
            float b = glow(n);
            float w = Math.min(1f, b * 1.3f);
            float fa = ((0.045f + 0.40f * (float) Math.pow(f, 2.2)) * appear + 0.5f * b) * env;
            nodeFill[i * 4] = TINT[0] + (1 - TINT[0]) * w;
            nodeFill[i * 4 + 1] = TINT[1] + (1 - TINT[1]) * w;
            nodeFill[i * 4 + 2] = TINT[2] + (1 - TINT[2]) * w;
            nodeFill[i * 4 + 3] = Math.min(fa, 0.9f);
            float flick = 1f - 0.4f * low * (0.5f + 0.5f * (float) Math.sin(t * 1.9f + i * 0.7f)); // a failing barrier stutters
            nodeLine[i] = Math.min(1f, ((0.28f + 0.45f * f) * appear + 0.8f * b) * env * flick);
        }
        for (int i = 0; i < HexSphere.CENTERS.length; i++) {
            Vec3 n = HexSphere.CENTERS[i];
            float f = fresnel(n, n.scale(R), cam);
            float b = glow(n);
            float w = Math.min(1f, b * 1.3f);
            float cv = (0.75f + 0.5f * hash(i)) * (0.92f + 0.08f * (float) Math.sin(t * 0.06f + i)); // each panel a touch different
            float fa = ((0.045f + 0.40f * (float) Math.pow(f, 2.2)) * appear * cv + 0.5f * b) * env;
            ctrFill[i * 4] = TINT[0] + (1 - TINT[0]) * w;
            ctrFill[i * 4 + 1] = TINT[1] + (1 - TINT[1]) * w;
            ctrFill[i * 4 + 2] = TINT[2] + (1 - TINT[2]) * w;
            ctrFill[i * 4 + 3] = Math.min(fa, 0.9f);
        }
    }

    private static float[] col(float[] a, int i) {
        return new float[]{a[i * 4], a[i * 4 + 1], a[i * 4 + 2], a[i * 4 + 3]};
    }

    private void drawPanels(DefPen pen, MultiBufferSource buf, double R) {
        pen.use(vc(buf, FILL));
        for (int i = 0; i < HexSphere.CELLS.length; i++) {
            int[] cell = HexSphere.CELLS[i];
            int n = cell.length;
            Vec3 c = HexSphere.CENTERS[i].scale(R);
            float[] cc = col(ctrFill, i);
            for (int k = 0; k < n; k += 2) {
                int i0 = cell[k], i1 = cell[(k + 1) % n];
                int i2 = k + 2 <= n ? cell[(k + 2) % n] : i1;
                pen.quadC(c, posN[i0], posN[i1], posN[i2], DefPen.FULL, cc, col(nodeFill, i0), col(nodeFill, i1), col(nodeFill, i2));
            }
        }
    }

    private void drawLines(DefPen pen, MultiBufferSource buf) {
        pen.use(vc(buf, LINE));
        for (int[] ed : HexSphere.EDGES) {
            float a = nodeLine[ed[0]], b = nodeLine[ed[1]];
            if (a <= 0.01f && b <= 0.01f) continue;
            pen.ribbon(posN[ed[0]], posN[ed[1]], 0.05, 0.05, 0f, 1f, a, b, 1f, 1f, 1f);
        }
    }

    // ---------------------------------------------------------------------------------------------- runes and rings
    private void drawGlyphs(DefPen pen, MultiBufferSource buf, Vec3 cam, double R, float t, float appear, float env) {
        pen.use(vc(buf, GLYPH));
        for (int i = 0; i < GLYPHS; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / GLYPHS;
            if (Math.abs(y) > 0.96) continue;
            double r = Math.sqrt(1 - y * y);
            double phi = i * 2.399963 + 0.012 * t; // slow drift round the vertical axis
            Vec3 n = new Vec3(r * Math.cos(phi), y, r * Math.sin(phi));
            Vec3 p = n.scale(R * 1.01);
            Vec3[] ax = tangent(n);
            float f = fresnel(n, p, cam);
            float al = ((0.35f + 0.45f * f) * (0.6f + 0.4f * (float) Math.sin(t * 0.08f + i * 1.7f)) * appear + 0.5f * glow(n)) * env;
            pen.plane(p, ax[0], ax[1], 0.26, 0.0, DefPen.frame4(i & 15), 1f, 1f, 1f, Math.min(1f, al));
        }
    }

    private void drawRings(DefPen pen, MultiBufferSource buf, double R, float t, float appear, float env) {
        DefPen p = pen.use(vc(buf, HALO));
        float a = 0.5f * appear * env;
        double half = R * 1.12;
        p.plane(Vec3.ZERO, X, Z, half, 0.02 * t, DefPen.FULL, 1, 1, 1, a);
        double th = Math.toRadians(60);
        for (int k = 0; k < 2; k++) {
            double al = (k == 0 ? 1 : -1) * 0.012 * t + k * 2.1;
            Vec3 n = new Vec3(Math.sin(th) * Math.cos(al), Math.cos(th), Math.sin(th) * Math.sin(al));
            Vec3 u = n.cross(Y).normalize();
            Vec3 v = n.cross(u).normalize();
            p.plane(Vec3.ZERO, u, v, half * (k == 0 ? 1.0 : 1.06), (k == 0 ? -1 : 1) * 0.03 * t, DefPen.FULL, 1, 1, 1, a);
        }
    }

    // ---------------------------------------------------------------------------------------------- impacts
    private void drawHits(DefensiveMagicEntity e, DefPen pen, MultiBufferSource buf, double R, float t) {
        if (live.isEmpty()) return;
        DefPen p = pen.use(vc(buf, WAVE));
        for (float[] h : live) {
            float age = t - h[4];
            if (age >= 14f) continue;
            Vec3 n = new Vec3(h[0], h[1], h[2]);
            Vec3[] ax = tangent(n);
            double rad = 0.2 + 1.6 * (1.0 - Math.pow(1.0 - age / 14.0, 2));
            float al = (1f - age / 14f) * 0.9f * h[3];
            p.plane(n.scale(R * 1.005), ax[0], ax[1], rad, 0.0, DefPen.FULL, 1, 1, 1, al);
        }
        DefPen sp = pen.use(vc(buf, SPARK));
        for (float[] h : live) {
            float age = t - h[4];
            if (age >= 14f) continue;
            Vec3 n = new Vec3(h[0], h[1], h[2]);
            Vec3[] ax = tangent(n);
            Random r = new Random(e.seed() * 31L + (long) (h[4] * 7) + (long) (h[3] * 100));
            int count = 5 + (int) (h[3] * 5);
            for (int i = 0; i < count; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                Vec3 v = ax[0].scale(Math.cos(ang)).add(ax[1].scale(Math.sin(ang))).add(n.scale(0.35)).normalize();
                double speed = 0.25 + r.nextDouble() * 0.5;
                double life = 8 + r.nextDouble() * 6;
                if (age >= life) continue;
                double dist = speed * (1 - Math.pow(0.88, age)) / 0.12;
                double vel = speed * Math.pow(0.88, age);
                Vec3 head = n.scale(R * 1.01).add(v.scale(dist));
                Vec3 tail = head.subtract(v.scale(0.2 + vel * 1.2));
                float al = (float) Math.pow(1 - age / life, 1.2);
                sp.ribbon(head, tail, 0.07, 0.035, 0f, 1f, al, al * 0.4f, 1, 1, 1);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- shatter
    private void drawShatter(DefensiveMagicEntity e, DefPen pen, MultiBufferSource buf, float s) {
        double R = DefensiveMagicSpell.RADIUS;
        float k = Mth.clamp(s / 16f, 0f, 1f);
        float ease = 1f - (1f - k) * (1f - k);
        float amul = (float) Math.pow(1f - k, 1.2);
        float w = 1f - k;
        float[] tint = {TINT[0] + (1 - TINT[0]) * w, TINT[1] + (1 - TINT[1]) * w, TINT[2] + (1 - TINT[2]) * w};
        if (k < 1f) {
            DefPen pf = pen.use(vc(buf, FILL));
            for (int i = 0; i < HexSphere.CELLS.length; i++) drawCell(pf, i, R, ease, k, s, amul, tint, true);
            DefPen pl = pen.use(vc(buf, LINE));
            for (int i = 0; i < HexSphere.CELLS.length; i++) drawCell(pl, i, R, ease, k, s, amul, tint, false);
        }
        // pulse: three crossed shock rings out to the pulse radius, a flash, and shards
        if (s < 10f) {
            float x = s / 10f;
            double rad = DefensiveMagicSpell.PULSE_RADIUS * (1.0 - Math.pow(1.0 - x, 3));
            float al = (float) Math.pow(1 - x, 1.2);
            DefPen pw = pen.use(vc(buf, WAVE));
            pw.plane(Vec3.ZERO, X, Z, rad, 0.0, DefPen.FULL, 1, 1, 1, al);
            pw.plane(Vec3.ZERO, X, Y, rad, 0.0, DefPen.FULL, 1, 1, 1, al * 0.8f);
            pw.plane(Vec3.ZERO, Z, Y, rad, 0.0, DefPen.FULL, 1, 1, 1, al * 0.8f);
        }
        if (s < 16f) {
            pen.use(vc(buf, BURST)).billboard(Vec3.ZERO, 4.0, 0.3, DefPen.frame4(Math.min(15, (int) (s * 0.95f))), 1, 1, 1, 1);
        }
        DefPen sp = pen.use(vc(buf, SPARK));
        Random r = new Random(e.seed() * 13L + 3);
        for (int i = 0; i < 36; i++) {
            Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            double speed = 0.5 + r.nextDouble() * 1.1;
            double life = 10 + r.nextDouble() * 6;
            if (s >= life) continue;
            double dist = speed * (1 - Math.pow(0.86, s)) / 0.14;
            double vel = speed * Math.pow(0.86, s);
            Vec3 head = v.scale(1.0 + dist);
            Vec3 tail = head.subtract(v.scale(0.3 + vel * 1.3));
            float al = (float) Math.pow(1 - s / life, 1.2);
            sp.ribbon(head, tail, 0.08, 0.04, 0f, 1f, al, al * 0.4f, 1, 1, 1);
        }
    }

    /** One flying panel: either its fill (3 quads) or its outline. */
    private void drawCell(DefPen pen, int i, double R, float ease, float k, float s, float amul, float[] tint, boolean fill) {
        int[] cell = HexSphere.CELLS[i];
        int n = cell.length;
        float h = hash(i);
        Vec3 cn = HexSphere.CENTERS[i];
        Vec3 off = cn.scale(ease * (2.2 + 3.0 * h)).add(0, -0.012 * s * s * h, 0);
        double scale = 1.0 - 0.55 * k;
        Vec3 c = cn.scale(R).add(off);
        Vec3[] pts = new Vec3[n];
        for (int j = 0; j < n; j++) pts[j] = c.add(HexSphere.NODES[cell[j]].scale(R).subtract(cn.scale(R)).scale(scale));
        if (fill) {
            float[] cc = {tint[0], tint[1], tint[2], 0.5f * amul};
            float[] ce = {tint[0], tint[1], tint[2], 0.3f * amul};
            for (int j = 0; j < n; j += 2) {
                Vec3 p1 = pts[(j + 1) % n];
                Vec3 p2 = j + 2 <= n ? pts[(j + 2) % n] : p1;
                pen.quadC(c, pts[j], p1, p2, DefPen.FULL, cc, ce, ce, ce);
            }
        } else {
            for (int j = 0; j < n; j++) pen.ribbon(pts[j], pts[(j + 1) % n], 0.045, 0.045, 0f, 1f, 0.9f * amul, 0.9f * amul, 1f, 1f, 1f);
        }
    }
}
