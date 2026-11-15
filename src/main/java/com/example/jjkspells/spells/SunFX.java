package com.example.jjkspells.spells;

import com.example.jjkspells.JJKConfig;
import com.example.jjkspells.ModParticles;
import com.example.jjkspells.particle.MoonRibbonOptions;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Helpers shared by the Sun Breathing forms: damage from the held weapon, dashing, and the flame visuals. */
public final class SunFX {
    private SunFX() {}

    public static final int SUN = MoonRibbonOptions.SUN;
    public static final int CRIMSON = MoonRibbonOptions.CRIMSON;

    // ---- damage ---------------------------------------------------------------------------------

    public static float levelMult(int spellLevel) {
        return 1f + 0.15f * (spellLevel - 1);
    }

    /** Damage of {@code mult} times the caster's held weapon, scaled by the config. */
    public static float damage(LivingEntity caster, float mult) {
        return MoonFX.weaponDamage(caster) * mult * JJKConfig.SUN_DAMAGE_SCALE.get().floatValue();
    }

    /** Hits {@code e} for {@code mult} x weapon damage, sets it alight and plays the flame hit. @return true if it hurt. */
    public static boolean hit(ServerLevel level, AbstractSpell spell, LivingEntity caster, LivingEntity e, float mult) {
        e.invulnerableTime = 0;
        boolean ok = DamageSources.applyDamage(e, damage(caster, mult), spell.getDamageSource(caster));
        if (ok) {
            SpellDamage.wound(e);
            if (JJKConfig.SUN_SETS_FIRE.get()) e.setSecondsOnFire(4);
            Vec3 c = e.getBoundingBox().getCenter();
            FX.one(level, ModParticles.SUN_FLARE.get(), c, new Vec3(0.9 + mult * 0.15, 0, 0));
            FX.one(level, ModParticles.SUN_GLOW.get(), c, new Vec3(1.2, 0, 0));
            RandomSource rng = level.random;
            for (int i = 0; i < 4; i++) {
                FX.one(level, ModParticles.SUN_SPARK.get(), c, MartialFX.randomDir(rng).scale(0.15 + rng.nextDouble() * 0.25));
            }
        }
        return ok;
    }

    /** Everything hostile and alive within {@code radius} of {@code centre}. */
    public static List<LivingEntity> enemies(ServerLevel level, LivingEntity caster, Vec3 centre, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(centre, centre).inflate(radius + 1.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster)) continue;
            if (e instanceof Player p && p.getAbilities().invulnerable) continue;
            double r = radius + e.getBbWidth() * 0.5;
            if (e.getBoundingBox().getCenter().distanceToSqr(centre) > r * r) continue;
            out.add(e);
        }
        return out;
    }

    // ---- movement -------------------------------------------------------------------------------

    /** How far along {@code dir} the caster can get (up to {@code max}) before running into something. */
    public static Vec3 dashPath(ServerLevel level, LivingEntity c, Vec3 dir, double max) {
        Vec3 start = c.position();
        Vec3 last = start;
        AABB base = c.getBoundingBox();
        for (double d = 0.5; d <= max + 1.0E-6; d += 0.5) {
            Vec3 cand = start.add(dir.scale(d));
            Vec3 use = null;
            for (double dy : new double[]{0, 1}) {
                Vec3 p = cand.add(0, dy, 0);
                if (level.noCollision(c, base.move(p.subtract(start)))) {
                    use = p;
                    break;
                }
            }
            if (use == null) break;
            last = use;
        }
        return last;
    }

    /** Puts the caster at {@code p}, optionally turning them. Works on players (the client is told). */
    public static void place(LivingEntity c, Vec3 p, float yaw, float pitch, boolean turn) {
        float y = turn ? yaw : c.getYRot();
        float x = turn ? pitch : c.getXRot();
        if (c instanceof ServerPlayer sp) {
            sp.connection.teleport(p.x, p.y, p.z, y, x);
        } else {
            c.moveTo(p.x, p.y, p.z, y, x);
        }
        c.fallDistance = 0;
    }

    public static float yawOf(Vec3 d) {
        return (float) (Mth.atan2(-d.x, d.z) * 57.2957795);
    }

    public static float pitchOf(Vec3 d) {
        return (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 57.2957795);
    }

    // ---- visuals --------------------------------------------------------------------------------

    public static void ribbon(ServerLevel level, int palette, Vec3 c, Vec3 u, Vec3 v, float r0, float r1, float lift,
                              float th0, float th1, float width, int life) {
        FX.one(level, MoonRibbonOptions.flame(palette, u, v, r0, r1, lift, th0, th1, width, life), c, Vec3.ZERO);
    }

    /** A fiery silhouette of the swordsman that fades in a few ticks. */
    public static void afterimage(ServerLevel level, Vec3 feet) {
        FX.one(level, ModParticles.SUN_FIGURE.get(), feet.add(0, 0.95, 0), new Vec3(1.15, 0, 0));
    }

    public static void sparks(ServerLevel level, Vec3 p, int n, double speed, double up) {
        RandomSource rng = level.random;
        for (int i = 0; i < n; i++) {
            Vec3 d = MartialFX.randomDir(rng);
            FX.one(level, ModParticles.SUN_SPARK.get(), p, new Vec3(d.x * speed, Math.abs(d.y) * speed + up, d.z * speed));
        }
    }

    public static void embers(ServerLevel level, Vec3 p, int n, double spread, double rise) {
        RandomSource rng = level.random;
        for (int i = 0; i < n; i++) {
            Vec3 q = p.add((rng.nextDouble() - 0.5) * spread, (rng.nextDouble() - 0.5) * spread * 0.5, (rng.nextDouble() - 0.5) * spread);
            FX.one(level, ModParticles.SUN_EMBER.get(), q, new Vec3((rng.nextDouble() - 0.5) * 0.04, rise * (0.5 + rng.nextDouble()), (rng.nextDouble() - 0.5) * 0.04));
        }
    }

    /** Burst of sunlight: flash, glint, rings, sparks. */
    public static void burst(ServerLevel level, Vec3 p, float size) {
        FX.one(level, ModParticles.SUN_GLOW.get(), p, new Vec3(size * 0.8, 0, 0));
        FX.one(level, ModParticles.SUN_FLARE.get(), p, new Vec3(size * 0.6, 0, 0));
        FX.one(level, ModParticles.SUN_RING.get(), p, new Vec3(size, 0, 0));
        FX.one(level, ModParticles.SUN_RING_FLAT.get(), new Vec3(p.x, p.y + 0.08, p.z), new Vec3(size * 1.1, 0, 0));
        sparks(level, p, (int) (10 + size * 4), 0.25 + size * 0.04, 0.1);
    }

    public static Vec3 rightOfDir(Vec3 f) {
        Vec3 r = new Vec3(-f.z, 0, f.x);
        return r.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : r.normalize();
    }

    public static Vec3 flat(Vec3 d) {
        Vec3 f = new Vec3(d.x, 0, d.z);
        return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    /** Where the sword's tip is, roughly: ahead, to the right, a little below the eyes. */
    public static Vec3 tipOf(LivingEntity c, Vec3 f) {
        return c.getEyePosition().add(f.scale(1.5)).add(rightOfDir(f).scale(0.35)).add(0, -0.3, 0);
    }
}
