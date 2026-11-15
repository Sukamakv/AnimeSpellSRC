package com.example.jjkspells.garou.client.render;

import com.example.jjkspells.garou.client.AllLifeFx;
import com.example.jjkspells.garou.entity.AllLifeEntity;
import com.example.jjkspells.garou.entity.GarouTimeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.garou.entity.AllLifeTimeline.*;
import static com.example.jjkspells.garou.entity.GarouTimeline.c01;
import static com.example.jjkspells.garou.entity.GarouTimeline.ease;
import static com.example.jjkspells.garou.entity.GarouTimeline.lerp;
import static com.example.jjkspells.garou.entity.GarouTimeline.smooth;

/**
 * Draws All Life Eradication Fist: the cosmic stance aura, the five strikes with their own impact animations, the nuclear
 * fission with several mushroom clouds, the gravitational sphere that swallows them, the white collapse and the final cosmic
 * shockwave. Every position is relative to the entity base and everything is a function of the age.
 */
public class AllLifeRenderer extends EntityRenderer<AllLifeEntity> {
    private static final ResourceLocation GLOW = Gx.tex("glow"), RINGC = Gx.tex("ringc"), RING = Gx.tex("ring"), COSMIC = Gx.tex("cosmic"),
            BURST = Gx.tex("burst"), FIRE = Gx.tex("fire"), SMOKE = Gx.tex("smoke"), SHELL = Gx.tex("shell"), GROUND = Gx.tex("ground"),
            SCORCH = Gx.tex("scorch"), STREAK = Gx.tex("streak"), GALAXY = Gx.tex("galaxy"), STARS = Gx.tex("stars"), VOID = Gx.tex("void");

    private static final float[] WHITE = {1f, 1f, 1f}, PALE = {0.78f, 0.9f, 1f}, LILAC = {0.78f, 0.62f, 1f}, VIOLET = {0.55f, 0.35f, 1f},
            ORANGE = {1f, 0.62f, 0.28f}, EMBER = {1f, 0.45f, 0.14f}, SOOT = {0.6f, 0.52f, 0.48f}, WARM = {1f, 0.85f, 0.6f},
            BLACK = {0.02f, 0.01f, 0.05f};

    public AllLifeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(AllLifeEntity e, Frustum f, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(AllLifeEntity e) {
        return GLOW;
    }

    private static float[] mix(float[] a, float[] b, float f) {
        f = c01(f);
        return new float[]{lerp(a[0], b[0], f), lerp(a[1], b[1], f), lerp(a[2], b[2], f)};
    }

    /** A point on the caster's body: side (+ = right), forward and up in the caster's facing frame. */
    private static Vec3 body(Entity o, float pt, double side, double fwd, double up) {
        double yaw = Math.toRadians(o.getViewYRot(pt));
        Vec3 f = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), r = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return o.getPosition(pt).add(r.scale(side)).add(f.scale(fwd)).add(0, up, 0);
    }

    @Override
    public void render(AllLifeEntity e, float yawIn, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount - 1 + pt;
        if (t < 0) {
            return;
        }
        Vec3 base = e.getPosition(pt);
        Entity owner = e.getOwnerEntity();
        int A = e.assaultAge();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(base);
        float ti = A >= 0 ? t - A - NUKE : -1000f;

        if (owner != null && (A < 0 || ti < FREE)) {
            drawCaster(e, owner, t, pt, ps, buf, base, cam, A);
        }
        if (A >= 0) {
            drawStrikes(e, owner, t - A, pt, ps, buf, base, cam);
            if (ti >= 0f) {
                drawNuclear(e, ti, ps, buf, base, cam);
                drawGravity(e, ti, ps, buf, base, cam);
                drawShock(e, ti, ps, buf, base, cam);
            }
        }
    }

    // ------------------------------------------------------------------ the caster

