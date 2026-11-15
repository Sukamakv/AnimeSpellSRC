package com.example.jjkspells.diablo.entity;

import com.example.jjkspells.diablo.config.DiaConfig;
import com.example.jjkspells.diablo.registry.*;
import com.example.jjkspells.diablo.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Abyssal Genesis: seven black stars rise, fall on the enemies, then a supernova and the cosmic collapse. fx type 1 = a falling star {index, x, y, z}. */
public class AbyssalGenesisEntity extends SeqEntity {
    private static final int FALL = 10;
    private final Set<UUID> struck = new HashSet<>();
    private final List<double[]> pending = new ArrayList<>();
    private Random rand;
    private boolean catastrophe, decided;

    public AbyssalGenesisEntity(EntityType<? extends AbyssalGenesisEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 30;
    }

    public static AbyssalGenesisEntity create(Level level, LivingEntity caster) {
        var c = DiaConfig.GENESIS;
        AbyssalGenesisEntity e = new AbyssalGenesisEntity(ModEntities.GENESIS.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.collapseRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(11.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = DiaConfig.GENESIS;
        Vec3 p = position();
        if (rand == null) {
            rand = new Random(getId() * 1299709L + 5);
        }
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HUM.get(), 5f, 0.4f);
        }
        if (at(age, 1.5)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.VORTEX.get(), 5f, 0.5f);
        }
        if (age >= tk(1.5) && age < tk(6.5) && age % 2 == 0) {
            for (LivingEntity e : foes(o, c.pullRadius.get())) {
                Util.pull(e, p, c.pullStrength.get(), 3.0);
            }
        }
        if (in(age, 1.5, 4.5) && age % 4 == 0) {
            Util.fx(level(), ModParticles.ASH.get(), p.x, p.y + 3, p.z, 6, 8, 3, 8, 0.02);
            Util.fx(level(), ModParticles.SOUL.get(), p.x, p.y + 1, p.z, 4, c.radius.get() * 0.5, 0.5, c.radius.get() * 0.5, 0.02);
        }
        int s0 = tk(4.5), s1 = tk(6.5), step = Math.max(1, (s1 - s0) / 7);
        if (age >= s0 && age < s1 && (age - s0) % step == 0 && (age - s0) / step < 7) {
            int i = (age - s0) / step;
            List<LivingEntity> cand = new ArrayList<>();
            for (LivingEntity e : foes(o, c.radius.get() + 5)) {
                if (!struck.contains(e.getUUID())) {
                    cand.add(e);
                }
            }
            Vec3 q = cand.isEmpty() ? spot(rand, c.radius.get() * 0.8) : cand.get(rand.nextInt(cand.size())).position();
            pending.add(new double[]{age + FALL, q.x, q.y, q.z});
            pushFx(1, i, q.x, q.y, q.z);
            Util.sound(level(), q.x, q.y, q.z, ModSounds.CHARGE.get(), 3f, 0.6f);
        }
        for (Iterator<double[]> it = pending.iterator(); it.hasNext(); ) {
            double[] d = it.next();
            if (age >= d[0]) {
                it.remove();
                Vec3 q = new Vec3(d[1], d[2], d[3]);
                for (LivingEntity e : Util.enemies(level(), q, c.starImpactRadius.get(), o)) {
                    if (struck.add(e.getUUID())) {
                        strike(o, e, c.starFlat.get(), c.starPercent.get(), 1.0, c.armorIgnorePercent.get(), c.reduceIgnorePercent.get());
                    }
                }
                Util.flameBurst(level(), q.x, q.y, q.z, 6, 80);
                Util.fx(level(), ModParticles.ASH.get(), q.x, q.y + 1, q.z, 30, 3, 1, 3, 0.4);
                Util.sound(level(), q.x, q.y, q.z, ModSounds.BOOM.get(), 4f, 0.7f);
            }
        }
        if (!decided && age >= s1 + FALL) {
            decided = true;
            catastrophe = struck.size() >= c.catastropheCount.get();
            if (catastrophe) {
                setR2(c.bigCollapseRadius.get());
            }
        }
        if (age == tk(6.5) + FALL + 1) {
            double mult = catastrophe ? 1.0 + c.catastropheBonusPercent.get() / 100.0 : 1.0;
            for (LivingEntity e : foes(o, c.radius.get())) {
                strike(o, e, c.supernovaFlat.get(), c.supernovaPercent.get(), mult, c.armorIgnorePercent.get(), c.reduceIgnorePercent.get());
            }
            Util.voidBurst(level(), p.x, p.y + 6, p.z, 20, 260);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.ROAR.get(), 5f, 0.5f);
        }
        if (at(age, 8.5)) {
            double R = catastrophe ? c.bigCollapseRadius.get() : c.collapseRadius.get();
            for (LivingEntity e : foes(o, R)) {
                strike(o, e, c.finalFlat.get(), c.finalPercent.get(), boss(e, c.bossBonusPercent.get()), c.armorIgnorePercent.get(), c.reduceIgnorePercent.get());
            }
            Util.voidBurst(level(), p.x, p.y + 3, p.z, 28, 300);
            Util.ring(level(), ModParticles.SOUL.get(), p.add(0, 0.5, 0), 96, 3.0, 0.05);
            Util.ring(level(), ModParticles.EMBER.get(), p.add(0, 0.8, 0), 96, 2.2, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 5f, 0.45f);
        }
    }
}
