package com.example.jjkspells.garou.util;

import com.example.jjkspells.garou.config.GarConfig;
import com.example.jjkspells.garou.registry.ModParticles;
import com.example.jjkspells.garou.registry.ModSpells;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.ArrayList;
import java.util.List;

/** Server helpers for the Garou spells: targeting, damage, knockback, particles and sounds. */
public final class GarUtil {
    private GarUtil() {
    }

    public static boolean isEnemy(Entity caster, Entity e) {
        if (!(e instanceof LivingEntity le) || e == caster || !le.isAlive() || e.isSpectator()) {
            return false;
        }
        if (e instanceof Player p && (p.isCreative() || !GarConfig.G.hitPlayers.get())) {
            return false;
        }
        return !DamageSources.isFriendlyFireBetween(caster, e);
    }

    public static boolean isBoss(LivingEntity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES);
    }

    public static DamageSource src(LivingEntity caster) {
        return ModSpells.GAMMA_RAY_BURST.get().getDamageSource(caster, caster);
    }

    /**
     * flat + pct% of the target's max health (times the config multiplier). The flat part is a normal spell hit; the percent
     * part goes through SpellDamage.percent like every other spell of this mod. True if the target died.
     */
    public static boolean hurt(LivingEntity caster, LivingEntity target, double flat, double pct, double pctFactor) {
        if (target.level().isClientSide || !target.isAlive()) {
            return false;
        }
        double m = GarConfig.G.damageMultiplier.get();
        DamageSource src = src(caster);
        float flatAmt = (float) (flat * m);
        float pctAmt = (float) (pct / 100.0 * target.getMaxHealth() * m * pctFactor);
        target.invulnerableTime = 0;
        com.example.jjkspells.spells.SpellDamage.irradiate(target, 120);
        com.example.jjkspells.spells.Debuffs.hit(caster, target);
        if (flatAmt > 0 && !DamageSources.applyDamage(target, flatAmt, src)) {
            return !target.isAlive();
        }
        if (target.isAlive() && pctAmt > 0) {
            com.example.jjkspells.spells.SpellDamage.percent(target, pctAmt, caster, src);
        }
        return !target.isAlive();
    }

    /** Living enemies whose body touches the beam cylinder (origin -> origin + dir * len, radius r). */
    public static List<LivingEntity> inBeam(Level level, LivingEntity caster, Vec3 origin, Vec3 dir, double len, double r) {
        Vec3 end = origin.add(dir.scale(len));
        AABB box = new AABB(origin, end).inflate(r + 2.0);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> isEnemy(caster, x))) {
            Vec3 c = e.getBoundingBox().getCenter();
            double t = Mth.clamp(c.subtract(origin).dot(dir), 0.0, len);
            Vec3 p = origin.add(dir.scale(t));
            double reach = r + Math.max(e.getBbWidth(), e.getBbHeight()) * 0.5;
            if (c.distanceToSqr(p) <= reach * reach) {
                out.add(e);
            }
        }
        return out;
    }

    /** Living enemies inside a sphere. */
    public static List<LivingEntity> around(Level level, LivingEntity caster, Vec3 c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), x -> isEnemy(caster, x))) {
            if (e.getBoundingBox().getCenter().distanceToSqr(c) <= (r + e.getBbWidth()) * (r + e.getBbWidth())) {
                out.add(e);
            }
        }
        return out;
    }

    /** Pushes a target along a direction (bosses and heavy targets barely move, knockback resistance is respected). */
    public static void push(LivingEntity e, Vec3 dir, double strength, double up) {
        if (strength <= 0) {
            return;
        }
        double f = isBoss(e) ? 0.25 : 1.0;
        double k = strength * f * (1.0 - Mth.clamp(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1));
        Vec3 m = e.getDeltaMovement();
        e.setDeltaMovement(m.x * 0.4 + dir.x * k, Math.max(m.y, up * f), m.z * 0.4 + dir.z * k);
        e.hurtMarked = true;
        e.setOnGround(false);
    }

    public static void fx(Level level, ParticleOptions type, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (level instanceof ServerLevel sl) {
            int n = Math.max(1, (int) Math.round(count * GarConfig.G.particleDensity.get()));
            sl.sendParticles(type, x, y, z, n, dx, dy, dz, speed);
        }
    }

    /** One particle with an exact velocity (the count-0 trick). */
    public static void fxDir(Level level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(type, x, y, z, 0, vx, vy, vz, 1.0);
        }
    }

    public static void sound(Level level, double x, double y, double z, SoundEvent s, float vol, float pitch) {
        level.playSound(null, x, y, z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /**
     * A secondary cosmic explosion: starburst, shock rings, sparks and smoke, plus the optional damage / knockback of the
     * config. The blocks around are never harmed.
     */
    public static void explosion(Level level, LivingEntity caster, Vec3 p, double size, boolean loud) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        fx(level, ModParticles.BURST.get(), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        fx(level, ModParticles.RING.get(), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        fx(level, ModParticles.SPARK.get(), p.x, p.y, p.z, (int) (14 + size * 4), size * 0.25, size * 0.25, size * 0.25, 0.45);
        fx(level, ModParticles.STAR.get(), p.x, p.y, p.z, (int) (6 + size * 2), size * 0.3, size * 0.3, size * 0.3, 0.18);
        fx(level, ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        if (loud) {
            sound(level, p.x, p.y, p.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 2.4f, 0.6f);
            sound(level, p.x, p.y, p.z, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT, 1.6f, 0.6f);
        }
        double radius = GarConfig.GAMMA.explosionRadius.get() * (size / 6.0);
        double dmg = GarConfig.GAMMA.explosionDamage.get();
        for (LivingEntity t : around(level, caster, p, radius)) {
            Vec3 away = t.getBoundingBox().getCenter().subtract(p);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : away.normalize();
            push(t, away, 0.55, 0.3);
            if (dmg > 0) {
                hurt(caster, t, dmg, 0, 1.0);
            }
        }
    }
}