    private void drawCaster(AllLifeEntity e, Entity owner, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, int A) {
        boolean first = owner == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;
        float bright = A < 0 ? smooth(t / STANCE) : 1f;
        float a = A >= 0 ? t - A : -1f;
        float fade = 1f;
        if (A >= 0) {
            float ti = a - NUKE;
            fade = 1f - smooth((ti - COLLAPSE - 6f) / 30f) * 0f;
        }
        Vec3 chest = body(owner, pt, 0, 0, 1.25).subtract(base);

        // the starfield on the body: a glowing cosmic aura around the whole figure
        if (!first) {
            Vec3 foot = owner.getPosition(pt).subtract(base);
            ps.pushPose();
            ps.translate(foot.x, foot.y, foot.z);
            ps.mulPose(Axis.YP.rotationDegrees(t * 3f));
            Gx.cone(Gx.add(buf, STARS), ps, -0.05f, 2.0f, 0.62f, 0.55f, 20, 2f, 2f, t * 0.01f, mix(PALE, LILAC, 0.4f), (0.35f + 0.65f * bright) * fade, (0.15f + 0.4f * bright) * fade);
            ps.popPose();
            Gx.billboard(buf, ps, GLOW, true, chest, 2.8f + 2.2f * bright, 0f, LILAC, 0.35f * bright * fade, null);
        }
        // the spiral galaxy on the chest grows brighter during the stance
        Vec3 look = owner.getLookAngle();
        Vec3 gal = chest.add(look.x * 0.16, 0.05, look.z * 0.16);
        Gx.billboard(buf, ps, GALAXY, true, gal, 0.85f + 0.5f * bright, t * 2.5f, WHITE, (0.4f + 0.6f * bright) * fade, null);
        Gx.billboard(buf, ps, GLOW, true, gal, 1.4f + 1.2f * bright, 0f, PALE, 0.5f * bright * fade, null);

        // cosmic energy circulating around both arms
        for (int h = 0; h < 2; h++) {
            float sgn = h == 0 ? 1f : -1f;
            Vec3 hand = handPos(owner, pt, h, A < 0 ? 0f : a).subtract(base);
            float gl = (0.4f + 0.6f * bright) * fade;
            Gx.billboard(buf, ps, GLOW, true, hand, 0.9f + 0.9f * bright, 0f, WHITE, 0.6f * gl, null);
            Gx.billboard(buf, ps, COSMIC, true, hand, 0.55f + 0.5f * bright, t * 6f * sgn, PALE, 0.9f * gl, Gx.cell((int) (t * 1.3f) + h * 5));
            Vec3 shoulder = body(owner, pt, 0.38 * sgn, 0, 1.45).subtract(base);
            Vec3 axis = hand.subtract(shoulder);
            for (int i = 0; i < 3; i++) {
                float ph = t * 0.35f * sgn + i * 2.094f;
                Vec3 prev = null;
                for (int s = 0; s < 8; s++) {
                    float k = s / 7f;
                    Vec3 mid = shoulder.add(axis.scale(0.15 + 0.85 * ((k + t * 0.04f * sgn + i * 0.33f) % 1.0f)));
                    Vec3 off = new Vec3(Math.cos(ph + k * 5f), Math.sin(ph + k * 5f), Math.cos(ph * 0.7f + k * 3f)).scale(0.22 + 0.1 * bright);
                    Vec3 p = mid.add(off);
                    if (prev != null && prev.distanceToSqr(p) < 0.6) {
                        Gx.ribbon(buf, ps, STREAK, prev, p, cam, 0.035f, i == 0 ? WHITE : PALE, 0.15f * gl, 0.9f * gl);
                    }
                    prev = p;
                }
            }
        }
        // dash streak
        if (A < 0 && t > STANCE) {
            float dt = t - STANCE;
            Vec3 lookV = owner.getLookAngle().normalize();
            Vec3 b = owner.getPosition(pt).add(0, 1.0, 0).subtract(base);
            Vec3 tail = b.subtract(lookV.scale(5f + Math.min(dt, 6f) * 2.5f));
            Gx.ribbon(buf, ps, STREAK, tail, b.add(lookV.scale(1.2)), cam, 1.1f, PALE, 0f, 0.8f);
            Gx.ribbon(buf, ps, STREAK, tail.add(lookV.scale(2)), b.add(lookV.scale(1.2)), cam, 0.5f, WHITE, 0.1f, 1f);
            Gx.billboard(buf, ps, BURST, true, b.add(lookV.scale(1.2)), 2.2f, dt * 30f, WHITE, 0.7f, null);
        }
    }

    /** Where a hand is: in guard during the stance, extended while its strike is happening. */
    private static Vec3 handPos(Entity o, float pt, int hand, float a) {
        double sgn = hand == 0 ? 1.0 : -1.0;
        Vec3 guard = body(o, pt, 0.42 * sgn, 0.35, 1.15);
        if (a <= 0f) {
            return guard;
        }
        // right hand: strike 0, left hand: strike 1, both hands: strike 4
        float p = 0f;
        if (hand == 0) {
            p = Math.max(pulse(a, STRIKE[0]), pulse(a, STRIKE[4]));
        } else {
            p = Math.max(pulse(a, STRIKE[1]), pulse(a, STRIKE[4]));
        }
        Vec3 reach = body(o, pt, (hand == 4 ? 0 : 0.1 * sgn), 2.0, 1.35);
        return guard.add(reach.subtract(guard).scale(p));
    }

    private static float pulse(float a, int start) {
        float k = (a - start + 1f) / 6f;
        return k <= 0f || k >= 1f ? 0f : (float) Math.sin(Math.PI * k);
    }

    // ------------------------------------------------------------------ the five strikes

