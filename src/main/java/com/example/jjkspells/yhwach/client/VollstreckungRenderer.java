package com.example.jjkspells.yhwach.client;

import com.example.jjkspells.yhwach.entity.VollstreckungEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Draws "Vollstreckung": a colossal crystalline Reishi sword (textured blade + jagged six-shard guard star) that hangs above the
 * target, shivers and brightens while a lock-on seal closes on the target, then plunges and detonates in a column of blue light.
 */
public class VollstreckungRenderer extends EntityRenderer<VollstreckungEntity> {
    private static final double LEN = 20.0;
    private static final double WID = LEN * 0.25;
    private static final double GUARD_AT = 0.225;
    private static final int STRIKE = VollstreckungEntity.STRIKE;
    private static final int IMPACT = VollstreckungEntity.IMPACT;
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Y = new Vec3(0, 1, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);

    private Pen p;
    private float t;
    private float fade;
    private Vec3 E;
    private double h;
    private int seed;
    private double lock;
    private double swordA;
    private double fallF;
    private Vec3 tip;
    private Vec3 top;
    private double impactAge;

    public VollstreckungRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(VollstreckungEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(VollstreckungEntity e) {
        return YhwachRender.GLOW;
    }

    private static double c01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double ease(double x) {
        double k = 1.0 - c01(x);
        return 1.0 - k * k * k;
    }

    private void use(MultiBufferSource buf, ResourceLocation tex) {
        p.vc = buf.getBuffer(YhwachRender.additive(tex));
    }

    private void glow(Vec3 c, double half, float[] col, float a) {
        p.billboard(c, half, 0, col, a);
    }

    private void column(Vec3 a, Vec3 b, double wa, double wb, float al, float u0, float u1) {
        if (al <= 0.003f) {
            return;
        }
        float[][] cols = {Pen.BLUE, Pen.CYAN, Pen.CORE, Pen.WHITE};
        double[] mul = {1.0, 0.58, 0.27, 0.11};
        float[] am = {0.35f, 0.6f, 0.9f, 1.0f};
        for (int l = 0; l < 4; l++) {
            p.ribbon(a, b, wa * mul[l], wb * mul[l], am[l] * al, am[l] * al, u0, u1, cols[l]);
        }
    }

    @Override
    public void render(VollstreckungEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        t = e.tickCount - 1 + pt;
        seed = e.getId();
        E = e.getPosition(pt);
        h = e.targetHeight();
        impactAge = t - IMPACT;
        fade = (float) (1.0 - c01((t - IMPACT - 18) / 16.0));
        if (fade <= 0.003f) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        p = new Pen(ps.last().pose(), E, camera.getPosition(), camera.getLeftVector(), camera.getUpVector());

        lock = c01((t - 8.0) / (STRIKE - 8.0));
        double a = ease(t / VollstreckungEntity.APPEAR);
        swordA = a;
        double hover = h + 6.0;
        double charge = ease((t - 52.0) / 28.0) * 1.8;
        double tremor = c01((t - 40.0) / 40.0) * 0.12;
        double rise = (1.0 - a) * 6.0;
        double tipY = hover + charge + rise;
        fallF = c01((t - STRIKE) / VollstreckungEntity.DROP);
        double f3 = fallF * fallF * fallF;
        tipY = tipY + (-0.3 - tipY) * f3;
        double jx = t < STRIKE ? Math.sin(t * 2.9) * tremor : 0;
        double jz = t < STRIKE ? Math.cos(t * 3.7) * tremor : 0;
        tip = E.add(jx, tipY, jz);
        double sway = t < STRIKE ? Math.sin(t * 0.06) * 0.3 : 0;
        top = tip.add(sway, LEN, 0);

        use(buf, YhwachRender.GLOW);
        glowPass();
        use(buf, YhwachRender.INS_OUTER);
        sealOuter();
        use(buf, YhwachRender.INS_MID);
        sealMid();
        use(buf, YhwachRender.VSWORD);
        swordPass();
        use(buf, YhwachRender.VSTAR);
        starPass();
        use(buf, YhwachRender.STREAK);
        streakPass();
        use(buf, YhwachRender.RING);
        ringPass();
        use(buf, YhwachRender.CROSS);
        crossPass();
        use(buf, YhwachRender.SPARKLE);
        sparklePass();
        use(buf, YhwachRender.BURST);
        burstPass();
    }

    private Vec3 along(double f) {
        return top.add(tip.subtract(top).scale(f));
    }

    // ------------------------------------------------------------------ GLOW

    private void glowPass() {
        float sa = (float) swordA * fade;
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.2);
        double hot = 1.0 + 0.8 * c01((t - 40.0) / 40.0);
        for (int i = 0; i < 5; i++) {
            Vec3 c = along(0.2 + i * 0.2);
            glow(c, (4.5 + 1.2 * Math.sin(t * 0.1 + i)) * hot, Pen.BLUE, 0.22f * sa * pulse);
            glow(c, 2.4 * hot, Pen.CYAN, 0.22f * sa);
        }
        glow(tip, 3.0 + 3.0 * lock, Pen.CYAN, 0.55f * sa);
        glow(tip, 1.4 + 1.4 * lock, Pen.WHITE, 0.8f * sa * (float) (0.5 + 0.5 * lock));
        Vec3 g = E.add(0, 0.06, 0);
        float la = (float) c01(t / 10.0) * fade;
        p.plane(g, X, Z, 8.0 - 3.5 * lock, 0, Pen.BLUE, (0.18f + 0.3f * (float) lock) * la);
        p.plane(g, X, Z, 3.0, 0, Pen.CYAN, (0.15f + 0.35f * (float) lock) * la);
        if (t < 18) {
            glow(top, 8.0 * (1.0 - t / 18.0) + 2.0, Pen.WHITE, (float) (0.9 * (1.0 - t / 18.0)));
        }
        if (impactAge >= 0) {
            double k = c01(impactAge / 22.0);
            glow(E.add(0, 1.0, 0), 22.0 * (1.0 - k) + 4.0, Pen.CYAN, (float) (1.0 - k) * fade);
            glow(E.add(0, 1.0, 0), 11.0 * (1.0 - k) + 2.0, Pen.WHITE, (float) (1.0 - k) * fade);
            p.plane(g, X, Z, 5.0 + 22.0 * ease(k), 0, Pen.BLUE, (float) (0.8 * (1.0 - k)) * fade);
        }
    }

