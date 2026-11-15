package com.example.jjkspells.frieren.client;

import com.example.jjkspells.frieren.entity.FlammeMagicEntity;
import com.example.jjkspells.frieren.spell.FrierenFlammesUltimateMagicSpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

import static com.example.jjkspells.frieren.spell.FrierenFlammesUltimateMagicSpell.*;

/** Client-only state of one Flamme controller: exact lance impact points (from the synced event list) and rising/bursting motes. */
public final class FlammeFx {
    public static final float TAU = (float) (Math.PI * 2.0);

    public static final class Mote {
        public float x, y, z, vx, vy, vz, size;
        public int born, life;
    }

    /** Exact impact point (relative to the entity) of lance k once it has been launched, else null. */
    public final float[][] impact = new float[LANCES][];
    public final List<Mote> motes = new ArrayList<>();
    private int seen;
    private boolean[] burst = new boolean[LANCES];

    public static FlammeFx of(FlammeMagicEntity e) {
        if (e.fx instanceof FlammeFx f) return f;
        FlammeFx created = new FlammeFx();
        e.fx = created;
        return created;
    }

    public static void tick(FlammeMagicEntity e) {
        Level lvl = e.level();
        RandomSource rnd = lvl.random;
        FlammeFx fx = of(e);
        int age = e.tickCount;

        List<float[]> ev = e.clientEvents;
        for (; fx.seen < ev.size(); fx.seen++) {
            float[] c = ev.get(fx.seen);
            int k = (int) c[0];
            if (k >= 0 && k < LANCES) fx.impact[k] = new float[]{c[1], c[2], c[3]};
        }

        // sparks rise off the caster's circle while it is open
        Entity owner = lvl.getEntity(e.getOwnerId());
        if (owner != null && age < WINDUP + 45 && age > 4) {
            float rx = (float) (owner.getX() - e.getX()), ry = (float) (owner.getY() - e.getY()), rz = (float) (owner.getZ() - e.getZ());
            for (int i = 0; i < 2; i++) {
                float a = rnd.nextFloat() * TAU, r = 1.5f + rnd.nextFloat() * 6.5f;
                Mote m = mote(rnd, age, 30 + rnd.nextInt(24), 0.10f + rnd.nextFloat() * 0.10f);
                m.x = rx + Mth.cos(a) * r;
                m.z = rz + Mth.sin(a) * r;
                m.y = ry + 0.1f;
                m.vy = 0.05f + rnd.nextFloat() * 0.07f;
                fx.motes.add(m);
            }
        }

        // landing bursts
        for (int k = 0; k < LANCES; k++) {
            if (!fx.burst[k] && fx.impact[k] != null && age >= FlammeMagicEntity.launchTick(k) + FLIGHT) {
                fx.burst[k] = true;
                boolean last = k == LANCES - 1;
                float[] p = fx.impact[k];
                int n = last ? 150 : 26;
                for (int i = 0; i < n; i++) {
                    float a = rnd.nextFloat() * TAU, sp = (last ? 0.25f : 0.14f) * (0.3f + rnd.nextFloat());
                    Mote m = mote(rnd, age, 26 + rnd.nextInt(30), 0.10f + rnd.nextFloat() * 0.16f);
                    m.x = p[0];
                    m.y = p[1] + 0.4f;
                    m.z = p[2];
                    m.vx = Mth.cos(a) * sp * (last ? 2.5f : 1f);
                    m.vz = Mth.sin(a) * sp * (last ? 2.5f : 1f);
                    m.vy = (0.12f + rnd.nextFloat() * 0.25f) * (last ? 1.6f : 1f);
                    fx.motes.add(m);
                }
                // vanilla particles stay a thin supporting layer
                for (int i = 0; i < (last ? 24 : 6); i++) {
                    lvl.addParticle(ParticleTypes.END_ROD, e.getX() + p[0] + (rnd.nextFloat() - 0.5f) * 3, e.getY() + p[1] + 0.5,
                            e.getZ() + p[2] + (rnd.nextFloat() - 0.5f) * 3, (rnd.nextFloat() - 0.5f) * 0.3, 0.2 + rnd.nextFloat() * 0.3, (rnd.nextFloat() - 0.5f) * 0.3);
                }
            }
        }

        for (Mote m : fx.motes) {
            m.x += m.vx;
            m.y += m.vy;
            m.z += m.vz;
            m.vy -= 0.0035f;
            m.vx *= 0.97f;
            m.vz *= 0.97f;
        }
        fx.motes.removeIf(m -> age - m.born >= m.life);
        while (fx.motes.size() > 420) fx.motes.remove(0);
    }

    private static Mote mote(RandomSource rnd, int age, int life, float size) {
        Mote m = new Mote();
        m.born = age;
        m.life = life;
        m.size = size;
        return m;
    }
}