    private void drawStrikes(AllLifeEntity e, Entity owner, float a, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam) {
        Vec3 p = AllLifeFx.strikePoint(e).subtract(base);
        Vec3 from = owner != null ? owner.getPosition(pt).add(0, 1.2, 0).subtract(base) : p.add(0, 0, -2);
        Vec3 dir = p.subtract(from);
        dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(dir).normalize();

        for (int i = 0; i < STRIKE.length; i++) {
            // the limb glow while the strike travels
            float lt = a - STRIKE[i];
            if (owner != null && lt > -2f && lt < HIT_DELAY + 3f) {
                float g = (float) Math.sin(Math.PI * c01((lt + 2f) / (HIT_DELAY + 5f)));
                Vec3 limb = switch (i) {
                    case 2 -> body(owner, pt, 0.2, 1.4, 0.9).subtract(base);
                    case 3 -> body(owner, pt, 0.3, 1.2, 1.3).subtract(base);
                    case 4 -> body(owner, pt, 0, 1.9, 1.3).subtract(base);
                    default -> handPos(owner, pt, i, a).subtract(base);
                };
                Gx.billboard(buf, ps, GLOW, true, limb, 1.2f + 0.5f * i, 0f, i % 2 == 0 ? WHITE : LILAC, 0.9f * g, null);
                Gx.billboard(buf, ps, BURST, true, limb, 0.9f + 0.3f * i, a * 40f, PALE, 0.7f * g, null);
                if (i == 2) {
                    // the spinning kick leaves a crescent trail around the caster
                    Vec3 foot = owner.getPosition(pt).subtract(base);
                    ps.pushPose();
                    ps.translate(foot.x, foot.y + 0.9, foot.z);
                    float yaw = -owner.getViewYRot(pt);
                    ps.mulPose(Axis.YP.rotationDegrees(yaw + 90f + lt * 90f));
                    Gx.arc(Gx.add(buf, RING), ps, 1.1f, 2.0f, -2.6f, 0.4f, 24, 3f, 0f, WHITE, 0f, 0.9f * g);
                    Gx.arc(Gx.add(buf, RING), ps, 1.3f, 1.8f, -2.6f, 0.4f, 24, 3f, 0f, LILAC, 0f, 0.5f * g);
                    ps.popPose();
                }
            }

            float k = a - (STRIKE[i] + HIT_DELAY);
            if (k < 0f || k > 22f) {
                continue;
            }
            float f = c01(k / 16f);
            float al = 1f - smooth(k / 18f);
            float flashA = 1f - smooth(k / 5f);
            float[] col = i % 2 == 0 ? WHITE : LILAC;
            Vec3 ip = p;
            Gx.billboard(buf, ps, GLOW, true, ip, (3.5f + i * 1.8f) * (0.6f + 0.4f * ease(k / 4f)), 0f, col, flashA, null);
            switch (i) {
                case 0 -> {
                    // right punch: white burst, vertical ring, radial rays
                    Gx.billboard(buf, ps, BURST, true, ip, 5.5f * ease(k / 5f), k * 6f, WHITE, al, null);
                    ps.pushPose();
                    ps.translate(ip.x, ip.y, ip.z);
                    Gx.faceY(ps, dir);
                    float rr = 0.5f + 5.5f * ease(f);
                    Gx.ring(Gx.add(buf, RING), ps, rr - 0.12f, rr + 0.12f, 40, 6f, k * 0.02f, WHITE, al);
                    ps.popPose();
                    for (int r = 0; r < 8; r++) {
                        double an = r * Math.PI / 4;
                        Vec3 d = right.scale(Math.cos(an)).add(up.scale(Math.sin(an)));
                        Gx.ribbon(buf, ps, STREAK, ip.add(d.scale(0.8 + 1.5 * ease(k / 6f))), ip.add(d.scale(1.8 + 4.0 * ease(k / 8f))), cam, 0.09f, WHITE, 0.9f * al, 0f);
                    }
                }
                case 1 -> {
                    // left punch: lilac burst, tilted ring, X-shaped slash
                    Gx.billboard(buf, ps, BURST, true, ip, 6f * ease(k / 5f), -k * 7f, LILAC, al, null);
                    ps.pushPose();
                    ps.translate(ip.x, ip.y, ip.z);
                    Gx.faceY(ps, dir.add(right.scale(0.8)));
                    float rr = 0.5f + 6f * ease(f);
                    Gx.ring(Gx.add(buf, RING), ps, rr - 0.14f, rr + 0.14f, 40, 6f, -k * 0.02f, LILAC, al);
                    ps.popPose();
                    float L = 1.0f + 5.0f * ease(k / 6f);
                    Gx.ribbon(buf, ps, STREAK, ip.add(right.scale(-L)).add(up.scale(L)), ip.add(right.scale(L)).add(up.scale(-L)), cam, 0.16f, WHITE, 0f, al);
                    Gx.ribbon(buf, ps, STREAK, ip.add(right.scale(L)).add(up.scale(L)), ip.add(right.scale(-L)).add(up.scale(-L)), cam, 0.16f, LILAC, 0f, al);
                }
                case 2 -> {
                    // spinning kick: flat rings spreading horizontally and a big crescent
                    for (int r = 0; r < 3; r++) {
                        float lk = k - r * 2f;
                        if (lk < 0f) {
                            continue;
                        }
                        float rr = 0.8f + 8f * ease(lk / 14f);
                        Gx.ringAt(buf, ps, RING, ip, rr - 0.2f, rr + 0.2f, 48, 8f, lk * 0.02f, r == 1 ? LILAC : WHITE, al * (1f - r * 0.2f));
                    }
                    ps.pushPose();
                    ps.translate(ip.x, ip.y, ip.z);
                    ps.mulPose(Axis.YP.rotationDegrees(k * 25f));
                    Gx.arc(Gx.add(buf, RING), ps, 2.0f + 5f * ease(f), 3.2f + 5f * ease(f), 0f, 3.6f, 30, 3f, 0f, WHITE, 0.9f * al, 0f);
                    ps.popPose();
                    Gx.billboard(buf, ps, BURST, true, ip, 5f * ease(k / 5f), k * 5f, PALE, 0.8f * al, null);
                }
                case 3 -> {
                    // elbow: a narrow piercing ray through the target and stacked rings
                    Vec3 tipA = ip.subtract(dir.scale(2.0)), tipB = ip.add(dir.scale(3.0 + 11.0 * ease(k / 5f)));
                    Gx.ribbon(buf, ps, STREAK, tipA, tipB, cam, 0.5f * (1f - f), WHITE, 0.9f * al, 0f);
                    Gx.ribbon(buf, ps, STREAK, tipA, tipB, cam, 1.1f * (1f - f), PALE, 0.3f * al, 0f);
                    for (int r = 0; r < 4; r++) {
                        float lk = k - r * 1.5f;
                        if (lk < 0f) {
                            continue;
                        }
                        ps.pushPose();
                        Vec3 rp = ip.add(dir.scale(r * 1.4f + 3.0f * ease(lk / 8f)));
                        ps.translate(rp.x, rp.y, rp.z);
                        Gx.faceY(ps, dir);
                        float rr = 0.4f + 2.2f * ease(lk / 10f) + r * 0.3f;
                        Gx.ring(Gx.add(buf, RING), ps, rr - 0.1f, rr + 0.1f, 32, 5f, 0f, r % 2 == 0 ? WHITE : LILAC, al);
                        ps.popPose();
                    }
                    Gx.billboard(buf, ps, BURST, true, ip, 4f * ease(k / 4f), k * 9f, WHITE, al, null);
                }
                default -> {
                    // final double-fist: a big sphere flash, giant burst, wide rings and ten rays
                    float fl = 1f - smooth(k / 7f);
                    Gx.sphereAt(buf, ps, GLOW, true, ip, 1.5f + 6f * ease(k / 6f), 1f, 12, 16, Gx.ALL, 1f, 1f, 0f, WHITE, fl);
                    Gx.billboard(buf, ps, BURST, true, ip, 14f * ease(k / 6f), k * 3f, WHITE, al, null);
                    Gx.billboard(buf, ps, BURST, true, ip, 9f * ease(k / 6f), -k * 5f, VIOLET, 0.8f * al, null);
                    Gx.billboard(buf, ps, RINGC, true, ip, 16f * ease(f), k * 6f, WHITE, 0.8f * al, null);
                    for (int r = 0; r < 2; r++) {
                        ps.pushPose();
                        ps.translate(ip.x, ip.y, ip.z);
                        Gx.faceY(ps, r == 0 ? dir : up);
                        float rr = 1f + 12f * ease(f) + r * 2f;
                        Gx.ring(Gx.add(buf, RING), ps, rr - 0.25f, rr + 0.25f, 48, 8f, k * 0.02f, r == 0 ? WHITE : PALE, al);
                        ps.popPose();
                    }
                    for (int r = 0; r < 10; r++) {
                        double an = r * Math.PI / 5 + 0.3;
                        Vec3 d = right.scale(Math.cos(an)).add(up.scale(Math.sin(an)));
                        Gx.ribbon(buf, ps, STREAK, ip.add(d.scale(1.0)), ip.add(d.scale(2.5 + 9.0 * ease(k / 8f))), cam, 0.14f, WHITE, 0.9f * al, 0f);
                    }
                }
            }
            // the small cosmic shockwave on the ground
            Gx.ringAt(buf, ps, RING, ip.subtract(0, Math.min(1.2, ip.y - (e.centerPos().y - base.y)), 0).add(0, 0.05, 0), 0.4f + (3f + i * 1.5f) * ease(f), 0.9f + (3.8f + i * 1.5f) * ease(f), 48, 8f, 0f, PALE, 0.6f * al);
        }
    }