    // ------------------------------------------------------------------ SEAL under the target

    private void sealLayer(double half, double rot, float al) {
        if (al <= 0.003f) {
            return;
        }
        Vec3 g = E.add(0, 0.08, 0);
        p.plane(g, X, Z, half, rot, Pen.BLUE, al * 0.6f);
        p.plane(g, X, Z, half, rot, Pen.CYAN, al);
        p.plane(g, X, Z, half, rot, Pen.CORE, al * 0.5f);
    }

    private void sealOuter() {
        double in = c01(t / 12.0);
        double half = 7.5 - 4.2 * ease(lock);
        double boom = impactAge >= 0 ? 1.0 + 0.4 * c01(impactAge / 8.0) : 1.0;
        sealLayer(half * boom, t * 0.03, (float) (0.9 * in) * fade);
    }

    private void sealMid() {
        double in = c01((t - 4.0) / 12.0);
        double half = 5.2 - 2.8 * ease(lock);
        sealLayer(half, -t * 0.05, (float) (0.9 * in) * fade);
    }

    // ------------------------------------------------------------------ SWORD

    private void swordPass() {
        float a = (float) swordA * fade;
        // afterimages while plunging
        if (fallF > 0.0 && impactAge < 6) {
            for (int i = 1; i <= 4; i++) {
                double lag = i * 3.2 * fallF;
                Vec3 up = new Vec3(0, lag, 0);
                float ga = a * (0.5f - i * 0.1f);
                p.ribbon(top.add(up), tip.add(up), WID * 0.9, WID * 0.9, ga * 0.6f, ga * 0.6f, 0f, 1f, Pen.BLUE);
            }
        }
        p.ribbon(top, tip, WID * 1.35, WID * 1.35, 0.4f * a, 0.4f * a, 0f, 1f, Pen.BLUE);
        p.ribbon(top, tip, WID, WID, 1.0f * a, 1.0f * a, 0f, 1f, Pen.CYAN);
        p.ribbon(top, tip, WID * 0.97, WID * 0.97, 0.75f * a, 0.75f * a, 0f, 1f, Pen.CORE);
        float flick = 0.3f + 0.2f * (float) Math.sin(t * 0.35);
        p.ribbon(top, tip, WID * 0.9, WID * 0.9, flick * a, flick * a, 0f, 1f, Pen.WHITE);
    }

