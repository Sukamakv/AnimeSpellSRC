package com.example.jjkspells.irontomb.client;

import com.example.jjkspells.irontomb.PhainonAttack;
import com.example.jjkspells.irontomb.entity.PhainonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/**
 * Everything around Phainon that is not the player model: the golden holy wing, the dark thorn wing, the sun crown,
 * orbiting sigils, the ground seal and the effect of every attack and scripted moment. All of it is drawn from the
 * synced entity state (attack id + tick, event id + tick, aim, seed), so every client sees the same thing.
 */
public final class PhainonFx {
    private static final float[] GOLD = {1f, 0.80f, 0.30f}, CREAM = {1f, 0.93f, 0.68f}, WHITE = {1f, 1f, 1f}, AMBER = {1f, 0.62f, 0.16f};

    private PhainonFx() {
    }

    // ================================================================== context
    static final class C {
        final PoseStack ps;
        final MultiBufferSource buf;
        final Vec3 cam, ePos, aim, from;
        final PhainonEntity e;
        final Level level;
        final float age, pt, t, et;
        final int atk, ev, phase, seed;
        final double yaw;
        final boolean awake;

        C(PoseStack ps, MultiBufferSource buf, Vec3 cam, PhainonEntity e, float pt) {
            this.ps = ps;
            this.buf = buf;
            this.cam = cam;
            this.e = e;
            this.level = e.level();
            this.pt = pt;
            this.age = e.tickCount + pt;
            this.atk = e.getAttack();
            this.ev = e.getEvent();
            this.phase = e.getPhase();
            this.t = atk == 0 ? 0f : e.getAtkT() + pt;
            this.et = ev == 0 ? 0f : e.getEvT() + pt;
            this.ePos = e.getPosition(pt);
            this.aim = e.getAim().subtract(ePos);
            this.from = e.getFrom().subtract(ePos);
            this.seed = e.getSeed();
            this.yaw = -Math.toRadians(Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot));
            this.awake = e.isAwake();
        }

        /** Body-local (+Z forward, +X his left) to boss-relative world axes. */
        Vec3 w(double x, double y, double z) {
            double cs = Math.cos(yaw), sn = Math.sin(yaw);
            return new Vec3(x * cs + z * sn, y, -x * sn + z * cs);
        }

        VertexConsumer add(String tex) {
            return buf.getBuffer(FxTypes.add(tex));
        }

        VertexConsumer alpha(String tex) {
            return buf.getBuffer(FxTypes.alpha(tex));
        }

        void billboard(String tex, Vec3 pos, float size, float roll, float[] col, float a) {
            if (a < 0.004f || size <= 0.001f) {
                return;
            }
            ps.pushPose();
            ps.translate(pos.x, pos.y, pos.z);
            ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            if (roll != 0f) {
                ps.mulPose(com.mojang.math.Axis.ZP.rotation(roll));
            }
            Fx.quadXY(add(tex), ps, size, size, col[0], col[1], col[2], a);
            ps.popPose();
        }

        void frame(String tex, Vec3 pos, float size, int frame, float roll, float[] col, float a) {
            if (a < 0.004f) {
                return;
            }
            ps.pushPose();
            ps.translate(pos.x, pos.y, pos.z);
            ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            if (roll != 0f) {
                ps.mulPose(com.mojang.math.Axis.ZP.rotation(roll));
            }
            Fx.flipXY(add(tex), ps, size, size, frame, 4, col[0], col[1], col[2], a);
            ps.popPose();
        }

        void flat(String tex, Vec3 pos, float half, float spin, float[] col, float a) {
            if (a < 0.004f || half <= 0.001f) {
                return;
            }
            ps.pushPose();
            ps.translate(pos.x, pos.y, pos.z);
            if (spin != 0f) {
                ps.mulPose(com.mojang.math.Axis.YP.rotation(spin));
            }
            Fx.quadXZ(add(tex), ps, half, 0f, col[0], col[1], col[2], a);
            ps.popPose();
        }

        void flatFrame(Vec3 pos, float half, int frame, float spin, float[] col, float a) {
            if (a < 0.004f) {
                return;
            }
            ps.pushPose();
            ps.translate(pos.x, pos.y, pos.z);
            if (spin != 0f) {
                ps.mulPose(com.mojang.math.Axis.YP.rotation(spin));
            }
            Fx.flipXZ(add("ph_glyphs"), ps, half, 0f, frame, 4, col[0], col[1], col[2], a);
            ps.popPose();
        }

        void beam(Vec3 a, Vec3 b, float width, float scroll, float[] col, float al) {
            if (al < 0.004f || width <= 0.001f) {
                return;
            }
            Fx.ribbon(add("ph_beam"), ps, a, b, cam, width, age * scroll, 0.25f, col[0], col[1], col[2], al);
        }

