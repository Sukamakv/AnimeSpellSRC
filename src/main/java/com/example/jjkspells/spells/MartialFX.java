package com.example.jjkspells.spells;

import com.example.jjkspells.ModParticles;
import com.example.jjkspells.effect.OverloadEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers shared by the martial arts spells (Barrage of Punches, Ground Smash, Hard Strike):
 * finding who is in front of the caster, knocking things back, and the hit / shock visuals.
 */
public final class MartialFX {
    private MartialFX() {}

    // ---- geometry -------------------------------------------------------------------------------

    /** Unit vector pointing to the caster's right (horizontal). */
    public static Vec3 rightOf(LivingEntity e) {
        float yaw = e.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
    }

    /** A random unit vector. */
    public static Vec3 randomDir(RandomSource rng) {
        double z = rng.nextDouble() * 2 - 1;
        double t = rng.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(Math.max(0, 1 - z * z));
        return new Vec3(r * Math.cos(t), z, r * Math.sin(t));
    }

    /**
     * Everything alive that stands inside a straight "tube" in front of the caster: from {@code origin} along the
     * unit vector {@code dir} for {@code reach} blocks, {@code halfWidth} blocks to each side. Skips the caster,
     * their allies, spectators and anything behind a wall.
     */
    public static List<LivingEntity> inFront(ServerLevel level, LivingEntity caster, Vec3 origin, Vec3 dir,
                                             double reach, double halfWidth) {
        Vec3 end = origin.add(dir.scale(reach));
        AABB box = new AABB(origin, end).inflate(halfWidth + 0.75);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (e == caster || e.isAlliedTo(caster)) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            Vec3 v = c.subtract(origin);
            double along = v.dot(dir);
            double r = e.getBbWidth() * 0.5;
            if (along < -r || along > reach + r) continue;
            double perp = v.subtract(dir.scale(along)).length();
            if (perp > halfWidth + r + e.getBbHeight() * 0.25) continue;
            if (!caster.hasLineOfSight(e)) continue;
            out.add(e);
        }
        return out;
    }

    // ---- knockback ------------------------------------------------------------------------------

    /**
     * Throws {@code target} along the horizontal direction (dx, dz) at {@code strength} blocks per tick, plus a hop of
     * {@code lift}. Respects knockback resistance, and works on Overloaded targets too (an outside force).
     */
    public static void shove(LivingEntity target, double dx, double dz, double strength, double lift) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            dx = 0;
            dz = 1;
            len = 1;
        }
        double res = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        double k = strength * (1.0 - res);
        target.setDeltaMovement(dx / len * k, lift * (1.0 - res * 0.5), dz / len * k);
        target.hurtMarked = true;
        OverloadEvents.forceMove(target);
    }

    // ---- sounds ---------------------------------------------------------------------------------

    public static void sound(ServerLevel level, Vec3 p, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, p.x, p.y, p.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ---- visuals --------------------------------------------------------------------------------

    /** A fist-shaped flash (the sprite is a clenched fist). {@code size} is its radius in blocks. */
    public static void fist(ServerLevel level, Vec3 p, float size) {
        FX.one(level, ModParticles.MA_FIST.get(), p, new Vec3(size, 0, 0));
    }

    /** A thin streak from {@code from} to {@code to}. */
    public static void streak(ServerLevel level, Vec3 from, Vec3 to) {
        FX.one(level, ModParticles.MA_LINE.get(), from, to.subtract(from));
    }

    /** A thick streak from {@code from} to {@code to}. */
    public static void heavyStreak(ServerLevel level, Vec3 from, Vec3 to) {
        FX.one(level, ModParticles.MA_LINE_HEAVY.get(), from, to.subtract(from));
    }

    /** Impact flash: glow + starburst + sparks + short radial lines. {@code size} is the burst radius in blocks. */
    public static void burst(ServerLevel level, Vec3 p, float size) {
        RandomSource rng = level.random;
        FX.one(level, ModParticles.MA_GLOW.get(), p, new Vec3(size * 1.2, 0, 0));
        FX.one(level, ModParticles.MA_IMPACT.get(), p, new Vec3(size, 0, 0));
        int sparks = 3 + (int) (size * 4);
        for (int i = 0; i < sparks; i++) {
            FX.one(level, ModParticles.MA_SPARK.get(), p, randomDir(rng).scale(0.15 + rng.nextDouble() * 0.22 * (0.6 + size)));
        }
        int lines = 2 + (int) (size * 2.5);
        for (int i = 0; i < lines; i++) {
            Vec3 d = randomDir(rng);
            FX.one(level, ModParticles.MA_LINE.get(), p.add(d.scale(size * 0.45)), d.scale(size * (0.9 + rng.nextDouble() * 0.9)));
        }
    }

    /** A big, heavy impact: everything in {@link #burst} plus a shock ring, thick streaks and a puff of dust. */
    public static void heavyBurst(ServerLevel level, Vec3 p, float size) {
        RandomSource rng = level.random;
        burst(level, p, size);
        FX.one(level, ModParticles.MA_SHOCK_RING.get(), p, new Vec3(size * 1.9, 0, 0));
        for (int i = 0; i < 5; i++) {
            Vec3 d = randomDir(rng);
            FX.one(level, ModParticles.MA_LINE_HEAVY.get(), p.add(d.scale(size * 0.35)), d.scale(size * (1.3 + rng.nextDouble() * 0.9)));
        }
        for (int i = 0; i < 3; i++) {
            Vec3 d = randomDir(rng);
            FX.one(level, ModParticles.MA_DUST.get(), p, new Vec3(d.x * 0.08, 0.02 + Math.abs(d.y) * 0.05, d.z * 0.08));
        }
    }
}
