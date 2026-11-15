package com.example.jjkspells.spells;

import com.example.jjkspells.JJKSpells;
import com.example.jjkspells.ModEffects;
import com.example.jjkspells.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Keeps every Harrowed Path mist field alive.
 *
 * Every few ticks it re-applies short effects to whoever stands inside, so everything fades by itself soon after
 * someone leaves (or the mist ends):
 *   Viego   - Viego's Mist (+35% speed, melee x2 in ViegoEvents) and a brief Invisibility
 *   enemies - Mist Drain (-20% speed, -35% spell power) and Weakness II
 *   players - Mist Vision, which makes their own client draw the dense mist and the closer, darker fog
 * It also draws the ring of spectral flame along the ground at the edge of the field, near each player that can see it.
 */
@Mod.EventBusSubscriber(modid = JJKSpells.MODID)
public class HarrowedPathManager {

    private static final int REFRESH_EVERY = 5;
    private static final int EFFECT_TICKS = 12;       // a little longer than the refresh interval
    private static final double HEIGHT = 12.0;        // the field reaches this far above and below its centre

    private static class Zone {
        final ServerLevel level;
        final UUID casterId;
        final Vec3 center;
        final double radius;
        int age = 0;
        final int duration;

        Zone(ServerLevel level, LivingEntity caster, int duration) {
            this.level = level;
            this.casterId = caster.getUUID();
            this.center = caster.position();
            this.radius = HarrowedPathSpell.RADIUS;
            this.duration = duration;
        }
    }

    private static final List<Zone> ZONES = new ArrayList<>();

    public static void start(ServerLevel level, LivingEntity caster, int duration) {
        Zone z = new Zone(level, caster, duration);
        ZONES.add(z);
        ViegoFX.ring(level, z.center, (float) z.radius);
        ViegoFX.mist(level, z.center.add(0, 1.0, 0), 30, 3.0);
        ViegoFX.flames(level, z.center, 14, 1.5);
        MartialFX.sound(level, z.center, SoundEvents.SOUL_ESCAPE, 2.0f, 0.5f);
        MartialFX.sound(level, z.center, SoundEvents.WITHER_AMBIENT, 1.2f, 0.6f);
        MartialFX.sound(level, z.center, SoundEvents.PHANTOM_AMBIENT, 1.5f, 0.4f);
    }

    /** True if this entity is currently standing in a mist field that its owner cast (for "who owns the mist" checks). */
    public static boolean inOwnMist(LivingEntity e) {
        for (Zone z : ZONES) {
            if (z.casterId.equals(e.getUUID()) && z.level == e.level() && inside(z, e)) return true;
        }
        return false;
    }

    private static boolean inside(Zone z, Entity e) {
        double dx = e.getX() - z.center.x, dz = e.getZ() - z.center.z;
        return dx * dx + dz * dz <= z.radius * z.radius && Math.abs(e.getY() - z.center.y) <= HEIGHT;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ZONES.isEmpty()) return;
        Iterator<Zone> it = ZONES.iterator();
        while (it.hasNext()) {
            Zone z = it.next();
            Entity ce = z.level.getEntity(z.casterId);
            LivingEntity caster = (ce instanceof LivingEntity le) ? le : null;   // the mist outlives a dead or absent caster
            if (z.age % REFRESH_EVERY == 0) refresh(z, caster);
            if (z.age % 3 == 0) edgeRing(z);
            z.age++;
            if (z.age >= z.duration) {
                MartialFX.sound(z.level, z.center, SoundEvents.SOUL_ESCAPE, 1.5f, 1.2f);
                it.remove();
            }
        }
    }

    private static void refresh(Zone z, LivingEntity caster) {
        ServerLevel level = z.level;
        double r = z.radius;
        AABB box = new AABB(z.center.x - r, z.center.y - HEIGHT, z.center.z - r, z.center.x + r, z.center.y + HEIGHT, z.center.z + r);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> t.isAlive() && !t.isSpectator())) {
            if (!inside(z, e)) continue;
            if (e instanceof ServerPlayer) {
                e.addEffect(new MobEffectInstance(ModEffects.MIST_VISION.get(), EFFECT_TICKS, 0, false, false, false));
            }
            if (caster != null && e == caster) {
                e.addEffect(new MobEffectInstance(ModEffects.VIEGO_MIST.get(), EFFECT_TICKS, 0, false, false, true));
                e.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));   // "brief": fades 2s after he leaves
            } else if (caster == null || !e.isAlliedTo(caster)) {
                e.addEffect(new MobEffectInstance(ModEffects.MIST_DRAIN.get(), EFFECT_TICKS, 0, false, false, true));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1, false, false, true));
            }
        }
    }

    /** Spectral flame and mist along the ground where the field ends, near every player within sight of that edge. */
    private static void edgeRing(Zone z) {
        ServerLevel level = z.level;
        RandomSource rng = level.random;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - z.center.x, dz = p.getZ() - z.center.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(dist - z.radius) > 28) continue;
            double bearing = Math.atan2(dz, dx);
            for (int i = 0; i < 8; i++) {
                double a = bearing + (rng.nextDouble() - 0.5) * 1.6;
                double x = z.center.x + Math.cos(a) * z.radius, zz = z.center.z + Math.sin(a) * z.radius;
                double y = MoonFX.groundAt(level, x, zz, p.getY());
                FX.one(level, ModParticles.VG_MIST.get(), new Vec3(x, y + 0.3, zz), new Vec3(0, 0.01, 0));
                if (i % 2 == 0) {
                    FX.one(level, ModParticles.VG_FLAME.get(), new Vec3(x, y + 0.1, zz), new Vec3(0, 0.05, 0));
                }
            }
        }
    }
}
