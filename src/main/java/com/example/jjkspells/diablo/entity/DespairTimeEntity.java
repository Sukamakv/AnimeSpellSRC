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

/** Despair Time: clock, domain, chains, stopped clock, collapse. */
public class DespairTimeEntity extends SeqEntity {
    private final Map<UUID, Integer> stacks = new HashMap<>();

    public DespairTimeEntity(EntityType<? extends DespairTimeEntity> type, Level level) {
        super(type, level);
    }

    public static DespairTimeEntity create(Level level, LivingEntity caster) {
        var c = DiaConfig.DESPAIR;
        DespairTimeEntity e = new DespairTimeEntity(ModEntities.DESPAIR.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.radius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(9.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = DiaConfig.DESPAIR;
        Vec3 p = position();
        double R = c.radius.get();
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TICK.get(), 4f, 0.8f);
        }
        if (age < tk(1.0) && age % 10 == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TICK.get(), 3f, 0.7f);
        }
        if (at(age, 1.0)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.openFlat.get(), c.openPercent.get());
                Util.knock(e, p, 4, 0.2);
            }
            Util.ring(level(), ModParticles.ASH.get(), p.add(0, 0.5, 0), 72, 2.2, 0.04);
            Util.voidBurst(level(), p.x, p.y + 2, p.z, 10, 120);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BELL.get(), 5f, 0.5f);
        }
        if (age >= tk(1.0) && age < tk(7.5) && age % 10 == 0) {
            int t = 30;
            for (LivingEntity e : foes(o, R)) {
                double f = Util.isBoss(e) ? c.bossFactorPercent.get() / 100.0 : 1.0;
                Util.slowPct(e, c.slowPercent.get() * f, t);
                Util.atkSlow(e, c.atkPercent.get() * f, t);
                Util.outDamage(e, c.outPercent.get() * f, t);
                e.addEffect(new MobEffectInstance(ModEffects.DESPAIR.get(), t + 10, 0, false, false, true));
                if (age % 20 == 0) {
                    stacks.merge(e.getUUID(), 1, (a, b) -> Math.min(c.stackMax.get(), a + b));
                }
            }
        }
        if (at(age, 2.5)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.CHAIN.get(), 4f, 0.6f);
        }
        if (age >= tk(2.5) && age < tk(5.5) && age % 20 == 0) {
            int t = tk(c.fractureSeconds.get());
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.tormentDps.get(), 0);
                if (e.isAlive()) {
                    Util.vulnerable(e, c.fractureVulnPercent.get(), t);
                    Util.healCut(e, c.fractureHealPercent.get(), t);
                    e.addEffect(new MobEffectInstance(ModEffects.WOUND.get(), t, 0, false, false, true));
                }
            }
            Util.fx(level(), ModParticles.SOUL.get(), p.x, p.y + 1, p.z, 24, R * 0.3, 1, R * 0.3, 0.05);
        }
        if (at(age, 5.5)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SNAP.get(), 5f, 0.4f);
        }
        if (age >= tk(5.5) && age < tk(7.5) && age % 5 == 0) {
            for (LivingEntity e : foes(o, R)) {
                Util.hold(e, 8, DiaConfig.G.bossStunSlowPercent.get());
            }
        }
        if (at(age, 7.5)) {
            double b = c.stackBonusPercent.get() / 100.0;
            for (LivingEntity e : foes(o, R)) {
                int s = stacks.getOrDefault(e.getUUID(), 0);
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), 1.0 + b * s, 1.0);
            }
            Util.voidBurst(level(), p.x, p.y + 2, p.z, 18, 220);
            Util.ring(level(), ModParticles.SOUL.get(), p.add(0, 0.5, 0), 72, 2.4, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 5f, 0.5f);
        }
    }
}