    // ------------------------------------------------------------------ nuclear fission

    private void drawNuclear(AllLifeEntity e, float ti, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam) {
        Vec3 c = e.centerPos().subtract(base);
        float R = (float) RADIUS;
        Vec3 gc = c.add(0, GRAV_HEIGHT, 0);
        float pull = smooth((ti - GRAV) / (float) GRAV_SPAN);
        float keep = 1f - smooth((pull - 0.55f) / 0.45f);

        // ground: lit and scorched
        float scorch = ease(ti / 6f) * (1f - smooth((ti - 100f) / 70f));
        Gx.disc(buf, ps, SCORCH, false, c.add(0, 0.07, 0), 17f * ease(ti / 6f), 17f, WHITE, 0.85f * scorch);
        float lit = ease(ti / 8f) * (1f - smooth((ti - 55f) / 90f));
        Gx.disc(buf, ps, GROUND, true, c.add(0, 0.12, 0), R * 0.95f * ease(ti / 22f), ti * 0.35f, WHITE, 0.9f * lit);
        Gx.billboard(buf, ps, GLOW, true, c.add(0, 6, 0), 56f, 0f, EMBER, 0.34f * ease(ti / 10f) * (1f - smooth((ti - 25f) / 100f)), null);

        // white flash and ground shockwaves
        float flash = 1f - smooth((ti - 1f) / 13f);
        if (flash > 0.01f) {
            Gx.billboard(buf, ps, GLOW, true, c.add(0, 3, 0), 90f * (0.5f + 0.5f * ease(ti / 6f)), 0f, WHITE, flash, null);
            Gx.billboard(buf, ps, BURST, true, c.add(0, 3, 0), 44f * ease(ti / 5f), ti * 2f, WHITE, flash, null);
            Gx.sphereAt(buf, ps, GLOW, true, c.add(0, 2, 0), 6f + 16f * ease(ti / 9f), 1f, 12, 16, Gx.ALL, 1f, 1f, 0f, WHITE, flash);
        }
        for (int i = 0; i < 3; i++) {
            float lt = ti - i * 3f;
            if (lt < 0f) {
                continue;
            }
            float rr = R * 1.15f * ease(lt / 26f);
            float al = (1f - smooth(lt / 32f)) * (i == 0 ? 1f : 0.7f);
            float w = 1.8f + rr * 0.12f;
            Gx.ringAt(buf, ps, RING, c.add(0, 0.25 + i * 0.1, 0), Math.max(0f, rr - w), rr + w * 0.4f, 80, 14f, ti * 0.01f, i == 0 ? WHITE : PALE, al);
        }
        for (int i = 0; i < 4; i++) {
            float lt = ti - 2f - i * 4f;
            if (lt < 0f) {
                continue;
            }
            float rr = R * (0.5f + 0.5f * i / 3f) * ease(c01(lt / 40f)) + 2f;
            float yy = 2f + lt * (0.5f + 0.35f * i) + 3f * i;
            float al = (1f - smooth((lt - 10f) / 30f)) * 0.95f;
            Gx.ringAt(buf, ps, RING, c.add(0, yy, 0), rr - 0.55f, rr + 0.55f, 72, 12f, -ti * 0.012f, i % 2 == 0 ? WHITE : PALE, al);
        }

        // the main fireball and the energy shell
        float fr = fireRadius(ti, BIG);
        float fireA = smooth((ti - 0.5f) / 3f) * (1f - smooth((ti - 48f) / 50f));
        if (fireA > 0.01f) {
            float[] col = mix(WHITE, ORANGE, ti / 55f);
            Vec3 fc = c.add(0, fr * 0.55f, 0);
            Gx.sphereAt(buf, ps, FIRE, true, fc, fr, 0.92f, 22, 30, Gx.cell((int) (ti * 0.6f)), 1f, 1f, 0f, col, fireA);
            Gx.sphereAt(buf, ps, FIRE, true, fc, fr * 1.08f, 0.92f, 16, 24, Gx.cell((int) (ti * 0.6f) + 5), 1f, 1f, 0.3f, EMBER, 0.55f * fireA);
            Gx.sphereAt(buf, ps, GLOW, true, fc, fr * 0.62f, 0.95f, 14, 20, Gx.ALL, 1f, 1f, 0f, WHITE, 1f - smooth(ti / 34f));
            Gx.billboard(buf, ps, GLOW, true, fc, fr * 2.1f, 0f, ORANGE, 0.5f * fireA, null);
        }
        float sr = shellRadius(ti);
        float shellA = (1f - smooth((ti - 22f) / 30f)) * smooth(ti / 2f);
        if (shellA > 0.01f) {
            Gx.sphereAt(buf, ps, SHELL, true, c, sr, 1f, 26, 40, Gx.ALL, 4f, 2f, ti * 0.02f, mix(WHITE, PALE, ti / 20f), 0.85f * shellA);
            Gx.sphereAt(buf, ps, SHELL, true, c, sr * 0.965f, 1f, 22, 32, Gx.ALL, 3f, 2f, -ti * 0.03f, PALE, 0.5f * shellA);
            Gx.sphereAt(buf, ps, GLOW, true, c, sr * 1.01f, 1f, 18, 28, Gx.ALL, 1f, 1f, 0f, PALE, 0.14f * shellA);
        }

        // the massive mushroom cloud: it is drawn toward the gravity sphere as it fades
        float cloudA = smooth((ti - 4f) / 9f) * keep;
        if (cloudA > 0.01f) {
            mushroom(ps, buf, c, gc, ti, BIG, pull, cloudA, false);
        }
        // several nuclear explosions around the target, each with a small mushroom
        for (int i = 0; i < MED_COUNT; i++) {
            float lt = ti - medTick(i);
            if (lt < 0f) {
                continue;
            }
            Vec3 p = c.add(medOffset(i, e.getId()));
            float a = (1f - smooth((lt - 6f) / 26f));
            float hot = 1f - smooth(lt / 10f);
            if (hot > 0.01f) {
                Gx.billboard(buf, ps, GLOW, true, p.add(0, 4, 0), 26f * hot + 3f, 0f, WHITE, hot, null);
                Gx.billboard(buf, ps, BURST, true, p.add(0, 4, 0), 16f * ease(lt / 4f), lt * 4f, WHITE, hot, null);
            }
            float rr = 2f + 9f * ease(lt / 14f);
            Gx.sphereAt(buf, ps, FIRE, true, p.add(0, rr * 0.55f, 0), rr, 0.9f, 16, 24, Gx.cell((int) (lt * 0.7f) + i), 1f, 1f, 0f, mix(WHITE, ORANGE, lt / 16f), a * keep);
            Gx.ringAt(buf, ps, RING, p.add(0, 0.25, 0), Math.max(0f, 11f * ease(lt / 22f) - 1f), 11f * ease(lt / 22f) + 0.5f, 48, 8f, 0f, PALE, (1f - smooth(lt / 26f)) * 0.8f);
            float ca = smooth((lt - 3f) / 9f) * keep * (1f - smooth((lt - 70f) / 30f));
            if (ca > 0.01f) {
                mushroom(ps, buf, p, gc, ti, 0.42f, pull, ca, true);
            }
        }
        // small bright blasts
        for (int i = 0; i < SAT_COUNT; i++) {
            float lt = ti - satTick(i);
            if (lt < 0f || lt > 60f) {
                continue;
            }
            Vec3 p = c.add(satOffset(i, e.getId()));
            float rr = 1.4f + 7f * ease(lt / 12f);
            float a = 1f - smooth((lt - 6f) / 26f);
            Gx.billboard(buf, ps, GLOW, true, p.add(0, 2, 0), 14f * (1f - smooth(lt / 8f)) + 1f, 0f, WHITE, 1f - smooth(lt / 8f), null);
            Gx.sphereAt(buf, ps, FIRE, true, p.add(0, rr * 0.55f, 0), rr, 0.9f, 14, 20, Gx.cell((int) (lt * 0.7f) + i), 1f, 1f, 0f, mix(WHITE, ORANGE, lt / 14f), a * keep);
        }
    }

