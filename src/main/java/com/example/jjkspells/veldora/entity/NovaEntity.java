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

/** Dragon Nova - the Star That Drowns the Sky. Centred on the aimed point; the star forms above the caster and falls on it. */
public class NovaEntity extends SeqEntity {
    public NovaEntity(EntityType<? extends NovaEntity> type, Level level) {
        super(type, level);
    }

    public static NovaEntity create(Level level, LivingEntity caster) {
        var c = VelConfig.NOVA;
        NovaEntity e = new NovaEntity(ModEntities.NOVA.get(), level);
        e.begin(caster, groundPoint(level, caster, c.range.get()), c.radius.get(), c.finalRadius.get(), caster.getYRot());
        return e;
    }

    @Override
    public int total() {
        return tk(12.0);
    }

    private void hit(LivingEntity o, double radius, double flat, double pct, double mult) {
        var c = VelConfig.NOVA;
        for (LivingEntity e : foes(o, radius)) {
            Util.hurt(o, e, flat, pct, mult * 1.0, 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
            track(e);
        }
    }

    @Override
    protected void run(int age, LivingEntity o) {
        var c = VelConfig.NOVA;
        Vec3 p = position();
        if (age == 0) {
            Util.sound(level(), o.getX(), o.getY(), o.getZ(), ModSounds.HUM.get(), 4f, 0.5f);
        }
        if (in(age, 0, 1.5) && age % 3 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), o.getX(), o.getY() + 2, o.getZ(), 6, 0.5, 0.5, 0.5, 0.1);
        }
        if (in(age, 1.5, 5.5) && age % 4 == 0) {
            Util.fx(level(), ModParticles.FRAGMENT.get(), p.x, p.y + 30, p.z, 6, 10, 6, 10, 0.05);
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 40, p.z, 6, 12, 4, 12, 0.1);
        }
        if (at(age, 3.0)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.CHARGE.get(), 6f, 0.5f);
        }
        if (at(age, 5.5)) {
            hit(o, c.radius.get(), c.impactFlat.get(), c.impactPercent.get(), 1.0);
            Util.sparkBurst(level(), p.x, p.y + 4, p.z, 16, 220);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.6, 0), 96, 2.6, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.BOOM.get(), 8f, 0.5f);
        }
        if (at(age, 6.0)) {
            int t = tk(c.fractureSeconds.get());
            for (LivingEntity e : foes(o, c.supernovaRadius.get())) {
                Util.hurt(o, e, c.supernovaFlat.get(), c.supernovaPercent.get(), 1.0, 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
                track(e);
                if (e.isAlive()) {
                    Util.vulnerable(e, c.fractureVulnPercent.get(), t);
                    Util.healCut(e, c.fractureHealPercent.get(), t);
                    e.addEffect(new MobEffectInstance(ModEffects.OVERLOAD.get(), t, 0, false, false, true));
                }
            }
            Util.voidBurst(level(), p.x, p.y + 6, p.z, 28, 340);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.8, 0), 144, 3.6, 0.06);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 8f, 0.5f);
        }
        if (age >= tk(6.0) && age < tk(8.5) && age % 2 == 0) {
            for (LivingEntity e : foes(o, c.supernovaRadius.get())) {
                Util.pull(e, p.add(0, 3, 0), c.gravityStrength.get(), 3.0);
            }
        }
        if (in(age, 6.0, 8.5) && age % 6 == 0) {
            Util.fx(level(), ModParticles.SPARK.get(), p.x, p.y + 6, p.z, 20, 10, 6, 10, 0.4);
        }
        if (at(age, 9.5)) {
            Util.sound(level(), p.x, p.y, p.z, ModSounds.TEAR.get(), 6f, 0.4f);
        }
        if (at(age, 11.0)) {
            for (LivingEntity e : foes(o, c.finalRadius.get())) {
                Util.hurt(o, e, c.finalFlat.get(), c.finalPercent.get(), below(e, c.executeThresholdPercent.get(), c.executeBonusPercent.get()), 1.0, c.armorIgnorePercent.get() / 100.0, c.reduceIgnorePercent.get() / 100.0);
                track(e);
            }
            Util.voidBurst(level(), p.x, p.y + 4, p.z, 40, 440);
            Util.ring(level(), ModParticles.SPARK.get(), p.add(0, 0.8, 0), 160, 4.0, 0.06);
            Util.ring(level(), ModParticles.SLIME.get(), p.add(0, 0.5, 0), 160, 3.2, 0.05);
            Util.sound(level(), p.x, p.y, p.z, ModSounds.COLLAPSE.get(), 10f, 0.4f);
        }
    }
}
