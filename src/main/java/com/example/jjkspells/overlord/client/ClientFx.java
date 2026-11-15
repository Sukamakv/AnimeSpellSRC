package com.example.jjkspells.overlord.client;

import com.example.jjkspells.overlord.config.OverlordConfig;
import com.example.jjkspells.overlord.entity.OverlordFxEntity;
import com.example.jjkspells.overlord.registry.ModParticles;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.render.animation.AnimationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Client-side state (screen darkness / flash / silence / input lock) and the per-tick particles of the controller entity. */
public final class ClientFx {
    // smoothed values read by the overlay; "want" values are re-pushed every tick by active entities
    public static float darkness, flash, silence;
    public static float flashR = 1f, flashG = 1f, flashB = 1f;
    private static float wantDarkness, wantSilence;
    private static boolean wantLock;
    public static boolean locked;

    private ClientFx() {
    }

    public static void clientTick() {
        darkness += (wantDarkness - darkness) * 0.12f;
        silence += (wantSilence - silence) * 0.2f;
        flash *= 0.9f;
        if (flash < 0.01f) {
            flash = 0f;
        }
        locked = wantLock;
        wantDarkness = 0f;
        wantSilence = 0f;
        wantLock = false;
    }

    private static void pushDark(float d, float s) {
        wantDarkness = Math.max(wantDarkness, d);
        wantSilence = Math.max(wantSilence, s);
    }

    public static void flash(float a, float r, float g, float b) {
        flash = Math.max(flash, a);
        flashR = r;
        flashG = g;
        flashB = b;
    }

