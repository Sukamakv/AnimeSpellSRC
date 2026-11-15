package com.example.jjkspells.irontomb;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Server-side particle / sound helpers. Particles are sent long-distance so the big attacks read from far away. */
public final class IrontombFxServer {
    public static final Vector3f RED = new Vector3f(1.0f, 0.18f, 0.10f);
    public static final Vector3f GOLD = new Vector3f(1.0f, 0.78f, 0.22f);
    public static final Vector3f VIOLET = new Vector3f(0.62f, 0.32f, 1.0f);
    public static final Vector3f WHITE = new Vector3f(1f, 1f, 1f);

    private IrontombFxServer() {
    }

    public static DustParticleOptions dust(Vector3f c, float size) {
        return new DustParticleOptions(c, size);
    }

    public static DustColorTransitionOptions fade(Vector3f from, Vector3f to, float size) {
        return new DustColorTransitionOptions(from, to, size);
    }

    public static void send(ServerLevel lv, ParticleOptions p, double x, double y, double z, int n, double dx, double dy, double dz, double speed) {
        for (ServerPlayer pl : lv.players()) {
            if (pl.distanceToSqr(x, y, z) < 170.0 * 170.0) {
                lv.sendParticles(pl, p, true, x, y, z, n, dx, dy, dz, speed);
            }
        }
    }

    /** Ring of particles flat on the ground. */
    public static void ring(ServerLevel lv, ParticleOptions p, Vec3 c, double radius, int n, double outSpeed) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            double cx = Math.cos(a), cz = Math.sin(a);
            send(lv, p, c.x + cx * radius, c.y + 0.15, c.z + cz * radius, 0, cx, 0.02, cz, outSpeed);
        }
    }

    /** A line of particles from a to b. */
    public static void line(ServerLevel lv, ParticleOptions p, Vec3 a, Vec3 b, int steps) {
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            send(lv, p, a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.z + (b.z - a.z) * f, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** Vertical column of particles. */
    public static void column(ServerLevel lv, ParticleOptions p, Vec3 base, double height, int steps, double spread) {
        for (int i = 0; i <= steps; i++) {
            send(lv, p, base.x, base.y + height * i / steps, base.z, 2, spread, 0.2, spread, 0.02);
        }
    }

    public static void burst(ServerLevel lv, Vec3 c, Vector3f color, int count, double spread, double speed) {
        send(lv, dust(color, 2.2f), c.x, c.y, c.z, count, spread, spread, spread, speed);
        send(lv, ParticleTypes.END_ROD, c.x, c.y, c.z, count / 3, spread * 0.5, spread * 0.5, spread * 0.5, speed * 1.4);
    }

    public static void sound(ServerLevel lv, double x, double y, double z, SoundEvent s, float vol, float pitch) {
        lv.playSound(null, x, y, z, s, SoundSource.HOSTILE, vol, pitch);
    }
}
