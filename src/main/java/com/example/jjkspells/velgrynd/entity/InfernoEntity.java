package com.example.jjkspells.velgrynd.entity;

import com.example.jjkspells.velgrynd.config.VgrConfig;
import com.example.jjkspells.velgrynd.registry.*;
import com.example.jjkspells.velgrynd.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Cardinal Inferno - the Seven Suns of Extinction. fx type 1 = a sun impact {x, y, z, index}. */
public class InfernoEntity extends SeqEntity {
    private final Map<UUID, Integer> brand = new HashMap<>();
    private Random rand;
    private int impacts;

    public InfernoEntity(EntityType<? extends InfernoEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 30;
    }

    public static InfernoEntity create(Level level, LivingEntity caster) {
        var c = VgrConfig.INFERNO;
        InfernoEntity e = new InfernoEntity(ModEntities.INFERNO.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(11.0);
    }

    private double mult(LivingEntity e) {
        var c = VgrConfig.INFERNO;
        return 1.0 + Math.min(c.brandMaxPercent.get(), brand.getOrDefault(e.getUUID(), 0) * c.brandPercent.get()) / 100.0;
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VgrConfig.INFERNO;
        Vec3 p = position();
        double R = c.radius.get();
        if (rand == null) {
            rand = new Random(getId() * 5557L + 1);
        }
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HUM.get(), 5f, 0.5f);
        }
        if (age < tk(10.5) && age % 2 == 0) {
            guard(o, c.selfReductionPercent.get());
        }
        if (in(age, 0.0, 1.5) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), o.getX(), o.getY() + 2.5, o.getZ(), 6, 1.0, 0.6, 1.0, 0.12);
        }
        if (at(age, 1.5)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.CHARGE.get(), 6f, 0.5f);
        }
        if (in(age, 1.5, 10.0)) {
            if (age % 10 == 0) {
                for (LivingEntity e : foes(o, R)) {
                    Util.slowPct(e, c.slowPercent.get(), 30);
                    Util.healCut(e, c.healPercent.get(), 30);
                }
            }
            if (age % 5 == 0) {
                Util.fx(level(), ModParticles.SLIME.get(), p.x, p.y + 1, p.z, 10, R * 0.45, 1, R * 0.45, 0.02);
            }
        }
        int s0 = tk(3.0), s1 = tk(6.5), n = c.sunCount.get();
        if (n > 0 && age >= s0 && age < s1 && impacts < n && age >= s0 + impacts * ((s1 - s0) / n)) {
            sunImpact(o, c, impacts);
            impacts++;
        }
        if (in(age, 6.5, 8.5) && age % 2 == 0) {
            for (LivingEntity e : foes(o, R)) {
                Util.pull(e, p.add(0, 4, 0), 0.25, 3.0);
            }
        }
        if (at(age, 7.5)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.convergeFlat.get(), c.convergePercent.get(), mult(e), 1.0);
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 6, p.z, 20, 280);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.6, 0), 120, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 8f, 0.5f);
        }
        if (at(age, 9.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TEAR.get(), 6f, 0.4f);
        }
        if (at(age, 10.0)) {
            for (LivingEntity e : foes(o, c.finalRadius.get())) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), mult(e) * below(e, c.executeThresholdPercent.get(), c.executeBonusPercent.get()), 1.0);
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 6, p.z, 40, 460);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.8, 0), 160, 4.0, 0.06);
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.5, 0), 160, 3.2, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 10f, 0.45f);
        }
    }

    private void sunImpact(LivingEntity o, VgrConfig.Inferno c, int i) {
        List<LivingEntity> foes = foes(o, c.radius.get());
        Vec3 q;
        if (!foes.isEmpty() && rand.nextDouble() < 0.7) {
            q = foes.get(rand.nextInt(foes.size())).position();
        } else {
            q = spot(rand, c.radius.get());
        }
        for (LivingEntity e : Util.enemies(level(), q, c.sunRadius.get(), o)) {
            Util.hurt(o, e, c.sunFlat.get(), 0, mult(e), 1.0);
            track(e);
            if (e.isAlive()) {
                brand.merge(e.getUUID(), 1, Integer::sum);
                e.addEffect(new MobEffectInstance(ModEffects.BRAND.get(), 200, 0, false, false, true));
            }
        }
        pushFx(1, q.x, q.y, q.z, i);
        Util.sparkBurst(level(), q.x, q.y + 2, q.z, 8, 140);
        Util.ring(level(), ModParticles.SPARK.get(), q.add(0, 0.5, 0), 72, 2.2, 0.05);
        Util.sound(level(), q.x, q.y, q.z, ModSounds.BOOM.get(), 6f, 0.6f + rand.nextFloat() * 0.3f);
    }
}