    private void starPass() {
        float a = (float) swordA * fade;
        Vec3 c = along(GUARD_AT);
        double half = LEN * 0.2;
        double pulse = 1.0 + 0.05 * Math.sin(t * 0.2);
        p.billboard(c, half * 1.7 * pulse, t * 0.012, Pen.BLUE, 0.35f * a);
        p.billboard(c, half * 1.25, -t * 0.01, Pen.CYAN, 0.5f * a);
        p.billboard(c, half, 0, Pen.CYAN, 1.0f * a);
        p.billboard(c, half * 0.96, 0, Pen.CORE, 0.7f * a);
        p.billboard(c, half * 0.9, 0, Pen.WHITE, 0.4f * a);
    }

    // ------------------------------------------------------------------ STREAK

    private void streakPass() {
        float a = (float) swordA * fade;
        // sky column above the hilt
        Vec3 sky = top.add(0, 60, 0);
        float u0 = -t * 0.05f;
        p.ribbon(top, sky, 2.2, 1.0, 0.45f * a, 0f, u0, u0 + 8f, Pen.CYAN);
        p.ribbon(top, sky, 0.8, 0.4, 0.8f * a, 0f, u0, u0 + 8f, Pen.WHITE);

        // tracking beam from the tip down to the target, and gathering rays
        if (t < IMPACT && lock > 0.0) {
            Vec3 head = E.add(0, h * 0.5, 0);
            float flick = 0.7f + 0.3f * (float) Math.sin(t * 0.9);
            float ba = (float) (0.1 + 0.5 * lock * lock) * flick * a;
            p.ribbon(tip, head, 0.5 + 0.9 * lock, 0.2, ba, ba * 0.8f, -t * 0.1f, -t * 0.1f + 4f, Pen.CYAN);
            p.ribbon(tip, head, 0.14, 0.08, ba * 1.4f, ba, -t * 0.1f, -t * 0.1f + 4f, Pen.WHITE);
            Random r = new Random(seed * 13L);
            for (int i = 0; i < 14; i++) {
                double ang = i * Math.PI * 2 / 14 + r.nextDouble() * 0.3 + t * 0.01;
                double ph = (t * 0.035 + r.nextDouble()) % 1.0;
                double r0 = 12.0 * (1.0 - ph) + 1.2;
                double r1 = r0 + 3.5 * (1.0 - ph);
                Vec3 q0 = E.add(Math.cos(ang) * r0, 0.15, Math.sin(ang) * r0);
                Vec3 q1 = E.add(Math.cos(ang) * r1, 0.15, Math.sin(ang) * r1);
                p.ribbon(q0, q1, 0.35, 0.12, (float) (0.9 * lock * Math.sin(Math.PI * ph)) * a, 0f, 0f, 1f, Pen.CYAN);
            }
        }

        // speed lines while plunging
        if (fallF > 0.0 && impactAge < 4) {
            Random r = new Random(seed * 29L);
            for (int i = 0; i < 10; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double rad = 1.0 + r.nextDouble() * 3.0;
                Vec3 q = top.add(Math.cos(ang) * rad, r.nextDouble() * 4, Math.sin(ang) * rad);
                p.ribbon(q, q.add(0, 18 + r.nextDouble() * 10, 0), 0.4, 0.1, (float) (0.8 * fallF) * a, 0f, 0f, 1f, Pen.WHITE);
            }
        }

        // impact
        if (impactAge >= 0) {
            double k = c01(impactAge / 30.0);
            float ia = fade;
            double w = 11.0 * Math.pow(1.0 - k, 1.6) + 0.4;
            Vec3 g0 = E.add(0, -1.0, 0);
            Vec3 g1 = E.add(0, 70.0, 0);
            column(g0, g1, w, w * 0.55, (float) Math.min(1.0, 1.4 * (1.0 - k)) * ia, -t * 0.2f, -t * 0.2f + 8f);
            Random r = new Random(seed * 71L);
            double sp = ease(impactAge / 10.0);
            for (int i = 0; i < 16; i++) {
                double ang = i * Math.PI * 2 / 16 + r.nextDouble() * 0.2;
                double len = (10.0 + r.nextDouble() * 12.0) * sp;
                double r0 = 1.0 + 7.0 * ease(impactAge / 16.0) * 0.4;
                Vec3 q0 = E.add(Math.cos(ang) * r0, 0.2, Math.sin(ang) * r0);
                Vec3 q1 = E.add(Math.cos(ang) * (r0 + len), 0.2, Math.sin(ang) * (r0 + len));
                p.ribbon(q0, q1, 1.4 * (1.0 - k), 0.1, (float) (1.0 - k) * ia, 0f, 0f, 1f, Pen.CYAN);
                p.ribbon(q0, q1, 0.4 * (1.0 - k), 0.05, (float) (1.0 - k) * ia, 0f, 0f, 1f, Pen.WHITE);
            }
            // ice shards thrown up
            for (int i = 0; i < 22; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double sh = 0.4 + r.nextDouble() * 0.9;
                double vy = 0.7 + r.nextDouble() * 1.0;
                double s = impactAge;
                Vec3 q = E.add(Math.cos(ang) * sh * s, vy * s - 0.045 * s * s, Math.sin(ang) * sh * s);
                Vec3 v = new Vec3(Math.cos(ang) * sh, vy - 0.09 * s, Math.sin(ang) * sh).normalize();
                p.ribbon(q, q.add(v.scale(1.8)), 0.5, 0.05, (float) (1.0 - k) * ia, 0f, 0f, 1f, i % 2 == 0 ? Pen.WHITE : Pen.CYAN);
            }
        }
    }

