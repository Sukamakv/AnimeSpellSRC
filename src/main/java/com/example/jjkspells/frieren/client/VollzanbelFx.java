package com.example.jjkspells.frieren.client;

import com.example.jjkspells.frieren.entity.VollzanbelEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.example.jjkspells.frieren.spell.FrierenVollzanbelSpell.*;

/**
 * Client-only state of one Vollzanbel controller: a short history of the caster's flame origin/direction (so a sweeping
 * flame whips instead of rotating rigidly), how far the flame reaches (walls), phase change times, embers and debris.
 */
public final class VollzanbelFx {
    public static final float TAU = (float) (Math.PI * 2.0);
    public static final int HIST = 32;

    public static final class Ember {
        public double x, y, z;
        public float vx, vy, vz, size;
        public int born, life;
    }

    public static final class Debris {
        public double x, y, z;
        public float vx, vy, vz, rot, spin, size;
        public int born, life, cell;
        public boolean stuck;
    }

    /** {originX, originY, originZ, dirX, dirY, dirZ} absolute, newest at ptr. */
    private final double[][] hist = new double[HIST][6];
    private int histCount, ptr = -1;
    public float reach = (float) RANGE;
    public boolean hitSurface;
    public double[] hit = new double[3];
    public int lastPhase, tConv = -1, tExp = -1;
    public final List<Ember> embers = new ArrayList<>();
    public final List<Debris> debris = new ArrayList<>();

    public static VollzanbelFx of(VollzanbelEntity e) {
        if (e.fx instanceof VollzanbelFx f) return f;
        VollzanbelFx created = new VollzanbelFx();
        e.fx = created;
        return created;
    }

    public boolean hasHistory() { return histCount > 0; }

    /** Entry recorded lag ticks ago (0 = most recent), clamped to what exists. */
    public double[] get(int lag) {
        int l = Mth.clamp(lag, 0, Math.max(histCount - 1, 0));
        return hist[((ptr - l) % HIST + HIST) % HIST];
    }

    private void push(Vec3 o, Vec3 d) {
        ptr = (ptr + 1) % HIST;
        double[] h = hist[ptr];
        h[0] = o.x;
        h[1] = o.y;
        h[2] = o.z;
        h[3] = d.x;
        h[4] = d.y;
        h[5] = d.z;
        histCount = Math.min(histCount + 1, HIST);
    }

    public static void tick(VollzanbelEntity e) {
        Level lvl = e.level();
        RandomSource rnd = lvl.random;
        VollzanbelFx fx = of(e);
        int age = e.tickCount;
        int phase = e.clientPhase;
        Entity owner = lvl.getEntity(e.getOwnerId());

        if (owner != null && phase == 0 && age <= FLAME_END + 1) {
            Vec3 look = owner.getLookAngle();
            Vec3 origin = VollzanbelEntity.origin(owner.getEyePosition(), look);
            fx.push(origin, look);
            if (age >= START - 6) {
                Vec3 end = origin.add(look.scale(RANGE));
                BlockHitResult h = lvl.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
                boolean surf = h.getType() != HitResult.Type.MISS;
                float target = surf ? (float) h.getLocation().distanceTo(origin) : (float) RANGE;
                fx.reach = Mth.lerp(0.5f, fx.reach, target);
                fx.hitSurface = surf;
                if (surf) {
                    fx.hit[0] = h.getLocation().x;
                    fx.hit[1] = h.getLocation().y;
                    fx.hit[2] = h.getLocation().z;
                }
            }
        }

        // phase changes
        if (phase != fx.lastPhase) {
            fx.lastPhase = phase;
            if (phase == 1) fx.tConv = age;
            if (phase == 2) {
                fx.tExp = age;
                if (e.clientPoint != null) fx.explode(lvl, rnd, e.clientPoint, age);
            }
        }

        // embers spat off the flame
        if (phase == 0 && age >= START && age < FLAME_END && fx.hasHistory()) {
            double[] h = fx.get(0);
            float len = Math.min((float) RANGE, fx.reach + (fx.hitSurface ? 3f : 0f));
            for (int i = 0; i < 4 && fx.embers.size() < 520; i++) {
                float d = rnd.nextFloat() * len;
                float R = (float) (CONE_BASE + CONE_SPREAD * d);
                Ember m = new Ember();
                m.x = h[0] + h[3] * d + (rnd.nextFloat() - 0.5f) * R * 1.6f;
                m.y = h[1] + h[4] * d + (rnd.nextFloat() - 0.5f) * R * 1.6f;
                m.z = h[2] + h[5] * d + (rnd.nextFloat() - 0.5f) * R * 1.6f;
                m.vx = (float) h[3] * 0.18f + (rnd.nextFloat() - 0.5f) * 0.1f;
                m.vy = (float) h[4] * 0.18f + 0.06f + rnd.nextFloat() * 0.08f;
                m.vz = (float) h[5] * 0.18f + (rnd.nextFloat() - 0.5f) * 0.1f;
                m.size = 0.12f + rnd.nextFloat() * 0.16f;
                m.born = age;
                m.life = 24 + rnd.nextInt(24);
                fx.embers.add(m);
            }
            if (age % 3 == 0) {
                // vanilla particles: a thin supporting layer only
                float d = rnd.nextFloat() * len;
                lvl.addParticle(ParticleTypes.FLAME, h[0] + h[3] * d, h[1] + h[4] * d, h[2] + h[5] * d, h[3] * 0.25, h[4] * 0.25 + 0.03, h[5] * 0.25);
            }
        }

        for (Ember m : fx.embers) {
            m.x += m.vx;
            m.y += m.vy;
            m.z += m.vz;
            m.vy -= 0.004f;
            m.vx *= 0.98f;
            m.vz *= 0.98f;
        }
        fx.embers.removeIf(m -> age - m.born >= m.life);
        for (Debris d : fx.debris) {
            if (d.stuck) continue;
            d.x += d.vx;
            d.y += d.vy;
            d.z += d.vz;
            d.vy -= 0.045f;
            d.vx *= 0.99f;
            d.vz *= 0.99f;
            d.rot += d.spin;
            BlockPos bp = BlockPos.containing(d.x, d.y, d.z);
            if (lvl.hasChunkAt(bp) && !lvl.getBlockState(bp).getCollisionShape(lvl, bp).isEmpty()) {
                d.stuck = true;
                d.life = (age - d.born) + 40;
            }
        }
        fx.debris.removeIf(d -> age - d.born >= d.life);
    }

