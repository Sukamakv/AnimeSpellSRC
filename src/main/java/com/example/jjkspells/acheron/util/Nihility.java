package com.example.jjkspells.acheron.util;

import com.example.jjkspells.acheron.config.AcheronConfig;
import com.example.jjkspells.acheron.registry.ModEffects;
import com.example.jjkspells.acheron.registry.ModParticles;
import com.example.jjkspells.acheron.registry.ModSounds;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The two shared resources of the set: Slashed Dream (0..9, on the caster) and Crimson Knot (0..9, on an enemy), both kept as effects whose amplifier is the count minus one. */
public final class Nihility {
    public static final int MAX = 9;
    /** Marked enemy -> the Rainleaf entity that owns the mark. */
    public static final Map<UUID, UUID> MARKED = new ConcurrentHashMap<>();

    private Nihility() {
    }

    // ------------------------------------------------------------------ Slashed Dream

    public static int dreams(LivingEntity e) {
        MobEffectInstance i = e.getEffect(ModEffects.DREAM.get());
        return i == null ? 0 : Math.min(MAX, i.getAmplifier() + 1);
    }

    private static void setDreams(LivingEntity e, int n) {
        e.removeEffect(ModEffects.DREAM.get());
        if (n > 0) {
            e.addEffect(new MobEffectInstance(ModEffects.DREAM.get(), 20 * 60 * 20, Math.min(MAX, n) - 1, false, false, true));
        }
    }

    public static int addDreams(LivingEntity e, int n) {
        if (n <= 0 || e.level().isClientSide) {
            return dreams(e);
        }
        int now = Math.min(MAX, dreams(e) + n);
        setDreams(e, now);
        Util.sound(e.level(), e.getX(), e.getY(), e.getZ(), ModSounds.CRIMSON_KNOT_APPLY.get(), 0.7f, 1.4f);
        Util.fx(e.level(), ModParticles.PETAL.get(), e.getX(), e.getY() + 1.2, e.getZ(), 10, 0.4, 0.6, 0.4, 0.05);
        return now;
    }

    public static boolean spendDreams(LivingEntity e, int n) {
        int d = dreams(e);
        if (d < n) {
            return false;
        }
        setDreams(e, d - n);
        return true;
    }

    // ------------------------------------------------------------------ Crimson Knot

    public static int knots(LivingEntity e) {
        MobEffectInstance i = e.getEffect(ModEffects.KNOT.get());
        return i == null ? 0 : Math.min(MAX, i.getAmplifier() + 1);
    }

    /** Puts knots on an enemy (up to the cap); returns the new count. Every call refreshes the duration. */
    public static int addKnots(LivingEntity e, int n, int cap) {
        if (e.level().isClientSide || n <= 0) {
            return knots(e);
        }
        int now = Math.min(Math.min(MAX, cap), knots(e) + n);
        e.removeEffect(ModEffects.KNOT.get());
        e.addEffect(new MobEffectInstance(ModEffects.KNOT.get(), 20 * AcheronConfig.G.knotSeconds.get(), now - 1, false, false, true));
        Util.fx(e.level(), ModParticles.MARK_R.get(), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 1, 0, 0, 0, 0);
        return now;
    }

    /** Removes up to max knots (all when max <= 0); returns how many were taken. */
    public static int takeKnots(LivingEntity e, int max) {
        int k = knots(e);
        if (k <= 0) {
            return 0;
        }
        int take = max <= 0 ? k : Math.min(k, max);
        e.removeEffect(ModEffects.KNOT.get());
        if (k - take > 0) {
            e.addEffect(new MobEffectInstance(ModEffects.KNOT.get(), 20 * AcheronConfig.G.knotSeconds.get(), k - take - 1, false, false, true));
        }
        Util.fx(e.level(), ModParticles.SHARD.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 4 + take * 2, 0.3, 0.5, 0.3, 0.12);
        Util.sound(e.level(), e.getX(), e.getY(), e.getZ(), ModSounds.CRIMSON_KNOT_SHATTER.get(), 0.7f, 0.9f + 0.05f * take);
        return take;
    }

    public static boolean elite(LivingEntity e) {
        return Util.isBoss(e) || e.getMaxHealth() >= AcheronConfig.G.eliteHealth.get();
    }

    public static Vec3 chest(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.6, 0);
    }
}