    // ------------------------------------------------------------------ RING

    private void ringPass() {
        float a = (float) swordA * fade;
        Vec3 g = E.add(0, 0.12, 0);
        // rings gathering onto the target
        if (t < IMPACT) {
            for (int i = 0; i < 3; i++) {
                double ph = (t * 0.03 + i / 3.0) % 1.0;
                double r = 9.0 * (1.0 - ph) + 0.8;
                p.plane(g, X, Z, r, 0, Pen.CYAN, (float) (Math.sin(Math.PI * ph) * (0.3 + 0.7 * lock)) * a);
            }
        }
        // energy rings sliding down the blade
        if (t < IMPACT + 4) {
            for (int i = 0; i < 3; i++) {
                double ph = (t * 0.02 + i / 3.0) % 1.0;
                Vec3 c = along(0.25 + 0.7 * ph);
                p.plane(c, X, Z, 2.6, t * 0.05, Pen.CYAN, (float) (0.7 * Math.sin(Math.PI * ph)) * a);
                p.plane(c, X, Z, 2.0, -t * 0.05, Pen.WHITE, (float) (0.5 * Math.sin(Math.PI * ph)) * a);
            }
        }
        // shockwaves
        if (impactAge >= 0) {
            for (int i = 0; i < 3; i++) {
                double k = c01((impactAge - i * 4.0) / 26.0);
                if (k <= 0.0 || k >= 1.0) {
                    continue;
                }
                double r = 22.0 * ease(k);
                p.plane(g, X, Z, r, 0, Pen.CYAN, (float) (1.0 - k) * fade);
                p.plane(g.add(0, 0.01, 0), X, Z, r * 0.97, 0, Pen.WHITE, (float) (0.7 * (1.0 - k)) * fade);
                p.plane(E.add(0, 0.6 + 10.0 * k, 0), X, Z, r * 0.35, t * 0.1, Pen.CYAN, (float) (0.7 * (1.0 - k)) * fade);
            }
        }
    }

