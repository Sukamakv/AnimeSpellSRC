package com.example.jjkspells.dante.client;

import com.example.jjkspells.dante.entity.StylishRankEntity;
import com.example.jjkspells.dante.spell.StylishRankSpell;
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
 * Draws "Stylish Rank": the rank letter beside the caster (pop and sparks on every rank up, a shrinking gold arc for the S timer),
 * the bullet hell disc (layered bullet patterns on a flat zone, not a dome) and the Hellfire Slash (layered flame beam, speed streaks,
 * blue-violet shards, wind-up flames). Glow stays low: thin lines, solid sprites and small glints.
 */
public class StylishRankRenderer extends EntityRenderer<StylishRankEntity> {
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final float[] SILVER = {0.85f, 0.88f, 0.95f};
    private static final float[] ICE = {0.6f, 0.85f, 1.0f};
    private static final float[] BLUE = {0.2f, 0.5f, 1.0f};
    private static final float[] VIOLET = {0.55f, 0.45f, 1.0f};
    private static final float[][] RANK_COL = {SILVER, ICE, BLUE, {0.2f, 0.7f, 1.0f}, Pen.GOLD, Pen.GOLD, Pen.GOLD};
    private static final float[][] BULLET_COL = {Pen.SCARLET, Pen.EMBER, Pen.GOLD, Pen.RED};
    private static final float[][] BULLET_COL_SS = {Pen.SCARLET, Pen.EMBER, Pen.GOLD, VIOLET};

    private Pen p;
    private float t;
    private Vec3 O;
    private Entity own;
    private StylishRankEntity ent;
    private int seed;

    public StylishRankRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(StylishRankEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(StylishRankEntity e) {
        return DanteRender.GLOW;
    }

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void add(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(DanteRender.additive(tex));
    }

    private void alpha(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(DanteRender.alpha(tex));
    }

    private void line(Vec3 a, Vec3 b, double w, float al, float[] col) {
        if (al <= 0.004f) {
            return;
        }
        p.ribbon(a, b, w * 2.4, al * 0.45f, 0f, 1f, col);
        p.ribbon(a, b, w, al, 0f, 1f, Pen.CORE);
    }

    @Override
    public void render(StylishRankEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        own = e.owner();
        if (t < 0 || own == null) {
            return;
        }
        ent = e;
        seed = e.getId() * 7919;
        O = own.getPosition(pt);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), e.getPosition(pt), camera.getPosition(), camera.getLeftVector(), camera.getUpVector());
        int ph = e.phase();
        double age = t - e.phaseTick();
        double fade = ph == StylishRankEntity.PH_END ? 1.0 - c01(age / StylishRankEntity.END_TICKS) : 1.0;

