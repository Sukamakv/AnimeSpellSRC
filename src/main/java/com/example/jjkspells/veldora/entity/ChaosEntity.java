package com.example.jjkspells.veldora.entity;

import com.example.jjkspells.veldora.config.VelConfig;
import com.example.jjkspells.veldora.registry.*;
import com.example.jjkspells.veldora.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Chaos King Nyarlathotep - the Law of Impossible Fate. fx type 1 = lightning {x,y,z}, 2 = spatial blade {x,y,z}, 3 = explosive sphere {x,y,z}. */
public class ChaosEntity extends SeqEntity {
    private Random rand;
    private int strikes;
    private final Set<Integer> rolled = new HashSet<>();

    public ChaosEntity(EntityType<? extends ChaosEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 30;
    }

    public static ChaosEntity create(Level level, LivingEntity caster) {
        var c = VelConfig.CHAOS;
        ChaosEntity e = new ChaosEntity(ModEntities.CHAOS.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.radius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(12.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VelConfig.CHAOS;
        Vec3 p = position();
        double R = c.radius.get();
        if (rand == null) {
            rand = new Random(getId() * 4243L + 5);
        }
        if (age == 0) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.HUM.get(), 5f, 0.4f);
        }
        if (in(age, 0, 1.0) && age % 3 == 0) {
            Util.fx(level(), ModParticles.RUNE.get(), o.getX(), o.getY() + 2, o.getZ(), 4, 0.4, 0.4, 0.4, 0.02);
        }
        if (at(age, 1.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.VORTEX.get(), 6f, 0.5f);
        }
        if (age >= tk(2.0) && age < tk(12.0)) {
            if (age % 4 == 0) {
                boolean inside = o.position().distanceToSqr(p) <= R * R;
                if (inside) {
                    Util.reduce(o, c.selfReductionPercent.get() / 100.0, 8);
                    Util.probability(o, c.negatePercent.get(), c.bonusChancePercent.get(), c.bonusDamagePercent.get(), c.redirectPercent.get(), 8);
                    o.addEffect(new MobEffectInstance(ModEffects.CHAOS.get(), 12, 0, false, false, true));
                    Util.redirectProjectiles(o, 48, c.redirectPercent.get() / 100.0, rolled);
                } else {
                    Util.endProbability(o);
                }
            }
            if (age % 10 == 0) {
                for (LivingEntity e : foes(o, R)) {
                    Util.slowPct(e, c.slowPercent.get(), 30);
                    Util.outDamage(e, c.outPercent.get(), 30);
                    Util.healCut(e, c.healPercent.get(), 30);
                }
            }
            if (age % 8 == 0) {
                Util.fx(level(), ModParticles.RUNE.get(), p.x, p.y + 20, p.z, 8, R * 0.5, 14, R * 0.5, 0.02);
            }
        }
        if (at(age, 2.0)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.firstFlat.get(), c.firstPercent.get());
                track(e);
            }
            Util.voidBurst(level(), p.x, p.y + 4, p.z, 16, 200);
            Util.ring(level(), ModParticles.RUNE.get(), p.add(0, 0.5, 0), 120, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 7f, 0.5f);
        }
        int s0 = tk(5.0), s1 = tk(8.0), n = c.strikeCount.get();
        if (n > 0 && age >= s0 && age < s1) {
            int step = Math.max(1, (s1 - s0) / n);
            if ((age - s0) % step == 0 && strikes < n) {
                strikes++;
                probabilityStrike(o, c);
            }
        }
        if (in(age, 8.0, 11.0) && age % 2 == 0) {
            for (LivingEntity e : foes(o, R)) {
                Util.pull(e, p.add(0, 5, 0), 0.25, 3.0);
            }
        }
        if (at(age, 11.0)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), below(e, c.executeThresholdPercent.get(), c.executeBonusPercent.get()), 1.0);
                track(e);
            }
            Util.voidBurst(level(), p.x, p.y + 5, p.z, 28, 320);
            Util.ring(level(), ModParticles.RUNE.get(), p.add(0, 0.8, 0), 144, 3.6, 0.06);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.5, 0), 144, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 8f, 0.45f);
        }
    }

    private void probabilityStrike(LivingEntity o, VelConfig.Chaos c) {
        List<LivingEntity> foes = foes(o, c.radius.get());
        Vec3 q;
        if (!foes.isEmpty() && rand.nextDouble() < 0.6) {
            q = foes.get(rand.nextInt(foes.size())).position();
        } else {
            q = spot(rand, c.radius.get());
        }
        for (LivingEntity e : Util.enemies(level(), q, 5, o)) {
            Util.hurt(o, e, c.strikeFlat.get(), 0);
            track(e);
        }
        int kind = 1 + rand.nextInt(3);
        pushFx(kind, q.x, q.y, q.z);
        Util.voidBurst(level(), q.x, q.y + 1, q.z, 4, 50);
        Util.sound(level(), q.x, q.y, q.z, kind == 1 ? ModSounds.THUNDER.get() : kind == 2 ? ModSounds.TEAR.get() : ModSounds.BOOM.get(), 3f, 0.7f + rand.nextFloat() * 0.5f);
    }

    @Override
    protected void onFinish(boolean normal) {
        LivingEntity o = ownerAny();
        if (o != null) {
            Util.endProbability(o);
        }
    }
}
