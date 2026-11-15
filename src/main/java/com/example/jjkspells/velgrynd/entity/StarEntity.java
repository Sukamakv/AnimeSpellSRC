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

/** Cardinal Cataclysm - the Death of a Thousand Stars. Centred on the aimed point. */
public class StarEntity extends SeqEntity {
    public StarEntity(EntityType<? extends StarEntity> type, Level level) {
        super(type, level);
    }

    public static StarEntity create(Level level, LivingEntity caster) {
        var c = VgrConfig.STAR;
        StarEntity e = new StarEntity(ModEntities.STAR.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(13.0);
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VgrConfig.STAR;
        Vec3 p = position();
        double armor = c.armorIgnorePercent.get() / 100.0, red = c.reduceIgnorePercent.get() / 100.0;
        if (age == 0) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.HUM.get(), 5f, 0.5f);
        }
        if (age >= tk(total() / 20.0 - c.protectSeconds.get()) && age < total() && age % 2 == 0) {
            guard(o, c.protectPercent.get());
        }
        if (in(age, 0.0, 1.5) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), o.getX(), o.getY() + 2, o.getZ(), 6, 0.5, 0.5, 0.5, 0.1);
        }
        if (in(age, 1.5, 5.5) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 35, p.z, 10, 18, 10, 18, 0.2);
            Util.fx(level(), ModParticles.GLOW.get(), p.x, p.y + 30, p.z, 4, 12, 8, 12, 0.1);
        }
        if (at(age, 3.5)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.CHARGE.get(), 7f, 0.5f);
        }
        if (at(age, 6.5)) {
            for (LivingEntity e : foes(o, c.radius.get())) {
                Util.hurt(o, e, c.impactFlat.get(), c.impactPercent.get(), 1.0, 1.0, armor, red);
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 4, p.z, 18, 260);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.6, 0), 120, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 9f, 0.5f);
        }
        if (at(age, 7.0)) {
            int t = tk(c.overheatSeconds.get());
            for (LivingEntity e : foes(o, c.supernovaRadius.get())) {
                Util.hurt(o, e, c.supernovaFlat.get(), c.supernovaPercent.get(), 1.0, 1.0, armor, red);
                track(e);
                if (e.isAlive()) {
                    Util.vulnerable(e, c.overheatPercent.get(), t);
                    e.addEffect(new MobEffectInstance(ModEffects.OVERHEAT.get(), t, 0, false, false, true));
                }
            }
            Util.sparkBurst(level(), p.x, p.y + 6, p.z, 32, 420);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.8, 0), 160, 3.8, 0.06);
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.5, 0), 120, 3.0, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 10f, 0.5f);
        }
        if (age >= tk(9.5) && age < tk(11.5) && age % 2 == 0) {
            for (LivingEntity e : foes(o, c.supernovaRadius.get())) {
                Util.pull(e, p.add(0, 3, 0), c.gravityStrength.get(), 3.0);
            }
        }
        if (in(age, 9.5, 11.5) && age % 5 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 6, p.z, 20, 8, 5, 8, 0.4);
        }
        if (at(age, 11.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TEAR.get(), 7f, 0.4f);
        }
        if (at(age, 12.0)) {
            double R = c.finalRadius.get();
            for (LivingEntity e : foes(o, R)) {
                double d = Math.sqrt(e.distanceToSqr(p.x, e.getY(), p.z));
                double centre = 1.0 + c.centerBonusPercent.get() / 100.0 * Math.max(0.0, 1.0 - d / R);
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), centre, 1.0, armor, red);
                track(e);
            }
            Util.sparkBurst(level(), p.x, p.y + 4, p.z, 44, 520);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.8, 0), 160, 4.2, 0.06);
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.5, 0), 160, 3.2, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 12f, 0.4f);
        }
    }
}