    /** One mushroom cloud rooted at c (scale s); while pull > 0 it is shrunk and drawn toward the gravity centre gc. */
    private void mushroom(PoseStack ps, MultiBufferSource buf, Vec3 c, Vec3 gc, float ti, float s, float pull, float cloudA, boolean small) {
        float sc = small ? 0.42f : BIG;
        float capY = GarouTimeline.capHeight(ti) * sc, capR = GarouTimeline.capRadius(ti) * sc, stemR = GarouTimeline.stemRadius(ti) * sc;
        float shrink = 1f - 0.88f * pull;
        float[] smoke = mix(WARM, SOOT, c01(ti / 60f) * 1.1f);
        Vec3 cc = c.add(0, capY, 0);
        cc = cc.add(gc.subtract(cc).scale(pull));
        float stemTop = capY - capR * 0.18f;
        Vec3 tip = c.add(0, stemTop, 0);
        tip = tip.add(gc.subtract(tip).scale(pull));
        // stem: a tapered column from the ground to the (possibly pulled) cap
        Vec3 axis = tip.subtract(c);
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        if (pull > 0.01f) {
            Gx.faceY(ps, axis);
        }
        float len = pull > 0.01f ? (float) axis.length() : stemTop;
        ps.mulPose(Axis.YP.rotationDegrees(ti * 0.8f));
        Gx.cone(Gx.trans(buf, SMOKE), ps, 0f, len * 0.45f, stemR * 2.3f * shrink, stemR * 1.05f * shrink, 20, 3f, 1.5f, ti * 0.004f, smoke, 0.88f * cloudA, 0.88f * cloudA);
        Gx.cone(Gx.trans(buf, SMOKE), ps, len * 0.45f, len, stemR * 1.05f * shrink, stemR * 1.5f * shrink, 20, 3f, 1.5f, ti * 0.004f, smoke, 0.88f * cloudA, 0.8f * cloudA);
        float glowA = (1f - smooth(ti / 70f)) * cloudA;
        Gx.cone(Gx.add(buf, FIRE), ps, 0f, len, stemR * 1.9f * shrink, stemR * 1.1f * shrink, 20, 2f, 3f, -ti * 0.01f, EMBER, 0.7f * glowA, 0.3f * glowA);
        ps.popPose();
        // cap, second bulge, hot underside
        float cr = capR * shrink;
        Gx.sphereAt(buf, ps, SMOKE, false, cc, cr, 0.62f, 20, 32, Gx.ALL, 3f, 1.5f, ti * 0.003f, smoke, 0.93f * cloudA);
        Gx.sphereAt(buf, ps, SMOKE, false, cc.add(0, cr * 0.34f, 0), cr * 0.62f, 0.85f, 16, 24, Gx.ALL, 2f, 1.5f, -ti * 0.004f, mix(smoke, WHITE, 0.15f), 0.9f * cloudA);
        float under = (1f - smooth(ti / 75f)) * cloudA;
        Gx.sphereAt(buf, ps, FIRE, true, cc.add(0, -cr * 0.14f, 0), cr * 0.86f, 0.45f, 16, 24, Gx.cell((int) (ti * 0.5f)), 1f, 1f, 0f, ORANGE, 0.85f * under);
        Gx.billboard(buf, ps, GLOW, true, cc, cr * 2.2f, 0f, EMBER, 0.38f * under, null);
        ps.pushPose();
        ps.translate(cc.x, cc.y - cr * 0.12f, cc.z);
        Gx.ring(Gx.trans(buf, SMOKE), ps, cr * 0.82f, cr * 1.16f, 40, 6f, ti * 0.004f, smoke, 0.8f * cloudA);
        ps.popPose();
    }

