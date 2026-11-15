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

/** Scorch Dragon's Dominion - the World Without Cold. Centred on the caster. fx type 1 = a pillar of white fire {x, y, z}. */
public class DominionEntity extends SeqEntity {
    private Random rand;

    public DominionEntity(EntityType<? extends DominionEntity> type, Level level) {
        super(type, level);
        this.fxKeep = 24;
    }

    public static DominionEntity create(Level level, LivingEntity caster) {
        var c = VgrConfig.DOMINION;
        DominionEntity e = new DominionEntity(ModEntities.DOMINION.get(), level);
        e.begin(caster, caster.position(), c.radius.get(), c.radius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(14.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VgrConfig.DOMINION;
        Vec3 p = position();
        double R = c.radius.get();
        if (rand == null) {
            rand = new Random(getId() * 8191L + 3);
        }
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HUM.get(), 6f, 0.5f);
        }
        if (age >= tk(1.0) && age < tk(12.5) && age % 4 == 0) {
            Util.reduce(o, c.selfReductionPercent.get() / 100.0, 8);
            Util.kbImmune(o, 8);
            Util.spellBonus(o, c.spellBonusPercent.get(), 8);
        }
        if (in(age, 0.0, 1.0) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 0.2, p.z, 8, 2, 0.3, 2, 0.15);
        }
        if (at(age, 1.0)) {
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.4, 0), 96, 2.2, 0.04);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.VORTEX.get(), 7f, 0.5f);
        }
        if (in(age, 1.0, 13.0) && age % 6 == 0) {
            Util.fx(level(), ModParticles.SLIME.get(), p.x, p.y + 1, p.z, 14, R * 0.5, 1, R * 0.5, 0.03);
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 20, p.z, 8, R * 0.4, 8, R * 0.4, 0.05);
        }
        if (at(age, 2.0)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.firstFlat.get(), c.firstPercent.get());
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 4, p.z, 16, 240);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.5, 0), 120, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 8f, 0.5f);
        }
        if (age >= tk(2.0) && age < tk(12.0) && age % 10 == 0) {
            for (LivingEntity e : foes(o, R)) {
                Util.slowPct(e, c.slowPercent.get(), 30);
                Util.outDamage(e, c.outPercent.get(), 30);
                Util.healCut(e, c.healPercent.get(), 30);
            }
        }
        if (age >= tk(3.0) && age < tk(11.0)) {
            if (age % 20 == 0) {
                for (LivingEntity e : foes(o, R)) {
                    Util.hurt(o, e, c.dps.get(), 0);
                    track(e);
                }
                Util.sound(level(), p.x, p.y, p.z, ModSounds.SUCTION.get(), 4f, 0.7f);
            }
            if (age % 6 == 0) {
                List<LivingEntity> foes = foes(o, R);
                int shown = 0;
                for (LivingEntity e : foes) {
                    if (shown++ >= 6) {
                        break;
                    }
                    Vec3 q = e.position();
                    pushFx(1, q.x, q.y, q.z);
                    Util.sparkBurst(level(), q.x, q.y + 1, q.z, 3, 30);
                }
                if (foes.isEmpty()) {
                    Vec3 q = spot(rand, R);
                    pushFx(1, q.x, q.y, q.z);
                }
            }
        }
        if (at(age, 12.0)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), below(e, c.executeThresholdPercent.get(), c.executeBonusPercent.get()), 1.0);
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 5, p.z, 30, 420);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.6, 0), 160, 3.8, 0.06);
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.9, 0), 120, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 10f, 0.5f);
        }
    }
}
