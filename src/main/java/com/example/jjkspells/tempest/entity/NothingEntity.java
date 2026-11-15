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

/** Imaginary Collapse - The Birth of Nothingness. */
public class NothingEntity extends SeqEntity {
    public NothingEntity(EntityType<? extends NothingEntity> type, Level level) {
        super(type, level);
    }

    public static NothingEntity create(Level level, LivingEntity caster) {
        var c = TemConfig.NOTHING;
        NothingEntity e = new NothingEntity(ModEntities.NOTHING.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(12.0);
    }

    private void hit(LivingEntity o, double radius, double flat, double pct, double mult) {
        var c = TemConfig.NOTHING;
        for (LivingEntity e : foes(o, radius)) {
            Util.hurt(o, e, flat, pct, mult, 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
        }
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = TemConfig.NOTHING;
        Vec3 p = position();
        if (age == 0) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.HUM.get(), 4f, 0.5f);
        }
        if (in(age, 1.5, 6.0) && age % 3 == 0) {
            Util.fx(level(), ModParticles.FRAGMENT.get(), p.x, p.y + 25, p.z, 6, 8, 6, 8, 0.05);
        }
        if (at(age, 3.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.VOID.get(), 5f, 0.5f);
        }
        if (at(age, 6.0)) {
            hit(o, c.radius.get(), c.impactFlat.get(), c.impactPercent.get(), 1.0);
            Util.voidBurst(level(), p.x, p.y + 4, p.z, 16, 200);
            Util.ring(level(), ModParticles.SOUL.get(), p.add(0, 0.6, 0), 96, 2.4, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 6f, 0.5f);
        }
        if (at(age, 6.5)) {
            int t = tk(c.fractureSeconds.get());
            for (LivingEntity e : foes(o, c.supernovaRadius.get())) {
                Util.hurt(o, e, c.supernovaFlat.get(), c.supernovaPercent.get(), 1.0, 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
                if (e.isAlive()) {
                    Util.vulnerable(e, c.fractureVulnPercent.get(), t);
                    Util.healCut(e, c.fractureHealPercent.get(), t);
                    e.addEffect(new MobEffectInstance(ModEffects.FRACTURE.get(), t, 0, false, false, true));
                }
            }
            Util.voidBurst(level(), p.x, p.y + 6, p.z, 28, 320);
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.8, 0), 120, 3.2, 0.06);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 6f, 0.5f);
        }
        if (age >= tk(6.0) && age < tk(12.0) && age % 2 == 0) {
            for (LivingEntity e : foes(o, c.gravityRadius.get())) {
                Util.pull(e, p.add(0, 3, 0), c.gravityStrength.get(), 3.0);
            }
        }
        if (at(age, 10.5)) {
            for (LivingEntity e : foes(o, c.finalRadius.get())) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), below(e, c.executeThresholdPercent.get(), c.executeBonusPercent.get()), 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
            }
            Util.voidBurst(level(), p.x, p.y + 4, p.z, 36, 400);
            Util.ring(level(), ModParticles.SOUL.get(), p.add(0, 0.8, 0), 144, 3.6, 0.06);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 7f, 0.4f);
        }
    }
}
