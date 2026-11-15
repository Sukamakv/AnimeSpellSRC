package com.example.jjkspells.malenia.client.render;

import com.example.jjkspells.malenia.entity.GoddessEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.example.jjkspells.malenia.client.render.Gfx.*;

/** Goddess of Rot: wings and halo, the marked circle, the dive, the colossal flower, eight falling blades, the judgment wave and the collapsing sphere. */
public class GoddessRenderer extends MelRenderer<GoddessEntity> {
    private static final float R_IMPACT = 16f, R_FLOWER = 25f, R_JUDG = 20f, R_SWORD = 5f;

    public GoddessRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    protected void draw(GoddessEntity e, float t, float pt, PoseStack ps, MultiBufferSource buf, Vec3 base, Vec3 cam, Entity owner, Vec3 ownerP) {
        Vec3 p = e.getPoint().subtract(base);
        float y = (float) p.y;
        int wingsEnd = e.getWingsEnd();
        float wingFade = 1f - c01((t - wingsEnd + 20f) / 20f);
        // ---- the goddess herself
        if (owner != null) {
            float in = c01(t / 20f);
            if (t < GoddessEntity.T_RISE || (t >= GoddessEntity.T_FINAL && t < GoddessEntity.T_END)) {
                // Divine Scarlet Protection
                float k = t < GoddessEntity.T_RISE ? in : 1f - c01((t - GoddessEntity.T_END + 10f) / 10f);
                Fx.glow(buf, ps, ownerP.add(0, 1.0, 0), 2.6f, SCARLET, 0.5f * k);
                Fx.glow(buf, ps, ownerP.add(0, 1.0, 0), 1.6f, GOLD, 0.35f * k);
            }
            if (t < GoddessEntity.T_RISE) {
                float s = in;
                Draw.disc(add(buf, RUNE), ps, 3.6f * s, t * 3f, (float) ownerP.y + 0.08f, CRIMSON, 0.85f * s);
                Draw.disc(add(buf, CIRCLE), ps, 2.2f * s, -t * 4f, (float) ownerP.y + 0.09f, GOLD, 0.7f * s);
                Draw.band(trans(buf, MIST), ps, 1.6f, 0f, 2.6f, 24, 4f, t * 0.02f, ROTC, 0.45f * s, 0f);
            }
            if (t >= GoddessEntity.T_RISE && t < GoddessEntity.T_IMPACT) {
                Draw.disc(add(buf, RUNE), ps, 3.0f, t * 6f, (float) ownerP.y - 0.5f, CRIMSON, 0.8f);
            }
            if (t >= GoddessEntity.T_DIVE && t < GoddessEntity.T_IMPACT + 2 && owner != null) {
                Vec3 up = ownerP.subtract(p);
                up = up.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : up.normalize();
                Fx.trail(buf, ps, java.util.List.of(ownerP.add(up.scale(9.0)), ownerP.add(up.scale(4.5)), ownerP.add(0, 1.0, 0)), cam, 1.5f, CRIMSON, 0.9f);
                Fx.trail(buf, ps, java.util.List.of(ownerP.add(up.scale(9.0)), ownerP.add(up.scale(4.5)), ownerP.add(0, 1.0, 0)), cam, 0.6f, WHITE, 0.9f);
            }
            if (t >= GoddessEntity.T_WINGS && wingFade > 0.01f) {
                float spread = smooth((t - GoddessEntity.T_WINGS) / 30f);
                float flap = spread * (0.92f + 0.08f * Mth.sin(t * 0.22f));
                float yaw = owner instanceof LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : 0f;
                // the wing is drawn behind the body: yaw of the body already points where she faces
                Fx.wings(buf, ps, ownerP.add(0, 1.15, 0), yaw, flap, 5.0f, WHITE, 0.95f * wingFade);
                Fx.wings(buf, ps, ownerP.add(0, 1.0, 0), yaw, flap * 0.9f, 7.0f, SCARLET, 0.35f * wingFade);
                ps.pushPose();
                ps.translate(ownerP.x, ownerP.y + 2.05, ownerP.z);
                billboard(ps);
                Draw.xy(add(buf, HALO), ps, 0.9f, 0.9f, GOLD, 0.9f * spread * wingFade);
                ps.popPose();
                int nb = 6;
                for (int i = 0; i < nb; i++) {
                    double ang = t * 0.05 + i * Math.PI * 2 / nb;
                    double r = 2.6 + 0.4 * Math.sin(t * 0.07 + i);
                    Fx.butterfly(buf, ps, ownerP.add(Math.cos(ang) * r, 1.2 + 0.9 * Math.sin(t * 0.06 + i * 1.7), Math.sin(ang) * r), 0.42f, t + i * 3f, WHITE, 0.9f * spread * wingFade);
                }
            }
        }
        // ---- the marked circle
        if (t < GoddessEntity.T_IMPACT + 2) {
            float s = c01((t - GoddessEntity.T_RISE + 20f) / 20f);
            if (s > 0f) {
                float pulse = 0.75f + 0.25f * Mth.sin(t * 0.5f);
                Draw.disc(add(buf, TARGET), ps, R_IMPACT, t * 2f, y + 0.07f, CRIMSON, 0.85f * s * pulse);
                Draw.disc(add(buf, RUNE), ps, R_IMPACT * 0.7f, -t * 2.5f, y + 0.08f, GOLD, 0.7f * s);
                Draw.disc(add(buf, CIRCLE), ps, R_IMPACT * 0.3f, t * 5f, y + 0.09f, SCARLET, 0.7f * s);
                Draw.disc(trans(buf, MIST), ps, R_IMPACT, 0f, y + 0.05f, ROTC, 0.3f * s);
            }
        }
        // ---- impact
        if (t >= GoddessEntity.T_IMPACT && t < GoddessEntity.T_IMPACT + 18) {
            float u = c01((t - GoddessEntity.T_IMPACT) / 18f);
            Draw.disc(add(buf, RING_WAVE), ps, 1f + R_IMPACT * u, 0f, y + 0.1f, CRIMSON, 1f - u);
            Draw.disc(add(buf, GLOW), ps, R_IMPACT, 0f, y + 0.06f, SCARLET, 0.7f * (1f - u));
            Fx.star(buf, ps, p.add(0, 2.0, 0), 8f * (1f - u), 0f, WHITE, 1f - u);
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            Draw.cross(add(buf, LINE), ps, 1.6f * (1f - u * 0.5f), 0f, 30f, t * 3f, SCARLET, 1f - u, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
        // ---- the flower
        float open = 0f;
        float flowerAlpha = 0.95f;
        if (t >= GoddessEntity.T_IMPACT) {
            if (t < GoddessEntity.T_JUDG) {
                open = smooth((t - GoddessEntity.T_IMPACT) / (GoddessEntity.T_BLOOM - GoddessEntity.T_IMPACT));
            } else if (t < GoddessEntity.T_FINAL) {
                open = 1f - 0.85f * smooth((t - GoddessEntity.T_JUDG - 4f) / (GoddessEntity.T_FINAL - GoddessEntity.T_JUDG - 4f));
            } else {
                open = 0.15f;
                flowerAlpha = 0.95f * (1f - c01((t - GoddessEntity.T_FINAL) / 8f));
            }
            Fx.flower(buf, ps, p, R_FLOWER, open, t * 0.3f, 28, WHITE, flowerAlpha);
            Fx.flower(buf, ps, p, R_FLOWER * 0.55f, open, -t * 0.5f, 18, GOLD, flowerAlpha * 0.5f);
        }
        // ---- the battlefield on the ground
        if (t >= GoddessEntity.T_IMPACT && t < GoddessEntity.T_FINAL + 120) {
            float g = c01((t - GoddessEntity.T_IMPACT) / 12f) * (1f - c01((t - GoddessEntity.T_FINAL - 90f) / 30f));
            Draw.disc(trans(buf, MIST), ps, R_FLOWER, 0f, y + 0.06f, ROTC, 0.3f * g);
            Draw.disc(add(buf, WAVE), ps, R_FLOWER, t * 0.4f, y + 0.07f, DARKRED, 0.35f * g);
            Draw.disc(add(buf, RING_WAVE), ps, R_FLOWER, 0f, y + 0.09f, CRIMSON, 0.5f * g);
            Draw.band(trans(buf, ROT_WAVE), ps, R_FLOWER, 0f, 1.4f, 64, 12f, t * 0.01f, ROTC, 0.5f * g, 0f);
        }
        // ---- bloom explosion
        if (t >= GoddessEntity.T_BLOOM && t < GoddessEntity.T_BLOOM + 18) {
            float u = c01((t - GoddessEntity.T_BLOOM) / 18f);
            Draw.disc(add(buf, RING_WAVE), ps, 1f + R_FLOWER * u, 0f, y + 0.12f, CRIMSON, 1f - u);
            Draw.disc(add(buf, WAVE), ps, 1f + R_FLOWER * u, 0f, y + 0.11f, SCARLET, 0.7f * (1f - u));
            Fx.star(buf, ps, p.add(0, 2.0, 0), 10f * (1f - u), 0f, WHITE, 1f - u);
        }
        // ---- eight blades
        for (int k = 0; k < GoddessEntity.SWORDS; k++) {
            int slam = GoddessEntity.T_SWORD0 + k * GoddessEntity.SWORD_GAP;
            float s = t - slam;
            if (s < -10f || s > 28f) {
                continue;
            }
            Vec3 q = GoddessEntity.swordPos(e.getSeed(), k, e.getPoint(), R_FLOWER).subtract(base);
            if (s < 0f) {
                float u = c01((s + 10f) / 10f);
                double h = 26.0 * (1.0 - u * u);
                Vec3 tip = q.add(0, h, 0);
                Draw.disc(add(buf, TARGET), ps, R_SWORD * 0.8f * u, t * 4f, (float) q.y + 0.08f, CRIMSON, 0.9f * u);
                Fx.sword(buf, ps, tip.add(0, 18, 0), tip, cam, 1.0f, WHITE, 0.95f * u);
                Fx.glow(buf, ps, tip, 1.6f, GOLD, 0.6f * u);
            } else {
                float f = 1f - c01((s - 12f) / 16f);
                Fx.sword(buf, ps, q.add(0, 16, 0), q, cam, 1.0f, WHITE, 0.95f * f);
                float u = c01(s / 10f);
                Draw.disc(add(buf, RING_WAVE), ps, 0.6f + R_SWORD * u, 0f, (float) q.y + 0.12f, CRIMSON, 1f - u);
                Fx.star(buf, ps, q.add(0, 1.0, 0), 4f * (1f - u), 0f, GOLD, 1f - u);
            }
        }
        // ---- the judgment wave
        if (t >= GoddessEntity.T_JUDG && t < GoddessEntity.T_JUDG + 18) {
            float u = c01((t - GoddessEntity.T_JUDG) / 18f);
            float r = 1f + R_JUDG * smooth(u);
            Draw.disc(add(buf, RING_WAVE), ps, r, 0f, y + 0.13f, GOLD, 1f - u);
            Fx.crescentRing(buf, ps, p, r, 5.0f, 16, u * 30f, GOLD, 0.9f * (1f - u));
            Fx.crescentRing(buf, ps, p, r * 0.75f, 3.5f, 12, -u * 40f, SILVER, 0.8f * (1f - u));
            Fx.star(buf, ps, p.add(0, 2.0, 0), 9f * (1f - u), 0f, WHITE, 1f - u);
        }
        // ---- the collapsing sphere
        if (t > GoddessEntity.T_JUDG + 6 && t < GoddessEntity.T_FINAL + 6) {
            float u = c01((t - GoddessEntity.T_JUDG - 6f) / (GoddessEntity.T_FINAL - GoddessEntity.T_JUDG - 6f));
            float r = u < 0.7f ? 6f * smooth(u / 0.7f) : 6f - 2.5f * smooth((u - 0.7f) / 0.3f);
            Vec3 core = p.add(0, 3.5, 0);
            float vis = t < GoddessEntity.T_FINAL ? 1f : 1f - c01((t - GoddessEntity.T_FINAL) / 6f);
            ps.pushPose();
            ps.translate(core.x, core.y, core.z);
            Draw.sphere(add(buf, SPHERE), ps, r, 12, 18, t * 0.02f, WHITE, 0.95f * vis);
            Draw.sphere(add(buf, SPHERE), ps, r * 1.25f, 10, 14, -t * 0.015f, SCARLET, 0.4f * vis);
            ps.popPose();
            Fx.glow(buf, ps, core, r * 2.8f, CRIMSON, 0.7f * vis);
            Fx.glow(buf, ps, core, r * 1.4f, WHITE, 0.6f * vis * u);
        }
        // ---- final detonation
        if (t >= GoddessEntity.T_FINAL && t < GoddessEntity.T_FINAL + 22) {
            float u = c01((t - GoddessEntity.T_FINAL) / 22f);
            float r = 1f + R_FLOWER * smooth(u);
            Draw.disc(add(buf, RING_WAVE), ps, r, 0f, y + 0.14f, WHITE, 1f - u);
            Draw.disc(add(buf, WAVE), ps, r, 0f, y + 0.13f, CRIMSON, 0.8f * (1f - u));
            Fx.star(buf, ps, p.add(0, 3.5, 0), 16f * (1f - u), t * 2f, WHITE, 1f - u);
            Fx.glow(buf, ps, p.add(0, 3.5, 0), 14f * (1f - u * 0.5f), SCARLET, 0.8f * (1f - u));
            Fx.crescentRing(buf, ps, p, r, 6.0f, 20, u * 20f, CRIMSON, 0.7f * (1f - u));
        }
    }
}
