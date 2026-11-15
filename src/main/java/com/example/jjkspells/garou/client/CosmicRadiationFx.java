package com.example.jjkspells.garou.client;

import com.example.jjkspells.garou.entity.CosmicRadiationEntity;
import com.example.jjkspells.garou.spell.GarouCosmicRadiationSpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only visual state of one Cosmic Radiation controller: twinkling stars, motes that drift towards the irradiated,
 * and the list of expanding waves. Ticked from the entity, drawn by {@link CosmicRadiationRenderer}.
 */
public final class CosmicRadiationFx {
    public static final float TAU = (float) (Math.PI * 2.0);
    public static final int WAVE_LIFE = 36;

    /** A star lives life ticks and plays the 16 frame twinkle flip-book once. follow >= 0: offset from that entity. */
    public static final class Star {
        public float x, y, z, vx, vy, vz, size, rot, spin;
        public int born, life, follow = -1;
    }

    public static final class Mote {
        public float sx, sy, sz, phase, amp, size;
        public int target, born, life;
    }

    public final List<Star> stars = new ArrayList<>();
    public final List<Mote> motes = new ArrayList<>();
    /** Entity age (ticks) at which each live wave started. */
    public final List<Float> waves = new ArrayList<>();
    private int lastPulse;

    public static CosmicRadiationFx of(CosmicRadiationEntity e) {
        if (e.fx instanceof CosmicRadiationFx f) return f;
        CosmicRadiationFx created = new CosmicRadiationFx();
        e.fx = created;
        return created;
    }

    /** 0..1 intensity of the aura itself: eases in over 16 ticks, eases out over the last 24 ticks of the aura. */
    public static float master(float age) {
        float in = RadPen.smooth(age / 16f);
        float out = 1f - RadPen.smooth((age - (GarouCosmicRadiationSpell.AURA_TICKS - 24f)) / 24f);
        return in * out;
    }

    public static void tick(CosmicRadiationEntity e) {
        Level lvl = e.level();
        RandomSource rnd = lvl.random;
        CosmicRadiationFx fx = of(e);
        int age = e.tickCount;

        if (e.getPulseCounter() != fx.lastPulse) {
            fx.lastPulse = e.getPulseCounter();
            fx.waves.add((float) age);
        }
        fx.waves.removeIf(w -> age - w > WAVE_LIFE + 2);

        boolean auraOn = age < GarouCosmicRadiationSpell.AURA_TICKS;
        float m = master(age);
        Entity owner = lvl.getEntity(e.getOwnerId());

        if (auraOn && m > 0.05f) {
            // tiny white stars close to the body ...
            for (int i = 0; i < 2; i++) {
                if (rnd.nextFloat() < 0.8f * m && fx.stars.size() < 240) {
                    Star s = star(rnd, age, 8 + rnd.nextInt(14));
                    float a = rnd.nextFloat() * TAU, r = 0.7f + rnd.nextFloat() * 1.7f;
                    s.x = Mth.cos(a) * r;
                    s.z = Mth.sin(a) * r;
                    s.y = rnd.nextFloat() * 2.5f;
                    s.vy = 0.006f + rnd.nextFloat() * 0.01f;
                    s.size = 0.07f + rnd.nextFloat() * 0.12f;
                    fx.stars.add(s);
                }
            }
            // ... and a sparser field of larger ones across the whole 8 block radius
            if (age % 2 == 0 && fx.stars.size() < 240) {
                Star s = star(rnd, age, 16 + rnd.nextInt(14));
                float a = rnd.nextFloat() * TAU, r = 2.2f + rnd.nextFloat() * 5.8f;
                s.x = Mth.cos(a) * r;
                s.z = Mth.sin(a) * r;
                s.y = -0.3f + rnd.nextFloat() * 3.5f;
                s.size = 0.11f + rnd.nextFloat() * 0.2f;
                fx.stars.add(s);
            }
            if (owner != null && age % 7 == 0) {
                // vanilla particles stay a supporting layer: a few white sparks rising off the body
                lvl.addParticle(ParticleTypes.END_ROD, owner.getRandomX(1.2), owner.getY() + rnd.nextFloat() * 2.2,
                        owner.getRandomZ(1.2), 0, 0.015, 0);
            }
        }

        // affected enemies: stars twinkling on them, motes floating towards them
        for (int pass = 0; pass < 2; pass++) {
            int[] ids = pass == 0 ? e.clientIn : e.clientOut;
            for (int id : ids) {
                Entity t = lvl.getEntity(id);
                if (t == null) continue;
                if (rnd.nextFloat() < (pass == 0 ? 0.45f : 0.2f) && fx.stars.size() < 240) {
                    Star s = star(rnd, age, 12 + rnd.nextInt(12));
                    s.follow = id;
                    s.x = (rnd.nextFloat() - 0.5f) * t.getBbWidth() * 1.7f;
                    s.z = (rnd.nextFloat() - 0.5f) * t.getBbWidth() * 1.7f;
                    s.y = rnd.nextFloat() * t.getBbHeight();
                    s.vy = 0.008f;
                    s.size = 0.09f + rnd.nextFloat() * 0.14f;
                    fx.stars.add(s);
                }
                if (pass == 0 && auraOn && age % 3 == 0 && fx.motes.size() < 90) {
                    Mote mo = new Mote();
                    float a = rnd.nextFloat() * TAU, r = 0.35f + rnd.nextFloat() * 0.5f;
                    mo.sx = Mth.cos(a) * r;
                    mo.sz = Mth.sin(a) * r;
                    mo.sy = 0.3f + rnd.nextFloat() * 1.5f;
                    mo.target = id;
                    mo.born = age;
                    mo.life = 22 + rnd.nextInt(14);
                    mo.phase = rnd.nextFloat() * TAU;
                    mo.amp = 0.25f + rnd.nextFloat() * 0.45f;
                    mo.size = 0.07f + rnd.nextFloat() * 0.05f;
                    fx.motes.add(mo);
                }
                if (age % 8 == 0 && rnd.nextFloat() < 0.5f) {
                    lvl.addParticle(ParticleTypes.REVERSE_PORTAL, t.getRandomX(0.6), t.getRandomY(), t.getRandomZ(0.6), 0, 0.02, 0);
                }
            }
        }

        fx.stars.removeIf(s -> age - s.born >= s.life);
        fx.motes.removeIf(mo -> age - mo.born >= mo.life);
    }

    private static Star star(RandomSource rnd, int age, int life) {
        Star s = new Star();
        s.born = age;
        s.life = life;
        s.rot = rnd.nextFloat() * TAU;
        s.spin = (rnd.nextFloat() - 0.5f) * 0.04f;
        return s;
    }
}