    // ------------------------------------------------------------------ CROSS lock-on

    private void crossPass() {
        if (t >= IMPACT || t < 6) {
            return;
        }
        float a = (float) (0.35 + 0.65 * lock) * fade;
        double half = 1.0 + 3.2 * (1.0 - ease(lock));
        Vec3 c = E.add(0, h * 0.5, 0);
        double rot = t * (0.05 + 0.1 * lock);
        p.billboard(c, half * 1.3, rot, Pen.BLUE, 0.4f * a);
        p.billboard(c, half, rot, Pen.CYAN, 0.9f * a);
        p.billboard(c, half * 0.9, -rot * 0.5, Pen.WHITE, 0.5f * a);
    }

    // ------------------------------------------------------------------ SPARKLE

    private void sparklePass() {
        float a = (float) swordA * fade;
        Random r = new Random(seed * 97L + 3);
        for (int i = 0; i < 70; i++) {
            double f = r.nextDouble();
            double ang = r.nextDouble() * Math.PI * 2 + t * 0.03 * (0.5 + r.nextDouble());
            double rad = (1.0 + r.nextDouble() * 3.2);
            double size = 0.2 + r.nextDouble() * 0.5;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (0.15 + r.nextDouble() * 0.3) + r.nextDouble() * 6.28);
            Vec3 c = along(f);
            Vec3 pos = c.add(Math.cos(ang) * rad, (r.nextDouble() - 0.5) * 2.0, Math.sin(ang) * rad);
            p.billboard(pos, size * (0.6 + 0.6 * tw), r.nextDouble() * 1.57 + t * 0.02, (i & 1) == 0 ? Pen.WHITE : Pen.CYAN, 0.95f * tw * a);
        }
        if (impactAge >= 0) {
            double k = c01(impactAge / 30.0);
            Random q = new Random(seed * 31L);
            for (int i = 0; i < 40; i++) {
                double ang = q.nextDouble() * Math.PI * 2;
                double sp = 0.3 + q.nextDouble() * 0.9;
                double vy = 0.3 + q.nextDouble() * 0.9;
                double s = impactAge;
                Vec3 pos = E.add(Math.cos(ang) * sp * s, vy * s - 0.04 * s * s, Math.sin(ang) * sp * s);
                p.billboard(pos, 0.45 * (1.0 - k) + 0.1, i + s * 0.2, i % 3 == 0 ? Pen.WHITE : Pen.CYAN, (float) (1.0 - k) * fade);
            }
        }
    }

    // ------------------------------------------------------------------ BURST

    private void burstPass() {
        if (t < 14) {
            double fr = t / 14.0;
            int fi = Math.min(15, (int) (fr * 16));
            float u0 = (fi % 4) / 4f;
            float v0 = (fi / 4) / 4f;
            p.billboard(top, 7.0, 0, Pen.CYAN, 0.9f, u0, v0, u0 + 0.25f, v0 + 0.25f);
        }
        if (impactAge >= 0) {
            double fr = impactAge / 22.0;
            if (fr < 1.0) {
                int fi = (int) (fr * 16);
                float u0 = (fi % 4) / 4f;
                float v0 = (fi / 4) / 4f;
                p.billboard(E.add(0, 3.0, 0), 14.0, 0, Pen.CYAN, 0.95f * fade, u0, v0, u0 + 0.25f, v0 + 0.25f);
                p.billboard(E.add(0, 3.0, 0), 8.0, 0.4, Pen.WHITE, 0.9f * fade, u0, v0, u0 + 0.25f, v0 + 0.25f);
            }
        }
    }
}
