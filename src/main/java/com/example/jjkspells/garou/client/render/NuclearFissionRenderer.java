package com.example.jjkspells.garou.client.render;

import com.example.jjkspells.garou.client.GarouFx;
import com.example.jjkspells.garou.entity.GarouTimeline;
import com.example.jjkspells.garou.entity.NuclearFissionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.garou.entity.GarouTimeline.c01;
import static com.example.jjkspells.garou.entity.GarouTimeline.ease;
import static com.example.jjkspells.garou.entity.GarouTimeline.lerp;
import static com.example.jjkspells.garou.entity.GarouTimeline.smooth;

/**
 * Draws Nuclear Fission: the cosmic sphere and rings around the fist, the dash streak, and the three stages of the
 * explosion (flash and shockwaves / fireball, energy shell, mushroom cloud and small blasts / aftermath).
 * Every position is relative to the entity base; everything is a function of the age, so it needs no state.
 */
public class NuclearFissionRenderer extends EntityRenderer<NuclearFissionEntity> {
    private static final ResourceLocation GLOW = Gx.tex("glow"), RINGC = Gx.tex("ringc"), RING = Gx.tex("ring"), COSMIC = Gx.tex("cosmic"),
            BURST = Gx.tex("burst"), FIRE = Gx.tex("fire"), FIRE_TILE = Gx.tex("fire_tile"), SMOKE = Gx.tex("smoke"), SHELL = Gx.tex("shell"), GROUND = Gx.tex("ground"),
            SCORCH = Gx.tex("scorch"), STREAK = Gx.tex("streak");

    private static final float[] WHITE = {1f, 1f, 1f}, PALE = {0.78f, 0.9f, 1f}, ORANGE = {1f, 0.62f, 0.28f}, EMBER = {1f, 0.45f, 0.14f},
            SOOT = {0.6f, 0.52f, 0.48f}, WARM = {1f, 0.85f, 0.6f}, DARK = {1f, 1f, 1f};

