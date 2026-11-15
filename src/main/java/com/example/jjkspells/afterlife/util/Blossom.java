package com.example.jjkspells.afterlife.util;

import com.example.jjkspells.afterlife.config.AfterlifeConfig;
import com.example.jjkspells.afterlife.registry.ModEffects;
import com.example.jjkspells.afterlife.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Blood Blossom bookkeeping: who planted it, apply / consume / detonate. */
public final class Blossom {
    private static final Map<UUID, UUID> OWNER = new ConcurrentHashMap<>();

    private Blossom() {
    }

    public static boolean has(LivingEntity e) {
        return e.hasEffect(ModEffects.BLOOD_BLOSSOM.get());
    }

    public static void apply(LivingEntity caster, LivingEntity target, int ticks) {
        if (!target.isAlive() || ticks <= 0 || target.level().isClientSide) {
            return;
        }
        target.addEffect(new MobEffectInstance(ModEffects.BLOOD_BLOSSOM.get(), ticks, 0, false, false, true));
        OWNER.put(target.getUUID(), caster.getUUID());
        Vec3 c = target.getBoundingBox().getCenter();
        Util.fx(target.level(), ModParticles.PETAL.get(), c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.08);
    }

    /** Removes the blossom; returns whether there was one. */
    public static boolean consume(LivingEntity target) {
        if (!has(target)) {
            return false;
        }
        target.removeEffect(ModEffects.BLOOD_BLOSSOM.get());
        OWNER.remove(target.getUUID());
        return true;
    }

    public static LivingEntity owner(ServerLevel level, LivingEntity target) {
        UUID id = OWNER.get(target.getUUID());
        if (id == null) {
            return null;
        }
        Entity e = level.getEntity(id);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    public static void forget(LivingEntity e) {
        OWNER.remove(e.getUUID());
    }

    /** Area burst around a consumed blossom. */
    public static void detonate(LivingEntity caster, LivingEntity centre, double flat, double pct, double radius) {
        Vec3 c = centre.getBoundingBox().getCenter();
        var level = centre.level();
        for (LivingEntity e : Util.enemies(level, new Vec3(centre.getX(), centre.getY(), centre.getZ()), radius, caster)) {
            Util.hurt(caster, e, flat, pct, 1.0);
        }
        Util.fx(level, ModParticles.FLASH.get(), c.x, c.y, c.z, 2, 0.1, 0.1, 0.1, 0);
        Util.fx(level, ModParticles.PETAL.get(), c.x, c.y, c.z, 40, radius * 0.4, 0.6, radius * 0.4, 0.2);
        Util.fx(level, ModParticles.BUTTERFLY_RED.get(), c.x, c.y, c.z, 10, 0.5, 0.5, 0.5, 0.12);
        Util.fx(level, ModParticles.EMBER.get(), c.x, c.y, c.z, 30, radius * 0.4, 0.5, radius * 0.4, 0.15);
        Util.sound(level, c.x, c.y, c.z, com.example.jjkspells.afterlife.registry.ModSounds.STRIKE.get(), 1.0f, 1.4f);
    }

    public static int ticksOf(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }

    public static int intervalTicks() {
        return Math.max(5, ticksOf(AfterlifeConfig.BLOSSOM.intervalSeconds.get()));
    }
}
