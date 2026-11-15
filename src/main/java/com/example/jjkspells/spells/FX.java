package com.example.jjkspells.spells;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Particle helper. Unlike ServerLevel.sendParticles(...), these reach players up to 200 blocks away
 * (the normal limit is 32), so the arrow and the explosion stay visible from far off.
 */
public final class FX {
    private static final double RANGE_SQR = 200.0 * 200.0;

    private FX() {}

    public static <T extends ParticleOptions> void emit(ServerLevel level, T type, double x, double y, double z,
                                                        int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) > RANGE_SQR) continue;
            level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** One particle at pos. The three "velocity" values are its velocity (or its vector, for line particles). */
    public static <T extends ParticleOptions> void one(ServerLevel level, T type, Vec3 pos, Vec3 vel) {
        emit(level, type, pos.x, pos.y, pos.z, 0, vel.x, vel.y, vel.z, 1.0);
    }
}