    public NuclearFissionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(NuclearFissionEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(NuclearFissionEntity e) {
        return GLOW;
    }

    private static float[] mix(float[] a, float[] b, float f) {
        f = c01(f);
        return new float[]{lerp(a[0], b[0], f), lerp(a[1], b[1], f), lerp(a[2], b[2], f)};
    }

    @Override
    public void render(NuclearFissionEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        Vec3 base = e.getPosition(pt);
        Entity owner = e.getOwnerEntity();
        int launch = e.launchAge(), imp = e.impactAge();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(base);

        if (owner != null && (imp < 0 || t - imp < 6f)) {
            drawFistAndStreak(e, owner, t, pt, ps, buf, base, cam, launch, imp);
        }
        if (imp >= 0) {
            float ti = t - imp;
            if (ti >= 0f) {
                drawBlast(e, ti, ps, buf, base, cam);
            }
        }
    }

    // ------------------------------------------------------------------ the fist: sphere, rings, dash streak

    private void drawFistAndStreak(NuclearFissionEntity e, Entity owner, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, int launch, int imp) {
        boolean punching = launch >= 0;
        Vec3 fist = GarouFx.fist(owner, punching, t, pt).subtract(base);
        float grow = punching ? 1f : smooth(t / 16f);
        float fade = 1f;
        if (imp >= 0) {
            fade = 1f - smooth((t - imp) / 5f);
        }
        float pulse = 1f + 0.06f * (float) Math.sin(t * 1.7f);
        float size = (0.12f + 0.95f * grow) * pulse * (imp >= 0 ? 1f + 0.8f * (1f - fade) : 1f);

        // the white cosmic sphere (animated swirl) with its halo
        Vec3 core = fist;
        Gx.billboard(buf, ps, GLOW, true, core, size * 3.4f, 0f, WHITE, 0.55f * fade * grow, null);
        Gx.billboard(buf, ps, COSMIC, true, core, size * 1.15f, t * 4f, WHITE, fade, Gx.cell((int) (t * 1.4f)));
        Gx.billboard(buf, ps, COSMIC, true, core, size * 0.9f, -t * 7f, PALE, 0.75f * fade, Gx.cell((int) (t * 1.4f) + 7));
        Gx.sphereAt(buf, ps, SHELL, true, core, size * 0.78f, 1f, 14, 20, Gx.ALL, 1f, 1f, t * 0.04f, PALE, 0.6f * fade);
        Gx.billboard(buf, ps, RINGC, true, core, size * 1.9f, t * 9f, WHITE, 0.55f * fade * grow, null);

        // several thin circular energy rings rotate around the hand
        for (int i = 0; i < 4; i++) {
            float ph = i * 47f;
            float rad = size * (1.5f + 0.34f * i) + 0.05f * (float) Math.sin(t * 0.7f + i);
            float spin = t * (13f + i * 6f) * (i % 2 == 0 ? 1f : -1f);
            ps.pushPose();
            ps.translate(core.x, core.y, core.z);
            ps.mulPose(Axis.YP.rotationDegrees(ph + spin * 0.6f));
            ps.mulPose(Axis.XP.rotationDegrees(25f + i * 38f));
            ps.mulPose(Axis.ZP.rotationDegrees(spin));
            Gx.ring(Gx.add(buf, RING), ps, rad - 0.045f, rad + 0.045f, 40, 3f + i, t * 0.03f * (i + 1), i % 2 == 0 ? WHITE : PALE, 0.95f * fade * grow);
            Gx.ring(Gx.add(buf, RING), ps, rad - 0.12f, rad + 0.12f, 40, 3f + i, t * 0.03f * (i + 1), PALE, 0.28f * fade * grow);
            ps.popPose();
        }

        // sparks being drawn into the sphere are spawned by the particle code; here the sphere throws a few light rays
        if (grow > 0.2f) {
            for (int i = 0; i < 6; i++) {
                float a = i * 60f + t * 11f;
                Vec3 d = new Vec3(Math.cos(Math.toRadians(a)), Math.sin(Math.toRadians(a * 1.3f)), Math.sin(Math.toRadians(a)));
                float len = size * (2.2f + 1.6f * (float) Math.abs(Math.sin(t * 0.5f + i)));
                Gx.ribbon(buf, ps, STREAK, core.add(d.scale(size * 0.8f)), core.add(d.scale(size * 0.8f + len)), cam, 0.05f, WHITE, 0.8f * fade, 0f);
            }
        }

        // the dash: a streak of cosmic energy behind the caster
        if (launch >= 0 && owner != null) {
            float dt = t - launch;
            float life = imp < 0 ? 1f : 1f - smooth((t - imp) / 4f);
            Vec3 look = owner.getLookAngle().normalize();
            Vec3 body = owner.getPosition(pt).add(0, 1.0, 0).subtract(base);
            float len = 5f + Math.min(dt, 4f) * 3.5f;
            Vec3 tail = body.subtract(look.scale(len));
            Vec3 head = body.add(look.scale(1.3));
            Gx.ribbon(buf, ps, STREAK, tail, head, cam, 1.3f, PALE, 0f, 0.85f * life);
            Gx.ribbon(buf, ps, STREAK, tail.add(look.scale(len * 0.3)), head, cam, 0.65f, WHITE, 0.2f * life, 1f * life);
            for (int i = 0; i < 5; i++) {
                float k = i / 4f;
                Vec3 off = new Vec3(Math.sin(i * 2.1 + dt), Math.cos(i * 1.7 + dt * 1.3), Math.sin(i * 3.3)).scale(0.9);
                Gx.ribbon(buf, ps, STREAK, tail.add(off), head.add(off.scale(0.4)), cam, 0.09f, WHITE, 0f, 0.7f * life * (0.4f + k * 0.6f));
            }
            Gx.billboard(buf, ps, BURST, true, head, 2.4f * life, dt * 30f, WHITE, 0.8f * life, null);
        }
    }

    // ------------------------------------------------------------------ the explosion

    private void drawBlast(NuclearFissionEntity e, float ti, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam) {
        Vec3 c = e.impactPos().subtract(base);
        float R = (float) GarouTimeline.RADIUS;

        // ---- ground: lit and scorched
        float scorch = ease(ti / 6f) * (1f - smooth((ti - 70f) / 80f));
        Gx.disc(buf, ps, SCORCH, false, c.add(0, 0.07, 0), 13f * ease(ti / 6f), 17f, WHITE, 0.85f * scorch);
        float lit = ease(ti / 8f) * (1f - smooth((ti - 40f) / 100f));
        Gx.disc(buf, ps, GROUND, true, c.add(0, 0.12, 0), R * 0.95f * ease(ti / 22f), ti * 0.35f, WHITE, 0.9f * lit);
        Gx.billboard(buf, ps, GLOW, true, c.add(0, 5, 0), 44f, 0f, EMBER, 0.32f * ease(ti / 10f) * (1f - smooth((ti - 20f) / 110f)), null);

        // ---- stage 1: the white flash and the shockwaves
        float flash = 1f - smooth((ti - 1f) / 13f);
        if (flash > 0.01f) {
            Gx.billboard(buf, ps, GLOW, true, c.add(0, 3, 0), 70f * (0.5f + 0.5f * ease(ti / 6f)), 0f, WHITE, flash, null);
            Gx.billboard(buf, ps, BURST, true, c.add(0, 3, 0), 34f * ease(ti / 5f), ti * 2f, WHITE, flash, null);
            Gx.billboard(buf, ps, BURST, true, c.add(0, 3, 0), 22f * ease(ti / 5f), -ti * 3f, WARM, 0.85f * flash, null);
            Gx.sphereAt(buf, ps, GLOW, true, c.add(0, 2, 0), 5f + 12f * ease(ti / 9f), 1f, 12, 16, Gx.ALL, 1f, 1f, 0f, WHITE, flash);
        }
        // circular shockwave on the ground
        for (int i = 0; i < 3; i++) {
            float lt = ti - i * 3f;
            if (lt < 0f) {
                continue;
            }
            float rr = R * 1.2f * ease(lt / 24f);
            float al = (1f - smooth(lt / 30f)) * (i == 0 ? 1f : 0.7f);
            float w = 1.6f + rr * 0.12f;
            Gx.ringAt(buf, ps, RING, c.add(0, 0.25 + i * 0.1, 0), Math.max(0f, rr - w), rr + w * 0.4f, 72, 14f, ti * 0.01f, i == 0 ? WHITE : PALE, al);
        }
        // distortion-like energy rings climbing the dome (as in the manga: big thin rings slicing the blast)
        for (int i = 0; i < 4; i++) {
            float lt = ti - 2f - i * 4f;
            if (lt < 0f) {
                continue;
            }
            float f = c01(lt / 38f);
            float rr = (R * (0.55f + 0.5f * i / 3f)) * ease(f) + 2f;
            float yy = 2f + lt * (0.5f + 0.35f * i) + 3f * i;
            float al = (1f - smooth((lt - 10f) / 28f)) * 0.95f;
            Gx.ringAt(buf, ps, RING, c.add(0, yy, 0), rr - 0.55f, rr + 0.55f, 72, 12f, -ti * 0.012f, i % 2 == 0 ? WHITE : PALE, al);
            Gx.ringAt(buf, ps, RING, c.add(0, yy, 0), rr - 1.6f, rr + 1.6f, 72, 12f, ti * 0.01f, PALE, al * 0.25f);
        }

        // ---- stage 2a: the orange-white explosion core
        float fr = GarouTimeline.fireRadius(ti);
        float fireA = smooth((ti - 0.5f) / 3f) * (1f - smooth((ti - 48f) / 50f));
        if (fireA > 0.01f) {
            float[] col = mix(WHITE, ORANGE, ti / 55f);
            Vec3 fc = c.add(0, fr * 0.55f, 0);
            Gx.sphereAt(buf, ps, FIRE, true, fc, fr, 0.92f, 22, 30, Gx.cell((int) (ti * 0.6f)), 1f, 1f, 0f, col, fireA);
            Gx.sphereAt(buf, ps, FIRE, true, fc, fr * 1.08f, 0.92f, 16, 24, Gx.cell((int) (ti * 0.6f) + 5), 1f, 1f, 0.3f, EMBER, 0.55f * fireA);
            float hot = 1f - smooth(ti / 34f);
            Gx.sphereAt(buf, ps, GLOW, true, fc, fr * 0.62f, 0.95f, 14, 20, Gx.ALL, 1f, 1f, 0f, WHITE, hot);
            Gx.billboard(buf, ps, GLOW, true, fc, fr * 2.1f, 0f, ORANGE, 0.5f * fireA, null);
        }

        // ---- stage 2b: the expanding white and pale-blue energy shell
        float sr = GarouTimeline.shellRadius(ti);
        float shellA = (1f - smooth((ti - 20f) / 30f)) * smooth(ti / 2f);
        if (shellA > 0.01f) {
            Gx.sphereAt(buf, ps, SHELL, true, c, sr, 1f, 26, 40, Gx.ALL, 4f, 2f, ti * 0.02f, mix(WHITE, PALE, ti / 20f), 0.85f * shellA);
            Gx.sphereAt(buf, ps, SHELL, true, c, sr * 0.965f, 1f, 22, 32, Gx.ALL, 3f, 2f, -ti * 0.03f, PALE, 0.5f * shellA);
            Gx.sphereAt(buf, ps, GLOW, true, c, sr * 1.01f, 1f, 18, 28, Gx.ALL, 1f, 1f, 0f, PALE, 0.14f * shellA);
        }

        // ---- stage 2c: the mushroom cloud
        float cloudA = smooth((ti - 3f) / 9f) * (1f - smooth((ti - 105f) / 45f));
        if (cloudA > 0.01f) {
            float capY = GarouTimeline.capHeight(ti), capR = GarouTimeline.capRadius(ti), stemR = GarouTimeline.stemRadius(ti);
            float age01 = c01(ti / 60f);
            float[] smoke = mix(WARM, SOOT, age01 * 1.1f);
            // the stem
            float stemTop = capY - capR * 0.18f;
            ps.pushPose();
            ps.translate(c.x, c.y, c.z);
            ps.mulPose(Axis.YP.rotationDegrees(ti * 0.8f));
            Gx.cone(Gx.trans(buf, SMOKE), ps, 0f, stemTop * 0.45f, stemR * 2.3f, stemR * 1.05f, 22, 3f, 1.5f, ti * 0.004f, smoke, 0.88f * cloudA, 0.88f * cloudA);
            Gx.cone(Gx.trans(buf, SMOKE), ps, stemTop * 0.45f, stemTop, stemR * 1.05f, stemR * 1.5f, 22, 3f, 1.5f, ti * 0.004f, smoke, 0.88f * cloudA, 0.8f * cloudA);
            float glowA = (1f - smooth(ti / 70f)) * cloudA;
            Gx.cone(Gx.add(buf, FIRE_TILE), ps, 0f, stemTop, stemR * 1.9f, stemR * 1.1f, 22, 2f, 3f, -ti * 0.01f, EMBER, 0.7f * glowA, 0.3f * glowA);
            ps.popPose();
            // the cap, with a hot underside and a second bulge on top
            Vec3 cc = c.add(0, capY, 0);
            Gx.sphereAt(buf, ps, SMOKE, false, cc, capR, 0.62f, 24, 36, Gx.ALL, 3f, 1.5f, ti * 0.003f, smoke, 0.93f * cloudA);
            Gx.sphereAt(buf, ps, SMOKE, false, cc.add(0, capR * 0.34f, 0), capR * 0.62f, 0.85f, 18, 28, Gx.ALL, 2f, 1.5f, -ti * 0.004f, mix(smoke, WHITE, 0.15f), 0.9f * cloudA);
            float under = (1f - smooth(ti / 75f)) * cloudA;
            Gx.sphereAt(buf, ps, FIRE, true, cc.add(0, -capR * 0.14f, 0), capR * 0.86f, 0.45f, 18, 28, Gx.cell((int) (ti * 0.5f)), 1f, 1f, 0f, ORANGE, 0.85f * under);
            Gx.billboard(buf, ps, GLOW, true, cc, capR * 2.2f, 0f, EMBER, 0.38f * under, null);
            // the roll of the cap's edge
            ps.pushPose();
            ps.translate(cc.x, cc.y - capR * 0.12f, cc.z);
            Gx.ring(Gx.trans(buf, SMOKE), ps, capR * 0.82f, capR * 1.16f, 48, 6f, ti * 0.004f, smoke, 0.8f * cloudA);
            ps.popPose();
        }

        // ---- stage 2d: the small explosions around the main blast
        for (int i = 0; i < GarouTimeline.SAT_COUNT; i++) {
            float lt = ti - GarouTimeline.satTick(i);
            if (lt < 0f || lt > 60f) {
                continue;
            }
            Vec3 p = NuclearFissionEntity.satPoint(e.level(), e.impactPos(), i, e.getId()).subtract(base);
            float rr = 1.2f + 6.5f * ease(lt / 12f);
            float a = 1f - smooth((lt - 6f) / 26f);
            Gx.billboard(buf, ps, GLOW, true, p.add(0, 2, 0), 12f * (1f - smooth(lt / 8f)) + 1f, 0f, WHITE, 1f - smooth(lt / 8f), null);
            Gx.sphereAt(buf, ps, FIRE, true, p.add(0, rr * 0.55f, 0), rr, 0.9f, 14, 20, Gx.cell((int) (lt * 0.7f) + i), 1f, 1f, 0f, mix(WHITE, ORANGE, lt / 14f), a);
            float sa = smooth((lt - 3f) / 8f) * (1f - smooth((lt - 24f) / 36f));
            Gx.sphereAt(buf, ps, SMOKE, false, p.add(0, 3f + lt * 0.16f, 0), 2.4f + 3.2f * smooth(lt / 40f), 0.9f, 12, 18, Gx.ALL, 2f, 1f, i * 0.2f, mix(WARM, SOOT, lt / 25f), 0.8f * sa);
        }

        // ---- stage 3: the last shockwave that pushes enemies away
        float st = ti - GarouTimeline.FINAL_SHOCK;
        if (st >= 0f && st < 24f) {
            float f = st / 20f;
            float rr = (float) GarouTimeline.SHOCK_RADIUS * ease(f);
            float al = 1f - smooth((st - 6f) / 18f);
            float w = 2.2f + rr * 0.1f;
            Gx.ringAt(buf, ps, RING, c.add(0, 0.35, 0), Math.max(0f, rr - w), rr + w * 0.35f, 90, 18f, st * 0.01f, PALE, al);
            Gx.ringAt(buf, ps, RING, c.add(0, 0.35, 0), Math.max(0f, rr - w * 3f), rr, 90, 18f, -st * 0.01f, WHITE, al * 0.3f);
            Gx.ringAt(buf, ps, RING, c.add(0, 2.5 + st * 0.3, 0), rr * 0.9f - 0.5f, rr * 0.9f + 0.5f, 90, 16f, 0f, WHITE, al * 0.8f);
        }
    }
}