    private static float proximity(OverlordFxEntity e, double reach) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return 0f;
        }
        double d = Math.sqrt(p.distanceToSqr(e.getX(), p.getY(), e.getZ()));
        return (float) Mth.clamp(1.0 - d / reach, 0.0, 1.0);
    }

    private static void add(Level l, ParticleOptions p, double x, double y, double z, double vx, double vy, double vz) {
        l.addParticle(p, x, y, z, vx, vy, vz);
    }

    /** Called on the logical client from OverlordFxEntity.tick(). */
    public static void tick(OverlordFxEntity e) {
        Level l = e.level();
        int t = e.tickCount;
        var rnd = l.getRandom();
        Entity owner = e.getOwnerEntity();
        double x = e.getX(), y = e.getY(), z = e.getZ();

        // stand-still ritual: input lock for the local caster + the charging pose for everyone watching
        if (e.getLock() > 0 && owner != null) {
            if (e.isLocking() && owner == Minecraft.getInstance().player) {
                wantLock = true;
            }
            if (owner instanceof AbstractClientPlayer ap) {
                if (t == 8) {
                    var holder = switch (e.getKind()) {
                        case OverlordFxEntity.GOAL -> SpellAnimations.ANIMATION_CONTINUOUS_OVERHEAD;
                        case OverlordFxEntity.FALLEN -> SpellAnimations.CHARGE_RAISED_HAND;
                        default -> SpellAnimations.CAST_KNEELING_PRAYER;
                    };
                    holder.getForPlayer().ifPresent(id -> AnimationHelper.animatePlayerStart(ap, id));
                }
                if (t == e.getLock()) {
                    AnimationHelper.cancelPlayerAnimation(ap);
                }
            }
        }

        switch (e.getKind()) {
            case OverlordFxEntity.SUMMON -> {
                if (t < 40 && t % 2 == 0) {
                    double r = e.getRadius() * 1.2;
                    double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * r;
                    add(l, ModParticles.RUNE.get(), x + Math.cos(a) * d, y + 0.2, z + Math.sin(a) * d, 0, 0.03, 0);
                    add(l, ModParticles.SMOKE.get(), x + Math.cos(a) * d, y + 0.1, z + Math.sin(a) * d, 0, 0.02, 0);
                }
            }
            case OverlordFxEntity.BANSHEE -> {
                float k = proximity(e, e.getRadius() * 2.0 + 6);
                float env = t < 6 ? t / 6f : Math.max(0f, 1f - (t - 6) / 28f);
                pushDark(0.55f * env * Math.max(0.35f, k), 0f);
                if (t >= 10 && t < 26) {
                    double R = e.getRadius() * Math.min(1.0, (t - 9) / 14.0);
                    for (int i = 0; i < 14; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2;
                        add(l, rnd.nextBoolean() ? ModParticles.SOUL.get() : ModParticles.VOID.get(), x + Math.cos(a) * R, y + 0.3 + rnd.nextDouble() * 2, z + Math.sin(a) * R, Math.cos(a) * 0.1, 0.02, Math.sin(a) * 0.1);
                    }
                }
            }
            case OverlordFxEntity.HOLE -> {
                int collapse = e.getMark();
                float size = Math.min(1f, t / 8f);
                if (t > 8 && t < collapse) {
                    double R = OverlordConfig.BLACK_HOLE.pullRadius.get();
                    for (int i = 0; i < 6; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2 + t * 0.2;
                        double d = 1.5 + rnd.nextDouble() * (R - 1.5);
                        Vec3 from = new Vec3(Math.cos(a) * d, (rnd.nextDouble() - 0.5) * d * 0.5, Math.sin(a) * d);
                        Vec3 tang = new Vec3(-from.z, 0, from.x).normalize().scale(0.18);
                        Vec3 in = from.normalize().scale(-0.16 * (1.2 - d / R));
                        add(l, ModParticles.VOID.get(), x + from.x, y + from.y, z + from.z, in.x + tang.x, in.y, in.z + tang.z);
                    }
                    if (t % 3 == 0) {
                        double a = rnd.nextDouble() * Math.PI * 2, d = 2 + rnd.nextDouble() * 4;
                        add(l, ModParticles.RUNE.get(), x + Math.cos(a) * d, y + rnd.nextDouble() * 2 - 1, z + Math.sin(a) * d, -Math.cos(a) * 0.1, 0, -Math.sin(a) * 0.1);
                    }
                }
                if (t == collapse) {
                    flash(0.35f * proximity(e, 24), 0.75f, 0.4f, 1f);
                    for (int i = 0; i < 40; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2;
                        add(l, ModParticles.VOID.get(), x, y, z, Math.cos(a) * 0.5, (rnd.nextDouble() - 0.5) * 0.3, Math.sin(a) * 0.5);
                    }
                }
            }
            case OverlordFxEntity.GOAL -> {
                int charge = e.getMark();
                float ramp = Math.min(1f, t / 40f) * (t < charge + 20 ? 1f : Math.max(0f, 1f - (t - charge - 20) / 40f));
                float k = Math.max(0.55f, proximity(e, e.getRadius() * 2.5));
                pushDark(0.72f * ramp * k, ramp);
                if (t < charge && t % 2 == 0) {
                    double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * e.getRadius();
                    add(l, ModParticles.RUNE.get(), x + Math.cos(a) * d, y + 0.1, z + Math.sin(a) * d, 0, 0.05, 0);
                    add(l, ModParticles.SOUL.get(), x + Math.cos(a) * d, y + 0.1, z + Math.sin(a) * d, 0, 0.08, 0);
                    add(l, ModParticles.BONE.get(), x + Math.cos(a) * d, y + 0.1, z + Math.sin(a) * d, 0, 0.2, 0);
                }
                if (t > 0 && t < charge && t % 20 == 0) {
                    flash(0.18f * k, 0.1f, 0f, 0.1f);
                }
                if (t >= charge && t < charge + 22) {
                    double R = e.getRadius() * Math.min(1.0, (t - charge + 1) / 20.0);
                    for (int i = 0; i < 26; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2;
                        add(l, ModParticles.SMOKE.get(), x + Math.cos(a) * R, y + 0.3 + rnd.nextDouble() * 3, z + Math.sin(a) * R, Math.cos(a) * 0.08, 0.02, Math.sin(a) * 0.08);
                    }
                    if (t == charge) {
                        flash(0.6f * k, 0.05f, 0f, 0.05f);
                    }
                }
            }
            case OverlordFxEntity.FALLEN -> {
                int charge = e.getMark();
                int impact = charge + 10;
                float lit = Math.min(1f, t / (float) charge);
                if (t < impact && t % 2 == 0) {
                    double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * e.getRadius();
                    add(l, ModParticles.GOLD.get(), x + Math.cos(a) * d, y + 20, z + Math.sin(a) * d, 0, -0.5 - 0.3 * lit, 0);
                    add(l, ModParticles.GOLD.get(), x + Math.cos(a) * d * 0.5, y + 16, z + Math.sin(a) * d * 0.5, 0, -0.4, 0);
                }
                if (t >= charge - 36 && t < impact) {
                    flash(0.10f * Math.max(0.3f, proximity(e, 60)) * ((t - charge + 36) / 36f), 1f, 0.9f, 0.6f);
                }
                if (t == impact) {
                    flash(1.0f * Math.max(0.35f, proximity(e, e.getRadius() * 2.2)), 1f, 0.95f, 0.8f);
                    for (int i = 0; i < 60; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, sp = 0.3 + rnd.nextDouble() * 0.9;
                        add(l, ModParticles.GOLD.get(), x, y + 1, z, Math.cos(a) * sp, rnd.nextDouble() * 0.4, Math.sin(a) * sp);
                    }
                }
                if (t > impact && t < impact + 60 && t % 3 == 0) {
                    double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * e.getRadius() * 0.55;
                    add(l, ModParticles.GOLD.get(), x + Math.cos(a) * d, y + 0.1, z + Math.sin(a) * d, 0, 0.04, 0);
                }
            }
            case OverlordFxEntity.SLASH -> {
                int w = e.getMark();
                if (t == w) {
                    flash(0.25f * proximity(e, 20), 0.75f, 0.35f, 1f);
                    Vec3 dir = e.getDir().normalize();
                    float len = e.getLength();
                    for (int i = 0; i < 40; i++) {
                        double f = rnd.nextDouble() * len;
                        add(l, ModParticles.VOID.get(), x + dir.x * f, y + dir.y * f, z + dir.z * f, rnd.nextGaussian() * 0.04, rnd.nextGaussian() * 0.04, rnd.nextGaussian() * 0.04);
                    }
                }
                if (t > w && t < w + 12) {
                    Vec3 dir = e.getDir().normalize();
                    double f = e.getLength() * Math.min(1.0, (t - w) / 12.0);
                    for (int i = 0; i < 4; i++) {
                        double g = f * (0.85 + rnd.nextDouble() * 0.15);
                        add(l, ModParticles.VOID.get(), x + dir.x * g, y + dir.y * g, z + dir.z * g, dir.x * 0.1, dir.y * 0.1, dir.z * 0.1);
                    }
                }
            }
            case OverlordFxEntity.GOAT -> {
                int charge = e.getMark();
                float ramp = Math.min(1f, t / 40f) * (t < charge + 30 ? 1f : Math.max(0f, 1f - (t - charge - 30) / 50f));
                float k = Math.max(0.5f, proximity(e, 36));
                pushDark(0.68f * ramp * k, ramp);
                if (t < charge + 20 && t % 2 == 0) {
                    double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * e.getRadius();
                    add(l, ModParticles.SMOKE.get(), x + Math.cos(a) * d, y + 0.1, z + Math.sin(a) * d, 0, 0.05, 0);
                    add(l, ModParticles.RUNE.get(), x + Math.cos(a) * d, y + 0.1, z + Math.sin(a) * d, 0, 0.05, 0);
                }
                if (t == Math.max(1, charge - 30)) {
                    flash(0.3f * k, 0.3f, 0f, 0.05f);
                }
            }
            default -> {
                Entity tgt = e.getTargetEntity();
                if (tgt != null && t < 26 && t % 2 == 0) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    double sx = tgt.getX() + Math.cos(a) * 1.5, sz = tgt.getZ() + Math.sin(a) * 1.5, sy = tgt.getY() + tgt.getBbHeight() * 0.65;
                    add(l, ModParticles.SOUL.get(), sx, sy, sz, (tgt.getX() - sx) * 0.08, 0, (tgt.getZ() - sz) * 0.08);
                }
            }
        }
    }
}