    // ------------------------------------------------------------------ gravity collapse

    private void drawGravity(AllLifeEntity e, float ti, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam) {
        Vec3 gc = e.centerPos().subtract(base).add(0, GRAV_HEIGHT, 0);
        float g = ti - GRAV;
        if (g >= 0f && ti < COLLAPSE) {
            float r = gravRadius(g);
            float form = smooth(g / 10f);
            float comp = c01((g - GRAV_FORM) / (float) (GRAV_SPAN - GRAV_FORM));
            float heat = 0.35f + 0.65f * comp;
            // the warped space behind it: a galaxy disc and lensing rings
            Gx.billboard(buf, ps, GALAXY, true, gc, r * 4.2f, g * 2.2f, mix(PALE, WHITE, comp), 0.55f * form, null);
            Gx.billboard(buf, ps, GLOW, true, gc, r * 5.5f, 0f, mix(VIOLET, WHITE, comp * 0.7f), (0.3f + 0.4f * comp) * form, null);
            for (int i = 0; i < 4; i++) {
                float rr = r * (1.5f + 0.55f * i) * (1f + 0.07f * (float) Math.sin(g * 0.6f + i));
                Gx.billboard(buf, ps, RINGC, true, gc, rr * 2f, g * (4f + i * 3f) * (i % 2 == 0 ? 1f : -1f), i % 2 == 0 ? WHITE : LILAC, (0.7f - 0.12f * i) * form, null);
            }
            // starfield spheres: the stars of the swallowed space
            Gx.sphereAt(buf, ps, STARS, true, gc, r * 2.2f, 1f, 20, 30, Gx.ALL, 6f, 3f, g * 0.01f, PALE, 0.25f * form);
            Gx.sphereAt(buf, ps, STARS, true, gc, r * 1.5f, 1f, 20, 30, Gx.ALL, 5f, 3f, -g * 0.02f, WHITE, 0.35f * form);
            // the black core and its bright rim
            Gx.sphereAt(buf, ps, VOID, false, gc, r, 1f, 22, 32, Gx.ALL, 1f, 1f, 0f, BLACK, 0.95f * form);
            Gx.sphereAt(buf, ps, SHELL, true, gc, r * 1.03f, 1f, 22, 32, Gx.ALL, 4f, 2f, g * 0.05f, mix(LILAC, WHITE, comp), 0.8f * heat * form);
            Gx.sphereAt(buf, ps, GLOW, true, gc, r * 1.2f, 1f, 16, 24, Gx.ALL, 1f, 1f, 0f, mix(VIOLET, WHITE, comp), 0.28f * heat * form);
            // two tilted accretion discs
            for (int i = 0; i < 2; i++) {
                ps.pushPose();
                ps.translate(gc.x, gc.y, gc.z);
                ps.mulPose(Axis.YP.rotationDegrees(i * 70f + g * (i == 0 ? 6f : -8f)));
                ps.mulPose(Axis.XP.rotationDegrees(i == 0 ? 72f : 25f));
                Gx.ring(Gx.add(buf, RING), ps, r * 1.35f, r * (2.1f + 0.4f * i), 64, 8f, g * 0.03f * (i == 0 ? 1f : -1f), i == 0 ? WHITE : PALE, 0.9f * form);
                Gx.ring(Gx.add(buf, RING), ps, r * 1.1f, r * 1.35f, 64, 8f, -g * 0.05f, LILAC, 0.55f * form);
                ps.popPose();
            }
            // the explosion's energy spirals in: ribbon arms toward the centre
            for (int arm = 0; arm < 10; arm++) {
                float base0 = arm * 36f + g * 7f;
                Vec3 prev = null;
                for (int sgm = 0; sgm <= 14; sgm++) {
                    float k = sgm / 14f;
                    float rad = lerp(r * 1.2f + 30f * (1f - comp * 0.5f), r * 1.1f, (float) Math.pow(k, 0.8f));
                    double ang = Math.toRadians(base0 + k * 220f);
                    double yy = Math.sin(Math.toRadians(arm * 47f)) * rad * 0.45f * (1f - k);
                    Vec3 p = gc.add(Math.cos(ang) * rad, yy, Math.sin(ang) * rad);
                    if (prev != null) {
                        Gx.ribbon(buf, ps, STREAK, prev, p, cam, 0.08f + 0.25f * k, arm % 3 == 0 ? ORANGE : (arm % 2 == 0 ? WHITE : PALE), 0.05f * form, (0.25f + 0.65f * k) * form);
                    }
                    prev = p;
                }
            }
        }
        // the final white flash
        float k = ti - COLLAPSE;
        if (k >= 0f && k < 22f) {
            float al = 1f - smooth(k / 18f);
            Gx.billboard(buf, ps, GLOW, true, gc, 90f * (0.4f + 0.6f * ease(k / 6f)), 0f, WHITE, al, null);
            Gx.billboard(buf, ps, GLOW, true, gc, 40f, 0f, PALE, al, null);
            Gx.sphereAt(buf, ps, GLOW, true, gc, 3f + 20f * ease(k / 8f), 1f, 16, 24, Gx.ALL, 1f, 1f, 0f, WHITE, al);
            Gx.billboard(buf, ps, BURST, true, gc, 50f * ease(k / 6f), k * 3f, WHITE, al, null);
            Gx.billboard(buf, ps, RINGC, true, gc, 4f + 70f * ease(k / 14f), 0f, WHITE, al * 0.9f, null);
            Gx.billboard(buf, ps, GALAXY, true, gc, 6f + 40f * ease(k / 10f), k * 20f, LILAC, 0.8f * al, null);
        }
    }

