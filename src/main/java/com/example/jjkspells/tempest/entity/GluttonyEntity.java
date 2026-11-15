package com.example.jjkspells.tempest.entity;

import com.example.jjkspells.tempest.config.TemConfig;
import com.example.jjkspells.tempest.registry.*;
import com.example.jjkspells.tempest.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Beelzebuth - The Abyss That Consumes All. */
public class GluttonyEntity extends SeqEntity {
    public GluttonyEntity(EntityType<? extends GluttonyEntity> type, Level level) {
        super(type, level);
    }

    public static GluttonyEntity create(Level level, LivingEntity caster) {
        var c = TemConfig.GLUTTONY;
        GluttonyEntity e = new GluttonyEntity(ModEntities.GLUTTONY.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(11.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = TemConfig.GLUTTONY;
        Vec3 p = position();
        double R = c.radius.get();
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HUM.get(), 4f, 0.5f);
        }
        if (in(age, 1.0, 9.0) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SOUL.get(), p.x, p.y + 10, p.z, 6, R * 0.5, 5, R * 0.5, 0.02);
            Util.fx(level(), ModParticles.FRAGMENT.get(), p.x, p.y + 6, p.z, 4, R * 0.4, 4, R * 0.4, 0.02);
        }
        if (at(age, 2.5)) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.openFlat.get(), c.openPercent.get());
                track(e);
            }
            Util.voidBurst(level(), p.x, p.y + 8, p.z, 14, 140);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.VOID.get(), 5f, 0.6f);
        }
        if (age >= tk(2.5) && age < tk(9.0)) {
            double boost = age >= tk(7.5) ? 1.6 : 1.0;
            if (age % 2 == 0) {
                for (LivingEntity e : foes(o, R)) {
                    double f = Util.isBoss(e) ? c.bossPullPercent.get() / 100.0 : 1.0;
                    Util.pull(e, p.add(0, 4, 0), c.pullStrength.get() * f * boost, 2.5);
                }
            }
            if (age % 10 == 0) {
                for (LivingEntity e : foes(o, R)) {
                    Util.slowPct(e, c.slowPercent.get(), 30);
                    Util.outDamage(e, c.outPercent.get(), 30);
                    e.addEffect(new MobEffectInstance(ModEffects.DEVOUR.get(), 40, 0, false, false, true));
                }
            }
        }
        if (age >= tk(4.0) && age < tk(7.5) && age % 20 == 0) {
            for (LivingEntity e : foes(o, R)) {
                Util.hurt(o, e, c.dps.get(), 0);
                track(e);
            }
            Util.sound(level(), p.x, p.y, p.z, ModSounds.SUCTION.get(), 3f, 0.6f);
        }
        if (at(age, 9.0)) {
            int t = tk(c.fractureSeconds.get());
            for (LivingEntity e : foes(o, c.finalRadius.get())) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get());
                devourWeak(o, e, c.consumeThresholdPercent.get());
                if (e.isAlive()) {
                    Util.vulnerable(e, c.fractureVulnPercent.get(), t);
                    Util.healCut(e, c.fractureHealPercent.get(), t);
                    e.addEffect(new MobEffectInstance(ModEffects.FRACTURE.get(), t, 0, false, false, true));
                }
            }
            restoreFromKills(o, c.healPercent.get(), c.manaPercent.get(), c.restoreMaxPercent.get());
            Util.voidBurst(level(), p.x, p.y + 6, p.z, 26, 280);
            Util.ring(level(), ModParticles.SOUL.get(), p.add(0, 0.8, 0), 120, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 6f, 0.5f);
        }
    }
}
