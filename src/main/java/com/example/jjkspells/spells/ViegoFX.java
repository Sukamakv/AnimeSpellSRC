package com.example.jjkspells.spells;

import com.example.jjkspells.ModParticles;
import com.example.jjkspells.particle.MoonRibbonOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Helpers shared by Viego's spells: spectral green and Black Mist visuals, teleporting, percent-health damage. */
public final class ViegoFX {
    private ViegoFX() {}

    /** Flat damage plus a percentage of the target's maximum health. */
    public static float flatPlusPercent(float flat, LivingEntity target, float percent) {
        return flat + target.getMaxHealth() * percent;
    }

    /**
     * One Viego blow: {@code flat} damage through the normal damage path, then {@code percent} (a fraction, 0.06 = 6%)
     * of the target's max health through SpellDamage.percent, which ignores damage caps.
     * @return false if the flat part was blocked (immune / cancelled); the percent part is then skipped too.
     */
    public static boolean strike(LivingEntity caster, io.redspace.ironsspellbooks.api.spells.AbstractSpell spell, LivingEntity target, float flat, float percent) {
        net.minecraft.world.damagesource.DamageSource src = spell.getDamageSource(caster);
        target.invulnerableTime = 0;
        if (!io.redspace.ironsspellbooks.damage.DamageSources.applyDamage(target, flat, src)) return false;
        if (target.isAlive() && percent > 0f) {
            SpellDamage.percent(target, target.getMaxHealth() * percent, caster, src);
        }
        return true;
    }

    /** True if the entity has Weakness II or stronger. */
    public static boolean hasWeaknessII(LivingEntity e) {
        MobEffectInstance w = e.getEffect(MobEffects.WEAKNESS);
        return w != null && w.getAmplifier() >= 1;
    }

    /** Moves an entity instantly. Players need the connection told, or they rubber-band back. */
    public static void teleport(LivingEntity e, Vec3 p) {
        if (e instanceof ServerPlayer sp) {
            sp.connection.teleport(p.x, p.y, p.z, sp.getYRot(), sp.getXRot());
        } else {
            e.moveTo(p.x, p.y, p.z, e.getYRot(), e.getXRot());
        }
        e.setDeltaMovement(Vec3.ZERO);
        e.fallDistance = 0;
    }

    // ---- visuals ------------------------------------------------------------------------------

    /** A spectral green slash ribbon (palette GREEN) or Black Mist one (palette DARK_GREEN). */
    public static void ribbon(ServerLevel level, Vec3 c, Vec3 u, Vec3 v, float r0, float r1, float lift,
                              float th0, float th1, float width, int life, int palette) {
        DariusFX.ribbon(level, c, u, v, r0, r1, lift, th0, th1, width, life, palette);
    }

    /** Spectral ring along the ground with mist rolling outward. */
    public static void ring(ServerLevel level, Vec3 ground, float radius) {
        RandomSource rng = level.random;
        FX.one(level, ModParticles.VG_RING_FLAT.get(), ground.add(0, 0.07, 0), new Vec3(radius, 0, 0));
        int puffs = Mth.clamp((int) (radius * 4), 8, 26);
        double phase = rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < puffs; i++) {
            double a = phase + i * Math.PI * 2 / puffs;
            double sp = radius * 0.06;
            FX.one(level, ModParticles.VG_MIST.get(), ground.add(Math.cos(a) * 0.5, 0.2, Math.sin(a) * 0.5),
                    new Vec3(Math.cos(a) * sp, 0.02, Math.sin(a) * sp));
            if ((i & 1) == 0) {
                FX.one(level, ModParticles.VG_SPARK.get(), ground.add(Math.cos(a) * 0.5, 0.2, Math.sin(a) * 0.5),
                        new Vec3(Math.cos(a) * sp * 1.5, 0.08 + rng.nextDouble() * 0.08, Math.sin(a) * sp * 1.5));
            }
        }
    }

    /** A green impact: flash, starburst, sparks and spectral shards. */
    public static void burst(ServerLevel level, Vec3 p, float size) {
        RandomSource rng = level.random;
        FX.one(level, ModParticles.VG_GLOW.get(), p, new Vec3(size * 1.2, 0, 0));
        FX.one(level, ModParticles.VG_HIT.get(), p, new Vec3(size, 0, 0));
        int sparks = 4 + (int) (size * 4);
        for (int i = 0; i < sparks; i++) {
            FX.one(level, ModParticles.VG_SPARK.get(), p, MartialFX.randomDir(rng).scale(0.15 + rng.nextDouble() * 0.25));
        }
        int shards = 2 + (int) (size * 2);
        for (int i = 0; i < shards; i++) {
            FX.one(level, ModParticles.VG_SHARD.get(), p, MartialFX.randomDir(rng).scale(0.2 + rng.nextDouble() * 0.3));
        }
    }

    /** Green energy streaming in toward {@code target} from all around. */
    public static void gather(ServerLevel level, Vec3 target, double radius, int count) {
        RandomSource rng = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 d = MartialFX.randomDir(rng);
            FX.one(level, ModParticles.VG_GATHER.get(), target.add(d.scale(radius)), d.scale(-radius * 0.13));
        }
    }

    /** Black Mist puffs around {@code pos}. */
    public static void mist(ServerLevel level, Vec3 pos, int count, double spread) {
        RandomSource rng = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 off = new Vec3((rng.nextDouble() - 0.5) * 2 * spread, (rng.nextDouble() - 0.3) * spread, (rng.nextDouble() - 0.5) * 2 * spread);
            FX.one(level, ModParticles.VG_MIST.get(), pos.add(off), new Vec3(off.x * 0.02, 0.01 + rng.nextDouble() * 0.02, off.z * 0.02));
        }
    }

    /** Spectral green flames rising from {@code pos}. */
    public static void flames(ServerLevel level, Vec3 pos, int count, double spread) {
        RandomSource rng = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 off = new Vec3((rng.nextDouble() - 0.5) * 2 * spread, rng.nextDouble() * spread, (rng.nextDouble() - 0.5) * 2 * spread);
            FX.one(level, ModParticles.VG_FLAME.get(), pos.add(off), new Vec3((rng.nextDouble() - 0.5) * 0.02, 0.04 + rng.nextDouble() * 0.04, (rng.nextDouble() - 0.5) * 0.02));
        }
    }

    /** Black Mist tendrils spiralling up around something standing at {@code base}. */
    public static void tendrils(ServerLevel level, Vec3 base, float radius, float height, int count) {
        RandomSource rng = level.random;
        for (int i = 0; i < count; i++) {
            double phi = i * Math.PI * 2 / count + rng.nextDouble() * 0.5;
            Vec3 u = new Vec3(Math.cos(phi), 0, Math.sin(phi));
            Vec3 v = new Vec3(-Math.sin(phi), 0, Math.cos(phi));
            // u x v points down, so a negative lift climbs
            ribbon(level, base, u, v, radius, radius * 0.55f, -height, 0f, (float) (Math.PI * (1.6 + rng.nextDouble() * 0.6)),
                    0.28f + rng.nextFloat() * 0.12f, 14, (i & 1) == 0 ? MoonRibbonOptions.DARK_GREEN : MoonRibbonOptions.GREEN);
        }
    }
}