        if (ph == StylishRankEntity.PH_HELL || ph == StylishRankEntity.PH_SLASH) {
            alpha(buf, DanteRender.SHARD_BLUE);
            slashShards(age);
            alpha(buf, DanteRender.SHARD);
            slashShardsRed(age);
        }
        add(buf, DanteRender.MIST);
        groundMist(ph, age);
        add(buf, DanteRender.RING);
        rings(ph, age);
        add(buf, DanteRender.BEAM);
        beam(ph, age);
        add(buf, DanteRender.LINE);
        lines(ph, age, fade);
        add(buf, DanteRender.BULLET);
        bullets(ph, age);
        add(buf, DanteRender.GLINT);
        glints(ph, age);
        add(buf, DanteRender.GLOW);
        letterGlow(ph, age, fade);
        alpha(buf, DanteRender.RANK);
        letter(ph, age, fade);
    }

    // ------------------------------------------------------------------ letter

    private Vec3 letterCentre(float pt) {
        float yaw = own.getViewYRot(1.0f) * (float) (Math.PI / 180.0);
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return O.add(right.scale(2.6)).add(0, 2.4 + 0.08 * Math.sin(t * 0.12), 0);
    }

    private Vec3 camLeft() {
        return new Vec3(p.left.x(), p.left.y(), p.left.z());
    }

    private Vec3 camUp() {
        return new Vec3(p.up.x(), p.up.y(), p.up.z());
    }

    private double pop() {
        double a = t - ent.rankTick();
        if (ent.rankTick() == 0 && ent.rank() == 0) {
            a = t;
        }
        return a < 0 ? 0 : a;
    }

    private void letter(int ph, double age, double fade) {
        double a = pop();
        double scale = 1.0 + 0.7 * Math.exp(-a / 3.0) * Math.cos(a * 1.1);
        double alphaMul = ph >= StylishRankEntity.PH_HELL ? 0.45 : 1.0;
        float al = (float) (c01(t / 5.0) * fade * alphaMul);
        if (al <= 0.01f) {
            return;
        }
        double shake = a < 10 ? Math.exp(-a / 3.0) * 0.12 : 0;
        Vec3 c = letterCentre(0);
        Random r = new Random((long) (t * 3) + seed);
        c = c.add(camLeft().scale((r.nextDouble() - 0.5) * shake)).add(camUp().scale((r.nextDouble() - 0.5) * shake));
        Vec3 L = camLeft().scale(2.8 * scale);
        Vec3 U = camUp().scale(1.4 * scale);
        int i = Math.min(6, ent.rank());
        float u0 = (i % 2) * 0.5f;
        float v0 = (i / 2) * 0.25f;
        p.quad(c.add(L).add(U), c.subtract(L).add(U), c.subtract(L).subtract(U), c.add(L).subtract(U), u0, v0, u0 + 0.5f, v0 + 0.25f, Pen.WHITE, al);
    }

    private void letterGlow(int ph, double age, double fade) {
        if (ph >= StylishRankEntity.PH_HELL) {
            return;
        }
        double a = pop();
        float[] col = RANK_COL[Math.min(6, ent.rank())];
        Vec3 c = letterCentre(0);
        float al = (float) (c01(t / 5.0) * fade);
        p.billboard(c, 2.2 + 0.4 * Math.exp(-a / 4.0), 0, col, al * 0.22f);
        if (ent.rank() >= StylishRankEntity.RANK_S) {
            p.billboard(c, 3.4, t * 0.01, col, al * (0.12f + 0.06f * (float) Math.sin(t * 0.3)));
        }
    }

    // ------------------------------------------------------------------ rings / mist

    private void rings(int ph, double age) {
        Vec3 g = O.add(0, 0.08, 0);
        if (ph == StylishRankEntity.PH_HELL) {
            double R = ent.hellRadius();
            boolean strong = R > StylishRankSpell.HELL_RADIUS_S + 0.5;
            double on = ease(age / 8.0) * (1.0 - c01((age - (StylishRankEntity.HELL_TICKS - 14)) / 14.0));
            float[] col = strong ? VIOLET : Pen.EMBER;
            p.plane(g, X, Z, R, t * 0.012, col, (float) (on * 0.55));
            p.plane(g.add(0, 0.01, 0), X, Z, R * 0.66, -t * 0.02, Pen.SCARLET, (float) (on * 0.5));
            p.plane(g.add(0, 0.02, 0), X, Z, R * 0.33, t * 0.035, Pen.CORE, (float) (on * 0.4));
            if (age < 14) {
                p.plane(g.add(0, 0.03, 0), X, Z, R * (0.2 + 0.85 * ease(age / 12.0)), 0, Pen.CORE, (float) (0.8 * (1 - age / 14.0)));
            }
        }
        if (ph == StylishRankEntity.PH_SLASH) {
            if (age < StylishRankEntity.SLASH_WINDUP) {
                double k = age / StylishRankEntity.SLASH_WINDUP;
                p.plane(g, X, Z, 14.0 * (1.0 - 0.75 * k), t * 0.2, Pen.EMBER, (float) (0.25 + 0.5 * k));
                p.plane(g.add(0, 0.01, 0), X, Z, 7.0 * (1.0 - 0.6 * k), -t * 0.3, Pen.CORE, (float) (0.2 + 0.4 * k));
            } else {
                double rel = age - StylishRankEntity.SLASH_WINDUP;
                if (rel < 22) {
                    p.plane(g, X, Z, 6 + 34 * ease(rel / 16.0), 0, Pen.CORE, (float) (0.8 * (1 - rel / 22.0)));
                    p.plane(g.add(0, 0.01, 0), X, Z, 4 + 22 * ease(rel / 12.0), 1.0, Pen.EMBER, (float) (0.6 * (1 - rel / 22.0)));
                }
            }
        }
        if (ph == StylishRankEntity.PH_COUNTDOWN || ph == StylishRankEntity.PH_RANKING) {
            double a = pop();
            if (a < 12) {
                p.plane(g, X, Z, 1.5 + 5.0 * ease(a / 12.0), 0, RANK_COL[Math.min(6, ent.rank())], (float) (0.5 * (1 - a / 12.0)));
            }
        }
    }

    private void groundMist(int ph, double age) {
        if (ph == StylishRankEntity.PH_HELL) {
            double R = ent.hellRadius();
            double on = ease(age / 10.0) * (1.0 - c01((age - (StylishRankEntity.HELL_TICKS - 14)) / 14.0));
            for (int i = 0; i < 3; i++) {
                p.plane(O.add(0, 0.05 + 0.35 * i, 0), X, Z, R * (1.0 - 0.1 * i), t * 0.01 * (i % 2 == 0 ? 1 : -1) + i * 1.7, Pen.BLOOD, (float) (on * (0.3 - 0.07 * i)));
            }
        }
    }

    // ------------------------------------------------------------------ countdown arc + sparks

    private void lines(int ph, double age, double fade) {
        Vec3 c = letterCentre(0);
        Vec3 L = camLeft();
        Vec3 U = camUp();
        if (ph == StylishRankEntity.PH_COUNTDOWN) {
            double left = 1.0 - c01(age / StylishRankEntity.COUNTDOWN_TICKS);
            int segs = 56;
            double R = 2.2;
            float[] col = (left * 7.0 < 3.0 && ((int) (t / 3) % 2 == 0)) ? Pen.SCARLET : Pen.GOLD;
            for (int i = 0; i < segs; i++) {
                double f0 = (double) i / segs;
                double f1 = (double) (i + 1) / segs;
                if (f1 > left) {
                    break;
                }
                double a0 = Math.PI / 2 - f0 * Math.PI * 2;
                double a1 = Math.PI / 2 - f1 * Math.PI * 2;
                Vec3 p0 = c.add(L.scale(Math.cos(a0) * R * 1.0)).add(U.scale(Math.sin(a0) * R * 0.62));
                Vec3 p1 = c.add(L.scale(Math.cos(a1) * R * 1.0)).add(U.scale(Math.sin(a1) * R * 0.62));
                line(p0, p1, 0.05, (float) fade, col);
            }
            for (int s = 0; s < 7; s++) {
                double f = s / 7.0;
                if (f > left) {
                    continue;
                }
                double a0 = Math.PI / 2 - f * Math.PI * 2;
                Vec3 d = L.scale(Math.cos(a0)).add(U.scale(Math.sin(a0) * 0.62));
                line(c.add(d.scale(R * 0.9)), c.add(d.scale(R * 1.12)), 0.06, (float) fade, col);
            }
        }
        double a = pop();
        if (a < 9 && ph < StylishRankEntity.PH_HELL) {
            float[] col = RANK_COL[Math.min(6, ent.rank())];
            Random r = new Random(ent.rank() * 977L + seed);
            for (int i = 0; i < 18; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double d0 = 0.8 + r.nextDouble() * 0.8;
                double d1 = d0 + (0.6 + r.nextDouble() * 1.6) * ease(a / 9.0);
                Vec3 dir = L.scale(Math.cos(ang) * 1.4).add(U.scale(Math.sin(ang)));
                line(c.add(dir.scale(d0 + 1.2 * ease(a / 9.0))), c.add(dir.scale(d1 + 1.2 * ease(a / 9.0))), 0.04, (float) (1 - a / 9.0), col);
            }
        }
        if (ph == StylishRankEntity.PH_SLASH && age >= StylishRankEntity.SLASH_WINDUP) {
            speedStreaks(age - StylishRankEntity.SLASH_WINDUP);
        }
        if (ph == StylishRankEntity.PH_SLASH && age < StylishRankEntity.SLASH_WINDUP) {
            double k = age / StylishRankEntity.SLASH_WINDUP;
            Vec3 core = O.add(0, own.getBbHeight() * 0.65, 0);
            Random r = new Random(seed + 11);
            for (int i = 0; i < 26; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double el = (r.nextDouble() - 0.3) * 1.2;
                double dist = (3 + r.nextDouble() * 6) * (1.0 - k * 0.9);
                double ph0 = r.nextDouble();
                Vec3 d = new Vec3(Math.cos(ang) * Math.cos(el), Math.sin(el), Math.sin(ang) * Math.cos(el));
                line(core.add(d.scale(dist)), core.add(d.scale(dist * 0.55 + 0.2)), 0.05, (float) (0.4 + 0.6 * k), ph0 > 0.5 ? Pen.EMBER : Pen.GOLD);
            }
        }
    }

    // ------------------------------------------------------------------ bullet hell

    private void bullets(int ph, double age) {
        if (ph != StylishRankEntity.PH_HELL) {
            return;
        }
        double R = ent.hellRadius();
        boolean strong = R > StylishRankSpell.HELL_RADIUS_S + 0.5;
        float[][] cols = strong ? BULLET_COL_SS : BULLET_COL;
        double on = c01(age / 4.0) * (1.0 - c01((age - (StylishRankEntity.HELL_TICKS - 14)) / 14.0));
        if (on <= 0.01) {
            return;
        }
        double life = 36.0;
        double v = R / life;
        double size = 0.26 + R * 0.01;
        // layer 1: ring bursts every 6 ticks, twisting
        int count = (int) (16 + R * 1.6);
        int lastBurst = (int) Math.floor(age / 6.0);
        for (int k = Math.max(0, lastBurst - 6); k <= lastBurst; k++) {
            double ageB = age - k * 6.0;
            if (ageB < 0 || ageB > life) {
                continue;
            }
            double rad = v * ageB;
            double a0 = k * 0.37 + ageB * 0.015 * ((k % 2) * 2 - 1);
            float[] col = cols[k % cols.length];
            for (int i = 0; i < count; i++) {
                double ang = a0 + i * Math.PI * 2 / count;
                double h = 0.7 + 0.5 * (0.5 + 0.5 * Math.sin(ang * 3 + k));
                bullet(ang, rad, h, size, col, on, v * 0.0 + 1.6);
            }
        }
        // layer 2: six spiral arms, continuous stream
        int arms = 6;
        for (int arm = 0; arm < arms; arm++) {
            float[] col = cols[(arm + 2) % cols.length];
            for (int e = (int) Math.floor(age / 2.0); e >= 0 && (age - e * 2.0) <= 44.0; e--) {
                double ageB = age - e * 2.0;
                double rad = v * 1.15 * ageB;
                if (rad > R) {
                    continue;
                }
                double ang = e * 0.16 + arm * Math.PI * 2 / arms + ageB * 0.01;
                bullet(ang, rad, 1.25, size * 0.9, col, on, 1.2);
            }
        }
        // layer 3: counter-rotating outer ring of heavy orbs near the edge
        int orbs = (int) (8 + R * 0.9);
        for (int i = 0; i < orbs; i++) {
            double ang = -age * 0.05 + i * Math.PI * 2 / orbs;
            double rad = R * (0.9 + 0.05 * Math.sin(age * 0.2 + i));
            bullet(ang, rad, 1.0 + 0.3 * Math.sin(age * 0.15 + i), size * 1.5, cols[i % cols.length], on * 0.9, 0);
        }
    }

    private void bullet(double ang, double rad, double h, double size, float[] col, double on, double tail) {
        Vec3 pos = O.add(Math.cos(ang) * rad, h, Math.sin(ang) * rad);
        p.billboard(pos, size, 0, col, (float) (on * 0.95));
        p.billboard(pos, size * 0.5, 0, Pen.WHITE, (float) on);
    }

    // ------------------------------------------------------------------ hellfire slash

    private void beam(int ph, double age) {
        if (ph != StylishRankEntity.PH_SLASH || age < StylishRankEntity.SLASH_WINDUP) {
            return;
        }
        double rel = age - StylishRankEntity.SLASH_WINDUP;
        double len = StylishRankSpell.SLASH_LENGTH;
        double head = len * ease(rel / 5.0);
        double env = ease(rel / 4.0) * (1.0 - c01((rel - (StylishRankEntity.SLASH_TICKS - 14)) / 14.0));
        if (env <= 0.01) {
            return;
        }
        Vec3 o = ent.slashOrigin();
        Vec3 d = ent.slashDir();
        Vec3 sideH = d.cross(new Vec3(0, 1, 0));
        sideH = sideH.lengthSqr() < 1.0E-6 ? X : sideH.normalize();
        Vec3 sideV = d.cross(sideH).normalize();
        this.sideHc = sideH;
        this.sideVc = sideV;
        double pulse = 1.0 + 0.05 * Math.sin(t * 1.3);
        int segs = 12;
        double segLen = head / segs;
        double[] widths = {18.0, 12.5, 7.5, 3.4};
        float[][] cols = {Pen.SCARLET, Pen.EMBER, Pen.GOLD, Pen.WHITE};
        float[] alphas = {0.5f, 0.75f, 0.9f, 1.0f};
        double[] speeds = {0.16, 0.24, 0.34, 0.5};
        for (int layer = 0; layer < 4; layer++) {
            double f = (-(t * speeds[layer])) % 1.0;
            if (f < 0) {
                f += 1.0;
            }
            for (int s = 0; s < segs; s++) {
                double l0 = s * segLen;
                double l1 = (s + 1) * segLen;
                double w0 = widths[layer] * pulse * env * taper(l0 / len);
                double w1 = widths[layer] * pulse * env * taper(l1 / len);
                Vec3 a = o.add(d.scale(l0));
                Vec3 b = o.add(d.scale(l1));
                float al = alphas[layer] * (float) env;
                strip(a, b, w0, w1, al, f, cols[layer], 0);
                if (layer >= 1) {
                    strip(a, b, w0 * 0.9, w1 * 0.9, al * 0.55f, f, cols[layer], 1);
                    strip(a, b, w0 * 0.9, w1 * 0.9, al * 0.55f, f, cols[layer], 2);
                }
            }
        }
        // ground scorch
        if (Math.abs(d.y) < 0.5) {
            Vec3 h = new Vec3(d.x, 0, d.z);
            Vec3 hn = h.lengthSqr() < 1.0E-6 ? Z : h.normalize();
            Vec3 side = new Vec3(-hn.z, 0, hn.x);
            Vec3 a = new Vec3(o.x, O.y + 0.06, o.z);
            Vec3 b = a.add(hn.scale(head));
            p.ribbonSide(a, b, side, 17 * env, 17 * env, (float) (0.45 * env), (float) (0.35 * env), 0f, 1f, Pen.CRIM);
        }
    }

    private Vec3 sideHc = X;
    private Vec3 sideVc = Z;

    private static double taper(double f) {
        double start = 0.55 + 0.45 * c01(f / 0.22);
        double end = 1.0 - 0.7 * c01((f - 0.8) / 0.2);
        return start * end;
    }

    /** Beam strip with the periodic texture scrolled by f. mode 0 camera facing, 1 horizontal, 2 vertical. */
    private void strip(Vec3 a, Vec3 b, double w0, double w1, float al, double f, float[] col, int mode) {
        Vec3 mid = a.add(b.subtract(a).scale(1.0 - f));
        part(a, mid, w0, w0 + (w1 - w0) * (1.0 - f), al, (float) f, 1f, col, mode);
        part(mid, b, w0 + (w1 - w0) * (1.0 - f), w1, al, 0f, (float) f, col, mode);
    }

    private void part(Vec3 a, Vec3 b, double w0, double w1, float al, float uA, float uB, float[] col, int mode) {
        if (mode == 0) {
            p.ribbon(a, b, w0, w1, al, al, uA, uB, col);
        } else {
            p.ribbonSide(a, b, mode == 1 ? sideHc : sideVc, w0, w1, al, al, uA, uB, col);
        }
    }

    private void speedStreaks(double rel) {
        double env = ease(rel / 4.0) * (1.0 - c01((rel - (StylishRankEntity.SLASH_TICKS - 12)) / 12.0));
        if (env <= 0.01) {
            return;
        }
        Vec3 o = ent.slashOrigin();
        Vec3 d = ent.slashDir();
        Vec3 sh = d.cross(new Vec3(0, 1, 0));
        sh = sh.lengthSqr() < 1.0E-6 ? X : sh.normalize();
        Vec3 sv = d.cross(sh).normalize();
        double len = StylishRankSpell.SLASH_LENGTH;
        Random r = new Random(seed + 4242);
        for (int i = 0; i < 46; i++) {
            double base = r.nextDouble();
            double speed = 4.5 + r.nextDouble() * 5.5;
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = Math.sqrt(r.nextDouble()) * 9.5;
            double slen = 8 + r.nextDouble() * 24;
            double pos = ((base * len + rel * speed) % len);
            Vec3 off = sh.scale(Math.cos(ang) * rad).add(sv.scale(Math.sin(ang) * rad));
            Vec3 a = o.add(d.scale(pos)).add(off);
            Vec3 b = o.add(d.scale(Math.max(0, pos - slen))).add(off);
            line(b, a, 0.12 + r.nextDouble() * 0.12, (float) (0.8 * env), r.nextBoolean() ? Pen.GOLD : Pen.EMBER);
        }
    }

    private static final float[][] SHARD_CELLS = {{0f, 0f}, {0.5f, 0f}, {0f, 0.5f}, {0.5f, 0.5f}};

    private void slashShards(double age) {
        shardCloud(age, 44, 1);
    }

    private void slashShardsRed(double age) {
        shardCloud(age, 14, 2);
    }

    private void shardCloud(double age, int n, int salt) {
        if (age < StylishRankEntity.SLASH_WINDUP || ent.phase() != StylishRankEntity.PH_SLASH) {
            return;
        }
        double rel = age - StylishRankEntity.SLASH_WINDUP;
        double env = ease(rel / 4.0) * (1.0 - c01((rel - (StylishRankEntity.SLASH_TICKS - 10)) / 10.0));
        if (env <= 0.01) {
            return;
        }
        Vec3 o = ent.slashOrigin();
        Vec3 d = ent.slashDir();
        Vec3 sh = d.cross(new Vec3(0, 1, 0));
        sh = sh.lengthSqr() < 1.0E-6 ? X : sh.normalize();
        Vec3 sv = d.cross(sh).normalize();
        double len = StylishRankSpell.SLASH_LENGTH;
        Random r = new Random(seed + 99L * salt);
        for (int i = 0; i < n; i++) {
            double base = r.nextDouble();
            double speed = 2.2 + r.nextDouble() * 3.0;
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 3 + r.nextDouble() * 9;
            double size = 0.7 + r.nextDouble() * 1.7;
            double spin = (r.nextDouble() - 0.5) * 0.3;
            int cell = r.nextInt(4);
            double pos = ((base * len * 0.9 + rel * speed) % (len * 0.9));
            double spread = rad * (0.6 + 0.4 * pos / len);
            Vec3 c = o.add(d.scale(pos)).add(sh.scale(Math.cos(ang) * spread)).add(sv.scale(Math.sin(ang) * spread));
            float al = (float) (env * 0.95);
            p.billboard(c, size, t * spin + i, Pen.WHITE, al, SHARD_CELLS[cell][0], SHARD_CELLS[cell][1], SHARD_CELLS[cell][0] + 0.5f, SHARD_CELLS[cell][1] + 0.5f);
        }
    }

    // ------------------------------------------------------------------ flashes

    private void glints(int ph, double age) {
        if (ph == StylishRankEntity.PH_SLASH) {
            Vec3 o = ent.slashOrigin();
            Vec3 d = ent.slashDir();
            if (age < StylishRankEntity.SLASH_WINDUP) {
                double k = age / StylishRankEntity.SLASH_WINDUP;
                p.billboard(o, 0.6 + 3.2 * k * k, t * 0.2, Pen.CORE, (float) (0.3 + 0.6 * k));
            } else {
                double rel = age - StylishRankEntity.SLASH_WINDUP;
                double env = ease(rel / 4.0) * (1.0 - c01((rel - (StylishRankEntity.SLASH_TICKS - 14)) / 14.0));
                p.billboard(o.add(d.scale(2)), 12 * env, 0, Pen.CORE, (float) (0.9 * (1.0 - c01(rel / 10.0)) + 0.25 * env));
                double len = StylishRankSpell.SLASH_LENGTH;
                p.billboard(o.add(d.scale(len * ease(rel / 5.0))), 10 * env, 0, Pen.HOT, (float) (0.7 * env));
                Random r = new Random(seed + 5);
                for (int i = 0; i < 30; i++) {
                    double pos = ((r.nextDouble() * len + rel * (3 + r.nextDouble() * 4)) % len);
                    Vec3 off = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).scale(16);
                    p.billboard(o.add(d.scale(pos)).add(off), 0.35 + r.nextDouble() * 0.4, 0, Pen.GOLD, (float) (0.9 * env));
                }
            }
        }
        if (ph == StylishRankEntity.PH_HELL && age < 8) {
            p.billboard(O.add(0, 1.2, 0), 6 * (1 - age / 8.0) + 1, 0, Pen.CORE, (float) (0.6 * (1 - age / 8.0)));
        }
    }
}
