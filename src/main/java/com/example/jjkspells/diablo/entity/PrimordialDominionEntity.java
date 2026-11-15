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

/** Primordial Dominion - Weight of Infinity. Centred on the caster: silence, silhouette, shockwave and pressure, gaze, subjugation. */
public class PrimordialDominionEntity extends SeqEntity {
    public PrimordialDominionEntity(EntityType<? extends PrimordialDominionEntity> type, Level level) {
        super(type, level);
    }

    public static PrimordialDominionEntity create(Level level, LivingEntity caster) {
        var c = DiaConfig.DOMINION;
        PrimordialDominionEntity e = new PrimordialDominionEntity(ModEntities.DOMINION.get(), level);
        e.begin(caster, caster.position(), c.radius.get(), c.radius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(7.0);
    }

    private void suppress(LivingEntity o, LivingEntity e, int ticks) {
        var c = DiaConfig.DOMINION;
        double f = Util.isBoss(e) ? 0.6 : 1.0;
        Util.slowPct(e, c.suppressSlowPercent.get() * f, ticks);
        Util.atkSlow(e, c.suppressAtkPercent.get() * f, ticks);
        Util.outDamage(e, c.suppressOutPercent.get() * f, ticks);
        Util.healCut(e, c.suppressHealPercent.get(), ticks);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = DiaConfig.DOMINION;
        Vec3 p = position();
        double R = c.radius.get();
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HEARTBEAT.get(), 4f, 0.5f);
        }
        if (age < total() - 2) {
            Util.reduce(o, c.selfReductionPercent.get() / 100.0, 6);
            Util.kbImmune(o, 6);
        }
        if (at(age, 1.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HUM.get(), 4f, 0.4f);
        }
        if (in(age, 1.0, 2.0) && age % 4 == 0) {
            Util.fx(level(), ModParticles.ASH.get(), p.x, p.y + 1, p.z, 8, R * 0.3, 1, R * 0.3, 0.02);
        }
        if (at(age, 2.0)) {
            int terror = tk(c.terrorSeconds.get());
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.shockFlat.get(), c.shockPercent.get());
                if (!e.isAlive()) {
                    continue;
                }
                Util.knock(e, p, 8, 0.3);
                if (e instanceof Mob m && !Util.isBoss(m)) {
                    Util.terrify(o, m, terror);
                    Util.slowPct(m, c.terrorSlowPercent.get(), terror);
                }
                e.addEffect(new MobEffectInstance(ModEffects.SUPPRESS.get(), tk(5.2) + 20, 0, false, false, true));
            }
            Util.ring(level(), ModParticles.ASH.get(), p.add(0, 0.5, 0), 96, 3.0, 0.05);
            Util.ring(level(), ModParticles.EMBER.get(), p.add(0, 0.8, 0), 96, 2.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 5f, 0.5f);
        }
        if (age >= tk(2.0) && age < tk(5.8) && age % 10 == 0) {
            for (LivingEntity e : foes(o, R)) {
                suppress(o, e, 40);
                if (age % 20 == 0 && age < tk(4.5)) {
                    Util.hurt(o, e, c.pressureDps.get(), 0);
                    if (e.isAlive()) {
                        e.setDeltaMovement(e.getDeltaMovement().multiply(0.6, 1, 0.6).add(0, -0.15, 0));
                        e.hurtMarked = true;
                    }
                }
            }
        }
        if (in(age, 4.5, 5.8) && age % 5 == 0) {
            for (LivingEntity e : foes(o, R)) {
                if (!Util.kbImmune(e) && !Util.ccImmune(e)) {
                    Vec3 m = e.getDeltaMovement();
                    e.setDeltaMovement(m.x * 0.4, Math.min(m.y, 0) - 0.5, m.z * 0.4);
                    e.hurtMarked = true;
                }
            }
            Util.fx(level(), ModParticles.CRACK.get(), p.x, p.y + 0.1, p.z, 6, R * 0.3, 0, R * 0.3, 0.0);
        }
        if (at(age, 5.8)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), below(e, c.executeThresholdPercent.get(), c.executeBonusPercent.get()), 1.0);
                if (e.isAlive()) {
                    Util.knock(e, p, 4, 0.1);
                }
            }
            Util.voidBurst(level(), p.x, p.y + 1, p.z, 22, 200);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 5f, 0.5f);
        }
    }
}
