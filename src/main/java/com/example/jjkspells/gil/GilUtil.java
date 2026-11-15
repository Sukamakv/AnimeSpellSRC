package com.example.jjkspells.gil;

import com.example.jjkspells.ModParticles;
import com.example.jjkspells.ModSounds;
import com.example.jjkspells.spells.FX;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Small server-side effect helpers shared by the five managers. */
public final class GilUtil {
    private GilUtil() {}

    public static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void announce(LivingEntity c, Component text) {
        if (c instanceof ServerPlayer sp) sp.displayClientMessage(text, true);
    }

    /** A golden portal disc at {@code pos} whose face points along {@code normal}. */
    public static void portal(ServerLevel level, Vec3 pos, Vec3 normal, double radius, boolean loud, float pitch) {
        Vec3 n = normal.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : normal.normalize();
        FX.one(level, ModParticles.GATE_PORTAL.get(), pos, n.scale(radius));
        FX.one(level, ModParticles.GOLD_GLOW.get(), pos, new Vec3(radius * 1.9, 0, 0));
        FX.one(level, ModParticles.GOLD_GLINT.get(), pos, new Vec3(radius * 1.4, 0, 0));
        if (loud) sound(level, pos, ModSounds.GATE_OPEN.get(), 0.8f, pitch);
    }

    public static void flatRing(ServerLevel level, Vec3 pos, double radius) {
        FX.one(level, ModParticles.GOLD_RING_FLAT.get(), pos, new Vec3(radius, 0, 0));
    }

    /** Gold sparks sprayed from a point. */
    public static void sparks(ServerLevel level, Vec3 at, int n, double speed) {
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(level.random.nextDouble() - 0.5, level.random.nextDouble() * 0.9, level.random.nextDouble() - 0.5).normalize()
                    .scale(speed * (0.3 + level.random.nextDouble() * 0.9));
            FX.one(level, i % 4 == 0 ? ModParticles.GOLD_SHARD.get() : ModParticles.GOLD_SPARK.get(), at, d);
        }
    }

    /** Throws {@code e} away from {@code from} and up. Bosses are only nudged. */
    public static void launch(LivingEntity e, Vec3 from, double out, double up) {
        Vec3 d = e.getBoundingBox().getCenter().subtract(from);
        d = new Vec3(d.x, 0, d.z);
        d = d.lengthSqr() < 1e-4 ? Vec3.ZERO : d.normalize();
        double k = GilDmg.isBoss(e) ? 0.35 : 1.0;
        e.setDeltaMovement(d.scale(out * k).add(0, up * k, 0));
        e.hurtMarked = true;
    }

    public static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    public static Vec3 rightOf(Vec3 forwardFlat) {
        return new Vec3(-forwardFlat.z, 0, forwardFlat.x);
    }
}