    private void explode(Level lvl, RandomSource rnd, double[] p, int age) {
        for (int i = 0; i < 110; i++) {
            Debris d = new Debris();
            float a = rnd.nextFloat() * TAU, sp = 0.35f + rnd.nextFloat() * 1.1f;
            d.x = p[0];
            d.y = p[1] + 0.5;
            d.z = p[2];
            d.vx = Mth.cos(a) * sp;
            d.vz = Mth.sin(a) * sp;
            d.vy = 0.5f + rnd.nextFloat() * 1.3f;
            d.rot = rnd.nextFloat() * TAU;
            d.spin = (rnd.nextFloat() - 0.5f) * 0.5f;
            d.size = 0.5f + rnd.nextFloat() * 1.3f;
            d.cell = rnd.nextInt(16);
            d.born = age;
            d.life = 55 + rnd.nextInt(60);
            debris.add(d);
        }
        for (int i = 0; i < 260 && embers.size() < 520; i++) {
            Ember m = new Ember();
            float a = rnd.nextFloat() * TAU, sp = 0.2f + rnd.nextFloat() * 1.0f;
            m.x = p[0];
            m.y = p[1] + 1;
            m.z = p[2];
            m.vx = Mth.cos(a) * sp;
            m.vz = Mth.sin(a) * sp;
            m.vy = 0.2f + rnd.nextFloat() * 0.9f;
            m.size = 0.15f + rnd.nextFloat() * 0.3f;
            m.born = age;
            m.life = 30 + rnd.nextInt(50);
            embers.add(m);
        }
        for (int i = 0; i < 40; i++) {
            float a = rnd.nextFloat() * TAU, sp = 0.2f + rnd.nextFloat() * 0.8f;
            lvl.addParticle(ParticleTypes.LARGE_SMOKE, p[0], p[1] + 1, p[2], Mth.cos(a) * sp, 0.2 + rnd.nextFloat() * 0.5, Mth.sin(a) * sp);
        }
        for (int i = 0; i < 30; i++) {
            float a = rnd.nextFloat() * TAU, sp = 0.4f + rnd.nextFloat() * 1.2f;
            lvl.addParticle(ParticleTypes.FLAME, p[0], p[1] + 1, p[2], Mth.cos(a) * sp, 0.1 + rnd.nextFloat() * 0.6, Mth.sin(a) * sp);
        }
    }
}
