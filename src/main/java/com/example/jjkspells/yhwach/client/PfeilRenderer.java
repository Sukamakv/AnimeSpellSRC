package com.example.jjkspells.yhwach.client;

import com.example.jjkspells.yhwach.entity.BowFrame;
import com.example.jjkspells.yhwach.entity.PfeilEntity;
import com.example.jjkspells.yhwach.spell.HeiligerPfeilSpell;
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

import java.util.Random;

/**
 * Draws the whole "Heiliger Pfeil" show with additive light: the 6 block Reishi bow (swept crescent, glowing string),
 * the charging arrow with converging rings and a Quincy seal, the laser beam with spiralling energy, and the blue
 * explosion (flip-book bloom, shockwave rings, light pillar, radial spikes, sparkles).
 */
public class PfeilRenderer extends EntityRenderer<PfeilEntity> {
    private static final double R = HeiligerPfeilSpell.BLAST_RADIUS;
    private static final double ARROW_LEN = 4.2;
    private static final double MAX_DRAW = 1.9;

    public PfeilRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(PfeilEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(PfeilEntity e) {
        return YhwachRender.GLOW;
    }

    /** Everything the effect phases need, resolved once per frame. */
    private static final class S {
        BowFrame fr;
        float t;
        float fa = -1f;
        float c;
        boolean fired;
        Vec3 O;
        Vec3 P;
        Vec3 beamStart;
        double beamLen;
        double reveal;
        float bowA;
        double draw;
        float arrowA;
        double ex = -1.0;
        int seed;
        Vec3 casterPos;
    }

    @Override
    public void render(PfeilEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        S s = new S();
        s.t = t;
        s.seed = e.getId();
        s.fired = e.isFired();
        s.c = clamp01(t / PfeilEntity.CHARGE_TICKS);
        if (!s.fired) {
            Entity owner = e.level().getEntity(e.getOwnerId());
            if (owner == null) {
                return;
            }
            s.fr = BowFrame.charging(owner.getPosition(pt), owner.getViewVector(pt));
            s.reveal = ease(clamp01(t / 7f));
            s.bowA = 1f;
            s.draw = MAX_DRAW * easeInOut(clamp01((t - 3f) / (PfeilEntity.CHARGE_TICKS - 3f)));
            s.arrowA = 1f;
        } else {
            s.O = e.origin();
            s.P = e.impact();
            s.fr = BowFrame.fired(s.O, s.P);
            s.fa = e.getFireTick() >= 0 ? Math.max(0f, e.tickCount - e.getFireTick() + pt) : pt;
            s.ex = s.fa - PfeilEntity.TRAVEL_TICKS;
            s.reveal = 1.0;
            s.bowA = 1f - clamp01(s.fa / 7f);
            s.draw = MAX_DRAW * (1.0 - clamp01(s.fa / 1.5f));
            s.arrowA = 1f - clamp01(s.fa / 1.2f);
            s.beamLen = s.P.subtract(s.O).length();
            s.beamStart = s.O.add(s.fr.f.scale(Math.min(BOW_TIP, s.beamLen * 0.9)));
            if (s.fa > PfeilEntity.LIFE_AFTER_FIRE) {
                return;
            }
        }
        s.casterPos = s.fr.center.subtract(s.fr.f.scale(PfeilEntity.BOW_FORWARD)).subtract(0, PfeilEntity.BOW_HEIGHT, 0);

        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 ePos = e.getPosition(pt);
        Pen pen = new Pen(ps.last().pose(), ePos, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        pen.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.GLOW));
        glowPhase(pen, s);
        pen.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.STREAK));
        streakPhase(pen, s);
        pen.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.RING));
        ringPhase(pen, s);
        pen.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.SEAL));
        sealPhase(pen, s);
        pen.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.BURST));
        burstPhase(pen, s);
        pen.vc = buf.getBuffer(YhwachRender.additive(YhwachRender.SPARKLE));
        sparklePhase(pen, s);
    }

    /** Distance from the bow centre to the arrow head at the moment of release. */
    private static final double BOW_TIP = -0.5 - MAX_DRAW + ARROW_LEN;

    // ------------------------------------------------------------------ glow sprites

    private void glowPhase(Pen p, S s) {
        BowFrame fr = s.fr;
        float pulse = 0.5f + 0.5f * (float) Math.sin(s.t * 0.55f);
        if (s.bowA > 0.01f) {
            p.billboard(fr.center, 5.2 * s.reveal, 0, Pen.BLUE, (0.20f + 0.06f * pulse) * s.bowA, 0, 0, 1, 1);
            p.billboard(fr.center, 2.8 * s.reveal, 0, Pen.CYAN, (0.20f + 0.06f * pulse) * s.bowA, 0, 0, 1, 1);
            p.billboard(fr.bowPoint(1, s.reveal), 1.1 * s.reveal, 0, Pen.CYAN, 0.55f * s.bowA, 0, 0, 1, 1);
            p.billboard(fr.bowPoint(-1, s.reveal), 1.1 * s.reveal, 0, Pen.CYAN, 0.55f * s.bowA, 0, 0, 1, 1);
        }
        Vec3 tail = fr.nock(s.draw);
        Vec3 head = tail.add(fr.f.scale(arrowLen(s)));
        if (s.arrowA > 0.01f) {
            double cc = s.fired ? 1.0 : s.c;
            p.billboard(head, 0.5 + 2.6 * cc * cc, 0, Pen.CYAN, (0.55f + 0.4f * (float) cc) * s.arrowA, 0, 0, 1, 1);
            p.billboard(head, 0.25 + 1.1 * cc * cc, 0, Pen.WHITE, 1.0f * s.arrowA, 0, 0, 1, 1);
            p.billboard(tail, 0.45 + 0.5 * cc, 0, Pen.CYAN, 0.55f * s.arrowA, 0, 0, 1, 1);
        }
        if (!s.fired) {
            return;
        }
        float fa = s.fa;
        if (fa < 7f) {
            float k = 1f - fa / 7f;
            p.billboard(s.beamStart, 7.0 * k + 1.0, 0, Pen.CORE, k, 0, 0, 1, 1);
            p.billboard(s.beamStart, 3.0 * k, 0, Pen.WHITE, k, 0, 0, 1, 1);
        }
        if (fa < PfeilEntity.TRAVEL_TICKS + 1) {
            float k = clamp01(fa / PfeilEntity.TRAVEL_TICKS);
            p.billboard(s.P, 0.8 + 3.0 * k, 0, Pen.CYAN, 0.8f * k, 0, 0, 1, 1);
        }
        double ex = s.ex;
        if (ex >= 0) {
            float flash = 1f - clamp01((float) ex / 7f);
            p.billboard(s.P, R * (1.0 + 0.8 * (1 - flash)), 0, Pen.WHITE, flash, 0, 0, 1, 1);
            p.billboard(s.P, R * 1.15, 0, Pen.CYAN, 0.6f * (1f - clamp01((float) ex / 22f)), 0, 0, 1, 1);
            p.billboard(s.P, R * 2.1, 0, Pen.BLUE, 0.24f * (1f - clamp01((float) ex / 32f)), 0, 0, 1, 1);
        }
    }

    private static double arrowLen(S s) {
        if (s.fired) {
            return ARROW_LEN;
        }
        return ARROW_LEN * ease(clamp01((s.t - 7f) / 13f));
    }

    // ------------------------------------------------------------------ ribbons: bow, string, arrow, beam, pillar, spikes

    private static final float[][] BOW_COL = {Pen.BLUE, Pen.CYAN, Pen.CORE, Pen.WHITE};
    private static final double[] BOW_MUL = {3.4, 1.9, 0.85, 0.34};
    private static final float[] BOW_ALPHA = {0.30f, 0.55f, 0.90f, 1.0f};

    private void streakPhase(Pen p, S s) {
        BowFrame fr = s.fr;
        float t = s.t;
        // ---- bow
        if (s.bowA > 0.01f && s.reveal > 0.02) {
            int n = 56;
            for (int layer = 0; layer < 4; layer++) {
                Vec3 prev = null;
                double prevW = 0;
                for (int i = 0; i <= n; i++) {
                    double tt = (-1.0 + 2.0 * i / n) * s.reveal;
                    Vec3 pt = fr.bowPoint(tt, 1.0);
                    double at = Math.abs(tt);
                    double w = (0.06 + 0.46 * Math.pow(1.0 - Math.pow(at, 1.8), 1.4)) * (1.0 + 0.07 * Math.sin(t * 0.7 + tt * 3.0)) * BOW_MUL[layer];
                    if (prev != null) {
                        float u0 = (i - 1) * 0.35f - t * 0.05f;
                        p.ribbon(prev, pt, prevW, w, BOW_ALPHA[layer] * s.bowA, BOW_ALPHA[layer] * s.bowA, u0, u0 + 0.35f, BOW_COL[layer]);
                    }
                    prev = pt;
                    prevW = w;
                }
            }
            if (s.reveal > 0.9) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    Vec3 tip = fr.bowPoint(sign, 1.0);
                    Vec3 dir = tip.subtract(fr.bowPoint(sign * 0.93, 1.0)).normalize();
                    Vec3 ext = tip.add(dir.scale(0.9 + 0.1 * Math.sin(t * 0.8)));
                    p.ribbon(tip, ext, 0.22, 0.0, 0.9f * s.bowA, 0.0f, 0f, 1f, Pen.CYAN);
                    p.ribbon(tip, ext, 0.10, 0.0, 1.0f * s.bowA, 0.0f, 0f, 1f, Pen.WHITE);
                }
            }
            // ---- string
            float sa = clamp01((float) (s.reveal - 0.6) / 0.4f) * s.bowA;
            if (sa > 0.01f) {
                Vec3 nock = fr.nock(s.draw);
                Vec3 lo = fr.bowPoint(-1, 1.0);
                Vec3 hi = fr.bowPoint(1, 1.0);
                float[][] sc = {Pen.BLUE, Pen.CYAN, Pen.WHITE};
                double[] sw = {0.34, 0.17, 0.06};
                float[] sal = {0.30f, 0.6f, 0.95f};
                for (int l = 0; l < 3; l++) {
                    p.ribbon(lo, nock, sw[l], sal[l] * sa, 0f, 2.5f, sc[l]);
                    p.ribbon(nock, hi, sw[l], sal[l] * sa, 0f, 2.5f, sc[l]);
                }
            }
        }
        // ---- arrow
        if (s.arrowA > 0.01f) {
            Vec3 tail = fr.nock(s.draw);
            double len = arrowLen(s);
            if (len > 0.05) {
                Vec3 head = tail.add(fr.f.scale(len));
                float a = s.arrowA;
                float[][] ac = {Pen.CYAN, Pen.CORE, Pen.WHITE};
                double[] aw = {0.50, 0.22, 0.09};
                float[] aal = {0.5f, 0.95f, 1.0f};
                for (int l = 0; l < 3; l++) {
                    p.ribbon(tail, head.subtract(fr.f.scale(0.25)), aw[l], aal[l] * a, -t * 0.1f, -t * 0.1f + 2.0f, ac[l]);
                }
                // arrow head: tapered blade
                float[][] hc = {Pen.BLUE, Pen.CYAN, Pen.WHITE};
                double[] hw = {1.35, 0.85, 0.40};
                float[] hal = {0.5f, 0.9f, 1.0f};
                for (int l = 0; l < 3; l++) {
                    p.ribbon(head.subtract(fr.f.scale(1.0)), head.add(fr.f.scale(0.75)), hw[l], 0.0, hal[l] * a, hal[l] * a * 0.4f, 0f, 1f, hc[l]);
                }
                // fletching
                if (len > 1.0) {
                    Vec3[] offs = {fr.r.scale(0.55), fr.r.scale(-0.55), fr.u.scale(0.55), fr.u.scale(-0.55)};
                    for (Vec3 off : offs) {
                        p.ribbon(tail.add(fr.f.scale(0.95)), tail.add(off).add(fr.f.scale(0.1)), 0.10, 0.03, 0.85f * a, 0.5f * a, 0f, 1f, Pen.CYAN);
                    }
                }
            }
        }
        if (!s.fired) {
            return;
        }
        float fa = s.fa;
        // ---- laser beam
        double hP = clamp01(fa / 1.5f);
        double tP = clamp01((fa - PfeilEntity.TRAVEL_TICKS) / 6f);
        float fade = 1f - clamp01((fa - PfeilEntity.TRAVEL_TICKS) / 14f);
        double wf = 0.25 + 0.75 * fade;
        if (fade > 0.01f) {
            Vec3 bs = s.beamStart;
            Vec3 line = s.P.subtract(bs);
            Vec3 a0 = bs.add(line.scale(tP));
            Vec3 a1 = bs.add(line.scale(hP));
            double len = a1.subtract(a0).length();
            if (len > 0.05) {
                int segs = Math.max(1, (int) Math.ceil(len / 4.0));
                float[][] bc = {Pen.BLUE, Pen.CYAN, Pen.CORE, Pen.WHITE};
                double[] bm = {3.4, 1.8, 0.75, 0.30};
                float[] ba = {0.35f, 0.6f, 0.95f, 1.0f};
                for (int l = 0; l < 4; l++) {
                    for (int i = 0; i < segs; i++) {
                        Vec3 q0 = a0.add(a1.subtract(a0).scale((double) i / segs));
                        Vec3 q1 = a0.add(a1.subtract(a0).scale((double) (i + 1) / segs));
                        float u0 = i * 0.66f - t * 0.8f;
                        p.ribbon(q0, q1, bm[l] * wf, ba[l] * fade, u0, u0 + 0.66f, bc[l]);
                    }
                }
                // twin helices of energy
                Vec3 dir = line.normalize();
                double step = Math.max(0.8, len / 100.0);
                for (int k = 0; k < 2; k++) {
                    Vec3 prev = null;
                    for (double d = 0; d <= len; d += step) {
                        double ph = d * 1.1 - t * 0.9 + k * Math.PI;
                        double rad = 0.85 * wf;
                        Vec3 q = a0.add(dir.scale(d)).add(fr.r.scale(Math.cos(ph) * rad)).add(fr.u.scale(Math.sin(ph) * rad));
                        if (prev != null) {
                            p.ribbon(prev, q, 0.24 * wf, 0.8f * fade, 0f, 1f, Pen.CYAN);
                        }
                        prev = q;
                    }
                }
            }
        }
        // ---- explosion: light pillar and radial spikes
        double ex = s.ex;
        if (ex >= 0) {
            Vec3 hx = new Vec3(0, 1, 0).cross(p.cam.subtract(s.P));
            hx = hx.lengthSqr() < 1.0E-8 ? new Vec3(1, 0, 0) : hx.normalize();
            float pf = 1f - clamp01((float) ex / 30f);
            double pw = 2.8 * pf * (1.0 + 0.1 * Math.sin(t * 1.3));
            Vec3 bot = s.P.add(0, -1.0, 0);
            Vec3 top = s.P.add(0, 90.0, 0);
            float[][] pc = {Pen.BLUE, Pen.CYAN, Pen.CORE};
            double[] pm = {1.9, 1.0, 0.42};
            float[] pa = {0.40f, 0.65f, 0.95f};
            for (int l = 0; l < 3; l++) {
                p.ribbonSide(bot, top, hx, pw * pm[l], pw * pm[l] * 0.55, pa[l] * pf, 0f, 0f, 12f - t * 0.4f, pc[l]);
            }
            Random rnd = new Random(s.seed * 7919L + 11);
            float sf = 1f - clamp01((float) ex / 16f);
            double grow = ease(clamp01((float) ex / 5f));
            for (int i = 0; i < 26; i++) {
                double th = rnd.nextDouble() * Math.PI * 2;
                double el = -0.05 + rnd.nextDouble() * 0.95;
                double ln = R * (0.55 + 0.95 * rnd.nextDouble()) * grow;
                Vec3 dir = new Vec3(Math.cos(th) * Math.cos(el), Math.sin(el), Math.sin(th) * Math.cos(el));
                Vec3 a = s.P.add(dir.scale(0.4));
                Vec3 b = s.P.add(dir.scale(0.4 + ln));
                p.ribbon(a, b, 0.55, 0.0, 0.95f * sf, 0.0f, 0f, 1f, (i & 1) == 0 ? Pen.CYAN : Pen.CORE);
            }
        }
    }

    // ------------------------------------------------------------------ rings

    private void ringPhase(Pen p, S s) {
        BowFrame fr = s.fr;
        float t = s.t;
        // converging charge rings around the arrow tip
        if (!s.fired && s.c > 0.15f) {
            Vec3 head = fr.nock(s.draw).add(fr.f.scale(arrowLen(s) + 0.2));
            for (int k = 0; k < 3; k++) {
                float ph = frac(s.c * 1.7f + k / 3f + t * 0.02f);
                double half = 5.0 * (1f - ph) + 0.4;
                float a = s.c * ph * (1f - ph) * 3.2f;
                p.plane(head, fr.r, fr.u, half, t * 0.1 * (k + 1), Pen.CYAN, a);
            }
        }
        if (!s.fired) {
            return;
        }
        float fa = s.fa;
        // muzzle shockwave
        if (fa < 7f) {
            float k = 1f - clamp01(fa / 6f);
            p.plane(s.beamStart, fr.r, fr.u, 1.0 + 6.5 * ease(clamp01(fa / 5f)), 0, Pen.CORE, k);
            if (fa > 1.5f) {
                p.plane(s.beamStart, fr.r, fr.u, 1.0 + 9.0 * ease(clamp01((fa - 1.5f) / 5f)), 0, Pen.CYAN, 0.7f * (1f - clamp01((fa - 1.5f) / 5.5f)));
            }
        }
        double ex = s.ex;
        if (ex < 0) {
            return;
        }
        Vec3 g = s.P.add(0, 0.12, 0);
        Vec3 X = new Vec3(1, 0, 0);
        Vec3 Z = new Vec3(0, 0, 1);
        p.plane(g, X, Z, R * 0.3 + R * 1.5 * ease(clamp01((float) ex / 11f)), 0, Pen.CORE, 1f - clamp01((float) ex / 16f));
        double e2 = ex - 2;
        if (e2 >= 0) {
            p.plane(g, X, Z, R * 0.2 + R * 1.9 * ease(clamp01((float) e2 / 13f)), 0, Pen.CYAN, 0.85f * (1f - clamp01((float) e2 / 18f)));
        }
        double e3 = ex - 4;
        if (e3 >= 0) {
            p.plane(g, X, Z, R * 0.2 + R * 2.5 * ease(clamp01((float) e3 / 16f)), 0, Pen.BLUE, 0.8f * (1f - clamp01((float) e3 / 20f)));
        }
        Vec3 L = new Vec3(p.left.x(), p.left.y(), p.left.z());
        Vec3 U = new Vec3(p.up.x(), p.up.y(), p.up.z());
        p.plane(s.P, L, U, R * 0.5 + R * 1.2 * ease(clamp01((float) ex / 9f)), 0, Pen.WHITE, 0.9f * (1f - clamp01((float) ex / 13f)));
    }

    // ------------------------------------------------------------------ Quincy seals

    private void sealPhase(Pen p, S s) {
        BowFrame fr = s.fr;
        float t = s.t;
        float in = clamp01(t / 10f);
        float k = s.fired ? 1f - clamp01(s.fa / 6f) : 1f;
        double grow = s.fired ? 1.0 + 1.5 * ease(clamp01(s.fa / 6f)) : 1.0;
        if (k > 0.01f) {
            Vec3 c = fr.center.add(fr.f.scale(2.9));
            p.plane(c, fr.r, fr.u, 3.5 * ease(in) * grow, t * 0.06, Pen.CYAN, 0.85f * in * k);
            p.plane(c.add(fr.f.scale(0.3)), fr.r, fr.u, 2.3 * ease(in) * grow, -t * 0.09, Pen.CORE, 0.6f * in * k);
        }
        // sigil on the ground below the caster
        float gk = s.fired ? 1f - clamp01(s.fa / 12f) : 1f;
        if (gk > 0.01f) {
            Vec3 gp = s.casterPos.add(0, 0.06, 0);
            p.plane(gp, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 3.4 * ease(in), -t * 0.05, Pen.CYAN, 0.7f * in * gk);
            p.plane(gp.add(0, 0.01, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 2.0 * ease(in), t * 0.08, Pen.CORE, 0.5f * in * gk);
        }
    }

    // ------------------------------------------------------------------ explosion flip-book

    private void burstPhase(Pen p, S s) {
        double ex = s.ex;
        if (!s.fired || ex < 0) {
            return;
        }
        int idx = Math.min(15, (int) (ex * 0.62));
        float u0 = (idx % 4) / 4f;
        float v0 = (idx / 4) / 4f;
        double half = R * (0.9 + 0.9 * ease(clamp01((float) ex / 9f)));
        float a = 1f - smooth(clamp01((float) ((ex - 18) / 12.0)));
        double rot = s.seed * 0.7;
        p.billboard(s.P, half, rot, Pen.CYAN, a, u0, v0, u0 + 0.25f, v0 + 0.25f);
        int idx2 = Math.min(15, (int) (ex * 0.45));
        float u2 = (idx2 % 4) / 4f;
        float v2 = (idx2 / 4) / 4f;
        p.billboard(s.P, half * 0.62, -rot * 1.7, Pen.CORE, a * 0.85f, u2, v2, u2 + 0.25f, v2 + 0.25f);
    }

    // ------------------------------------------------------------------ sparkles

    private void sparklePhase(Pen p, S s) {
        BowFrame fr = s.fr;
        float t = s.t;
        // sparkles hugging the bow plus a drifting crystalline star field
        if (s.bowA > 0.02f && s.reveal > 0.05) {
            Random rnd = new Random(s.seed * 131L + 5);
            for (int i = 0; i < 64; i++) {
                double tt = (rnd.nextDouble() * 2 - 1) * s.reveal;
                double ang = rnd.nextDouble() * Math.PI * 2 + t * 0.03 * (0.5 + rnd.nextDouble());
                double rad = 0.15 + rnd.nextDouble() * 1.1;
                double size = 0.12 + rnd.nextDouble() * 0.34;
                float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.25 + rnd.nextDouble() * 0.3) + rnd.nextDouble() * 6.28);
                Vec3 pos = fr.bowPoint(tt, 1.0).add(fr.r.scale(Math.cos(ang) * rad)).add(fr.f.scale(Math.sin(ang) * rad));
                p.billboard(pos, size * (0.6 + 0.6 * tw), rnd.nextDouble() * 1.57 + t * 0.02, (i & 1) == 0 ? Pen.WHITE : Pen.CYAN, 0.95f * tw * s.bowA * (float) s.reveal);
            }
            for (int i = 0; i < 44; i++) {
                double rx = rnd.nextDouble() * 2 - 1;
                double ry = rnd.nextDouble() * 2 - 1;
                double rz = rnd.nextDouble() * 2 - 1;
                double dr = Math.sqrt(rx * rx + ry * ry + rz * rz);
                if (dr < 1.0E-3) {
                    continue;
                }
                double rr = (1.2 + rnd.nextDouble() * 3.8) * s.reveal;
                float drift = frac((float) (rnd.nextDouble() + t * 0.012 * (0.5 + rnd.nextDouble())));
                Vec3 pos = fr.center.add(rx / dr * rr, ry / dr * rr + drift * 1.4 - 0.7, rz / dr * rr);
                float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.2 + rnd.nextDouble() * 0.3) + rnd.nextDouble() * 6.28);
                p.billboard(pos, 0.10 + rnd.nextDouble() * 0.28, rnd.nextDouble() * 1.57, Pen.CORE, 0.8f * tw * s.bowA);
            }
        }
        // big star flare on the arrow head while charging
        if (s.arrowA > 0.01f) {
            Vec3 head = fr.nock(s.draw).add(fr.f.scale(arrowLen(s)));
            double cc = s.fired ? 1.0 : s.c;
            p.billboard(head, 0.35 + 2.2 * cc * cc, t * 0.04, Pen.WHITE, (0.4f + 0.6f * (float) cc) * s.arrowA);
            p.billboard(head, 0.2 + 1.2 * cc * cc, -t * 0.07 + 0.78, Pen.CYAN, 0.8f * s.arrowA);
        }
        if (!s.fired) {
            return;
        }
        float fa = s.fa;
        Random rnd = new Random(s.seed * 977L + 3);
        // sparkles flung along the beam
        if (fa < PfeilEntity.TRAVEL_TICKS + 16) {
            float bf = 1f - clamp01((fa - 2f) / (PfeilEntity.TRAVEL_TICKS + 14f));
            Vec3 line = s.P.subtract(s.beamStart);
            for (int i = 0; i < 56; i++) {
                double d = rnd.nextDouble();
                double ang = rnd.nextDouble() * Math.PI * 2;
                double rad = 0.4 + rnd.nextDouble() * 1.6;
                Vec3 pos = s.beamStart.add(line.scale(d)).add(fr.r.scale(Math.cos(ang) * rad)).add(fr.u.scale(Math.sin(ang) * rad));
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 0.6 + rnd.nextDouble() * 6.28);
                p.billboard(pos, 0.2 + rnd.nextDouble() * 0.4, rnd.nextDouble() * 1.57, Pen.CORE, tw * bf);
            }
        }
        // blast debris stars
        double ex = s.ex;
        if (ex >= 0 && ex < 32) {
            float df = 1f - clamp01((float) ex / 30f);
            for (int i = 0; i < 110; i++) {
                double th = rnd.nextDouble() * Math.PI * 2;
                double el = -0.15 + rnd.nextDouble() * 1.0;
                double sp = 0.35 + rnd.nextDouble() * 0.55;
                Vec3 dir = new Vec3(Math.cos(th) * Math.cos(el), Math.sin(el), Math.sin(th) * Math.cos(el));
                double dist = R * sp * (1.0 - Math.pow(1.0 - clamp01((float) ex / 22f), 2.0)) * 1.8;
                Vec3 pos = s.P.add(dir.scale(dist)).add(0, ex * 0.04 * rnd.nextDouble(), 0);
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 0.7 + rnd.nextDouble() * 6.28);
                p.billboard(pos, (0.2 + rnd.nextDouble() * 0.55) * (0.4 + 0.6 * df), rnd.nextDouble() * 1.57 + t * 0.03, (i % 3 == 0) ? Pen.WHITE : Pen.CYAN, tw * df);
            }
        }
    }

    // ------------------------------------------------------------------ math helpers

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static double ease(float x) {
        double k = 1.0 - clamp01(x);
        return 1.0 - k * k * k;
    }

    private static double easeInOut(float x) {
        double c = clamp01(x);
        return c * c * (3.0 - 2.0 * c);
    }

    private static float smooth(float x) {
        float c = clamp01(x);
        return c * c * (3f - 2f * c);
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }
}
