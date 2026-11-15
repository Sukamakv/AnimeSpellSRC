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

/** Black Thunder Apocalypse. fx type 1 = a bolt {x, y, z}, type 2 = the first bolt {x, y, z}. */
public class ThunderEntity extends SeqEntity {
    private final Map<UUID, Integer> hits = new HashMap<>();
    private Random rand;
    private int lastStrike = -1;

    public ThunderEntity(EntityType<? extends ThunderEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 16;
    }

    public static ThunderEntity create(Level level, LivingEntity caster) {
        var c = VelConfig.THUNDER;
        ThunderEntity e = new ThunderEntity(ModEntities.THUNDER.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(9.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VelConfig.THUNDER;
        Vec3 p = position();
        if (rand == null) {
            rand = new Random(getId() * 31337L + 17);
        }
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.THUNDER.get(), 5f, 0.45f);
        }
        if (age >= tk(2.5) && age < tk(8.0) && age % 2 == 0) {
            guard(o, c.selfReductionPercent.get());
        }
        if (in(age, 0, 2.5) && age % 5 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 40, p.z, 10, c.radius.get() * 0.5, 4, c.radius.get() * 0.5, 0.1);
        }
        if (at(age, 2.5)) {
            int t = tk(c.paralysisSeconds.get());
            for (LivingEntity e : foes(o, c.firstRadius.get())) {
                Util.hurt(o, e, c.firstFlat.get(), c.firstPercent.get(), water(e, c.waterBonusPercent.get()), 1.0);
                track(e);
                if (e.isAlive()) {
                    Util.slowPct(e, c.paralysisSlowPercent.get(), t);
                    Util.atkSlow(e, c.paralysisAtkPercent.get(), t);
                }
            }
            pushFx(2, p.x, p.y, p.z);
            Util.sparkBurst(level(), p.x, p.y + 2, p.z, 14, 180);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.5, 0), 96, 2.4, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.THUNDER.get(), 7f, 0.5f);
        }
        int s0 = tk(3.8), s1 = tk(6.5), n = c.strikeCount.get();
        if (age >= s0 && age < s1 && n > 0) {
            int idx = (age - s0) * n / (s1 - s0);
            int due = idx - lastStrike;
            for (int k = 0; k < due; k++) {
                strike(o, c);
            }
            lastStrike = idx;
        }
        if (at(age, 8.0)) {
            for (LivingEntity e : foes(o, c.finalRadius.get())) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), below(e, c.executeThresholdPercent.get(), c.executeBonusPercent.get()) * water(e, c.waterBonusPercent.get()), 1.0);
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 3, p.z, 24, 320);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.6, 0), 144, 3.8, 0.06);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 7f, 0.55f);
        }
    }

    private void strike(LivingEntity o, VelConfig.Thunder c) {
        List<LivingEntity> foes = foes(o, c.radius.get());
        Vec3 q;
        if (!foes.isEmpty() && rand.nextDouble() < 0.6) {
            q = foes.get(rand.nextInt(foes.size())).position();
        } else {
            q = spot(rand, c.radius.get());
        }
        for (LivingEntity e : Util.enemies(level(), q, c.strikeRadius.get(), o)) {
            int h = hits.getOrDefault(e.getUUID(), 0);
            if (h >= c.maxHitsPerTarget.get()) {
                continue;
            }
            hits.put(e.getUUID(), h + 1);
            Util.hurt(o, e, c.strikeFlat.get(), 0, water(e, c.waterBonusPercent.get()), 1.0);
            track(e);
        }
        pushFx(1, q.x, q.y, q.z);
        Util.sparkBurst(level(), q.x, q.y + 1, q.z, 3, 24);
        if (rand.nextInt(4) == 0) {
            Util.sound(level(), q.x, q.y, q.z, ModSounds.THUNDER.get(), 3f, 0.7f + rand.nextFloat() * 0.4f);
        }
    }
}