    // ------------------------------------------------------------------ the final cosmic shockwave

    private void drawShock(AllLifeEntity e, float ti, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam) {
        float s = ti - SHOCK0;
        if (s < 0f) {
            return;
        }
        Vec3 c = e.centerPos().subtract(base);
        float R = (float) RADIUS;
        float f = c01(s / SHOCK_LEN);
        float fade = 1f - smooth((s - SHOCK_LEN * 0.55f) / (SHOCK_LEN * 0.9f));
        if (fade <= 0.01f) {
            return;
        }
        float rr = R * ease(f);
        float w = 2.0f + rr * 0.12f;
        // ground ring
        Gx.ringAt(buf, ps, RING, c.add(0, 0.3, 0), Math.max(0f, rr - w), rr + w * 0.35f, 96, 20f, s * 0.01f, WHITE, fade);
        Gx.ringAt(buf, ps, RING, c.add(0, 0.32, 0), Math.max(0f, rr - w * 3.5f), rr, 96, 20f, -s * 0.01f, LILAC, 0.45f * fade);
        // the cosmic dome: stars and a pale shell
        Gx.sphereAt(buf, ps, STARS, true, c, rr, 1f, 26, 40, Gx.ALL, 8f, 4f, s * 0.01f, PALE, 0.55f * fade);
        Gx.sphereAt(buf, ps, SHELL, true, c, rr * 0.99f, 1f, 24, 36, Gx.ALL, 4f, 2f, -s * 0.02f, LILAC, 0.55f * fade);
        Gx.sphereAt(buf, ps, GLOW, true, c, rr * 1.02f, 1f, 18, 28, Gx.ALL, 1f, 1f, 0f, VIOLET, 0.16f * fade);
        // rings at several elevations
        float[] el = {15f, 40f, 65f};
        for (int i = 0; i < el.length; i++) {
            double ang = Math.toRadians(el[i]);
            float rad = (float) (rr * Math.cos(ang)), yy = (float) (rr * Math.sin(ang));
            Gx.ringAt(buf, ps, RING, c.add(0, yy, 0), Math.max(0f, rad - 0.5f), rad + 0.5f, 80, 14f, s * 0.015f * (i % 2 == 0 ? 1 : -1), i % 2 == 0 ? WHITE : PALE, 0.8f * fade);
        }
        // a rotating galaxy on the ground and the glow of the wave
        Gx.disc(buf, ps, GALAXY, true, c.add(0, 0.2, 0), rr, s * 2f, WHITE, 0.5f * fade);
        Gx.billboard(buf, ps, GLOW, true, c.add(0, 2, 0), rr * 1.5f, 0f, LILAC, 0.25f * fade * (1f - f), null);
    }
}