        /** Where the ground is below a boss-relative point (boss-relative y). */
        double groundY(Vec3 rel) {
            Vec3 a = ePos.add(rel.x, rel.y + 1.0, rel.z);
            BlockHitResult h = level.clip(new ClipContext(a, a.add(0, -80, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            return h.getType() == HitResult.Type.MISS ? rel.y - 60 : h.getLocation().y - ePos.y;
        }
    }

    // ================================================================== entry
    public static void draw(PoseStack ps, MultiBufferSource buf, Vec3 cam, PhainonEntity e, float pt) {
        C c = new C(ps, buf, cam, e, pt);
        if (c.cam.lengthSqr() > 280.0 * 280.0) {
            return;
        }
        float vis = visibility(c);
        if (vis > 0.01f) {
            wings(c);
            corona(c, vis);
            sigils(c, vis);
            motes(c, vis);
            bodyGlow(c, vis);
        }
        if (c.awake) {
            groundSeal(c, vis);
        }
        switch (c.atk) {
            case PhainonAttack.LANCE -> lance(c);
            case PhainonAttack.DASH -> dash(c);
            case PhainonAttack.FEATHER -> featherRain(c);
            case PhainonAttack.SUNFALL -> sunfall(c);
            case PhainonAttack.HALO -> halo(c);
            case PhainonAttack.SWEEP -> sweep(c);
            case PhainonAttack.JUDGMENT -> judgment(c);
            case PhainonAttack.STAGGER -> stagger(c);
            default -> {
            }
        }
        switch (c.ev) {
            case PhainonAttack.EV_INTRO -> intro(c);
            case PhainonAttack.EV_PHASE2 -> phaseChange(c, 2);
            case PhainonAttack.EV_PHASE3 -> phaseChange(c, 3);
            case PhainonAttack.EV_DEATH -> death(c);
            default -> {
            }
        }
    }

    /** 0..1: how much of his glow is shown (dormant: a faint ember, death: fades out at the end). */
    private static float visibility(C c) {
        if (!c.awake) {
            return 0.25f;
        }
        if (c.ev == PhainonAttack.EV_DEATH) {
            return 1f - Fx.smooth(Fx.ramp(c.et, 100, 116));
        }
        if (c.ev == PhainonAttack.EV_INTRO) {
            return Fx.smooth(Fx.ramp(c.et, 4, 40));
        }
        return 1f;
    }

    private static float flare(C c) {
        float f = 0f;
        float t = c.t;
        switch (c.atk) {
            case PhainonAttack.FEATHER -> f = Fx.smooth(Fx.ramp(t, 0, 14)) * (1f - Fx.smooth(Fx.ramp(t, 86, 102)));
            case PhainonAttack.SWEEP -> f = Fx.smooth(Fx.ramp(t, 0, 20)) * (1f - Fx.smooth(Fx.ramp(t, 98, 112)));
            case PhainonAttack.SUNFALL -> f = Fx.smooth(Fx.ramp(t, 4, 40)) * (1f - Fx.smooth(Fx.ramp(t, 86, 110)));
            case PhainonAttack.JUDGMENT -> f = Fx.smooth(Fx.ramp(t, 4, 40)) * (1f - Fx.smooth(Fx.ramp(t, 120, 160)));
            case PhainonAttack.HALO -> f = Fx.smooth(Fx.ramp(t, 0, 18)) * (1f - Fx.smooth(Fx.ramp(t, 90, 106)));
            case PhainonAttack.LANCE -> f = Fx.smooth(Fx.ramp(t, 8, 24)) * (1f - Fx.smooth(Fx.ramp(t, 40, 60))) * 0.6f;
            case PhainonAttack.STAGGER -> f = -1f * Fx.smooth(Fx.ramp(t, 0, 10)) * (1f - Fx.smooth(Fx.ramp(t, 66, 80)));
            default -> {
            }
        }
        if (c.ev == PhainonAttack.EV_PHASE2 || c.ev == PhainonAttack.EV_PHASE3) {
            f = Fx.window(c.et, 20, 40, 60, 90);
        }
        return f;
    }

    // ================================================================== wings
    private static void wings(C c) {
        float open;
        if (!c.awake) {
            open = 0.12f;
        } else if (c.ev == PhainonAttack.EV_INTRO) {
            open = Mth.lerp(Fx.smooth(Fx.ramp(c.et, 18, 70)), 0.12f, 1f);
        } else if (c.ev == PhainonAttack.EV_DEATH) {
            open = 1f - Fx.smooth(Fx.ramp(c.et, 44, 58));
        } else {
            open = 1f;
        }
        float fl = flare(c);
        float flap = (float) Math.sin(c.age * 0.07) * 4f + fl * 16f + (fl < 0 ? fl * 30f : 0f);
        float goldScale = c.phase >= 3 ? 1.2f : c.phase == 2 ? 1.05f : 0.9f;
        float darkScale = c.phase >= 3 ? 1.2f : c.phase == 2 ? 1.0f : 0.5f;
        if (c.ev == PhainonAttack.EV_PHASE2) {
            darkScale = Mth.lerp(Fx.smooth(Fx.ramp(c.et, 10, 52)), 0.5f, 1.0f);
        } else if (c.ev == PhainonAttack.EV_PHASE3) {
            goldScale = Mth.lerp(Fx.smooth(Fx.ramp(c.et, 20, 70)), 1.05f, 1.2f);
            darkScale = Mth.lerp(Fx.smooth(Fx.ramp(c.et, 20, 70)), 1.0f, 1.2f);
        }
        float extra = c.phase >= 3 ? 1f : 0f;
        if (c.ev == PhainonAttack.EV_PHASE3) {
            extra = Fx.smooth(Fx.ramp(c.et, 36, 84));
        } else if (c.ev == PhainonAttack.EV_PHASE2) {
            extra = 0f;
        }
        // the golden holy wing sits on his right (the viewer's left in the key art), the dark thorn wing on his left
        gold(c, -1, 0.20, 1.78, -0.28, goldScale, open, 0f, flap, 1f);
        dark(c, 1, 0.20, 1.78, -0.28, darkScale, open, 0f, flap * 0.7f, 1f);
        if (extra > 0.02f) {
            gold(c, -1, 0.16, 1.55, -0.26, 0.58f * extra, open, -42f, flap, 0.9f);
            gold(c, 1, 0.16, 1.55, -0.26, 0.58f * extra, open, -42f, flap, 0.9f);
        }
    }

    private static void feather(C c, VertexConsumer vc, Vec3 b, Vec3 d, Vec3 p, float len, float wid, float r, float g, float bl, float a) {
        Matrix4f m = c.ps.last().pose();
        Vec3 hw = p.scale(wid * 0.5);
        Vec3 tip = b.add(d.scale(len));
        quad(vc, m, b.subtract(hw), 0, 0, b.add(hw), 1, 0, tip.add(hw), 1, 1, tip.subtract(hw), 0, 1, r, g, bl, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, float u0, float v0, Vec3 p1, float u1, float v1,
                             Vec3 p2, float u2, float v2, Vec3 p3, float u3, float v3, float r, float g, float b, float a) {
        vc.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, b, a).uv(u0, v0).endVertex();
        vc.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, a).uv(u1, v1).endVertex();
        vc.vertex(m, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, b, a).uv(u2, v2).endVertex();
        vc.vertex(m, (float) p3.x, (float) p3.y, (float) p3.z).color(r, g, b, a).uv(u3, v3).endVertex();
    }

    /** The golden holy wing: three overlapping rows of long feathers fanned out and swept back. */
    private static void gold(C c, int side, double sx, double sy, double sz, float scale, float open, float shift, float flap, float alpha) {
        if (scale < 0.02f || open < 0.02f || alpha < 0.02f) {
            return;
        }
        double sweep = 0.55;
        Vec3 o = c.w(side * Math.cos(sweep), 0, -Math.sin(sweep));
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 root = c.w(side * Math.abs(sx), sy, sz);
        // rows: count, alpha0, alpha1 (degrees), length mul, width, colour
        float[][] rows = {
                {7, -20, 58, 0.40f, 0.40f, 1f, 0.72f, 0.24f},
                {8, -30, 66, 0.66f, 0.54f, 1f, 0.82f, 0.34f},
                {9, -40, 76, 1.00f, 0.66f, 1f, 0.92f, 0.52f}
        };
        Vec3[] tips = new Vec3[9];
        VertexConsumer vc = c.add("ph_feather");
        for (float[] row : rows) {
            int n = (int) row[0];
            for (int i = 0; i < n; i++) {
                float f = n == 1 ? 0f : i / (float) (n - 1);
                double aOpen = row[1] + f * (row[2] - row[1]);
                double aClosed = 80 - f * 6;
                double a = Math.toRadians(Mth.lerp(open, (float) aClosed, (float) aOpen) + shift + flap * (0.4f + f) + Math.sin(c.age * 0.09 + i * 0.6 + side) * 1.6);
                float len = (float) ((3.2 + 1.8 * Math.sin(Math.PI * (0.12 + 0.82 * f))) * row[3] * scale * (0.5 + 0.5 * open));
                Vec3 d = o.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                Vec3 p = o.scale(-Math.sin(a)).add(up.scale(Math.cos(a)));
                Vec3 base = root.add(o.scale(0.12 * scale));
                feather(c, vc, base, d, p, len, row[4] * scale, row[5] * alpha, row[6] * alpha, row[7] * alpha, 0.8f * alpha);
                if (row[3] == 1.00f && i < tips.length) {
                    tips[i] = base.add(d.scale(len));
                }
            }
        }
        // glowing rim along the tips of the long row, and a soft glow where the wing joins the back
        for (int i = 0; i + 1 < 9; i++) {
            if (tips[i] != null && tips[i + 1] != null) {
                Fx.ribbon(c.add("ph_beam"), c.ps, tips[i], tips[i + 1], c.cam, 0.10f * scale, 0f, 0.5f, 1f, 0.95f, 0.75f, 0.8f * alpha);
            }
        }
        for (int i = 0; i < 9; i += 2) {
            if (tips[i] != null) {
                c.billboard("ph_star", tips[i], 0.5f * scale, 0f, CREAM, 0.7f * alpha * (0.7f + 0.3f * (float) Math.sin(c.age * 0.2 + i)));
            }
        }
        c.billboard("ph_glow", root.add(o.scale(2.2 * scale)).add(0, 0.9 * scale, 0), 3.4f * scale, 0f, GOLD, 0.35f * alpha * open);
    }

    /** The dark thorn wing: jagged violet blades with a golden rim (alpha blended, so it reads as solid). */
    private static void dark(C c, int side, double sx, double sy, double sz, float scale, float open, float shift, float flap, float alpha) {
        if (scale < 0.02f || open < 0.02f) {
            return;
        }
        double sweep = 0.5;
        Vec3 o = c.w(side * Math.cos(sweep), 0, -Math.sin(sweep));
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 root = c.w(side * Math.abs(sx), sy, sz);
        float[][] rows = {
                {6, -46, 50, 0.55f, 0.66f, 0.62f, 0.62f, 0.9f},
                {7, -58, 62, 1.00f, 0.95f, 1f, 1f, 1f}
        };
        VertexConsumer vc = c.alpha("ph_thorn");
        for (float[] row : rows) {
            int n = (int) row[0];
            for (int i = 0; i < n; i++) {
                float f = i / (float) (n - 1);
                double aOpen = row[1] + f * (row[2] - row[1]);
                double aClosed = 82 - f * 5;
                double a = Math.toRadians(Mth.lerp(open, (float) aClosed, (float) aOpen) + shift + flap * (0.4f + f) + Math.sin(c.age * 0.07 + i * 0.8) * 1.4);
                float len = (float) ((2.5 + 3.0 * Math.sin(Math.PI * (0.1 + 0.8 * f))) * row[3] * scale * (0.5 + 0.5 * open));
                Vec3 d = o.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                Vec3 p = o.scale(-Math.sin(a)).add(up.scale(Math.cos(a)));
                feather(c, vc, root, d, p, len, row[4] * scale, row[5], row[6], row[7], 0.97f * alpha);
            }
        }
        c.billboard("ph_glow", root.add(o.scale(2.4 * scale)).add(0, 1.0 * scale, 0), 3.0f * scale, 0f, new float[]{0.7f, 0.45f, 1f}, 0.18f * open * alpha);
    }

    // ================================================================== crown, sigils, motes, glow
    private static Vec3 head(C c) {
        return c.w(0, 2.08, 0);
    }

    private static void corona(C c, float vis) {
        float fl = flare(c);
        float size = (c.phase >= 3 ? 4.6f : c.phase == 2 ? 3.6f : 2.8f) * (1f + 0.25f * Math.max(fl, 0f));
        if (fl < 0) {
            size *= 0.45f;
        }
        if (c.ev == PhainonAttack.EV_PHASE3) {
            size *= 1f + 0.5f * Fx.window(c.et, 30, 50, 60, 90);
        }
        Vec3 h = head(c);
        Vec3 away = h.subtract(c.cam);
        away = away.lengthSqr() < 1e-6 ? Vec3.ZERO : away.normalize().scale(0.6);
        Vec3 p = h.add(away);
        float pulse = 0.9f + 0.1f * (float) Math.sin(c.age * 0.15);
        c.billboard("ph_glow", p, size * 1.25f, 0f, GOLD, 0.6f * vis * pulse);
        c.billboard("ph_corona", p, size, c.age * 0.01f, CREAM, 0.95f * vis);
        c.billboard("ph_corona", p, size * 1.45f, -c.age * 0.006f, GOLD, 0.45f * vis);
        c.billboard("ph_star", p, size * 0.9f, c.age * 0.004f, WHITE, 0.5f * vis);
        if (c.phase >= 3) {
            c.billboard("ph_corona", p, size * 2.4f, c.age * 0.003f, AMBER, 0.22f * vis);
        }
    }

    private static void sigils(C c, float vis) {
        int n = c.phase >= 3 ? 12 : c.phase == 2 ? 8 : 5;
        float fl = Math.max(flare(c), 0f);
        for (int i = 0; i < n; i++) {
            double ang = c.age * 0.012 * (i % 2 == 0 ? 1 : -1) + i * Math.PI * 2 / n;
            double r = 3.8 + 0.9 * Math.sin(c.age * 0.03 + i) + 1.2 * fl;
            double y = 1.7 + 1.5 * Math.sin(c.age * 0.02 * (1 + i % 3) + i * 1.7) + (i % 3) * 0.5;
            Vec3 p = new Vec3(Math.cos(ang) * r, y, Math.sin(ang) * r);
            float tw = 0.65f + 0.35f * (float) Math.sin(c.age * 0.12 + i * 2.1);
            c.frame("ph_glyphs", p, 0.6f * (1f + 0.4f * fl), (i * 5) % 16, (float) Math.sin(c.age * 0.01 + i) * 0.2f, GOLD, 0.95f * vis * tw);
            c.billboard("ph_star", p, 1.0f, 0f, CREAM, 0.45f * vis * tw);
        }
    }

    private static void motes(C c, float vis) {
        for (int i = 0; i < 26; i++) {
            float hsh = Fx.rnd(i * 13 + 5);
            double speed = 0.012 + Fx.rnd(i * 7 + 1) * 0.02;
            double y = (c.age * speed + hsh * 6.0) % 6.0;
            double r = 0.8 + Fx.rnd(i * 3 + 2) * 3.4;
            double a = hsh * Math.PI * 2 + c.age * 0.01 * (i % 2 == 0 ? 1 : -1);
            float size = 0.10f + Fx.rnd(i * 11) * 0.16f;
            float al = (float) Math.sin(Math.PI * y / 6.0);
            c.billboard("ph_glow", new Vec3(Math.cos(a) * r, 0.2 + y, Math.sin(a) * r), size, 0f, CREAM, 0.9f * al * vis);
        }
    }

    private static void bodyGlow(C c, float vis) {
        c.billboard("ph_glow", c.w(0, 1.45, 0).add(c.w(0, 0, -0.5)), 2.4f, 0f, GOLD, 0.22f * vis);
    }

    /** A rotating golden seal on the floor under him; it flares during attacks. */
    private static void groundSeal(C c, float vis) {
        double gy = c.groundY(Vec3.ZERO) + 0.07;
        if (gy < -70) {
            return;
        }
        float fl = Math.max(flare(c), 0f);
        float R = 3.2f + 1.6f * fl;
        float a = (0.35f + 0.5f * fl) * Math.max(vis, 0.3f);
        if (c.ev == PhainonAttack.EV_DEATH) {
            a *= 1f - Fx.smooth(Fx.ramp(c.et, 90, 110));
        }
        Vec3 p = new Vec3(0, gy, 0);
        c.flat("ph_ring", p, R / 0.92f, c.age * 0.02f, GOLD, a);
        c.flat("ph_ring", p.add(0, 0.01, 0), R * 0.62f / 0.92f, -c.age * 0.03f, CREAM, a * 0.8f);
        for (int i = 0; i < 10; i++) {
            double an = i * Math.PI * 2 / 10 + c.age * 0.02;
            c.flatFrame(p.add(Math.cos(an) * R * 0.8, 0.02, Math.sin(an) * R * 0.8), 0.5f, (i * 3) % 16, (float) -an, GOLD, a * 0.9f);
        }
        c.flat("ph_glow", p.add(0, 0.03, 0), R * 1.2f, 0f, GOLD, a * 0.5f);
    }

    // ================================================================== helpers for attack effects
    private static Vec3 groundPt(C c, Vec3 rel) {
        return new Vec3(rel.x, c.groundY(rel) + 0.08, rel.z);
    }

    private static void shock(C c, Vec3 centre, float k, double maxR, float[] col, float a) {
        if (k <= 0f || k >= 1f) {
            return;
        }
        double r = maxR * Fx.ease(k);
        c.flat("ph_ring", centre, (float) (r / 0.92), 0f, col, a * (1f - k));
        c.flat("ph_ring", centre.add(0, 0.02, 0), (float) (r * 0.8 / 0.92), 0f, WHITE, a * 0.5f * (1f - k));
    }

    private static void sphere(C c, Vec3 pos, float radius, float[] col, float a) {
        if (radius < 0.02f) {
            return;
        }
        c.ps.pushPose();
        c.ps.translate(pos.x, pos.y, pos.z);
        Fx.sphere(c.add("ph_plasma"), c.ps, radius, 12, 18, c.age * 0.008f, col[0], col[1], col[2], a);
        c.ps.popPose();
        c.ps.pushPose();
        c.ps.translate(pos.x, pos.y, pos.z);
        Fx.sphere(c.add("ph_plasma"), c.ps, radius * 1.12f, 10, 14, -c.age * 0.012f, 1f, 0.7f, 0.25f, a * 0.45f);
        c.ps.popPose();
        c.billboard("ph_glow", pos, radius * 3.4f, 0f, GOLD, a * 0.8f);
        c.billboard("ph_corona", pos, radius * 2.8f, c.age * 0.02f, CREAM, a * 0.9f);
        c.billboard("ph_corona", pos, radius * 4.2f, -c.age * 0.011f, GOLD, a * 0.5f);
        c.billboard("ph_star", pos, radius * 3.0f, c.age * 0.004f, WHITE, a * 0.7f);
    }

    private static void converge(C c, Vec3 centre, float t0, float t1, float radius, int n, float size) {
        float k = Fx.ramp(c.t + 0f, t0, t1);
        if (k <= 0f || k >= 1f) {
            return;
        }
        for (int i = 0; i < n; i++) {
            float prog = (c.age * 0.045f + Fx.rnd(i * 17 + 3)) % 1f;
            double th = Fx.rnd(i * 5 + 1) * Math.PI * 2, ph = (Fx.rnd(i * 9 + 4) - 0.5) * Math.PI;
            Vec3 v = new Vec3(Math.cos(ph) * Math.cos(th), Math.sin(ph), Math.cos(ph) * Math.sin(th));
            c.billboard("ph_glow", centre.add(v.scale(radius * (1f - prog))), size * prog, 0f, CREAM, 0.9f * prog * Fx.window(k, 0f, 0.15f, 0.85f, 1f));
        }
    }

    // ================================================================== Dawn Lance
    private static void lance(C c) {
        float t = c.t;
        Vec3 hand = c.w(-0.42, 1.95, 1.0);
        Vec3 dir = c.aim.subtract(hand);
        dir = dir.lengthSqr() < 1e-4 ? c.w(0, 0, 1) : dir.normalize();
        float ch = Fx.ramp(t, 0, 24);
        if (t < PhainonAttack.LANCE_FIRE) {
            c.billboard("ph_glow", hand, 0.3f + 1.5f * ch, 0f, GOLD, 0.9f);
            c.billboard("ph_star", hand, 0.8f + 3.2f * ch, c.age * 0.05f, WHITE, 0.9f * ch);
            for (int k = 0; k < 3; k++) {
                float ph = (c.age * 0.04f + k / 3f) % 1f;
                c.billboard("ph_ring", hand, (1.8f * (1f - ph) + 0.3f) * (0.5f + ch), 0f, CREAM, 0.6f * ph * ch);
            }
            converge(c, hand, 0f, PhainonAttack.LANCE_FIRE, 6f, 14, 0.3f);
            c.beam(hand, hand.add(dir.scale(90)), 0.14f, 0.2f, CREAM, 0.1f + 0.45f * Fx.ramp(t, 4, 22));
        }
        List3 dirs = new List3(dir, c.phase >= 3);
        float pulse = Fx.ease(Fx.ramp(t, 24, 28)) * (1f - Fx.smooth(Fx.ramp(t, 34, 48)));
        if (pulse > 0.01f) {
            for (Vec3 d : dirs.v) {
                Vec3 end = hand.add(d.scale(90));
                c.beam(hand, end, 5.6f * pulse, 0.35f, GOLD, 0.55f);
                c.beam(hand, end, 3.2f * pulse, 0.5f, CREAM, 0.85f);
                c.beam(hand, end, 1.2f * pulse, 0.7f, WHITE, 0.95f);
                for (int i = 1; i <= 8; i++) {
                    c.billboard("ph_star", hand.add(d.scale(i * 10.0)), 2.6f * pulse, c.age * 0.02f, CREAM, 0.45f * pulse);
                }
            }
            c.billboard("ph_star", hand, 9f * pulse, 0f, WHITE, 0.95f * pulse);
            c.billboard("ph_glow", hand, 6f * pulse, 0f, GOLD, 0.8f * pulse);
        }
        if (c.phase >= 2) {
            Vec3 g = groundPt(c, c.aim);
            c.flat("ph_ring", g, 7f / 0.92f, c.age * 0.04f, GOLD, 0.5f * Fx.window(t, 26, 40, 44, 48));
            shock(c, g, Fx.ramp(t, 44, 62), 12.0, GOLD, 0.95f);
            c.billboard("ph_star", g.add(0, 1.5, 0), 11f * (1f - Fx.ramp(t, 44, 56)), 0f, WHITE, 0.9f * Fx.window(t, 44, 45, 46, 56));
        }
    }

    private record List3(Vec3[] v) {
        List3(Vec3 d, boolean flank) {
            this(flank ? new Vec3[]{d, com.example.jjkspells.irontomb.entity.PhainonEntity.rotY(d, 0.32), com.example.jjkspells.irontomb.entity.PhainonEntity.rotY(d, -0.32)} : new Vec3[]{d});
        }
    }

    // ================================================================== Daybreak Rush
    private static void dash(C c) {
        float t = c.t;
        int n = PhainonAttack.dashes(c.phase);
        if (t < PhainonAttack.DASH_FIRST) {
            float k = Fx.ramp(t, 0, 14);
            c.billboard("ph_glow", c.w(0, 1.4, 0), 1.2f + 2.4f * k, 0f, GOLD, 0.5f * k);
            c.billboard("ph_star", c.w(0, 1.4, 0), 2.5f * k, c.age * 0.1f, WHITE, 0.6f * k);
            return;
        }
        int k = (int) ((t - PhainonAttack.DASH_FIRST) / PhainonAttack.DASH_STEP);
        if (k >= n) {
            return;
        }
        float local = t - PhainonAttack.DASH_FIRST - k * PhainonAttack.DASH_STEP;
        Vec3 f = c.from.add(0, 1.3, 0), d = c.aim.add(0, 1.3, 0);
        float fade = 1f - Fx.ramp(local, 0, 9);
        if (fade > 0.01f) {
            c.beam(f, d, 0.2f + 1.5f * fade, 0.4f, GOLD, 0.8f * fade);
            c.beam(f, d, 0.1f + 0.6f * fade, 0.6f, WHITE, 0.95f * fade);
            c.billboard("ph_star", f, 3.4f * fade, 0f, CREAM, 0.9f * fade);
            c.billboard("ph_star", d, 3.8f * fade, 0f, CREAM, 0.9f * fade);
            c.billboard("ph_glow", f, 2.2f * fade, 0f, GOLD, 0.7f * fade);
        }
        if (local >= 3 && local <= 14) {
            Vec3 fwd = c.w(0, 0, 1);
            Vec3 p = d.add(fwd.scale(1.5));
            float roll = Fx.rnd(c.seed) * 6.2832f;
            float size = 2.0f + 4.2f * Fx.ease(Fx.ramp(local, 3, 8));
            float a = Fx.window(local, 3, 5, 8, 14);
            c.billboard("ph_arc", p, size, roll, CREAM, 0.95f * a);
            c.billboard("ph_arc", p, size * 1.3f, roll + 0.12f, GOLD, 0.6f * a);
            c.billboard("ph_star", p, size * 0.9f, 0f, WHITE, 0.7f * Fx.window(local, 4, 5, 6, 10));
            for (int i = 0; i < 6; i++) {
                double an = Fx.rnd(i + c.seed) * 6.28;
                c.billboard("ph_glow", p.add(Math.cos(an) * size * 0.5 * Fx.ramp(local, 4, 12), Math.sin(an) * size * 0.4 * Fx.ramp(local, 4, 12), 0), 0.25f, 0f, CREAM, 0.8f * a);
            }
        }
    }

    // ================================================================== Aureate Feather Rain
    private static void featherRain(C c) {
        float t = c.t;
        int S = PhainonAttack.spokes(c.phase);
        for (int s = -1; s <= 1; s += 2) {
            c.billboard("ph_star", c.w(s * 2.6, 3.2, -1.0), 3.0f * Fx.window(t, 2, 12, 16, 24), 0f, CREAM, 0.8f);
        }
        Vec3 centre = c.e.getAim();
        Vec3 ePos = c.ePos;
        for (int v = 0; v < PhainonAttack.FEATHER_VOLLEYS; v++) {
            float tv = PhainonAttack.FEATHER_START + v * PhainonAttack.FEATHER_STEP;
            float local = t - tv;
            if (local < 0f || local > PhainonAttack.FEATHER_FALL + 12f) {
                continue;
            }
            for (int s = 0; s < S; s++) {
                Vec3 p = PhainonAttack.featherPoint(c.seed, v, s, S, centre).subtract(ePos).add(0, 0.08, 0);
                float k = Fx.ramp(local, 0, PhainonAttack.FEATHER_FALL);
                if (local < PhainonAttack.FEATHER_FALL) {
                    c.flat("ph_ring", p, 3.4f / 0.92f, c.age * 0.05f, GOLD, 0.6f * Fx.smooth(Fx.ramp(local, 0, 5)));
                    c.flat("ph_ring", p.add(0, 0.01, 0), 3.4f * (1f - k) / 0.92f, 0f, CREAM, 0.7f * k);
                    float h = 28f * (float) Math.pow(1f - k, 1.6) + 0.3f;
                    Vec3 fp = p.add(0, h + 2.4, 0);
                    VertexConsumer vc = c.add("ph_feather");
                    feather(c, vc, fp, new Vec3(0, -1, 0), new Vec3(Math.cos(v + s), 0, Math.sin(v + s)), 2.6f, 0.8f, 1f, 0.92f, 0.6f, 0.95f);
                    c.beam(fp.add(0, 7, 0), fp, 0.35f, 0.4f, GOLD, 0.45f * k);
                } else {
                    float q = Fx.ramp(local, PhainonAttack.FEATHER_FALL, PhainonAttack.FEATHER_FALL + 12);
                    shock(c, p, q, 6.0, GOLD, 0.9f);
                    c.billboard("ph_star", p.add(0, 1.0, 0), 3.2f * (1f - q), 0f, WHITE, 0.9f * (1f - q));
                    c.billboard("ph_glow", p.add(0, 0.6, 0), 2.6f * (1f - q), 0f, GOLD, 0.7f * (1f - q));
                }
            }
        }
    }

    // ================================================================== Sunfall
    private static void sunfall(C c) {
        float t = c.t;
        Vec3 s0 = c.w(0, 5.6, 0.4);
        Vec3 g = groundPt(c, c.aim);
        // telegraph: the landing zone and a ring that closes in until impact
        float tele = Fx.smooth(Fx.ramp(t, 22, 40)) * (1f - Fx.ramp(t, PhainonAttack.SUN_HIT, PhainonAttack.SUN_HIT + 3));
        c.flat("ph_ring", g, (float) (PhainonAttack.SUN_RADIUS / 0.92), c.age * 0.02f, GOLD, 0.7f * tele);
        c.flat("ph_ring", g.add(0, 0.02, 0), (float) (PhainonAttack.SUN_RADIUS * (1f - Fx.ramp(t, 50, PhainonAttack.SUN_HIT)) / 0.92), 0f, CREAM, 0.8f * tele);
        c.flat("ph_glow", g.add(0, 0.03, 0), (float) PhainonAttack.SUN_RADIUS, 0f, GOLD, 0.25f * tele);
        for (int i = 0; i < 12; i++) {
            double an = i * Math.PI / 6 + c.age * 0.015;
            c.flatFrame(g.add(Math.cos(an) * PhainonAttack.SUN_RADIUS * 0.85, 0.04, Math.sin(an) * PhainonAttack.SUN_RADIUS * 0.85), 1.3f, (i * 5) % 16, (float) -an, GOLD, 0.85f * tele);
        }
        // the sun
        float form = Fx.smooth(Fx.ramp(t, 8, 60));
        Vec3 pos = s0;
        float radius = 0.25f + 3.0f * form;
        if (t >= PhainonAttack.SUN_THROW) {
            float k = Fx.ramp(t, PhainonAttack.SUN_THROW, PhainonAttack.SUN_HIT);
            pos = s0.lerp(g.add(0, 2.0, 0), k * k);
            radius = 3.2f + 1.4f * k;
        }
        if (t < PhainonAttack.SUN_HIT) {
            converge(c, s0, 4, 60, 11f, 24, 0.42f);
            sphere(c, pos, radius, WHITE, 0.95f);
        }
        // impact
        if (t >= PhainonAttack.SUN_HIT) {
            float k = Fx.ramp(t, PhainonAttack.SUN_HIT, PhainonAttack.SUN_HIT + 26);
            if (k < 1f) {
                shock(c, g, k, 44, GOLD, 1f);
                shock(c, g.add(0, 0.03, 0), Fx.ramp(t, PhainonAttack.SUN_HIT + 4, PhainonAttack.SUN_HIT + 30), 36, CREAM, 0.9f);
                shock(c, g.add(0, 0.05, 0), Fx.ramp(t, PhainonAttack.SUN_HIT + 8, PhainonAttack.SUN_HIT + 34), 26, WHITE, 0.8f);
                c.billboard("ph_star", g.add(0, 3, 0), 46f * (1f - Fx.ramp(t, PhainonAttack.SUN_HIT, PhainonAttack.SUN_HIT + 12)), 0f, WHITE, 1f * Fx.window(t, PhainonAttack.SUN_HIT, PhainonAttack.SUN_HIT + 1, PhainonAttack.SUN_HIT + 4, PhainonAttack.SUN_HIT + 14));
                c.billboard("ph_corona", g.add(0, 6, 0), 30f * (1f - k * 0.4f), c.age * 0.03f, CREAM, 0.9f * (1f - k));
                Fx.pillar(c.add("ph_beam"), c.ps, g, 90f, 7f * (1f - k * 0.5f), c.age * 0.08f, 1f, 0.85f, 0.45f, 0.9f * (1f - k));
                Fx.pillar(c.add("ph_beam"), c.ps, g, 90f, 3f * (1f - k * 0.5f), -c.age * 0.1f, 1f, 1f, 1f, 0.9f * (1f - k));
                c.flat("ph_corona", g.add(0, 0.06, 0), 22f, c.age * 0.02f, GOLD, 0.7f * (1f - k));
            }
        }
    }

    // ================================================================== Ringing Halo
    private static void halo(C c) {
        float t = c.t;
        Vec3 o = groundPt(c, c.aim);
        int N = PhainonAttack.rings(c.phase);
        float seal = Fx.window(t, 0, 10, 100, 112);
        c.flat("ph_ring", o, 2.6f / 0.92f, c.age * 0.05f, GOLD, 0.8f * seal);
        for (int i = 0; i < 8; i++) {
            double an = i * Math.PI / 4 + c.age * 0.06;
            c.flatFrame(o.add(Math.cos(an) * 2.1, 0.02, Math.sin(an) * 2.1), 0.45f, (i * 2) % 16, (float) -an, CREAM, 0.9f * seal);
        }
        for (int i = 0; i < N; i++) {
            float t0 = PhainonAttack.HALO_START + PhainonAttack.HALO_STEP * i;
            // warning pulse just before each ring is released
            c.billboard("ph_ring", o.add(0, 0.5, 0), 3.0f * Fx.window(t, t0 - 10, t0 - 2, t0 - 1, t0 + 2), 0f, CREAM, 0.7f);
            if (t < t0) {
                continue;
            }
            double r = PhainonAttack.HALO_SPEED * (t - t0);
            if (r > 36) {
                continue;
            }
            float a = 1f - Fx.ramp((float) r, 26, 36);
            c.flat("ph_ring", o.add(0, 0.03, 0), (float) (r / 0.92), 0f, GOLD, 0.95f * a);
            c.flat("ph_ring", o.add(0, 0.05, 0), (float) ((r + 1.3) / 0.92), 0f, WHITE, 0.45f * a);
            int spikes = (int) Mth.clamp((float) (r * 2.2), 10, 56);
            VertexConsumer vc = c.add("ph_beam");
            for (int s = 0; s < spikes; s++) {
                double an = s * Math.PI * 2 / spikes + i * 0.3;
                Fx.pillar(vc, c.ps, o.add(Math.cos(an) * r, 0, Math.sin(an) * r), 2.6f + 0.8f * (s % 3), 0.55f, c.age * 0.06f + s, 1f, 0.85f, 0.4f, 0.8f * a);
            }
        }
    }

    // ================================================================== Aureate Sweep
    private static void sweep(C c) {
        float t = c.t;
        Vec3 o = groundPt(c, c.aim);
        int B = PhainonAttack.beams(c.phase);
        float master = 1f - Fx.smooth(Fx.ramp(t, 96, 108));
        double a0 = Math.toRadians((c.seed & 0x7fffffff) % 360);
        double dir = (c.seed & 1) == 0 ? 1.0 : -1.0;
        double step = Math.toRadians(PhainonAttack.SWEEP_DEG_PER_TICK);
        float tt = Mth.clamp(t, PhainonAttack.SWEEP_START, PhainonAttack.SWEEP_END);
        double base = a0 + dir * step * (tt - PhainonAttack.SWEEP_START);
        // hub
        c.flat("ph_corona", o.add(0, 0.06, 0), 9f, c.age * 0.04f, GOLD, 0.65f * Fx.smooth(Fx.ramp(t, 4, 24)) * master);
        c.flat("ph_ring", o.add(0, 0.08, 0), 4f / 0.92f, -c.age * 0.05f, CREAM, 0.8f * Fx.smooth(Fx.ramp(t, 4, 24)) * master);
        c.billboard("ph_glow", o.add(0, 1.0, 0), 7f, 0f, GOLD, 0.6f * Fx.smooth(Fx.ramp(t, 8, 30)) * master);
        Vec3 up = new Vec3(0, 0.7, 0);
        for (int b = 0; b < B; b++) {
            double ang = base + b * Math.PI * 2 / B;
            Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 end = o.add(d.scale(PhainonAttack.SWEEP_LENGTH)).add(up);
            if (t < PhainonAttack.SWEEP_START) {
                c.beam(o.add(up), end, 0.28f, 0.1f, CREAM, 0.5f * Fx.ramp(t, 8, 28));
                continue;
            }
            // afterglow of where the blade has just been
            for (int k = 6; k >= 1; k--) {
                double an = ang - dir * step * k * 1.6;
                Vec3 e2 = o.add(Math.cos(an) * PhainonAttack.SWEEP_LENGTH, 0.7, Math.sin(an) * PhainonAttack.SWEEP_LENGTH);
                c.beam(o.add(up), e2, 2.0f, 0.2f, GOLD, 0.28f * (1f - k / 7f) * master);
            }
            c.beam(o.add(up), end, 4.0f, 0.4f, GOLD, 0.7f * master);
            c.beam(o.add(up), end, 2.2f, 0.55f, CREAM, 0.85f * master);
            c.beam(o.add(up), end, 0.8f, 0.7f, WHITE, 0.95f * master);
            c.billboard("ph_star", end, 6.0f, c.age * 0.05f, CREAM, 0.85f * master);
            for (int i = 1; i <= 5; i++) {
                c.billboard("ph_glow", o.add(d.scale(i * 8.0)).add(up), 1.8f, 0f, CREAM, 0.5f * master);
            }
        }
    }

    // ================================================================== Dawnbreaker's Judgment
    private static void bladeQuad(C c, Vec3 top, Vec3 tip, float width, float a) {
        Vec3 axis = tip.subtract(top);
        if (axis.lengthSqr() < 1e-4 || a < 0.004f) {
            return;
        }
        Vec3 mid = top.add(tip).scale(0.5);
        Vec3 side = axis.cross(c.cam.subtract(mid));
        if (side.lengthSqr() < 1e-6) {
            side = new Vec3(1, 0, 0);
        }
        side = side.normalize().scale(width * 0.5);
        Matrix4f m = c.ps.last().pose();
        VertexConsumer vc = c.add("ph_blade");
        quad(vc, m, top.subtract(side), 0, 1, top.add(side), 1, 1, tip.add(side), 1, 0, tip.subtract(side), 0, 0, 1f, 1f, 1f, a);
    }

    private static void judgment(C c) {
        float t = c.t;
        Vec3 g = groundPt(c, c.aim);
        Vec3 s0 = c.w(0, 8.0, 1.0);
        // the blade forms above him, ringed by turning seals
        float grow = Fx.smooth(Fx.ramp(t, 18, 66));
        float L = 36f * grow;
        Vec3 tip = s0;
        Vec3 axis = new Vec3(0, -1, 0);
        if (t >= PhainonAttack.JUDG_THROW) {
            float k = Fx.ramp(t, PhainonAttack.JUDG_THROW, PhainonAttack.JUDG_HIT);
            Vec3 dest = g.add(0, 1.0, 0);
            axis = dest.subtract(s0).normalize();
            tip = s0.lerp(dest, k * k);
        }
        float hold = 1f - Fx.smooth(Fx.ramp(t, PhainonAttack.JUDG_HIT + 6, PhainonAttack.JUDG_HIT + 28));
        if (L > 0.5f && hold > 0.01f) {
            Vec3 top = tip.subtract(axis.scale(L));
            bladeQuad(c, top, tip, 6.0f * grow, 0.95f * hold);
            bladeQuad(c, top, tip, 3.4f * grow, 0.8f * hold);
            c.billboard("ph_star", tip, 5.0f * grow, 0f, WHITE, 0.9f * hold);
            c.billboard("ph_glow", tip, 7.0f * grow, 0f, GOLD, 0.6f * hold);
            for (int i = 0; i < 6; i++) {
                Vec3 rp = tip.subtract(axis.scale(L * (0.2 + 0.13 * i)));
                float half = (3.0f + 0.7f * i) * grow;
                c.flat("ph_ring", rp, half, c.age * 0.03f * (i % 2 == 0 ? 1 : -1), GOLD, 0.7f * hold * grow);
                for (int s = 0; s < 6; s++) {
                    double an = s * Math.PI / 3 + c.age * 0.03 * (i % 2 == 0 ? 1 : -1);
                    c.flatFrame(rp.add(Math.cos(an) * half * 0.8, 0, Math.sin(an) * half * 0.8), 0.5f, (i * 3 + s) % 16, (float) -an, CREAM, 0.8f * hold * grow);
                }
            }
            converge(c, s0.add(0, 6, 0), 16, 70, 20f, 28, 0.55f);
        }
        // ground: the zone, closing in
        float tele = Fx.smooth(Fx.ramp(t, 34, 56)) * (1f - Fx.ramp(t, PhainonAttack.JUDG_HIT, PhainonAttack.JUDG_HIT + 3));
        float R = (float) PhainonAttack.JUDG_RADIUS;
        c.flat("ph_ring", g, R / 0.92f, c.age * 0.01f, GOLD, 0.8f * tele);
        c.flat("ph_ring", g.add(0, 0.02, 0), R * (1f - Fx.ramp(t, PhainonAttack.JUDG_LOCK, PhainonAttack.JUDG_HIT)) / 0.92f, 0f, CREAM, 0.9f * tele);
        c.flat("ph_glow", g.add(0, 0.03, 0), R, 0f, GOLD, 0.22f * tele);
        for (int i = 0; i < 4; i++) {
            double an = i * Math.PI / 2 + Math.PI / 4;
            c.beam(g.add(Math.cos(an) * R, 0.2, Math.sin(an) * R), g.add(0, 0.2, 0), 0.5f, 0.2f, CREAM, 0.6f * tele);
        }
        for (int i = 0; i < 16; i++) {
            double an = i * Math.PI / 8 - c.age * 0.01;
            c.flatFrame(g.add(Math.cos(an) * R * 0.9, 0.04, Math.sin(an) * R * 0.9), 1.6f, (i * 3) % 16, (float) -an, GOLD, 0.85f * tele);
        }
        // impact
        if (t >= PhainonAttack.JUDG_HIT) {
            float k = Fx.ramp(t, PhainonAttack.JUDG_HIT, PhainonAttack.JUDG_HIT + 40);
            if (k < 1f) {
                shock(c, g, k, 56, GOLD, 1f);
                shock(c, g.add(0, 0.03, 0), Fx.ramp(t, PhainonAttack.JUDG_HIT + 6, PhainonAttack.JUDG_HIT + 46), 44, CREAM, 0.95f);
                shock(c, g.add(0, 0.05, 0), Fx.ramp(t, PhainonAttack.JUDG_HIT + 14, PhainonAttack.JUDG_HIT + 54), 62, WHITE, 0.8f);
                c.billboard("ph_star", g.add(0, 4, 0), 90f * (1f - Fx.ramp(t, PhainonAttack.JUDG_HIT, PhainonAttack.JUDG_HIT + 16)), 0f, WHITE, Fx.window(t, PhainonAttack.JUDG_HIT, PhainonAttack.JUDG_HIT + 1, PhainonAttack.JUDG_HIT + 6, PhainonAttack.JUDG_HIT + 18));
                c.billboard("ph_corona", g.add(0, 14, 0), 70f * (1f - k * 0.3f), c.age * 0.02f, CREAM, 0.9f * (1f - k));
                c.billboard("ph_corona", g.add(0, 14, 0), 110f * (1f - k * 0.3f), -c.age * 0.01f, GOLD, 0.5f * (1f - k));
                Fx.pillar(c.add("ph_beam"), c.ps, g, 160f, 10f * (1f - k * 0.5f), c.age * 0.08f, 1f, 0.85f, 0.45f, 0.95f * (1f - k));
                Fx.pillar(c.add("ph_beam"), c.ps, g, 160f, 4f * (1f - k * 0.5f), -c.age * 0.1f, 1f, 1f, 1f, 0.95f * (1f - k));
                c.flat("ph_corona", g.add(0, 0.07, 0), 38f, c.age * 0.01f, GOLD, 0.75f * (1f - k));
                // the cross of light the blade leaves behind
                for (int i = 0; i < 4; i++) {
                    double an = i * Math.PI / 2 + Math.PI / 4;
                    c.beam(g.add(0, 0.5, 0), g.add(Math.cos(an) * 60 * Fx.ease(k), 0.5, Math.sin(an) * 60 * Fx.ease(k)), 3.0f * (1f - k), 0.4f, CREAM, 0.9f * (1f - k));
                }
            }
        }
    }

    private static void stagger(C c) {
        float t = c.t;
        float k = Fx.smooth(Fx.ramp(t, 0, 10)) * (1f - Fx.smooth(Fx.ramp(t, 66, 80)));
        for (int i = 0; i < 4; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(c.age * 0.4 + i * 1.9);
            c.billboard("ph_star", c.w((Fx.rnd(i) - 0.5) * 0.9, 0.6 + Fx.rnd(i + 9) * 1.4, (Fx.rnd(i + 4) - 0.5) * 0.5), 1.1f, c.age * 0.1f, WHITE, 0.8f * tw * k);
        }
        c.billboard("ph_glow", c.w(0, 1.4, 0), 3.2f, 0f, new float[]{1f, 0.5f, 0.25f}, 0.4f * k);
    }

    // ================================================================== scripted moments
    private static void intro(C c) {
        float et = c.et;
        Vec3 g = groundPt(c, Vec3.ZERO);
        // a pillar of light calls him down
        float pil = Fx.window(et, 0, 10, 52, 76);
        Fx.pillar(c.add("ph_beam"), c.ps, g, 140f, 4f, c.age * 0.07f, 1f, 0.85f, 0.45f, 0.9f * pil);
        Fx.pillar(c.add("ph_beam"), c.ps, g, 140f, 1.8f, -c.age * 0.09f, 1f, 1f, 1f, 0.9f * pil);
        c.billboard("ph_corona", c.w(0, 6, 0), 16f * Fx.window(et, 0, 14, 40, 76), c.age * 0.02f, CREAM, 0.7f);
        float k = Fx.ramp(et, 74, 96);
        shock(c, g, k, 40, GOLD, 1f);
        shock(c, g.add(0, 0.03, 0), Fx.ramp(et, 78, 100), 30, CREAM, 0.8f);
        c.billboard("ph_star", c.w(0, 2.0, 0), 26f * (1f - Fx.ramp(et, 74, 88)), 0f, WHITE, Fx.window(et, 74, 75, 77, 90));
        for (int i = 0; i < 20; i++) {
            double an = i * Math.PI * 2 / 20;
            c.flatFrame(g.add(Math.cos(an) * 9 * Fx.ease(Fx.ramp(et, 30, 70)), 0.03, Math.sin(an) * 9 * Fx.ease(Fx.ramp(et, 30, 70))), 0.8f, (i * 3) % 16, (float) -an, GOLD, 0.8f * Fx.window(et, 30, 40, 70, 90));
        }
    }

    private static void phaseChange(C c, int to) {
        float et = c.et;
        int len = PhainonAttack.EV_LENGTH[c.ev];
        Vec3 g = groundPt(c, Vec3.ZERO);
        Vec3 ch = c.w(0, 1.6, 0);
        float gather = Fx.ramp(et, 0, to == 2 ? 26 : 44);
        if (gather < 1f) {
            for (int i = 0; i < 24; i++) {
                float prog = (c.age * 0.05f + Fx.rnd(i * 17 + 3)) % 1f;
                double th = Fx.rnd(i * 5 + 1) * Math.PI * 2, ph = (Fx.rnd(i * 9 + 4) - 0.5) * Math.PI;
                Vec3 v = new Vec3(Math.cos(ph) * Math.cos(th), Math.sin(ph), Math.cos(ph) * Math.sin(th));
                c.billboard("ph_glow", ch.add(v.scale(12f * (1f - prog))), 0.4f * prog, 0f, CREAM, 0.9f * prog);
            }
        }
        float hit = to == 2 ? 26f : 44f;
        float k = Fx.ramp(et, hit, hit + 26);
        double R = to == 2 ? 38 : 52;
        shock(c, g, k, R, GOLD, 1f);
        shock(c, g.add(0, 0.03, 0), Fx.ramp(et, hit + 4, hit + 30), R * 0.75, WHITE, 0.8f);
        c.billboard("ph_star", ch, (to == 2 ? 30f : 44f) * (1f - Fx.ramp(et, hit, hit + 14)), 0f, WHITE, Fx.window(et, hit, hit + 1, hit + 3, hit + 16));
        Fx.pillar(c.add("ph_beam"), c.ps, g, 100f, 3.5f, c.age * 0.08f, 1f, 0.85f, 0.45f, 0.8f * Fx.window(et, hit - 8, hit, hit + 8, hit + 26));
        c.billboard("ph_corona", ch, 14f * Fx.window(et, 4, 24, len - 30, len), c.age * 0.03f, to == 2 ? CREAM : AMBER, 0.6f);
    }

    private static void death(C c) {
        float et = c.et;
        Vec3 g = groundPt(c, Vec3.ZERO);
        Vec3 chest = c.w(0, 1.6, 0);
        // the wings burst into feathers
        if (et >= 44f) {
            float s = et - 44f;
            VertexConsumer vc = c.add("ph_feather");
            for (int i = 0; i < 44; i++) {
                double th = Fx.rnd(i * 3 + 1) * Math.PI * 2;
                double ph = (Fx.rnd(i * 5 + 2) - 0.25) * Math.PI * 0.8;
                Vec3 d = new Vec3(Math.cos(ph) * Math.cos(th), Math.sin(ph), Math.cos(ph) * Math.sin(th));
                double sp = 0.12 + Fx.rnd(i * 7 + 3) * 0.34;
                Vec3 p = c.w(0, 1.8, -0.3).add(d.scale(sp * s * (1.0 - 0.006 * s))).add(0, -0.003 * s * s, 0);
                float a = 1f - Fx.ramp(s, 24, 62);
                Vec3 perp = new Vec3(-d.z, 0, d.x);
                feather(c, vc, p, d, perp.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : perp.normalize(), 1.6f, 0.5f, 1f, 0.88f, 0.5f, 0.9f * a);
            }
            c.billboard("ph_star", c.w(0, 1.8, -0.3), 14f * Fx.window(et, 44, 46, 50, 62), 0f, WHITE, 0.95f);
        }
        // the crown dies down, the body dissolves into light
        float melt = Fx.ramp(et, 88, 112);
        c.billboard("ph_glow", chest, 3.0f + 9f * melt, 0f, GOLD, 0.7f * Fx.smooth(Fx.ramp(et, 80, 100)) * (1f - Fx.ramp(et, 124, 150)));
        c.billboard("ph_star", chest, 30f * Fx.window(et, 96, 106, 108, 126), c.age * 0.01f, WHITE, 1f);
        Fx.pillar(c.add("ph_beam"), c.ps, g, 150f, 3.4f, c.age * 0.07f, 1f, 0.88f, 0.5f, 0.9f * Fx.window(et, 100, 110, 130, 156));
        Fx.pillar(c.add("ph_beam"), c.ps, g, 150f, 1.4f, -c.age * 0.09f, 1f, 1f, 1f, 0.9f * Fx.window(et, 100, 110, 130, 156));
        shock(c, g, Fx.ramp(et, 106, 140), 46, GOLD, 1f);
        for (int i = 0; i < 40; i++) {
            if (et < 108) {
                break;
            }
            double an = Fx.rnd(i) * Math.PI * 2, r = Fx.rnd(i + 3) * 3.0;
            double y = ((et - 108) * (0.03 + Fx.rnd(i + 7) * 0.05)) % 14.0;
            c.billboard("ph_glow", chest.add(Math.cos(an) * r, y, Math.sin(an) * r), 0.3f, 0f, CREAM, 0.9f * (1f - Fx.ramp(et, 128, 160)));
        }
    }
}
